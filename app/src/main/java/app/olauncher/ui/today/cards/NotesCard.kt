package app.olauncher.ui.today.cards

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
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
import app.olauncher.helper.UndoMessage
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.showPopupMenu
import app.olauncher.helper.showToast
import app.olauncher.ui.today.TodayCard
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Full-width card: the open count, the main note, up to two more, the next dated note and a field to add one. */
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
        b.tvCardTitle.setOnClickListener {
            Haptics.tick(it)
            val nav = fragment.findNavController()
            if (nav.currentDestination?.id == R.id.todayFragment) nav.navigate(R.id.action_todayFragment_to_notesFragment)
        }
        b.etSearchNotes.doOnTextChanged { _, _, _, _ -> render(b) }
        b.etSearchNotes.setOnEditorActionListener { v, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener false
            v.hideKeyboard()
            true
        }
        b.etAddNote.setOnEditorActionListener { v, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
            val context = v.context
            when (val parsed = Notes.parse(v.text.toString(), ZonedDateTime.now())) {
                is Notes.Parsed.Error -> context.showToast(context.getString(R.string.command_cant_read, parsed.token))
                is Notes.Parsed.Ok -> {
                    if (store(context).add(parsed.text, System.currentTimeMillis(), parsed.showFrom)) {
                        v.text = ""
                        b.etSearchNotes.setText("") // re-renders through the text watcher when it had text
                        render(b)
                    }
                }
            }
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
        // The main note is the featured one showing now (or the best match while searching); tap marks it
        // done, long-press opens a menu (Pin/Unpin, Delete, Tick next). A pinned note is shown first.
        val today = LocalDate.now()
        val list = Notes.cardList(all, today)
        val lines = Notes.cardLines(if (query.isBlank()) list else Notes.search(list, query), today)
        val latest = lines.main
        renderExtras(b, lines, today)
        if (latest == null) {
            b.tvNote.setText(if (query.isBlank()) R.string.notes_card_empty else R.string.notes_search_empty)
            b.tvNote.isClickable = false
            b.tvNote.isLongClickable = false
            b.tvNote.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
            b.tvNoteDate.visibility = View.GONE
            return
        }
        b.tvNote.text = Notes.displayText(latest)
        b.tvNote.setCompoundDrawablesRelativeWithIntrinsicBounds(if (latest.pinned) R.drawable.ic_pin else 0, 0, 0, 0)
        b.tvNote.compoundDrawablePadding = (8 * context.resources.displayMetrics.density).toInt()
        val edited = Notes.editedLabel(
            latest, today, ZoneId.systemDefault(),
            Notes.EditedFormat(context.getString(R.string.note_edited_today), context.getString(R.string.note_edited_yesterday), context.getString(R.string.note_edited_on)),
        )
        val progress = Notes.progressText(latest, context.getString(R.string.notes_card_progress))
        val meta = listOfNotNull(progress, Notes.dateLabel(latest), edited).joinToString(" · ")
        b.tvNoteDate.text = meta
        b.tvNoteDate.visibility = if (meta.isEmpty()) View.GONE else View.VISIBLE
        b.tvNote.setOnClickListener {
            Haptics.tick(it)
            store.markDone(latest.id)
            render(b)
            UndoMessage.show(context as? Activity, context.getString(R.string.note_marked_done)) {
                store.markDone(latest.id, done = false)
                render(b)
            }
        }
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
                    MENU_DELETE -> store.delete(latest.id)
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
