package app.olauncher

import app.olauncher.helper.ChecklistItem
import app.olauncher.helper.DraftResult
import app.olauncher.helper.Note
import app.olauncher.helper.NoteDraft
import app.olauncher.helper.NoteDraftRules
import app.olauncher.helper.Notes
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteDraftTest {
    private val zoned = ZonedDateTime.of(2026, 10, 24, 9, 0, 0, 0, ZoneId.of("UTC"))
    private fun added(notes: List<Note>, draft: NoteDraft): Note {
        val result = NoteDraftRules.add(notes, draft, 1000L, zoned)
        return (result as DraftResult.Added).note
    }

    @Test
    fun switchOnTurnsLinesIntoItemLines() {
        assertEquals("milk\nbread\ntea", NoteDraftRules.switchChecklist("milk\n\n  bread \ntea\n", on = true))
    }

    @Test
    fun switchOnSplitsASingleCommaLine() {
        assertEquals("milk\nbread", NoteDraftRules.switchChecklist("milk, bread", on = true))
    }

    @Test
    fun switchOffJoinsBackAndRoundTrips() {
        assertEquals("milk, bread, tea", NoteDraftRules.switchChecklist("milk\nbread\ntea", on = false))
        val on = NoteDraftRules.switchChecklist("milk, bread", on = true)
        assertEquals("milk, bread", NoteDraftRules.switchChecklist(on, on = false))
    }

    @Test
    fun itemTextsDropBlanksAndCapAtMax() {
        assertEquals(listOf("a", "b"), NoteDraftRules.itemTexts("a\n \nb"))
        val many = (1..30).joinToString("\n") { "item$it" }
        assertEquals(Notes.MAX_ITEMS, NoteDraftRules.itemTexts(many).size)
    }

    @Test
    fun titleAloneSavesAPlainNote() {
        val note = added(emptyList(), NoteDraft(title = "Call the landlord"))
        assertEquals("Call the landlord", note.text)
        assertEquals("", note.body)
        assertTrue(note.items.isEmpty())
        assertNull(note.showFrom)
        assertFalse(note.pinned)
    }

    @Test
    fun plainNoteKeepsBodyLines() {
        val note = added(emptyList(), NoteDraft(title = "Landlord", body = "About heating\nthen lease"))
        assertEquals("About heating\nthen lease", note.body)
    }

    @Test
    fun emptyTitleUsesFirstBodyLine() {
        val note = added(emptyList(), NoteDraft(body = "Heating\nand the lease"))
        assertEquals("Heating", note.text)
        assertEquals("and the lease", note.body)
    }

    @Test
    fun nothingTypedIsEmpty() {
        assertEquals(DraftResult.Empty, NoteDraftRules.add(emptyList(), NoteDraft(title = "  ", body = "\n"), 1L, zoned))
    }

    @Test
    fun checklistSwitchMakesBodyLinesItemsWithTitle() {
        val note = added(emptyList(), NoteDraft(title = "Shopping", body = "milk\nbread", checklist = true))
        assertEquals("Shopping", note.text)
        assertEquals(listOf(ChecklistItem("milk"), ChecklistItem("bread")), note.items)
        assertEquals("", note.body)
    }

    @Test
    fun checklistSwitchAllowsEmptyTitle() {
        val note = added(emptyList(), NoteDraft(body = "milk\nbread", checklist = true))
        assertEquals("", note.text)
        assertEquals(2, note.items.size)
    }

    @Test
    fun checklistSwitchWithoutLinesFallsBackToPlain() {
        val note = added(emptyList(), NoteDraft(title = "Shopping", checklist = true))
        assertTrue(note.items.isEmpty())
        assertEquals("Shopping", note.text)
    }

    @Test
    fun typedShortcutStillMakesAChecklistAndBodyLinesAddItems() {
        val note = added(emptyList(), NoteDraft(title = "[] Shopping: milk, bread", body = "tea"))
        assertEquals("Shopping", note.text)
        assertEquals(listOf("milk", "bread", "tea"), note.items.map { it.text })
    }

    @Test
    fun dateRowPinAndListsAreStored() {
        val day = LocalDate.of(2026, 10, 27)
        val note = added(emptyList(), NoteDraft(title = "Renew card", showFrom = day, pinned = true, lists = listOf("home", "home", "work")))
        assertEquals(day.toEpochDay(), note.showFrom)
        assertTrue(note.pinned)
        assertEquals(listOf("home", "work"), note.lists)
    }

    @Test
    fun typedDateWordSetsDateButDateRowWins() {
        val typed = added(emptyList(), NoteDraft(title = "Renew card 27.10"))
        assertEquals("Renew card", typed.text)
        assertEquals(LocalDate.of(2026, 10, 27).toEpochDay(), typed.showFrom)
        val row = LocalDate.of(2026, 11, 2)
        assertEquals(row.toEpochDay(), added(emptyList(), NoteDraft(title = "Renew card 27.10", showFrom = row)).showFrom)
    }

    @Test
    fun unreadableDateWordIsReported() {
        val result = NoteDraftRules.add(emptyList(), NoteDraft(title = "Call 31.02"), 1L, zoned)
        assertEquals(DraftResult.BadDate("31.02"), result)
    }

    @Test
    fun newNoteGetsNextIdAndExistingNotesStay() {
        val existing = listOf(Note(7, "old", 1))
        val result = NoteDraftRules.add(existing, NoteDraft(title = "new"), 5L, zoned) as DraftResult.Added
        assertEquals(8L, result.note.id)
        assertEquals(listOf(7L, 8L), result.notes.map { it.id })
    }

    @Test
    fun cancelAsksOnlyWhenTextWasTyped() {
        assertFalse(NoteDraftRules.hasTypedText(NoteDraft(pinned = true, showFrom = LocalDate.of(2026, 1, 1))))
        assertTrue(NoteDraftRules.hasTypedText(NoteDraft(title = "x")))
        assertTrue(NoteDraftRules.hasTypedText(NoteDraft(body = "x")))
    }

    @Test
    fun bodyAndListsRoundTripThroughJsonOnlyWhenSet() {
        val plain = Notes.encode(listOf(Note(1, "a", 1)))
        assertFalse(plain.contains("body"))
        assertFalse(plain.contains("lists"))
        val rich = Note(1, "a", 1, body = "line1\nline2", lists = listOf("home"))
        assertEquals(listOf(rich), Notes.decode(Notes.encode(listOf(rich))))
    }

    @Test
    fun searchFindsBodyText() {
        val notes = listOf(Note(1, "Landlord", 1, body = "about the heating"), Note(2, "Other", 2))
        assertEquals(listOf(1L), Notes.search(notes, "heating").map { it.id })
    }

    @Test
    fun cardItemsKeepRealIndexAndCap() {
        val note = Note(1, "Shop", 1, items = (1..6).map { ChecklistItem("i$it", checked = it == 1) })
        val shown = Notes.cardItems(note)
        assertEquals(Notes.CARD_MAX_ITEMS, shown.size)
        assertEquals(listOf(0, 1, 2, 3), shown.map { it.index })
        assertTrue(Notes.cardItems(Note(2, "plain", 1)).isEmpty())
    }

    @Test
    fun cardTitleIsTitleOrProgress() {
        assertEquals("Shop", Notes.cardTitle(Note(1, "Shop", 1, items = listOf(ChecklistItem("a")))))
        assertEquals("1/2", Notes.cardTitle(Note(1, "", 1, items = listOf(ChecklistItem("a", true), ChecklistItem("b")))))
        assertEquals("plain", Notes.cardTitle(Note(1, "plain", 1)))
    }
}
