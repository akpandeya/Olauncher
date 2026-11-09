package app.olauncher.helper

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.olauncher.data.store.GermanDeviceCredentialStore
import app.olauncher.data.store.GermanOutboxReplay
import app.olauncher.data.store.GermanReplayStopReason
import app.olauncher.data.store.GermanWebApi
import app.olauncher.data.store.GermanWebOutbox
import app.olauncher.data.store.GermanWebSession
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Replays the German web outbox (epic #835, task 03) whenever a capture was
 * queued, pairing succeeded, or the app started with a non-empty queue. Unique
 * work (`german_web_sync`, [ExistingWorkPolicy.KEEP] so a burst of captures
 * produces one run), network-constrained, exponential backoff on retry —
 * the `WeatherWorker` scheduling pattern.
 *
 * The device token is read from the credential store inside the worker and
 * never put into WorkManager `Data`. A session that is not signed in stops the
 * run at once: lapsed and signed out have no credential to send with, and a
 * queued 401 must not loop retries.
 */
class GermanSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val credential = GermanDeviceCredentialStore(applicationContext).read()
            ?: return@withContext Result.success()
        val session = GermanWebSession(applicationContext)
        val base = session.baseUrl() ?: return@withContext Result.success()
        val outbox = GermanWebOutbox(applicationContext.noBackupFilesDir)
        if (outbox.pendingCount() == 0) return@withContext Result.success()
        val outcome = GermanOutboxReplay.replayAll(
            outbox = outbox,
            api = GermanWebApi(base),
            token = credential.token,
            onUnauthorized = {
                session.onUnauthorized()
                // A lapsed session ends the due-set refresh too (#835, task 04):
                // the cached due set is dropped and the periodic work stops.
                GermanDueRefreshWorker.onSessionEnded(applicationContext)
            },
        )
        // Unauthorized: the session is lapsed now; pairing again re-schedules.
        // Anything else still pending (backoff, Retry-After or the 15-per-run
        // cap) is picked up by the next WorkManager retry.
        if (outcome.stopReason == GermanReplayStopReason.UNAUTHORIZED || outbox.pendingCount() == 0) {
            Result.success()
        } else {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "german_web_sync"

        /** Enqueues the unique replay run; an already-queued or running one is kept. */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<GermanSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}
