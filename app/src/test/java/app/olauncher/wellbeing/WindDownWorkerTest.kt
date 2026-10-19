package app.olauncher.wellbeing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WindDownWorkerTest {
    private val start = 21 * 60 + 30
    private val end = 6 * 60
    private fun ms(h: Int, m: Int, s: Int = 0) = ((h * 60L + m) * 60 + s) * 1000

    @Test fun greyOnlyWhenEnabledOnAndInsideWindow() {
        assertTrue(WindDownGrey.shouldBeGrey(true, true, 22 * 60, start, end))
        assertFalse(WindDownGrey.shouldBeGrey(false, true, 22 * 60, start, end))
        assertFalse(WindDownGrey.shouldBeGrey(true, false, 22 * 60, start, end))
        assertFalse(WindDownGrey.shouldBeGrey(true, true, 12 * 60, start, end))
    }

    @Test fun needsChangeOnlyWhenStatesDiffer() {
        assertTrue(WindDownGrey.needsChange(true, false))
        assertTrue(WindDownGrey.needsChange(false, true))
        assertFalse(WindDownGrey.needsChange(true, true))
        assertFalse(WindDownGrey.needsChange(false, false))
    }

    @Test fun beforeStartSchedulesGreyAtStart() {
        val next = WindDownGrey.nextBoundary(true, true, ms(20, 0), start, end)!!
        assertTrue(next.toGrey)
        assertEquals(90 * 60_000L, next.delayMillis)
    }

    @Test fun insideWindowSchedulesRestoreAtEnd() {
        val next = WindDownGrey.nextBoundary(true, true, ms(23, 0), start, end)!!
        assertFalse(next.toGrey)
        assertEquals(7 * 60 * 60_000L, next.delayMillis)
    }

    @Test fun secondsAreSubtracted() {
        val next = WindDownGrey.nextBoundary(true, true, ms(21, 29, 30), start, end)!!
        assertTrue(next.toGrey)
        assertEquals(30_000L, next.delayMillis)
    }

    @Test fun afterEndSchedulesNextEveningStart() {
        val next = WindDownGrey.nextBoundary(true, true, ms(7, 0), start, end)!!
        assertTrue(next.toGrey)
        assertEquals((14 * 60 + 30) * 60_000L, next.delayMillis)
    }

    @Test fun exactlyAtStartTargetsEnd() {
        val next = WindDownGrey.nextBoundary(true, true, ms(21, 30), start, end)!!
        assertFalse(next.toGrey)
        assertEquals((8 * 60 + 30) * 60_000L, next.delayMillis)
    }

    @Test fun noScheduleWhenOffOrEmptyWindow() {
        assertNull(WindDownGrey.nextBoundary(false, true, ms(20, 0), start, end))
        assertNull(WindDownGrey.nextBoundary(true, false, ms(20, 0), start, end))
        assertNull(WindDownGrey.nextBoundary(true, true, ms(20, 0), 600, 600))
    }

    @Test fun sameDayWindowWorks() {
        val grey = WindDownGrey.nextBoundary(true, true, ms(8, 0), 9 * 60, 17 * 60)
        assertNotNull(grey)
        assertTrue(grey!!.toGrey)
        assertEquals(60 * 60_000L, grey.delayMillis)
    }
}
