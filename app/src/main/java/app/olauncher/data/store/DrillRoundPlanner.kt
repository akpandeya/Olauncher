package app.olauncher.data.store

import java.util.Random

/**
 * Question formats on the recognition ladder (#639, docs/design/drills-ux.md):
 *
 * | Rung | Format        | Who gets it                                        |
 * |------|---------------|----------------------------------------------------|
 * | 1    | [FLASHCARD]   | early items (maturity 0 and 1), shown before their question |
 * | 2    | [TAP]         | first exposure — always the first question format   |
 * | 3    | [TRUE_FALSE], [ODD_ONE_OUT], [MATCH], [WORD_BANK], listen-and-tap | items answered right once |
 * | 4    | [TYPED]       | graduation: answered right twice, max 3 per round   |
 *
 * ODD_ONE_OUT, MATCH, WORD_BANK and listen-and-tap (TTS, #532) have no question
 * generation yet; the planner degrades those rung-3 assignments to [TAP] until
 * they are implemented (see [varietyFormat]).
 */
enum class DrillFormat {
    FLASHCARD,
    TAP,
    TRUE_FALSE,
    ODD_ONE_OUT,
    MATCH,
    WORD_BANK,
    TYPED,

    /** Unscored rule-recap card: the adaptive breather after consecutive misses (#drill-pacing). */
    RECAP,
}

/**
 * The question displayed for one planned step: the underlying [item], its ladder
 * [format], and the format-specific payload ([choices], a [statement] with its
 * truth value for true-false).
 */
data class DrillQuizQuestion(
    val item: DrillItem,
    val format: DrillFormat,
    val choices: List<String> = emptyList(),
    /** Filled statement for [DrillFormat.TRUE_FALSE]; null otherwise. */
    val statement: String? = null,
    /** Whether [statement] is the true one. Only meaningful for true-false. */
    val isTrue: Boolean = false,
) {
    val id: DrillItemId get() = item.id
    val prompt: String get() = item.prompt
    val answer: String get() = item.answer
    val hint: String? get() = item.hint
    val isFlashcard: Boolean get() = format == DrillFormat.FLASHCARD
    val isRecap: Boolean get() = format == DrillFormat.RECAP

    /** An unscored teaching step (flashcard or rule recap) that advances on Continue. */
    val isTeaching: Boolean get() = isFlashcard || isRecap
    val isTyped: Boolean get() = format == DrillFormat.TYPED

    /**
     * Defensive guard for a choice question that somehow lost its choices: it gets a
     * reveal-style presentation with an enabled Continue and is never graded, so the
     * screen can never dead-end with an empty grid and a hidden button.
     */
    val isReveal: Boolean get() = !isTeaching && !isTyped && choices.isEmpty()

    /** The choice a learner must pick to be right, or null when there are no choices. */
    val correctChoice: String?
        get() = when (format) {
            DrillFormat.TRUE_FALSE -> if (isTrue) TRUE_CHOICE else FALSE_CHOICE
            DrillFormat.TAP, DrillFormat.ODD_ONE_OUT -> answer
            else -> null
        }

    companion object {
        /** Neutral tokens the true-false format is graded on; the screen localises them. */
        const val TRUE_CHOICE = "True"
        const val FALSE_CHOICE = "False"
    }
}

/**
 * Context-free round construction for the redesigned drill round (#639):
 *
 * - 3 to 5 items per round ([MIN_ROUND_SIZE]..[MAX_ROUND_SIZE]); a type with fewer
 *   items contributes all of them.
 * - A mixed round interleaves the 2-3 sampled types' items.
 * - Per-item format from the recognition ladder, driven by the per-item maturity
 *   count (rounds answered right, see [DrillItemHistory]): first exposure is always
 *   tap, rung-3 variety after one right answer, typed only after two.
 * - At most [MAX_TYPED_PER_ROUND] typed and [MAX_FLASHCARDS_PER_ROUND] flashcard
 *   steps per round, so a round stays inside the ~12-interaction budget.
 * - AI practice items (#621, Epic 9 #561) join a mixed round as flashcard-only steps,
 *   capped at [MAX_AI_PER_ROUND] — their own cap, not [MAX_FLASHCARDS_PER_ROUND],
 *   which stays the cap for built-in flashcard teaching steps.
 * - No two consecutive questions share a format (as long as the assigned formats
 *   allow it).
 */
