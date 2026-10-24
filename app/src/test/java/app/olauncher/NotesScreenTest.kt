package app.olauncher

import app.olauncher.helper.ChecklistItem
import app.olauncher.helper.Note
import app.olauncher.helper.Notes
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesScreenTest {
    private val today = LocalDate.of(2026, 10, 24)
    private val notes = listOf(
        Note(1, "Café", 1),
        Note(2, "Shop", 2, pinned = true, items = listOf(ChecklistItem("milk", true), ChecklistItem("eggs"), ChecklistItem("tea"))),
        Note(3, "done one", 3, done = true),
        Note(4, "Dentist", 4, showFrom = today.plusDays(3).toEpochDay()),
        Note(5, "Tax", 5, pinned = true),
    )

    @Test
    fun sectionsSplitPinnedFirstAndSkipDone() {
        val s = Notes.sections(notes, "", today)
        assertEquals(listOf(2L, 5L), s.pinned.map { it.id })
        assertEquals(listOf(1L, 4L), s.rest.map { it.id })
    }

    @Test
    fun sectionsUseSearchLogic() {
        val s = Notes.sections(notes, "cafe", today)
        assertTrue(s.pinned.isEmpty())
        assertEquals(listOf(1L), s.rest.map { it.id })
        assertEquals(listOf(2L), Notes.sections(notes, "eggs", today).pinned.map { it.id })
        assertTrue(Notes.sections(notes, "zzz", today).isEmpty)
        assertTrue(Notes.sections(emptyList(), "", today).isEmpty)
    }

    @Test
    fun countLineCountsOpenAndPinned() {
        assertEquals("4 open · 2 pinned", Notes.countLine(notes, "%1\$d open · %2\$d pinned"))
        assertEquals("0 open · 0 pinned", Notes.countLine(emptyList(), "%1\$d open · %2\$d pinned"))
    }

    @Test
    fun doneCountAndDated() {
        assertEquals(1, Notes.doneCount(notes[1]))
        assertEquals(0, Notes.doneCount(notes[0]))
        assertTrue(Notes.isDated(notes[3]))
        assertFalse(Notes.isDated(notes[0]))
    }

    @Test
    fun tickingAnItemUpdatesProgress() {
        val after = Notes.toggleItem(notes, 2, 1, 99L)
        assertEquals(2, Notes.doneCount(after[1]))
        assertEquals(99L, after[1].edited)
    }
}
