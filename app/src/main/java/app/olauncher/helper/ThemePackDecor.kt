package app.olauncher.helper

import kotlin.math.PI
import kotlin.math.cos

/**
 * Pure logic for the seasonal home dressing (docs/design/themes/mocks/index.html, `decorHTML`):
 * the decorated families and the animation phase maths the [app.olauncher.ui.HomeDecorView]
 * draws with. Decoration is an app-side presentation choice derived from the pack family —
 * never pack data (docs/design/themes/PACK_FORMAT.md).
 */
object ThemePackDecor {

    const val FAMILY_HALLOWEEN = "halloween"
    const val FAMILY_DIWALI = "diwali"
    const val FAMILY_CHRISTMAS = "christmas"

    /**
     * The decorated family of a pack id, or null for a plain pack. The family is the id prefix
     * before the first `-` (`diwali-dark` -> `diwali`); anything else (Thumbline, Sage, Midnight,
     * Rose) draws nothing.
     */
    fun familyFor(packId: String?): String? {
        if (packId.isNullOrEmpty()) return null
        return when (val prefix = packId.substringBefore('-').lowercase()) {
            FAMILY_HALLOWEEN, FAMILY_DIWALI, FAMILY_CHRISTMAS -> prefix
            else -> null
        }
    }

    /** Frames per second cap of the dressing loop; the motion is slow, so 30 reads as smooth. */
    const val MAX_FPS = 30
    const val FRAME_INTERVAL_MS = 1000L / MAX_FPS

    /**
     * Animation phase of [elapsedS] into a cycle of [periodS] seconds, shifted by [delayS]
     * (negative delays phase-shift forward, like CSS `animation-delay`), as a fraction
     * wrapped into 0 (inclusive) until 1 (exclusive). 0 when the period is not positive.
     */
    fun phase(elapsedS: Float, periodS: Float, delayS: Float = 0f): Float {
        if (periodS <= 0f) return 0f
        // Truncated remainder (sign of the dividend), folded up into 0 until periodS.
        var shifted = (elapsedS + delayS) % periodS
        if (shifted < 0f) shifted += periodS
        return shifted / periodS
    }

    /**
     * Twinkle opacity (`@keyframes twinkle`): 0.2 at the cycle ends, 1 at the midpoint.
     * [p] is a phase from [phase].
     */
    fun twinkleAlpha(p: Float): Float = 0.2f + 0.4f * (1f - cos(2.0 * PI * p).toFloat())

    /**
     * Diya flame height scale (`@keyframes flicker` vertical part): 0.85 at the cycle start,
     * 1.1 at the midpoint, pivoting on the flame base.
     */
    fun flickerScaleY(p: Float): Float = 0.975f - 0.125f * cos(2.0 * PI * p).toFloat()

    /** Diya flame opacity wobble (`@keyframes flicker` alpha part): 1 at the ends, 0.8 mid-cycle. */
    fun flickerAlpha(p: Float): Float = 1f - 0.1f * (1f - cos(2.0 * PI * p).toFloat())

    /**
     * Fraction of an ease-in-out cycle completed, 0 to 1: 0 at the ends, 1 at the midpoint.
     * The CSS keyframe motions (floaty, drift, flicker) are of this shape.
     */
    fun eased(p: Float): Float = 0.5f * (1f - cos(2.0 * PI * p).toFloat())

    /**
     * Vertical bob offset in dp (`@keyframes floaty`): 0 at the ends, [amplitudeDp] up mid-cycle.
     * [p] is a phase from [phase].
     */
    fun bobOffsetDp(p: Float, amplitudeDp: Float): Float = -amplitudeDp * eased(p)

    /**
     * Horizontal drift offset in dp (`@keyframes drift` x): 0 at the ends, [amplitudeDp] towards
     * the left mid-cycle.
     */
    fun driftOffsetXDp(p: Float, amplitudeDp: Float): Float = -amplitudeDp * eased(p)

    /** Vertical drift offset in dp (`@keyframes drift` y): 0 at the ends, [amplitudeDp] down mid-cycle. */
    fun driftOffsetYDp(p: Float, amplitudeDp: Float): Float = amplitudeDp * eased(p)

    /**
     * Fall progress of a snowflake, 0 (just above the top edge) to 1 (just past the bottom edge),
     * wrapping top to bottom. [periodS] is one full crossing; [delayS] spreads the flakes out.
     */
    fun snowProgress(elapsedS: Float, periodS: Float, delayS: Float): Float =
        phase(elapsedS, periodS, delayS)
}
