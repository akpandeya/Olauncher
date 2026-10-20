package app.olauncher.wellbeing

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.olauncher.R
import app.olauncher.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Scheduler for gentle nudge background work via WorkManager.
 */
object NudgeScheduler {

    const val WORK_NAME_PREFIX = "nudge_"
    const val KEY_TYPE = "type"

    fun workName(type: String): String = "$WORK_NAME_PREFIX$type"

    /**
     * Enqueues unique one-time work for the given nudge [type].
     * Uses [ExistingWorkPolicy.REPLACE] by default.
     */
    fun schedule(
        context: Context,
        type: String,
        policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE,
        nowMillis: Long = System.currentTimeMillis(),
        intervalMin: Int? = null,
    ) {
        if (type == Nudges.SOCIAL) {
            scheduleSocial(context)
            return
        }

        val prefs = Prefs(context)
        val interval = intervalMin ?: intervalMinForType(prefs, type)
        if (interval <= 0) {
            cancel(context, type)
            return
        }

        val nextFire = ScheduleMath.nextFire(
            nowMillis = nowMillis,
            zone = ZoneId.systemDefault(),
            windowStartMin = prefs.nudgeWindowStartMin,
            windowEndMin = prefs.nudgeWindowEndMin,
            intervalMin = interval,
        )
        val delayMillis = maxOf(0L, nextFire - nowMillis)

        val request = OneTimeWorkRequestBuilder<NudgeWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(KEY_TYPE to type))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(type),
            policy,
            request,
        )
    }

    /**
     * Cancels unique work for the given nudge [type].
     */
    fun cancel(context: Context, type: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(type))
    }

    /**
     * Enqueues unique 15-min periodic work for the social nudge.
     * Uses [ExistingPeriodicWorkPolicy.KEEP] by default.
     */
    fun scheduleSocial(
        context: Context,
        policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP,
    ) {
        val prefs = Prefs(context)
        if (prefs.nudgeSocialThresholdMin <= 0) {
            cancel(context, Nudges.SOCIAL)
            return
        }
        val request = PeriodicWorkRequestBuilder<SocialTimeWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            workName(Nudges.SOCIAL),
            policy,
            request,
        )
    }

    /**
     * Ensures all nudge types match current preferences (schedules enabled nudges with
     * [policy] and cancels disabled nudges). Defaults to [ExistingWorkPolicy.KEEP] so
     * app launches do not push pending nudge timers back.
     */
    fun ensureAll(
        context: Context,
        policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP,
    ) {
        val prefs = Prefs(context)
        val supportedTypes = listOf(Nudges.WATER, Nudges.WALK)
        for (type in supportedTypes) {
            if (intervalMinForType(prefs, type) > 0) {
                schedule(context, type, policy = policy)
            } else {
                cancel(context, type)
                Nudges.cancel(context, type)
            }
        }
        if (prefs.nudgeSocialThresholdMin > 0) {
            scheduleSocial(
                context,
                if (policy == ExistingWorkPolicy.REPLACE) ExistingPeriodicWorkPolicy.UPDATE
                else ExistingPeriodicWorkPolicy.KEEP,
            )
        } else {
            cancel(context, Nudges.SOCIAL)
            Nudges.cancel(context, Nudges.SOCIAL)
        }
    }

    /**
     * Re-schedules all enabled nudge types using [ExistingWorkPolicy.REPLACE].
     */
    fun rescheduleAll(context: Context) {
        ensureAll(context, ExistingWorkPolicy.REPLACE)
    }

    fun intervalMinForType(prefs: Prefs, type: String): Int = when (type) {
        Nudges.WATER -> prefs.nudgeWaterIntervalMin
        Nudges.WALK -> prefs.nudgeWalkIntervalMin
        Nudges.SOCIAL -> prefs.nudgeSocialThresholdMin
        else -> 0
    }
}

/**
 * Worker that posts a nudge notification and reschedules itself.
 */
class NudgeWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val type = inputData.getString(NudgeScheduler.KEY_TYPE) ?: return@withContext Result.success()

        val prefs = Prefs(applicationContext)
        val interval = NudgeScheduler.intervalMinForType(prefs, type)
        if (interval > 0) {
            val count = prefs.nudgeDone(type)
            val useGerman = prefs.isGermanUi
            val text = when (type) {
                Nudges.WATER -> applicationContext.getString(
                    if (useGerman) R.string.nudge_water_notification_de else R.string.nudge_water_notification,
                    count
                )
                Nudges.WALK -> applicationContext.getString(
                    if (useGerman) R.string.nudge_walk_notification_de else R.string.nudge_walk_notification,
                    count
                )
                else -> null
            }
            if (text != null && !shouldSkipWalk(prefs, type, interval)) {
                val doneAction = androidx.core.app.NotificationCompat.Action.Builder(
                    0,
                    applicationContext.getString(
                        if (useGerman) R.string.done_de else R.string.done
                    ),
                    NudgeActionReceiver.createDonePendingIntent(applicationContext, type),
                ).build()
                Nudges.post(applicationContext, type, text, doneAction)
            }

            // Reschedule self for the next interval
            NudgeScheduler.schedule(applicationContext, type)
        }
        Result.success()
    }

    /** True when Health Connect shows recent steps or exercise. Any read problem means post. */
    private suspend fun shouldSkipWalk(prefs: Prefs, type: String, intervalMin: Int): Boolean {
        if (type != Nudges.WALK || !prefs.healthConnectEnabled) return false
        return try {
            val now = java.time.Instant.now()
            val reading = HealthRepository(applicationContext, { prefs.healthConnectEnabled })
                .readWalkWindow(now, intervalMin) ?: return false
            WalkNudgePolicy.shouldSkip(
                steps = reading.steps,
                exerciseSessions = reading.exercise,
                windowStartMs = now.minusSeconds(intervalMin * 60L).toEpochMilli(),
                windowEndMs = now.toEpochMilli(),
            )
        } catch (_: Exception) {
            false
        }
    }
}
