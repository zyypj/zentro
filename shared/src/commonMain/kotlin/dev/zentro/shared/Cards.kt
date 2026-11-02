package dev.zentro.shared

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * Faturas do cartão de crédito. Uma fatura é identificada pelo mês em que vence. Cada compra
 * (à vista ou parcelada) cai na fatura aberta na data da compra, e as parcelas seguintes nas
 * faturas dos meses seguintes. Pagar a fatura lança uma saída, que é o que desconta do saldo.
 */
object Cards {
    const val CATEGORY = "Cartão de crédito"
    const val MAX_INSTALLMENTS = 48

    enum class Status { OPEN, CLOSED, OVERDUE, PAID }

    /** Uma parcela (ou a compra à vista) dentro de uma fatura. */
    data class Item(val tx: Transaction, val number: Int, val count: Int, val amountCents: Long)

    data class Invoice(
        val card: CreditCard,
        /** Primeiro dia do mês de vencimento. */
        val month: LocalDate,
        val closing: LocalDate,
        val due: LocalDate,
        val items: List<Item>,
        val payment: Transaction?,
    ) {
        val totalCents: Long get() = items.sumOf { it.amountCents }
        val paid: Boolean get() = payment != null
        val key: String get() = invoiceKey(card.id, month)

        fun status(today: LocalDate): Status = when {
            paid -> Status.PAID
            today < closing -> Status.OPEN
            today <= due -> Status.CLOSED
            else -> Status.OVERDUE
        }
    }

    private fun mm(month: LocalDate) = month.monthNumber.toString().padStart(2, '0')

    /** Identifica a fatura no lançamento que a paga: `<cartão>/aaaa-mm`. */
    fun invoiceKey(cardId: String, month: LocalDate) = "$cardId/${month.year}-${mm(month)}"

    /** Id fixo do pagamento: pagar de novo não duplica e desfazer sabe o que apagar. */
    fun paymentId(cardId: String, month: LocalDate) = "invoice-$cardId-${month.year}-${mm(month)}"

    private fun dayIn(month: LocalDate, day: Int): LocalDate =
        LocalDate(month.year, month.month, day.coerceIn(1, Finance.lengthOfMonth(month.year, month.monthNumber)))

    /** Se vence num dia maior que o do fechamento, fecha e vence no mesmo mês; senão vence no mês seguinte. */
    private fun dueSameMonth(card: CreditCard) = card.dueDay > card.closingDay

    fun closingDate(card: CreditCard, month: LocalDate): LocalDate =
        dayIn(if (dueSameMonth(card)) month else Finance.addMonths(Finance.firstOfMonth(month), -1), card.closingDay)

    fun dueDate(card: CreditCard, month: LocalDate): LocalDate = dayIn(month, card.dueDay)

    /** Mês da fatura em que entra uma compra feita em [date]. No dia do fechamento já vai para a próxima. */
    fun invoiceFor(card: CreditCard, date: LocalDate): LocalDate {
        var closingMonth = Finance.firstOfMonth(date)
        if (date >= dayIn(closingMonth, card.closingDay)) closingMonth = Finance.addMonths(closingMonth, 1)
        return if (dueSameMonth(card)) closingMonth else Finance.addMonths(closingMonth, 1)
    }

    /** Divide o total em parcelas; os centavos que sobram vão nas primeiras. */
    fun split(totalCents: Long, installments: Int): List<Long> {
        val n = installments.coerceIn(1, MAX_INSTALLMENTS)
        val base = totalCents / n
        val rest = (totalCents % n).toInt()
        return List(n) { base + if (it < rest) 1 else 0 }
    }

    fun purchases(card: CreditCard, txs: Collection<Transaction>): List<Transaction> =
        txs.filter { !it.deleted && it.cardId == card.id }

    private fun monthsBetween(from: LocalDate, to: LocalDate): Int =
        (to.year * 12 + to.monthNumber) - (from.year * 12 + from.monthNumber)

    fun invoice(card: CreditCard, month: LocalDate, txs: Collection<Transaction>, tz: TimeZone): Invoice {
        val first = Finance.firstOfMonth(month)
        val items = purchases(card, txs).mapNotNull { tx ->
            val start = invoiceFor(card, Schedule.toLocal(tx.date, tz).date)
            val k = monthsBetween(start, first)
            val parts = split(tx.amountCents, tx.installments)
            if (k in parts.indices) Item(tx, k + 1, parts.size, parts[k]) else null
        }.sortedByDescending { it.tx.date }
        val key = invoiceKey(card.id, first)
        val payment = txs.firstOrNull { !it.deleted && it.invoiceKey == key }
        return Invoice(card, first, closingDate(card, first), dueDate(card, first), items, payment)
    }

    /** A fatura que importa agora: a mais antiga já fechada e não paga; se não houver, a que está aberta. */
    fun current(card: CreditCard, txs: Collection<Transaction>, today: LocalDate, tz: TimeZone): Invoice {
        val open = invoiceFor(card, today)
        for (back in 12 downTo 1) {
            val inv = invoice(card, Finance.addMonths(open, -back), txs, tz)
            if (!inv.paid && inv.totalCents > 0) return inv
        }
        return invoice(card, open, txs, tz)
    }

    /** Quanto do limite está usado: todas as parcelas que ainda estão em faturas não pagas. */
    fun usedLimit(card: CreditCard, txs: Collection<Transaction>, tz: TimeZone): Long {
        val paidKeys = txs.filter { !it.deleted && it.invoiceKey != null }.mapNotNull { it.invoiceKey }.toSet()
        return purchases(card, txs).sumOf { tx ->
            val start = invoiceFor(card, Schedule.toLocal(tx.date, tz).date)
            split(tx.amountCents, tx.installments).withIndex().sumOf { (k, cents) ->
                if (invoiceKey(card.id, Finance.addMonths(start, k)) in paidKeys) 0L else cents
            }
        }
    }

    /** O lançamento de saída que paga a fatura. */
    fun payment(invoice: Invoice, now: Long): Transaction = Transaction(
        id = paymentId(invoice.card.id, invoice.month),
        type = TxType.EXPENSE,
        amountCents = invoice.totalCents,
        description = "Fatura ${invoice.card.name}",
        category = CATEGORY,
        date = now,
        invoiceKey = invoice.key,
    )
}
