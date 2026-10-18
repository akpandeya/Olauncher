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

            // All files exist, plus an unexported file
            val wordProgressFile = File(tempDir, "word_progress.json")
            wordProgressFile.writeText("""{"streak":3}""")
            val wordsFile = File(tempDir, "words.json")
            wordsFile.writeText("""[{"id":10,"text":"test"}]""")
            val otherFile = File(tempDir, "other.json")
            otherFile.writeText("ignored")

            val filesMap = Backup.readFiles(tempDir)
            assertEquals(3, filesMap.size)
            assertEquals("""[{"id":1}]""", filesMap["saved_items.json"])
            assertEquals("""{"streak":3}""", filesMap["word_progress.json"])
            assertEquals("""[{"id":10,"text":"test"}]""", filesMap["words.json"])
            assertFalse(filesMap.containsKey("other.json"))
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

    @Test
    fun `decode to encode is stable for fixture backup_v1 json`() {
        val resourceStream = javaClass.classLoader?.getResourceAsStream("backup_v1.json")
            ?: javaClass.getResourceAsStream("/backup_v1.json")
            ?: File("app/src/test/resources/backup_v1.json").takeIf { it.exists() }?.inputStream()
            ?: File("src/test/resources/backup_v1.json").takeIf { it.exists() }?.inputStream()
        assertNotNull("Fixture backup_v1.json must be readable", resourceStream)

        val rawJson = resourceStream!!.bufferedReader().use { it.readText() }.trim().replace("\r\n", "\n")
        val decoded = BackupCodec.decode(rawJson)

        assertEquals("thumbline-backup", BackupCodec.FORMAT)
        assertEquals("v6.9.17-tl.113", decoded.appVersion)
        assertEquals(8, decoded.prefs.size)
        assertEquals(2, decoded.files.size)

        val reEncoded = BackupCodec.encode(decoded.prefs, decoded.files, decoded.appVersion).trim().replace("\r\n", "\n")
        assertEquals(rawJson, reEncoded)

        val reDecoded = BackupCodec.decode(reEncoded)
        assertEquals(decoded.prefs, reDecoded.prefs)
        assertEquals(decoded.files, reDecoded.files)
        assertEquals(decoded.appVersion, reDecoded.appVersion)
    }

    @Test
    fun `restorePrefs clears old prefs and writes all typed values and commits`() {
        val initialPrefs = mutableMapOf<String, Any?>(
            "old_key_1" to "old_value",
            "old_key_2" to 999
        )
        val fakePrefs = FakeSharedPreferences(initialPrefs)

        val restoreMap = mapOf<String, Any?>(
            "pref_bool" to true,
            "pref_int" to 123,
            "pref_long" to 456789L,
            "pref_float" to 1.5f,
            "pref_string" to "Restored String",
            "pref_string_set" to setOf("x", "y", "z")
        )

        Backup.restorePrefs(fakePrefs, restoreMap)

        assertFalse(fakePrefs.contains("old_key_1"))
        assertFalse(fakePrefs.contains("old_key_2"))
        assertEquals(true, fakePrefs.getBoolean("pref_bool", false))
        assertEquals(123, fakePrefs.getInt("pref_int", 0))
        assertEquals(456789L, fakePrefs.getLong("pref_long", 0L))
        assertEquals(1.5f, fakePrefs.getFloat("pref_float", 0f), 0.001f)
        assertEquals("Restored String", fakePrefs.getString("pref_string", null))
        assertEquals(setOf("x", "y", "z"), fakePrefs.getStringSet("pref_string_set", null))
    }

    @Test
    fun `writeFiles writes files atomically and removes deleted files`() {
        val tempDir = File.createTempFile("backup_restore_test", "dir")
        tempDir.delete()
        tempDir.mkdirs()
        try {
            // Pre-existing files: one that is in backup, one old exported file not in backup, one user file
            val oldSavedItems = File(tempDir, "saved_items.json")
            oldSavedItems.writeText("""[{"id":99,"url":"https://old.com"}]""")

            val oldWordProgress = File(tempDir, "word_progress.json")
            oldWordProgress.writeText("""{"streak":10}""")

            val otherFile = File(tempDir, "other.txt")
            otherFile.writeText("preserved")

            // Restoring backup that only has saved_items.json (word_progress.json was deleted)
            val filesToRestore = mapOf(
                "saved_items.json" to """[{"id":1,"url":"https://restored.com"}]"""
            )

            Backup.writeFiles(tempDir, filesToRestore)

            // saved_items.json should be overwritten
            assertEquals("""[{"id":1,"url":"https://restored.com"}]""", File(tempDir, "saved_items.json").readText())
            // word_progress.json should have been deleted because it's an EXPORTED_FILE not in the backup
            assertFalse(File(tempDir, "word_progress.json").exists())
            // other.txt should not be touched
            assertTrue(File(tempDir, "other.txt").exists())
            assertEquals("preserved", File(tempDir, "other.txt").readText())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private class FakeSharedPreferences(
        val map: MutableMap<String, Any?> = mutableMapOf()
    ) : android.content.SharedPreferences {
        override fun getAll(): MutableMap<String, *> = HashMap(map)
        override fun getString(key: String?, defValue: String?): String? = (map[key] as? String) ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            (map[key] as? Set<String>)?.toMutableSet() ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun edit(): android.content.SharedPreferences.Editor = FakeEditor()
        override fun registerOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}

        inner class FakeEditor : android.content.SharedPreferences.Editor {
            private val pending = mutableMapOf<String, Any?>()
            private var clearRequested = false

            override fun putString(key: String?, value: String?): android.content.SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): android.content.SharedPreferences.Editor {
                if (key != null) pending[key] = values?.toSet()
                return this
            }
            override fun putInt(key: String?, value: Int): android.content.SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putLong(key: String?, value: Long): android.content.SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putFloat(key: String?, value: Float): android.content.SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun putBoolean(key: String?, value: Boolean): android.content.SharedPreferences.Editor {
                if (key != null) pending[key] = value
                return this
            }
            override fun remove(key: String?): android.content.SharedPreferences.Editor {
                if (key != null) pending[key] = this
                return this
            }
            override fun clear(): android.content.SharedPreferences.Editor {
                clearRequested = true
                return this
            }
            override fun commit(): Boolean {
                if (clearRequested) {
                    map.clear()
                }
                for ((k, v) in pending) {
                    if (v === this) {
                        map.remove(k)
                    } else if (v == null) {
                        map.remove(k)
                    } else {
                        map[k] = v
                    }
                }
                return true
            }
            override fun apply() {
                commit()
            }
        }
    }
}
