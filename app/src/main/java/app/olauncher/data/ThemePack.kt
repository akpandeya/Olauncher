package app.olauncher.data

import org.json.JSONObject
import java.util.Locale
import kotlin.math.pow

/** One bundled theme pack: colours, font and bold flag (see docs/design/themes/PACK_FORMAT.md). */
data class ThemePack(
    /** File name without `.json`, e.g. `thumbline-light`. */
    val id: String,
    val name: String,
    /** Exactly [ThemePackCodec.MODE_LIGHT] or [ThemePackCodec.MODE_DARK]. */
    val mode: String,
    val background: String,
    val surface: String,
    val ink: String,
    val inkMuted: String,
    val accent: String,
    val accentSecondary: String,
    /** Snow colour; only Christmas packs set it. */
    val snow: String?,
    /** One of [ThemePackCodec.FONTS]. */
    val font: String,
    val bold: Boolean,
    /** Asset file name the pack was read from, e.g. `thumbline-light.json`. */
    val fileName: String,
)

/** Thrown by [ThemePackCodec.decode] for any malformed or invalid pack file. */
class ThemePackException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Strict codec for the theme pack JSON format (docs/design/themes/PACK_FORMAT.md): every
 * required field must be present, `bold` must be explicit, hex fields must be `#RRGGBB`,
 * unknown fields are rejected, and the palette must pass the AA contrast thresholds of
 * docs/design/themes/CONTRAST.md. Errors name the field or pair and the measured ratio.
 */
object ThemePackCodec {

    const val MODE_LIGHT = "light"
    const val MODE_DARK = "dark"

    /** Fonts a pack may name; all three ship with the app. */
    val FONTS = setOf("Inter", "IBM Plex Sans", "Nunito")

    /** Dark text the format pairs with accent fills. */
    const val DARK_TEXT_COLOR = 0xFF0B0B0C.toInt()

    private const val WHITE = 0xFFFFFFFF.toInt()
    private val HEX_PATTERN = Regex("^#[0-9a-fA-F]{6}$")

    private val REQUIRED_FIELDS = setOf(
        "name", "mode", "background", "surface", "ink", "inkMuted", "accent", "accentSecondary", "font", "bold",
    )
    private val OPTIONAL_FIELDS = setOf("snow")
    private val KNOWN_FIELDS = REQUIRED_FIELDS + OPTIONAL_FIELDS

    /** Text threshold: 4.5:1 for `ink`/`inkMuted` and for text on an accent fill. */
    const val TEXT_CONTRAST_MIN = 4.5

    /** Accent threshold: 3.0:1 for accent-only use against the background. */
    const val ACCENT_CONTRAST_MIN = 3.0

    fun decode(raw: String, fileName: String): ThemePack {
        val obj = try {
            JSONObject(raw)
        } catch (e: Exception) {
            throw ThemePackException("$fileName is not valid JSON", e)
        }

        val keys = mutableSetOf<String>()
        for (key in obj.keys()) keys.add(key)
        val unknown = keys - KNOWN_FIELDS
        if (unknown.isNotEmpty()) {
            throw ThemePackException("$fileName has unknown field(s): ${unknown.sorted().joinToString(", ")}")
        }
        for (field in REQUIRED_FIELDS) {
            if (!obj.has(field)) throw ThemePackException("$fileName is missing required field \"$field\"")
        }

        val name = stringField(obj, "name", fileName)
        if (name.isBlank()) throw ThemePackException("$fileName field \"name\" must not be empty")

        val mode = stringField(obj, "mode", fileName)
        if (mode != MODE_LIGHT && mode != MODE_DARK) {
            throw ThemePackException("$fileName field \"mode\" must be \"$MODE_LIGHT\" or \"$MODE_DARK\", got \"$mode\"")
        }

        val background = hexField(obj, "background", fileName)
        val surface = hexField(obj, "surface", fileName)
        val ink = hexField(obj, "ink", fileName)
        val inkMuted = hexField(obj, "inkMuted", fileName)
        val accent = hexField(obj, "accent", fileName)
        val accentSecondary = hexField(obj, "accentSecondary", fileName)
        val snow = if (obj.has("snow") && !obj.isNull("snow")) hexField(obj, "snow", fileName) else null

        val font = stringField(obj, "font", fileName)
        if (font !in FONTS) {
            throw ThemePackException("$fileName field \"font\" must be one of ${FONTS.sorted().joinToString(", ")}, got \"$font\"")
        }

        if (obj.get("bold") !is Boolean) {
            throw ThemePackException("$fileName field \"bold\" must be true or false")
        }
        val bold = obj.getBoolean("bold")

        val pack = ThemePack(
            id = fileName.removeSuffix(".json"),
            name = name,
            mode = mode,
            background = background,
            surface = surface,
            ink = ink,
            inkMuted = inkMuted,
            accent = accent,
            accentSecondary = accentSecondary,
            snow = snow,
            font = font,
            bold = bold,
            fileName = fileName,
        )
        validateContrast(pack, fileName)
        return pack
    }

