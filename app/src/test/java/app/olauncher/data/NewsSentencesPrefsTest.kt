package app.olauncher.data

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsSentencesPrefsTest {

    @Test
    fun `preference keys match constants`() {
        assertEquals("NEWS_SENTENCES_ENABLED", Prefs.NEWS_SENTENCES_ENABLED)
        assertEquals("NEWS_FETCH_DAY", Prefs.NEWS_FETCH_DAY)
        assertEquals("NEWS_FETCH_COUNT", Prefs.NEWS_FETCH_COUNT)
    }

    @Test
    fun `default values match specifications`() {
        assertFalse(Prefs.NEWS_SENTENCES_ENABLED_DEFAULT)
        assertEquals(30, Prefs.NEWS_DAILY_ARTICLE_CAP)
    }

    @Test
    fun `newsSentencesEnabled defaults false and persists under NEWS_SENTENCES_ENABLED`() {
        val map = mutableMapOf<String, Any?>()
        val prefs = NewsSentencesPrefsStore(FakeSharedPreferences(map))

        assertFalse(prefs.newsSentencesEnabled)

        prefs.newsSentencesEnabled = true
        assertTrue(prefs.newsSentencesEnabled)
        assertEquals(true, map[Prefs.NEWS_SENTENCES_ENABLED])

        prefs.newsSentencesEnabled = false
        assertFalse(prefs.newsSentencesEnabled)
        assertEquals(false, map[Prefs.NEWS_SENTENCES_ENABLED])
    }

    @Test
    fun `recordNewsArticlesFetched accumulates count for current day and resets on new day`() {
        val map = mutableMapOf<String, Any?>()
        val prefs = NewsSentencesPrefsStore(FakeSharedPreferences(map))

        assertEquals(0, prefs.newsArticlesFetchedToday(100))

        prefs.recordNewsArticlesFetched(4, 100)
        prefs.recordNewsArticlesFetched(3, 100)

        assertEquals(7, prefs.newsArticlesFetchedToday(100))
        assertEquals(0, prefs.newsArticlesFetchedToday(101))

        prefs.recordNewsArticlesFetched(2, 101)
        assertEquals(2, prefs.newsArticlesFetchedToday(101))
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
}
