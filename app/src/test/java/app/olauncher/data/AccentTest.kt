package app.olauncher.data

import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class AccentTest {

    @Test
    fun resolve_off_returnsFallback() {
        assertEquals(-1, Accent.resolve(Accent.OFF, -1) { it.id * 10 })
    }

    @Test
    fun resolve_eachAccent_returnsOwnValue() {
        for (accent in Accent.entries.filter { it != Accent.OFF }) {
            assertEquals(accent.id * 10, Accent.resolve(accent, -1) { it.id * 10 })
        }
    }

    @Test
    fun `all accent options have expected ids`() {
        assertEquals(0, Accent.OFF.id)
        assertEquals(1, Accent.SYSTEM.id)
        assertEquals(2, Accent.SAGE.id)
        assertEquals(3, Accent.SAND.id)
        assertEquals(4, Accent.SKY.id)
        assertEquals(5, Accent.ROSE.id)
    }

    @Test
    fun `accent ids are all distinct`() {
        val ids = Accent.entries.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `fromId returns corresponding accent for valid ids`() {
        assertEquals(Accent.OFF, Accent.fromId(0))
        assertEquals(Accent.SYSTEM, Accent.fromId(1))
        assertEquals(Accent.SAGE, Accent.fromId(2))
        assertEquals(Accent.SAND, Accent.fromId(3))
        assertEquals(Accent.SKY, Accent.fromId(4))
        assertEquals(Accent.ROSE, Accent.fromId(5))
        assertEquals(Accent.WALLPAPER, Accent.fromId(6))
    }

    @Test
    fun `fromId returns OFF for unknown or invalid ids`() {
        val invalidIds = listOf(-1, -100, 7, 8, 42, 99, 999, Int.MIN_VALUE, Int.MAX_VALUE)
        for (id in invalidIds) {
            assertEquals("Id $id must fall back to OFF", Accent.OFF, Accent.fromId(id))
        }
    }

    @Test
    fun `accent preference constant is ACCENT`() {
        assertEquals("ACCENT", Prefs.ACCENT)
    }

    @Test
    fun `accent preference default is OFF (0)`() {
        val map = mutableMapOf<String, Any?>()
        val prefs = Prefs(FakeContext(FakeSharedPreferences(map)))
        assertEquals(Accent.OFF.id, prefs.accent)
        assertEquals(0, prefs.accent)
    }

    @Test
    fun `accent preference round-trip persists each option`() {
        val map = mutableMapOf<String, Any?>()
        val prefs = Prefs(FakeContext(FakeSharedPreferences(map)))

        Accent.entries.forEach { accent ->
            prefs.accent = accent.id
            assertEquals(accent.id, prefs.accent)
            assertEquals(accent.id, map[Prefs.ACCENT])
        }
    }

    @Test
    fun `accent preference normalizes unknown value to OFF`() {
        val map = mutableMapOf<String, Any?>()
        val prefs = Prefs(FakeContext(FakeSharedPreferences(map)))

        prefs.accent = 42
        assertEquals(Accent.OFF.id, prefs.accent)
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
