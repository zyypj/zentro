package dev.zentro.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import dev.zentro.app.Platform
import dev.zentro.app.ui.notes.NoteMedia
import dev.zentro.app.ui.notes.NotesScreen
import dev.zentro.app.data.AppData
import dev.zentro.app.data.Repository
import dev.zentro.app.data.SyncStatus
import dev.zentro.app.data.UpdateState
import dev.zentro.app.data.newId
import dev.zentro.shared.Event
import dev.zentro.shared.Note
import dev.zentro.shared.Notes
import dev.zentro.shared.Format
import dev.zentro.shared.Person
import dev.zentro.shared.QuickInput
import dev.zentro.shared.Recurrence
import dev.zentro.shared.Schedule
import dev.zentro.shared.Transaction
import dev.zentro.shared.TxType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime

enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Hoje", Line.Home),
    AGENDA("Agenda", Line.Calendar),
    NOTES("Notas", Line.Note),
    FINANCE("Finanças", Line.Wallet),
    SETTINGS("Ajustes", Line.Settings),
}

@Composable
fun App(repo: Repository, platform: Platform) {
    ZentroTheme {
        val data by repo.data.collectAsState()
        val status by repo.status.collectAsState()
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (data.session == null) LoginScreen(repo) else MainScreen(repo, platform, data, status)
        }
    }
}

