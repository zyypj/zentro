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

    fun parse(content: String): List<MdBlock> {
        val blocks = mutableListOf<MdBlock>()
        val paragraph = mutableListOf<String>()
        val code = mutableListOf<String>()
        var inCode = false

        fun flush() {
            if (paragraph.isNotEmpty()) {
                blocks += MdBlock.Paragraph(paragraph.joinToString("\n"))
                paragraph.clear()
            }
        }

        content.lines().forEachIndexed { index, raw ->
            if (raw.trimStart().startsWith("```")) {
                if (inCode) {
                    blocks += MdBlock.Code(code.joinToString("\n"))
                    code.clear()
                } else {
                    flush()
                }
                inCode = !inCode
                return@forEachIndexed
            }
            if (inCode) {
                code += raw
                return@forEachIndexed
            }
            val indent = raw.takeWhile { it == ' ' || it == '\t' }.count { it == '\t' } * 2 + raw.takeWhile { it == ' ' }.length / 2
            when {
                raw.isBlank() -> flush()
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
        }
        if (inCode && code.isNotEmpty()) blocks += MdBlock.Code(code.joinToString("\n"))
        flush()
        return blocks
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

    fun inline(text: String): List<MdSpan> {
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
