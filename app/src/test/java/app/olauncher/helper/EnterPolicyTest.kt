package app.olauncher.helper

import app.olauncher.data.AppModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnterPolicyTest {

    private fun createApp(label: String): AppModel.App = AppModel.App(
        appLabel = label,
        key = null,
        appPackage = "pkg.$label",
        activityClassName = null,
        isNew = false,
        user = AppModel.defaultUser,
        firstInstallTime = 0L,
        isArchived = false
    )

    private fun createResult(text: String): AppModel.Result =
        AppModel.Result(text = text, action = AppModel.Result.Action.Copy(text))

    @Test
    fun `enter with score at least 60 opens the top app`() {
        val gmail = createApp("Gmail")
        val actionPrefix = EnterPolicy.resolve(
            query = "gm",
            topItem = gmail,
            topScore = 90
        )
        assertTrue(actionPrefix is EnterPolicy.Action.LaunchApp)
        assertEquals(gmail, (actionPrefix as EnterPolicy.Action.LaunchApp).app)

        val spotify = createApp("Spotify")
        val actionExact = EnterPolicy.resolve(
            query = "spotify",
            topItem = spotify,
            topScore = 100
        )
        assertTrue(actionExact is EnterPolicy.Action.LaunchApp)

        val maps = createApp("Google Maps")
        val actionWordStart = EnterPolicy.resolve(
            query = "maps",
            topItem = maps,
            topScore = 80
        )
        assertTrue(actionWordStart is EnterPolicy.Action.LaunchApp)

        val youtube = createApp("YouTube")
        val actionInitials = EnterPolicy.resolve(
            query = "yt",
            topItem = youtube,
            topScore = 70
        )
        assertTrue(actionInitials is EnterPolicy.Action.LaunchApp)

        val telegram = createApp("Telegram")
        val actionSubstring = EnterPolicy.resolve(
            query = "gram",
            topItem = telegram,
            topScore = 60
        )
        assertTrue(actionSubstring is EnterPolicy.Action.LaunchApp)
    }

    @Test
    fun `enter with score below 60 or no match runs web search`() {
        val spotify = createApp("Spotify")
        // Fuzzy match: score 40
        val actionFuzzy = EnterPolicy.resolve(
            query = "spotfy",
            topItem = spotify,
            topScore = 40
        )
        assertEquals(EnterPolicy.Action.WebSearch, actionFuzzy)

        // "how tall is" with no matching apps
        val actionWeb = EnterPolicy.resolve(
            query = "how tall is",
            topItem = null,
            topScore = null
        )
        assertEquals(EnterPolicy.Action.WebSearch, actionWeb)
    }

    @Test
    fun `result row on top executes that row action`() {
        val calcRow = createResult("= 42")
        val actionCalc = EnterPolicy.resolve(
            query = "40+2",
            topItem = calcRow,
            topScore = null
        )
        assertTrue(actionCalc is EnterPolicy.Action.ExecuteResult)
        assertEquals(calcRow, (actionCalc as EnterPolicy.Action.ExecuteResult).result)

        val dialRow = AppModel.Result(text = "Call 911", action = AppModel.Result.Action.Dial("911"))
        val actionDial = EnterPolicy.resolve(
            query = "911",
            topItem = dialRow,
            topScore = null
        )
        assertTrue(actionDial is EnterPolicy.Action.ExecuteResult)
        assertEquals(dialRow, (actionDial as EnterPolicy.Action.ExecuteResult).result)
    }

    @Test
    fun `bang search runs web search even if match exists`() {
        val wikipedia = createApp("Wikipedia")
        val action = EnterPolicy.resolve(
            query = "!w x",
            topItem = wikipedia,
            topScore = 90
        )
        assertEquals(EnterPolicy.Action.WebSearch, action)
    }

    @Test
    fun `empty query or null topItem or header runs web search`() {
        val app = createApp("App")
        assertEquals(EnterPolicy.Action.WebSearch, EnterPolicy.resolve("", app, 100))
        assertEquals(EnterPolicy.Action.WebSearch, EnterPolicy.resolve(null, app, 100))
        assertEquals(EnterPolicy.Action.WebSearch, EnterPolicy.resolve("query", null, null))

        val header = AppModel.PrivateSpaceHeader(isLocked = true, user = AppModel.defaultUser)
        assertEquals(EnterPolicy.Action.WebSearch, EnterPolicy.resolve("query", header, 90))
    }

    @Test
    fun `contact or event row on top does not steal enter from a matching app`() {
        val gmail = createApp("Gmail")
        val contact = AppModel.Result("👤 Gerda Müller", AppModel.Result.Action.Contact("content://lookup/gm"))
        val event = AppModel.Result("📅 Gmeeting", AppModel.Result.Action.Event(42L))

        val onContact = EnterPolicy.resolve("gm", contact, 90, gmail)
        assertEquals(gmail, (onContact as EnterPolicy.Action.LaunchApp).app)

        val onEvent = EnterPolicy.resolve("gm", event, 90, gmail)
        assertEquals(gmail, (onEvent as EnterPolicy.Action.LaunchApp).app)
    }

    @Test
    fun `contact row on top takes enter when no app scores at least 60`() {
        val contact = AppModel.Result("👤 Anna", AppModel.Result.Action.Contact("content://lookup/anna"))
        val weakApp = createApp("Banana")

        val noApp = EnterPolicy.resolve("anna", contact, null, null)
        assertEquals(contact, (noApp as EnterPolicy.Action.ExecuteResult).result)

        val weak = EnterPolicy.resolve("anna", contact, 40, weakApp)
        assertEquals(contact, (weak as EnterPolicy.Action.ExecuteResult).result)
    }

    @Test
    fun `answer rows keep enter even with a matching app`() {
        val app = createApp("Euro Tracker")
        val rate = AppModel.Result("= 21.68 USD · ECB 18 Oct", AppModel.Result.Action.Copy("21.68"))
        val web = AppModel.Result("💱 20 EUR → USD on Google", AppModel.Result.Action.Web("20 eur in usd"))

        assertEquals(rate, (EnterPolicy.resolve("20 eur in usd", rate, 90, app) as EnterPolicy.Action.ExecuteResult).result)
        assertEquals(web, (EnterPolicy.resolve("20 eur in usd", web, 90, app) as EnterPolicy.Action.ExecuteResult).result)
    }
}
