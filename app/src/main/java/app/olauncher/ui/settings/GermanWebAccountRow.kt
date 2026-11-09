package app.olauncher.ui.settings

import androidx.annotation.StringRes
import app.olauncher.R
import app.olauncher.data.store.GermanWebSessionState

/**
 * The text of the web-account row's value line for one German web session
 * (epic #835, task 02): "Not connected" while signed out, "Connected as
 * <user> · <host>" while signed in, "Signed out. Enter a new code" once a
 * stored token got 401 and pairing is needed again. Context-free so the
 * state-to-text mapping is unit-tested; the fragment only formats it.
 */
data class GermanWebAccountRowText(
    @StringRes val stringRes: Int,
    /** The arguments the string takes, in order (the username, then the host). */
    val formatArgs: List<String>,
)

object GermanWebAccountRow {

    /**
     * The value line for [state]. The signed-in line names the server host too
     * — the user sees what they are paired with — unless the session somehow
     * lost it, in which case only the username is shown.
     */
    fun valueFor(state: GermanWebSessionState, serverHost: String? = null): GermanWebAccountRowText =
        when (state) {
            GermanWebSessionState.SignedOut ->
                GermanWebAccountRowText(R.string.german_web_not_connected, emptyList())
            is GermanWebSessionState.SignedIn ->
                if (serverHost.isNullOrBlank()) {
                    GermanWebAccountRowText(R.string.german_web_connected_as, listOf(state.username))
                } else {
                    GermanWebAccountRowText(R.string.german_web_connected_as_host, listOf(state.username, serverHost))
                }
            GermanWebSessionState.Lapsed ->
                GermanWebAccountRowText(R.string.german_web_lapsed, emptyList())
        }

    /**
     * The sync-count lines (task 03) appended to the value line when non-zero:
     * "· N waiting to sync" while the outbox is non-empty, "· N failed" once
     * entries were dropped. Both use distinguishing counts, so swapped or
     * missing lines cannot pass.
     */
    fun countLines(waitingCount: Int, failedCount: Int): List<GermanWebAccountRowText> = buildList {
        if (waitingCount > 0) {
            add(GermanWebAccountRowText(R.string.german_web_waiting_to_sync, listOf(waitingCount.toString())))
        }
        if (failedCount > 0) {
            add(GermanWebAccountRowText(R.string.german_web_failed_count, listOf(failedCount.toString())))
        }
    }
}
