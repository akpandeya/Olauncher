package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCoverageTest {

    @Test
    fun everyUserDataFileIsExported() {
        assertEquals(
            listOf(
                "saved_items.json",
                "word_progress.json",
                "words.json",
                "usage_days.json",
                "intention_history.json"
            ),
            Backup.EXPORTED_FILES
        )
        assertEquals(
            setOf(
                Backup.FILE_SAVED_ITEMS,
                Backup.FILE_WORD_PROGRESS,
                Backup.FILE_WORDS,
                Backup.FILE_USAGE_DAYS,
                Backup.FILE_INTENTION_HISTORY
            ),
            Backup.EXPORTED_FILES.toSet()
        )
    }

    @Test
    fun cachesAreNotExported() {
        listOf("weather.json", "rates.json", "held.json", "wiktionary_cache.json").forEach {
            assertFalse(it, it in Backup.EXPORTED_FILES)
        }
    }

    @Test
    fun widgetKeysNeverLeaveTheDevice() {
        // Widget instance IDs and heights are meaningless on another device or after a
        // reinstall: restoring them binds dead IDs ("This widget is unavailable").
        listOf("TODAY_WIDGET_IDS", "TODAY_PENDING_WIDGET_ID", "TODAY_WIDGET_HEIGHTS").forEach {
            assertTrue(it, Backup.isVolatileKey(it))
        }
    }

    @Test
    fun restoringKeepsTheDevicesOwnWidgetState() {
        val current = mapOf<String, Any?>(
            "TODAY_WIDGET_IDS" to "2,7",
            "TODAY_WIDGET_HEIGHTS" to "2:496",
            "TODAY_PENDING_WIDGET_ID" to -1,
        )
        val imported = mapOf<String, Any?>(
            "TODAY_WIDGET_IDS" to "3,9",
            "THEME" to "dark",
        )
        val merged = Backup.mergeKeepingVolatile(current, imported)
        assertEquals("2,7", merged["TODAY_WIDGET_IDS"])
        assertEquals("2:496", merged["TODAY_WIDGET_HEIGHTS"])
        assertEquals(-1, merged["TODAY_PENDING_WIDGET_ID"])
        assertEquals("dark", merged["THEME"])
    }
}
