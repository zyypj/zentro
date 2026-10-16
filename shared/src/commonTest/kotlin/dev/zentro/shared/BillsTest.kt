package dev.zentro.shared

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BillsTest {
    // 16/10/2026 é uma sexta-feira
    private val today = LocalDate(2026, 10, 16)
    private val tz = TimeZone.of("America/Sao_Paulo")
    private fun at(y: Int, mo: Int, d: Int, h: Int = 8) = Schedule.toMillis(LocalDateTime(y, mo, d, h, 0), tz)

    @Test
    fun monthlyBillFromQuickInput() {
        val d = QuickInput.parseEvent("Hospedagem todo dia 5 R$ 49,90", today)!!
        assertEquals("Hospedagem", d.title)
        assertEquals(Recurrence.MONTHLY, d.recurrence)
        assertEquals(LocalDate(2026, 11, 5), d.date) // dia 5 deste mês já passou
        assertEquals(4990L, d.amountCents)
        assertNull(d.time)
    }

    @Test
    fun amountWithoutCurrencyAndDayLaterThisMonth() {
        val d = QuickInput.parseEvent("pagar internet todo dia 20 99,90", today)!!
        assertEquals("Pagar internet", d.title)
        assertEquals(LocalDate(2026, 10, 20), d.date)
        assertEquals(9990L, d.amountCents)
    }

    @Test
    fun day31ClampsInShortMonths() {
        val d = QuickInput.parseEvent("Aluguel todo dia 31", LocalDate(2026, 2, 10))!!
        assertEquals(LocalDate(2026, 2, 28), d.date)
    }

    @Test
    fun weeklyAndDailyRecurrence() {
        val weekly = QuickInput.parseEvent("Futebol toda segunda 20h", today)!!
        assertEquals(Recurrence.WEEKLY, weekly.recurrence)
        assertEquals(LocalDate(2026, 10, 19), weekly.date)
        assertEquals("Futebol", weekly.title)
        assertEquals(Recurrence.DAILY, QuickInput.parseEvent("Tomar remédio todo dia 8h", today)!!.recurrence)
    }

    @Test
    fun timesAndDatesAreNotMistakenForMoney() {
        val d = QuickInput.parseEvent("Reunião 10/11 9:30", today)!!
        assertNull(d.amountCents)
        assertEquals(Recurrence.NONE, d.recurrence)
    }

    @Test
    fun paidOccurrencesStopReminding() {
        val due = at(2026, 11, 5)
        val bill = Event(id = "b", title = "Hospedagem", startAt = due, allDay = true, recurrence = Recurrence.MONTHLY, reminders = listOf(1440, 0), billAmountCents = 4990)
        val window = Schedule.triggers(listOf(bill), at(2026, 11, 1), at(2026, 11, 10), tz)
        assertEquals(2, window.size)
        assertEquals(4990L, window.first().amountCents)
        assertTrue(Schedule.triggers(listOf(bill.copy(paid = listOf(due))), at(2026, 11, 1), at(2026, 11, 10), tz).isEmpty())
    }

    @Test
    fun billsOfTheMonthWithStatus() {
        val bill = Event(id = "b", title = "Luz", startAt = at(2026, 9, 10), allDay = true, recurrence = Recurrence.MONTHLY, billAmountCents = 15000)
        val items = Bills.inMonth(listOf(bill), LocalDate(2026, 10, 1), tz, now = at(2026, 10, 16, 12))
        assertEquals(1, items.size)
        assertTrue(items[0].overdue)
        val paid = Bills.inMonth(listOf(bill.copy(paid = listOf(items[0].due))), LocalDate(2026, 10, 1), tz, now = at(2026, 10, 16, 12))
        assertTrue(paid[0].paid && !paid[0].overdue)
    }
}
