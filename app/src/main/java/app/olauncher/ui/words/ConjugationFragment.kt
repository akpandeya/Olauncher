package app.olauncher.ui.words

import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.data.store.Conjugation
import app.olauncher.data.store.AUTO_ADVANCE_DELAY_MS
import app.olauncher.data.store.ConjugationQuiz
import app.olauncher.data.store.FormStatus
import app.olauncher.data.store.OfflineGermanStore
import app.olauncher.data.store.QuizAnswer
import app.olauncher.data.store.QuizQuestion
import app.olauncher.data.store.Tense
import app.olauncher.data.store.VerbParadigm
import app.olauncher.data.store.shouldAutoAdvance
import app.olauncher.databinding.FragmentConjugationBinding
import app.olauncher.helper.Haptics
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.showKeyboard
import app.olauncher.ui.BaseFragment
import app.olauncher.ui.today.attachPressScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen conjugation quiz for the `word` nav argument: the offline
 * dictionary's present-tense cells, asked one pronoun at a time with four
 * form choices in multiple choice or an input field in typed mode. The user
 * picks or types, checks, and continues; missed forms come back until answered
 * right, and the page ends on a first-try summary.
 * System back exits normally.
 */
class ConjugationFragment : BaseFragment() {

    private var _binding: FragmentConjugationBinding? = null
    private val binding get() = _binding!!

