package app.olauncher.helper

import app.olauncher.data.store.SavedItem

/**
 * Context-free before/after state for the actions that show an Undo message
 * (hide, remove from home, archive). Each change remembers the state it replaced
 * and only undoes itself when nothing else has touched that state since.
 */

/** Hiding (or unhiding) one app. [key] is `package|user`; [wasHidden] is its state before. */
data class HiddenChange(val key: String, val wasHidden: Boolean) {
    /** The hidden set after the action: toggles [key]. */
    fun apply(current: Set<String>): Set<String> =
        if (wasHidden) current - key else current + key

    /** The hidden set after Undo: puts [key] back to its earlier state, leaves other entries alone. */
    fun undo(current: Set<String>): Set<String> =
        if (wasHidden) current + key else current - key

    companion object {
        fun toggle(current: Set<String>, key: String): HiddenChange = HiddenChange(key, key in current)
    }
}

/** All stored fields of one home slot. */
data class HomeSlotSnapshot(
    val name: String,
    val packageName: String,
    val user: String,
    val activityClassName: String,
    val isShortcut: Boolean,
    val shortcutId: String,
) {
    val isEmpty: Boolean get() = packageName.isBlank() && name.isBlank()

    companion object {
        val EMPTY = HomeSlotSnapshot("", "", "", "", isShortcut = false, shortcutId = "")
    }
}

/** Removing an app from a home slot: [before] is the full slot, label included. */
data class HomeSlotRemoval(val before: HomeSlotSnapshot) {
    /** The slot after the removal. */
    fun apply(): HomeSlotSnapshot = HomeSlotSnapshot.EMPTY

    /**
     * The slot after Undo. If the user already put something else in the slot
     * ([current] is not empty), it is left as is.
     */
    fun undo(current: HomeSlotSnapshot): HomeSlotSnapshot =
        if (current.isEmpty) before else current
}

/** Archiving one saved item; [previousArchivedAt] is its archive stamp before (null = was active). */
data class ArchiveChange(val id: Long, val previousArchivedAt: Long?) {
    /** The list after Undo: the item gets its old stamp back, in place, other items untouched. */
    fun undo(items: List<SavedItem>): List<SavedItem> =
        items.map { if (it.id == id) it.copy(archivedAt = previousArchivedAt) else it }
}
