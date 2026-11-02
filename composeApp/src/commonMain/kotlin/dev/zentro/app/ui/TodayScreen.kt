package dev.zentro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zentro.app.data.AppData
import dev.zentro.shared.Bills
import dev.zentro.shared.Event
import dev.zentro.shared.Finance
import dev.zentro.shared.Format
import dev.zentro.shared.Note
import dev.zentro.shared.Schedule
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Dias (entre [from] e [to]) que têm algum compromisso — os pontinhos do calendário. */
fun eventDays(events: List<Event>, from: LocalDate, to: LocalDate, tz: TimeZone): Set<LocalDate> {
    val start = Schedule.startOfDay(from, tz)
    val end = Schedule.startOfDay(to.plus(1, DateTimeUnit.DAY), tz)
    return events.flatMap { e -> Schedule.occurrences(e, start, end, tz).map { Schedule.toLocal(it, tz).date } }.toSet()
}

/** Tudo o que a tela Hoje mostra, calculado uma vez. */
private class TodayModel(data: AppData, tz: TimeZone, now: Long) {
    val today = Schedule.today(tz, now)
    val tomorrow = today.plus(1, DateTimeUnit.DAY)
    private val events = data.activeEvents
    val agenda = Schedule.agendaFor(events, today, tz).filter { it.first.billAmountCents == null }
    val tomorrowAgenda = Schedule.agendaFor(events, tomorrow, tz).filter { it.first.billAmountCents == null }
    private val bills = (Bills.inMonth(events, today.minus(1, DateTimeUnit.MONTH), tz, now) + Bills.inMonth(events, today, tz, now) +
        Bills.inMonth(events, today.plus(1, DateTimeUnit.MONTH), tz, now)).filter { !it.paid }
    val overdue = bills.filter { it.overdue }
    val upcomingBills = (overdue + bills.filter { !it.overdue }).take(3)
    val recentNotes = data.activeNotes.sortedByDescending { it.updatedAt }.take(3)
    val month = Finance.month(data.activeTransactions, today, tz)
    val needsReview = Schedule.needsReview(data.settings.copy(timezone = tz.id), now)
    val greeting = when (Schedule.toLocal(now, tz).hour) {
        in 5..11 -> "Bom dia"
        in 12..17 -> "Boa tarde"
        else -> "Boa noite"
    }
    val firstName = data.session?.user?.name?.substringBefore(' ').orEmpty()
    val people = data.people
}

