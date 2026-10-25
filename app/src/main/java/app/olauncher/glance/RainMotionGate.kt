package app.olauncher.glance

import app.olauncher.helper.WeatherLook
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pure gate deciding whether the rain layer on the weather card should animate
 * or hold a single still frame.
 *
 * Context-free and safe for JVM unit tests.
 */
object RainMotionGate {

    /**
     * Rain moves only when all three gates are open:
     * 1. The forecast is a rain look (LIGHT_RAIN, HEAVY_RAIN or STORM)
     * 2. The card is visible on screen
     * 3. System animators are enabled
     *
     * In any other state the card holds a still frame (not an empty card).
     */
    fun shouldAnimate(
        isRainLook: Boolean,
        cardVisible: Boolean,
        animatorsEnabled: Boolean,
    ): Boolean = isRainLook && cardVisible && animatorsEnabled

    fun shouldAnimate(
        look: WeatherLook?,
        cardVisible: Boolean,
        animatorsEnabled: Boolean,
    ): Boolean = shouldAnimate(
        isRainLook = isRainLook(look),
        cardVisible = cardVisible,
        animatorsEnabled = animatorsEnabled,
    )

    fun isRainLook(look: WeatherLook?): Boolean = when (look) {
        WeatherLook.LIGHT_RAIN,
        WeatherLook.HEAVY_RAIN,
        WeatherLook.STORM -> true
        else -> false
    }

    /**
     * Deterministic FNV-1a seed from a string.
     * Matches the JavaScript mock's `seedFor` (lines 936–940 of today-weather.html).
     */
    fun seedFor(text: String): Long {
        var h = 0x811c9dc5L
        for (i in 0 until text.length) {
            h = h xor text[i].code.toLong()
            h = (h * 0x01000193L) and 0xFFFFFFFFL
        }
        return h
    }

    /**
     * Builds a deterministic seed for a look and card dimensions in dp.
     * Matches mock's `seedFor('rain:' + look + ':' + w + 'x' + h)`.
     */
    fun seedFor(look: WeatherLook, widthDp: Float, heightDp: Float): Long {
        val lookKey = when (look) {
            WeatherLook.LIGHT_RAIN -> "light"
            WeatherLook.HEAVY_RAIN -> "heavy"
            WeatherLook.STORM -> "storm"
            else -> look.name.lowercase(Locale.ROOT)
        }
        return seedFor("rain:$lookKey:${widthDp.roundToInt()}x${heightDp.roundToInt()}")
    }
}
