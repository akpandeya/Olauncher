package app.olauncher.res

import app.olauncher.R
import app.olauncher.ui.settings.SettingsCatalog
import app.olauncher.ui.settings.SettingsHeadingStyle
import app.olauncher.ui.settings.SettingsItem
import app.olauncher.ui.settings.SettingsSections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Verifies the section order and ID preservation for Settings. Rows now come from
 * [SettingsCatalog] and one layout serves both orientations, so this checks:
 * - Section order: Thumbline header, Home screen, Shortcut icons, Drawer, Wellbeing, Today (with Glance),
 *   Deutsch, Appearance, Gestures, Backup
 * - Each section holds the expected rows (Drawer holds autoShowKeyboard and drawerBottomSearchLayout,
 *   Wellbeing holds the nudge rows under a sub-heading, Today holds the three Today rows and Glance holds weather)
 * - The set of android:ids equals main's: every id still resolves, and every id the old layout held is owned by the
 *   page frame, a catalog row, or a section heading (no id added or removed)
 * - There is no landscape copy of the layout; landscape spacing comes from values-land
 * - Required string resources are present and wellbeing_section is not added
 */
class SettingsSectionsResourceTest {

    private fun resolveFile(relative: String): File {
        return listOf(
            File(relative),
            File("app/$relative"),
        ).firstOrNull { it.isFile } ?: error("$relative not found from ${File(".").absolutePath}")
    }

    private fun parseXml(file: File): Document {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
    }

    private fun loadStrings(): Map<String, String> {
        val file = resolveFile("src/main/res/values/strings.xml")
        val doc = parseXml(file)
        val nodes = doc.getElementsByTagName("string")
        val result = mutableMapOf<String, String>()
        for (i in 0 until nodes.length) {
            val element = nodes.item(i) as Element
            var text = element.textContent
            if (text.length >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
                text = text.substring(1, text.length - 1)
            }
            result[element.getAttribute("name")] = text
        }
        return result
    }

    private val idNames: Map<Int, String> =
        R.id::class.java.fields.filter { it.type == Int::class.javaPrimitiveType }.associate { it.getInt(null) to it.name }

    private val stringNames: Map<Int, String> =
        R.string::class.java.fields.filter { it.type == Int::class.javaPrimitiveType }.associate { it.getInt(null) to it.name }

    private fun frameIds(): Set<String> {
        val doc = parseXml(resolveFile("src/main/res/layout/fragment_settings.xml"))
        val ids = mutableSetOf<String>()
        val all = doc.getElementsByTagName("*")
        for (i in 0 until all.length) {
            val id = (all.item(i) as Element).getAttribute("android:id")
            if (id.isNotEmpty()) ids.add(id.substringAfterLast("/"))
        }
        return ids
    }

    /** Names of every view id the rows of [sectionKey] own. */
    private fun rowIdNames(sectionKey: String): Set<String> =
        SettingsCatalog.rowsFor(sectionKey).flatMap { it.viewIds }.map { idNames.getValue(it) }.toSet()

    private fun headingNames(sectionKey: String): List<String> =
        SettingsCatalog.itemsFor(sectionKey).filterIsInstance<SettingsItem.Heading>().map { stringNames.getValue(it.titleRes) }

    @Test
    fun `strings contains drawer and today_section, reuses wellbeing and shortcut_icons, omits wellbeing_section`() {
        val strings = loadStrings()
        assertEquals("Drawer", strings["drawer"])
        assertEquals("Today", strings["today_section"])
        assertEquals("Wellbeing", strings["wellbeing"])
        assertEquals("Shortcut icons", strings["shortcut_icons"])
        assertNull("wellbeing_section must not be added", strings["wellbeing_section"])
    }

