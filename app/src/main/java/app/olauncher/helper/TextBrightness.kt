package app.olauncher.helper

import app.olauncher.data.Prefs

/** How much to dim home text on dark themes, so bright text does not glare on light photos. */
object TextBrightness {
    /** Opacity of home text for each brightness setting; full keeps the theme colour as it is. */
    fun opacity(level: Int): Float = when (level) {
        Prefs.BRIGHTNESS_SOFT -> 0.9f
        Prefs.BRIGHTNESS_DIM -> 0.75f
        else -> 1f
    }
}
