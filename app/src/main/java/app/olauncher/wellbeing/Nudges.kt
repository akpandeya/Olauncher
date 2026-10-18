package app.olauncher.wellbeing

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import app.olauncher.R
import app.olauncher.helper.showMessageDialog

/**
 * Channel setup, posting, and constants for gentle nudges.
 */
object Nudges {
    const val CHANNEL_ID = "nudges"

    const val WATER = "water"
    const val WALK = "walk"
    const val SOCIAL = "social"
    const val QUIET = "quiet"

    fun notificationId(type: String): Int = when (type) {
        WATER -> 1001
        WALK -> 1002
        SOCIAL -> 1003
        QUIET -> 1004
        else -> type.hashCode()
    }

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        val name = context.getString(R.string.nudges_channel_name)
        val channel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW)
        manager.createNotificationChannel(channel)
    }

    @SuppressLint("MissingPermission")
    fun post(
        context: Context,
        type: String,
        text: CharSequence,
        actions: List<NotificationCompat.Action> = emptyList(),
    ) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            return
        }
        ensureChannel(context)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(NudgeActionReceiver.createDismissPendingIntent(context, type))

        for (action in actions) {
            builder.addAction(action)
        }

        try {
            manager.notify(notificationId(type), builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun post(
        context: Context,
        type: String,
        text: CharSequence,
        vararg actions: NotificationCompat.Action,
    ) {
        post(context, type, text, actions.toList())
    }

    fun cancel(context: Context, type: String) {
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(notificationId(type))
    }
}

/**
 * Handles requesting POST_NOTIFICATIONS permission with an explanatory dialog.
 * Registered in `onCreate` of the hosting fragment.
 */
class NotificationPermission(private val fragment: Fragment) {

    private var pendingCallback: ((Boolean) -> Unit)? = null

    private val launcher: ActivityResultLauncher<String> = fragment.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val cb = pendingCallback
        pendingCallback = null
        cb?.invoke(isGranted)
    }

    init {
        instances[fragment] = this
        fragment.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                instances.remove(fragment)
            }
        })
    }

    fun request(onResult: (Boolean) -> Unit): Boolean {
        return request(fragment, onResult)
    }

    fun request(targetFragment: Fragment, onResult: (Boolean) -> Unit): Boolean {
        val context = targetFragment.context ?: fragment.context ?: return false
        if (isGranted(context)) {
            onResult(true)
            return true
        }

        pendingCallback = onResult
        var actionClicked = false
        val dialog = context.showMessageDialog(
            R.string.nudges,
            R.string.notification_permission_message,
            R.string.allow,
        ) {
            actionClicked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        dialog.setOnDismissListener {
            if (!actionClicked && pendingCallback != null) {
                val cb = pendingCallback
                pendingCallback = null
                cb?.invoke(false)
            }
        }
        return false
    }

    companion object {
        private val instances = mutableMapOf<Fragment, NotificationPermission>()

        fun isGranted(context: Context): Boolean {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            val enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            return hasPermission && enabled
        }

        fun register(fragment: Fragment): NotificationPermission {
            return instances[fragment] ?: NotificationPermission(fragment)
        }

        fun request(fragment: Fragment, onResult: (Boolean) -> Unit): Boolean {
            val instance = instances[fragment] ?: register(fragment)
            return instance.request(fragment, onResult)
        }
    }
}
