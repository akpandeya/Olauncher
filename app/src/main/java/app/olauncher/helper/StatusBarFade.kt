package app.olauncher.helper

import android.view.View
import android.view.WindowInsets

/** The gradient strip that fades scrolling content out under the status bar. */
object StatusBarFade {
    /** Extra height below the status bar, in dp, so the fade ends softly. */
    const val EXTRA_DP = 16

    /** Strip height in px: the status bar plus [EXTRA_DP]. */
    fun heightPx(statusBarTopPx: Int, density: Float): Int =
        statusBarTopPx.coerceAtLeast(0) + Math.round(EXTRA_DP * density)

    /** Sizes this view to the strip height now and whenever the insets change. */
    fun attach(view: View) {
        view.setOnApplyWindowInsetsListener { v, insets ->
            val top = insets.getInsets(WindowInsets.Type.statusBars()).top
            val height = heightPx(top, v.resources.displayMetrics.density)
            if (v.layoutParams.height != height) {
                v.layoutParams = v.layoutParams.also { it.height = height }
            }
            insets
        }
        view.requestApplyInsets()
    }
}
