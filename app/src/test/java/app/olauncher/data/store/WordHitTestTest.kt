package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class WordHitTestTest {

    /** Boxed word on node 0; boxes are small enough to reason about by hand. */
    private fun word(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        ScreenWord(text, ScreenBox(left, top, right, bottom), nodeId = 0, charRange = 0 until text.length)

    /** A word without geometry (its node shipped no character locations) — never hittable. */
    private fun unpositioned(text: String) =
        ScreenWord(text, null, nodeId = 0, charRange = 0 until text.length)

    // Three lines of three words; line 3 holds a single word. Word boxes are 20 tall,
    // lines start at y = 0, 40 and 80, so the gap between lines is 20 pixels.
    private val page = listOf(
        word("eins", 0, 0, 50, 20),
        word("zwei", 60, 0, 110, 20),
        word("drei", 120, 0, 170, 20),
        word("vier", 0, 40, 50, 60),
        word("fünf", 60, 40, 110, 60),
        word("sechs", 120, 40, 170, 60),
        word("sieben", 0, 80, 50, 100),
        word("acht", 60, 80, 110, 100),
        word("neun", 120, 80, 170, 100),
    )

    private fun texts(selection: Selection?): List<String> = when (selection) {
        is Selection.Single -> listOf(selection.word.text)
        is Selection.Phrase -> selection.words.map { it.text }
        null -> emptyList()
    }

    // -- tap -----------------------------------------------------------------------------

    @Test
    fun tapInsideBoxHitsThatWord() {
        val hit = WordHitTest.tap(page, Point(80, 10), slop = 0)
        assertSame(page[1], hit) // zwei
        // Edges count as inside the box.
        assertSame(page[0], WordHitTest.tap(page, Point(50, 20), slop = 0))
    }

    @Test
    fun tapInGapResolvesToNearerWord() {
        // Gap between "eins" (ends at x=50) and "zwei" (starts at x=60), midpoint x=55.
        assertSame(page[0], WordHitTest.tap(page, Point(52, 10), slop = 8)) // nearer eins
        assertSame(page[1], WordHitTest.tap(page, Point(58, 10), slop = 8)) // nearer zwei
        // Exactly in the middle is a tie — reading order wins.
        assertSame(page[0], WordHitTest.tap(page, Point(55, 10), slop = 8))
    }

    @Test
    fun tapBeyondSlopReturnsNull() {
        // Nearest box is "eins" (starts at x=0), 10 pixels away from x=-10.
        assertNull(WordHitTest.tap(page, Point(-10, 10), slop = 9))
        // At exactly the slop distance it still resolves.
        assertSame(page[0], WordHitTest.tap(page, Point(-10, 10), slop = 10))
        // Negative slop is a tap that must land inside a box.
        assertNull(WordHitTest.tap(page, Point(55, 10), slop = -1))
        assertNull(WordHitTest.tap(emptyList(), Point(0, 0), slop = 10))
    }

    @Test
    fun tapBetweenLinesResolvesToCloserLine() {
        // Line 1 spans y 0..20, line 2 y 40..60. Point y=31 is 11 from line 1, 9 from line 2.
        assertSame(page[0], WordHitTest.tap(page, Point(25, 21), slop = 30)) // closer to line 1
        assertSame(page[3], WordHitTest.tap(page, Point(25, 31), slop = 30)) // closer to line 2
        // Tie at y=30 (10 from each) — reading order, so the line above wins.
        assertSame(page[0], WordHitTest.tap(page, Point(25, 30), slop = 30))
    }

    @Test
    fun tapSkipsWordsWithoutBox() {
        assertNull(WordHitTest.tap(listOf(unpositioned("Hallo")), Point(10, 10), slop = 100))
        val withGhost = listOf(unpositioned("Geist"), word("echt", 0, 0, 50, 20))
        // The ghost sits first in reading order but has no geometry to hit.
        assertSame(withGhost[1], WordHitTest.tap(withGhost, Point(10, 10), slop = 0))
    }

    @Test
    fun zeroAreaBoxIsNeverHitAndNeverExtendsSelection() {
        // Hidden nodes park at 0,0,0,0: their words must not be tappable at the frame's
        // corner, and their zero-height band at y=0 must not catch the long-press drag.
        val ghost = ScreenWord("Geist", ScreenBox(0, 0, 0, 0), nodeId = 0, charRange = 0 until 5)
        val words = listOf(ghost, word("echt", 0, 40, 50, 60))
        assertNull(WordHitTest.tap(words, Point(0, 0), slop = 20))
        assertSame(words[1], WordHitTest.tap(words, Point(10, 50), slop = 0))

        // Dragging from "echt" up toward y=0 stays within "echt"'s own line.
        val selection = WordHitTest.extendByLine(Selection.Single(words[1]), Point(0, 0), words)
        assertEquals(listOf("echt"), texts(selection))
    }

    // -- stroke --------------------------------------------------------------------------

    @Test
    fun strokeOverOneWordGivesSingle() {
        val selection = WordHitTest.stroke(
            page,
            listOf(Point(-10, 10), Point(200, 10)), // crosses "eins", "zwei" and "drei"
        )
        // Only the requested word participates: stroke over a slice of one word.
        val single = WordHitTest.stroke(
            listOf(page[1]),
            listOf(Point(-10, 10), Point(200, 10)),
        )
        assertTrue(single is Selection.Single)
        assertSame(page[1], single!!.head)
        assertEquals(listOf("eins", "zwei", "drei"), texts(selection))
    }

    @Test
    fun strokeOverSeveralWordsGivesPhraseInReadingOrder() {
        // Swipe right to left over "eins" and "zwei": reading order still puts eins first.
        val selection = WordHitTest.stroke(
            page,
            listOf(Point(80, 10), Point(20, 10)),
        )
        assertTrue(selection is Selection.Phrase)
        assertEquals(listOf("eins", "zwei"), texts(selection))
        assertSame(page[0], selection!!.head)
    }

    @Test
    fun strokeOverTwoWordsReturnsBothForAddAll() {
        // The overlay feeds every reported word into ReaderSelection.addAll, so a stroke
        // over two words must report both — not only the head.
        val selection = WordHitTest.stroke(page, listOf(Point(30, 10), Point(90, 10)))
        assertTrue(selection is Selection.Phrase)
        assertEquals(listOf("eins", "zwei"), texts(selection))
        assertSame(page[0], selection!!.head)
    }

    @Test
    fun strokeSkipsNullBoxWordBetweenTouchedWords() {
        val words = listOf(
            word("eins", 0, 0, 50, 20),
            unpositioned("Geist"),
            word("drei", 120, 0, 170, 20),
        )
        val selection = WordHitTest.stroke(words, listOf(Point(-10, 10), Point(200, 10)))
        // The phrase holds only hittable words, in reading order.
        assertEquals(listOf("eins", "drei"), texts(selection))
        assertTrue(selection is Selection.Phrase)
        assertSame(words[0], selection!!.head)
    }

    @Test
    fun strokeTouchingNothingReturnsNull() {
        assertNull(WordHitTest.stroke(page, listOf(Point(200, 200), Point(300, 200))))
        assertNull(WordHitTest.stroke(page, emptyList()))
        assertNull(WordHitTest.stroke(page, listOf(Point(10, 10)))) // a point is not a stroke
        assertNull(WordHitTest.stroke(listOf(unpositioned("Geist")), listOf(Point(-10, 10), Point(200, 10))))
    }

    @Test
    fun closedStrokeSelectsWordsInsideEvenWithoutCrossing() {
        // A square loop around "fünf" (box 60..110, 40..60), sized to graze no other word:
        // the segments themselves never touch the box, but its center lies inside the
        // traced polygon.
        val loop = listOf(
            Point(55, 25), Point(115, 25), Point(115, 75), Point(55, 75), Point(55, 25),
        )
        // Open square (first != last by 50 pixels): nothing is selected.
        assertNull(WordHitTest.stroke(page, loop.subList(0, 4)))

        // Closed square: the encircled word is picked though no segment crossed it.
        val selection = WordHitTest.stroke(page, loop)
        assertEquals(listOf("fünf"), texts(selection))
        assertTrue(selection is Selection.Single)
    }

    @Test
    fun closedStrokeSelectsEveryEncircledWordInReadingOrder() {
        // Loop around "eins" and "vier" (left column, lines 1–2), away from the other words.
        val loop = listOf(
            Point(-20, -20), Point(55, -20), Point(55, 70), Point(-20, 70), Point(-20, -20),
        )
        val selection = WordHitTest.stroke(page, loop)
        assertEquals(listOf("eins", "vier"), texts(selection))
        assertSame(page[0], selection!!.head)
    }

    @Test
    fun strokeCombinesCrossedAndEncircledWords() {
        // A hook: the first segment crosses "eins", then the path loops down and around so
        // "vier" ends up inside the (closed) polygon while "fünf" is grazed on its edge.
        val hook = listOf(
            Point(30, 10),
            Point(-15, 10),
            Point(-15, 70),
            Point(60, 70),
            Point(60, 40),
            Point(50, 30), // 28px from the start — closes the shape
        )
        assertEquals(listOf("eins", "vier", "fünf"), texts(WordHitTest.stroke(page, hook)))
    }

    // -- extendByLine --------------------------------------------------------------------

    @Test
    fun extendByLineAddsWholeLinesDownToThePoint() {
        // Long-press "fünf" (line 2), drag to "neun" (line 3): whole lines 2–3, reading order.
        val selection = WordHitTest.extendByLine(
            Selection.Single(page[4]),
            Point(130, 90),
            page,
        )
        assertEquals(listOf("vier", "fünf", "sechs", "sieben", "acht", "neun"), texts(selection))
        assertSame(page[4], selection.head)
    }

    @Test
    fun extendByLineAddsWholeLinesUpToThePoint() {
        // Long-press "sieben" (line 3), drag up to line 1: every word of lines 1–3.
        val selection = WordHitTest.extendByLine(
            Selection.Single(page[6]),
            Point(200, 10),
            page,
        )
        assertEquals(listOf("eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun"), texts(selection))
        assertSame(page[6], selection.head)
    }

    @Test
    fun extendByLineUsesNearestLineForAPointBetweenLines() {
        // Lines sit 20 apart (line 1 ends y=20, line 2 starts y=40); y=31 is closer to line 2.
        val selection = WordHitTest.extendByLine(
            Selection.Single(page[2]), // drei, line 1
            Point(0, 31),
            page,
        )
        assertEquals(listOf("eins", "zwei", "drei", "vier", "fünf", "sechs"), texts(selection))
        assertSame(page[2], selection.head)
    }

    @Test
    fun extendByLineWithinOneLineKeepsSingleWordResult() {
        // "neun" is line 3's only word in this layout variant — extending onto its own
        // line yields exactly that word, so the result stays Single.
        val single = listOf(
            word("eins", 0, 0, 50, 20),
            word("zwei", 60, 0, 110, 20),
            word("neun", 120, 80, 170, 100),
        )
        val selection = WordHitTest.extendByLine(
            Selection.Single(single[2]),
            Point(130, 90),
            single,
        )
        assertEquals(listOf("neun"), texts(selection))
        assertTrue(selection is Selection.Single)
    }

    @Test
    fun extendByLineExtendsAPhraseAndKeepsItsHead() {
        val phrase = WordHitTest.stroke(page, listOf(Point(80, 10), Point(20, 10))) // eins + zwei
        val extended = WordHitTest.extendByLine(phrase!!, Point(130, 50), page)
        assertEquals(listOf("eins", "zwei", "drei", "vier", "fünf", "sechs"), texts(extended))
        assertSame(page[0], extended.head)
    }

    @Test
    fun extendByLineWithoutGeometryReturnsSelectionUnchanged() {
        val boxless = Selection.Single(unpositioned("Geist"))
        assertSame(boxless, WordHitTest.extendByLine(boxless, Point(0, 0), page))
        // Nothing on screen at all — nothing to extend with.
        val empty = WordHitTest.extendByLine(Selection.Single(page[4]), Point(0, 0), emptyList())
        assertEquals(listOf("fünf"), texts(empty))
    }
}
