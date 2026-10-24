package app.olauncher.ui.today.cards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WordsCardMenuTest {

    @Test
    fun reviewIsFirstWhenWordsAreDue() {
        assertEquals(
            listOf(WordsCardMenuItem.REVIEW, WordsCardMenuItem.WORDS, WordsCardMenuItem.LISTS),
            WordsCardLogic.menuItems(7),
        )
    }

    @Test
    fun reviewIsHiddenWhenNothingIsDue() {
        val items = WordsCardLogic.menuItems(0)
        assertFalse(WordsCardMenuItem.REVIEW in items)
        assertEquals(listOf(WordsCardMenuItem.WORDS, WordsCardMenuItem.LISTS), items)
    }

    @Test
    fun wordsThenListsAreAlwaysLast() {
        for (due in listOf(0, 1, 7, 250)) {
            assertEquals(
                listOf(WordsCardMenuItem.WORDS, WordsCardMenuItem.LISTS),
                WordsCardLogic.menuItems(due).takeLast(2),
            )
        }
    }
}
