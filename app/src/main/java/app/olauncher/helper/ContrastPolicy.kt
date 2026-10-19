package app.olauncher.helper

/**
 * How home text should be drawn over the current wallpaper.
 * [color] is null when the theme colour is kept (always, for now).
 * [shadowRadiusDp] 0 means "leave the theme's own shadow alone".
 */
data class TextTreatment(
    val color: Int?,
    val shadowRadiusDp: Int,
    val shadowAlpha: Float,
)

object ContrastPolicy {
    const val STRONG_SHADOW_RADIUS_DP = 6
    const val STRONG_SHADOW_ALPHA = 0.6f

    val THEME_DEFAULT = TextTreatment(color = null, shadowRadiusDp = 0, shadowAlpha = 0f)
    val STRONG_SHADOW = TextTreatment(null, STRONG_SHADOW_RADIUS_DP, STRONG_SHADOW_ALPHA)

    /**
     * @param supportsDarkText wallpaper is light (HINT_SUPPORTS_DARK_TEXT); null when unknown.
     * @param themeIsDark theme draws light text.
     * Theme text that clashes with the wallpaper keeps its colour but gets a strong shadow.
     */
    fun decide(supportsDarkText: Boolean?, themeIsDark: Boolean): TextTreatment {
        if (supportsDarkText == null) return THEME_DEFAULT
        val wallpaperIsLight = supportsDarkText
        // Light text on a light wallpaper, or dark text on a dark wallpaper.
        val clashes = themeIsDark == wallpaperIsLight
        return if (clashes) STRONG_SHADOW else THEME_DEFAULT
    }
}
