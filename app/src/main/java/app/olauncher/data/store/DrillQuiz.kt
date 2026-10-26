package app.olauncher.data.store

import java.util.Random

/** The round phase of a [DrillQuiz]. */
enum class DrillRound { CHOICE, TYPED }

/** Result of an item attempt in a drill round. */
enum class DrillResult {
    WRONG,
    ALMOST,
    RIGHT;

    val isRight: Boolean get() = this == RIGHT
    val isAlmost: Boolean get() = this == ALMOST
    val isWrong: Boolean get() = this == WRONG
}

/**
 * The current question displayed to the user: the underlying [item],
 * the list of [choices] (empty in typed mode), and the current [round].
 */
data class DrillQuizQuestion(
    val item: DrillItem,
    val choices: List<String>,
    val round: DrillRound,
) {
    val id: DrillItemId get() = item.id
    val prompt: String get() = item.prompt
    val answer: String get() = item.answer
    val hint: String? get() = item.hint
}

/**
 * Quiz engine for drills.
 *
 * Runs a multiple-choice round first, followed by a typed round over the same items
 * for types that keep a typed round. Choice-only types finish after the choice round.
 * A wrong answer puts the item back at the end of the current round.
 * First-try accuracy is tracked across items in the session and never changes during retries
 * or the typed round.
 */
class DrillQuiz(
    val items: List<DrillItem>,
    private val random: Random = Random(),
    val isChoiceOnly: Boolean = items.firstOrNull()?.id?.type?.isChoiceOnly ?: false,
    startRound: DrillRound = DrillRound.CHOICE,
) {
    val hasTypedRound: Boolean get() = !isChoiceOnly

    private val firstAttemptsMap = mutableMapOf<DrillItemId, Boolean>()
    private val itemResultsMap = mutableMapOf<DrillItemId, DrillResult>()
    private val typedMissIds = mutableSetOf<DrillItemId>()
    private val queue = ArrayDeque<DrillItem>()
    private var question: DrillQuizQuestion? = null
    private var lastAnswerValue: QuizAnswer? = null

    private var nearMissOffered = false
    private var awaitingRetry = false

    var round: DrillRound = startRound
        private set

    init {
        if (items.isNotEmpty()) {
            queue.addAll(items)
            buildQuestion()
        }
    }

    val isTyped: Boolean get() = round == DrillRound.TYPED

    val total: Int get() = items.size

    /** Current question to present, or null when the quiz is finished. */
    val current: DrillQuizQuestion? get() = question

    /** Current drill item, or null when finished. */
    val currentItem: DrillItem? get() = question?.item

    /** The outcome of the most recent answer, or null if unanswered. */
    val lastAnswer: QuizAnswer? get() = lastAnswerValue

    /** True once the current question has been answered. */
    val isAnswered: Boolean get() = lastAnswerValue != null

    /** True if the current question was a near miss and is awaiting a single retry. */
    val isAwaitingRetry: Boolean get() = awaitingRetry

    /** True when both rounds have finished or there were no items. */
    val isFinished: Boolean get() = question == null

    /** True when both rounds have finished. */
    val isRoundFinished: Boolean get() = question == null

    /**
     * Map of first-attempt results for each item in the session.
     * Retries and the typed round never alter these results.
     */
    fun firstAttempts(): Map<DrillItemId, Boolean> = firstAttemptsMap.toMap()

    /** Map of overall results for each item in the round. */
    fun itemResults(): Map<DrillItemId, DrillResult> = itemResultsMap.toMap()

    /** Number of items answered correctly on the very first try. */
    val firstTryScore: Int get() = firstAttemptsMap.values.count { it }

    /** Number of items marked 'almost' (exact retry after near miss). */
    val almostScore: Int get() = itemResultsMap.values.count { it == DrillResult.ALMOST }

    /** Records a multiple-choice pick. Delegates to [answerTyped] in typed mode. */
    fun answer(choice: String): QuizAnswer? {
        if (round == DrillRound.TYPED) {
            return answerTyped(choice)
        }
        val q = question ?: return null
        if (lastAnswerValue != null) return null
        val expectedOptions = q.answer.split(Regex("\\s*/\\s*"))
        val isCorrect = choice == q.answer || choice.trim() in expectedOptions
        val result = QuizAnswer(isCorrect, q.answer)
        recordAttempt(q.item, isCorrect)
        lastAnswerValue = result
        return result
    }

    /** Records a typed answer using [checkTypedAnswer] and [isNearMiss]. */
    fun answerTyped(input: String): QuizAnswer? {
        val q = question ?: return null
        if (lastAnswerValue != null) return null

        val isExact = checkTypedAnswer(input, q.answer)
        if (isExact) {
            val wasNearMiss = nearMissOffered
            val result = QuizAnswer(correct = true, answer = q.answer, isAlmost = wasNearMiss)
            awaitingRetry = false
            nearMissOffered = false
            lastAnswerValue = result
            val drillResult = if (wasNearMiss) DrillResult.ALMOST else DrillResult.RIGHT
            recordTypedResult(q.item, drillResult)
            return result
        }

        // Not an exact match
        if (!nearMissOffered && isNearMiss(input, q.answer)) {
            nearMissOffered = true
            awaitingRetry = true
            return QuizAnswer(correct = false, answer = q.answer, isNearMiss = true)
        }

        // Second miss (if nearMissOffered) or far miss (not near miss on first try)
        awaitingRetry = false
        nearMissOffered = false
        val result = QuizAnswer(correct = false, answer = q.answer)
        lastAnswerValue = result
        recordTypedResult(q.item, DrillResult.WRONG)
        return result
    }

    /**
     * Advances to the next question.
     *
     * A wrong answer puts the item at the end of the current round.
     * When the choice round completes, automatically switches to the typed round.
     */
    fun advance() {
        val q = question ?: return
        val result = lastAnswerValue ?: return
        awaitingRetry = false
        nearMissOffered = false
        queue.removeFirst()
        if (!result.correct) {
            queue.addLast(q.item)
        }
        lastAnswerValue = null
        if (queue.isEmpty()) {
            if (round == DrillRound.CHOICE && hasTypedRound) {
                round = DrillRound.TYPED
                queue.addAll(items)
                buildQuestion()
            } else {
                question = null
            }
        } else {
            buildQuestion()
        }
    }

    private fun recordAttempt(item: DrillItem, isCorrect: Boolean) {
        if (round == DrillRound.CHOICE && item.id !in firstAttemptsMap) {
            firstAttemptsMap[item.id] = isCorrect
            itemResultsMap[item.id] = if (isCorrect) DrillResult.RIGHT else DrillResult.WRONG
        }
    }

    private fun recordTypedResult(item: DrillItem, result: DrillResult) {
        if (round != DrillRound.TYPED) return
        if (result == DrillResult.WRONG) typedMissIds.add(item.id)
        // A miss earlier in the typed round stays a miss, so a later correct retry cannot erase it.
        itemResultsMap[item.id] = if (item.id in typedMissIds) DrillResult.WRONG else result
        if (item.id !in firstAttemptsMap) {
            firstAttemptsMap[item.id] = result == DrillResult.RIGHT
        }
    }

    private fun buildQuestion() {
        awaitingRetry = false
        nearMissOffered = false
        val item = queue.firstOrNull()
        if (item == null) {
            question = null
            return
        }
        val choices = if (round == DrillRound.TYPED) {
            emptyList()
        } else {
            item.choices.shuffled(random)
        }
        question = DrillQuizQuestion(item, choices, round)
    }

    companion object {
        const val ROUND_SIZE = DRILL_ROUND_SIZE
    }
}
