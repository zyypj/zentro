package dev.zentro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.zentro.app.data.AppData
import dev.zentro.shared.Bills
import dev.zentro.shared.Cards
import dev.zentro.shared.CreditCard
import dev.zentro.shared.Event
import dev.zentro.shared.Format
import dev.zentro.shared.Note
import dev.zentro.shared.Notes
import dev.zentro.shared.Schedule
import dev.zentro.shared.Transaction
import dev.zentro.shared.TxType
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.minus

// ---------------------------------------------------------------------------------------------
// Avisos (sininho)
// ---------------------------------------------------------------------------------------------

sealed interface AlertTarget {
    data object Finance : AlertTarget
    data class Card(val cardId: String) : AlertTarget
    data object Review : AlertTarget
    data class EventAt(val event: Event, val date: LocalDate) : AlertTarget
}

data class AlertItem(
    val key: String,
    val title: String,
    val subtitle: String,
    val color: Color,
    val icon: ImageVector,
    val target: AlertTarget,
    /** Só informativo (próximo compromisso): não acende a bolinha do sino. */
    val info: Boolean = false,
)

/** Tudo o que pede atenção agora: contas e faturas atrasadas ou vencendo, revisão do dia e o próximo compromisso. */
fun buildAlerts(data: AppData, tz: TimeZone, now: Long): List<AlertItem> {
    val today = Schedule.today(tz, now)
    val events = data.activeEvents
    val txs = data.activeTransactions
    val alerts = mutableListOf<AlertItem>()

    val bills = Bills.inMonth(events, today.minus(1, DateTimeUnit.MONTH), tz, now) + Bills.inMonth(events, today, tz, now)
    bills.filter { !it.paid }.forEach { bill ->
        val due = Schedule.toLocal(bill.due, tz).date
        val days = today.daysUntil(due)
        val amount = Format.money(bill.event.billAmountCents ?: 0)
        when {
            bill.overdue -> alerts += AlertItem(
                "bill-${bill.event.id}-${bill.due}", "Conta atrasada: ${bill.event.title}",
                "Venceu ${Format.shortDate(due)} · $amount", ZColors.Expense, Line.Alert, AlertTarget.Finance,
            )
            days in 0..1 -> alerts += AlertItem(
                "bill-${bill.event.id}-${bill.due}", "${if (days == 0) "Vence hoje" else "Vence amanhã"}: ${bill.event.title}",
                amount, ZColors.Warning, Line.Clock, AlertTarget.Finance,
            )
        }
    }

    data.activeCards.forEach { card ->
        val invoice = Cards.current(card, txs, today, tz)
        val days = today.daysUntil(invoice.due)
        when (invoice.status(today)) {
            Cards.Status.OVERDUE -> alerts += AlertItem(
                "inv-${invoice.key}", "Fatura ${card.name} atrasada",
                "Venceu ${Format.shortDate(invoice.due)} · ${Format.money(invoice.totalCents)}", ZColors.Expense, Line.Card, AlertTarget.Card(card.id),
            )
            Cards.Status.CLOSED -> if (days <= 3 && invoice.totalCents > 0) alerts += AlertItem(
                "inv-${invoice.key}", "Fatura ${card.name} vence ${if (days == 0) "hoje" else "em $days dia(s)"}",
                "${Format.shortDate(invoice.due)} · ${Format.money(invoice.totalCents)}", ZColors.Warning, Line.Card, AlertTarget.Card(card.id),
            )
            else -> Unit
        }
    }

    if (Schedule.needsReview(data.settings.copy(timezone = tz.id), now)) {
        alerts += AlertItem("review", "Revise sua agenda", "Confira se está tudo certo para hoje.", ZPalette.PurpleText, Line.Check, AlertTarget.Review)
    }

    Schedule.agendaFor(events, today, tz).firstOrNull { (e, start) -> start >= now && e.billAmountCents == null }?.let { (event, start) ->
        alerts += AlertItem(
            "next-${event.id}-$start", "Próximo: ${event.title}",
            "Hoje às ${Format.time(Schedule.toLocal(start, tz))}", Color(0xFF8C7CDD), Line.Calendar, AlertTarget.EventAt(event, today), info = true,
        )
    }
    return alerts
}

