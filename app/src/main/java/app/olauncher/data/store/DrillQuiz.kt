package app.olauncher.data.store

import java.util.Random

/** The round phase of a [DrillQuiz]. */
enum class DrillRound { CHOICE, TYPED }

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
) {
    val hasTypedRound: Boolean get() = !isChoiceOnly

    private val firstAttemptsMap = mutableMapOf<DrillItemId, Boolean>()
    private val queue = ArrayDeque<DrillItem>()
    private var question: DrillQuizQuestion? = null
    private var lastAnswerValue: QuizAnswer? = null

    var round: DrillRound = DrillRound.CHOICE
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

    /** True when both rounds have finished or there were no items. */
    val isFinished: Boolean get() = question == null

    /** True when both rounds have finished. */
    val isRoundFinished: Boolean get() = question == null

    /**
     * Map of first-attempt results for each item in the session.
     * Retries and the typed round never alter these results.
     */
    fun firstAttempts(): Map<DrillItemId, Boolean> = firstAttemptsMap.toMap()

    /** Number of items answered correctly on the very first try. */
    val firstTryScore: Int get() = firstAttemptsMap.values.count { it }

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

    /** Records a typed answer using [checkTypedAnswer]. */
    fun answerTyped(input: String): QuizAnswer? {
        val q = question ?: return null
        if (lastAnswerValue != null) return null
        val isCorrect = checkTypedAnswer(input, q.answer)
        val result = QuizAnswer(isCorrect, q.answer)
        recordAttempt(q.item, isCorrect)
        lastAnswerValue = result
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
        }
    }

    private fun buildQuestion() {
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
