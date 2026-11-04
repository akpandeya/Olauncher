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

    @Test
    fun `pinned composer rides the keyboard while it shows`() {
        assertEquals(742, ImeInset.pinnedBottomPadding(imeVisible = true, imeBottomPx = 742, basePx = 132))
    }

    @Test
    fun `pinned composer returns to its base padding when the keyboard hides`() {
        assertEquals(132, ImeInset.pinnedBottomPadding(imeVisible = false, imeBottomPx = 742, basePx = 132))
    }

    @Test
    fun `pinned composer never takes a negative inset and never stacks on its base`() {
        assertEquals(0, ImeInset.pinnedBottomPadding(imeVisible = true, imeBottomPx = -5, basePx = 132))
        // A keyboard shorter than the base still wins: the base clears the nav bar,
        // which the keyboard already covers.
        assertEquals(40, ImeInset.pinnedBottomPadding(imeVisible = true, imeBottomPx = 40, basePx = 132))
    }

    @Test
    fun windowLiftSkipsTheNavBarTheKeyboardCovers() {
        assertEquals(700, ImeInset.windowLift(true, 800, 100))
    }

    @Test
    fun windowLiftIsZeroWithoutKeyboardOrWhenKeyboardIsShorterThanNavBar() {
        assertEquals(0, ImeInset.windowLift(false, 800, 100))
        assertEquals(0, ImeInset.windowLift(true, 50, 100))
    }

    @Test
    fun `ride lifts the window by the keyboard and caps the scroll to the space above it`() {
        // The numbers measured on the Pixel 9 Pro AVD: keyboard 1008 px, nav bar 120,
        // status bar 128, screen 2856, density 3.
        val ride = ImeInset.ride(imeVisible = true, imeBottomPx = 1008, navBottomPx = 120, statusTopPx = 128, screenHeightPx = 2856, density = 3f)
        assertEquals(888, ride.lift)
        // 2856 - 1008 - 128 - 540 = 1180.
        assertEquals(1180, ride.scrollCap)
    }

    @Test
    fun `ride keeps a minimum scroll cap and resets both when the keyboard hides`() {
        val tight = ImeInset.ride(imeVisible = true, imeBottomPx = 2700, navBottomPx = 120, statusTopPx = 128, screenHeightPx = 2856, density = 3f)
        assertEquals(300, tight.scrollCap)
        val hidden = ImeInset.ride(imeVisible = false, imeBottomPx = 1008, navBottomPx = 120, statusTopPx = 128, screenHeightPx = 2856, density = 3f)
        assertEquals(0, hidden.lift)
        assertEquals(0, hidden.scrollCap)
    }
}