@Composable
private fun MainScreen(repo: Repository, platform: Platform, data: AppData, status: SyncStatus) {
    val tz = repo.timeZone
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val now by produceState(Schedule.now()) {
        while (true) {
            delay(30_000)
            value = Schedule.now()
        }
    }
    var tab by remember { mutableStateOf(Tab.TODAY) }
    var selectedDay by remember { mutableStateOf(Schedule.today(tz)) }
    var editingEvent by remember { mutableStateOf<EventEditRequest?>(null) }
    var editingTx by remember { mutableStateOf<TxEditRequest?>(null) }
    var editingCard by remember { mutableStateOf<CardEditRequest?>(null) }
    var openCardId by remember { mutableStateOf<String?>(null) }
    var notesFolder by remember { mutableStateOf<String?>(null) }
    var openNoteId by remember { mutableStateOf<String?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var bellOpen by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf(false) }
    val alerts = remember(data, now) { buildAlerts(data, tz, now) }
    val hasNews = alerts.any { !it.info }
    val appFocus = remember { FocusRequester() }
    var agendaFilter by remember { mutableStateOf<String?>(null) }
    val updateState by platform.updates.state.collectAsState()
    val restoreOffer by repo.restoreOffer.collectAsState()
    val noteMedia = remember {
        NoteMedia(
            load = { src -> repo.imageBytes(src)?.let { platform.decodeImage(it) } },
            pick = {
                platform.pickImage()?.let { img ->
                    repo.uploadImage(img.bytes, img.name).map { "![${img.name.substringBeforeLast('.')}]($it)" }
                }
            },
            hasClipboardImage = { platform.hasClipboardImage() },
            paste = { platform.clipboardImage()?.let { img -> repo.uploadImage(img.bytes, img.name).map { "![${if (img.name == "print.png") "" else img.name.substringBeforeLast('.')}]($it)" } } },
        )
    }
    val uriHandler = LocalUriHandler.current

    fun message(text: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
        }
    }

    fun addPerson(name: String): Person =
        Person(id = newId(), name = name, color = (data.activePeople.size + 4) % ZColors.events.size).also { repo.savePerson(it) }

    fun quickEvent(input: String): Boolean {
        // "Ana: prova sexta 10h" marca o compromisso na agenda da Ana.
        val person = data.activePeople.firstOrNull { input.startsWith(it.name + ":", ignoreCase = true) }
        val text = if (person != null) input.substringAfter(':').trim() else input
        val draft = QuickInput.parseEvent(text, Schedule.today(tz)) ?: return false
        val billCents = draft.amountCents
        if (billCents != null) {
            // Tem valor: é uma conta a pagar. Sem horário, vale o dia todo e avisa um dia antes e no dia.
            val start = Schedule.toMillis(LocalDateTime(draft.date, draft.time ?: LocalTime(8, 0)), tz)
            repo.saveEvent(
                Event(
                    id = newId(), title = draft.title, startAt = start, allDay = draft.time == null,
                    reminders = listOf(1440, 0), recurrence = draft.recurrence, color = 3,
                    billAmountCents = billCents, personId = person?.id,
                ),
            )
            val repeat = if (draft.recurrence == Recurrence.MONTHLY) " todo dia ${draft.date.dayOfMonth}" else " para ${Format.shortDate(draft.date)}"
            message("Conta \"${draft.title}\" de ${Format.money(billCents)}$repeat")
            return true
        }
        val time = draft.time ?: run {
            // Sem horário: abre o formulário já preenchido para escolher a hora.
            editingEvent = EventEditRequest(null, draft.date, title = draft.title, personId = person?.id)
            return true
        }
        val start = Schedule.toMillis(LocalDateTime(draft.date, time), tz)
        repo.saveEvent(
            Event(
                id = newId(), title = draft.title, startAt = start, endAt = start + 3_600_000,
                reminders = listOf(15, 0), personId = person?.id, recurrence = draft.recurrence,
            ),
        )
        val day = if (draft.date == Schedule.today(tz)) "hoje" else Format.longDate(draft.date)
        message((person?.let { "${it.name}: " } ?: "") + "\"${draft.title}\" criado para $day às ${Format.time(LocalDateTime(draft.date, time))}")
        return true
    }

    fun togglePaid(event: Event, occurrence: Long, paid: Boolean) {
        repo.setBillPaid(event, occurrence, paid)
        val amount = event.billAmountCents?.let { Format.money(it) }.orEmpty()
        message(if (paid) "\"${event.title}\" paga · $amount nas saídas" else "Pagamento de \"${event.title}\" desfeito")
    }

    fun quickTransaction(text: String): Boolean {
        val draft = QuickInput.parseTransaction(text)
        if (draft == null) {
            message("Comece pelo valor. Ex.: 45,90 almoço")
            return false
        }
        val category = QuickInput.guessCategory(draft.description, draft.type)
        repo.saveTransaction(
            Transaction(
                id = newId(),
                type = draft.type,
                amountCents = draft.amountCents,
                description = draft.description.ifEmpty { category },
                category = category,
                date = Schedule.now(),
            ),
        )
        message((if (draft.type == TxType.INCOME) "Entrada" else "Saída") + " de ${Format.money(draft.amountCents)} registrada")
        return true
    }

    fun newNote(text: String = "", folder: String? = null, open: Boolean = true): Note {
        val title = text.lineSequence().firstOrNull().orEmpty().take(80).trim()
        val note = Note(
            id = newId(),
            title = title,
            content = if (text.trim().length > title.length) text.trim() else "",
            folder = folder ?: notesFolder ?: Notes.DEFAULT_FOLDER,
            createdAt = Schedule.now(),
        )
        repo.saveNote(note)
        if (open) {
            openNoteId = note.id
            tab = Tab.NOTES
        }
        return note
    }

    /** Captura rápida: valor vira movimentação, dia/hora vira compromisso e o resto vira anotação. */
    fun quickCapture(text: String): Boolean = when (QuickInput.classify(text, Schedule.today(tz))) {
        QuickInput.Kind.TRANSACTION -> quickTransaction(text)
        QuickInput.Kind.EVENT -> quickEvent(text)
        QuickInput.Kind.NOTE -> {
            val note = newNote(text, folder = Notes.DEFAULT_FOLDER, open = false)
            message("Anotação \"${note.title}\" salva em ${note.folder}")
            true
        }
    }

    fun newItem(item: NewItem) {
        val today = Schedule.today(tz)
        when (item) {
            NewItem.EVENT -> editingEvent = EventEditRequest(null, if (tab == Tab.AGENDA) selectedDay else today)
            NewItem.BILL -> editingEvent = EventEditRequest(null, today, billAmountCents = 0, recurrence = Recurrence.MONTHLY)
            NewItem.NOTE -> newNote()
            NewItem.TRANSACTION -> editingTx = TxEditRequest(null)
            NewItem.CARD_PURCHASE -> editingTx = TxEditRequest(null, cardId = data.activeCards.firstOrNull()?.id)
        }
    }

    fun openAlert(target: AlertTarget) {
        when (target) {
            AlertTarget.Finance -> tab = Tab.FINANCE
            is AlertTarget.Card -> {
                tab = Tab.FINANCE
                openCardId = target.cardId
            }
            AlertTarget.Review -> tab = Tab.TODAY
            is AlertTarget.EventAt -> editingEvent = EventEditRequest(target.event, target.date)
        }
    }

    fun openHit(hit: SearchHit) {
        when (hit) {
            is SearchHit.EventHit -> editingEvent = EventEditRequest(hit.event, Schedule.toLocal(hit.event.startAt, tz).date)
            is SearchHit.NoteHit -> {
                tab = Tab.NOTES
                notesFolder = null
                openNoteId = hit.note.id
            }
            is SearchHit.TxHit -> editingTx = TxEditRequest(hit.tx)
            is SearchHit.CardHit -> {
                tab = Tab.FINANCE
                openCardId = hit.card.id
            }
        }
    }

    val bell: @Composable () -> Unit = {
        Box {
            BellButton(hasNews, { bellOpen = true })
            AlertsMenu(bellOpen, alerts, { bellOpen = false }, ::openAlert)
        }
    }

    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize()) {
            if (status.sessionExpired) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Sua sessão expirou. Entre de novo para continuar.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { scope.launch { repo.logout() } }) { Text("Entrar") }
                    }
                }
            }
            UpdateBanner(updateState, platform.updates.canInstall, platform.updates.installsInBackground, onInstall = { platform.updates.installNow() }, onOpenPage = { uriHandler.openUri(it) })
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                when (tab) {
                    Tab.TODAY -> TodayScreen(
                        data = data,
                        tz = tz,
                        now = now,
                        onCapture = ::quickCapture,
                        onOpenEvent = { e, d, t -> editingEvent = EventEditRequest(e, d, t) },
                        onReviewed = {
                            repo.markReviewed()
                            message("Agenda de hoje confirmada ✓")
                        },
                        onOpenAgenda = { day ->
                            if (day != null) selectedDay = day
                            tab = Tab.AGENDA
                        },
                        onOpenFinance = { tab = Tab.FINANCE },
                        onOpenNotes = {
                            notesFolder = null
                            tab = Tab.NOTES
                        },
                        onOpenNote = {
                            openNoteId = it.id
                            tab = Tab.NOTES
                        },
                        onNewNote = { newNote() },
                        onTogglePaid = ::togglePaid,
                        bell = bell,
                        onSearch = { searchOpen = true },
                    )
                    Tab.AGENDA -> AgendaScreen(
                        onNew = { editingEvent = EventEditRequest(null, selectedDay) },
                        allEvents = data.activeEvents,
                        people = data.activePeople,
                        filter = agendaFilter,
                        onFilter = { agendaFilter = it },
                        tz = tz,
                        now = now,
                        selected = selectedDay,
                        onSelect = { selectedDay = it },
                        onOpenEvent = { editingEvent = EventEditRequest(it, selectedDay) },
                        onTogglePaid = ::togglePaid,
                    )
                    Tab.NOTES -> NotesScreen(
                        folder = notesFolder,
                        onFolder = { notesFolder = it },
                        openId = openNoteId,
                        onOpenId = { openNoteId = it },
                        notes = data.activeNotes,
                        tz = tz,
                        now = now,
                        onSave = { repo.saveNote(it) },
                        onDelete = { repo.deleteNote(it) },
                        onMessage = ::message,
                        media = noteMedia,
                    )
                    Tab.FINANCE -> FinanceScreen(
                        onNewTransaction = { editingTx = TxEditRequest(null) },
                        transactions = data.activeTransactions,
                        events = data.activeEvents,
                        tz = tz,
                        now = now,
                        onQuickAdd = ::quickTransaction,
                        onOpen = { editingTx = TxEditRequest(it) },
                        onOpenBill = { editingEvent = EventEditRequest(it, Schedule.today(tz)) },
                        onTogglePaid = ::togglePaid,
                        onNewBill = { editingEvent = EventEditRequest(null, Schedule.today(tz), billAmountCents = 0, recurrence = Recurrence.MONTHLY) },
                        cards = data.activeCards,
                        onOpenCard = { openCardId = it.id },
                        onNewCard = { editingCard = CardEditRequest(null) },
                        onPayInvoice = { invoice, paid ->
                            repo.setInvoicePaid(invoice, paid)
                            message(if (paid) "Fatura paga: ${Format.money(invoice.totalCents)} saiu do saldo" else "Pagamento da fatura desfeito")
                        },
                        onAdjustBalance = {
                            repo.adjustBalance(it)
                            message("Saldo ajustado")
                        },
                    )
                    Tab.SETTINGS -> SettingsScreen(repo, platform, data, status, tz, ::message)
                }
            }
        }
    }

    val folders = remember(data.notes) {
        val notes = data.activeNotes
        Notes.folders(notes).filter { '/' !in it }.map { f -> f to notes.count { Notes.inFolder(it, f) } }
    }
    val pending = data.outbox.size
    val userName = data.session?.user?.name.orEmpty()

    BoxWithConstraints(
        Modifier.fillMaxSize().focusRequester(appFocus).focusable().onPreviewKeyEvent { e ->
            val ctrl = e.isCtrlPressed || e.isMetaPressed
            if (e.type == KeyEventType.KeyDown && ctrl && e.key == Key.K) {
                searchOpen = true
                true
            } else {
                false
            }
        },
    ) {
        LaunchedEffect(Unit) { runCatching { appFocus.requestFocus() } }
        val wide = maxWidth >= 840.dp
        CompositionLocalProvider(LocalWide provides wide) {
            if (wide) {
                Row(Modifier.fillMaxSize().background(ZPalette.Background)) {
                    DesktopSidebar(
                        tab = tab,
                        onTab = {
                            tab = it
                            if (it == Tab.NOTES) notesFolder = null
                        },
                        counts = mapOf(Tab.AGENDA to Schedule.agendaFor(data.activeEvents, Schedule.today(tz, now), tz).size),
                        folders = folders,
                        selectedFolder = notesFolder,
                        onFolder = {
                            notesFolder = it
                            openNoteId = null
                            tab = Tab.NOTES
                        },
                        onNewFolder = { newFolder = true },
                        version = dev.zentro.shared.BuildVersion.CURRENT,
                        status = status,
                        pending = pending,
                        userName = userName,
                        onProfile = { tab = Tab.SETTINGS },
                    )
                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbar) },
                        containerColor = ZPalette.Background,
                        topBar = {
                            DesktopToolbar(
                                crumbs = listOfNotNull("Meu espaço", tab.label, notesFolder?.takeIf { tab == Tab.NOTES }),
                                onSearch = { searchOpen = true },
                                hasNews = hasNews,
                                onBell = { bellOpen = true },
                                bellPopup = { AlertsMenu(bellOpen, alerts, { bellOpen = false }, ::openAlert) },
                                onNewItem = ::newItem,
                                canAddCardPurchase = data.activeCards.isNotEmpty(),
                            )
                        },
                    ) { padding -> Box(Modifier.padding(padding)) { content() } }
                }
            } else {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) },
                    containerColor = ZPalette.Background,
                    bottomBar = { MobileTabBar(tab) { tab = it } },
                ) { padding -> Box(Modifier.padding(padding).statusBarsPadding()) { content() } }
            }

            if (searchOpen) SearchDialog(data, tz, { searchOpen = false }, ::openHit)
            if (newFolder) {
                NewFolderDialog(
                    onCreate = { name ->
                        newFolder = false
                        notesFolder = name
                        newNote(folder = name)
                        message("Pasta \"$name\" criada com uma nota nova")
                    },
                    onDismiss = { newFolder = false },
                )
            }

            editingEvent?.let { req ->
                EventEditor(
                    request = req,
                    tz = tz,
                    people = data.activePeople,
                    onAddPerson = ::addPerson,
                    onSave = {
                        repo.saveEvent(it)
                        message("Compromisso salvo")
                    },
                    onDelete = {
                        repo.deleteEvent(it)
                        message("Compromisso excluído")
                    },
                    onDismiss = { editingEvent = null },
                )
            }
            restoreOffer?.let { offer ->
                AlertDialog(
                    onDismissRequest = { repo.dismissRestore() },
                    title = { Text("Enviar alterações guardadas?") },
                    text = {
                        Text(
                            "Este aparelho tem alterações da sua conta que ainda não chegaram ao servidor: " +
                                listOfNotNull(
                                    offer.events.takeIf { it > 0 }?.let { "$it compromisso(s)" },
                                    offer.notes.takeIf { it > 0 }?.let { "$it nota(s)" },
                                    offer.transactions.takeIf { it > 0 }?.let { "$it movimentação(ões)" },
                                    offer.people.takeIf { it > 0 }?.let { "$it pessoa(s)" },
                                ).joinToString(", ").ifEmpty { "algumas exclusões" } + ". Enviar agora?",
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            repo.restoreBackup()
                            message("Alterações enviadas")
                        }) { Text("Enviar") }
                    },
                    dismissButton = { TextButton(onClick = { repo.dismissRestore() }) { Text("Agora não") } },
                )
            }
            openCardId?.let { id -> data.cards[id] }?.takeIf { !it.deleted }?.let { card ->
                CardSheet(
                    card = card,
                    transactions = data.activeTransactions,
                    tz = tz,
                    now = now,
                    onEdit = { editingCard = CardEditRequest(card) },
                    onAddPurchase = { editingTx = TxEditRequest(null, cardId = card.id) },
                    onOpenTx = { editingTx = TxEditRequest(it) },
                    onPay = { invoice, paid ->
                        repo.setInvoicePaid(invoice, paid)
                        message(if (paid) "Fatura paga: ${Format.money(invoice.totalCents)} saiu do saldo" else "Pagamento da fatura desfeito")
                    },
                    onDismiss = { openCardId = null },
                )
            }
            editingCard?.let { req ->
                CardEditor(
                    card = req.card,
                    onSave = {
                        repo.saveCard(it)
                        message("Cartão salvo")
                    },
                    onDelete = {
                        repo.deleteCard(it)
                        openCardId = null
                        message("Cartão excluído")
                    },
                    onDismiss = { editingCard = null },
                )
            }
            editingTx?.let { req ->
                TransactionEditor(
                    request = req,
                    tz = tz,
                    cards = data.activeCards,
                    onSave = {
                        repo.saveTransaction(it)
                        message("Movimentação salva")
                    },
                    onDelete = {
                        repo.deleteTransaction(it)
                        message("Movimentação excluída")
                    },
                    onDismiss = { editingTx = null },
                )
            }
        }
    }
}

