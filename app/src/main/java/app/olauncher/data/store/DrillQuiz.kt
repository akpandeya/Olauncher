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
 *
 * Pacing (#drill-pacing): the round is self-shrinking — every wrong answer drops one
 * future step, down to [DrillRoundPlanner.MIN_ROUND_SIZE] — and after 2 consecutive
 * wrong answers of a drill type, the next step of that type is preceded by an
 * unscored rule-recap card built from the missed item's own answer and rule, before
 * quizzing resumes. Above the floor the recap replaces that step; at the floor it
 * is inserted instead, so the round never drops below [DrillRoundPlanner.MIN_ROUND_SIZE]
 * scored steps.
 */
class DrillQuiz(
    steps: List<DrillQuizQuestion>,
    private val random: Random = Random(),
) {
    private val firstAttemptsMap = mutableMapOf<DrillItemId, Boolean>()
    private val itemResultsMap = mutableMapOf<DrillItemId, DrillResult>()
    private val typedResultsMap = mutableMapOf<DrillItemId, DrillResult>()
    private val learnerAnswersMap = mutableMapOf<DrillItemId, String>()
    private val typedMissIds = mutableSetOf<DrillItemId>()

    /** Items whose help ("Why?") was opened before the answer (design §2 scoring). */
    private val whyTappedIds = mutableSetOf<DrillItemId>()

    /** The live plan: wrong answers shrink it and insert recap steps, so it is mutable. */
    private val stepsList = steps.toMutableList()
    private var question: DrillQuizQuestion? = null
    private var lastAnswerValue: QuizAnswer? = null
    private var position = 0

    /** Consecutive wrong answers per drill type, and the item each streak ends on. */
    private val missStreaks = mutableMapOf<DrillType, Int>()
    private val lastMissedByType = mutableMapOf<DrillType, DrillItem>()

    private var nearMissOffered = false
    private var awaitingRetry = false

    init {
        if (stepsList.isNotEmpty()) {
            buildQuestion()
        }
    }

    /** The scored items of the round in step order; teaching and reveal steps are never scored. */
    val items: List<DrillItem> get() = stepsList.filterNot { it.isTeaching || it.isReveal }.map { it.item }

    val total: Int get() = stepsList.size

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
     * True once the current question has been dealt with: answered, a teaching step
     * (flashcard or recap) waiting for its Continue tap, or a reveal-style fallback.
     */
    val isAnswered: Boolean
        get() = lastAnswerValue != null || question?.isTeaching == true || question?.isReveal == true

    /** True if the current question was a near miss and is awaiting a single retry. */
    val isAwaitingRetry: Boolean get() = awaitingRetry

    /** True when all steps have finished or there were no steps. */
    val isFinished: Boolean get() = question == null

    /** The round's steps as they stand now — shrunk and recap-augmented by the pacing rules. */
    val steps: List<DrillQuizQuestion> get() = stepsList

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

    /**
     * The items whose help ("Why?") was opened before answering, never after. A right
     * answer on these is recorded as a weaker right answer (design §2).
     */
    fun whyTappedIds(): Set<DrillItemId> = whyTappedIds.toSet()

    /**
     * Records a "Why?" tap on the current question. Only a tap before the answer
     * counts; a teaching step (flashcard, recap) is never quizzed, so it never counts.
     */
    fun recordWhyTap() {
        val q = question ?: return
        if (lastAnswerValue != null || q.isTeaching || q.isReveal) return
        whyTappedIds.add(q.item.id)
    }

    /** Number of items answered correctly on the very first try. */
    val firstTryScore: Int get() = firstAttemptsMap.values.count { it }

    /** Number of items marked 'almost' (exact retry after near miss). */
    val almostScore: Int get() = itemResultsMap.values.count { it == DrillResult.ALMOST }

    /** Records a choice pick for a tap, true-false or odd-one-out question. */
    fun answer(choice: String): QuizAnswer? {
        val q = question ?: return null
        if (lastAnswerValue != null || q.isTeaching || q.isReveal) return null
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
     * recorded and corrected inline. Teaching steps (flashcards, recaps) advance on
     * their Continue tap.
     */
    fun advance() {
        if (question == null || !isAnswered) return
        awaitingRetry = false
        nearMissOffered = false
        position++
        lastAnswerValue = null
        buildQuestion()
    }

    private fun recordAttempt(item: DrillItem, isCorrect: Boolean) {
        if (item.id !in firstAttemptsMap) {
            firstAttemptsMap[item.id] = isCorrect
            itemResultsMap[item.id] = resultOf(item, isCorrect)
            if (isCorrect) onHit(item.id.type) else onMissed(item)
        }
    }

    /**
     * The round result of a correct answer. A right answer after a "Why?" tap is a
     * weaker right answer (#651 design §2): shown as ALMOST (sand, not sage) and
     * halved in the score evidence, but still a right first attempt.
     */
    private fun resultOf(item: DrillItem, isCorrect: Boolean): DrillResult = when {
        !isCorrect -> DrillResult.WRONG
        item.id in whyTappedIds -> DrillResult.ALMOST
        else -> DrillResult.RIGHT
    }

    private fun recordTypedResult(item: DrillItem, result: DrillResult) {
        if (result == DrillResult.WRONG) typedMissIds.add(item.id)
        // A miss earlier in the typed answer stays a miss, so a later correct retry cannot erase it.
        val finalResult = when {
            item.id in typedMissIds -> DrillResult.WRONG
            result == DrillResult.RIGHT -> resultOf(item, isCorrect = true)
            else -> result
        }
        itemResultsMap[item.id] = finalResult
        typedResultsMap[item.id] = finalResult
        if (item.id !in firstAttemptsMap) {
            firstAttemptsMap[item.id] = result == DrillResult.RIGHT
        }
        if (finalResult == DrillResult.WRONG) onMissed(item) else onHit(item.id.type)
    }

    /** A right (or recovered) answer ends that type's miss streak. */
    private fun onHit(type: DrillType) {
        missStreaks.remove(type)
    }

    /**
     * A wrong answer extends that type's miss streak and shrinks the round by one step
     * (floor [DrillRoundPlanner.MIN_ROUND_SIZE]). At the second consecutive miss of a
     * type, the next step of that type is replaced by an unscored rule recap before
     * quizzing resumes.
     */
    private fun onMissed(item: DrillItem) {
        val type = item.id.type
        missStreaks[type] = (missStreaks[type] ?: 0) + 1
        lastMissedByType[type] = item
        if ((missStreaks[type] ?: 0) >= BREATHER_AFTER_MISSES) {
            insertBreather(type)
            missStreaks[type] = 0
        }
        shrinkRound()
    }

    /**
     * Drops the last future step so the round ends one question earlier; the floor is
     * [DrillRoundPlanner.MIN_ROUND_SIZE] scored steps. A teaching step is never the
     * drop target: a recap was just placed for this miss, and a flashcard travels
     * with its question — when the pair ends the round, both steps go together so
     * the card is never left without its question.
     */
    private fun shrinkRound() {
        if (scoredStepCount() <= DrillRoundPlanner.MIN_ROUND_SIZE) return
        if (position >= stepsList.lastIndex) return // nothing future to drop
        val last = stepsList.lastIndex
        if (stepsList[last].isTeaching) return
        val flashcardBefore = stepsList[last - 1].isFlashcard &&
            stepsList[last - 1].id == stepsList[last].id && last - 1 > position
        if (flashcardBefore) {
            stepsList.removeAt(last)
            stepsList.removeAt(last - 1)
        } else {
            stepsList.removeAt(last)
        }
    }

    /** Steps that carry a score: neither teaching (flashcard, recap) nor a reveal fallback. */
    private fun scoredStepCount(): Int = stepsList.count { !it.isTeaching && !it.isReveal }

    /**
     * Places a recap of the item the streak ended on before the next scored step of
     * [type]. Above the floor the recap replaces that step; at the floor
     * ([DrillRoundPlanner.MIN_ROUND_SIZE] scored steps) it is inserted instead, so the
     * round keeps its three scored questions. A flashcard travels with its question:
     * when the target step has one right before it, the card goes too — it would front
     * a recap of a different item and teach something the round never asks. Above the
     * floor the card and its question are both replaced by the recap; at the floor the
     * card's slot becomes the recap and the question still follows it.
     */
    private fun insertBreather(type: DrillType) {
        val missedItem = lastMissedByType[type] ?: return
        val index = (position + 1 until stepsList.size).firstOrNull { i ->
            val step = stepsList[i]
            !step.isTeaching && step.id.type == type
        } ?: return
        val flashcardBefore = index > 0 && stepsList[index - 1].isFlashcard &&
            stepsList[index - 1].id == stepsList[index].id && index - 1 > position
        val recap = DrillQuizQuestion(item = missedItem, format = DrillFormat.RECAP)
        if (scoredStepCount() <= DrillRoundPlanner.MIN_ROUND_SIZE) {
            // At the floor the recap must not eat a scored question: it takes the
            // paired flashcard's slot if there is one, and the question stays.
            if (flashcardBefore) {
                stepsList[index - 1] = recap
            } else {
                stepsList.add(index, recap)
            }
            return
        }
        if (flashcardBefore) stepsList.removeAt(index - 1)
        stepsList[if (flashcardBefore) index - 1 else index] = recap
    }

    private fun buildQuestion() {
        awaitingRetry = false
        nearMissOffered = false
        val step = stepsList.getOrNull(position)
        question = when {
            step == null -> null
            step.isTeaching -> step
            else -> step.copy(choices = step.choices.shuffled(random))
        }
    }

    companion object {
        /** Consecutive wrong answers of a drill type after which a rule recap is inserted. */
        const val BREATHER_AFTER_MISSES = 2
    }
}
