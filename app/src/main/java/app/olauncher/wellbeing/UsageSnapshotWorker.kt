package app.olauncher.wellbeing

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.olauncher.data.Prefs
import app.olauncher.helper.appUsagePermissionGranted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class UsageSnapshotWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = Prefs(applicationContext)
        val store = UsageSnapshotStore(applicationContext.filesDir)
        val usageRepo = UsageRepository(applicationContext)
        val hasUsageAccess = applicationContext.appUsagePermissionGranted()

        fillUsageSnapshots(
            today = LocalDate.now().toEpochDay(),
            store = store,
            usageRepo = usageRepo,
            hasUsageAccess = hasUsageAccess,
            distractingPkgs = prefs.distractingApps,
            allowanceMin = prefs.pauseAllowanceMin,
            resolveAppLabel = { pkg ->
                val renamed = prefs.getAppRenameLabel(pkg)
                if (renamed.isNotBlank()) renamed else SocialTimeWorker.getAppLabel(applicationContext, pkg)
            }
        )

        Result.success()
    }

    companion object {
        const val WORK_NAME = "usage_snapshot"

        /** Runs the daily worker only while there are distracting apps; cancels it when the list is empty. */
        fun syncWithDistractingApps(context: Context, distractingApps: Set<String>) {
            if (distractingApps.isNotEmpty()) {
                enqueue(context)
            } else {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            }
        }

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<UsageSnapshotWorker>(24, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        suspend fun fillUsageSnapshots(
            today: Long,
            store: UsageSnapshotStore,
            usageRepo: UsageRepository,
            hasUsageAccess: Boolean,
            distractingPkgs: Set<String>,
            allowanceMin: Int,
            resolveAppLabel: (String) -> String,
        ) {
            val yesterday = today - 1
            val daysToProcess = mutableListOf<Long>()
            daysToProcess.add(yesterday)
            for (day in (today - 7) until yesterday) {
                val existing = store.get(day)
                if (existing == null || existing.distractingMinutes == null) {
                    daysToProcess.add(day)
                }
            }
            daysToProcess.sort()

            for (day in daysToProcess) {
                val snapshot = if (!hasUsageAccess) {
                    DaySnapshot(
                        epochDay = day,
                        distractingMinutes = null,
                        distractingOpens = null,
                        topApps = emptyList(),
                        overAllowance = false,
                    )
                } else {
                    val statsByPkg = usageRepo.foregroundMillisByPackageForDay(distractingPkgs, day)
                    val opens = usageRepo.openCountForDay(distractingPkgs, day)

                    val distractingMinutes = statsByPkg?.let { (it.values.sum() / 60_000L).toInt() }
                    val distractingOpens = opens
                    val overAllowance = distractingMinutes != null && allowanceMin > 0 && distractingMinutes >= allowanceMin

                    val topApps = if (statsByPkg != null) {
                        statsByPkg.filter { it.value > 0 }
                            .entries
                            .sortedByDescending { it.value }
                            .take(3)
                            .map { (pkg, millis) ->
                                resolveAppLabel(pkg) to (millis / 60_000L).toInt()
                            }
                    } else {
                        emptyList()
                    }

                    DaySnapshot(
                        epochDay = day,
                        distractingMinutes = distractingMinutes,
                        distractingOpens = distractingOpens,
                        topApps = topApps,
                        overAllowance = overAllowance,
                    )
                }
                store.upsert(snapshot)
            }
        }
    }
}
