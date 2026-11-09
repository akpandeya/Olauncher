package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The web-word overload of the pause picker (epic #835, task 04): the same
 * rotation away from the last shown word, over server text ids. The local
 * `pick(List<Word>, Long?)` keeps its own tests in [PauseWordPickerTest].
 */
class PauseWordPickerWebTest {

    private fun due(id: String): GermanDueWord =
        GermanDueWord(id = id, text = "Wort $id", box = 3, dueDay = 20401L)

    @Test
    fun `pick rotates away from the last shown server id`() {
        val dueWords = listOf(due("srv-1"), due("srv-2"), due("srv-3"))
        val id: (GermanDueWord) -> String = { it.id }

        assertEquals("srv-2", PauseWordPicker.pick(dueWords, "srv-1", id)?.id)
        assertEquals("srv-1", PauseWordPicker.pick(dueWords, "srv-2", id)?.id)
        assertEquals("srv-1", PauseWordPicker.pick(dueWords, "srv-3", id)?.id)

        // Null last shown, or one that is not due any more: the first due word.
        assertEquals("srv-1", PauseWordPicker.pick(dueWords, null, id)?.id)
        assertEquals("srv-1", PauseWordPicker.pick(dueWords, "srv-9", id)?.id)
    }

    @Test
    fun `pick returns null when nothing is due`() {
        val id: (GermanDueWord) -> String = { it.id }
        assertNull(PauseWordPicker.pick(emptyList<GermanDueWord>(), null, id))
        assertNull(PauseWordPicker.pick(emptyList<GermanDueWord>(), "srv-1", id))
    }

    @Test
    fun `pick falls back to the only due word even when it was last shown`() {
        assertEquals("srv-1", PauseWordPicker.pick(listOf(due("srv-1")), "srv-1") { it.id }?.id)
    }
}
