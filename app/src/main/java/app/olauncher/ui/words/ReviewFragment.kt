package app.olauncher.ui.words

import android.os.Bundle
import android.os.SystemClock
import android.view.Menu
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.lifecycleScope
import app.olauncher.R
import app.olauncher.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.olauncher.data.store.AiProviderConfig
import app.olauncher.data.store.AiProviders
import app.olauncher.data.store.KeySource
import app.olauncher.data.store.KeyVault
import app.olauncher.data.store.PracticeGeneration
import app.olauncher.data.store.PracticeGenerator
import app.olauncher.data.store.PracticeItemsStore
import app.olauncher.data.store.PracticeItem
import app.olauncher.data.store.PracticeQueue
import app.olauncher.data.store.PracticeRotation
import app.olauncher.data.store.SharedOfflineGermanStore
import app.olauncher.data.store.ReviewComebacks
import app.olauncher.data.store.ReviewEvent
import app.olauncher.data.store.ReviewEventsStore
import app.olauncher.data.store.ReviewPreview
import app.olauncher.data.store.ReviewSessionLogic
import app.olauncher.data.store.ReviewSummaryItem
import app.olauncher.data.store.ReviewSummaryLogic
import app.olauncher.data.store.Word
import app.olauncher.databinding.FragmentReviewBinding
import app.olauncher.databinding.ItemSummaryRowBinding
import app.olauncher.helper.Haptics
import app.olauncher.helper.MotionPolicy
import app.olauncher.helper.SystemMotion
import app.olauncher.helper.showPopupMenu
import app.olauncher.ui.BaseFragment
import app.olauncher.ui.today.attachPressScale
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
    private var progressAnimated = false
    private var practiceItems: List<PracticeItem> = emptyList()
    internal var genderResolver: ((String) -> String?)? = null

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
        // Three tones for review buttons: No is rose, Almost is sand, Knew it is sage (#508).
        binding.tvNoLabel.setTextColor(ContextCompat.getColor(view.context, ReviewButtonTones.colorResForResult(ReviewEvent.Result.AGAIN)))
        binding.tvAlmostLabel.setTextColor(ContextCompat.getColor(view.context, ReviewButtonTones.colorResForResult(ReviewEvent.Result.ALMOST)))
        binding.tvKnewLabel.setTextColor(ContextCompat.getColor(view.context, ReviewButtonTones.colorResForResult(ReviewEvent.Result.KNEW)))
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
        binding.reviewCard.attachPressScale()

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
        practiceItems = PracticeItemsStore(context).all()
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
        practiceItems = PracticeItemsStore(context).all()
        generateOnDemandIfNeeded()
    }

    /**
     * The on-demand half of the hybrid generation design (#601): when the session
     * starts with thin due pools, top them up inline before the first card, off
     * the UI thread. The first render waits behind the result — bounded by the
     * ~10 s on-demand ceiling, which the per-request read timeout enforces on
     * the connection (connect + read time; see review R2 C) — so the round
     * starts once, on the final pool, and never mutates mid-view
     * (review R1 #2). A gate that is off, a rejected key, a missing key or
     * nothing queued skips the call and renders immediately.
     */
    private fun generateOnDemandIfNeeded() {
        val context = context ?: return
        val appContext = context.applicationContext
        val prefs = Prefs(appContext)
        if (!prefs.aiPracticeEnabled || prefs.aiModel.isBlank() || prefs.aiKeyRejected) {
            render()
            return
        }
        if (PracticeQueue.thinWords(queue, practiceItems).isEmpty()) {
            render()
            return
        }
        _binding?.tvGenerating?.isVisible = true
        viewLifecycleOwner.lifecycleScope.launch {
            val generation = withContext(Dispatchers.IO) {
                val vault = KeyVault(appContext)
                val keySource = KeySource { vault.read() }
                if (keySource.get().isNullOrBlank()) {
                    PracticeGeneration.Skipped(queue, PracticeGeneration.Reason.NO_KEY)
                } else {
                    val config = AiProviderConfig(prefs.aiProvider, prefs.aiBaseUrl, prefs.aiModel)
                    val provider = AiProviders.create(config, keySource)
                    if (provider == null) {
                        PracticeGeneration.Skipped(queue, PracticeGeneration.Reason.NO_MODEL)
                    } else {
                        PracticeGenerator(
                            provider = provider,
                            store = PracticeItemsStore(appContext),
                            model = prefs.aiModel,
                            keySource = keySource,
                        ).onDemand(queue, prefs, LocalDate.now())
                    }
                }
            }
            val b = _binding ?: return@launch
            b.tvGenerating.isVisible = false
            // Generated and Partial both put items in the store (review R2 B):
            // reload so the round renders on what actually arrived.
            if (generation is PracticeGeneration.Generated || generation is PracticeGeneration.Partial) {
                practiceItems = PracticeItemsStore(appContext).all()
            }
            render()
        }
    }

    /** The rotating practice item for [word], or null when its pool is empty. */
    private fun currentPracticeItem(word: Word): PracticeItem? {
        val reviewCount = (eventsBeforeSession + sessionEvents).count { it.wordId == word.id }
        return PracticeRotation.pick(word, practiceItems, reviewCount)
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
        val animatorsEnabled = SystemMotion.isAnimationsEnabled(context)
        val duration = MotionPolicy.durationMs(MotionPolicy.TODAY_DURATION_MS, animatorsEnabled)
        if (!progressAnimated && animatorsEnabled && duration > 0L && answered > 0) {
            progressAnimated = true
            android.animation.ObjectAnimator.ofInt(b.reviewProgress, "progress", 0, answered).apply {
                this.duration = duration
                interpolator = android.view.animation.DecelerateInterpolator()
                start()
            }
        } else {
            if (!progressAnimated) progressAnimated = true
            b.reviewProgress.progress = answered
        }
        b.reviewProgress.isVisible = !finished
        // Back lives in the header while reviewing; the summary page ends on Done.
        b.btnBack.isVisible = !finished || sessionEvents.isEmpty()
        b.btnDone.isVisible = finished && sessionEvents.isNotEmpty()
        b.reviewCard.rotationY = 0f
        b.reviewCard.isVisible = remaining > 0
        b.btnAddToList.isVisible = remaining > 0
        b.summaryHeader.isVisible = remaining == 0 && sessionEvents.isNotEmpty()
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

        val eventMap = sessionEvents.associateBy { it.wordId }
        val summaryItems = queue.mapIndexed { index, word ->
            val event = eventMap[word.id]
            val result = event?.result ?: ReviewEvent.Result.KNEW
            val currentWord = store.get(word.id) ?: word
            ReviewSummaryItem(word = currentWord, result = result, sessionIndex = index)
        }
        val sortedItems = ReviewSummaryLogic.sort(summaryItems)

        b.summaryList.removeAllViews()
        for (item in sortedItems) {
            // The review side's thin adapter over the one shared row layout (#623):
            // the result→mark mapping comes from SummaryMarks, the same one the
            // drill summary uses.
            val rowBinding = ItemSummaryRowBinding.inflate(layoutInflater, b.summaryList, false)
            rowBinding.tvPrimary.text = item.word.text
            val mark = SummaryMarks.forReviewResult(item.result)
            rowBinding.ivMark.setImageResource(mark.icon)
            rowBinding.ivMark.setColorFilter(ContextCompat.getColor(context, mark.tint))
            when (item.result) {
                ReviewEvent.Result.KNEW, ReviewEvent.Result.ALMOST -> rowBinding.tvSecondary.isVisible = false
                ReviewEvent.Result.AGAIN -> {
                    val correction = ReviewSummaryLogic.formatCorrection(item.word.meaning, item.word.context)
                    rowBinding.tvSecondary.text = correction
                    rowBinding.tvSecondary.isVisible = correction.isNotEmpty()
                }
            }
            b.summaryList.addView(rowBinding.root)
        }
    }

    private fun currentWord(): Word? = queue.getOrNull(answered)

    private fun setRevealed(value: Boolean) {
        if (revealed == value) return
        val context = context ?: return
        val animatorsEnabled = SystemMotion.isAnimationsEnabled(context)
        val halfDuration = MotionPolicy.durationMs(MotionPolicy.FLIP_HALF_DURATION_MS, animatorsEnabled)
        val card = _binding?.reviewCard

        if (value && animatorsEnabled && halfDuration > 0L && card != null) {
            val distance = 8000f * resources.displayMetrics.density
            card.cameraDistance = distance
            card.animate()
                .rotationY(90f)
                .setDuration(halfDuration)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction {
                    revealed = true
                    currentWord()?.let { bindWord(WordsCard.getStore(context).get(it.id) ?: it) }
                    card.rotationY = -90f
                    card.animate()
                        .rotationY(0f)
                        .setDuration(halfDuration)
                        .setInterpolator(android.view.animation.DecelerateInterpolator())
                        .start()
                }
                .start()
        } else {
            revealed = value
            _binding?.reviewCard?.rotationY = 0f
            currentWord()?.let { bindWord(WordsCard.getStore(context).get(it.id) ?: it) }
        }
    }

    /** Front: the word and its saved context. Revealed: meaning, example and the three graded buttons. */
    private fun bindWord(word: Word) {
        val b = _binding ?: return
        val context = context ?: return
        val today = WordsCard.epochDaySupplier()
        val item = currentPracticeItem(word)
        if (item != null) {
            // The rotating practice item replaces the bare word on the front (#603);
            // its hint takes the context line, and its answer joins the reveal.
            b.tvWord.text = item.prompt
            b.tvQuestionContext.text = item.hint ?: ""
            b.tvItemAnswer.text = item.answer
        } else {
            val immediate = NounArticle.extractLeadingArticle(word.text) ?: genderResolver?.invoke(word.text)
            NounArticle.applyToTextView(b.tvWord, NounArticle.format(word.text, immediate))
            if (immediate == null && genderResolver == null && Prefs(context).germanOfflineDictionary) {
                val appContext = context.applicationContext
                viewLifecycleOwner.lifecycleScope.launch {
                    val found = withContext(Dispatchers.IO) { SharedOfflineGermanStore.get(appContext).gender(word.text) }
                    val current = _binding
                    if (found != null && current != null && currentWord()?.id == word.id) {
                        NounArticle.applyToTextView(current.tvWord, NounArticle.format(word.text, found))
                    }
                }
            }
            b.tvQuestionContext.text = word.context
            b.tvItemAnswer.text = ""
        }
        b.tvQuestionContext.isVisible = !revealed && b.tvQuestionContext.text.isNotBlank()
        b.answerGroup.isVisible = revealed
        b.tvItemAnswer.isVisible = revealed && item != null
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
