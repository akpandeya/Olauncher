package app.olauncher.helper

import android.content.Context
import androidx.work.BackoffPolicy
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
import app.olauncher.data.store.AiProviderConfig
import app.olauncher.data.store.AiProviders
import app.olauncher.data.store.AiResult
import app.olauncher.data.store.KeySource
import app.olauncher.data.store.KeyVault
import app.olauncher.data.store.PracticeGeneration
import app.olauncher.data.store.PracticeGenerator
import app.olauncher.data.store.PracticeItemsStore
import app.olauncher.data.store.PracticeQueue
import app.olauncher.data.store.WordsStore
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The background top-up of the hybrid generation design (#601, 9.8; docs/design/
 * ai-practice.md "Budgets"): unique periodic work `ai_practice`, 24 h, network-
 * constrained, that restores every word's pool to 3 items — one batched request,
 * FIFO oldest-first with jitter, under the daily word cap ([PracticeQueue.next]).
 *
 * AUTH sets `AI_KEY_REJECTED` and stops; BILLING sets `AI_BILLING_BLOCKED` (the
 * user must top up their credits) and stops; RATE_LIMIT and NETWORK retry with
 * WorkManager's jittered exponential backoff; every other outcome waits for the
 * next run — the words stay in the derived queue, so nothing is lost and nothing
 * hot-loops. The API key only ever flows through [KeyVault] and [KeySource]; it
 * is never logged.
 */
class PracticeWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = Prefs(applicationContext)
        // The gate: off, no model or no key means no call, ever.
        if (!prefs.aiPracticeEnabled || prefs.aiModel.isBlank()) return@withContext Result.success()
        val vault = KeyVault(applicationContext)
        val keySource = KeySource { vault.read() }
        if (keySource.get().isNullOrBlank()) return@withContext Result.success()

        val config = AiProviderConfig(prefs.aiProvider, prefs.aiBaseUrl, prefs.aiModel)
        val provider = AiProviders.create(config, keySource)
            ?: return@withContext Result.success()
        val generator = PracticeGenerator(
            provider = provider,
            store = PracticeItemsStore(applicationContext),
            model = prefs.aiModel,
            keySource = keySource,
        )
        val queue = PracticeQueue.next(
            words = WordsStore(applicationContext).all(),
            items = PracticeItemsStore(applicationContext).all(),
            today = LocalDate.now(),
            cap = prefs.aiDailyWordCap,
            random = kotlin.random.Random.Default,
        )
        when (val outcome = generator.topUp(queue, prefs, LocalDate.now())) {
            is PracticeGeneration.Generated -> Result.success()
            is PracticeGeneration.Skipped -> Result.success()
            is PracticeGeneration.Failed -> when (outcome.kind) {
                // AUTH and BILLING cannot be fixed by retrying; the state is in prefs.
                AiResult.ErrorKind.AUTH, AiResult.ErrorKind.BILLING -> Result.success()
                // Ordinary throttling and lost connectivity: WorkManager backs off.
                AiResult.ErrorKind.RATE_LIMIT, AiResult.ErrorKind.NETWORK -> Result.retry()
                // Bad config or a provider outage: the words stay queued for tomorrow.
                else -> Result.success()
            }
        }
    }

    companion object {
        const val WORK_NAME_PERIODIC = "ai_practice"
        const val WORK_NAME_NOW = "ai_practice_now"
        private const val BACKOFF_MS = 30_000L

        private fun constraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<PracticeWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MS, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        /** One immediate run of the same queue — the settings "Generate now" entry (#602). */
        fun runOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<PracticeWorker>()
                .setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MS, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_NOW, ExistingWorkPolicy.REPLACE, request
            )
        }

        fun cancel(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(WORK_NAME_PERIODIC)
            wm.cancelUniqueWork(WORK_NAME_NOW)
        }
    }
}
