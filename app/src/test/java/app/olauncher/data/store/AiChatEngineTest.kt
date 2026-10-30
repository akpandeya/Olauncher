package app.olauncher.data.store

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AiChatEngine] — the send/fail/retry/cancel matrix, first-turn
 * cancel, compaction from the stored originals and the truncation fallback
 * (docs/design/ask-ai-chat.md §Chat screen Cancel, §Reliability, §Threads).
 * Hermetic: a scripted fake provider and an in-memory store, no network.
 */
private fun okReply(text: String) =
    AiResult.Ok(text, AiUsage(inputTokens = 111, outputTokens = 22), latencyMs = 5L)

class AiChatEngineTest {

    // --- hermetic fakes -----------------------------------------------------------

    /** In-memory [AiChatPersistence]; [failWrites] simulates a broken storage write. */
    private class FakeStore : AiChatPersistence {
        val threads = LinkedHashMap<String, AiChatThread>()
        var failWrites = false

        override fun thread(id: String): AiChatThread? = threads[id]

        override fun upsert(thread: AiChatThread): AiChatWrite {
            if (failWrites) {
                return AiChatWrite.Failed(AiChatStoreException("test: write refused"))
            }
            threads[thread.id] = thread
            return AiChatWrite.Ok
        }

        override fun delete(threadId: String): AiChatWrite {
            if (failWrites) {
                return AiChatWrite.Failed(AiChatStoreException("test: delete refused"))
            }
            threads.remove(threadId)
            return AiChatWrite.Ok
        }
    }

    /**
     * Scripted [AiProvider]. [script] answers every request; [gate] suspends the
     * call so a test can cancel or send again while a request is in flight (a
     * test signals the send with its own deferred in [onRequest]).
     */
    private class FakeProvider : AiProvider {
        val requests = mutableListOf<AiRequest>()

        /** Chat (non-summarise) requests seen so far, so replies can be numbered. */
        var chatRequests = 0
            private set

        /** Pads every chat reply, so a delivered turn pair is big for the budget tests. */
        var replyPadding = 0

        var script: ((FakeProvider, AiRequest) -> AiResult)? = null
        var onRequest: (AiRequest) -> Unit = {}
        var gate: CompletableDeferred<Unit>? = null

        /**
         * Hold for summarise requests only (falls back to [gate]), so a test can
         * keep compaction in flight while chat requests proceed or the session
         * switches threads.
         */
        var summariseGate: CompletableDeferred<Unit>? = null

        override suspend fun complete(request: AiRequest): AiResult {
            requests.add(request)
            if (!request.prompt.startsWith(AiChatRequestBuilder.SUMMARISE_INSTRUCTIONS)) chatRequests++
            onRequest(request)
            if (request.prompt.startsWith(AiChatRequestBuilder.SUMMARISE_INSTRUCTIONS)) {
                (summariseGate ?: gate)?.await()
            } else {
                gate?.await()
            }
            return script?.invoke(this, request) ?: error("unscripted request")
        }

        fun reply(): AiResult = okReply("Antwort $chatRequests" + "y".repeat(replyPadding))
    }

    // --- fixtures --------------------------------------------------------------------

    private val subject = AiChatSubject(AiChatSubject.Kind.RULE, "prep-two-way")

    private var now = 1_700_000_000_000L

    private fun fail(kind: AiResult.ErrorKind = AiResult.ErrorKind.RATE_LIMIT) =
        AiResult.Error(kind, "test error")

    /**
     * A ~1.3k-token question; four of these with padded replies push the
     * projected next request past the 10k trigger.
     */
    private fun bigQuestion(index: Int) = "Frage $index " + "x".repeat(5196)

    /** Padding that makes a chat reply's line ~1.3k tokens. */
    private val bigReplyPadding = 5196

    private fun grounding(id: String) = AiChatGrounding(
        id = id,
        payload = AiChatGroundingPayload.Miss(
            prompt = "Ich lege das Buch auf ___ Tisch.",
            picked = "dem",
            answer = "den",
            ruleId = "prep-two-way",
            chips = listOf("movement"),
        ),
    )

