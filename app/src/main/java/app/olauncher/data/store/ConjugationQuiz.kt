package app.olauncher.data.store

import java.text.Normalizer
import java.util.Random

/** How a form stands in the quiz: not yet answered right, right first try, or missed at least once. */
enum class FormStatus { PENDING, RIGHT_FIRST_TRY, MISSED }

/** The question on screen: the form at [formIndex] in the paradigm, with its shuffled [choices]. */
data class QuizQuestion(val formIndex: Int, val pronoun: String, val answer: String, val choices: List<String>)

/** The outcome of one pick: whether it was [correct] and the form that was asked for. */
data class QuizAnswer(
    val correct: Boolean,
    val answer: String,
    val isNearMiss: Boolean = false,
    val isAlmost: Boolean = false,
)

/** Only a right answer moves on by itself; a wrong one waits so the right form can be read. */
fun shouldAutoAdvance(answer: QuizAnswer?): Boolean = answer?.correct == true

/** How long a right answer stays green before the quiz moves on. */
const val AUTO_ADVANCE_DELAY_MS = 800L

/**
 * Checks a typed answer against the expected form:
 * - Trims whitespace
 * - NFC-normalises (canonical composition for diacritics)
 * - Case-sensitive except for the first letter
 * - Umlauts required (no ae/oe/ue folding)
 */
fun checkTypedAnswer(input: String, expected: String): Boolean {
    if ("/" in expected) {
        return expected.split(Regex("\\s*/\\s*")).any { checkTypedAnswer(input, it) }
    }
    val normInput = Normalizer.normalize(input.trim(), Normalizer.Form.NFC)
    val normExpected = Normalizer.normalize(expected.trim(), Normalizer.Form.NFC)
    if (normInput.isEmpty() || normExpected.isEmpty()) {
        return normInput.isEmpty() && normExpected.isEmpty()
    }
    if (normInput.length != normExpected.length) {
        return false
    }
    val firstInput = normInput.take(1)
    val firstExpected = normExpected.take(1)
    if (!firstInput.equals(firstExpected, ignoreCase = true)) {
        return false
    }
    return normInput.drop(1) == normExpected.drop(1)
}

private val UMLAUT_EQUIVALENTS = listOf(
    "ä" to "ae",
    "ö" to "oe",
    "ü" to "ue",
    "ß" to "ss",
)

private fun levenshteinDistance(s1: String, s2: String): Int {
    if (s1 == s2) return 0
    if (s1.isEmpty()) return s2.length
    if (s2.isEmpty()) return s1.length

    val prev = IntArray(s2.length + 1) { it }
    val curr = IntArray(s2.length + 1)

    for (i in 1..s1.length) {
        curr[0] = i
        for (j in 1..s2.length) {
            val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
            curr[j] = minOf(
                curr[j - 1] + 1,
                prev[j] + 1,
                prev[j - 1] + cost,
            )
        }
        System.arraycopy(curr, 0, prev, 0, s2.length + 1)
    }
    return prev[s2.length]
}

private fun isOneUmlautEquivalentAway(s1: String, s2: String): Boolean {
    for ((single, double) in UMLAUT_EQUIVALENTS) {
        var idx = s1.indexOf(single)
        while (idx >= 0) {
            val replaced = s1.substring(0, idx) + double + s1.substring(idx + single.length)
            if (replaced == s2) return true
            idx = s1.indexOf(single, idx + 1)
        }
        idx = s1.indexOf(double)
        while (idx >= 0) {
            val replaced = s1.substring(0, idx) + single + s1.substring(idx + double.length)
            if (replaced == s2) return true
            idx = s1.indexOf(double, idx + 1)
        }
    }
    return false
}

/**
 * Checks whether [input] is a near miss for [expected]:
 * - Not an exact match (exact match returns false)
 * - One letter added, missing, or wrong (Levenshtein distance == 1)
 * - One umlaut-equivalent difference (ä/ae, ö/oe, ü/ue, ß/ss)
 * - Case-sensitive except for the first letter
 */
