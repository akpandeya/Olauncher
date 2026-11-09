package app.olauncher.ui.today.cards

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.annotation.StringRes
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.data.store.GermanDueCache
import app.olauncher.data.store.GermanWebSession
import app.olauncher.data.store.GermanWebSessionState
import app.olauncher.databinding.CardGermanWebBinding
import app.olauncher.helper.GermanDueRefreshWorker
import app.olauncher.helper.Haptics
import app.olauncher.helper.openUrl
import app.olauncher.ui.settings.SettingsDeepLinks
import app.olauncher.ui.today.TodayCard
import app.olauncher.ui.today.attachPressScale

/**
 * What the German web card shows for one session (epic #835, task 05):
 * the due count from the last cached refresh with the line under it, or the
 * reconnect message while lapsed. Context-free so the state-to-lines mapping
 * is unit-tested; the card only formats it.
 */
data class GermanWebCardContent(
    /** The big number; null while lapsed (there is no count to show). */
    val dueCount: Int?,
    /** The line under the number; while lapsed the reconnect message. */
    @StringRes val lineRes: Int,
    /** True when a tap opens the German settings section instead of the web app. */
    val openSettings: Boolean,
    /** The web app URL a tap opens (base + route); null while lapsed. */
    val openUrl: String?,
)

/**
 * Context-free rules for the Today German web card (epic #835, task 05):
 * - Available whenever German is on and the web session is signed in or lapsed.
 * - The big number is the server's due count from the last refresh minus the
 *   words rated locally since then, never below 0.
 * - The line under it is "Due on the web", with a "Not synced yet" hint while
 *   the cache is empty or older than a day.
 * - A tap opens the web app's practice route while words are due, else its
 *   start page — the installed PWA takes the URL when there is one.
 */
object GermanWebCardLogic {

    /** The web app's practice route (task 05: `#/wiederholen`). */
    const val ROUTE_DUE = "/#/wiederholen"

    /** The web app's start page, when nothing is due. */
    const val ROUTE_START = "/#/start"

    /** The line gets the "Not synced yet" hint once the cache is a day old. */
    const val HINT_AFTER_MILLIS = 24L * 60 * 60 * 1000

    /** Available whenever German is on and the session is signed in or lapsed. */
    fun isAvailable(germanEnabled: Boolean, state: GermanWebSessionState): Boolean =
        germanEnabled && state !is GermanWebSessionState.SignedOut

    /** The big number: the server's count from the last refresh minus locally rated words, never below 0. */
    fun dueCount(serverDueCount: Int, ratedLocally: Int): Int =
        (serverDueCount - ratedLocally).coerceAtLeast(0)

    /** The hint shows while the cache is empty or older than a day. */
    fun showFreshnessHint(fetchedAtMillis: Long?, nowMillis: Long): Boolean =
        fetchedAtMillis == null || nowMillis - fetchedAtMillis > HINT_AFTER_MILLIS

    /** The web URL a tap opens: practice while words are due, else the start page. */
    fun openUrl(baseUrl: String?, dueCount: Int): String? =
        baseUrl?.takeIf { it.isNotBlank() }
            ?.let { base -> base + if (dueCount > 0) ROUTE_DUE else ROUTE_START }

    /**
     * The card's whole content, or null when it must not be shown at all:
     * German off, signed out, or no server address. [serverDueCount] and
     * [ratedLocally] come from the last cached refresh ([GermanDueCache.read];
     * 0 with a null [fetchedAtMillis] when there is none yet) — never a
     * network call.
     */
    fun content(
        germanEnabled: Boolean,
        state: GermanWebSessionState,
        baseUrl: String?,
        serverDueCount: Int,
        ratedLocally: Int,
        fetchedAtMillis: Long?,
        nowMillis: Long,
    ): GermanWebCardContent? {
        if (!isAvailable(germanEnabled, state)) return null
        if (baseUrl.isNullOrBlank()) return null
        return when (state) {
            GermanWebSessionState.Lapsed -> GermanWebCardContent(
                dueCount = null,
                lineRes = R.string.german_web_card_lapsed,
                openSettings = true,
                openUrl = null,
            )
            is GermanWebSessionState.SignedIn -> {
                val due = dueCount(serverDueCount, ratedLocally)
                GermanWebCardContent(
                    dueCount = due,
                    lineRes = if (showFreshnessHint(fetchedAtMillis, nowMillis)) {
                        R.string.german_web_card_due_line_stale
                    } else {
                        R.string.german_web_card_due_line
                    },
                    openSettings = false,
                    openUrl = openUrl(baseUrl, due),
                )
            }
            GermanWebSessionState.SignedOut -> null
        }
    }
}

