package app.olauncher.ui.words

import android.animation.ObjectAnimator
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.animation.PathInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.GridLayout
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.navigation.fragment.findNavController
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.data.store.DrillFlashcard
import app.olauncher.data.store.DrillFormat
import app.olauncher.data.store.DrillHelpCard
import app.olauncher.data.store.DrillItem
import app.olauncher.data.store.DrillItemHistory
import app.olauncher.data.store.DrillItemId
import app.olauncher.data.store.DrillLastRound
import app.olauncher.data.store.DrillQuiz
import app.olauncher.data.store.DrillQuizQuestion
import app.olauncher.data.store.DrillResult
import app.olauncher.data.store.DrillReviewFeed
import app.olauncher.data.store.DrillRoundPlanner
import app.olauncher.data.store.DrillScores
import app.olauncher.data.store.DrillSummaryItem
import app.olauncher.data.store.DrillSummaryLogic
import app.olauncher.data.store.DrillTables
import app.olauncher.data.store.DrillType
import app.olauncher.data.store.PracticeDrillItems
import app.olauncher.data.store.PracticeItemsStore
import app.olauncher.data.store.ReviewEventsStore
import app.olauncher.data.store.ReviewSessionLogic
import app.olauncher.data.store.SharedOfflineGermanStore
import app.olauncher.data.store.WordOrderQuiz
import app.olauncher.data.store.shouldAutoAdvance
import app.olauncher.databinding.FragmentDrillBinding
import app.olauncher.databinding.ItemSummaryRowBinding
import app.olauncher.helper.DrawerScrollHelper
import app.olauncher.helper.Haptics
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.isSystemAnimationsDisabled
import app.olauncher.helper.showKeyboard
import app.olauncher.ui.BaseFragment
import app.olauncher.ui.today.attachPressScale
import app.olauncher.ui.today.cards.WordsCard
import java.util.Random

/**
 * Full-screen drill quiz for one short round (#639): 3-5 items drawn from one type or a
 * mix of the weakest types, each question's format taken from the recognition ladder
 * (flashcard, tap, true-false, typed graduation). Wrong answers are corrected inline on
 * the same screen, the round shrinks by one per miss, and nothing is requeued; the
 * summary offers "Another 5" and "Practise misses".
 */
class DrillFragment : BaseFragment() {

    private var _binding: FragmentDrillBinding? = null
    private val binding get() = _binding!!

    /** The round's drill types: one type, or the 2-3 types of a mixed round. */
    private var drillTypes: List<DrillType> = listOf(DrillType.PREPOSITIONS)
    private var quiz: DrillQuiz? = null
    private var wordOrderQuiz: WordOrderQuiz? = null
    private var selectedChoice: String? = null
    private var lastRenderedItemId: String? = null
    private var autoAdvanceScheduled = false
    private var hintRevealedForItemId: String? = null
    /** The item whose help card was opened with "Why?" before answering (#651 design §2). */
    private var whyRevealedForItemId: String? = null
    /** The flashcard currently revealed by tapping its card (tap-to-reveal, retrieval first). */
    private var flashcardRevealedForItemId: String? = null
    private var recordedQuiz: Any? = null
    private var isPractiseMissesRound = false
    /** Feedback motion runs once per question, at the moment the answer lands (#640). */
    private var feedbackAnimatedItemId: String? = null
    /** The near-miss sand border pulse runs once per retried typed answer (#640). */
    private var nearMissPulsedItemId: String? = null
    /** True while a question-card exit is in flight, so a double tap cannot skip ahead. */
    private var transitionInProgress = false
    private val sessionGradedWordIds = mutableSetOf<Long>()
    private val roundRandom = Random()

    private val isWordOrderRound: Boolean get() = drillTypes.singleOrNull() == DrillType.WORD_ORDER

    private val autoAdvance = Runnable {
        autoAdvanceScheduled = false
        if (_binding == null) return@Runnable
        if (isWordOrderRound) {
            val q = wordOrderQuiz ?: return@Runnable
            if (!q.isAnswered) return@Runnable
            advanceWithMotion(binding.questionCard) {
                resetQuestionScroll()
                q.advance()
                render()
            }
            return@Runnable
        }
        val q = quiz ?: return@Runnable
        if (!q.isAnswered) return@Runnable
        advanceWithMotion(binding.questionCard) {
            resetQuestionScroll()
            q.advance()
            resetQuestionState()
            render()
        }
    }

    /** Clears the per-question render state when the round moves to the next question. */
    private fun resetQuestionState() {
        selectedChoice = null
        lastRenderedItemId = null
        hintRevealedForItemId = null
        whyRevealedForItemId = null
        flashcardRevealedForItemId = null
        feedbackAnimatedItemId = null
        nearMissPulsedItemId = null
    }

