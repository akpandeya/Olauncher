package app.olauncher

import app.olauncher.helper.ChecklistItem
import app.olauncher.helper.Note
import app.olauncher.helper.Notes
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class NotesEditedTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.of(2026, 10, 24)
    private val format = Notes.EditedFormat("edited today", "edited yesterday", "edited %1\$s")
    private val t = 1_761_300_123_456L
    private val list = listOf(Note(1, "Shop", 10, items = listOf(ChecklistItem("milk"), ChecklistItem("eggs"))))

    private fun editedOn(day: LocalDate) = Note(1, "x", 1, edited = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli())

    @Test
    fun newNoteHasNoEditedAndNoLabel() {
        val note = Notes.add(emptyList(), "buy milk", 5L).single()
        assertNull(note.edited)
        assertNull(Notes.editedLabel(note, today, zone, format))
    }

    @Test
    fun toggleItemAndSetPinnedSetEdited() {
        val ticked = Notes.toggleItem(list, 1, 0, t)
        assertEquals(t, ticked.single().edited)
        assertEquals(t + 1, Notes.setPinned(list, 1, true, t + 1).single().edited)
        assertNull(list.single().edited)
    }

    @Test
    fun badToggleLeavesEditedAlone() {
        assertNull(Notes.toggleItem(list, 1, 9, t).single().edited)
        assertNull(Notes.setPinned(list, 99, true, t).single().edited)
    }

    @Test
    fun markDoneKeepsEdited() {
        val ticked = Notes.toggleItem(list, 1, 0, t)
        assertEquals(t, Notes.markDone(ticked, 1).single().edited)
        assertEquals(t, Notes.markDone(Notes.markDone(ticked, 1), 1, done = false).single().edited)
    }

    @Test
    fun labelsForTodayYesterdayAndEarlier() {
        assertEquals("edited today", Notes.editedLabel(editedOn(today), today, zone, format))
        assertEquals("edited yesterday", Notes.editedLabel(editedOn(today.minusDays(1)), today, zone, format))
        assertEquals("edited Wed 21 Oct", Notes.editedLabel(editedOn(today.minusDays(3)), today, zone, format))
    }

    @Test
    fun roundTripKeepsEditedAndOldJsonGivesNull() {
        val ticked = Notes.toggleItem(list, 1, 0, t)
        assertEquals(t, Notes.decode(Notes.encode(ticked)).single().edited)
        assertFalse(Notes.encode(list).contains("edited"))
        assertNull(Notes.decode("""[{"id":1,"text":"a","created":2,"done":false}]""").single().edited)
    }
}
