package app.olauncher.ui.notes

import android.animation.ObjectAnimator
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import app.olauncher.R
import app.olauncher.helper.MotionPolicy
import app.olauncher.helper.Note
import app.olauncher.helper.Notes
import app.olauncher.helper.getColorFromAttr

/** One look for a checklist note in the Today card, the Notes screen and the note view. */
object ChecklistStyle {
    private const val MAX = 1000
    private const val TRACK_ALPHA = 0x33
    private const val DIVIDER_ALPHA = 0x40

    /** Sets the "N of M" count (accent colour) and the thin progress bar; both hide for a plain note or an empty checklist. */
    fun bindProgress(note: Note, count: TextView, bar: ProgressBar, animate: Boolean = false) {
        val context = count.context
        val show = Notes.showsProgressBar(note)
        count.visibility = if (show) View.VISIBLE else View.GONE
        bar.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) return
        val accent = context.getColorFromAttr(R.attr.accentColor)
        count.text = Notes.countText(note, context.getString(R.string.note_checklist_count))
        count.setTextColor(accent)
        bar.max = MAX
        val target = (Notes.progressFraction(note) * MAX).toInt()
        val animatorsEnabled = MotionPolicy.isAnimationsEnabled(context)
        val duration = MotionPolicy.durationMs(MotionPolicy.TODAY_DURATION_MS, animatorsEnabled)
        if (animate && animatorsEnabled && duration > 0L) {
            ObjectAnimator.ofInt(bar, "progress", 0, target).apply {
                this.duration = duration
                interpolator = DecelerateInterpolator()
                start()
            }
        } else {
            bar.progress = target
        }
        bar.progressTintList = ColorStateList.valueOf(accent)
        bar.progressBackgroundTintList = ColorStateList.valueOf((accent and 0x00FFFFFF) or (TRACK_ALPHA shl 24))
    }

    /** Draws a 1px line between the rows of [panel]. */
    fun addDividers(panel: LinearLayout) {
        val ink = panel.context.getColorFromAttr(R.attr.inkMuted)
        panel.dividerDrawable = GradientDrawable().apply {
            setColor((ink and 0x00FFFFFF) or (DIVIDER_ALPHA shl 24))
            setSize(0, 1)
        }
        panel.showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
    }
}
