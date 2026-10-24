package app.olauncher.ui.notes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import androidx.core.widget.doOnTextChanged
import app.olauncher.R
import app.olauncher.databinding.FragmentNotesBinding
import app.olauncher.databinding.ItemNoteBinding
import app.olauncher.helper.Haptics
import app.olauncher.helper.Note
import app.olauncher.helper.NoteViewRules
import app.olauncher.helper.Notes
import app.olauncher.helper.hideKeyboard
import app.olauncher.ui.BaseFragment
import app.olauncher.ui.today.attachPressScale
import app.olauncher.ui.today.cards.NotesCard
import java.time.LocalDate
import java.time.ZoneId

/** Every open note: pinned first, then the rest, with search and a bar that opens the Add note sheet. Tapping a row opens the note view. */
class NotesFragment : BaseFragment() {

    private var savedScrollY = 0
    private var _binding: FragmentNotesBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNotesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.etSearch.doOnTextChanged { _, _, _, _ -> render() }
        binding.etSearch.setOnEditorActionListener { v, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener false
            v.hideKeyboard()
            true
        }
        val addNote = View.OnClickListener {
            Haptics.tick(it)
            AddNoteSheet.show(it.context) {
                binding.etSearch.setText("")
                render()
            }
        }
        binding.btnNewNote.setOnClickListener(addNote)
        binding.btnNewNotePlus.setOnClickListener(addNote)
        refreshHook = { render() }
    }

    override fun onResume() {
        super.onResume()
        render()
        // Back from the note view: put the list where it was.
        binding.notesScrollView.post { _binding?.notesScrollView?.scrollTo(0, savedScrollY) }
    }

    private fun render() {
        val b = _binding ?: return
        val context = context ?: return
        val all = NotesCard.store(context).all()
        val today = LocalDate.now()
        val openCount = Notes.open(all).size
        b.tvCounts.text = Notes.countLine(all, context.getString(R.string.notes_screen_count_line))
        val sections = Notes.sections(all, b.etSearch.text.toString(), today)
        b.tvPinnedHeading.isVisible = sections.pinned.isNotEmpty()
        b.tvOpenHeading.isVisible = sections.rest.isNotEmpty()
        fill(b.pinnedPanel, sections.pinned, today)
        fill(b.openPanel, sections.rest, today)
        b.tvEmpty.isVisible = sections.isEmpty
        b.tvEmpty.setText(if (openCount == 0) R.string.notes_screen_empty else R.string.notes_search_empty)
    }

    private fun fill(panel: ViewGroup, notes: List<Note>, today: LocalDate) {
        panel.removeAllViews()
        for (note in notes) panel.addView(bindNote(panel, note, today))
    }

    private fun bindNote(parent: ViewGroup, note: Note, today: LocalDate): View {
        val context = parent.context
        val row = ItemNoteBinding.inflate(layoutInflater, parent, false)
        row.root.attachPressScale()
        val isChecklist = note.items.isNotEmpty()
        row.tvNoteTitle.text = note.text
        // An untitled checklist keeps the (empty) title so the count stays right-aligned.
        row.tvNoteTitle.visibility = if (note.text.isNotEmpty()) View.VISIBLE else if (isChecklist) View.INVISIBLE else View.GONE
        ChecklistStyle.bindProgress(note, row.tvNoteCount, row.pbNote)
        ChecklistStyle.addDividers(row.itemsPanel)
        row.ivPin.isVisible = note.pinned
        row.tvDue.isVisible = NoteViewRules.isDue(note, today)
        val preview = if (isChecklist) null else NoteViewRules.bodyPreview(note)
        row.tvNoteBody.text = preview
        row.tvNoteBody.isVisible = preview != null
        row.root.setOnClickListener {
            Haptics.tick(it)
            val nav = findNavController()
            if (nav.currentDestination?.id == R.id.notesFragment) {
                nav.navigate(R.id.action_notesFragment_to_noteViewFragment, bundleOf(NoteViewFragment.ARG_NOTE_ID to note.id))
            }
        }
        val edited = Notes.editedLabel(
            note, today, ZoneId.systemDefault(),
            Notes.EditedFormat(context.getString(R.string.note_edited_today), context.getString(R.string.note_edited_yesterday), context.getString(R.string.note_edited_on)),
        )
        val meta = listOfNotNull(Notes.dateLabel(note), edited).joinToString(" · ")
        row.tvNoteMeta.text = meta
        row.tvNoteMeta.isVisible = meta.isNotEmpty()
        note.items.forEachIndexed { index, item ->
            val box = layoutInflater.inflate(R.layout.item_note_checkbox, row.itemsPanel, false) as android.widget.CheckBox
            box.text = item.text
            box.isChecked = item.checked
            box.alpha = if (item.checked) 0.5f else 1f
            box.paintFlags = if (item.checked) box.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            else box.paintFlags and android.graphics.Paint.STRIKE_THRU_TEXT_FLAG.inv()
            box.setOnClickListener {
                Haptics.tick(it)
                NotesCard.store(context).toggleItem(note.id, index)
                render()
            }
            row.itemsPanel.addView(box)
        }
        return row.root
    }

    override fun onDestroyView() {
        savedScrollY = _binding?.notesScrollView?.scrollY ?: savedScrollY
        refreshHook = null
        super.onDestroyView()
        _binding = null
    }

    companion object {
        /** Redraws the open Notes screen; the note view's Undo calls it after the screen is back. */
        internal var refreshHook: (() -> Unit)? = null
    }
}
