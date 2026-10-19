package app.olauncher.helper

/**
 * Pure policy for motion and transition durations.
 *
 * When system animators are disabled (e.g. animator duration scale is set to off in developer
 * options), transitions must be instant (duration = 0).
 */
object MotionPolicy {

    const val DRAWER_DURATION_MS: Long = 120L
    const val TODAY_DURATION_MS: Long = 120L
    const val DEFAULT_DURATION_MS: Long = 120L
    const val MAX_DURATION_MS: Long = 150L

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
}
