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
import java.time.ZonedDateTime

/** Half-width card: the open count, the newest note and a line to add one. */
object NotesCard : TodayCard {

    const val KEY = "notes"
    private const val MENU_PIN = 1
    private const val MENU_DELETE = 2

    override val key: String = KEY
    override val titleRes: Int = R.string.notes_card_title
    override val emoji: String = "📝"
    override val drawsOwnHeader: Boolean = true
    override val halfWidth: Boolean = true

    fun store(context: Context): NotesStore {
        val prefs = Prefs(context)
        return NotesStore({ prefs.notesJson }, { prefs.notesJson = it })
    }

    override fun createView(inflater: LayoutInflater, parent: ViewGroup, fragment: Fragment): View {
        val b = CardNotesBinding.inflate(inflater, parent, false)
        ViewCompat.setAccessibilityHeading(b.tvCardTitle, true)
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
        // The newest note that is showing now (or the newest match while searching); tap marks it
        // done, long-press opens a menu (Pin/Unpin, Delete). A pinned note is shown first.
        val today = LocalDate.now()
        val latest = Notes.featured(if (query.isBlank()) Notes.visible(all, today) else Notes.search(Notes.cardList(all, today), query))
        if (latest == null) {
            b.tvNote.setText(if (query.isBlank()) R.string.notes_card_empty else R.string.notes_search_empty)
            b.tvNote.isClickable = false
            b.tvNote.isLongClickable = false
            return
        }
        b.tvNote.text = latest.text
        b.tvNote.setCompoundDrawablesRelativeWithIntrinsicBounds(if (latest.pinned) R.drawable.ic_pin else 0, 0, 0, 0)
        b.tvNote.compoundDrawablePadding = (8 * context.resources.displayMetrics.density).toInt()
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
                menu.add(0, MENU_PIN, 0, if (latest.pinned) R.string.note_unpin else R.string.note_pin)
                menu.add(0, MENU_DELETE, 1, R.string.note_delete)
            }) { item ->
                when (item.itemId) {
                    MENU_PIN -> store.setPinned(latest.id, !latest.pinned)
                    MENU_DELETE -> store.delete(latest.id)
                }
                render(b)
            }
            true
        }
    }
}
