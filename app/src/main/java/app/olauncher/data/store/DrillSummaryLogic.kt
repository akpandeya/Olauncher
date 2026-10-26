package app.olauncher.data.store

/**
 * An item in the drill round summary list.
 */
data class DrillSummaryItem(
    val id: DrillItemId,
    val prompt: String,
    val answer: String,
    val hint: String? = null,
    val result: DrillResult,
    val learnerAnswer: String? = null,
    val roundIndex: Int = 0,
)

/**
 * Context-free rules for drill round summary:
 * - Sorting: lowest score first (WRONG, then ALMOST, then RIGHT).
 * - Ties keep original round order.
 * - Row prompt formatting with learner answer.
 * - Row correction formatting with answer and hint.
 */
object DrillSummaryLogic {

    /**
     * Sorts items lowest score first:
     * 1. WRONG
     * 2. ALMOST
     * 3. RIGHT
     * Ties keep their original round order.
     *
     * [DrillSummaryItem.result] is derived from the quiz's first attempts, not from retries.
     */
    fun sort(items: List<DrillSummaryItem>): List<DrillSummaryItem> =
        items.sortedWith(
            compareBy(
                { item ->
                    when (item.result) {
                        DrillResult.WRONG -> 0
                        DrillResult.ALMOST -> 1
                        DrillResult.RIGHT -> 2
                    }
                },
                { it.roundIndex },
            )
        )

    /**
     * Formats prompt text. Appends learner answer in parentheses when wrong or almost.
     */
    fun formatPrompt(prompt: String, result: DrillResult, learnerAnswer: String?): String {
        return if ((result == DrillResult.WRONG || result == DrillResult.ALMOST) && !learnerAnswer.isNullOrBlank()) {
            "$prompt ($learnerAnswer)"
        } else {
            prompt
        }
    }

    /**
     * Formats the correction line under a red (wrong) item:
     * Answer, followed by one-line rule/hint if distinct.
     *
     * Returns an empty string when the correction would repeat the prompt text
     * (word-order rows show the full sentence as the prompt), in which case only
     * the distinguishing hint is shown.
     */
    fun formatCorrection(prompt: String, answer: String, hint: String?): String {
        val trimmedPrompt = prompt.trim()
        // Word-order rows append the learner answer in parentheses; strip a trailing
        // annotation so a prompt that repeats the answer is still recognised as one.
        val basePrompt = Regex("""\s*\([^()]*\)$""").replace(trimmedPrompt, "").trim()
        val trimmedAnswer = answer.trim()
        val trimmedHint = hint?.trim().orEmpty()
        val promptIsAnswer = trimmedAnswer.equals(basePrompt, ignoreCase = true) ||
            trimmedAnswer.equals(trimmedPrompt, ignoreCase = true)
        return when {
            promptIsAnswer && trimmedHint.isEmpty() -> ""
            promptIsAnswer -> trimmedHint
            trimmedHint.isEmpty() || trimmedHint.equals(trimmedAnswer, ignoreCase = true) -> trimmedAnswer
            else -> "$trimmedAnswer · $trimmedHint"
        }
    }

    /**
     * Computes the header totals from summary items: right, almost, and wrong counts.
     * The numbers match the grouped sort output because they are derived from the
     * same items that are sorted and rendered as rows.
     */
    fun counts(items: List<DrillSummaryItem>): DrillSummaryCounts {
        var right = 0
        var almost = 0
        var wrong = 0
        for (item in items) {
            when (item.result) {
                DrillResult.RIGHT -> right++
                DrillResult.ALMOST -> almost++
                DrillResult.WRONG -> wrong++
            }
        }
        return DrillSummaryCounts(right = right, almost = almost, wrong = wrong)
    }
}

/**
 * Header totals for a drill round summary, matching the grouped rows shown below them.
 */
data class DrillSummaryCounts(
    val right: Int,
    val almost: Int,
    val wrong: Int,
)
