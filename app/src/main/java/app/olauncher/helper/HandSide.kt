package app.olauncher.helper

import android.view.Gravity

/**
 * Result layout of [HandSide.swap], specifying the hand-side configuration
 * for quick icon shortcuts and home app labels.
 */
data class HandLayout(
    val shortcutSideStart: Boolean,
    val alignment: Int,
)

/**
 * Pure helper for switching one-handed layout orientation.
 *
 * Flips the shortcut icon column (left <-> right) and mirrors
 * the home app text alignment (left <-> right, while centre remains unchanged).
 */
object HandSide {

    /**
     * Computes the mirrored [HandLayout].
     *
     * @param shortcutSideStart true if shortcuts are on start/left, false if on end/right.
     * @param alignment home app alignment (e.g. [Gravity.START], [Gravity.END], [Gravity.CENTER]).
     * @return [HandLayout] with inverted [shortcutSideStart] and mirrored [alignment].
     */
    fun swap(shortcutSideStart: Boolean, alignment: Int): HandLayout {
        val mirroredAlignment = when (alignment) {
            Gravity.START, Gravity.LEFT -> Gravity.END
            Gravity.END, Gravity.RIGHT -> Gravity.START
            else -> alignment
        }
        return HandLayout(
            shortcutSideStart = !shortcutSideStart,
            alignment = mirroredAlignment,
        )
    }
}
