package dev.zentro.shared

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FinanceTest {
    private val tz = TimeZone.of(DEFAULT_TIMEZONE)

    private fun at(y: Int, m: Int, d: Int) = Schedule.toMillis(LocalDateTime(y, m, d, 12, 0), tz)

    private fun tx(id: String, type: TxType, cents: Long, date: Long, cardId: String? = null, category: String = "Outros") =
        Transaction(id, type, cents, id, category = category, date = date, cardId = cardId)

    private val txs = listOf(
        tx("salario-set", TxType.INCOME, 300_000, at(2026, 9, 5)),
        tx("aluguel-set", TxType.EXPENSE, 100_000, at(2026, 9, 10)),
        tx("salario-out", TxType.INCOME, 300_000, at(2026, 10, 5)),
        tx("mercado-out", TxType.EXPENSE, 50_000, at(2026, 10, 20)),
        // Compra no cartão: não mexe no saldo.
        tx("tv", TxType.EXPENSE, 200_000, at(2026, 10, 21), cardId = "nubank"),
    )

    @Test
    fun balanceCarriesOverToTheNextMonth() {
        val oct = Finance.month(txs, LocalDate(2026, 10, 15), tz)
        assertEquals(200_000, oct.opening)
        assertEquals(300_000, oct.income)
        assertEquals(50_000, oct.expense)
        assertEquals(450_000, oct.closing)
        // Mês sem nada: o saldo continua o mesmo.
        val nov = Finance.month(txs, LocalDate(2026, 11, 1), tz)
        assertEquals(450_000, nov.opening)
        assertEquals(450_000, nov.closing)
    }

    @Test
    fun historyListsEachMonthEndNewestFirst() {
        val history = Finance.history(txs, LocalDate(2026, 11, 2), tz)
        assertEquals(listOf(LocalDate(2026, 11, 1), LocalDate(2026, 10, 1), LocalDate(2026, 9, 1)), history.map { it.month })
        assertEquals(listOf(450_000L, 450_000L, 200_000L), history.map { it.closing })
        assertEquals(emptyList(), Finance.history(emptyList(), LocalDate(2026, 11, 2), tz))
    }

    @Test
    fun adjustmentFixesTheCurrentBalance() {
        val now = at(2026, 11, 2)
        val up = Finance.adjustment(txs, 500_000, "a", now)!!
        assertEquals(TxType.INCOME, up.type)
        assertEquals(50_000, up.amountCents)
        val down = Finance.adjustment(txs, 400_000, "b", now)!!
        assertEquals(TxType.EXPENSE, down.type)
        assertEquals(50_000, down.amountCents)
        assertNull(Finance.adjustment(txs, 450_000, "c", now))
    }

    @Test
    fun spendingCountsCardPurchasesButNotTheInvoicePayment() {
        val payment = Transaction("p", TxType.EXPENSE, 200_000, "Fatura", Cards.CATEGORY, at(2026, 10, 25), invoiceKey = "nubank/2026-11")
        val spending = Finance.spendingByCategory(txs + payment, LocalDate(2026, 10, 1), tz)
        assertEquals(250_000, spending.sumOf { it.second })
    }

    @Test
    fun addMonthsClampsTheDay() {
        assertEquals(LocalDate(2027, 2, 28), Finance.addMonths(LocalDate(2027, 1, 31), 1))
        assertEquals(LocalDate(2025, 12, 1), Finance.addMonths(LocalDate(2026, 1, 1), -1))
    }
}
