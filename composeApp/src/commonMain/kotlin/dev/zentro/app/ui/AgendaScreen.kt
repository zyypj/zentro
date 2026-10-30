package dev.zentro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.zentro.shared.Event
import dev.zentro.shared.Format
import dev.zentro.shared.Person
import dev.zentro.shared.Schedule
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus

@Composable
fun AgendaScreen(
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
    val events = remember(allEvents, filter) {
        when (filter) {
            null -> allEvents
            AGENDA_MINE -> allEvents.filter { it.personId == null }
            else -> allEvents.filter { it.personId == filter }
        }
    }
    val peopleById = remember(people) { people.associateBy { it.id } }
    var month by remember { mutableStateOf(LocalDate(selected.year, selected.month, 1)) }
    val gridStart = remember(month) { month.minus(month.dayOfWeek.ordinal, DateTimeUnit.DAY) }
    val days = remember(gridStart) { (0 until 42).map { gridStart.plus(it, DateTimeUnit.DAY) } }
    val colorsByDay = remember(events, gridStart) {
        days.associateWith { day -> Schedule.agendaFor(events, day, tz).map { it.first.color }.distinct().take(3) }
    }
    val dayItems = remember(events, selected) { Schedule.agendaFor(events, selected, tz) }

    val calendar = @Composable { modifier: Modifier ->
        Column(modifier) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Format.monthYear(month), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    month = LocalDate(today.year, today.month, 1)
                    onSelect(today)
                }) { Text("Hoje") }
                IconButton(onClick = { month = month.minus(1, DateTimeUnit.MONTH) }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Mês anterior")
                }
                IconButton(onClick = { month = month.plus(1, DateTimeUnit.MONTH) }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Próximo mês")
                }
            }
            if (people.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = filter == null, onClick = { onFilter(null) }, label = { Text("Todos") })
                    FilterChip(selected = filter == AGENDA_MINE, onClick = { onFilter(AGENDA_MINE) }, label = { Text("Meus") })
                    people.forEach { p ->
                        FilterChip(
                            selected = filter == p.id,
                            onClick = { onFilter(p.id) },
                            label = { Text(p.name) },
                            leadingIcon = { StatusDot(ZColors.event(p.color)) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                repeat(7) { i ->
                    Text(
                        Format.weekdayInitial(i),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(bottom = 6.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            days.chunked(7).forEach { week ->
                Row {
                    week.forEach { day ->
                        DayCell(
                            day = day,
                            inMonth = day.month == month.month,
                            isToday = day == today,
                            isSelected = day == selected,
                            dots = colorsByDay[day].orEmpty(),
                            onClick = {
                                onSelect(day)
                                if (day.month != month.month) month = LocalDate(day.year, day.month, 1)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }

    val dayList = @Composable { modifier: Modifier ->
        ScrollableLazyColumn(modifier, contentPadding = PaddingValues(bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                SectionTitle(
                    (if (selected == today) "Hoje · " else "") + Format.longDate(selected),
                )
            }
            if (dayItems.isEmpty()) {
                item { EmptyState(Icons.Rounded.DateRange, "Nada marcado", "Toque em + para adicionar um compromisso neste dia.") }
            } else {
                items(dayItems, key = { "${it.first.id}-${it.second}" }) { (event, start) ->
                    EventRow(event, start, tz, now, peopleById[event.personId], { onTogglePaid(event, start, it) }) { onOpenEvent(event) }
                }
            }
        }
    }

    if (LocalWide.current) {
        Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            calendar(Modifier.weight(1f).widthIn(max = 520.dp))
            dayList(Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 16.dp)) {
            calendar(Modifier.fillMaxWidth())
            dayList(Modifier.fillMaxWidth().weight(1f))
        }
    }
}

/** Filtro da agenda: só os seus compromissos. */
const val AGENDA_MINE = "__me__"

@Composable
private fun DayCell(
    day: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    dots: List<Int>,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    Column(
        modifier.aspectRatio(1f).padding(2.dp).clip(MaterialTheme.shapes.small).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape)
                .background(if (isSelected) primary else Color.Transparent)
                .border(1.5.dp, if (isToday && !isSelected) primary else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    !inMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
        Row(Modifier.height(8.dp).padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            dots.forEach { StatusDot(ZColors.event(it), size = 5) }
        }
    }
}
