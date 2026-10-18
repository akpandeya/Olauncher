package app.olauncher.helper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeReturnPolicyTest {

    @Test
    fun `pops to home when leaving a non-home screen`() {
        assertTrue(HomeReturnPolicy.shouldPopToHome(isOnHome = false, privateSpaceToggling = false, awaitingActivityResult = false))
    }

    @Test
    fun `does nothing when already on home`() {
        assertFalse(HomeReturnPolicy.shouldPopToHome(isOnHome = true, privateSpaceToggling = false, awaitingActivityResult = false))
    }

    @Test
    fun `keeps the screen while private space auth is showing`() {
        assertFalse(HomeReturnPolicy.shouldPopToHome(isOnHome = false, privateSpaceToggling = true, awaitingActivityResult = false))
    }

    @Test
    fun `keeps the screen while a picker is open for a result`() {
        assertFalse(HomeReturnPolicy.shouldPopToHome(isOnHome = false, privateSpaceToggling = false, awaitingActivityResult = true))
    }
}
