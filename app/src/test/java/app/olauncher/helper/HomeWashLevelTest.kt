package app.olauncher.helper

import app.olauncher.data.Prefs
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeWashLevelTest {
    private val nearBlack = 0xFF101010.toInt()

    @Test
    fun strongShowsWithLightTextEvenWhenWallpaperIsDark() {
        assertTrue(HomeWash.shouldShow(true, themeIsDark = true, supportsDarkText = false, wallpaperColors = listOf(nearBlack), level = Prefs.WASH_STRONG))
    }

    @Test
    fun offNeverShowsEvenOnBusyWallpaper() {
        assertFalse(HomeWash.shouldShow(true, themeIsDark = true, supportsDarkText = null, wallpaperColors = emptyList(), level = Prefs.WASH_OFF))
    }

    @Test
    fun strongStillNeverShowsWithDarkText() {
        assertFalse(HomeWash.shouldShow(true, themeIsDark = false, supportsDarkText = true, wallpaperColors = emptyList(), level = Prefs.WASH_STRONG))
    }
}
