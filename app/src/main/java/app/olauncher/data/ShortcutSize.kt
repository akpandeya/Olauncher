package app.olauncher.data

object ShortcutSize {
    const val SMALL = 0
    const val MEDIUM = 1
    const val LARGE = 2

    const val DEFAULT = MEDIUM
    const val DEFAULT_STEP = MEDIUM
    const val MIN = SMALL
    const val MAX = LARGE

    const val VIEW_SIZE_SMALL_DP = 40
    const val VIEW_SIZE_MEDIUM_DP = 48
    const val VIEW_SIZE_LARGE_DP = 56

    const val COLUMN_ALLOWANCE_EXTRA_DP = 8

    fun clamp(step: Int): Int = step.coerceIn(MIN, MAX)

    fun viewSizeDp(step: Int): Int = when (clamp(step)) {
        SMALL -> VIEW_SIZE_SMALL_DP
        MEDIUM -> VIEW_SIZE_MEDIUM_DP
        LARGE -> VIEW_SIZE_LARGE_DP
        else -> VIEW_SIZE_MEDIUM_DP
    }

    fun columnAllowanceDp(step: Int): Int = viewSizeDp(step) + COLUMN_ALLOWANCE_EXTRA_DP
}
