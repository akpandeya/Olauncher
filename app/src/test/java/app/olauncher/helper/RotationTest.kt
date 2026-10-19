package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class RotationTest {

    @Test
    fun `step with count zero or negative returns null and keeps lastChangedMillis`() {
        val stepZero = Rotation.step(
            index = 0,
            count = 0,
            nowMillis = 100_000L,
            lastChangedMillis = 50_000L,
            interval = RotationInterval.DAILY,
        )
        assertNull(stepZero.nextIndex)
        assertEquals(50_000L, stepZero.lastChangedMillis)

        val stepNegative = Rotation.step(
            index = 2,
            count = -1,
            nowMillis = 100_000L,
            lastChangedMillis = 50_000L,
            interval = RotationInterval.DAILY,
        )
        assertNull(stepNegative.nextIndex)
        assertEquals(50_000L, stepNegative.lastChangedMillis)
    }

    @Test
    fun `step with NEVER interval never rotates and keeps lastChangedMillis`() {
        val step = Rotation.step(
            index = 1,
            count = 5,
            nowMillis = 10_000_000_000L,
            lastChangedMillis = 1_000L,
            interval = RotationInterval.NEVER,
        )
        assertNull(step.nextIndex)
        assertEquals(1_000L, step.lastChangedMillis)
    }

    @Test
    fun `slack boundary - just below returns null and at boundary advances`() {
        val intervalMillis = TimeUnit.HOURS.toMillis(24) // 86_400_000 ms
        val slack = intervalMillis / 10 // 8_640_000 ms (2.4h)
        val threshold = intervalMillis - slack // 77_760_000 ms (21.6h)
        val lastChanged = 1_000_000_000L

        // Just below boundary: 24h - 2.4h - 1 -> null
        val stepBelow = Rotation.step(
            index = 2,
            count = 5,
            nowMillis = lastChanged + threshold - 1L,
            lastChangedMillis = lastChanged,
            interval = RotationInterval.DAILY,
        )
        assertNull(stepBelow.nextIndex)
        assertEquals(lastChanged, stepBelow.lastChangedMillis)

        // At boundary: 24h - 2.4h -> advance
        val stepAt = Rotation.step(
            index = 2,
            count = 5,
            nowMillis = lastChanged + threshold,
            lastChangedMillis = lastChanged,
            interval = RotationInterval.DAILY,
        )
        assertEquals(3, stepAt.nextIndex)
        assertEquals(lastChanged + threshold, stepAt.lastChangedMillis)
    }

    @Test
    fun `step advances and wraps around`() {
        val lastChanged = 100_000L
        val now = lastChanged + TimeUnit.HOURS.toMillis(24)

        // Step advance in middle
        val stepMiddle = Rotation.step(
            index = 1,
            count = 5,
            nowMillis = now,
            lastChangedMillis = lastChanged,
            interval = RotationInterval.DAILY,
        )
        assertEquals(2, stepMiddle.nextIndex)
        assertEquals(now, stepMiddle.lastChangedMillis)

        // Wrap-around to start
        val stepWrap = Rotation.step(
            index = 4,
            count = 5,
            nowMillis = now,
            lastChangedMillis = lastChanged,
            interval = RotationInterval.DAILY,
        )
        assertEquals(0, stepWrap.nextIndex)
        assertEquals(now, stepWrap.lastChangedMillis)

        // Count of 1 wraps to 0
        val stepSingle = Rotation.step(
            index = 0,
            count = 1,
            nowMillis = now,
            lastChangedMillis = lastChanged,
            interval = RotationInterval.DAILY,
        )
        assertEquals(0, stepSingle.nextIndex)
        assertEquals(now, stepSingle.lastChangedMillis)
    }

    @Test
    fun `clock going backwards rebases lastChangedMillis to nowMillis without rotating`() {
        val lastChanged = 2_000_000L
        val now = 1_000_000L // nowMillis < lastChangedMillis
        val step = Rotation.step(
            index = 1,
            count = 5,
            nowMillis = now,
            lastChangedMillis = lastChanged,
            interval = RotationInterval.DAILY,
        )
        assertNull(step.nextIndex)
        assertEquals(now, step.lastChangedMillis)
    }

    @Test
    fun `interval change with same timestamps - not due for DAILY but due for HOURS_1`() {
        val lastChanged = 10_000_000L
        val now = lastChanged + TimeUnit.HOURS.toMillis(2) // 2 hours elapsed

        // DAILY (slack threshold 21.6h): 2h elapsed -> not due
        val stepDaily = Rotation.step(
            index = 1,
            count = 5,
            nowMillis = now,
            lastChangedMillis = lastChanged,
            interval = RotationInterval.DAILY,
        )
        assertNull(stepDaily.nextIndex)
        assertEquals(lastChanged, stepDaily.lastChangedMillis)

        // HOURS_1 (slack threshold 54 min): 2h elapsed -> due
        val stepHours1 = Rotation.step(
            index = 1,
            count = 5,
            nowMillis = now,
            lastChangedMillis = lastChanged,
            interval = RotationInterval.HOURS_1,
        )
        assertEquals(2, stepHours1.nextIndex)
        assertEquals(now, stepHours1.lastChangedMillis)
    }

    @Test
    fun `ON_OPEN is always due`() {
        val now = 5_000_000L

        // Zero elapsed time (nowMillis == lastChangedMillis)
        val stepZeroElapsed = Rotation.step(
            index = 0,
            count = 3,
            nowMillis = now,
            lastChangedMillis = now,
            interval = RotationInterval.ON_OPEN,
        )
        assertEquals(1, stepZeroElapsed.nextIndex)
        assertEquals(now, stepZeroElapsed.lastChangedMillis)

        // Positive elapsed time
        val stepLater = Rotation.step(
            index = 1,
            count = 3,
            nowMillis = now + 500L,
            lastChangedMillis = now,
            interval = RotationInterval.ON_OPEN,
        )
        assertEquals(2, stepLater.nextIndex)
        assertEquals(now + 500L, stepLater.lastChangedMillis)
    }

    @Test
    fun `fromId returns corresponding interval or DAILY for unknown ids`() {
        assertEquals(RotationInterval.DAILY, RotationInterval.DEFAULT)
        assertEquals(RotationInterval.DAILY, RotationInterval.fromId(-1))
        assertEquals(RotationInterval.DAILY, RotationInterval.fromId(0))
        assertEquals(RotationInterval.DAILY, RotationInterval.fromId(999))

        assertEquals(RotationInterval.ON_OPEN, RotationInterval.fromId(1))
        assertEquals(RotationInterval.MINUTES_15, RotationInterval.fromId(2))
        assertEquals(RotationInterval.MINUTES_30, RotationInterval.fromId(3))
        assertEquals(RotationInterval.HOURS_1, RotationInterval.fromId(4))
        assertEquals(RotationInterval.HOURS_3, RotationInterval.fromId(5))
        assertEquals(RotationInterval.HOURS_6, RotationInterval.fromId(6))
        assertEquals(RotationInterval.HOURS_12, RotationInterval.fromId(7))
        assertEquals(RotationInterval.DAILY, RotationInterval.fromId(8))
        assertEquals(RotationInterval.WEEKLY, RotationInterval.fromId(9))
        assertEquals(RotationInterval.NEVER, RotationInterval.fromId(10))
    }

    @Test
    fun `RotationInterval ids are unique`() {
        val ids = RotationInterval.entries.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `resolveLastChanged covers all three migration branches`() {
        val now = 1_700_000_000_000L
        val today = 19700

        // Branch 1: storedMillis > 0 -> returns storedMillis
        val stored = 1_699_999_000_000L
        assertEquals(
            stored,
            Rotation.resolveLastChanged(
                storedMillis = stored,
                lastDay = today,
                today = today,
                nowMillis = now,
            ),
        )
        assertEquals(
            stored,
            Rotation.resolveLastChanged(
                storedMillis = stored,
                lastDay = today - 1,
                today = today,
                nowMillis = now,
            ),
        )

        // Branch 2: storedMillis <= 0 and lastDay == today -> returns nowMillis (already changed today, do not rotate)
        assertEquals(
            now,
            Rotation.resolveLastChanged(
                storedMillis = 0L,
                lastDay = today,
                today = today,
                nowMillis = now,
            ),
        )
        assertEquals(
            now,
            Rotation.resolveLastChanged(
                storedMillis = -1L,
                lastDay = today,
                today = today,
                nowMillis = now,
            ),
        )

        // Branch 3: storedMillis <= 0 and lastDay != today -> returns 0L (treat as due)
        assertEquals(
            0L,
            Rotation.resolveLastChanged(
                storedMillis = 0L,
                lastDay = today - 1,
                today = today,
                nowMillis = now,
            ),
        )
        assertEquals(
            0L,
            Rotation.resolveLastChanged(
                storedMillis = -5L,
                lastDay = today - 1,
                today = today,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun `previous wraps around from 0 to last index`() {
        assertEquals(4, Rotation.previous(index = 0, count = 5))
        assertEquals(2, Rotation.previous(index = 0, count = 3))
    }

    @Test
    fun `previous steps backward in middle`() {
        assertEquals(2, Rotation.previous(index = 3, count = 5))
        assertEquals(1, Rotation.previous(index = 2, count = 5))
        assertEquals(0, Rotation.previous(index = 1, count = 5))
    }

    @Test
    fun `previous with count 1 returns 0`() {
        assertEquals(0, Rotation.previous(index = 0, count = 1))
    }

    @Test
    fun `previous with zero or negative count returns null`() {
        assertNull(Rotation.previous(index = 0, count = 0))
        assertNull(Rotation.previous(index = 2, count = 0))
        assertNull(Rotation.previous(index = 0, count = -1))
        assertNull(Rotation.previous(index = 3, count = -5))
    }

    @Test
    fun `previous handles out-of-range index safely`() {
        assertEquals(4, Rotation.previous(index = 5, count = 5))
        assertEquals(1, Rotation.previous(index = 7, count = 5))
        assertEquals(3, Rotation.previous(index = -1, count = 5))
        assertEquals(2, Rotation.previous(index = -2, count = 5))
        assertEquals(4, Rotation.previous(index = -5, count = 5))
        assertEquals(3, Rotation.previous(index = -6, count = 5))
    }

    @Test
    fun `manualNext advances by one and wraps at end`() {
        assertEquals(1, Rotation.manualNext(index = 0, count = 5))
        assertEquals(2, Rotation.manualNext(index = 1, count = 5))
        assertEquals(0, Rotation.manualNext(index = 4, count = 5))
        assertEquals(0, Rotation.manualNext(index = 0, count = 1))
    }

    @Test
    fun `manualNext with zero or negative count returns null`() {
        assertNull(Rotation.manualNext(index = 0, count = 0))
        assertNull(Rotation.manualNext(index = 2, count = 0))
        assertNull(Rotation.manualNext(index = 0, count = -1))
        assertNull(Rotation.manualNext(index = 3, count = -5))
    }

    @Test
    fun `manualNext handles out-of-range index safely`() {
        assertEquals(1, Rotation.manualNext(index = 5, count = 5))
        assertEquals(0, Rotation.manualNext(index = -1, count = 5))
        assertEquals(4, Rotation.manualNext(index = -2, count = 5))
    }

    @Test
    fun `wallpaperHomeMenu isShown logic`() {
        // All four true/false combos
        assertTrue(WallpaperHomeMenu.isShown(enabled = true, count = 5))
        assertFalse(WallpaperHomeMenu.isShown(enabled = false, count = 5))
        assertFalse(WallpaperHomeMenu.isShown(enabled = true, count = 0))
        assertFalse(WallpaperHomeMenu.isShown(enabled = false, count = 0))

        // Negative count
        assertFalse(WallpaperHomeMenu.isShown(enabled = true, count = -1))
        assertFalse(WallpaperHomeMenu.isShown(enabled = false, count = -1))
    }
}
