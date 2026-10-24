package app.olauncher.ui.words

import android.os.Bundle
import android.os.SystemClock
import android.view.Menu
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import app.olauncher.R
import app.olauncher.data.store.ReviewComebacks
import app.olauncher.data.store.ReviewEvent
import app.olauncher.data.store.ReviewEventsStore
import app.olauncher.data.store.ReviewPreview
import app.olauncher.data.store.ReviewSessionLogic
import app.olauncher.data.store.Word
import app.olauncher.databinding.FragmentReviewBinding
import app.olauncher.helper.Haptics
import app.olauncher.helper.showPopupMenu
import app.olauncher.ui.BaseFragment
import app.olauncher.ui.today.cards.WordsCard
import app.olauncher.ui.today.cards.WordsCardLogic
import java.time.LocalDate
import java.time.ZoneId

/**
 * Full-screen review session for due words: flashcard front (word + saved
 * context), tap reveals the meaning, then three grades. Every grade updates
 * the word and appends to the review event log; the queue is the due snapshot
 * from session start, and the page ends on a one-line summary.
 */
class ReviewFragment : BaseFragment() {

    private var _binding: FragmentReviewBinding? = null
    private val binding get() = _binding!!

    private var revealed = false
    private var queue: List<Word> = emptyList()
    private var answered = 0
    private var sessionEvents: List<ReviewEvent> = emptyList()
    private var eventsBeforeSession: List<ReviewEvent> = emptyList()
    private var lastGradeAt = 0L
    private var sessionStarted = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentReviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnShowAnswer.setOnClickListener {
            Haptics.tick(it)
            setRevealed(true)
        }
        binding.tvWord.setOnClickListener {
            Haptics.tick(it)
            setRevealed(!revealed)
        }
        binding.tvWord.setOnLongClickListener {
            val word = currentWord() ?: return@setOnLongClickListener false
            Haptics.longPress(it)
            showWordPopup(it, word)
            true
        }
        binding.btnNo.setOnClickListener { currentWord()?.let { grade(it, ReviewEvent.Result.AGAIN) } }
        binding.btnAlmost.setOnClickListener { currentWord()?.let { grade(it, ReviewEvent.Result.ALMOST) } }
        binding.btnKnew.setOnClickListener { currentWord()?.let { grade(it, ReviewEvent.Result.KNEW) } }
        binding.btnAddToList.setOnClickListener {
            val word = currentWord() ?: return@setOnClickListener
            Haptics.tick(it)
            WordDialogs.showListsDialog(it.context, word) { refreshQueueFromStore() }
        }
        binding.btnBack.setOnClickListener { leave(it) }
        binding.btnOpenWords.setOnClickListener { openWords(it) }

        binding.btnDone.setOnClickListener { leave(it) }

