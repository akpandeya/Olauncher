package app.olauncher.data.store

import kotlin.math.ceil

/**
 * Builds the flattened chat request for Ask-AI threads (docs/design/ask-ai-chat.md
 * §Threads, docs/design/ai-model-decision.md "Chat transport (#699)" verdict A):
 * instructions + grounding block + active summary + transcript as labelled lines
 * in ONE `prompt` string, sent through the existing `AiProvider.complete` with
 * `AiRequest(jsonMode = false)` — chat is prose, so no `response_format` is sent
 * and the `AiProvider` interface is unchanged.
 *
 * Request scope (design §Threads):
 * - the newest [GROUNDING_CAP] groundings are re-injected per request; older ones
 *   stay in the store but the model is not told about them;
 * - only the newest [AiChatSummary] is active and replaces the turns it covers in
 *   the request — the full transcript always stays in the store;
 * - failed turns are excluded: they were persisted before the request fired
 *   (design §Reliability) but never reached the model, so they never re-enter it.
 *
 * The tutor instructions are ported verbatim from the #699 transport benchmark's
 * `AiChatBenchPrompt` (single source, so production cannot drift from what the
 * benchmark measured); [SUBJECT_INSTRUCTION] is appended ONLY when a fresh thread
 * has both a word and a rule candidate.
 *
 * It also parses the first reply's `SUBJECT:` line and carries the token-budget
 * math: the 10k compaction trigger and the 7k post-compaction projection.
 */
object AiChatRequestBuilder {

    /** Tutor instructions, ported verbatim from the #699 transport benchmark. */
    val INSTRUCTIONS = """
        You are a patient German tutor inside a language-learning app. The learner asks about German grammar or vocabulary, often about a specific mistake they made or a saved word. Answer in English about German: quote German words and sentences as needed, but explain in English. Keep the answer short (2-5 sentences), concrete and directly about the question. Use earlier turns when the question builds on them. Work only from what this conversation shows you; never invent saved words, rules or earlier answers that are not in it.
    """.trimIndent()

    /**
     * Appended only when a fresh thread has both a word and a rule candidate
     * (design §Threads: "When a fresh thread could be word or rule").
     */
    val SUBJECT_INSTRUCTION = """
        This thread could be about either the saved word or the grammar rule shown in the facts. Begin your reply with exactly one first line — "SUBJECT: word" if the question is mainly about the saved word, or "SUBJECT: rule" if it is mainly about the grammar rule — then answer normally after it.
    """.trimIndent()

    /**
     * The newest N groundings re-injected per request (design §Threads: "Groundings
     * are bounded too" — bounded request, unbounded store).
     */
    const val GROUNDING_CAP = 3

    /**
     * Cost threshold that starts automatic compaction (design §Threads). A named
     * threshold, not a model limit: BYOK models vary and the provider carries no
     * context-size metadata, and every turn re-bills the history it resends.
     */
    const val COMPACTION_TRIGGER_TOKENS = 10_000

    /**
     * Post-compaction headroom target (design §Threads: the projected next request
     * — summary + remaining turns + groundings — fits under 7k, so compaction does
     * not fire again immediately).
     */
    const val POST_COMPACTION_TARGET_TOKENS = 7_000

    /**
     * Documented heuristic behind [estimateTokens]: roughly 4 characters per token
     * for the English/German prose this prompt carries. Deliberately simple — the
     * provider reports real usage per reply; this estimate only decides WHEN to
     * compact, never whether a send goes out (design §Threads: a send is never
     * blocked).
     */
    const val CHARS_PER_TOKEN = 4.0

    /**
     * Documented heuristic for the compaction projection: a summary is assumed to
     * take this fraction of the tokens of the turns it covers. A real summary is
     * usually shorter, so this over-estimates the projection — the safe direction,
     * because it makes compaction summarise a little more than strictly needed.
     */
    const val SUMMARY_COMPRESSION_RATIO = 0.5

