package app.olauncher.helper

import app.olauncher.data.store.SavedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoStateTest {

    private val keyA = "com.a|user1"
    private val keyB = "com.b|user1"

    @Test
    fun hide_thenUndo_restoresExactHiddenSet() {
        val before = setOf(keyB)
        val change = HiddenChange.toggle(before, keyA)
        val after = change.apply(before)
        assertEquals(setOf(keyA, keyB), after)
        assertEquals(before, change.undo(after))
    }

    @Test
    fun unhide_thenUndo_putsAppBackInHiddenSet() {
        val before = setOf(keyA, keyB)
        val change = HiddenChange.toggle(before, keyA)
        assertTrue(change.wasHidden)
        val after = change.apply(before)
        assertEquals(setOf(keyB), after)
        assertEquals(before, change.undo(after))
    }

    @Test
    fun hideUndo_keepsEntriesChangedMeanwhile() {
        val change = HiddenChange.toggle(emptySet(), keyA)
        val later = change.apply(emptySet()) + keyB
        assertEquals(setOf(keyB), change.undo(later))
    }

    private val slot = HomeSlotSnapshot(
        name = "My Mail",
        packageName = "com.mail",
        user = "user1",
        activityClassName = "com.mail.Main",
        isShortcut = true,
        shortcutId = "sc-7",
    )

    @Test
    fun removeFromHome_thenUndo_restoresEveryFieldIncludingLabel() {
        val removal = HomeSlotRemoval(slot)
        val after = removal.apply()
        assertTrue(after.isEmpty)
        assertEquals(slot, removal.undo(after))
        assertEquals("My Mail", removal.undo(after).name)
    }

    @Test
    fun removeFromHome_undoDoesNotOverwriteSlotFilledMeanwhile() {
        val other = slot.copy(name = "Other", packageName = "com.other")
        assertEquals(other, HomeSlotRemoval(slot).undo(other))
    }

    @Test
    fun archive_thenUndo_restoresStampInPlace() {
        val items = listOf(
            SavedItem(1, "u1", "t1", savedAt = 10),
            SavedItem(2, "u2", "t2", savedAt = 20),
            SavedItem(3, "u3", "t3", savedAt = 30, archivedAt = 99),
        )
        val change = ArchiveChange(id = 2, previousArchivedAt = items[1].archivedAt)
        val archived = items.map { if (it.id == 2L) it.copy(archivedAt = 500) else it }
        assertEquals(items, change.undo(archived))
    }

    @Test
    fun archive_undoOfAlreadyArchivedItemRestoresOldStamp() {
        val item = SavedItem(1, "u", "t", savedAt = 1, archivedAt = 42)
        val change = ArchiveChange(1, 42)
        assertEquals(listOf(item), change.undo(listOf(item.copy(archivedAt = 77))))
    }
}