    @Test
    fun `section header order matches acceptance criteria`() {
        val expectedResourceHeaders = listOf(
            "home_screen",
            "shortcut_icons",
            "drawer",
            "wellbeing",
            "today_section",
            "glance",
            "german_section",
            "appearance",
            "gestures",
            "backup",
        )
        val strings = loadStrings()
        val expectedResolvedList = listOf(
            "Home screen",
            "Shortcut icons",
            "Drawer",
            "Wellbeing",
            "Today",
            "Glance",
            "German",
            "Appearance",
            "Gestures",
            "Backup",
        )
        assertEquals(expectedResolvedList, expectedResourceHeaders.map { strings[it] ?: error("Missing string: $it") })

        // The page heading of each section, then the Glance heading that sits inside Today.
        val actual = SettingsSections.all.flatMap { section ->
            listOf(stringNames.getValue(section.titleRes)) +
                SettingsCatalog.itemsFor(section.key)
                    .filterIsInstance<SettingsItem.Heading>()
                    .filter { it.style == SettingsHeadingStyle.CARD }
                    .map { stringNames.getValue(it.titleRes) }
        }
        assertEquals(expectedResourceHeaders, actual)
    }

    @Test
    fun `set of android ids equals main with no id added or removed`() {
        val frame = frameIds()
        val owned = mutableSetOf<String>()
        owned += SettingsCatalog.rows.flatMap { it.viewIds }.map { idNames.getValue(it) }
        owned += SettingsSections.all.map { idNames.getValue(it.containerId) }
        owned += SettingsCatalog.allItems.filterIsInstance<SettingsItem.Heading>().filter { it.id != 0 }.map { idNames.getValue(it.id) }
        // glanceSection heads the Glance block; the block is a heading inside Today now.
        owned += "glanceSection"

        val removed = EXPECTED_MAIN_IDS - frame - owned
        assertTrue("Ids dropped from Settings: $removed", removed.isEmpty())

        val unresolved = EXPECTED_MAIN_IDS.filter { name -> R.id::class.java.fields.none { it.name == name } }
        assertTrue("Ids that no longer resolve in R.id: $unresolved", unresolved.isEmpty())

        val added = (frame + owned) - EXPECTED_MAIN_IDS - setOf("settingsRows", "statusBarFade")
        assertTrue("Ids added to Settings: $added", added.isEmpty())
    }

