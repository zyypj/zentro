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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.zentro.app.Platform
import dev.zentro.app.data.AppData
import dev.zentro.app.data.Repository
import dev.zentro.app.data.SyncStatus
import dev.zentro.app.data.newId
import dev.zentro.shared.Event
import dev.zentro.shared.Format
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

    fun message(text: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
        }
    }

    fun quickEvent(text: String): Boolean {
        val draft = QuickInput.parseEvent(text, Schedule.today(tz)) ?: return false
        val time = draft.time ?: run {
            // Sem horário: abre o formulário já preenchido para escolher a hora.
            editingEvent = EventEditRequest(null, draft.date, title = draft.title)
            return true
        }
        val start = Schedule.toMillis(LocalDateTime(draft.date, time), tz)
        repo.saveEvent(Event(id = newId(), title = draft.title, startAt = start, endAt = start + 3_600_000, reminders = listOf(15, 0)))
        val day = if (draft.date == Schedule.today(tz)) "hoje" else Format.longDate(draft.date)
        message("\"${draft.title}\" criado para $day às ${Format.time(LocalDateTime(draft.date, time))}")
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
                onClick = { editingEvent = EventEditRequest(null, if (tab == Tab.AGENDA) selectedDay else Schedule.today(tz)) },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Compromisso") },
            )
            Tab.FINANCE -> FloatingActionButton(onClick = { editingTx = TxEditRequest(null) }) { Icon(Icons.Rounded.Add, "Nova movimentação") }
            Tab.SETTINGS -> Unit
        }
    }

    val content: @Composable () -> Unit = {
        Column(Modifier.fillMaxSize()) {
            if (status.sessionExpired) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Sua sessão expirou. Entre de novo para sincronizar.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { scope.launch { repo.logout() } }) { Text("Entrar") }
                    }
                }
            }
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
                        events = data.activeEvents,
                        tz = tz,
                        now = now,
                        selected = selectedDay,
                        onSelect = { selectedDay = it },
                        onOpenEvent = { editingEvent = EventEditRequest(it, selectedDay) },
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
