package app.olauncher.ui.settings

import app.olauncher.R
import app.olauncher.data.store.GermanWebSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The web-account row's value line for each session state (#835, task 02).
 * Usernames and hosts are distinguishing, so a line that ignores or swaps
 * them cannot pass.
 */
class GermanWebAccountRowTest {

    @Test
    fun `signed out reads not connected`() {
        val text = GermanWebAccountRow.valueFor(GermanWebSessionState.SignedOut)
        assertEquals(R.string.german_web_not_connected, text.stringRes)
        assertTrue(text.formatArgs.isEmpty())
    }

    @Test
    fun `signed in names the user and the server host`() {
        val anna = GermanWebAccountRow.valueFor(
            GermanWebSessionState.SignedIn(username = "anna-hh", deviceId = "dev-1"),
            serverHost = "web.example.com",
        )
        assertEquals(R.string.german_web_connected_as_host, anna.stringRes)
        assertEquals(listOf("anna-hh", "web.example.com"), anna.formatArgs)

        val bernd = GermanWebAccountRow.valueFor(
            GermanWebSessionState.SignedIn(username = "bernd-k", deviceId = "dev-2"),
            serverHost = "anderer.example.org",
        )
        assertEquals(anna.stringRes, bernd.stringRes)
        assertEquals(listOf("bernd-k", "anderer.example.org"), bernd.formatArgs)
    }

    @Test
    fun `signed in without a host names only the user`() {
        val text = GermanWebAccountRow.valueFor(
            GermanWebSessionState.SignedIn(username = "clara-b", deviceId = "dev-3"),
            serverHost = null,
        )
        assertEquals(R.string.german_web_connected_as, text.stringRes)
        assertEquals(listOf("clara-b"), text.formatArgs)
    }

    @Test
    fun `lapsed asks for a new code`() {
        val text = GermanWebAccountRow.valueFor(GermanWebSessionState.Lapsed)
        assertEquals(R.string.german_web_lapsed, text.stringRes)
        assertTrue(text.formatArgs.isEmpty())
    }

    @Test
    fun `the three states read differently`() {
        val resources = setOf(
            GermanWebAccountRow.valueFor(GermanWebSessionState.SignedOut).stringRes,
            GermanWebAccountRow.valueFor(
                GermanWebSessionState.SignedIn(username = "dina-x", deviceId = "dev-4"),
                serverHost = "web.example.com",
            ).stringRes,
            GermanWebAccountRow.valueFor(GermanWebSessionState.Lapsed).stringRes,
        )
        assertEquals(3, resources.size)
    }

    @Test
    fun `count lines appear only when their count is non-zero`() {
        assertTrue(GermanWebAccountRow.countLines(waitingCount = 0, failedCount = 0).isEmpty())

        val waiting = GermanWebAccountRow.countLines(waitingCount = 7, failedCount = 0).single()
        assertEquals(R.string.german_web_waiting_to_sync, waiting.stringRes)
        assertEquals(listOf("7"), waiting.formatArgs)

        val failed = GermanWebAccountRow.countLines(waitingCount = 0, failedCount = 2).single()
        assertEquals(R.string.german_web_failed_count, failed.stringRes)
        assertEquals(listOf("2"), failed.formatArgs)
    }

    @Test
    fun `both counts appear, waiting first, when both are non-zero`() {
        val lines = GermanWebAccountRow.countLines(waitingCount = 7, failedCount = 2)

        assertEquals(2, lines.size)
        assertEquals(R.string.german_web_waiting_to_sync, lines[0].stringRes)
        assertEquals(listOf("7"), lines[0].formatArgs)
        assertEquals(R.string.german_web_failed_count, lines[1].stringRes)
        assertEquals(listOf("2"), lines[1].formatArgs)
    }
}