/**
 * Full-width card: the due count waiting on the German web app and a one-tap
 * open of its practice page. The count comes from the local cache only —
 * nothing here reaches the network; a stale cache only asks WorkManager for
 * one background refresh. While lapsed the card shows the reconnect message
 * and a tap opens the German settings section. Per-view state lives in
 * [GermanWebCardView], stored on the root view.
 */
object GermanWebCard : TodayCard {

    const val KEY = "german_web"

    override val key: String = KEY
    override val titleRes: Int = R.string.german_web_card_title
    override val emoji: String = "🌐"

    var nowMillisSupplier: () -> Long = { System.currentTimeMillis() }

    override fun isAvailable(context: Context): Boolean {
        return GermanWebCardLogic.isAvailable(
            Prefs(context).germanEnabled,
            GermanWebSession(context).state(),
        )
    }

    /** No server address (not paired with one) means no panel at all. */
    override fun hasContent(context: Context): Boolean {
        return GermanWebSession(context).baseUrl() != null
    }

    override fun createView(inflater: LayoutInflater, parent: ViewGroup, fragment: Fragment): View {
        val cardView = GermanWebCardView(
            CardGermanWebBinding.inflate(inflater, parent, false),
            onOpenWeb = { url -> fragment.context?.openUrl(url) },
            onOpenSettings = {
                val navController = fragment.findNavController()
                // Ignore a second tap delivered before the first navigation lands.
                if (navController.currentDestination?.id == R.id.todayFragment) {
                    navController.navigate(R.id.settingsFragment, SettingsDeepLinks.germanWebAccountBundle())
                }
            },
        )
        cardView.binding.root.tag = cardView
        cardView.binding.root.attachPressScale()
        cardView.render()
        return cardView.binding.root
    }

    override fun onResume(view: View) {
        (view.tag as? GermanWebCardView)?.render()
    }
}

/** Holds the state of one German web card view. */
internal class GermanWebCardView(
    val binding: CardGermanWebBinding,
    private val onOpenWeb: (String) -> Unit,
    private val onOpenSettings: () -> Unit,
) {

    private var targetUrl: String? = null
    private var opensSettings = false

    init {
        binding.root.setOnClickListener { view ->
            Haptics.tick(view)
            open()
        }
        binding.tvOpen.setOnClickListener { view ->
            Haptics.tick(view)
            open()
        }
    }

    private fun open() {
        if (opensSettings) onOpenSettings() else targetUrl?.let(onOpenWeb)
    }

    fun render() {
        val context = binding.root.context ?: return
        val session = GermanWebSession(context)
        val state = session.state()
        // The count and freshness come from the local cache; a cache older than
        // an hour only asks for one background refresh (task 04), so the card
        // renders in airplane mode too.
        if (state is GermanWebSessionState.SignedIn) {
            GermanDueRefreshWorker.refreshIfStale(context, state)
        }
        val cacheState = (state as? GermanWebSessionState.SignedIn)
            ?.let { GermanDueCache(context.noBackupFilesDir).read(it.username) }
        val content = GermanWebCardLogic.content(
            germanEnabled = Prefs(context).germanEnabled,
            state = state,
            baseUrl = session.baseUrl(),
            serverDueCount = cacheState?.dueCount ?: 0,
            ratedLocally = cacheState?.ratedIds?.size ?: 0,
            fetchedAtMillis = cacheState?.fetchedAt,
            nowMillis = GermanWebCard.nowMillisSupplier(),
        ) ?: return
        opensSettings = content.openSettings
        targetUrl = content.openUrl
        val showCount = content.dueCount != null
        binding.numberRow.visibility = if (showCount) View.VISIBLE else View.GONE
        binding.divider.visibility = if (showCount) View.VISIBLE else View.GONE
        if (showCount) binding.tvDue.text = content.dueCount.toString()
        binding.tvLine.setText(content.lineRes)
        binding.tvOpen.visibility = if (content.openSettings) View.GONE else View.VISIBLE
    }
}
