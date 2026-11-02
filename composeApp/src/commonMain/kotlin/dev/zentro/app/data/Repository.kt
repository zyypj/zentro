package dev.zentro.app.data

import dev.zentro.shared.Cards
import dev.zentro.shared.CreditCard
import dev.zentro.shared.Event
import dev.zentro.shared.Finance
import dev.zentro.shared.MercadoPagoStatus
import dev.zentro.shared.Note
import dev.zentro.shared.Person
import dev.zentro.shared.Schedule
import dev.zentro.shared.SettingsPatch
import dev.zentro.shared.TelegramLink
import dev.zentro.shared.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone

/**
 * Fonte única de dados do app.
 *
 * Funciona "local primeiro": toda alteração é aplicada e salva no aparelho na hora (e os alarmes
 * são reagendados na hora), e só depois enviada ao servidor por uma fila persistente. Sem internet
 * o app continua funcionando e os avisos continuam disparando; quando a conexão volta, tudo sobe.
 */
class Repository(private val storage: FileStorage, private val scope: CoroutineScope) {
    private val _data = MutableStateFlow(load())
    val data: StateFlow<AppData> = _data.asStateFlow()

    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    /** Dados guardados neste aparelho que podem ser reenviados a uma conta vazia no servidor. */
    private val _restoreOffer = MutableStateFlow<RestoreOffer?>(null)
    val restoreOffer: StateFlow<RestoreOffer?> = _restoreOffer.asStateFlow()

    /** Chamado sempre que a agenda ou as configurações mudam — cada plataforma reagenda seus alarmes aqui. */
    var onScheduleChanged: ((AppData) -> Unit)? = null

    private val writeLock = Mutex()
    private val syncLock = Mutex()
    private var liveJob: Job? = null

    val timeZone: TimeZone get() = TimeZone.currentSystemDefault()

    private fun load(): AppData = runCatching {
        storage.read(FILE)?.let { ApiClient.json.decodeFromString(AppData.serializer(), it) }
    }.getOrNull() ?: AppData()

    /** Recarrega do disco (usado por receivers do Android que podem rodar com o app fechado). */
    fun reload() {
        _data.value = load()
    }

    private suspend fun update(transform: (AppData) -> AppData) {
        val (before, after) = writeLock.withLock {
            val before = _data.value
            val after = transform(before)
            _data.value = after
            withContext(Dispatchers.IO) { storage.write(FILE, ApiClient.json.encodeToString(AppData.serializer(), after)) }
            before to after
        }
        if (before.events != after.events || before.settings != after.settings || before.session != after.session || before.people != after.people) {
            runCatching { onScheduleChanged?.invoke(after) }
        }
    }

    private fun api(): ApiClient? = _data.value.session?.let { ApiClient(it.serverUrl, it.token) }

    private fun todayString() = Schedule.today(timeZone).toString()

    private fun AppData.enqueue(op: PendingOp): AppData {
        // Compacta a fila: só a última alteração de cada item precisa ir para o servidor.
        val sameTarget: (PendingOp) -> Boolean = when (op.kind) {
            OpKind.UPSERT_EVENT, OpKind.DELETE_EVENT ->
                { o -> (o.kind == OpKind.UPSERT_EVENT || o.kind == OpKind.DELETE_EVENT) && o.targetId == op.targetId }
            OpKind.UPSERT_TX, OpKind.DELETE_TX ->
                { o -> (o.kind == OpKind.UPSERT_TX || o.kind == OpKind.DELETE_TX) && o.targetId == op.targetId }
            OpKind.UPSERT_PERSON, OpKind.DELETE_PERSON ->
                { o -> (o.kind == OpKind.UPSERT_PERSON || o.kind == OpKind.DELETE_PERSON) && o.targetId == op.targetId }
            OpKind.UPSERT_NOTE, OpKind.DELETE_NOTE ->
                { o -> (o.kind == OpKind.UPSERT_NOTE || o.kind == OpKind.DELETE_NOTE) && o.targetId == op.targetId }
            OpKind.UPSERT_CARD, OpKind.DELETE_CARD ->
                { o -> (o.kind == OpKind.UPSERT_CARD || o.kind == OpKind.DELETE_CARD) && o.targetId == op.targetId }
            OpKind.REVIEW -> { o -> o.kind == OpKind.REVIEW }
            OpKind.SETTINGS -> { _ -> false }
        }
        return copy(outbox = outbox.filterNot(sameTarget) + op)
    }

