package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RotationTest {

    @Test
    fun `same day returns null`() {
        assertNull(Rotation.next(index = 0, count = 5, today = 100, lastDay = 100))
        assertNull(Rotation.next(index = 3, count = 10, today = 20261017, lastDay = 20261017))
    }

    @Test
    fun `new day advances index by one`() {
        assertEquals(1, Rotation.next(index = 0, count = 5, today = 101, lastDay = 100))
        assertEquals(2, Rotation.next(index = 1, count = 5, today = 101, lastDay = 100))
        assertEquals(3, Rotation.next(index = 2, count = 5, today = 105, lastDay = 100))
    }

    @Test
    fun `new day wraps around to start`() {
        assertEquals(0, Rotation.next(index = 4, count = 5, today = 101, lastDay = 100))
        assertEquals(0, Rotation.next(index = 0, count = 1, today = 101, lastDay = 100))
    }

    @Test
    fun `count zero returns null`() {
        assertNull(Rotation.next(index = 0, count = 0, today = 101, lastDay = 100))
        assertNull(Rotation.next(index = 2, count = 0, today = 101, lastDay = 100))
        assertNull(Rotation.next(index = 0, count = -1, today = 101, lastDay = 100))
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
