package app.olauncher.ui.notes

import android.app.DatePickerDialog
import android.graphics.Paint
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import app.olauncher.R
import app.olauncher.databinding.FragmentNoteViewBinding
import app.olauncher.databinding.ItemNoteViewItemBinding
import app.olauncher.helper.Haptics
import app.olauncher.helper.Note
import app.olauncher.helper.NoteViewRules
import app.olauncher.helper.Notes
import app.olauncher.helper.NotesStore
import app.olauncher.helper.UndoMessage
import app.olauncher.helper.createChoiceDialog
import app.olauncher.helper.dpToPx
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.showMessageDialog
import app.olauncher.helper.showToast
import app.olauncher.ui.BaseFragment
import app.olauncher.ui.today.ImeInset
import app.olauncher.ui.today.cards.NotesCard
import app.olauncher.ui.today.cards.WordsCard
import java.time.LocalDate
import java.time.ZoneId

/**
 * One note in full: title, body or checklist items (each with its own box and editable text), date,
 * pin, lists, and the actions Pin, Move to list, Done (plain notes only) and a separate Delete.
 * Text edits are saved when a field loses focus or the screen is left.
 */
class NoteViewFragment : BaseFragment() {

    private var _binding: FragmentNoteViewBinding? = null
    private val binding get() = _binding!!
    private var noteId = -1L
    private val store: NotesStore get() = NotesCard.store(requireContext())

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentNoteViewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        noteId = arguments?.getLong(ARG_NOTE_ID, -1L) ?: -1L
        val b = binding
        // Edge-to-edge activity: the window is not resized for the keyboard, so pad
        // the page by the IME inset and the scroll view gives up the height.
        b.root.setOnApplyWindowInsetsListener { root, insets ->
            val padding = ImeInset.bottomPadding(
                imeVisible = insets.isVisible(WindowInsets.Type.ime()),
                imeBottomPx = insets.getInsets(WindowInsets.Type.ime()).bottom,
            )
            if (root.paddingBottom != padding) {
                root.setPadding(root.paddingLeft, root.paddingTop, root.paddingRight, padding)
            }
            insets
        }
        b.btnBack.setOnClickListener { goBack() }
        b.etViewTitle.onCommit { text ->
            update { n, now -> NoteViewRules.withTitle(n, text, now) }
            // A blank title on a plain note is refused, so show the saved one again.
            store.get(noteId)?.let { _binding?.let { v -> setIfChanged(v.etViewTitle, it.text) } }
        }
        b.etViewBody.setOnFocusChangeListener { v, hasFocus ->
            if (!hasFocus) update { n, now -> NoteViewRules.withBody(n, (v as EditText).text.toString(), now) }
        }
        b.etAddItem.setOnEditorActionListener { v, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
            val text = (v as EditText).text.toString()
            v.setText("")
            if (text.isNotBlank()) {
                update { n, now -> NoteViewRules.withNewItem(n, text, now) }
                render()
                binding.etAddItem.requestFocus() // keep typing the next item
            } else {
                v.hideKeyboard()
            }
            true
        }
        b.rowViewDate.setOnClickListener { pickDate() }
        b.switchViewPin.setOnClickListener { togglePin(it) }
        b.btnViewPin.setOnClickListener { togglePin(it) }
        b.btnViewMove.setOnClickListener {
            commitPending()
            showMoveDialog()
        }
        b.btnViewDone.setOnClickListener { markDone(it) }
        b.btnViewDelete.setOnClickListener {
            commitPending()
            requireContext().showMessageDialog(R.string.note_delete_title, R.string.note_delete_message, R.string.note_delete) {
                store.delete(noteId)
                leave()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!render()) leave()
    }

    override fun onPause() {
        // Saves a field that is still being edited.
        _binding?.root?.findFocus()?.clearFocus()
        super.onPause()
    }

    private fun update(change: (Note, Long) -> Note) {
        if (noteId >= 0) store.update(noteId, change)
        _binding?.let { renderEdited() }
    }

    private fun goBack() {
        binding.root.findFocus()?.clearFocus()
        binding.root.hideKeyboard()
        leave()
    }

    private fun leave() {
        val nav = findNavController()
        if (nav.currentDestination?.id == R.id.noteViewFragment) nav.popBackStack()
    }

    /** Saves the field being edited, if any, before the screen is redrawn from the store. */
    private fun commitPending() {
        _binding?.root?.findFocus()?.clearFocus()
    }

    private fun togglePin(view: View) {
        commitPending()
        Haptics.tick(view)
        val note = store.get(noteId) ?: return
        store.setPinned(noteId, !note.pinned)
        render()
    }

    private fun markDone(view: View) {
        commitPending()
        Haptics.tick(view)
        val context = requireContext()
        val id = noteId
        val activity = activity
        store.markDone(id)
        leave()
        UndoMessage.show(activity, context.getString(R.string.note_marked_done)) {
            store.markDone(id, done = false)
            NotesFragment.refreshHook?.invoke()
        }
    }

