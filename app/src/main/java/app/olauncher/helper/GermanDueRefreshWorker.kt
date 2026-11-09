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
import app.olauncher.data.store.GermanDeviceCredentialStore
import app.olauncher.data.store.GermanDueCache
import app.olauncher.data.store.GermanDueRefresh
import app.olauncher.data.store.GermanDueRefreshOutcome
import app.olauncher.data.store.GermanWebApi
import app.olauncher.data.store.GermanWebOutbox
import app.olauncher.data.store.GermanWebSession
import app.olauncher.data.store.GermanWebSessionState
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Keeps the local copy of the server's due set fresh (epic #835, task 04):
 * unique periodic work (every 6 h, network-constrained) while the session is
 * [GermanWebSessionState.SignedIn], plus one unique one-time run when the
 * pause screen or the app opens with a cache older than an hour — the
 * `WeatherWorker` scheduling pattern. The refresh itself is the
 * Context-free, unit-tested [GermanDueRefresh].
 *
 * Signing out or lapsing clears the cache and cancels the periodic work
 * ([onSessionEnded], called from the task 01–03 call sites). The device
 * token is read inside the worker and never put into WorkManager `Data`.
 */
class GermanDueRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val credential = GermanDeviceCredentialStore(applicationContext).read()
            ?: return@withContext Result.success()
        val session = GermanWebSession(applicationContext)
        val base = session.baseUrl() ?: return@withContext Result.success()
        val outcome = GermanDueRefresh.refresh(
            cache = GermanDueCache(applicationContext.noBackupFilesDir),
            outbox = GermanWebOutbox(applicationContext.noBackupFilesDir),
            api = GermanWebApi(base),
            token = credential.token,
            username = credential.username,
            today = LocalDate.now().toEpochDay(),
            nowMillis = System.currentTimeMillis(),
            onUnauthorized = {
                session.onUnauthorized()
                // The session is lapsed now: drop the dead user's cache and
                // stop the periodic refresh; pairing again re-schedules both.
                onSessionEnded(applicationContext)
            },
        )
        when (outcome) {
            GermanDueRefreshOutcome.Refreshed -> Result.success()
            // A retry would loop a dead token; pairing again re-schedules the work.
            GermanDueRefreshOutcome.Unauthorized -> Result.success()
            GermanDueRefreshOutcome.KeptOldCache -> Result.retry()
        }
    }

    companion object {
        const val PERIODIC_WORK_NAME = "german_due_refresh_periodic"
        const val ONE_TIME_WORK_NAME = "german_due_refresh_once"

        private const val REPEAT_HOURS = 6L

        /** The pause screen shows a cached word; an hour-old cache is refreshed once in the background. */
        const val STALE_AFTER_MILLIS = 60L * 60 * 1000

        private fun networkConstrained(): Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** The periodic refresh while signed in; an already-scheduled one is kept (idempotent). */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<GermanDueRefreshWorker>(REPEAT_HOURS, TimeUnit.HOURS)
                .setConstraints(networkConstrained())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        private fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        }

        /**
         * One refresh now when the pause screen or the app opens with a cache
         * that is missing, older than an hour, or another user's. Callers
         * that already read the session pass [state]; every other session
         * state than signed in is a no-op.
         */
        fun refreshIfStale(
            context: Context,
            state: GermanWebSessionState = GermanWebSession(context).state(),
        ) {
            val signedIn = state as? GermanWebSessionState.SignedIn ?: return
            if (GermanWebSession(context).baseUrl() == null) return
            val fetchedAt = GermanDueCache(context.noBackupFilesDir).fetchedAt(signedIn.username)
            if (fetchedAt != null && System.currentTimeMillis() - fetchedAt < STALE_AFTER_MILLIS) return
            val request = OneTimeWorkRequestBuilder<GermanDueRefreshWorker>()
                .setConstraints(networkConstrained())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONE_TIME_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        /** Signing out or lapsing: the due cache is dropped and the periodic refresh stops. */
        fun onSessionEnded(context: Context) {
            GermanDueCache(context.noBackupFilesDir).clear()
            cancelPeriodic(context)
        }
    }
}
