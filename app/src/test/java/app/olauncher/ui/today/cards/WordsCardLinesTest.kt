package app.olauncher.ui.today.cards

import app.olauncher.data.store.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordsCardLinesTest {
    private fun word(text: String, meaning: String = "") = Word(id = 1, text = text, meaning = meaning, dueDay = 0, addedAt = 0)

    @Test
    fun noWordsShowsHintAndNoCounts() {
        val l = WordsCardLogic.lines(total = 0, due = 0, next = null)
        assertTrue(l.showHint)
        assertFalse(l.showAllDone)
        assertFalse(l.showNext)
        assertFalse(l.showCounts)
        assertFalse(l.showBoxBar)
        assertFalse(l.showDayStrip)
    }

    @Test
    fun allDoneShowsAllDoneAndCounts() {
        val l = WordsCardLogic.lines(total = 10, due = 0, next = null)
        assertTrue(l.showAllDone)
        assertFalse(l.showHint)
        assertFalse(l.showNext)
        assertTrue(l.showCounts)
        assertTrue(l.showBoxBar)
        assertTrue(l.showDayStrip)
    }

    @Test
    fun nextWordWithMeaning() {
        val l = WordsCardLogic.lines(total = 10, due = 7, next = word("verschieden", " different "))
        assertEquals("verschieden", l.nextWord)
        assertEquals("different", l.nextMeaning)
        assertFalse(l.showAllDone)
        assertFalse(l.showHint)
        assertTrue(l.showCounts)
        assertTrue(l.showBoxBar)
        assertTrue(l.showDayStrip)
    }

    @Test
    fun nextWordWithBlankMeaningHidesMeaning() {
        val l = WordsCardLogic.lines(total = 3, due = 1, next = word("Fernweh", "   "))
        assertEquals("Fernweh", l.nextWord)
        assertNull(l.nextMeaning)
    }
}
