package app.olauncher.data

import android.content.ContextWrapper
import android.content.SharedPreferences
import app.olauncher.R
import app.olauncher.ui.ShortcutIconRenderer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ShortcutActionTest {

    @Test
    fun `defaults keep the original slot mapping`() {
        assertEquals(ShortcutAction.SEARCH, ShortcutAction.defaultFor(1))
        assertEquals(ShortcutAction.GALLERY, ShortcutAction.defaultFor(2))
        assertEquals(ShortcutAction.CAMERA, ShortcutAction.defaultFor(3))
        assertEquals(ShortcutAction.MESSAGES, ShortcutAction.defaultFor(4))
        assertEquals(ShortcutAction.PHONE, ShortcutAction.defaultFor(5))
        assertEquals(ShortcutAction.SETTINGS, ShortcutAction.defaultFor(6))
        for (slot in 7..Constants.MAX_SHORTCUT_SLOTS) assertNull(ShortcutAction.defaultFor(slot))
    }

    @Test
    fun `ids are stable and unique`() {
        assertEquals(listOf(1, 2, 3, 4, 5, 6), ShortcutAction.values().map { it.id })
        assertNull(ShortcutAction.fromId(ShortcutAction.NONE_ID))
    }

    @Test
    fun `stored id overrides the default in any slot`() {
        for (slot in 1..Constants.MAX_SHORTCUT_SLOTS) {
            for (action in ShortcutAction.values()) {
                assertEquals(action, ShortcutAction.resolve(slot, action.id))
            }
        }
    }

    @Test
    fun `unset or unknown id falls back to the slot default`() {
        assertEquals(ShortcutAction.CAMERA, ShortcutAction.resolve(3, ShortcutAction.NONE_ID))
        assertEquals(ShortcutAction.CAMERA, ShortcutAction.resolve(3, 99))
        assertNull(ShortcutAction.resolve(9, ShortcutAction.NONE_ID))
        assertNull(ShortcutAction.resolve(9, -5))
    }

    @Test
    fun `renderer icon and label follow the chosen built-in`() {
        assertEquals(R.drawable.ic_shortcut_more, ShortcutIconRenderer.getBuiltinIconRes(1, ShortcutAction.SETTINGS.id))
        assertEquals(R.string.shortcut_more, ShortcutIconRenderer.getBuiltinLabelRes(1, ShortcutAction.SETTINGS.id))
        assertEquals(R.drawable.ic_shortcut_camera, ShortcutIconRenderer.getBuiltinIconRes(10, ShortcutAction.CAMERA.id))
        assertEquals(R.string.shortcut_camera, ShortcutIconRenderer.getBuiltinLabelRes(10, ShortcutAction.CAMERA.id))
        assertEquals(R.drawable.ic_shortcut_search, ShortcutIconRenderer.getBuiltinIconRes(1, ShortcutAction.NONE_ID))
    }

    @Test
    fun `prefs store a built-in per slot and reset restores the default`() {
        val map = mutableMapOf<String, Any?>()
        val prefs = Prefs(FakeContext(FakeSharedPreferences(map)))

        // Existing users: no new key, defaults unchanged
        assertEquals(ShortcutAction.PHONE, prefs.getShortcutAction(5))
        assertNull(prefs.getShortcutAction(8))

        prefs.setShortcutApp(5, "Clock", "com.example.clock", null, "")
        prefs.setShortcutBuiltin(5, ShortcutAction.GALLERY.id)
        prefs.setShortcutBuiltin(8, ShortcutAction.GALLERY.id)
        assertEquals(Constants.SHORTCUT_MODE_DEFAULT, prefs.getShortcutMode(5))
        assertEquals(ShortcutAction.GALLERY, prefs.getShortcutAction(5))
        assertEquals(ShortcutAction.GALLERY, prefs.getShortcutAction(8))
        assertEquals(ShortcutAction.GALLERY.id, map["SHORTCUT_BUILTIN_8"])
        // Other slots are untouched
        assertEquals(ShortcutAction.CAMERA, prefs.getShortcutAction(3))

        prefs.resetShortcut(5)
        prefs.resetShortcut(8)
        assertEquals(ShortcutAction.PHONE, prefs.getShortcutAction(5))
        assertNull(prefs.getShortcutAction(8))
        assertFalse(map.containsKey("SHORTCUT_BUILTIN_5"))
        assertFalse(map.containsKey("SHORTCUT_BUILTIN_8"))
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
