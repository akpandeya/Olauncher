package app.olauncher.ui.words

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import app.olauncher.R
import app.olauncher.data.store.ReviewComebacks
import app.olauncher.data.store.ReviewEvent
import app.olauncher.data.store.ReviewEventsStore
import app.olauncher.data.store.ReviewSessionLogic
import app.olauncher.data.store.Word
import app.olauncher.databinding.FragmentReviewBinding
import app.olauncher.helper.Haptics
import app.olauncher.ui.BaseFragment
import app.olauncher.ui.today.WordReveal
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

    private var wordReveal: WordReveal? = null
    private var queue: List<Word> = emptyList()
    private var answered = 0
    private var sessionEvents: List<ReviewEvent> = emptyList()
    private var eventsBeforeSession: List<ReviewEvent> = emptyList()

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
        wordReveal = WordReveal(
            binding = binding.wordReveal,
            onAnswer = { word, knewIt ->
                grade(word, if (knewIt) ReviewEvent.Result.KNEW else ReviewEvent.Result.AGAIN)
            },
            onAlmost = { word ->
                grade(word, ReviewEvent.Result.ALMOST)
            },
            alwaysShowContext = true,
        ).also { it.showAlmostButton(true) }
    }

    override fun onResume() {
        super.onResume()
        startSession()
    }

    private fun startSession() {
        val context = context ?: return
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
        Haptics.tick(binding.root)
        val store = WordsCard.getStore(context)
        val today = WordsCard.epochDaySupplier()
        val updated = ReviewSessionLogic.grade(word, result, today)
        store.update(updated)
        val event = ReviewEventsStore(context).append(
            wordId = word.id,
            result = result,
            boxBefore = word.box,
            boxAfter = updated.box,
            reviewedAt = System.currentTimeMillis(),
        )
        sessionEvents = sessionEvents + event
        answered++
        render()
    }

    private fun render() {
        val b = _binding ?: return
        val context = context ?: return
        val store = WordsCard.getStore(context)
        val remaining = queue.size - answered

        b.tvRemaining.text = if (remaining > 0) {
            context.getString(R.string.words_review_remaining, remaining)
        } else {
            ""
        }
        b.reviewCard.isVisible = remaining > 0
        b.summaryContainer.isVisible = remaining == 0 && sessionEvents.isNotEmpty()
        b.tvEmpty.isVisible = remaining == 0 && sessionEvents.isEmpty()
        if (remaining == 0 && sessionEvents.isEmpty()) {
            b.tvEmpty.text = context.getString(
                R.string.words_all_done,
                WordsCardLogic.formatWordsCount(context, store.all().size),
            )
        }

        if (remaining > 0) {
            val word = queue[answered]
            // A lookup may have refilled the meaning since the session started.
            wordReveal?.bind(store.get(word.id) ?: word, startRevealed = false)
            return
        }

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

    override fun onDestroyView() {
        super.onDestroyView()
        wordReveal = null
        _binding = null
    }
}
