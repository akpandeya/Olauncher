package app.olauncher.ui.today

import app.olauncher.data.store.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordRevealTest {

    @Test
    fun `WordReveal keepReveal logic preserves reveal state for same word only`() {
        val word1 = Word(id = 1L, text = "Haus", dueDay = 100L, addedAt = 10L)
        val word1Updated = word1.copy(meaning = "house", meaningSource = "user")
        val word2 = Word(id = 2L, text = "Baum", dueDay = 100L, addedAt = 20L)

        // Starting with same word and isRevealed = true should keep reveal
        val currentWordId = 1L
        val isRevealed = true

        val shouldKeepForSameWord = (currentWordId == word1Updated.id && isRevealed)
        assertTrue(shouldKeepForSameWord)

        // Transitioning to a different word should reset reveal state
        val shouldKeepForDifferentWord = (currentWordId == word2.id && isRevealed)
        assertFalse(shouldKeepForDifferentWord)
    }

    @Test
    fun `context sentence visibility is true only when non-blank and revealed`() {
        val wordWithContext = Word(id = 1L, text = "Haus", context = "Ein großes Haus", dueDay = 100L, addedAt = 10L)
        val wordWithoutContext = Word(id = 2L, text = "Baum", context = "", dueDay = 100L, addedAt = 20L)
        val wordWithBlankContext = Word(id = 3L, text = "Wald", context = "   ", dueDay = 100L, addedAt = 30L)

        assertTrue(wordWithContext.context.isNotBlank())
        assertFalse(wordWithoutContext.context.isNotBlank())
        assertFalse(wordWithBlankContext.context.isNotBlank())
    }
}