object DrillRoundPlanner {

    const val MIN_ROUND_SIZE = 3
    const val MAX_ROUND_SIZE = 5
    const val MAX_TYPED_PER_ROUND = 3
    const val MAX_FLASHCARDS_PER_ROUND = 1

    /**
     * The per-round cap for AI practice items (#621) in a mixed round: independent of
     * [MAX_FLASHCARDS_PER_ROUND], which caps the built-in flashcard teaching steps.
     */
    const val MAX_AI_PER_ROUND = 2

    /** An item becomes typeable once it has been answered correctly this many times. */
    const val TYPED_MATURITY = 2

    /**
     * Draws the round's item count: a random size between [MIN_ROUND_SIZE] and
     * [MAX_ROUND_SIZE] — capped at 5 so no round bombards the learner; the round
     * itself shrinks further on wrong answers (see [DrillQuiz]). Deterministic
     * under a seeded [random].
     */
    fun roundSize(random: Random = Random()): Int =
        MIN_ROUND_SIZE + random.nextInt(MAX_ROUND_SIZE - MIN_ROUND_SIZE + 1)

    /**
     * Plans one round from the candidate [pools] (items per drill type), using
     * [maturity] (item id -> rounds answered right) for the ladder. A round with no
     * graduated items falls back to tap-only by construction; it never stalls.
     * The [size] is capped at [MAX_ROUND_SIZE] — practise-misses rounds keep their
     * exact size via [planMisses]. [aiItems] are AI practice items (#621) that join
     * the round as flashcard-only steps, capped at [MAX_AI_PER_ROUND]; an empty list
     * (fresh install, no stored items) leaves the round exactly as it would be
     * without the parameter.
     */
    fun plan(
        pools: Map<DrillType, List<DrillItem>>,
        maturity: Map<DrillItemId, Int> = emptyMap(),
        random: Random = Random(),
        size: Int = roundSize(random),
        aiItems: List<DrillItem> = emptyList(),
    ): List<DrillQuizQuestion> =
        planRound(pools, maturity, random, size.coerceIn(1, MAX_ROUND_SIZE), allowFlashcard = true, aiItems)

    /**
     * Plans a practise-misses round: exactly the given items, no flashcard steps
     * (their first exposure is long past), ladder formats as usual. AI practice
     * items stay out — misses rounds are AI-free (#621).
     */
    fun planMisses(
        missed: List<DrillItem>,
        maturity: Map<DrillItemId, Int> = emptyMap(),
        random: Random = Random(),
    ): List<DrillQuizQuestion> =
        // The misses keep their own item ids; the placeholder type only balances sampling.
        planRound(
            mapOf(DrillType.PREPOSITIONS to missed),
            maturity,
            random,
            missed.size,
            allowFlashcard = false,
            aiItems = emptyList(),
        )

