package app.olauncher.data.store

import app.olauncher.data.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Retention guards for the web import (epic #835, task 05; the browser import
 * is gambitflow #859): the later removal tasks (07–09) delete native screens
 * and code only, never stored data. These tests fail the build if the export
 * stops writing a file or pref the import reads. Every value here is synthetic;
 * no real backup is read.
 */
class GermanWebImportRetentionTest {

    /** Synthetic drill payloads — never data from a real device. */
    private val drillScores = """{"word_order":{"s1":3,"s2":1}}"""
    private val drillItemHistory = """[["word_order",20400,42]]"""
    private val wordsJson = """{"format":"thumbline-words","version":2,"words":[]}"""
    private val reviewEvents = """[{"id":7,"wordId":3,"result":"again","reviewedAt":20400}]"""

    @Test
    fun `the export still contains the files the web import reads`() {
        assertEquals("words.json", Backup.FILE_WORDS)
        assertEquals("review_events.json", Backup.FILE_REVIEW_EVENTS)
        assertTrue("'words.json' must stay exported", Backup.EXPORTED_FILES.contains("words.json"))
        assertTrue("'review_events.json' must stay exported", Backup.EXPORTED_FILES.contains("review_events.json"))
    }

    @Test
    fun `drill prefs are not volatile and survive every export`() {
        assertFalse("DRILL_SCORES must not be volatile", Backup.isVolatileKey(Prefs.DRILL_SCORES))
        assertFalse("DRILL_ITEM_HISTORY must not be volatile", Backup.isVolatileKey(Prefs.DRILL_ITEM_HISTORY))

        val filtered = Backup.filterPrefs(
            mapOf(
                Prefs.DRILL_SCORES to drillScores,
                Prefs.DRILL_ITEM_HISTORY to drillItemHistory,
                // A genuinely volatile key, as the control that must drop out.
                Prefs.AUTO_BACKUP_ENABLED to true,
            )
        )
        assertEquals(drillScores, filtered[Prefs.DRILL_SCORES])
        assertEquals(drillItemHistory, filtered[Prefs.DRILL_ITEM_HISTORY])
        assertFalse(filtered.containsKey(Prefs.AUTO_BACKUP_ENABLED))
    }

    @Test
    fun `drill prefs and import files round trip byte for byte`() {
        val prefs = mapOf(
            Prefs.DRILL_SCORES to drillScores,
            Prefs.DRILL_ITEM_HISTORY to drillItemHistory,
        )
        val files = mapOf(
            Backup.FILE_WORDS to wordsJson,
            Backup.FILE_REVIEW_EVENTS to reviewEvents,
        )

        val decoded = BackupCodec.decode(BackupCodec.encode(prefs, files, "test"))

        assertEquals(drillScores, decoded.prefs[Prefs.DRILL_SCORES])
        assertEquals(drillItemHistory, decoded.prefs[Prefs.DRILL_ITEM_HISTORY])
        assertEquals(wordsJson, decoded.files[Backup.FILE_WORDS])
        assertEquals(reviewEvents, decoded.files[Backup.FILE_REVIEW_EVENTS])
    }

    @Test
    fun `drill pref keys keep their exact names`() {
        assertEquals("DRILL_SCORES", Prefs.DRILL_SCORES)
        assertEquals("DRILL_ITEM_HISTORY", Prefs.DRILL_ITEM_HISTORY)
        assertEquals("DRILL_LAST_ROUND", Prefs.DRILL_LAST_ROUND)
        assertEquals("DRILL_LAST_TYPE", Prefs.DRILL_LAST_TYPE)
    }

    @Test
    fun `the export day of the guided path stays device-local`() {
        // The row's "Saved on" guidance must not restore onto another device
        // and claim its progress was already moved.
        assertTrue(Backup.isVolatileKey(Prefs.GERMAN_WEB_PROGRESS_EXPORTED_DAY))
    }
}
