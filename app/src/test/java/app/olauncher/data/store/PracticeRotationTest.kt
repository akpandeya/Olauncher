package app.olauncher.data.store

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Rotation over a word's practice pool (#603, 9.10): the pool index is the
 * word's review count mod pool size — no new state, the cursor survives a
 * store reload because it derives from the persisted review event log.
 */
class PracticeRotationTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("practice_rotation_test").toFile()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private val word = Word(
        id = 7,
        text = "insistieren",
        context = "Die Arbeit insistiert auf seiner Unterschrift.",
        dueDay = 20_261_027,
        addedAt = 1_000,
    )

    private fun event(wordId: Long, id: Long, result: String = ReviewEvent.Result.AGAIN) =
        ReviewEvent(id = id, wordId = wordId, reviewedAt = id, result = result, boxBefore = 1, boxAfter = 1)

    private fun item(wordId: Long, prompt: String) = PracticeItem(
        wordId = wordId,
        type = DrillType.CLOZE,
        prompt = prompt,
        answer = "insistiert",
        choices = listOf("insistiert", "insistieren", "insistiertest"),
        createdAt = 1,
    )

    @Test
    fun `rotation cycles a 3-item pool deterministically`() {
        val pool = listOf(
            item(7, "Die Arbeit ___ auf seiner Unterschrift."),
            item(7, "Er ___ weiter auf seiner Unterschrift."),
            item(7, "Der Vertrag ___ auf deiner Unterschrift."),
        )
        val events = (0 until 9).map { event(7, it.toLong() + 1) }

        // Reviews 0..8 land on items 0,1,2,0,1,2,0,1,2 — every grade on the same
        // word moves the cursor, then the pool wraps.
        for (review in 0 until 9) {
            val picked = PracticeRotation.pick(word, pool, events.take(review))
            assertEquals(pool[review % 3], picked)
        }
    }

    @Test
    fun `single-item pool serves the same item every time`() {
        val pool = listOf(item(7, "Die Arbeit ___ auf seiner Unterschrift."))
        val events = (0 until 5).map { event(7, it.toLong() + 1) }

        for (review in 0 until 5) {
            assertEquals(pool[0], PracticeRotation.pick(word, pool, events.take(review)))
        }
    }

    @Test
    fun `empty pool returns null and the word keeps its built-in content`() {
        assertNull(PracticeRotation.pick(word, emptyList(), emptyList()))
        // Items of other words never leak into this word's pool.
        val other = item(8, "Ein anderer Satz ___.")
        assertNull(PracticeRotation.pick(word, listOf(other), event(8, 1).let { listOf(it) }))
    }

    @Test
    fun `only this word's review events move the cursor`() {
        val pool = listOf(item(7, "A"), item(7, "B"), item(7, "C"))
        // Nine events for another word: this word's cursor stays at 0.
        val others = (0 until 9).map { event(8, it.toLong() + 1) }

        assertEquals("A", PracticeRotation.pick(word, pool, others)?.prompt)
    }

    @Test
    fun `rotation cursor survives a store reload`() {
        // Persist one review and a full pool, then rebuild the stores from disk.
        val eventsStore = ReviewEventsStore(tempDir)
        val itemsStore = PracticeItemsStore(tempDir)
        eventsStore.append(word.id, ReviewEvent.Result.AGAIN, 1, 1, reviewedAt = 1)
        eventsStore.append(word.id, ReviewEvent.Result.AGAIN, 1, 1, reviewedAt = 2)
        itemsStore.append(
            listOf(item(7, "A"), item(7, "B"), item(7, "C")),
        )

        val reloadedEvents = ReviewEventsStore(tempDir).all().filter { it.wordId == word.id }
        val reloadedPool = PracticeItemsStore(tempDir).all()

        // Two stored reviews: index 2 mod 3, and a fresh store instance picks the same.
        assertEquals(
            PracticeRotation.pick(word, reloadedPool, reloadedEvents),
            PracticeRotation.pick(word, reloadedPool, reloadedEvents),
        )
        assertEquals("C", PracticeRotation.pick(word, reloadedPool, reloadedEvents)?.prompt)
    }
}
