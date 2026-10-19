package app.olauncher.helper

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.olauncher.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class WallpaperRotateWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = Prefs(applicationContext)
        val count = prefs.photoWallpaperCount
        if (count <= 0) return@withContext Result.success()

        val today = PhotoWallpaper.today()
        val now = System.currentTimeMillis()
        val lastChanged = Rotation.resolveLastChanged(0L, prefs.photoWallpaperLastDay, today, now)
        val step = Rotation.step(prefs.photoWallpaperIndex, count, now, lastChanged, RotationInterval.DAILY)
        if (step.nextIndex != null) {
            prefs.photoWallpaperIndex = step.nextIndex
            prefs.photoWallpaperLastDay = today
        }

        val success = PhotoWallpaper.applyCurrent(applicationContext, prefs)
        if (success) Result.success() else Result.retry()
    }

    companion object {
        const val WORK_NAME = "photo_wallpaper"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<WallpaperRotateWorker>(24, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun runOnce(context: Context) {
            val request = OneTimeWorkRequestBuilder<WallpaperRotateWorker>()
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
