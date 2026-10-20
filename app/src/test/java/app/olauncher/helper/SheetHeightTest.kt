package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class SheetHeightTest {
    @Test
    fun `2000px screen at half gives 1000px`() {
        assertEquals(1000, SheetHeight.maxListHeight(2000, 0.5))
    }

    @Test
    fun `never negative`() {
        assertEquals(0, SheetHeight.maxListHeight(-500, 0.5))
        assertEquals(0, SheetHeight.maxListHeight(2000, -0.5))
    }
}
