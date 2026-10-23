package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockColourTest {
    private val teal = 0xFF1A7F8F.toInt()
    private val grey = 0xFF404040.toInt()

    @Test fun emptyWallpaperKeepsTheme() = assertNull(ClockColour.pick(emptyList(), true))

    @Test fun darkTheme_pickReachesContrastOnEveryColour() {
        val colours = listOf(0xFF102030.toInt(), teal, 0xFF301020.toInt())
        val pick = ClockColour.pick(colours, true)!!
        colours.forEach { assertTrue(ReadableScrim.contrast(pick, it) >= ReadableScrim.MIN_CONTRAST) }
    }

    @Test fun lightTheme_pickIsDarkenedAndReadable() {
        val colours = listOf(0xFFF0E8D8.toInt(), 0xFFE0C0A0.toInt())
        val pick = ClockColour.pick(colours, false)!!
        colours.forEach { assertTrue(ReadableScrim.contrast(pick, it) >= ReadableScrim.MIN_CONTRAST) }
        assertTrue(HomeWash.relativeLuminance(pick) < 0.4)
    }

    @Test fun mostColourfulColourSuppliesTheHue() {
        val pick = ClockColour.pick(listOf(grey, 0xFF0A3A8A.toInt()), true)!!
        val hue = AccentSuggestions.hsl(pick)[0]
        assertTrue("hue $hue should be blue", hue in 200f..240f)
    }

    @Test fun busyPhotoWithDarkAndLightAreasKeepsTheme() {
        val dark = 0xFF202020.toInt()
        val light = 0xFFE0E0E0.toInt()
        assertNull(ClockColour.pick(listOf(dark, light), true))
        assertNull(ClockColour.pick(listOf(dark, light), false))
    }

    @Test fun lightPhotoUnderLightTextKeepsTheme() {
        assertNull(ClockColour.pick(listOf(0xFFFFFFFF.toInt()), true))
    }

    @Test fun alphaIsIgnoredAndResultIsOpaque() {
        val pick = ClockColour.pick(listOf(0x00102030), true)
        assertNotNull(pick)
        assertEquals(0xFF, pick!! ushr 24)
    }

    @Test fun readableOnAllFalseWhenOneBackdropClashes() {
        assertTrue(!ClockColour.readableOnAll(0xFFFFFFFF.toInt(), listOf(0xFF000000.toInt(), 0xFFEEEEEE.toInt())))
        assertTrue(ClockColour.readableOnAll(0xFFFFFFFF.toInt(), listOf(0xFF000000.toInt(), 0xFF202020.toInt())))
    }

    @Test fun fixedAppThemeBeatsSystemSetting() {
        assertTrue(ClockColour.themeIsDark(2, systemIsDark = false))
        assertTrue(!ClockColour.themeIsDark(1, systemIsDark = true))
    }

    @Test fun followSystemUsesSystemSetting() {
        assertTrue(ClockColour.themeIsDark(-1, systemIsDark = true))
        assertTrue(!ClockColour.themeIsDark(-1, systemIsDark = false))
    }
}
