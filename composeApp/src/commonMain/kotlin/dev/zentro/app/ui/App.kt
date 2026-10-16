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
import dev.zentro.app.Platform
import dev.zentro.app.ui.notes.NotesScreen
import dev.zentro.app.data.AppData
import dev.zentro.app.data.Repository
import dev.zentro.app.data.SyncStatus
import dev.zentro.app.data.UpdateState
import dev.zentro.app.data.newId
import dev.zentro.shared.Event
import dev.zentro.shared.Format
import dev.zentro.shared.Person
import dev.zentro.shared.QuickInput
import dev.zentro.shared.Schedule
import dev.zentro.shared.Transaction
import dev.zentro.shared.TxType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime

enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Hoje", Icons.Rounded.Home),
    AGENDA("Agenda", Icons.Rounded.DateRange),
    NOTES("Notas", ZIcons.Notes),
    FINANCE("Finanças", ZIcons.Wallet),
    SETTINGS("Ajustes", Icons.Rounded.Settings),
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
    var agendaFilter by remember { mutableStateOf<String?>(null) }
    val updateState by platform.updates.state.collectAsState()
    val restoreOffer by repo.restoreOffer.collectAsState()
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
        val time = draft.time ?: run {
            // Sem horário: abre o formulário já preenchido para escolher a hora.
            editingEvent = EventEditRequest(null, draft.date, title = draft.title, personId = person?.id)
            return true
        }
        val start = Schedule.toMillis(LocalDateTime(draft.date, time), tz)
        repo.saveEvent(Event(id = newId(), title = draft.title, startAt = start, endAt = start + 3_600_000, reminders = listOf(15, 0), personId = person?.id))
        val day = if (draft.date == Schedule.today(tz)) "hoje" else Format.longDate(draft.date)
        message((person?.let { "${it.name}: " } ?: "") + "\"${draft.title}\" criado para $day às ${Format.time(LocalDateTime(draft.date, time))}")
        return true
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

    val fab: @Composable () -> Unit = {
        when (tab) {
            Tab.TODAY, Tab.AGENDA -> ExtendedFloatingActionButton(
                onClick = {
                    val personId = agendaFilter?.takeIf { tab == Tab.AGENDA && it != AGENDA_MINE }
                    editingEvent = EventEditRequest(null, if (tab == Tab.AGENDA) selectedDay else Schedule.today(tz), personId = personId)
                },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Compromisso") },
            )
            Tab.FINANCE -> FloatingActionButton(onClick = { editingTx = TxEditRequest(null) }) { Icon(Icons.Rounded.Add, "Nova movimentação") }
            Tab.SETTINGS, Tab.NOTES -> Unit
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
                        onQuickEvent = ::quickEvent,
                        onOpenEvent = { e, d, t -> editingEvent = EventEditRequest(e, d, t) },
                        onReviewed = {
                            repo.markReviewed()
                            message("Agenda de hoje confirmada ✓")
                        },
                        onOpenFinance = { tab = Tab.FINANCE },
                    )
                    Tab.AGENDA -> AgendaScreen(
                        allEvents = data.activeEvents,
                        people = data.activePeople,
                        filter = agendaFilter,
                        onFilter = { agendaFilter = it },
                        tz = tz,
                        now = now,
                        selected = selectedDay,
                        onSelect = { selectedDay = it },
                        onOpenEvent = { editingEvent = EventEditRequest(it, selectedDay) },
                    )
                    Tab.NOTES -> NotesScreen(
                        notes = data.activeNotes,
                        tz = tz,
                        now = now,
                        onSave = { repo.saveNote(it) },
                        onDelete = { repo.deleteNote(it) },
                        onMessage = ::message,
                    )
                    Tab.FINANCE -> FinanceScreen(
                        transactions = data.activeTransactions,
                        tz = tz,
                        now = now,
                        onQuickAdd = ::quickTransaction,
                        onOpen = { editingTx = TxEditRequest(it) },
                    )
                    Tab.SETTINGS -> SettingsScreen(repo, platform, data, status, tz, ::message)
                }
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 840.dp
        CompositionLocalProvider(LocalWide provides wide) {
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(
                        header = {
                            Spacer(Modifier.height(12.dp))
                            ZentroLogo(40)
                            Spacer(Modifier.height(20.dp))
                        },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        Tab.entries.forEach { t ->
                            NavigationRailItem(
                                selected = tab == t,
                                onClick = { tab = t },
                                icon = { Icon(t.icon, null) },
                                label = { Text(t.label) },
                            )
                        }
                    }
                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbar) },
                        floatingActionButton = fab,
                        containerColor = MaterialTheme.colorScheme.background,
                    ) { padding -> Box(Modifier.padding(padding)) { content() } }
                }
            } else {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) },
                    floatingActionButton = fab,
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Tab.entries.forEach { t ->
                                NavigationBarItem(
                                    selected = tab == t,
                                    onClick = { tab = t },
                                    icon = { Icon(t.icon, null) },
                                    label = { Text(t.label) },
                                )
                            }
                        }
                    },
                ) { padding -> Box(Modifier.padding(padding)) { content() } }
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
                    title = { Text("Recuperar seus dados?") },
                    text = {
                        Text(
                            "Encontramos neste aparelho dados da sua conta que não estão salvos: " +
                                listOfNotNull(
                                    offer.events.takeIf { it > 0 }?.let { "$it compromisso(s)" },
                                    offer.notes.takeIf { it > 0 }?.let { "$it nota(s)" },
                                    offer.transactions.takeIf { it > 0 }?.let { "$it movimentação(ões)" },
                                    offer.people.takeIf { it > 0 }?.let { "$it pessoa(s)" },
                                ).joinToString(", ") + ". Quer recuperar?",
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            repo.restoreBackup()
                            message("Dados recuperados")
                        }) { Text("Recuperar") }
                    },
                    dismissButton = { TextButton(onClick = { repo.dismissRestore() }) { Text("Agora não") } },
                )
            }
            editingTx?.let { req ->
                TransactionEditor(
                    request = req,
                    tz = tz,
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
