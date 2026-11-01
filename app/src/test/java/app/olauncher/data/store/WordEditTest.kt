package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WordEditTest {

    private val wiktionaryWord = Word(
        id = 1L,
        text = "ging",
        meaning = "simple past of gehen",
        meaningSource = "wiktionary",
        dueDay = 100L,
        addedAt = 10L
    )

    @Test
    fun `editing the meaning makes it the user's own`() {
        val outcome = WordEdit.apply(wiktionaryWord, "ging", "went")
        assertEquals("went", outcome.word.meaning)
        assertEquals("user", outcome.word.meaningSource)
        assertFalse(outcome.needsLookup)
    }

    @Test
    fun `new spelling with untouched Wiktionary meaning asks for a fresh lookup`() {
        val outcome = WordEdit.apply(wiktionaryWord, " gehen ", "simple past of gehen")
        assertEquals("gehen", outcome.word.text)
        assertEquals("", outcome.word.meaning)
        assertEquals("", outcome.word.meaningSource)
        assertTrue(outcome.needsLookup)
    }

    @Test
    fun `new spelling keeps a meaning the user wrote`() {
        val own = wiktionaryWord.copy(meaning = "went", meaningSource = "user")
        val outcome = WordEdit.apply(own, "gehen", "went")
        assertEquals("gehen", outcome.word.text)
        assertEquals("went", outcome.word.meaning)
        assertEquals("user", outcome.word.meaningSource)
        assertFalse(outcome.needsLookup)
    }

    @Test
    fun `clearing the meaning hands it back to Wiktionary`() {
        val own = wiktionaryWord.copy(meaning = "went", meaningSource = "user")
        val outcome = WordEdit.apply(own, "ging", "  ")
        assertEquals("", outcome.word.meaning)
        assertEquals("", outcome.word.meaningSource)
        assertTrue(outcome.needsLookup)
    }

    @Test
    fun `blank text keeps the old word and unchanged edit changes nothing`() {
        val outcome = WordEdit.apply(wiktionaryWord, "", "simple past of gehen")
        assertEquals(wiktionaryWord, outcome.word)
        assertFalse(outcome.needsLookup)
    }

    @Test
    fun `edits keep the learning progress`() {
        val progressed = wiktionaryWord.copy(box = 3, dueDay = 120L)
        val outcome = WordEdit.apply(progressed, "gehen", "to go")
        assertEquals(3, outcome.word.box)
        assertEquals(120L, outcome.word.dueDay)
        assertEquals(progressed.id, outcome.word.id)
    }

    @Test
    fun `new spelling keeps the Wiktionary meaning when lookups are off`() {
        val outcome = WordEdit.apply(wiktionaryWord, "gehen", "simple past of gehen", lookupAllowed = false)
        assertEquals("gehen", outcome.word.text)
        assertEquals("simple past of gehen", outcome.word.meaning)
        assertEquals("wiktionary", outcome.word.meaningSource)
        assertFalse(outcome.needsLookup)
    }

    @Test
    fun `changing spelling and meaning together keeps both as the user's own`() {
        val outcome = WordEdit.apply(wiktionaryWord, "gehen", "to go")
        assertEquals("gehen", outcome.word.text)
        assertEquals("to go", outcome.word.meaning)
        assertEquals("user", outcome.word.meaningSource)
        assertFalse(outcome.needsLookup)
    }

    @Test
    fun `unchanged word with an empty meaning still asks for a lookup`() {
        val empty = wiktionaryWord.copy(meaning = "", meaningSource = "")
        val outcome = WordEdit.apply(empty, "ging", "")
        assertEquals(empty, outcome.word)
        assertTrue(outcome.needsLookup)
    }

    @Test
    fun `new spelling that matches another saved word clashes, ignoring case`() {
        val other = wiktionaryWord.copy(id = 2L, text = "Gehen")
        val words = listOf(wiktionaryWord, other)
        assertTrue(WordEdit.clashesWithOtherWord(" gehen ", wiktionaryWord.id, words))
        assertFalse(WordEdit.clashesWithOtherWord("GING", wiktionaryWord.id, words))
        assertFalse(WordEdit.clashesWithOtherWord("laufen", wiktionaryWord.id, words))
        assertFalse(WordEdit.clashesWithOtherWord("  ", wiktionaryWord.id, words))
    }

    @Test
    fun `reset clears meaning and source`() {
        val reset = WordEdit.resetMeaning(wiktionaryWord.copy(meaningSource = "user"))
        assertEquals("", reset.meaning)
        assertEquals("", reset.meaningSource)
        assertEquals("ging", reset.text)
    }

    @Test
    fun `unchanged context keeps the existing source`() {
        val captured = wiktionaryWord.copy(context = "Ein alter Satz.", contextSource = ContextSource.CAPTURED)
        assertEquals(ContextSource.CAPTURED, WordEdit.contextSourceAfterEdit(captured, " Ein alter Satz. "))
        val auto = captured.copy(contextSource = ContextSource.AUTO)
        assertEquals(ContextSource.AUTO, WordEdit.contextSourceAfterEdit(auto, "Ein alter Satz."))
    }

    @Test
    fun `a changed non-blank context marks it as the user's own`() {
        val captured = wiktionaryWord.copy(context = "Ein alter Satz.", contextSource = ContextSource.CAPTURED)
        assertEquals(ContextSource.USER, WordEdit.contextSourceAfterEdit(captured, "Ein neuer Satz."))
    }

    @Test
    fun `a cleared context returns to captured`() {
        val user = wiktionaryWord.copy(context = "Ein alter Satz.", contextSource = ContextSource.USER)
        assertEquals(ContextSource.CAPTURED, WordEdit.contextSourceAfterEdit(user, "  "))
    }

    @Test
    fun `wiktionary url points at the German section`() {
        assertEquals("https://en.wiktionary.org/wiki/ging#German", WordEdit.wiktionaryUrl("ging"))
        assertEquals(
            "https://en.wiktionary.org/wiki/Gr%C3%BC%C3%9F_Gott#German",
            WordEdit.wiktionaryUrl(" Grüß Gott ")
        )
    }

    @Test
    fun `status reports due, due in days and learned`() {
        val today = 100L
        assertEquals(WordsListLogic.Status.Due, WordsListLogic.status(wiktionaryWord.copy(dueDay = 99L), today))
        assertEquals(WordsListLogic.Status.Due, WordsListLogic.status(wiktionaryWord, today))
        assertEquals(WordsListLogic.Status.DueIn(4), WordsListLogic.status(wiktionaryWord.copy(dueDay = 104L), today))
        assertEquals(
            WordsListLogic.Status.Learned,
            WordsListLogic.status(wiktionaryWord.copy(dueDay = 50L, learnedAt = 90L), today)
        )
    }

    @Test
    fun `sort puts due words first and learned words last`() {
        val learned = wiktionaryWord.copy(id = 1, text = "a", dueDay = 10L, learnedAt = 90L)
        val later = wiktionaryWord.copy(id = 2, text = "b", dueDay = 108L)
        val due = wiktionaryWord.copy(id = 3, text = "c", dueDay = 100L)
        val dueNewer = wiktionaryWord.copy(id = 4, text = "d", dueDay = 100L, addedAt = 20L)
        val sorted = WordsListLogic.sort(listOf(learned, later, due, dueNewer))
        assertEquals(listOf("d", "c", "b", "a"), sorted.map { it.text })
    }
}
