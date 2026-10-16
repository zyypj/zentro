package dev.zentro.shared

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus

/**
 * Entende frases curtas em português para criar coisas sem preencher formulário:
 * "Dentista amanhã 14h", "reunião sexta 9:30", "pagar luz 10/11",
 * "-45,90 almoço", "+1200 salário".
 */
object QuickInput {
    data class EventDraft(val title: String, val date: LocalDate, val time: LocalTime?)
    data class TransactionDraft(val type: TxType, val amountCents: Long, val description: String)

    private val timeRegex = Regex("""(?<![\d/])(?:às\s+|as\s+)?([01]?\d|2[0-3])(?:h([0-5]\d)?|:([0-5]\d))(?![\d/])""", RegexOption.IGNORE_CASE)
    private val dateRegex = Regex("""(?<![\d:])(?:dia\s+)?(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?(?![\d:])""", RegexOption.IGNORE_CASE)
    private val weekdayNames = mapOf(
        "segunda" to DayOfWeek.MONDAY, "terça" to DayOfWeek.TUESDAY, "terca" to DayOfWeek.TUESDAY,
        "quarta" to DayOfWeek.WEDNESDAY, "quinta" to DayOfWeek.THURSDAY, "sexta" to DayOfWeek.FRIDAY,
        "sábado" to DayOfWeek.SATURDAY, "sabado" to DayOfWeek.SATURDAY, "domingo" to DayOfWeek.SUNDAY,
    )
    private val relative = listOf(
        "depois de amanhã" to 2, "depois de amanha" to 2,
        "amanhã" to 1, "amanha" to 1, "hoje" to 0,
    )

    fun parseEvent(input: String, today: LocalDate): EventDraft? {
        var text = " ${input.trim()} "
        if (text.isBlank()) return null
        var date: LocalDate? = null
        var time: LocalTime? = null

        timeRegex.find(text)?.let { m ->
            val hour = m.groupValues[1].toInt()
            val minute = (m.groupValues[2].ifEmpty { m.groupValues[3] }).ifEmpty { "0" }.toInt()
            time = LocalTime(hour, minute)
            text = text.removeRange(m.range)
        }

        dateRegex.find(text)?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt()
            val yearRaw = m.groupValues[3]
            val year = when {
                yearRaw.isEmpty() -> today.year
                yearRaw.length == 2 -> 2000 + yearRaw.toInt()
                else -> yearRaw.toInt()
            }
            runCatching { LocalDate(year, month, day) }.getOrNull()?.let { parsed ->
                // Sem ano informado e data já passou: entende como no ano que vem.
                date = if (yearRaw.isEmpty() && parsed < today) LocalDate(year + 1, month, day) else parsed
                text = text.removeRange(m.range)
            }
        }

        if (date == null) {
            val lower = text.lowercase()
            for ((word, days) in relative) {
                val idx = lower.indexOf(" $word ")
                if (idx >= 0) {
                    date = today.plus(days, DateTimeUnit.DAY)
                    text = text.removeRange(idx, idx + word.length + 1)
                    break
                }
            }
        }

        if (date == null) {
            val lower = text.lowercase()
            for ((word, dow) in weekdayNames) {
                val match = Regex("""\s(?:na\s+|no\s+|nesta\s+|neste\s+|próxima\s+|proxima\s+)?$word(?:-feira)?\s""").find(lower) ?: continue
                var diff = (dow.ordinal - today.dayOfWeek.ordinal + 7) % 7
                if (diff == 0) diff = 7
                date = today.plus(diff, DateTimeUnit.DAY)
                text = text.removeRange(match.range.first, match.range.last)
                break
            }
        }

        val title = text.replace(Regex("\\s+"), " ").trim().trim(',', '-', '.').trim()
            .replaceFirstChar { it.uppercase() }
        if (title.isEmpty()) return null
        return EventDraft(title, date ?: today, time)
    }

    private val moneyRegex = Regex("""^\s*([+-])?\s*(?:R\$\s*)?(\d{1,3}(?:\.\d{3})+(?:,\d{1,2})?|\d+(?:[.,]\d{1,2})?)\s*(.*)$""", RegexOption.IGNORE_CASE)

    /** "+" ou palavras de entrada viram ENTRADA; o resto é SAÍDA (o caso mais comum). */
    fun parseTransaction(input: String): TransactionDraft? {
        val m = moneyRegex.find(input) ?: return null
        val cents = Format.parseMoney(m.groupValues[2]) ?: return null
        if (cents <= 0) return null
        val description = m.groupValues[3].trim().replaceFirstChar { it.uppercase() }
        val incomeWords = listOf("salário", "salario", "recebi", "freela", "venda", "pix recebido", "reembolso")
        val type = when {
            m.groupValues[1] == "+" -> TxType.INCOME
            m.groupValues[1] == "-" -> TxType.EXPENSE
            incomeWords.any { description.lowercase().startsWith(it) } -> TxType.INCOME
            else -> TxType.EXPENSE
        }
        return TransactionDraft(type, cents, description)
    }

    /** Sugere uma categoria pela descrição, para o lançamento rápido já sair organizado. */
    fun guessCategory(description: String, type: TxType): String {
        val d = description.lowercase()
        val rules = if (type == TxType.INCOME) listOf(
            "Salário" to listOf("salário", "salario", "pagamento empresa"),
            "Freela" to listOf("freela", "projeto", "cliente"),
            "Vendas" to listOf("venda", "vendi"),
            "Pix recebido" to listOf("pix"),
            "Investimentos" to listOf("rendimento", "dividendo", "juros"),
        ) else listOf(
            "Alimentação" to listOf("almoço", "almoco", "janta", "lanche", "ifood", "restaurante", "café", "cafe", "pizza", "padaria"),
            "Mercado" to listOf("mercado", "supermercado", "feira", "atacad"),
            "Transporte" to listOf("uber", "99", "gasolina", "combustível", "combustivel", "ônibus", "onibus", "metrô", "metro", "estacionamento"),
            "Moradia" to listOf("aluguel", "condomínio", "condominio"),
            "Contas" to listOf("luz", "água", "agua", "internet", "celular", "telefone", "gás", "gas", "boleto", "fatura"),
            "Saúde" to listOf("farmácia", "farmacia", "remédio", "remedio", "médico", "medico", "dentista", "academia"),
            "Lazer" to listOf("cinema", "show", "bar", "netflix", "spotify", "jogo", "viagem"),
            "Compras" to listOf("roupa", "loja", "amazon", "shopee", "mercado livre", "presente"),
            "Educação" to listOf("curso", "livro", "faculdade", "escola"),
        )
        return rules.firstOrNull { (_, words) -> words.any { d.contains(it) } }?.first ?: Categories.OTHER
    }
}
