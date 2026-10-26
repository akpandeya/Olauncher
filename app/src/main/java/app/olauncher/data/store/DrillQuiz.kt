package app.olauncher.data.store

import java.util.Random

/**
 * Result of an item attempt in a drill round.
 */
enum class DrillResult {
    WRONG,
    ALMOST,
    RIGHT;

    val isRight: Boolean get() = this == RIGHT
    val isAlmost: Boolean get() = this == ALMOST
    val isWrong: Boolean get() = this == WRONG
}

/**
 * Quiz engine for one redesigned drill round (#639).
 *
 * Runs the planned steps of [DrillRoundPlanner] in order: flashcards, tap and
 * true-false questions, and graduated typed questions, all in a single short round.
 * Nothing is requeued inside the round — a wrong answer is corrected inline and the
 * miss is recorded at the moment of the wrong answer, so an abandoned round (#624)
 * already carries it. Misses come back through "Practise misses" in the summary.
 * A near-miss spelling still gets one retry (#622), unchanged.
 * First-try accuracy is tracked across items and never changes during retries.
 */
class DrillQuiz(
    val steps: List<DrillQuizQuestion>,
    private val random: Random = Random(),
) {
    private val firstAttemptsMap = mutableMapOf<DrillItemId, Boolean>()
    private val itemResultsMap = mutableMapOf<DrillItemId, DrillResult>()
    private val typedResultsMap = mutableMapOf<DrillItemId, DrillResult>()
    private val learnerAnswersMap = mutableMapOf<DrillItemId, String>()
    private val typedMissIds = mutableSetOf<DrillItemId>()
    private val queue = ArrayDeque(steps)
    private var question: DrillQuizQuestion? = null
    private var lastAnswerValue: QuizAnswer? = null
    private var position = 0

    private var nearMissOffered = false
    private var awaitingRetry = false

    init {
        if (steps.isNotEmpty()) {
            buildQuestion()
        }
    }

    /** The scored items of the round in step order; flashcards are not scored. */
    val items: List<DrillItem> get() = steps.filterNot { it.isFlashcard }.map { it.item }

    val total: Int get() = steps.size

    /** Zero-based position of the current step in [steps], or [total] when finished. */
    val currentPosition: Int get() = position

    /** Current question to present, or null when the quiz is finished. */
    val current: DrillQuizQuestion? get() = question

    val currentFormat: DrillFormat? get() = question?.format

    /** Current drill item, or null when finished. */
    val currentItem: DrillItem? get() = question?.item

    /** The outcome of the most recent answer, or null if unanswered. */
    val lastAnswer: QuizAnswer? get() = lastAnswerValue

    /**
     * True once the current question has been dealt with: answered, or a flashcard
     * waiting for its Continue tap.
     */
    val isAnswered: Boolean get() = lastAnswerValue != null || question?.isFlashcard == true

    /** True if the current question was a near miss and is awaiting a single retry. */
    val isAwaitingRetry: Boolean get() = awaitingRetry

    /** True when all steps have finished or there were no steps. */
    val isFinished: Boolean get() = question == null

    /**
     * Map of first-attempt results for each scored item in the session.
     * Retries never alter these results; flashcards never appear here.
     */
    fun firstAttempts(): Map<DrillItemId, Boolean> = firstAttemptsMap.toMap()

    /** Map of overall results for each scored item in the round. */
    fun itemResults(): Map<DrillItemId, DrillResult> = itemResultsMap.toMap()

    /** Results of the typed answers given so far, keyed by item; items not typed yet are absent. */
    fun typedResults(): Map<DrillItemId, DrillResult> = typedResultsMap.toMap()

    /** Map of learner answers for items that were wrong or almost. */
    fun learnerAnswers(): Map<DrillItemId, String> = learnerAnswersMap.toMap()

    /** Number of items answered correctly on the very first try. */
    val firstTryScore: Int get() = firstAttemptsMap.values.count { it }

    /** Number of items marked 'almost' (exact retry after near miss). */
    val almostScore: Int get() = itemResultsMap.values.count { it == DrillResult.ALMOST }

    /** Records a choice pick for a tap, true-false or odd-one-out question. */
    fun answer(choice: String): QuizAnswer? {
        val q = question ?: return null
        if (lastAnswerValue != null || q.isFlashcard) return null
        val correctChoice = q.correctChoice ?: return null
        val isCorrect = when (q.format) {
            DrillFormat.TRUE_FALSE -> choice == correctChoice
            else -> choice == q.answer || choice.trim() in q.answer.split(Regex("\\s*/\\s*"))
        }
        val result = QuizAnswer(isCorrect, q.answer)
        recordAttempt(q.item, isCorrect)
        if (!isCorrect && q.item.id !in learnerAnswersMap) {
            learnerAnswersMap[q.item.id] = choice
        }
        lastAnswerValue = result
        return result
    }

    /** Records a typed answer using [checkTypedAnswer] and [isNearMiss]. */
    fun answerTyped(input: String): QuizAnswer? {
        val q = question ?: return null
        if (lastAnswerValue != null || !q.isTyped) return null

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
            learnerAnswersMap[q.item.id] = input.trim()
            return QuizAnswer(correct = false, answer = q.answer, isNearMiss = true)
        }

        // Second miss (if nearMissOffered) or far miss (not near miss on first try)
        awaitingRetry = false
        nearMissOffered = false
        val result = QuizAnswer(correct = false, answer = q.answer)
        lastAnswerValue = result
        learnerAnswersMap[q.item.id] = input.trim()
        recordTypedResult(q.item, DrillResult.WRONG)
        return result
    }

    /**
     * Advances to the next step. Nothing is requeued: a wrong answer has already been
     * recorded and corrected inline. Flashcards advance on their Continue tap.
     */
    fun advance() {
        if (question == null || !isAnswered) return
        awaitingRetry = false
        nearMissOffered = false
        queue.removeFirst()
        position++
        lastAnswerValue = null
        buildQuestion()
    }

    private fun recordAttempt(item: DrillItem, isCorrect: Boolean) {
        if (item.id !in firstAttemptsMap) {
            firstAttemptsMap[item.id] = isCorrect
            itemResultsMap[item.id] = if (isCorrect) DrillResult.RIGHT else DrillResult.WRONG
        }
    }

    private fun recordTypedResult(item: DrillItem, result: DrillResult) {
        if (result == DrillResult.WRONG) typedMissIds.add(item.id)
        // A miss earlier in the typed answer stays a miss, so a later correct retry cannot erase it.
        val finalResult = if (item.id in typedMissIds) DrillResult.WRONG else result
        itemResultsMap[item.id] = finalResult
        typedResultsMap[item.id] = finalResult
        if (item.id !in firstAttemptsMap) {
            firstAttemptsMap[item.id] = result == DrillResult.RIGHT
        }
    }

    private fun buildQuestion() {
        awaitingRetry = false
        nearMissOffered = false
        val step = queue.firstOrNull()
        question = when {
            step == null -> null
            step.isFlashcard -> step
            step.isTyped -> step
            else -> step.copy(choices = step.choices.shuffled(random))
        }
    }
}
