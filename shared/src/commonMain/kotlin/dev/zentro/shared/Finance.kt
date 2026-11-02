package dev.zentro.shared

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.math.abs

/**
 * Saldo acumulado: o que sobra (ou falta) num mês continua no seguinte, como numa conta de banco.
 * Compras no cartão de crédito não mexem no saldo na hora; quem desconta é o pagamento da fatura.
 */
object Finance {
    const val ADJUSTMENT = "Ajuste de saldo"

    /** Um mês fechado: saldo que veio do mês anterior, o que entrou, o que saiu e o saldo no fim. */
    data class Month(val month: LocalDate, val opening: Long, val income: Long, val expense: Long) {
        val result: Long get() = income - expense
        val closing: Long get() = opening + result
    }

    fun affectsBalance(tx: Transaction): Boolean = !tx.deleted && tx.cardId == null

    fun signed(tx: Transaction): Long = if (tx.type == TxType.INCOME) tx.amountCents else -tx.amountCents

    fun firstOfMonth(date: LocalDate): LocalDate = LocalDate(date.year, date.month, 1)

    fun addMonths(date: LocalDate, months: Int): LocalDate {
        val index = date.year * 12 + (date.monthNumber - 1) + months
        val year = index.floorDiv(12)
        val month = index.mod(12) + 1
        val last = lengthOfMonth(year, month)
        return LocalDate(year, month, date.dayOfMonth.coerceAtMost(last))
    }

    fun lengthOfMonth(year: Int, month: Int): Int = when (month) {
        2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }

    /** Saldo considerando tudo o que aconteceu antes de [at] (epoch ms). */
    fun balanceAt(txs: Collection<Transaction>, at: Long): Long =
        txs.filter { affectsBalance(it) && it.date < at }.sumOf { signed(it) }

    /** Resumo de um mês, já com o saldo que veio dos meses anteriores. */
    fun month(txs: Collection<Transaction>, month: LocalDate, tz: TimeZone): Month {
        val first = firstOfMonth(month)
        val start = Schedule.startOfDay(first, tz)
        val end = Schedule.startOfDay(addMonths(first, 1), tz)
        var opening = 0L
        var income = 0L
        var expense = 0L
        for (t in txs) {
            if (!affectsBalance(t)) continue
            when {
                t.date < start -> opening += signed(t)
                t.date < end -> if (t.type == TxType.INCOME) income += t.amountCents else expense += t.amountCents
            }
        }
        return Month(first, opening, income, expense)
    }

    /** Com quanto de saldo terminou cada mês, do primeiro lançamento até [until]. O mais recente vem primeiro. */
    fun history(txs: Collection<Transaction>, until: LocalDate, tz: TimeZone): List<Month> {
        val relevant = txs.filter { affectsBalance(it) }.sortedBy { it.date }
        if (relevant.isEmpty()) return emptyList()
        val last = firstOfMonth(until)
        var month = firstOfMonth(Schedule.toLocal(relevant.first().date, tz).date)
        if (month > last) return emptyList()
        val result = mutableListOf<Month>()
        var opening = 0L
        var i = 0
        while (month <= last) {
            val end = Schedule.startOfDay(addMonths(month, 1), tz)
            var income = 0L
            var expense = 0L
            while (i < relevant.size && relevant[i].date < end) {
                val t = relevant[i]
                if (t.type == TxType.INCOME) income += t.amountCents else expense += t.amountCents
                i++
            }
            val m = Month(month, opening, income, expense)
            result += m
            opening = m.closing
            month = addMonths(month, 1)
        }
        return result.reversed()
    }

    /** Lançamento que corrige o saldo de agora para [target] (ex.: o que o banco mostra). null se já bate. */
    fun adjustment(txs: Collection<Transaction>, target: Long, id: String, now: Long): Transaction? {
        val diff = target - balanceAt(txs, now + 1)
        if (diff == 0L) return null
        return Transaction(
            id = id,
            type = if (diff > 0) TxType.INCOME else TxType.EXPENSE,
            amountCents = abs(diff),
            description = ADJUSTMENT,
            category = ADJUSTMENT,
            date = now,
        )
    }

    /**
     * Para onde foi o dinheiro no mês, por categoria. Compras no cartão contam pela data da compra;
     * o pagamento da fatura não conta de novo, para não dobrar o valor.
     */
    fun spendingByCategory(txs: Collection<Transaction>, month: LocalDate, tz: TimeZone): List<Pair<String, Long>> {
        val first = firstOfMonth(month)
        val start = Schedule.startOfDay(first, tz)
        val end = Schedule.startOfDay(addMonths(first, 1), tz)
        return txs.filter {
            !it.deleted && it.type == TxType.EXPENSE && it.invoiceKey == null && it.category != ADJUSTMENT && it.date >= start && it.date < end
        }.groupBy { it.category }
            .map { (category, list) -> category to list.sumOf { it.amountCents } }
            .sortedByDescending { it.second }
    }
}
