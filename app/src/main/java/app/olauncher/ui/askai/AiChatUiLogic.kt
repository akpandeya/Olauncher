package app.olauncher.ui.askai

import app.olauncher.data.store.AiChatGrounding
import app.olauncher.data.store.AiChatGroundingPayload
import app.olauncher.data.store.AiChatMessage
import app.olauncher.data.store.AiChatState
import app.olauncher.data.store.AiChatSubject
import app.olauncher.data.store.AiChatThread
import app.olauncher.data.store.AiChatThreads
import app.olauncher.data.store.AiResult
import app.olauncher.data.store.AiUsage
import app.olauncher.data.store.DrillHelpContent
import app.olauncher.data.store.DrillTables
import app.olauncher.data.store.DrillType
import java.util.Locale

/**
 * The Context-free rendering decisions of the Ask-AI chat screen
 * (docs/design/ask-ai-chat.md §Chat screen + §Reliability): which rows the
 * message list shows, where the compaction markers sit, what the header reads,
 * and how the usage line formats. No [android.content.Context] — the fragment
 * maps these values to views and string resources, so the decisions stay
 * JVM-testable.
 */
object AiChatUiLogic {

    /** One rendered line of the message list, top to bottom. */
    sealed class Row {

        /** A user tile on the right; [failed] renders it dimmed ("Not sent · …" below). */
        data class UserTile(val text: String, val failed: Boolean) : Row()

        /**
         * The inline error line under a failed turn (§Reliability error table):
         * [kind] picks the message; [retryable] renders the Retry button, an
         * older failed turn renders Ask again instead — [question] is the text
         * that turn asked, which the Ask-again tap sends as a new turn.
         */
        data class ErrorLine(
            val kind: AiResult.ErrorKind?,
            val retryable: Boolean,
            val question: String,
        ) : Row()

        /** An assistant panel on the left. */
        data class AssistantPanel(val text: String) : Row()

        /**
         * The compaction marker (§Threads: "one pattern, two meanings"):
         * [summarised] reads "Earlier turns summarised · see what it kept" and
         * expands to [summaryText] plus the covered originals; the omission
         * variant reads "Earlier turns omitted" and expands to the originals.
         */
        data class CompactionMarker(
            val summarised: Boolean,
            val summaryText: String?,
            val originals: List<OriginalTurn>,
        ) : Row()

        /** The in-flight placeholder with its Cancel button. */
        object Thinking : Row()

        /** The inline "Couldn't save this conversation" storage-failure line. */
        object StoreError : Row()
    }

    /** One delivered question/answer pair, read-only under a compaction marker. */
    data class OriginalTurn(val question: String, val answer: String)

    /**
     * The rows for [state]'s thread: transcript messages in order, the compaction
     * marker where its turns were, the Thinking placeholder while a request is in
     * flight, and the storage-failure line last.
     */
    fun rows(state: AiChatState): List<Row> {
        val thread = state.thread ?: return pendingOnlyRows(state)
        val summary = thread.summaries.lastOrNull()
        val rows = mutableListOf<Row>()
        val failedIndexes = thread.messages
            .withIndex()
            .filter { it.value.role == AiChatMessage.Role.USER && it.value.failed }
            .map { it.index }
        for (i in thread.messages.indices) {
            val message = thread.messages[i]
            when {
                message.role == AiChatMessage.Role.USER && message.failed -> {
                    rows.add(Row.UserTile(text = message.text, failed = true))
                    rows.add(
                        Row.ErrorLine(
                            kind = message.errorKind,
                            // Only the newest failed turn retries (design §Reliability);
                            // an older one converts to Ask again.
                            retryable = i == failedIndexes.last(),
                            question = message.text,
                        )
                    )
                }
                message.role == AiChatMessage.Role.USER -> rows.add(Row.UserTile(message.text, failed = false))
                else -> rows.add(Row.AssistantPanel(message.text))
            }
            // The marker sits where its turns were: after the last message the
            // active summary covers, or after the truncation fallback's omission
            // boundary. The two sit at different indexes; when a later compaction
            // made them coincide, the summary marker wins.
            when {
                summary != null && i == summary.coversUpTo ->
                    rows.add(
                        Row.CompactionMarker(
                            summarised = true,
                            summaryText = summary.text,
                            originals = originalTurns(thread, summary.coversUpTo),
                        )
                    )
                state.omittedUpTo != null && i == state.omittedUpTo ->
                    rows.add(
                        Row.CompactionMarker(
                            summarised = false,
                            summaryText = null,
                            originals = originalTurns(thread, state.omittedUpTo),
                        )
                    )
            }
        }
        if (state.pendingQuestion != null) rows.add(Row.Thinking)
        if (state.storeError) rows.add(Row.StoreError)
        return rows
    }

