package dev.zentro.shared

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScheduleTest {
    private val tz = TimeZone.of("America/Sao_Paulo")

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) =
        Schedule.toMillis(LocalDateTime(y, mo, d, h, mi), tz)

    private fun event(start: Long, recurrence: Recurrence = Recurrence.NONE, reminders: List<Int> = listOf(15, 0)) =
        Event(id = "e1", title = "Teste", startAt = start, recurrence = recurrence, reminders = reminders)

    @Test
    fun singleEventTriggersAtEachReminder() {
        val e = event(at(2026, 10, 20, 14))
        val triggers = Schedule.triggers(listOf(e), at(2026, 10, 20, 0), at(2026, 10, 21, 0), tz)
        assertEquals(listOf(at(2026, 10, 20, 13, 45), at(2026, 10, 20, 14)), triggers.map { it.triggerAt })
    }

    @Test
    fun reminderTheDayBeforeIsFoundInPreviousWindow() {
        val e = event(at(2026, 10, 20, 9), reminders = listOf(1440))
        val triggers = Schedule.triggers(listOf(e), at(2026, 10, 19, 0), at(2026, 10, 20, 0), tz)
        assertEquals(listOf(at(2026, 10, 19, 9)), triggers.map { it.triggerAt })
    }

    @Test
    fun dailyRecurrenceFarInTheFuture() {
        val e = event(at(2020, 1, 1, 8), Recurrence.DAILY)
        val occ = Schedule.occurrences(e, at(2026, 10, 16, 0), at(2026, 10, 18, 0), tz)
        assertEquals(listOf(at(2026, 10, 16, 8), at(2026, 10, 17, 8)), occ)
    }

    @Test
    fun weekdaysSkipWeekend() {
        // 17/10/2026 é sábado e 19/10/2026 é segunda
        val e = event(at(2026, 10, 1, 7), Recurrence.WEEKDAYS)
        val occ = Schedule.occurrences(e, at(2026, 10, 16, 0), at(2026, 10, 20, 0), tz)
        assertEquals(listOf(at(2026, 10, 16, 7), at(2026, 10, 19, 7)), occ)
    }

    @Test
    fun monthlyClampsToEndOfMonth() {
        val e = event(at(2026, 1, 31, 10), Recurrence.MONTHLY)
        val occ = Schedule.occurrences(e, at(2026, 2, 1, 0), at(2026, 3, 1, 0), tz)
        assertEquals(listOf(at(2026, 2, 28, 10)), occ)
    }

    @Test
    fun deletedEventsNeverTrigger() {
        val e = event(at(2026, 10, 20, 14)).copy(deleted = true)
        assertTrue(Schedule.triggers(listOf(e), 0, at(2030, 1, 1, 0), tz).isEmpty())
    }

    @Test
    fun agendaForDayListsRecurringAndSingleEvents() {
        val daily = event(at(2026, 10, 1, 8), Recurrence.DAILY)
        val single = event(at(2026, 10, 16, 14)).copy(id = "e2")
        val other = event(at(2026, 10, 17, 0)).copy(id = "e3")
        val agenda = Schedule.agendaFor(listOf(single, daily, other), LocalDate(2026, 10, 16), tz)
        assertEquals(listOf("e1", "e2"), agenda.map { it.first.id })
    }

    @Test
    fun reviewIsNeededUntilTheDayIsMarked() {
        val now = at(2026, 10, 16, 10)
        val settings = UserSettings(lastReviewDate = "2026-10-15")
        assertTrue(Schedule.needsReview(settings, now))
        assertFalse(Schedule.needsReview(settings.copy(lastReviewDate = LocalDate(2026, 10, 16).toString()), now))
    }

    @Test
    fun reviewChecksAreInTheFuture() {
        val now = at(2026, 10, 16, 10)
        val checks = Schedule.nextReviewChecks(UserSettings(reviewTimes = listOf("09:00", "20:00")), now, days = 1)
        assertEquals(listOf(at(2026, 10, 16, 20), at(2026, 10, 17, 9), at(2026, 10, 17, 20)), checks)
    }

    @Test
    fun moneyFormatting() {
        assertEquals("R$ 1.234,56", Format.money(123456))
        assertEquals("- R$ 0,05", Format.money(-5))
        assertEquals(1250L, Format.parseMoney("12,50"))
        assertEquals(120000L, Format.parseMoney("1.200,00"))
        assertEquals(390L, Format.parseMoney("R$ 3,9"))
    }
}
