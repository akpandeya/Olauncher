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
import app.olauncher.data.store.DrillScores
import app.olauncher.data.store.KeySource
import app.olauncher.data.store.KeyVault
import app.olauncher.data.store.PracticeDigest
import app.olauncher.data.store.PracticeGeneration
import app.olauncher.data.store.PracticeGenerator
import app.olauncher.data.store.PracticeItemsStore
import app.olauncher.data.store.PracticeQueue
import app.olauncher.data.store.ReviewEventsStore
import app.olauncher.data.store.WordsStore
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The background top-up of the hybrid generation design (#601, 9.8; docs/design/
 * ai-practice.md "Budgets"): unique periodic work `ai_practice`, 24 h, network-
 * constrained, that restores every word's pool to 3 items — chunked requests,
 * FIFO oldest-first with jitter, under the daily word cap ([PracticeQueue.next]).
 *
 * The first top-up after switching on is the unique one-shot `ai_practice_now`
 * work; the periodic run starts a day later, so switching on never runs two
 * workers that send the same queue at once (review R1 #3).
 *
 * AUTH sets `AI_KEY_REJECTED` and stops — later runs are skipped until a new key
 * is saved, which clears the flag; BILLING sets `AI_BILLING_BLOCKED` (the user
 * must top up their credits) and stops; both end the run before the digest, which
 * would otherwise call the provider again in the same run (review R1 #6).
 * RATE_LIMIT and NETWORK retry with WorkManager's jittered exponential backoff;
 * every other outcome waits for the next run — the words stay in the derived
 * queue, so nothing is lost and nothing hot-loops. The API key only ever flows
 * through [KeyVault] and [KeySource]; it is never logged.
 */
class PracticeWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = Prefs(applicationContext)
        // The shared gate (#602) plus a standing key rejection (review R1 #6):
        // after AUTH no call goes out until a new key is saved (it clears the flag).
        val vault = KeyVault(applicationContext)
        val keySource = KeySource { vault.read() }
        if (!PracticeWorkerPolicy.mayCall(
                enabled = prefs.aiPracticeEnabled,
                hasKey = !keySource.get().isNullOrBlank(),
                model = prefs.aiModel,
                keyRejected = prefs.aiKeyRejected,
            )
        ) {
            return@withContext Result.success()
        }

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
        val topUpOutcome = generator.topUp(queue, prefs, LocalDate.now())
        val topUpResult = when (topUpOutcome) {
            is PracticeGeneration.Generated -> Result.success()
            is PracticeGeneration.Skipped -> Result.success()
            is PracticeGeneration.Failed -> when (topUpOutcome.kind) {
                // AUTH and BILLING cannot be fixed by retrying; the state is in prefs.
                AiResult.ErrorKind.AUTH, AiResult.ErrorKind.BILLING -> Result.success()
                // Ordinary throttling and lost connectivity: WorkManager backs off —
                // the next run covers the digest too.
                AiResult.ErrorKind.RATE_LIMIT, AiResult.ErrorKind.NETWORK -> Result.retry()
                // Bad config or a provider outage: the words stay queued for tomorrow.
                else -> Result.success()
            }
        }
        if (topUpResult == Result.retry()) return@withContext topUpResult
        // AUTH and BILLING end the run here: the digest would call the provider
        // again with a key that was just rejected or an account out of credits
        // (review R1 #6). A failed top-up of any other kind does not block it;
        // the digest applies the same gate and cap inside the generator.
        if (topUpOutcome is PracticeGeneration.Failed && PracticeWorkerPolicy.stopsRun(topUpOutcome.kind)) {
            return@withContext Result.success()
        }
        maybeDigest(generator, prefs)
        return@withContext Result.success()
    }

    /**
     * The digest trigger: due per [PracticeDigest.due], and only when there is
     * something weak to send — weak words from the review event log, restricted
     * to the weak drill types from `DRILL_SCORES`. The batched call goes through
     * [PracticeGenerator.digest]; on success the one-shot Words-card announcement
     * is armed, on anything else `AI_LAST_DIGEST_AT` stays untouched.
     */
    private suspend fun maybeDigest(generator: PracticeGenerator, prefs: Prefs) {
        val now = System.currentTimeMillis()
        val events = ReviewEventsStore(applicationContext).all()
        if (!PracticeDigest.due(prefs.aiLastDigestAt, now, events)) return
        val weak = PracticeDigest.summary(events, DrillScores.decode(prefs.drillScores), now)
        if (weak.wordIds.isEmpty()) return
        val words = WordsStore(applicationContext).all()
        val weakWords = weak.wordIds.mapNotNull { id -> words.firstOrNull { it.id == id } }
            .filter { it.context.isNotBlank() }
        if (weakWords.isEmpty()) return
        val outcome = generator.digest(weakWords, weak.drillTypes, prefs, LocalDate.now(), now)
        if (outcome is PracticeGeneration.Generated) {
            // The announcement counts the words the digest actually covered —
            // outcome.words — not the whole weak-spot list: blank-context words
            // are filtered out before the call (review R1 #5).
            prefs.aiDigestNew = outcome.words.size
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
            // The first day's top-up is the one-shot `ai_practice_now` run; the
            // periodic work starts a day later so switching on never launches two
            // workers that both send the same queue at once (review R1 #3).
            val request = PeriodicWorkRequestBuilder<PracticeWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(1, TimeUnit.DAYS)
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
            // KEEP: a run already in flight is never replaced and restarted mid-call.
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_NOW, ExistingWorkPolicy.KEEP, request
            )
        }

        fun cancel(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(WORK_NAME_PERIODIC)
            wm.cancelUniqueWork(WORK_NAME_NOW)
        }
    }
}
