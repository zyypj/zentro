package dev.zentro.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class MarkdownLinesTest {
    @Test
    fun eachBlockKnowsTheLineWhereItStarts() {
        val text = listOf(
            "# Título", // 0
            "", // 1
            "Parágrafo que", // 2
            "continua aqui.", // 3
            "- item", // 4
            "> citação", // 5
            "> mais citação", // 6
            "```", // 7
            "código", // 8
            "```", // 9
            "| a | b |", // 10
            "|---|---|", // 11
            "| 1 | 2 |", // 12
            "", // 13
            "![foto](zentro:abc)", // 14
        ).joinToString("\n")
        val located = Markdown.parseWithLines(text)
        assertEquals(listOf(0, 2, 4, 5, 7, 10, 14), located.map { it.line })
        assertEquals(Markdown.parse(text), located.map { it.block })
    }
}