    private fun planRound(
        pools: Map<DrillType, List<DrillItem>>,
        maturity: Map<DrillItemId, Int>,
        random: Random,
        size: Int,
        allowFlashcard: Boolean,
        aiItems: List<DrillItem>,
    ): List<DrillQuizQuestion> {
        // AI practice items (#621): at most MAX_AI_PER_ROUND, drawn once per round, and
        // each rendered as a flashcard step. They take question slots, so the built-in
        // pool fills the rest of the round and the total stays at the planned size.
        val aiCards = aiItems.shuffled(random).take(MAX_AI_PER_ROUND)
        val builtInSize = (size - aiCards.size).coerceAtLeast(1)
        val sampled = sample(pools, random, builtInSize)
        if (sampled.isEmpty()) return emptyList()

        var typedCount = 0
        val planned = sampled.map { item ->
            val level = maturity[item.id] ?: 0
            val format = when {
                level >= TYPED_MATURITY && typedCount < MAX_TYPED_PER_ROUND -> {
                    typedCount++
                    DrillFormat.TYPED
                }
                level >= 1 -> varietyFormat(item)
                // First exposure is always tap — never typed before graduation.
                else -> DrillFormat.TAP
            }
            question(item, format, random)
        }

        // Without AI items the flashcard budget is the round cap as before; with them,
        // the AI cards' slots come out of it, so the whole round stays at [size].
        val flashcardCap = if (aiCards.isEmpty()) MAX_ROUND_SIZE else builtInSize
        val withFlashcard = addFlashcards(planned, maturity, random, allowFlashcard, flashcardCap)
        val withAi = addAiFlashcards(withFlashcard, aiCards, random)
        return orderWithoutRepeat(withAi, random)
    }

    /**
     * Draws up to [size] items from the pools, interleaving the types round-robin so a
     * mixed round alternates its types. Each pool is shuffled first; a pool with fewer
     * items than its share simply runs out.
     */
    private fun sample(pools: Map<DrillType, List<DrillItem>>, random: Random, size: Int): List<DrillItem> {
        val queues = pools.values.map { it.shuffled(random).toMutableList() }.filter { it.isNotEmpty() }
        if (queues.isEmpty()) return emptyList()
        val result = mutableListOf<DrillItem>()
        while (result.size < size) {
            val before = result.size
            for (queue in queues) {
                if (result.size >= size) break
                queue.removeFirstOrNull()?.let { result.add(it) }
            }
            if (result.size == before) break // every pool exhausted
        }
        return result
    }

    /**
     * The rung-3 variety format for an item answered right once. Only true-false has
     * generation today; the other variety formats degrade to tap until implemented.
     */
    private fun varietyFormat(item: DrillItem): DrillFormat {
        // TODO(#639): odd-one-out, match-the-columns and word-bank generation.
        // TODO(#532): listen-and-tap needs TTS and stays out until then.
        return if (trueFalseQuestion(item, isTrue = true) != null) DrillFormat.TRUE_FALSE else DrillFormat.TAP
    }

    /** Builds the question for [item] in [format], or falls back to tap when unsupported. */
    private fun question(item: DrillItem, format: DrillFormat, random: Random): DrillQuizQuestion =
        when (format) {
            DrillFormat.TYPED -> DrillQuizQuestion(item, DrillFormat.TYPED)
            DrillFormat.TRUE_FALSE -> trueFalseQuestion(item, isTrue = random.nextBoolean())
                ?: DrillQuizQuestion(item, DrillFormat.TAP, item.choices)
            else -> DrillQuizQuestion(item, DrillFormat.TAP, item.choices)
        }

    /**
     * A true-false question: the prompt with its gap filled by the answer (true) or by
     * a safe distractor (false). Null when the item has no gap, too few choices, or no
     * distractor that is not itself an accepted answer.
     */
    private fun trueFalseQuestion(item: DrillItem, isTrue: Boolean): DrillQuizQuestion? {
        if (!item.prompt.contains(DrillItem.GAP_MARKER)) return null
        if (item.choices.size < 2) return null
        val fill = if (isTrue) {
            item.answer
        } else {
            val accepted = item.answer.split(Regex("\\s*/\\s*")).map { it.trim() }
            item.choices.firstOrNull { it.trim() !in accepted } ?: return null
        }
        return DrillQuizQuestion(
            item = item,
            format = DrillFormat.TRUE_FALSE,
            choices = listOf(DrillQuizQuestion.TRUE_CHOICE, DrillQuizQuestion.FALSE_CHOICE),
            statement = item.prompt.replaceFirst(DrillItem.GAP_MARKER, fill),
            isTrue = isTrue,
        )
    }