    // ---------------- sessão ----------------

    suspend fun login(serverUrl: String, email: String, password: String, name: String?, register: Boolean): String? = try {
        val url = ApiClient.normalizeUrl(serverUrl)
        // Guarda o que houver no aparelho antes de trocar de sessão — nunca jogamos dados fora.
        saveBackup(_data.value)
        val client = ApiClient(url, null)
        val auth = if (register) client.register(name.orEmpty(), email.trim(), password) else client.login(email.trim(), password)
        update { AppData(session = Session(url, auth.token, auth.user)) }
        _status.value = SyncStatus()
        runCatching { ApiClient(url, auth.token).patchSettings(SettingsPatch(timezone = timeZone.id)) }
        sync()
        offerRestore(auth.user.email)
        null
    } catch (e: ApiFailure) {
        e.message ?: "Não foi possível entrar"
    }

    suspend fun logout() {
        stopLive()
        saveBackup(_data.value)
        update { AppData() }
        _status.value = SyncStatus()
    }

    // ---------------- proteção contra perda de dados ----------------

    private fun AppData.hasContent() = events.isNotEmpty() || notes.isNotEmpty() || transactions.isNotEmpty() || people.isNotEmpty() || cards.isNotEmpty()

    private suspend fun saveBackup(data: AppData) {
        if (data.session == null || !data.hasContent()) return
        runCatching {
            withContext(Dispatchers.IO) {
                // A fila de envio vai junto: inclui exclusões que ainda não chegaram ao servidor.
                storage.write(BACKUP_FILE, ApiClient.json.encodeToString(AppData.serializer(), data))
            }
        }
    }

    private fun loadBackup(): AppData? = runCatching {
        storage.read(BACKUP_FILE)?.let { ApiClient.json.decodeFromString(AppData.serializer(), it) }
    }.getOrNull()

    /**
     * O que a cópia guardada tem e o servidor não: itens alterados no aparelho depois da última
     * sincronização que deu certo (feitos sem internet ou com o servidor fora do ar), mais o que
     * estava na fila de envio. Itens que o servidor já tem numa versão mais nova ficam de fora.
     */
    private class Unsynced(
        val events: List<Event>,
        val notes: List<Note>,
        val transactions: List<Transaction>,
        val people: List<Person>,
        val cards: List<CreditCard>,
        val pending: List<PendingOp>,
    ) {
        val isEmpty get() = events.isEmpty() && notes.isEmpty() && transactions.isEmpty() && people.isEmpty() && cards.isEmpty() && pending.isEmpty()
    }

    private fun unsynced(email: String): Unsynced? {
        val backup = loadBackup() ?: return null
        if (!backup.session?.user?.email.equals(email, ignoreCase = true)) return null
        val current = _data.value
        val since = backup.lastSyncOk ?: 0L
        val events = backup.events.filter { (id, e) -> e.updatedAt > since && (current.events[id]?.updatedAt ?: 0) < e.updatedAt }.values.toList()
        val notes = backup.notes.filter { (id, n) -> n.updatedAt > since && (current.notes[id]?.updatedAt ?: 0) < n.updatedAt }.values.toList()
        val transactions = backup.transactions.filter { (id, t) -> t.updatedAt > since && (current.transactions[id]?.updatedAt ?: 0) < t.updatedAt }.values.toList()
        val people = backup.people.filter { (id, p) -> p.updatedAt > since && (current.people[id]?.updatedAt ?: 0) < p.updatedAt }.values.toList()
        val cards = backup.cards.filter { (id, c) -> c.updatedAt > since && (current.cards[id]?.updatedAt ?: 0) < c.updatedAt }.values.toList()
        return Unsynced(events, notes, transactions, people, cards, backup.outbox).takeIf { !it.isEmpty }
    }

