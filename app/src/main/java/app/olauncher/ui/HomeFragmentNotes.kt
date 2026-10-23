package app.olauncher.ui

import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import java.time.LocalDate
import app.olauncher.R
import app.olauncher.helper.Haptics
import app.olauncher.helper.Note
import app.olauncher.helper.NotesStore
import app.olauncher.helper.Notes
import app.olauncher.helper.UndoMessage
import app.olauncher.helper.createDialog

private fun HomeFragment.notesStore() = NotesStore({ prefs.notesJson }, { prefs.notesJson = it })

/** Shows the oldest open note as one line, or hides the line when there is none or the setting is off. */
internal fun HomeFragment.populateNoteLine() {
    if (_binding == null) return
    val all = if (prefs.homeNotes) notesStore().all() else emptyList()
    val today = LocalDate.now()
    val note = Notes.top(all, today)
    val more = Notes.moreText(Notes.moreCount(all, today), getString(R.string.note_home_more))
    val text = note?.let { getString(R.string.note_home_line, it.text) + more?.let { m -> "  $m" }.orEmpty() }
    noteMoreStart = if (text != null && more != null) text.length - more.length else -1
    // Skip unchanged text: the line is a live region and would be re-announced every second.
    if (binding.noteLine.text.toString() != text.orEmpty()) binding.noteLine.text = text
    binding.noteLine.isVisible = text != null
}

/** Tap on the note line: on the "+N more" suffix open Today, else mark the shown note done. */
internal fun HomeFragment.onNoteLineTap() {
    val line = binding.noteLine
    val layout = line.layout
    val start = noteMoreStart
    if (start >= 0 && layout != null) {
        val x = (noteLineTouchX - line.totalPaddingLeft + line.scrollX).coerceAtLeast(0f)
        val row = layout.getLineForVertical((noteLineTouchY - line.totalPaddingTop + line.scrollY).toInt().coerceAtLeast(0))
        if (layout.getOffsetForHorizontal(row, x) >= start) {
            openToday()
            return
        }
    }
    markTopNoteDone()
}

/** Tap on the note line: mark the shown note done, with Undo, and show the next one. */
internal fun HomeFragment.markTopNoteDone() {
    val store = notesStore()
    val note = store.top(LocalDate.now()) ?: return
    store.markDone(note.id)
    populateNoteLine()
    UndoMessage.show(activity, getString(R.string.note_marked_done)) {
        store.markDone(note.id, done = false)
        populateNoteLine()
    }
}

/** Long-press on the note line: all open notes, each with Done and Delete. */
internal fun HomeFragment.showNotesDialog() {
    val ctx = context ?: return
    val notes = notesStore().open()
    if (notes.isEmpty()) return
    val dialog = ctx.createDialog(R.string.notes_title, action = 0) { parent ->
        val context = parent.context
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        notes.forEach { column.addView(noteRow(it)) }
        ScrollView(context).apply {
            addView(column)
            layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }
    notesDialog?.dismiss()
    notesDialog = dialog
    dialog.setOnDismissListener { if (notesDialog === dialog) notesDialog = null }
    dialog.showRespectingStatusBar()
}

private fun HomeFragment.noteRow(note: Note): View {
    val context = requireContext()
    val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    row.addView(
        actionText(listOfNotNull(note.text, Notes.dateLabel(note)).joinToString(" · ")).apply { maxLines = 2 },
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    )
    row.addView(actionText(getString(R.string.done)).apply {
        setOnClickListener {
            Haptics.perform(it, Haptics.Kind.MENU)
            notesStore().markDone(note.id)
            afterNoteChange()
        }
    })
    row.addView(actionText(getString(R.string.note_delete)).apply {
        setOnClickListener {
            Haptics.perform(it, Haptics.Kind.MENU)
            notesStore().delete(note.id)
            afterNoteChange()
        }
    })
    return row
}

private fun HomeFragment.actionText(label: String): TextView {
    val context = requireContext()
    return TextView(context).apply {
        TextViewCompat.setTextAppearance(this, R.style.TextSmall)
        val ripple = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
        setBackgroundResource(ripple.resourceId)
        minHeight = (48 * resources.displayMetrics.density).toInt()
        val pad = (8 * resources.displayMetrics.density).toInt()
        setPadding(pad, 0, pad, 0)
        gravity = Gravity.CENTER_VERTICAL
        text = label
    }
}

/** Refreshes the home line and the open dialog after a change made in the list. */
private fun HomeFragment.afterNoteChange() {
    populateNoteLine()
    notesDialog?.dismiss()
    showNotesDialog()
}
