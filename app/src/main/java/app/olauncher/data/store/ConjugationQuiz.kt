package app.olauncher.data.store

import java.util.Random

/** How a form stands in the quiz: not yet answered right, right first try, or missed at least once. */
enum class FormStatus { PENDING, RIGHT_FIRST_TRY, MISSED }

/** The question on screen: the form at [formIndex] in the paradigm, with its shuffled [choices]. */
data class QuizQuestion(val formIndex: Int, val pronoun: String, val answer: String, val choices: List<String>)

/** The outcome of one pick: whether it was [correct] and the form that was asked for. */
data class QuizAnswer(val correct: Boolean, val answer: String)

/** Only a right answer moves on by itself; a wrong one waits so the right form can be read. */
fun shouldAutoAdvance(answer: QuizAnswer?): Boolean = answer?.correct == true

/** How long a right answer stays green before the quiz moves on. */
const val AUTO_ADVANCE_DELAY_MS = 800L

/**
 * Android-free state for the conjugation quiz. Each form is asked with up to
 * four choices (the right form plus other distinct forms of the same verb).
 * A wrong answer puts the form back at the end of the queue, so misses come
 * back until answered right. The first-try score only counts forms that were
 * right the first time they were asked and never changes after a retry.
 */
class ConjugationQuiz(
    private val prompts: List<DrillPrompt>,
    private val random: Random = Random(),
) {
    private val missCounts = IntArray(prompts.size)
    private val seen = BooleanArray(prompts.size)
    private val queue = ArrayDeque<Int>()
    private var question: QuizQuestion? = null
    private var lastAnswer: QuizAnswer? = null

    init {
        queue.addAll(prompts.indices)
        buildQuestion()
    }

    val total: Int get() = prompts.size

    /** The question to answer now, or null when the round is over. */
    val current: QuizQuestion? get() = question

    /** True once the current question has been answered. */
    val isAnswered: Boolean get() = lastAnswer != null

    val isRoundFinished: Boolean get() = question == null

    val firstTryScore: Int get() = prompts.indices.count { status(it) == FormStatus.RIGHT_FIRST_TRY }

    fun missCount(formIndex: Int): Int = missCounts[formIndex]

    val missedForms: List<Int> get() = prompts.indices.filter { missCounts[it] > 0 }

    fun status(formIndex: Int): FormStatus = when {
        missCounts[formIndex] > 0 -> FormStatus.MISSED
        seen[formIndex] && formIndex != question?.formIndex -> FormStatus.RIGHT_FIRST_TRY
        else -> FormStatus.PENDING
    }

    fun pronoun(formIndex: Int): String = prompts[formIndex].pronoun
    fun form(formIndex: Int): String = prompts[formIndex].form

    /** Records the pick for the current question. Null when there is none or it was already answered. */
    fun answer(choice: String): QuizAnswer? {
        val q = question ?: return null
        if (lastAnswer != null) return null
        val result = QuizAnswer(choice == q.answer, q.answer)
        if (!result.correct) missCounts[q.formIndex]++
        lastAnswer = result
        return result
    }

    /** Moves to the next question. A form answered wrong goes to the back of the queue. */
    fun advance() {
        val q = question ?: return
        val result = lastAnswer ?: return
        queue.removeFirst()
        if (!result.correct) queue.addLast(q.formIndex)
        lastAnswer = null
        buildQuestion()
    }

    /** Starts a new round with only the missed forms; returns false when nothing was missed. */
    fun startRetry(): Boolean {
        if (!isRoundFinished) return false
        val missed = missedForms
        if (missed.isEmpty()) return false
        queue.clear()
        queue.addAll(missed)
        lastAnswer = null
        buildQuestion()
        return true
    }

    private fun buildQuestion() {
        val index = queue.firstOrNull()
        if (index == null) {
            question = null
            return
        }
        val prompt = prompts[index]
        val others = prompts.map { it.form }.distinct().filter { it != prompt.form }.toMutableList()
        others.shuffle(random)
        val choices = (others.take(MAX_CHOICES - 1) + prompt.form).toMutableList()
        choices.shuffle(random)
        question = QuizQuestion(index, prompt.pronoun, prompt.form, choices)
        seen[index] = true
    }

    private companion object {
        const val MAX_CHOICES = 4
    }
}
