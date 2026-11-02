package dev.zentro.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.zentro.shared.Event
import dev.zentro.shared.EventCategories
import dev.zentro.shared.Finance
import dev.zentro.shared.Format
import dev.zentro.shared.Person
import dev.zentro.shared.Schedule
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Filtro da agenda: só os seus compromissos. */
const val AGENDA_MINE = "__me__"

/** Filtro da agenda por categoria ("cat:Trabalho"). */
private const val CATEGORY_PREFIX = "cat:"

private val billTag = TagColors(Color(0xFF2A2417), Color(0xFFD9B76F), ZColors.Warning)
private val paidTag = TagColors(Color(0xFF1A2821), Color(0xFF8CC0A0), ZColors.Income)

fun filterEvents(events: List<Event>, filter: String?): List<Event> = when {
    filter == null -> events
    filter == AGENDA_MINE -> events.filter { it.personId == null }
    filter.startsWith(CATEGORY_PREFIX) -> events.filter { it.category == filter.removePrefix(CATEGORY_PREFIX) }
    else -> events.filter { it.personId == filter }
}

@Composable
fun AgendaScreen(
    onNew: () -> Unit,
    allEvents: List<Event>,
    people: List<Person>,
    filter: String?,
    onFilter: (String?) -> Unit,
    tz: TimeZone,
    now: Long,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onOpenEvent: (Event) -> Unit,
    onTogglePaid: (Event, Long, Boolean) -> Unit,
) {
    val today = Schedule.today(tz, now)
    val events = remember(allEvents, filter) { filterEvents(allEvents, filter) }
    val peopleById = remember(people) { people.associateBy { it.id } }
    var month by remember { mutableStateOf(Finance.firstOfMonth(selected)) }
    var week by remember { mutableStateOf(false) }
    val marked = remember(events, month) {
        eventDays(events, month.minus(7, DateTimeUnit.DAY), Finance.addMonths(month, 1).plus(7, DateTimeUnit.DAY), tz)
    }

    fun select(day: LocalDate) {
        onSelect(day)
        if (day.month != month.month || day.year != month.year) month = Finance.firstOfMonth(day)
    }

    val calendar = @Composable {
        Panel(Modifier.fillMaxWidth(), padding = 16.dp) {
            MonthCalendar(month, today, selected, { it in marked }, ::select, { month = Finance.addMonths(month, it) }, rowHeight = 34.dp)
        }
    }

    val filters = @Composable {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterPill("Todos", filter == null, { onFilter(null) })
            EventCategories.all.forEach { c ->
                val key = CATEGORY_PREFIX + c
                FilterPill(c, filter == key, { onFilter(if (filter == key) null else key) }, icon = categoryIcon(c), iconTint = categoryColors(c).text)
            }
            if (people.isNotEmpty()) {
                FilterPill("Meus", filter == AGENDA_MINE, { onFilter(if (filter == AGENDA_MINE) null else AGENDA_MINE) }, icon = Line.User)
                people.forEach { p ->
                    FilterPill(p.name, filter == p.id, { onFilter(if (filter == p.id) null else p.id) }, icon = Line.User, iconTint = ZColors.event(p.color))
                }
            }
        }
    }

    val weekCard = @Composable {
        Panel(Modifier.fillMaxWidth(), onClick = { week = !week }, bg = ZPalette.PurpleSoft, border = ZPalette.PurpleLine, padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(Line.Calendar, Color(0xFF2E2850), ZPalette.PurpleText, size = 34.dp, iconSize = 17.dp)
                Column(Modifier.weight(1f)) {
                    ZText(if (week) "Ver só o dia" else "Ver semana", 13.5.sp, 650)
                    ZText(
                        if (week) "Volte para os compromissos do dia escolhido." else "Confira os próximos dias da sua agenda.",
                        11.sp, 400, ZPalette.Muted,
                    )
                }
                Icon(Line.ChevronRight, null, tint = ZPalette.Muted, modifier = Modifier.size(16.dp))
            }
        }
    }

    val dayList: @Composable ColumnScope.() -> Unit = {
        val days = if (week) (0 until 7).map { selected.plus(it, DateTimeUnit.DAY) } else listOf(selected)
        days.forEachIndexed { index, day ->
            val items = Schedule.agendaFor(events, day, tz)
            if (week && items.isEmpty()) return@forEachIndexed
            Row(Modifier.fillMaxWidth().padding(top = if (index == 0) 0.dp else 22.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                ZText(dayTitle(day, today), 15.sp, 700, tracking = -0.015, modifier = Modifier.weight(1f), maxLines = 1)
                ZText("${items.size} compromisso${if (items.size == 1) "" else "s"}", 11.sp, 400, ZPalette.Quiet)
            }
            if (items.isEmpty()) {
                ZText("Nada marcado neste dia.", 12.5.sp, 400, ZPalette.Muted, Modifier.padding(bottom = 8.dp))
                LinkAction("Adicionar compromisso", onNew)
            }
            items.forEachIndexed { i, (event, start) ->
                val person = peopleById[event.personId]
                val isBill = event.billAmountCents != null
                val paid = isBill && start in event.paid
                TimelineRow(
                    time = if (event.allDay) "Dia todo" else Format.time(Schedule.toLocal(start, tz)),
                    title = event.title,
                    subtitle = if (isBill) "Conta · ${Format.money(event.billAmountCents ?: 0)}" else eventDetails(event, start, now).substringBefore(" · Lembrete").substringBefore(" · Sem"),
                    dotColor = if (isBill) ZColors.Warning else eventDot(event),
                    last = i == items.lastIndex,
                    onClick = { onOpenEvent(event) },
                    inlineTag = !LocalWide.current,
                    titleColor = if (paid) ZPalette.Muted else ZPalette.Ink,
                    tag = {
                        if (isBill) {
                            Tag(
                                if (paid) "Paga ✓" else "Marcar paga",
                                if (paid) paidTag else billTag,
                                Modifier.clip(RoundedCornerShape(5.dp)).clickable { onTogglePaid(event, start, !paid) },
                            )
                        } else {
                            EventTag(event, person)
                        }
                    },
                )
            }
        }
        if (week && days.all { Schedule.agendaFor(events, it, tz).isEmpty() }) {
            ZText("Nada marcado nos próximos 7 dias.", 12.5.sp, 400, ZPalette.Muted)
        }
    }

    if (LocalWide.current) {
        Row(Modifier.fillMaxSize()) {
            ScrollableColumn(
                Modifier.width(420.dp).fillMaxHeight(),
                Modifier.padding(start = 48.dp, end = 20.dp, top = 32.dp, bottom = 32.dp),
            ) {
                calendar()
                Spacer(Modifier.height(16.dp))
                filters()
                Spacer(Modifier.height(16.dp))
                weekCard()
            }
            ScrollableColumn(
                Modifier.weight(1f).fillMaxHeight(),
                Modifier.padding(start = 28.dp, end = 46.dp, top = 36.dp, bottom = 40.dp),
            ) {
                Column(Modifier.widthIn(max = 760.dp)) { dayList() }
            }
        }
    } else {
        ScrollableColumn(Modifier.fillMaxSize(), Modifier.padding(bottom = 24.dp)) {
            MobileHeader("Agenda") { SquarePlus(onNew, "Novo compromisso") }
            Column(Modifier.padding(horizontal = 18.dp)) {
                calendar()
                Spacer(Modifier.height(14.dp))
                filters()
                Spacer(Modifier.height(20.dp))
                dayList()
                Spacer(Modifier.height(20.dp))
                weekCard()
            }
        }
    }
}

private fun dayTitle(day: LocalDate, today: LocalDate): String {
    val base = fullDate(day).substringBeforeLast(" de ")
    return when (day) {
        today -> "Hoje · $base"
        today.plus(1, DateTimeUnit.DAY) -> "Amanhã · $base"
        else -> base
    }
}
