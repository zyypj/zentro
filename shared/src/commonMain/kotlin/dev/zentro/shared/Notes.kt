package dev.zentro.shared

import kotlinx.datetime.LocalDate

/**
 * A "inteligência" das anotações, compartilhada entre os apps: links `[[...]]`, backlinks,
 * tags `#...`, tarefas `- [ ]`, busca e sugestões de link enquanto você digita.
 */
object Notes {
    const val DEFAULT_FOLDER = "Geral"
    const val DAILY_FOLDER = "Diário"
    val defaultFolders = listOf("Trabalho", "Faculdade", "Projetos", "Pessoal")

    data class Link(val target: String, val label: String)
    data class Task(val line: Int, val text: String, val done: Boolean)
    data class SearchHit(val note: Note, val snippet: String, val score: Int)

    private val linkRegex = Regex("""\[\[([^\[\]|#]+)(?:#[^\[\]|]*)?(?:\|([^\[\]]+))?]]""")
    internal val tagRegex = Regex("""(?<![\w#&/])#([\p{L}\p{N}_/-]*[\p{L}_/-][\p{L}\p{N}_/-]*)""")
    private val taskRegex = Regex("""^(\s*)[-*+] \[([ xX])] (.*)$""")

    /** Linhas fora de blocos de código (```), com o índice original. */
    private fun textLines(content: String): List<IndexedValue<String>> {
        var inCode = false
        return content.lines().withIndex().filter { (_, line) ->
            if (line.trimStart().startsWith("```")) {
                inCode = !inCode
                false
            } else {
                !inCode
            }
        }
    }

    fun links(content: String): List<Link> = textLines(content).flatMap { (_, line) ->
        linkRegex.findAll(line).map { m ->
            val target = m.groupValues[1].trim()
            Link(target, m.groupValues[2].trim().ifEmpty { target })
        }
    }

    /** Tags em minúsculas, sem o "#". */
    fun tags(content: String): Set<String> = textLines(content).flatMap { (_, line) ->
        tagRegex.findAll(line).map { it.groupValues[1].trimEnd('/', '-').lowercase() }
    }.filter { it.isNotEmpty() }.toSet()

    fun tasks(content: String): List<Task> = textLines(content).mapNotNull { (index, line) ->
        taskRegex.matchEntire(line)?.let { Task(index, it.groupValues[3], it.groupValues[2] != " ") }
    }

    /** Marca/desmarca a tarefa da linha [line] e devolve o novo conteúdo. */
    fun toggleTask(content: String, line: Int): String {
        val lines = content.lines().toMutableList()
        val current = lines.getOrNull(line) ?: return content
        val m = taskRegex.matchEntire(current) ?: return content
        val mark = if (m.groupValues[2] == " ") "x" else " "
        lines[line] = current.replaceFirst(Regex("""\[[ xX]]"""), "[$mark]")
        return lines.joinToString("\n")
    }

    private fun key(title: String) = title.trim().lowercase()

    fun findByTitle(notes: Collection<Note>, title: String): Note? =
        notes.firstOrNull { !it.deleted && key(it.title) == key(title) }

    /** Notas que apontam para [note] com `[[Título]]`. */
    fun backlinks(notes: Collection<Note>, note: Note): List<Note> {
        val target = key(note.title)
        if (target.isEmpty()) return emptyList()
        return notes.filter { other ->
            !other.deleted && other.id != note.id && links(other.content).any { key(it.target) == target }
        }.sortedByDescending { it.updatedAt }
    }

    /** Pares (origem, destino) de todos os links que apontam para notas existentes — base do mapa. */
    fun graph(notes: Collection<Note>): List<Pair<String, String>> {
        val byTitle = notes.filter { !it.deleted }.associateBy { key(it.title) }
        return notes.filter { !it.deleted }.flatMap { note ->
            links(note.content).mapNotNull { byTitle[key(it.target)]?.id }
                .filter { it != note.id }.distinct().map { note.id to it }
        }
    }

    /** Texto puro da primeira linha com conteúdo, para a lista de notas. */
    fun preview(content: String, max: Int = 120): String {
        val line = textLines(content).map { it.value.trim() }.firstOrNull { it.isNotEmpty() && it != "---" } ?: return ""
        return plain(line).take(max)
    }

    fun plain(text: String): String = text
        .replace(Regex("""^#{1,6}\s+"""), "")
        .replace(Regex("""^\s*[-*+] \[[ xX]]\s*"""), "")
        .replace(Regex("""^\s*([-*+]|\d+\.)\s+"""), "")
        .replace(Regex("""^>\s?"""), "")
        .replace(linkRegex) { it.groupValues[2].ifEmpty { it.groupValues[1] } }
        .replace("**", "").replace("~~", "").replace("`", "")

