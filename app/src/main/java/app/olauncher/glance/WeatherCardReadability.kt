package app.olauncher.glance

import app.olauncher.helper.HomeWash
import app.olauncher.helper.ReadableScrim
import app.olauncher.helper.WeatherLook
import kotlin.math.pow

/**
 * Named visual styling tokens for a weather look card in a given theme.
 *
 * All colours are 32-bit ARGB ints.
 */
data class WeatherCardStyle(
    val sky1: Int,
    val sky2: Int,
    val ink: Int,
    val ink2: Int,
    val halo: Int,
    val rainFar: Int,
    val rainMid: Int,
    val rainNear: Int,
    val iconsDark: Boolean,
) {
    val rainColours: Triple<Int, Int, Int>
        get() = Triple(rainFar, rainMid, rainNear)

    val rainColors: Triple<Int, Int, Int>
        get() = Triple(rainFar, rainMid, rainNear)
}

/** Single overlay layer with RGB [0..255] and alpha [0..1]. */
data class OverlayLayer(
    val r: Double,
    val g: Double,
    val b: Double,
    val a: Double,
)

/** Canvas pixel composited before keep-out, premultiplied. */
data class CanvasPixel(
    val pr: Double,
    val pg: Double,
    val pb: Double,
    val a: Double,
)

/** Background colour point resulting from sky stops and overlay compositing. */
data class BackgroundPoint(
    val name: String,
    val r: Double,
    val g: Double,
    val b: Double,
) {
    val rgb: DoubleArray
        get() = doubleArrayOf(r, g, b)

    val argb: Int
        get() {
            val ir = Math.round(r).toInt().coerceIn(0, 255)
            val ig = Math.round(g).toInt().coerceIn(0, 255)
            val ib = Math.round(b).toInt().coerceIn(0, 255)
            return (0xFF shl 24) or (ir shl 16) or (ig shl 8) or ib
        }
}

/** Contrast evaluation result for main and small text. */
data class ContrastResult(
    val main: Double,
    val small: Double,
    val worstBy: String,
    val pass: Boolean,
)

/**
 * Readability and style table for Weather cards on Home and Today.
 * Pure Kotlin, Context-free.
 *
 * Source of truth: docs/design/today-weather.html lines 38–105, 180–193, and 998–1082.
 */
object WeatherCardReadability {

    const val KEEP_OUT: Double = 0.85
    const val KEEP_OUT_PEAK: Double = 0.87
    const val KEEP_OUT_PAD: Int = 14
    const val KEEP_OUT_SIGMA: Int = 6
    const val MIN_CONTRAST: Double = 4.5

    private const val RAIN_PALE_FAR = 0x6696B9D0.toInt()
    private const val RAIN_PALE_MID = 0xB396B9D0.toInt()
    private const val RAIN_PALE_NEAR = 0xE6CDE2F0.toInt()

    private const val RAIN_INK_FAR = 0x4D2E6585.toInt()
    private const val RAIN_INK_MID = 0x8C2E6585.toInt()
    private const val RAIN_INK_NEAR = 0xD9215470.toInt()

    private fun hex(rgb: Int): Int = 0xFF000000.toInt() or (rgb and 0xFFFFFF)

