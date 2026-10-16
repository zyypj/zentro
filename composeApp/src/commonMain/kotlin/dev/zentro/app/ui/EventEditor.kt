package dev.zentro.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.zentro.app.data.newId
import dev.zentro.shared.Event
import dev.zentro.shared.Format
import dev.zentro.shared.Person
import dev.zentro.shared.Recurrence
import dev.zentro.shared.Schedule
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

data class EventEditRequest(
    val event: Event?,
    val date: LocalDate,
    val time: LocalTime? = null,
    val title: String = "",
    val personId: String? = null,
)

private val reminderOptions = listOf(0, 5, 15, 30, 60, 120, 1440)
private val durationOptions = listOf(0 to "Sem duração", 30 to "30 min", 60 to "1 h", 120 to "2 h", 240 to "4 h")
private val recurrenceOptions = listOf(
    Recurrence.NONE to "Não repete",
    Recurrence.DAILY to "Todo dia",
    Recurrence.WEEKDAYS to "Dias úteis",
    Recurrence.WEEKLY to "Toda semana",
    Recurrence.MONTHLY to "Todo mês",
    Recurrence.YEARLY to "Todo ano",
)

/** Compromissos "dia todo" avisam com base neste horário. */
private val ALL_DAY_TIME = LocalTime(8, 0)

private fun reminderLabel(minutes: Int) = when (minutes) {
    0 -> "Na hora"
    1440 -> "1 dia antes"
    else -> if (minutes >= 60) "${minutes / 60} h antes" else "$minutes min"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventEditor(
    request: EventEditRequest,
    tz: TimeZone,
    people: List<Person>,
    onAddPerson: (String) -> Person,
    onSave: (Event) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val original = request.event
    val startLocal = original?.let { Schedule.toLocal(it.startAt, tz) }

    var title by remember { mutableStateOf(original?.title ?: request.title) }
    var notes by remember { mutableStateOf(original?.notes ?: "") }
    var date by remember { mutableStateOf(startLocal?.date ?: request.date) }
    var time by remember { mutableStateOf(startLocal?.time ?: request.time ?: defaultTime(tz, request.date)) }
    var allDay by remember { mutableStateOf(original?.allDay ?: false) }
    var duration by remember {
        mutableStateOf(original?.endAt?.let { ((it - original.startAt) / 60_000).toInt() } ?: 60)
    }
    var reminders by remember { mutableStateOf(original?.reminders?.toSet() ?: setOf(15, 0)) }
    var critical by remember { mutableStateOf(original?.critical ?: false) }
    var recurrence by remember { mutableStateOf(original?.recurrence ?: Recurrence.NONE) }
    var color by remember { mutableStateOf(original?.color ?: 0) }
    var personId by remember { mutableStateOf(original?.personId ?: request.personId) }
    var addingPerson by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { if (original == null) runCatching { focus.requestFocus() } }

    fun save() {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            error = "Dê um nome ao compromisso"
            return
        }
        val startTime = if (allDay) ALL_DAY_TIME else time
        val start = Schedule.toMillis(LocalDateTime(date, startTime), tz)
        val end = if (!allDay && duration > 0) start + duration * 60_000L else null
        onSave(
            Event(
                id = original?.id ?: newId(),
                title = cleanTitle,
                notes = notes.trim(),
                startAt = start,
                endAt = end,
                allDay = allDay,
                reminders = reminders.sortedDescending(),
                critical = critical,
                recurrence = recurrence,
                color = color,
                personId = personId,
            ),
        )
        onDismiss()
    }

    AdaptiveSheet(onDismiss) {
        Text(if (original == null) "Novo compromisso" else "Editar compromisso", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it; error = null },
            label = { Text("O que é?") },
            singleLine = true,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )

        ChipGroup("De quem é?") {
            FilterChip(selected = personId == null, onClick = { personId = null }, label = { Text("Meu") })
            people.forEach { person ->
                FilterChip(
                    selected = personId == person.id,
                    onClick = { personId = person.id },
                    label = { Text(person.name) },
                    leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(ZColors.event(person.color))) },
                )
            }
            AssistChip(onClick = { addingPerson = true }, label = { Text("Pessoa") }, leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(16.dp)) })
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1.4f)) {
                LabeledRow("Data", Format.shortDate(date) + "  ·  " + Format.weekday(date.dayOfWeek).take(3), { showDate = true }, Icons.Rounded.DateRange)
            }
            if (!allDay) {
                Box(Modifier.weight(1f)) {
                    LabeledRow("Hora", Format.time(LocalDateTime(date, time)), { showTime = true }, ZIcons.Alarm)
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Dia todo", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = allDay, onCheckedChange = { allDay = it })
        }

        if (!allDay) {
            ChipGroup("Duração") {
                durationOptions.forEach { (minutes, label) ->
                    FilterChip(selected = duration == minutes, onClick = { duration = minutes }, label = { Text(label) })
                }
            }
        }

        ChipGroup(if (allDay) "Avisar (a partir das 08:00)" else "Avisar") {
            reminderOptions.forEach { minutes ->
                val selected = minutes in reminders
                FilterChip(
                    selected = selected,
                    onClick = { reminders = if (selected) reminders - minutes else reminders + minutes },
                    label = { Text(reminderLabel(minutes)) },
                    leadingIcon = if (selected) ({ Icon(Icons.Rounded.Check, null, Modifier.size(16.dp)) }) else null,
                )
            }
        }
        if (reminders.isEmpty()) {
            Text(
                "Sem avisos: este compromisso não vai tocar.",
                color = ZColors.Warning,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                .background(if (critical) ZColors.Expense.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable { critical = !critical }.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Warning, null, tint = if (critical) ZColors.Expense else MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text("Compromisso crítico", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Toca como despertador, em tela cheia, até você confirmar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = critical, onCheckedChange = { critical = it })
        }

        ChipGroup("Repetir") {
            recurrenceOptions.forEach { (value, label) ->
                FilterChip(selected = recurrence == value, onClick = { recurrence = value }, label = { Text(label) })
            }
        }

        Text("Cor", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ZColors.events.forEachIndexed { index, c ->
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(c)
                        .border(3.dp, if (color == index) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                        .clickable { color = index },
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Anotações (opcional)") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (original != null) {
                OutlinedButton(onClick = { onDelete(original.id); onDismiss() }) {
                    Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Excluir")
                }
            }
            Button(onClick = ::save, modifier = Modifier.weight(1f).height(48.dp)) { Text("Salvar") }
        }
    }

    if (showDate) DatePickerModal(date, { date = it }, { showDate = false })
    if (showTime) TimePickerModal(time, { time = it }, { showTime = false })
    if (addingPerson) AddPersonDialog(onDismiss = { addingPerson = false }) { name -> personId = onAddPerson(name).id }
}

/** Pergunta só o nome — criar uma pessoa leva dois segundos. */
@Composable
fun AddPersonDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova pessoa") },
        text = {
            Column {
                Text(
                    "Acompanhe a agenda de alguém junto com a sua (ex.: namorada, filho, sócio).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onAdd(name.trim())
                onDismiss()
            }) { Text("Adicionar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipGroup(label: String, content: @Composable () -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

/** Próxima hora cheia (se for hoje) ou 09:00 para outros dias. */
private fun defaultTime(tz: TimeZone, date: LocalDate): LocalTime {
    val now = Schedule.toLocal(Schedule.now(), tz)
    if (date != now.date) return LocalTime(9, 0)
    return LocalTime((now.hour + 1).coerceAtMost(23), 0)
}