    @Test
    fun `each section holds expected rows`() {
        val home = rowIdNames(SettingsSections.KEY_HOME)
        for (id in listOf(
            "homeAppsNum", "dateTime", "batteryMode", "batteryModeLayout", "batteryModeTitle", "infoLineLayout",
            "contextLineLayout", "homeNextEventLayout", "alignment", "swapSides", "swapSidesLayout",
        )) assertTrue("$id in Home screen", id in home)
        assertFalse("distractingApps must not be in Home screen", "distractingAppsLayout" in home)
        assertFalse("pauseBeforeApps must not be in Home screen", "pauseBeforeAppsLayout" in home)
        assertFalse("screenTime must not be in Home screen", "screenTimeLayout" in home)

        val shortcuts = rowIdNames(SettingsSections.KEY_SHORTCUTS)
        for (id in listOf(
            "shortcutIconsOnOffLayout", "shortcutCountLayout", "shortcutSizeLayout", "shortcutSideLayout",
            "shortcutHeightLayout", "shortcutIconStyleLayout",
        )) assertTrue("$id in Shortcut icons", id in shortcuts)

        val drawer = rowIdNames(SettingsSections.KEY_DRAWER)
        for (id in listOf(
            "autoShowKeyboard", "drawerBottomSearchLayout", "searchEngineLayout", "drawerNewFirstLayout",
            "drawerLetterStripLayout", "drawerRecentsLayout",
        )) assertTrue("$id in Drawer", id in drawer)

        val wellbeing = rowIdNames(SettingsSections.KEY_WELLBEING)
        for (id in listOf(
            "distractingAppsLayout", "pauseBeforeAppsLayout", "pauseAllowanceLayout", "pauseEscalateLayout",
            "pauseAskWhyLayout", "commitLockLayout", "holdNotificationsLayout", "quietReleaseTimesLayout",
            "screenTimeLayout", "nudgeWaterLayout", "nudgeWalkLayout", "nudgeSocialLayout", "nudgeActiveHoursLayout",
        )) assertTrue("$id in Wellbeing", id in wellbeing)

        // The nudges sub-heading sits inside Wellbeing, between the screen time rows and the nudge rows.
        val wellbeingItems = SettingsCatalog.itemsFor(SettingsSections.KEY_WELLBEING)
        val nudgesAt = wellbeingItems.indexOfFirst { it is SettingsItem.Heading && it.titleRes == R.string.nudges }
        assertTrue("nudges sub-heading must be in Wellbeing", nudgesAt >= 0)
        assertEquals(SettingsHeadingStyle.SUB, (wellbeingItems[nudgesAt] as SettingsItem.Heading).style)
        assertEquals(R.id.nudgesSection, (wellbeingItems[nudgesAt] as SettingsItem.Heading).id)
        val firstNudgeAt = wellbeingItems.indexOfFirst { it is SettingsItem.Row && it.spec.primaryId == R.id.nudgeWater }
        assertEquals("nudge rows follow their sub-heading", nudgesAt + 1, firstNudgeAt)

        // Today: its three rows, then the Glance block with weather.
        val todayItems = SettingsCatalog.itemsFor(SettingsSections.KEY_TODAY)
        val glanceAt = todayItems.indexOfFirst { it is SettingsItem.Heading && it.titleRes == R.string.glance }
        assertTrue("Glance heading must be in Today", glanceAt > 0)
        val beforeGlance = todayItems.take(glanceAt).filterIsInstance<SettingsItem.Row>().map { it.spec.primaryId }
        assertEquals(listOf(R.id.todayCards, R.id.todayOrder, R.id.intentionOnHome), beforeGlance)
        val afterGlance = todayItems.drop(glanceAt + 1).filterIsInstance<SettingsItem.Row>().map { it.spec.primaryId }
        assertEquals(listOf(R.id.weather), afterGlance)
        val today = rowIdNames(SettingsSections.KEY_TODAY)
        for (id in listOf("weatherLayout", "weatherTitle", "weather")) assertTrue("$id in Today", id in today)

        val deutsch = rowIdNames(SettingsSections.KEY_DEUTSCH)
        for (id in listOf(
            "germanLayout", "germanClockLayout", "germanLookupLayout", "germanPauseWordLayout", "germanUiLayout",
        )) assertTrue("$id in Deutsch", id in deutsch)

        val appearance = rowIdNames(SettingsSections.KEY_APPEARANCE)
        for (id in listOf(
            "photoWallpaperLayout", "statusBar", "appThemeText", "accent", "fontFamily", "boldFont",
            "textSizeValue", "haptics", "textContrastLayout",
        )) assertTrue("$id in Appearance", id in appearance)
        assertFalse("autoShowKeyboard moved to Drawer", "autoShowKeyboard" in appearance)
        assertFalse("drawerBottomSearch moved to Drawer", "drawerBottomSearchLayout" in appearance)

        val gestures = rowIdNames(SettingsSections.KEY_GESTURES)
        for (id in listOf("swipeLeftApp", "swipeRightLayout", "toggleLock")) assertTrue("$id in Gestures", id in gestures)
        assertFalse("todayCardsLayout moved to Today", "todayCardsLayout" in gestures)

        val backup = rowIdNames(SettingsSections.KEY_BACKUP)
        for (id in listOf("exportBackupLayout", "importBackupLayout")) assertTrue("$id in Backup", id in backup)
    }

