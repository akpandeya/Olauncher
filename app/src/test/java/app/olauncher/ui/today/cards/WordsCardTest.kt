package app.olauncher.ui.today.cards

import app.olauncher.R
import app.olauncher.data.store.Leitner
import app.olauncher.data.store.Word
import app.olauncher.data.store.WordsStore
import app.olauncher.ui.today.TodayCards
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class WordsCardTest {

    private lateinit var tempDir: File
    private lateinit var store: WordsStore

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("words_card_test").toFile()
        store = WordsStore(tempDir)
        store.epochDaySupplier = { 20000L }
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `words card is registered in TodayCards all`() {
        val card = TodayCards.all.firstOrNull { it.key == WordsCard.KEY }
        assertNotNull(card)
        assertEquals("words", card?.key)
        assertEquals(R.string.words, card?.titleRes)
    }

    @Test
    fun `isAvailable follows germanEnabled flag`() {
        assertTrue(WordsCardLogic.isAvailable(germanEnabled = true))
        assertFalse(WordsCardLogic.isAvailable(germanEnabled = false))
    }

    @Test
    fun `formatWordsCount handles singular and plural`() {
        assertEquals("1 word", WordsCardLogic.formatWordsCount(1))
        assertEquals("0 words", WordsCardLogic.formatWordsCount(0))
        assertEquals("2 words", WordsCardLogic.formatWordsCount(2))
        assertEquals("12 words", WordsCardLogic.formatWordsCount(12))
    }

    @Test
    fun `formatFooter formats total words, due and learned`() {
        assertEquals(
            "12 words · 3 due · 4 learned",
            WordsCardLogic.formatFooter(total = 12, due = 3, learned = 4)
        )
        assertEquals(
            "1 word · 1 due · 0 learned",
            WordsCardLogic.formatFooter(total = 1, due = 1, learned = 0)
        )
        assertEquals(
            "5 words · 0 due · 2 learned",
            WordsCardLogic.formatFooter(total = 5, due = 0, learned = 2)
        )
    }

    @Test
    fun `formatAllDone formats total words and learned without due count`() {
        assertEquals(
            "All done for today · 12 words · 4 learned",
            WordsCardLogic.formatAllDone(total = 12, learned = 4)
        )
        assertEquals(
            "All done for today · 1 word · 0 learned",
            WordsCardLogic.formatAllDone(total = 1, learned = 0)
        )
        assertEquals(
            "All done for today · 3 words · 3 learned",
            WordsCardLogic.formatAllDone(total = 3, learned = 3)
        )
    }

    @Test
    fun `formatMeaning appends Wiktionary suffix only when sourced from wiktionary`() {
        assertEquals(
            "yearning for distant places · Wiktionary",
            WordsCardLogic.formatMeaning("yearning for distant places", "wiktionary")
        )
        assertEquals(
            "yearning for distant places · Wiktionary",
            WordsCardLogic.formatMeaning("yearning for distant places", "Wiktionary")
        )
        assertEquals(
            "wanderlust",
            WordsCardLogic.formatMeaning("wanderlust", "user")
        )
        assertEquals(
            "plain meaning",
            WordsCardLogic.formatMeaning("plain meaning", "")
        )
    }

    @Test
    fun `formatMeaning returns No meaning yet when meaning is blank`() {
        assertEquals("No meaning yet", WordsCardLogic.formatMeaning("", ""))
        assertEquals("No meaning yet", WordsCardLogic.formatMeaning("   ", ""))
        assertEquals("No meaning yet", WordsCardLogic.formatMeaning("", "wiktionary"))
        assertEquals("No meaning yet", WordsCardLogic.formatMeaning("  ", "wiktionary"))
        assertEquals("No meaning yet", WordsCardLogic.formatMeaning("", "user"))
    }

    @Test
    fun `card study flow through 3 words with Know it and Again moves due list and updates stats`() {
        val today = 20000L
        val w1 = store.add("Fernweh", "Ich habe Fernweh.")
        val w2 = store.add("Gemütlichkeit", "Gemütlichkeit ist schön.")
        val w3 = store.add("Kummerspeck", "Zu viel Kummerspeck.")

        // Initially 3 due words
        var dueWords = store.due(today, 20)
        assertEquals(3, dueWords.size)
        var stats = store.stats(today)
        assertEquals(3, stats.total)
        assertEquals(3, stats.due)
        assertEquals(0, stats.learned)
        assertEquals("3 words · 3 due · 0 learned", WordsCardLogic.formatFooter(stats.total, stats.due, stats.learned))

        // Word 1 answered "Know it"
        val current1 = dueWords.first()
        val updated1 = Leitner.knowIt(current1, today)
        store.update(updated1)

        dueWords = store.due(today, 20)
        assertEquals(2, dueWords.size)
        stats = store.stats(today)
        assertEquals(3, stats.total)
        assertEquals(2, stats.due)
        assertEquals("3 words · 2 due · 0 learned", WordsCardLogic.formatFooter(stats.total, stats.due, stats.learned))

        // Word 2 answered "Again"
        val current2 = dueWords.first()
        val updated2 = Leitner.again(current2, today)
        store.update(updated2)

        dueWords = store.due(today, 20)
        assertEquals(1, dueWords.size)
        stats = store.stats(today)
        assertEquals(3, stats.total)
        assertEquals(1, stats.due)
        assertEquals("3 words · 1 due · 0 learned", WordsCardLogic.formatFooter(stats.total, stats.due, stats.learned))

        // Word 3 answered "Know it"
        val current3 = dueWords.first()
        val updated3 = Leitner.knowIt(current3, today)
        store.update(updated3)

        dueWords = store.due(today, 20)
        assertTrue(dueWords.isEmpty())
        stats = store.stats(today)
        assertEquals(3, stats.total)
        assertEquals(0, stats.due)
        assertEquals("All done for today · 3 words · 0 learned", WordsCardLogic.formatAllDone(stats.total, stats.learned))

        // Tomorrow (today + 1), the "Again" word is back!
        val tomorrow = today + 1L
        val tomorrowDue = store.due(tomorrow, 20)
        assertEquals(1, tomorrowDue.size)
        assertEquals(current2.id, tomorrowDue.first().id)
    }

    @Test
    fun `editing meaning sets meaningSource to user and preserves word in store`() {
        val word = store.add("Fernweh", "Ich habe Fernweh.")
        val edited = word.copy(meaning = "longing for travels", meaningSource = "user")
        store.update(edited)

        val retrieved = store.get(word.id)
        assertNotNull(retrieved)
        assertEquals("longing for travels", retrieved?.meaning)
        assertEquals("user", retrieved?.meaningSource)
        assertEquals("longing for travels", WordsCardLogic.formatMeaning(retrieved!!.meaning, retrieved.meaningSource))
    }

    @Test
    fun `deleting a word removes it from store and updates due count`() {
        val today = 20000L
        val w1 = store.add("Fernweh")
        val w2 = store.add("Wanderlust")

        assertEquals(2, store.stats(today).total)
        assertEquals(2, store.due(today, 20).size)

        store.delete(w1.id)

        assertEquals(1, store.stats(today).total)
        val remainingDue = store.due(today, 20)
        assertEquals(1, remainingDue.size)
        assertEquals(w2.id, remainingDue.first().id)
    }
}