    private fun rgba(r: Int, g: Int, b: Int, a: Double): Int {
        val alpha = Math.round(a * 255.0).toInt().coerceIn(0, 255)
        return (alpha shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
    }

    private val LIGHT_STYLES: Map<WeatherLook, WeatherCardStyle> = mapOf(
        WeatherLook.CLEAR to WeatherCardStyle(
            sky1 = hex(0xCFE2F6),
            sky2 = hex(0xF4E7CF),
            ink = hex(0x0F141B),
            ink2 = hex(0x36404F),
            halo = rgba(255, 255, 255, 0.55),
            rainFar = RAIN_INK_FAR,
            rainMid = RAIN_INK_MID,
            rainNear = RAIN_INK_NEAR,
            iconsDark = false,
        ),
        WeatherLook.PARTLY to WeatherCardStyle(
            sky1 = hex(0xD3E0EE),
            sky2 = hex(0xE8E5DC),
            ink = hex(0x0F141B),
            ink2 = hex(0x36404F),
            halo = rgba(255, 255, 255, 0.55),
            rainFar = RAIN_INK_FAR,
            rainMid = RAIN_INK_MID,
            rainNear = RAIN_INK_NEAR,
            iconsDark = false,
        ),
        WeatherLook.OVERCAST to WeatherCardStyle(
            sky1 = hex(0xD0D5DC),
            sky2 = hex(0xDFE2E6),
            ink = hex(0x0F141B),
            ink2 = hex(0x36404F),
            halo = rgba(255, 255, 255, 0.55),
            rainFar = RAIN_INK_FAR,
            rainMid = RAIN_INK_MID,
            rainNear = RAIN_INK_NEAR,
            iconsDark = false,
        ),
        WeatherLook.LIGHT_RAIN to WeatherCardStyle(
            sky1 = hex(0xC3CCD7),
            sky2 = hex(0xD2D9E1),
            ink = hex(0x0F141B),
            ink2 = hex(0x333D4B),
            halo = rgba(255, 255, 255, 0.50),
            rainFar = RAIN_INK_FAR,
            rainMid = RAIN_INK_MID,
            rainNear = RAIN_INK_NEAR,
            iconsDark = false,
        ),
        WeatherLook.HEAVY_RAIN to WeatherCardStyle(
            sky1 = hex(0xAEB8C5),
            sky2 = hex(0xC0C8D2),
            ink = hex(0x0B0F15),
            ink2 = hex(0x262F3B),
            halo = rgba(255, 255, 255, 0.45),
            rainFar = RAIN_INK_FAR,
            rainMid = RAIN_INK_MID,
            rainNear = RAIN_INK_NEAR,
            iconsDark = false,
        ),
        WeatherLook.STORM to WeatherCardStyle(
            sky1 = hex(0x0D1222),
            sky2 = hex(0x1A1D2E),
            ink = hex(0xF4F7FB),
            ink2 = hex(0xCDD6E2),
            halo = rgba(4, 8, 18, 0.60),
            rainFar = RAIN_PALE_FAR,
            rainMid = RAIN_PALE_MID,
            rainNear = RAIN_PALE_NEAR,
            iconsDark = true,
        ),
        WeatherLook.SNOW to WeatherCardStyle(
            sky1 = hex(0xB2BFCF),
            sky2 = hex(0xC8D2DE),
            ink = hex(0x0B0F15),
            ink2 = hex(0x262F3B),
            halo = rgba(255, 255, 255, 0.40),
            rainFar = RAIN_INK_FAR,
            rainMid = RAIN_INK_MID,
            rainNear = RAIN_INK_NEAR,
            iconsDark = false,
        ),
        WeatherLook.FOG to WeatherCardStyle(
            sky1 = hex(0xCCD1D6),
            sky2 = hex(0xDCDFE2),
            ink = hex(0x0F141B),
            ink2 = hex(0x36404F),
            halo = rgba(255, 255, 255, 0.50),
            rainFar = RAIN_INK_FAR,
            rainMid = RAIN_INK_MID,
            rainNear = RAIN_INK_NEAR,
            iconsDark = false,
        ),
        WeatherLook.CLEAR_NIGHT to WeatherCardStyle(
            sky1 = hex(0x0A1022),
            sky2 = hex(0x161E3C),
            ink = hex(0xF4F7FB),
            ink2 = hex(0xCDD6E2),
            halo = rgba(4, 8, 18, 0.60),
            rainFar = RAIN_PALE_FAR,
            rainMid = RAIN_PALE_MID,
            rainNear = RAIN_PALE_NEAR,
            iconsDark = true,
        ),
    )

    private val DARK_STYLES: Map<WeatherLook, WeatherCardStyle> = mapOf(
        WeatherLook.CLEAR to WeatherCardStyle(
            sky1 = hex(0x143A63),
            sky2 = hex(0x1C4874),
            ink = hex(0xF4F7FB),
            ink2 = hex(0xCDD6E2),
            halo = rgba(4, 10, 24, 0.55),
            rainFar = RAIN_PALE_FAR,
            rainMid = RAIN_PALE_MID,
            rainNear = RAIN_PALE_NEAR,
            iconsDark = true,
        ),
        WeatherLook.PARTLY to WeatherCardStyle(
            sky1 = hex(0x1B3758),
            sky2 = hex(0x25405D),
            ink = hex(0xF4F7FB),
            ink2 = hex(0xCDD6E2),
            halo = rgba(4, 10, 24, 0.55),
            rainFar = RAIN_PALE_FAR,
            rainMid = RAIN_PALE_MID,
            rainNear = RAIN_PALE_NEAR,
            iconsDark = true,
        ),
        WeatherLook.OVERCAST to WeatherCardStyle(
            sky1 = hex(0x2F3640),
            sky2 = hex(0x3B434E),
            ink = hex(0xF4F7FB),
            ink2 = hex(0xCDD6E2),
            halo = rgba(4, 10, 24, 0.55),
            rainFar = RAIN_PALE_FAR,
            rainMid = RAIN_PALE_MID,
            rainNear = RAIN_PALE_NEAR,
            iconsDark = true,
        ),
        WeatherLook.LIGHT_RAIN to WeatherCardStyle(
            sky1 = hex(0x243244),
            sky2 = hex(0x2E3E51),
            ink = hex(0xF4F7FB),
            ink2 = hex(0xCDD6E2),
            halo = rgba(4, 10, 24, 0.55),
            rainFar = RAIN_PALE_FAR,
            rainMid = RAIN_PALE_MID,
            rainNear = RAIN_PALE_NEAR,
            iconsDark = true,
        ),
        WeatherLook.HEAVY_RAIN to WeatherCardStyle(
            sky1 = hex(0x101828),
            sky2 = hex(0x182133),
            ink = hex(0xF4F7FB),
            ink2 = hex(0xCDD6E2),
            halo = rgba(4, 10, 24, 0.60),
            rainFar = RAIN_PALE_FAR,
            rainMid = RAIN_PALE_MID,
            rainNear = RAIN_PALE_NEAR,
            iconsDark = true,
        ),
        WeatherLook.STORM to WeatherCardStyle(
            sky1 = hex(0x0A0F1D),
            sky2 = hex(0x171A2A),
            ink = hex(0xF4F7FB),
            ink2 = hex(0xCDD6E2),
            halo = rgba(4, 8, 18, 0.60),
            rainFar = RAIN_PALE_FAR,
            rainMid = RAIN_PALE_MID,
            rainNear = RAIN_PALE_NEAR,
            iconsDark = true,
        ),
        WeatherLook.SNOW to WeatherCardStyle(
            sky1 = hex(0x26313F),
            sky2 = hex(0x35404E),
            ink = hex(0xF4F7FB),
            ink2 = hex(0xCDD6E2),
            halo = rgba(4, 10, 24, 0.60),
            rainFar = RAIN_PALE_FAR,
            rainMid = RAIN_PALE_MID,
            rainNear = RAIN_PALE_NEAR,
            iconsDark = true,
        ),
        WeatherLook.FOG to WeatherCardStyle(
            sky1 = hex(0x2C3238),
            sky2 = hex(0x383F46),
            ink = hex(0xF4F7FB),
            ink2 = hex(0xCDD6E2),
            halo = rgba(4, 10, 24, 0.60),
            rainFar = RAIN_PALE_FAR,
            rainMid = RAIN_PALE_MID,
            rainNear = RAIN_PALE_NEAR,
            iconsDark = true,
        ),
        WeatherLook.CLEAR_NIGHT to WeatherCardStyle(
            sky1 = hex(0x060A1A),
            sky2 = hex(0x111833),
            ink = hex(0xF4F7FB),
            ink2 = hex(0xCDD6E2),
            halo = rgba(4, 8, 18, 0.60),
            rainFar = RAIN_PALE_FAR,
            rainMid = RAIN_PALE_MID,
            rainNear = RAIN_PALE_NEAR,
            iconsDark = true,
        ),
    )

    val OVERLAYS: Map<String, List<OverlayLayer>> = mapOf(
        "sun" to listOf(
            OverlayLayer(255.0, 210.0, 122.0, 0.6),
            OverlayLayer(255.0, 210.0, 122.0, 0.08),
        ),
        "cloud" to listOf(
            OverlayLayer(242.0, 244.0, 247.0, 0.42),
            OverlayLayer(242.0, 244.0, 247.0, 0.42),
        ),
        "cloudGrey" to listOf(
            OverlayLayer(168.0, 176.0, 190.0, 0.42),
            OverlayLayer(168.0, 176.0, 190.0, 0.42),
        ),
        "fog" to listOf(
            OverlayLayer(230.0, 233.0, 238.0, 0.16),
            OverlayLayer(230.0, 233.0, 238.0, 0.32),
            OverlayLayer(230.0, 233.0, 238.0, 0.32),
        ),
        "rainInk" to listOf(
            OverlayLayer(46.0, 101.0, 133.0, 0.30 * RAIN_OPACITY),
            OverlayLayer(46.0, 101.0, 133.0, 0.55 * RAIN_OPACITY),
            OverlayLayer(33.0, 84.0, 112.0, 0.85 * RAIN_OPACITY),
            OverlayLayer(33.0, 84.0, 112.0, 0.85 * 0.9 * RAIN_OPACITY),
        ),
        "rainPale" to listOf(
            OverlayLayer(150.0, 185.0, 208.0, 0.40 * RAIN_OPACITY),
            OverlayLayer(150.0, 185.0, 208.0, 0.70 * RAIN_OPACITY),
            OverlayLayer(205.0, 226.0, 240.0, 0.90 * RAIN_OPACITY),
            OverlayLayer(205.0, 226.0, 240.0, 0.90 * 0.9 * RAIN_OPACITY),
        ),
        "flake" to listOf(
            OverlayLayer(255.0, 255.0, 255.0, 0.2),
            OverlayLayer(255.0, 255.0, 255.0, 0.9),
        ),
        "flash" to listOf(
            OverlayLayer(232.0, 240.0, 255.0, 0.38),
            OverlayLayer(255.0, 255.0, 255.0, 0.9),
        ),
        "star" to listOf(
            OverlayLayer(220.0, 230.0, 255.0, 0.95),
        ),
    )

    val LOOK_OVERLAYS_BY_NAME: Map<String, List<String>> = mapOf(
        "clear" to listOf("sun"),
        "partly" to listOf("sun", "cloud", "sun+cloud"),
        "overcast" to listOf("cloudGrey"),
        "light" to listOf("rainInk", "rainPale"),
        "heavy" to listOf("rainInk", "rainPale"),
        "storm" to listOf("rainInk", "rainPale", "flash", "rainPale+flash"),
        "snow" to listOf("flake"),
        "fog" to listOf("fog"),
        "night" to listOf("star"),
    )

    val LOOK_OVERLAYS: Map<WeatherLook, List<String>> = mapOf(
        WeatherLook.CLEAR to LOOK_OVERLAYS_BY_NAME.getValue("clear"),
        WeatherLook.PARTLY to LOOK_OVERLAYS_BY_NAME.getValue("partly"),
        WeatherLook.OVERCAST to LOOK_OVERLAYS_BY_NAME.getValue("overcast"),
        WeatherLook.LIGHT_RAIN to LOOK_OVERLAYS_BY_NAME.getValue("light"),
        WeatherLook.HEAVY_RAIN to LOOK_OVERLAYS_BY_NAME.getValue("heavy"),
        WeatherLook.STORM to LOOK_OVERLAYS_BY_NAME.getValue("storm"),
        WeatherLook.SNOW to LOOK_OVERLAYS_BY_NAME.getValue("snow"),
        WeatherLook.FOG to LOOK_OVERLAYS_BY_NAME.getValue("fog"),
        WeatherLook.CLEAR_NIGHT to LOOK_OVERLAYS_BY_NAME.getValue("night"),
    )

    /** Returns the style tokens for [look] in [themeIsDark] theme. */
    fun styleFor(look: WeatherLook, themeIsDark: Boolean): WeatherCardStyle =
        (if (themeIsDark) DARK_STYLES else LIGHT_STYLES).getValue(look)

    fun style(look: WeatherLook, themeIsDark: Boolean): WeatherCardStyle =
        styleFor(look, themeIsDark)

    operator fun get(look: WeatherLook, themeIsDark: Boolean): WeatherCardStyle =
        styleFor(look, themeIsDark)

    operator fun invoke(look: WeatherLook, themeIsDark: Boolean): WeatherCardStyle =
        styleFor(look, themeIsDark)

    fun stackFor(name: String): List<OverlayLayer> =
        name.split("+").flatMap { OVERLAYS[it] ?: emptyList() }

    fun canvasPixel(stack: List<OverlayLayer>, keepOut: Double = KEEP_OUT): CanvasPixel {
        var r = 0.0
        var g = 0.0
        var b = 0.0
        var a = 0.0
        for (o in stack) {
            r = o.r * o.a + r * (1.0 - o.a)
            g = o.g * o.a + g * (1.0 - o.a)
            b = o.b * o.a + b * (1.0 - o.a)
            a = o.a + a * (1.0 - o.a)
        }
        val k = 1.0 - keepOut
        return CanvasPixel(pr = r * k, pg = g * k, pb = b * k, a = a * k)
    }

    fun over(px: CanvasPixel, bg: DoubleArray): DoubleArray =
        doubleArrayOf(
            px.pr + bg[0] * (1.0 - px.a),
            px.pg + bg[1] * (1.0 - px.a),
            px.pb + bg[2] * (1.0 - px.a),
        )

    /** WCAG relative luminance (0..1) for RGB channels [0..255]. */
    fun relativeLuminance(r: Double, g: Double, b: Double): Double {
        fun ch(v: Double): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b)
    }

