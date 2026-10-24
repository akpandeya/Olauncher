package app.olauncher

import app.olauncher.helper.ChecklistItem
import app.olauncher.helper.Note
import app.olauncher.helper.NoteViewRules
import app.olauncher.helper.Notes
import app.olauncher.helper.NotesStore
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteViewTest {
    private val today = LocalDate.of(2026, 10, 24)
    private val checklist = Note(1, "Shop", 1, items = listOf(ChecklistItem("milk", true), ChecklistItem("bread")))
    private val plain = Note(2, "Call", 2, body = "about heat")

    @Test
    fun dueChipOnlyWhenDateIsTodayOrPast() {
        assertFalse(NoteViewRules.isDue(plain, today))
        assertFalse(NoteViewRules.isDue(plain.copy(showFrom = today.plusDays(1).toEpochDay()), today))
        assertTrue(NoteViewRules.isDue(plain.copy(showFrom = today.toEpochDay()), today))
        assertTrue(NoteViewRules.isDue(plain.copy(showFrom = today.minusDays(3).toEpochDay()), today))
    }

    @Test
    fun doneIsOnlyForPlainNotes() {
        assertTrue(NoteViewRules.canMarkDone(plain))
        assertFalse(NoteViewRules.canMarkDone(checklist))
    }

    @Test
    fun titleEditCleansAndMarksEdited() {
        val n = NoteViewRules.withTitle(plain, "  New   title ", 99L)
        assertEquals("New title", n.text)
        assertEquals(99L, n.edited)
    }

    @Test
    fun unchangedEditKeepsSameNoteAndEditedTime() {
        assertSame(plain, NoteViewRules.withTitle(plain, "Call", 99L))
        assertSame(plain, NoteViewRules.withBody(plain, "about heat", 99L))
        assertSame(checklist, NoteViewRules.withItemText(checklist, 1, "bread", 99L))
        assertSame(plain, NoteViewRules.withShowFrom(plain, null, 99L))
        assertSame(plain, NoteViewRules.withMovedTo(plain, null, 99L))
    }

    @Test
    fun plainTitleCannotBeEmptiedButChecklistTitleCan() {
        assertSame(plain, NoteViewRules.withTitle(plain, "   ", 5L))
        assertEquals("", NoteViewRules.withTitle(checklist, "", 5L).text)
    }

    @Test
    fun bodyEditIsStored() {
        assertEquals("a\nb", NoteViewRules.withBody(plain, "a  \nb\n\n", 7L).body)
        assertEquals("", NoteViewRules.withBody(plain, "", 7L).body)
    }

    @Test
    fun itemTextEditKeepsTick() {
        val n = NoteViewRules.withItemText(checklist, 0, " oat milk ", 8L)
        assertEquals(ChecklistItem("oat milk", true), n.items[0])
        assertEquals(8L, n.edited)
    }

    @Test
    fun blankItemTextRemovesItemExceptTheLastOfAnUntitledChecklist() {
        val removed = NoteViewRules.withItemText(checklist, 0, "", 8L)
        assertEquals(listOf("bread"), removed.items.map { it.text })
        val only = Note(3, "", 3, items = listOf(ChecklistItem("x")))
        assertSame(only, NoteViewRules.withItemText(only, 0, "", 8L))
        val titled = only.copy(text = "T")
        assertTrue(NoteViewRules.withItemText(titled, 0, "", 8L).items.isEmpty())
    }

    @Test
    fun badIndexChangesNothing() {
        assertSame(checklist, NoteViewRules.withItemText(checklist, 5, "x", 1L))
        assertSame(checklist, NoteViewRules.withItemText(checklist, -1, "x", 1L))
    }

    @Test
    fun newItemAppendsUntickedAndRespectsLimits() {
        val n = NoteViewRules.withNewItem(checklist, " tea ", 4L)
        assertEquals(ChecklistItem("tea"), n.items.last())
        assertSame(checklist, NoteViewRules.withNewItem(checklist, "  ", 4L))
        val full = checklist.copy(items = (1..Notes.MAX_ITEMS).map { ChecklistItem("i$it") })
        assertSame(full, NoteViewRules.withNewItem(full, "extra", 4L))
    }

    @Test
    fun dateCanBeSetAndCleared() {
        val set = NoteViewRules.withShowFrom(plain, today, 3L)
        assertEquals(today.toEpochDay(), set.showFrom)
        assertNull(NoteViewRules.withShowFrom(set, null, 4L).showFrom)
    }

    @Test
    fun listsToggleAndMove() {
        val a = NoteViewRules.withListToggled(plain, "home", 1L)
        val b = NoteViewRules.withListToggled(a, "work", 2L)
        assertEquals(listOf("home", "work"), b.lists)
        assertEquals(listOf("work"), NoteViewRules.withListToggled(b, "home", 3L).lists)
        assertEquals(listOf("errands"), NoteViewRules.withMovedTo(b, "errands", 4L).lists)
        assertTrue(NoteViewRules.withMovedTo(b, null, 5L).lists.isEmpty())
    }

    @Test
    fun liveListsDropsDeletedLists() {
        val n = plain.copy(lists = listOf("home", "gone", "work"))
        assertEquals(listOf("home", "work"), NoteViewRules.liveLists(n, setOf("work", "home")))
    }

    @Test
    fun bodyPreviewIsFirstNonBlankLine() {
        assertEquals("about heat", NoteViewRules.bodyPreview(plain))
        assertEquals("b", NoteViewRules.bodyPreview(plain.copy(body = "\n b \nc")))
        assertNull(NoteViewRules.bodyPreview(Note(9, "t", 1)))
    }

    @Test
    fun screenPutsPinnedFirstThenOpenAndSkipsDone() {
        val notes = listOf(Note(1, "a", 1), Note(2, "b", 2, pinned = true), Note(3, "c", 3, done = true))
        val s = Notes.sections(notes, "", today)
        assertEquals(listOf(2L), s.pinned.map { it.id })
        assertEquals(listOf(1L), s.rest.map { it.id })
        assertEquals("2 open · 1 pinned", Notes.countLine(listOf(Note(1, "a", 1), Note(2, "b", 2, pinned = true)), "%1\$d open · %2\$d pinned"))
    }

    @Test
    fun storeUpdateSavesOnlyRealChangesAndKeepsOthers() {
        var raw: String? = Notes.encode(listOf(checklist, plain))
        var writes = 0
        val store = NotesStore({ raw }, { raw = it; writes++ })
        store.update(2) { n, now -> NoteViewRules.withTitle(n, "Call", now) }
        assertEquals(0, writes)
        store.update(2) { n, now -> NoteViewRules.withTitle(n, "Phone", now) }
        assertEquals(1, writes)
        assertEquals("Phone", store.get(2)?.text)
        assertEquals(checklist, store.get(1))
        store.update(99) { n, now -> NoteViewRules.withTitle(n, "x", now) }
        assertEquals(1, writes)
    }

    @Test
    fun doneNoteCanBeUndone() {
        var raw: String? = Notes.encode(listOf(plain))
        val store = NotesStore({ raw }, { raw = it })
        store.markDone(2)
        assertTrue(store.get(2)!!.done)
        store.markDone(2, done = false)
        assertFalse(store.get(2)!!.done)
    }
}
