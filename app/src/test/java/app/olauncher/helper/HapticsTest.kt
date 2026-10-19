package app.olauncher.helper

import android.content.ContextWrapper
import android.content.SharedPreferences
import android.view.HapticFeedbackConstants
import app.olauncher.data.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticsTest {

    @Test
    fun `constantFor returns expected constants when enabled is true`() {
        val tick: Int? = Haptics.constantFor(Haptics.Kind.TICK, enabled = true)
        val confirm: Int? = Haptics.constantFor(Haptics.Kind.CONFIRM, enabled = true)
        val longPress: Int? = Haptics.constantFor(Haptics.Kind.LONG_PRESS, enabled = true)
        val clockTick: Int? = Haptics.constantFor(Haptics.Kind.CLOCK_TICK, enabled = true)

        assertEquals(HapticFeedbackConstants.CLOCK_TICK, tick)
        assertEquals(HapticFeedbackConstants.CONFIRM, confirm)
        assertEquals(HapticFeedbackConstants.LONG_PRESS, longPress)
        assertEquals(HapticFeedbackConstants.CLOCK_TICK, clockTick)
    }

    @Test
    fun `constantFor returns null when enabled is false`() {
        assertNull(Haptics.constantFor(Haptics.Kind.TICK, enabled = false))
        assertNull(Haptics.constantFor(Haptics.Kind.CONFIRM, enabled = false))
        assertNull(Haptics.constantFor(Haptics.Kind.LONG_PRESS, enabled = false))
        assertNull(Haptics.constantFor(Haptics.Kind.CLOCK_TICK, enabled = false))
    }

    @Test
    fun `haptics preference defaults to true and persists changes`() {
        assertEquals("HAPTICS", Prefs.HAPTICS)

        val map = mutableMapOf<String, Any?>()
        val prefs = Prefs(FakeContext(FakeSharedPreferences(map)))

        assertTrue("Haptics should default to true", prefs.haptics)

        prefs.haptics = false
        assertFalse(prefs.haptics)
        assertEquals(false, map[Prefs.HAPTICS])

        prefs.haptics = true
        assertTrue(prefs.haptics)
        assertEquals(true, map[Prefs.HAPTICS])
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
