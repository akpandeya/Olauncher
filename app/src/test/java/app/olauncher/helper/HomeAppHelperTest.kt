package app.olauncher.helper

import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.UserHandle
import app.olauncher.data.AppModel
import app.olauncher.data.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [HomeAppHelper].
 *
 * Tests that require [UserHandle.toString] (not mocked in the JVM android stub)
 * are intentionally excluded — those code paths are exercised by the
 * assembleDebug + on-device smoke test.
 */
class HomeAppHelperTest {

    // ── Fakes ─────────────────────────────────────────────────────────────────

    private class FakeEditor(private val map: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removed = mutableSetOf<String>()
        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { removed.add(key) }
        override fun clear() = apply { map.clear() }
        override fun commit(): Boolean { flush(); return true }
        override fun apply() { flush() }
        private fun flush() {
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
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    private class FakeContext(private val sp: SharedPreferences) : ContextWrapper(null) {
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = sp
    }

    private fun makePrefs(map: MutableMap<String, Any?> = mutableMapOf()): Prefs =
        Prefs(FakeContext(FakeSharedPreferences(map)))

    private val dummyUser: UserHandle by lazy {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val f = unsafeClass.getDeclaredField("theUnsafe").also { it.isAccessible = true }
        val unsafe = f.get(null)
        val allocate = unsafeClass.getMethod("allocateInstance", Class::class.java)
        allocate.invoke(unsafe, UserHandle::class.java) as UserHandle
    }

    private fun app(pkg: String = "com.example.app"): AppModel.App = AppModel.App(
        appLabel = "App",
        key = null,
        appPackage = pkg,
        activityClassName = "com.example.app.MainActivity",
        isNew = false,
        user = dummyUser,
    )

    // ── Constants ─────────────────────────────────────────────────────────────

    @Test
    fun `MAX_HOME_APPS is 8`() {
        assertEquals(8, HomeAppHelper.MAX_HOME_APPS)
    }

    // ── findTargetSlot – pure Prefs lookup, no UserHandle.toString ────────────

    @Test
    fun `findTargetSlot returns 1 when homeAppsNum is 0`() {
        val prefs = makePrefs(mutableMapOf("HOME_APPS_NUM" to 0))
        assertEquals(1, HomeAppHelper.findTargetSlot(prefs))
    }

    @Test
    fun `findTargetSlot returns first empty slot among occupied ones`() {
        val map = mutableMapOf<String, Any?>(
            "HOME_APPS_NUM" to 4,
            "APP_PACKAGE_1" to "p1",
            "APP_PACKAGE_2" to "p2",
            "APP_PACKAGE_3" to "",
        )
        assertEquals(3, HomeAppHelper.findTargetSlot(makePrefs(map)))
    }

    @Test
    fun `findTargetSlot expands homeAppsNum when all current slots filled and count below 8`() {
        val map = mutableMapOf<String, Any?>(
            "HOME_APPS_NUM" to 3,
            "APP_PACKAGE_1" to "p1",
            "APP_PACKAGE_2" to "p2",
            "APP_PACKAGE_3" to "p3",
        )
        assertEquals(4, HomeAppHelper.findTargetSlot(makePrefs(map)))
    }

    @Test
    fun `findTargetSlot returns null when all 8 slots are occupied`() {
        val map = mutableMapOf<String, Any?>(
            "HOME_APPS_NUM" to 8,
            "APP_PACKAGE_1" to "p1", "APP_PACKAGE_2" to "p2",
            "APP_PACKAGE_3" to "p3", "APP_PACKAGE_4" to "p4",
            "APP_PACKAGE_5" to "p5", "APP_PACKAGE_6" to "p6",
            "APP_PACKAGE_7" to "p7", "APP_PACKAGE_8" to "p8",
        )
        assertNull(HomeAppHelper.findTargetSlot(makePrefs(map)))
    }

    // ── addToHome early-exit guards – no UserHandle.toString needed ───────────

    @Test
    fun `addToHome returns HomeFull for archived app`() {
        val prefs = makePrefs()
        val archived = app().copy(isArchived = true)
        assertEquals(HomeAppHelper.AddResult.HomeFull, HomeAppHelper.addToHome(prefs, archived))
    }

    @Test
    fun `addToHome returns HomeFull for non-App non-PinnedShortcut model`() {
        // AppModel.Result is another concrete AppModel subtype that is not App or PinnedShortcut.
        // addToHome guards against this with an early return before calling UserHandle.toString().
        val prefs = makePrefs()
        val result = AppModel.Result(text = "= 42", action = AppModel.Result.Action.Copy("42"))
        assertEquals(HomeAppHelper.AddResult.HomeFull, HomeAppHelper.addToHome(prefs, result))
    }
}