    private fun isSummarise(request: AiRequest) =
        request.prompt.startsWith(AiChatRequestBuilder.SUMMARISE_INSTRUCTIONS)

    private fun engine(store: FakeStore, provider: FakeProvider) =
        AiChatEngine(store, provider) { now++ }

    private fun FakeProvider.lastChatRequest(): AiRequest = requests.last { !isSummarise(it) }

    // --- send: persist before firing ----------------------------------------------------

    @Test
    fun `send persists the user turn before the request fires`() = runBlocking {
        val store = FakeStore()
        val provider = FakeProvider()
        val chat = engine(store, provider)
        chat.open(AiChatOpen.Fresh(subject))
        var storeDuringRequest: AiChatThread? = null
        provider.onRequest = { storeDuringRequest = store.threads.values.single() }
        provider.script = { p, _ -> p.reply() }

        chat.send("Warum accusativ?")

        // The turn was already on disk — unfailed — when the request went out.
        assertNotNull(storeDuringRequest)
        val turnAtRequest = storeDuringRequest!!.messages.single()
        assertEquals("Warum accusativ?", turnAtRequest.text)
        assertEquals(AiChatMessage.Role.USER, turnAtRequest.role)
        assertFalse(turnAtRequest.failed)
        // The question is the [New question] line, rendered exactly once.
        val prompt = provider.requests.single().prompt
        assertEquals(1, prompt.split("Warum accusativ?").size - 1)
        assertTrue(prompt.contains("[New question | user] Warum accusativ?"))
        assertFalse(prompt.contains("[Turn 1 | user] Warum accusativ?"))
    }

    @Test
    fun `a second send while one is in flight is refused and stores nothing`() = runBlocking {
        val store = FakeStore()
        val provider = FakeProvider()
        val chat = engine(store, provider)
        chat.open(AiChatOpen.Fresh(subject))
        val inFlight = CompletableDeferred<Unit>()
        provider.onRequest = { inFlight.complete(Unit) }
        provider.gate = CompletableDeferred<Unit>()
        provider.script = { p, _ -> p.reply() }
        val job = launch { chat.send("Erste Frage") }
        inFlight.await()

        val refused = chat.send("Zweite Frage")

        assertTrue(refused is AiChatSend.Refused)
        assertEquals(AiChatSend.Reason.BUSY, (refused as AiChatSend.Refused).reason)
        assertEquals(1, store.threads.values.single().messages.size)
        assertEquals("Erste Frage", chat.state.value.pendingQuestion)
        provider.gate!!.complete(Unit)
        job.join()
    }

    @Test
    fun `a send with no sendable session is refused`() = runBlocking {
        val store = FakeStore()
        val provider = FakeProvider()
        val chat = engine(store, provider)
        // A resumed id that is not in the store cannot send (no subject to create from).
        chat.open(AiChatOpen.Resumed("missing"))

        val refused = chat.send("Frage")

        assertEquals(AiChatSend.Refused(AiChatSend.Reason.NO_THREAD), refused)
        assertTrue(provider.requests.isEmpty())
        assertTrue(store.threads.isEmpty())
    }

    // --- failure ------------------------------------------------------------------------

    @Test
    fun `failed send marks the turn failed and excludes it from context and counts`() = runBlocking {
        val store = FakeStore()
        val provider = FakeProvider()
        val chat = engine(store, provider)
        chat.open(AiChatOpen.Fresh(subject))
        provider.script = { _, _ -> fail() }

        chat.send("Warum accusativ?")

        val state = chat.state.value
        assertNull(state.pendingQuestion)
        val turn = state.thread!!.messages.single()
        assertTrue(turn.failed)
        assertEquals(0, AiChatThreads.deliveredTurns(state.thread!!))
        assertTrue(store.threads.values.single().messages.single().failed)

        // A later send's request excludes the failed question from the context.
        provider.script = { p, _ -> p.reply() }
        chat.send("Zweite Frage")
        val prompt = provider.lastChatRequest().prompt
        assertFalse(prompt.contains("Warum accusativ?"))
        assertTrue(prompt.contains("[New question | user] Zweite Frage"))
        assertEquals(1, AiChatThreads.deliveredTurns(chat.state.value.thread!!))
    }

