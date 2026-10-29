package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ReaderSelectionTest {

    @Test
    fun initialStateIsEmpty() {
        val selection = ReaderSelection<String>()
        assertTrue(selection.isEmpty)
        assertEquals(0, selection.count)
        assertTrue(selection.pending.isEmpty())
        assertNull(selection.lastTapped)
    }

    @Test
    fun tapAddsUnsavedWord() {
        val selection = ReaderSelection<String>()
        val added = selection.tap("apfel")

        assertTrue(added)
        assertFalse(selection.isEmpty)
        assertEquals(1, selection.count)
        assertEquals(setOf("apfel"), selection.pending)
        assertTrue(selection.isPending("apfel"))
        assertEquals("apfel", selection.lastTapped)
    }

    @Test
    fun tapTogglesOffAlreadyPendingWord() {
        val selection = ReaderSelection<String>()
        selection.tap("apfel")
        val added = selection.tap("apfel")

        assertFalse(added)
        assertTrue(selection.isEmpty)
        assertEquals(0, selection.count)
        assertFalse(selection.isPending("apfel"))
        assertEquals("apfel", selection.lastTapped)
    }

    @Test
    fun multiWordSelectionTracksPendingCountAndLastTapped() {
        val selection = ReaderSelection<String>()

        assertTrue(selection.tap("apfel"))
        assertEquals(1, selection.count)
        assertEquals("apfel", selection.lastTapped)

        assertTrue(selection.tap("birne"))
        assertEquals(2, selection.count)
        assertEquals("birne", selection.lastTapped)

        assertTrue(selection.tap("citrone"))
        assertEquals(3, selection.count)
        assertEquals("citrone", selection.lastTapped)

        assertEquals(listOf("apfel", "birne", "citrone"), selection.pending.toList())

        // Untoggling "birne" keeps the other selections and updates lastTapped
        assertFalse(selection.tap("birne"))
        assertEquals(2, selection.count)
        assertEquals("birne", selection.lastTapped)
        assertEquals(listOf("apfel", "citrone"), selection.pending.toList())
        assertTrue(selection.isPending("apfel"))
        assertFalse(selection.isPending("birne"))
        assertTrue(selection.isPending("citrone"))
    }

    @Test
    fun alreadySavedWordCannotBeSelectedButUpdatesLastTapped() {
        val savedWords = mutableSetOf("dattel")
        val selection = ReaderSelection<String>(isSaved = { it in savedWords })

        val added = selection.tap("dattel")

        assertFalse(added)
        assertTrue(selection.isEmpty)
        assertEquals(0, selection.count)
        assertFalse(selection.isPending("dattel"))
        // Meaning line follows the most recently tapped word:
        assertEquals("dattel", selection.lastTapped)
    }

    @Test
    fun savedWordDoesNotDisruptExistingPendingSelection() {
        val savedWords = mutableSetOf("dattel")
        val selection = ReaderSelection<String>(isSaved = { it in savedWords })

        selection.tap("apfel")
        assertEquals(1, selection.count)

        // Tapping saved word does not add it or drop existing selections
        val added = selection.tap("dattel")
        assertFalse(added)
        assertEquals(1, selection.count)
        assertEquals(setOf("apfel"), selection.pending)
        assertEquals("dattel", selection.lastTapped)
    }

    @Test
    fun clearEmptiesPendingSelection() {
        val selection = ReaderSelection<String>()
        selection.tap("apfel")
        selection.tap("birne")
        assertEquals(2, selection.count)

        selection.clear()
        assertTrue(selection.isEmpty)
        assertEquals(0, selection.count)
        assertTrue(selection.pending.isEmpty())
        assertFalse(selection.isPending("apfel"))
        assertFalse(selection.isPending("birne"))
    }

    @Test
    fun screenWordDistinguishingValuesWiring() {
        val wordA = ScreenWord("Aufgabe", ScreenBox(10, 20, 80, 50), nodeId = 101, charRange = 0..6)
        val wordB = ScreenWord("Lösung", ScreenBox(90, 20, 150, 50), nodeId = 101, charRange = 8..13)
        val wordSaved = ScreenWord("Beispiel", ScreenBox(10, 60, 90, 90), nodeId = 102, charRange = 0..7)

        val savedSet = setOf(wordSaved)
        val selection = ReaderSelection<ScreenWord>(isSaved = { it in savedSet })

        assertTrue(selection.tap(wordA))
        assertTrue(selection.tap(wordB))
        assertFalse(selection.tap(wordSaved))

        assertEquals(2, selection.count)
        assertEquals(listOf(wordA, wordB), selection.pending.toList())
        assertTrue(selection.isPending(wordA))
        assertTrue(selection.isPending(wordB))
        assertFalse(selection.isPending(wordSaved))
        assertEquals(wordSaved, selection.lastTapped)
    }

    @Test
    fun sameTokenAtTwoPositionsSharesOnePendingEntry() {
        // "Regierung" in one paragraph, "Regierung." in another: the store dedupes by
        // token, so the two ScreenWords must share one pending entry (Save (1), one
        // store write) — never two.
        val regierung = ScreenWord("Regierung", ScreenBox(10, 20, 90, 50), nodeId = 101, charRange = 0..8)
        val regierungPunkt = ScreenWord("Regierung.", ScreenBox(300, 420, 400, 450), nodeId = 103, charRange = 40..49)
        assertNotEquals(regierung, regierungPunkt)
        assertEquals(SavedMarks.keyOf(regierung), SavedMarks.keyOf(regierungPunkt))

        val selection = ReaderSelection<ScreenWord>(keyOf = SavedMarks::keyOf)

        assertTrue(selection.tap(regierung))
        assertEquals(1, selection.count)

        // Tapping another occurrence of the same token keeps the shared entry (still
        // Save (1)) and moves it to the occurrence just tapped.
        assertTrue(selection.tap(regierungPunkt))
        assertEquals(1, selection.count)
        assertEquals(listOf(regierungPunkt), selection.pending.toList())
        assertEquals(regierungPunkt, selection.lastTapped)
        assertTrue(selection.isPending(regierung))

        // Re-tapping the highlighted occurrence toggles the shared entry off.
        assertFalse(selection.tap(regierungPunkt))
        assertEquals(0, selection.count)
        assertFalse(selection.isPending(regierungPunkt))
        assertEquals(regierungPunkt, selection.lastTapped)
    }

    @Test
    fun pendingSetIsUnmodifiableAndDetached() {
        val selection = ReaderSelection<String>()
        selection.tap("apfel")
        val snapshot = selection.pending

        try {
            @Suppress("UNCHECKED_CAST")
            (snapshot as MutableSet<String>).add("birne")
            fail("pending set must not be modifiable")
        } catch (_: UnsupportedOperationException) {
        }

        // Later selections do not leak into an earlier snapshot.
        selection.tap("birne")
        assertEquals(setOf("apfel"), snapshot)
        assertEquals(setOf("apfel", "birne"), selection.pending)
    }
}
