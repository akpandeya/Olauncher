package app.olauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs

/**
 * Strict parse rules of [ThemePackCodec] (docs/design/themes/PACK_FORMAT.md) and the AA
 * contrast thresholds of docs/design/themes/CONTRAST.md. Failing colours are constructed
 * arithmetically: each test first proves with the codec's own ratio helper that the colour
 * really is below the threshold, then asserts the codec rejects the file.
 */
class ThemePackCodecTest {

    /** The worked example from PACK_FORMAT.md. */
    private val workedExample = """
        {
          "name": "Thumbline",
          "mode": "light",
          "background": "#FFFFFF",
          "surface": "#F1F1EF",
          "ink": "#0E0E0F",
          "inkMuted": "#5A5A60",
          "accent": "#D9F24B",
          "accentSecondary": "#C2185B",
          "font": "Inter",
          "bold": false
        }
    """.trimIndent()

    private fun decode(json: String, fileName: String = "test-light.json"): ThemePack =
        ThemePackCodec.decode(json, fileName)

    private fun expectFailure(json: String, vararg fragments: String) {
        try {
            decode(json)
        } catch (e: ThemePackException) {
            for (fragment in fragments) {
                assertTrue(
                    "Message \"${e.message}\" must name \"$fragment\"",
                    e.message?.contains(fragment) == true,
                )
            }
            return
        }
        fail("Expected ThemePackException, decoded successfully")
    }

    private fun withField(json: String, field: String, value: String): String {
        val quoted = Regex("\"$field\"\\s*:\\s*\"[^\"]*\"")
        require(quoted.containsMatchIn(json)) { "Field $field not found as a string in the sample JSON" }
        return json.replace(quoted, "\"$field\": \"$value\"")
    }

    private fun withoutField(json: String, field: String): String {
        val line = json.lines().filterNot { it.trimStart().startsWith("\"$field\"") }
        require(line.size < json.lines().size) { "Field $field not found in the sample JSON" }
        return line.joinToString("\n")
    }

    @Test
    fun `parses the worked example from the format doc`() {
        val pack = decode(workedExample, "thumbline-light.json")

        assertEquals("thumbline-light", pack.id)
        assertEquals("Thumbline", pack.name)
        assertEquals("light", pack.mode)
        assertEquals("#FFFFFF", pack.background)
        assertEquals("#F1F1EF", pack.surface)
        assertEquals("#0E0E0F", pack.ink)
        assertEquals("#5A5A60", pack.inkMuted)
        assertEquals("#D9F24B", pack.accent)
        assertEquals("#C2185B", pack.accentSecondary)
        assertEquals(null, pack.snow)
        assertEquals("Inter", pack.font)
        assertEquals(false, pack.bold)
        assertEquals("thumbline-light.json", pack.fileName)
    }

    @Test
    fun `lowercase hex and the optional snow field parse`() {
        val json = withField(workedExample, "accent", "#d9f24b")
            .replace("\"bold\": false", "\"bold\": false,\n  \"snow\": \"#6FB594\"")
        val pack = decode(json)
        assertEquals("#d9f24b", pack.accent)
        assertEquals("#6FB594", pack.snow)
    }

    @Test
    fun `rejects bad hex in a colour field`() {
        expectFailure(withField(workedExample, "background", "#FFF"), "background")
        expectFailure(withField(workedExample, "ink", "#12345G"), "ink")
        expectFailure(withField(workedExample, "surface", "rgb(255, 0, 0)"), "surface")
        expectFailure(withField(workedExample, "accent", "red"), "accent")
    }

    @Test
    fun `rejects a missing required field`() {
        expectFailure(withoutField(workedExample, "inkMuted"), "inkMuted")
        expectFailure(withoutField(workedExample, "surface"), "surface")
        expectFailure(withoutField(workedExample, "accentSecondary"), "accentSecondary")
        expectFailure(withoutField(workedExample, "name"), "name")
    }

    @Test
    fun `bold must be explicit, not implied by absence`() {
        expectFailure(withoutField(workedExample, "bold"), "bold")
    }

    @Test
    fun `rejects unknown modes`() {
        expectFailure(withField(workedExample, "mode", "system"), "mode")
        expectFailure(withField(workedExample, "mode", "auto"), "mode")
        expectFailure(withField(workedExample, "mode", "Light"), "mode")
    }

    @Test
    fun `rejects fonts outside the bundled set`() {
        expectFailure(withField(workedExample, "font", "Comic Sans"), "font")
        expectFailure(withField(workedExample, "font", "nunito"), "font")
    }

    @Test
    fun `rejects unknown fields so a typo cannot do nothing`() {
        val json = workedExample
            .replace("\"bold\": false", "\"bold\": false,\n  \"wash\": \"#123456\"")
        expectFailure(json, "wash")
    }

    @Test
    fun `rejects a bad snow colour`() {
        val json = workedExample
            .replace("\"bold\": false", "\"bold\": false,\n  \"snow\": \"#E6F6E\"")
        expectFailure(json, "snow")
    }

    @Test
    fun `rejects ink that fails 4_5 to 1 against the background`() {
        // A grey well above mid: its ratio against white must be under the text threshold.
        val ink = "#B4B4B4"
        val ratio = ThemePackCodec.contrastRatio(
            ThemePackCodec.parseHex(ink),
            ThemePackCodec.parseHex("#FFFFFF"),
        )
        assertTrue("Constructed colour must fail: measured $ratio", ratio < ThemePackCodec.TEXT_CONTRAST_MIN)

        expectFailure(withField(workedExample, "ink", ink), "ink", "background")
    }