    /** A new question opens at the top: undo any scroll left by the previous long item. */
    private fun resetQuestionScroll() {
        _binding?.drillScrollView?.scrollTo(0, 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentDrillBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val footerBottomPadding = binding.drillFooter.paddingBottom
        binding.root.setOnApplyWindowInsetsListener { root, insets ->
            val padding = DrawerScrollHelper.imeBottomPadding(
                drawerBottomSearch = true,
                imeBottom = insets.getInsets(WindowInsets.Type.ime()).bottom,
                searchBottomMargin = footerBottomPadding,
            )
            if (root.paddingBottom != padding) {
                root.setPadding(root.paddingLeft, root.paddingTop, root.paddingRight, padding)
            }
            insets
        }
        binding.root.requestApplyInsets()

        binding.btnPrimary.setOnClickListener {
            onPrimary(it)
        }
        binding.btnPractiseMisses.setOnClickListener {
            onPractiseMisses(it)
        }
        binding.btnSecondary.setOnClickListener {
            Haptics.tick(it)
            findNavController().popBackStack()
        }

        binding.tvHint.setOnClickListener {
            val q = quiz ?: return@setOnClickListener
            val question = q.current ?: return@setOnClickListener
            if (q.isAnswered) return@setOnClickListener
            if (question.isFlashcard) return@setOnClickListener
            Haptics.tick(it)
            hintRevealedForItemId = question.id.toString()
            render()
        }

        // "Why?" opens the full help card before an answer (design §2); the tap is
        // recorded so a right answer afterwards lands as a weaker right answer.
        binding.btnWhy.setOnClickListener {
            val q = quiz ?: return@setOnClickListener
            val question = q.current ?: return@setOnClickListener
            if (q.isAnswered || question.isTeaching) return@setOnClickListener
            Haptics.tick(it)
            q.recordWhyTap()
            whyRevealedForItemId = question.id.toString()
            render()
        }

        binding.etAnswer.setOnEditorActionListener { _, actionId, event ->
            val isEnter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO || isEnter) {
                onPrimary(binding.btnPrimary)
                true
            } else {
                false
            }
        }
        binding.etAnswer.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                onPrimary(binding.btnPrimary)
                true
            } else {
                false
            }
        }

        binding.wordOrderView.onTileSelected = { tile ->
            wordOrderQuiz?.selectTile(tile)
            render()
        }
        binding.wordOrderView.onTileUnselected = { tile ->
            wordOrderQuiz?.unselectTile(tile)
            render()
        }

        // The help slot caps at about 40% of the scroll body's height (design §1);
        // the body height is known after the first layout pass.
        binding.drillScrollView.post {
            _binding?.helpScroll?.bodyHeight = _binding?.drillScrollView?.height ?: 0
        }

        val typeStr = arguments?.getString("drillType").orEmpty()
        drillTypes = typeStr.split(',')
            .mapNotNull { DrillType.fromId(it.trim()) }
            .distinct()
            .ifEmpty { listOf(DrillType.PREPOSITIONS) }

        if (isWordOrderRound) {
            if (wordOrderQuiz == null) {
                val words = WordsCard.getStore(view.context.applicationContext).all()
                val items = DrillTables.roundWordOrderItems(
                    words,
                    limit = DrillRoundPlanner.roundSize(roundRandom),
                    random = roundRandom,
                )
                if (items.isNotEmpty()) {
                    wordOrderQuiz = WordOrderQuiz(items)
                }
            }
        } else {
            if (quiz == null) {
                quiz = buildRound()
            }
        }

        render()
        animateQuestionEnter(motionSpec())
    }

    /** The per-item maturity counts (rounds answered right), decoded from prefs. */
    private fun itemMaturity(): Map<DrillItemId, Int> {
        val context = context ?: return emptyMap()
        return DrillItemHistory.maturities(Prefs(context.applicationContext).drillItemHistory)
    }

    /** Builds a new 3-5 item round over [drillTypes] with ladder formats. */
    private fun buildRound(): DrillQuiz? {
        val appContext = context?.applicationContext ?: return null
        val words = WordsCard.getStore(appContext).all()
        val genderLookup: (String) -> String? = { text ->
            if (Prefs(appContext).germanOfflineDictionary) {
                SharedOfflineGermanStore.get(appContext).gender(text)
            } else null
        }
        val pluralLookup: (String) -> String? = { text ->
            if (Prefs(appContext).germanOfflineDictionary) {
                SharedOfflineGermanStore.get(appContext).plural(text)
            } else null
        }
        val pools = drillTypes
            .filterNot { it == DrillType.WORD_ORDER }
            .map { it to DrillTables.items(it, words, genderLookup, pluralLookup) }
            .filter { it.second.isNotEmpty() }
            .toMap()
        if (pools.isEmpty()) return null
        // AI practice items (#621, Epic 9 #561) join mixed rounds only: single-type
        // rounds, the dedicated word-order round and practise-misses rounds stay
        // AI-free. The planner renders them flashcard-only, capped per round.
        val aiItems = if (drillTypes.size > 1) {
            PracticeDrillItems.toDrillItems(PracticeItemsStore(appContext).all())
        } else {
            emptyList()
        }
        val steps = DrillRoundPlanner.plan(pools, itemMaturity(), roundRandom, aiItems = aiItems)
        return if (steps.isEmpty()) null else DrillQuiz(steps, roundRandom)
    }

    /** Builds a practise-misses round over exactly the missed items. */
    private fun buildMissesRound(missed: List<DrillItem>): DrillQuiz? {
        if (missed.isEmpty()) return null
        val steps = DrillRoundPlanner.planMisses(missed, itemMaturity(), roundRandom)
        return if (steps.isEmpty()) null else DrillQuiz(steps, roundRandom)
    }

    private fun onPrimary(view: View) {
        if (isWordOrderRound) {
            val q = wordOrderQuiz ?: return
            Haptics.tick(view)
            when {
                q.isFinished -> {
                    isPractiseMissesRound = false
                    val appContext = view.context.applicationContext
                    val words = WordsCard.getStore(appContext).all()
                    val items = DrillTables.roundWordOrderItems(
                        words,
                        limit = DrillRoundPlanner.roundSize(roundRandom),
                        random = roundRandom,
                    )
                    wordOrderQuiz = WordOrderQuiz(items)
                    resetQuestionState()
                    render()
                    resetQuestionScroll()
                    animateQuestionEnter(motionSpec())
                }
                q.isAnswered -> {
                    advanceWithMotion(binding.questionCard) {
                        resetQuestionScroll()
                        q.advance()
                        render()
                    }
                }
                else -> {
                    val result = q.check() ?: return
                    render()
                    if (shouldAutoAdvance(result)) scheduleAutoAdvance()
                }
            }
            return
        }

        val q = quiz ?: return
        Haptics.tick(view)
        when {
            q.isFinished -> {
                // "Another 5": a new shuffled round of the same type or mix.
                isPractiseMissesRound = false
                quiz = buildRound()
                resetQuestionState()
                render()
                resetQuestionScroll()
                animateQuestionEnter(motionSpec())
            }
            q.isAnswered -> {
                // Continues past a flashcard, an inline correction, or a right answer.
                advanceWithMotion(binding.questionCard) {
                    resetQuestionScroll()
                    q.advance()
                    resetQuestionState()
                    render()
                }
            }
            q.currentFormat == DrillFormat.TYPED -> {
                val input = binding.etAnswer.text?.toString().orEmpty()
                val result = q.answerTyped(input) ?: return
                render()
                if (shouldAutoAdvance(result)) scheduleAutoAdvance()
            }
            else -> Unit
        }
    }

    private fun onPractiseMisses(view: View) {
        Haptics.tick(view)
        if (isWordOrderRound) {
            val q = wordOrderQuiz ?: return
            val results = q.itemResults()
            val missedItems = q.items.filter { results[it.id]?.isRight == false }
            if (missedItems.isNotEmpty()) {
                isPractiseMissesRound = true
                wordOrderQuiz = WordOrderQuiz(missedItems)
                resetQuestionState()
                render()
                resetQuestionScroll()
                animateQuestionEnter(motionSpec())
            }
            return
        }

        val q = quiz ?: return
        val results = q.itemResults()
        val missedItems = q.items.filter { results[it.id]?.isRight == false }
        buildMissesRound(missedItems)?.let { misses ->
            isPractiseMissesRound = true
            quiz = misses
            resetQuestionState()
            render()
            resetQuestionScroll()
            animateQuestionEnter(motionSpec())
        }
    }

    private fun render() {
        val b = _binding ?: return
        val context = context ?: return

        if (isWordOrderRound) {
            val q = wordOrderQuiz
            val empty = q == null || q.total == 0
            val finished = q != null && q.total > 0 && q.isFinished
            val inQuiz = q != null && !q.isFinished

            b.quizContainer.isVisible = inQuiz
            b.summaryHeader.isVisible = finished
            b.questionCard.isVisible = inQuiz
            b.choiceScroll.isVisible = false
            b.typedContainer.isVisible = false
            b.wordOrderView.isVisible = inQuiz
            b.quizCard.isVisible = finished
            b.tvEmpty.isVisible = empty
            b.btnPrimary.isVisible = !empty
            b.btnSecondary.isVisible = finished

            if (empty) {
                b.btnPrimary.isVisible = false
                b.btnPractiseMisses.isVisible = false
                return
            }
            if (finished) {
                renderWordOrderSummary(q)
                return
            }
            b.btnPractiseMisses.isVisible = false
            if (inQuiz) {
                renderWordOrderQuestion(q)
                // Defensive: a correct answer must never leave the screen with neither a
                // button nor a pending advance (e.g. a lost post after a config change).
                if (!autoAdvanceScheduled && !transitionInProgress &&
                    q.isAnswered && q.lastAnswer?.correct == true && !b.btnPrimary.isVisible
                ) {
                    scheduleAutoAdvance()
                }
            }
            return
        }

        val q = quiz

        val empty = q == null || q.total == 0
        val finished = q != null && q.total > 0 && q.isFinished
        val inQuiz = q != null && !q.isFinished

        b.quizContainer.isVisible = inQuiz
        b.summaryHeader.isVisible = finished
        b.questionCard.isVisible = inQuiz
        b.choiceScroll.isVisible = inQuiz && q.current?.correctChoice != null &&
            q.current?.choices?.isNotEmpty() == true
        b.typedContainer.isVisible = inQuiz && q.currentFormat == DrillFormat.TYPED
        b.wordOrderView.isVisible = false
        b.quizCard.isVisible = finished
        b.tvEmpty.isVisible = empty
        b.btnPrimary.isVisible = !empty
        b.btnSecondary.isVisible = finished

        // Question cards are plain; the flashcard render overrides both.
        b.questionCard.setBackgroundResource(R.drawable.today_panel)
        b.questionCard.setOnClickListener(null)
        b.questionCard.isClickable = false
        // The help slot and the "Why?" button are re-decided by the question renders.
        b.helpScroll.isVisible = false
        b.btnWhy.isVisible = false

        if (empty) {
            b.btnPrimary.isVisible = false
            b.btnPractiseMisses.isVisible = false
            return
        }
        if (finished) {
            renderSummary(q)
            return
        }
        b.btnPractiseMisses.isVisible = false
        if (inQuiz) {
            renderQuestion(q)
            // Defensive: a correct answer must never leave the screen with neither a
            // button nor a pending advance (e.g. a lost post after a config change).
            if (!autoAdvanceScheduled && !transitionInProgress &&
                q.isAnswered && q.lastAnswer?.correct == true && !b.btnPrimary.isVisible
            ) {
                scheduleAutoAdvance()
            }
        }
    }

    private fun drillTitleRes(): Int =
        if (drillTypes.size > 1) R.string.drill_mixed_title else DrillTables.labelRes(drillTypes.first())

    private fun formatLabelRes(format: DrillFormat): Int = when (format) {
        DrillFormat.FLASHCARD -> R.string.drill_round_flashcard
        DrillFormat.RECAP -> R.string.drill_round_recap
        DrillFormat.TRUE_FALSE -> R.string.drill_round_true_false
        DrillFormat.TYPED -> R.string.drill_round_typed
        else -> R.string.drill_round_choice
    }

    /** Localises the neutral true/false tokens; other choices pass through. */
    private fun formatChoiceToken(token: String?): String? = when (token) {
        DrillQuizQuestion.TRUE_CHOICE -> getString(R.string.drill_true)
        DrillQuizQuestion.FALSE_CHOICE -> getString(R.string.drill_false)
        else -> token
    }

    private fun renderQuestion(q: DrillQuiz) {
        val b = binding
        val context = b.root.context
        val question = q.current ?: return

        b.tvTitle.text = context.getString(drillTitleRes())
        val currentIdx = q.currentPosition + 1
        // A mixed round labels each question with its type (drills-ux.md, Mixed rounds).
        val formatLabel = getString(formatLabelRes(question.format))
        val questionLabel = if (drillTypes.size > 1) {
            getString(DrillTables.labelRes(question.id.type)) + " · " + formatLabel
        } else {
            formatLabel
        }
        b.tvProgress.text = context.getString(
            R.string.drill_progress,
            questionLabel,
            currentIdx,
            q.total,
        )

        renderSegments(q)

        // Prompt is text only; clearing the listener after setting isClickable would
        // re-enable it (setOnClickListener always calls setClickable(true)).
        b.tvPrompt.isClickable = false

        if (question.isFlashcard) {
            renderFlashcard(q, question)
            return
        }
        if (question.isRecap) {
            renderRecap(q, question)
            return
        }

        b.tvPrompt.isVisible = true
        b.tvPrompt.text = question.statement ?: question.prompt
        renderHint(q, question)
        renderHelp(q, question)

        if (question.isTyped) {
            renderTypedQuestion(q, question)
        } else {
            renderChoiceQuestion(q, question)
        }
    }

    /**
     * A flashcard (rung 1): a teaching beat, not a question — distinct label and card
     * treatment, tap-to-reveal so the learner retrieves before seeing the answer, and
     * the target in context (the prompt's example sentence, gap filled once revealed).
     * It is never scored. One tap on Continue moves to the next question.
     */
    private fun renderFlashcard(q: DrillQuiz, question: DrillQuizQuestion) {
        val b = binding
        val revealed = flashcardRevealedForItemId == question.id.toString()
        // With a gapped example the answer is in the filled sentence once revealed;
        // without one the answer line stays the label the card teaches.
        val revealedHint = DrillFlashcard.revealedHint(question)

        b.tvPrompt.isVisible = true
        b.tvPrompt.text = DrillFlashcard.prompt(question, revealed)
        b.tvHint.isVisible = !revealed || !revealedHint.isNullOrBlank()
        b.tvHint.isClickable = false
        b.tvHint.text = when {
            !revealed -> getString(R.string.drill_flashcard_reveal)
            else -> revealedHint.orEmpty()
        }
        // The flashcard keeps its distinct panel once revealed too; only the
        // tap-to-reveal interaction is unrevealed-only.
        b.questionCard.setBackgroundResource(R.drawable.flashcard_panel)
        if (!revealed) {
            b.questionCard.isClickable = true
            b.questionCard.setOnClickListener {
                Haptics.tick(it)
                flashcardRevealedForItemId = question.id.toString()
                render()
            }
        }
        b.choiceScroll.isVisible = false
        b.typedContainer.isVisible = false
        b.tvCorrection.isVisible = false
        b.btnPrimary.setText(R.string.conjugation_continue)
        b.btnPrimary.isVisible = true
        b.btnPrimary.isEnabled = true
        b.btnPrimary.alpha = 1f
        // Once revealed, the card opens the full help slot in its flashcard state
        // (design §2: open, full card, the correct form shown). Before the reveal the
        // slot stays closed — retrieval comes first.
        if (revealed) {
            renderHelp(q, question)
        } else {
            b.helpScroll.isVisible = false
            b.btnWhy.isVisible = false
        }
    }

    /**
     * A rule recap (the adaptive breather after consecutive misses): unscored, built
     * from the missed item's own answer and rule. Quizzing resumes after Continue.
     */
    private fun renderRecap(q: DrillQuiz, question: DrillQuizQuestion) {
        val b = binding
        b.tvPrompt.isVisible = true
        b.tvPrompt.text = DrillFlashcard.filledPrompt(question)
        b.tvHint.isVisible = !question.hint.isNullOrBlank()
        b.tvHint.isClickable = false
        b.tvHint.text = question.hint.orEmpty()
        b.choiceScroll.isVisible = false
        b.typedContainer.isVisible = false
        b.tvCorrection.isVisible = false
        b.btnPrimary.setText(R.string.conjugation_continue)
        b.btnPrimary.isVisible = true
        b.btnPrimary.isEnabled = true
        b.btnPrimary.alpha = 1f
    }

    /**
     * Binds the hint line for the current question. Before answering the hint is hidden
     * behind a tappable "Show hint" placeholder (explicit reveal); after a wrong answer
     * the one-line rule is always shown — the inline correction, on the same screen.
     */
    private fun renderHint(q: DrillQuiz, question: DrillQuizQuestion) {
        val b = binding
        val hint = question.hint
        if (q.isAnswered && q.lastAnswer?.correct == true) {
            // Answered correctly: no hint needed, not even the placeholder.
            b.tvHint.isVisible = false
            b.tvHint.isClickable = false
            return
        }
        if (hint.isNullOrBlank()) {
            b.tvHint.isVisible = false
            b.tvHint.isClickable = false
            return
        }
        if (q.isAnswered && q.lastAnswer == null) {
            // Reveal-style step (never answered): the rule is the content, not a hint.
            b.tvHint.isClickable = false
            b.tvHint.text = hint
            b.tvHint.isVisible = true
            return
        }
        val answeredWrong = q.isAnswered && q.lastAnswer?.correct == false
        val revealed = hintRevealedForItemId == question.id.toString()
        if (answeredWrong || revealed) {
            b.tvHint.isClickable = false
            b.tvHint.text = hint
        } else {
            b.tvHint.isClickable = true
            b.tvHint.text = getString(R.string.drill_show_hint)
        }
        b.tvHint.isVisible = true
    }

    // ---------------------------------------------------------------------
    // Help card (#651, docs/design/drill-help-card.md): the slot above the
    // prompt. What it may say per state is decided in DrillHelpCard (Context-
    // free, JVM tested); this wiring only binds the state to views.
    // ---------------------------------------------------------------------

    /**
     * Decides the help slot's state for the current question and binds it. Closed
     * topics without help content keep the plain hint line; open states (flashcard,
     * "Why?", after a miss) replace the collapsed cue line entirely.
     */
    private fun renderHelp(q: DrillQuiz, question: DrillQuizQuestion) {
        val b = binding
        val answeredWrong = q.isAnswered && q.lastAnswer?.correct == false
        val whyOpen = !q.isAnswered && whyRevealedForItemId == question.id.toString()
        // A true-false verdict's learner answer is "True"/"False", never a German
        // word — its miss line would name nothing, so no miss line is built for it.
        val wrongChoice = if (question.format == DrillFormat.TRUE_FALSE) {
            null
        } else {
            q.learnerAnswers()[question.id]
        }
        val state = DrillHelpCard.state(
            question = question,
            whyOpen = whyOpen,
            answeredWrong = answeredWrong,
            wrongChoice = wrongChoice,
        )
        if (state == null) {
            b.helpScroll.isVisible = false
            b.btnWhy.isVisible = question.item.ruleId != null && !q.isAnswered && !question.isTeaching
            // INVISIBLE, not GONE, everywhere hintRow hides: the row keeps its height,
            // so the prompt and the answer tiles stay at the same y across consecutive
            // questions (design §1: the card grows upward, nothing below it moves).
            b.hintRow.isInvisible = !(b.tvHint.isVisible || b.btnWhy.isVisible)
        } else {
            bindHelpCard(state, question.answer.takeIf { state.showAnswer })
            b.helpScroll.isVisible = true
            // The open card replaces the collapsed cue line — except on a flashcard,
            // where the line is the tap-to-reveal affordance and its revealed hint.
            // Off the flashcard the row keeps its height (invisible, not gone — the
            // hidden Why? chip included), so opening "Why?" or the miss line never
            // shifts the prompt and the tiles (design §1).
            if (question.isFlashcard) {
                b.btnWhy.isVisible = false
                b.hintRow.isVisible = b.tvHint.isVisible
            } else {
                b.btnWhy.isInvisible = true
                b.hintRow.isInvisible = true
            }
        }
    }

    /** Binds one help-card state: chips, rule, forms table, example, miss line. */
    private fun bindHelpCard(state: DrillHelpCard.State, answer: String?) {
        val b = binding
        val context = b.root.context
        val table = state.rule.table

        // Chips: the table's column headers carry the pattern (Wohin?/Wo?, m·f·n·pl),
        // never this item's answer.
        b.helpChips.removeAllViews()
        val chips = table.headers.drop(1).filter { it.isNotBlank() }
        for (chip in chips) b.helpChips.addView(newHelpChip(chip))
        b.helpChips.isVisible = chips.isNotEmpty()

        b.tvHelpRule.text = state.rule.rule

        b.helpTable.removeAllViews()
        b.helpTable.columnCount = table.headers.size
        for (header in table.headers) {
            b.helpTable.addView(
                helpCell(
                    text = header,
                    emphasised = true,
                    color = genderColorFor(header),
                ),
            )
        }
        for (row in table.rows) {
            b.helpTable.addView(helpCell(text = row.label, emphasised = true, color = null))
            row.cells.forEachIndexed { index, cell ->
                b.helpTable.addView(
                    helpCell(
                        text = cell,
                        emphasised = false,
                        color = genderColorFor(table.headers.getOrNull(index + 1)),
                        showAnswer = state.showAnswer && answer != null &&
                            cell.equals(answer, ignoreCase = true),
                    ),
                )
            }
        }

        // Example: shown in the flashcard and after-a-miss states, never before a tap
        // answer (design §3).
        if (state.example != null) {
            b.tvHelpExample.text = markedText(state.example)
            b.tvHelpExample.isVisible = true
        } else {
            b.tvHelpExample.isVisible = false
        }

        // Miss line: rose ✗ mark, then the wrong choice and why it is wrong, in ink.
        val miss = state.miss
        if (miss != null) {
            val line = getString(R.string.drill_help_miss_line, miss.wrong, miss.reason)
            val span = SpannableString(line)
            if (line.isNotEmpty()) {
                span.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(context, R.color.accentRose)),
                    0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            b.tvHelpMiss.text = span
            b.tvHelpMiss.isVisible = true
        } else {
            b.tvHelpMiss.isVisible = false
        }
    }

    /** A rule chip (design §3 anatomy 1): TodayChip.Small on the chip background. */
    private fun newHelpChip(label: String): TextView = TextView(requireContext()).apply {
        TextViewCompat.setTextAppearance(this, R.style.TodayChip_Small)
        text = label
        background = ContextCompat.getDrawable(context, R.drawable.today_chip)
        val horizontalPadding = resources.getDimensionPixelSize(R.dimen.today_chip_padding_horizontal)
        setPadding(horizontalPadding, 0, horizontalPadding, 0)
        gravity = Gravity.CENTER
    }

    /**
     * One forms-table cell. Gender columns take their fixed colour ([genderColorFor]);
     * the answer's cell, shown only in the flashcard and after-a-miss states, is bold
     * and underlined (design §4).
     */
    private fun helpCell(
        text: String,
        emphasised: Boolean,
        color: Int?,
        showAnswer: Boolean = false,
    ): TextView {
        val tv = TextView(requireContext())
        TextViewCompat.setTextAppearance(tv, R.style.TodayCardBody)
        tv.text = text
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.today_label_size))
        if (color != null) tv.setTextColor(color)
        tv.maxWidth = resources.getDimensionPixelSize(R.dimen.help_cell_max_width)
        val padding = resources.getDimensionPixelSize(R.dimen.help_cell_padding)
        tv.setPadding(padding, padding / 2, padding, padding / 2)
        if (showAnswer) {
            val span = SpannableString(text)
            span.setSpan(StyleSpan(Typeface.BOLD), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            span.setSpan(UnderlineSpan(), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            tv.text = span
        } else if (emphasised) {
            tv.paint.isFakeBoldText = true
        }
        return tv
    }

    /**
     * The fixed gender colour of a table column header (design §4): m blue, f red,
     * n green; plural and any other label stay ink (null). Case rows carry no colour.
     */
    private fun genderColorFor(header: String?): Int? = when (header?.trim()) {
        "m" -> ContextCompat.getColor(requireContext(), R.color.genderMasc)
        "f" -> ContextCompat.getColor(requireContext(), R.color.genderFem)
        "n" -> ContextCompat.getColor(requireContext(), R.color.genderNeut)
        else -> null
    }

    /** Renders `**…**` marked help text: marked segments bold and underlined. */
    private fun markedText(text: String): CharSequence {
        val segments = DrillHelpCard.markedSegments(text)
        if (segments.none { it.bold }) return text
        val span = SpannableString(segments.joinToString("") { it.text })
        var index = 0
        for (segment in segments) {
            if (segment.bold) {
                span.setSpan(StyleSpan(Typeface.BOLD), index, index + segment.text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                span.setSpan(UnderlineSpan(), index, index + segment.text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            index += segment.text.length
        }
        return span
    }

    private fun renderChoiceQuestion(q: DrillQuiz, question: DrillQuizQuestion) {
        val b = binding
        // Part-B guard: a choice question without choices must never dead-end. Reveal
        // the answer with an enabled Continue instead of an empty grid; the step is
        // never graded (see DrillQuizQuestion.isReveal).
        if (question.choices.isEmpty()) {
            renderRevealFallback(question)
            return
        }
        val answered = q.isAnswered
        val correctChoice = question.correctChoice
        b.choiceGrid.removeAllViews()

        // Feedback targets for the #640 micro-animations, collected while binding.
        var tappedTile: TextView? = null
        var wrongTile: TextView? = null
        val neutralTiles = ArrayList<TextView>()

        for (choice in question.choices) {
            val tv = newCell(formatChoiceToken(choice) ?: choice)
            tv.gravity = Gravity.CENTER
            tv.minHeight = resources.getDimensionPixelSize(R.dimen.quiz_choice_min_height)

            when (ConjugationCellLook.of(answered, choice, correctChoice.orEmpty(), selectedChoice)) {
                ConjugationCellLook.RIGHT -> {
                    tv.isActivated = true
                    // The tapped tile on a right answer settles and pops its check; a
                    // right-lit tile after a wrong answer is the reveal, not the tap.
                    if (answered && choice == selectedChoice) tappedTile = tv
                }
                ConjugationCellLook.WRONG -> {
                    tv.setBackgroundResource(R.drawable.quiz_choice_wrong)
                    wrongTile = tv
                }
                ConjugationCellLook.NEUTRAL -> if (answered) neutralTiles.add(tv)
            }

            if (!answered) {
                tv.attachPressScale()
                tv.setOnClickListener {
                    Haptics.tick(it)
                    val result = q.answer(choice) ?: return@setOnClickListener
                    selectedChoice = choice
                    if (shouldAutoAdvance(result)) {
                        scheduleAutoAdvance()
                    }
                    render()
                }
            }
            b.choiceGrid.addView(tv, cellParams(b.choiceGrid))
        }

        // Wrong answer: inline correction on this screen — the rule under the question and
        // one tap on Continue. Nothing is requeued.
        val waitingForContinue = answered && q.lastAnswer?.correct != true
        if (answered && !waitingForContinue) scheduleAutoAdvance()
        // A wrong true-false verdict only marks the True/False tile; the right word
        // itself is never on screen, so it is the correction line.
        if (question.format == DrillFormat.TRUE_FALSE && waitingForContinue) {
            b.tvCorrection.text = question.answer
            b.tvCorrection.isVisible = true
        } else {
            b.tvCorrection.isVisible = false
        }
        b.btnPrimary.setText(
            if (waitingForContinue) R.string.conjugation_continue else R.string.conjugation_check
        )
        b.btnPrimary.isVisible = waitingForContinue
        b.btnPrimary.isEnabled = true
        b.btnPrimary.alpha = 1f

        // Feedback micro-animations run once, at the moment the answer lands (#640),
        // after this render has decided what the correction reveals.
        val feedbackKey = question.id.toString()
        if (answered && feedbackAnimatedItemId != feedbackKey) {
            feedbackAnimatedItemId = feedbackKey
            animateChoiceFeedback(q, tappedTile, wrongTile, neutralTiles)
            if (waitingForContinue) {
                // The mock rises the whole correction block: the rule, the answer and
                // the Continue button that just appeared with them.
                animateCorrectionRise(
                    motionSpec(),
                    b.btnPrimary,
                    *revealedCorrectionViews(feedbackKey).toTypedArray(),
                )
            }
        }
    }

    /** Reveal-style presentation for a choice question with no choices: answer + enabled Continue. */
    private fun renderRevealFallback(question: DrillQuizQuestion) {
        val b = binding
        b.choiceGrid.removeAllViews()
        b.choiceScroll.isVisible = false
        b.tvCorrection.text = question.statement ?: question.answer
        b.tvCorrection.isVisible = true
        b.btnPrimary.setText(R.string.conjugation_continue)
        b.btnPrimary.isVisible = true
        b.btnPrimary.isEnabled = true
        b.btnPrimary.alpha = 1f
    }

    private fun renderTypedQuestion(q: DrillQuiz, question: DrillQuizQuestion) {
        val b = binding
        val answered = q.isAnswered
        val currentItemId = question.id.toString()

        if (!answered) {
            if (lastRenderedItemId != currentItemId) {
                lastRenderedItemId = currentItemId
                b.etAnswer.setText("")
            }
            b.etAnswer.isEnabled = true
            b.etAnswer.isActivated = false
            b.etAnswer.setBackgroundResource(R.drawable.quiz_choice)
            if (q.isAwaitingRetry) {
                b.tvCorrection.text = getString(R.string.drill_near_miss)
                b.tvCorrection.isVisible = true
                b.etAnswer.setSelection(b.etAnswer.text?.length ?: 0)
                pulseNearMissBorder(b, currentItemId)
            } else {
                b.tvCorrection.isVisible = false
            }
            b.btnPrimary.setText(R.string.conjugation_check)
            b.btnPrimary.isVisible = true
            b.btnPrimary.isEnabled = true
            b.btnPrimary.alpha = 1f
            b.etAnswer.requestFocus()
            b.etAnswer.post { b.etAnswer.showKeyboard() }
        } else {
            lastRenderedItemId = null
            b.etAnswer.isEnabled = false
            val isCorrect = q.lastAnswer?.correct == true
            if (isCorrect) {
                b.etAnswer.isActivated = true
                b.etAnswer.setBackgroundResource(R.drawable.quiz_choice)
                b.tvCorrection.isVisible = false
                b.btnPrimary.isVisible = false
                scheduleAutoAdvance()
            } else {
                b.etAnswer.isActivated = false
                b.etAnswer.setBackgroundResource(R.drawable.quiz_choice_wrong)
                b.tvCorrection.text = question.answer
                b.tvCorrection.isVisible = true
                b.btnPrimary.setText(R.string.conjugation_continue)
                b.btnPrimary.isVisible = true
                b.btnPrimary.isEnabled = true
                b.btnPrimary.alpha = 1f
            }
            // Feedback micro-animations run once, at the moment the answer lands (#640).
            if (feedbackAnimatedItemId != currentItemId) {
                feedbackAnimatedItemId = currentItemId
                val spec = motionSpec()
                if (isCorrect) {
                    popView(b.etAnswer, spec, fadeWhenReduced = false)
                } else {
                    shakeView(b.etAnswer, spec)
                    animateCorrectionRise(spec, *revealedCorrectionViews(currentItemId).toTypedArray())
                }
            }
        }
    }

    private fun scheduleAutoAdvance() {
        if (autoAdvanceScheduled) return
        autoAdvanceScheduled = true
        _binding?.root?.postDelayed(autoAdvance, motionSpec().autoAdvanceMs)
    }

    // ---------------------------------------------------------------------
    // Motion (#640): the decisions live in DrillMotion (Context-free, JVM
    // tested by DrillMotionDesignLanguageTest); this wiring only applies them.
    // ---------------------------------------------------------------------

    /** Reduced motion is the system "remove animations" setting — the app adds no toggle. */
    private fun motionSpec(): DrillMotion.Spec =
        DrillMotion.spec(context?.isSystemAnimationsDisabled() ?: false)

    /** cubic-bezier(.2, 0, 0, 1) — the decelerate easing every drill motion shares. */
    private fun easeInterpolator() = PathInterpolator(
        DrillMotion.EASE_CONTROL_X1,
        DrillMotion.EASE_CONTROL_Y1,
        DrillMotion.EASE_CONTROL_X2,
        DrillMotion.EASE_CONTROL_Y2,
    )

    /** Slide/rise distance in px, or 0 when reduced motion removed the translation. */
    private fun densityPx(dp: Float, translates: Boolean): Float =
        if (translates) dp * resources.displayMetrics.density else 0f

    /**
     * The question-card transition (#640): [exitView] (the question card, or the whole
     * round when a fresh one starts) fades out toward the left, then [advance] re-binds
     * and the new card slides in from the right. Sequential, no gap, no overlap — the
     * header, progress line and segment bar never move.
     */
    private fun advanceWithMotion(exitView: View, advance: () -> Unit) {
        if (transitionInProgress) return
        val b = _binding ?: return
        transitionInProgress = true
        val spec = motionSpec()
        b.root.removeCallbacks(autoAdvance)
        autoAdvanceScheduled = false
        exitView.animate()
            .translationX(-densityPx(DrillMotion.SLIDE_X_DP, spec.translates))
            .alpha(0f)
            .setDuration(spec.exitMs)
            .setInterpolator(easeInterpolator())
            .withEndAction {
                transitionInProgress = false
                if (_binding == null) return@withEndAction
                advance()
                animateQuestionEnter(spec)
            }
    }

    /** Enter-only variant: the first question of a fresh round or of the screen. */
    private fun animateQuestionEnter(spec: DrillMotion.Spec) {
        val card = _binding?.questionCard ?: return
        if (!card.isVisible) return
        card.translationX = densityPx(DrillMotion.SLIDE_X_DP, spec.translates)
        card.alpha = 0f
        card.animate()
            .translationX(0f)
            .alpha(1f)
            .setStartDelay(0) // view.animate() reuses the animator; drop any prior summary delay
            .setDuration(spec.enterMs)
            .setInterpolator(easeInterpolator())
            .start()
    }

    /** The pop: the view overshoots slightly and settles; reduced motion fades it instead. */
    private fun popView(view: View, spec: DrillMotion.Spec, fadeWhenReduced: Boolean) {
        if (spec.scales) {
            view.scaleX = DrillMotion.POP_MAX_SCALE
            view.scaleY = DrillMotion.POP_MAX_SCALE
            view.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(0) // view.animate() reuses the animator; drop any prior delay
                .setDuration(spec.popMs)
                .setInterpolator(easeInterpolator())
                .start()
        } else if (fadeWhenReduced) {
            view.alpha = 0f
            view.animate()
                .alpha(1f)
                .setStartDelay(0) // view.animate() reuses the animator; drop any prior delay
                .setDuration(spec.popMs)
                .setInterpolator(easeInterpolator())
                .start()
        }
    }

    /** The wrong-answer shake on the tapped tile; dropped entirely under reduced motion. */
    private fun shakeView(view: View, spec: DrillMotion.Spec) {
        if (!spec.shakes) return
        val shakePx = densityPx(DrillMotion.SHAKE_X_DP, translates = true)
        val fractions = DrillMotion.shakeFractions()
        val values = FloatArray(fractions.size + 2)
        values[0] = 0f
        fractions.forEachIndexed { index, fraction -> values[index + 1] = fraction * shakePx }
        values[values.size - 1] = 0f
        ObjectAnimator.ofFloat(view, View.TRANSLATION_X, *values).apply {
            duration = spec.shakeMs
            interpolator = easeInterpolator()
            start()
        }
    }

    /**
     * The inline correction slides up once, when the wrong answer lands: every view
     * this render just revealed — the rule line in the hint, the answer in the
     * correction, the Continue button where it just appeared — rises together for
     * [DrillMotion.RISE_MS], like the mock's one correction block.
     */
    private fun animateCorrectionRise(spec: DrillMotion.Spec, vararg views: View) {
        for (view in views) {
            view.translationY = densityPx(DrillMotion.RISE_Y_DP, spec.translates)
            view.alpha = 0f
            view.animate()
                .translationY(0f)
                .alpha(1f)
                .setStartDelay(0) // view.animate() reuses the animator; drop the summary's stagger delay
                .setDuration(spec.riseMs)
                .setInterpolator(easeInterpolator())
                .start()
        }
    }

    /**
     * The correction views the wrong-answer render just revealed: the rule line in the
     * hint — unless the learner revealed it earlier, that line is old news — and the
     * answer line in the correction.
     */
    private fun revealedCorrectionViews(feedbackKey: String): List<View> {
        val b = _binding ?: return emptyList()
        val views = ArrayList<View>()
        val hintShowsRule = b.tvHint.isVisible && !b.tvHint.isClickable
        if (hintShowsRule && hintRevealedForItemId != feedbackKey) views.add(b.tvHint)
        if (b.tvCorrection.isVisible) views.add(b.tvCorrection)
        return views
    }

    /** Right or wrong on a choice tile: settle/flash + shake, the rest of the grid dims. */
    private fun animateChoiceFeedback(
        q: DrillQuiz,
        tappedTile: TextView?,
        wrongTile: TextView?,
        neutralTiles: List<TextView>,
    ) {
        val spec = motionSpec()
        if (q.lastAnswer?.correct == true) {
            tappedTile?.let { tile ->
                tile.text = tile.text.toString() + " \u2713"
                popView(tile, spec, fadeWhenReduced = false)
            }
        } else {
            wrongTile?.let { tile ->
                tile.setBackgroundResource(R.drawable.quiz_choice_wrong_flash)
                tile.postDelayed({
                    if (_binding != null) tile.setBackgroundResource(R.drawable.quiz_choice_wrong)
                }, spec.flashMs)
                shakeView(tile, spec)
            }
            // The correction rise itself runs in the render that reveals it, after
            // this tile motion, with the views that just appeared.
        }
        // The rest of the grid falls back so the tapped tile carries the verdict.
        for (tile in neutralTiles) tile.alpha = DrillMotion.DIMMED_ALPHA
    }

    /**
     * Near miss (#622) motion (#640): a soft accentSand border pulse — no shake, no rose,
     * no reveal — then back to the resting border while the single retry stays open.
     */
    private fun pulseNearMissBorder(b: FragmentDrillBinding, itemId: String) {
        if (nearMissPulsedItemId == itemId) return
        nearMissPulsedItemId = itemId
        val spec = motionSpec()
        b.etAnswer.setBackgroundResource(R.drawable.quiz_choice_near)
        b.root.postDelayed({
            val bind = _binding ?: return@postDelayed
            val quizNow = quiz
            val stillAwaitingRetry = quizNow != null && !quizNow.isAnswered &&
                quizNow.isAwaitingRetry && quizNow.current?.id?.toString() == itemId
            if (stillAwaitingRetry) {
                bind.etAnswer.setBackgroundResource(R.drawable.quiz_choice)
            }
        }, spec.shakeMs)
    }

    /**
     * Summary entrance (#640): the counts pop, the rows rise with a 50ms stagger
     * (misses first — [DrillSummaryLogic.sort] order), the buttons come last, all
     * inside the ~700ms budget.
     */
    private fun animateSummaryEntrance() {
        val b = _binding ?: return
        val spec = motionSpec()
        popView(b.tvSummaryCounts, spec, fadeWhenReduced = true)
        val rowCount = b.summaryList.childCount
        for (index in 0 until rowCount) {
            val row = b.summaryList.getChildAt(index)
            row.translationY = densityPx(DrillMotion.RISE_Y_DP, spec.translates)
            row.alpha = 0f
            row.animate()
                .translationY(0f)
                .alpha(1f)
                .setStartDelay(DrillMotion.summaryRowDelayMs(index, rowCount, spec))
                .setDuration(spec.rowMs)
                .setInterpolator(easeInterpolator())
                .start()
        }
        for (button in listOf(b.btnPrimary, b.btnPractiseMisses, b.btnSecondary)) {
            button.alpha = 0f
            button.animate()
                .alpha(1f)
                .setStartDelay(DrillMotion.summaryButtonsDelayMs(rowCount, spec))
                .setDuration(spec.rowMs)
                .setInterpolator(easeInterpolator())
                .start()
        }
    }

    private fun renderSegments(q: DrillQuiz) {
        val row = binding.segmentRow
        row.removeAllViews()
        val firstAttempts = q.firstAttempts()

        for ((index, step) in q.steps.withIndex()) {
            val seg = View(row.context)
            val firstTry = firstAttempts[step.item.id]
            // A teaching step (flashcard, recap) is never scored, so its segment stays
            // neutral instead of taking the first-attempt colour of a question.
            val color = when {
                step.isTeaching -> attrColor(R.attr.todayChipColor)
                firstTry == false -> attrColor(R.attr.dangerColor)
                firstTry == true -> attrColor(R.attr.accentColor)
                index == q.currentPosition -> attrColor(R.attr.primaryColor)
                else -> attrColor(R.attr.todayChipColor)
            }
            seg.setBackgroundColor(color)
            val lp = android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            if (index > 0) lp.marginStart = row.resources.getDimensionPixelSize(R.dimen.quiz_segment_gap)
            row.addView(seg, lp)
        }
    }

    private fun renderSummary(q: DrillQuiz) {
        recordFinishedRound(q)
        val b = binding
        val context = b.root.context
        val results = q.itemResults()
        val learnerAnswers = q.learnerAnswers()
        // A true-false item is judged on its filled statement, not on the gapped prompt:
        // the summary row must say which statement the learner called false. Reveal
        // fallback steps are not scored, so they never reach the summary either.
        val statementByItem = q.steps.filterNot { it.isTeaching || it.isReveal }.associateBy { it.id }
        val summaryItems = q.items.mapIndexed { index, item ->
            val res = results[item.id] ?: if (q.firstAttempts()[item.id] == true) DrillResult.RIGHT else DrillResult.WRONG
            val step = statementByItem[item.id]
            // A right answer on a FALSE statement judged correctly: the green check must not
            // read as "this German is correct", so mark the false statement explicitly.
            val judgedFalse = step != null && step.format == DrillFormat.TRUE_FALSE && !step.isTrue
            val rowPrompt = (step?.statement ?: item.prompt) +
                if (res == DrillResult.RIGHT && judgedFalse) " (False)" else ""
            DrillSummaryItem(
                id = item.id,
                prompt = rowPrompt,
                answer = item.answer,
                hint = item.hint,
                result = res,
                learnerAnswer = learnerAnswers[item.id],
                roundIndex = index,
            )
        }
        val sortedItems = DrillSummaryLogic.sort(summaryItems)
        val counts = DrillSummaryLogic.counts(summaryItems)

        b.tvSummaryTitle.text = context.getString(drillTitleRes())
        b.tvSummaryCounts.text = context.getString(R.string.drill_summary_counts, counts.right, counts.almost, counts.wrong)

        bindSummaryRows(sortedItems)

        b.btnPrimary.setText(R.string.drill_summary_another)
        b.btnPrimary.isVisible = true
        b.btnPrimary.isEnabled = true
        b.btnPrimary.alpha = 1f

        val hasMisses = counts.wrong > 0 || counts.almost > 0
        b.btnPractiseMisses.setText(R.string.drill_summary_practise_misses)
        b.btnPractiseMisses.isVisible = hasMisses
        b.btnPractiseMisses.isEnabled = hasMisses

        b.btnSecondary.setText(R.string.drill_summary_done)
        b.btnSecondary.isVisible = true
        b.btnSecondary.isEnabled = true
        b.btnSecondary.alpha = 1f

        animateSummaryEntrance()

        b.root.hideKeyboard()
    }

    /**
     * Binds the sorted summary rows shared by the drill and word-order summaries —
     * the drill side's thin adapter over the one shared row layout (#623): mark icon,
     * tint, prompt text, and correction line per result. A near-miss (almost) row
     * shows the retried input, the answer, and the near-miss note.
     */
    private fun bindSummaryRows(items: List<DrillSummaryItem>) {
        val b = binding
        val context = b.root.context
        b.summaryList.removeAllViews()
        for (item in items) {
            val itemBinding = ItemSummaryRowBinding.inflate(layoutInflater, b.summaryList, false)
            val learnerAnswer = formatChoiceToken(item.learnerAnswer)
            itemBinding.tvPrimary.text = DrillSummaryLogic.formatPrompt(
                item.prompt,
                item.result,
                learnerAnswer,
                answer = item.answer,
                almostNote = context.getString(R.string.drill_summary_near_miss),
            )
            when (item.result) {
                DrillResult.RIGHT -> {
                    itemBinding.ivMark.setImageResource(R.drawable.ic_check)
                    itemBinding.ivMark.setColorFilter(ContextCompat.getColor(context, R.color.accentSage))
                    itemBinding.tvSecondary.isVisible = false
                }
                DrillResult.ALMOST -> {
                    itemBinding.ivMark.setImageResource(R.drawable.ic_dot)
                    itemBinding.ivMark.setColorFilter(ContextCompat.getColor(context, R.color.accentSand))
                    itemBinding.tvSecondary.isVisible = false
                }
                DrillResult.WRONG -> {
                    itemBinding.ivMark.setImageResource(R.drawable.ic_close)
                    itemBinding.ivMark.setColorFilter(ContextCompat.getColor(context, R.color.accentRose))
                    val correction = DrillSummaryLogic.formatCorrection(item.prompt, item.answer, item.hint)
                    itemBinding.tvSecondary.text = correction
                    itemBinding.tvSecondary.isVisible = correction.isNotEmpty()
                }
            }
            b.summaryList.addView(itemBinding.root)
        }
    }

    private fun recordFinishedRound(q: DrillQuiz) {
        if (recordedQuiz === q) return
        recordedQuiz = q
        // A practise-misses round feeds neither the per-type scores nor the per-item
        // maturity: graduation counts fresh rounds, not repeats of the same misses in
        // one sitting (docs/design/drills-ux.md, Format ladder rules). Review grades
        // still record, de-duplicated per word in recordReviewGrades.
        if (!isPractiseMissesRound) {
            recordScores(q, abandoned = false)
        }
        recordReviewGrades(q.firstAttempts())
    }

    /**
     * Records one finished or abandoned round into the per-type scores: each type
     * present in the round gets its own evidence (see [DrillScores.roundEvidenceByType]),
     * so a mixed round scores per type through the review feed unchanged. Also records
     * the per-item maturity ([DrillItemHistory]) that earns typed graduation later.
     */
    private fun recordScores(q: DrillQuiz, abandoned: Boolean) {
        val context = context ?: return
        val prefs = Prefs(context.applicationContext)
        val byType = DrillScores.roundEvidenceByType(
            q.firstAttempts(),
            q.typedResults(),
            abandoned,
            q.whyTappedIds(),
        )
        for ((type, round) in byType) {
            DrillScores.recordRound(
                prefs,
                type.id,
                asked = round.asked,
                firstTryRight = round.firstTryRight,
                askedEvidence = round.evidence.askedDelta,
                rightEvidence = round.evidence.rightDelta,
            )
            if (drillTypes.size == 1) {
                DrillLastRound.from(type.id, round.rightAnswers, q.items.size)?.let { lastRound ->
                    DrillLastRound.record(prefs, lastRound)
                }
            }
        }

        // Per-item maturity: every item answered right this round counts toward graduation.
        DrillItemHistory.record(prefs, q.itemResults().filterValues { it == DrillResult.RIGHT })
    }

    /** Feeds the round's first attempts through the review schedule for matching saved words. */
    private fun recordReviewGrades(firstAttempts: Map<DrillItemId, Boolean>) {
        val context = context ?: return
        val appContext = context.applicationContext
        val words = WordsCard.getStore(appContext).all()
        val today = WordsCard.epochDaySupplier()
        val grades = DrillReviewFeed.grades(firstAttempts, words, today)
        val store = WordsCard.getStore(appContext)
        val events = ReviewEventsStore(appContext)
        val now = System.currentTimeMillis()
        for (grade in grades) {
            if (sessionGradedWordIds.add(grade.word.id)) {
                val current = store.get(grade.word.id) ?: grade.word
                ReviewSessionLogic.gradeAndLog(
                    word = current,
                    result = grade.result,
                    today = today,
                    reviewedAt = now,
                    saveWord = store::update,
                    events = events,
                    source = grade.source,
                )
            }
        }
    }

    private fun renderWordOrderQuestion(q: WordOrderQuiz) {
        val b = binding
        val context = b.root.context
        val question = q.current ?: return

        b.tvTitle.text = context.getString(R.string.drill_type_word_order)
        val roundLabel = context.getString(R.string.drill_round_word_order)
        val currentIdx = (q.items.indexOf(question.item).takeIf { it >= 0 } ?: 0) + 1
        b.tvProgress.text = context.getString(R.string.drill_progress, roundLabel, currentIdx, q.total)

        renderWordOrderSegments(q)

        b.tvPrompt.isVisible = false

        b.choiceScroll.isVisible = false
        b.typedContainer.isVisible = false
        b.wordOrderView.isVisible = true

        val answered = q.isAnswered
        val lastAnswer = q.lastAnswer

        val isWrongAnswer = answered && lastAnswer?.correct == false
        b.tvHint.isVisible = isWrongAnswer && !question.hint.isNullOrBlank()
        b.tvHint.isClickable = false
        b.tvHint.text = question.hint.orEmpty()

        b.wordOrderView.render(
            answerTiles = question.answerTiles,
            availableTiles = question.availableTiles,
            answerState = lastAnswer,
        )

        if (!answered) {
            b.tvCorrection.isVisible = false
            b.btnPrimary.setText(R.string.conjugation_check)
            b.btnPrimary.isVisible = true
            b.btnPrimary.isEnabled = question.answerTiles.isNotEmpty()
            b.btnPrimary.alpha = if (question.answerTiles.isNotEmpty()) 1f else 0.5f
        } else {
            val isCorrect = lastAnswer?.correct == true
            if (isCorrect) {
                b.tvCorrection.isVisible = false
                b.btnPrimary.isVisible = false
                scheduleAutoAdvance()
            } else {
                // Inline correction: the answer and its rule on this screen, one Continue.
                b.tvCorrection.text = question.item.canonicalAnswer
                b.tvCorrection.isVisible = true
                b.btnPrimary.setText(R.string.conjugation_continue)
                b.btnPrimary.isVisible = true
                b.btnPrimary.isEnabled = true
                b.btnPrimary.alpha = 1f
            }
            // Feedback runs once per question, at the moment the answer lands (#640).
            val feedbackKey = "wo:" + question.item.id
            if (feedbackAnimatedItemId != feedbackKey) {
                feedbackAnimatedItemId = feedbackKey
                if (lastAnswer?.correct != true) {
                    animateCorrectionRise(motionSpec(), *revealedCorrectionViews(feedbackKey).toTypedArray())
                }
            }
        }
    }

    private fun renderWordOrderSegments(q: WordOrderQuiz) {
        val row = binding.segmentRow
        row.removeAllViews()
        val firstAttempts = q.firstAttempts()
        val current = q.current ?: return
        val currentItem = current.item

        for ((index, item) in q.items.withIndex()) {
            val seg = View(row.context)
            val firstTry = firstAttempts[item.id]
            val color = when {
                firstTry == false -> attrColor(R.attr.dangerColor)
                firstTry == true -> attrColor(R.attr.accentColor)
                item == currentItem -> attrColor(R.attr.primaryColor)
                else -> attrColor(R.attr.todayChipColor)
            }
            seg.setBackgroundColor(color)
            val lp = android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            if (index > 0) lp.marginStart = row.resources.getDimensionPixelSize(R.dimen.quiz_segment_gap)
            row.addView(seg, lp)
        }
    }

    private fun renderWordOrderSummary(q: WordOrderQuiz) {
        recordFinishedWordOrderRound(q)
        val b = binding
        val context = b.root.context
        val results = q.itemResults()
        val learnerAnswers = q.learnerAnswers()
        val summaryItems = q.items.mapIndexed { index, item ->
            val res = results[item.id] ?: if (q.firstAttempts()[item.id] == true) DrillResult.RIGHT else DrillResult.WRONG
            DrillSummaryItem(
                id = item.id,
                prompt = item.canonicalAnswer,
                answer = item.canonicalAnswer,
                hint = item.hint,
                result = res,
                learnerAnswer = learnerAnswers[item.id],
                roundIndex = index,
            )
        }
        val sortedItems = DrillSummaryLogic.sort(summaryItems)
        val counts = DrillSummaryLogic.counts(summaryItems)

        b.tvSummaryTitle.text = context.getString(R.string.drill_type_word_order)
        b.tvSummaryCounts.text = context.getString(R.string.drill_summary_counts, counts.right, counts.almost, counts.wrong)

        bindSummaryRows(sortedItems)

        b.btnPrimary.setText(R.string.drill_summary_another)
        b.btnPrimary.isVisible = true
        b.btnPrimary.isEnabled = true
        b.btnPrimary.alpha = 1f

        val hasMisses = counts.wrong > 0 || counts.almost > 0
        b.btnPractiseMisses.setText(R.string.drill_summary_practise_misses)
        b.btnPractiseMisses.isVisible = hasMisses
        b.btnPractiseMisses.isEnabled = hasMisses

        b.btnSecondary.setText(R.string.drill_summary_done)
        b.btnSecondary.isVisible = true
        b.btnSecondary.isEnabled = true
        b.btnSecondary.alpha = 1f

        animateSummaryEntrance()

        b.root.hideKeyboard()
    }

    private fun recordFinishedWordOrderRound(q: WordOrderQuiz) {
        if (recordedQuiz === q) return
        recordedQuiz = q
        val context = context ?: return
        val appContext = context.applicationContext
        if (!isPractiseMissesRound) {
            val first = q.firstAttempts()
            val choiceRight = first.values.count { it }
            val prefs = Prefs(appContext)
            DrillScores.recordRound(
                prefs,
                DrillType.WORD_ORDER.id,
                asked = first.size,
                firstTryRight = choiceRight,
                askedEvidence = first.size.toDouble(),
                rightEvidence = choiceRight.toDouble(),
            )
            DrillLastRound.from(DrillType.WORD_ORDER.id, choiceRight, q.items.size)?.let { lastRound ->
                DrillLastRound.record(prefs, lastRound)
            }
        }

        recordReviewGrades(q.firstAttempts())
    }

    /**
     * An abandoned round still counts the answers already given, but only when at least
     * [DrillScores.MIN_ABANDONED_ANSWERS] were given; items not reached are not counted.
     * Rounds already recorded and practise-misses rounds are skipped.
     */
    private fun recordAbandonedRound() {
        if (isPractiseMissesRound) return
        if (isWordOrderRound) {
            val q = wordOrderQuiz ?: return
            if (q === recordedQuiz || q.isFinished) return
            val first = q.firstAttempts()
            val choiceRight = first.values.count { it }
            val evidence = DrillScores.abandonedRoundEvidence(
                hasTypedRound = false,
                choiceAnswered = first.size,
                choiceRight = choiceRight,
            ) ?: return
            recordedQuiz = q
            val context = context ?: return
            val prefs = Prefs(context.applicationContext)
            DrillScores.recordRound(
                prefs,
                DrillType.WORD_ORDER.id,
                asked = first.size,
                firstTryRight = choiceRight,
                askedEvidence = evidence.askedDelta,
                rightEvidence = evidence.rightDelta,
            )
        } else {
            val q = quiz ?: return
            if (q === recordedQuiz || q.isFinished) return
            recordedQuiz = q
            recordScores(q, abandoned = true)
        }
    }

    private fun newCell(text: String): TextView = TextView(binding.root.context).apply {
        this.text = text
        setTextAppearance(R.style.TodayCardBodyBold)
        setBackgroundResource(R.drawable.quiz_choice)
        val pad = resources.getDimensionPixelSize(R.dimen.quiz_cell_padding)
        setPadding(pad, pad, pad, pad)
        isClickable = true
    }

    private fun cellParams(grid: GridLayout): GridLayout.LayoutParams =
        GridLayout.LayoutParams(
            GridLayout.spec(GridLayout.UNDEFINED, 1f),
            GridLayout.spec(GridLayout.UNDEFINED, 1f),
        ).apply {
            width = 0
            val margin = grid.resources.getDimensionPixelSize(R.dimen.quiz_cell_margin)
            setMargins(margin, margin, margin, margin)
        }

    private fun attrColor(@AttrRes attr: Int): Int {
        val tv = TypedValue()
        binding.root.context.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    override fun onDestroyView() {
        recordAbandonedRound()
        _binding?.root?.removeCallbacks(autoAdvance)
        autoAdvanceScheduled = false
        _binding?.root?.hideKeyboard()
        super.onDestroyView()
        _binding = null
    }
}
