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

        override suspend fun complete(request: AiRequest): AiResult {
            prompts += request.prompt
            if (hangMs > 0) {
                kotlinx.coroutines.delay(hangMs)
            }
            return result ?: AiResult.Error(AiResult.ErrorKind.NETWORK, "no script")
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
        prefs.aiKeyRejected = true // a successful call clears it

        val outcome = generator(provider).onDemand(listOf(word(5), word(6)), prefs, today)

        assertEquals(1, provider.prompts.size)
        assertTrue(provider.prompts[0].contains("word_id 5"))
        assertFalse(provider.prompts[0].contains("word_id 6"))
        assertTrue(outcome is PracticeGeneration.Generated)
        assertEquals(5, (outcome as PracticeGeneration.Generated).words.single().id)
        // 3 new items for word 5 landed in the store, alongside the untouched 3 old ones.
        assertEquals(6, store.all().size)
        assertEquals(1, prefs.aiWordsSentToday(todayEpochDay))
        assertFalse(prefs.aiKeyRejected)
    }

    @Test
    fun `on-demand timeout falls back and the word stays queued`() = runBlocking {
        val provider = FakeProvider(hangMs = 200)
        val due = listOf(word(7))

        val outcome = generator(provider).onDemand(due, prefs, today, timeoutMs = 20)

        assertTrue(outcome is PracticeGeneration.Failed)
        assertTrue((outcome as PracticeGeneration.Failed).timedOut)
        assertEquals(AiResult.ErrorKind.NETWORK, outcome.kind)
        // Nothing was generated and nothing was billed.
        assertTrue(store.all().isEmpty())
        assertEquals(0, prefs.aiWordsSentToday(todayEpochDay))
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
        assertEquals(0, prefs.aiWordsSentToday(todayEpochDay))
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
}
