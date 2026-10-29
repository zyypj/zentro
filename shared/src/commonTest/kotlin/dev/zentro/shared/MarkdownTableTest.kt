package dev.zentro.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarkdownTableTest {
    @Test
    fun parsesTableLikeTheOnesAiWrites() {
        val md = """
            | Slot visual | Item |
            |---|---|
            | 5 | `&aMenu do Bed Wars` |
            | 9 | `&aVoltar ao Lobby` |
        """.trimIndent()
        val table = Markdown.parse(md).single() as MdBlock.Table
        assertEquals(listOf("Slot visual", "Item"), table.header)
        assertEquals(listOf(listOf("5", "`&aMenu do Bed Wars`"), listOf("9", "`&aVoltar ao Lobby`")), table.rows)
    }

    @Test
    fun alignmentsShortRowsAndEscapedPipes() {
        val md = "Antes\n\n| A | B | C |\n|:--|:-:|--:|\n| 1 | a \\| b |\n| `x|y` | 2 | 3 |\n\nDepois"
        val blocks = Markdown.parse(md)
        assertEquals(MdBlock.Paragraph("Antes"), blocks[0])
        val table = blocks[1] as MdBlock.Table
        assertEquals(listOf(MdBlock.Align.LEFT, MdBlock.Align.CENTER, MdBlock.Align.RIGHT), table.aligns)
        assertEquals(listOf("1", "a | b", ""), table.rows[0])
        assertEquals("`x|y`", table.rows[1][0])
        assertEquals(MdBlock.Paragraph("Depois"), blocks[2])
    }

    @Test
    fun pipeWithoutSeparatorIsJustText() {
        assertTrue(Markdown.parse("a | b sem tabela").single() is MdBlock.Paragraph)
    }

    @Test
    fun imagesOnTheirOwnLine() {
        val blocks = Markdown.parse("Print do menu:\n\n![Menu do Bed Wars](zentro:abc123)\n\n![](https://exemplo.com/a.png \"título\")")
        assertEquals(MdBlock.Image("Menu do Bed Wars", "zentro:abc123"), blocks[1])
        assertEquals(MdBlock.Image("", "https://exemplo.com/a.png"), blocks[2])
        assertEquals("abc123", Markdown.fileId("zentro:abc123"))
        assertNull(Markdown.fileId("https://exemplo.com/a.png"))
        assertEquals(2, Markdown.images("![a](zentro:1)\ntexto\n![b](zentro:2)").size)
    }

    @Test
    fun inlineImageShowsCaption() {
        assertEquals(listOf(MdSpan.Text("veja 🖼 print aqui")), Markdown.inline("veja ![print](zentro:x) aqui"))
    }

    @Test
    fun dividerAfterTextWithPipeIsNotATable() {
        val blocks = Markdown.parse("a | b\n---")
        assertTrue(blocks[0] is MdBlock.Paragraph)
        assertEquals(MdBlock.Divider, blocks[1])
    }
}
