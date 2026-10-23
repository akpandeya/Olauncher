package app.olauncher.ui.today.cards

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import app.olauncher.R
import app.olauncher.helper.Haptics
import app.olauncher.data.Prefs
import app.olauncher.data.store.Leitner
import app.olauncher.data.store.Word
import app.olauncher.data.store.WordsStore
import app.olauncher.databinding.CardWordsBinding
import app.olauncher.ui.today.TodayCard
import app.olauncher.ui.today.WordReveal
import java.time.LocalDate

object WordsCardLogic {

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

    fun formatAllDone(context: Context, total: Int): String {
        return context.getString(R.string.words_all_done, formatWordsCount(context, total))
    }

    fun showReview(due: Int): Boolean = due > 0

    fun showCardMeaning(meaning: String): Boolean = meaning.isNotBlank()

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
 * Stateless card entry. Per-view state (binding and [WordReveal]) lives in [WordsCardView],
 * which is stored on the card's root view, so it goes away with the view.
 */
object WordsCard : TodayCard {

    const val KEY = "words"
    const val DUE_LIMIT = 20

    override val key: String = KEY
    override val titleRes: Int = R.string.words
    override val emoji: String = "🇩🇪"

    var storeProvider: ((Context) -> WordsStore)? = null
    var epochDaySupplier: () -> Long = { LocalDate.now().toEpochDay() }

    fun getStore(context: Context): WordsStore {
        return storeProvider?.invoke(context) ?: WordsStore(context)
    }

    override fun isAvailable(context: Context): Boolean {
        return WordsCardLogic.isAvailable(Prefs(context).germanEnabled)
    }

    override fun createView(inflater: LayoutInflater, parent: ViewGroup, fragment: Fragment): View {
        val cardView = WordsCardView(CardWordsBinding.inflate(inflater, parent, false)) {
            val navController = fragment.findNavController()
            if (navController.currentDestination?.id == R.id.todayFragment) {
                navController.navigate(R.id.action_todayFragment_to_wordsFragment)
            }
        }
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
) {

    private var wordReveal: WordReveal? = null

    init {
        // The summary lines lead to the Words page, where every word and meaning can be edited.
        binding.tvFooter.setOnClickListener { Haptics.tick(it); onOpenWords() }
        binding.tvEmpty.setOnClickListener { Haptics.tick(it); onOpenWords() }
        binding.btnReview.setOnClickListener { Haptics.tick(it); onOpenWords() }
    }

    fun render() {
        val b = binding
        val context = b.root.context ?: return
        val store = WordsCard.getStore(context)
        val today = WordsCard.epochDaySupplier()

        val stats = store.stats(today)
        val dueWords = store.due(today, WordsCard.DUE_LIMIT)

        if (dueWords.isNotEmpty()) {
            val dueWord = dueWords.first()
            b.tvEmpty.isVisible = false
            b.wordReveal.root.isVisible = true
            b.tvFooter.isVisible = true
            b.tvFooter.text = WordsCardLogic.formatFooter(context, stats.total, stats.due, stats.learned)

            val helper = wordReveal ?: WordReveal(
                binding = b.wordReveal,
                onAnswer = { word, knewIt ->
                    val s = WordsCard.getStore(context)
                    val currentToday = WordsCard.epochDaySupplier()
                    val updated = if (knewIt) Leitner.knowIt(word, currentToday) else Leitner.again(word, currentToday)
                    s.update(updated)
                    render()
                },
                onWordUpdated = { updated ->
                    WordsCard.getStore(context).update(updated)
                    render()
                },
                onWordDeleted = { word ->
                    WordsCard.getStore(context).delete(word.id)
                    render()
                }
            ).also { wordReveal = it }

            helper.bind(dueWord)
            // The card always shows the saved meaning, one line, under the word.
            b.wordReveal.tvMeaning.isVisible = WordsCardLogic.showCardMeaning(dueWord.meaning)
            b.btnReview.isVisible = WordsCardLogic.showReview(stats.due)
            b.btnReview.text = context.getString(R.string.words_review, stats.due)
        } else {
            b.wordReveal.root.isVisible = false
            b.tvFooter.isVisible = false
            b.btnReview.isVisible = false
            b.tvEmpty.isVisible = true
            b.tvEmpty.isClickable = stats.total > 0
            b.tvEmpty.text = if (stats.total == 0) {
                context.getString(R.string.words_empty_state)
            } else {
                WordsCardLogic.formatAllDone(context, stats.total)
            }
        }
    }
}
