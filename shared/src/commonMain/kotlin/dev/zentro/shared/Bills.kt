package dev.zentro.shared

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** Contas a pagar: compromissos com valor que, ao serem pagos, viram uma saída nas finanças. */
object Bills {
    data class Item(val event: Event, val due: Long, val paid: Boolean, val overdue: Boolean)

    /** Id fixo do lançamento criado ao pagar — pagar de novo não duplica e "desfazer" sabe o que apagar. */
    fun paymentId(eventId: String, occurrence: Long) = "bill-$eventId-$occurrence"

    fun isBill(event: Event) = event.billAmountCents != null

    /** Todas as contas que vencem no mês de [month], com a situação de cada uma. */
    fun inMonth(events: Collection<Event>, month: LocalDate, tz: TimeZone, now: Long = Schedule.now()): List<Item> {
        val first = LocalDate(month.year, month.month, 1)
        val from = Schedule.startOfDay(first, tz)
        val to = Schedule.startOfDay(first.plusMonths(1), tz)
        val today = Schedule.startOfDay(Schedule.today(tz, now), tz)
        return events.filter { isBill(it) && !it.deleted }.flatMap { event ->
            Schedule.occurrences(event.copy(endAt = null), from, to, tz).map { start ->
                val paid = start in event.paid
                Item(event, start, paid, overdue = !paid && start < today)
            }
        }.sortedBy { it.due }
    }

    private fun LocalDate.plusMonths(n: Int): LocalDate =
        kotlinx.datetime.LocalDate(year + (monthNumber - 1 + n) / 12, (monthNumber - 1 + n) % 12 + 1, 1)
}
