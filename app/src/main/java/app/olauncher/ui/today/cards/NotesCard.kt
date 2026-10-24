package app.olauncher.ui.today.cards

import android.graphics.Paint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.CheckBox
import androidx.core.view.ViewCompat
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.databinding.CardNotesBinding
import app.olauncher.helper.Haptics
import app.olauncher.helper.Notes
import app.olauncher.helper.NotesStore
import app.olauncher.helper.showMessageDialog
import app.olauncher.ui.notes.AddNoteSheet
import app.olauncher.ui.notes.ChecklistStyle
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.showPopupMenu
import app.olauncher.ui.today.TodayCard
import java.time.LocalDate
import java.time.ZoneId

/** Full-width card: the open count, the main note, up to two more, the next dated note and a button that opens the Add note sheet. */
object NotesCard : TodayCard {

    const val KEY = "notes"
    private const val MENU_PIN = 1
    private const val MENU_DELETE = 2
    private const val MENU_TICK = 3

    override val key: String = KEY
    override val titleRes: Int = R.string.notes_card_title
    override val emoji: String = "📝"
    override val drawsOwnHeader: Boolean = true

    fun store(context: Context): NotesStore {
        val prefs = Prefs(context)
        return NotesStore({ prefs.notesJson }, { prefs.notesJson = it })
    }

    override fun createView(inflater: LayoutInflater, parent: ViewGroup, fragment: Fragment): View {
        val b = CardNotesBinding.inflate(inflater, parent, false)
        ViewCompat.setAccessibilityHeading(b.tvCardTitle, true)
        // The card body, the title and the note line all open the Notes screen. Only a tap on an item's box ticks.
        val open = View.OnClickListener {
            Haptics.tick(it)
            val nav = fragment.findNavController()
            if (nav.currentDestination?.id == R.id.todayFragment) nav.navigate(R.id.action_todayFragment_to_notesFragment)
        }
        b.root.setOnClickListener(open)
        b.tvCardTitle.setOnClickListener(open)
        b.tvNote.setOnClickListener(open)
        b.tvMore1.setOnClickListener(open)
        b.tvMore2.setOnClickListener(open)
        b.tvNextDated.setOnClickListener(open)
        b.btnAddNote.setOnClickListener {
            Haptics.tick(it)
            AddNoteSheet.show(it.context) {
                b.etSearchNotes.setText("") // re-renders through the text watcher when it had text
                render(b)
            }
        }
        b.etSearchNotes.doOnTextChanged { _, _, _, _ -> render(b) }
        b.etSearchNotes.setOnEditorActionListener { v, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener false
            v.hideKeyboard()
            true
        }
        render(b)
        b.root.tag = b
        return b.root
    }

    override fun onResume(view: View) {
        (view.tag as? CardNotesBinding)?.let { render(it) }
    }

    private fun render(b: CardNotesBinding) {
        val context = b.root.context
        val store = store(context)
        val all = store.all()
        b.tvCardTitle.text = context.getString(R.string.notes_card_count_title, Notes.open(all).size)
        val searching = Notes.showSearch(Notes.open(all).size)
        b.etSearchNotes.visibility = if (searching) View.VISIBLE else View.GONE
        val query = if (searching) b.etSearchNotes.text.toString() else ""
        // The main note is the featured one showing now (or the best match while searching). A tap opens the
        // Notes screen and never ticks or finishes anything; long-press opens a menu (Tick next, Pin/Unpin,
        // Delete). A pinned note is shown first.
        val today = LocalDate.now()
        val list = Notes.cardList(all, today)
        val lines = Notes.cardLines(if (query.isBlank()) list else Notes.search(list, query), today)
        val latest = lines.main
        renderExtras(b, lines, today)
        b.itemsPanel.removeAllViews()
        ChecklistStyle.addDividers(b.itemsPanel)
        if (latest == null) {
            b.tvNote.setText(if (query.isBlank()) R.string.notes_card_empty else R.string.notes_search_empty)
            b.tvNote.isLongClickable = false
            b.tvNote.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
            b.tvNoteDate.visibility = View.GONE
            b.tvNoteCount.visibility = View.GONE
            b.pbNote.visibility = View.GONE
            return
        }
        b.tvNote.text = Notes.cardTitle(latest)
        b.tvNote.setCompoundDrawablesRelativeWithIntrinsicBounds(if (latest.pinned) R.drawable.ic_pin else 0, 0, 0, 0)
        b.tvNote.compoundDrawablePadding = (8 * context.resources.displayMetrics.density).toInt()
        ChecklistStyle.bindProgress(latest, b.tvNoteCount, b.pbNote)
        for ((index, item) in Notes.cardItems(latest)) {
            val box = LayoutInflater.from(context).inflate(R.layout.item_note_checkbox, b.itemsPanel, false) as CheckBox
            box.text = item.text
            box.isChecked = item.checked
            box.alpha = if (item.checked) 0.5f else 1f
            box.paintFlags = if (item.checked) box.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            else box.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            box.setOnClickListener {
                Haptics.tick(it)
                store.toggleItem(latest.id, index)
                render(b)
            }
            b.itemsPanel.addView(box)
        }
        val edited = Notes.editedLabel(
            latest, today, ZoneId.systemDefault(),
            Notes.EditedFormat(context.getString(R.string.note_edited_today), context.getString(R.string.note_edited_yesterday), context.getString(R.string.note_edited_on)),
        )
        val meta = listOfNotNull(Notes.dateLabel(latest), edited).joinToString(" · ")
        b.tvNoteDate.text = meta
        b.tvNoteDate.visibility = if (meta.isEmpty()) View.GONE else View.VISIBLE
        b.tvNote.setOnLongClickListener {
            Haptics.tick(it)
            it.showPopupMenu(configure = { menu ->
                if (Notes.nextUnchecked(latest) != null) menu.add(0, MENU_TICK, 0, R.string.note_tick_next)
                menu.add(0, MENU_PIN, 1, if (latest.pinned) R.string.note_unpin else R.string.note_pin)
                menu.add(0, MENU_DELETE, 2, R.string.note_delete)
            }) { item ->
                when (item.itemId) {
                    MENU_TICK -> Notes.nextUnchecked(latest)?.let { store.toggleItem(latest.id, it) }
                    MENU_PIN -> store.setPinned(latest.id, !latest.pinned)
                    MENU_DELETE -> {
                        context.showMessageDialog(R.string.note_delete_title, R.string.note_delete_message, R.string.note_delete) {
                            store.delete(latest.id)
                            render(b)
                        }
                        return@showPopupMenu
                    }
                }
                render(b)
            }
            true
        }
    }

    /** The extra open-note lines and the next dated note under the main note. */
    private fun renderExtras(b: CardNotesBinding, lines: Notes.CardLines, today: LocalDate) {
        val context = b.root.context
        for ((view, note) in listOf(b.tvMore1 to lines.more.getOrNull(0), b.tvMore2 to lines.more.getOrNull(1))) {
            view.visibility = if (note == null) View.GONE else View.VISIBLE
            view.text = note?.let { "· " + Notes.displayText(it) }
        }
        val dated = lines.dated
        b.tvNextDated.visibility = if (dated == null) View.GONE else View.VISIBLE
        b.vNextDivider.visibility = b.tvNextDated.visibility
        b.tvNextDated.text = dated?.let {
            context.getString(R.string.notes_card_next_dated, Notes.dateLabel(it) ?: "", Notes.displayText(it))
        }
    }
}
