package app.olauncher.ui.words

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import androidx.lifecycle.lifecycleScope
import app.olauncher.R
import app.olauncher.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.olauncher.data.store.AiChatGrounding
import app.olauncher.data.store.AiChatRouter
import app.olauncher.data.store.AiChatSubject
import app.olauncher.data.store.PracticeItemsStore
import app.olauncher.data.store.PracticeItem
import app.olauncher.data.store.PracticeQueue
import app.olauncher.data.store.PracticeRotation
import app.olauncher.data.store.PracticeVisibleItems
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
import app.olauncher.helper.PracticeWorker
import app.olauncher.helper.SystemMotion
import app.olauncher.helper.showPopupMenu
import app.olauncher.ui.BaseFragment
import app.olauncher.ui.askai.AskAiEntries
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

    /**
     * One render pass's Ask-AI entry state (docs/design/ask-ai-chat.md §Entry
     * points): whether any entry shows and the per-subject turn counts. Loaded
     * once per render — a key-vault read per row would decrypt the keystore over
     * and over — and asked by the summary chips and the revealed card's row.
     */
    private var askAi = AskAiEntryPoints.Environment.hidden()
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
        practiceItems = PracticeVisibleItems.of(PracticeItemsStore(context).all(), store.all())
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
        val storedItems = PracticeItemsStore(context).all()
        // Stale stored cards are hidden, never deleted (PracticeVisibleItems).
        practiceItems = PracticeVisibleItems.of(storedItems, store.all())
        // Never wait on generation (#725): a short due pool only starts a
        // background run; this session renders on the pools it has now. The
        // queue counts the stored pool, so the check does too.
        PracticeWorker.kickFromReview(context.applicationContext, PracticeQueue.anyNeedsItems(queue, storedItems))
        render()
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
        askAi = AskAiEntryPoints.environment(context)
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
        b.askAiRow.isVisible = false

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
            bindSummaryAskAiChip(rowBinding.tvAskAi, context, item.word, item.result)
            b.summaryList.addView(rowBinding.root)
        }
    }

    /**
     * Binds one review-summary row's Ask-AI chip (docs/design/ask-ai-chat.md
     * §Entry points): again and almost rows only, visible iff the AI gate is
     * open and the provider config valid — hidden, never greyed. The subject is
     * the reviewed word; the grounding carries the word as the AI sees it. Tap
     * opens the latest thread; long-press opens the thread sheet.
     */
    private fun bindSummaryAskAiChip(chip: TextView, context: Context, word: Word, result: String) {
        if (result == ReviewEvent.Result.KNEW || !askAi.visible) {
            chip.isVisible = false
            return
        }
        val subject = (AiChatRouter.routeForReviewMiss(word) as AiChatRouter.Route.Word).subject
        val groundings = listOf(AskAiEntryPoints.wordGrounding(word))
        chip.text = AskAiEntryPoints.chipText(context, askAi.turns(subject))
        chip.contentDescription = AskAiEntryPoints.chipAnnouncement(context, askAi.turns(subject))
        chip.isVisible = true
        chip.setOnClickListener { view ->
            Haptics.tick(view)
            AskAiEntryPoints.openChat(this, R.id.reviewFragment, subject, groundings = groundings)
        }
        chip.setOnLongClickListener { view ->
            Haptics.tick(view)
            AskAiThreadSheet.show(
                this,
                subject,
                AskAiEntryPoints.subjectTitle(context, WordsCard.getStore(context).all(), subject),
                groundings,
            ) { threadId ->
                AskAiEntryPoints.open(this, R.id.reviewFragment, subject, threadId, groundings = groundings)
            }
            true
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
        bindAskAiRow(b, context, word, item)
    }

    /**
     * Binds the Ask-AI row under the review buttons (docs/design/ask-ai-chat.md
     * §Entry points): revealed cards only — the pre-reveal practice prompt would
     * leak its answer — and hidden entirely when AI is unavailable, never greyed.
     * On a practice prompt the subject is the item's word resolved to its
     * citation text, and the grounding additionally carries the prompt and its
     * answer. Tap opens the latest thread; long-press opens the thread sheet.
     */
    private fun bindAskAiRow(b: FragmentReviewBinding, context: Context, word: Word, practiceItem: PracticeItem?) {
        val target = askAiTarget(context, word, practiceItem)
        if (target == null) {
            b.askAiRow.isVisible = false
            b.askAiRow.setOnClickListener(null)
            b.askAiRow.setOnLongClickListener(null)
            b.askAiHint.isVisible = false
            return
        }
        b.askAiRow.text = AskAiEntryPoints.wordRowText(context, askAi.turns(target.subject))
        b.askAiRow.isVisible = true
        // The muted older-threads hint under the row (mock screen 3): only when
        // the subject has two or more threads to list.
        val threadCount = askAi.threadCount(target.subject)
        b.askAiHint.isVisible = AskAiEntries.showOlderThreadsHint(threadCount)
        b.askAiHint.text = context.getString(R.string.ask_ai_word_row_older_hint, threadCount)
        b.askAiRow.setOnClickListener { view ->
            Haptics.tick(view)
            AskAiEntryPoints.openChat(this, R.id.reviewFragment, target.subject, groundings = target.groundings)
        }
        b.askAiRow.setOnLongClickListener { view ->
            Haptics.tick(view)
            AskAiThreadSheet.show(
                this,
                target.subject,
                AskAiEntryPoints.subjectTitle(context, WordsCard.getStore(context).all(), target.subject),
                target.groundings,
            ) { threadId ->
                AskAiEntryPoints.open(this, R.id.reviewFragment, target.subject, threadId, groundings = target.groundings)
            }
            true
        }
    }

    /** The revealed card's Ask-AI action: subject and groundings, or null when hidden. */
    private fun askAiTarget(context: Context, word: Word, practiceItem: PracticeItem?): AiChatTarget? {
        if (!revealed || !askAi.visible) return null
        // A practice item's wordId resolves to the word whose citation text is the
        // subject (design §Entry points); its pool is the word's own items, so the
        // bare card and the practice prompt open the same conversation.
        val subjectWord = practiceItem?.let { WordsCard.getStore(context).get(it.wordId) } ?: word
        val subject = AiChatSubject(AiChatSubject.Kind.WORD, AiChatRouter.normalizeCitation(subjectWord.text))
        val groundings = buildList {
            add(AskAiEntryPoints.wordGrounding(subjectWord))
            practiceItem?.let { add(AskAiEntryPoints.practiceGrounding(it.prompt, it.answer, it.wordId)) }
        }
        return AiChatTarget(subject, groundings)
    }

    /** One Ask-AI entry's navigation payload: where the chat opens and what it carries. */
    private data class AiChatTarget(
        val subject: AiChatSubject,
        val groundings: List<AiChatGrounding>,
    )

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
        // "Ask AI about this word" joins the popup only on a revealed card: the
        // popup is reachable pre-reveal, and a pre-reveal entry would leak the
        // answer (design §Entry points, the deliberate suppression).
        val ai = askAiTarget(context, word, currentPracticeItem(word))
        anchor.showPopupMenu(
            configure = { menu ->
                menu.add(Menu.NONE, 1, Menu.NONE, R.string.words_edit)
                if (ai != null) menu.add(Menu.NONE, 3, Menu.NONE, R.string.ask_ai_word_row)
                menu.add(Menu.NONE, 2, Menu.NONE, R.string.words_delete)
            }
        ) { item ->
            when (item.itemId) {
                1 -> WordDialogs.showEditDialog(context, word, { viewLifecycleOwnerOrNull() }) { refreshQueueFromStore() }
                3 -> ai?.let {
                    // showPopupMenu already ticks (Haptics.Kind.MENU) per item;
                    // another tick here would double-vibrate.
                    AskAiEntryPoints.openChat(this, R.id.reviewFragment, it.subject, groundings = it.groundings)
                }
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
