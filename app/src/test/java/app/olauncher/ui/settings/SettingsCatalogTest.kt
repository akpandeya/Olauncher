package app.olauncher.ui.settings

import android.content.ContextWrapper
import android.content.SharedPreferences
import app.olauncher.R
import app.olauncher.data.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCatalogTest {

    private val idNames: Map<Int, String> =
        R.id::class.java.fields.filter { it.type == Int::class.javaPrimitiveType }.associate { it.getInt(null) to it.name }

    private val stringNames: Map<Int, String> =
        R.string::class.java.fields.filter { it.type == Int::class.javaPrimitiveType }.associate { it.getInt(null) to it.name }

    private fun idName(id: Int): String = idNames[id] ?: error("No R.id with value $id")

    @Test
    fun `every setting of the old layout is present in its section`() {
        val actual = SettingsSections.all.associate { it.key to SettingsCatalog.rowsFor(it.key).map { row -> row.key } }
        assertEquals(INVENTORY, actual)
        assertEquals(81, SettingsCatalog.rows.size)
        assertEquals(80, SettingsCatalog.rows.count { it.searchable })
    }

    @Test
    fun `row keys are unique and name the primary id`() {
        val keys = SettingsCatalog.rows.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        for (row in SettingsCatalog.rows) {
            assertEquals(row.key, idName(row.primaryId))
            assertSame(row, SettingsCatalog.rowByKey(row.key))
        }
    }

    @Test
    fun `no view id is owned by two rows`() {
        val owners = mutableMapOf<Int, String>()
        for (row in SettingsCatalog.rows) {
            for (id in row.viewIds) {
                val previous = owners.put(id, row.key)
                assertNull("${idName(id)} is owned by both $previous and ${row.key}", previous)
            }
        }
    }

    @Test
    fun `every row is well formed for its type and shape`() {
        for (row in SettingsCatalog.rows) {
            val where = row.key
            assertTrue("$where title", row.titleRes != 0)
            assertTrue("$where primary id is one of its view ids", row.primaryId in row.viewIds)
            when (row.type) {
                SettingsRowType.SLIDER -> {
                    assertEquals("$where shape", SettingsRowShape.SLIDER, row.shape)
                    assertTrue("$where slider id", row.seekBarId != 0)
                    assertTrue("$where value id", row.valueId != 0)
                }
                SettingsRowType.ACTION -> assertEquals("$where has no value to show", 0, row.valueId)
                SettingsRowType.TOGGLE, SettingsRowType.CHOICE -> assertTrue("$where value id", row.valueId != 0)
                SettingsRowType.STATUS -> {
                    assertEquals("$where shape", SettingsRowShape.STATUS, row.shape)
                    assertFalse("$where is not searchable", row.searchable)
                }
            }
            if (row.shape != SettingsRowShape.SLIDER) assertEquals("$where slider id", 0, row.seekBarId)
            assertEquals("$where action labels", row.actionIds.size, row.actionLabelRes.size)
            assertTrue("$where action ids fit the two action views", row.actionIds.size <= 2)
            assertEquals("$where actions need a container", row.actionIds.isNotEmpty(), row.actionsContainerId != 0)
        }
    }

    @Test
    fun `rows sit in the section they name and every section has rows`() {
        for (section in SettingsSections.all) {
            val rows = SettingsCatalog.rowsFor(section.key)
            assertTrue("${section.key} has rows", rows.isNotEmpty())
            for (row in rows) assertEquals(section.key, row.sectionKey)
        }
        assertEquals(SettingsCatalog.rows, SettingsSections.all.flatMap { SettingsCatalog.rowsFor(it.key) })
    }

    @Test
    fun `row titles are string resources that exist and are not empty`() {
        val file = listOf(java.io.File("src/main/res/values/strings.xml"), java.io.File("app/src/main/res/values/strings.xml"))
            .first { it.isFile }
        val text = file.readText()
        val resources = SettingsCatalog.rows.flatMap { listOf(it.titleRes, it.subtitleRes) + it.actionLabelRes.toList() }
        for (res in resources.filter { it != 0 }) {
            val name = stringNames[res] ?: error("No R.string with value $res")
            val match = Regex("<string name=\"$name\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL).find(text)
            assertNotNull("$name is defined in values/strings.xml", match)
            assertTrue("$name is not empty", match!!.groupValues[1].isNotBlank())
        }
    }

    @Test
    fun `any view id of a row finds the row`() {
        for (row in SettingsCatalog.rows) {
            for (id in row.viewIds) assertSame(idName(id), row, SettingsCatalog.rowByViewId(id))
            assertEquals(row.sectionKey, SettingsCatalog.sectionKeyOfViewId(row.primaryId))
        }
        assertNull(SettingsCatalog.rowByViewId(0))
        assertNull(SettingsCatalog.sectionKeyOfViewId(R.id.footer))
    }

    @Test
    fun `section containers and sub-headings resolve to their section`() {
        for (section in SettingsSections.all) {
            assertEquals(section.key, SettingsCatalog.sectionKeyOfViewId(section.containerId))
        }
        assertEquals(SettingsSections.KEY_TODAY, SettingsCatalog.sectionKeyOfViewId(R.id.glanceSection))
        assertEquals(SettingsSections.KEY_WELLBEING, SettingsCatalog.sectionKeyOfViewId(R.id.nudgesSection))
        assertEquals(SettingsSections.WELLBEING, SettingsSections.findSectionByViewId(R.id.pauseBeforeAppsOnOff))
        assertEquals(SettingsSections.TODAY, SettingsSections.findSectionByViewId(R.id.weatherTitle))
        assertEquals(SettingsSections.WELLBEING, SettingsSections.findSectionByViewId(SettingsDeepLinks.PAUSE.rowViewId))
    }

    @Test
    fun `rows hidden by a parent switch are rows of the list`() {
        val hiddenWhenOff = SettingsCatalog.rows.filter {
            !SettingsRowAvailability.isAvailable(it.primaryId, shortcutsOn = false, germanOn = false)
        }
        assertEquals(
            setOf(
                "shortcutCount", "shortcutSize", "shortcutSide", "shortcutHeight", "shortcutIconStyle",
                "germanClock", "germanLookupOnOff", "germanSpellSuggestOnOff", "germanPauseWordOnOff", "germanUiOnOff",
            ),
            hiddenWhenOff.map { it.key }.toSet(),
        )
        assertTrue(SettingsCatalog.rows.all { SettingsRowAvailability.isAvailable(it.primaryId, shortcutsOn = true, germanOn = true) })
    }

    @Test
    fun `search shows matching rows under their section heading in display order`() {
        val items = SettingsCatalog.itemsForSearch(setOf(R.id.searchEngine, R.id.drawerBottomSearch, R.id.alignment))
        assertEquals(
            listOf(
                "heading:${R.string.home_screen}",
                "row:alignment",
                "heading:${R.string.drawer}",
                "row:drawerBottomSearch",
                "row:searchEngine",
            ),
            items.map { describe(it) },
        )
    }

    @Test
    fun `search shows a sub-heading only with a row under it`() {
        val withNudge = SettingsCatalog.itemsForSearch(setOf(R.id.nudgeWater)).map { describe(it) }
        assertEquals(
            listOf("heading:${R.string.wellbeing}", "heading:${R.string.nudges}", "row:nudgeWater"),
            withNudge,
        )
        val withoutNudge = SettingsCatalog.itemsForSearch(setOf(R.id.distractingApps)).map { describe(it) }
        assertEquals(listOf("heading:${R.string.wellbeing}", "row:distractingApps"), withoutNudge)
    }

    @Test
    fun `search for weather leads with the Glance heading, not a second Today heading`() {
        val items = SettingsCatalog.itemsForSearch(setOf(R.id.weather)).map { describe(it) }
        assertEquals(listOf("heading:${R.string.glance}", "row:weather"), items)
    }

    @Test
    fun `the backup status line rides along with the nightly backup row`() {
        val withBackup = SettingsCatalog.itemsForSearch(setOf(R.id.autoBackup)).map { describe(it) }
        assertEquals(listOf("heading:${R.string.backup}", "row:autoBackup", "row:autoBackupStatus"), withBackup)
        val withoutBackup = SettingsCatalog.itemsForSearch(setOf(R.id.importBackup)).map { describe(it) }
        assertEquals(listOf("heading:${R.string.backup}", "row:importBackup"), withoutBackup)
    }

    @Test
    fun `search with no match draws nothing`() {
        assertTrue(SettingsCatalog.itemsForSearch(emptySet()).isEmpty())
    }

    @Test
    fun `plain on off rows read and write their own pref`() {
        val withPref = SettingsCatalog.rows.filter { it.pref != null }
        assertEquals(
            listOf("drawerBottomSearch", "drawerNewFirst", "drawerLetterStrip", "drawerRecents", "intentionOnHome", "haptics"),
            withPref.map { it.key },
        )
        assertTrue(withPref.all { it.type == SettingsRowType.TOGGLE })

        // Each pref flips from its own default and lands under its own, unchanged key.
        val keysByRow = mapOf(
            "drawerBottomSearch" to Prefs.DRAWER_BOTTOM_SEARCH,
            "drawerNewFirst" to Prefs.DRAWER_NEW_FIRST,
            "drawerLetterStrip" to Prefs.DRAWER_LETTER_STRIP,
            "drawerRecents" to Prefs.DRAWER_RECENTS,
            "haptics" to Prefs.HAPTICS,
            "intentionOnHome" to "INTENTION_ON_HOME",
        )
        for (row in withPref) {
            val store = mutableMapOf<String, Any?>()
            val prefs = Prefs(FakeContext(FakeSharedPreferences(store)))
            val pref = row.pref!!
            val before = pref.get(prefs)
            pref.set(prefs, !before)
            assertEquals("${row.key} flipped", !before, pref.get(prefs))
            assertEquals("${row.key} stored under its key", !before, store[keysByRow.getValue(row.key)])
            assertEquals("${row.key} wrote only its own key", 1, store.size)
        }
    }

    private fun describe(item: SettingsItem): String = when (item) {
        is SettingsItem.Heading -> "heading:${item.titleRes}"
        is SettingsItem.Row -> "row:${item.spec.key}"
    }

    private class FakeEditor(private val map: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removed = mutableSetOf<String>()
        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values?.toSet() }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { removed += key }
        override fun clear() = apply { map.clear() }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun apply() {
            removed.forEach { map.remove(it) }
            map.putAll(pending)
            removed.clear()
            pending.clear()
        }
    }

    private class FakeSharedPreferences(private val map: MutableMap<String, Any?>) : SharedPreferences {
        override fun getAll(): MutableMap<String, *> = HashMap(map)
        override fun getString(key: String, defValue: String?): String? = (map[key] as? String) ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
            (map[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(map)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    private class FakeContext(private val prefs: SharedPreferences) : ContextWrapper(null) {
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
    }

    private companion object {
        /**
         * The settings the old hand-written layout held, by section: the 80 searchable rows of the old
         * runtime row list in layout order, plus the backup status line. Written out here, not read from
         * the model, so a row dropped from the model fails this test.
         */
        val INVENTORY: Map<String, List<String>> = mapOf(
        SettingsSections.KEY_HOME to listOf(
            "homeAppsNum",
            "dateTime",
            "batteryMode",
            "infoLine",
            "homeSets",
            "editMorningAppsLayout",
            "editEveningAppsLayout",
            "morningStart",
            "dayStart",
            "eveningStart",
            "contextLineValue",
            "homeNextEventValue",
            "alignment",
            "swapSides",
        ),
        SettingsSections.KEY_SHORTCUTS to listOf(
            "shortcutIconsOnOff",
            "shortcutCount",
            "shortcutSize",
            "shortcutSide",
            "shortcutHeight",
            "shortcutIconStyle",
        ),
        SettingsSections.KEY_DRAWER to listOf(
            "autoShowKeyboard",
            "drawerBottomSearch",
            "searchEngine",
            "drawerNewFirst",
            "drawerLetterStrip",
            "drawerRecents",
            "searchContacts",
            "currencyRates",
        ),
        SettingsSections.KEY_WELLBEING to listOf(
            "distractingApps",
            "pauseBeforeAppsOnOff",
            "pauseAllowance",
            "pauseEscalateOnOff",
            "pauseAskWhyOnOff",
            "windDown",
            "windDownGreyOnOff",
            "systemGreyOnOff",
            "commitLockOnOff",
            "holdNotificationsOnOff",
            "healthConnectOnOff",
            "quietReleaseTimes",
            "screenTimeOnOff",
            "screenTimeStyle",
            "screenTimeTarget",
            "nudgeWater",
            "nudgeWalk",
            "nudgeSocial",
            "nudgeActiveHours",
        ),
        SettingsSections.KEY_TODAY to listOf(
            "todayCards",
            "todayOrder",
            "intentionOnHome",
            "weather",
        ),
        SettingsSections.KEY_DEUTSCH to listOf(
            "germanOnOff",
            "germanClock",
            "germanLookupOnOff",
            "germanSpellSuggestOnOff",
            "germanScreenPickOnOff",
            "germanPauseWordOnOff",
            "germanUiOnOff",
        ),
        SettingsSections.KEY_APPEARANCE to listOf(
            "photoWallpaper",
            "statusBar",
            "appThemeText",
            "accent",
            "fontFamily",
            "boldFont",
            "textSizeValue",
            "haptics",
            "hapticStrength",
            "hapticDuration",
            "textContrast",
            "homeDarkness",
            "textBrightness",
            "musicPulse",
            "glowStrength",
            "weatherEffects",
        ),
        SettingsSections.KEY_GESTURES to listOf(
            "swipeLeftApp",
            "swipeRightApp",
            "toggleLock",
        ),
        SettingsSections.KEY_BACKUP to listOf(
            "exportBackup",
            "autoBackup",
            "autoBackupStatus",
            "importBackup",
        ),
        )
    }
}
