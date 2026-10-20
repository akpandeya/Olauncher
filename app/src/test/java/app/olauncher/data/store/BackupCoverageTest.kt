package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
