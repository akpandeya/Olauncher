package app.olauncher.ui.askai

import app.olauncher.data.store.AiChatEngine
import app.olauncher.data.store.AiChatOpen
import app.olauncher.data.store.AiChatPersistence
import app.olauncher.data.store.AiChatSubject
import app.olauncher.data.store.AiChatThread
import app.olauncher.data.store.AiChatWrite
import app.olauncher.data.store.AiProvider
import app.olauncher.data.store.AiRequest
import app.olauncher.data.store.AiResult
import app.olauncher.data.store.AiUsage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AiChatViewModel] — the ViewModel surface the chat screen
 * collects (docs/design/ask-ai-chat.md §Chat screen). Hermetic: a scripted
 * provider and an in-memory store, no network; [Dispatchers.setMain] backs
 * [androidx.lifecycle.viewModelScope].
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AiChatViewModelTest {

    // --- hermetic fakes -----------------------------------------------------------

    private class MemoryStore : AiChatPersistence {
        val threads = LinkedHashMap<String, AiChatThread>()

        override fun thread(id: String): AiChatThread? = threads[id]

        override fun upsert(thread: AiChatThread): AiChatWrite {
            threads[thread.id] = thread
            return AiChatWrite.Ok
        }

        override fun delete(threadId: String): AiChatWrite {
            threads.remove(threadId)
            return AiChatWrite.Ok
        }
    }

    /** Replies every request with one fixed text; [gate] holds a request in flight. */
    private class ScriptedProvider(private val reply: String) : AiProvider {
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun complete(request: AiRequest): AiResult {
            gate?.await()
            return AiResult.Ok(reply, AiUsage(inputTokens = 3, outputTokens = 4), latencyMs = 1L)
        }
    }

    private val subject = AiChatSubject(AiChatSubject.Kind.RULE, "prep-two-way")

    // --- cancel ----------------------------------------------------------------------

    @Test
    fun `cancel restores the draft through the exposed flow - nothing pending emits nothing`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val provider = ScriptedProvider("Antwort")
                val vm = AiChatViewModel(AiChatEngine(MemoryStore(), provider))
                val drafts = mutableListOf<String>()
                val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                    vm.restoredDrafts.collect { drafts.add(it) }
                }

                // Nothing in flight: cancelling emits nothing.
                vm.cancel()
                advanceUntilIdle()
                assertTrue(drafts.isEmpty())

                // A first send in flight, then cancel: the draft reaches the
                // composer (design §Chat screen: "Cancel … restores the draft").
                provider.gate = CompletableDeferred()
                vm.open(AiChatOpen.Fresh(subject))
                vm.send("Erste Frage")
                advanceUntilIdle()
                vm.cancel()
                advanceUntilIdle()
                assertEquals(listOf("Erste Frage"), drafts)
                assertNull(vm.state.value.thread)

                // The late reply of the cancelled request is discarded.
                provider.gate!!.complete(Unit)
                advanceUntilIdle()
                collector.cancel()
                assertNull(vm.state.value.pendingQuestion)
                assertTrue(vm.state.value.thread == null)
            } finally {
                Dispatchers.resetMain()
            }
        }
}
