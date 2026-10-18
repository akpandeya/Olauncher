package app.olauncher.quiet

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.olauncher.data.Prefs

class QuietListenerService : NotificationListenerService() {

    private lateinit var prefs: Prefs
    private lateinit var heldStore: HeldStore

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        heldStore = HeldStore(filesDir)
        heldStore.pruneOlderThan(System.currentTimeMillis() - HeldStore.FORTY_EIGHT_HOURS_MS)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (!::prefs.isInitialized) prefs = Prefs(this)
        if (!::heldStore.isInitialized) heldStore = HeldStore(filesDir)

        if (!prefs.quietEnabled) return

        val notification = sbn.notification ?: return
        val flags = notification.flags
        val isOngoing = sbn.isOngoing
        val isForeground = (flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
        val isGroupSummary = (flags and Notification.FLAG_GROUP_SUMMARY) != 0
        val category = notification.category

        val shouldHold = HoldPolicy.shouldHold(
            pkg = sbn.packageName,
            distracting = prefs.distractingApps,
            ownPkg = packageName,
            category = category,
            isOngoing = isOngoing,
            isForegroundService = isForeground,
            isGroupSummary = isGroupSummary
        )

        if (shouldHold) {
            val label = try {
                val appInfo = packageManager.getApplicationInfo(sbn.packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                sbn.packageName
            }
            val title = notification.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text = (notification.extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: notification.extras?.getCharSequence(Notification.EXTRA_TEXT))
                ?.toString() ?: ""

            val item = HeldItem(
                key = sbn.key,
                pkg = sbn.packageName,
                label = label,
                title = title,
                text = text,
                postTime = sbn.postTime
            )
            heldStore.add(item)
            try {
                cancelNotification(sbn.key)
            } catch (_: Exception) {
                // If listener service connection is temporarily lost
            }

            cancelGroupSummaryIfAllChildrenHeld(sbn.packageName, notification.group)
        } else if (isGroupSummary && sbn.packageName in prefs.distractingApps) {
            cancelGroupSummaryIfAllChildrenHeld(sbn.packageName, notification.group)
        }
    }

    private fun cancelGroupSummaryIfAllChildrenHeld(pkg: String, group: String?) {
        if (group == null) return
        try {
            val active = activeNotifications ?: return
            val pkgGroupNotifications = active.filter {
                it.packageName == pkg && it.notification?.group == group
            }
            val nonSummary = pkgGroupNotifications.filter {
                ((it.notification?.flags ?: 0) and Notification.FLAG_GROUP_SUMMARY) == 0
            }
            if (nonSummary.isEmpty()) {
                pkgGroupNotifications.filter {
                    ((it.notification?.flags ?: 0) and Notification.FLAG_GROUP_SUMMARY) != 0
                }.forEach { summary ->
                    cancelNotification(summary.key)
                }
            }
        } catch (_: Exception) {
            // Ignore
        }
    }
}
