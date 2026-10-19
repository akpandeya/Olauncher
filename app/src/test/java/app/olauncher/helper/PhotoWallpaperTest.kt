package app.olauncher.helper

import android.content.ContextWrapper
import android.content.SharedPreferences
import app.olauncher.data.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class PhotoWallpaperTest {

    private class FakeEditor(private val map: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private var clearPending = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            if (key != null) pending[key] = values?.toSet()
            return this
        }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) pending[key] = value
            return this
        }

        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) pending[key] = this
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearPending = true
            return this
        }

        override fun commit(): Boolean {
            apply()
            return true
        }

        override fun apply() {
            if (clearPending) {
                map.clear()
                clearPending = false
            }
            for ((key, value) in pending) {
                if (value === this) {
                    map.remove(key)
                } else {
                    map[key] = value
                }
            }
            pending.clear()
        }
    }

    private class FakeSharedPreferences(
        val map: MutableMap<String, Any?> = mutableMapOf()
    ) : SharedPreferences {
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
        override fun edit(): SharedPreferences.Editor = FakeEditor(map)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    private class FakeContext(
        private val prefs: SharedPreferences,
        private val filesDirectory: File,
    ) : ContextWrapper(null) {
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
        override fun getFilesDir(): File = filesDirectory
    }

    private fun createPrefs(map: MutableMap<String, Any?> = mutableMapOf()): Pair<Prefs, File> {
        val tempDir = Files.createTempDirectory("photowallpaper_test").toFile()
        val fakePrefs = FakeSharedPreferences(map)
        val context = FakeContext(fakePrefs, tempDir)
        return Pair(Prefs(context), tempDir)
    }

    @Test
    fun `photoWallpaperInterval defaults to DAILY id and persists changes`() {
        val (prefs, tempDir) = createPrefs()
        try {
            assertEquals(RotationInterval.DEFAULT.id, prefs.photoWallpaperInterval)
            assertEquals(8, prefs.photoWallpaperInterval)

            prefs.photoWallpaperInterval = RotationInterval.HOURS_1.id
            assertEquals(RotationInterval.HOURS_1.id, prefs.photoWallpaperInterval)
            assertEquals(4, prefs.photoWallpaperInterval)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `photoWallpaperLastChanged defaults to 0L and persists changes`() {
        val (prefs, tempDir) = createPrefs()
        try {
            assertEquals(0L, prefs.photoWallpaperLastChanged)

            prefs.photoWallpaperLastChanged = 1_700_000_000_000L
            assertEquals(1_700_000_000_000L, prefs.photoWallpaperLastChanged)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `markChanged writes both photoWallpaperLastChanged and photoWallpaperLastDay`() {
        val (prefs, tempDir) = createPrefs()
        try {
            val now = 1_700_000_123_456L
            PhotoWallpaper.markChanged(prefs, nowMillis = now)

            assertEquals(now, prefs.photoWallpaperLastChanged)
            assertEquals(PhotoWallpaper.today(), prefs.photoWallpaperLastDay)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `rotateIfDue returns true and updates index and timestamps when due`() {
        val (prefs, tempDir) = createPrefs()
        try {
            prefs.photoWallpaperCount = 3
            prefs.photoWallpaperIndex = 0
            prefs.photoWallpaperInterval = RotationInterval.DAILY.id

            val baseTime = 1_000_000_000L
            prefs.photoWallpaperLastChanged = baseTime
            prefs.photoWallpaperLastDay = PhotoWallpaper.today() - 1

            // 24 hours later -> due
            val now = baseTime + TimeUnit.HOURS.toMillis(24)
            val rotated = PhotoWallpaper.rotateIfDue(prefs, nowMillis = now)

            assertTrue(rotated)
            assertEquals(1, prefs.photoWallpaperIndex)
            assertEquals(now, prefs.photoWallpaperLastChanged)
            assertEquals(PhotoWallpaper.today(), prefs.photoWallpaperLastDay)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `rotateIfDue returns false and does not rotate when not due`() {
        val (prefs, tempDir) = createPrefs()
        try {
            prefs.photoWallpaperCount = 3
            prefs.photoWallpaperIndex = 0
            prefs.photoWallpaperInterval = RotationInterval.DAILY.id

            val baseTime = 1_000_000_000L
            prefs.photoWallpaperLastChanged = baseTime
            prefs.photoWallpaperLastDay = PhotoWallpaper.today()

            // Only 1 hour later -> not due
            val now = baseTime + TimeUnit.HOURS.toMillis(1)
            val rotated = PhotoWallpaper.rotateIfDue(prefs, nowMillis = now)

            assertFalse(rotated)
            assertEquals(0, prefs.photoWallpaperIndex)
            assertEquals(baseTime, prefs.photoWallpaperLastChanged)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `rotateIfDue returns false when clock goes backwards but rebases lastChanged`() {
        val (prefs, tempDir) = createPrefs()
        try {
            prefs.photoWallpaperCount = 3
            prefs.photoWallpaperIndex = 1
            prefs.photoWallpaperInterval = RotationInterval.DAILY.id

            val baseTime = 2_000_000_000L
            prefs.photoWallpaperLastChanged = baseTime

            // Clock went backwards to 1_000_000_000L
            val now = 1_000_000_000L
            val rotated = PhotoWallpaper.rotateIfDue(prefs, nowMillis = now)

            assertFalse(rotated)
            assertEquals(1, prefs.photoWallpaperIndex)
            assertEquals(now, prefs.photoWallpaperLastChanged)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `rotateIfDue returns false when count is 0 or interval is NEVER`() {
        val (prefs, tempDir) = createPrefs()
        try {
            prefs.photoWallpaperCount = 0
            prefs.photoWallpaperIndex = 0
            prefs.photoWallpaperLastChanged = 1000L
            assertFalse(PhotoWallpaper.rotateIfDue(prefs, nowMillis = 5000L))

            prefs.photoWallpaperCount = 3
            prefs.photoWallpaperInterval = RotationInterval.NEVER.id
            assertFalse(PhotoWallpaper.rotateIfDue(prefs, nowMillis = 5000L))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `rotateIfDue returns false for single photo since index does not change`() {
        val (prefs, tempDir) = createPrefs()
        try {
            prefs.photoWallpaperCount = 1
            prefs.photoWallpaperIndex = 0
            prefs.photoWallpaperInterval = RotationInterval.DAILY.id

            val baseTime = 1_000_000_000L
            prefs.photoWallpaperLastChanged = baseTime
            val now = baseTime + TimeUnit.HOURS.toMillis(24)

            val rotated = PhotoWallpaper.rotateIfDue(prefs, nowMillis = now)

            assertFalse(rotated)
            assertEquals(0, prefs.photoWallpaperIndex)
            assertEquals(now, prefs.photoWallpaperLastChanged)
            assertEquals(PhotoWallpaper.today(), prefs.photoWallpaperLastDay)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `rotateIfDue with ON_OPEN interval rotates on every call when count is greater than one`() {
        val (prefs, tempDir) = createPrefs()
        try {
            prefs.photoWallpaperCount = 3
            prefs.photoWallpaperIndex = 0
            prefs.photoWallpaperInterval = RotationInterval.ON_OPEN.id

            val now = 1_000_000_000L
            prefs.photoWallpaperLastChanged = now

            // First call advances to 1
            assertTrue(PhotoWallpaper.rotateIfDue(prefs, nowMillis = now))
            assertEquals(1, prefs.photoWallpaperIndex)
            assertEquals(now, prefs.photoWallpaperLastChanged)

            // Subsequent call rotates immediately to 2
            assertTrue(PhotoWallpaper.rotateIfDue(prefs, nowMillis = now + 100L))
            assertEquals(2, prefs.photoWallpaperIndex)
            assertEquals(now + 100L, prefs.photoWallpaperLastChanged)

            // Subsequent call wraps around to 0
            assertTrue(PhotoWallpaper.rotateIfDue(prefs, nowMillis = now + 200L))
            assertEquals(0, prefs.photoWallpaperIndex)
            assertEquals(now + 200L, prefs.photoWallpaperLastChanged)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `rotateIfDue with ON_OPEN interval returns false when count is one`() {
        val (prefs, tempDir) = createPrefs()
        try {
            prefs.photoWallpaperCount = 1
            prefs.photoWallpaperIndex = 0
            prefs.photoWallpaperInterval = RotationInterval.ON_OPEN.id

            val now = 1_000_000_000L
            prefs.photoWallpaperLastChanged = now

            assertFalse(PhotoWallpaper.rotateIfDue(prefs, nowMillis = now))
            assertEquals(0, prefs.photoWallpaperIndex)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `rotateIfDue migrates legacy users with lastChanged 0L and lastDay matching today`() {
        val (prefs, tempDir) = createPrefs()
        try {
            val today = PhotoWallpaper.today()
            prefs.photoWallpaperCount = 3
            prefs.photoWallpaperIndex = 1
            prefs.photoWallpaperInterval = RotationInterval.DAILY.id
            prefs.photoWallpaperLastChanged = 0L
            prefs.photoWallpaperLastDay = today

            val now = 1_700_000_000_000L
            // Legacy user already changed today: should not rotate immediately
            val rotated = PhotoWallpaper.rotateIfDue(prefs, nowMillis = now)

            assertFalse(rotated)
            assertEquals(1, prefs.photoWallpaperIndex)
            assertEquals(now, prefs.photoWallpaperLastChanged)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `rotateIfDue migrates legacy users with lastChanged 0L and lastDay before today`() {
        val (prefs, tempDir) = createPrefs()
        try {
            val today = PhotoWallpaper.today()
            prefs.photoWallpaperCount = 3
            prefs.photoWallpaperIndex = 1
            prefs.photoWallpaperInterval = RotationInterval.DAILY.id
            prefs.photoWallpaperLastChanged = 0L
            prefs.photoWallpaperLastDay = today - 1

            val now = 1_700_000_000_000L
            // Legacy user last changed yesterday: treated as due and rotates
            val rotated = PhotoWallpaper.rotateIfDue(prefs, nowMillis = now)

            assertTrue(rotated)
            assertEquals(2, prefs.photoWallpaperIndex)
            assertEquals(now, prefs.photoWallpaperLastChanged)
            assertEquals(today, prefs.photoWallpaperLastDay)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
