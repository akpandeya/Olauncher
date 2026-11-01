package app.olauncher.data.store

import app.olauncher.data.AiPracticePrefs
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rewrite loop for weak word contexts (#693): shared gate, shared app-wide generation
 * mutex, daily word cap billed at send time, at most 3 attempts per job, and the
 * AUTH / BILLING / consume bookkeeping. The provider is faked — no network in tests —
 * and the key never leaves [KeySource].
 */
class SentenceRewriterTest {

    private val today: LocalDate = LocalDate.of(2026, 11, 1)
    private val todayEpochDay: Long = today.toEpochDay()

    /** Distinguishing input: a harvested sentence whose id must reach the consume callback. */
    private val sourceText = "Der Notar sandte den Vertrag zur Post."

    private fun source(id: String) = RecentSentence(
        id = id,
        text = sourceText,
        source = "news",
        url = "https://example.invalid/article",
        publishedAt = 1L,
        fetchedAt = 1L,
    )

    private val sourceA = source("5e7a1f0c9b2d4e6a")
    private val sourceB = source("b19c4d2e8f605a37")

    /** Small fake lemma map; unknown forms fall back to their lowercase form. */
    private val matcher = SentenceWordMatcher { form ->
        when (form.lowercase(Locale.GERMAN)) {
            "verträge" -> "vertrag"
            "sandte" -> "senden"
            else -> null
        }
    }

    /** Scripted provider: records every request, pops one queued result per call. */
    private class QueueProvider(vararg results: AiResult) : AiProvider {
        private val queue = results.toMutableList()
        val requests = mutableListOf<AiRequest>()

        override suspend fun complete(request: AiRequest): AiResult {
            requests += request
            check(queue.isNotEmpty()) { "provider script exhausted" }
            return queue.removeAt(0)
        }
    }

    private class FakePrefs : AiPracticePrefs {
        var enabled = true
        var cap = 7
        var sentCount = 0
        var sentDay = -1L
        var keyRejected = false
        var billingBlocked = false
        var lastDigestAt = 0L
        override var aiProvider: String = "melious"
        override var aiBaseUrl: String = "https://api.melious.ai"
        // A distinguishing model id: the gate must pass it and the rewriter must be built with it.
        override var aiModel: String = "rewrite-model-x3"
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

    private val prefs = FakePrefs()

    private fun job(source: RecentSentence = sourceA) = RewriteJob(
        word = Word(id = 42L, text = "Vertrag", dueDay = 20_261_101, addedAt = 42L),
        source = source,
    )

    private fun okSentence(text: String) =
        AiResult.Ok("""{"sentence": "$text"}""", AiUsage(10, 20), 5L)

    private fun rewriter(provider: AiProvider, consumed: MutableList<String>) = SentenceRewriter(
        provider = provider,
        model = prefs.aiModel,
        keySource = KeySource { "fake-key" },
        matcher = matcher,
        markConsumed = { consumed += it },
    )

    @Test
    fun `bad bad good is accepted on the third attempt with each rejection reason carried into the next prompt`() = runBlocking {
        val provider = QueueProvider(
            AiResult.Ok("this is not a json object", AiUsage(10, 20), 5L),
            okSentence("Der Vertrag gilt der Kaktus fremd blau schnell."), // grounding failure
            okSentence(sourceText),
        )
        val consumed = mutableListOf<String>()

        val run = rewriter(provider, consumed).rewrite(listOf(job()), prefs, today)

        val accepted = run.outcomes.single() as RewriteOutcome.Accepted
        assertEquals(3, accepted.attempts)
        assertEquals(sourceText, accepted.text)
        assertEquals(listOf("json", "grounding"), accepted.rejections.map { it.filter })
        assertEquals(listOf(1, 2), accepted.rejections.map { it.attempt })
        assertTrue(provider.requests[1].prompt.contains("The answer was not valid JSON (truncated or malformed)"))
        assertTrue(provider.requests[2].prompt.contains("of the rewrite's words come from the original sentence"))
        // Exactly 3 billed calls, each carrying the contract's request shape.
        assertEquals(3, provider.requests.size)
        assertTrue(
            provider.requests.all {
                it.jsonMode && it.maxTokens == RewriteContract.MAX_TOKENS &&
                    it.readTimeoutMs == RewriteContract.READ_TIMEOUT_MS
            },
        )
        // The distinguishing source text and word reach the prompt…
        assertTrue(provider.requests[0].prompt.contains("Der Notar sandte den Vertrag zur Post."))
        assertTrue(provider.requests[0].prompt.contains("\"Vertrag\""))
        // …and the distinguishing source id reaches the consume callback exactly once.
        assertEquals(listOf("5e7a1f0c9b2d4e6a"), consumed)
        assertEquals(3, prefs.aiWordsSentToday(todayEpochDay))
    }

    @Test
    fun `three rejected attempts discard the rewrite and consume the source exactly once`() = runBlocking {
        val provider = QueueProvider(
            AiResult.Ok("still not json", AiUsage(10, 20), 5L), // invalid JSON
            okSentence("Der Vertrag gilt der Kaktus fremd blau schnell."), // grounding failure
            okSentence("Der Kaktus schläft fremd."), // containment failure
        )
        val consumed = mutableListOf<String>()

        val run = rewriter(provider, consumed).rewrite(listOf(job()), prefs, today)

        val discarded = run.outcomes.single() as RewriteOutcome.Discarded
        assertEquals(listOf("json", "grounding", "containment"), discarded.rejections.map { it.filter })
        assertEquals(3, provider.requests.size)
        // Exactly one consume after the third failure, so the source is never retried in a loop.
        assertEquals(listOf("5e7a1f0c9b2d4e6a"), consumed)
        assertEquals(3, prefs.aiWordsSentToday(todayEpochDay))
    }

    @Test
    fun `a grounded rewrite that is itself a weak context is rejected as weak and reprompted`() = runBlocking {
        // Number-heavy but fully grounded: every letter token comes from the source, so the
        // guards accept it — ContextQuality must still reject it, or replaceWeakContexts
        // could not store the result.
        val numberHeavy = "Der Notar sandte den Vertrag 12:30 3 2026 45 60 100 €."
        val provider = QueueProvider(okSentence(numberHeavy), okSentence(sourceText))
        val consumed = mutableListOf<String>()

        val run = rewriter(provider, consumed).rewrite(listOf(job()), prefs, today)

        val accepted = run.outcomes.single() as RewriteOutcome.Accepted
        assertEquals(listOf("weak"), accepted.rejections.map { it.filter })
        assertEquals(sourceText, accepted.text)
        assertTrue(provider.requests[1].prompt.contains(SentenceRewriter.WEAK_CONTEXT_REASON))
        assertEquals(2, provider.requests.size)
        assertEquals(2, accepted.attempts)
        assertEquals(listOf("5e7a1f0c9b2d4e6a"), consumed)
        assertEquals(2, prefs.aiWordsSentToday(todayEpochDay))
    }

    @Test
    fun `a blocked gate skips every job with no call and no counter change`() = runBlocking {
        val jobs = listOf(job(source = sourceA), job(source = sourceB))

        prefs.enabled = false
        val disabledProvider = QueueProvider(okSentence(sourceText))
        val disabledConsumed = mutableListOf<String>()
        val disabled = rewriter(disabledProvider, disabledConsumed).rewrite(jobs, prefs, today)
        assertTrue(disabled.outcomes.all { it is RewriteOutcome.Skipped && it.reason == RewriteOutcome.Reason.DISABLED })
        assertTrue(disabledProvider.requests.isEmpty())
        assertTrue(disabledConsumed.isEmpty())

        prefs.enabled = true
        val noKeyProvider = QueueProvider(okSentence(sourceText))
        val noKeyConsumed = mutableListOf<String>()
        val noKey = SentenceRewriter(
            noKeyProvider, prefs.aiModel, KeySource { null }, matcher,
        ) { noKeyConsumed += it }.rewrite(jobs, prefs, today)
        assertTrue(noKey.outcomes.all { it is RewriteOutcome.Skipped && it.reason == RewriteOutcome.Reason.NO_KEY })
        assertTrue(noKeyProvider.requests.isEmpty())
        assertTrue(noKeyConsumed.isEmpty())

        prefs.keyRejected = true
        val rejectedProvider = QueueProvider(okSentence(sourceText))
        val rejectedConsumed = mutableListOf<String>()
        val rejected = rewriter(rejectedProvider, rejectedConsumed).rewrite(jobs, prefs, today)
        assertTrue(rejected.outcomes.all { it is RewriteOutcome.Skipped && it.reason == RewriteOutcome.Reason.KEY_REJECTED })
        assertTrue(rejectedProvider.requests.isEmpty())
        assertTrue(rejectedConsumed.isEmpty())
        prefs.keyRejected = false

        assertEquals(0, prefs.aiWordsSentToday(todayEpochDay))
    }

    @Test
    fun `a cap of one accepts the first job and skips the rest without consuming them`() = runBlocking {
        prefs.cap = 1
        val provider = QueueProvider(okSentence(sourceText))
        val consumed = mutableListOf<String>()

        val run = rewriter(provider, consumed).rewrite(
            listOf(job(source = sourceA), job(source = sourceB)), prefs, today,
        )

        assertTrue(run.outcomes[0] is RewriteOutcome.Accepted)
        val skipped = run.outcomes[1] as RewriteOutcome.Skipped
        assertEquals(RewriteOutcome.Reason.CAP_REACHED, skipped.reason)
        assertEquals("b19c4d2e8f605a37", skipped.job.source.id)
        assertEquals(1, provider.requests.size)
        // Job 2's source stays unconsumed: the next run's budget can still rewrite it.
        assertEquals(listOf("5e7a1f0c9b2d4e6a"), consumed)
        assertEquals(1, prefs.aiWordsSentToday(todayEpochDay))
    }

    @Test
    fun `an exhausted cap mid-job skips that job without consuming it`() = runBlocking {
        prefs.cap = 2
        val provider = QueueProvider(
            AiResult.Ok("no json here", AiUsage(10, 20), 5L),
            okSentence("Der Vertrag gilt der Kaktus fremd blau schnell."),
        )
        val consumed = mutableListOf<String>()

        val run = rewriter(provider, consumed).rewrite(listOf(job()), prefs, today)

        val skipped = run.outcomes.single() as RewriteOutcome.Skipped
        assertEquals(RewriteOutcome.Reason.CAP_REACHED, skipped.reason)
        assertEquals(2, provider.requests.size)
        assertTrue(consumed.isEmpty())
        assertEquals(2, prefs.aiWordsSentToday(todayEpochDay))
    }

    @Test
    fun `an auth error fails the job, stops the remaining jobs and flags the key`() = runBlocking {
        val provider = QueueProvider(AiResult.Error(AiResult.ErrorKind.AUTH, "401"))
        val consumed = mutableListOf<String>()

        val run = rewriter(provider, consumed).rewrite(
            listOf(job(source = sourceA), job(source = sourceB)), prefs, today,
        )

        assertTrue(prefs.aiKeyRejected)
        assertFalse(prefs.aiBillingBlocked)
        val failed = run.outcomes[0] as RewriteOutcome.Failed
        assertEquals(AiResult.ErrorKind.AUTH, failed.kind)
        val stopped = run.outcomes[1] as RewriteOutcome.Skipped
        assertEquals(RewriteOutcome.Reason.STOPPED, stopped.reason)
        assertEquals("b19c4d2e8f605a37", stopped.job.source.id)
        assertEquals(1, provider.requests.size)
        assertTrue(consumed.isEmpty())
        // The failed call was still billed at send time.
        assertEquals(1, prefs.aiWordsSentToday(todayEpochDay))
    }

    @Test
    fun `a billing error flags the billing state without touching the key`() = runBlocking {
        val provider = QueueProvider(AiResult.Error(AiResult.ErrorKind.BILLING, "out of credits"))
        val consumed = mutableListOf<String>()

        val run = rewriter(provider, consumed).rewrite(listOf(job()), prefs, today)

        assertTrue(prefs.aiBillingBlocked)
        assertFalse(prefs.aiKeyRejected)
        assertEquals(AiResult.ErrorKind.BILLING, (run.outcomes.single() as RewriteOutcome.Failed).kind)
        assertTrue(consumed.isEmpty())
    }
}