    /**
     * Instruction block for the compaction summarise call ([buildSummarisePrompt]).
     * Distinct from [INSTRUCTIONS] so a caller (and the tests' fake provider) can
     * tell a summarise request from a chat request by the prompt alone.
     */
    val SUMMARISE_INSTRUCTIONS = """
        Compress the tutor conversation turns below into a short summary for future context. Keep every concrete fact: the German words and their meanings, the grammar rules discussed, the mistakes and their corrections, and any decision the learner made. Write compact prose — no greetings, no advice, nothing that is not in the turns.
    """.trimIndent()

    /**
     * Output-token budget for one compaction summarise call: a summary must be far
     * shorter than the turns it replaces; a reply that hits the cap is a poor
     * summary but still usable, so it is kept rather than rejected.
     */
    const val SUMMARY_MAX_TOKENS = 2048

    /** Sentinel for [build]/[projectedRequestTokens]: no turns are omitted. */
    const val NO_OMITTED_TURNS = -1

    // ---- Prompt assembly ----------------------------------------------------

    /**
     * Assembles one flattened prompt string ending on the new question.
     * [newQuestion] is the pending draft (not yet persisted); pass null to render
     * the request as it would look without a pending question (used by the budget
     * projection, where the next question is not known yet). [subjectProposal]
     * appends [SUBJECT_INSTRUCTION] — only for a fresh thread with both a word and
     * a rule candidate. [omitTurnsUpTo] is the truncation fallback (design §Threads:
     * compaction could not run): delivered turns whose reply message index is at or
     * below it leave the request, which the UI marks "Earlier turns omitted".
     */
    fun build(
        thread: AiChatThread,
        newQuestion: String?,
        subjectProposal: Boolean = false,
        omitTurnsUpTo: Int = NO_OMITTED_TURNS,
    ): String = buildString {
        append(INSTRUCTIONS)
        if (subjectProposal) {
            append('\n').append(SUBJECT_INSTRUCTION).append('\n')
        }
        append(FACTS_HEADER)
        factsLines(thread).forEach { append(it) }
        activeSummary(thread)?.let { summary ->
            append(SUMMARY_HEADER).append(summary.text).append('\n')
        }
        append(TRANSCRIPT_HEADER)
        transcriptLines(thread, omitTurnsUpTo).forEach { append(it) }
        if (newQuestion != null) {
            append(QUESTION_PREFIX).append(newQuestion).append('\n')
            append(TAIL)
        }
    }

    /** Labelled grounding lines for the newest [GROUNDING_CAP] groundings, oldest first. */
    private fun factsLines(thread: AiChatThread): List<String> =
        thread.groundings.takeLast(GROUNDING_CAP).map { grounding ->
            groundingLine(grounding.payload) + "\n"
        }

    private fun groundingLine(payload: AiChatGroundingPayload): String = when (payload) {
        is AiChatGroundingPayload.Miss -> buildString {
            append("- Miss: prompt \"${payload.prompt}\" | learner picked: ${payload.picked} | correct: ${payload.answer}")
            if (payload.ruleId != null) append(" | rule: ${payload.ruleId}")
            if (payload.chips.isNotEmpty()) append(" | chips: ${payload.chips.joinToString(", ")}")
        }
        is AiChatGroundingPayload.Word ->
            "- Saved word: \"${payload.text}\" means \"${payload.meaning}\"; saved context: \"${payload.context}\""
        is AiChatGroundingPayload.Practice ->
            "- Practice item: prompt \"${payload.prompt}\" | answer: ${payload.answer}"
    }

    /** The newest summary is the active one; older entries stay for the audit trail. */
    private fun activeSummary(thread: AiChatThread): AiChatSummary? = thread.summaries.lastOrNull()

