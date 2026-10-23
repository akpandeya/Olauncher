package app.olauncher.data.store

/**
 * Pure rules for a review session: grading one word and reading the event log
 * for the summary line. Android-free so it can be unit tested.
 */
object ReviewSessionLogic {

    /** The three grade buttons, in display order. */
    val GRADES = listOf(ReviewEvent.Result.KNEW, ReviewEvent.Result.ALMOST, ReviewEvent.Result.AGAIN)

    /** Applies one grade to [word]. Learned words never reach the queue, but the math stays total. */
    fun grade(word: Word, result: String, today: Long): Word = when (result) {
        ReviewEvent.Result.KNEW -> Leitner.knowIt(word, today)
        ReviewEvent.Result.ALMOST -> Leitner.almost(word, today)
        ReviewEvent.Result.AGAIN -> Leitner.again(word, today)
        else -> word
    }

    /** Words for one session: every due word, oldest due first. */
    fun queue(words: List<Word>, today: Long, limit: Int = Int.MAX_VALUE): List<Word> =
        Leitner.due(words, today, limit)

    data class Summary(val knew: Int, val almost: Int, val again: Int) {
        val total: Int get() = knew + almost + again
    }

    fun summarize(events: List<ReviewEvent>): Summary = Summary(
        knew = events.count { it.result == ReviewEvent.Result.KNEW },
        almost = events.count { it.result == ReviewEvent.Result.ALMOST },
        again = events.count { it.result == ReviewEvent.Result.AGAIN },
    )
}
