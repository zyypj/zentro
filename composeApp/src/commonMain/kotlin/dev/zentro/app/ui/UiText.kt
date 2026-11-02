package dev.zentro.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.zentro.shared.Event
import dev.zentro.shared.Format
import dev.zentro.shared.Person
import dev.zentro.shared.Recurrence
import dev.zentro.shared.Schedule
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil

/** "45 min", "1 hora", "1h30". */
fun durationText(minutes: Long): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0L -> if (minutes == 60L) "1 hora" else "${minutes / 60} horas"
    else -> "${minutes / 60}h${(minutes % 60).toString().padStart(2, '0')}"
}

/** Linha de detalhes de um compromisso: duração e aviso ("45 min · Lembrete 15 min antes"). */
fun eventDetails(event: Event, start: Long, now: Long, person: Person? = null): String {
    val parts = mutableListOf<String>()
    when {
        event.allDay -> parts += "Dia todo"
        event.endAt != null -> parts += durationText((event.endAt!! - event.startAt) / 60_000)
    }
    val reminders = event.reminders.sortedDescending()
    val pending = reminders.filter { start - it * 60_000L > now }
    parts += when {
        reminders.isEmpty() -> "Sem lembrete"
        pending.isEmpty() -> "Sem aviso pendente"
        else -> "Lembrete " + Schedule.describeOffset(pending.first()).let { if (it == "na hora") "no horário" else it }
    }
    if (event.critical) parts += "Despertador"
    person?.let { parts += it.name }
    return parts.joinToString(" · ")
}

/** Cor da bolinha da linha do tempo: a da categoria, ou a cor escolhida no compromisso. */
fun eventDot(event: Event): Color =
    if (event.category.isNotEmpty()) categoryColors(event.category).dot else ZColors.event(event.color)

/** Etiqueta à direita do compromisso: categoria ou, se for de outra pessoa, o nome dela. */
@Composable
fun EventTag(event: Event, person: Person?) {
    when {
        event.category.isNotEmpty() -> Tag(event.category, categoryColors(event.category))
        person != null -> Tag(person.name, TagColors(ZColors.event(person.color).copy(alpha = 0.16f), ZColors.event(person.color), ZColors.event(person.color)))
    }
}

/** "editada ontem", "editada há 5 min", "29 out". */
fun editedText(millis: Long, now: Long, tz: TimeZone): String {
    val diff = now - millis
    val day = Schedule.toLocal(millis, tz).date
    val days = day.daysUntil(Schedule.today(tz, now))
    return when {
        diff < 60_000 -> "editada agora"
        diff < 3_600_000 -> "editada há ${diff / 60_000} min"
        days == 0 -> "editada hoje"
        days == 1 -> "editada ontem"
        days < 7 -> "editada há $days dias"
        else -> "${day.dayOfMonth} ${Format.monthName(day.month).take(3)}"
    }
}

/** Como a conta se repete ("Recorrente · mensal"). */
fun recurrenceText(event: Event): String = when (event.recurrence) {
    Recurrence.NONE -> "Conta única"
    Recurrence.DAILY -> "Recorrente · diária"
    Recurrence.WEEKDAYS -> "Recorrente · dias úteis"
    Recurrence.WEEKLY -> "Recorrente · semanal"
    Recurrence.MONTHLY -> "Recorrente · mensal"
    Recurrence.YEARLY -> "Recorrente · anual"
}

/** "Vence hoje", "Vencida há 2 dias", "Vence dia 10". */
fun dueText(due: LocalDate, today: LocalDate): String {
    val days = today.daysUntil(due)
    return when {
        days < 0 -> if (days == -1) "Vencida há 1 dia" else "Vencida há ${-days} dias"
        days == 0 -> "Vence hoje"
        days == 1 -> "Vence amanhã"
        else -> "Vence dia ${due.dayOfMonth}"
    }
}

/** "Segunda-feira, 2 de novembro de 2026". */
fun fullDate(date: LocalDate): String {
    val weekday = Format.weekday(date.dayOfWeek).let { if (it.endsWith("feira") || it in setOf("sábado", "domingo")) it else "$it-feira" }
    return "${weekday.replaceFirstChar { it.uppercase() }}, ${date.dayOfMonth} de ${Format.monthName(date.month)} de ${date.year}"
}
