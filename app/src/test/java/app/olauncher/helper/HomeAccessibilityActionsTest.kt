package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAccessibilityActionsTest {
    @Test
    fun alwaysHasThreeCoreActions() {
        assertEquals(
            listOf(
                HomeAccessibilityAction.OPEN_APPS,
                HomeAccessibilityAction.OPEN_TODAY,
                HomeAccessibilityAction.SETTINGS,
            ),
            HomeAccessibilityActions.enabled(false, false, false),
        )
    }

    @Test
    fun optionalActionsFollowPrefs() {
        assertEquals(
            listOf(
                HomeAccessibilityAction.OPEN_APPS,
                HomeAccessibilityAction.OPEN_TODAY,
                HomeAccessibilityAction.SETTINGS,
                HomeAccessibilityAction.LOCK_SCREEN,
                HomeAccessibilityAction.SWIPE_RIGHT_APP,
            ),
            HomeAccessibilityActions.enabled(true, false, true),
        )
        val all = HomeAccessibilityActions.enabled(true, true, true)
        assertTrue(HomeAccessibilityAction.SWIPE_LEFT_APP in all)
        assertFalse(HomeAccessibilityAction.SWIPE_LEFT_APP in HomeAccessibilityActions.enabled(true, false, true))
    }

    @Test
    fun countdownAnnouncedOnlyAtStartAndZero() {
        val announced = (5 downTo 0).filter { HomeAccessibilityActions.shouldAnnounceCountdown(it, 5) }
        assertEquals(listOf(5, 0), announced)
    }
}
