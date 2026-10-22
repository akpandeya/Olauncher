package app.olauncher.helper

import app.olauncher.data.AppModel

/** What the drawer filter hands from its worker thread to the UI thread. */
data class DrawerFilterPayload(
    val items: List<AppModel>,
    val scores: List<Int>,
    val searchQuery: String,
    val isBangSearch: Boolean,
    val autoLaunch: Boolean,
) {
    companion object {
        fun from(result: DrawerSearchResult) = DrawerFilterPayload(
            result.items, result.scores, result.searchQuery, result.isBangSearch, result.autoLaunch,
        )
    }
}

/** The filter state the adapter keeps between publishes. */
data class DrawerFilterState(
    val items: List<AppModel>,
    val scores: List<Int>,
    val searchQuery: String,
    val isBangSearch: Boolean,
    val autoLaunch: Boolean,
)

/** Context-free parts of AppDrawerAdapter, kept here so they run in JVM tests. */
object DrawerAdapterLogic {

    /**
     * State after a filter run. A missing [payload] shows an empty list and keeps the
     * [constraint] as the query; [previous] bang and auto-launch flags are kept then.
     */
    fun publish(
        constraint: CharSequence?,
        payload: DrawerFilterPayload?,
        previous: DrawerFilterState,
    ): DrawerFilterState =
        if (payload != null) {
            DrawerFilterState(payload.items, payload.scores, payload.searchQuery, payload.isBangSearch, payload.autoLaunch)
        } else {
            previous.copy(items = emptyList(), scores = emptyList(), searchQuery = constraint?.toString().orEmpty())
        }

    /** Row to scroll to after a publish: the last row for bottom search, else the first. */
    fun scrollTarget(bottomSearch: Boolean, itemCount: Int): Int? =
        if (itemCount <= 0) null else if (bottomSearch) itemCount - 1 else 0

    fun strongMatches(scores: List<Int>): Int = scores.count { it >= AppMatcher.SCORE_SUBSTRING }

    /** The row nearest the search box, which auto-launch would open. */
    fun autoLaunchCandidate(list: List<AppModel>, bottomSearch: Boolean): AppModel? =
        if (bottomSearch) list.lastOrNull() else list.firstOrNull()

    /** Drops every row with the same package and user as [app]. Returns a new list. */
    fun removeApp(list: List<AppModel>, app: AppModel): List<AppModel> =
        // The identity check short-circuits UserHandle.equals, which is a stub in JVM tests.
        list.filterNot { it.appPackage == app.appPackage && (it.user === app.user || it.user == app.user) }
}
