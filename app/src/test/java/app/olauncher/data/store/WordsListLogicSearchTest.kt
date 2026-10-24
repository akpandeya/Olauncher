package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Test

class WordsListLogicSearchTest {
    private val words = listOf(
        Word(id = 1L, text = "Teller", meaning = "plate", dueDay = 0L, addedAt = 0L),
        Word(id = 2L, text = "Gabel", meaning = "fork", dueDay = 0L, addedAt = 0L),
        Word(id = 3L, text = "Mädchen", meaning = "girl", dueDay = 0L, addedAt = 0L),
    )

    @Test
    fun blankQueryKeepsEveryPosition() {
        assertEquals(setOf(0, 1, 2), WordsListLogic.matchingIndices(words, "  "))
    }

    @Test
    fun matchesWordText() {
        assertEquals(setOf(1), WordsListLogic.matchingIndices(words, "gab"))
    }

    @Test
    fun matchesMeaning() {
        assertEquals(setOf(0), WordsListLogic.matchingIndices(words, "plate"))
    }

    @Test
    fun foldsUmlauts() {
        assertEquals(setOf(2), WordsListLogic.matchingIndices(words, "maedchen"))
    }

    @Test
    fun noMatchGivesEmptySet() {
        assertEquals(emptySet<Int>(), WordsListLogic.matchingIndices(words, "zzz"))
    }
}
