package app.olauncher.data.store

import java.util.Collections

/**
 * Context-free selection logic for the full-screen Reader view (#523).
 *
 * Models a pending set of words to be saved. Unsaved words can be toggled into and out
 * of the pending set. Already-saved words cannot be selected. Items are identified by
 * [keyOf], so two occurrences of the same token ("Regierung" / "Regierung.") share one
 * pending entry — the store would dedupe them anyway. The entry stores the occurrence
 * currently highlighted: re-tapping that occurrence deselects, tapping another
 * occurrence keeps the entry and moves it to the occurrence just tapped.
 * [lastTapped] always tracks the most recently tapped item so the meaning line can
 * follow it regardless of selection outcome.
 */
class ReaderSelection<T : Any>(
    private val isSaved: (T) -> Boolean = { false },
    private val keyOf: (T) -> Any = { it },
) {
    /** Pending items by key, in selection order — the single source of truth. */
    private val pendingByKey = LinkedHashMap<Any, T>()

    /** The most recently tapped item, regardless of whether it was added, toggled, or saved. */
    var lastTapped: T? = null
        private set

    /** The unmodifiable set of items pending save, in selection order. */
    val pending: Set<T>
        get() = Collections.unmodifiableSet(LinkedHashSet(pendingByKey.values))

    /** Number of items currently pending save. */
    val count: Int get() = pendingByKey.size

    val isEmpty: Boolean get() = pendingByKey.isEmpty()

    fun isPending(item: T): Boolean = pendingByKey.containsKey(keyOf(item))

    /**
     * Taps [item]:
     * 1. Updates [lastTapped] to [item].
     * 2. If [item] is saved according to [isSaved], ensures its key is not in [pending] and returns false.
     * 3. Otherwise toggles by key: removes only when the tapped [item] is the occurrence
     *    currently stored for its key (re-tapping the highlighted copy deselects); a
     *    different occurrence of the same token keeps the entry and replaces the stored
     *    occurrence with [item] — the highlight moves, the count does not change.
     *
     * Returns true if [item] is pending after this tap, false otherwise.
     */
    fun tap(item: T): Boolean {
        lastTapped = item
        val key = keyOf(item)
        if (isSaved(item)) {
            pendingByKey.remove(key)
            return false
        }
        if (pendingByKey[key] == item) {
            pendingByKey.remove(key)
            return false
        }
        pendingByKey[key] = item
        return true
    }

    /**
     * Adds every item in [items] that is neither saved nor already pending, add-only: an
     * existing entry is never toggled off, so a stroke or circle over an already-picked
     * word keeps the pending set growing. New entries keep the given order; an item whose
     * key is already pending keeps its earlier position and occurrence. [lastTapped] is set
     * to the first given item, matching the meaning card following the gesture's head.
     * Returns how many items were added.
     */
    fun addAll(items: List<T>): Int {
        items.firstOrNull()?.let { lastTapped = it }
        var added = 0
        for (item in items) {
            if (isSaved(item)) continue
            val key = keyOf(item)
            if (pendingByKey.containsKey(key)) continue
            pendingByKey[key] = item
            added++
        }
        return added
    }

    /** Clears all pending items (e.g. after saving). */
    fun clear() {
        pendingByKey.clear()
    }
}
