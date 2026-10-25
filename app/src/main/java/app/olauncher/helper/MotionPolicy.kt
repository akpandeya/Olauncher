package app.olauncher.helper

/**
 * Pure, Context-free policy for motion and transition durations: constants, [durationMs] and
 * [staggerDelayMs]. Whether animations are on at all is decided by [SystemMotion] (it needs a
 * Context); callers pass that answer in as `animatorsEnabled`. When it is false, transitions
 * must be instant (duration = 0).
 */
object MotionPolicy {

    const val DRAWER_DURATION_MS: Long = 120L
    const val TODAY_DURATION_MS: Long = 120L
    const val DEFAULT_DURATION_MS: Long = 120L
    const val MAX_DURATION_MS: Long = 150L
    const val STAGGER_STEP_MS: Long = 30L
    const val PRESS_DURATION_MS: Long = 80L
    const val PRESS_SCALE: Float = 0.98f
    const val FLIP_HALF_DURATION_MS: Long = 60L
    const val FISHEYE_DURATION_MS: Long = 80L

    /**
     * Returns the duration in milliseconds to use for a transition.
     * When animators are disabled, returns 0L so transitions are instant.
     */
    fun durationMs(baseMs: Long, animatorsEnabled: Boolean): Long {
        if (!animatorsEnabled) return 0L
        return baseMs.coerceAtLeast(0L)
    }

    fun durationMs(baseMs: Int, animatorsEnabled: Boolean): Long =
        durationMs(baseMs.toLong(), animatorsEnabled)

    /**
     * Delay in milliseconds for staggered item entry at [index] (0-based).
     * Returns 0L when [animatorsEnabled] is false or [index] <= 0.
     * Each step is clamped to [MAX_DURATION_MS].
     */
    fun staggerDelayMs(
        index: Int,
        animatorsEnabled: Boolean,
        stepMs: Long = STAGGER_STEP_MS,
    ): Long {
        if (!animatorsEnabled || index <= 0) return 0L
        val clampedStep = stepMs.coerceIn(0L, MAX_DURATION_MS)
        return index.toLong() * clampedStep
    }
}
