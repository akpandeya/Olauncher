package app.olauncher.helper

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.olauncher.data.Prefs
import app.olauncher.data.store.HttpNewsFetcher
import app.olauncher.data.store.NewsBackfill
import app.olauncher.data.store.NewsHarvestLimits
import app.olauncher.data.store.NewsHarvester
import app.olauncher.data.store.NewsSources
import app.olauncher.data.store.RecentSentencesStore
import app.olauncher.data.store.SentenceWordMatcher
import app.olauncher.data.store.SharedOfflineGermanStore
import app.olauncher.data.store.WordsStore
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background worker that harvests German news sentences into [RecentSentencesStore].
 *
 * Fetches only allowlisted news feeds and article pages from [NewsSources.ALL] hosts.
 * Runs only under unmetered network (Wi-Fi) and while charging, scheduled every 12 hours.
 * Enforces caps: at most 10 articles per run ([NewsHarvestLimits.maxArticlesPerRun]) and
 * at most 30 articles per day ([Prefs.NEWS_DAILY_ARTICLE_CAP]).
 *
 * Only sentences harvested in this run are matched, which bounds the lemma lookups per run
 * (≈ 10 articles × 40 sentences); words saved later get links from later harvests. No AI call
 * is triggered here; filled contexts are picked up by the next [PracticeWorker] periodic run.
 *
 * Never retries ([androidx.work.ListenableWorker.Result.retry]): on failure, it logs and
 * returns [androidx.work.ListenableWorker.Result.success] so the next periodic run can try
 * again without hot loops against news sites.
 */
class NewsHarvestWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = Prefs(applicationContext)
        if (!NewsHarvestPolicy.mayRun(prefs.newsSentencesEnabled, prefs.germanEnabled)) {
            return@withContext Result.success()
        }

        runGuard.runExclusive { harvestAndBackfill(prefs) }
        Result.success()
    }

    private suspend fun harvestAndBackfill(prefs: Prefs) {
        val today = LocalDate.now().toEpochDay()
        val left = NewsHarvestPolicy.articlesLeftToday(
            Prefs.NEWS_DAILY_ARTICLE_CAP,
            prefs.newsArticlesFetchedToday(today),
        )
        if (left == 0) return

        try {
            val sentenceStore = RecentSentencesStore(applicationContext)
            // Count each article request as it is sent, so a run cancelled part-way (charger
            // unplugged, Wi-Fi lost) or a failing backfill still uses up the daily cap.
            val result = NewsHarvester(HttpNewsFetcher(), sentenceStore)
                .harvest(NewsHarvestLimits(articlesLeftToday = left)) {
                    prefs.recordNewsArticlesFetched(1, today)
                }
            if (result.newSentences.isNotEmpty()) {
                val offline = if (prefs.germanOfflineDictionary) SharedOfflineGermanStore.get(applicationContext) else null
                val matcher = SentenceWordMatcher { form -> offline?.lemma(form) }
                val wordsStore = WordsStore(applicationContext)
                val plan = NewsBackfill.plan(wordsStore.all(), result.newSentences, matcher)
                NewsBackfill.applyPlan(plan, sentenceStore, wordsStore)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "News harvest failed (${e.javaClass.simpleName})")
        }
    }

    companion object {
        private const val TAG = "NewsHarvestWorker"
        const val WORK_NAME_PERIODIC = "news_sentences"
        const val WORK_NAME_NOW = "news_sentences_now"
        const val PERIODIC_INTERVAL_HOURS = 12L

        /** Process-wide: the periodic and the one-off work must not harvest at the same time. */
        private val runGuard = NewsHarvestRunGuard()

        private fun constraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresCharging(true)
            .build()

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<NewsHarvestWorker>(
                PERIODIC_INTERVAL_HOURS,
                TimeUnit.HOURS,
            )
                .setConstraints(constraints())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun runOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<NewsHarvestWorker>()
                .setConstraints(constraints())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_NOW,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(WORK_NAME_PERIODIC)
            wm.cancelUniqueWork(WORK_NAME_NOW)
        }
    }
}
