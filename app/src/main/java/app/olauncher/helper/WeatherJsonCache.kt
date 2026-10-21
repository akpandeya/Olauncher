package app.olauncher.helper

/**
 * Keeps the last loaded weather in memory. [load] reads the disk cache and may block, so call
 * [get] off the main thread. The main thread only calls [peek]. The cache is reloaded only when
 * the fetched-at stamp (written by WeatherWorker) differs from the one last loaded.
 */
class WeatherJsonCache<T : Any>(private val load: () -> T?) {

    private val lock = Any()
    private var value: T? = null
    private var loadedFetchedAt: Long? = null

    /** Memory only, never touches the disk. */
    fun peek(): T? = synchronized(lock) { value }

    fun isCurrent(fetchedAt: Long): Boolean = synchronized(lock) {
        value != null && loadedFetchedAt == fetchedAt
    }

    /** Returns the in-memory value when [fetchedAt] is unchanged, else reloads from disk. */
    fun get(fetchedAt: Long): T? = synchronized(lock) {
        if (value != null && loadedFetchedAt == fetchedAt) return value
        val loaded = load()
        value = loaded
        loadedFetchedAt = if (loaded != null) fetchedAt else null
        loaded
    }
}
