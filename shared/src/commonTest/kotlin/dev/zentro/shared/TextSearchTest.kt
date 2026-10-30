package dev.zentro.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TextSearchTest {
    private val text = "Bed Wars solo. Modo SOLO e duplas; solo de novo."

    @Test
    fun findsAllIgnoringCase() {
        val found = TextSearch.matches(text, "solo")
        assertEquals(3, found.size)
        assertEquals("solo", text.substring(found[0]))
        assertEquals("SOLO", text.substring(found[1]))
    }

    @Test
    fun emptyQueryFindsNothing() {
        assertTrue(TextSearch.matches(text, "").isEmpty())
        assertTrue(TextSearch.matches("", "x").isEmpty())
    }

    @Test
    fun overlappingMatchesAreNotCountedTwice() {
        assertEquals(2, TextSearch.matches("aaaa", "aa").size)
    }

    @Test
    fun replaceOneMovesTheCursorAfterTheReplacement() {
        val first = TextSearch.matches(text, "solo")[0]
        val (newText, cursor) = TextSearch.replaceOne(text, first, "1v1")
        assertTrue(newText.startsWith("Bed Wars 1v1."))
        assertEquals(first.first + 3, cursor)
    }

    @Test
    fun replaceAllCountsReplacements() {
        val (newText, count) = TextSearch.replaceAll(text, "solo", "1v1")
        assertEquals(3, count)
        assertEquals("Bed Wars 1v1. Modo 1v1 e duplas; 1v1 de novo.", newText)
        assertEquals(text to 0, TextSearch.replaceAll(text, "trios", "x"))
    }

    @Test
    fun nearestStartsFromTheCursorAndWrapsAround() {
        val found = TextSearch.matches(text, "solo")
        assertEquals(1, TextSearch.nearest(found, 14))
        assertEquals(0, TextSearch.nearest(found, text.length))
    }
}