@Composable
fun TodayScreen(
    data: AppData,
    tz: TimeZone,
    now: Long,
    onCapture: (String) -> Boolean,
    onOpenEvent: (Event?, LocalDate, LocalTime?) -> Unit,
    onReviewed: () -> Unit,
    onOpenAgenda: (LocalDate?) -> Unit,
    onOpenFinance: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenNote: (Note) -> Unit,
    onNewNote: () -> Unit,
    onTogglePaid: (Event, Long, Boolean) -> Unit,
    bell: @Composable () -> Unit,
    onSearch: () -> Unit,
) {
    val m = remember(data, now / 60_000) { TodayModel(data, tz, now) }
    if (LocalWide.current) {
        Row(Modifier.fillMaxSize()) {
            ScrollableColumn(
                Modifier.weight(1f).fillMaxHeight(),
                Modifier.padding(start = 48.dp, end = 46.dp, top = 32.dp, bottom = 40.dp),
            ) {
                Column(Modifier.widthIn(max = 980.dp)) { DesktopMain(m, tz, now, onCapture, onOpenEvent, onReviewed, onOpenAgenda, onOpenFinance, onOpenNotes, onOpenNote) }
            }
            ScrollableColumn(
                Modifier.width(340.dp).fillMaxHeight().background(ZPalette.Aside)
                    .drawBehind { drawRect(ZPalette.Line, Offset.Zero, Size(1.dp.toPx(), size.height)) },
                Modifier.padding(start = 28.dp, end = 28.dp, top = 37.dp, bottom = 24.dp),
            ) { Aside(m, data, tz, onOpenAgenda, onOpenFinance, onNewNote) }
        }
    } else {
        ScrollableColumn(Modifier.fillMaxSize(), Modifier.padding(horizontal = 18.dp).padding(top = 14.dp, bottom = 28.dp)) {
            PhoneToday(m, tz, now, onCapture, onOpenEvent, onReviewed, onOpenAgenda, onOpenFinance, bell, onSearch)
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Desktop
// ---------------------------------------------------------------------------------------------

@Composable
private fun ColumnScope.DesktopMain(
    m: TodayModel,
    tz: TimeZone,
    now: Long,
    onCapture: (String) -> Boolean,
    onOpenEvent: (Event?, LocalDate, LocalTime?) -> Unit,
    onReviewed: () -> Unit,
    onOpenAgenda: (LocalDate?) -> Unit,
    onOpenFinance: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenNote: (Note) -> Unit,
) {
    ZText(fullDate(m.today), 12.5.sp, 600, ZPalette.Muted)
    Spacer(Modifier.height(10.dp))
    ZText(if (m.firstName.isEmpty()) "${m.greeting}." else "${m.greeting}, ${m.firstName}.", 43.sp, 700, tracking = -0.05, maxLines = 1)
    Spacer(Modifier.height(6.dp))
    ZText("Aqui está o que você tem para hoje.", 13.5.sp, 400, ZPalette.Muted)

    Eyebrow("Captura rápida", Modifier.padding(top = 25.dp, bottom = 9.dp))
    QuickCapture("Escreva um compromisso, uma anotação ou um gasto…", onCapture)
    ZText("Experimente: “reunião amanhã às 14h” ou “45,90 almoço”", 11.sp, 400, ZPalette.Quiet, Modifier.padding(top = 7.dp, start = 2.dp))

    if (m.needsReview) {
        ReviewStrip("Revise sua agenda", "Confira se está tudo certo para hoje.", "Marcar como revisada", onReviewed, Modifier.padding(top = 18.dp))
    }

    val showTomorrow = m.agenda.isEmpty() && m.tomorrowAgenda.isNotEmpty()
    val list = if (showTomorrow) m.tomorrowAgenda else m.agenda
    val day = if (showTomorrow) m.tomorrow else m.today
    SectionHeader(
        if (showTomorrow) "Amanhã na sua agenda" else "Na sua agenda",
        Modifier.padding(top = 29.dp, bottom = 17.dp),
        count = "${list.size} compromisso${if (list.size == 1) "" else "s"}",
        action = "Ver agenda completa",
        onAction = { onOpenAgenda(null) },
    )
    if (list.isEmpty()) {
        ZText("Nada marcado para hoje. Escreva na captura rápida para adicionar algo.", 12.5.sp, 400, ZPalette.Muted, Modifier.padding(bottom = 8.dp))
    }
    list.forEachIndexed { i, (event, start) ->
        val person = m.people[event.personId]?.takeIf { !it.deleted }
        TimelineRow(
            time = if (event.allDay) "Dia todo" else Format.time(Schedule.toLocal(start, tz)),
            title = event.title,
            subtitle = eventDetails(event, start, now, person.takeIf { event.category.isNotEmpty() }),
            dotColor = eventDot(event),
            last = i == list.lastIndex,
            onClick = { onOpenEvent(event, day, null) },
            tag = { EventTag(event, person) },
        )
    }

    Spacer(Modifier.height(26.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
        Column(Modifier.weight(1f)) {
            SmallHead("Próximas contas", "Ver todas", onOpenFinance)
            if (m.upcomingBills.isEmpty()) EmptyLine("Nenhuma conta para pagar agora.")
            m.upcomingBills.forEach { bill ->
                val due = Schedule.toLocal(bill.due, tz).date
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenEvent(bill.event, due, null) }
                        .drawBehind { drawRect(ZPalette.SoftLine, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx())) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    DateBox(due, highlight = if (bill.overdue) ZColors.Expense else null)
                    Column(Modifier.weight(1f)) {
                        ZText(bill.event.title, 12.5.sp, 600, maxLines = 1)
                        Spacer(Modifier.height(4.dp))
                        ZText(
                            if (bill.overdue) dueText(due, m.today) else recurrenceText(bill.event),
                            10.5.sp, 400, if (bill.overdue) ZColors.Expense else ZPalette.Quiet, maxLines = 1,
                        )
                    }
                    ZText(Format.money(bill.event.billAmountCents ?: 0), 12.5.sp, 700, tabular = true)
                }
            }
        }
        Column(Modifier.weight(1f)) {
            SmallHead("Anotações recentes", "Abrir notas", onOpenNotes)
            if (m.recentNotes.isEmpty()) EmptyLine("Nenhuma anotação ainda.")
            m.recentNotes.take(2).forEachIndexed { i, note ->
                NoteLine(note, i, now, tz) { onOpenNote(note) }
            }
        }
    }
}

@Composable
private fun SmallHead(title: String, action: String, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().drawBehind { drawRect(ZPalette.Line, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx())) }
            .padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZText(title, 14.sp, 700, tracking = -0.01, modifier = Modifier.weight(1f))
        LinkAction(action, onAction)
    }
}

@Composable
private fun EmptyLine(text: String) {
    ZText(text, 12.sp, 400, ZPalette.Quiet, Modifier.padding(vertical = 14.dp))
}

