package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordSearchTest {

    private fun word(id: Long, text: String, meaning: String = "") =
        Word(id = id, text = text, meaning = meaning, dueDay = 0L, addedAt = id)

    private val maedchen = word(1, "Mädchen", "girl")
    private val ging = word(2, "ging", "simple past of gehen")
    private val untersuchung = word(3, "Untersuchung", "examination")
    private val strasse = word(4, "Straße", "street")
    private val cafe = word(5, "Café", "coffee house")
    private val all = listOf(maedchen, ging, untersuchung, strasse, cafe)

    private fun ids(query: String) = WordSearch.filter(all, query).map { it.id }

    @Test
    fun `blank query keeps every word in order`() {
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), ids(""))
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), ids("   "))
    }

    @Test
    fun `matches the word ignoring case`() {
        assertEquals(listOf(3L), ids("UNTERSUCH"))
        assertEquals(listOf(3L), ids("untersuch"))
    }

    @Test
    fun `matches the meaning`() {
        assertEquals(listOf(4L), ids("street"))
        assertEquals(listOf(2L), ids("Past"))
    }

    @Test
    fun `umlaut can be typed as the umlaut, as ae, or as the base letter`() {
        assertEquals(listOf(1L), ids("mädchen"))
        assertEquals(listOf(1L), ids("maedchen"))
        assertEquals(listOf(1L), ids("madchen"))
    }

    @Test
    fun `sharp s and ss are the same`() {
        assertEquals(listOf(4L), ids("strasse"))
        assertEquals(listOf(4L), ids("straße"))
    }

    @Test
    fun `other accents are ignored`() {
        assertEquals(listOf(5L), ids("cafe"))
    }

    @Test
    fun `verb form finds the noun and the noun finds the verb by stem`() {
        assertEquals(listOf(3L), ids("untersuchen"))
        val verb = word(9, "untersuchen", "to examine")
        assertEquals(listOf(9L), WordSearch.filter(listOf(verb, ging), "Untersuchung").map { it.id })
    }

    @Test
    fun `every query word must match, in any order`() {
        assertEquals(listOf(2L), ids("gehen past"))
        assertEquals(emptyList<Long>(), ids("gehen street"))
    }

    @Test
    fun `short words are not stemmed, so they do not match too much`() {
        // "Haus" has only four letters, so it is not cut to "Hau" and does not find "Hauptbahnhof".
        assertFalse(WordSearch.matches(word(10, "Hauptbahnhof", "main station"), "Haus"))
        assertTrue(WordSearch.matches(word(11, "Haustür", "front door"), "Haus"))
    }

    @Test
    fun `nothing matches gives an empty list`() {
        assertEquals(emptyList<Long>(), ids("xyz"))
    }
}