    private var paradigms: Map<Tense, VerbParadigm> = emptyMap()
    private var currentTense: Tense = Tense.PRESENT
    private val paradigm: VerbParadigm? get() = paradigms[currentTense] ?: paradigms.values.firstOrNull()
    private var quiz: ConjugationQuiz? = null
    private var selectedChoice: String? = null
    private var lastRenderedFormIndex: Int? = null
    private var loadStarted = false
    private var loaded = false
    private var autoAdvanceScheduled = false
    private val autoAdvance = Runnable {
        autoAdvanceScheduled = false
        val q = quiz ?: return@Runnable
        if (_binding == null || !q.isAnswered) return@Runnable
        q.advance()
        selectedChoice = null
        lastRenderedFormIndex = null
        render()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentConjugationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.licenseNote.text = getText(R.string.wiktionary_license_note)
        binding.licenseNote.movementMethod = android.text.method.LinkMovementMethod.getInstance()
        binding.btnPrimary.setOnClickListener {
            if (binding.btnPrimary.text == getString(R.string.conjugation_type_them)) {
                Haptics.tick(it)
                startTypedRound()
            } else {
                onPrimary(it)
            }
        }
        binding.btnType.setOnClickListener {
            Haptics.tick(it)
            startTypedRound()
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
        if (loaded) {
            setupTenseChips()
        }
        render()
        // A recreated view re-renders the held state above; only the first view loads.
        if (loadStarted) return
        loadStarted = true
        val word = arguments?.getString("word").orEmpty()
        val appContext = view.context.applicationContext
        val offlineAllowed = Prefs(appContext).germanOfflineDictionary
        // Fragment-scoped: the load must outlive the view it started with.
        lifecycleScope.launch {
            val result = if (offlineAllowed) {
                withContext(Dispatchers.IO) { OfflineGermanStore(appContext).paradigms(word) }
            } else {
                emptyMap()
            }
            paradigms = result
            val initialTense = Tense.PRESENT.takeIf { it in result } ?: result.keys.firstOrNull() ?: Tense.PRESENT
            currentTense = initialTense
            quiz = result[initialTense]?.let { ConjugationQuiz(Conjugation.drillPrompts(it), tense = initialTense) }
            loaded = true
            setupTenseChips()
            render()
        }
    }

    private val Tense.labelRes: Int get() = when (this) {
        Tense.PRESENT -> R.string.conjugation_tense_present
        Tense.PRETERITE -> R.string.conjugation_tense_preterite
        Tense.PERFEKT -> R.string.conjugation_tense_perfekt
        Tense.SUBJUNCTIVE_II -> R.string.conjugation_tense_subjunctive_ii
        Tense.IMPERATIVE -> R.string.conjugation_tense_imperative
        Tense.STAMMFORMEN -> R.string.conjugation_tense_stammformen
    }

    private fun setupTenseChips() {
        val b = _binding ?: return
        b.tenseRow.removeAllViews()
        val available = Tense.values().filter { it in paradigms && paradigms[it]!!.cells.isNotEmpty() }
        val margin = b.root.resources.getDimensionPixelSize(R.dimen.quiz_cell_margin) * 2
        for ((index, tense) in available.withIndex()) {
            val chip = newTenseChip(tense)
            chip.tag = tense
            chip.isSelected = (tense == currentTense)
            chip.setOnClickListener {
                Haptics.tick(it)
                selectTense(tense)
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                if (index > 0) marginStart = margin
            }
            b.tenseRow.addView(chip, lp)
        }
    }

    private fun updateTenseChipsSelection() {
        val b = _binding ?: return
        for (i in 0 until b.tenseRow.childCount) {
            val chip = b.tenseRow.getChildAt(i) as? TextView ?: continue
            chip.isSelected = (chip.tag == currentTense)
        }
    }

    private fun selectTense(tense: Tense) {
        if (currentTense == tense && quiz != null && !quiz!!.isRoundFinished) return
        currentTense = tense
        val p = paradigms[tense] ?: return
        _binding?.root?.removeCallbacks(autoAdvance)
        autoAdvanceScheduled = false
        selectedChoice = null
        lastRenderedFormIndex = null
        quiz = ConjugationQuiz(Conjugation.drillPrompts(p), tense = tense)
        updateTenseChipsSelection()
        render()
    }

    private fun newTenseChip(tense: Tense): TextView = TextView(binding.root.context).apply {
        text = getString(tense.labelRes)
        setTextAppearance(R.style.TodayChip)
        setBackgroundResource(R.drawable.today_chip_selectable)
        gravity = Gravity.CENTER
        minHeight = resources.getDimensionPixelSize(R.dimen.today_touch_min_height)
        val pad = resources.getDimensionPixelSize(R.dimen.today_chip_padding_horizontal)
        setPadding(pad, 0, pad, 0)
        isClickable = true
        isFocusable = true
        attachPressScale()
    }

    private fun startTypedRound() {
        val p = paradigm ?: return
        quiz = ConjugationQuiz(Conjugation.drillPrompts(p), isTyped = true, tense = currentTense)
        selectedChoice = null
        lastRenderedFormIndex = null
        render()
    }

    private fun onPrimary(view: View) {
        val q = quiz ?: return
        Haptics.tick(view)
        when {
            q.isRoundFinished -> {
                if (!q.isTyped && q.missedForms.isEmpty()) {
                    startTypedRound()
                } else if (q.startRetry()) {
                    selectedChoice = null
                    lastRenderedFormIndex = null
                    render()
                }
            }
            q.isAnswered -> {
                q.advance()
                selectedChoice = null
                lastRenderedFormIndex = null
                render()
            }
            q.isTyped -> {
                val input = binding.etAnswer.text?.toString().orEmpty()
                val result = q.answer(input) ?: return
                render()
                if (shouldAutoAdvance(result)) scheduleAutoAdvance()
            }
            else -> Unit
        }
    }

    private fun render() {
        val b = _binding ?: return
        val context = context ?: return
        val q = quiz
        val availableTenses = Tense.values().filter { it in paradigms && paradigms[it]!!.cells.isNotEmpty() }
        val noParadigm = loaded && (q == null || q.total == 0)
        val finished = loaded && q != null && q.total > 0 && q.isRoundFinished
        val inQuiz = loaded && q != null && !q.isRoundFinished

        b.quizContainer.isVisible = inQuiz
        b.tenseScroll.isVisible = inQuiz && availableTenses.isNotEmpty()
        b.quizCardContent.isVisible = inQuiz
        b.choiceScroll.isVisible = inQuiz && !q.isTyped
        b.typedContainer.isVisible = inQuiz && q.isTyped
        b.questionCard.isVisible = inQuiz
        b.quizCard.isVisible = finished
        b.summaryContainer.isVisible = finished
        b.tvEmpty.isVisible = noParadigm
        b.conjugationFooter.isVisible = loaded
        b.btnPrimary.isVisible = !noParadigm
        b.btnSecondary.isVisible = finished
        b.btnType.isVisible = false
        b.licenseNote.isVisible = !noParadigm

        if (noParadigm) {
            b.tvEmpty.text = context.getString(
                R.string.conjugation_not_in_dictionary,
                arguments?.getString("word").orEmpty(),
            )
            return
        }
        if (q == null) return
        if (finished) {
            renderSummary(q)
            return
        }
        if (inQuiz) renderQuestion(q)
    }

    private fun renderQuestion(q: ConjugationQuiz) {
        val b = binding
        val context = b.root.context
        val question = q.current ?: return
        val lemma = paradigm?.lemma.orEmpty()
        b.tvLemma.text = lemma
        b.tvProgress.text = context.getString(
            R.string.conjugation_progress,
            context.getString(currentTense.labelRes),
            question.formIndex + 1,
            q.total,
        )
        updateTenseChipsSelection()
        renderSegments(q, question.formIndex)
        b.tvPrompt.text = context.getString(R.string.conjugation_prompt, lemma)
        b.tvPronoun.text = question.pronoun
        b.tvMeaning.text = meaningFor(question.pronoun)

        if (q.isTyped) {
            renderTypedQuestion(q, question)
        } else {
            renderChoiceQuestion(q, question)
        }
    }

    private fun renderChoiceQuestion(q: ConjugationQuiz, question: QuizQuestion) {
        val b = binding
        val answered = q.isAnswered
        b.choiceGrid.removeAllViews()
        for (choice in question.choices) {
            val tv = newCell(choice, TextSize.Large)
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
                    // answer() ignores a second tap, so a double tap cannot advance twice.
                    val result = q.answer(choice) ?: return@setOnClickListener
                    selectedChoice = choice
                    if (shouldAutoAdvance(result)) scheduleAutoAdvance()
                    render()
                }
            }
            b.choiceGrid.addView(tv, cellParams(b.choiceGrid))
        }