/** Painel de avisos que abre ao tocar no sino. */
@Composable
fun AlertsMenu(expanded: Boolean, alerts: List<AlertItem>, onDismiss: () -> Unit, onOpen: (AlertTarget) -> Unit) {
    DropdownMenu(expanded, onDismiss, containerColor = ZPalette.CardHigh, modifier = Modifier.width(340.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            ZText("Avisos", 14.sp, 700)
            Spacer(Modifier.height(8.dp))
            if (alerts.isEmpty()) {
                ZText("Nada pendente. Está tudo em dia.", 12.sp, 400, ZPalette.Muted, Modifier.padding(vertical = 10.dp))
            }
            alerts.forEach { a ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                        onDismiss()
                        onOpen(a.target)
                    }.padding(vertical = 9.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconTile(a.icon, a.color.copy(alpha = 0.14f), a.color, size = 32.dp)
                    Column(Modifier.weight(1f)) {
                        ZText(a.title, 12.5.sp, 600, maxLines = 1)
                        ZText(a.subtitle, 11.sp, 400, ZPalette.Muted, maxLines = 1)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Busca global (Ctrl+K)
// ---------------------------------------------------------------------------------------------

sealed interface SearchHit {
    val title: String
    val subtitle: String
    val icon: ImageVector

    data class EventHit(val event: Event, override val title: String, override val subtitle: String) : SearchHit {
        override val icon get() = Line.Calendar
    }
    data class NoteHit(val note: Note, override val title: String, override val subtitle: String) : SearchHit {
        override val icon get() = Line.Note
    }
    data class TxHit(val tx: Transaction, override val title: String, override val subtitle: String) : SearchHit {
        override val icon get() = if (tx.type == TxType.INCOME) Line.TrendUp else Line.TrendDown
    }
    data class CardHit(val card: CreditCard, override val title: String, override val subtitle: String) : SearchHit {
        override val icon get() = Line.Card
    }
}

private val accents = mapOf(
    'á' to 'a', 'à' to 'a', 'â' to 'a', 'ã' to 'a', 'ä' to 'a', 'é' to 'e', 'ê' to 'e', 'è' to 'e', 'í' to 'i', 'ì' to 'i',
    'ó' to 'o', 'ô' to 'o', 'õ' to 'o', 'ò' to 'o', 'ú' to 'u', 'ü' to 'u', 'ù' to 'u', 'ç' to 'c',
)

/** Minúsculas e sem acento, para "reuniao" achar "Reunião". */
fun fold(text: String): String = text.lowercase().map { accents[it] ?: it }.joinToString("")

/** Procura em compromissos, notas, movimentações e cartões. */
fun searchEverything(data: AppData, query: String, tz: TimeZone): List<Pair<String, List<SearchHit>>> {
    val q = fold(query.trim())
    if (q.isEmpty()) return emptyList()
    val words = q.split(' ').filter { it.isNotBlank() }
    fun matches(vararg fields: String): Boolean {
        val hay = fold(fields.joinToString(" "))
        return words.all { it in hay }
    }
    val events = data.activeEvents.filter { matches(it.title, it.notes, it.category) }
        .sortedByDescending { it.startAt }.take(6)
        .map { e ->
            val d = Schedule.toLocal(e.startAt, tz)
            SearchHit.EventHit(e, e.title, listOfNotNull(Format.shortDate(d.date) + "/" + d.date.year, e.category.ifEmpty { null }).joinToString(" · "))
        }
    val notes = Notes.search(data.activeNotes, query).take(6).map {
        SearchHit.NoteHit(it.note, it.note.title.ifEmpty { "Sem título" }, it.note.folder + " · " + it.snippet.take(80))
    }
    val txs = data.activeTransactions.filter { matches(it.description, it.category) }
        .sortedByDescending { it.date }.take(6)
        .map { t ->
            SearchHit.TxHit(t, t.description, Format.shortDate(Schedule.toLocal(t.date, tz).date) + " · " + Format.money(if (t.type == TxType.INCOME) t.amountCents else -t.amountCents, signed = true))
        }
    val cards = data.activeCards.filter { matches(it.name) }.map { SearchHit.CardHit(it, it.name, "Cartão · vence dia ${it.dueDay}") }
    return listOf("Compromissos" to events, "Notas" to notes, "Movimentações" to txs, "Cartões" to cards).filter { it.second.isNotEmpty() }
}

@Composable
fun SearchDialog(data: AppData, tz: TimeZone, onDismiss: () -> Unit, onOpen: (SearchHit) -> Unit) {
    var query by remember { mutableStateOf("") }
    var cursor by remember { mutableStateOf(0) }
    val groups = remember(query, data) { searchEverything(data, query, tz) }
    val flat = groups.flatMap { it.second }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(query) { cursor = 0 }

    fun open(hit: SearchHit) {
        onDismiss()
        onOpen(hit)
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().clickable(onClick = onDismiss), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.padding(top = 90.dp, start = 16.dp, end = 16.dp).widthIn(max = 620.dp).fillMaxWidth().imePadding()
                    .clip(RoundedCornerShape(14.dp)).background(ZPalette.CardHigh).border(1.dp, ZPalette.CardHighest, RoundedCornerShape(14.dp))
                    .clickable(enabled = false) {},
            ) {
                Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Line.Search, null, tint = ZPalette.Muted, modifier = Modifier.size(18.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it.replace("\n", "") },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = InterFamily, fontSize = 15.sp, color = ZPalette.Ink),
                        cursorBrush = SolidColor(ZPalette.Purple),
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp).focusRequester(focus).onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (e.key) {
                                Key.DirectionDown -> { if (flat.isNotEmpty()) cursor = (cursor + 1) % flat.size; true }
                                Key.DirectionUp -> { if (flat.isNotEmpty()) cursor = (cursor - 1 + flat.size) % flat.size; true }
                                Key.Enter, Key.NumPadEnter -> { flat.getOrNull(cursor)?.let(::open); true }
                                Key.Escape -> { onDismiss(); true }
                                else -> false
                            }
                        },
                        decorationBox = { inner ->
                            Box {
                                if (query.isEmpty()) ZText("Buscar compromissos, notas, gastos…", 15.sp, 400, ZPalette.Quiet, maxLines = 1)
                                inner()
                            }
                        },
                    )
                    Kbd("Esc")
                }
                HLine(color = ZPalette.CardHighest)
                Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()).padding(8.dp)) {
                    when {
                        query.isBlank() -> ZText("Digite para procurar em tudo do Zentro.", 12.sp, 400, ZPalette.Muted, Modifier.padding(12.dp))
                        flat.isEmpty() -> ZText("Nada encontrado para \"$query\".", 12.sp, 400, ZPalette.Muted, Modifier.padding(12.dp))
                    }
                    var index = 0
                    groups.forEach { (label, hits) ->
                        Eyebrow(label, Modifier.padding(start = 10.dp, top = 10.dp, bottom = 4.dp), ZPalette.Quiet)
                        hits.forEach { hit ->
                            val i = index++
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                    .background(if (i == cursor) ZPalette.PurpleSoft else Color.Transparent)
                                    .clickable { open(hit) }.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(hit.icon, null, tint = if (i == cursor) ZPalette.PurpleText else ZPalette.Muted, modifier = Modifier.size(17.dp))
                                Column(Modifier.weight(1f)) {
                                    ZText(hit.title, 13.sp, 600, maxLines = 1)
                                    ZText(hit.subtitle, 11.sp, 400, ZPalette.Muted, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
