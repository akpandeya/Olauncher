package app.olauncher.ui.words

import android.content.Context
import android.view.LayoutInflater
import java.util.Locale
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.work.WorkManager
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.data.store.Word
import app.olauncher.data.store.WordEdit
import app.olauncher.data.store.ReviewEventsStore
import app.olauncher.data.store.WordList
import app.olauncher.data.store.WordProgress
import app.olauncher.data.store.WordListsLogic
import app.olauncher.data.store.WordLookup
import app.olauncher.data.store.WordsListLogic
import app.olauncher.data.store.WordsStore
import app.olauncher.databinding.DialogEditWordBinding
import app.olauncher.databinding.DialogWordListNameBinding
import app.olauncher.helper.OlDialog
import app.olauncher.helper.PracticeWorker
import app.olauncher.helper.createDialog
import app.olauncher.helper.createMultiChoiceDialog
import app.olauncher.helper.showMessageDialog
import app.olauncher.helper.showToast
import app.olauncher.ui.today.TodayChips
import app.olauncher.ui.today.cards.WordsCard

/**
 * The word dialogs shared by the words screens (all words, lists, one list), so edit,
 * delete and membership changes behave the same everywhere. [onChanged] runs after any
 * store write; the calling screen re-reads and re-renders there.
 */
object WordDialogs {

    /** The due-status pill text on a word row. */
    fun formatStatus(context: Context, status: WordsListLogic.Status): String = when (status) {
        WordsListLogic.Status.Due -> context.getString(R.string.words_status_due)
        is WordsListLogic.Status.DueIn -> context.getString(R.string.words_status_due_in, status.days)
        WordsListLogic.Status.Learned -> context.getString(R.string.words_status_learned)
    }

    /**
     * The full edit dialog: word text, meaning, the meaning's source hint, and a
     * "Look up again" neutral action when Wiktionary lookups are on. [lifecycleOwner]
     * scopes the lookup-finished observer so it cannot outlive the caller's view.
     */
    fun showEditDialog(
        context: Context,
        listedWord: Word,
        lifecycleOwner: () -> LifecycleOwner?,
        onChanged: () -> Unit,
    ) {
        val store = WordsCard.getStore(context)
        // A lookup may have filled the meaning since the list was drawn, so edit the saved copy.
        val word = store.get(listedWord.id) ?: return onChanged()
        val prefs = Prefs(context)
        val lookupAllowed = WordLookup.isLookupAllowed(prefs.germanEnabled, prefs.germanLookup)
        var dialogBinding: DialogEditWordBinding? = null
        var dialogRef: OlDialog? = null
        // Tick state for the list chips; applied on Save only, so Cancel leaves memberships alone.
        val selectedLists = word.listIds.toMutableSet()

        val dialog = context.createDialog(
            title = R.string.words_edit,
            action = R.string.save,
            neutral = if (lookupAllowed) R.string.words_look_up_again else 0,
            onNeutral = {
                lookUpAgain(context, WordEdit.resetMeaning(word), lifecycleOwner, onChanged)
            },
            onAction = {
                dialogBinding?.let { db ->
                    val newText = db.etWord.text?.toString().orEmpty()
                    if (WordEdit.clashesWithOtherWord(newText, word.id, store.all())) {
                        context.showToast(context.getString(R.string.words_already_saved, newText.trim()))
                        return@let
                    }
                    val applied = WordEdit.apply(
                        word,
                        newText = newText,
                        newMeaning = db.etMeaning.text?.toString().orEmpty(),
                        lookupAllowed = lookupAllowed
                    )
                    val knownListIds = store.lists().mapTo(HashSet()) { it.id }
                    val outcome = applied.copy(
                        word = applied.word.copy(
                            context = db.etExample.text?.toString().orEmpty().trim(),
                            listIds = selectedLists.filter { it in knownListIds },
                        )
                    )
                    if (outcome.needsLookup && lookupAllowed) {
                        lookUpAgain(context, outcome.word, lifecycleOwner, onChanged)
                    } else {
                        store.update(outcome.word)
                        onChanged()
                    }
                    if (outcome.word.context != word.context) PracticeWorker.scheduleAfterCapture(context)
                }
            }
        ) { container ->
            DialogEditWordBinding.inflate(LayoutInflater.from(container.context), container, false).also { db ->
                dialogBinding = db
                db.etWord.setText(word.text)
                db.etMeaning.setText(word.meaning)
                db.etExample.setText(word.context)
                val sourceRes = when {
                    word.meaning.isBlank() -> 0
                    word.meaningSource.equals("wiktionary", ignoreCase = true) -> R.string.words_source_wiktionary
                    else -> R.string.words_source_user
                }
                db.tvSource.isVisible = sourceRes != 0
                if (sourceRes != 0) db.tvSource.setText(sourceRes)
                db.etMeaning.focusAtEnd(context)
                bindListChips(context, db, selectedLists)
                bindProgress(context, db, word)
                db.btnDeleteWord.setOnClickListener {
                    dialogRef?.dismiss()
                    showDeleteDialog(context, word, onChanged)
                }
            }.root
        }
        dialogRef = dialog
        dialog.showRespectingStatusBar()
    }

