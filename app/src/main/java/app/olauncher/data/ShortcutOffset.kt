package app.olauncher.data

object ShortcutOffset {
    const val MIN_STEP = 0
    const val MAX_STEP = 6
    const val DEFAULT_STEP = 2
    const val BASE_MARGIN_DP = 48
    const val STEP_DP = 24
    const val SIDE_INSET_DP = 16

    fun clamp(step: Int): Int = step.coerceIn(MIN_STEP, MAX_STEP)

    fun bottomMarginDp(step: Int): Int = BASE_MARGIN_DP + clamp(step) * STEP_DP

    fun format(step: Int): String = if (step > 0) "+$step" else "$step"
}
