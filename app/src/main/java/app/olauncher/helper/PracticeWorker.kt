package app.olauncher.helper

import android.content.Context
import android.os.SystemClock
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
import app.olauncher.data.store.PracticeRunLock
import app.olauncher.data.store.ReviewEventsStore
import app.olauncher.data.store.WordsStore
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The background top-up of the hybrid generation design (#601, 9.8; docs/design/
 * ai-practice.md "Budgets"): unique periodic work `ai_practice`, 6 h, network-
 * constrained, that restores every word's pool to 3 items — chunked requests,
 * FIFO oldest-first with jitter, under the daily word cap ([PracticeQueue.next]).
 *
 * The first top-up after switching on is the unique one-shot `ai_practice_now`
 * work; the periodic run starts 6 h later, so switching on never runs two
 * workers that send the same queue at once (review R1 #3).
 *
 * A capture or sentence edit enqueues one-shot `ai_practice_soon` 2 min later
 * (#725). Every run takes [PracticeRunLock.APP] and skips when another run holds it.
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

        // One run at a time (#725): a run that finds another in flight ends here,
        // without a request; its words stay queued for the next trigger.
        PracticeRunLock.APP.runOrSkip { generateAndDigest(prefs, keySource) } ?: Result.success()
    }

    private suspend fun generateAndDigest(prefs: Prefs, keySource: KeySource): Result {
        val config = AiProviderConfig(prefs.aiProvider, prefs.aiBaseUrl, prefs.aiModel)
        val provider = AiProviders.create(config, keySource)
            ?: return Result.success()
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
            // Chunks that stored keep their success (review R2 B): the covered
            // words are done, so only the retryable kinds rerun the worker for
            // the rest of the queue; the others wait for the next periodic run.
            is PracticeGeneration.Partial -> when (topUpOutcome.kind) {
                AiResult.ErrorKind.NETWORK, AiResult.ErrorKind.RATE_LIMIT -> Result.retry()
                else -> Result.success()
            }
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
        if (topUpResult == Result.retry()) return topUpResult
        // AUTH and BILLING end the run here: the digest would call the provider
        // again with a key that was just rejected or an account out of credits
        // (review R1 #6) — also when earlier chunks stored (a Partial, R2 B). A
        // failed top-up of any other kind does not block it; the digest applies
        // the same gate and cap inside the generator.
        val topUpErrorKind = when (topUpOutcome) {
            is PracticeGeneration.Failed -> topUpOutcome.kind
            is PracticeGeneration.Partial -> topUpOutcome.kind
            else -> null
        }
        if (topUpErrorKind != null && PracticeWorkerPolicy.stopsRun(topUpErrorKind)) {
            return Result.success()
        }
        maybeDigest(generator, prefs)
        return Result.success()
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
        // The announcement counts the words the digest actually covered —
        // outcome.words — not the whole weak-spot list: blank-context words
        // are filtered out before the call (review R1 #5). A partial digest
        // counts the covered words too (review R2 B). A digest that stored
        // nothing new (every card a copy of a stored one) announces nothing.
        PracticeWorkerPolicy.digestAnnouncement(outcome)?.let { prefs.aiDigestNew = it }
    }

    companion object {
        const val WORK_NAME_PERIODIC = "ai_practice"
        const val WORK_NAME_NOW = "ai_practice_now"
        /** One-shot work after a capture or on review open (#725); separate from "Generate now". */
        const val WORK_NAME_SOON = "ai_practice_soon"
        /** Captures within this window go out together in one run (#725). */
        const val CAPTURE_DELAY_MS = 120_000L
        /** Periodic safety net (#725): was 24 h. Also the initial delay. */
        const val PERIODIC_INTERVAL_HOURS = 6L
        private const val BACKOFF_MS = 30_000L

        private fun constraints() = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedulePeriodic(context: Context) {
            // The initial delay stays one period. Switching on already calls
            // runOnce at once, so a zero delay would start a second run that the
            // lock skips anyway (#725). UPDATE moves existing installs from 24 h to 6 h.
            val request = PeriodicWorkRequestBuilder<PracticeWorker>(PERIODIC_INTERVAL_HOURS, TimeUnit.HOURS)
                .setInitialDelay(PERIODIC_INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MS, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request
            )
        }

        /**
         * After a word is saved or its sentence changes (#725): one run about
         * [CAPTURE_DELAY_MS] later, network-constrained. KEEP: a capture while
         * the work is pending joins that run (one batch); a capture while it is
         * running never cancels a billed call — that word waits for the next
         * trigger. No-op while AI practice is off.
         */
        fun scheduleAfterCapture(context: Context) {
            if (!Prefs(context).aiPracticeEnabled) return
            enqueueSoon(context, CAPTURE_DELAY_MS)
        }

        /** elapsedRealtime of this process's last review-open kick; null before the first. */
        @Volatile private var lastReviewKickAtMs: Long? = null

        /**
         * Review opened (#725): fire-and-forget. Enqueues `ai_practice_soon` with
         * no delay when [ReviewKickPolicy.shouldKick] allows; never waits, never
         * blocks the first card. KEEP: a pending capture run (≤ 2 min away) or a
         * run in flight absorbs the kick.
         */
        fun kickFromReview(context: Context, dueNeedItems: Boolean) {
            val prefs = Prefs(context)
            val now = SystemClock.elapsedRealtime()
            if (!ReviewKickPolicy.shouldKick(
                    prefs.aiPracticeEnabled, prefs.aiModel, prefs.aiKeyRejected,
                    dueNeedItems, now, lastReviewKickAtMs,
                )
            ) return
            lastReviewKickAtMs = now
            enqueueSoon(context, delayMs = 0L)
        }

        private fun enqueueSoon(context: Context, delayMs: Long) {
            val request = OneTimeWorkRequestBuilder<PracticeWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MS, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                WORK_NAME_SOON, ExistingWorkPolicy.KEEP, request
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
            wm.cancelUniqueWork(WORK_NAME_SOON)
        }
    }
}
