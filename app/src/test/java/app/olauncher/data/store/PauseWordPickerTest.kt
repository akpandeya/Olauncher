package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PauseWordPickerTest {

    private fun word(id: Long, text: String): Word {
        return Word(
            id = id,
            text = text,
            dueDay = 100L,
            addedAt = 1000L
        )
    }

    @Test
    fun `pick returns null when nothing is due`() {
        val resultNullLast = PauseWordPicker.pick(emptyList(), null)
        assertNull(resultNullLast)

        val resultWithLast = PauseWordPicker.pick(emptyList(), 42L)
        assertNull(resultWithLast)
    }

    @Test
    fun `pick returns the first due word that is not the last one shown`() {
        val w1 = word(1L, "Apfel")
        val w2 = word(2L, "Birne")
        val w3 = word(3L, "Kirsche")
        val due = listOf(w1, w2, w3)

        // When last shown was w1, pick returns w2
        assertEquals(w2, PauseWordPicker.pick(due, 1L))

        // When last shown was w2, pick returns w1
        assertEquals(w1, PauseWordPicker.pick(due, 2L))

        // When last shown was w3, pick returns w1
        assertEquals(w1, PauseWordPicker.pick(due, 3L))

        // When last shown was null, pick returns the first due word (w1)
        assertEquals(w1, PauseWordPicker.pick(due, null))

        // When last shown was not in the due list, pick returns the first due word (w1)
        assertEquals(w1, PauseWordPicker.pick(due, 999L))
    }

    @Test
    fun `pick falls back to the last one if it is the only one due`() {
        val onlyWord = word(1L, "Apfel")
        val due = listOf(onlyWord)

        val result = PauseWordPicker.pick(due, 1L)
        assertEquals(onlyWord, result)
    }
}
