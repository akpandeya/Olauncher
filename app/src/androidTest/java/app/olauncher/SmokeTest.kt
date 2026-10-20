package app.olauncher

import android.os.SystemClock
import androidx.navigation.Navigation
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
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

    @Test
    fun homeLoads() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.mainLayout)).check(matches(isDisplayed()))
            assertDestination(scenario, R.id.mainFragment)
        }
    }

    @Test
    fun drawerOpens() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            navigate(scenario, R.id.action_mainFragment_to_appListFragment)
            onView(withId(R.id.drawerLayout)).check(matches(isDisplayed()))
            assertDestination(scenario, R.id.appListFragment)
        }
    }

    @Test
    fun settingsOpens() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            navigate(scenario, R.id.action_mainFragment_to_settingsFragment)
            assertDestination(scenario, R.id.settingsFragment)
        }
    }
}
