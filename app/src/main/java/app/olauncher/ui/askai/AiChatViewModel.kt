package app.olauncher.ui.askai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.olauncher.data.store.AiChatEngine
import app.olauncher.data.store.AiChatGrounding
import app.olauncher.data.store.AiChatOpen
import app.olauncher.data.store.AiChatState
import app.olauncher.data.store.AiChatThreads
import kotlinx.coroutines.flow.StateFlow
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

    /** Opens a fresh chat for [open]'s subject, or resumes a stored thread. */
    fun open(open: AiChatOpen) {
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

    /** Cancels the in-flight request: stored turn deleted, draft restored. */
    fun cancel() {
        viewModelScope.launch {
            engine.cancel()
        }
    }

    /** Delivered turns of the open thread — the chip, usage line and sheet count. */
    fun deliveredTurns(): Int {
        return engine.state.value.thread?.let { AiChatThreads.deliveredTurns(it) } ?: 0
    }
}
