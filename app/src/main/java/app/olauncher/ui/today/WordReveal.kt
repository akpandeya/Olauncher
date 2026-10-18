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
import app.olauncher.data.store.Word
import app.olauncher.databinding.DialogEditMeaningBinding
import app.olauncher.databinding.ViewWordRevealBinding
import app.olauncher.helper.OlDialog
import app.olauncher.helper.createDialog
import app.olauncher.helper.showMessageDialog
import app.olauncher.helper.showPopupMenu
import app.olauncher.ui.today.cards.WordsCardLogic

class WordReveal(
    val binding: ViewWordRevealBinding,
    var onAnswer: ((word: Word, knewIt: Boolean) -> Unit)? = null,
    var onWordUpdated: ((word: Word) -> Unit)? = null,
    var onWordDeleted: ((word: Word) -> Unit)? = null,
) {
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
    }

    fun bind(word: Word, startRevealed: Boolean? = null) {
        val keepReveal = startRevealed ?: (currentWord?.id == word.id && isRevealed)
        currentWord = word
        binding.tvWord.text = word.text
        binding.tvMeaning.text = WordsCardLogic.formatMeaning(binding.root.context, word.meaning, word.meaningSource)
        binding.tvContext.text = word.context
        setRevealed(keepReveal)
    }

    fun setRevealed(revealed: Boolean) {
        isRevealed = revealed
        val word = currentWord
        binding.tvMeaning.isVisible = revealed
        binding.tvContext.isVisible = revealed && (word != null && word.context.isNotBlank())
        binding.buttonsContainer.isVisible = revealed
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

    companion object {
        fun inflate(
            inflater: LayoutInflater,
            parent: ViewGroup?,
            attachToParent: Boolean = false,
            onAnswer: ((Word, Boolean) -> Unit)? = null,
            onWordUpdated: ((Word) -> Unit)? = null,
            onWordDeleted: ((Word) -> Unit)? = null,
        ): WordReveal {
            val binding = ViewWordRevealBinding.inflate(inflater, parent, attachToParent)
            return WordReveal(binding, onAnswer, onWordUpdated, onWordDeleted)
        }
    }
}
