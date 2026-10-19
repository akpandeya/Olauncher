package app.olauncher.wellbeing

/** Screen time as a gentle cue: home text dims slightly once the daily target is reached. */
object ScreenTimeCue {
    const val STYLE_NUMBER = 0
    const val STYLE_CUE = 1

    const val DEFAULT_TARGET_MIN = 120
    val TARGET_CHOICES_MIN = listOf(60, 120, 180, 240)

    const val ALPHA_NORMAL = 1.0f
    const val ALPHA_DIMMED = 0.7f

    fun alpha(usedMin: Int, targetMin: Int): Float {
        if (targetMin <= 0) return ALPHA_NORMAL
        return if (usedMin >= targetMin) ALPHA_DIMMED else ALPHA_NORMAL
    }
}
