package app.olauncher.helper

import android.app.Activity
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import app.olauncher.R

/**
 * Short message pinned near the bottom (thumb reach) with an Undo action.
 * One at a time: a new message replaces the old one. Goes away on its own.
 */
object UndoMessage {

    private const val DURATION_MS = 5000L

    private var current: View? = null
    private var dismiss: Runnable? = null

    fun show(activity: Activity?, message: String, onUndo: () -> Unit) {
        val host = activity?.findViewById<ViewGroup>(android.R.id.content) ?: return
        remove()
        val view = LayoutInflater.from(host.context).inflate(R.layout.view_undo_message, host, false)
        view.findViewById<TextView>(R.id.tvUndoMessage).text = message
        val gone = Runnable { remove() }
        view.findViewById<View>(R.id.tvUndoAction).setOnClickListener {
            remove()
            onUndo()
        }
        view.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM,
        ).apply {
            val side = host.resources.getDimensionPixelSize(R.dimen.popup_card_screen_margin)
            val bottom = host.resources.getDimensionPixelSize(R.dimen.undo_message_bottom_margin)
            setMargins(side, 0, side, bottom)
        }
        host.addView(view)
        current = view
        dismiss = gone
        view.postDelayed(gone, DURATION_MS)
    }

    private fun remove() {
        val view = current ?: return
        dismiss?.let { view.removeCallbacks(it) }
        (view.parent as? ViewGroup)?.removeView(view)
        current = null
        dismiss = null
    }
}
