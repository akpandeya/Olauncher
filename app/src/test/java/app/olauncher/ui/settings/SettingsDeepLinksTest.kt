package app.olauncher.ui.settings

import app.olauncher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SettingsDeepLinksTest {

    @Test
    fun `pause deep link targets wellbeing section and pauseBeforeAppsOnOff row`() {
        val link = SettingsDeepLinks.pause()

        assertEquals(SettingsSections.KEY_WELLBEING, link.sectionKey)
        assertEquals(R.id.pauseBeforeAppsOnOff, link.rowViewId)

        // Exact string value and non-zero row id
        assertEquals("wellbeing", link.sectionKey)
        assertNotEquals(0, link.rowViewId)

        // Values would fail if swapped or mismatched
        assertNotEquals(link.sectionKey, link.rowViewId.toString())

        // Resolves to Wellbeing section via SettingsSections resolver
        val resolvedWithBoth = SettingsSections.resolveTarget(link.sectionKey, link.rowViewId)
        assertEquals(SettingsSections.WELLBEING, resolvedWithBoth)

        val resolvedWithRowOnly = SettingsSections.resolveTarget(null, link.rowViewId)
        assertEquals(SettingsSections.WELLBEING, resolvedWithRowOnly)

        val resolvedFromRowDirect = SettingsSections.findSectionByViewId(link.rowViewId)
        assertEquals(SettingsSections.WELLBEING, resolvedFromRowDirect)

        val resolvedFromKeyDirect = SettingsSections.findByKey(link.sectionKey)
        assertEquals(SettingsSections.WELLBEING, resolvedFromKeyDirect)
    }

    @Test
    fun `PAUSE constant and forPause helper match pause`() {
        assertEquals(SettingsDeepLinks.PAUSE, SettingsDeepLinks.pause())
        assertEquals(SettingsDeepLinks.PAUSE, SettingsDeepLinks.forPause())
        assertEquals(SettingsSections.KEY_WELLBEING, SettingsDeepLinks.PAUSE.sectionKey)
        assertEquals(R.id.pauseBeforeAppsOnOff, SettingsDeepLinks.PAUSE.rowViewId)
    }

    @Test
    fun `swapped or altered values fail to match expected section and row`() {
        val link = SettingsDeepLinks.pause()

        // Swapping row id with home, shortcuts, or drawer rows would fail
        assertNotEquals(R.id.homeAppsNum, link.rowViewId)
        assertNotEquals(R.id.shortcutIconsOnOff, link.rowViewId)
        assertNotEquals(R.id.autoShowKeyboard, link.rowViewId)

        // Swapping section key with another section key would fail
        assertNotEquals(SettingsSections.KEY_HOME, link.sectionKey)
        assertNotEquals(SettingsSections.KEY_SHORTCUTS, link.sectionKey)
        assertNotEquals(SettingsSections.KEY_DRAWER, link.sectionKey)
        assertNotEquals(SettingsSections.KEY_BACKUP, link.sectionKey)

        // Section from row does not match other sections
        assertNotEquals(SettingsSections.HOME, SettingsSections.findSectionByViewId(link.rowViewId))
        assertNotEquals(SettingsSections.SHORTCUTS, SettingsSections.findSectionByViewId(link.rowViewId))
        assertNotEquals(SettingsSections.DRAWER, SettingsSections.findSectionByViewId(link.rowViewId))
    }
}
