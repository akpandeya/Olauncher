package app.olauncher.ui.today.cards

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.databinding.CardNotesBinding
import app.olauncher.databinding.ItemNoteBinding
import app.olauncher.helper.Haptics
import app.olauncher.helper.Notes
import app.olauncher.helper.NotesStore
import app.olauncher.helper.UndoMessage
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.showToast
import app.olauncher.ui.today.TodayCard
import java.time.LocalDate
import java.time.ZonedDateTime

/** All open notes: tap marks done (with Undo), long-press deletes, the last line adds a note. */
object NotesCard : TodayCard {

    const val KEY = "notes"

    override val key: String = KEY
    override val titleRes: Int = R.string.notes_card_title
    override val emoji: String = "📝"

    fun store(context: Context): NotesStore {
        val prefs = Prefs(context)
        return NotesStore({ prefs.notesJson }, { prefs.notesJson = it })
    }

    override fun createView(inflater: LayoutInflater, parent: ViewGroup, fragment: Fragment): View {
        val b = CardNotesBinding.inflate(inflater, parent, false)
        b.etAddNote.setOnEditorActionListener { v, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
            val context = v.context
            when (val parsed = Notes.parse(v.text.toString(), ZonedDateTime.now())) {
                is Notes.Parsed.Error -> context.showToast(context.getString(R.string.command_cant_read, parsed.token))
                is Notes.Parsed.Ok -> {
                    if (store(context).add(parsed.text, System.currentTimeMillis(), parsed.showFrom)) {
                        v.text = ""
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
        val inflater = LayoutInflater.from(context)
        val store = store(context)
        b.notesContainer.removeAllViews()
        for (note in Notes.cardList(store.all(), LocalDate.now())) {
            val item = ItemNoteBinding.inflate(inflater, b.notesContainer, false)
            item.tvNoteText.text = note.text
            val label = Notes.dateLabel(note)
            item.tvNoteDate.isVisible = label != null
            item.tvNoteDate.text = label
            item.root.setOnClickListener {
                Haptics.tick(it)
                store.markDone(note.id)
                render(b)
                UndoMessage.show(context as? Activity, context.getString(R.string.note_marked_done)) {
                    store.markDone(note.id, done = false)
                    render(b)
                }
            }
            item.root.setOnLongClickListener {
                Haptics.tick(it)
                store.delete(note.id)
                render(b)
                true
            }
            b.notesContainer.addView(item.root)
        }
    }
}