    /** Ao entrar: se este aparelho tem alterações dessa conta que não chegaram ao servidor, oferece enviar. */
    private fun offerRestore(email: String) {
        val u = unsynced(email) ?: return
        _restoreOffer.value = RestoreOffer(u.events.size, u.notes.size, u.transactions.size, u.people.size)
    }

    fun dismissRestore() {
        _restoreOffer.value = null
    }

    /** Envia ao servidor as alterações guardadas no aparelho que ainda não tinham chegado lá. */
    fun restoreBackup() = scope.launch {
        val email = _data.value.session?.user?.email ?: return@launch
        val u = unsynced(email) ?: return@launch
        _restoreOffer.value = null
        update { current ->
            var d = current.copy(
                events = current.events + u.events.associateBy { it.id },
                notes = current.notes + u.notes.associateBy { it.id },
                transactions = current.transactions + u.transactions.associateBy { it.id },
                people = current.people + u.people.associateBy { it.id },
                cards = current.cards + u.cards.associateBy { it.id },
            )
            u.cards.forEach { d = d.enqueue(PendingOp(OpKind.UPSERT_CARD, it.id, card = it)) }
            u.people.forEach { d = d.enqueue(PendingOp(OpKind.UPSERT_PERSON, it.id, person = it)) }
            u.events.forEach { d = d.enqueue(PendingOp(OpKind.UPSERT_EVENT, it.id, event = it)) }
            u.notes.forEach { d = d.enqueue(PendingOp(OpKind.UPSERT_NOTE, it.id, note = it)) }
            u.transactions.forEach { d = d.enqueue(PendingOp(OpKind.UPSERT_TX, it.id, transaction = it)) }
            // Exclusões feitas offline que estavam na fila.
            u.pending.filter { it.kind.name.startsWith("DELETE") }.forEach { op ->
                d = d.enqueue(op)
                d = when (op.kind) {
                    OpKind.DELETE_EVENT -> d.copy(events = d.events - op.targetId)
                    OpKind.DELETE_NOTE -> d.copy(notes = d.notes - op.targetId)
                    OpKind.DELETE_TX -> d.copy(transactions = d.transactions - op.targetId)
                    OpKind.DELETE_PERSON -> d.copy(people = d.people - op.targetId)
                    OpKind.DELETE_CARD -> d.copy(cards = d.cards - op.targetId)
                    else -> d
                }
            }
            d
        }
        sync()
    }

    /** Envia de novo tudo o que está neste aparelho para o servidor (não apaga nada lá). */
    fun reuploadAll() = scope.launch {
        update { it.enqueueEverything() }
        sync()
    }

    private fun AppData.enqueueEverything(): AppData {
        var d = this
        people.values.forEach { d = d.enqueue(PendingOp(OpKind.UPSERT_PERSON, it.id, person = it)) }
        cards.values.forEach { d = d.enqueue(PendingOp(OpKind.UPSERT_CARD, it.id, card = it)) }
        events.values.forEach { d = d.enqueue(PendingOp(OpKind.UPSERT_EVENT, it.id, event = it)) }
        notes.values.forEach { d = d.enqueue(PendingOp(OpKind.UPSERT_NOTE, it.id, note = it)) }
        transactions.values.forEach { d = d.enqueue(PendingOp(OpKind.UPSERT_TX, it.id, transaction = it)) }
        return d
    }

    // ---------------- agenda ----------------

    fun saveEvent(event: Event) = scope.launch {
        val saved = event.copy(updatedAt = Schedule.now(), deleted = false)
        update {
            it.copy(
                events = it.events + (saved.id to saved),
                settings = it.settings.copy(lastReviewDate = todayString()),
            ).enqueue(PendingOp(OpKind.UPSERT_EVENT, saved.id, event = saved))
        }
        sync()
    }

