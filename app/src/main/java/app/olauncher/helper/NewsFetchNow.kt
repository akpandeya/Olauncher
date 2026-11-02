package app.olauncher.helper

import app.olauncher.data.store.NewsHarvestLimits

/**
 * The Settings "Fetch now" action (#794): decides whether a manual news harvest may
 * run, and which feedback line the user gets for it. Context-free — it only sees
 * booleans and counts — so the JVM tests cover the whole decision; the Settings code
 * maps each result to a string and to the WorkManager enqueue.
 *
 * A manual run obeys the same rules as the periodic one ([NewsHarvestPolicy.mayRun]
 * — German on and the switch on — and the daily article cap); only the network gate
 * is dropped, because the user asked for this run. Overlapping runs are still
 * stopped by [NewsHarvestRunGuard] inside the worker.
 */
object NewsFetchNow {

    /** What a tap does. */
    sealed interface Plan {
        /** One manual harvest; at most [articles] articles this run. */
        data class Run(val articles: Int) : Plan

        /** The daily article cap is already spent; nothing runs. */
        object CapUsed : Plan

        /** German is off or the News sentences switch is off; nothing runs. */
        object Off : Plan
    }

    /** The line after the manual harvest has finished. */
    sealed interface Finished {
        /** The run fetched [articles] articles. */
        data class Fetched(val articles: Int) : Finished

        /** The run fetched nothing (also the outcome when a run was skipped as overlapping). */
        object NothingNew : Finished
    }

    fun plan(
        germanEnabled: Boolean,
        newsSentencesEnabled: Boolean,
        articlesLeftToday: Int,
        maxArticlesPerRun: Int = NewsHarvestLimits.DEFAULT_MAX_ARTICLES_PER_RUN,
    ): Plan = when {
        !NewsHarvestPolicy.mayRun(newsSentencesEnabled, germanEnabled) -> Plan.Off
        articlesLeftToday <= 0 -> Plan.CapUsed
        else -> Plan.Run(minOf(articlesLeftToday, maxArticlesPerRun))
    }

    fun finished(articlesFetched: Int): Finished =
        if (articlesFetched > 0) Finished.Fetched(articlesFetched) else Finished.NothingNew
}
