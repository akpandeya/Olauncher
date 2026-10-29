package app.olauncher.data.store

import app.olauncher.data.store.CaptureMarks.Mark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureMarksTest {

    @Test
    fun markOfGivesThreeDistinctOutcomes() {
        assertEquals(Mark.PLAIN, CaptureMarks.markOf(isSaved = false, isPending = false))
        assertEquals(Mark.PENDING, CaptureMarks.markOf(isSaved = false, isPending = true))
        assertEquals(Mark.SAVED, CaptureMarks.markOf(isSaved = true, isPending = false))

        val outcomes = setOf(
            CaptureMarks.markOf(isSaved = false, isPending = false),
            CaptureMarks.markOf(isSaved = false, isPending = true),
            CaptureMarks.markOf(isSaved = true, isPending = false),
        )
        assertEquals(3, outcomes.size)
    }

    @Test
    fun savedWinsOverPending() {
        assertEquals(Mark.SAVED, CaptureMarks.markOf(isSaved = true, isPending = true))
    }

    @Test
    fun pendingFillReplacesAlphaAndKeepsRgb() {
        assertEquals(0x40112233, CaptureMarks.pendingFill(0xFF112233.toInt()))
    }

    @Test
    fun pendingFillIsTranslucentButVisible() {
        // Distinctive bases: a swapped accent/primary argument must change the result.
        val accentFill = CaptureMarks.pendingFill(0xFF2E7D32.toInt())
        val primaryFill = CaptureMarks.pendingFill(0xFFB39DDB.toInt())

        for (fill in listOf(accentFill, primaryFill)) {
            val alpha = fill ushr 24 and 0xFF
            assertTrue("alpha must not be fully opaque", alpha < 0xFF)
            assertTrue("alpha must not be fully transparent", alpha > 0)
        }
        assertNotEquals(accentFill, primaryFill)
    }

    @Test
    fun distinctBaseColorsGiveDistinctFills() {
        val first = CaptureMarks.pendingFill(0xFF123456.toInt())
        val second = CaptureMarks.pendingFill(0xFF654321.toInt())

        assertEquals(0x40123456, first)
        assertEquals(0x40654321, second)
        assertNotEquals(first, second)
    }
}
