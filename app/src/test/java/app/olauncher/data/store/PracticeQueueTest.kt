package app.olauncher.data.store

import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The derived generation queue (#601, 9.8): pool-thin words, oldest first with
 * jitter, under the daily word cap. All seeds are fixed so the tests are
 * deterministic.
 */
class PracticeQueueTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 27)
    private val todayMillis: Long =
        today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun word(id: Long, addedAt: Long, context: String = "Die Arbeit ___ auf seiner Unterschrift.") =
        Word(
            id = id,
            text = "insistieren$id",
            context = context,
            dueDay = 20_261_027,
            addedAt = addedAt,
        )

    private fun item(wordId: Long, createdAt: Long) = PracticeItem(
        wordId = wordId,
        type = DrillType.CLOZE,
        prompt = "Die Arbeit ___ auf seiner Unterschrift.",
        answer = "insistiert",
        choices = listOf("insistiert", "insistieren", "insistiertest"),
        createdAt = createdAt,
    )

    /** Stocks [wordId]'s pool to [count] items, timestamped before today. */
    private fun pool(wordId: Long, count: Int): List<PracticeItem> =
        (0 until count).map { item(wordId, todayMillis - 100_000) }

    @Test
    fun `fully stocked words skipped, thin words oldest first`() {
        // w1 has a full pool of 3; w2 (older) has 1; w3 has none.
        val words = listOf(
            word(1, addedAt = 1_000),
            word(2, addedAt = 2_000),
            word(3, addedAt = 3_000),
        )
        val items = pool(1, 3) + pool(2, 1)

        val queued = PracticeQueue.next(words, items, today, cap = 50, random = Random(1))

        assertEquals(listOf(2L, 3L), queued.map { it.id })
    }

    @Test
    fun `cap trims the batch, counted from today's items`() {
        // One word already got items today, so only 1 of the 2 thin words fits the cap of 2.
        val words = listOf(word(1, addedAt = 1_000), word(2, addedAt = 2_000))
        val items = pool(9, 1).map { it.copy(createdAt = todayMillis + 1) }

        val queued = PracticeQueue.next(words, items, today, cap = 2, random = Random(1))

        assertEquals(listOf(1L), queued.map { it.id })
    }

    @Test
    fun `cap reached returns empty even with thin words`() {
        val words = listOf(word(1, addedAt = 1_000), word(2, addedAt = 2_000))
        // Two distinct words already generated today: the cap of 2 is spent.
        val items = pool(8, 1).map { it.copy(createdAt = todayMillis + 1) } +
            pool(9, 1).map { it.copy(createdAt = todayMillis + 2) }

        assertEquals(
            emptyList<Word>(),
            PracticeQueue.next(words, items, today, cap = 2, random = Random(1)),
        )
        assertEquals(
            emptyList<Word>(),
            PracticeQueue.next(words, emptyList(), today, cap = 0, random = Random(1)),
        )
    }

    @Test
    fun `jitter never moves a word ahead of one older by more than the window`() {
        // addedAt 30 s apart — inside the 60 s jitter window, so swaps can happen,
        // but a word may never precede one older by more than JITTER_MS.
        val words = (1L..6L).map { word(it, addedAt = it * 30_000) }

        for (seed in 0..20) {
            val queued = PracticeQueue.next(words, emptyList(), today, cap = 50, random = Random(seed))
            for (i in 0 until queued.size - 1) {
                val gap = queued[i + 1].addedAt - queued[i].addedAt
                assertTrue("seed $seed, pair $i: gap $gap", gap > -PracticeQueue.JITTER_MS)
            }
        }
    }

    @Test
    fun `jitter stays in bounds when the timestamps are far apart`() {
        // addedAt one hour apart — far outside the jitter window, so the order is
        // always exactly FIFO, whatever the seed.
        val words = (1L..5L).map { word(it, addedAt = it * 3_600_000) }

        for (seed in 0..20) {
            val queued = PracticeQueue.next(words, emptyList(), today, cap = 50, random = Random(seed))
            assertEquals(words, queued)
        }
    }

    @Test
    fun `blank context words are never queued`() {
        val words = listOf(word(1, addedAt = 1_000, context = ""), word(2, addedAt = 2_000))

        val queued = PracticeQueue.next(words, emptyList(), today, cap = 50, random = Random(1))

        assertEquals(listOf(2L), queued.map { it.id })
    }

    @Test
    fun `deleted words drop out with their items ignored`() {
        // Items for word 99 (deleted) neither queue it nor crash the queue.
        val words = listOf(word(1, addedAt = 1_000))
        val items = pool(99, 3)

        val queued = PracticeQueue.next(words, items, today, cap = 50, random = Random(1))

        assertEquals(listOf(1L), queued.map { it.id })
    }

    @Test
    fun `a due word short of three items needs a run`() {
        val w1 = word(1, addedAt = 1_000)
        val w2 = word(2, addedAt = 2_000)
        val w3 = word(3, addedAt = 3_000)
        val w4 = word(4, addedAt = 4_000, context = "")
        val words = listOf(w1, w2, w3, w4)
        val items = pool(1, 2) + pool(2, 1) + pool(3, 3)

        assertFalse(PracticeQueue.anyNeedsItems(listOf(w3, w4), items))
        assertTrue(PracticeQueue.anyNeedsItems(listOf(w1, w3), items))
        assertFalse(PracticeQueue.anyNeedsItems(emptyList(), items))

        // The top-up restores every word's pool to 3, so 2 items still queues.
        assertEquals(
            listOf(1L, 2L),
            PracticeQueue.next(words, items, today, cap = 50, random = Random(1)).map { it.id },
        )
    }

    @Test
    fun `a word whose pool holds its own fallback card is stocked`() {
        // The store keeps one fallback copy instead of three: that copy must
        // keep the word out of the queue, or every run would resend it.
        val w1 = word(1, addedAt = 1_000, context = "Er insistiert1 darauf.")
        val w2 = word(2, addedAt = 2_000, context = "Er insistiert2 darauf.")
        val fallback = PracticeContract.fallbacksFor(w1, 1, PracticeItem.Source.WORD).single()
            .copy(id = 1, createdAt = todayMillis - 100_000)
        // w2's single card is not its fallback: still thin.
        val items = listOf(fallback) + pool(2, 1)

        assertTrue(PracticeQueue.isStocked(w1, listOf(fallback)))
        assertFalse(PracticeQueue.isStocked(w2, pool(2, 1)))
        assertEquals(listOf(2L), PracticeQueue.next(listOf(w1, w2), items, today, cap = 50, random = Random(1)).map { it.id })
        assertFalse(PracticeQueue.anyNeedsItems(listOf(w1), items))
        assertTrue(PracticeQueue.anyNeedsItems(listOf(w2), items))
        // A changed sentence gives a different fallback card: queued again.
        assertFalse(PracticeQueue.isStocked(w1.copy(context = "Sie insistiert1 nicht."), listOf(fallback)))
    }
}
