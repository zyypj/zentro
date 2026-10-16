package dev.zentro.app.data

import dev.zentro.shared.Event
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
        offerRestoreIfServerEmpty(auth.user.email)
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

    private fun AppData.hasContent() = events.isNotEmpty() || notes.isNotEmpty() || transactions.isNotEmpty() || people.isNotEmpty()

    private suspend fun saveBackup(data: AppData) {
        if (data.session == null || !data.hasContent()) return
        runCatching {
            withContext(Dispatchers.IO) {
                storage.write(BACKUP_FILE, ApiClient.json.encodeToString(AppData.serializer(), data.copy(outbox = emptyList())))
            }
        }
    }

    private fun loadBackup(): AppData? = runCatching {
        storage.read(BACKUP_FILE)?.let { ApiClient.json.decodeFromString(AppData.serializer(), it) }
    }.getOrNull()

    /** Entrou numa conta vazia, mas este aparelho tem dados dessa mesma conta guardados? Oferece reenviar. */
    private fun offerRestoreIfServerEmpty(email: String) {
        if (_data.value.hasContent()) return
        val backup = loadBackup() ?: return
        if (!backup.session?.user?.email.equals(email, ignoreCase = true) || !backup.hasContent()) return
        _restoreOffer.value = RestoreOffer(backup.events.size, backup.notes.size, backup.transactions.size, backup.people.size)
    }

    fun dismissRestore() {
        _restoreOffer.value = null
    }

    /** Reenvia ao servidor os dados guardados no aparelho (após trocar de servidor ou perder o banco). */
    fun restoreBackup() = scope.launch {
        val backup = loadBackup() ?: return@launch
        _restoreOffer.value = null
        update { current ->
            current.copy(
                events = backup.events + current.events,
                notes = backup.notes + current.notes,
                transactions = backup.transactions + current.transactions,
                people = backup.people + current.people,
            ).enqueueEverything()
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
                val settingsPending = d.outbox.any { it.kind == OpKind.REVIEW || it.kind == OpKind.SETTINGS }
                d.copy(
                    events = events,
                    transactions = transactions,
                    people = people,
                    notes = notes,
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
