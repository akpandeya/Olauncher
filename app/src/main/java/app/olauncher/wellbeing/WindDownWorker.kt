package app.olauncher.wellbeing

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.olauncher.data.Prefs
import app.olauncher.helper.HomeSets
import app.olauncher.helper.PhotoWallpaper
import app.olauncher.helper.setPlainWallpaperByTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Pure decisions for the grey wallpaper. Times are minutes since local midnight. */
object WindDownGrey {
    private const val DAY_MINUTES = 24 * 60
    private const val MINUTE_MILLIS = 60_000L

    /** What the wallpaper should be right now. */
    fun shouldBeGrey(enabled: Boolean, windDownOn: Boolean, nowMinutes: Int, start: Int, end: Int): Boolean =
        enabled && windDownOn && WindDown.isActive(nowMinutes, start, end)

    /** The wallpaper needs a change only when the wanted state differs from the applied one. */
    fun needsChange(wantGrey: Boolean, appliedGrey: Boolean): Boolean = wantGrey != appliedGrey

    data class Next(val delayMillis: Long, val toGrey: Boolean)

    /**
     * The next start/end boundary after [nowMillisOfDay], or null when nothing should be scheduled
     * (feature off, or an empty window). [nowMillisOfDay] is millis since local midnight.
     */
    fun nextBoundary(
        enabled: Boolean,
        windDownOn: Boolean,
        nowMillisOfDay: Long,
        start: Int,
        end: Int,
    ): Next? {
        if (!enabled || !windDownOn) return null
        val s = HomeSets.normalize(start)
        val e = HomeSets.normalize(end)
        if (s == e) return null
        val nowMinutes = HomeSets.normalize((nowMillisOfDay / MINUTE_MILLIS).toInt())
        val activeNow = WindDown.isActive(nowMinutes, s, e)
        val target = if (activeNow) e else s
        val minutesAhead = ((target - nowMinutes) % DAY_MINUTES + DAY_MINUTES) % DAY_MINUTES
        val delay = minutesAhead * MINUTE_MILLIS - (nowMillisOfDay % MINUTE_MILLIS)
        // Landing exactly on the boundary means the next one is a full day away.
        return Next(if (delay <= 0L) delay + DAY_MINUTES * MINUTE_MILLIS else delay, toGrey = !activeNow)
    }
}

/** One-shot work that greys the wallpaper when wind-down starts and restores it when it ends. */
class WindDownWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        sync(applicationContext)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "wind_down_grey"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        /** Fire-and-forget [sync], for resume-time checks. */
        fun syncAsync(context: Context) {
            val appContext = context.applicationContext
            scope.launch { sync(appContext) }
        }

        /** Applies the wanted wallpaper if it differs from what is applied, then schedules the next boundary. */
        suspend fun sync(context: Context) = withContext(Dispatchers.IO) {
            val prefs = Prefs(context)
            val now = java.time.LocalTime.now()
            val nowMinutes = HomeSets.minutesOfDay(now.hour, now.minute)
            val want = WindDownGrey.shouldBeGrey(
                prefs.windDownGrey, prefs.windDown, nowMinutes, prefs.windDownStart, prefs.windDownEnd,
            )
            if (WindDownGrey.needsChange(want, prefs.windDownGreyApplied)) {
                PhotoWallpaper.withEditLock {
                    if (prefs.photoWallpaperCount > 0) {
                        // Greyed copy or the original, never modifying the stored photos.
                        if (!PhotoWallpaper.applyCurrent(context, prefs, grey = want)) return@withEditLock
                    } else {
                        // The plain black/white theme wallpaper has no colour to remove.
                        setPlainWallpaperByTheme(context, prefs.appTheme)
                    }
                    prefs.windDownGreyApplied = want
                }
            }
            val wantSystemGrey = WindDownGrey.shouldBeGrey(
                prefs.windDownSystemGrey, prefs.windDown, nowMinutes, prefs.windDownStart, prefs.windDownEnd,
            )
            SystemGrey.sync(context, prefs, wantSystemGrey)
            schedule(context, prefs, now.toNanoOfDay() / 1_000_000L)
        }

        private fun schedule(context: Context, prefs: Prefs, nowMillisOfDay: Long) {
            val manager = WorkManager.getInstance(context)
            val next = WindDownGrey.nextBoundary(
                prefs.windDownGrey || prefs.windDownSystemGrey, prefs.windDown, nowMillisOfDay,
                prefs.windDownStart, prefs.windDownEnd,
            )
            if (next == null) {
                manager.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = OneTimeWorkRequestBuilder<WindDownWorker>()
                .setInitialDelay(next.delayMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build()
            manager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
