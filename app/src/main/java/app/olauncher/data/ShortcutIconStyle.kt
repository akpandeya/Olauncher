package app.olauncher.data

import kotlin.math.roundToInt

object ShortcutIconStyle {
    const val ORIGINAL = 0
    const val MONOCHROME = 1
    const val TEXT_COLOUR = 2

    const val DEFAULT = ORIGINAL
    const val MIN = ORIGINAL
    const val MAX = TEXT_COLOUR

    const val FALLBACK_ALPHA = 0.85f
    val FALLBACK_ALPHA_INT: Int = (255 * FALLBACK_ALPHA).roundToInt() // 217

    enum class Treatment {
        ORIGINAL,
        MONOCHROME_LAYER,
        FALLBACK_GRAYSCALE,
        FALLBACK_GRAYSCALE_ALPHA,
    }

    fun clamp(style: Int): Int = style.coerceIn(MIN, MAX)

    fun resolveTreatment(style: Int, hasMonochromeLayer: Boolean): Treatment {
        return when (clamp(style)) {
            ORIGINAL -> Treatment.ORIGINAL
            MONOCHROME -> if (hasMonochromeLayer) Treatment.MONOCHROME_LAYER else Treatment.FALLBACK_GRAYSCALE
            TEXT_COLOUR -> if (hasMonochromeLayer) Treatment.MONOCHROME_LAYER else Treatment.FALLBACK_GRAYSCALE_ALPHA
            else -> Treatment.ORIGINAL
        }
    }

    fun alphaForTreatment(treatment: Treatment): Int {
        return when (treatment) {
            Treatment.FALLBACK_GRAYSCALE_ALPHA -> FALLBACK_ALPHA_INT
            else -> 255
        }
    }
}
