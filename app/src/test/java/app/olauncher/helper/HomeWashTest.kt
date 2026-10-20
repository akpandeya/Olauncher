package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeWashTest {

    private val nearBlack = 0xFF101018.toInt()
    private val navy = 0xFF1A2340.toInt()
    private val pinkFlower = 0xFFF0A8C0.toInt()
    private val nearWhite = 0xFFF4F4F2.toInt()

    @Test
    fun lightWallpaperWithLightTextShowsWash() {
        assertTrue(HomeWash.shouldShow(true, themeIsDark = true, supportsDarkText = true, wallpaperColors = listOf(nearWhite)))
    }

    @Test
    fun darkWallpaperWithOnlyDarkColoursHidesWash() {
        assertFalse(HomeWash.shouldShow(true, themeIsDark = true, supportsDarkText = false, wallpaperColors = listOf(nearBlack, navy)))
    }

    @Test
    fun darkWallpaperWithABrightPartShowsWash() {
        assertTrue(HomeWash.shouldShow(true, themeIsDark = true, supportsDarkText = false, wallpaperColors = listOf(navy, pinkFlower)))
    }

    @Test
    fun busyFlowerPhotoShowsWash() {
        // Colours the emulator reported for a busy flower photo: red, green, orange; no light hint.
        val red = 0xFFDC3C5A.toInt()
        val green = 0xFF17752D.toInt()
        val orange = 0xFFFA8C28.toInt()
        assertTrue(HomeWash.shouldShow(true, themeIsDark = true, supportsDarkText = false, wallpaperColors = listOf(red, green, orange)))
        // Deep green and navy alone still read well under white text.
        assertFalse(HomeWash.shouldShow(true, themeIsDark = true, supportsDarkText = false, wallpaperColors = listOf(green, navy)))
    }

    @Test
    fun whiteContrastMatchesWcag() {
        assertEquals(21.0, HomeWash.whiteContrastOn(0xFF000000.toInt()), 1e-9)
        assertEquals(1.0, HomeWash.whiteContrastOn(0xFFFFFFFF.toInt()), 1e-9)
    }

    @Test
    fun darkTextNeverGetsADarkWash() {
        assertFalse(HomeWash.shouldShow(true, themeIsDark = false, supportsDarkText = true, wallpaperColors = listOf(nearWhite)))
        assertFalse(HomeWash.shouldShow(true, themeIsDark = false, supportsDarkText = null, wallpaperColors = emptyList()))
    }

    @Test
    fun followThemeContrastSettingHidesWash() {
        assertFalse(HomeWash.shouldShow(false, themeIsDark = true, supportsDarkText = true, wallpaperColors = listOf(nearWhite)))
    }

    @Test
    fun unknownWallpaperWithLightTextShowsWash() {
        assertTrue(HomeWash.shouldShow(true, themeIsDark = true, supportsDarkText = null, wallpaperColors = emptyList()))
    }

    @Test
    fun luminanceEnds() {
        assertEquals(0.0, HomeWash.relativeLuminance(0xFF000000.toInt()), 1e-9)
        assertEquals(1.0, HomeWash.relativeLuminance(0xFFFFFFFF.toInt()), 1e-9)
        // Alpha is ignored.
        assertEquals(1.0, HomeWash.relativeLuminance(0x00FFFFFF), 1e-9)
    }

    @Test
    fun luminanceWeightsGreenMost() {
        val red = HomeWash.relativeLuminance(0xFFFF0000.toInt())
        val green = HomeWash.relativeLuminance(0xFF00FF00.toInt())
        val blue = HomeWash.relativeLuminance(0xFF0000FF.toInt())
        assertEquals(0.2126, red, 1e-4)
        assertEquals(0.7152, green, 1e-4)
        assertEquals(0.0722, blue, 1e-4)
    }
}