    fun deleteEvent(id: String) = scope.launch {
        update {
            it.copy(
                events = it.events - id,
                settings = it.settings.copy(lastReviewDate = todayString()),
            ).enqueue(PendingOp(OpKind.DELETE_EVENT, id))
        }
        sync()
    }

    /** "Revisei minha agenda hoje" — para de cobrar a atualização do dia. */
    fun markReviewed() = scope.launch {
        val today = todayString()
        update {
            it.copy(settings = it.settings.copy(lastReviewDate = today))
                .enqueue(PendingOp(OpKind.REVIEW, date = today))
        }
        sync()
    }

    fun updateSettings(patch: SettingsPatch) = scope.launch {
        update {
            it.copy(
                settings = it.settings.copy(
                    timezone = patch.timezone ?: it.settings.timezone,
                    reviewTimes = patch.reviewTimes?.sorted() ?: it.settings.reviewTimes,
                ),
            ).enqueue(PendingOp(OpKind.SETTINGS, patch = patch))
        }
        sync()
    }

    // ---------------- pessoas ----------------

    fun savePerson(person: Person) = scope.launch {
        val saved = person.copy(updatedAt = Schedule.now(), deleted = false)
        update {
            it.copy(people = it.people + (saved.id to saved))
                .enqueue(PendingOp(OpKind.UPSERT_PERSON, saved.id, person = saved))
        }
        sync()
    }

    fun deletePerson(id: String) = scope.launch {
        update { it.copy(people = it.people - id).enqueue(PendingOp(OpKind.DELETE_PERSON, id)) }
        sync()
    }

    // ---------------- anotações ----------------

    /**
     * Salva na hora no aparelho; o envio ao servidor espera você parar de digitar por alguns
     * segundos, para não mandar uma requisição a cada letra.
     */
    fun saveNote(note: Note) = scope.launch {
        val now = Schedule.now()
        val saved = note.copy(updatedAt = now, createdAt = note.createdAt.takeIf { it > 0 } ?: now, deleted = false)
        update {
            it.copy(notes = it.notes + (saved.id to saved))
                .enqueue(PendingOp(OpKind.UPSERT_NOTE, saved.id, note = saved))
        }
        requestSync()
    }

    fun deleteNote(id: String) = scope.launch {
        update { it.copy(notes = it.notes - id).enqueue(PendingOp(OpKind.DELETE_NOTE, id)) }
        sync()
    }

    private var pendingSync: Job? = null

    /** Sincroniza daqui a pouco, juntando várias alterações seguidas num envio só. */
    fun requestSync(delayMillis: Long = 2_500) {
        pendingSync?.cancel()
        pendingSync = scope.launch {
            delay(delayMillis)
            sync()
        }
    }

    /**
     * Marca uma conta como paga (ou desfaz). Pagar lança a saída nas finanças com um id fixo e
     * desliga os avisos daquele mês; desfazer apaga o lançamento.
     */
    fun setBillPaid(event: Event, occurrence: Long, paid: Boolean) = scope.launch {
        val now = Schedule.now()
        val current = _data.value.events[event.id] ?: event
        val amount = current.billAmountCents ?: return@launch
        val updated = current.copy(
            paid = if (paid) (current.paid + occurrence).distinct() else current.paid - occurrence,
            updatedAt = now,
        )
        val txId = dev.zentro.shared.Bills.paymentId(event.id, occurrence)
        update { d ->
            val withEvent = d.copy(events = d.events + (updated.id to updated))
                .enqueue(PendingOp(OpKind.UPSERT_EVENT, updated.id, event = updated))
            if (paid) {
                val tx = Transaction(
                    id = txId,
                    type = dev.zentro.shared.TxType.EXPENSE,
                    amountCents = amount,
                    description = current.title,
                    category = current.billCategory,
                    date = now,
                    updatedAt = now,
                )
                withEvent.copy(transactions = withEvent.transactions + (txId to tx)).enqueue(PendingOp(OpKind.UPSERT_TX, txId, transaction = tx))
            } else {
                withEvent.copy(transactions = withEvent.transactions - txId).enqueue(PendingOp(OpKind.DELETE_TX, txId))
            }
        }
        sync()
    }

