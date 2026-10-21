package app.olauncher.helper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppListReloadPolicyTest {
    @Test
    fun resumeAloneDoesNotReloadLoadedList() {
        assertFalse(AppListReloadPolicy.shouldReload(AppListReloadTrigger.RESUME, isListLoaded = true))
    }

    @Test
    fun resumeLoadsWhenListNotLoadedYet() {
        assertTrue(AppListReloadPolicy.shouldReload(AppListReloadTrigger.RESUME, isListLoaded = false))
    }

    @Test
    fun everyChangeTriggerReloadsLoadedList() {
        AppListReloadTrigger.values().filter { it != AppListReloadTrigger.RESUME }.forEach {
            assertTrue(it.name, AppListReloadPolicy.shouldReload(it, isListLoaded = true))
        }
    }

    @Test
    fun hideAndRenameReloadLoadedList() {
        assertTrue(AppListReloadPolicy.shouldReload(AppListReloadTrigger.APP_HIDDEN_OR_UNHIDDEN, true))
        assertTrue(AppListReloadPolicy.shouldReload(AppListReloadTrigger.APP_RENAMED, true))
    }
}
