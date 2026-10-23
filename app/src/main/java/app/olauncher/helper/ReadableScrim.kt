package app.olauncher.helper

/**
 * The darkening (or lightening, in the light theme) behind text on a photo wallpaper.
 * One rule per screen: home keeps its wash ([HomeWash]), the drawer gets a gradient
 * rising from the bottom, settings and Today cards get a stronger card background.
 * The alpha values here must match `readableCard` and `drawerScrimBottom` in
 * res/values/colors.xml and res/values-night/colors.xml (a test checks this).
 */
object ReadableScrim {
    /** WCAG AA contrast for body text. */
    const val MIN_CONTRAST = 4.5

    /** Card background alpha (0-255) for settings and Today cards. */
    const val CARD_ALPHA = 0xB8

    /** Drawer gradient alpha (0-255) at the bottom edge; it fades to 0 at the top. */
    const val DRAWER_BOTTOM_ALPHA = 0x99

    /** Body text colour alpha in both themes (`inkMuted`), the faintest text on a card. */
    const val HINT_TEXT_ALPHA = 0xB3

    private const val WHITE = 0xFFFFFFFF.toInt()
    private const val BLACK = 0xFF000000.toInt()

    /** The photo that is hardest to read on: white under a dark scrim, black under a light one. */
    fun worstPhoto(scrimIsDark: Boolean): Int = if (scrimIsDark) WHITE else BLACK

    /** [top] drawn over opaque [bottom], as opaque ARGB. */
    fun blend(top: Int, bottom: Int): Int {
        val a = (top ushr 24) / 255.0
        fun ch(shift: Int): Int {
            val t = (top shr shift) and 0xFF
            val b = (bottom shr shift) and 0xFF
            return Math.round(t * a + b * (1 - a)).toInt()
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** WCAG contrast ratio between two opaque colours. */
    fun contrast(a: Int, b: Int): Double {
        val la = HomeWash.relativeLuminance(a)
        val lb = HomeWash.relativeLuminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /**
     * Contrast of hint text on a card of [cardAlpha] (0-255) over the worst-case photo.
     * Dark theme: white text, black card. Light theme: black text, white card.
     */
    fun hintContrastOnCard(cardAlpha: Int, scrimIsDark: Boolean): Double {
        val scrim = if (scrimIsDark) BLACK else WHITE
        val ink = if (scrimIsDark) WHITE else BLACK
        val background = blend((cardAlpha shl 24) or (scrim and 0xFFFFFF), worstPhoto(scrimIsDark))
        val text = blend((HINT_TEXT_ALPHA shl 24) or (ink and 0xFFFFFF), background)
        return contrast(text, background)
    }

    /** The smallest card alpha (0-255) whose hint text reaches [MIN_CONTRAST]; null if none does. */
    fun minCardAlpha(scrimIsDark: Boolean): Int? =
        (0..255).firstOrNull { hintContrastOnCard(it, scrimIsDark) >= MIN_CONTRAST }
}
