package app.olauncher.quiet

import android.content.Context
import androidx.core.app.NotificationManagerCompat

object QuietAccess {
    fun isNotificationAccessGranted(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
}
