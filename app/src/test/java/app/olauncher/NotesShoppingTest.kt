package app.olauncher

import app.olauncher.helper.ChecklistItem
import app.olauncher.helper.Note
import app.olauncher.helper.Notes
import app.olauncher.helper.NotesStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesShoppingTest {
    private fun shopping(vararg items: ChecklistItem, id: Long = 1L, done: Boolean = false, title: String = "Shopping") =
        Note(id, title, 100L, done = done, items = items.toList())

    @Test
    fun `creates a Shopping checklist when none exists`() {
        val r = Notes.addShopping(emptyList(), listOf("milk", "eggs"), 500L)
        assertEquals(2, r.added)
        val note = r.notes.single()
        assertEquals("Shopping", note.text)
        assertEquals(listOf(ChecklistItem("milk"), ChecklistItem("eggs")), note.items)
        assertEquals(500L, note.created)
    }

    @Test
    fun `done Shopping note or plain note titled Shopping is not reused`() {
        val done = shopping(ChecklistItem("old"), done = true)
        val plain = Note(2L, "Shopping", 100L)
        val r = Notes.addShopping(listOf(done, plain), listOf("milk"), 500L)
        assertEquals(1, r.added)
        assertEquals(3, r.notes.size)
        assertEquals(listOf(ChecklistItem("milk")), r.notes.last().items)
    }

    @Test
    fun `appends and keeps existing items and ticks`() {
        val note = shopping(ChecklistItem("bread", true), ChecklistItem("tea"), title = "shopping")
        val r = Notes.addShopping(listOf(note), listOf("milk", "eggs"), 900L)
        assertEquals(2, r.added)
        val out = r.notes.single()
        assertEquals(listOf(ChecklistItem("bread", true), ChecklistItem("tea"), ChecklistItem("milk"), ChecklistItem("eggs")), out.items)
        assertEquals(900L, out.edited)
    }

    @Test
    fun `skips items still unticked, any case`() {
        val note = shopping(ChecklistItem("Milk"), ChecklistItem("tea"))
        val r = Notes.addShopping(listOf(note), listOf("milk", "EGGS", "eggs"), 900L)
        assertEquals(1, r.added)
        assertEquals(listOf("Milk", "tea", "EGGS"), r.notes.single().items.map { it.text })
    }

    @Test
    fun `all duplicates leaves the list untouched`() {
        val notes = listOf(shopping(ChecklistItem("milk")))
        val r = Notes.addShopping(notes, listOf("MILK"), 900L)
        assertEquals(0, r.added)
        assertSame(notes, r.notes)
    }

    @Test
    fun `re-adding a ticked item adds a new unticked one`() {
        val note = shopping(ChecklistItem("milk", true))
        val r = Notes.addShopping(listOf(note), listOf("milk"), 900L)
        assertEquals(1, r.added)
        assertEquals(listOf(ChecklistItem("milk", true), ChecklistItem("milk", false)), r.notes.single().items)
    }

    @Test
    fun `caps a new note at MAX_ITEMS`() {
        val items = (1..30).map { "i$it" }
        val r = Notes.addShopping(emptyList(), items, 500L)
        assertEquals(Notes.MAX_ITEMS, r.added)
        assertEquals(Notes.MAX_ITEMS, r.notes.single().items.size)
        assertEquals("i1", r.notes.single().items.first().text)
    }

    @Test
    fun `caps an existing note at MAX_ITEMS in total`() {
        val existing = (1..18).map { ChecklistItem("old$it") }.toTypedArray()
        val r = Notes.addShopping(listOf(shopping(*existing)), listOf("a", "b", "c", "d"), 900L)
        assertEquals(2, r.added)
        assertEquals(Notes.MAX_ITEMS, r.notes.single().items.size)
        val full = Notes.addShopping(r.notes, listOf("e"), 901L)
        assertEquals(0, full.added)
    }

    @Test
    fun `empty or blank input does nothing`() {
        val notes = listOf(shopping(ChecklistItem("milk")))
        assertEquals(0, Notes.addShopping(notes, emptyList(), 1L).added)
        assertSame(notes, Notes.addShopping(notes, listOf(" ", ""), 1L).notes)
        assertTrue(Notes.addShopping(emptyList(), emptyList(), 1L).notes.isEmpty())
    }

    @Test
    fun `store saves only when something was added`() {
        var raw: String? = null
        var writes = 0
        val store = NotesStore({ raw }, { raw = it; writes++ })
        assertEquals(0, store.addShopping(emptyList(), 1L))
        assertEquals(0, writes)
        assertEquals(2, store.addShopping(listOf("milk", "eggs"), 2L))
        assertEquals(0, store.addShopping(listOf("milk"), 3L))
        assertEquals(1, writes)
        assertEquals(1, store.addShopping(listOf("tea"), 4L))
        assertEquals(listOf("milk", "eggs", "tea"), store.all().single().items.map { it.text })
    }
}
