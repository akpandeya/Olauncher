package app.olauncher.ui.askai

import androidx.annotation.StringRes
import app.olauncher.R
import app.olauncher.data.store.AiChatMessage
import app.olauncher.data.store.AiPracticeGate
import app.olauncher.data.store.AiChatThread
import app.olauncher.data.store.AiChatThreads

/**
 * Context-free decisions for the Ask-AI entry points (docs/design/ask-ai-chat.md
 * §Entry points): whether an entry shows at all, what the chip and the word-card
 * row say, and what the thread sheet lists. The fragments bind these decisions to
 * views; nothing here touches a Context, prefs or the store.
 *
 * One shape per surface: a chip reads "Ask AI" or "Chat · n"; a full-width row
 * reads "Ask AI about this word" and appends "n turns". n is always delivered
 * turns ([AiChatThreads.deliveredTurns]) — summarised turns included, failed and
 * in-flight questions not.
 */
object AskAiEntries {

    /**
     * An entry is visible iff the practice gate is open (`AiPracticeGate.canRun`)
     * and the provider config validates (`AiProviderConfig.validate()` returns
     * null). A blocked gate or invalid config means hidden, never greyed.
     */
    fun visible(enabled: Boolean, hasKey: Boolean, model: String, configError: String?): Boolean {
        return AiPracticeGate.canRun(enabled, hasKey, model) && configError == null
    }

    /** The summary chip's label decision: "Ask AI" with no turns, "Chat · n" with n. */
    sealed class Chip {

        /** "Ask AI" — no delivered turns on the subject yet. */
        object Fresh : Chip()

        /** "Chat · n" — the subject's latest thread has n delivered turns. */
        data class Resumed(val turns: Int) : Chip()
    }

    fun chip(deliveredTurns: Int): Chip = if (deliveredTurns > 0) Chip.Resumed(deliveredTurns) else Chip.Fresh

    /** The string resource a chip binds: [R.string.ask_ai_chip] or the "Chat · n" form. */
    @StringRes
    fun chipLabelRes(chip: Chip): Int = when (chip) {
        is Chip.Fresh -> R.string.ask_ai_chip
        is Chip.Resumed -> R.string.ask_ai_chip_turns
    }

    /** The chip's count argument; 0 when the chip is the fresh "Ask AI" form. */
    fun chipTurns(chip: Chip): Int = when (chip) {
        is Chip.Fresh -> 0
        is Chip.Resumed -> chip.turns
    }

    /**
     * The word-card row's turn suffix: "n turns" when history exists, no suffix
     * otherwise ("Ask AI about this word" stays alone on a first-ever entry).
     */
    fun wordRowTurns(deliveredTurns: Int): Int = if (deliveredTurns > 0) deliveredTurns else 0

    /**
     * One thread-sheet row: the thread's id, its first question as the snippet,
     * its last activity and its delivered-turn count. Newest first.
     */
    data class SheetRow(
        val threadId: String,
        val snippet: String,
        val updatedAt: Long,
        val turns: Int,
    )

    /**
     * The sheet's rows for one subject's threads, newest first (the newest is the
     * default the entry itself opens). The snippet is the thread's first user
     * question — a failed first question is still the first question asked, so it
     * is not filtered (design §Reliability: the "Not sent · Retry" tail reads
     * correctly on arrival).
     */
    fun sheetRows(threads: List<AiChatThread>): List<SheetRow> {
        return threads
            .sortedByDescending { it.updatedAt }
            .map { thread ->
                SheetRow(
                    threadId = thread.id,
                    snippet = thread.messages.firstOrNull { it.role == AiChatMessage.Role.USER }?.text.orEmpty(),
                    updatedAt = thread.updatedAt,
                    turns = AiChatThreads.deliveredTurns(thread),
                )
            }
    }

    /**
     * Whether the sheet title carries the conversation count: only with two or
     * more ("Two-way prepositions · 2 conversations"); one or none reads plain.
     */
    fun sheetTitleShowsCount(threadCount: Int): Boolean = threadCount > 1
}
