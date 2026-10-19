package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContrastPolicyTest {
    @Test
    fun lightTextOnLightWallpaperGetsStrongShadowAndKeepsThemeColour() {
        val t = ContrastPolicy.decide(supportsDarkText = true, themeIsDark = true)
        assertNull(t.color)
        assertEquals(6, t.shadowRadiusDp)
        assertEquals(0.6f, t.shadowAlpha, 0f)
    }

    @Test
    fun darkTextOnDarkWallpaperGetsStrongShadow() {
        val t = ContrastPolicy.decide(supportsDarkText = false, themeIsDark = false)
        assertNull(t.color)
        assertEquals(6, t.shadowRadiusDp)
        assertEquals(0.6f, t.shadowAlpha, 0f)
    }

    @Test
    fun lightTextOnDarkWallpaperUsesThemeDefault() {
        assertEquals(ContrastPolicy.THEME_DEFAULT, ContrastPolicy.decide(false, true))
    }

    @Test
    fun darkTextOnLightWallpaperUsesThemeDefault() {
        assertEquals(ContrastPolicy.THEME_DEFAULT, ContrastPolicy.decide(true, false))
    }

    @Test
    fun unknownWallpaperUsesThemeDefault() {
        assertEquals(ContrastPolicy.THEME_DEFAULT, ContrastPolicy.decide(null, true))
        assertEquals(ContrastPolicy.THEME_DEFAULT, ContrastPolicy.decide(null, false))
    }
}
