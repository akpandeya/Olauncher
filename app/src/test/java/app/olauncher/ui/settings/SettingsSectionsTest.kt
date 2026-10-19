package app.olauncher.ui.settings

import app.olauncher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSectionsTest {

    @Test
    fun `lists exactly 9 sections in order`() {
        assertEquals(9, SettingsSections.size)
        assertEquals(9, SettingsSections.all.size)

        val expectedOrder = listOf(
            SettingsSections.KEY_HOME,
            SettingsSections.KEY_SHORTCUTS,
            SettingsSections.KEY_DRAWER,
            SettingsSections.KEY_WELLBEING,
            SettingsSections.KEY_TODAY,
            SettingsSections.KEY_DEUTSCH,
            SettingsSections.KEY_APPEARANCE,
            SettingsSections.KEY_GESTURES,
            SettingsSections.KEY_BACKUP,
        )

        val actualOrder = SettingsSections.map { it.key }
        assertEquals(expectedOrder, actualOrder)

        val expectedSections = listOf(
            SettingsSections.HOME,
            SettingsSections.SHORTCUTS,
            SettingsSections.DRAWER,
            SettingsSections.WELLBEING,
            SettingsSections.TODAY,
            SettingsSections.DEUTSCH,
            SettingsSections.APPEARANCE,
            SettingsSections.GESTURES,
            SettingsSections.BACKUP,
        )
        assertEquals(expectedSections, SettingsSections.all)
    }

    @Test
    fun `each section has non-zero title and container id`() {
        for (section in SettingsSections) {
            assertTrue("titleRes must be non-zero for ${section.key}", section.titleRes != 0)
            assertTrue("containerId must be non-zero for ${section.key}", section.containerId != 0)
            assertEquals(section.titleRes, section.title)
            assertEquals(section.titleRes, section.titleStringRes)
            assertEquals(section.containerId, section.containerViewId)
        }

        assertEquals(R.string.home_screen, SettingsSections.HOME.titleRes)
        assertEquals(R.id.homeSection, SettingsSections.HOME.containerId)

        assertEquals(R.string.shortcut_icons, SettingsSections.SHORTCUTS.titleRes)
        assertEquals(R.id.shortcutIconsSection, SettingsSections.SHORTCUTS.containerId)

        assertEquals(R.string.drawer, SettingsSections.DRAWER.titleRes)
        assertEquals(R.id.drawerSection, SettingsSections.DRAWER.containerId)

        assertEquals(R.string.wellbeing, SettingsSections.WELLBEING.titleRes)
        assertEquals(R.id.wellbeingSection, SettingsSections.WELLBEING.containerId)

        assertEquals(R.string.today_section, SettingsSections.TODAY.titleRes)
        assertEquals(R.id.todaySection, SettingsSections.TODAY.containerId)

        assertEquals(R.string.deutsch_de, SettingsSections.DEUTSCH.titleRes)
        assertEquals(R.id.deutschSection, SettingsSections.DEUTSCH.containerId)

        assertEquals(R.string.appearance, SettingsSections.APPEARANCE.titleRes)
        assertEquals(R.id.appearanceSection, SettingsSections.APPEARANCE.containerId)

        assertEquals(R.string.gestures, SettingsSections.GESTURES.titleRes)
        assertEquals(R.id.gesturesSection, SettingsSections.GESTURES.containerId)

        assertEquals(R.string.backup, SettingsSections.BACKUP.titleRes)
        assertEquals(R.id.backupSection, SettingsSections.BACKUP.containerId)
    }

    @Test
    fun `lookup by key finds correct section`() {
        assertEquals(SettingsSections.HOME, SettingsSections.findByKey("home"))
        assertEquals(SettingsSections.SHORTCUTS, SettingsSections.findByKey("shortcuts"))
        assertEquals(SettingsSections.DRAWER, SettingsSections.findByKey("drawer"))
        assertEquals(SettingsSections.WELLBEING, SettingsSections.findByKey("wellbeing"))
        assertEquals(SettingsSections.TODAY, SettingsSections.findByKey("today"))
        assertEquals(SettingsSections.DEUTSCH, SettingsSections.findByKey("deutsch"))
        assertEquals(SettingsSections.APPEARANCE, SettingsSections.findByKey("appearance"))
        assertEquals(SettingsSections.GESTURES, SettingsSections.findByKey("gestures"))
        assertEquals(SettingsSections.BACKUP, SettingsSections.findByKey("backup"))

        // Indexed / operator access
        assertEquals(SettingsSections.HOME, SettingsSections["home"])
        assertEquals(SettingsSections.WELLBEING, SettingsSections["wellbeing"])
        assertEquals(SettingsSections.DEUTSCH, SettingsSections.get("deutsch"))
        assertEquals(SettingsSections.BACKUP, SettingsSections.byKey("backup"))
    }

    @Test
    fun `lookup by key is case-insensitive and trims whitespace`() {
        assertEquals(SettingsSections.HOME, SettingsSections.findByKey("  HOME  "))
        assertEquals(SettingsSections.WELLBEING, SettingsSections.findByKey("WellBeing"))
        assertEquals(SettingsSections.DEUTSCH, SettingsSections.findByKey("DEUTSCH"))
    }

    @Test
    fun `unknown key returns null`() {
        assertNull(SettingsSections.findByKey(null))
        assertNull(SettingsSections.findByKey(""))
        assertNull(SettingsSections.findByKey("   "))
        assertNull(SettingsSections.findByKey("unknown"))
        assertNull(SettingsSections.findByKey("not_a_section"))
        assertNull(SettingsSections["nonexistent"])
        assertNull(SettingsSections.get("invalid"))
    }

    @Test
    fun `findSectionByViewId returns correct section for row views and container ids`() {
        assertEquals(SettingsSections.HOME, SettingsSections.findSectionByViewId(R.id.homeSection))
        assertEquals(SettingsSections.HOME, SettingsSections.findSectionByViewId(R.id.homeAppsNum))
        assertEquals(SettingsSections.HOME, SettingsSections.findSectionByViewId(R.id.dateTime))

        assertEquals(SettingsSections.SHORTCUTS, SettingsSections.findSectionByViewId(R.id.shortcutIconsSection))
        assertEquals(SettingsSections.SHORTCUTS, SettingsSections.findSectionByViewId(R.id.shortcutIconsOnOff))

        assertEquals(SettingsSections.DRAWER, SettingsSections.findSectionByViewId(R.id.drawerSection))
        assertEquals(SettingsSections.DRAWER, SettingsSections.findSectionByViewId(R.id.autoShowKeyboard))
        assertEquals(SettingsSections.DRAWER, SettingsSections.findSectionByViewId(R.id.drawerBottomSearchLayout))

        assertEquals(SettingsSections.WELLBEING, SettingsSections.findSectionByViewId(R.id.wellbeingSection))
        assertEquals(SettingsSections.WELLBEING, SettingsSections.findSectionByViewId(R.id.pauseBeforeAppsLayout))
        assertEquals(SettingsSections.WELLBEING, SettingsSections.findSectionByViewId(R.id.distractingAppsLayout))
        assertEquals(SettingsSections.WELLBEING, SettingsSections.findSectionByViewId(R.id.screenTimeLayout))
        assertEquals(SettingsSections.WELLBEING, SettingsSections.findSectionByViewId(R.id.nudgeWaterLayout))

        assertEquals(SettingsSections.TODAY, SettingsSections.findSectionByViewId(R.id.todaySection))
        assertEquals(SettingsSections.TODAY, SettingsSections.findSectionByViewId(R.id.todayCardsLayout))
        assertEquals(SettingsSections.TODAY, SettingsSections.findSectionByViewId(R.id.glanceSection))
        assertEquals(SettingsSections.TODAY, SettingsSections.findSectionByViewId(R.id.weatherLayout))

        assertEquals(SettingsSections.DEUTSCH, SettingsSections.findSectionByViewId(R.id.deutschSection))
        assertEquals(SettingsSections.DEUTSCH, SettingsSections.findSectionByViewId(R.id.germanLayout))

        assertEquals(SettingsSections.APPEARANCE, SettingsSections.findSectionByViewId(R.id.appearanceSection))
        assertEquals(SettingsSections.APPEARANCE, SettingsSections.findSectionByViewId(R.id.photoWallpaper))
        assertEquals(SettingsSections.APPEARANCE, SettingsSections.findSectionByViewId(R.id.boldFont))

        assertEquals(SettingsSections.GESTURES, SettingsSections.findSectionByViewId(R.id.gesturesSection))
        assertEquals(SettingsSections.GESTURES, SettingsSections.findSectionByViewId(R.id.swipeRightLayout))
        assertEquals(SettingsSections.GESTURES, SettingsSections.findSectionByViewId(R.id.toggleLock))

        assertEquals(SettingsSections.BACKUP, SettingsSections.findSectionByViewId(R.id.backupSection))
        assertEquals(SettingsSections.BACKUP, SettingsSections.findSectionByViewId(R.id.exportBackupLayout))
        assertEquals(SettingsSections.BACKUP, SettingsSections.findSectionByViewId(R.id.importBackupLayout))

        assertNull(SettingsSections.findSectionByViewId(-1))
        assertNull(SettingsSections.findSectionByViewId(0))
    }

    @Test
    fun `findSectionByNameOrKey resolves section by name keywords`() {
        assertEquals(SettingsSections.WELLBEING, SettingsSections.findSectionByNameOrKey("pauseBeforeAppsLayout"))
        assertEquals(SettingsSections.WELLBEING, SettingsSections.findSectionByNameOrKey("pause"))
        assertEquals(SettingsSections.HOME, SettingsSections.findSectionByNameOrKey("home_screen"))
        assertEquals(SettingsSections.TODAY, SettingsSections.findSectionByNameOrKey("weather"))
        assertEquals(SettingsSections.TODAY, SettingsSections.findSectionByNameOrKey("glance"))
        assertEquals(SettingsSections.DEUTSCH, SettingsSections.findSectionByNameOrKey("german"))
        assertNull(SettingsSections.findSectionByNameOrKey(null))
        assertNull(SettingsSections.findSectionByNameOrKey(""))
        assertNull(SettingsSections.findSectionByNameOrKey("randomUnknown"))
    }

    @Test
    fun `resolveTarget prefers explicit section key over row id`() {
        assertEquals(
            SettingsSections.BACKUP,
            SettingsSections.resolveTarget(SettingsSections.KEY_BACKUP, R.id.pauseBeforeAppsOnOff),
        )
    }

    @Test
    fun `resolveTarget falls back to the section holding the row`() {
        assertEquals(SettingsSections.WELLBEING, SettingsSections.resolveTarget(null, R.id.pauseBeforeAppsOnOff))
        assertEquals(SettingsSections.DEUTSCH, SettingsSections.resolveTarget("not-a-section", R.id.germanUiOnOff))
    }

    @Test
    fun `resolveTarget returns null without a usable target`() {
        assertNull(SettingsSections.resolveTarget(null, 0))
        assertNull(SettingsSections.resolveTarget("todayFragment", 0))
    }

    @Test
    fun `initialSection uses explicit target only on first creation`() {
        val first = SettingsSections.initialSection(
            restored = false,
            restoredSectionKey = SettingsSections.KEY_BACKUP,
            explicitTarget = { SettingsSections.WELLBEING },
        )
        assertEquals(SettingsSections.WELLBEING, first)
    }

    @Test
    fun `initialSection keeps the restored section over the deep-link target`() {
        val restored = SettingsSections.initialSection(
            restored = true,
            restoredSectionKey = SettingsSections.KEY_BACKUP,
            explicitTarget = { SettingsSections.WELLBEING },
        )
        assertEquals(SettingsSections.BACKUP, restored)
    }

    @Test
    fun `initialSection keeps the section list after the user left the deep-linked section`() {
        val restored = SettingsSections.initialSection(
            restored = true,
            restoredSectionKey = null,
            explicitTarget = { SettingsSections.WELLBEING },
        )
        assertNull(restored)
    }
}
