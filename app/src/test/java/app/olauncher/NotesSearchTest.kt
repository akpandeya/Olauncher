package app.olauncher

import app.olauncher.helper.Note
import app.olauncher.helper.Notes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesSearchTest {
    private fun note(id: Long, text: String) = Note(id, text, id)
    private fun ids(notes: List<Note>) = notes.map { it.id }

    @Test
    fun matchesIgnoringCase() {
        assertEquals(listOf(1L), ids(Notes.search(listOf(note(1, "buy milk"), note(2, "call mum")), "MILK")))
    }

    @Test
    fun foldsAccentsAndEszett() {
        assertEquals(listOf(1L), ids(Notes.search(listOf(note(1, "Café Ludwig"), note(2, "other")), "cafe")))
        assertEquals(listOf(1L), ids(Notes.search(listOf(note(1, "Hauptstraße 5"), note(2, "other")), "strasse")))
        assertEquals(listOf(1L), ids(Notes.search(listOf(note(1, "Mädchen"), note(2, "other")), "madchen")))
    }

    @Test
    fun everyWordMustMatch() {
        val notes = listOf(note(1, "bread and milk"), note(2, "buy milk"))
        assertEquals(listOf(1L), ids(Notes.search(notes, "milk bread")))
    }

    @Test
    fun blankQueryKeepsListAndOrder() {
        val notes = listOf(note(3, "c"), note(1, "a"), note(2, "b"))
        assertEquals(notes, Notes.search(notes, ""))
        assertEquals(notes, Notes.search(notes, "   "))
    }

    @Test
    fun keepsInputOrder() {
        val notes = listOf(note(3, "milk a"), note(1, "milk b"), note(2, "milk c"))
        assertEquals(listOf(3L, 1L, 2L), ids(Notes.search(notes, "milk")))
    }

    @Test
    fun noHitsGivesEmptyList() {
        assertTrue(Notes.search(listOf(note(1, "milk")), "zzz").isEmpty())
    }

    @Test
    fun searchShowsFromFiveNotes() {
        assertFalse(Notes.showSearch(4))
        assertTrue(Notes.showSearch(5))
    }
}
