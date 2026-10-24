package app.olauncher

import app.olauncher.helper.ChecklistItem
import app.olauncher.helper.Note
import app.olauncher.helper.Notes
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotesCardLinesTest {
    private val today = LocalDate.of(2026, 10, 24)
    private fun lines(notes: List<Note>) = Notes.cardLines(Notes.cardList(notes, today), today)

    @Test
    fun noNotesShowsNothing() {
        val l = lines(emptyList())
        assertNull(l.main)
        assertEquals(emptyList<Note>(), l.more)
        assertNull(l.dated)
    }

    @Test
    fun oneNoteHasNoExtraRows() {
        val l = lines(listOf(Note(1, "Milk", 1)))
        assertEquals(1L, l.main?.id)
        assertEquals(emptyList<Note>(), l.more)
        assertNull(l.dated)
    }

    @Test
    fun moreNotesAreCappedAtTwoAndSkipTheMainOne() {
        val notes = (1L..5L).map { Note(it, "n$it", it) }
        val l = lines(notes)
        assertEquals(5L, l.main?.id) // newest is featured
        assertEquals(listOf(1L, 2L), l.more.map { it.id })
    }

    @Test
    fun pinnedIsMainAndKeepsListOrderForTheRest() {
        val notes = listOf(Note(1, "a", 1), Note(2, "b", 2, pinned = true), Note(3, "c", 3))
        val l = lines(notes)
        assertEquals(2L, l.main?.id)
        assertEquals(listOf(1L, 3L), l.more.map { it.id })
    }

    @Test
    fun datedNoteIsShownSeparatelyAndSoonestWins() {
        val notes = listOf(
            Note(1, "Milk", 1),
            Note(2, "Later", 2, showFrom = today.plusDays(9).toEpochDay()),
            Note(3, "Dentist", 3, showFrom = today.plusDays(3).toEpochDay()),
        )
        val l = lines(notes)
        assertEquals(1L, l.main?.id)
        assertEquals(emptyList<Note>(), l.more)
        assertEquals(3L, l.dated?.id)
    }

    @Test
    fun noDatedNoteMeansNoDatedLine() {
        val l = lines(listOf(Note(1, "a", 1), Note(2, "b", 2, showFrom = today.toEpochDay())))
        assertNull(l.dated)
    }

    @Test
    fun checklistProgressText() {
        val note = Note(1, "Shop", 1, items = listOf(ChecklistItem("a", true), ChecklistItem("b"), ChecklistItem("c")))
        assertEquals("1 of 3 done", Notes.progressText(note, "%1\$d of %2\$d done"))
        assertNull(Notes.progressText(Note(2, "plain", 2), "%1\$d of %2\$d done"))
    }
}