    // --- retry ---------------------------------------------------------------------------

    @Test
    fun `retry re-fires the newest failed turn and success makes it count`() = runBlocking {
        val store = FakeStore()
        val provider = FakeProvider()
        val chat = engine(store, provider)
        chat.open(AiChatOpen.Fresh(subject))
        provider.script = { _, _ -> fail() }
        chat.send("Erste Frage")
        provider.script = { p, _ -> p.reply() }

        val outcome = chat.retry()

        assertTrue(outcome is AiChatRetry.Fired)
        assertFalse((outcome as AiChatRetry.Fired).askAgain)
        assertTrue(provider.lastChatRequest().prompt.contains("[New question | user] Erste Frage"))
        val thread = chat.state.value.thread!!
        assertEquals(2, thread.messages.size)
        assertFalse(thread.messages[0].failed)
        assertEquals("Antwort 2", thread.messages[1].text)
        assertEquals(1, AiChatThreads.deliveredTurns(thread))
        assertEquals(AiUsage(111, 22), chat.state.value.lastUsage)
        assertEquals(listOf(thread), store.threads.values.toList())
    }

    @Test
    fun `an older failed turn does not retry in place but asks again with the quoted question`() =
        runBlocking {
            val store = FakeStore()
            val provider = FakeProvider()
            val chat = engine(store, provider)
            chat.open(AiChatOpen.Fresh(subject))
            provider.script = { _, _ -> fail() }
            chat.send("Erste Frage")
            provider.script = { p, _ -> p.reply() }
            chat.send("Zweite Frage")
            provider.script = { p, _ -> p.reply() }

            val outcome = chat.retry()

            // The newest failed turn sits before a delivered turn: it cannot
            // retry in place — a new turn quoting the question goes out instead.
            assertTrue(outcome is AiChatRetry.Fired)
            assertTrue((outcome as AiChatRetry.Fired).askAgain)
            val thread = chat.state.value.thread!!
            assertEquals(5, thread.messages.size)
            assertEquals("Erste Frage", thread.messages[3].text)
            assertTrue(thread.messages[0].failed)
            assertTrue(provider.lastChatRequest().prompt.contains("[New question | user] Erste Frage"))
            assertEquals(2, AiChatThreads.deliveredTurns(thread))
        }

    @Test
    fun `retry with nothing failed or while a request is in flight does nothing`() = runBlocking {
        val store = FakeStore()
        val provider = FakeProvider()
        val chat = engine(store, provider)
        chat.open(AiChatOpen.Fresh(subject))
        provider.script = { p, _ -> p.reply() }

        assertEquals(AiChatRetry.NotRetryable, chat.retry())
        chat.send("Erste Frage")
        assertEquals(AiChatRetry.NotRetryable, chat.retry())

        // In flight: also not retryable.
        val inFlight = CompletableDeferred<Unit>()
        provider.onRequest = { inFlight.complete(Unit) }
        provider.gate = CompletableDeferred<Unit>()
        provider.script = { _, _ -> fail() }
        val job = launch { chat.send("Zweite Frage") }
        inFlight.await()
        assertEquals(AiChatRetry.NotRetryable, chat.retry())
        provider.gate!!.complete(Unit)
        job.join()
    }

    // --- cancel ---------------------------------------------------------------------------

    @Test
    fun `cancel mid-thread removes the stored turn, restores the draft and discards the late reply`() =
        runBlocking {
            val store = FakeStore()
            val provider = FakeProvider()
            val chat = engine(store, provider)
            chat.open(AiChatOpen.Fresh(subject))
            provider.script = { p, _ -> p.reply() }
            chat.send("Erste Frage")

            val inFlight = CompletableDeferred<Unit>()
            provider.onRequest = { inFlight.complete(Unit) }
            provider.gate = CompletableDeferred<Unit>()
            val job = launch { chat.send("Zweite Frage") }
            inFlight.await()

            val outcome = chat.cancel()

            assertTrue(outcome is AiChatCancel.Cancelled)
            assertEquals("Zweite Frage", (outcome as AiChatCancel.Cancelled).restoredDraft)
            assertFalse(outcome.threadDeleted)
            assertNull(chat.state.value.pendingQuestion)
            // Nothing of the cancelled turn remains — in memory or on disk.
            val stored = store.threads.values.single()
            assertEquals(listOf("Erste Frage", "Antwort 1"), stored.messages.map { it.text })
            // The late reply is discarded.
            provider.gate!!.complete(Unit)
            job.join()
            assertEquals(2, chat.state.value.thread!!.messages.size)
            assertEquals(2, stored.messages.size)
        }

