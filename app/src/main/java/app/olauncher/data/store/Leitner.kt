package app.olauncher.data.store

object Leitner {
    val INTERVALS = listOf(1, 2, 4, 8, 16)

    fun newWordDueDay(today: Long): Long = today

    fun knowIt(word: Word, today: Long): Word {
        return if (word.box >= 5) {
            word.copy(learnedAt = today)
        } else {
            val newBox = word.box + 1
            val newDueDay = today + INTERVALS[newBox - 1]
            word.copy(box = newBox, dueDay = newDueDay)
        }
    }

    fun again(word: Word, today: Long): Word {
        return word.copy(
            box = 1,
            dueDay = today + 1,
            learnedAt = null
        )
    }

    fun due(words: List<Word>, today: Long, limit: Int = Int.MAX_VALUE): List<Word> {
        if (limit <= 0) return emptyList()
        return words
            .filter { it.learnedAt == null && it.dueDay <= today }
            .sortedWith(compareBy<Word> { it.dueDay }.thenBy { it.addedAt })
            .take(limit)
    }
}