    /**
     * The transcript the model sees: delivered turns (a user message that was
     * answered by a non-failed assistant reply) after the active summary's
     * coverage, as labelled lines with trailing newlines. A pair is shown only
     * when BOTH of its messages sit after the coverage, so a boundary can never
     * split a turn. Turns whose reply index is at or below [omitTurnsUpTo] are
     * left out (the truncation fallback).
     */
    private fun transcriptLines(thread: AiChatThread, omitTurnsUpTo: Int = NO_OMITTED_TURNS): List<String> =
        transcriptLinesFrom(
            thread,
            firstIndex = (activeSummary(thread)?.coversUpTo ?: -1) + 1,
            omitTurnsUpTo = omitTurnsUpTo,
        )

    private fun transcriptLinesFrom(
        thread: AiChatThread,
        firstIndex: Int,
        omitTurnsUpTo: Int = NO_OMITTED_TURNS,
        upTo: Int = Int.MAX_VALUE,
    ): List<String> {
        val lines = mutableListOf<String>()
        var turn = 0
        for (i in thread.messages.indices) {
            val user = thread.messages[i]
            val reply = thread.messages.getOrNull(i + 1) ?: break
            if (i < firstIndex || user.role != AiChatMessage.Role.USER || user.failed) continue
            if (reply.role != AiChatMessage.Role.ASSISTANT || reply.failed) continue
            if (i + 1 > upTo) break
            if (i + 1 <= omitTurnsUpTo) continue
            turn++
            lines.add("[Turn $turn | ${user.role.name.lowercase()}] ${user.text}\n")
            lines.add("[Turn $turn | ${reply.role.name.lowercase()}] ${reply.text}\n")
        }
        return lines
    }

    // ---- First-reply SUBJECT parse ------------------------------------------

    /**
     * The first reply's subject and the text to render. [subject] is `word` or
     * `rule` (design §Threads); `topic` never comes from a parse, and `rule` is
     * the fallback value when the line is malformed or missing — then
     * [renderText] is the reply unchanged (it still renders normally).
     */
    data class FirstReply(
        val subject: AiChatSubject.Kind,
        val renderText: String,
    )

    /** Emphasis markers stripped around the SUBJECT line (design §Threads). */
    private val EMPHASIS_MARKERS = Regex("[*_`]")

    /**
     * Parses a first reply's SUBJECT line: the FIRST non-empty line, trimmed,
     * emphasis markers (`*`, `_`, backticks) stripped, compared case-insensitively
     * against exactly `SUBJECT: word` or `SUBJECT: rule`. The space after the
     * colon is REQUIRED — `subject:rule` fails. Malformed or missing falls back to
     * the rule subject with the reply unchanged.
     *
     * When the line parses, it is removed before render: [FirstReply.renderText]
     * drops everything up to and including the SUBJECT line.
     */
    fun parseFirstReply(reply: String): FirstReply {
        val firstLine = reply.lineSequence().firstOrNull { it.isNotBlank() }
            ?: return FirstReply(AiChatSubject.Kind.RULE, reply)
        val cleaned = firstLine.replace(EMPHASIS_MARKERS, "").trim()
        val subject = when {
            cleaned.equals("SUBJECT: word", ignoreCase = true) -> AiChatSubject.Kind.WORD
            cleaned.equals("SUBJECT: rule", ignoreCase = true) -> AiChatSubject.Kind.RULE
            else -> return FirstReply(AiChatSubject.Kind.RULE, reply)
        }
        // Drop everything up to and including the SUBJECT line; the rest renders.
        val afterSubject = reply.substring(reply.indexOf(firstLine) + firstLine.length)
        return FirstReply(subject, afterSubject.trimStart())
    }

    // ---- Token budget (design §Threads: "Context budget: 10k input tokens") --

