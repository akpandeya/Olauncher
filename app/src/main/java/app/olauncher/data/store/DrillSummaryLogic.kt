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
     */
    fun formatCorrection(answer: String, hint: String?): String {
        val trimmedAnswer = answer.trim()
        val trimmedHint = hint?.trim().orEmpty()
        return if (trimmedHint.isEmpty() || trimmedHint.equals(trimmedAnswer, ignoreCase = true)) {
            trimmedAnswer
        } else {
            "$trimmedAnswer · $trimmedHint"
        }
    }
}