        // One session per screen visit: resuming (e.g. back from another app) or
        // recreating the view keeps the queue and the summary.
        revealed = savedInstanceState?.getBoolean(KEY_REVEALED) == true
        if (savedInstanceState?.getBoolean(KEY_STARTED) == true && !sessionStarted) {
            restoreSession(savedInstanceState)
        }
        if (sessionStarted) render() else startSession()
    }

    // The fragment is rebuilt on rotation / font-size / language change; without
    // this the session restarts and graded words (no longer due) show "all done".
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (!sessionStarted) return
        outState.putBoolean(KEY_STARTED, true)
        outState.putBoolean(KEY_REVEALED, revealed)
        outState.putInt(KEY_ANSWERED, answered)
        outState.putLongArray(KEY_QUEUE_IDS, queue.map { it.id }.toLongArray())
        outState.putLongArray(KEY_EVENT_IDS, sessionEvents.map { it.id }.toLongArray())
    }

    private fun restoreSession(state: Bundle) {
        val context = context ?: return
        val store = WordsCard.getStore(context)
        sessionStarted = true
        queue = (state.getLongArray(KEY_QUEUE_IDS) ?: LongArray(0)).toList().mapNotNull { store.get(it) }
        answered = state.getInt(KEY_ANSWERED).coerceAtMost(queue.size)
        val split = ReviewSessionLogic.splitEvents(
            ReviewEventsStore(context).all(),
            (state.getLongArray(KEY_EVENT_IDS) ?: LongArray(0)).toSet(),
        )
        sessionEvents = split.session
        eventsBeforeSession = split.before
    }

    private fun startSession() {
        val context = context ?: return
        sessionStarted = true
        val store = WordsCard.getStore(context)
        val today = WordsCard.epochDaySupplier()
        queue = ReviewSessionLogic.queue(store.all(), today)
        answered = 0
        sessionEvents = emptyList()
        eventsBeforeSession = ReviewEventsStore(context).all()
        render()
    }

    private fun grade(word: Word, result: String) {
        val context = context ?: return
        // A tap lands on the *next* word once render() advances; dropping taps
        // within the debounce keeps rapid tapping from grading unseen words.
        val now = SystemClock.uptimeMillis()
        if (now - lastGradeAt < GRADE_DEBOUNCE_MS) return
        lastGradeAt = now
        Haptics.tick(binding.root)
        val store = WordsCard.getStore(context)
        val today = WordsCard.epochDaySupplier()
        // The queue holds the copy from session start; grading must start from the
        // stored word so list changes and background lookups made meanwhile survive.
        val current = store.get(word.id) ?: word
        val event = ReviewSessionLogic.gradeAndLog(
            current, result, today, System.currentTimeMillis(), store::update, ReviewEventsStore(context),
        )
        sessionEvents = sessionEvents + event
        answered++
        revealed = false
        render()
    }

    private fun render() {
        val b = _binding ?: return
        val context = context ?: return
        val store = WordsCard.getStore(context)
        val remaining = queue.size - answered
        val finished = remaining == 0

        b.tvRemaining.isVisible = !finished
        b.tvRemaining.text = context.getString(R.string.words_review_progress, remaining, queue.size)
        b.reviewProgress.max = queue.size.coerceAtLeast(1)
        b.reviewProgress.progress = answered
        b.reviewProgress.isVisible = !finished
        // Back lives in the header while reviewing; the summary page ends on Done.
        b.btnBack.isVisible = !finished || sessionEvents.isEmpty()
        b.btnDone.isVisible = finished && sessionEvents.isNotEmpty()
        b.reviewCard.isVisible = remaining > 0
        b.btnAddToList.isVisible = remaining > 0
        b.summaryContainer.isVisible = remaining == 0 && sessionEvents.isNotEmpty()
        b.emptyContainer.isVisible = remaining == 0 && sessionEvents.isEmpty()
        if (remaining == 0 && sessionEvents.isEmpty()) {
            b.tvEmpty.text = context.getString(
                R.string.words_all_done,
                WordsCardLogic.formatWordsCount(context, store.all().size),
            )
        }

        if (remaining > 0) {
            val word = queue[answered]
            // A lookup may have refilled the meaning since the session started.
            bindWord(store.get(word.id) ?: word)
            return
        }
        b.btnShowAnswer.isVisible = false
        b.gradeRow.isVisible = false

        if (sessionEvents.isEmpty()) return
        val summary = ReviewSessionLogic.summarize(sessionEvents)
        b.tvSummary.text = context.getString(
            R.string.words_review_summary, summary.knew, summary.almost, summary.again,
        )
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        if (ReviewComebacks.isComeback(eventsBeforeSession, today, zone)) {
            val count = ReviewComebacks.count(eventsBeforeSession, today, zone)
            b.tvComeback.text = context.getString(R.string.words_review_comeback, count)
            b.tvComeback.isVisible = true
        } else {
            b.tvComeback.isVisible = false
        }
    }

    private fun currentWord(): Word? = queue.getOrNull(answered)

    private fun setRevealed(value: Boolean) {
        revealed = value
        currentWord()?.let { bindWord(WordsCard.getStore(requireContext()).get(it.id) ?: it) }
    }

    /** Front: the word and its saved context. Revealed: meaning, example and the three graded buttons. */
    private fun bindWord(word: Word) {
        val b = _binding ?: return
        val context = context ?: return
        val today = WordsCard.epochDaySupplier()
        b.tvWord.text = word.text
        b.tvQuestionContext.text = word.context
        b.tvQuestionContext.isVisible = !revealed && word.context.isNotBlank()
        b.answerGroup.isVisible = revealed
        b.tvMeaning.text = if (word.meaning.isBlank()) {
            context.getString(R.string.words_review_no_meaning)
        } else {
            WordsCardLogic.formatMeaning(context, word.meaning, word.meaningSource)
        }
        b.tvExample.text = word.context
        b.tvExampleLabel.isVisible = word.context.isNotBlank()
        b.tvExample.isVisible = word.context.isNotBlank()
        b.btnShowAnswer.isVisible = !revealed
        b.gradeRow.isVisible = revealed
        if (revealed) {
            b.tvNoInterval.text = intervalText(context, ReviewPreview.interval(word, ReviewEvent.Result.AGAIN, today))
            b.tvAlmostInterval.text = intervalText(context, ReviewPreview.interval(word, ReviewEvent.Result.ALMOST, today))
            b.tvKnewInterval.text = intervalText(context, ReviewPreview.interval(word, ReviewEvent.Result.KNEW, today))
        }
    }

    private fun intervalText(context: android.content.Context, interval: ReviewPreview.Interval): String = when (interval) {
        ReviewPreview.Interval.AgainTomorrow -> context.getString(R.string.words_interval_again)
        ReviewPreview.Interval.SameBox -> context.getString(R.string.words_interval_same)
        ReviewPreview.Interval.Learned -> context.getString(R.string.words_interval_learned)
        is ReviewPreview.Interval.Promoted ->
            if (interval.days <= 1) {
                context.getString(R.string.words_interval_box_tomorrow, interval.box)
            } else {
                context.getString(R.string.words_interval_box_days, interval.box, interval.days.toInt())
            }
    }

    private fun showWordPopup(anchor: View, word: Word) {
        val context = anchor.context
        anchor.showPopupMenu(
            configure = { menu ->
                menu.add(Menu.NONE, 1, Menu.NONE, R.string.words_edit)
                menu.add(Menu.NONE, 2, Menu.NONE, R.string.words_delete)
            }
        ) { item ->
            when (item.itemId) {
                1 -> WordDialogs.showEditDialog(context, word, { viewLifecycleOwnerOrNull() }) { refreshQueueFromStore() }
                2 -> WordDialogs.showDeleteDialog(context, word) { refreshQueueFromStore() }
            }
        }
    }

    private fun viewLifecycleOwnerOrNull() = if (_binding != null) viewLifecycleOwner else null

    /** Re-reads the queue after an edit or delete; a word removed from before the cursor shifts it. */
    private fun refreshQueueFromStore() {
        val context = context ?: return
        val store = WordsCard.getStore(context)
        var newAnswered = answered
        val fresh = ArrayList<Word>()
        queue.forEachIndexed { index, old ->
            val current = store.get(old.id)
            if (current != null) fresh.add(current) else if (index < answered) newAnswered--
        }
        queue = fresh
        answered = newAnswered.coerceAtLeast(0)
        render()
    }

    private fun leave(view: View) {
        Haptics.tick(view)
        // Ignore a second tap delivered before the first pop lands.
        if (findNavController().currentDestination?.id == R.id.reviewFragment) {
            findNavController().navigateUp()
        }
    }

    /** "Open Words" on the summary: back to Words if it is underneath, else go there (e.g. from Today). */
    private fun openWords(view: View) {
        Haptics.tick(view)
        val nav = findNavController()
        if (nav.currentDestination?.id != R.id.reviewFragment) return
        if (!nav.popBackStack(R.id.wordsFragment, false)) {
            nav.navigate(R.id.action_reviewFragment_to_wordsFragment)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val GRADE_DEBOUNCE_MS = 300L
        const val KEY_STARTED = "review_started"
        const val KEY_REVEALED = "review_revealed"
        const val KEY_ANSWERED = "review_answered"
        const val KEY_QUEUE_IDS = "review_queue_ids"
        const val KEY_EVENT_IDS = "review_event_ids"
    }
}
