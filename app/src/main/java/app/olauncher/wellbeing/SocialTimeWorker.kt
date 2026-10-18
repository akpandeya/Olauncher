package app.olauncher.wellbeing

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.helper.appUsagePermissionGranted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Worker that periodically checks distracting-app foreground usage and posts a
 * gentle "Put the phone down" nudge if threshold and repeat conditions are met.
 */
class SocialTimeWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = Prefs(applicationContext)
        val threshold = prefs.nudgeSocialThresholdMin
        // Returns early when off
        if (threshold <= 0) {
            return@withContext Result.success()
        }

        // Returns early when usage access is missing
        if (!applicationContext.appUsagePermissionGranted()) {
            return@withContext Result.success()
        }

        // Packages considered distracting
        val distractingPkgs = if (prefs.hasSavedDistractingApps()) {
            prefs.distractingApps
        } else {
            DistractingApps.DEFAULT_PACKAGES
        }
        if (distractingPkgs.isEmpty()) {
            return@withContext Result.success()
        }

        val usageRepo = UsageRepository(applicationContext)

        // Returns early when no DistractingApps package had an ACTIVITY_RESUMED in the last 5 min
        val inForegroundRecently = usageRepo.hasRecentResume(distractingPkgs, UsageRepository.RECENT_RESUME_WINDOW_MS)
        if (!inForegroundRecently) {
            return@withContext Result.success()
        }

        // Total foreground time today for distracting apps
        val foregroundMillis = usageRepo.foregroundMillisToday(distractingPkgs) ?: 0L
        val minutesToday = (foregroundMillis / 60_000L).toInt()

        val repeat = if (prefs.nudgeSocialRepeatMin > 0) prefs.nudgeSocialRepeatMin else 15
        val lastBucket = prefs.nudgeSocialLastBucket

        val newBucket = SocialNudgePolicy.shouldNudge(
            minutesToday = minutesToday,
            threshold = threshold,
            repeat = repeat,
            lastBucket = lastBucket,
            inForegroundRecently = true,
        )

        if (newBucket != null) {
            prefs.nudgeSocialLastBucket = newBucket

            val statsByPkg = usageRepo.foregroundMillisByPackageToday(distractingPkgs)
            val appNames = resolveDistractingAppNames(applicationContext, distractingPkgs, statsByPkg)
            val useGerman = prefs.isGermanUi
            val text = if (appNames.isNotBlank()) {
                applicationContext.getString(
                    if (useGerman) R.string.nudge_social_notification_de else R.string.nudge_social_notification,
                    minutesToday,
                    appNames,
                )
            } else {
                applicationContext.getString(
                    if (useGerman) R.string.nudge_social_notification_no_apps_de else R.string.nudge_social_notification_no_apps,
                    minutesToday,
                )
            }

            // Post nudge notification with no action buttons
            Nudges.post(applicationContext, Nudges.SOCIAL, text)
        }

        Result.success()
    }

    companion object {
        fun resolveDistractingAppNames(
            context: Context,
            distractingPkgs: Set<String>,
            statsByPkg: Map<String, Long>?,
        ): String {
            val prefs = Prefs(context)
            val usedPackages = if (statsByPkg != null && statsByPkg.isNotEmpty()) {
                statsByPkg.filter { it.value > 0 }.entries
                    .sortedByDescending { it.value }
                    .map { it.key }
            } else {
                emptyList()
            }

            val targetPkgs = if (usedPackages.isNotEmpty()) usedPackages else distractingPkgs.toList()
            val labels = targetPkgs.map { pkg ->
                val renamed = prefs.getAppRenameLabel(pkg)
                if (renamed.isNotBlank()) renamed else getAppLabel(context, pkg)
            }.filter { it.isNotBlank() }.distinct()

            return labels.joinToString(", ")
        }

        fun getAppLabel(context: Context, packageName: String): String {
            return try {
                val pm = context.packageManager
                val appInfo = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                when (packageName) {
                    "com.instagram.android" -> "Instagram"
                    "com.google.android.youtube" -> "YouTube"
                    "com.zhiliaoapp.musically" -> "TikTok"
                    "com.reddit.frontpage" -> "Reddit"
                    "com.twitter.android" -> "Twitter"
                    "com.facebook.katana" -> "Facebook"
                    "com.snapchat.android" -> "Snapchat"
                    else -> packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
                }
            }
        }
    }
}
