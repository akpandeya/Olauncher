package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class LetterStripTargetTest {

    // Apps A,B,C (ordered index 0..2) plus one padding row.

    @Test
    fun topSearchNoRecents() {
        // Rows: A B C pad
        assertEquals(0, LetterStripTarget.position(0, false, 4, 0))
        assertEquals(2, LetterStripTarget.position(2, false, 4, 0))
    }

    @Test
    fun topSearchWithRecents() {
        // Rows: header r1 r2 A B C pad
        assertEquals(3, LetterStripTarget.position(0, false, 7, 3))
        assertEquals(5, LetterStripTarget.position(2, false, 7, 3))
    }

    @Test
    fun bottomSearchNoRecents() {
        // Rows: pad C B A
        assertEquals(3, LetterStripTarget.position(0, true, 4, 0))
        assertEquals(1, LetterStripTarget.position(2, true, 4, 0))
    }

    @Test
    fun bottomSearchWithRecents() {
        // Rows: pad C B A header r2 r1
        assertEquals(3, LetterStripTarget.position(0, true, 7, 3))
        assertEquals(1, LetterStripTarget.position(2, true, 7, 3))
    }

    @Test
    fun emptyListReturnsZero() {
        assertEquals(0, LetterStripTarget.position(5, true, 0, 0))
    }
}