    private fun pickDate() {
        commitPending()
        val note = store.get(noteId) ?: return
        val start = note.showFrom?.let { LocalDate.ofEpochDay(it) } ?: LocalDate.now()
        val picker = DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                update { n, now -> NoteViewRules.withShowFrom(n, LocalDate.of(year, month + 1, day), now) }
                render()
            },
            start.year, start.monthValue - 1, start.dayOfMonth,
        )
        picker.setButton(DatePickerDialog.BUTTON_NEUTRAL, getString(R.string.note_add_remind_none)) { _, _ ->
            update { n, now -> NoteViewRules.withShowFrom(n, null, now) }
            render()
        }
        picker.show()
    }

    private fun showMoveDialog() {
        val context = requireContext()
        val lists = WordsCard.getStore(context).lists()
        if (lists.isEmpty()) return context.showToast(getString(R.string.note_view_no_lists))
        val note = store.get(noteId) ?: return
        val labels = listOf<CharSequence>(getString(R.string.note_view_move_none)) + lists.map { it.name }
        val current = lists.indexOfFirst { it.id in note.lists } + 1
        val dialog = context.createChoiceDialog(title = R.string.note_view_move, items = labels, checkedIndex = current) { index ->
            update { n, now -> NoteViewRules.withMovedTo(n, lists.getOrNull(index - 1)?.id, now) }
            render()
        }
        dialog.showRespectingStatusBar()
    }

    /** Draws the saved note; false when it no longer exists (deleted or done elsewhere). */
    private fun render(): Boolean {
        val b = _binding ?: return false
        val note = store.get(noteId)?.takeIf { !it.done } ?: return false
        val isChecklist = note.items.isNotEmpty()
        setIfChanged(b.etViewTitle, note.text)
        b.etViewBody.isVisible = !isChecklist
        if (!isChecklist) setIfChanged(b.etViewBody, note.body)
        b.etAddItem.isVisible = isChecklist
        b.btnViewDone.isVisible = NoteViewRules.canMarkDone(note)
        b.tvViewDate.text = note.showFrom?.let { Notes.dateLabel(LocalDate.ofEpochDay(it)) } ?: getString(R.string.note_add_remind_none)
        b.switchViewPin.isChecked = note.pinned
        b.btnViewPin.setText(if (note.pinned) R.string.note_unpin else R.string.note_pin)
        renderEdited(note)
        renderItems(b, note)
        renderListChips(b, note)
        return true
    }

    private fun renderEdited(note: Note? = store.get(noteId)) {
        val b = _binding ?: return
        val context = context ?: return
        val label = note?.let {
            Notes.editedLabel(
                it, LocalDate.now(), ZoneId.systemDefault(),
                Notes.EditedFormat(context.getString(R.string.note_edited_today), context.getString(R.string.note_edited_yesterday), context.getString(R.string.note_edited_on)),
            )
        }
        b.tvEdited.text = label
        b.tvEdited.isVisible = label != null
    }

    private fun renderItems(b: FragmentNoteViewBinding, note: Note) {
        b.viewItemsPanel.removeAllViews()
        ChecklistStyle.addDividers(b.viewItemsPanel)
        ChecklistStyle.bindProgress(note, b.tvViewCount, b.pbView)
        note.items.forEachIndexed { index, item ->
            val row = ItemNoteViewItemBinding.inflate(layoutInflater, b.viewItemsPanel, false)
            row.etItem.setText(item.text)
            row.cbItem.isChecked = item.checked
            styleItem(row, item.checked)
            row.cbItem.setOnClickListener {
                commitPending()
                Haptics.tick(it)
                store.toggleItem(noteId, index)
                render()
            }
            // Cleared text removes the item; the list is redrawn so the remaining rows keep their right index.
            row.etItem.onCommit { text ->
                val before = store.get(noteId)
                update { n, now -> NoteViewRules.withItemText(n, index, text, now) }
                if (before != store.get(noteId)) render()
            }
            b.viewItemsPanel.addView(row.root)
        }
    }

    private fun styleItem(row: ItemNoteViewItemBinding, checked: Boolean) {
        row.etItem.alpha = if (checked) 0.5f else 1f
        row.etItem.paintFlags = if (checked) row.etItem.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        else row.etItem.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
    }

    private fun renderListChips(b: FragmentNoteViewBinding, note: Note) {
        val context = requireContext()
        val lists = WordsCard.getStore(context).lists()
        b.viewListChips.removeAllViews()
        b.viewListChips.isVisible = lists.isNotEmpty()
        for (list in lists) {
            val chip = newChip(context, list.name)
            chip.isSelected = list.id in note.lists
            chip.setOnClickListener {
                update { n, now -> NoteViewRules.withListToggled(n, list.id, now) }
                it.isSelected = !it.isSelected
            }
            b.viewListChips.addView(chip)
        }
    }

    /** Sets [text] only when it differs, so a redraw does not move the cursor of a field being edited. */
    private fun setIfChanged(field: EditText, text: String) {
        if (field.text?.toString() != text) field.setText(text)
    }

    /** Runs [save] with the field's text on focus loss or the keyboard's Done. */
    private fun EditText.onCommit(save: (String) -> Unit) {
        setOnFocusChangeListener { v, hasFocus -> if (!hasFocus) save((v as EditText).text.toString()) }
        setOnEditorActionListener { v, actionId, _ ->
            if (actionId != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
            v.clearFocus()
            v.hideKeyboard()
            true
        }
    }

    /** A plain TextView chip: a Material Chip throws under the app's AppCompat theme. */
    private fun newChip(context: android.content.Context, label: String): TextView = TextView(context).apply {
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_NOTE_ID = "noteId"
    }
}
