package dev.zentro.shared

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.Month
import kotlin.math.abs
import kotlin.math.round

/** Formatação em pt-BR sem depender de java.text, para funcionar em qualquer plataforma. */
object Format {
    private val months = listOf(
        "janeiro", "fevereiro", "março", "abril", "maio", "junho",
        "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
    )
    private val weekdays = listOf("segunda", "terça", "quarta", "quinta", "sexta", "sábado", "domingo")
    private val weekdaysShort = listOf("S", "T", "Q", "Q", "S", "S", "D")

    fun money(cents: Long, signed: Boolean = false): String {
        val value = abs(cents)
        val reais = (value / 100).toString().reversed().chunked(3).joinToString(".").reversed()
        val centavos = (value % 100).toString().padStart(2, '0')
        val sign = when {
            cents < 0 -> "- "
            signed && cents > 0 -> "+ "
            else -> ""
        }
        return "${sign}R$ $reais,$centavos"
    }

    /** Converte o texto digitado ("12,50", "1.200", "R$ 3,9") em centavos. */
    fun parseMoney(text: String): Long? {
        val clean = text.replace("R$", "").replace(" ", "").trim()
        if (clean.isEmpty()) return null
        val normalized = if (clean.contains(',')) clean.replace(".", "").replace(',', '.') else clean
        val value = normalized.toDoubleOrNull() ?: return null
        if (value < 0) return null
        return round(value * 100).toLong()
    }

    fun time(dt: LocalDateTime): String =
        "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"

    fun monthName(month: Month): String = months[month.ordinal]

    fun monthYear(date: LocalDate): String =
        "${monthName(date.month).replaceFirstChar { it.uppercase() }} ${date.year}"

    fun weekday(day: DayOfWeek): String = weekdays[day.ordinal]

    /** Inicial do dia da semana, começando na segunda (índice 0). */
    fun weekdayInitial(index: Int): String = weekdaysShort[index]

    /** "quinta, 16 de outubro" */
    fun longDate(date: LocalDate): String =
        "${weekday(date.dayOfWeek)}, ${date.dayOfMonth} de ${monthName(date.month)}"

    fun shortDate(date: LocalDate): String =
        "${date.dayOfMonth.toString().padStart(2, '0')}/${date.monthNumber.toString().padStart(2, '0')}"
}