    /** One tickable chip per list plus a "+ name" chip that creates a list and ticks it. */
    private fun bindListChips(context: Context, db: DialogEditWordBinding, selected: MutableSet<String>) {
        db.listChips.removeAllViews()
        val store = WordsCard.getStore(context)
        for (list in store.lists()) {
            val chip = TodayChips.newSelectableChip(context, list.name)
            chip.isSelected = list.id in selected
            chip.setOnClickListener {
                it.isSelected = WordListChips.toggle(selected, list.id)
            }
            db.listChips.addView(chip)
        }
        val add = TodayChips.newSelectableChip(context, context.getString(R.string.words_edit_new_list_chip))
        add.setOnClickListener {
            val before = store.lists().mapTo(HashSet()) { it.id }
            showListNameDialog(context, list = null) {
                store.lists().firstOrNull { it.id !in before }?.let { selected.add(it.id) }
                bindListChips(context, db, selected)
            }
        }
        db.listChips.addView(add)
    }

    /** "Box 2 · next review Mon 26 Oct · 6 reviews". */
    private fun bindProgress(context: Context, db: DialogEditWordBinding, word: Word) {
        val progress = WordProgress.of(word, ReviewEventsStore(context).all())
        val reviews = if (progress.reviewCount == 1) {
            context.getString(R.string.words_progress_reviews_single)
        } else {
            context.getString(R.string.words_progress_reviews_plural, progress.reviewCount)
        }
        db.tvProgress.text = if (progress.learned) {
            context.getString(R.string.words_progress_learned, reviews)
        } else {
            context.getString(
                R.string.words_progress_line,
                progress.box,
                WordProgress.formatDay(progress.nextReviewDay, Locale.getDefault()),
                reviews,
            )
        }
    }

    /** Confirm-then-delete for one word. */
    fun showDeleteDialog(context: Context, word: Word, onChanged: () -> Unit) {
        context.showMessageDialog(
            title = R.string.words_delete,
            message = context.getString(R.string.words_delete_confirm, word.text),
            action = R.string.words_delete,
        ) {
            WordsCard.getStore(context).delete(word.id)
            onChanged()
        }
    }

    /**
     * One checkbox per list, pre-checked from the word's memberships; Apply replaces them.
     * With no lists yet the only row is a hint that cannot be ticked, and a neutral
     * "New list" action opens the create dialog instead.
     */
    fun showListsDialog(context: Context, word: Word, onChanged: () -> Unit) {
        val store = WordsCard.getStore(context)
        val lists = store.lists()
        if (lists.isEmpty()) {
            val dialog = context.createMultiChoiceDialog(
                title = R.string.word_lists_title,
                items = listOf(context.getString(R.string.word_lists_none_yet)),
                checked = BooleanArray(1),
                action = 0,
                neutral = R.string.word_lists_new_list,
                onNeutral = { showListNameDialog(context, list = null, onChanged = onChanged) },
                onToggle = { _, _ -> false },
                onAction = {},
            )
            dialog.showRespectingStatusBar()
            return
        }
        // Re-read: a lookup may have touched the word since the row was drawn.
        val current = store.get(word.id) ?: return onChanged()
        val checked = BooleanArray(lists.size) { lists[it].id in current.listIds }
        val dialog = context.createMultiChoiceDialog(
            title = R.string.word_lists_title,
            items = lists.map { it.name },
            checked = checked,
            action = R.string.apply,
        ) {
            val selected = lists.mapIndexedNotNull { index, list -> if (checked[index]) list.id else null }
            WordsCard.getStore(context).setLists(current.id, selected)
            onChanged()
        }
        dialog.showRespectingStatusBar()
    }

