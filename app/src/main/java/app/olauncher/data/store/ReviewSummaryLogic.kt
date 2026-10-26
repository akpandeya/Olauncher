package app.olauncher.data.store

/**
 * An item in the review session summary list.
 */
data class ReviewSummaryItem(
    val word: Word,
    val result: String, // ReviewEvent.Result.AGAIN, ALMOST, KNEW
    val sessionIndex: Int = 0,
)

/**
 * Context-free rules for review session summary:
 * - Sorting: lowest score first (AGAIN red, then ALMOST amber, then KNEW green).
 * - Ties keep original session order.
 * - Correction formatting for AGAIN items.
 */
object ReviewSummaryLogic {

    /**
     * Sorts review items lowest score first:
     * 1. AGAIN (red cross)
     * 2. ALMOST (amber dot)
     * 3. KNEW (green tick)
     * Ties keep their original session order.
     */
    fun sort(items: List<ReviewSummaryItem>): List<ReviewSummaryItem> =
        items.sortedWith(
            compareBy(
                { item ->
                    when (item.result) {
                        ReviewEvent.Result.AGAIN -> 0
                        ReviewEvent.Result.ALMOST -> 1
                        ReviewEvent.Result.KNEW -> 2
                        else -> 3
                    }
                },
                { it.sessionIndex },
            )
        )

    /**
     * Correction line under a red (AGAIN) review item:
     * Word meaning (and context/example if available and distinct).
     */
    fun formatCorrection(meaning: String, context: String = ""): String {
        val trimmedMeaning = meaning.trim()
        val trimmedContext = context.trim()
        return when {
            trimmedMeaning.isNotEmpty() && trimmedContext.isNotEmpty() -> "$trimmedMeaning · $trimmedContext"
            trimmedMeaning.isNotEmpty() -> trimmedMeaning
            else -> trimmedContext
        }
    }
}
