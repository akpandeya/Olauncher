package app.olauncher.ui.words

import android.content.Context
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.data.store.AiChatGrounding
import app.olauncher.data.store.AiChatGroundingPayload
import app.olauncher.data.store.AiChatRouter
import app.olauncher.data.store.AiChatStore
import app.olauncher.data.store.AiChatSubject
import app.olauncher.data.store.AiChatThreads
import app.olauncher.data.store.AiProviderConfig
import app.olauncher.data.store.DrillHelpContent
import app.olauncher.data.store.DrillTables
import app.olauncher.data.store.DrillType
import app.olauncher.data.store.KeyVault
import app.olauncher.data.store.Word
import app.olauncher.ui.askai.AskAiEntries

/**
 * The one wiring point of the Ask-AI entry points (docs/design/ask-ai-chat.md
 * §Entry points): visibility ([environment]), the per-subject turn counts, the
 * groundings each entry carries, and the navigation into the chat — tap opens
 * the subject's latest thread when one exists, else a fresh one. All decisions
 * stay in [AskAiEntries] (Context-free, JVM tested); this object only reads
 * prefs/vault/store and navigates.
 */
object AskAiEntryPoints {

    /**
     * One render pass's entry state: whether any entry shows, and the latest
     * thread's delivered turns per subject. Fragments load it once per render —
     * a key-vault read per row would decrypt the keystore over and over — and
     * ask it per row.
     */
    class Environment private constructor(
        /** False hides every entry, never greys one. */
        val visible: Boolean,
        private val turnsBySubject: Map<AiChatSubject, Int>,
        /** The subjects that already have a thread (the router's existingSubjects). */
        val subjects: Set<AiChatSubject>,
    ) {
        /** The subject's latest-thread delivered turns; 0 when none (fresh "Ask AI"). */
        fun turns(subject: AiChatSubject): Int = turnsBySubject[subject] ?: 0

        companion object {
            /** The hidden environment: everything invisible, no store read at all. */
            fun hidden() = Environment(false, emptyMap(), emptySet())

            /** Loads the visible environment: the store's threads, counted per subject. */
            fun visible(context: Context): Environment {
                val bySubject = AiChatStore(context).threads().groupBy { it.subject }
                val turns = bySubject.mapValues { (_, threads) ->
                    // "Chat · n" counts the subject's LATEST thread (design §Entry
                    // points); older threads are reachable from the thread sheet.
                    AiChatThreads.deliveredTurns(threads.maxByOrNull { it.updatedAt } ?: return@mapValues 0)
                }
                return Environment(true, turns, bySubject.keys)
            }
        }
    }

    /** Loads the render pass's environment; hidden when the gate or config blocks. */
    fun environment(context: Context): Environment {
        val prefs = Prefs(context.applicationContext)
        if (!AskAiEntries.visible(
                enabled = prefs.aiPracticeEnabled,
                hasKey = !KeyVault(context).read().isNullOrBlank(),
                model = prefs.aiModel,
                configError = AiProviderConfig(prefs.aiProvider, prefs.aiBaseUrl, prefs.aiModel).validate(),
            )
        ) {
            return Environment.hidden()
        }
        return Environment.visible(context)
    }

    /**
     * Opens the chat for [subject] from an entry tap: the subject's latest
     * thread when one exists, else a fresh chat carrying [groundings];
     * [proposal] is set only for a fresh both-candidates thread.
     */
    fun openChat(
        fragment: Fragment,
        @IdRes currentDestinationId: Int,
        subject: AiChatSubject,
        proposal: AiChatSubject? = null,
        groundings: List<AiChatGrounding> = emptyList(),
    ) {
        val latest = AiChatThreads.latestThread(
            AiChatStore(fragment.requireContext()).threadsFor(subject),
            subject,
        )
        open(fragment, currentDestinationId, subject, latest?.id, proposal, groundings)
    }

