package dev.zentro.shared

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.monthsUntil
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearsUntil

/** Um aviso concreto que precisa disparar em [triggerAt]. */
data class ReminderTrigger(
    val eventId: String,
    val title: String,
    val occurrenceStart: Long,
    val minutesBefore: Int,
    val triggerAt: Long,
    val critical: Boolean,
) {
    /** Chave estável usada para nunca disparar o mesmo aviso duas vezes. */
    val key: String get() = "$eventId@$occurrenceStart-$minutesBefore"
}

/**
 * Motor de agenda compartilhado entre servidor, Android e desktop.
 * Toda a lógica de recorrência e de horários dos avisos vive aqui, então os três
 * calculam exatamente os mesmos disparos.
 */
object Schedule {
    private const val MINUTE = 60_000L
    private const val MAX_OCCURRENCES = 5_000

    fun now(): Long = Clock.System.now().toEpochMilliseconds()

    fun zone(id: String?): TimeZone =
        runCatching { TimeZone.of(id ?: DEFAULT_TIMEZONE) }.getOrElse { TimeZone.of(DEFAULT_TIMEZONE) }

    fun toLocal(millis: Long, tz: TimeZone): LocalDateTime =
        Instant.fromEpochMilliseconds(millis).toLocalDateTime(tz)

    fun toMillis(dateTime: LocalDateTime, tz: TimeZone): Long =
        dateTime.toInstant(tz).toEpochMilliseconds()

    fun today(tz: TimeZone, at: Long = now()): LocalDate = toLocal(at, tz).date

    fun startOfDay(date: LocalDate, tz: TimeZone): Long = date.atStartOfDayIn(tz).toEpochMilliseconds()

    fun endOfDay(date: LocalDate, tz: TimeZone): Long = startOfDay(date.plus(1, DateTimeUnit.DAY), tz)

    /** Inícios (epoch ms) de todas as ocorrências de [event] que tocam o intervalo [from, to). */
    fun occurrences(event: Event, from: Long, to: Long, tz: TimeZone): List<Long> {
        if (event.deleted || to <= from) return emptyList()
        val duration = ((event.endAt ?: event.startAt) - event.startAt).coerceAtLeast(0)
        if (event.recurrence == Recurrence.NONE) {
            val overlaps = event.startAt < to && event.startAt + duration >= from
            return if (overlaps) listOf(event.startAt) else emptyList()
        }

        val base = toLocal(event.startAt, tz)
        val fromDate = toLocal((from - duration).coerceAtLeast(event.startAt), tz).date
        // Pula direto para perto de `from` em vez de iterar desde o início do evento.
        var n = when (event.recurrence) {
            Recurrence.DAILY, Recurrence.WEEKDAYS -> base.date.daysUntil(fromDate) - 3
            Recurrence.WEEKLY -> base.date.daysUntil(fromDate) / 7 - 1
            Recurrence.MONTHLY -> base.date.monthsUntil(fromDate) - 1
            Recurrence.YEARLY -> base.date.yearsUntil(fromDate) - 1
            Recurrence.NONE -> 0
        }.coerceAtLeast(0)

        val result = mutableListOf<Long>()
        var guard = 0
        while (guard++ < MAX_OCCURRENCES) {
            val date = when (event.recurrence) {
                Recurrence.DAILY, Recurrence.WEEKDAYS -> base.date.plus(n, DateTimeUnit.DAY)
                Recurrence.WEEKLY -> base.date.plus(n * 7, DateTimeUnit.DAY)
                Recurrence.MONTHLY -> base.date.plus(n, DateTimeUnit.MONTH)
                Recurrence.YEARLY -> base.date.plus(n, DateTimeUnit.YEAR)
                Recurrence.NONE -> base.date
            }
            n++
            if (event.recurrence == Recurrence.WEEKDAYS &&
                (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY)
            ) continue
            val start = toMillis(LocalDateTime(date, base.time), tz)
            if (start >= to) break
            if (start + duration >= from) result += start
        }
        return result
    }

    /** Todos os avisos que disparam dentro de [from, to), ordenados por horário. */
    fun triggers(events: Collection<Event>, from: Long, to: Long, tz: TimeZone): List<ReminderTrigger> {
        val result = mutableListOf<ReminderTrigger>()
        for (event in events) {
            if (event.deleted || event.reminders.isEmpty()) continue
            val maxBefore = event.reminders.max().coerceAtLeast(0)
            for (start in occurrences(event.copy(endAt = null), from, to + maxBefore * MINUTE, tz)) {
                for (minutes in event.reminders.distinct()) {
                    val at = start - minutes * MINUTE
                    if (at in from until to) {
                        result += ReminderTrigger(event.id, event.title, start, minutes, at, event.critical)
                    }
                }
            }
        }
        return result.sortedBy { it.triggerAt }
    }

    /** Ocorrências do dia [date], ordenadas, como pares (evento, início). */
    fun agendaFor(events: Collection<Event>, date: LocalDate, tz: TimeZone): List<Pair<Event, Long>> {
        val from = startOfDay(date, tz)
        val to = endOfDay(date, tz)
        return events.flatMap { e -> occurrences(e, from, to - 1, tz).map { e to it } }
            .filter { it.second >= from || it.first.endAt != null }
            .sortedBy { it.second }
    }

    fun parseTime(value: String): LocalTime? = runCatching {
        val (h, m) = value.trim().split(":").map { it.toInt() }
        LocalTime(h, m)
    }.getOrNull()

    /**
     * Próximos horários em que o lembrete "atualize sua agenda" deve ser avaliado,
     * cobrindo hoje e os próximos [days] dias.
     */
    fun nextReviewChecks(settings: UserSettings, from: Long, days: Int = 2): List<Long> {
        val tz = zone(settings.timezone)
        val today = today(tz, from)
        val times = settings.reviewTimes.mapNotNull(::parseTime)
        return (0..days).flatMap { offset ->
            val date = today.plus(offset, DateTimeUnit.DAY)
            times.map { toMillis(LocalDateTime(date, it), tz) }
        }.filter { it > from }.sorted()
    }

    /** True quando a agenda ainda não foi atualizada no dia de [at]. */
    fun needsReview(settings: UserSettings, at: Long = now()): Boolean =
        settings.lastReviewDate != today(zone(settings.timezone), at).toString()

    fun describeOffset(minutes: Int): String = when {
        minutes <= 0 -> "na hora"
        minutes % 1440 == 0 -> if (minutes == 1440) "1 dia antes" else "${minutes / 1440} dias antes"
        minutes % 60 == 0 -> if (minutes == 60) "1 hora antes" else "${minutes / 60} horas antes"
        else -> "$minutes min antes"
    }
}
