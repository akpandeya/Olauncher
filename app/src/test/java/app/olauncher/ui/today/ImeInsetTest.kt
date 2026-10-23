package app.olauncher.ui.today

import org.junit.Assert.assertEquals
import org.junit.Test

class ImeInsetTest {
    @Test
    fun `padding is the keyboard height while it shows`() {
        assertEquals(742, ImeInset.bottomPadding(true, 742))
    }

    @Test
    fun `no padding when the keyboard is hidden or the inset is negative`() {
        assertEquals(0, ImeInset.bottomPadding(false, 742))
        assertEquals(0, ImeInset.bottomPadding(true, -5))
    }
}
