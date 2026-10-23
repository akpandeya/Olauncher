package app.olauncher.ui.today.cards

import android.view.View
import app.olauncher.R
import app.olauncher.data.store.Leitner
import app.olauncher.data.store.Word
import app.olauncher.data.store.WordsStore
import app.olauncher.databinding.CardWordsBinding
import app.olauncher.ui.today.TodayCards
import app.olauncher.ui.today.WordReveal
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
    fun `review button shows only when words are due`() {
        assertTrue(WordsCardLogic.showReview(6))
        assertFalse(WordsCardLogic.showReview(0))
    }

    @Test
    fun `review label shows the due count`() {
        assertEquals("Review 6", WordsCardLogic.reviewLabel("Review %d", 6))
    }

    @Test
    fun `isAvailable follows germanEnabled flag`() {
        assertTrue(WordsCardLogic.isAvailable(germanEnabled = true))
        assertFalse(WordsCardLogic.isAvailable(germanEnabled = false))
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

        // Word 1 answered "Know it"
        val current1 = dueWords.first()
        val updated1 = Leitner.knowIt(current1, today)
        store.update(updated1)

        dueWords = store.due(today, 20)
        assertEquals(2, dueWords.size)
        stats = store.stats(today)
        assertEquals(3, stats.total)
        assertEquals(2, stats.due)

        // Word 2 answered "Again"
        val current2 = dueWords.first()
        val updated2 = Leitner.again(current2, today)
        store.update(updated2)

        dueWords = store.due(today, 20)
        assertEquals(1, dueWords.size)
        stats = store.stats(today)
        assertEquals(3, stats.total)
        assertEquals(1, stats.due)

        // Word 3 answered "Know it"
        val current3 = dueWords.first()
        val updated3 = Leitner.knowIt(current3, today)
        store.update(updated3)

        dueWords = store.due(today, 20)
        assertTrue(dueWords.isEmpty())
        stats = store.stats(today)
        assertEquals(3, stats.total)
        assertEquals(0, stats.due)

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

    @Test
    fun `WordsCard singleton holds no per-view state`() {
        val perViewTypes = setOf(
            CardWordsBinding::class.java,
            WordReveal::class.java,
        )
        val leaked = WordsCard::class.java.declaredFields.filter { field ->
            perViewTypes.any { it.isAssignableFrom(field.type) } || View::class.java.isAssignableFrom(field.type)
        }
        assertTrue("per-view fields on WordsCard: ${leaked.map { it.name }}", leaked.isEmpty())
    }
}
