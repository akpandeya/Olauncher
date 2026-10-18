package app.olauncher.wellbeing

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.olauncher.data.Prefs

/**
 * Handles actions from gentle nudge notifications.
 */
class NudgeActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action == ACTION_DONE || action == "ACTION_DONE") {
            val type = intent.getStringExtra(EXTRA_TYPE) ?: intent.getStringExtra("type") ?: return
            val prefs = Prefs(context)
            prefs.incrementNudgeDone(type)
            Nudges.cancel(context, type)
        } else if (action == ACTION_DISMISS || action == "ACTION_DISMISS") {
            val type = intent.getStringExtra(EXTRA_TYPE) ?: intent.getStringExtra("type") ?: return
            Nudges.cancel(context, type)
        }
    }

    companion object {
        const val ACTION_DONE = "app.olauncher.action.NUDGE_DONE"
        const val ACTION_DISMISS = "app.olauncher.action.NUDGE_DISMISS"
        const val EXTRA_TYPE = "type"

        fun createDonePendingIntent(context: Context, type: String): PendingIntent {
            val intent = Intent(context, NudgeActionReceiver::class.java).apply {
                action = ACTION_DONE
                putExtra(EXTRA_TYPE, type)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getBroadcast(
                context,
                Nudges.notificationId(type),
                intent,
                flags,
            )
        }

        fun createDismissPendingIntent(context: Context, type: String): PendingIntent {
            val intent = Intent(context, NudgeActionReceiver::class.java).apply {
                action = ACTION_DISMISS
                putExtra(EXTRA_TYPE, type)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getBroadcast(
                context,
                Nudges.notificationId(type) + 1000,
                intent,
                flags,
            )
        }
    }
}