    /** Busca por título, tag e conteúdo, com trecho em volta do termo encontrado. */
    fun search(notes: Collection<Note>, query: String): List<SearchHit> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val tagQuery = q.removePrefix("#")
        return notes.filter { !it.deleted }.mapNotNull { note ->
            val title = note.title.lowercase()
            val content = note.content.lowercase()
            var score = 0
            if (title == q) score += 100
            if (title.startsWith(q)) score += 50
            if (title.contains(q)) score += 30
            if (tags(note.content).any { it.startsWith(tagQuery) }) score += 25
            val index = content.indexOf(q)
            if (index >= 0) score += 10
            if (note.folder.lowercase().contains(q)) score += 5
            if (score == 0) return@mapNotNull null
            val snippet = if (index >= 0) {
                val start = (index - 40).coerceAtLeast(0)
                (if (start > 0) "…" else "") + plain(note.content.substring(start, (index + q.length + 60).coerceAtMost(note.content.length))).replace('\n', ' ')
            } else preview(note.content)
            SearchHit(note, snippet, score)
        }.sortedWith(compareByDescending<SearchHit> { it.score }.thenByDescending { it.note.updatedAt })
    }

    /**
     * Se o texto antes do cursor termina com `[[algo`, devolve o "algo" — o app mostra sugestões
     * de notas para completar o link.
     */
    fun pendingLink(textBeforeCursor: String): String? {
        val start = textBeforeCursor.lastIndexOf("[[")
        if (start < 0) return null
        val partial = textBeforeCursor.substring(start + 2)
        if (partial.contains("]]") || partial.contains('\n') || partial.length > 60) return null
        return partial
    }

    fun linkSuggestions(notes: Collection<Note>, partial: String, excludeId: String? = null, limit: Int = 6): List<Note> {
        val q = partial.trim().lowercase()
        return notes.filter { !it.deleted && it.id != excludeId && it.title.isNotBlank() }
            .filter { q.isEmpty() || it.title.lowercase().contains(q) }
            .sortedWith(compareByDescending<Note> { it.title.lowercase().startsWith(q) }.thenByDescending { it.updatedAt })
            .take(limit)
    }

    fun dailyTitle(date: LocalDate): String = date.toString()

    /** Todas as pastas usadas, mais as padrão, incluindo as pastas-pai de subpastas. */
    fun folders(notes: Collection<Note>): List<String> {
        val used = notes.filter { !it.deleted }.map { it.folder.trim().trim('/') }.filter { it.isNotEmpty() }
        val withParents = used.flatMap { path ->
            val parts = path.split('/')
            parts.indices.map { parts.subList(0, it + 1).joinToString("/") }
        }
        return (defaultFolders + withParents).distinct().sortedBy { it.lowercase() }
    }

    fun inFolder(note: Note, folder: String): Boolean =
        note.folder == folder || note.folder.startsWith("$folder/")
}

/** Modelos prontos para começar uma nota sem encarar a página em branco. */
object NoteTemplates {
    data class Template(val name: String, val icon: String, val folder: String?, val body: (date: LocalDate) -> String)

    val all = listOf(
        Template("Em branco", "📝", null) { "" },
        Template("Reunião", "🤝", "Trabalho") { date ->
            "**Data:** ${Format.shortDate(date)}/${date.year}\n**Participantes:** \n\n## Pauta\n- \n\n## Decisões\n- \n\n## Próximos passos\n- [ ] \n\n#reunião"
        },
        Template("Aula", "🎓", "Faculdade") { date ->
            "**Matéria:** \n**Data:** ${Format.shortDate(date)}/${date.year}\n\n## Conteúdo\n- \n\n## Dúvidas\n- \n\n## Tarefas\n- [ ] \n\n#aula"
        },
        Template("Projeto", "🚀", "Projetos") { _ ->
            "## Objetivo\n\n\n## Tarefas\n- [ ] \n\n## Ideias\n- \n\n## Links\n- \n\n#projeto"
        },
        Template("Ideia", "💡", null) { _ -> "## A ideia\n\n\n## Por que vale a pena\n- \n\n#ideia" },
    )

    fun daily(date: LocalDate): String =
        "# ${Format.longDate(date).replaceFirstChar { it.uppercase() }}\n\n## Foco do dia\n- [ ] \n\n## Anotações\n\n\n## Gratidão\n- "
}
