package app.olauncher.ui.notes

import android.app.DatePickerDialog
import android.content.Context
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import app.olauncher.R
import app.olauncher.databinding.DialogAddNoteBinding
import app.olauncher.helper.DraftResult
import app.olauncher.helper.Haptics
import app.olauncher.helper.NoteDraft
import app.olauncher.helper.NoteDraftRules
import app.olauncher.helper.Notes
import app.olauncher.helper.OlDialog
import app.olauncher.helper.createDialog
import app.olauncher.helper.showToast
import app.olauncher.ui.today.cards.NotesCard
import app.olauncher.ui.today.ImeInset
import java.time.LocalDate
import java.time.ZonedDateTime

/** The one Add note sheet, shown from the Today card and from the Notes screen. */
object AddNoteSheet {

    /** [onSaved] runs after a note was saved, so the caller can redraw. */
    fun show(context: Context, onSaved: () -> Unit) {
        val dialog = OlDialog(context)
        val b = DialogAddNoteBinding.inflate(LayoutInflater.from(dialog.context))
        var date: LocalDate? = null

        fun draft() = NoteDraft(
            title = b.etNoteTitle.text?.toString().orEmpty(),
            body = b.etNoteBody.text?.toString().orEmpty(),
            checklist = b.switchChecklist.isChecked,
            showFrom = date,
            pinned = b.switchPin.isChecked,
        )

        fun renderDate() {
            b.tvRemindDate.text = date?.let { Notes.dateLabel(it) } ?: context.getString(R.string.note_add_remind_none)
        }

        fun cancel() {
            if (!NoteDraftRules.hasTypedText(draft())) return dialog.dismiss()
            val confirm = context.createDialog(
                title = R.string.note_discard_title,
                action = R.string.note_discard_action,
                message = R.string.note_discard_message,
                onAction = { dialog.dismiss() },
            )
            confirm.showRespectingStatusBar()
        }

        b.switchChecklist.setOnCheckedChangeListener { _, on ->
            val before = b.etNoteBody.text?.toString().orEmpty()
            val after = NoteDraftRules.switchChecklist(before, on)
            if (after != before) {
                b.etNoteBody.setText(after)
                b.etNoteBody.setSelection(after.length)
            }
        }
        b.rowRemind.setOnClickListener { pickDate(context, date) { date = it; renderDate() } }
        renderDate()
        b.btnNoteCancel.setOnClickListener { cancel() }
        b.btnNoteSave.setOnClickListener {
            when (val result = NotesCard.store(context).addDraft(draft(), System.currentTimeMillis(), ZonedDateTime.now())) {
                is DraftResult.Added -> {
                    Haptics.tick(it)
                    dialog.dismiss()
                    onSaved()
                }
                DraftResult.Empty -> context.showToast(context.getString(R.string.note_add_needs_text))
                is DraftResult.BadDate -> context.showToast(context.getString(R.string.command_cant_read, result.token))
            }
        }

        dialog.setView(b.root)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                cancel()
                true
            } else keyCode == KeyEvent.KEYCODE_BACK
        }
        // Edge-to-edge app: the dialog window is not resized for the keyboard, and
        // a listener on the sheet's own view never sees the IME inset (the window
        // decor consumes it first). Read the raw insets on the decor view, pass them
        // on unchanged, and raise the bottom-anchored window by the keyboard height.
        // The reported keyboard overlap is measured against this window, which the
        // lift itself moves — recomputing the lift on every dispatch oscillates
        // (partial lifts, or an overshoot that parks the sheet off screen). So the
        // ride is applied once per keyboard visibility change: the first dispatch
        // after the keyboard appears still sees the un-lifted window, and later
        // dispatches for the same keyboard state are ignored.
        var imeShown = false
        dialog.window?.apply {
            setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            decorView.setOnApplyWindowInsetsListener { decor, insets ->
                val imeVisible = insets.isVisible(WindowInsets.Type.ime())
                if (imeVisible != imeShown) {
                    imeShown = imeVisible
                    val density = context.resources.displayMetrics.density
                    // The screen size must come from the display: the decor root's
                    // height is the sheet window's own (wrap-content) height, which
                    // would collapse the scroll cap to its minimum.
                    val ride = ImeInset.ride(
                        imeVisible = imeVisible,
                        imeBottomPx = insets.getInsets(WindowInsets.Type.ime()).bottom,
                        navBottomPx = insets.getInsets(WindowInsets.Type.navigationBars()).bottom,
                        statusTopPx = insets.getInsets(WindowInsets.Type.statusBars()).top,
                        screenHeightPx = context.resources.displayMetrics.heightPixels,
                        density = density,
                    )
                    b.scrollNote.maxHeightPx = ride.scrollCap
                    val params = attributes
                    if (params.y != ride.lift) {
                        params.y = ride.lift
                        attributes = params
                    }
                }
                decor.onApplyWindowInsets(insets)
            }
        }
        dialog.showRespectingStatusBar()
        b.etNoteTitle.post {
            b.etNoteTitle.requestFocus()
            (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.showSoftInput(b.etNoteTitle, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    /** A date picker that starts on [current] (or today) and offers "No date" to clear the choice. */
    private fun pickDate(context: Context, current: LocalDate?, onPicked: (LocalDate?) -> Unit) {
        val start = current ?: LocalDate.now()
        val picker = DatePickerDialog(
            context,
            { _, year, month, day -> onPicked(LocalDate.of(year, month + 1, day)) },
            start.year, start.monthValue - 1, start.dayOfMonth,
        )
        picker.setButton(DatePickerDialog.BUTTON_NEUTRAL, context.getString(R.string.note_add_remind_none)) { _, _ -> onPicked(null) }
        picker.show()
    }
}
