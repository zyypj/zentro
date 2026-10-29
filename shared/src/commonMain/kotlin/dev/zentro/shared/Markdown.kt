package dev.zentro.shared

/** Blocos de um documento Markdown (o subconjunto que o Zentro usa nas anotações). */
sealed interface MdBlock {
    data class Heading(val level: Int, val text: String) : MdBlock
    data class Paragraph(val text: String) : MdBlock
    data class Bullet(val text: String, val indent: Int) : MdBlock
    data class Numbered(val number: Int, val text: String, val indent: Int) : MdBlock
    /** [line] é a linha no texto original, usada para marcar/desmarcar a tarefa. */
    data class Task(val line: Int, val text: String, val done: Boolean, val indent: Int) : MdBlock
    data class Quote(val text: String) : MdBlock
    data class Code(val text: String) : MdBlock
    data object Divider : MdBlock
    /** Tabela no formato GFM (`| a | b |` + `|---|---|`), o mesmo que IAs como o Claude produzem. */
    data class Table(val header: List<String>, val aligns: List<Align>, val rows: List<List<String>>) : MdBlock
    /** Imagem sozinha numa linha: `![legenda](endereço)`. */
    data class Image(val alt: String, val src: String) : MdBlock

    enum class Align { LEFT, CENTER, RIGHT }
}

/** Trechos de formatação dentro de uma linha. */
sealed interface MdSpan {
    data class Text(val text: String) : MdSpan
    data class Bold(val text: String) : MdSpan
    data class Italic(val text: String) : MdSpan
    data class Strike(val text: String) : MdSpan
    data class Code(val text: String) : MdSpan
    data class Link(val target: String, val label: String) : MdSpan
    data class Tag(val name: String) : MdSpan
    data class Url(val url: String) : MdSpan
}

object Markdown {
    private val heading = Regex("""^(#{1,6})\s+(.*)$""")
    private val task = Regex("""^(\s*)[-*+] \[([ xX])] ?(.*)$""")
    private val bullet = Regex("""^(\s*)[-*+]\s+(.*)$""")
    private val numbered = Regex("""^(\s*)(\d+)[.)]\s+(.*)$""")
    private val divider = Regex("""^\s*(-{3,}|\*{3,}|_{3,})\s*$""")
    private val tableSeparator = Regex("""^\s*\|?\s*:?-+:?\s*(\|\s*:?-+:?\s*)*\|?\s*$""")
    private val image = Regex("""^\s*!\[([^\]]*)]\(\s*([^)\s]+)(?:\s+"[^"]*")?\s*\)\s*$""")
    private val inlineImage = Regex("""!\[([^\]]*)]\(\s*[^)\s]+(?:\s+"[^"]*")?\s*\)""")

    /** Endereço de imagem guardada no servidor do Zentro: `zentro:<id>`. */
    const val FILE_SCHEME = "zentro:"

    fun fileId(src: String): String? =
        src.takeIf { it.startsWith(FILE_SCHEME) }?.removePrefix(FILE_SCHEME)?.takeIf { it.isNotBlank() }

    /** Todas as imagens (linhas `![...](...)`) de uma nota, na ordem em que aparecem. */
    fun images(content: String): List<MdBlock.Image> = parse(content).filterIsInstance<MdBlock.Image>()

    fun parse(content: String): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        val paragraph = mutableListOf<String>()
        val code = mutableListOf<String>()
        var inCode = false
        val lines = content.lines()

        fun flush() {
            if (paragraph.isNotEmpty()) {
                blocks += MdBlock.Paragraph(paragraph.joinToString("\n"))
                paragraph.clear()
            }
        }

