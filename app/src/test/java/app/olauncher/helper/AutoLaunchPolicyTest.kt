package app.olauncher.helper

import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoLaunchPolicyTest {

    private fun createApp(
        label: String,
        isArchived: Boolean = false
    ): AppModel.App = AppModel.App(
        appLabel = label,
        key = null,
        appPackage = "pkg.$label",
        activityClassName = null,
        isNew = false,
        user = AppModel.defaultUser,
        firstInstallTime = 0L,
        isArchived = isArchived
    )

    @Test
    fun `5 k with a single matching app results in no auto-launch`() {
        val app = createApp("5K Player")
        val canLaunch = AutoLaunchPolicy.canAutoLaunch(
            query = "5 k",
            hasResultRow = false,
            strongMatches = 1,
            firstItem = app
        )
        assertFalse("Queries starting with a digit must never auto-launch", canLaunch)
    }

    @Test
    fun `leading space results in no auto-launch`() {
        val app = createApp("Spotify")
        val canLaunch = AutoLaunchPolicy.canAutoLaunch(
            query = " spot",
            hasResultRow = false,
            strongMatches = 1,
            firstItem = app
        )
        assertFalse("Leading space must suppress auto-launch", canLaunch)
    }

    @Test
    fun `normal query spot auto-launches single match`() {
        val app = createApp("Spotify")
        val canLaunch = AutoLaunchPolicy.canAutoLaunch(
            query = "spot",
            hasResultRow = false,
            strongMatches = 1,
            firstItem = app
        )
        assertTrue("Normal single strong match must auto-launch", canLaunch)
    }

    @Test
    fun `bang search results in no auto-launch`() {
        val app = createApp("Wikipedia")
        val canLaunch = AutoLaunchPolicy.canAutoLaunch(
            query = "!w x",
            hasResultRow = false,
            strongMatches = 1,
            firstItem = app
        )
        assertFalse("Bang search must never auto-launch", canLaunch)
    }

    @Test
    fun `currency symbols result in no auto-launch`() {
        val app = createApp("Cash")
        val symbols = listOf("€", "$", "£", "¥", "₹")
        for (sym in symbols) {
            assertFalse(
                AutoLaunchPolicy.canAutoLaunch(
                    query = "${sym}5",
                    hasResultRow = false,
                    strongMatches = 1,
                    firstItem = app
                )
            )
            assertFalse(
                AutoLaunchPolicy.canAutoLaunch(
                    query = "  ${sym}100",
                    hasResultRow = false,
                    strongMatches = 1,
                    firstItem = app
                )
            )
        }
    }

    @Test
    fun `archived app or result row or strongMatches not 1 returns false`() {
        val normalApp = createApp("Calc")
        val archivedApp = createApp("Calc", isArchived = true)
        val resultRow = AppModel.Result("= 4", AppModel.Result.Action.Copy("4"))

        assertFalse(
            AutoLaunchPolicy.canAutoLaunch(
                query = "calc",
                hasResultRow = true,
                strongMatches = 1,
                firstItem = normalApp
            )
        )
        assertFalse(
            AutoLaunchPolicy.canAutoLaunch(
                query = "calc",
                hasResultRow = false,
                strongMatches = 1,
                firstItem = archivedApp
            )
        )
        assertFalse(
            AutoLaunchPolicy.canAutoLaunch(
                query = "calc",
                hasResultRow = false,
                strongMatches = 2,
                firstItem = normalApp
            )
        )
        assertFalse(
            AutoLaunchPolicy.canAutoLaunch(
                query = "calc",
                hasResultRow = false,
                strongMatches = 1,
                firstItem = resultRow
            )
        )
    }
}