fun isNearMiss(input: String, expected: String): Boolean {
    if (checkTypedAnswer(input, expected)) return false
    if ("/" in expected) {
        return expected.split(Regex("\\s*/\\s*")).any { isNearMiss(input, it) }
    }
    val normInput = Normalizer.normalize(input.trim(), Normalizer.Form.NFC)
    val normExpected = Normalizer.normalize(expected.trim(), Normalizer.Form.NFC)
    if (normInput.isEmpty() || normExpected.isEmpty()) {
        return false
    }
    val s1 = normInput.replaceFirstChar { it.lowercase() }
    val s2 = normExpected.replaceFirstChar { it.lowercase() }
    if (levenshteinDistance(s1, s2) == 1) {
        return true
    }
    return isOneUmlautEquivalentAway(s1, s2)
}

/**
 * Android-free state for the conjugation quiz. Each form is asked with up to
 * four choices in multiple-choice mode, or with typed answers in typed mode ([isTyped]).
 * A wrong answer puts the form back at the end of the queue, so misses come
 * back until answered right. The first-try score only counts forms that were
 * right the first time they were asked and never changes after a retry.
 */
class ConjugationQuiz(
    private val prompts: List<DrillPrompt>,
    private val random: Random = Random(),
    val isTyped: Boolean = false,
    val tense: Tense = Tense.PRESENT,
) {
    private val missCounts = IntArray(prompts.size)
    private val seen = BooleanArray(prompts.size)
    private val queue = ArrayDeque<Int>()
    private var question: QuizQuestion? = null
    private var lastAnswerValue: QuizAnswer? = null

    init {
        queue.addAll(prompts.indices)
        buildQuestion()
    }

    val total: Int get() = prompts.size

    /** The question to answer now, or null when the round is over. */
    val current: QuizQuestion? get() = question

    /** The outcome of the most recent answer, or null when the current question hasn't been answered. */
    val lastAnswer: QuizAnswer? get() = lastAnswerValue

    /** True once the current question has been answered. */
    val isAnswered: Boolean get() = lastAnswerValue != null

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

    /** Records the pick for the current question. In typed mode, delegates to [answerTyped]. */
    fun answer(choice: String): QuizAnswer? {
        if (isTyped) {
            return answerTyped(choice)
        }
        val q = question ?: return null
        if (lastAnswerValue != null) return null
        val expectedOptions = q.answer.split(Regex("\\s*/\\s*"))
        val isCorrect = choice == q.answer || choice.trim() in expectedOptions
        val result = QuizAnswer(isCorrect, q.answer)
        if (!result.correct) missCounts[q.formIndex]++
        lastAnswerValue = result
        return result
    }

    /** Records a typed answer using [checkTypedAnswer]. */
    fun answerTyped(input: String): QuizAnswer? {
        val q = question ?: return null
        if (lastAnswerValue != null) return null
        val result = QuizAnswer(checkTypedAnswer(input, q.answer), q.answer)
        if (!result.correct) missCounts[q.formIndex]++
        lastAnswerValue = result
        return result
    }

    /** Moves to the next question. A form answered wrong goes to the back of the queue. */
    fun advance() {
        val q = question ?: return
        val result = lastAnswerValue ?: return
        queue.removeFirst()
        if (!result.correct) queue.addLast(q.formIndex)
        lastAnswerValue = null
        buildQuestion()
    }

    /** Starts a new round with only the missed forms; returns false when nothing was missed. */
    fun startRetry(): Boolean {
        if (!isRoundFinished) return false
        val missed = missedForms
        if (missed.isEmpty()) return false
        queue.clear()
        queue.addAll(missed)
        lastAnswerValue = null
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
        val choices = if (isTyped) {
            emptyList()
        } else {
            val others = prompts.map { it.form }.distinct().filter { it != prompt.form }.toMutableList()
            others.shuffle(random)
            (others.take(MAX_CHOICES - 1) + prompt.form).toMutableList().apply { shuffle(random) }
        }
        question = QuizQuestion(index, prompt.pronoun, prompt.form, choices)
        seen[index] = true
    }

    private companion object {
        const val MAX_CHOICES = 4
    }
}
