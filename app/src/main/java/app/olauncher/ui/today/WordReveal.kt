package app.olauncher.ui.today

import android.content.Context
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.isVisible
import app.olauncher.R
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import app.olauncher.data.Prefs
import app.olauncher.data.store.SharedOfflineGermanStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.olauncher.data.store.Word
import app.olauncher.databinding.DialogEditMeaningBinding
import app.olauncher.databinding.ViewWordRevealBinding
import app.olauncher.helper.OlDialog
import app.olauncher.helper.createDialog
import app.olauncher.helper.showMessageDialog
import app.olauncher.helper.showPopupMenu
import app.olauncher.ui.today.cards.WordsCardLogic
import app.olauncher.ui.words.NounArticle

class WordReveal(
    val binding: ViewWordRevealBinding,
    var onAnswer: ((word: Word, knewIt: Boolean) -> Unit)? = null,
    var onAlmost: ((word: Word) -> Unit)? = null,
    var onWordUpdated: ((word: Word) -> Unit)? = null,
    var onWordDeleted: ((word: Word) -> Unit)? = null,
    private val alwaysShowMeaning: Boolean = false,
    /** Show the saved context before the reveal, as the review session's flashcard front does. */
    private val alwaysShowContext: Boolean = false,
) {
    internal var genderResolver: ((String) -> String?)? = null

    /**
     * When set, shown in place of the word text — the pause word's rotating
     * practice prompt (#603). Null keeps the plain word; grading is unchanged.
     */
    var promptOverride: String? = null

    /**
     * The practice item's answer (or the word itself), shown in the reveal when
     * [promptOverride] is active: without it the user sees the meaning and the
     * context but can never check what the answer was (review R1 #10).
     */
    var answerOverride: String? = null

    var currentWord: Word? = null
        private set

    var isRevealed: Boolean = false
        private set

    init {
        binding.tvWord.setOnClickListener {
            if (currentWord != null) {
                setRevealed(!isRevealed)
            }
        }

        binding.tvWord.setOnLongClickListener {
            val word = currentWord ?: return@setOnLongClickListener false
            showWordPopup(binding.tvWord, word)
            true
        }

        binding.btnKnowIt.setOnClickListener {
            currentWord?.let { word ->
                onAnswer?.invoke(word, true)
            }
        }

        binding.btnAgain.setOnClickListener {
            currentWord?.let { word ->
                onAnswer?.invoke(word, false)
            }
        }

        binding.btnAlmost.setOnClickListener {
            currentWord?.let { word ->
                onAlmost?.invoke(word)
            }
        }
    }

    fun bind(word: Word, startRevealed: Boolean? = null) {
        val keepReveal = startRevealed ?: (currentWord?.id == word.id && isRevealed)
        currentWord = word
        val context = binding.root.context
        val prompt = promptOverride
        if (prompt != null) {
            binding.tvWord.text = prompt
        } else {
            val immediate = NounArticle.extractLeadingArticle(word.text)
                ?: genderResolver?.invoke(word.text)
            NounArticle.applyToTextView(binding.tvWord, NounArticle.format(word.text, immediate))
            if (immediate == null && genderResolver == null && Prefs(context).germanOfflineDictionary) {
                val appContext = context.applicationContext
                binding.root.findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
                    val found = withContext(Dispatchers.IO) { SharedOfflineGermanStore.get(appContext).gender(word.text) }
                    if (found != null && currentWord?.id == word.id) {
                        NounArticle.applyToTextView(binding.tvWord, NounArticle.format(word.text, found))
                    }
                }
            }
        }
        binding.tvMeaning.text = if (promptOverride != null) {
            revealMeaningWithAnswer(answerOverride, WordsCardLogic.formatMeaning(context, word.meaning, word.meaningSource))
        } else {
            WordsCardLogic.formatMeaning(context, word.meaning, word.meaningSource)
        }
        binding.tvContext.text = word.context
        setRevealed(keepReveal)
    }

    fun setRevealed(revealed: Boolean) {
        isRevealed = revealed
        val word = currentWord
        binding.tvMeaning.isVisible = revealed || (alwaysShowMeaning && word != null && word.meaning.isNotBlank())
        binding.tvContext.isVisible = (revealed || alwaysShowContext) && word != null && word.context.isNotBlank()
        binding.buttonsContainer.isVisible = revealed
    }

    /** The session grades with three buttons; the Today card keeps its two. */
    fun showAlmostButton(show: Boolean) {
        binding.btnAlmost.isVisible = show
    }

    fun showWordPopup(anchor: View, word: Word) {
        val context = anchor.context
        anchor.showPopupMenu(
            configure = { menu ->
                menu.add(Menu.NONE, 1, Menu.NONE, R.string.words_edit_meaning)
                menu.add(Menu.NONE, 2, Menu.NONE, R.string.words_delete)
            }
        ) { item ->
            when (item.itemId) {
                1 -> showEditMeaningDialog(context, word)
                2 -> showDeleteDialog(context, word)
            }
        }
    }

    fun showEditMeaningDialog(context: Context, word: Word): OlDialog {
        var editText: AppCompatEditText? = null
        val dialog = context.createDialog(
            title = R.string.words_edit_meaning,
            action = R.string.save,
            onAction = {
                val newMeaning = editText?.text?.toString()?.trim().orEmpty()
                val updated = word.copy(meaning = newMeaning, meaningSource = "user")
                onWordUpdated?.invoke(updated)
            }
        ) { container ->
            val dialogBinding = DialogEditMeaningBinding.inflate(
                LayoutInflater.from(container.context),
                container,
                false
            )
            editText = dialogBinding.etMeaning.apply {
                setText(word.meaning)
                setSelection(word.meaning.length)
                post {
                    requestFocus()
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
                }
            }
            dialogBinding.root
        }
        dialog.showRespectingStatusBar()
        return dialog
    }

    fun showDeleteDialog(context: Context, word: Word): OlDialog {
        return context.showMessageDialog(
            title = R.string.words_delete,
            message = context.getString(R.string.words_delete_confirm, word.text),
            action = R.string.words_delete,
        ) {
            onWordDeleted?.invoke(word)
        }
    }
}

/**
 * The reveal's meaning line when a practice prompt replaces the word (#603): the
 * item's answer leads, so the user can check themselves (review R1 #10). Pure
 * and deterministic so the composition is unit-tested.
 */
internal fun revealMeaningWithAnswer(answer: String?, meaning: String): String = when {
    answer.isNullOrBlank() -> meaning
    meaning.isBlank() -> answer
    else -> "$answer · $meaning"
}
