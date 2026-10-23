package app.olauncher.ui.settings

import app.olauncher.helper.ReadableScrim
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsValueToneTest {
    @Test
    fun offIsDimmed() {
        assertEquals(SettingsValueTone.DIMMED_ALPHA, SettingsValueTone.alphaFor("Aus", "Aus"), 0f)
    }

    @Test
    fun dimmedOffStillMeetsHintReadabilityBar() {
        val a = Math.round(SettingsValueTone.DIMMED_ALPHA * 255)
        for (dark in listOf(true, false)) {
            val scrim = if (dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            val ink = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            val bg = ReadableScrim.blend((ReadableScrim.CARD_ALPHA shl 24) or (scrim and 0xFFFFFF), ReadableScrim.worstPhoto(dark))
            val text = ReadableScrim.blend((a shl 24) or (ink and 0xFFFFFF), bg)
            val c = ReadableScrim.contrast(text, bg)
            assertTrue("dark=$dark contrast=$c", c >= ReadableScrim.MIN_CONTRAST)
        }
    }

    @Test
    fun onAndOtherValuesAreFull() {
        assertEquals(SettingsValueTone.FULL_ALPHA, SettingsValueTone.alphaFor("On", "Off"), 0f)
        assertEquals(SettingsValueTone.FULL_ALPHA, SettingsValueTone.alphaFor("60 min", "Off"), 0f)
        assertEquals(SettingsValueTone.FULL_ALPHA, SettingsValueTone.alphaFor(null, "Off"), 0f)
    }
}