    @Test
    fun `first-turn cancel deletes the thread and the session is fresh again`() = runBlocking {
        val store = FakeStore()
        val provider = FakeProvider()
        val chat = engine(store, provider)
        chat.open(AiChatOpen.Fresh(subject, groundings = listOf(grounding("g1"))))
        val inFlight = CompletableDeferred<Unit>()
        provider.onRequest = { inFlight.complete(Unit) }
        provider.gate = CompletableDeferred<Unit>()
        provider.script = { p, _ -> p.reply() }
        val job = launch { chat.send("Erste Frage") }
        inFlight.await()

        val outcome = chat.cancel()

        assertTrue(outcome is AiChatCancel.Cancelled)
        assertTrue((outcome as AiChatCancel.Cancelled).threadDeleted)
        assertEquals("Erste Frage", outcome.restoredDraft)
        assertTrue(store.threads.isEmpty())
        assertNull(chat.state.value.thread)
        // The late reply of the cancelled request is discarded.
        provider.gate!!.complete(Unit)
        job.join()
        // The fresh session is restored with its grounding: a new send re-creates
        // the thread with it.
        provider.gate = null
        chat.send("Erneut gefragt")
        val recreated = store.threads.values.single()
        assertEquals(listOf(grounding("g1")), recreated.groundings)
        assertEquals(2, recreated.messages.size)
    }

    // --- storage failure ---------------------------------------------------------------------

    @Test
    fun `storage-write failure surfaces the inline error and never loses the message`() = runBlocking {
        val store = FakeStore()
        val provider = FakeProvider()
        val chat = engine(store, provider)
        chat.open(AiChatOpen.Fresh(subject))
        store.failWrites = true
        provider.script = { p, _ -> p.reply() }

        chat.send("Wichtige Frage")

        val state = chat.state.value
        assertTrue(state.storeError)
        assertNotNull(state.thread)
        assertEquals(2, state.thread!!.messages.size)
        assertTrue(store.threads.isEmpty())

        // A later successful write persists the whole working copy.
        store.failWrites = false
        chat.send("Zweite Frage")
        assertFalse(chat.state.value.storeError)
        assertEquals(4, store.threads.values.single().messages.size)
        assertEquals(2, AiChatThreads.deliveredTurns(chat.state.value.thread!!))
    }

    // --- compaction ---------------------------------------------------------------------------

    @Test
    fun `after the answer lands a projected request over 10k is compacted from the stored originals`() =
        runBlocking {
            val store = FakeStore()
            val provider = FakeProvider()
            provider.replyPadding = bigReplyPadding
            val chat = engine(store, provider)
            chat.open(AiChatOpen.Fresh(subject))
            provider.script = { p, request ->
                if (isSummarise(request)) okReply("Kompakt: Praepositionen und Kasus geklaert") else p.reply()
            }
            for (i in 1..4) chat.send(bigQuestion(i))

            // The summarise call went out after the fourth answer, carrying the
            // STORED ORIGINALS of the turns the plan covers (the oldest three;
            // the fourth still projects under the 7k target as prose).
            val summariseRequests = provider.requests.filter { isSummarise(it) }
            assertEquals(1, summariseRequests.size)
            val summarisePrompt = summariseRequests.single().prompt
            assertTrue(summarisePrompt.startsWith(AiChatRequestBuilder.SUMMARISE_INSTRUCTIONS))
            for (i in 1..3) assertTrue(summarisePrompt.contains("Frage $i "))
            assertFalse(summarisePrompt.contains("Frage 4 "))
            assertFalse(summarisePrompt.contains("Summary of earlier turns:"))
            // The summary is stored with the coverage of the last summarised message.
            val thread = chat.state.value.thread!!
            assertEquals(1, thread.summaries.size)
            assertEquals("Kompakt: Praepositionen und Kasus geklaert", thread.summaries.single().text)
            assertEquals(5, thread.summaries.single().coversUpTo)
            // Counts never regress: summarised turns still count.
            assertEquals(4, AiChatThreads.deliveredTurns(thread))
            assertNull(chat.state.value.omittedUpTo)
            // The next request carries the summary instead of the covered turns.
            chat.send("Kurze Nachfrage")
            val nextPrompt = provider.lastChatRequest().prompt
            assertTrue(nextPrompt.contains("Summary of earlier turns:\nKompakt: Praepositionen und Kasus geklaert\n"))
            assertFalse(nextPrompt.contains("Frage 1 "))
            assertTrue(nextPrompt.contains("[Turn 1 | user] Frage 4 "))
            assertTrue(nextPrompt.contains("[New question | user] Kurze Nachfrage"))
        }

