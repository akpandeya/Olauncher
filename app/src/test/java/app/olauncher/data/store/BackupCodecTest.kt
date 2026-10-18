package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.time.LocalDate

class BackupCodecTest {

    @Test
    fun `round trip of every pref type`() {
        val prefs = mapOf<String, Any?>(
            "pref_bool_true" to true,
            "pref_bool_false" to false,
            "pref_int" to 42,
            "pref_int_zero" to 0,
            "pref_int_negative" to -10,
            "pref_long" to 1700000000000L,
            "pref_long_zero" to 0L,
            "pref_float" to 3.14f,
            "pref_float_zero" to 0.0f,
            "pref_string" to "Thumbline Launcher",
            "pref_string_empty" to "",
            "pref_string_set" to setOf("alpha", "beta", "gamma"),
            "pref_string_set_empty" to emptySet<String>()
        )
        val files = mapOf(
            "saved_items.json" to """[{"id":1,"url":"https://example.com"}]""",
            "word_progress.json" to """{"day":1,"streak":5}"""
        )
        val appVersion = "v6.9.17-tl.113"

        val encoded = BackupCodec.encode(prefs, files, appVersion)
        val decoded = BackupCodec.decode(encoded)

        assertEquals(prefs, decoded.prefs)
        assertEquals(files, decoded.files)
        assertEquals(appVersion, decoded.appVersion)

        // Verify individual types
        assertTrue(decoded.prefs["pref_bool_true"] is Boolean)
        assertTrue(decoded.prefs["pref_int"] is Int)
        assertTrue(decoded.prefs["pref_long"] is Long)
        assertTrue(decoded.prefs["pref_float"] is Float)
        assertTrue(decoded.prefs["pref_string"] is String)
        assertTrue(decoded.prefs["pref_string_set"] is Set<*>)
    }

    @Test
    fun `round trip empty prefs and empty files`() {
        val prefs = emptyMap<String, Any?>()
        val files = emptyMap<String, String>()
        val appVersion = "1.0.0"

        val encoded = BackupCodec.encode(prefs, files, appVersion)
        val decoded = BackupCodec.decode(encoded)

        assertTrue(decoded.prefs.isEmpty())
        assertTrue(decoded.files.isEmpty())
        assertEquals(appVersion, decoded.appVersion)
    }

    @Test
    fun `destructuring BackupData yields prefs and files`() {
        val prefs = mapOf<String, Any?>("key" to "value")
        val files = mapOf("file.txt" to "content")
        val encoded = BackupCodec.encode(prefs, files, "1.0")

        val (decodedPrefs, decodedFiles) = BackupCodec.decode(encoded)

        assertEquals(prefs, decodedPrefs)
        assertEquals(files, decodedFiles)
    }

    @Test
    fun `unknown version throws BackupFormatException`() {
        val raw = """
            {
                "format": "thumbline-backup",
                "version": 2,
                "appVersion": "1.0",
                "prefs": {},
                "files": {}
            }
        """.trimIndent()

        try {
            BackupCodec.decode(raw)
            fail("Expected BackupFormatException for unknown version 2")
        } catch (e: BackupFormatException) {
            assertTrue(e.message?.contains("version") == true)
        }
    }

    @Test
    fun `missing version throws BackupFormatException`() {
        val raw = """
            {
                "format": "thumbline-backup",
                "appVersion": "1.0",
                "prefs": {},
                "files": {}
            }
        """.trimIndent()

        try {
            BackupCodec.decode(raw)
            fail("Expected BackupFormatException for missing version")
        } catch (e: BackupFormatException) {
            assertTrue(e.message?.contains("version") == true)
        }
    }

    @Test
    fun `malformed JSON throws BackupFormatException`() {
        val malformedSamples = listOf(
            "",
            "   ",
            "not json at all",
            "{ unclosed object",
            "[1, 2, 3]",
            "12345",
            "true",
            "null"
        )
        for (sample in malformedSamples) {
            try {
                BackupCodec.decode(sample)
                fail("Expected BackupFormatException for input '$sample'")
            } catch (e: BackupFormatException) {
                // Expected
            }
        }
    }

    @Test
    fun `unknown format throws BackupFormatException`() {
        val raw = """
            {
                "format": "other-app-backup",
                "version": 1,
                "appVersion": "1.0",
                "prefs": {},
                "files": {}
            }
        """.trimIndent()

        try {
            BackupCodec.decode(raw)
            fail("Expected BackupFormatException for invalid format")
        } catch (e: BackupFormatException) {
            assertTrue(e.message?.contains("format") == true)
        }
    }

    @Test
    fun `missing prefs or files object throws BackupFormatException`() {
        val missingPrefs = """
            {
                "format": "thumbline-backup",
                "version": 1,
                "appVersion": "1.0",
                "files": {}
            }
        """.trimIndent()
        try {
            BackupCodec.decode(missingPrefs)
            fail("Expected BackupFormatException when prefs is missing")
        } catch (e: BackupFormatException) {
            // Expected
        }

        val missingFiles = """
            {
                "format": "thumbline-backup",
                "version": 1,
                "appVersion": "1.0",
                "prefs": {}
            }
        """.trimIndent()
        try {
            BackupCodec.decode(missingFiles)
            fail("Expected BackupFormatException when files is missing")
        } catch (e: BackupFormatException) {
            // Expected
        }
    }

