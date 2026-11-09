package app.olauncher.ui.today.cards

import app.olauncher.R
import app.olauncher.data.store.GermanWebSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GermanWebCardLogicTest {

    private val now = 1_760_000_000_000L
    private val base = "https://de.example.com"
    private val signedIn = GermanWebSessionState.SignedIn(username = "gfd_test_user", deviceId = "gfd_test_device")

    private fun signedInContent(
        serverDueCount: Int,
        ratedLocally: Int = 0,
        fetchedAtMillis: Long? = now - 60_000L,
    ): GermanWebCardContent? = GermanWebCardLogic.content(
        germanEnabled = true,
        state = signedIn,
        baseUrl = base,
        serverDueCount = serverDueCount,
        ratedLocally = ratedLocally,
        fetchedAtMillis = fetchedAtMillis,
        nowMillis = now,
    )

    // --- Availability -----------------------------------------------------------

    @Test
    fun availableOnlyWhenGermanIsOnAndSignedInOrLapsed() {
        assertTrue(GermanWebCardLogic.isAvailable(germanEnabled = true, state = signedIn))
        assertTrue(GermanWebCardLogic.isAvailable(germanEnabled = true, state = GermanWebSessionState.Lapsed))
        assertFalse(GermanWebCardLogic.isAvailable(germanEnabled = false, state = signedIn))
        assertFalse(GermanWebCardLogic.isAvailable(germanEnabled = true, state = GermanWebSessionState.SignedOut))
        assertFalse(GermanWebCardLogic.isAvailable(germanEnabled = false, state = GermanWebSessionState.SignedOut))
    }

    // --- Signed-in counts, distinguishing 0 / 1 / 12 ----------------------------

    @Test
    fun signedInWithNothingDueShowsZeroAndTheStartRoute() {
        val content = signedInContent(serverDueCount = 0)!!
        assertEquals(0, content.dueCount)
        assertEquals(R.string.german_web_card_due_line, content.lineRes)
        assertFalse(content.openSettings)
        assertEquals("$base${GermanWebCardLogic.ROUTE_START}", content.openUrl)
    }

    @Test
    fun signedInWithOneDueShowsOneAndThePracticeRoute() {
        val content = signedInContent(serverDueCount = 1)!!
        assertEquals(1, content.dueCount)
        assertEquals("$base${GermanWebCardLogic.ROUTE_DUE}", content.openUrl)
    }

    @Test
    fun signedInWithTwelveDueShowsTwelveAndThePracticeRoute() {
        val content = signedInContent(serverDueCount = 12)!!
        assertEquals(12, content.dueCount)
        assertEquals("$base${GermanWebCardLogic.ROUTE_DUE}", content.openUrl)
    }

    // --- Rated-locally subtraction ----------------------------------------------

    @Test
    fun locallyRatedWordsReduceTheServerCount() {
        assertEquals(4, GermanWebCardLogic.dueCount(serverDueCount = 7, ratedLocally = 3))
        assertEquals(
            4,
            signedInContent(serverDueCount = 7, ratedLocally = 3)!!.dueCount,
        )
    }

    @Test
    fun locallyRatedWordsNeverPushTheCountBelowZero() {
        assertEquals(0, GermanWebCardLogic.dueCount(serverDueCount = 3, ratedLocally = 5))
        val content = signedInContent(serverDueCount = 3, ratedLocally = 5)!!
        assertEquals(0, content.dueCount)
        // Nothing is due any more, so the tap goes to the start page.
        assertEquals("$base${GermanWebCardLogic.ROUTE_START}", content.openUrl)
    }

    @Test
    fun everyRatedWordRemovesExactlyOneDueWord() {
        assertEquals(0, GermanWebCardLogic.dueCount(serverDueCount = 4, ratedLocally = 4))
    }

    // --- Freshness ---------------------------------------------------------------

    @Test
    fun freshCacheShowsThePlainDueLine() {
        assertEquals(R.string.german_web_card_due_line, signedInContent(serverDueCount = 5, fetchedAtMillis = now - 23 * 60 * 60 * 1000L)!!.lineRes)
    }

    @Test
    fun cacheOlderThanADayShowsTheNotSyncedHint() {
        assertEquals(R.string.german_web_card_due_line_stale, signedInContent(serverDueCount = 5, fetchedAtMillis = now - 25 * 60 * 60 * 1000L)!!.lineRes)
    }

    @Test
    fun emptyCacheShowsTheNotSyncedHintAndZero() {
        val content = signedInContent(serverDueCount = 0, fetchedAtMillis = null)!!
        assertEquals(0, content.dueCount)
        assertEquals(R.string.german_web_card_due_line_stale, content.lineRes)
    }

    // --- Lapsed ------------------------------------------------------------------

    @Test
    fun lapsedShowsTheReconnectMessageAndNoUrl() {
        val content = GermanWebCardLogic.content(
            germanEnabled = true,
            state = GermanWebSessionState.Lapsed,
            baseUrl = base,
            serverDueCount = 5,
            ratedLocally = 0,
            fetchedAtMillis = now,
            nowMillis = now,
        )!!
        assertNull(content.dueCount)
        assertEquals(R.string.german_web_card_lapsed, content.lineRes)
        assertTrue(content.openSettings)
        assertNull(content.openUrl)
    }

    // --- The URL and when the card is not shown ----------------------------------

    @Test
    fun openUrlIsBuiltFromThePairedBaseAddress() {
        assertEquals("https://host.example.com/#/wiederholen", GermanWebCardLogic.openUrl("https://host.example.com", 1))
        assertEquals("https://host.example.com/#/start", GermanWebCardLogic.openUrl("https://host.example.com", 0))
        assertNull(GermanWebCardLogic.openUrl(null, 12))
    }

    @Test
    fun noBaseAddressMeansTheCardIsNotShown() {
        assertNull(
            GermanWebCardLogic.content(
                germanEnabled = true,
                state = signedIn,
                baseUrl = null,
                serverDueCount = 12,
                ratedLocally = 0,
                fetchedAtMillis = now,
                nowMillis = now,
            ),
        )
    }

    @Test
    fun germanOffOrSignedOutMeansTheCardIsNotShown() {
        assertNull(
            GermanWebCardLogic.content(
                germanEnabled = false,
                state = signedIn,
                baseUrl = base,
                serverDueCount = 12,
                ratedLocally = 0,
                fetchedAtMillis = now,
                nowMillis = now,
            ),
        )
        assertNull(
            GermanWebCardLogic.content(
                germanEnabled = true,
                state = GermanWebSessionState.SignedOut,
                baseUrl = base,
                serverDueCount = 12,
                ratedLocally = 0,
                fetchedAtMillis = now,
                nowMillis = now,
            ),
        )
    }
}
