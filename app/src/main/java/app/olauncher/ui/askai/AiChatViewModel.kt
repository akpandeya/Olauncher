package app.olauncher.ui.askai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.olauncher.data.store.AiChatCancel
import app.olauncher.data.store.AiChatEngine
import app.olauncher.data.store.AiChatGrounding
import app.olauncher.data.store.AiChatOpen
import app.olauncher.data.store.AiChatState
import app.olauncher.data.store.AiChatThreads
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * ViewModel of the Ask-AI chat screen (docs/design/ask-ai-chat.md §Chat screen).
 * Thin shell over the Context-free [AiChatEngine]: every request runs in
 * [viewModelScope] (design §Reliability — rotation cancels nothing; process death
 * leaves the persisted "Not sent · Retry" turn for the next open) and the screen
 * collects [state]. The engine owns the behaviour — persist-before-fire, failure
 * marking, retry/ask-again, cancel, and post-answer compaction — so it stays
 * JVM-testable with a fake provider and an in-memory store.
 */
class AiChatViewModel(private val engine: AiChatEngine) : ViewModel() {

    /** The engine's session state; collected by the chat screen. */
    val state: StateFlow<AiChatState> = engine.state

    private val _restoredDrafts = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * Drafts restored by Cancel (design §Chat screen: "Cancel … restores the
     * draft"): the screen collects these and puts each text back into the
     * composer. Nothing is emitted when nothing was in flight.
     */
    val restoredDrafts: SharedFlow<String> = _restoredDrafts.asSharedFlow()

    /**
     * Whether [open] has already run for this session. The ViewModel outlives the
     * fragment across rotation, so a re-created screen must not open again — a
     * second engine.open() bumps the generation and would discard an in-flight
     * reply or wipe a resumed transcript (design §Reliability).
     */
    var opened = false
        private set

    /** Opens a fresh chat for [open]'s subject, or resumes a stored thread. */
    fun open(open: AiChatOpen) {
        opened = true
        engine.open(open)
    }

    /** Sends [question]; one request at a time — the engine refuses while one runs. */
    fun send(question: String, grounding: AiChatGrounding? = null) {
        if (question.isBlank()) return
        viewModelScope.launch {
            engine.send(question, grounding)
        }
    }

    /** Retries the newest failed turn, in place or as an ask-again turn. */
    fun retry() {
        viewModelScope.launch {
            engine.retry()
        }
    }

    /**
     * Cancels the in-flight request: stored turn deleted, draft restored. The
     * restored draft is published on [restoredDrafts] — cancel runs off the
     * caller's hands, so the outcome cannot be a return value.
     */
    fun cancel() {
        viewModelScope.launch {
            val outcome = engine.cancel()
            if (outcome is AiChatCancel.Cancelled) {
                _restoredDrafts.tryEmit(outcome.restoredDraft)
            }
        }
    }

    /** Delivered turns of the open thread — the chip, usage line and sheet count. */
    fun deliveredTurns(): Int {
        return engine.state.value.thread?.let { AiChatThreads.deliveredTurns(it) } ?: 0
    }
}