    @Test
    fun `compaction failure is not an error - the oldest prose is omitted and the store stays lossless`() =
        runBlocking {
            val store = FakeStore()
            val provider = FakeProvider()
            provider.replyPadding = bigReplyPadding
            val chat = engine(store, provider)
            chat.open(AiChatOpen.Fresh(subject))
            provider.script = { p, request ->
                if (isSummarise(request)) fail(AiResult.ErrorKind.NETWORK) else p.reply()
            }
            for (i in 1..4) chat.send(bigQuestion(i))

            // The summarise call failed: no summary, but the "Earlier turns
            // omitted" marker marks the prose that left the request. The send was
            // never blocked and the reply still landed.
            val thread = chat.state.value.thread!!
            assertTrue(thread.summaries.isEmpty())
            assertEquals(8, thread.messages.size)
            assertEquals(4, AiChatThreads.deliveredTurns(thread))
            assertEquals(3, chat.state.value.omittedUpTo)

            // The next request leaves the omitted turns out but keeps the rest,
            // and the store still holds every turn (lossless for the user).
            chat.send("Kurze Nachfrage")
            val nextPrompt = provider.lastChatRequest().prompt
            assertFalse(nextPrompt.contains("Frage 1 "))
            assertFalse(nextPrompt.contains("Frage 2 "))
            assertTrue(nextPrompt.contains("Frage 3 "))
            assertTrue(nextPrompt.contains("Frage 4 "))
            assertTrue(nextPrompt.contains("[New question | user] Kurze Nachfrage"))
            assertEquals(10, store.threads.values.single().messages.size)
            assertEquals(5, AiChatThreads.deliveredTurns(chat.state.value.thread!!))
            // The recomputed omission still fits under the 7k target (the new
            // turn is a short question, so the same cut still suffices).
            assertEquals(3, chat.state.value.omittedUpTo)
        }

    @Test
    fun `a later successful compaction replaces the omitted marker`() = runBlocking {
        val store = FakeStore()
        val provider = FakeProvider()
        provider.replyPadding = bigReplyPadding
        val chat = engine(store, provider)
        chat.open(AiChatOpen.Fresh(subject))
        var summariseFails = true
        provider.script = { p, request ->
            when {
                isSummarise(request) && summariseFails -> fail(AiResult.ErrorKind.NETWORK)
                isSummarise(request) -> okReply("Kompakte Zusammenfassung")
                else -> p.reply()
            }
        }
        for (i in 1..4) chat.send(bigQuestion(i))
        assertEquals(3, chat.state.value.omittedUpTo)

        summariseFails = false
        chat.send("Kurze Nachfrage")

        assertNull(chat.state.value.omittedUpTo)
        val thread = chat.state.value.thread!!
        assertEquals("Kompakte Zusammenfassung", thread.summaries.last().text)
        assertEquals(1, thread.summaries.size)
        assertEquals(7, thread.summaries.last().coversUpTo)
        assertEquals(5, AiChatThreads.deliveredTurns(thread))
    }