    @Test
    fun `section list rows order matches expected sections`() {
        val expectedSectionRowIds = listOf(
            "sectionRowHome",
            "sectionRowShortcuts",
            "sectionRowDrawer",
            "sectionRowWellbeing",
            "sectionRowToday",
            "sectionRowDeutsch",
            "sectionRowAppearance",
            "sectionRowGestures",
            "sectionRowBackup",
        )
        val expectedSectionRowTexts = listOf(
            "@string/home_screen",
            "@string/shortcut_icons",
            "@string/drawer",
            "@string/wellbeing",
            "@string/today_section",
            "@string/german_section",
            "@string/appearance",
            "@string/gestures",
            "@string/backup",
            "@string/privacy",
        )

        val file = resolveFile("src/main/res/layout/fragment_settings.xml")
        val doc = parseXml(file)
        val allLinear = doc.getElementsByTagName("LinearLayout")
        var sectionList: Element? = null
        for (i in 0 until allLinear.length) {
            val el = allLinear.item(i) as Element
            if (el.getAttribute("android:id").endsWith("sectionListLayout")) {
                sectionList = el
                break
            }
        }
        requireNotNull(sectionList) { "sectionListLayout not found in ${file.name}" }

        val rowIds = mutableListOf<String>()
        val rowTexts = mutableListOf<String>()
        val children = sectionList.getElementsByTagName("TextView")
        for (i in 0 until children.length) {
            val tv = children.item(i) as Element
            if (tv.getAttribute("style") == "@style/SettingsSectionHint") continue // hint line under a row
            val idAttr = tv.getAttribute("android:id")
            if (idAttr.isNotEmpty()) {
                rowIds.add(idAttr.substringAfterLast("/"))
            }
            rowTexts.add(tv.getAttribute("android:text"))
            assertEquals("TextLarge style on row in ${file.name}", "@style/TextLarge", tv.getAttribute("style"))
        }

        assertEquals("Section row IDs in ${file.name}", expectedSectionRowIds, rowIds)
        assertEquals("Section row texts in ${file.name}", expectedSectionRowTexts, rowTexts)
    }

    @Test
    fun `settings back row has expected id and text`() {
        val file = resolveFile("src/main/res/layout/fragment_settings.xml")
        val doc = parseXml(file)
        val allLinear = doc.getElementsByTagName("LinearLayout")
        var backLayout: Element? = null
        for (i in 0 until allLinear.length) {
            val el = allLinear.item(i) as Element
            if (el.getAttribute("android:id").endsWith("settingsBackLayout")) {
                backLayout = el
                break
            }
        }
        requireNotNull(backLayout) { "settingsBackLayout not found in ${file.name}" }

        val textViews = backLayout.getElementsByTagName("TextView")
        assertEquals("One TextView in settingsBackLayout in ${file.name}", 1, textViews.length)
        val tv = textViews.item(0) as Element
        assertEquals("settingsBack", tv.getAttribute("android:id").substringAfterLast("/"))
        assertEquals("@string/settings_back", tv.getAttribute("android:text"))
        assertEquals("@style/TextLarge", tv.getAttribute("style"))
    }

    @Test
    fun `settings rows are drawn by one list and there is no landscape copy`() {
        val layout = parseXml(resolveFile("src/main/res/layout/fragment_settings.xml"))
        val recyclers = layout.getElementsByTagName("androidx.recyclerview.widget.RecyclerView")
        assertEquals("one list holds every row", 1, recyclers.length)
        assertEquals("@+id/settingsRows", (recyclers.item(0) as Element).getAttribute("android:id"))

        val resDir = resolveFile("src/main/res/layout/fragment_settings.xml").parentFile.parentFile
        assertFalse("layout-land/fragment_settings.xml must stay deleted", File(resDir, "layout-land/fragment_settings.xml").exists())

        // Landscape spacing is a resource override of the same names, not a second layout.
        fun settingsDimens(file: File): Set<String> {
            val nodes = parseXml(file).getElementsByTagName("dimen")
            return (0 until nodes.length).map { (nodes.item(it) as Element).getAttribute("name") }
                .filter { it.startsWith("settings_") }.toSet()
        }
        val portrait = settingsDimens(File(resDir, "values/dimens.xml"))
        val landscape = settingsDimens(File(resDir, "values-land/dimens.xml"))
        assertTrue("settings_ dimens exist", portrait.isNotEmpty())
        assertEquals("values-land overrides every settings_ dimen", portrait, landscape)
    }

