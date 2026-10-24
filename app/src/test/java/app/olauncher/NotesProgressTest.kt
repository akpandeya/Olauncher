package app.olauncher

import app.olauncher.helper.ChecklistItem
import app.olauncher.helper.Note
import app.olauncher.helper.Notes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesProgressTest {
    private fun note(vararg checked: Boolean) =
        Note(1L, "Shopping", 1L, items = checked.mapIndexed { i, c -> ChecklistItem("item $i", c) })

    private val format = "%1\$d of %2\$d"

    @Test fun zeroItems() {
        val n = note()
        assertFalse(Notes.showsProgressBar(n))
        assertEquals(0f, Notes.progressFraction(n), 0f)
        assertNull(Notes.countText(n, format))
    }

    @Test fun allUnchecked() {
        val n = note(false, false, false)
        assertTrue(Notes.showsProgressBar(n))
        assertEquals(0f, Notes.progressFraction(n), 0f)
        assertEquals("0 of 3", Notes.countText(n, format))
    }

    @Test fun partlyChecked() {
        val n = note(true, false, false, true)
        assertEquals(0.5f, Notes.progressFraction(n), 0.0001f)
        assertEquals("2 of 4", Notes.countText(n, format))
    }

    @Test fun allChecked() {
        val n = note(true, true)
        assertEquals(1f, Notes.progressFraction(n), 0f)
        assertEquals("2 of 2", Notes.countText(n, format))
    }
}
