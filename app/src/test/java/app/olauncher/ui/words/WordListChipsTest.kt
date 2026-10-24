package app.olauncher.ui.words

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordListChipsTest {

    @Test
    fun toggleTicksAnUntickedList() {
        val selected = mutableSetOf("b")
        assertTrue(WordListChips.toggle(selected, "a"))
        assertEquals(setOf("a", "b"), selected)
    }

    @Test
    fun toggleUnticksATickedListAndLeavesOthers() {
        val selected = mutableSetOf("a", "b")
        assertFalse(WordListChips.toggle(selected, "a"))
        assertEquals(setOf("b"), selected)
    }

    @Test
    fun toggleTwiceRestoresTheSet() {
        val selected = mutableSetOf("x")
        WordListChips.toggle(selected, "y")
        WordListChips.toggle(selected, "y")
        assertEquals(setOf("x"), selected)
    }
}
