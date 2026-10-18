package app.olauncher.data

object SlotCount {
    const val MIN = 3
    const val MAX = 12
    const val DEFAULT = 6

    fun clamp(v: Int): Int = v.coerceIn(MIN, MAX)

    fun fitting(requested: Int, columnHeightDp: Int, viewSizeDp: Int, bottomMarginDp: Int): Int {
        if (viewSizeDp <= 0) return MIN
        val fit = (columnHeightDp - 120 - bottomMarginDp) / viewSizeDp
        return fit.coerceAtMost(requested).coerceAtLeast(MIN)
    }
}
