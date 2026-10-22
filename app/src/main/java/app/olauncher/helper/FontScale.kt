package app.olauncher.helper

const val MAX_EFFECTIVE_FONT_SCALE = 1.5f

/** Multiplies the phone's font size by the app's text size scale, capped so home apps stay on screen. */
fun effectiveFontScale(system: Float, app: Float): Float =
    (system * app).coerceAtMost(MAX_EFFECTIVE_FONT_SCALE)