/** Uma anotação na lista "recentes": ícone, título, pasta e quando foi editada. */
@Composable
fun NoteLine(note: Note, index: Int, now: Long, tz: TimeZone, onClick: () -> Unit) {
    val warm = index % 2 == 0
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .drawBehind { drawRect(ZPalette.SoftLine, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx())) }
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconTile(
            Line.Note,
            if (warm) Color(0xFF2A2518) else Color(0xFF1F2030),
            if (warm) ZPalette.WarmAccent else Color(0xFF9A9AD0),
            Modifier.size(width = 31.dp, height = 35.dp),
        )
        Column(Modifier.weight(1f)) {
            ZText(note.title.ifEmpty { "Sem título" }, 12.5.sp, 650, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            ZText("${note.folder} · ${editedText(note.updatedAt, now, tz)}", 10.5.sp, 400, ZPalette.Quiet, maxLines = 1)
        }
        Icon(Line.ChevronRight, null, tint = ZPalette.Quiet, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun ColumnScope.Aside(
    m: TodayModel,
    data: AppData,
    tz: TimeZone,
    onOpenAgenda: (LocalDate?) -> Unit,
    onOpenFinance: () -> Unit,
    onNewNote: () -> Unit,
) {
    var month by remember { mutableStateOf(Finance.firstOfMonth(m.today)) }
    val days = remember(data.events, month) {
        eventDays(data.activeEvents, month.minus(7, DateTimeUnit.DAY), Finance.addMonths(month, 1).plus(7, DateTimeUnit.DAY), tz)
    }
    MonthCalendar(month, m.today, null, { it in days }, { onOpenAgenda(it) }, { month = Finance.addMonths(month, it) })

    HLine(Modifier.padding(vertical = 28.dp), ZPalette.Line)

    Row(verticalAlignment = Alignment.CenterVertically) {
        ZText("Finanças do mês", 14.sp, 700, tracking = -0.015, modifier = Modifier.weight(1f))
        ZText(Format.monthName(m.today.month).replaceFirstChar { it.uppercase() }, 11.sp, 400, ZPalette.Quiet)
    }
    Spacer(Modifier.height(22.dp))
    ZText("Saldo atual", 11.sp, 400, ZPalette.Muted)
    Spacer(Modifier.height(7.dp))
    ZText(
        Format.money(m.month.closing), 29.sp, 700,
        if (m.month.closing < 0) ZColors.Expense else ZPalette.Ink, tracking = -0.045, tabular = true, maxLines = 1,
    )
    Spacer(Modifier.height(4.dp))
    ZText("Atualizado com seus lançamentos", 10.sp, 400, ZPalette.Quiet)
    Spacer(Modifier.height(20.dp))
    MoneyPair(m.month.income, m.month.expense)
    Spacer(Modifier.height(22.dp))
    LinkAction("Abrir finanças", onOpenFinance)

    HLine(Modifier.padding(vertical = 28.dp), ZPalette.Line)
    IdeaCard(onNewNote)
}

/** Entradas e saídas lado a lado, com a bolinha colorida. */
@Composable
fun MoneyPair(income: Long, expense: Long, modifier: Modifier = Modifier, topLine: Boolean = true) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf(Triple("Entradas", income, Color(0xFF83B799)), Triple("Saídas", expense, Color(0xFFD79B8D))).forEach { (label, cents, dot) ->
            Column(
                Modifier.weight(1f).let {
                    if (topLine) it.drawBehind { drawRect(ZPalette.Line, Offset.Zero, Size(size.width, 1.dp.toPx())) }.padding(top = 13.dp) else it
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
                    ZText(label, 10.5.sp, 400, ZPalette.Muted)
                }
                Spacer(Modifier.height(8.dp))
                ZText(Format.money(cents), 14.sp, 700, tracking = -0.025, tabular = true, maxLines = 1)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Celular
// ---------------------------------------------------------------------------------------------

@Composable
private fun ColumnScope.PhoneToday(
    m: TodayModel,
    tz: TimeZone,
    now: Long,
    onCapture: (String) -> Boolean,
    onOpenEvent: (Event?, LocalDate, LocalTime?) -> Unit,
    onReviewed: () -> Unit,
    onOpenAgenda: (LocalDate?) -> Unit,
    onOpenFinance: () -> Unit,
    bell: @Composable () -> Unit,
    onSearch: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        ZentroWordmark(26.dp, 19, lowercase = false)
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onSearch), contentAlignment = Alignment.Center) {
            Icon(Line.Search, "Buscar", tint = ZPalette.Muted, modifier = Modifier.size(19.dp))
        }
        bell()
    }
    Spacer(Modifier.height(18.dp))
    ZText(fullDate(m.today), 12.sp, 500, ZPalette.Muted)
    Spacer(Modifier.height(4.dp))
    ZText(if (m.firstName.isEmpty()) "${m.greeting}." else "${m.greeting}, ${m.firstName}.", 29.sp, 700, tracking = -0.045)
    Spacer(Modifier.height(4.dp))
    ZText("Aqui está o que você tem para hoje.", 12.5.sp, 400, ZPalette.Muted)

    ZText("Captura rápida", 14.sp, 700, modifier = Modifier.padding(top = 22.dp, bottom = 9.dp))
    QuickCapture("Escreva um compromisso, nota ou gasto…", onCapture, compact = true)

    if (m.needsReview) {
        ReviewStrip("Revise sua agenda", "Confira se está tudo certo para hoje.", "Revisada", onReviewed, Modifier.padding(top = 14.dp))
    }

    Spacer(Modifier.height(14.dp))
    Panel(Modifier.fillMaxWidth(), onClick = { onOpenAgenda(null) }, bg = ZPalette.PurpleSoft, border = ZPalette.PurpleLine, padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(Line.Calendar, Color(0xFF2E2850), ZPalette.PurpleText, size = 34.dp, iconSize = 17.dp)
            Column(Modifier.weight(1f)) {
                ZText("Hoje na agenda", 13.5.sp, 650)
                ZText("${m.agenda.size} compromisso${if (m.agenda.size == 1) "" else "s"}", 11.sp, 400, ZPalette.Muted)
            }
            Icon(Line.ChevronRight, null, tint = ZPalette.Muted, modifier = Modifier.size(16.dp))
        }
    }

    SectionHeader("Próximos compromissos", Modifier.padding(top = 22.dp, bottom = 14.dp), action = "Ver agenda", onAction = { onOpenAgenda(null) }, size = 15.sp)
    val upcoming = m.agenda.filter { (e, start) -> e.allDay || start >= now - 30 * 60_000 }.ifEmpty { m.tomorrowAgenda }
    val upcomingDay = if (m.agenda.any { (e, start) -> e.allDay || start >= now - 30 * 60_000 }) m.today else m.tomorrow
    if (upcoming.isEmpty()) ZText("Nada marcado. Use a captura rápida acima.", 12.sp, 400, ZPalette.Muted)
    upcoming.take(4).forEachIndexed { i, (event, start) ->
        val person = m.people[event.personId]?.takeIf { !it.deleted }
        TimelineRow(
            time = if (event.allDay) "Dia" else Format.time(Schedule.toLocal(start, tz)),
            title = event.title,
            subtitle = eventDetails(event, start, now).substringBefore(" · Lembrete").substringBefore(" · Sem"),
            dotColor = eventDot(event),
            last = i == minOf(upcoming.size, 4) - 1,
            onClick = { onOpenEvent(event, upcomingDay, null) },
            tag = { EventTag(event, person) },
            timeWidth = 52.dp,
            inlineTag = true,
        )
    }

    if (m.overdue.isNotEmpty()) {
        SectionHeader(
            "Contas em atraso", Modifier.padding(top = 22.dp, bottom = 12.dp),
            count = m.overdue.size.toString(), countBg = ZColors.Expense.copy(alpha = 0.16f), countColor = ZColors.Expense,
            action = "Ver todas", onAction = onOpenFinance, size = 15.sp,
        )
        Panel(Modifier.fillMaxWidth(), padding = 0.dp) {
            Column {
                m.overdue.forEachIndexed { i, bill ->
                    val due = Schedule.toLocal(bill.due, tz).date
                    if (i > 0) HLine(Modifier.padding(horizontal = 14.dp))
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenEvent(bill.event, due, null) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        IconTile(Line.Repeat, ZPalette.CardHigh, ZPalette.Muted, size = 34.dp, iconSize = 17.dp)
                        Column(Modifier.weight(1f)) {
                            ZText(bill.event.title, 13.sp, 650, maxLines = 1)
                            ZText(dueText(due, m.today), 11.sp, 400, ZColors.Expense, maxLines = 1)
                        }
                        ZText(Format.money(bill.event.billAmountCents ?: 0), 13.sp, 700, tabular = true)
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    Panel(Modifier.fillMaxWidth(), onClick = onOpenFinance, padding = 16.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(Line.Chart, ZPalette.CardHigh, ZPalette.PurpleText, size = 34.dp, iconSize = 17.dp)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ZText("Finanças do mês", 13.5.sp, 650, modifier = Modifier.weight(1f))
                    ZText(Format.monthName(m.today.month).replaceFirstChar { it.uppercase() }, 11.sp, 400, ZPalette.Quiet)
                }
                Spacer(Modifier.height(4.dp))
                ZText(Format.money(m.month.closing), 22.sp, 700, if (m.month.closing < 0) ZColors.Expense else ZPalette.Ink, tracking = -0.04, tabular = true)
                Spacer(Modifier.height(10.dp))
                MoneyPair(m.month.income, m.month.expense, topLine = false)
            }
        }
    }
}