    /**
     * Opens the chat from the thread sheet: [threadId] resumes the tapped
     * thread; null is "New conversation", a fresh chat carrying [groundings].
     * [currentDestinationId] is the caller's own destination, so a second tap
     * delivered before the first navigation lands is ignored — the same guard
     * every other opener in the app uses.
     */
    fun open(
        fragment: Fragment,
        @IdRes currentDestinationId: Int,
        subject: AiChatSubject,
        threadId: String?,
        proposal: AiChatSubject? = null,
        groundings: List<AiChatGrounding> = emptyList(),
    ) {
        val navController = fragment.findNavController()
        if (navController.currentDestination?.id != currentDestinationId) return
        val args = if (threadId != null) {
            AiChatFragment.bundle(subject, threadId = threadId)
        } else {
            AiChatFragment.bundle(subject, proposal = proposal, groundings = groundings)
        }
        navController.navigate(R.id.action_global_aiChatFragment, args)
    }

    /** The saved word whose citation text is [subject]'s ref, or null. */
    fun wordForSubject(savedWords: Collection<Word>, subject: AiChatSubject): Word? {
        if (subject.kind != AiChatSubject.Kind.WORD) return null
        return savedWords.firstOrNull { AiChatRouter.normalizeCitation(it.text) == subject.ref }
    }

    /** The word grounding of a saved word: citation text, meaning, saved context. */
    fun wordGrounding(word: Word): AiChatGrounding = AiChatGrounding(
        AiChatGrounding.newId(),
        AiChatGroundingPayload.Word(text = word.text, meaning = word.meaning, context = word.context),
    )

    /** The practice grounding: the prompt and its revealed answer. */
    fun practiceGrounding(prompt: String, answer: String, wordId: Long): AiChatGrounding = AiChatGrounding(
        AiChatGrounding.newId(),
        AiChatGroundingPayload.Practice(prompt = prompt, answer = answer, wordId = wordId),
    )

    /** The miss grounding: prompt, wrong pick, correct answer, rule id and chips. */
    fun missGrounding(prompt: String, picked: String?, answer: String, ruleId: String?): AiChatGrounding = AiChatGrounding(
        AiChatGrounding.newId(),
        AiChatGroundingPayload.Miss(
            prompt = prompt,
            picked = picked.orEmpty(),
            answer = answer,
            ruleId = ruleId,
            chips = ruleChips(ruleId),
        ),
    )

    /** The rule chips of a miss: the help table's pattern headers, as the help card shows them. */
    private fun ruleChips(ruleId: String?): List<String> {
        val table = DrillHelpContent.rule(ruleId)?.table ?: return emptyList()
        return table.headers.drop(1).filter { it.isNotBlank() }
    }

    /** The sheet's title for [subject]: the word as written, the rule's title, the topic's label. */
    fun subjectTitle(context: Context, savedWords: Collection<Word>, subject: AiChatSubject): String {
        return when (subject.kind) {
            AiChatSubject.Kind.WORD ->
                wordForSubject(savedWords, subject)?.text ?: subject.ref
            AiChatSubject.Kind.RULE ->
                DrillHelpContent.rule(subject.ref)?.title ?: subject.ref
            AiChatSubject.Kind.TOPIC ->
                DrillType.fromId(subject.ref)?.let { context.getString(DrillTables.labelRes(it)) } ?: subject.ref
        }
    }

    /** The chip's label: "Ask AI" with no delivered turns, "Chat · n" with n. */
    fun chipText(context: Context, turns: Int): String = when (val chip = AskAiEntries.chip(turns)) {
        is AskAiEntries.Chip.Fresh -> context.getString(R.string.ask_ai_chip)
        is AskAiEntries.Chip.Resumed -> context.getString(R.string.ask_ai_chip_turns, chip.turns)
    }

    /** The chip's announcement ("Chat, 4 turns"), design §Accessibility. */
    fun chipAnnouncement(context: Context, turns: Int): String = when (val chip = AskAiEntries.chip(turns)) {
        is AskAiEntries.Chip.Fresh -> context.getString(R.string.ask_ai_chip)
        is AskAiEntries.Chip.Resumed -> context.getString(R.string.ask_ai_chip_a11y, chip.turns)
    }

    /**
     * The word-card row's label: "Ask AI about this word", appending "n turns"
     * when the subject's latest thread has history (one shape per surface —
     * there is no "Ask AI · n" form).
     */
    fun wordRowText(context: Context, turns: Int): String {
        val label = context.getString(R.string.ask_ai_word_row)
        val suffixTurns = AskAiEntries.wordRowTurns(turns)
        return if (suffixTurns > 0) {
            context.getString(R.string.ask_ai_word_row_turns, label, suffixTurns)
        } else {
            label
        }
    }
}