    // ---------------- finanças ----------------

    fun saveTransaction(tx: Transaction) = scope.launch {
        val saved = tx.copy(updatedAt = Schedule.now(), deleted = false)
        update {
            it.copy(transactions = it.transactions + (saved.id to saved))
                .enqueue(PendingOp(OpKind.UPSERT_TX, saved.id, transaction = saved))
        }
        sync()
    }

    fun deleteTransaction(id: String) = scope.launch {
        update {
            it.copy(transactions = it.transactions - id).enqueue(PendingOp(OpKind.DELETE_TX, id))
        }
        sync()
    }

    /** Lança a diferença para o saldo de agora ficar igual a [targetCents] (ex.: o que o banco mostra). */
    fun adjustBalance(targetCents: Long) = scope.launch {
        val now = Schedule.now()
        val tx = Finance.adjustment(_data.value.activeTransactions, targetCents, newId(), now) ?: return@launch
        val saved = tx.copy(updatedAt = now)
        update {
            it.copy(transactions = it.transactions + (saved.id to saved))
                .enqueue(PendingOp(OpKind.UPSERT_TX, saved.id, transaction = saved))
        }
        sync()
    }

    // ---------------- cartões de crédito ----------------

    fun saveCard(card: CreditCard) = scope.launch {
        val saved = card.copy(updatedAt = Schedule.now(), deleted = false)
        update {
            it.copy(cards = it.cards + (saved.id to saved))
                .enqueue(PendingOp(OpKind.UPSERT_CARD, saved.id, card = saved))
        }
        sync()
    }

    /** Apaga o cartão e as compras feitas nele. Faturas já pagas continuam (já saíram do saldo). */
    fun deleteCard(id: String) = scope.launch {
        update { d ->
            val purchases = d.transactions.values.filter { it.cardId == id }.map { it.id }
            var next = d.copy(cards = d.cards - id, transactions = d.transactions - purchases.toSet())
                .enqueue(PendingOp(OpKind.DELETE_CARD, id))
            purchases.forEach { next = next.enqueue(PendingOp(OpKind.DELETE_TX, it)) }
            next
        }
        sync()
    }

    /**
     * Marca a fatura como paga (ou desfaz). Pagar lança uma saída com o total da fatura, que desconta
     * do saldo; desfazer apaga esse lançamento.
     */
    fun setInvoicePaid(invoice: Cards.Invoice, paid: Boolean) = scope.launch {
        val now = Schedule.now()
        val id = Cards.paymentId(invoice.card.id, invoice.month)
        update { d ->
            if (paid) {
                if (invoice.totalCents <= 0) return@update d
                val tx = Cards.payment(invoice, now).copy(updatedAt = now)
                d.copy(transactions = d.transactions + (id to tx)).enqueue(PendingOp(OpKind.UPSERT_TX, id, transaction = tx))
            } else {
                val existing = d.transactions.values.filter { it.invoiceKey == invoice.key }.map { it.id }.toSet() + id
                var next = d.copy(transactions = d.transactions - existing)
                existing.forEach { next = next.enqueue(PendingOp(OpKind.DELETE_TX, it)) }
                next
            }
        }
        sync()
    }

    // ---------------- sincronização ----------------

