package app.olauncher.ui.today.cards

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import app.olauncher.R
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

    fun formatWordsCount(total: Int): String {
        return if (total == 1) "1 word" else "$total words"
    }

    fun formatWordsCount(context: Context, total: Int): String {
        return if (total == 1) {
            context.getString(R.string.words_count_single)
        } else {
            context.getString(R.string.words_count_plural, total)
        }
    }

    fun formatFooter(total: Int, due: Int, learned: Int): String {
        return "${formatWordsCount(total)} · $due due · $learned learned"
    }

    fun formatFooter(context: Context, total: Int, due: Int, learned: Int): String {
        return context.getString(
            R.string.words_footer,
            formatWordsCount(context, total),
            due,
            learned
        )
    }

    fun formatAllDone(total: Int, learned: Int): String {
        return "All done for today · ${formatWordsCount(total)} · $learned learned"
    }

    fun formatAllDone(context: Context, total: Int, learned: Int): String {
        return context.getString(
            R.string.words_all_done,
            formatWordsCount(context, total),
            learned
        )
    }

    fun formatMeaning(meaning: String, meaningSource: String): String {
        if (meaning.isBlank()) return "No meaning yet"
        return if (meaningSource.equals("wiktionary", ignoreCase = true)) {
            "$meaning · Wiktionary"
        } else {
            meaning
        }
    }

    fun formatMeaning(context: Context, meaning: String, meaningSource: String): String {
        if (meaning.isBlank()) {
            return context.getString(R.string.words_no_meaning)
        }
        return if (meaningSource.equals("wiktionary", ignoreCase = true)) {
            "$meaning${context.getString(R.string.words_wiktionary_suffix)}"
        } else {
            meaning
        }
    }
}

object WordsCard : TodayCard {

    const val KEY = "words"
    const val DUE_LIMIT = 20

    override val key: String = KEY
    override val titleRes: Int = R.string.words

    var storeProvider: ((Context) -> WordsStore)? = null
    var epochDaySupplier: () -> Long = { LocalDate.now().toEpochDay() }

    internal var binding: CardWordsBinding? = null
    private var wordReveal: WordReveal? = null

    fun getStore(context: Context): WordsStore {
        return storeProvider?.invoke(context) ?: WordsStore(context)
    }

    override fun isAvailable(context: Context): Boolean {
        return WordsCardLogic.isAvailable(Prefs(context).germanEnabled)
    }

    override fun createView(inflater: LayoutInflater, parent: ViewGroup, fragment: Fragment): View {
        val viewBinding = CardWordsBinding.inflate(inflater, parent, false)
        binding = viewBinding

        viewBinding.root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                if (binding === viewBinding) {
                    binding = null
                    wordReveal = null
                }
            }
        })

        render()
        return viewBinding.root
    }

    override fun onResume() {
        render()
    }

    fun render() {
        val b = binding ?: return
        val context = b.root.context ?: return
        val store = getStore(context)
        val today = epochDaySupplier()

        val stats = store.stats(today)
        val dueWords = store.due(today, DUE_LIMIT)

        if (dueWords.isNotEmpty()) {
            val dueWord = dueWords.first()
            b.tvEmpty.isVisible = false
            b.wordReveal.root.isVisible = true
            b.tvFooter.isVisible = true
            b.tvFooter.text = WordsCardLogic.formatFooter(context, stats.total, stats.due, stats.learned)

            val helper = wordReveal ?: WordReveal(
                binding = b.wordReveal,
                onAnswer = { word, knewIt ->
                    val s = getStore(context)
                    val currentToday = epochDaySupplier()
                    val updated = if (knewIt) Leitner.knowIt(word, currentToday) else Leitner.again(word, currentToday)
                    s.update(updated)
                    render()
                },
                onWordUpdated = { updated ->
                    getStore(context).update(updated)
                    render()
                },
                onWordDeleted = { word ->
                    getStore(context).delete(word.id)
                    render()
                }
            ).also { wordReveal = it }

            helper.bind(dueWord)
        } else {
            b.wordReveal.root.isVisible = false
            b.tvFooter.isVisible = false
            b.tvEmpty.isVisible = true
            b.tvEmpty.text = if (stats.total == 0) {
                context.getString(R.string.words_empty_state)
            } else {
                WordsCardLogic.formatAllDone(context, stats.total, stats.learned)
            }
        }
    }
}
