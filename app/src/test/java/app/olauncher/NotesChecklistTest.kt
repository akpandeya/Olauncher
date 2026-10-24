package app.olauncher

import app.olauncher.helper.ChecklistItem
import app.olauncher.helper.Note
import app.olauncher.helper.Notes
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesChecklistTest {
    private fun one(text: String) = Notes.add(emptyList(), text, 1L).single()

    @Test fun titleAndItems() {
        val n = one("[] Shopping: milk, eggs, bread")
        assertEquals("Shopping", n.text)
        assertEquals(listOf("milk", "eggs", "bread"), n.items.map { it.text })
        assertTrue(n.items.none { it.checked })
    }

    @Test fun blankItemsDroppedNoTitle() {
        val n = one("[] milk, , eggs")
        assertEquals("", n.text)
        assertEquals(listOf("milk", "eggs"), n.items.map { it.text })
    }

    @Test fun colonAfterCommaIsPartOfItem() {
        val n = one("[] meet 10:30, call")
        assertEquals("", n.text)
        assertEquals(listOf("meet 10:30", "call"), n.items.map { it.text })
    }

    @Test fun noItemsIsPlainNote() {
        for (input in listOf("[]", "[] :")) {
            val notes = Notes.add(emptyList(), input, 1L)
            if (input == "[]") {
                assertEquals("[]", notes.single().text)
            } else {
                assertEquals("[] :", notes.single().text)
            }
            assertTrue(notes.single().items.isEmpty())
        }
    }

    @Test fun toggleRoundTripAndDisplay() {
        val start = listOf(one("[] Shopping: milk, eggs, bread"))
        val once = Notes.decode(Notes.encode(Notes.toggleItem(start, 1L, 1, 1L)))
        assertEquals(listOf(false, true, false), once.single().items.map { it.checked })
        assertEquals("Shopping · 1/3", Notes.displayText(once.single()))
        val twice = Notes.decode(Notes.encode(Notes.toggleItem(once, 1L, 1, 1L)))
        assertEquals(listOf(false, false, false), twice.single().items.map { it.checked })
        assertEquals(listOf("milk", "eggs", "bread"), twice.single().items.map { it.text })
    }

    @Test fun toggleOutOfRangeOrMissing() {
        val start = listOf(one("[] a, b"))
        assertEquals(start, Notes.toggleItem(start, 1L, 5, 1L))
        assertEquals(start, Notes.toggleItem(start, 1L, -1, 1L))
        assertEquals(start, Notes.toggleItem(start, 99L, 0, 1L))
    }

    @Test fun displayTextWithoutTitleAndPlain() {
        assertEquals("0/2", Notes.displayText(one("[] a, b")))
        assertEquals("buy milk", Notes.displayText(one("buy milk")))
    }

    @Test fun nextUnchecked() {
        val n = Note(1, "t", 1, items = listOf(ChecklistItem("a", true), ChecklistItem("b"), ChecklistItem("c")))
        assertEquals(1, Notes.nextUnchecked(n))
        assertNull(Notes.nextUnchecked(n.copy(items = n.items.map { it.copy(checked = true) })))
        assertNull(Notes.nextUnchecked(Note(2, "plain", 1)))
    }

    @Test fun jsonKeys() {
        assertFalse(Notes.encode(listOf(Note(1, "plain", 1))).contains("\"items\""))
        val old = Notes.decode("""[{"id":1,"text":"x","created":1,"done":false}]""")
        assertTrue(old.single().items.isEmpty())
        assertTrue(Notes.encode(listOf(one("[] a"))).contains("\"items\""))
    }

    @Test fun parseKeepsDateAndItems() {
        val now = ZonedDateTime.of(2026, 10, 24, 9, 0, 0, 0, ZoneId.of("UTC"))
        val ok = Notes.parse("[] milk, eggs fri", now) as Notes.Parsed.Ok
        assertEquals("[] milk, eggs", ok.text)
        assertEquals("2026-10-30", ok.showFrom.toString())
        assertEquals(listOf("milk", "eggs"), one(ok.text).items.map { it.text })
    }

    @Test fun capAtTwentyItems() {
        val n = one("[] " + (1..25).joinToString(", ") { "i$it" })
        assertEquals(20, n.items.size)
    }

    @Test fun searchMatchesItems() {
        val notes = listOf(one("[] Shopping: milk, eggs"))
        assertEquals(1, Notes.search(notes, "EGGS").size)
        assertEquals(1, Notes.search(notes, "shopping milk").size)
        assertEquals(0, Notes.search(notes, "bread").size)
    }
}
