package app.olauncher.helper

import android.animation.ValueAnimator
import android.content.Context

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
    const val STAGGER_STEP_MS: Long = 30L
    const val PRESS_DURATION_MS: Long = 80L
    const val PRESS_SCALE: Float = 0.98f
    const val FLIP_HALF_DURATION_MS: Long = 60L

    var animationsEnabledSupplier: ((Context) -> Boolean)? = null

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

    /**
     * Checks whether animations are enabled on the device.
     */
    fun isAnimationsEnabled(context: Context): Boolean {
        animationsEnabledSupplier?.let { return it(context) }
        return !context.isEinkDisplay() && !context.isSystemAnimationsDisabled() && ValueAnimator.areAnimatorsEnabled()
    }
}