    @Test
    fun `a summary of thread A is never applied after the session switched to thread B`() =
        runBlocking {
            val store = FakeStore()
            val provider = FakeProvider()
            provider.replyPadding = bigReplyPadding
            val chat = engine(store, provider)
            chat.open(AiChatOpen.Fresh(subject))
            provider.script = { p, request ->
                if (isSummarise(request)) okReply("Zusammenfassung von A") else p.reply()
            }
            for (i in 1..3) chat.send(bigQuestion(i))

            // Hold the summarise call while the session switches to another thread.
            val summariseHeld = CompletableDeferred<Unit>()
            provider.onRequest = { request -> if (isSummarise(request)) summariseHeld.complete(Unit) }
            provider.summariseGate = CompletableDeferred()
            val job = launch { chat.send(bigQuestion(4)) }
            summariseHeld.await()

            val otherId = "other-thread"
            store.threads[otherId] = AiChatThread(
                id = otherId,
                subject = subject,
                createdAt = now,
                updatedAt = now,
                messages = listOf(AiChatMessage(role = AiChatMessage.Role.USER, text = "Andere Frage", ts = now)),
            )
            chat.open(AiChatOpen.Resumed(otherId))
            provider.summariseGate!!.complete(Unit)
            job.join()

            // Thread B is untouched: no summary of A's turns was spliced onto it,
            // and its early turns stay in every future request.
            val resumed = chat.state.value.thread!!
            assertEquals(otherId, resumed.id)
            assertTrue(resumed.summaries.isEmpty())
            assertNull(chat.state.value.omittedUpTo)
            assertTrue(store.threads[otherId]!!.summaries.isEmpty())
            // Thread A's summary was dropped as well — a coverage index into A
            // must not outlive the session that computed it.
            val original = store.threads.values.single { it.id != otherId }
            assertTrue(original.summaries.isEmpty())
        }

    @Test
    fun `a send in flight during compaction keeps its waiting state when the summary lands`() =
        runBlocking {
            val store = FakeStore()
            val provider = FakeProvider()
            provider.replyPadding = bigReplyPadding
            val chat = engine(store, provider)
            chat.open(AiChatOpen.Fresh(subject))
            provider.script = { p, request ->
                if (isSummarise(request)) okReply("Kompakt: alles geklaert") else p.reply()
            }
            for (i in 1..3) chat.send(bigQuestion(i))

            val summariseHeld = CompletableDeferred<Unit>()
            provider.onRequest = { request -> if (isSummarise(request)) summariseHeld.complete(Unit) }
            provider.summariseGate = CompletableDeferred()
            val compactionJob = launch { chat.send(bigQuestion(4)) }
            summariseHeld.await()

            // A send is never blocked by compaction: it fires while the
            // summarise call is still in flight.
            provider.gate = CompletableDeferred()
            val sendJob = launch { chat.send("Neue Frage") }

            // The summary lands while the send waits: the send's waiting state
            // survives (Send must not re-enable mid-flight).
            provider.summariseGate!!.complete(Unit)
            compactionJob.join()
            assertEquals("Neue Frage", chat.state.value.pendingQuestion)
            assertEquals(1, chat.state.value.thread!!.summaries.size)
            // The new turn was persisted before its request; the summary's
            // coverage index still points into the unchanged prefix.
            assertEquals(9, chat.state.value.thread!!.messages.size)
            assertEquals(9, store.threads.values.single().messages.size)

            // Then the send's own reply applies normally.
            provider.gate!!.complete(Unit)
            sendJob.join()
            assertNull(chat.state.value.pendingQuestion)
            assertEquals(10, chat.state.value.thread!!.messages.size)
        }

    // --- resumed session ------------------------------------------------------------------------

    @Test
    fun `resumed thread loads from the store with its latest usage`() = runBlocking {
        val store = FakeStore()
        val provider = FakeProvider()
        val chat = engine(store, provider)
        chat.open(AiChatOpen.Fresh(subject))
        provider.script = { p, _ -> p.reply() }
        chat.send("Erste Frage")
        val storedId = store.threads.keys.single()
        val resumed = AiChatEngine(store, provider) { now++ }

        resumed.open(AiChatOpen.Resumed(storedId))

        val state = resumed.state.value
        assertEquals(store.threads.values.single(), state.thread)
        assertEquals(AiUsage(111, 22), state.lastUsage)
        assertNull(state.pendingQuestion)
        assertFalse(state.storeError)
    }
}
