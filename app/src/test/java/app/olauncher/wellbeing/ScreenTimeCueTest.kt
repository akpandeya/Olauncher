package app.olauncher.wellbeing

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenTimeCueTest {

    @Test
    fun `under target is full alpha`() {
        assertEquals(1.0f, ScreenTimeCue.alpha(59, 60), 0f)
        assertEquals(1.0f, ScreenTimeCue.alpha(0, 120), 0f)
    }

    @Test
    fun `at target is dimmed`() {
        assertEquals(0.7f, ScreenTimeCue.alpha(60, 60), 0f)
    }

    @Test
    fun `over target is dimmed`() {
        assertEquals(0.7f, ScreenTimeCue.alpha(200, 120), 0f)
    }

    @Test
    fun `target zero never dims`() {
        assertEquals(1.0f, ScreenTimeCue.alpha(500, 0), 0f)
        assertEquals(1.0f, ScreenTimeCue.alpha(0, 0), 0f)
    }
}