        var index = 0
        while (index < lines.size) {
            val raw = lines[index]
            if (raw.trimStart().startsWith("```")) {
                if (inCode) {
                    blocks += MdBlock.Code(code.joinToString("\n"))
                    code.clear()
                } else {
                    flush()
                }
                inCode = !inCode
                index++
                continue
            }
            if (inCode) {
                code += raw
                index++
                continue
            }

            // Tabela: uma linha com "|" seguida da linha separadora "|---|---|".
            if ('|' in raw && index + 1 < lines.size && '|' in lines[index + 1] && tableSeparator.matches(lines[index + 1])) {
                flush()
                val header = cells(raw)
                val aligns = cells(lines[index + 1]).map { cell ->
                    val t = cell.trim()
                    when {
                        t.startsWith(":") && t.endsWith(":") -> MdBlock.Align.CENTER
                        t.endsWith(":") -> MdBlock.Align.RIGHT
                        else -> MdBlock.Align.LEFT
                    }
                }
                index += 2
                val rows = mutableListOf<List<String>>()
                while (index < lines.size && lines[index].isNotBlank() && '|' in lines[index]) {
                    val row = cells(lines[index])
                    rows += List(header.size) { row.getOrElse(it) { "" } }
                    index++
                }
                blocks += MdBlock.Table(header, List(header.size) { aligns.getOrElse(it) { MdBlock.Align.LEFT } }, rows)
                continue
            }

            val indent = raw.takeWhile { it == ' ' || it == '\t' }.count { it == '\t' } * 2 + raw.takeWhile { it == ' ' }.length / 2
            when {
                raw.isBlank() -> flush()
                image.matches(raw) -> {
                    flush()
                    val m = image.matchEntire(raw)!!
                    blocks += MdBlock.Image(m.groupValues[1].trim(), m.groupValues[2].trim())
                }
                divider.matches(raw) -> { flush(); blocks += MdBlock.Divider }
                heading.matches(raw) -> {
                    flush()
                    val m = heading.matchEntire(raw)!!
                    blocks += MdBlock.Heading(m.groupValues[1].length, m.groupValues[2].trim())
                }
                task.matches(raw) -> {
                    flush()
                    val m = task.matchEntire(raw)!!
                    blocks += MdBlock.Task(index, m.groupValues[3], m.groupValues[2] != " ", indent)
                }
                bullet.matches(raw) -> {
                    flush()
                    blocks += MdBlock.Bullet(bullet.matchEntire(raw)!!.groupValues[2], indent)
                }
                numbered.matches(raw) -> {
                    flush()
                    val m = numbered.matchEntire(raw)!!
                    blocks += MdBlock.Numbered(m.groupValues[2].toInt(), m.groupValues[3], indent)
                }
                raw.trimStart().startsWith(">") -> {
                    flush()
                    val text = raw.trimStart().removePrefix(">").trimStart()
                    val last = blocks.lastOrNull()
                    if (last is MdBlock.Quote) blocks[blocks.lastIndex] = MdBlock.Quote(last.text + "\n" + text)
                    else blocks += MdBlock.Quote(text)
                }
                else -> paragraph += raw.trimEnd()
            }
            index++
        }
        if (inCode && code.isNotEmpty()) blocks += MdBlock.Code(code.joinToString("\n"))
        flush()
        return blocks
    }

    /** Divide uma linha de tabela nas células, respeitando `\|` e barras dentro de `código`. */
    private fun cells(line: String): List<String> {
        var text = line.trim()
        if (text.startsWith("|")) text = text.drop(1)
        if (text.endsWith("|") && !text.endsWith("\\|")) text = text.dropLast(1)
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inCode = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c == '\\' && i + 1 < text.length && text[i + 1] == '|' -> {
                    current.append('|')
                    i++
                }
                c == '`' -> {
                    inCode = !inCode
                    current.append(c)
                }
                c == '|' && !inCode -> {
                    result += current.toString().trim()
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        result += current.toString().trim()
        return result
    }

    private val inline = Regex(
        listOf(
            """\[\[([^\[\]|#]+)(?:#[^\[\]|]*)?(?:\|([^\[\]]+))?]]""",
            """\*\*([^*]+)\*\*""",
            """~~([^~]+)~~""",
            """`([^`]+)`""",
            """(?<![\w*])\*([^*\s][^*]*)\*(?!\w)""",
            """(?<!\w)_([^_\s][^_]*)_(?!\w)""",
            """(https?://[^\s)\]]+)""",
            Notes.tagRegex.pattern,
        ).joinToString("|") { "($it)" },
    )

    fun inline(rawText: String): List<MdSpan> {
        // Imagem no meio do texto vira só a legenda (imagens aparecem quando estão numa linha própria).
        val text = rawText.replace(inlineImage) { "🖼 " + it.groupValues[1].ifBlank { "imagem" } }
        val spans = mutableListOf<MdSpan>()
        var last = 0
        for (m in inline.findAll(text)) {
            if (m.range.first > last) spans += MdSpan.Text(text.substring(last, m.range.first))
            val g = m.groupValues
            spans += when {
                g[1].isNotEmpty() -> MdSpan.Link(g[2].trim(), g[3].trim().ifEmpty { g[2].trim() })
                g[4].isNotEmpty() -> MdSpan.Bold(g[5])
                g[6].isNotEmpty() -> MdSpan.Strike(g[7])
                g[8].isNotEmpty() -> MdSpan.Code(g[9])
                g[10].isNotEmpty() -> MdSpan.Italic(g[11])
                g[12].isNotEmpty() -> MdSpan.Italic(g[13])
                g[14].isNotEmpty() -> MdSpan.Url(g[15])
                else -> MdSpan.Tag(g[17])
            }
            last = m.range.last + 1
        }
        if (last < text.length) spans += MdSpan.Text(text.substring(last))
        return spans
    }
}
