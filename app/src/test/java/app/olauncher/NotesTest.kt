package app.olauncher

import app.olauncher.helper.DrawerCommand
import app.olauncher.helper.Note
import app.olauncher.helper.Notes
import app.olauncher.helper.NotesStore
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesTest {
    private val DAY = LocalDate.of(2025, 10, 22)
    private val noon = ZonedDateTime.of(2025, 10, 22, 12, 0, 0, 0, ZoneId.of("Europe/Berlin"))

    @Test
    fun `parser gives Note with trimmed args`() {
        assertEquals(DrawerCommand.Note("call Mama back"), DrawerCommand.parse(":n  call Mama back ", false))
        assertEquals(DrawerCommand.Note(""), DrawerCommand.parse(":n", false))
    }

    @Test
    fun `add appends an open note with the next id`() {
        val a = Notes.add(emptyList(), "  buy   oat milk ", 100L)
        assertEquals(listOf(Note(1, "buy oat milk", 100L)), a)
        val b = Notes.add(a, "reply to landlord", 200L)
        assertEquals(2L, b.last().id)
        assertFalse(b.last().done)
    }

    @Test
    fun `blank text adds nothing`() {
        val a = Notes.add(emptyList(), "x", 1L)
        assertTrue(Notes.add(a, "   ", 2L) === a)
    }

    @Test
    fun `long text is cut`() {
        val n = Notes.add(emptyList(), "a".repeat(500), 1L).single()
        assertEquals(Notes.MAX_TEXT_CHARS, n.text.length)
    }

    @Test
    fun `mark done hides the note and undo brings it back`() {
        var list = Notes.add(Notes.add(emptyList(), "first", 1L), "second", 2L)
        list = Notes.markDone(list, 1L)
        assertEquals("second", Notes.top(list, DAY)?.text)
        list = Notes.markDone(list, 1L, done = false)
        assertEquals("first", Notes.top(list, DAY)?.text)
    }

    @Test
    fun `open notes are oldest first and top is null when none are open`() {
        val list = listOf(Note(1, "late", 300L), Note(2, "early", 100L), Note(3, "done", 50L, done = true), Note(4, "tie", 100L))
        assertEquals(listOf("early", "tie", "late"), Notes.open(list).map { it.text })
        assertEquals("early", Notes.top(list, DAY)?.text)
        assertNull(Notes.top(list.map { it.copy(done = true) }, DAY))
        assertNull(Notes.top(emptyList(), DAY))
    }

    @Test
    fun `delete removes only that note`() {
        val list = Notes.add(Notes.add(emptyList(), "a", 1L), "b", 2L)
        assertEquals(listOf("b"), Notes.delete(list, 1L).map { it.text })
    }

    @Test
    fun `json round trip keeps every field`() {
        val list = listOf(Note(7, "parcel at \"Packstation\" 112", 12345L, done = true), Note(9, "reply", 9L))
        assertEquals(list, Notes.decode(Notes.encode(list)))
    }

    @Test
    fun `corrupt json gives an empty list`() {
        assertEquals(emptyList<Note>(), Notes.decode("{not json"))
        assertEquals(emptyList<Note>(), Notes.decode("{\"a\":1}"))
        assertEquals(emptyList<Note>(), Notes.decode(""))
        assertEquals(emptyList<Note>(), Notes.decode(null))
    }

    @Test
    fun `bad entries are skipped and good ones kept`() {
        val raw = """[1, {"id":1,"text":"ok","created":5}, {"id":2,"text":" "}, {"text":"no id"}, {"id":1,"text":"dup"}]"""
        assertEquals(listOf(Note(1, "ok", 5L)), Notes.decode(raw))
    }

    @Test
    fun `old done notes are pruned but open notes stay`() {
        var list = emptyList<Note>()
        for (i in 1..30) list = Notes.add(list, "n$i", i.toLong())
        for (i in 1..25) list = Notes.markDone(list, i.toLong())
        list = Notes.add(list, "new", 99L)
        assertEquals(20, list.count { it.done })
        assertEquals(listOf("n26", "n27", "n28", "n29", "n30", "new"), Notes.open(list).map { it.text })
    }

    @Test
    fun `store saves to the backing string and reads it back`() {
        var raw = ""
        val store = NotesStore({ raw }, { raw = it })
        assertNull(store.top(DAY))
        assertTrue(store.add("call Mama back", 10L))
        assertFalse(store.add("  ", 11L))
        store.add("second", 20L)
        assertEquals("call Mama back", store.top(DAY)?.text)
        store.markDone(store.top(DAY)!!.id)
        assertEquals("second", store.top(DAY)?.text)
        store.delete(store.top(DAY)!!.id)
        assertNull(store.top(DAY))
    }

    @Test
    fun `dated note is hidden the day before and shown on its day`() {
        val list = Notes.add(emptyList(), "pay rent", 1L, LocalDate.of(2025, 11, 1))
        assertNull(Notes.top(list, LocalDate.of(2025, 10, 31)))
        assertEquals("pay rent", Notes.top(list, LocalDate.of(2025, 11, 1))?.text)
        assertEquals("pay rent", Notes.top(list, LocalDate.of(2025, 11, 2))?.text)
        assertEquals(1, Notes.open(list).size)
    }

    @Test
    fun `hidden dated note does not block an older undated one or a newer one`() {
        var list = Notes.add(emptyList(), "later", 1L, LocalDate.of(2025, 12, 1))
        list = Notes.add(list, "undated", 2L)
        assertEquals("undated", Notes.top(list, DAY)?.text)
        assertEquals("later", Notes.top(list, LocalDate.of(2025, 12, 1))?.text)
    }

    @Test
    fun `dated note round trips and old json without showFrom stays undated`() {
        val list = Notes.add(emptyList(), "x", 1L, LocalDate.of(2025, 11, 1))
        assertEquals(list, Notes.decode(Notes.encode(list)))
        val old = Notes.decode("[{\"id\":1,\"text\":\"old\",\"created\":5,\"done\":false}]")
        assertEquals(listOf(Note(1, "old", 5L)), old)
        assertNull(old.single().showFrom)
        assertFalse(Notes.encode(listOf(Note(1, "old", 5L))).contains("showFrom"))
        assertEquals("old", Notes.top(old, DAY)?.text)
    }

    @Test
    fun `parse reads a trailing date token`() {
        assertEquals(Notes.Parsed.Ok("pay rent", LocalDate.of(2025, 11, 1)), Notes.parse("pay rent 1.11", noon))
        assertEquals(Notes.Parsed.Ok("return book", LocalDate.of(2025, 10, 24)), Notes.parse("return book fri", noon))
        assertEquals(Notes.Parsed.Ok("call", LocalDate.of(2025, 10, 23)), Notes.parse("call tomorrow", noon))
        assertEquals(Notes.Parsed.Ok("a", LocalDate.of(2026, 1, 5)), Notes.parse("a 5.1", noon))
    }

    @Test
    fun `parse leaves undated text alone`() {
        assertEquals(Notes.Parsed.Ok("buy milk", null), Notes.parse("  buy   milk ", noon))
        assertEquals(Notes.Parsed.Ok("pay 5", null), Notes.parse("pay 5", noon))
        assertEquals(Notes.Parsed.Ok("fri", null), Notes.parse("fri", noon))
        assertEquals(Notes.Parsed.Ok("fri meeting", null), Notes.parse("fri meeting", noon))
        assertEquals(Notes.Parsed.Ok("", null), Notes.parse("  ", noon))
    }

    @Test
    fun `parse reports an unreadable date and a store saves nothing for it`() {
        assertEquals(Notes.Parsed.Error("32.13"), Notes.parse("pay rent 32.13", noon))
        assertEquals(Notes.Parsed.Error("31.2"), Notes.parse("x 31.2", noon))
    }
}
