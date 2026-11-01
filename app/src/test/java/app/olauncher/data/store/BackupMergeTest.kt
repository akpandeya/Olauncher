package app.olauncher.data.store

import android.content.SharedPreferences
import app.olauncher.data.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupMergeTest {

    @Test
    fun keepsCurrentVolatileValuesAndImportsTheRest() {
        val current = mapOf<String, Any?>(
            "AUTO_BACKUP_ENABLED" to true,
            "AUTO_BACKUP_TREE_URI" to "content://tree/current",
            "THEME" to "dark",
        )
        val imported = mapOf<String, Any?>(
            "AUTO_BACKUP_ENABLED" to false,
            "THEME" to "light",
            "FONT" to 3,
        )
        val merged = Backup.mergeKeepingVolatile(current, imported)
        assertEquals(true, merged["AUTO_BACKUP_ENABLED"])
        assertEquals("content://tree/current", merged["AUTO_BACKUP_TREE_URI"])
        assertEquals("light", merged["THEME"])
        assertEquals(3, merged["FONT"])
    }

    @Test
    fun volatileKeyAbsentOnDeviceIsNotInvented() {
        val merged = Backup.mergeKeepingVolatile(emptyMap<String, Any?>(), mapOf("AUTO_BACKUP_ENABLED" to true, "A" to 1))
        assertFalse(merged.containsKey("AUTO_BACKUP_ENABLED"))
        assertEquals(1, merged["A"])
    }

    @Test
    fun newsFetchCounterKeysAreVolatileAndFilteredFromExport() {
        assertTrue(Backup.isVolatileKey(Prefs.NEWS_FETCH_DAY))
        assertTrue(Backup.isVolatileKey(Prefs.NEWS_FETCH_COUNT))

        val exported = Backup.filterPrefs(
            mapOf(
                Prefs.NEWS_FETCH_DAY to 20000L,
                Prefs.NEWS_FETCH_COUNT to 15,
                Prefs.NEWS_SENTENCES_ENABLED to true,
            )
        )
        assertFalse(exported.containsKey(Prefs.NEWS_FETCH_DAY))
        assertFalse(exported.containsKey(Prefs.NEWS_FETCH_COUNT))
        assertEquals(true, exported[Prefs.NEWS_SENTENCES_ENABLED])
    }

    @Test
    fun restoreDoesNotResetDailyCounterWhenBackupHasNoCounter() {
        val current = mapOf<String, Any?>(
            Prefs.NEWS_FETCH_DAY to 20000L,
            Prefs.NEWS_FETCH_COUNT to 25,
            "THEME" to "dark",
        )
        val imported = mapOf<String, Any?>(
            "THEME" to "light",
        )
        val merged = Backup.mergeKeepingVolatile(current, imported)
        assertEquals(20000L, merged[Prefs.NEWS_FETCH_DAY])
        assertEquals(25, merged[Prefs.NEWS_FETCH_COUNT])
        assertEquals("light", merged["THEME"])
    }

    @Test
    fun restoreDoesNotLowerDailyCounterWhenBackupHasLowerCount() {
        val current = mapOf<String, Any?>(
            Prefs.NEWS_FETCH_DAY to 20000L,
            Prefs.NEWS_FETCH_COUNT to 28,
        )
        val imported = mapOf<String, Any?>(
            Prefs.NEWS_FETCH_DAY to 20000L,
            Prefs.NEWS_FETCH_COUNT to 5,
        )
        val merged = Backup.mergeKeepingVolatile(current, imported)
        assertEquals(20000L, merged[Prefs.NEWS_FETCH_DAY])
        assertEquals(28, merged[Prefs.NEWS_FETCH_COUNT])
    }

    @Test
    fun restoreDoesNotResetDailyCounterWhenBackupHasYesterdayDate() {
        val current = mapOf<String, Any?>(
            Prefs.NEWS_FETCH_DAY to 20000L,
            Prefs.NEWS_FETCH_COUNT to 22,
        )
        val imported = mapOf<String, Any?>(
            Prefs.NEWS_FETCH_DAY to 19999L,
            Prefs.NEWS_FETCH_COUNT to 30,
        )
        val merged = Backup.mergeKeepingVolatile(current, imported)
        assertEquals(20000L, merged[Prefs.NEWS_FETCH_DAY])
        assertEquals(22, merged[Prefs.NEWS_FETCH_COUNT])
    }

    @Test
    fun restoreIgnoresBackupCounterWithALaterDay() {
        // A later (future) imported day would make today's count read as 0 and bypass the cap.
        val current = mapOf<String, Any?>(
            Prefs.NEWS_FETCH_DAY to 20000L,
            Prefs.NEWS_FETCH_COUNT to 29,
        )
        val imported = mapOf<String, Any?>(
            Prefs.NEWS_FETCH_DAY to 20007L,
            Prefs.NEWS_FETCH_COUNT to 1,
        )
        val merged = Backup.mergeKeepingVolatile(current, imported)
        assertEquals(20000L, merged[Prefs.NEWS_FETCH_DAY])
        assertEquals(29, merged[Prefs.NEWS_FETCH_COUNT])
    }

    @Test
    fun restoreTakesHigherCountWhenBackupHasMoreArticlesFetchedToday() {
        val current = mapOf<String, Any?>(
            Prefs.NEWS_FETCH_DAY to 20000L,
            Prefs.NEWS_FETCH_COUNT to 10,
        )
        val imported = mapOf<String, Any?>(
            Prefs.NEWS_FETCH_DAY to 20000L,
            Prefs.NEWS_FETCH_COUNT to 20,
        )
        val merged = Backup.mergeKeepingVolatile(current, imported)
        assertEquals(20000L, merged[Prefs.NEWS_FETCH_DAY])
        assertEquals(20, merged[Prefs.NEWS_FETCH_COUNT])
    }

    @Test
    fun restorePrefsWritesMergedNewsCounterToSharedPreferences() {
        val map = mutableMapOf<String, Any?>(
            Prefs.NEWS_FETCH_DAY to 20000L,
            Prefs.NEWS_FETCH_COUNT to 27,
            "THEME" to "dark",
        )
        val fakePrefs = FakeSharedPreferences(map)
        val imported = mapOf<String, Any?>(
            Prefs.NEWS_FETCH_DAY to 20000L,
            Prefs.NEWS_FETCH_COUNT to 3,
            "THEME" to "light",
        )

        Backup.restorePrefs(fakePrefs, imported)

        assertEquals("light", map["THEME"])
        assertEquals(20000L, map[Prefs.NEWS_FETCH_DAY])
        assertEquals(27, map[Prefs.NEWS_FETCH_COUNT])
    }

    private class FakeEditor(private val map: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removed = mutableSetOf<String>()
        private var clearPending = false

        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values?.toSet() }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { removed += key }
        override fun clear() = apply { clearPending = true }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun apply() {
            if (clearPending) {
                map.clear()
                clearPending = false
            }
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
}
