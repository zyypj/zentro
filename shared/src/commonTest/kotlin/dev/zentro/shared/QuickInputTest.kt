package dev.zentro.shared

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuickInputTest {
    // 16/10/2026 é uma sexta-feira
    private val today = LocalDate(2026, 10, 16)

    @Test
    fun relativeDayAndHour() {
        val d = QuickInput.parseEvent("Dentista amanhã 14h", today)!!
        assertEquals("Dentista", d.title)
        assertEquals(LocalDate(2026, 10, 17), d.date)
        assertEquals(LocalTime(14, 0), d.time)
    }

    @Test
    fun weekdayWithMinutes() {
        val d = QuickInput.parseEvent("reunião com cliente segunda às 9:30", today)!!
        assertEquals("Reunião com cliente", d.title)
        assertEquals(LocalDate(2026, 10, 19), d.date)
        assertEquals(LocalTime(9, 30), d.time)
    }

    @Test
    fun sameWeekdayMeansNextWeek() {
        val d = QuickInput.parseEvent("Futebol sexta 20h30", today)!!
        assertEquals(LocalDate(2026, 10, 23), d.date)
        assertEquals(LocalTime(20, 30), d.time)
    }

    @Test
    fun explicitDateWithoutTime() {
        val d = QuickInput.parseEvent("pagar luz 10/11", today)!!
        assertEquals("Pagar luz", d.title)
        assertEquals(LocalDate(2026, 11, 10), d.date)
        assertNull(d.time)
    }

    @Test
    fun pastDateWithoutYearGoesToNextYear() {
        assertEquals(LocalDate(2027, 1, 5), QuickInput.parseEvent("Renovar CNH 05/01", today)!!.date)
    }

    @Test
    fun plainTextIsToday() {
        val d = QuickInput.parseEvent("Ligar para a mãe", today)!!
        assertEquals(today, d.date)
        assertNull(d.time)
    }

    @Test
    fun transactions() {
        val expense = QuickInput.parseTransaction("45,90 almoço")!!
        assertEquals(TxType.EXPENSE, expense.type)
        assertEquals(4590, expense.amountCents)
        assertEquals("Almoço", expense.description)
        assertEquals("Alimentação", QuickInput.guessCategory(expense.description, expense.type))

        val income = QuickInput.parseTransaction("+1.200,00 freela site")!!
        assertEquals(TxType.INCOME, income.type)
        assertEquals(120000, income.amountCents)
        assertEquals("Freela", QuickInput.guessCategory(income.description, income.type))

        assertEquals(TxType.INCOME, QuickInput.parseTransaction("3500 salário")!!.type)
        assertNull(QuickInput.parseTransaction("almoço"))
    }

    @Test
    fun classifyDecidesWhereTheQuickCaptureGoes() {
        val today = kotlinx.datetime.LocalDate(2026, 11, 2)
        assertEquals(QuickInput.Kind.TRANSACTION, QuickInput.classify("45,90 almoço", today))
        assertEquals(QuickInput.Kind.TRANSACTION, QuickInput.classify("+1200 salário", today))
        assertEquals(QuickInput.Kind.EVENT, QuickInput.classify("reunião amanhã às 14h", today))
        assertEquals(QuickInput.Kind.EVENT, QuickInput.classify("10h dentista", today))
        assertEquals(QuickInput.Kind.EVENT, QuickInput.classify("Hospedagem todo dia 5 49,90", today))
        assertEquals(QuickInput.Kind.EVENT, QuickInput.classify("prova sexta 10h", today))
        assertEquals(QuickInput.Kind.NOTE, QuickInput.classify("Ideia: app de receitas", today))
        assertEquals(QuickInput.Kind.NOTE, QuickInput.classify("comprar presente da Ana", today))
    }
}