    private suspend fun send(client: ApiClient, op: PendingOp) {
        when (op.kind) {
            OpKind.UPSERT_EVENT -> client.putEvent(op.event!!)
            OpKind.DELETE_EVENT -> client.deleteEvent(op.targetId)
            OpKind.UPSERT_TX -> client.putTransaction(op.transaction!!)
            OpKind.DELETE_TX -> client.deleteTransaction(op.targetId)
            OpKind.REVIEW -> client.review(op.date!!)
            OpKind.SETTINGS -> client.patchSettings(op.patch!!)
            OpKind.UPSERT_PERSON -> client.putPerson(op.person!!)
            OpKind.DELETE_PERSON -> client.deletePerson(op.targetId)
            OpKind.UPSERT_NOTE -> client.putNote(op.note!!)
            OpKind.DELETE_NOTE -> client.deleteNote(op.targetId)
            OpKind.UPSERT_CARD -> client.putCard(op.card!!)
            OpKind.DELETE_CARD -> client.deleteCard(op.targetId)
        }
    }

    /** Envia a fila pendente e baixa as novidades. Retorna true se tudo correu bem. */
    suspend fun sync(): Boolean = syncLock.withLock {
        val client = api() ?: return false
        _status.update { it.copy(syncing = true) }
        try {
            while (true) {
                val op = _data.value.outbox.firstOrNull() ?: break
                var warning: String? = null
                try {
                    send(client, op)
                } catch (e: ApiFailure) {
                    // Erro de rede, servidor fora ou sessão expirada: mantém na fila e tenta depois.
                    if (e.network || e.status == null || e.status == 401 || e.status >= 500) throw e
                    // Erro de validação (4xx): o servidor nunca vai aceitar; descarta para não travar a fila.
                    warning = e.message
                }
                update { d -> d.copy(outbox = d.outbox.filterNot { it.opId == op.opId }) }
                if (warning != null) _status.update { it.copy(error = warning) }
            }

            val response = client.sync(_data.value.lastSync)
            update { d ->
                val pendingIds = d.outbox.map { it.targetId }.toSet()
                val events = d.events.toMutableMap()
                for (e in response.events) {
                    if (e.id in pendingIds) continue
                    if (e.deleted) events.remove(e.id) else events[e.id] = e
                }
                val transactions = d.transactions.toMutableMap()
                for (t in response.transactions) {
                    if (t.id in pendingIds) continue
                    if (t.deleted) transactions.remove(t.id) else transactions[t.id] = t
                }
                val people = d.people.toMutableMap()
                for (p in response.people) {
                    if (p.id in pendingIds) continue
                    if (p.deleted) people.remove(p.id) else people[p.id] = p
                }
                val notes = d.notes.toMutableMap()
                for (n in response.notes) {
                    if (n.id in pendingIds) continue
                    if (n.deleted) notes.remove(n.id) else notes[n.id] = n
                }
                val cards = d.cards.toMutableMap()
                for (c in response.cards) {
                    if (c.id in pendingIds) continue
                    if (c.deleted) cards.remove(c.id) else cards[c.id] = c
                }
                val settingsPending = d.outbox.any { it.kind == OpKind.REVIEW || it.kind == OpKind.SETTINGS }
                d.copy(
                    events = events,
                    transactions = transactions,
                    people = people,
                    notes = notes,
                    cards = cards,
                    settings = if (settingsPending) d.settings else response.settings,
                    lastSync = response.serverTime,
                    lastSyncOk = Schedule.now(),
                )
            }
            _status.update { it.copy(error = null, sessionExpired = false) }
            true
        } catch (e: ApiFailure) {
            _status.update { it.copy(error = e.message, sessionExpired = e.status == 401) }
            false
        } finally {
            _status.update { it.copy(syncing = false) }
        }
    }

