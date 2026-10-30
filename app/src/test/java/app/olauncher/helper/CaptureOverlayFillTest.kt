package app.olauncher.helper

import app.olauncher.data.store.ScreenBox
import app.olauncher.data.store.ScreenWord
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The drag pass of the capture overlay must not repaint already-pending words: their
 * translucent fill is drawn by the pending loop, and a second fill stacks the alpha
 * (issue #697 — re-covered pending words read darker than their neighbours mid-drag).
 */
class CaptureOverlayFillTest {

    private fun word(text: String, id: Int) = ScreenWord(
        text = text,
        box = ScreenBox(id * 100, 0, id * 100 + 80, 30),
        nodeId = 1,
        charRange = 0 until text.length,
    )

    @Test
    fun `a pending word under the drag region is not drawn a second time`() {
        val pending = word("Haus", 0)
        val fresh = word("Katze", 1)
        assertEquals(listOf(fresh), dragFillWords(listOf(pending, fresh), setOf(pending)))
    }

    @Test
    fun `a drag word equal by value to a pending word is skipped`() {
        // The pending set holds an equal-valued word, not the same instance the drag
        // carries — data-class equality is what onDraw's membership checks rely on.
        val drag = word("Regierung", 2)
        assertEquals(emptyList<ScreenWord>(), dragFillWords(listOf(drag), setOf(word("Regierung", 2))))
    }

    @Test
    fun `no drag selection paints nothing in the drag pass`() {
        assertEquals(emptyList<ScreenWord>(), dragFillWords(null, setOf(word("Haus", 0))))
    }

    @Test
    fun `an empty pending set leaves the drag words untouched`() {
        val drag = listOf(word("Haus", 0), word("Katze", 1))
        assertEquals(drag, dragFillWords(drag, emptySet()))
    }
}
