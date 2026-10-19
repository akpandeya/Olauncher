package app.olauncher.ui.settings

import app.olauncher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {

    private val sampleRows = listOf(
        SettingsRow("Apps on home screen", SettingsSections.KEY_HOME, R.id.homeAppsNum),
        SettingsRow("Show date & time", SettingsSections.KEY_HOME, R.id.dateTime),
        SettingsRow("Battery", SettingsSections.KEY_HOME, R.id.batteryMode),
        SettingsRow("Size", SettingsSections.KEY_SHORTCUTS, R.id.shortcutSize),
        SettingsRow("Shortcut icons", SettingsSections.KEY_SHORTCUTS, R.id.shortcutIconsOnOff),
        SettingsRow("Auto show keyboard", SettingsSections.KEY_DRAWER, R.id.autoShowKeyboard),
        SettingsRow("Wallpaper photos", SettingsSections.KEY_APPEARANCE, R.id.photoWallpaper),
        SettingsRow("Text size", SettingsSections.KEY_APPEARANCE, R.id.textSizeValue),
        SettingsRow("Bold font", SettingsSections.KEY_APPEARANCE, R.id.boldFont),
        SettingsRow("Export backup", SettingsSections.KEY_BACKUP, R.id.exportBackup),
    )

    @Test
    fun `exact match returns matching row with highest priority`() {
        val results = SettingsSearch.match("Battery", sampleRows)
        assertEquals(1, results.size)
        assertEquals("Battery", results[0].label)
        assertEquals(SettingsSections.KEY_HOME, results[0].sectionKey)
        assertEquals(R.id.batteryMode, results[0].viewId)
    }

    @Test
    fun `word start matching finds rows matching word boundary`() {
        val textSizeOnly = listOf(
            SettingsRow("Text size", SettingsSections.KEY_APPEARANCE, R.id.textSizeValue),
            SettingsRow("Other setting", SettingsSections.KEY_APPEARANCE, R.id.boldFont),
        )
        val matches = SettingsSearch.match("size", textSizeOnly)
        assertEquals(1, matches.size)
        assertEquals("Text size", matches[0].label)
    }

    @Test
    fun `query size matches both Size and Text size sorted by score descending`() {
        val rows = listOf(
            SettingsRow("Text size", SettingsSections.KEY_APPEARANCE, R.id.textSizeValue),
            SettingsRow("Size", SettingsSections.KEY_SHORTCUTS, R.id.shortcutSize),
            SettingsRow("Battery", SettingsSections.KEY_HOME, R.id.batteryMode),
        )
        val matches = SettingsSearch.match("size", rows)
        assertEquals(2, matches.size)
        // Exact match ("Size", score 100) ranks before word-start match ("Text size", score 80)
        assertEquals("Size", matches[0].label)
        assertEquals("Text size", matches[1].label)
    }

    @Test
    fun `one typo matches via fuzzy score`() {
        val matches = SettingsSearch.match("wallpapr", sampleRows)
        assertEquals(1, matches.size)
        assertEquals("Wallpaper photos", matches[0].label)
        assertEquals(SettingsSections.KEY_APPEARANCE, matches[0].sectionKey)
    }

    @Test
    fun `no match returns empty list`() {
        val matches = SettingsSearch.match("nonexistentxyz", sampleRows)
        assertTrue(matches.isEmpty())
    }

    @Test
    fun `empty query returns empty list`() {
        assertTrue(SettingsSearch.match("", sampleRows).isEmpty())
        assertTrue(SettingsSearch.match("   ", sampleRows).isEmpty())
        assertTrue(SettingsSearch.match(null, sampleRows).isEmpty())
    }

    @Test
    fun `empty rows list returns empty list`() {
        assertTrue(SettingsSearch.match("battery", emptyList()).isEmpty())
    }

    @Test
    fun `ties in score are sorted by section order`() {
        val rowHome = SettingsRow("Custom option", SettingsSections.KEY_HOME, R.id.homeAppsNum)
        val rowAppearance = SettingsRow("Custom option", SettingsSections.KEY_APPEARANCE, R.id.textSizeValue)
        val rowBackup = SettingsRow("Custom option", SettingsSections.KEY_BACKUP, R.id.exportBackup)

        // Pass in reverse section order
        val rows = listOf(rowBackup, rowAppearance, rowHome)
        val matches = SettingsSearch.match("custom option", rows)

        assertEquals(3, matches.size)
        assertEquals(SettingsSections.KEY_HOME, matches[0].sectionKey)
        assertEquals(SettingsSections.KEY_APPEARANCE, matches[1].sectionKey)
        assertEquals(SettingsSections.KEY_BACKUP, matches[2].sectionKey)
    }
}
