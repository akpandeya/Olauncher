package app.olauncher.data.store

object Leitner {
    val INTERVALS = listOf(1, 2, 4, 8, 16, 32, 64)

    /** A word at this box or beyond is learned; never hardcode a number instead. */
    val learnedBox: Int = INTERVALS.size

    fun newWordDueDay(today: Long): Long = today

    fun knowIt(word: Word, today: Long): Word {
        return if (word.box >= learnedBox) {
            word.copy(learnedAt = today)
        } else {
            val newBox = word.box + 1
            val newDueDay = today + INTERVALS[newBox - 1]
            word.copy(box = newBox, dueDay = newDueDay)
        }
    }

    /**
     * "Almost": the box is unchanged, but the word comes back at half its
     * current interval, so it is seen again sooner than a full success would.
     */
    fun almost(word: Word, today: Long): Word {
        val interval = INTERVALS[(word.box - 1).coerceIn(0, INTERVALS.lastIndex)]
        return word.copy(dueDay = today + maxOf(1, interval / 2))
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
