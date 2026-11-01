package app.olauncher.data.store

import app.olauncher.data.AiPracticePrefs
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The digest (#604, 9.11): weekly OR threshold-triggered, whichever first; the
 * batched call sends only the weak words and the weak drill type ids, is parsed
 * by [PracticeContract.parse], stores items with `source = digest`, and updates
 * `AI_LAST_DIGEST_AT` only on success. The provider is faked — no network.
 */
class PracticeDigestTest {

    /** 2026-10-27 12:00 UTC in epoch millis. */
    private val now: Long = 1_792_128_000_000L
    private val today: LocalDate = LocalDate.of(2026, 10, 27)
    private val dayMs = 24L * 60 * 60 * 1000

    private lateinit var tempDir: File
    private lateinit var store: PracticeItemsStore
    private lateinit var prefs: FakePrefs

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("practice_digest_test").toFile()
        store = PracticeItemsStore(tempDir)
        prefs = FakePrefs()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    private class FakeProvider(var result: AiResult? = null) : AiProvider {
        val prompts = mutableListOf<String>()

        override suspend fun complete(request: AiRequest): AiResult {
            prompts += request.prompt
            return result ?: AiResult.Error(AiResult.ErrorKind.NETWORK, "no script")
        }
    }

    private class FakePrefs : AiPracticePrefs {
        var enabled = true
        var cap = 50
        var sentCount = 0
        var sentDay = -1L
        var lastDigestAt = 0L
        override var aiProvider: String = "melious"
        override var aiBaseUrl: String = "https://api.melious.ai"
        override var aiModel: String = "glm-5.3-flash"
        override var aiPracticeEnabled: Boolean
            get() = enabled
            set(value) { enabled = value }
        override var aiDailyWordCap: Int
            get() = cap
            set(value) { cap = value }
        override var aiKeyRejected: Boolean = false
        override var aiBillingBlocked: Boolean = false
        override var aiLastDigestAt: Long
            get() = lastDigestAt
            set(value) { lastDigestAt = value }
        override fun aiWordsSentToday(todayEpochDay: Long): Int =
            if (sentDay == todayEpochDay) sentCount else 0
        override fun recordAiWordsSent(count: Int, todayEpochDay: Long) {
            if (sentDay != todayEpochDay) {
                sentDay = todayEpochDay
                sentCount = 0
            }
            sentCount += count
        }
    }

    private fun event(wordId: Long, id: Long, reviewedAt: Long, result: String) = ReviewEvent(
        id = id, wordId = wordId, reviewedAt = reviewedAt, result = result, boxBefore = 1, boxAfter = 1,
    )

    private fun again(wordId: Long, id: Long, ageDays: Long = 1) =
        event(wordId, id, now - ageDays * dayMs, ReviewEvent.Result.AGAIN)

    private fun word(id: Long, context: String = "Die Arbeit insistiert auf seiner Unterschrift.") =
        Word(id = id, text = "Wort$id", context = context, dueDay = 20_261_027, addedAt = id)

    private fun okJson(vararg wordIds: Long): String {
        val items = wordIds.joinToString(",") { id ->
            """{"word_id": $id, "type": "cloze", "prompt": "Die Arbeit ___ auf seiner Unterschrift.",
               "answer": "insistiert", "choices": ["insistiert", "insistieren"]}"""
        }
        return """{"items": [$items]}"""
    }

    private fun generator(provider: AiProvider) = PracticeGenerator(
        provider = provider,
        store = store,
        model = prefs.aiModel,
        keySource = KeySource { "sk-test-key" },
    )

    // --- due: the 7-day boundary ---

    @Test
    fun `six days after a digest with no pattern is not due`() {
        val lastDigestAt = now - 6 * dayMs
        assertFalse(PracticeDigest.due(lastDigestAt, now, emptyList()))
    }

    @Test
    fun `seven days after a digest is due`() {
        val lastDigestAt = now - 7 * dayMs
        assertTrue(PracticeDigest.due(lastDigestAt, now, emptyList()))
    }

    @Test
    fun `never digested is due`() {
        assertTrue(PracticeDigest.due(0L, now, emptyList()))
    }

    // --- due: the weak-spot threshold fires before the week ---

    @Test
    fun `two AGAIN on one word fire the threshold before 7 days`() {
        val lastDigestAt = now - dayMs
        // Both AGAINs are newer than the digest, so they are a fresh pattern.
        val events = listOf(
            again(wordId = 3, id = 1, ageDays = 0),
            again(wordId = 3, id = 2, ageDays = 0),
        )
        assertTrue(PracticeDigest.due(lastDigestAt, now, events))
    }

    @Test
    fun `no pattern with a fresh digest is not due`() {
        val lastDigestAt = now - dayMs
        // One AGAIN is not a pattern; KNEW never is; AGAINs outside the 14-day window are not.
        val events = listOf(
            again(wordId = 3, id = 1),
            event(4, 2, now - 2 * dayMs, ReviewEvent.Result.KNEW),
            again(wordId = 5, id = 3, ageDays = 15),
            again(wordId = 5, id = 4, ageDays = 16),
        )
        assertFalse(PracticeDigest.due(lastDigestAt, now, events))
    }

    @Test
    fun `two AGAIN on different words do not fire the threshold`() {
        val lastDigestAt = now - dayMs
        val events = listOf(again(wordId = 3, id = 1), again(wordId = 4, id = 2))
        assertFalse(PracticeDigest.due(lastDigestAt, now, events))
    }

    @Test
    fun `AGAINs from before the last digest do not re-fire the threshold`() {
        val lastDigestAt = now - dayMs
        // The same two AGAINs that fired the previous digest: they stay inside the
        // 14-day window but are older than the digest — the threshold must not
        // re-fire every day for them (review R1 #4).
        val digested = listOf(
            again(wordId = 3, id = 1, ageDays = 2),
            again(wordId = 3, id = 2, ageDays = 3),
        )
        assertFalse(PracticeDigest.due(lastDigestAt, now, digested))

        // A single new AGAIN after the digest is still below the threshold…
        val oneNew = digested + again(wordId = 3, id = 3, ageDays = 0)
        assertFalse(PracticeDigest.due(lastDigestAt, now, oneNew))

        // …two new AGAINs on the word fire it again.
        val twoNew = oneNew + again(wordId = 3, id = 4, ageDays = 0)
        assertTrue(PracticeDigest.due(lastDigestAt, now, twoNew))
    }

    // --- summary: the weak spots ---

    @Test
    fun `summary lists weak words and weak drill types`() {
        val events = listOf(
            again(wordId = 3, id = 1), again(wordId = 3, id = 2), // weak: 2 AGAIN
            again(wordId = 4, id = 3), // not weak: 1 AGAIN
            again(wordId = 5, id = 4, ageDays = 20), again(wordId = 5, id = 5, ageDays = 21), // outside window
        )
        val scores = mapOf(
            DrillType.PLURALS.id to DrillScores.Score(asked = 10, firstTryRight = 4), // 40%: weak
            DrillType.ARTICLES.id to DrillScores.Score(asked = 10, firstTryRight = 9), // 90%: fine
            DrillType.CLOZE.id to DrillScores.Score(asked = 0, firstTryRight = 0), // never played
        )

        val weak = PracticeDigest.summary(events, scores, now)

        assertEquals(listOf(3L), weak.wordIds)
        assertEquals(listOf(DrillType.PLURALS), weak.drillTypes)
        assertFalse(weak.isEmpty)
    }

    @Test
    fun `summary with nothing weak is empty`() {
        val weak = PracticeDigest.summary(
            listOf(event(3, 1, now - dayMs, ReviewEvent.Result.KNEW)),
            mapOf(DrillType.PLURALS.id to DrillScores.Score(asked = 5, firstTryRight = 5)),
            now,
        )
        assertTrue(weak.isEmpty)
    }

    // --- the batched digest call ---

    @Test
    fun `digest sends only weak words and weak types, stored with source digest`() = runBlocking {
        val provider = FakeProvider(result = AiResult.Ok(okJson(3, 5), AiUsage(10, 20), 5))
        val weakWords = listOf(word(3), word(5))

        val outcome = generator(provider).digest(
            weakWords, listOf(DrillType.PLURALS), prefs, today, nowMs = now,
        )

        assertTrue(outcome is PracticeGeneration.Generated)
        // One chunk call plus its retry-on-missing follow-up (both words stayed
        // thin on one item); the follow-up prompt covers exactly the thin words.
        assertEquals(2, provider.prompts.size)
        val prompt = provider.prompts[0]
        assertTrue(prompt.contains("Wort3"))
        assertTrue(prompt.contains("Wort5"))
        assertTrue(prompt.contains("Pick only types that fit this word: plurals\n"))
        // The digest keeps its explicit type list: no per-word narrowing lines.
        assertFalse(prompt.contains("\n  types:"))
        // The stored items carry the digest source and are served by rotation:
        // per word the AI item plus one fallback card (the padding's copies
        // are the same card, stored once).
        val stored = store.all()
        assertEquals(4, stored.size)
        assertTrue(stored.all { it.source == PracticeItem.Source.DIGEST })
        val pool = stored.filter { it.wordId == 3L }
        assertEquals(pool[0], PracticeRotation.pick(word(3), stored, 0))
        assertEquals(pool[1], PracticeRotation.pick(word(3), stored, 1))
        assertEquals(pool[0], PracticeRotation.pick(word(3), stored, 2))
    }

    @Test
    fun `AI_LAST_DIGEST_AT updates only on success`() = runBlocking {
        val ok = generator(FakeProvider(result = AiResult.Ok(okJson(3), AiUsage(1, 1), 1)))
        assertTrue(ok.digest(listOf(word(3)), listOf(DrillType.PLURALS), prefs, today, nowMs = now)
            is PracticeGeneration.Generated)
        assertEquals(now, prefs.aiLastDigestAt)

        // A provider error leaves the timestamp untouched.
        prefs.lastDigestAt = 0L
        val failed = generator(FakeProvider(result = AiResult.Error(AiResult.ErrorKind.NETWORK, "down")))
        assertTrue(failed.digest(listOf(word(3)), listOf(DrillType.PLURALS), prefs, today, nowMs = now)
            is PracticeGeneration.Failed)
        assertEquals(0L, prefs.aiLastDigestAt)

        // So does a skipped attempt: the gate is off.
        prefs.enabled = false
        val gated = generator(FakeProvider(result = AiResult.Ok(okJson(3), AiUsage(1, 1), 1)))
        val outcome = gated.digest(listOf(word(3)), listOf(DrillType.PLURALS), prefs, today, nowMs = now)
        assertTrue(outcome is PracticeGeneration.Skipped)
        assertEquals(PracticeGeneration.Reason.DISABLED, (outcome as PracticeGeneration.Skipped).reason)
        assertEquals(0L, prefs.aiLastDigestAt)
    }

    @Test
    fun `digest respects the daily word cap`() = runBlocking {
        prefs.cap = 50
        prefs.recordAiWordsSent(50, today.toEpochDay())
        val provider = FakeProvider(result = AiResult.Ok(okJson(3), AiUsage(1, 1), 1))

        val outcome = generator(provider).digest(listOf(word(3)), listOf(DrillType.PLURALS), prefs, today, nowMs = now)

        assertTrue(outcome is PracticeGeneration.Skipped)
        assertEquals(PracticeGeneration.Reason.CAP_REACHED, (outcome as PracticeGeneration.Skipped).reason)
        assertTrue(provider.prompts.isEmpty())
        assertEquals(0L, prefs.aiLastDigestAt)
    }

    @Test
    fun `a truncated digest response stores nothing and leaves AI_LAST_DIGEST_AT`() = runBlocking {
        // HTTP 200, body cut off mid-JSON: parse rejects everything, so the
        // digest must count as failed — no timestamp, no announcement (review R1 #5).
        val provider = FakeProvider(result = AiResult.Ok("{\"items\": [{\"word_id\": 3, \"type\": \"clo\"", AiUsage(10, 4096), 5))

        val outcome = generator(provider).digest(listOf(word(3)), listOf(DrillType.PLURALS), prefs, today, nowMs = now)

        assertTrue(outcome is PracticeGeneration.Failed)
        assertEquals(AiResult.ErrorKind.BAD_RESPONSE, (outcome as PracticeGeneration.Failed).kind)
        assertTrue(store.all().isEmpty())
        assertEquals(0L, prefs.aiLastDigestAt)
        // The words still went out and were billed: the cap counted them.
        assertEquals(1, prefs.aiWordsSentToday(today.toEpochDay()))
    }
}
