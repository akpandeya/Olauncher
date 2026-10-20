package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordSuggestTest {

    @Test
    fun `typo finds Hermann`() {
        assertEquals(listOf("Hermann"), WordSuggest.suggest(listOf("Hermann", "Tisch"), "hermwn"))
    }

    @Test
    fun `ue folds to umlaut`() {
        assertEquals(listOf("Müll"), WordSuggest.suggest(listOf("Müll", "Tisch"), "Muell"))
    }

    @Test
    fun `prefix ranks before substring and fuzzy`() {
        val saved = listOf("Hermel", "Schermann", "Hermann")
        assertEquals(listOf("Hermann", "Hermel", "Schermann").sorted(), WordSuggest.suggest(saved, "Herm").sorted())
        val ranked = WordSuggest.suggest(listOf("Kerman", "Hermann", "Xhermann"), "herma")
        assertEquals(listOf("Hermann", "Xhermann", "Kerman"), ranked)
    }

    @Test
    fun `empty input returns empty`() {
        assertTrue(WordSuggest.suggest(listOf("Hermann"), "").isEmpty())
        assertTrue(WordSuggest.suggest(listOf("Hermann"), "   ").isEmpty())
    }

    @Test
    fun `exact matches are not suggested`() {
        assertTrue(WordSuggest.suggest(listOf("Müll"), "müll").isEmpty())
        assertTrue(WordSuggest.suggest(listOf("Hermann"), "HERMANN").isEmpty())
    }

    @Test
    fun `returns at most three`() {
        val saved = listOf("Haus1", "Haus2", "Haus3", "Haus4")
        assertEquals(3, WordSuggest.suggest(saved, "Haus").size)
    }

    @Test
    fun `far words are ignored`() {
        assertTrue(WordSuggest.suggest(listOf("Tisch"), "hermwn").isEmpty())
    }

    @Test
    fun `two-item list suggests per item and skips exact matches`() {
        val saved = listOf("Hermann", "Müll")
        val result = WordSuggest.suggestForItems(saved, listOf("hermwn", "müll"))
        assertEquals(mapOf("hermwn" to listOf("Hermann")), result)
        val both = WordSuggest.suggestForItems(saved, listOf("hermwn", "Mull"))
        assertEquals(mapOf("hermwn" to listOf("Hermann"), "Mull" to listOf("Müll")), both)
    }
}
