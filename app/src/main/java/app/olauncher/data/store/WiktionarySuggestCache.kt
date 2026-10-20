package app.olauncher.data.store

import java.util.Locale

/** Small in-memory cache of spelling suggestions per query; Failed results are never stored. */
object WiktionarySuggestCache {
    private const val MAX_ENTRIES = 50
    private val map = object : LinkedHashMap<String, List<String>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?): Boolean =
            size > MAX_ENTRIES
    }

    private fun keyOf(text: String) = text.trim().lowercase(Locale.GERMAN)

    @Synchronized
    fun get(text: String): List<String>? = map[keyOf(text)]

    @Synchronized
    fun put(text: String, words: List<String>) {
        map[keyOf(text)] = words
    }

    @Synchronized
    fun clear() = map.clear()
}