    @Test
    fun `corrupt pref entry throws BackupFormatException`() {
        val unknownType = """
            {
                "format": "thumbline-backup",
                "version": 1,
                "appVersion": "1.0",
                "prefs": {
                    "foo": {"t": "unknown", "v": "bar"}
                },
                "files": {}
            }
        """.trimIndent()
        try {
            BackupCodec.decode(unknownType)
            fail("Expected BackupFormatException for unknown pref type")
        } catch (e: BackupFormatException) {
            // Expected
        }

        val missingValue = """
            {
                "format": "thumbline-backup",
                "version": 1,
                "appVersion": "1.0",
                "prefs": {
                    "foo": {"t": "s"}
                },
                "files": {}
            }
        """.trimIndent()
        try {
            BackupCodec.decode(missingValue)
            fail("Expected BackupFormatException for missing value")
        } catch (e: BackupFormatException) {
            // Expected
        }

        val mismatchedType = """
            {
                "format": "thumbline-backup",
                "version": 1,
                "appVersion": "1.0",
                "prefs": {
                    "foo": {"t": "b", "v": "not a boolean"}
                },
                "files": {}
            }
        """.trimIndent()
        try {
            BackupCodec.decode(mismatchedType)
            fail("Expected BackupFormatException for mismatched type")
        } catch (e: BackupFormatException) {
            // Expected
        }
    }

    @Test
    fun `volatile keys are correctly identified and filtered`() {
        assertTrue(Backup.isVolatileKey("SCREEN_TIME_LAST_UPDATED"))
        assertTrue(Backup.isVolatileKey("LAUNCHER_RECREATE_TIMESTAMP"))
        assertTrue(Backup.isVolatileKey("OWN_OPENS_DAY"))
        assertTrue(Backup.isVolatileKey("OWN_OPENS_com.example.test"))
        assertTrue(Backup.isVolatileKey("OWN_OPENS_"))

        assertFalse(Backup.isVolatileKey("HOME_APPS_NUM"))
        assertFalse(Backup.isVolatileKey("DRAWER_BOTTOM_SEARCH"))
        assertFalse(Backup.isVolatileKey("com.example.test")) // App rename key
        assertFalse(Backup.isVolatileKey("APP_NAME_1"))

        val allPrefs = mapOf(
            "SCREEN_TIME_LAST_UPDATED" to 1234567L,
            "LAUNCHER_RECREATE_TIMESTAMP" to 999999L,
            "OWN_OPENS_DAY" to 19500L,
            "OWN_OPENS_com.android.chrome" to 12,
            "HOME_APPS_NUM" to 6,
            "com.android.chrome" to "Browser"
        )
        val filtered = Backup.filterPrefs(allPrefs)

        assertEquals(2, filtered.size)
        assertEquals(6, filtered["HOME_APPS_NUM"])
        assertEquals("Browser", filtered["com.android.chrome"])
        assertFalse(filtered.containsKey("SCREEN_TIME_LAST_UPDATED"))
        assertFalse(filtered.containsKey("LAUNCHER_RECREATE_TIMESTAMP"))
        assertFalse(filtered.containsKey("OWN_OPENS_DAY"))
        assertFalse(filtered.containsKey("OWN_OPENS_com.android.chrome"))
    }

    @Test
    fun `readFiles reads saved_items and word_progress when they exist and ignores other files`() {
        val tempDir = File.createTempFile("backup_test", "dir")
        tempDir.delete()
        tempDir.mkdirs()
        try {
            // Neither file exists
            val emptyMap = Backup.readFiles(tempDir)
            assertTrue(emptyMap.isEmpty())

            // Only saved_items.json exists
            val savedItemsFile = File(tempDir, "saved_items.json")
            savedItemsFile.writeText("""[{"id":1}]""")
            val oneFileMap = Backup.readFiles(tempDir)
            assertEquals(1, oneFileMap.size)
            assertEquals("""[{"id":1}]""", oneFileMap["saved_items.json"])

            // Both files exist, plus an unexported file
            val wordProgressFile = File(tempDir, "word_progress.json")
            wordProgressFile.writeText("""{"streak":3}""")
            val otherFile = File(tempDir, "other.json")
            otherFile.writeText("ignored")

            val twoFilesMap = Backup.readFiles(tempDir)
            assertEquals(2, twoFilesMap.size)
            assertEquals("""[{"id":1}]""", twoFilesMap["saved_items.json"])
            assertEquals("""{"streak":3}""", twoFilesMap["word_progress.json"])
            assertFalse(twoFilesMap.containsKey("other.json"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `defaultBackupFileName matches expected format`() {
        val date = LocalDate.of(2026, 10, 18)
        val filename = Backup.defaultBackupFileName(date)
        assertEquals("thumbline-backup-2026-10-18.json", filename)
    }
}
