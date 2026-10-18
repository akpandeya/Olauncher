package app.olauncher.wellbeing

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
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

        // Hook for posting notifications per type (to be connected in task-02)
        when (type) {
            Nudges.WATER -> {
                // Hook: water nudge
            }
            Nudges.WALK -> {
                // Hook: walk nudge
            }
            Nudges.SOCIAL -> {
                // Hook: social nudge
            }
            else -> {
                // No types yet
            }
        }

        // Reschedule self for the next interval
        NudgeScheduler.schedule(applicationContext, type)
        Result.success()
    }
}