    /** A fresh session has no thread yet: only the waiting/error lines can show. */
    private fun pendingOnlyRows(state: AiChatState): List<Row> {
        val rows = mutableListOf<Row>()
        if (state.pendingQuestion != null) rows.add(Row.Thinking)
        if (state.storeError) rows.add(Row.StoreError)
        return rows
    }

    /** The delivered turns a summary covering [coversUpTo] replaced, read-only. */
    private fun originalTurns(thread: AiChatThread, coversUpTo: Int): List<OriginalTurn> {
        val originals = mutableListOf<OriginalTurn>()
        var i = 0
        while (i < thread.messages.size) {
            val user = thread.messages[i]
            val reply = thread.messages.getOrNull(i + 1) ?: break
            val delivered = user.role == AiChatMessage.Role.USER && !user.failed &&
                reply.role == AiChatMessage.Role.ASSISTANT && !reply.failed
            if (delivered && i + 1 <= coversUpTo) {
                originals.add(OriginalTurn(question = user.text, answer = reply.text))
                i += 2
            } else {
                // A failed or unanswered turn occupies its slot alone; the next
                // pair starts one message later.
                i += 1
            }
        }
        return originals
    }

    // ---- header ---------------------------------------------------------------

    /** What the header's subject line reads (§Chat screen header bullet). */
    sealed class Header {
        /** A provisional both-candidates thread until the first reply names the subject. */
        object NewConversation : Header()

        /** The saved word as written, from its grounding (the ref is normalised). */
        data class Word(val text: String) : Header()

        /** The rule's short title, falling back to the rule id when unauthored. */
        data class Rule(val title: String) : Header()

        /** The drill type's label as a string resource. */
        data class Topic(@androidx.annotation.StringRes val labelRes: Int) : Header()
    }

    /**
     * The header for [state]'s open session. [provisional] is the screen's
     * both-candidates state: true until the first reply files the thread. While
     * a fresh single-subject chat has no thread yet (§Chat screen: thread
     * existence starts at first send), [openingSubject] and
     * [openingGroundings] — the screen's open arguments — render the opening
     * subject's header instead of "New conversation", which is reserved for
     * provisional both-candidates threads.
     */
    fun header(
        state: AiChatState,
        provisional: Boolean,
        openingSubject: AiChatSubject? = null,
        openingGroundings: List<app.olauncher.data.store.AiChatGrounding> = emptyList(),
    ): Header {
        if (provisional) return Header.NewConversation
        val thread = state.thread
        if (thread != null) return header(thread.subject, thread.groundings)
        return openingSubject?.let { header(it, openingGroundings) } ?: Header.NewConversation
    }

    private fun header(subject: AiChatSubject, groundings: List<app.olauncher.data.store.AiChatGrounding>): Header =
        when (subject.kind) {
            AiChatSubject.Kind.WORD -> Header.Word(
                // The citation form as written lives on the word grounding; the
                // stored subject ref is the normalised citation text.
                groundings.lastOrNull { it.payload is AiChatGroundingPayload.Word }
                    ?.let { (it.payload as AiChatGroundingPayload.Word).text }
                    ?: subject.ref
            )
            AiChatSubject.Kind.RULE -> Header.Rule(DrillHelpContent.rule(subject.ref)?.title ?: subject.ref)
            AiChatSubject.Kind.TOPIC -> DrillType.fromId(subject.ref)
                ?.let { Header.Topic(DrillTables.labelRes(it)) }
                ?: Header.Rule(subject.ref)
        }

    // ---- usage line -----------------------------------------------------------

    /**
     * The token count the usage line reports: the latest request's input tokens
     * (§Threads: "the line always reports the latest request"), or null while no
     * reply has reported usage.
     */
    fun usageTokens(state: AiChatState): Int? {
        return state.lastUsage?.inputTokens
    }

    /** The delivered-turn count the usage line and the announcement read. */
    fun deliveredTurns(state: AiChatState): Int {
        val thread = state.thread ?: return 0
        return AiChatThreads.deliveredTurns(thread)
    }

    /** "1.8k" for thousands, "823" below; dot decimal regardless of locale. */
    fun formatTokenCount(tokens: Int): String {
        return if (tokens >= 1000) {
            String.format(Locale.US, "%.1fk", tokens / 1000.0)
        } else {
            tokens.toString()
        }
    }
}
