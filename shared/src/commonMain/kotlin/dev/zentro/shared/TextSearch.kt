package dev.zentro.shared

/** Buscar e substituir dentro de uma nota (Ctrl+F / Ctrl+R). Ignora maiúsculas/minúsculas. */
object TextSearch {
    /** Posições de todas as ocorrências de [query], sem sobreposição, na ordem do texto. */
    fun matches(text: String, query: String): List<IntRange> {
        if (query.isEmpty()) return emptyList()
        val result = mutableListOf<IntRange>()
        var from = 0
        while (from <= text.length - query.length) {
            val at = text.indexOf(query, from, ignoreCase = true)
            if (at < 0) break
            result += at until at + query.length
            from = at + query.length
        }
        return result
    }

    /** Troca uma ocorrência; devolve o texto novo e onde o cursor deve ficar. */
    fun replaceOne(text: String, range: IntRange, replacement: String): Pair<String, Int> =
        text.replaceRange(range.first, range.last + 1, replacement) to range.first + replacement.length

    /** Troca todas as ocorrências; devolve o texto novo e quantas foram trocadas. */
    fun replaceAll(text: String, query: String, replacement: String): Pair<String, Int> {
        val found = matches(text, query)
        if (found.isEmpty()) return text to 0
        val out = StringBuilder()
        var last = 0
        for (r in found) {
            out.append(text, last, r.first).append(replacement)
            last = r.last + 1
        }
        out.append(text, last, text.length)
        return out.toString() to found.size
    }

    /** Índice da primeira ocorrência a partir de [offset] (para começar do cursor), dando a volta no fim. */
    fun nearest(matches: List<IntRange>, offset: Int): Int =
        matches.indexOfFirst { it.first >= offset }.takeIf { it >= 0 } ?: 0
}
