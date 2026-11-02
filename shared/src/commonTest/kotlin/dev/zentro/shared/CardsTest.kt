package dev.zentro.shared

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardsTest {
    private val tz = TimeZone.of(DEFAULT_TIMEZONE)

    /** Fecha dia 3 e vence dia 10 do mesmo mês. */
    private val card = CreditCard("nubank", "Nubank", closingDay = 3, dueDay = 10, limitCents = 500_000)

    /** Fecha dia 25 e vence dia 5 do mês seguinte. */
    private val other = CreditCard("inter", "Inter", closingDay = 25, dueDay = 5)

    private fun at(y: Int, m: Int, d: Int) = Schedule.toMillis(LocalDateTime(y, m, d, 12, 0), tz)

    private fun buy(id: String, cents: Long, date: Long, n: Int = 1, c: CreditCard = card) =
        Transaction(id, TxType.EXPENSE, cents, id, date = date, cardId = c.id, installments = n)

    @Test
    fun purchaseOnOrAfterClosingDayGoesToTheNextInvoice() {
        assertEquals(LocalDate(2026, 11, 1), Cards.invoiceFor(card, LocalDate(2026, 11, 2)))
        assertEquals(LocalDate(2026, 12, 1), Cards.invoiceFor(card, LocalDate(2026, 11, 3)))
        assertEquals(LocalDate(2026, 12, 1), Cards.invoiceFor(card, LocalDate(2026, 11, 20)))
        // Vencimento antes do fechamento: vence no mês seguinte ao fechamento.
        assertEquals(LocalDate(2026, 12, 1), Cards.invoiceFor(other, LocalDate(2026, 11, 24)))
        assertEquals(LocalDate(2027, 1, 1), Cards.invoiceFor(other, LocalDate(2026, 11, 25)))
        assertEquals(LocalDate(2026, 11, 25), Cards.closingDate(other, LocalDate(2026, 12, 1)))
        assertEquals(LocalDate(2026, 12, 5), Cards.dueDate(other, LocalDate(2026, 12, 1)))
    }

    @Test
    fun installmentsSpreadOverTheFollowingInvoices() {
        val txs = listOf(buy("tv", 100_000, at(2026, 11, 20), n = 3), buy("cafe", 1_500, at(2026, 12, 1)))
        val dec = Cards.invoice(card, LocalDate(2026, 12, 1), txs, tz)
        assertEquals(33_334 + 1_500, dec.totalCents)
        assertEquals(1, dec.items.first { it.tx.id == "tv" }.number)
        val jan = Cards.invoice(card, LocalDate(2027, 1, 1), txs, tz)
        assertEquals(33_333, jan.totalCents)
        assertEquals(2, jan.items.single().number)
        assertEquals(1, Cards.invoice(card, LocalDate(2027, 2, 1), txs, tz).items.size)
        assertEquals(0, Cards.invoice(card, LocalDate(2027, 3, 1), txs, tz).items.size)
    }

    @Test
    fun splitKeepsEveryCent() {
        assertEquals(listOf(3_334L, 3_333L, 3_333L), Cards.split(10_000, 3))
        assertEquals(listOf(999L), Cards.split(999, 0))
    }

    @Test
    fun statusFollowsClosingDueAndPayment() {
        val inv = Cards.invoice(card, LocalDate(2026, 12, 1), listOf(buy("a", 1_000, at(2026, 11, 10))), tz)
        assertEquals(Cards.Status.OPEN, inv.status(LocalDate(2026, 12, 2)))
        assertEquals(Cards.Status.CLOSED, inv.status(LocalDate(2026, 12, 3)))
        assertEquals(Cards.Status.CLOSED, inv.status(LocalDate(2026, 12, 10)))
        assertEquals(Cards.Status.OVERDUE, inv.status(LocalDate(2026, 12, 11)))
        val paid = Cards.invoice(card, LocalDate(2026, 12, 1), listOf(buy("a", 1_000, at(2026, 11, 10)), Cards.payment(inv, at(2026, 12, 5))), tz)
        assertEquals(Cards.Status.PAID, paid.status(LocalDate(2026, 12, 20)))
    }

    @Test
    fun paymentAffectsBalanceButPurchaseDoesNot() {
        val purchase = buy("a", 1_000, at(2026, 11, 10))
        val inv = Cards.invoice(card, LocalDate(2026, 12, 1), listOf(purchase), tz)
        val payment = Cards.payment(inv, at(2026, 12, 5))
        assertFalse(Finance.affectsBalance(purchase))
        assertTrue(Finance.affectsBalance(payment))
        assertEquals(-1_000, Finance.balanceAt(listOf(purchase, payment), at(2026, 12, 6)))
    }

    @Test
    fun currentIsTheOldestUnpaidClosedInvoice() {
        val txs = listOf(buy("a", 1_000, at(2026, 10, 10)), buy("b", 2_000, at(2026, 11, 10)))
        val today = LocalDate(2026, 12, 4)
        assertEquals(LocalDate(2026, 11, 1), Cards.current(card, txs, today, tz).month)
        val nov = Cards.invoice(card, LocalDate(2026, 11, 1), txs, tz)
        val paidNov = txs + Cards.payment(nov, at(2026, 11, 9))
        assertEquals(LocalDate(2026, 12, 1), Cards.current(card, paidNov, today, tz).month)
        assertEquals(2_000, Cards.usedLimit(card, paidNov, tz))
    }
}