    /** Conexão de tempo real: qualquer mudança feita em outro aparelho aparece aqui em segundos. */
    fun startLive() {
        if (liveJob?.isActive == true) return
        liveJob = scope.launch {
            var backoff = 2_000L
            while (isActive) {
                val client = api() ?: break
                try {
                    client.live(
                        onConnected = {
                            backoff = 2_000L
                            _status.update { it.copy(online = true) }
                            sync()
                        },
                        onMessage = { sync() },
                    )
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
                _status.update { it.copy(online = false) }
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(60_000L)
            }
        }
    }

    fun stopLive() {
        liveJob?.cancel()
        liveJob = null
        _status.update { it.copy(online = false) }
    }

    // ---------------- integrações (precisam de internet) ----------------

    private suspend fun <T> online(block: suspend (ApiClient) -> T): Result<T> {
        val client = api() ?: return Result.failure(ApiFailure("Entre na sua conta primeiro"))
        return try {
            Result.success(block(client))
        } catch (e: ApiFailure) {
            Result.failure(e)
        }
    }

    // ---------------- imagens das notas ----------------

    private val imageMemory = LinkedHashMap<String, ByteArray>()

    /** Envia uma imagem e devolve o endereço para usar na nota (`zentro:<id>`). Precisa de internet. */
    suspend fun uploadImage(bytes: ByteArray, name: String): Result<String> = online { client ->
        val id = client.uploadFile(bytes, name).id
        runCatching { withContext(Dispatchers.IO) { storage.writeBytes("images/$id", bytes) } }
        dev.zentro.shared.Markdown.FILE_SCHEME + id
    }

    /**
     * Conteúdo de uma imagem da nota: primeiro da memória, depois do cache no aparelho e, por fim,
     * do servidor (ou da internet, para links https). Depois da primeira vez funciona offline.
     */
    suspend fun imageBytes(src: String): ByteArray? {
        imageMemory[src]?.let { return it }
        val key = dev.zentro.shared.Markdown.fileId(src)?.let { "images/$it" } ?: "images/url-${src.hashCode().toUInt()}"
        val cached = withContext(Dispatchers.IO) { runCatching { storage.readBytes(key) }.getOrNull() }
        val bytes = cached ?: run {
            val fileId = dev.zentro.shared.Markdown.fileId(src)
            val downloaded = when {
                fileId != null -> online { it.downloadFile(fileId) }.getOrNull()
                src.startsWith("https://") || src.startsWith("http://") -> ApiClient.downloadUrl(src)
                else -> null
            } ?: return null
            withContext(Dispatchers.IO) { runCatching { storage.writeBytes(key, downloaded) } }
            downloaded
        }
        imageMemory[src] = bytes
        if (imageMemory.size > 40) imageMemory.remove(imageMemory.keys.first())
        return bytes
    }

    // ---------------- IAs (Claude e ChatGPT via MCP) ----------------

    suspend fun aiLink(reset: Boolean = false): Result<dev.zentro.shared.AiLink> = online { it.aiLink(reset) }

    suspend fun changePassword(current: String, new: String): Result<Unit> = online { it.changePassword(current, new) }

    suspend fun telegramLink(): Result<TelegramLink> = online { it.telegramLink() }
    suspend fun telegramUnlink(): Result<Unit> = online { it.telegramUnlink() }.also { sync() }
    suspend fun telegramTest(): Result<Unit> = online { it.telegramTest() }
    suspend fun mercadoPago(): Result<MercadoPagoStatus> = online { it.mercadoPago() }
    suspend fun mercadoPagoConnect(token: String): Result<MercadoPagoStatus> = online { it.mercadoPagoConnect(token) }.also { sync() }
    suspend fun mercadoPagoDisconnect(): Result<Unit> = online { it.mercadoPagoDisconnect() }.also { sync() }
    /** Pergunta ao servidor (que pode apontar para outro repositório); se não der, vai direto ao GitHub. */
    suspend fun latestRelease(): Result<dev.zentro.shared.UpdateInfo> {
        online { it.latestRelease() }.getOrNull()?.let { return Result.success(it) }
        return ApiClient.githubLatest()?.let { Result.success(it) } ?: Result.failure(ApiFailure("Sem conexão para verificar atualizações", network = true))
    }
    suspend fun mercadoPagoSync(): Result<MercadoPagoStatus> = online { it.mercadoPagoSync() }.also { sync() }

    private companion object {
        const val FILE = "zentro-data.json"
        const val BACKUP_FILE = "zentro-backup.json"
    }
}
