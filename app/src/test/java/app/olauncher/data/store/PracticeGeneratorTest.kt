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
 * The one generation path behind every worker run (#601, 9.8): gate, daily word cap,
 * chunking, and the AUTH / BILLING bookkeeping. The
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
        // A model without a json_object preference keeps these tests on the
        // strict-schema default path; glm-5.3-flash's json_object start has its
        // own test below.
        override var aiModel: String = "gemma-4-31b"
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
        val items = wordIds.mapIndexed { index, id ->
            """{"word_id": $id, "type": "cloze", "prompt": "Sie ___ (insistieren) auf der Arbeit $index.",
               "answer": "insistiert$index", "choices": ["insistiert$index", "insistieren"]}"""
        }.joinToString(",")
        return """{"items": [$items]}"""
    }

    private fun generator(provider: AiProvider) = PracticeGenerator(
        provider = provider,
        store = store,
        model = prefs.aiModel,
        keySource = KeySource { "sk-test-key" },
    )

    private fun generatorWithModel(provider: AiProvider, model: String) = PracticeGenerator(
        provider = provider,
        store = store,
        model = model,
        keySource = KeySource { "sk-test-key" },
    )

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
        // One chunk call plus its retry-on-missing follow-up (round 3).
        assertEquals(2, provider.prompts.size)
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
        // Every word stays thin on one item, so each chunk also gets its one
        // retry-on-missing follow-up (same small size, round 3).
        assertTrue(outcome is PracticeGeneration.Generated)
        assertEquals(20, provider.requests.size)
        for (request in provider.requests) {
            assertTrue(
                "max_tokens ${request.maxTokens} exceeds the per-word budget",
                request.maxTokens <= PracticeGenerator.WORDS_PER_REQUEST * PracticeGenerator.MAX_TOKENS_PER_WORD,
            )
            // Each request's prompt carries at most the chunk's words (plus the
            // one example item in the schema).
            assertTrue(request.prompt.split("word_id ").size - 1 <= PracticeGenerator.WORDS_PER_REQUEST + 1)
            assertEquals(PracticeGenerator.BACKGROUND_READ_TIMEOUT_MS, request.readTimeoutMs)
            // Round 3: every request asks for strict json_schema first.
            assertTrue(request.prompt.contains("write exactly 3 cards"))
        }
        assertTrue(provider.requests.all { it.jsonSchema != null })
        // Every word was billed exactly once across the chunks; the follow-up
        // calls stay inside the same accounting (no extra cap counts).
        assertEquals(50, prefs.aiWordsSentToday(todayEpochDay))
    }

    @Test
    fun `requests carry the contract schema and fall back to json_object on model_not_capable`() = runBlocking {
        val provider = object : AiProvider {
            val requests = mutableListOf<AiRequest>()
            var calls = 0
            override suspend fun complete(request: AiRequest): AiResult {
                requests += request
                // The first call (strict json_schema) is rejected as not capable;
                // the retry must come back with plain json_object.
                return if (calls++ == 0) {
                    AiResult.Error(AiResult.ErrorKind.BAD_RESPONSE, "not capable", errorCode = "model_not_capable")
                } else {
                    AiResult.Ok(okJson(1), AiUsage(1, 1), 1)
                }
            }
        }

        val outcome = generator(provider).topUp(listOf(word(1)), prefs, today)

        assertTrue(outcome is PracticeGeneration.Generated)
        assertTrue("first request was strict", provider.requests[0].jsonSchema != null)
        assertTrue("fallback request dropped the schema", provider.requests[1].jsonSchema == null)
        // Chunk call + fallback retry + the retry-on-missing follow-up (word 1
        // stayed thin on one item) — the follow-up also runs in json_object.
        assertEquals(3, provider.requests.size)
        assertTrue(provider.requests[2].jsonSchema == null)
    }

    @Test
    fun `a real failure on the strict schema does not fall back`() = runBlocking {
        val provider = object : AiProvider {
            val requests = mutableListOf<AiRequest>()
            override suspend fun complete(request: AiRequest): AiResult {
                requests += request
                return AiResult.Error(AiResult.ErrorKind.BAD_RESPONSE, "truncated")
            }
        }

        val outcome = generator(provider).topUp(listOf(word(1)), prefs, today)

        assertTrue(outcome is PracticeGeneration.Failed)
        assertEquals(AiResult.ErrorKind.BAD_RESPONSE, (outcome as PracticeGeneration.Failed).kind)
        assertEquals(1, provider.requests.size)
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
        // 11 words → chunks [1–5], [6–10], [11]. Calls in order: chunk 1 OK,
        // its follow-up (retry-on-missing) is junk and ignored, chunk 2 is junk
        // and skipped, chunk 3 OK, its follow-up tops word 11 up to 2 items.
        val words = (1L..11L).map { word(it) }
        val provider = ScriptedProvider(
            AiResult.Ok(okJson(1, 2, 3, 4, 5), AiUsage(10, 20), 5),
            AiResult.Ok("{\"items\": [{\"word_id\": 6, \"type\": \"clo\"", AiUsage(10, 4096), 5),
            AiResult.Ok("{\"items\": [{\"word_id\": 6, \"type\": \"clo\"", AiUsage(10, 4096), 5),
            AiResult.Ok(okJson(11), AiUsage(10, 20), 5),
            AiResult.Ok(okJson(11), AiUsage(10, 20), 5),
        )

        val outcome = generator(provider).topUp(words, prefs, today)

        // Chunk 2 was skipped, chunk 3 was still attempted (review R2 B); each
        // good chunk added its one follow-up call (round 3).
        assertEquals(5, provider.requests.size)
        assertTrue(outcome is PracticeGeneration.Generated)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L, 11L), (outcome as PracticeGeneration.Generated).words.map { it.id })
        // Chunk 1's and chunk 3's items are in the store: each word's parsed
        // item plus one cloze fallback (the padding's rotated copies are the
        // same card, stored once); chunk 2's stay queued.
        assertEquals(12, store.all().size)
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
        // The parsed item plus one cloze fallback per word (the padding's
        // copies are the same card, stored once).
        assertEquals(10, partial.itemCount)
        assertEquals((1L..5L).toList(), partial.words.map { it.id })
        assertEquals(10, store.all().size)
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

    /** A cloze item for [wordId] whose prompt and answer carry [tag], so copies are exact. */
    private fun clozeJson(wordId: Long, tag: String) =
        """{"word_id": $wordId, "type": "cloze", "prompt": "Sie ___ (insistieren) auf dem Vertrag $tag.",
           "answer": "insistiert$tag", "choices": ["insistiert$tag", "insistieren"]}"""

    @Test
    fun `an AI reply that repeats a card still gets a fallback and leaves the queue`() = runBlocking {
        // [A, A, B]: parse accepts three items, but the store keeps only A and
        // B. The fallback count must see two unique cards, so the cloze
        // fallback is stored and the word is stocked — not resent every run.
        val target = word(73, context = "Der Anwalt insistiert auf dem Vertrag.")
        val reply = """{"items": [${clozeJson(73, "Alpha")}, ${clozeJson(73, "Alpha")}, ${clozeJson(73, "Beta")}]}"""
        val provider = ScriptedProvider(AiResult.Ok(reply, AiUsage(10, 20), 5))

        val outcome = generator(provider).topUp(listOf(target), prefs, today)

        assertTrue(outcome is PracticeGeneration.Generated)
        assertEquals(1, provider.requests.size)
        val stored = store.all()
        assertEquals(listOf("insistiertAlpha", "insistiertBeta"), stored.filter { it.model != null }.map { it.answer })
        val fallback = PracticeContract.fallbacksFor(target, 1, PracticeItem.Source.WORD).first()
        assertEquals(1, stored.count { it.model == null && PracticeCardKey.of(it) == PracticeCardKey.of(fallback) })
        assertEquals(3, stored.size)
        assertTrue(PracticeQueue.next(listOf(target), stored, today, cap = 50, random = Random(7)).isEmpty())
    }

    @Test
    fun `a reply that only repeats stored cards still gets a fallback and leaves the queue`() = runBlocking {
        // The word already holds two AI cards and no fallback; the reply sends
        // both back as exact copies. The store skips them, so the fallback count
        // must see two unique cards and store the fallback as the third.
        val target = word(74, context = "Die Kanzlei insistiert auf dem Vertrag.")
        store.append(
            PracticeContract.parse(
                """{"items": [${clozeJson(74, "Gamma")}, ${clozeJson(74, "Delta")}]}""",
                listOf(target), prefs.aiModel, padFallbacks = false,
            ).items,
        )
        assertEquals(2, store.all().size)
        assertFalse(PracticeQueue.next(listOf(target), store.all(), today, cap = 50, random = Random(7)).isEmpty())
        val reply = """{"items": [${clozeJson(74, "Gamma")}, ${clozeJson(74, "Delta")}, ${clozeJson(74, "Gamma")}]}"""
        val provider = ScriptedProvider(AiResult.Ok(reply, AiUsage(10, 20), 5))

        generator(provider).topUp(listOf(target), prefs, today)

        val stored = store.all()
        assertEquals(3, stored.size)
        assertEquals(1, stored.count { it.model == null })
        assertTrue(PracticeQueue.next(listOf(target), stored, today, cap = 50, random = Random(7)).isEmpty())
    }

    @Test
    fun `a repeated digest stores no copies of known cards`() = runBlocking {
        // The AI repeats its cloze and the padding rebuilds the same fallback on
        // every run (#749 follow-up): the second digest must add nothing, and
        // still count as done — the call was billed.
        val provider = ScriptedProvider(AiResult.Ok(okJson(1), AiUsage(10, 20), 5))

        generator(provider).digest(listOf(word(1)), emptyList(), prefs, today, nowMs = 1L)
        val afterFirst = store.all()
        val second = generator(provider).digest(listOf(word(1)), emptyList(), prefs, today, nowMs = 2L)

        assertEquals(2, afterFirst.size)
        assertEquals(afterFirst, store.all())
        assertTrue(second is PracticeGeneration.Generated)
        assertEquals(0, (second as PracticeGeneration.Generated).itemCount)
        assertEquals(listOf(1L), second.words.map { it.id })
        assertEquals(2L, prefs.aiLastDigestAt)
    }

    @Test
    fun `a bad chunk next to a chunk of only known cards is not a failure`() = runBlocking {
        // Word 6's cards are already stored, so its chunk stores nothing; the
        // junk chunk must not turn the billed, parsed chunk into a Failed run.
        store.append(
            PracticeContract.parse(okJson(6), listOf(word(6)), prefs.aiModel).let { it.items + it.clozeFallbacks },
        )
        val words = (1L..6L).map { word(it) }
        val provider = ScriptedProvider(
            AiResult.Ok("{\"items\": [{\"word_id\": 1", AiUsage(10, 4096), 5),
            AiResult.Ok(okJson(6), AiUsage(10, 20), 5),
            AiResult.Ok(okJson(6), AiUsage(10, 20), 5),
        )

        val outcome = generator(provider).topUp(words, prefs, today)

        assertTrue(outcome is PracticeGeneration.Generated)
        assertEquals(listOf(6L), (outcome as PracticeGeneration.Generated).words.map { it.id })
        assertEquals(0, outcome.itemCount)
    }

    @Test
    fun `retry-on-missing tops thin words up to three accepted items with one follow-up call`() = runBlocking {
        // First call: only one item for word 1. The follow-up brings the two
        // missing slots as real items, so no cloze fallback is needed.
        val first = """{"items": [{"word_id": 1, "type": "cloze", "prompt": "Sie ___ (insistieren) auf der Arbeit.",
            "answer": "insistiert", "choices": ["insistiert", "insistieren"]}]}"""
        val followUp = """{"items": [
            {"word_id": 1, "type": "conjugation", "prompt": "Wir ___ auf der Arbeit.",
             "answer": "insistieren", "choices": ["insistieren", "insistiert"], "full": "Wir insistieren auf der Arbeit."},
            {"word_id": 1, "type": "praeteritum", "prompt": "Sie ___ (insistieren) auf der Arbeit.",
             "answer": "insistierte", "choices": ["insistierte", "insistiert"], "full": "Sie insistierte (insistieren) auf der Arbeit."}]}"""
        val provider = ScriptedProvider(
            AiResult.Ok(first, AiUsage(10, 20), 5),
            AiResult.Ok(followUp, AiUsage(10, 20), 5),
        )

        val outcome = generator(provider).topUp(listOf(word(1)), prefs, today)

        assertTrue(outcome is PracticeGeneration.Generated)
        assertEquals(2, provider.requests.size)
        // The follow-up prompt carries only the thin word.
        assertTrue(provider.requests[1].prompt.contains("word_id 1"))
        // Three accepted AI items, none of them a locally built fallback
        // (fallbacks carry no model name).
        val stored = store.all()
        assertEquals(3, stored.size)
        assertTrue(stored.all { it.model != null })
        assertEquals(1, stored.count { it.type == DrillType.CONJUGATION })
        assertEquals(1, stored.count { it.type == DrillType.PRAETERITUM })
    }

    @Test
    fun `a failed follow-up is ignored and cloze fallbacks fill the rest`() = runBlocking {
        val first = """{"items": [{"word_id": 1, "type": "cloze", "prompt": "Sie ___ (insistieren) auf der Arbeit.",
            "answer": "insistiert", "choices": ["insistiert", "insistieren"]}]}"""
        val provider = ScriptedProvider(
            AiResult.Ok(first, AiUsage(10, 20), 5),
            AiResult.Error(AiResult.ErrorKind.NETWORK, "dropped"),
        )

        val outcome = generator(provider).topUp(listOf(word(1)), prefs, today)

        // The follow-up failed once and was NOT repeated (retry-on-missing is
        // once per run); the word ends with its AI item and one fallback card
        // (the padding's two copies are the same card, stored once), which
        // keeps it out of the queue.
        assertTrue(outcome is PracticeGeneration.Generated)
        assertEquals(2, provider.requests.size)
        assertEquals(2, store.all().size)
        assertEquals(1, store.all().count { it.model == null })
        assertTrue(PracticeQueue.next(listOf(word(1)), store.all(), today, cap = 50, random = Random(1)).isEmpty())
    }

    @Test
    fun `word generation narrows the types per word`() = runBlocking {
        val words = listOf(
            word(1).copy(text = "Unterschrift"),
            word(2).copy(text = "insistieren"),
            word(3).copy(text = "schnell"), // unknown class → no types line
        )
        val provider = FakeProvider(result = AiResult.Ok(okJson(1), AiUsage(10, 20), 5))

        val outcome = generator(provider).topUp(words, prefs, today)

        assertTrue(outcome is PracticeGeneration.Generated)
        val prompt = provider.prompts[0]
        assertTrue(prompt.contains("  types: ${PracticeTypeNarrowing.NOUN_TYPES.joinToString(", ") { it.id }}\n"))
        assertTrue(prompt.contains("  types: ${PracticeTypeNarrowing.VERB_TYPES.joinToString(", ") { it.id }}\n"))
        // The digest path keeps its explicit type list without narrowing.
        val digestProvider = FakeProvider(result = AiResult.Ok(okJson(1), AiUsage(10, 20), 5))
        generator(digestProvider).digest(
            words, weakTypes = listOf(DrillType.CONJUGATION), prefs = prefs, today = today,
        )
        val digestPrompt = digestProvider.prompts[0]
        assertFalse(digestPrompt.contains("  types:"))
        assertTrue(digestPrompt.contains("Pick only types that fit this word: conjugation"))
    }

    // ------------------------------------------------------------------
    // epic 9 final review conditions (2026-10-28)
    // ------------------------------------------------------------------

    @Test
    fun `glm 5 3 flash starts in json_object without a capability round-trip`() = runBlocking {
        // Condition 1: the passing benchmark configuration for this model is
        // json_object, so the very first request goes out without the strict
        // schema — no model_not_capable detour that costs a billed call.
        val provider = FakeProvider(result = AiResult.Ok(okJson(1), AiUsage(10, 20), 5))

        val outcome = generatorWithModel(provider, "glm-5.3-flash").topUp(listOf(word(1)), prefs, today)

        assertTrue(outcome is PracticeGeneration.Generated)
        // Chunk call plus its retry-on-missing follow-up (word 1 stays thin on
        // one item) — both in json_object, neither carrying the schema.
        assertEquals(2, provider.requests.size)
        assertTrue(provider.requests.all { it.jsonSchema == null })
    }

    @Test
    fun `the token budget covers the measured per-word output and the cap still counts words`() = runBlocking {
        // Condition 2: the passing json_object run measured 34,849–38,434 output
        // tokens per 60 items — about 1,750–1,920 per word — so 1,200 cut real
        // chunks off mid-JSON. 2,400 leaves headroom.
        assertEquals(2400, PracticeGenerator.MAX_TOKENS_PER_WORD)
        assertTrue(
            PracticeGenerator.MAX_TOKENS_PER_WORD * PracticeGenerator.WORDS_PER_REQUEST >= 5 * 1920,
        )
        // The daily cap is untouched: it counts WORDS at send time, never tokens
        // — the retry follow-up adds calls, not cap counts.
        prefs.cap = 50
        val words = (1L..3L).map { word(it) }
        val provider = FakeProvider(result = AiResult.Ok(okJson(1), AiUsage(10, 20), 5))

        generator(provider).topUp(words, prefs, today)

        assertEquals(3, prefs.aiWordsSentToday(todayEpochDay))
    }

    @Test
    fun `retry skips the duplicate cloze when the word already has one`() = runBlocking {
        // Condition 3: the retry prompt still asks for the cloze first, so the
        // response's first item is a second copy of the accepted cloze. It must
        // not be taken; the two non-cloze items behind it fill the slots.
        val first = """{"items": [{"word_id": 1, "type": "cloze", "prompt": "Sie ___ (insistieren) auf der Arbeit.",
            "answer": "insistiert", "choices": ["insistiert", "insistieren"]}]}"""
        val followUp = """{"items": [
            {"word_id": 1, "type": "cloze", "prompt": "Sie ___ (insistieren) auf der Arbeit.",
             "answer": "insistiert", "choices": ["insistiert", "insistieren"], "full": "Sie insistiert (insistieren) auf der Arbeit."},
            {"word_id": 1, "type": "conjugation", "prompt": "Wir ___ auf der Arbeit.",
             "answer": "insistieren", "choices": ["insistieren", "insistiert"], "full": "Wir insistieren auf der Arbeit."},
            {"word_id": 1, "type": "praeteritum", "prompt": "Sie ___ (insistieren) auf der Arbeit.",
             "answer": "insistierte", "choices": ["insistierte", "insistiert"], "full": "Sie insistierte (insistieren) auf der Arbeit."}]}"""
        val provider = ScriptedProvider(
            AiResult.Ok(first, AiUsage(10, 20), 5),
            AiResult.Ok(followUp, AiUsage(10, 20), 5),
        )

        val outcome = generator(provider).topUp(listOf(word(1)), prefs, today)

        assertTrue(outcome is PracticeGeneration.Generated)
        assertEquals(2, provider.requests.size)
        val stored = store.all()
        // Three real AI items: the word's one cloze plus the two non-cloze
        // slots — no duplicate cloze, no locally built fallback.
        assertEquals(3, stored.size)
        assertTrue(stored.all { it.model != null })
        assertEquals(1, stored.count { it.type == DrillType.CLOZE })
        assertEquals(1, stored.count { it.type == DrillType.CONJUGATION })
        assertEquals(1, stored.count { it.type == DrillType.PRAETERITUM })
    }

    @Test
    fun `retry items repeating an accepted answer are skipped against the whole pool`() = runBlocking {
        // Condition 3, duplicate check: parse only sees the retry batch, so a
        // retry item repeating the FIRST call's answer must be dropped here —
        // case- and punctuation-insensitively — with fallbacks filling the slot.
        val first = """{"items": [{"word_id": 1, "type": "cloze", "prompt": "Sie ___ (insistieren) auf der Arbeit.",
            "answer": "insistiert", "choices": ["insistiert", "insistieren"]}]}"""
        val followUp = """{"items": [{"word_id": 1, "type": "conjugation", "prompt": "Wir ___ auf der Arbeit.",
             "answer": "Insistiert.", "choices": ["Insistiert.", "insistieren"], "full": "Wir insistiert auf der Arbeit."}]}"""
        val provider = ScriptedProvider(
            AiResult.Ok(first, AiUsage(10, 20), 5),
            AiResult.Ok(followUp, AiUsage(10, 20), 5),
        )

        val outcome = generator(provider).topUp(listOf(word(1)), prefs, today)

        assertTrue(outcome is PracticeGeneration.Generated)
        val stored = store.all()
        assertEquals(2, stored.size)
        // Only the first call's cloze is a real item; the repeated answer never
        // stored, the two missing slots are padded with one fallback card (its
        // copies are the same card, stored once).
        assertEquals(1, stored.count { it.model != null })
        assertEquals(1, stored.count { it.model == null })
        // The repeated answer never stored as a conjugation.
        assertEquals(0, stored.count { it.type == DrillType.CONJUGATION })
        assertEquals(1, stored.count { it.type == DrillType.CLOZE && it.model != null })
    }

    @Test
    fun `a retry that returns only clozes falls back as before`() = runBlocking {
        // Condition 3, fallback path: the word has its cloze, the retry brings
        // nothing but clozes — the accepted set stays and the cloze fallbacks
        // fill the two missing slots exactly like the pre-fix behaviour.
        val first = """{"items": [{"word_id": 1, "type": "cloze", "prompt": "Sie ___ (insistieren) auf der Arbeit.",
            "answer": "insistiert", "choices": ["insistiert", "insistieren"]}]}"""
        val followUp = """{"items": [
            {"word_id": 1, "type": "cloze", "prompt": "Sie ___ (insistieren) auf der Arbeit.",
             "answer": "insistiert", "choices": ["insistiert", "insistieren"], "full": "Sie insistiert (insistieren) auf der Arbeit."},
            {"word_id": 1, "type": "cloze", "prompt": "Wir ___ auf der Arbeit.",
             "answer": "insistieren", "choices": ["insistieren", "insistiert"], "full": "Wir insistieren auf der Arbeit."}]}"""
        val provider = ScriptedProvider(
            AiResult.Ok(first, AiUsage(10, 20), 5),
            AiResult.Ok(followUp, AiUsage(10, 20), 5),
        )

        val outcome = generator(provider).topUp(listOf(word(1)), prefs, today)

        assertTrue(outcome is PracticeGeneration.Generated)
        val stored = store.all()
        assertEquals(2, stored.size)
        // One AI cloze only; the second retry cloze ("insistieren") is skipped
        // even though its answer differs — the word already has its cloze. The
        // padding stores its fallback card once.
        assertEquals(1, stored.count { it.type == DrillType.CLOZE && it.model != null })
        assertEquals(1, stored.count { it.model == null })
    }

    @Test
    fun `a run sends only words short of three items, at most five per request`() = runBlocking {
        val items = listOf(
            PracticeItem(
                wordId = 7, type = DrillType.CLOZE, prompt = "a", answer = "a",
                choices = listOf("a", "b"), createdAt = 1,
            ),
            PracticeItem(
                wordId = 7, type = DrillType.CLOZE, prompt = "b", answer = "b",
                choices = listOf("b", "c"), createdAt = 2,
            ),
            PracticeItem(
                wordId = 7, type = DrillType.CLOZE, prompt = "c", answer = "c",
                choices = listOf("c", "d"), createdAt = 3,
            ),
        )
        tempDir.resolve("practice_items.json").writeText(PracticeItemsCodec.encode(items))
        val words = (1L..7L).map { word(it) }
        val queue = PracticeQueue.next(words, store.all(), today, cap = prefs.cap, random = Random(1))
        assertEquals((1L..6L).toSet(), queue.map { it.id }.toSet())

        val provider = FakeProvider(result = AiResult.Ok(okJson(1, 2, 3, 4, 5, 6), AiUsage(10, 20), 5))
        generator(provider).topUp(queue, prefs, today)

        assertTrue(provider.prompts.none { it.contains("word_id 7") })
        val countInFirst = (1L..6L).count { provider.requests[0].prompt.contains("word_id $it") }
        assertEquals(5, countInFirst)
        assertEquals(5 * PracticeGenerator.MAX_TOKENS_PER_WORD, provider.requests[0].maxTokens)
        assertEquals(6, prefs.aiWordsSentToday(todayEpochDay))
    }

    @Test
    fun `a run with every pool full makes no request`() = runBlocking {
        val items = (1L..2L).flatMap { id ->
            (1..3).map { i ->
                PracticeItem(
                    wordId = id, type = DrillType.CLOZE, prompt = "p$i", answer = "a$i",
                    choices = listOf("a$i", "b$i"), createdAt = i.toLong(),
                )
            }
        }
        tempDir.resolve("practice_items.json").writeText(PracticeItemsCodec.encode(items))
        val words = listOf(word(1), word(2))
        val queue = PracticeQueue.next(words, store.all(), today, cap = prefs.cap, random = Random(1))
        assertTrue(queue.isEmpty())

        val provider = FakeProvider()
        generator(provider).topUp(emptyList(), prefs, today)

        assertTrue(provider.requests.isEmpty())
        assertEquals(0, prefs.aiWordsSentToday(todayEpochDay))
    }
}
