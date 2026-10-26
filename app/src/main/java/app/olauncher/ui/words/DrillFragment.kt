package app.olauncher.ui.words

import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.widget.GridLayout
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.data.store.AUTO_ADVANCE_DELAY_MS
import app.olauncher.data.store.DrillFormat
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
import app.olauncher.data.store.ReviewEventsStore
import app.olauncher.data.store.ReviewSessionLogic
import app.olauncher.data.store.SharedOfflineGermanStore
import app.olauncher.data.store.WordOrderQuiz
import app.olauncher.data.store.shouldAutoAdvance
import app.olauncher.databinding.FragmentDrillBinding
import app.olauncher.databinding.ItemDrillSummaryBinding
import app.olauncher.helper.DrawerScrollHelper
import app.olauncher.helper.Haptics
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.showKeyboard
import app.olauncher.ui.BaseFragment
import app.olauncher.ui.today.attachPressScale
import app.olauncher.ui.today.cards.WordsCard
import java.util.Random

/**
 * Full-screen drill quiz for one short round (#639): 5-8 items drawn from one type or a
 * mix of the weakest types, each question's format taken from the recognition ladder
 * (flashcard, tap, true-false, typed graduation). Wrong answers are corrected inline on
 * the same screen and nothing is requeued; the summary offers "Another 5" and
 * "Practise misses".
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
    private var recordedQuiz: Any? = null
    private var isPractiseMissesRound = false
    private val sessionGradedWordIds = mutableSetOf<Long>()
    private val roundRandom = Random()

    private val isWordOrderRound: Boolean get() = drillTypes.singleOrNull() == DrillType.WORD_ORDER

    private val autoAdvance = Runnable {
        autoAdvanceScheduled = false
        if (_binding == null) return@Runnable
        if (isWordOrderRound) {
            val q = wordOrderQuiz ?: return@Runnable
            if (!q.isAnswered) return@Runnable
            q.advance()
            render()
            return@Runnable
        }
        val q = quiz ?: return@Runnable
        if (!q.isAnswered) return@Runnable
        q.advance()
        selectedChoice = null
        lastRenderedItemId = null
        hintRevealedForItemId = null
        render()
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
    }

    /** The per-item maturity counts (rounds answered right), decoded from prefs. */
    private fun itemMaturity(): Map<DrillItemId, Int> {
        val context = context ?: return emptyMap()
        return DrillItemHistory.maturities(Prefs(context.applicationContext).drillItemHistory)
    }

    /** Builds a new 5-8 item round over [drillTypes] with ladder formats. */
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
        val steps = DrillRoundPlanner.plan(pools, itemMaturity(), roundRandom)
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
                    render()
                }
                q.isAnswered -> {
                    q.advance()
                    render()
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
                selectedChoice = null
                lastRenderedItemId = null
                hintRevealedForItemId = null
                render()
            }
            q.isAnswered -> {
                // Continues past a flashcard, an inline correction, or a right answer.
                q.advance()
                selectedChoice = null
                lastRenderedItemId = null
                hintRevealedForItemId = null
                render()
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
                render()
            }
            return
        }

        val q = quiz ?: return
        val results = q.itemResults()
        val missedItems = q.items.filter { results[it.id]?.isRight == false }
        buildMissesRound(missedItems)?.let { misses ->
            isPractiseMissesRound = true
            quiz = misses
            selectedChoice = null
            lastRenderedItemId = null
            hintRevealedForItemId = null
            render()
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
        b.choiceScroll.isVisible = inQuiz && q.current?.correctChoice != null
        b.typedContainer.isVisible = inQuiz && q.currentFormat == DrillFormat.TYPED
        b.wordOrderView.isVisible = false
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
            renderSummary(q)
            return
        }
        b.btnPractiseMisses.isVisible = false
        if (inQuiz) {
            renderQuestion(q)
        }
    }

    private fun drillTitleRes(): Int =
        if (drillTypes.size > 1) R.string.drill_mixed_title else DrillTables.labelRes(drillTypes.first())

    private fun formatLabelRes(format: DrillFormat): Int = when (format) {
        DrillFormat.FLASHCARD -> R.string.drill_round_flashcard
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

        if (question.isFlashcard) {
            renderFlashcard(q, question)
            return
        }

        b.tvPrompt.isVisible = true
        b.tvPrompt.text = question.statement ?: question.prompt
        renderHint(q, question)

        if (question.isTyped) {
            renderTypedQuestion(q, question)
        } else {
            renderChoiceQuestion(q, question)
        }
    }

    /**
     * A flashcard (rung 1): it shows the answer by design — that is its purpose, and it
     * is not scored. One tap on Continue moves to the next question.
     */
    private fun renderFlashcard(q: DrillQuiz, question: DrillQuizQuestion) {
        val b = binding
        b.tvPrompt.isVisible = true
        b.tvPrompt.text = question.prompt
        b.tvHint.isClickable = false
        b.tvHint.text = listOfNotNull(question.answer, question.hint).joinToString(" · ")
        b.tvHint.isVisible = true
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

    private fun renderChoiceQuestion(q: DrillQuiz, question: DrillQuizQuestion) {
        val b = binding
        val answered = q.isAnswered
        val correctChoice = question.correctChoice
        b.choiceGrid.removeAllViews()

        for (choice in question.choices) {
            val tv = newCell(formatChoiceToken(choice) ?: choice)
            tv.gravity = Gravity.CENTER
            tv.minHeight = resources.getDimensionPixelSize(R.dimen.quiz_choice_min_height)

            when (ConjugationCellLook.of(answered, choice, correctChoice.orEmpty(), selectedChoice)) {
                ConjugationCellLook.RIGHT -> tv.isActivated = true
                ConjugationCellLook.WRONG -> tv.setBackgroundResource(R.drawable.quiz_choice_wrong)
                ConjugationCellLook.NEUTRAL -> Unit
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
        }
    }

    private fun scheduleAutoAdvance() {
        if (autoAdvanceScheduled) return
        autoAdvanceScheduled = true
        _binding?.root?.postDelayed(autoAdvance, AUTO_ADVANCE_DELAY_MS)
    }

    private fun renderSegments(q: DrillQuiz) {
        val row = binding.segmentRow
        row.removeAllViews()
        val firstAttempts = q.firstAttempts()

        for ((index, step) in q.steps.withIndex()) {
            val seg = View(row.context)
            val firstTry = firstAttempts[step.item.id]
            // A flashcard is never scored, so its segment stays neutral instead of
            // taking the first-attempt colour of its tap question.
            val color = when {
                step.isFlashcard -> attrColor(R.attr.todayChipColor)
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
        // the summary row must say which statement the learner called false.
        val statementByItem = q.steps.filterNot { it.isFlashcard }.associateBy { it.id }
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

        b.root.hideKeyboard()
    }

    /**
     * Binds the sorted summary rows shared by the drill and word-order summaries:
     * mark icon, tint, prompt text, and correction line per result. A near-miss (almost)
     * row shows the retried input, the answer, and the near-miss note.
     */
    private fun bindSummaryRows(items: List<DrillSummaryItem>) {
        val b = binding
        val context = b.root.context
        b.summaryList.removeAllViews()
        for (item in items) {
            val itemBinding = ItemDrillSummaryBinding.inflate(layoutInflater, b.summaryList, false)
            val learnerAnswer = formatChoiceToken(item.learnerAnswer)
            itemBinding.tvPrompt.text = DrillSummaryLogic.formatPrompt(
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
                    itemBinding.tvCorrection.isVisible = false
                }
                DrillResult.ALMOST -> {
                    itemBinding.ivMark.setImageResource(R.drawable.ic_dot)
                    itemBinding.ivMark.setColorFilter(ContextCompat.getColor(context, R.color.accentSand))
                    itemBinding.tvCorrection.isVisible = false
                }
                DrillResult.WRONG -> {
                    itemBinding.ivMark.setImageResource(R.drawable.ic_close)
                    itemBinding.ivMark.setColorFilter(ContextCompat.getColor(context, R.color.accentRose))
                    val correction = DrillSummaryLogic.formatCorrection(item.prompt, item.answer, item.hint)
                    itemBinding.tvCorrection.text = correction
                    itemBinding.tvCorrection.isVisible = correction.isNotEmpty()
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
        val byType = DrillScores.roundEvidenceByType(q.firstAttempts(), q.typedResults(), abandoned)
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
