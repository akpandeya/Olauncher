package app.olauncher

import android.os.SystemClock
import androidx.navigation.Navigation
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingRootException
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** Emulator smoke tests: the launcher starts and its main screens open. */
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    private fun navigate(scenario: ActivityScenario<MainActivity>, actionId: Int) {
        scenario.onActivity {
            Navigation.findNavController(it, R.id.nav_host_fragment).navigate(actionId)
        }
    }

    private fun assertDestination(scenario: ActivityScenario<MainActivity>, expected: Int) {
        // Poll the nav controller instead of Espresso: a settled settings screen may not
        // hold the RESUMED stage that onView() needs, so only the destination is read.
        val deadline = SystemClock.uptimeMillis() + 10_000
        var actual: Int? = null
        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity {
                actual = Navigation.findNavController(it, R.id.nav_host_fragment).currentDestination?.id
            }
            if (actual == expected) break
            SystemClock.sleep(100)
        }
        assertEquals(expected, actual)
    }

    /** Clicks Skip on the first-run welcome if it is showing; does nothing when it is not. */
    private fun dismissWelcomeIfShown() {
        val deadline = SystemClock.uptimeMillis() + 2_000
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                onView(withText(R.string.skip)).perform(click())
                return
            } catch (_: NoMatchingViewException) {
            } catch (_: NoMatchingRootException) {
            }
            SystemClock.sleep(100)
        }
    }

    @Test
    fun homeLoads() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            dismissWelcomeIfShown()
            onView(withId(R.id.mainLayout)).check(matches(isDisplayed()))
            assertDestination(scenario, R.id.mainFragment)
        }
    }

    @Test
    fun drawerOpens() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            dismissWelcomeIfShown()
            navigate(scenario, R.id.action_mainFragment_to_appListFragment)
            onView(withId(R.id.drawerLayout)).check(matches(isDisplayed()))
            assertDestination(scenario, R.id.appListFragment)
        }
    }

    @Test
    fun settingsOpens() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            dismissWelcomeIfShown()
            navigate(scenario, R.id.action_mainFragment_to_settingsFragment)
            assertDestination(scenario, R.id.settingsFragment)
        }
    }
}