/** Faixa no topo avisando de versão nova, com o progresso do download. */
@Composable
private fun UpdateBanner(state: UpdateState, canInstall: Boolean, background: Boolean, onInstall: () -> Unit, onOpenPage: (String) -> Unit) {
    val (text, action) = when (state) {
        UpdateState.None -> return
        is UpdateState.Available -> "Zentro ${state.info.version} disponível" to (if (canInstall) "Atualizar" else "Baixar")
        is UpdateState.Downloading -> "Baixando a versão ${state.info.version}... ${(state.progress * 100).toInt()}%" to null
        is UpdateState.Ready ->
            if (background) "Versão ${state.info.version} pronta. O Zentro reinicia sozinho quando você minimizar." to "Reiniciar agora"
            else "Versão ${state.info.version} pronta" to "Instalar"
        is UpdateState.Failed -> state.message to "Tentar de novo"
    }
    Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(ZIcons.Sync, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f).padding(start = 10.dp),
                )
                if (action != null) {
                    TextButton(onClick = {
                        val info = (state as? UpdateState.Available)?.info
                        if (info != null && !canInstall) onOpenPage(info.pageUrl) else onInstall()
                    }) { Text(action) }
                } else {
                    Spacer(Modifier.height(48.dp))
                }
            }
            if (state is UpdateState.Downloading) {
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/** Pede o nome de uma pasta nova; ela passa a existir com a primeira nota dentro. */
@Composable
private fun NewFolderDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova pasta") },
        text = {
            Column {
                Text(
                    "Use \"/\" para subpastas (ex.: Faculdade/Cálculo). A pasta é criada com uma nota nova dentro.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.replace("\n", "") },
                    label = { Text("Nome da pasta") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onCreate(name.trim().trim('/')) }) { Text("Criar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
