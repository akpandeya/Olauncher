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

    /** Counts words per box for boxes 1 through [learnedBox]. Words outside the range are clamped. */
    fun boxCounts(words: List<Word>): List<Int> {
        val counts = IntArray(learnedBox)
        for (word in words) {
            val idx = (word.box.coerceIn(1, learnedBox)) - 1
            counts[idx]++
        }
        return counts.toList()
    }

    /**
     * Width of each box segment proportional to its count.
     * Empty boxes take 0 width. If total count is 0, all widths are 0.
     * When [totalWidth] is 1f, returned values are width ratios in [0, 1].
     */
    fun segmentWidths(boxCounts: List<Int>, totalWidth: Float = 1f): List<Float> {
        val total = boxCounts.sum()
        if (total <= 0) return List(learnedBox) { 0f }
        return (0 until learnedBox).map { i ->
            val count = boxCounts.getOrElse(i) { 0 }
            if (count <= 0) 0f else (count.toFloat() / total) * totalWidth
        }
    }

    @JvmName("segmentWidthsForWords")
    fun segmentWidths(words: List<Word>, totalWidth: Float = 1f): List<Float> {
        return segmentWidths(boxCounts(words), totalWidth)
    }

    /**
     * Discrete colours from [sandColor] (box 1, new) to [sageColor] (box [learnedBox], learned).
     */
    fun segmentColors(sandColor: Int, sageColor: Int, count: Int = learnedBox): List<Int> {
        if (count <= 1) return listOf(sandColor)
        return (0 until count).map { i ->
            val ratio = i.toFloat() / (count - 1)
            blendArgb(sandColor, sageColor, ratio)
        }
    }

    private fun blendArgb(c1: Int, c2: Int, ratio: Float): Int {
        val r = ratio.coerceIn(0f, 1f)
        val inv = 1f - r
        val a = ((c1 ushr 24 and 0xff) * inv + (c2 ushr 24 and 0xff) * r).toInt()
        val red = ((c1 ushr 16 and 0xff) * inv + (c2 ushr 16 and 0xff) * r).toInt()
        val green = ((c1 ushr 8 and 0xff) * inv + (c2 ushr 8 and 0xff) * r).toInt()
        val blue = ((c1 and 0xff) * inv + (c2 and 0xff) * r).toInt()
        return (a shl 24) or (red shl 16) or (green shl 8) or blue
    }
}