    /**
     * Heuristic token estimate for [text]: [CHARS_PER_TOKEN] characters per token,
     * rounded up. An empty string costs nothing.
     */
    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        return ceil(text.length / CHARS_PER_TOKEN).toInt()
    }

    /**
     * The projected input-token cost of the NEXT request for [thread]: the prompt
     * [build] would assemble for [newQuestion], estimated block by block. The
     * projection skips the literal newlines [build] appends between blocks, so it
     * can land ~1 token below the built prompt — close enough for a compaction
     * decision, which only compares the estimate against the trigger and target.
     * [omitTurnsUpTo] matches [build].
     */
    fun projectedRequestTokens(
        thread: AiChatThread,
        newQuestion: String?,
        subjectProposal: Boolean = false,
        omitTurnsUpTo: Int = NO_OMITTED_TURNS,
    ): Int {
        var tokens = estimateTokens(INSTRUCTIONS)
        if (subjectProposal) tokens += estimateTokens(SUBJECT_INSTRUCTION)
        tokens += estimateTokens(FACTS_HEADER)
        factsLines(thread).forEach { tokens += estimateTokens(it) }
        activeSummary(thread)?.let { summary ->
            tokens += estimateTokens(SUMMARY_HEADER) + estimateTokens(summary.text)
        }
        tokens += estimateTokens(TRANSCRIPT_HEADER)
        transcriptLines(thread, omitTurnsUpTo).forEach { tokens += estimateTokens(it) }
        if (newQuestion != null) {
            tokens += estimateTokens(QUESTION_PREFIX + newQuestion + "\n")
            tokens += estimateTokens(TAIL)
        }
        return tokens
    }

    /**
     * Whether the next send for [thread] would cross [COMPACTION_TRIGGER_TOKENS] —
     * the send-time check that queues compaction. The send itself is never blocked
     * (design §Threads); compaction runs after the reply lands.
     */
    fun exceedsTrigger(
        thread: AiChatThread,
        newQuestion: String?,
        subjectProposal: Boolean = false,
    ): Boolean {
        return projectedRequestTokens(thread, newQuestion, subjectProposal) > COMPACTION_TRIGGER_TOKENS
    }

    /**
     * The projected cost of the next request AFTER a compaction whose new summary
     * covers all messages up to [coversUpTo]: instructions + groundings + the
     * estimated summary ([SUMMARY_COMPRESSION_RATIO] of the covered originals'
     * tokens, re-summarised from the STORED originals — never from a previous
     * summary) + the delivered turns after [coversUpTo]. No pending question is
     * assumed: the projection runs after a reply lands, before the next send.
     */
    fun projectedAfterCompaction(
        thread: AiChatThread,
        coversUpTo: Int,
        subjectProposal: Boolean = false,
    ): Int {
        var tokens = estimateTokens(INSTRUCTIONS)
        if (subjectProposal) tokens += estimateTokens(SUBJECT_INSTRUCTION)
        tokens += estimateTokens(FACTS_HEADER)
        factsLines(thread).forEach { tokens += estimateTokens(it) }
        val coveredTokens = summarisedOriginals(thread, coversUpTo).sumOf { estimateTokens(it.text) }
        tokens += estimateTokens(SUMMARY_HEADER) + ceil(coveredTokens * SUMMARY_COMPRESSION_RATIO).toInt()
        tokens += estimateTokens(TRANSCRIPT_HEADER)
        transcriptLinesFrom(thread, firstIndex = coversUpTo + 1).forEach { tokens += estimateTokens(it) }
        return tokens
    }

    /**
     * Plans a compaction for [thread]: the message index the new summary should
     * cover up to (the oldest delivered turns, up to the point where the projected
     * next request fits under [POST_COMPACTION_TARGET_TOKENS]), or null when no
     * compaction is needed.
     *
     * - null when the projected next request does not cross [COMPACTION_TRIGGER_TOKENS];
     * - otherwise the SMALLEST delivered-turn end index (≥ the active summary's
     *   coverage, since the new summary re-summarises the stored originals the old
     *   one covered) whose projection fits under the target;
     * - null again when even full coverage cannot fit (for example groundings
     *   alone over the target) — the caller then falls back to truncation.
     */
    fun compactionCoversUpTo(thread: AiChatThread, subjectProposal: Boolean = false): Int? {
        if (projectedRequestTokens(thread, newQuestion = null, subjectProposal) <= COMPACTION_TRIGGER_TOKENS) {
            return null
        }
        val start = activeSummary(thread)?.coversUpTo ?: -1
        val coverableEnds = deliveredTurnEndIndexes(thread).filter { it > start }
        for (end in coverableEnds) {
            if (projectedAfterCompaction(thread, end, subjectProposal) <= POST_COMPACTION_TARGET_TOKENS) {
                return end
            }
        }
        return null
    }

    /** The delivered messages (both roles) the summary covering [coversUpTo] summarises. */
    private fun summarisedOriginals(thread: AiChatThread, coversUpTo: Int): List<AiChatMessage> {
        val originals = mutableListOf<AiChatMessage>()
        for (end in deliveredTurnEndIndexes(thread)) {
            if (end > coversUpTo) break
            originals.add(thread.messages[end - 1])
            originals.add(thread.messages[end])
        }
        return originals
    }

    /**
     * The prompt for one compaction summarise call (design §Threads): the stored
     * ORIGINALS of every delivered turn up to [coversUpTo] — never a previous
     * summary ("Re-summarise from the stored originals, never from the previous
     * summary, so drift is capped at one pass") — as labelled transcript lines.
     */
    fun buildSummarisePrompt(thread: AiChatThread, coversUpTo: Int): String = buildString {
        append(SUMMARISE_INSTRUCTIONS)
        append(SUMMARISE_HEADER)
        transcriptLinesFrom(thread, firstIndex = 0, upTo = coversUpTo).forEach { append(it) }
    }

    /**
     * The truncation fallback planner (design §Threads: "Fallback when compaction
     * cannot run: the oldest prose turns leave the request and 'Earlier turns
     * omitted' appears in the visible history"): the smallest delivered-turn end
     * index whose omission leaves the projected request under
     * [POST_COMPACTION_TARGET_TOKENS], or the LAST delivered-turn end when even
     * that cannot fit (the send still goes out — it is never blocked). null when
     * the projection already fits and nothing needs to be dropped.
     */
    fun truncationOmitUpTo(thread: AiChatThread): Int? {
        if (projectedRequestTokens(thread, newQuestion = null) <= POST_COMPACTION_TARGET_TOKENS) {
            return null
        }
        val ends = deliveredTurnEndIndexes(thread)
        for (end in ends) {
            if (projectedRequestTokens(thread, newQuestion = null, omitTurnsUpTo = end) <= POST_COMPACTION_TARGET_TOKENS) {
                return end
            }
        }
        return ends.lastOrNull()
    }

    /** Index in [AiChatThread.messages] of the reply that completes each delivered turn. */
    private fun deliveredTurnEndIndexes(thread: AiChatThread): List<Int> {
        val ends = mutableListOf<Int>()
        for (i in thread.messages.indices) {
            val user = thread.messages[i]
            val reply = thread.messages.getOrNull(i + 1) ?: break
            if (user.role == AiChatMessage.Role.USER &&
                !user.failed &&
                reply.role == AiChatMessage.Role.ASSISTANT &&
                !reply.failed
            ) {
                ends.add(i + 1)
            }
        }
        return ends
    }

    private const val FACTS_HEADER = "\nFacts you may use:\n"
    private const val SUMMARY_HEADER = "\nSummary of earlier turns:\n"
    private const val SUMMARISE_HEADER = "\nTurns to summarise:\n"
    private const val TRANSCRIPT_HEADER = "\nTranscript:\n"
    private const val QUESTION_PREFIX = "[New question | user] "
    private const val TAIL = "\nAnswer as the assistant: reply to the new question only.\n"
}
