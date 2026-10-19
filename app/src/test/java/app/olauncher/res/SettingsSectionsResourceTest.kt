package app.olauncher.res

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Verifies the section order and ID preservation for Settings:
 * - Section order in portrait and landscape:
 *   Thumbline header · Home screen · Shortcut icons · Drawer · Wellbeing · Today · Deutsch · Appearance · Gestures · Backup
 * - Drawer holds autoShowKeyboard and drawerBottomSearchLayout
 * - Wellbeing holds distracting apps, pause before distracting apps, screen time and the four nudge rows
 *   with nudges as a TextSmallLight sub-heading
 * - Today holds todayCardsLayout only; swipeRightLayout stays in Gestures
 * - The set of android:ids equals main's (no id added or removed)
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

    private fun extractAllIds(doc: Document): Set<String> {
        val ids = mutableSetOf<String>()
        val allNodes = doc.getElementsByTagName("*")
        for (i in 0 until allNodes.length) {
            val element = allNodes.item(i) as Element
            val idAttr = element.getAttribute("android:id")
            if (idAttr.isNotEmpty()) {
                ids.add(idAttr.substringAfterLast("/"))
            }
        }
        return ids
    }

    private fun extractSectionLinearChildren(doc: Document): List<Element> {
        val allLinear = doc.getElementsByTagName("LinearLayout")
        var scrollLayout: Element? = null
        for (i in 0 until allLinear.length) {
            val el = allLinear.item(i) as Element
            if (el.getAttribute("android:id").endsWith("scrollLayout")) {
                scrollLayout = el
                break
            }
        }
        requireNotNull(scrollLayout) { "scrollLayout not found" }
        val sectionCards = mutableListOf<Element>()
        val children = scrollLayout.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE && (child as Element).tagName == "LinearLayout") {
                val id = child.getAttribute("android:id")
                if (!id.endsWith("settingsBackLayout") && !id.endsWith("sectionListLayout")) {
                    sectionCards.add(child)
                }
            }
        }
        return sectionCards
    }

    private fun extractSectionHeaders(doc: Document): List<String> {
        val cards = extractSectionLinearChildren(doc)
        val headers = mutableListOf<String>()
        for (card in cards) {
            val textViews = card.getElementsByTagName("TextView")
            for (i in 0 until textViews.length) {
                val element = textViews.item(i) as Element
                if (element.getAttribute("style") == "@style/TextLarge") {
                    val textAttr = element.getAttribute("android:text")
                    if (textAttr.isNotEmpty()) {
                        headers.add(textAttr)
                    }
                }
            }
        }
        return headers
    }

    private fun collectDescendantIds(element: Element): Set<String> {
        val ids = mutableSetOf<String>()
        val elId = element.getAttribute("android:id")
        if (elId.isNotEmpty()) {
            ids.add(elId.substringAfterLast("/"))
        }
        val descendants = element.getElementsByTagName("*")
        for (i in 0 until descendants.length) {
            val item = descendants.item(i) as Element
            val id = item.getAttribute("android:id")
            if (id.isNotEmpty()) {
                ids.add(id.substringAfterLast("/"))
            }
        }
        return ids
    }

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
    fun `section header order matches acceptance criteria in portrait and landscape`() {
        val expectedResourceHeaders = listOf(
            "@string/app_name",
            "@string/home_screen",
            "@string/shortcut_icons",
            "@string/drawer",
            "@string/wellbeing",
            "@string/today_section",
            "@string/glance",
            "@string/deutsch_de",
            "@string/appearance",
            "@string/gestures",
            "@string/backup",
        )

        val strings = loadStrings()
        val expectedResolvedTitles = expectedResourceHeaders.map { res ->
            val key = res.removePrefix("@string/")
            strings[key] ?: error("Missing string: $key")
        }

        val expectedResolvedList = listOf(
            "Thumbline",
            "Home screen",
            "Shortcut icons",
            "Drawer",
            "Wellbeing",
            "Today",
            "Glance",
            "Deutsch",
            "Appearance",
            "Gestures",
            "Backup",
        )
        assertEquals(expectedResolvedList, expectedResolvedTitles)

        val layoutFiles = listOf(
            resolveFile("src/main/res/layout/fragment_settings.xml"),
            resolveFile("src/main/res/layout-land/fragment_settings.xml"),
        )

        for (file in layoutFiles) {
            val doc = parseXml(file)
            val actualHeaders = extractSectionHeaders(doc)
            assertEquals("Section headers in ${file.name}", expectedResourceHeaders, actualHeaders)
        }
    }

    @Test
    fun `set of android ids equals main with no id added or removed`() {
        val layoutFiles = listOf(
            resolveFile("src/main/res/layout/fragment_settings.xml"),
            resolveFile("src/main/res/layout-land/fragment_settings.xml"),
        )

        for (file in layoutFiles) {
            val doc = parseXml(file)
            val actualIds = extractAllIds(doc)

            val added = actualIds - EXPECTED_MAIN_IDS
            val removed = EXPECTED_MAIN_IDS - actualIds

            assertTrue("No id added in ${file.name}: $added", added.isEmpty())
            assertTrue("No id removed in ${file.name}: $removed", removed.isEmpty())
            assertEquals("Set of IDs in ${file.name} must equal main's", EXPECTED_MAIN_IDS, actualIds)
        }
    }

    @Test
    fun `each section holds expected rows and layout structure`() {
        val layoutFiles = listOf(
            resolveFile("src/main/res/layout/fragment_settings.xml"),
            resolveFile("src/main/res/layout-land/fragment_settings.xml"),
        )

        for (file in layoutFiles) {
            val doc = parseXml(file)
            val sections = extractSectionLinearChildren(doc)
            assertEquals("Expected 11 section cards in ${file.name}", 11, sections.size)

            val sectionIds = sections.map { collectDescendantIds(it) }

            // Section 0: Thumbline header
            assertTrue(sectionIds[0].contains("olauncherHiddenApps"))
            assertTrue(sectionIds[0].contains("appInfo"))
            assertTrue(sectionIds[0].contains("setLauncher"))

            // Section 1: Home screen
            assertTrue(sectionIds[1].contains("homeAppsNum"))
            assertTrue(sectionIds[1].contains("dateTime"))
            assertTrue(sectionIds[1].contains("batteryMode"))
            assertTrue(sectionIds[1].contains("batteryModeLayout"))
            assertTrue(sectionIds[1].contains("batteryModeTitle"))
            assertTrue(sectionIds[1].contains("infoLineLayout"))
            assertTrue(sectionIds[1].contains("contextLineLayout"))
            assertTrue(sectionIds[1].contains("alignment"))
            assertTrue(sectionIds[1].contains("swapSides"))
            assertTrue(sectionIds[1].contains("swapSidesLayout"))
            assertFalse("distractingApps must not be in Home screen", sectionIds[1].contains("distractingAppsLayout"))
            assertFalse("pauseBeforeApps must not be in Home screen", sectionIds[1].contains("pauseBeforeAppsLayout"))
            assertFalse("screenTime must not be in Home screen", sectionIds[1].contains("screenTimeLayout"))

            // Section 2: Shortcut icons
            assertTrue(sectionIds[2].contains("shortcutIconsSection"))
            assertTrue(sectionIds[2].contains("shortcutIconsOnOffLayout"))
            assertTrue(sectionIds[2].contains("shortcutCountLayout"))
            assertTrue(sectionIds[2].contains("shortcutSizeLayout"))
            assertTrue(sectionIds[2].contains("shortcutSideLayout"))
            assertTrue(sectionIds[2].contains("shortcutHeightLayout"))
            assertTrue(sectionIds[2].contains("shortcutIconStyleLayout"))

            // Section 3: Drawer
            assertTrue(sectionIds[3].contains("autoShowKeyboard"))
            assertTrue(sectionIds[3].contains("drawerBottomSearchLayout"))
            assertTrue(sectionIds[3].contains("searchEngineLayout"))
            assertTrue(sectionIds[3].contains("drawerNewFirstLayout"))
            assertTrue(sectionIds[3].contains("drawerLetterStripLayout"))
            assertTrue(sectionIds[3].contains("drawerRecentsLayout"))

            // Section 4: Wellbeing
            assertTrue(sectionIds[4].contains("distractingAppsLayout"))
            assertTrue(sectionIds[4].contains("pauseBeforeAppsLayout"))
            assertTrue(sectionIds[4].contains("pauseAllowanceLayout"))
            assertTrue(sectionIds[4].contains("pauseEscalateLayout"))
            assertTrue(sectionIds[4].contains("pauseAskWhyLayout"))
            assertTrue(sectionIds[4].contains("commitLockLayout"))
            assertTrue(sectionIds[4].contains("holdNotificationsLayout"))
            assertTrue(sectionIds[4].contains("quietReleaseTimesLayout"))
            assertTrue(sectionIds[4].contains("screenTimeLayout"))
            assertTrue(sectionIds[4].contains("nudgesSection"))
            assertTrue(sectionIds[4].contains("nudgeWaterLayout"))
            assertTrue(sectionIds[4].contains("nudgeWalkLayout"))
            assertTrue(sectionIds[4].contains("nudgeSocialLayout"))
            assertTrue(sectionIds[4].contains("nudgeActiveHoursLayout"))

            // Verify nudges sub-heading has TextSmallLight style inside Wellbeing
            val wellbeingTextViews = sections[4].getElementsByTagName("TextView")
            var nudgesSubheadingFound = false
            for (j in 0 until wellbeingTextViews.length) {
                val tv = wellbeingTextViews.item(j) as Element
                if (tv.getAttribute("android:text") == "@string/nudges") {
                    assertEquals(
                        "nudges must be sub-heading TextSmallLight in ${file.name}",
                        "@style/TextSmallLight",
                        tv.getAttribute("style")
                    )
                    nudgesSubheadingFound = true
                }
            }
            assertTrue("nudges sub-heading must be in Wellbeing section in ${file.name}", nudgesSubheadingFound)

            // Section 5: Today
            assertTrue(sectionIds[5].contains("todayCardsLayout"))
            assertEquals(
                "Today section holds todayCardsLayout only",
                setOf("todaySection", "todayCardsLayout", "todayCardsTitle", "todayCards"),
                sectionIds[5]
            )

            // Section 6: Glance
            assertTrue(sectionIds[6].contains("weatherLayout"))
            assertTrue(sectionIds[6].contains("weatherTitle"))
            assertTrue(sectionIds[6].contains("weather"))

            // Section 7: Deutsch
            assertTrue(sectionIds[7].contains("deutschSection"))
            assertTrue(sectionIds[7].contains("germanLayout"))
            assertTrue(sectionIds[7].contains("germanClockLayout"))
            assertTrue(sectionIds[7].contains("germanLookupLayout"))
            assertTrue(sectionIds[7].contains("germanPauseWordLayout"))
            assertTrue(sectionIds[7].contains("germanUiLayout"))

            // Section 8: Appearance
            assertTrue(sectionIds[8].contains("photoWallpaperLayout"))
            assertTrue(sectionIds[8].contains("statusBar"))
            assertTrue(sectionIds[8].contains("appThemeText"))
            assertTrue(sectionIds[8].contains("accent"))
            assertTrue(sectionIds[8].contains("fontFamily"))
            assertTrue(sectionIds[8].contains("boldFont"))
            assertTrue(sectionIds[8].contains("textSizeValue"))
            assertTrue(sectionIds[8].contains("haptics"))
            assertTrue(sectionIds[8].contains("textContrastLayout"))
            assertFalse("autoShowKeyboard moved to Drawer", sectionIds[8].contains("autoShowKeyboard"))
            assertFalse("drawerBottomSearch moved to Drawer", sectionIds[8].contains("drawerBottomSearchLayout"))

            // Section 9: Gestures
            assertTrue(sectionIds[9].contains("swipeLeftApp"))
            assertTrue(sectionIds[9].contains("swipeRightLayout"))
            assertTrue(sectionIds[9].contains("toggleLock"))
            assertFalse("todayCardsLayout moved to Today", sectionIds[9].contains("todayCardsLayout"))

            // Section 10: Backup
            assertTrue(sectionIds[10].contains("backupSection"))
            assertTrue(sectionIds[10].contains("exportBackupLayout"))
            assertTrue(sectionIds[10].contains("importBackupLayout"))
        }
    }

    @Test
    fun `section list rows order matches expected sections in portrait and landscape`() {
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
            "@string/deutsch_de",
            "@string/appearance",
            "@string/gestures",
            "@string/backup",
        )

        val layoutFiles = listOf(
            resolveFile("src/main/res/layout/fragment_settings.xml"),
            resolveFile("src/main/res/layout-land/fragment_settings.xml"),
        )

        for (file in layoutFiles) {
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
    }

    @Test
    fun `settings back row has expected id and text in portrait and landscape`() {
        val layoutFiles = listOf(
            resolveFile("src/main/res/layout/fragment_settings.xml"),
            resolveFile("src/main/res/layout-land/fragment_settings.xml"),
        )

        for (file in layoutFiles) {
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
            "infoLineTitle",
            "boldFont",
            "commitLockLayout",
            "commitLockOnOff",
            "commitLockSubtitle",
            "commitLockTitle",
            "contextLineLayout",
            "contextLineTitle",
            "contextLineValue",
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
            "textSizeValue",
            "thumblineHeaderCard",
            "todayCards",
            "todayCardsLayout",
            "todayCardsTitle",
            "todaySection",
            "toggleLock",
            "weather",
            "weatherLayout",
            "weatherTitle",
            "wellbeingSection",
        )
    }
}
