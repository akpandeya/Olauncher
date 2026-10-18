package app.olauncher.quiet

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.olauncher.MainActivity
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.wellbeing.Nudges
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class ReleaseWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "quiet_release"
        val NOTIFICATION_ID = Nudges.notificationId(Nudges.QUIET)

        fun schedule(
            context: Context,
            policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE,
            nowMillis: Long = System.currentTimeMillis(),
        ) {
            val prefs = Prefs(context)
            if (!prefs.quietEnabled) {
                cancel(context)
                return
            }

            val times = QuietRelease.parseTimes(prefs.quietReleaseTimes)
            if (times.isEmpty()) {
                cancel(context)
                return
            }

            val nextFire = QuietRelease.nextRelease(
                nowMillis = nowMillis,
                zone = ZoneId.systemDefault(),
                times = times,
            )
            val delayMillis = maxOf(0L, nextFire - nowMillis)

            val request = OneTimeWorkRequestBuilder<ReleaseWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                policy,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        fun createNotificationPendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_NAVIGATE_TO, MainActivity.DESTINATION_TODAY)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                intent,
                flags,
            )
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = Prefs(applicationContext)
        if (!prefs.quietEnabled) {
            return@withContext Result.success()
        }

        val heldStore = HeldStore(applicationContext.filesDir)
        heldStore.pruneOlderThan(System.currentTimeMillis() - HeldStore.FORTY_EIGHT_HOURS_MS)
        val items = heldStore.all()

        if (items.isNotEmpty()) {
            val summaryText = QuietRelease.summary(items)
            postSummaryNotification(applicationContext, summaryText)
        }

        // Reschedule self for the next release time
        schedule(applicationContext, ExistingWorkPolicy.REPLACE)

        Result.success()
    }

    @SuppressLint("MissingPermission")
    private fun postSummaryNotification(context: Context, summaryText: String) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            return
        }
        Nudges.ensureChannel(context)

        val pendingIntent = createNotificationPendingIntent(context)

        val builder = NotificationCompat.Builder(context, Nudges.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(summaryText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summaryText))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            manager.notify(NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
        }
    }
}