    /** Relative luminance from ARGB int using the shared helper. */
    fun relativeLuminance(argb: Int): Double = HomeWash.relativeLuminance(argb)

    /** WCAG contrast ratio between two relative luminance values. */
    fun contrast(la: Double, lb: Double): Double =
        (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)

    /** WCAG contrast between two RGB colour vectors [0..255]. */
    fun contrast(rgb1: DoubleArray, rgb2: DoubleArray): Double {
        val la = relativeLuminance(rgb1[0], rgb1[1], rgb1[2])
        val lb = relativeLuminance(rgb2[0], rgb2[1], rgb2[2])
        return contrast(la, lb)
    }

    /** WCAG contrast between two ARGB Ints, reusing [ReadableScrim.contrast]. */
    fun contrast(c1: Int, c2: Int): Double = ReadableScrim.contrast(c1, c2)

    /**
     * Plain worst-case background points for a look and style:
     * sky1, sky mid, sky2, each alone and with every overlay stack for that look after keep-out.
     * No pixels are read from any canvas or bitmap.
     */
    fun worstCaseBackgroundPoints(
        look: WeatherLook,
        style: WeatherCardStyle,
        keepOut: Double = KEEP_OUT,
    ): List<BackgroundPoint> {
        val s1 = doubleArrayOf(
            ((style.sky1 shr 16) and 0xFF).toDouble(),
            ((style.sky1 shr 8) and 0xFF).toDouble(),
            (style.sky1 and 0xFF).toDouble(),
        )
        val s2 = doubleArrayOf(
            ((style.sky2 shr 16) and 0xFF).toDouble(),
            ((style.sky2 shr 8) and 0xFF).toDouble(),
            (style.sky2 and 0xFF).toDouble(),
        )
        val mid = doubleArrayOf(
            Math.round((s1[0] + s2[0]) / 2.0).toDouble(),
            Math.round((s1[1] + s2[1]) / 2.0).toDouble(),
            Math.round((s1[2] + s2[2]) / 2.0).toDouble(),
        )
        val overlays = LOOK_OVERLAYS[look] ?: emptyList()
        val points = mutableListOf<BackgroundPoint>()

        for (sky in listOf(s1, mid, s2)) {
            points.add(BackgroundPoint("sky", sky[0], sky[1], sky[2]))
            for (name in overlays) {
                val px = canvasPixel(stackFor(name), keepOut)
                val bg = over(px, sky)
                points.add(BackgroundPoint(name, bg[0], bg[1], bg[2]))
            }
        }
        return points
    }

