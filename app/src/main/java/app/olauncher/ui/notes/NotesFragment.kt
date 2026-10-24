package app.olauncher.ui.notes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.view.isVisible
import androidx.core.widget.doOnTextChanged
import app.olauncher.R
import app.olauncher.databinding.FragmentNotesBinding
import app.olauncher.databinding.ItemNoteBinding
import app.olauncher.helper.Haptics
import app.olauncher.helper.Note
import app.olauncher.helper.Notes
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.showToast
import app.olauncher.ui.BaseFragment
import app.olauncher.ui.today.cards.NotesCard
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** Every open note: pinned first, then the rest, with search and a bar to add a note. */
class NotesFragment : BaseFragment() {

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
        binding.etNewNote.setOnEditorActionListener { v, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
            val context = v.context
            when (val parsed = Notes.parse(v.text.toString(), ZonedDateTime.now())) {
                is Notes.Parsed.Error -> context.showToast(context.getString(R.string.command_cant_read, parsed.token))
                is Notes.Parsed.Ok -> if (NotesCard.store(context).add(parsed.text, System.currentTimeMillis(), parsed.showFrom)) {
                    v.text = ""
                    binding.etSearch.setText("")
                    render()
                }
            }
            v.hideKeyboard()
            true
        }
    }

    override fun onResume() {
        super.onResume()
        render()
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
        val isChecklist = note.items.isNotEmpty()
        row.tvNoteTitle.text = note.text
        row.tvNoteTitle.isVisible = note.text.isNotEmpty()
        row.ivPin.isVisible = note.pinned
        row.tvDue.isVisible = note.showFrom?.let { it <= today.toEpochDay() } == true
        val edited = Notes.editedLabel(
            note, today, ZoneId.systemDefault(),
            Notes.EditedFormat(context.getString(R.string.note_edited_today), context.getString(R.string.note_edited_yesterday), context.getString(R.string.note_edited_on)),
        )
        val progress = if (isChecklist) context.getString(R.string.notes_screen_progress, Notes.doneCount(note), note.items.size) else null
        val meta = listOfNotNull(progress, Notes.dateLabel(note), edited).joinToString(" · ")
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
        if (!isChecklist) {
            // A plain note's text is the body; keep it normal weight.
            row.tvNoteTitle.setTypeface(null, android.graphics.Typeface.NORMAL)
        }
        return row.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
