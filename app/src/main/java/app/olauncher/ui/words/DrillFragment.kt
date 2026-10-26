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
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.data.store.AUTO_ADVANCE_DELAY_MS
import app.olauncher.data.store.DrillItem
import app.olauncher.data.store.DrillQuiz
import app.olauncher.data.store.DrillQuizQuestion
import app.olauncher.data.store.DrillReviewFeed
import app.olauncher.data.store.DrillRound
import app.olauncher.data.store.DrillScores
import app.olauncher.data.store.DrillTables
import app.olauncher.data.store.DrillType
import app.olauncher.data.store.QuizAnswer
import app.olauncher.data.store.ReviewEventsStore
import app.olauncher.data.store.ReviewSessionLogic
import app.olauncher.data.store.SharedOfflineGermanStore
import app.olauncher.data.store.WordOrderItem
import app.olauncher.data.store.WordOrderQuiz
import app.olauncher.data.store.shouldAutoAdvance
import app.olauncher.databinding.FragmentDrillBinding
import app.olauncher.helper.DrawerScrollHelper
import app.olauncher.helper.Haptics
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.showKeyboard
import app.olauncher.ui.BaseFragment
import app.olauncher.ui.today.attachPressScale
import app.olauncher.ui.today.cards.WordsCard

/**
 * Full-screen drill quiz for any [DrillType]: renders multiple-choice round first,
 * followed by a typed round, with green on right, wait on wrong, and first-try score summary.
 */
class DrillFragment : BaseFragment() {

    private var _binding: FragmentDrillBinding? = null
    private val binding get() = _binding!!

    private var drillType: DrillType = DrillType.PREPOSITIONS
    private var quiz: DrillQuiz? = null
    private var wordOrderQuiz: WordOrderQuiz? = null
    private var selectedChoice: String? = null
    private var lastRenderedItemId: String? = null
    private var autoAdvanceScheduled = false
    private var recordedQuiz: Any? = null
    private val sessionGradedWordIds = mutableSetOf<Long>()

