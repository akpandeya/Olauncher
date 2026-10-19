package app.olauncher.data

import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class FontPrefsTest {

    @Test
    fun `font family constants match expected values`() {
        assertEquals("FONT_FAMILY", Prefs.FONT_FAMILY)
        assertEquals(0, Constants.FontFamily.SYSTEM)
        assertEquals(1, Constants.FontFamily.INTER)
        assertEquals(2, Constants.FontFamily.PLEX)
        assertEquals(0, Constants.FONT_FAMILY_SYSTEM)
        assertEquals(1, Constants.FONT_FAMILY_INTER)
        assertEquals(2, Constants.FONT_FAMILY_PLEX)
        assertEquals(0, Prefs.FONT_FAMILY_SYSTEM)
        assertEquals(1, Prefs.FONT_FAMILY_INTER)
        assertEquals(2, Prefs.FONT_FAMILY_PLEX)
    }

    @Test
    fun `font family default is System`() {
        val map = mutableMapOf<String, Any?>()
        val prefs = Prefs(FakeContext(FakeSharedPreferences(map)))

        assertEquals(Constants.FontFamily.SYSTEM, prefs.fontFamily)
        assertEquals(0, prefs.fontFamily)
    }

    @Test
    fun `font family round-trip persists and retrieves each value`() {
        val map = mutableMapOf<String, Any?>()
        val prefs = Prefs(FakeContext(FakeSharedPreferences(map)))

        prefs.fontFamily = Constants.FontFamily.INTER
        assertEquals(Constants.FontFamily.INTER, prefs.fontFamily)
        assertEquals(1, map[Prefs.FONT_FAMILY])

        prefs.fontFamily = Constants.FontFamily.PLEX
        assertEquals(Constants.FontFamily.PLEX, prefs.fontFamily)
        assertEquals(2, map[Prefs.FONT_FAMILY])

        prefs.fontFamily = Constants.FontFamily.SYSTEM
        assertEquals(Constants.FontFamily.SYSTEM, prefs.fontFamily)
        assertEquals(0, map[Prefs.FONT_FAMILY])
    }

    @Test
    fun `unknown stored value falls back to System`() {
        val map = mutableMapOf<String, Any?>()
        val prefs = Prefs(FakeContext(FakeSharedPreferences(map)))

        listOf(-1, 3, 4, 99, 999, Int.MIN_VALUE, Int.MAX_VALUE).forEach { unknownValue ->
            map[Prefs.FONT_FAMILY] = unknownValue
            assertEquals(
                "Value $unknownValue must fall back to System default",
                Constants.FontFamily.SYSTEM,
                prefs.fontFamily
            )
        }
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
}
