package app.olauncher.helper

import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchivedAppsHelperTest {

    private fun createApp(
        label: String,
        isNew: Boolean = false,
        isArchived: Boolean = false,
        packageName: String = "pkg.$label"
    ): AppModel.App = AppModel.App(
        appLabel = label,
        key = null,
        appPackage = packageName,
        activityClassName = null,
        isNew = isNew,
        user = AppModel.defaultUser,
        firstInstallTime = 0L,
        isArchived = isArchived
    )

    private fun createPinnedShortcut(
        label: String,
        isArchived: Boolean = false
    ): AppModel.PinnedShortcut = AppModel.PinnedShortcut(
        appLabel = label,
        key = null,
        appPackage = "pkg.$label",
        shortcutId = "sc.$label",
        isNew = false,
        user = AppModel.defaultUser,
        firstInstallTime = 0L,
        isArchived = isArchived
    )

    @Test
    fun `formatAppTitle formats regular app without suffix`() {
        val app = createApp("Camera", isNew = false, isArchived = false)
        assertEquals("Camera", ArchivedAppsHelper.formatAppTitle(app))
    }

    @Test
    fun `formatAppTitle formats new app with star suffix`() {
        val app = createApp("Camera", isNew = true, isArchived = false)
        assertEquals("Camera ✦", ArchivedAppsHelper.formatAppTitle(app))
    }

    @Test
    fun `formatAppTitle formats archived app with cloud suffix`() {
        val app = createApp("Spotify", isNew = false, isArchived = true)
        assertEquals("Spotify ☁︎", ArchivedAppsHelper.formatAppTitle(app))
        assertEquals(ArchivedAppsHelper.ARCHIVED_APP_SUFFIX, " ☁︎")
    }

    @Test
    fun `formatAppTitle prioritizes cloud suffix over star suffix if both set`() {
        val app = createApp("Duolingo", isNew = true, isArchived = true)
        assertEquals("Duolingo ☁︎", ArchivedAppsHelper.formatAppTitle(app))
    }

    @Test
    fun `isPickerFlag identifies picker flags correctly`() {
        // Home app pickers
        for (flag in Constants.FLAG_SET_HOME_APP_1..Constants.FLAG_SET_HOME_APP_8) {
            assertTrue("Expected home app flag $flag to be picker", ArchivedAppsHelper.isPickerFlag(flag))
        }

        // Shortcut-column pickers
        for (flag in Constants.FLAG_SET_SHORTCUT_1..Constants.FLAG_SET_SHORTCUT_12) {
            assertTrue("Expected shortcut flag $flag to be picker", ArchivedAppsHelper.isPickerFlag(flag))
        }

        // Other setting pickers
        assertTrue(ArchivedAppsHelper.isPickerFlag(Constants.FLAG_SET_SWIPE_LEFT_APP))
        assertTrue(ArchivedAppsHelper.isPickerFlag(Constants.FLAG_SET_SWIPE_RIGHT_APP))
        assertTrue(ArchivedAppsHelper.isPickerFlag(Constants.FLAG_SET_CLOCK_APP))
        assertTrue(ArchivedAppsHelper.isPickerFlag(Constants.FLAG_SET_CALENDAR_APP))
        assertTrue(ArchivedAppsHelper.isPickerFlag(Constants.FLAG_SET_SCREEN_TIME_APP))
        assertTrue(ArchivedAppsHelper.isPickerFlag(Constants.FLAG_SET_WEATHER_APP))

        // Non-pickers
        assertFalse(ArchivedAppsHelper.isPickerFlag(Constants.FLAG_LAUNCH_APP))
        assertFalse(ArchivedAppsHelper.isPickerFlag(Constants.FLAG_HIDDEN_APPS))
    }

    @Test
    fun `filterForPicker excludes archived apps for home app pickers`() {
        val app1 = createApp("Browser", isArchived = false)
        val app2 = createApp("Calculator", isArchived = true)
        val app3 = createApp("Camera", isArchived = false)
        val list = listOf(app1, app2, app3)

        val filtered = ArchivedAppsHelper.filterForPicker(list, Constants.FLAG_SET_HOME_APP_1)
        assertEquals(listOf(app1, app3), filtered)
    }

    @Test
    fun `filterForPicker excludes archived apps for shortcut-column pickers`() {
        val app1 = createApp("Gallery", isArchived = false)
        val app2 = createApp("Games", isArchived = true)
        val list = listOf(app1, app2)

        val filtered = ArchivedAppsHelper.filterForPicker(list, Constants.FLAG_SET_SHORTCUT_1)
        assertEquals(listOf(app1), filtered)
    }

    @Test
    fun `filterForPicker retains archived apps for launcher drawer and hidden apps`() {
        val app1 = createApp("Browser", isArchived = false)
        val app2 = createApp("Calculator", isArchived = true)
        val list = listOf(app1, app2)

        assertEquals(list, ArchivedAppsHelper.filterForPicker(list, Constants.FLAG_LAUNCH_APP))
        assertEquals(list, ArchivedAppsHelper.filterForPicker(list, Constants.FLAG_HIDDEN_APPS))
    }

    @Test
    fun `canAutoLaunch returns true for normal app with single strong match`() {
        val app = createApp("Calculator", isArchived = false)
        val result = ArchivedAppsHelper.canAutoLaunch(
            hasResultRow = false,
            strongMatches = 1,
            autoLaunch = true,
            isBangSearch = false,
            flag = Constants.FLAG_LAUNCH_APP,
            firstItem = app
        )
        assertTrue(result)
    }

    @Test
    fun `canAutoLaunch returns false when first app is archived`() {
        val archivedApp = createApp("Calculator", isArchived = true)
        val result = ArchivedAppsHelper.canAutoLaunch(
            hasResultRow = false,
            strongMatches = 1,
            autoLaunch = true,
            isBangSearch = false,
            flag = Constants.FLAG_LAUNCH_APP,
            firstItem = archivedApp
        )
        assertFalse("Archived apps must never auto-launch", result)
    }

    @Test
    fun `canAutoLaunch returns false if hasResultRow is true`() {
        val app = createApp("Calc", isArchived = false)
        val result = ArchivedAppsHelper.canAutoLaunch(
            hasResultRow = true,
            strongMatches = 1,
            autoLaunch = true,
            isBangSearch = false,
            flag = Constants.FLAG_LAUNCH_APP,
            firstItem = app
        )
        assertFalse(result)
    }

    @Test
    fun `canAutoLaunch returns false if strongMatches is not 1`() {
        val app = createApp("Calc", isArchived = false)
        assertFalse(
            ArchivedAppsHelper.canAutoLaunch(
                hasResultRow = false,
                strongMatches = 0,
                autoLaunch = true,
                isBangSearch = false,
                flag = Constants.FLAG_LAUNCH_APP,
                firstItem = app
            )
        )
        assertFalse(
            ArchivedAppsHelper.canAutoLaunch(
                hasResultRow = false,
                strongMatches = 2,
                autoLaunch = true,
                isBangSearch = false,
                flag = Constants.FLAG_LAUNCH_APP,
                firstItem = app
            )
        )
    }

    @Test
    fun `canAutoLaunch returns false for bang search or disabled autoLaunch`() {
        val app = createApp("Calc", isArchived = false)
        assertFalse(
            ArchivedAppsHelper.canAutoLaunch(
                hasResultRow = false,
                strongMatches = 1,
                autoLaunch = false,
                isBangSearch = false,
                flag = Constants.FLAG_LAUNCH_APP,
                firstItem = app
            )
        )
        assertFalse(
            ArchivedAppsHelper.canAutoLaunch(
                hasResultRow = false,
                strongMatches = 1,
                autoLaunch = true,
                isBangSearch = true,
                flag = Constants.FLAG_LAUNCH_APP,
                firstItem = app
            )
        )
    }

    @Test
    fun `canAutoLaunch returns false when flag is not launch app`() {
        val app = createApp("Calc", isArchived = false)
        assertFalse(
            ArchivedAppsHelper.canAutoLaunch(
                hasResultRow = false,
                strongMatches = 1,
                autoLaunch = true,
                isBangSearch = false,
                flag = Constants.FLAG_SET_HOME_APP_1,
                firstItem = app
            )
        )
    }

    @Test
    fun `canAutoLaunch returns false when firstItem is null, PrivateSpaceHeader, or Result`() {
        assertFalse(
            ArchivedAppsHelper.canAutoLaunch(
                hasResultRow = false,
                strongMatches = 1,
                autoLaunch = true,
                isBangSearch = false,
                flag = Constants.FLAG_LAUNCH_APP,
                firstItem = null
            )
        )
        assertFalse(
            ArchivedAppsHelper.canAutoLaunch(
                hasResultRow = false,
                strongMatches = 1,
                autoLaunch = true,
                isBangSearch = false,
                flag = Constants.FLAG_LAUNCH_APP,
                firstItem = AppModel.PrivateSpaceHeader(isLocked = true, user = AppModel.defaultUser)
            )
        )
        assertFalse(
            ArchivedAppsHelper.canAutoLaunch(
                hasResultRow = false,
                strongMatches = 1,
                autoLaunch = true,
                isBangSearch = false,
                flag = Constants.FLAG_LAUNCH_APP,
                firstItem = AppModel.Result(text = "= 42", action = AppModel.Result.Action.Copy("42"))
            )
        )
    }
}