    companion object {
        val EXPECTED_MAIN_IDS = setOf(
            "accent",
            "alignment",
            "appInfo",
            "appThemeText",
            "appearanceSection",
            "autoShowKeyboard",
            "backupSection",
            "batteryMode",
            "batteryModeLayout",
            "batteryModeTitle",
            "infoLine",
            "infoLineLayout",
            "musicPulse",
            "musicPulseLayout",
            "musicPulseTitle",
            "hapticStrength",
            "hapticStrengthLayout",
            "hapticStrengthSeekBar",
            "hapticStrengthTitle",
            "hapticDuration",
            "hapticDurationLayout",
            "hapticDurationSeekBar",
            "hapticDurationTitle",
            "glowStrength",
            "glowStrengthLayout",
            "glowStrengthSeekBar",
            "glowStrengthTitle",
            "weatherEffects",
            "weatherEffectsLayout",
            "weatherEffectsTitle",
            "infoLineTitle",
            "homeSetsLayout",
            "homeSetsTitle",
            "editMorningAppsLayout",
            "editMorningAppsTitle",
            "editEveningAppsLayout",
            "editEveningAppsTitle",
            "morningStartLayout",
            "morningStartTitle",
            "dayStartLayout",
            "dayStartTitle",
            "eveningStartLayout",
            "eveningStartTitle",
            "profileSwitchLayout",
            "profileSwitchTitle",
            "profileSwitch",
            "profileScheduleLayout",
            "profileScheduleTitle",
            "profileSchedule",
            "homeSets",
            "morningStart",
            "dayStart",
            "eveningStart",
            "boldFont",
            "windDownLayout",
            "windDownTitle",
            "windDown",
            "windDownGreyLayout",
            "windDownGreyTitle",
            "windDownGreyOnOff",
            "systemGreyLayout",
            "systemGreyTitle",
            "systemGreyStatus",
            "systemGreyActions",
            "systemGreyCopy",
            "systemGreyBedtime",
            "systemGreyOnOff",
            "commitLockLayout",
            "commitLockOnOff",
            "commitLockSubtitle",
            "commitLockTitle",
            "contextLineLayout",
            "contextLineTitle",
            "contextLineValue",
            "homeNextEventLayout",
            "homeNextEventTitle",
            "homeNextEventValue",
            "homeNotesLayout",
            "homeNotesTitle",
            "homeNotesValue",
            "dateTime",
            "deutschSection",
            "distractingApps",
            "distractingAppsLayout",
            "distractingAppsTitle",
            "drawerBottomSearch",
            "drawerBottomSearchLayout",
            "drawerBottomSearchTitle",
            "drawerLetterStrip",
            "drawerLetterStripLayout",
            "drawerLetterStripTitle",
            "drawerNewFirst",
            "drawerNewFirstLayout",
            "drawerNewFirstTitle",
            "drawerRecents",
            "drawerRecentsLayout",
            "drawerRecentsTitle",
            "autoBackupLayout",
            "autoBackupTitle",
            "autoBackup",
            "autoBackupStatus",
            "drawerSection",
            "searchContacts",
            "searchContactsLayout",
            "searchContactsTitle",
            "currencyRates",
            "currencyRatesLayout",
            "currencyRatesTitle",
            "exportBackup",
            "exportBackupLayout",
            "fontFamily",
            "footer",
            "germanClock",
            "germanClockLayout",
            "germanClockTitle",
            "germanLayout",
            "germanLookupLayout",
            "germanLookupOnOff",
            "germanSpellSuggestLayout",
            "germanSpellSuggestTitle",
            "germanSpellSuggestSubtitle",
            "germanSpellSuggestOnOff",
            "licenseNote",
            "germanScreenPickLayout",
            "germanScreenPickTitle",
            "germanScreenPickSubtitle",
            "germanScreenPickOnOff",
            "germanLookupSubtitle",
            "germanLookupTitle",
            "germanOnOff",
            "germanPauseWordLayout",
            "germanPauseWordOnOff",
            "germanPauseWordTitle",
            "germanTitle",
            "germanUiLayout",
            "germanUiOnOff",
            "germanUiTitle",
            "gesturesSection",
            "glanceSection",
            "haptics",
            "healthConnectLayout",
            "healthConnectOnOff",
            "healthConnectTitle",
            "holdNotificationsLayout",
            "holdNotificationsOnOff",
            "holdNotificationsTitle",
            "homeAppsNum",
            "homeSection",
            "importBackup",
            "importBackupLayout",
            "mainActivityLayout",
            "nudgeActiveHours",
            "nudgeActiveHoursLayout",
            "nudgeActiveHoursTitle",
            "nudgeSocial",
            "nudgeSocialLayout",
            "nudgeSocialTitle",
            "nudgeWalk",
            "nudgeWalkLayout",
            "nudgeWalkTitle",
            "nudgeWater",
            "nudgeWaterLayout",
            "nudgeWaterTitle",
            "nudgesSection",
            "olauncherHiddenApps",
            "pauseAllowance",
            "pauseAllowanceLayout",
            "pauseAllowanceTitle",
            "pauseAskWhyLayout",
            "pauseAskWhyOnOff",
            "pauseAskWhyTitle",
            "pauseBeforeAppsLayout",
            "pauseBeforeAppsOnOff",
            "pauseBeforeAppsTitle",
            "pauseEscalateLayout",
            "pauseEscalateOnOff",
            "pauseEscalateTitle",
            "photoWallpaper",
            "photoWallpaperLayout",
            "photoWallpaperTitle",
            "quietReleaseTimes",
            "quietReleaseTimesLayout",
            "quietReleaseTimesTitle",
            "screenTimeLayout",
            "screenTimeOnOff",
            "screenTimeStyleLayout",
            "screenTimeStyleTitle",
            "screenTimeStyle",
            "screenTimeTargetLayout",
            "screenTimeTargetTitle",
            "screenTimeTarget",
            "scrollLayout",
            "scrollView",
            "searchEngine",
            "searchEngineLayout",
            "searchEngineTitle",
            "sectionListLayout",
            "sectionRowAppearance",
            "sectionRowBackup",
            "sectionRowDeutsch",
            "sectionRowDrawer",
            "sectionRowGestures",
            "sectionRowHome",
            "sectionRowShortcuts",
            "sectionRowToday",
            "sectionRowWellbeing",
            "setLauncher",
            "settingsBack",
            "settingsBackLayout",
            "settingsSearch",
            "settingsSearchContainer",
            "shortcutCount",
            "shortcutCountLayout",
            "shortcutCountTitle",
            "shortcutHeight",
            "shortcutHeightLayout",
            "shortcutHeightTitle",
            "shortcutIconStyle",
            "shortcutIconStyleLayout",
            "shortcutIconStyleTitle",
            "shortcutIconsOnOff",
            "shortcutIconsOnOffLayout",
            "shortcutIconsOnOffTitle",
            "shortcutIconsSection",
            "shortcutSide",
            "shortcutSideLayout",
            "shortcutSideTitle",
            "shortcutSize",
            "shortcutSizeLayout",
            "shortcutSizeTitle",
            "statusBar",
            "swapSides",
            "swapSidesLayout",
            "swipeLeftApp",
            "swipeRightApp",
            "swipeRightLayout",
            "textContrast",
            "textContrastLayout",
            "textContrastTitle",
            "homeDarkness",
            "homeWashLayout",
            "homeWashTitle",
            "textBrightness",
            "textBrightnessLayout",
            "textBrightnessTitle",
            "textSizeValue",
            "thumblineHeaderCard",
            "todayCards",
            "todayCardsLayout",
            "todayCardsTitle",
            "todayOrder",
            "todayOrderLayout",
            "todayOrderTitle",
            "intentionOnHome",
            "intentionOnHomeLayout",
            "intentionOnHomeTitle",
            "todaySection",
            "toggleLock",
            "doubleTapAction",
            "clockTapAction",
            "dateTapAction",
            "weather",
            "weatherLayout",
            "weatherTitle",
            "wellbeingSection",
        )
    }
}