    fun worstCaseBackgroundPoints(
        look: WeatherLook,
        themeIsDark: Boolean,
        keepOut: Double = KEEP_OUT,
    ): List<BackgroundPoint> =
        worstCaseBackgroundPoints(look, styleFor(look, themeIsDark), keepOut)

    /**
     * Port of `worstContrast` from docs/design/today-weather.html lines 1067–1081.
     * Calculates worst contrast for main text (ink) and small text (ink2) across all worst-case points.
     */
    fun worstContrast(
        look: WeatherLook,
        style: WeatherCardStyle,
        keepOut: Double = KEEP_OUT,
    ): ContrastResult {
        val ink = doubleArrayOf(
            ((style.ink shr 16) and 0xFF).toDouble(),
            ((style.ink shr 8) and 0xFF).toDouble(),
            (style.ink and 0xFF).toDouble(),
        )
        val ink2 = doubleArrayOf(
            ((style.ink2 shr 16) and 0xFF).toDouble(),
            ((style.ink2 shr 8) and 0xFF).toDouble(),
            (style.ink2 and 0xFF).toDouble(),
        )
        val points = worstCaseBackgroundPoints(look, style, keepOut)
        var main = Double.POSITIVE_INFINITY
        var small = Double.POSITIVE_INFINITY
        var worstBy = "sky"

        for (pt in points) {
            val ptRgb = doubleArrayOf(pt.r, pt.g, pt.b)
            val c1 = contrast(ink, ptRgb)
            val c2 = contrast(ink2, ptRgb)
            if (c2 < small) {
                small = c2
                worstBy = pt.name
            }
            main = minOf(main, c1)
        }
        return ContrastResult(
            main = main,
            small = small,
            worstBy = worstBy,
            pass = minOf(main, small) >= MIN_CONTRAST,
        )
    }

    fun worstContrast(
        look: WeatherLook,
        themeIsDark: Boolean,
        keepOut: Double = KEEP_OUT,
    ): ContrastResult =
        worstContrast(look, styleFor(look, themeIsDark), keepOut)
}
