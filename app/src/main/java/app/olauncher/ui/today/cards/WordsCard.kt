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
import app.olauncher.data.store.Leitner
import app.olauncher.data.store.ReviewEvent
import app.olauncher.data.store.ReviewEventsStore
import app.olauncher.data.store.StudyGrid
import app.olauncher.data.store.Word
import app.olauncher.data.store.WordsStore
import app.olauncher.data.store.WordStats
import app.olauncher.databinding.CardWordsBinding
import app.olauncher.ui.today.TodayCard
import app.olauncher.ui.today.attachPressScale
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

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

    /** The Words page header shows the Drills button whenever German is on, like the Words card. */
    fun showDrillsButton(germanEnabled: Boolean): Boolean = isAvailable(germanEnabled)

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

    /**
     * What the right side and footer of the full-width card show. [nextWord] is the next due word
     * (null when nothing is due). The meaning is never part of the Today card, so it does not give away the answer.
     */
    data class Lines(
        val nextWord: String?,
        val showAllDone: Boolean,
        val showHint: Boolean,
        val showCounts: Boolean,
        val showBoxBar: Boolean = !showHint,
        val showDayStrip: Boolean = !showHint,
    ) {
        val showNext: Boolean get() = nextWord != null
    }

    fun lines(total: Int, due: Int, next: Word?): Lines {
        val noWords = total == 0
        val nextWord = if (!noWords && due > 0) next?.text?.trim()?.takeIf { it.isNotEmpty() } else null
        return Lines(
            nextWord = nextWord,
            showAllDone = !noWords && due == 0,
            showHint = noWords,
            showCounts = !noWords,
            showBoxBar = !noWords,
            showDayStrip = !noWords,
        )
    }

    fun dayStrip(
        events: List<ReviewEvent>,
        today: LocalDate,
        zone: ZoneId,
        locale: Locale = Locale.getDefault(),
    ): List<StudyGrid.DayStripCell> = StudyGrid.buildDayStrip(events, today, zone, locale)

    fun boxCounts(words: List<Word>): List<Int> = Leitner.boxCounts(words)
    fun segmentWidths(counts: List<Int>, totalWidth: Float = 1f): List<Float> = Leitner.segmentWidths(counts, totalWidth)
    @JvmName("segmentWidthsForWords")
    fun segmentWidths(words: List<Word>, totalWidth: Float = 1f): List<Float> = Leitner.segmentWidths(words, totalWidth)

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
 * Full-width card: the due count, the next due word with its meaning, and the size of the word
 * list. Tap opens the review when words are due, else the Words page. Per-view state lives in [WordsCardView], stored on the root view.
 */
object WordsCard : TodayCard {

    const val KEY = "words"

    override val key: String = KEY
    override val titleRes: Int = R.string.words
    override val emoji: String = "🇩🇪"

    var storeProvider: ((Context) -> WordsStore)? = null
    var reviewEventsProvider: ((Context) -> List<ReviewEvent>)? = null
    var epochDaySupplier: () -> Long = { LocalDate.now().toEpochDay() }

    fun getStore(context: Context): WordsStore {
        return storeProvider?.invoke(context) ?: WordsStore(context)
    }

    fun getReviewEvents(context: Context): List<ReviewEvent> {
        return reviewEventsProvider?.invoke(context) ?: ReviewEventsStore(context).all()
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
        cardView.binding.root.attachPressScale()
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
            if (WordsCardLogic.showReview(due)) openReview() else onOpenWords()
        }
        binding.tvOpen.setOnClickListener {
            Haptics.tick(it)
            onOpenWords()
        }
        // The digest announcement opens the review and clears itself (#604).
        binding.tvDigest.setOnClickListener {
            Haptics.tick(it)
            openReview()
        }
        binding.root.setOnLongClickListener { view ->
            showWordsMenu(view.context, due, { openReview() }, onOpenWords, onOpenLists)
            true
        }
    }

    /** Opens the review and disarms the one-shot digest announcement, if any. */
    private fun openReview() {
        val context = binding.root.context ?: return
        Prefs(context).aiDigestNew = 0
        onReview()
    }

    fun render() {
        val b = binding
        val context = b.root.context ?: return
        val stats = WordsCard.getStore(context).stats(WordsCard.epochDaySupplier())
        due = stats.due
        val lines = WordsCardLogic.lines(stats.total, stats.due, WordsCard.getStore(context).due(WordsCard.epochDaySupplier(), 1).firstOrNull())
        b.tvDue.text = stats.due.toString()
        b.tvDueLabel.setText(if (stats.total == 0) R.string.words_card_none else R.string.words_card_due)
        b.tvNextWord.visibility = if (lines.showNext) View.VISIBLE else View.GONE
        b.tvNextWord.text = lines.nextWord?.let { context.getString(R.string.words_card_next, it) }
        b.tvSide.visibility = if (lines.showAllDone || lines.showHint) View.VISIBLE else View.GONE
        b.tvSide.setText(if (lines.showHint) R.string.words_card_hint else R.string.words_card_all_done)
        b.boxBar.visibility = if (lines.showBoxBar) View.VISIBLE else View.GONE
        if (lines.showBoxBar) {
            b.boxBar.setBoxCounts(stats.boxCounts)
        }
        b.dayStrip.visibility = if (lines.showDayStrip) View.VISIBLE else View.GONE
        if (lines.showDayStrip) {
            val events = WordsCard.getReviewEvents(context)
            val todayDate = LocalDate.ofEpochDay(WordsCard.epochDaySupplier())
            val zone = ZoneId.systemDefault()
            b.dayStrip.setDays(StudyGrid.buildDayStrip(events, todayDate, zone))
        }
        b.tvLine.visibility = if (lines.showCounts) View.VISIBLE else View.INVISIBLE
        b.tvLine.text = WordsCardLogic.formatCardLine(context, stats.total, stats.learned)
        val digestNew = Prefs(context).aiDigestNew
        b.tvDigest.visibility = if (digestNew > 0) View.VISIBLE else View.GONE
        if (digestNew > 0) {
            b.tvDigest.text = context.getString(R.string.words_digest_announcement, digestNew)
        }
    }
}

/** The long-press menu used by the Words card. */
private fun showWordsMenu(
    context: Context,
    due: Int,
    onReview: () -> Unit,
    onOpenWords: () -> Unit,
    onOpenLists: () -> Unit,
) {
    val items = WordsCardLogic.menuItems(due)
    context.createChoiceDialog(
        title = R.string.words,
        items = items.map {
            context.getString(
                when (it) {
                    WordsCardMenuItem.REVIEW -> R.string.words_review_title
                    WordsCardMenuItem.WORDS -> R.string.words
                    WordsCardMenuItem.LISTS -> R.string.word_lists_title
                },
            )
        },
        onPick = { index ->
            when (items[index]) {
                WordsCardMenuItem.REVIEW -> onReview()
                WordsCardMenuItem.WORDS -> onOpenWords()
                WordsCardMenuItem.LISTS -> onOpenLists()
            }
        },
    ).show()
}