    /** WCAG 2.x relative luminance of an sRGB colour held in an ARGB int. */
    fun relativeLuminance(color: Int): Double {
        fun linear(channel: Int): Double {
            val c = channel / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        val r = linear(color shr 16 and 0xFF)
        val g = linear(color shr 8 and 0xFF)
        val b = linear(color and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    /** WCAG 2.x contrast ratio of two ARGB colours, lighter over darker. */
    fun contrastRatio(a: Int, b: Int): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** Parses `#RRGGBB` into an opaque ARGB int. Assumes the hex was validated. */
    fun parseHex(hex: String): Int = (0xFF shl 24) or hex.substring(1).toLong(16).toInt()

    private fun validateContrast(pack: ThemePack, fileName: String) {
        val background = parseHex(pack.background)
        val surface = parseHex(pack.surface)

        fun requireText(field: String, hex: String, againstField: String, against: Int) {
            val ratio = contrastRatio(parseHex(hex), against)
            if (ratio < TEXT_CONTRAST_MIN) {
                throw ThemePackException(
                    "$fileName contrast \"$field\" / \"$againstField\" is ${ratio(ratio)}:1, needs $TEXT_CONTRAST_MIN:1",
                )
            }
        }
        requireText("ink", pack.ink, "background", background)
        requireText("ink", pack.ink, "surface", surface)
        requireText("inkMuted", pack.inkMuted, "background", background)
        requireText("inkMuted", pack.inkMuted, "surface", surface)

        requireAccent("accent", pack.accent, background, fileName)
        requireAccent("accentSecondary", pack.accentSecondary, background, fileName)
    }

    /**
     * An accent must reach 3.0:1 against the background (accent-only use) or, when it is
     * used as a fill with text on top, reach 4.5:1 against the dark or white text colour.
     */
    private fun requireAccent(field: String, hex: String, background: Int, fileName: String) {
        val color = parseHex(hex)
        val againstBackground = contrastRatio(color, background)
        if (againstBackground >= ACCENT_CONTRAST_MIN) return
        val againstDark = contrastRatio(color, DARK_TEXT_COLOR)
        if (againstDark >= TEXT_CONTRAST_MIN) return
        val againstWhite = contrastRatio(color, WHITE)
        if (againstWhite >= TEXT_CONTRAST_MIN) return
        throw ThemePackException(
            "$fileName contrast \"$field\" / \"background\" is ${ratio(againstBackground)}:1, needs $ACCENT_CONTRAST_MIN:1, " +
                "and no fill text reaches $TEXT_CONTRAST_MIN:1 (dark ${ratio(againstDark)}:1, white ${ratio(againstWhite)}:1)",
        )
    }

    private fun ratio(value: Double): String = String.format(Locale.US, "%.4f", value)

    private fun stringField(obj: JSONObject, field: String, fileName: String): String {
        val value = obj.opt(field) as? String
            ?: throw ThemePackException("$fileName field \"$field\" must be a string")
        return value
    }

    private fun hexField(obj: JSONObject, field: String, fileName: String): String {
        val value = stringField(obj, field, fileName)
        if (!HEX_PATTERN.matches(value)) {
            throw ThemePackException("$fileName field \"$field\" must be #RRGGBB hex, got \"$value\"")
        }
        return value
    }
}
