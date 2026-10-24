package app.olauncher

import app.olauncher.helper.Note
import app.olauncher.helper.Notes
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesPinTest {
    private val today = LocalDate.of(2026, 10, 24)
    private fun threeNotes() = listOf(Note(1, "a", 1), Note(2, "b", 2), Note(3, "c", 3))
    private fun ids(notes: List<Note>) = notes.map { it.id }

    @Test
    fun pinnedNoteComesFirstOnCard() {
        val pinned = Notes.setPinned(threeNotes(), 3, true, 1L)
        assertEquals(listOf(3L, 1L, 2L), ids(Notes.cardList(pinned, today)))
        assertEquals(listOf(1L, 2L, 3L), ids(Notes.cardList(Notes.setPinned(pinned, 3, false, 1L), today)))
    }

    @Test
    fun pinnedNotesAreOldestFirstAmongThemselves() {
        val pinned = Notes.setPinned(Notes.setPinned(threeNotes(), 3, true, 1L), 2, true, 1L)
        assertEquals(listOf(2L, 3L, 1L), ids(Notes.cardList(pinned, today)))
    }

    @Test
    fun pinnedFutureNoteStaysInLaterGroup() {
        val tomorrow = today.plusDays(1).toEpochDay()
        val notes = listOf(Note(1, "a", 1), Note(2, "b", 2), Note(3, "c", 3, showFrom = tomorrow, pinned = true))
        assertEquals(listOf(1L, 2L, 3L), ids(Notes.cardList(notes, today)))
        assertEquals(listOf(1L, 2L), ids(Notes.visible(notes, today)))
    }

    @Test
    fun topAndMoreCountWithPin() {
        val pinned = Notes.setPinned(threeNotes(), 3, true, 1L)
        assertEquals(3L, Notes.top(pinned, today)?.id)
        assertEquals(2, Notes.moreCount(pinned, today))
        assertEquals(1L, Notes.top(threeNotes(), today)?.id)
    }

    @Test
    fun featuredPrefersPinnedElseNewest() {
        assertEquals(3L, Notes.featured(Notes.visible(threeNotes(), today))?.id)
        assertEquals(1L, Notes.featured(Notes.visible(Notes.setPinned(threeNotes(), 1, true, 1L), today))?.id)
        assertNull(Notes.featured(emptyList()))
    }

    @Test
    fun roundTripKeepsPinned() {
        val notes = Notes.setPinned(threeNotes(), 2, true, 1L)
        assertEquals(notes, Notes.decode(Notes.encode(notes)))
        assertTrue(Notes.decode(Notes.encode(notes)).first { it.id == 2L }.pinned)
    }

    @Test
    fun unpinnedJsonHasNoPinnedKeyAndOldJsonIsUnpinned() {
        assertFalse(Notes.encode(threeNotes()).contains("\"pinned\""))
        val old = """[{"id":1,"text":"a","created":1,"done":false}]"""
        assertFalse(Notes.decode(old).single().pinned)
    }

    @Test
    fun pruneNeverDropsPinnedNote() {
        var notes = listOf(Note(1, "old pinned", 1, pinned = true))
        for (i in 2L..101L) notes = notes + Note(i, "n$i", i)
        val after = Notes.add(notes, "one more", 500)
        assertTrue(after.any { it.id == 1L && it.pinned })
        assertEquals(101, after.size)
    }

    @Test
    fun setPinnedOnMissingIdKeepsList() {
        assertEquals(threeNotes(), Notes.setPinned(threeNotes(), 99, true, 1L))
    }
}
