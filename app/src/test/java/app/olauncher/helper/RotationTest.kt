package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
