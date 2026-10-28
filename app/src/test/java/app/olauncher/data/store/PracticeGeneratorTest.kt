package app.olauncher.data.store

import app.olauncher.data.AiPracticePrefs
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The one generation path behind both triggers (#601, 9.8): gate, daily word cap,
 * on-demand timeout with fallback, and the AUTH / BILLING bookkeeping. The
 * provider is faked — no network in tests — and the key never leaves [KeySource].
 */
class PracticeGeneratorTest {

    private val today: LocalDate = LocalDate.of(2026, 10, 27)
    private val todayEpochDay: Long = today.toEpochDay()

    private lateinit var tempDir: File
    private lateinit var store: PracticeItemsStore
    private lateinit var prefs: FakePrefs

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("practice_generator_test").toFile()
        store = PracticeItemsStore(tempDir)
        prefs = FakePrefs()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    /** Scripted provider: records every request, returns the queued result or hangs. */
    private class FakeProvider(var result: AiResult? = null, var hangMs: Long = 0) : AiProvider {
        val prompts = mutableListOf<String>()
        val requests = mutableListOf<AiRequest>()

        override suspend fun complete(request: AiRequest): AiResult {
            prompts += request.prompt
            requests += request
            if (hangMs > 0) {
                kotlinx.coroutines.delay(hangMs)
            }
            return result ?: AiResult.Error(AiResult.ErrorKind.NETWORK, "no script")
        }
    }

    /**
     * Blocks the thread like the real HttpURLConnection does (no suspension
     * point): a cancelled caller can only stop it by interrupting the thread —
     * the suspending delay() fake above could never pin that (review R1 #2).
     */
    private class BlockingProvider(private val hangMs: Long) : AiProvider {
        val requests = mutableListOf<AiRequest>()

        override suspend fun complete(request: AiRequest): AiResult {
            requests += request
            return kotlinx.coroutines.runInterruptible(kotlinx.coroutines.Dispatchers.IO) {
                Thread.sleep(hangMs)
                AiResult.Error(AiResult.ErrorKind.NETWORK, "unreachable")
            }
        }
    }

    /** Scripted provider: pops one queued result per call; the last one repeats. */
    private class ScriptedProvider(vararg results: AiResult) : AiProvider {
        private val queue = results.toMutableList()
        val requests = mutableListOf<AiRequest>()

        override suspend fun complete(request: AiRequest): AiResult {
            requests += request
            return if (queue.size > 1) queue.removeAt(0) else queue.first()
        }
    }

    private class FakePrefs : AiPracticePrefs {
        var enabled = true
        var cap = 50
        var sentCount = 0
        var sentDay = -1L
        var keyRejected = false
        var billingBlocked = false
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
        override var aiKeyRejected: Boolean
            get() = keyRejected
            set(value) { keyRejected = value }
        override var aiBillingBlocked: Boolean
            get() = billingBlocked
            set(value) { billingBlocked = value }
        override fun aiWordsSentToday(todayEpochDay: Long): Int =
            if (sentDay == todayEpochDay) sentCount else 0
        override fun recordAiWordsSent(count: Int, todayEpochDay: Long) {
            if (sentDay != todayEpochDay) {
                sentDay = todayEpochDay
                sentCount = 0
            }
            sentCount += count
        }
        override var aiLastDigestAt: Long
            get() = lastDigestAt
            set(value) { lastDigestAt = value }
    }

    private fun word(id: Long, context: String = "Die Arbeit ___ auf seiner Unterschrift.") =
        Word(id = id, text = "insistieren", context = context, dueDay = 20_261_027, addedAt = id)

    private fun okJson(vararg wordIds: Long): String {
        val items = wordIds.joinToString(",") { id ->
            """{"word_id": $id, "type": "cloze", "prompt": "Sie ___ auf der Arbeit.",
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

    @Test
    fun `on-demand generates for thin words only and stores the items`() = runBlocking {
        // Word 5 has 1 item (thin); word 6 has 2 (not thin) — only word 5 goes out.
        val items = listOf(
            PracticeItem(
                wordId = 5, type = DrillType.CLOZE, prompt = "alt", answer = "alt",
                choices = listOf("alt", "altes"), createdAt = 1,
            ),
            PracticeItem(
                wordId = 6, type = DrillType.CLOZE, prompt = "a", answer = "a",
                choices = listOf("a", "b"), createdAt = 1,
            ),
            PracticeItem(
                wordId = 6, type = DrillType.CLOZE, prompt = "b", answer = "b",
                choices = listOf("b", "c"), createdAt = 2,
            ),
        )
        tempDir.resolve("practice_items.json").writeText(PracticeItemsCodec.encode(items))
        val provider = FakeProvider(result = AiResult.Ok(okJson(5), AiUsage(10, 20), 5))

        val outcome = generator(provider).onDemand(listOf(word(5), word(6)), prefs, today)

        assertEquals(1, provider.prompts.size)
        assertTrue(provider.prompts[0].contains("word_id 5"))
        assertFalse(provider.prompts[0].contains("word_id 6"))
        assertTrue(outcome is PracticeGeneration.Generated)
        assertEquals(5, (outcome as PracticeGeneration.Generated).words.single().id)
        // 3 new items for word 5 landed in the store, alongside the untouched 3 old ones.
        assertEquals(6, store.all().size)
        assertEquals(1, prefs.aiWordsSentToday(todayEpochDay))
    }

    @Test
    fun `on-demand timeout interrupts the call and still counts the words as sent`() = runBlocking {
        // Blocks the thread for far longer than the deadline: only a real
        // interruption returns in time — the old suspending fake never pinned it.
        val provider = BlockingProvider(hangMs = 20_000)
        val due = listOf(word(7))

        val startedAt = System.currentTimeMillis()
        val outcome = generator(provider).onDemand(due, prefs, today, timeoutMs = 50)
        val elapsed = System.currentTimeMillis() - startedAt

        assertTrue("timeout returned after ${elapsed} ms — the blocking call was not interrupted", elapsed < 10_000)
        assertTrue(outcome is PracticeGeneration.Failed)
        assertTrue((outcome as PracticeGeneration.Failed).timedOut)
        assertEquals(AiResult.ErrorKind.NETWORK, outcome.kind)
        // The request went out and was billed, so the cap counts it even on a
        // timeout (review R1 #2).
        assertEquals(1, prefs.aiWordsSentToday(todayEpochDay))
        // The deadline lives on the connection too, not only around the call.
        assertEquals(50, provider.requests.single().readTimeoutMs)
        // The word is still pool-thin, so the derived queue picks it up: "enqueued".
        val queue = PracticeQueue.next(listOf(word(7)), store.all(), today, cap = 50, Random(1))
        assertEquals(listOf(7L), queue.map { it.id })
    }

    @Test
    fun `daily cap blocks the on-demand path`() = runBlocking {
        prefs.cap = 50
        prefs.recordAiWordsSent(50, todayEpochDay)
        val provider = FakeProvider(result = AiResult.Ok(okJson(1), AiUsage(1, 1), 1))

        val outcome = generator(provider).onDemand(listOf(word(1)), prefs, today)

        assertTrue(outcome is PracticeGeneration.Skipped)
        assertEquals(PracticeGeneration.Reason.CAP_REACHED, (outcome as PracticeGeneration.Skipped).reason)
        assertTrue(provider.prompts.isEmpty())
    }

    @Test
    fun `daily cap blocks the background top-up path and resets next day`() = runBlocking {
        prefs.cap = 50
        prefs.recordAiWordsSent(50, todayEpochDay)
        val provider = FakeProvider(result = AiResult.Ok(okJson(1), AiUsage(1, 1), 1))
        val gen = generator(provider)

        val blocked = gen.topUp(listOf(word(1)), prefs, today)
        assertTrue(blocked is PracticeGeneration.Skipped)
        assertEquals(PracticeGeneration.Reason.CAP_REACHED, (blocked as PracticeGeneration.Skipped).reason)
        assertTrue(provider.prompts.isEmpty())

        // Tomorrow the counter resets by calendar day and generation runs again.
        val tomorrow = today.plusDays(1)
        val allowed = gen.topUp(listOf(word(1)), prefs, tomorrow)
        assertTrue(allowed is PracticeGeneration.Generated)
        assertEquals(1, provider.prompts.size)
        assertEquals(1, prefs.aiWordsSentToday(tomorrow.toEpochDay()))
    }

    @Test
    fun `cap trims the batch to the remaining budget`() = runBlocking {
        prefs.cap = 3
        prefs.recordAiWordsSent(2, todayEpochDay)
        val provider = FakeProvider(result = AiResult.Ok(okJson(1, 2), AiUsage(1, 1), 1))

        val outcome = generator(provider).topUp(listOf(word(1), word(2), word(3)), prefs, today)

        // Only 1 word of the budget was left: word 1 went out, words 2 and 3 stay queued.
        assertTrue(outcome is PracticeGeneration.Generated)
        assertTrue(provider.prompts[0].contains("word_id 1"))
        assertFalse(provider.prompts[0].contains("word_id 2"))
        assertFalse(provider.prompts[0].contains("word_id 3"))
        assertEquals(3, prefs.aiWordsSentToday(todayEpochDay))
    }

    @Test
    fun `auth sets AI_KEY_REJECTED and the words stay queued`() = runBlocking {
        val provider = FakeProvider(result = AiResult.Error(AiResult.ErrorKind.AUTH, "401"))

        val outcome = generator(provider).topUp(listOf(word(1)), prefs, today)

        assertTrue(outcome is PracticeGeneration.Failed)
        assertTrue(prefs.aiKeyRejected)
        assertFalse(prefs.aiBillingBlocked)
        // The request went out, so the cap counts the word even though the
        // provider rejected it (review R1 #2).
        assertEquals(1, prefs.aiWordsSentToday(todayEpochDay))
    }

    @Test
    fun `a 50-word queue is chunked into small requests each under the token budget`() = runBlocking {
        prefs.cap = 50
        val words = (1L..50L).map { word(it) }
        val provider = FakeProvider(result = AiResult.Ok(okJson(1), AiUsage(10, 20), 5))

        val outcome = generator(provider).topUp(words, prefs, today)

        // Several small requests instead of one 50-word call under one 4096 cap.
        assertTrue(outcome is PracticeGeneration.Generated)
        assertEquals(10, provider.requests.size)
        for (request in provider.requests) {
            assertTrue(
                "max_tokens ${request.maxTokens} exceeds the per-word budget",
                request.maxTokens <= PracticeGenerator.WORDS_PER_REQUEST * PracticeGenerator.MAX_TOKENS_PER_WORD,
            )
            // Each request's prompt carries at most the chunk's words (plus the
            // one example item in the schema).
            assertTrue(request.prompt.split("word_id ").size - 1 <= PracticeGenerator.WORDS_PER_REQUEST + 1)
            assertEquals(PracticeGenerator.BACKGROUND_READ_TIMEOUT_MS, request.readTimeoutMs)
        }
        // Every word was billed exactly once across the chunks.
        assertEquals(50, prefs.aiWordsSentToday(todayEpochDay))
    }

    @Test
    fun `a truncated response fails as BAD_RESPONSE with nothing stored`() = runBlocking {
        // HTTP 200 with a body cut off mid-JSON: parse rejects everything.
        val provider = FakeProvider(result = AiResult.Ok("{\"items\": [{\"word_id\": 1, \"type\": \"clo\"", AiUsage(10, 4096), 5))

        val outcome = generator(provider).topUp(listOf(word(1)), prefs, today)

        assertTrue(outcome is PracticeGeneration.Failed)
        assertEquals(AiResult.ErrorKind.BAD_RESPONSE, (outcome as PracticeGeneration.Failed).kind)
        assertTrue(store.all().isEmpty())
        // The words were still sent and billed, so the cap counted them.
        assertEquals(1, prefs.aiWordsSentToday(todayEpochDay))
    }

    @Test
    fun `billing pauses generation without touching the key state`() = runBlocking {
        val provider = FakeProvider(
            result = AiResult.Error(AiResult.ErrorKind.BILLING, "out of credits"),
        )

        val outcome = generator(provider).topUp(listOf(word(1)), prefs, today)

        assertTrue(outcome is PracticeGeneration.Failed)
        assertEquals(AiResult.ErrorKind.BILLING, (outcome as PracticeGeneration.Failed).kind)
        assertTrue(prefs.aiBillingBlocked)
        assertFalse(prefs.aiKeyRejected)
    }

    @Test
    fun `a successful call clears both error states`() = runBlocking {
        prefs.aiKeyRejected = true
        prefs.aiBillingBlocked = true
        val provider = FakeProvider(result = AiResult.Ok(okJson(1), AiUsage(1, 1), 1))

        val outcome = generator(provider).topUp(listOf(word(1)), prefs, today)

        assertTrue(outcome is PracticeGeneration.Generated)
        assertFalse(prefs.aiKeyRejected)
        assertFalse(prefs.aiBillingBlocked)
    }

    @Test
    fun `no generation when AI_PRACTICE_ENABLED is false`() = runBlocking {
        prefs.enabled = false
        val provider = FakeProvider(result = AiResult.Ok(okJson(1), AiUsage(1, 1), 1))

        val outcome = generator(provider).topUp(listOf(word(1)), prefs, today)

        assertTrue(outcome is PracticeGeneration.Skipped)
        assertEquals(PracticeGeneration.Reason.DISABLED, (outcome as PracticeGeneration.Skipped).reason)
        assertTrue(provider.prompts.isEmpty())
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `no key or no model skips without a call`() = runBlocking {
        val noKey = PracticeGenerator(
            provider = FakeProvider(result = AiResult.Ok(okJson(1), AiUsage(1, 1), 1)),
            store = store,
            model = prefs.aiModel,
            keySource = KeySource { null },
        )
        assertTrue(noKey.topUp(listOf(word(1)), prefs, today) is PracticeGeneration.Skipped)

        val noModel = PracticeGenerator(
            provider = FakeProvider(result = AiResult.Ok(okJson(1), AiUsage(1, 1), 1)),
            store = store,
            model = "",
            keySource = KeySource { "sk-test-key" },
        )
        assertTrue(noModel.topUp(listOf(word(1)), prefs, today) is PracticeGeneration.Skipped)
    }

    @Test
    fun `a bad chunk is skipped and later chunks still run`() = runBlocking {
        // 11 words → chunks [1–5], [6–10], [11]; the middle reply is junk.
        val words = (1L..11L).map { word(it) }
        val provider = ScriptedProvider(
            AiResult.Ok(okJson(1, 2, 3, 4, 5), AiUsage(10, 20), 5),
            AiResult.Ok("{\"items\": [{\"word_id\": 6, \"type\": \"clo\"", AiUsage(10, 4096), 5),
            AiResult.Ok(okJson(11), AiUsage(10, 20), 5),
        )

        val outcome = generator(provider).topUp(words, prefs, today)

        // Chunk 2 was skipped, chunk 3 was still attempted (review R2 B).
        assertEquals(3, provider.requests.size)
        assertTrue(outcome is PracticeGeneration.Generated)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 11L), (outcome as PracticeGeneration.Generated).words.map { it.id })
        // Chunk 1's and chunk 3's items are in the store (parse pads each word's
        // pool to 3 with cloze fallbacks); chunk 2's stay queued.
        assertEquals(18, store.all().size)
        assertEquals(11, prefs.aiWordsSentToday(todayEpochDay))
    }

    @Test
    fun `an auth failure after a stored chunk returns a partial outcome`() = runBlocking {
        val words = (1L..11L).map { word(it) }
        val provider = ScriptedProvider(
            AiResult.Ok(okJson(1, 2, 3, 4, 5), AiUsage(10, 20), 5),
            AiResult.Error(AiResult.ErrorKind.AUTH, "401"),
        )

        val outcome = generator(provider).topUp(words, prefs, today)

        // The batch stopped (AUTH), but chunk 1's stored and billed success is
        // honored as a Partial (review R2 B).
        assertTrue(outcome is PracticeGeneration.Partial)
        val partial = outcome as PracticeGeneration.Partial
        assertEquals(AiResult.ErrorKind.AUTH, partial.kind)
        // The parsed item plus the two cloze fallbacks per word.
        assertEquals(15, partial.itemCount)
        assertEquals((1L..5L).toList(), partial.words.map { it.id })
        assertEquals(15, store.all().size)
        assertTrue(prefs.aiKeyRejected)
    }

    @Test
    fun `a batch where every chunk fails still fails as before`() = runBlocking {
        val words = (1L..11L).map { word(it) }
        val provider = ScriptedProvider(
            AiResult.Error(AiResult.ErrorKind.BAD_RESPONSE, "truncated"),
            AiResult.Error(AiResult.ErrorKind.BAD_RESPONSE, "truncated"),
        )

        val outcome = generator(provider).topUp(words, prefs, today)

        // Nothing was ever stored: a failure, not an empty success (R1 #5).
        assertTrue(outcome is PracticeGeneration.Failed)
        assertEquals(AiResult.ErrorKind.BAD_RESPONSE, (outcome as PracticeGeneration.Failed).kind)
        assertTrue(store.all().isEmpty())
        // Every chunk was attempted and billed; the junk reply did not stop the
        // loop, the empty store decided the outcome.
        assertEquals(3, provider.requests.size)
        assertEquals(11, prefs.aiWordsSentToday(todayEpochDay))
    }

    @Test
    fun `a partial digest still timestamps the covered words`() = runBlocking {
        val weak = (1L..11L).map { word(it) }
        val provider = ScriptedProvider(
            AiResult.Ok(okJson(1, 2, 3, 4, 5), AiUsage(10, 20), 5),
            AiResult.Error(AiResult.ErrorKind.NETWORK, "dropped"),
        )

        val outcome = generator(provider).digest(weak, emptyList(), prefs, today, nowMs = 1234L)

        assertTrue(outcome is PracticeGeneration.Partial)
        // The covered words are done — re-digesting them would only duplicate
        // source=digest items (review R2 B) — so the timestamp is set.
        assertEquals(1234L, prefs.aiLastDigestAt)
    }

    @Test
    fun `on-demand sends one small chunk of at most two words`() = runBlocking {
        val due = (1L..5L).map { word(it) }
        val provider = FakeProvider(result = AiResult.Ok(okJson(1, 2), AiUsage(10, 20), 5))

        val outcome = generator(provider).onDemand(due, prefs, today)

        // One 2-word request, not a full 5-word chunk that mostly times out
        // billed (review R2 D).
        assertEquals(1, provider.requests.size)
        assertTrue(provider.prompts[0].contains("word_id 1"))
        assertTrue(provider.prompts[0].contains("word_id 2"))
        assertFalse(provider.prompts[0].contains("word_id 3"))
        assertTrue(outcome is PracticeGeneration.Generated)
        assertEquals(listOf(1L, 2L), (outcome as PracticeGeneration.Generated).words.map { it.id })
        // Words 1–2 are now at the full 3-item pool; only words 3–5 stay thin,
        // so the derived queue hands exactly them to the background top-up.
        val queue = PracticeQueue.next(due, store.all(), today, cap = 50, Random(1))
        assertEquals((3L..5L).toSet(), queue.map { it.id }.toSet())
    }

    @Test
    fun `on-demand skips entirely while the key is rejected`() = runBlocking {
        prefs.aiKeyRejected = true
        val provider = FakeProvider(result = AiResult.Ok(okJson(1), AiUsage(1, 1), 1))

        val outcome = generator(provider).onDemand(listOf(word(1)), prefs, today)

        // Like the worker gate: no call until a new key is saved (review R2 D).
        assertTrue(outcome is PracticeGeneration.Skipped)
        assertEquals(PracticeGeneration.Reason.KEY_REJECTED, (outcome as PracticeGeneration.Skipped).reason)
        assertTrue(provider.prompts.isEmpty())
        assertTrue(store.all().isEmpty())
    }
}
