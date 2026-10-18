package app.olauncher.wellbeing

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
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
     * Ensures all enabled nudge types are scheduled using [ExistingWorkPolicy.KEEP].
     */
    fun ensureAll(context: Context) {
        val prefs = Prefs(context)
        val supportedTypes = listOf(Nudges.WATER, Nudges.WALK)
        for (type in supportedTypes) {
            if (intervalMinForType(prefs, type) > 0) {
                schedule(context, type, policy = ExistingWorkPolicy.KEEP)
            }
        }
    }

    /**
     * Re-schedules all enabled nudge types using [ExistingWorkPolicy.REPLACE].
     */
    fun rescheduleAll(context: Context) {
        val prefs = Prefs(context)
        val supportedTypes = listOf(Nudges.WATER, Nudges.WALK)
        for (type in supportedTypes) {
            if (intervalMinForType(prefs, type) > 0) {
                schedule(context, type, policy = ExistingWorkPolicy.REPLACE)
            }
        }
    }

    fun intervalMinForType(prefs: Prefs, type: String): Int = when (type) {
        Nudges.WATER -> prefs.nudgeWaterIntervalMin
        Nudges.WALK -> prefs.nudgeWalkIntervalMin
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
            val text = when (type) {
                Nudges.WATER -> applicationContext.getString(R.string.nudge_water_notification, count)
                Nudges.WALK -> applicationContext.getString(R.string.nudge_walk_notification, count)
                else -> null
            }
            if (text != null) {
                val doneAction = androidx.core.app.NotificationCompat.Action.Builder(
                    0,
                    applicationContext.getString(app.olauncher.R.string.done),
                    NudgeActionReceiver.createDonePendingIntent(applicationContext, type),
                ).build()
                Nudges.post(applicationContext, type, text, doneAction)
            }

            // Reschedule self for the next interval
            NudgeScheduler.schedule(applicationContext, type)
        }
        Result.success()
    }
}