    /**
     * Shows at most one flashcard: an early item (maturity 0 or 1) gets a flashcard
     * step immediately before its own question, so the learner sees the answer right
     * before the first or second quiz on it. The flashcard counts toward the round's
     * 3-5 steps — when the round is already over [cap], one other question
     * is dropped to make room.
     */
    private fun addFlashcards(
        planned: List<DrillQuizQuestion>,
        maturity: Map<DrillItemId, Int>,
        random: Random,
        allowFlashcard: Boolean,
        cap: Int = MAX_ROUND_SIZE,
    ): List<DrillQuizQuestion> {
        if (!allowFlashcard || planned.size < 2) return planned
        val candidates = planned
            .withIndex()
            .filter { (_, step) ->
                step.format == DrillFormat.TAP || step.format == DrillFormat.TRUE_FALSE
            }
            .filter { (_, step) -> (maturity[step.id] ?: 0) <= 1 }
        val chosen = if (candidates.isEmpty()) {
            null
        } else {
            candidates[random.nextInt(candidates.size)]
        } ?: return planned

        val flashcard = DrillQuizQuestion(chosen.value.item, DrillFormat.FLASHCARD)
        val withCard = planned.subList(0, chosen.index) + flashcard + planned.subList(chosen.index, planned.size)
        if (withCard.size <= cap) return withCard
        val dropIndex = withCard.indexOfLast { !it.isTeaching && it.id != chosen.value.id }
        return if (dropIndex >= 0) withCard.filterIndexed { index, _ -> index != dropIndex } else withCard
    }

    /**
     * Adds the AI practice items (#621) as flashcard steps: flashcard-only, never a
     * question, never graduating to tap or typed — [DrillQuiz] scores teaching steps
     * not at all, so an AI card changes no type's evidence. Each card is inserted
     * before a random step (or at the end) so the cards spread through the round;
     * the no-two-consecutive-same-format rule applies to them best-effort, like to
     * every step, through [orderWithoutRepeat].
     */
    private fun addAiFlashcards(
        planned: List<DrillQuizQuestion>,
        aiItems: List<DrillItem>,
        random: Random,
    ): List<DrillQuizQuestion> {
        if (aiItems.isEmpty()) return planned
        var result = planned
        for (item in aiItems) {
            val card = DrillQuizQuestion(item, DrillFormat.FLASHCARD)
            val index = random.nextInt(result.size + 1)
            result = result.subList(0, index) + card + result.subList(index, result.size)
        }
        return result
    }

    /**
     * Shuffles the steps until no two consecutive questions share a format. A flashcard
     * stays immediately before its own tap question. Returns the last shuffle when no
     * arrangement can satisfy the rule (for example a tap-only round of one format).
     */
    private fun orderWithoutRepeat(steps: List<DrillQuizQuestion>, random: Random): List<DrillQuizQuestion> {
        if (steps.size < 2) return steps
        // A flashcard and its tap travel together: the flashcard must stay before its tap.
        val units = mutableListOf<List<DrillQuizQuestion>>()
        var index = 0
        while (index < steps.size) {
            val step = steps[index]
            val next = steps.getOrNull(index + 1)
            if (step.isFlashcard && next != null && next.item.id == step.item.id) {
                units.add(listOf(step, next))
                index += 2
            } else {
                units.add(listOf(step))
                index++
            }
        }

        var best = steps
        var bestViolations = countRepeats(best)
        var attempts = 0
        while (bestViolations > 0 && attempts < 64) {
            val candidate = units.shuffled(random).flatten()
            val violations = countRepeats(candidate)
            if (violations < bestViolations) {
                best = candidate
                bestViolations = violations
            }
            attempts++
        }
        return best
    }

    private fun countRepeats(steps: List<DrillQuizQuestion>): Int =
        steps.zipWithNext().count { it.first.format == it.second.format }
}
