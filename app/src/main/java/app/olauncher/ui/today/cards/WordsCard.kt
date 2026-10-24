package app.olauncher.ui.today.cards

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import app.olauncher.R
import app.olauncher.helper.Haptics
import app.olauncher.helper.createChoiceDialog
import app.olauncher.data.Prefs
import app.olauncher.data.store.WordsStore
import app.olauncher.databinding.CardWordsBinding
import app.olauncher.ui.today.TodayCard
import java.time.LocalDate

/** Entries of the Words card long-press menu. */
enum class WordsCardMenuItem { REVIEW, WORDS, LISTS }

object WordsCardLogic {

    /** Long-press menu entries in display order; Review only when words are due. */
    fun menuItems(due: Int): List<WordsCardMenuItem> = buildList {
        if (showReview(due)) add(WordsCardMenuItem.REVIEW)
        add(WordsCardMenuItem.WORDS)
        add(WordsCardMenuItem.LISTS)
    }

    fun isAvailable(germanEnabled: Boolean): Boolean = germanEnabled

    fun formatWordsCount(context: Context, total: Int): String {
        return if (total == 1) {
            context.getString(R.string.words_count_single)
        } else {
            context.getString(R.string.words_count_plural, total)
        }
    }

    fun formatFooter(context: Context, total: Int, due: Int, learned: Int): String {
        return context.getString(
            R.string.words_footer,
            formatWordsCount(context, total),
            due,
            learned
        )
    }

    /** "10 words · 3 learned", the line under the due count. */
    fun formatCardLine(context: Context, total: Int, learned: Int): String =
        context.getString(R.string.words_card_line, formatWordsCount(context, total), learned)

    fun showReview(due: Int): Boolean = due > 0

    fun reviewLabel(template: String, due: Int): String = String.format(template, due)

    fun formatMeaning(context: Context, meaning: String, meaningSource: String): String {
        if (meaning.isBlank()) {
            return context.getString(R.string.word_row_no_meaning_yet)
        }
        return if (meaningSource.equals("wiktionary", ignoreCase = true)) {
            "$meaning${context.getString(R.string.words_wiktionary_suffix)}"
        } else {
            meaning
        }
    }
}

/**
 * Half-width card: the due count and the size of the word list. Tap opens the review when words
 * are due, else the Words page. Per-view state lives in [WordsCardView], stored on the root view.
 */
object WordsCard : TodayCard {

    const val KEY = "words"

    override val key: String = KEY
    override val titleRes: Int = R.string.words
    override val emoji: String = "🇩🇪"
    override val halfWidth: Boolean = true

    var storeProvider: ((Context) -> WordsStore)? = null
    var epochDaySupplier: () -> Long = { LocalDate.now().toEpochDay() }

    fun getStore(context: Context): WordsStore {
        return storeProvider?.invoke(context) ?: WordsStore(context)
    }

    override fun title(context: Context): CharSequence = context.getString(R.string.words_card_title)

    override fun isAvailable(context: Context): Boolean {
        return WordsCardLogic.isAvailable(Prefs(context).germanEnabled)
    }

    override fun createView(inflater: LayoutInflater, parent: ViewGroup, fragment: Fragment): View {
        val cardView = WordsCardView(
            CardWordsBinding.inflate(inflater, parent, false),
            onOpenWords = {
                val navController = fragment.findNavController()
                if (navController.currentDestination?.id == R.id.todayFragment) {
                    navController.navigate(R.id.action_todayFragment_to_wordsFragment)
                }
            },
            onOpenLists = {
                val navController = fragment.findNavController()
                if (navController.currentDestination?.id == R.id.todayFragment) {
                    navController.navigate(R.id.action_todayFragment_to_wordListsFragment)
                }
            },
            onReview = {
                val navController = fragment.findNavController()
                if (navController.currentDestination?.id == R.id.todayFragment) {
                    navController.navigate(R.id.action_todayFragment_to_reviewFragment)
                }
            },
        )
        cardView.binding.root.tag = cardView
        cardView.render()
        return cardView.binding.root
    }

    override fun onResume(view: View) {
        (view.tag as? WordsCardView)?.render()
    }
}

/** Holds the state of one Words card view. */
internal class WordsCardView(
    val binding: CardWordsBinding,
    private val onOpenWords: () -> Unit = {},
    private val onReview: () -> Unit = {},
    private val onOpenLists: () -> Unit = {},
) {

    private var due = 0

    init {
        binding.root.setOnClickListener {
            Haptics.tick(it)
            if (WordsCardLogic.showReview(due)) onReview() else onOpenWords()
        }
        binding.tvOpen.setOnClickListener {
            Haptics.tick(it)
            onOpenWords()
        }
        binding.root.setOnLongClickListener { view ->
            val context = view.context
            val items = WordsCardLogic.menuItems(due)
            context.createChoiceDialog(
                title = R.string.words,
                items = items.map { context.getString(labelRes(it)) },
                onPick = { index ->
                    when (items[index]) {
                        WordsCardMenuItem.REVIEW -> onReview()
                        WordsCardMenuItem.WORDS -> onOpenWords()
                        WordsCardMenuItem.LISTS -> onOpenLists()
                    }
                },
            ).show()
            true
        }
    }

    private fun labelRes(item: WordsCardMenuItem): Int = when (item) {
        WordsCardMenuItem.REVIEW -> R.string.words_review_title
        WordsCardMenuItem.WORDS -> R.string.words
        WordsCardMenuItem.LISTS -> R.string.word_lists_title
    }

    fun render() {
        val b = binding
        val context = b.root.context ?: return
        val stats = WordsCard.getStore(context).stats(WordsCard.epochDaySupplier())
        due = stats.due
        b.tvDue.text = stats.due.toString()
        b.tvDueLabel.setText(R.string.words_card_due)
        b.tvLine.text = if (stats.total == 0) {
            context.getString(R.string.words_card_none)
        } else {
            WordsCardLogic.formatCardLine(context, stats.total, stats.learned)
        }
    }
}
