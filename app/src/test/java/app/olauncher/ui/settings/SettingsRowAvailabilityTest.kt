package app.olauncher.ui.settings

import app.olauncher.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsRowAvailabilityTest {

    private val shortcutRows = listOf(
        R.id.shortcutCount, R.id.shortcutSize, R.id.shortcutSide, R.id.shortcutHeight, R.id.shortcutIconStyle,
    )
    private val germanRows = listOf(
        R.id.germanClock, R.id.germanLookupOnOff, R.id.germanPauseWordOnOff, R.id.germanUiOnOff,
    )

    @Test
    fun `shortcut sub-rows follow the shortcuts switch only`() {
        for (id in shortcutRows) {
            assertFalse(SettingsRowAvailability.isAvailable(id, shortcutsOn = false, germanOn = true))
            assertTrue(SettingsRowAvailability.isAvailable(id, shortcutsOn = true, germanOn = false))
        }
    }

    @Test
    fun `german sub-rows follow the german switch only`() {
        for (id in germanRows) {
            assertFalse(SettingsRowAvailability.isAvailable(id, shortcutsOn = true, germanOn = false))
            assertTrue(SettingsRowAvailability.isAvailable(id, shortcutsOn = false, germanOn = true))
        }
    }

    @Test
    fun `parent switches and other rows are always available`() {
        for (id in listOf(R.id.shortcutIconsOnOff, R.id.germanOnOff, R.id.photoWallpaper, R.id.textSizeValue)) {
            assertTrue(SettingsRowAvailability.isAvailable(id, shortcutsOn = false, germanOn = false))
        }
    }
}