        // Picking a choice answers at once. Right: green, then on to the next
        // question by itself. Wrong: Continue stays so the right form can be read.
        val waitingForContinue = answered && !shouldAutoAdvance(QuizAnswer(selectedChoice == question.answer, question.answer))
        if (answered && !waitingForContinue) scheduleAutoAdvance()
        b.btnPrimary.setText(R.string.conjugation_continue)
        b.btnPrimary.isVisible = waitingForContinue
        b.btnPrimary.isEnabled = true
        b.btnPrimary.alpha = 1f
    }

    private fun renderTypedQuestion(q: ConjugationQuiz, question: QuizQuestion) {
        val b = binding
        val answered = q.isAnswered
        if (!answered) {
            if (lastRenderedFormIndex != question.formIndex) {
                lastRenderedFormIndex = question.formIndex
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
            lastRenderedFormIndex = null
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

    private fun renderSegments(q: ConjugationQuiz, currentIndex: Int) {
        val row = binding.segmentRow
        row.removeAllViews()
        for (i in 0 until q.total) {
            val seg = View(row.context)
            val color = when {
                q.status(i) == FormStatus.MISSED -> attrColor(R.attr.dangerColor)
                q.status(i) == FormStatus.RIGHT_FIRST_TRY -> attrColor(R.attr.accentColor)
                i == currentIndex -> attrColor(R.attr.primaryColor)
                else -> attrColor(R.attr.todayChipColor)
            }
            seg.setBackgroundColor(color)
            val lp = android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            if (i > 0) lp.marginStart = row.resources.getDimensionPixelSize(R.dimen.quiz_segment_gap)
            row.addView(seg, lp)
        }
    }

    private fun renderSummary(q: ConjugationQuiz) {
        val b = binding
        val context = b.root.context
        b.tvScore.text = context.getString(R.string.conjugation_score, q.firstTryScore, q.total)
        b.summaryGrid.removeAllViews()
        for (i in 0 until q.total) {
            val misses = q.missCount(i)
            val text = if (misses == 0) {
                context.getString(R.string.conjugation_cell_right, q.pronoun(i), q.form(i))
            } else {
                context.getString(R.string.conjugation_cell_missed, q.pronoun(i), q.form(i), misses)
            }
            val tv = newCell(text, TextSize.Small)
            tv.setBackgroundResource(if (misses == 0) R.drawable.today_chip else R.drawable.quiz_choice_wrong)
            tv.gravity = Gravity.CENTER_VERTICAL
            b.summaryGrid.addView(tv, cellParams(b.summaryGrid))
        }
        val missed = q.missedForms
        if (!q.isTyped) {
            if (missed.isNotEmpty()) {
                b.btnPrimary.isVisible = true
                b.btnPrimary.isEnabled = true
                b.btnPrimary.alpha = 1f
                b.btnPrimary.text = if (missed.size == 1) {
                    context.getString(R.string.conjugation_practise_again, q.pronoun(missed.first()))
                } else {
                    context.getString(R.string.conjugation_practise_misses)
                }
                b.btnType.isVisible = true
            } else {
                b.btnPrimary.isVisible = true
                b.btnPrimary.isEnabled = true
                b.btnPrimary.alpha = 1f
                b.btnPrimary.text = context.getString(R.string.conjugation_type_them)
                b.btnType.isVisible = false
            }
        } else {
            b.btnType.isVisible = false
            if (missed.isNotEmpty()) {
                b.btnPrimary.isVisible = true
                b.btnPrimary.isEnabled = true
                b.btnPrimary.alpha = 1f
                b.btnPrimary.text = if (missed.size == 1) {
                    context.getString(R.string.conjugation_practise_again, q.pronoun(missed.first()))
                } else {
                    context.getString(R.string.conjugation_practise_misses)
                }
            } else {
                b.btnPrimary.isVisible = false
            }
        }
        b.btnSecondary.isVisible = true
        b.root.hideKeyboard()
    }

    private enum class TextSize { Large, Small }

    private fun newCell(text: String, size: TextSize): TextView = TextView(binding.root.context).apply {
        this.text = text
        setTextAppearance(if (size == TextSize.Large) R.style.TodayCardBodyBold else R.style.TodayCardBody)
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

    private fun meaningFor(pronoun: String): String = getString(
        when (pronoun) {
            "ich" -> R.string.conjugation_meaning_ich
            "du" -> R.string.conjugation_meaning_du
            "er/sie/es" -> R.string.conjugation_meaning_er
            "wir" -> R.string.conjugation_meaning_wir
            "ihr" -> R.string.conjugation_meaning_ihr
            "sie/Sie", "Sie" -> R.string.conjugation_meaning_sie
            "Präteritum" -> R.string.conjugation_meaning_preterite
            "Partizip II" -> R.string.conjugation_meaning_participle
            else -> R.string.conjugation_meaning_sie
        },
    )

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
