package app.olauncher.data.store

object DrillReviewFeed {

    data class Grade(
        val word: Word,
        val result: String,
        val source: String,
        val today: Long,
    )

    /**
     * Maps the first attempts in a drill session to review grades for matching saved words.
     * At most one grade is returned per saved word per session: the first attempt on the first item
     * linked to that word. Right gives [ReviewEvent.Result.KNEW], wrong gives [ReviewEvent.Result.AGAIN].
     * Items not linked to a saved word (table-only) and items for deleted words are ignored.
     */
    fun grades(
        firstAttempts: Map<DrillItemId, Boolean>,
        words: Collection<Word>,
        today: Long,
    ): List<Grade> {
        val wordsById = words.associateBy { it.id }
        val seenWordIds = mutableSetOf<Long>()
        val resultList = mutableListOf<Grade>()

        for ((itemId, isCorrect) in firstAttempts) {
            val wordId = extractWordId(itemId.key) ?: continue
            val word = wordsById[wordId] ?: continue
            if (seenWordIds.add(wordId)) {
                val gradeResult = if (isCorrect) ReviewEvent.Result.KNEW else ReviewEvent.Result.AGAIN
                resultList.add(
                    Grade(
                        word = word,
                        result = gradeResult,
                        source = "drill:${itemId.type.id}",
                        today = today,
                    )
                )
            }
        }

        return resultList
    }

    /**
     * Feeds the list of grades through [ReviewSessionLogic.gradeAndLog].
     */
    fun applyGrades(
        grades: List<Grade>,
        saveWord: (Word) -> Unit,
        events: ReviewEventsStore,
        reviewedAt: Long = System.currentTimeMillis(),
    ): List<ReviewEvent> {
        return grades.map { grade ->
            ReviewSessionLogic.gradeAndLog(
                word = grade.word,
                result = grade.result,
                today = grade.today,
                reviewedAt = reviewedAt,
                saveWord = saveWord,
                events = events,
                source = grade.source,
            )
        }
    }

    internal fun extractWordId(key: String): Long? {
        key.toLongOrNull()?.let { return it }
        val parts = key.split(':')
        if (parts.size >= 2 && parts[0] == "word") {
            parts[1].toLongOrNull()?.let { return it }
        }
        parts[0].toLongOrNull()?.let { return it }
        return null
    }
}