    private val autoAdvance = Runnable {
        autoAdvanceScheduled = false
        if (_binding == null) return@Runnable
        if (drillType == DrillType.WORD_ORDER) {
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
        binding.btnSecondary.setOnClickListener {
            Haptics.tick(it)
            findNavController().popBackStack()
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
        drillType = DrillType.fromId(typeStr) ?: DrillType.PREPOSITIONS
        val appContext = view.context.applicationContext
        val words = WordsCard.getStore(appContext).all()

        if (drillType == DrillType.WORD_ORDER) {
            if (wordOrderQuiz == null) {
                val items = WordOrderQuiz.items(words)
                if (items.isNotEmpty()) {
                    wordOrderQuiz = WordOrderQuiz(items)
                }
            }
        } else {
            if (quiz == null) {
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
                val items = DrillTables.items(drillType, words, genderLookup, pluralLookup)
                if (items.isNotEmpty()) {
                    quiz = DrillQuiz(items)
                }
            }
        }

        render()
    }

    private fun onPrimary(view: View) {
        if (drillType == DrillType.WORD_ORDER) {
            val q = wordOrderQuiz ?: return
            Haptics.tick(view)
            when {
                q.isFinished -> {
                    val appContext = view.context.applicationContext
                    val words = WordsCard.getStore(appContext).all()
                    val items = WordOrderQuiz.items(words)
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
                // Restart quiz
                val appContext = view.context.applicationContext
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
                val items = DrillTables.items(drillType, words, genderLookup, pluralLookup)
                quiz = DrillQuiz(items)
                selectedChoice = null
                lastRenderedItemId = null
                render()
            }
            q.isAnswered -> {
                q.advance()
                selectedChoice = null
                lastRenderedItemId = null
                render()
            }
            q.isTyped -> {
                val input = binding.etAnswer.text?.toString().orEmpty()
                val result = q.answerTyped(input) ?: return
                render()
                if (shouldAutoAdvance(result)) scheduleAutoAdvance()
            }
            else -> Unit
        }
    }

    private fun render() {
        val b = _binding ?: return
        val context = context ?: return

        if (drillType == DrillType.WORD_ORDER) {
            val q = wordOrderQuiz
            val empty = q == null || q.total == 0
            val finished = q != null && q.total > 0 && q.isFinished
            val inQuiz = q != null && !q.isFinished

            b.quizContainer.isVisible = inQuiz
            b.questionCard.isVisible = inQuiz
            b.choiceScroll.isVisible = false
            b.typedContainer.isVisible = false
            b.wordOrderView.isVisible = inQuiz
            b.quizCard.isVisible = finished
            b.summaryContainer.isVisible = finished
            b.tvEmpty.isVisible = empty
            b.btnPrimary.isVisible = !empty
            b.btnSecondary.isVisible = finished

            if (empty) {
                b.btnPrimary.isVisible = false
                return
            }
            if (finished) {
                renderWordOrderSummary(q)
                return
            }
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
        b.questionCard.isVisible = inQuiz
        b.choiceScroll.isVisible = inQuiz && !q.isTyped
        b.typedContainer.isVisible = inQuiz && q.isTyped
        b.wordOrderView.isVisible = false
        b.quizCard.isVisible = finished
        b.summaryContainer.isVisible = finished
        b.tvEmpty.isVisible = empty
        b.btnPrimary.isVisible = !empty
        b.btnSecondary.isVisible = finished

        if (empty) {
            b.btnPrimary.isVisible = false
            return
        }
        if (finished) {
            renderSummary(q)
            return
        }
        if (inQuiz) {
            renderQuestion(q)
        }
    }

    private fun renderQuestion(q: DrillQuiz) {
        val b = binding
        val context = b.root.context
        val question = q.current ?: return

        b.tvTitle.text = context.getString(DrillTables.labelRes(drillType))
        val roundLabel = if (q.isTyped) {
            context.getString(R.string.drill_round_typed)
        } else {
            context.getString(R.string.drill_round_choice)
        }
        val currentIdx = (q.items.indexOf(question.item).takeIf { it >= 0 } ?: 0) + 1
        b.tvProgress.text = context.getString(R.string.drill_progress, roundLabel, currentIdx, q.total)

        renderSegments(q)

        b.tvPrompt.isVisible = true
        b.tvPrompt.text = question.prompt
        b.tvHint.isVisible = !question.hint.isNullOrBlank()
        b.tvHint.text = question.hint.orEmpty()

        if (q.isTyped) {
            renderTypedQuestion(q, question)
        } else {
            renderChoiceQuestion(q, question)
        }
    }

    private fun renderChoiceQuestion(q: DrillQuiz, question: DrillQuizQuestion) {
        val b = binding
        val answered = q.isAnswered
        b.choiceGrid.removeAllViews()

        for (choice in question.choices) {
            val tv = newCell(choice)
            tv.gravity = Gravity.CENTER
            tv.minHeight = resources.getDimensionPixelSize(R.dimen.quiz_choice_min_height)

            when (ConjugationCellLook.of(answered, choice, question.answer, selectedChoice)) {
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

        val waitingForContinue = answered && !shouldAutoAdvance(QuizAnswer(selectedChoice == question.answer, question.answer))
        if (answered && !waitingForContinue) scheduleAutoAdvance()
        b.btnPrimary.setText(R.string.conjugation_continue)
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
            b.tvCorrection.isVisible = false
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

    private fun renderSummary(q: DrillQuiz) {
        recordFinishedRound(q)
        val b = binding
        val context = b.root.context
        b.tvScore.text = context.getString(R.string.conjugation_score, q.firstTryScore, q.total)
        b.btnPrimary.setText(R.string.drill_restart)
        b.btnPrimary.isVisible = true
        b.btnPrimary.isEnabled = true
        b.btnPrimary.alpha = 1f
        b.btnSecondary.isVisible = true
        b.root.hideKeyboard()
    }

    private fun recordFinishedRound(q: DrillQuiz) {
        if (recordedQuiz === q) return
        recordedQuiz = q
        val context = context ?: return
        val appContext = context.applicationContext
        val prefs = Prefs(appContext)
        DrillScores.recordRound(prefs, drillType.id, q.total, q.firstTryScore)

        val words = WordsCard.getStore(appContext).all()
        val today = WordsCard.epochDaySupplier()
        val grades = DrillReviewFeed.grades(q.firstAttempts(), words, today)
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
        b.tvScore.text = context.getString(R.string.conjugation_score, q.firstTryScore, q.total)
        b.btnPrimary.setText(R.string.drill_restart)
        b.btnPrimary.isVisible = true
        b.btnPrimary.isEnabled = true
        b.btnPrimary.alpha = 1f
        b.btnSecondary.isVisible = true
        b.root.hideKeyboard()
    }

    private fun recordFinishedWordOrderRound(q: WordOrderQuiz) {
        if (recordedQuiz === q) return
        recordedQuiz = q
        val context = context ?: return
        val appContext = context.applicationContext
        val prefs = Prefs(appContext)
        DrillScores.recordRound(prefs, drillType.id, q.total, q.firstTryScore)

        val words = WordsCard.getStore(appContext).all()
        val today = WordsCard.epochDaySupplier()
        val grades = DrillReviewFeed.grades(q.firstAttempts(), words, today)
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
        _binding?.root?.removeCallbacks(autoAdvance)
        autoAdvanceScheduled = false
        _binding?.root?.hideKeyboard()
        super.onDestroyView()
        _binding = null
    }
}
