package app.olauncher.glance

import app.olauncher.helper.WeatherLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherCardReadabilityTest {

    @Test
    fun `keep-out constants match design mock specifications`() {
        assertEquals(0.85, WeatherCardReadability.KEEP_OUT, 0.0001)
        assertEquals(0.87, WeatherCardReadability.KEEP_OUT_PEAK, 0.0001)
        assertEquals(14, WeatherCardReadability.KEEP_OUT_PAD)
        assertEquals(6, WeatherCardReadability.KEEP_OUT_SIGMA)
        assertEquals(4.5, WeatherCardReadability.MIN_CONTRAST, 0.0001)
    }

    @Test
    fun `no look is missing from the table for both themes`() {
        val looks = WeatherLook.values()
        assertEquals("Expected exactly 9 WeatherLooks", 9, looks.size)

        for (themeIsDark in listOf(false, true)) {
            for (look in looks) {
                val style = WeatherCardReadability.styleFor(look, themeIsDark)
                assertNotNull("Style for $look (dark=$themeIsDark) must not be null", style)
                assertTrue("sky1 for $look must be non-zero", style.sky1 != 0)
                assertTrue("sky2 for $look must be non-zero", style.sky2 != 0)
                assertTrue("ink for $look must be non-zero", style.ink != 0)
                assertTrue("ink2 for $look must be non-zero", style.ink2 != 0)
                assertTrue("halo for $look must be non-zero", style.halo != 0)
                assertTrue("rainFar for $look must be non-zero", style.rainFar != 0)
                assertTrue("rainMid for $look must be non-zero", style.rainMid != 0)
                assertTrue("rainNear for $look must be non-zero", style.rainNear != 0)
            }
        }
    }

    @Test
    fun `all 9 looks times 2 themes give at least 4 point 5 to 1 contrast for ink and ink2 against every worst-case point`() {
        for (themeIsDark in listOf(false, true)) {
            for (look in WeatherLook.values()) {
                val style = WeatherCardReadability.styleFor(look, themeIsDark)
                val points = WeatherCardReadability.worstCaseBackgroundPoints(look, style)
                assertTrue("Points for $look must not be empty", points.isNotEmpty())

                val inkRgb = doubleArrayOf(
                    ((style.ink shr 16) and 0xFF).toDouble(),
                    ((style.ink shr 8) and 0xFF).toDouble(),
                    (style.ink and 0xFF).toDouble(),
                )
                val ink2Rgb = doubleArrayOf(
                    ((style.ink2 shr 16) and 0xFF).toDouble(),
                    ((style.ink2 shr 8) and 0xFF).toDouble(),
                    (style.ink2 and 0xFF).toDouble(),
                )

                for (pt in points) {
                    val cMain = WeatherCardReadability.contrast(inkRgb, pt.rgb)
                    val cSmall = WeatherCardReadability.contrast(ink2Rgb, pt.rgb)
                    assertTrue(
                        "Look $look (dark=$themeIsDark, pt=${pt.name}) ink contrast $cMain < 4.5",
                        cMain >= WeatherCardReadability.MIN_CONTRAST,
                    )
                    assertTrue(
                        "Look $look (dark=$themeIsDark, pt=${pt.name}) ink2 contrast $cSmall < 4.5",
                        cSmall >= WeatherCardReadability.MIN_CONTRAST,
                    )
                }

                val result = WeatherCardReadability.worstContrast(look, themeIsDark)
                assertTrue("Look $look (dark=$themeIsDark) must pass readability", result.pass)
                assertTrue(
                    "Look $look (dark=$themeIsDark) main contrast ${result.main} < 4.5",
                    result.main >= WeatherCardReadability.MIN_CONTRAST,
                )
                assertTrue(
                    "Look $look (dark=$themeIsDark) small contrast ${result.small} < 4.5",
                    result.small >= WeatherCardReadability.MIN_CONTRAST,
                )
            }
        }
    }

    @Test
    fun `pins dark snow at 4 point 74, dark partly at 5 point 13, dark light rain at 5 point 26 as tightest cases`() {
        val snowResult = WeatherCardReadability.worstContrast(WeatherLook.SNOW, themeIsDark = true)
        assertEquals("Dark snow small contrast must be 4.74 (±0.01)", 4.74, snowResult.small, 0.01)
        assertEquals("flake", snowResult.worstBy)

        val partlyResult = WeatherCardReadability.worstContrast(WeatherLook.PARTLY, themeIsDark = true)
        assertEquals("Dark partly small contrast must be 5.13 (±0.01)", 5.13, partlyResult.small, 0.01)
        assertEquals("sun+cloud", partlyResult.worstBy)

        val lightRainResult = WeatherCardReadability.worstContrast(WeatherLook.LIGHT_RAIN, themeIsDark = true)
        assertEquals("Dark light rain small contrast must be 5.26 (±0.01)", 5.26, lightRainResult.small, 0.01)
        assertEquals("rainPale", lightRainResult.worstBy)

        // Verify that snow, partly, and light rain are the 3 tightest cases among all looks in dark theme.
        val sortedDarkLooks = WeatherLook.values()
            .map { it to WeatherCardReadability.worstContrast(it, themeIsDark = true).small }
            .sortedBy { it.second }

        assertEquals(WeatherLook.SNOW, sortedDarkLooks[0].first)
        assertEquals(WeatherLook.PARTLY, sortedDarkLooks[1].first)
        assertEquals(WeatherLook.LIGHT_RAIN, sortedDarkLooks[2].first)
    }

    @Test
    fun `storm and night use dark skies with luminance below 0 point 05 and light ink in light theme too`() {
        for (look in listOf(WeatherLook.STORM, WeatherLook.CLEAR_NIGHT)) {
            val lightStyle = WeatherCardReadability.styleFor(look, themeIsDark = false)
            val lumSky1 = WeatherCardReadability.relativeLuminance(lightStyle.sky1)
            val lumSky2 = WeatherCardReadability.relativeLuminance(lightStyle.sky2)
            val lumInk = WeatherCardReadability.relativeLuminance(lightStyle.ink)

            assertTrue("Light theme $look sky1 luminance $lumSky1 must be < 0.05", lumSky1 < 0.05)
            assertTrue("Light theme $look sky2 luminance $lumSky2 must be < 0.05", lumSky2 < 0.05)
            assertTrue("Light theme $look ink luminance $lumInk must be light (> 0.8)", lumInk > 0.8)
            assertTrue("Light theme $look must use iconsDark = true", lightStyle.iconsDark)

            val darkStyle = WeatherCardReadability.styleFor(look, themeIsDark = true)
            val darkLumSky1 = WeatherCardReadability.relativeLuminance(darkStyle.sky1)
            val darkLumSky2 = WeatherCardReadability.relativeLuminance(darkStyle.sky2)
            val darkLumInk = WeatherCardReadability.relativeLuminance(darkStyle.ink)

            assertTrue("Dark theme $look sky1 luminance $darkLumSky1 must be < 0.05", darkLumSky1 < 0.05)
            assertTrue("Dark theme $look sky2 luminance $darkLumSky2 must be < 0.05", darkLumSky2 < 0.05)
            assertTrue("Dark theme $look ink luminance $darkLumInk must be light (> 0.8)", darkLumInk > 0.8)
            assertTrue("Dark theme $look must use iconsDark = true", darkStyle.iconsDark)
        }
    }

    @Test
    fun `iconsDark is true for storm and night in both themes, and all looks in dark theme`() {
        for (look in WeatherLook.values()) {
            val lightStyle = WeatherCardReadability.styleFor(look, themeIsDark = false)
            if (look == WeatherLook.STORM || look == WeatherLook.CLEAR_NIGHT) {
                assertTrue("Light theme $look must have iconsDark = true", lightStyle.iconsDark)
            } else {
                assertFalse("Light theme $look must have iconsDark = false", lightStyle.iconsDark)
            }

            val darkStyle = WeatherCardReadability.styleFor(look, themeIsDark = true)
            assertTrue("Dark theme $look must have iconsDark = true", darkStyle.iconsDark)
        }
    }

    @Test
    fun `rain colours follow design tokens and storm-night override block`() {
        val lightRainInk = WeatherCardReadability.styleFor(WeatherLook.LIGHT_RAIN, themeIsDark = false)
        val paleRainStorm = WeatherCardReadability.styleFor(WeatherLook.STORM, themeIsDark = false)
        val paleRainNight = WeatherCardReadability.styleFor(WeatherLook.CLEAR_NIGHT, themeIsDark = false)

        // Non-storm/night in light theme use ink rain
        assertEquals(0x4D2E6585.toInt(), lightRainInk.rainFar)
        assertEquals(0x8C2E6585.toInt(), lightRainInk.rainMid)
        assertEquals(0xD9215470.toInt(), lightRainInk.rainNear)

        // Storm and night in light theme use pale rain override
        assertEquals(0x6696B9D0.toInt(), paleRainStorm.rainFar)
        assertEquals(0xB396B9D0.toInt(), paleRainStorm.rainMid)
        assertEquals(0xE6CDE2F0.toInt(), paleRainStorm.rainNear)

        assertEquals(0x6696B9D0.toInt(), paleRainNight.rainFar)
        assertEquals(0xB396B9D0.toInt(), paleRainNight.rainMid)
        assertEquals(0xE6CDE2F0.toInt(), paleRainNight.rainNear)

        // All looks in dark theme use pale rain
        for (look in WeatherLook.values()) {
            val darkStyle = WeatherCardReadability.styleFor(look, themeIsDark = true)
            assertEquals("Dark theme $look rainFar must be pale", 0x6696B9D0.toInt(), darkStyle.rainFar)
            assertEquals("Dark theme $look rainMid must be pale", 0xB396B9D0.toInt(), darkStyle.rainMid)
            assertEquals("Dark theme $look rainNear must be pale", 0xE6CDE2F0.toInt(), darkStyle.rainNear)
        }
    }
}
