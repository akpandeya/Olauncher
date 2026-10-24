package app.olauncher.ui.notes

import android.app.DatePickerDialog
import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import app.olauncher.R
import app.olauncher.databinding.DialogAddNoteBinding
import app.olauncher.helper.DraftResult
import app.olauncher.helper.Haptics
import app.olauncher.helper.NoteDraft
import app.olauncher.helper.NoteDraftRules
import app.olauncher.helper.Notes
import app.olauncher.helper.OlDialog
import app.olauncher.helper.createDialog
import app.olauncher.helper.dpToPx
import app.olauncher.helper.showToast
import app.olauncher.ui.today.cards.NotesCard
import app.olauncher.ui.today.cards.WordsCard
import java.time.LocalDate
import java.time.ZonedDateTime

/** The one Add note sheet, shown from the Today card and from the Notes screen. */
object AddNoteSheet {

    /** [onSaved] runs after a note was saved, so the caller can redraw. */
    fun show(context: Context, onSaved: () -> Unit) {
        val dialog = OlDialog(context)
        val b = DialogAddNoteBinding.inflate(LayoutInflater.from(dialog.context))
        var date: LocalDate? = null
        val selectedLists = LinkedHashSet<String>()

        fun draft() = NoteDraft(
            title = b.etNoteTitle.text?.toString().orEmpty(),
            body = b.etNoteBody.text?.toString().orEmpty(),
            checklist = b.switchChecklist.isChecked,
            showFrom = date,
            pinned = b.switchPin.isChecked,
            lists = selectedLists.toList(),
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
        bindListChips(context, b, selectedLists)
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
        dialog.window?.apply {
            setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
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

    /** One tickable chip per existing list; the whole block stays hidden when there are none. */
    private fun bindListChips(context: Context, b: DialogAddNoteBinding, selected: MutableSet<String>) {
        val lists = WordsCard.getStore(context).lists()
        val visible = if (lists.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        b.tvListsHeading.visibility = visible
        b.noteListChips.visibility = visible
        for (list in lists) {
            val chip = newChip(context, list.name)
            chip.setOnClickListener {
                it.isSelected = !it.isSelected
                if (it.isSelected) selected.add(list.id) else selected.remove(list.id)
            }
            b.noteListChips.addView(chip)
        }
    }

    /** A plain TextView chip: a Material Chip throws under the app's AppCompat theme. */
    private fun newChip(context: Context, label: String): TextView = TextView(context).apply {
        text = label
        setTextColor(TypedValue().also { context.theme.resolveAttribute(R.attr.primaryColor, it, true) }.data)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(R.dimen.text_small))
        gravity = Gravity.CENTER
        minHeight = 48.dpToPx()
        setPadding(12.dpToPx(), 0, 12.dpToPx(), 0)
        isClickable = true
        isFocusable = true
        background = ContextCompat.getDrawable(context, R.drawable.today_chip_selectable)
    }
}
