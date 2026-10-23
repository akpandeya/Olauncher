package app.olauncher.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsValueToneTest {
    @Test
    fun offIsDimmed() {
        assertEquals(SettingsValueTone.DIMMED_ALPHA, SettingsValueTone.alphaFor("Aus", "Aus"), 0f)
    }

    @Test
    fun onAndOtherValuesAreFull() {
        assertEquals(SettingsValueTone.FULL_ALPHA, SettingsValueTone.alphaFor("On", "Off"), 0f)
        assertEquals(SettingsValueTone.FULL_ALPHA, SettingsValueTone.alphaFor("60 min", "Off"), 0f)
        assertEquals(SettingsValueTone.FULL_ALPHA, SettingsValueTone.alphaFor(null, "Off"), 0f)
    }
}