    /** Every saved word not yet in [list], due-first like the words page; the ticked ones join on Apply. */
    fun showAddWordsDialog(context: Context, list: WordList, onChanged: () -> Unit) {
        val candidates = WordsListLogic.sort(WordsCard.getStore(context).all().filter { list.id !in it.listIds })
        if (candidates.isEmpty()) return
        val checked = BooleanArray(candidates.size)
        val dialog = context.createMultiChoiceDialog(
            title = R.string.word_list_add_words,
            items = candidates.map { candidateLabel(context, it) },
            checked = checked,
            action = R.string.word_list_add_words,
            actionLabel = { count -> addWordsLabel(context, count) },
            searchHint = R.string.word_list_add_search_hint,
            matchingIndices = { query -> WordsListLogic.matchingIndices(candidates, query) },
        ) {
            val picked = candidates.mapIndexedNotNull { index, candidate -> if (checked[index]) candidate.id else null }
            if (picked.isNotEmpty()) WordsCard.getStore(context).assignAll(picked, list.id)
            onChanged()
        }
        dialog.showRespectingStatusBar()
    }

    /** "Add words" at zero picks, "Add 1 word" / "Add N words" otherwise. */
    private fun addWordsLabel(context: Context, picked: Int): String =
        if (picked > 0) {
            context.resources.getQuantityString(R.plurals.word_list_add_count, picked, picked)
        } else {
            context.getString(R.string.word_list_add_words)
        }

    /** Word plus its meaning when one is saved, so lookalike words stay distinguishable. */
    private fun candidateLabel(context: Context, word: Word): String {
        return if (word.meaning.isBlank()) {
            word.text
        } else {
            context.getString(R.string.word_row_meaning, word.text, word.meaning)
        }
    }

    /** Create ([list] null) or rename a list. Blank names are ignored; a name clash only toasts. */
    fun showListNameDialog(context: Context, list: WordList?, onChanged: () -> Unit) {
        val store = WordsCard.getStore(context)
        var dialogBinding: DialogWordListNameBinding? = null
        val dialog = context.createDialog(
            title = if (list == null) R.string.word_lists_new_list else R.string.rename,
            action = R.string.save,
            onAction = {
                dialogBinding?.let { db ->
                    saveListName(context, store, list, db.etListName.text?.toString().orEmpty(), onChanged)
                }
            }
        ) { container ->
            DialogWordListNameBinding.inflate(LayoutInflater.from(container.context), container, false).also { db ->
                dialogBinding = db
                if (list != null) db.etListName.setText(list.name)
                db.etListName.focusAtEnd(context)
            }.root
        }
        dialog.showRespectingStatusBar()
    }

    private fun saveListName(
        context: Context,
        store: WordsStore,
        list: WordList?,
        rawName: String,
        onChanged: () -> Unit,
    ) {
        val name = WordListsLogic.normalizeName(rawName)
        val error = WordListsLogic.validateName(name, store.lists(), exceptId = list?.id)
        when (error) {
            WordListsLogic.NameError.BLANK -> return
            WordListsLogic.NameError.DUPLICATE -> {
                context.showToast(context.getString(R.string.word_lists_duplicate, name))
                return
            }
            null -> {
                if (list == null) {
                    store.addList(name)
                } else {
                    store.renameList(list.id, name)
                }
                onChanged()
            }
        }
    }

    private fun lookUpAgain(
        context: Context,
        word: Word,
        lifecycleOwner: () -> LifecycleOwner?,
        onChanged: () -> Unit,
    ) {
        WordsCard.getStore(context).update(word)
        WordLookup.enqueue(context, word.id)
        context.showToast(R.string.words_looking_up)
        onChanged()
        refreshWhenLookupFinishes(context, word.id, lifecycleOwner, onChanged)
    }

    private fun refreshWhenLookupFinishes(
        context: Context,
        wordId: Long,
        lifecycleOwner: () -> LifecycleOwner?,
        onChanged: () -> Unit,
    ) {
        val owner = lifecycleOwner() ?: return
        val work = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkLiveData(WordLookup.uniqueWorkName(wordId))
        work.observe(owner) { infos ->
            if (infos.isNotEmpty() && infos.all { it.state.isFinished }) {
                work.removeObservers(owner)
                onChanged()
            }
        }
    }

    private fun AppCompatEditText.focusAtEnd(context: Context) {
        setSelection(text?.length ?: 0)
        post {
            requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }
}