    @Test
    fun `rejects ink that fails 4_5 to 1 against the surface`() {
        // The worked example with a mid grey ink: it still reaches 4.5:1 against the white
        // background, so the codec reaches the surface pair, where it measures below 4.5:1.
        val ink = "#737373"
        val againstBackground = ThemePackCodec.contrastRatio(
            ThemePackCodec.parseHex(ink),
            ThemePackCodec.parseHex("#FFFFFF"),
        )
        val againstSurface = ThemePackCodec.contrastRatio(
            ThemePackCodec.parseHex(ink),
            ThemePackCodec.parseHex("#F1F1EF"),
        )
        assertTrue("Constructed colour must pass the background pair: $againstBackground", againstBackground >= ThemePackCodec.TEXT_CONTRAST_MIN)
        assertTrue("Constructed colour must fail the surface pair: $againstSurface", againstSurface < ThemePackCodec.TEXT_CONTRAST_MIN)

        expectFailure(withField(workedExample, "ink", ink), "ink", "surface")
    }

    @Test
    fun `rejects inkMuted that fails 4_5 to 1`() {
        val inkMuted = "#B4B4B4"
        val ratio = ThemePackCodec.contrastRatio(
            ThemePackCodec.parseHex(inkMuted),
            ThemePackCodec.parseHex("#FFFFFF"),
        )
        assertTrue("Constructed colour must fail: measured $ratio", ratio < ThemePackCodec.TEXT_CONTRAST_MIN)

        expectFailure(withField(workedExample, "inkMuted", inkMuted), "inkMuted")
    }

    @Test
    fun `rejects an accent that fails both the background and the fill branch`() {
        // An accent fails every branch only near the mid point where it is within 3:1 of
        // the background yet roughly 4.4:1 away from both the dark and the white fill text.
        val accent = "#787878"
        val background = "#3A3A3A"
        val accentColor = ThemePackCodec.parseHex(accent)
        val againstBackground = ThemePackCodec.contrastRatio(accentColor, ThemePackCodec.parseHex(background))
        val againstDark = ThemePackCodec.contrastRatio(accentColor, ThemePackCodec.DARK_TEXT_COLOR)
        val againstWhite = ThemePackCodec.contrastRatio(accentColor, 0xFFFFFFFF.toInt())
        assertTrue("Must fail the background branch: $againstBackground", againstBackground < ThemePackCodec.ACCENT_CONTRAST_MIN)
        assertTrue("Must fail the dark fill branch: $againstDark", againstDark < ThemePackCodec.TEXT_CONTRAST_MIN)
        assertTrue("Must fail the white fill branch: $againstWhite", againstWhite < ThemePackCodec.TEXT_CONTRAST_MIN)

        val darkBase = """
            {
              "name": "Test Dark",
              "mode": "dark",
              "background": "$background",
              "surface": "#444444",
              "ink": "#F4F1E8",
              "inkMuted": "#A9C4B4",
              "accent": "$accent",
              "accentSecondary": "#FF6459",
              "font": "Inter",
              "bold": false
            }
        """.trimIndent()
        expectFailure(darkBase, "accent")
    }

    @Test
    fun `rejects accentSecondary that fails both branches`() {
        val darkBase = """
            {
              "name": "Test Dark",
              "mode": "dark",
              "background": "#3A3A3A",
              "surface": "#444444",
              "ink": "#F4F1E8",
              "inkMuted": "#A9C4B4",
              "accent": "#FF6459",
              "accentSecondary": "#787878",
              "font": "Inter",
              "bold": false
            }
        """.trimIndent()
        expectFailure(darkBase, "accentSecondary")
    }

    @Test
    fun `accepts a light accent that only passes the fill branch`() {
        // The Thumbline light accent fails 3:1 against the white background but reaches
        // 15:1 with dark text on the fill, so the format accepts it (CONTRAST.md).
        val pack = decode(workedExample)
        val ratio = ThemePackCodec.contrastRatio(
            ThemePackCodec.parseHex(pack.accent),
            ThemePackCodec.DARK_TEXT_COLOR,
        )
        assertTrue(ratio >= ThemePackCodec.TEXT_CONTRAST_MIN)
    }

    @Test
    fun `contrast helper follows WCAG 2x`() {
        assertEquals(21.0, ThemePackCodec.contrastRatio(0xFFFFFFFF.toInt(), 0xFF000000.toInt()), 0.01)
        assertEquals(1.0, ThemePackCodec.contrastRatio(0xFF123456.toInt(), 0xFF123456.toInt()), 0.0001)
        // Pure white is the lightest possible colour.
        assertEquals(1.0, ThemePackCodec.relativeLuminance(0xFFFFFFFF.toInt()), 0.0001)
        assertEquals(0.0, ThemePackCodec.relativeLuminance(0xFF000000.toInt()), 0.0001)
        // Black on white and white on black measure the same.
        val forward = ThemePackCodec.contrastRatio(0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        val backward = ThemePackCodec.contrastRatio(0xFFFFFFFF.toInt(), 0xFF000000.toInt())
        assertTrue(abs(forward - backward) < 0.0001)
    }

    @Test
    fun `parseHex turns RRGGBB into an opaque ARGB int`() {
        assertEquals(0xFFD9F24B.toInt(), ThemePackCodec.parseHex("#D9F24B"))
        assertEquals(0xFF000000.toInt(), ThemePackCodec.parseHex("#000000"))
        assertEquals(0xFFFFFFFF.toInt(), ThemePackCodec.parseHex("#FFFFFF"))
    }
}
