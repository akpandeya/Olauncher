package app.olauncher.ui.words

import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.TextView
import androidx.annotation.AttrRes
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import app.olauncher.R
import app.olauncher.data.store.Conjugation
import app.olauncher.data.store.AUTO_ADVANCE_DELAY_MS
import app.olauncher.data.store.ConjugationQuiz
import app.olauncher.data.store.FormStatus
import app.olauncher.data.store.OfflineGermanStore
import app.olauncher.data.store.QuizAnswer
import app.olauncher.data.store.VerbParadigm
import app.olauncher.data.store.shouldAutoAdvance
import app.olauncher.databinding.FragmentConjugationBinding
import app.olauncher.helper.Haptics
import app.olauncher.ui.BaseFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen conjugation quiz for the `word` nav argument: the offline
 * dictionary's present-tense cells, asked one pronoun at a time with four
 * form choices. The user picks one, checks, and continues; missed forms come
 * back until answered right, and the page ends on a first-try summary with a
 * retry for the misses. A word with no verb entry (nouns, unknown words) gets
 * a graceful one-liner. System back exits normally.
 */
class ConjugationFragment : BaseFragment() {

    private var _binding: FragmentConjugationBinding? = null
    private val binding get() = _binding!!

    private var paradigm: VerbParadigm? = null
    private var quiz: ConjugationQuiz? = null
    private var selectedChoice: String? = null
    private var loadStarted = false
    private var loaded = false
    private var autoAdvanceScheduled = false
    private val autoAdvance = Runnable {
        autoAdvanceScheduled = false
        val q = quiz ?: return@Runnable
        if (_binding == null || !q.isAnswered) return@Runnable
        q.advance()
        selectedChoice = null
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
        binding.btnPrimary.setOnClickListener { onPrimary(it) }
        binding.btnSecondary.setOnClickListener {
            Haptics.tick(it)
            findNavController().popBackStack()
        }
        render()
        // A recreated view re-renders the held state above; only the first view loads.
        if (loadStarted) return
        loadStarted = true
        val word = arguments?.getString("word").orEmpty()
        val appContext = view.context.applicationContext
        // Fragment-scoped: the load must outlive the view it started with.
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { OfflineGermanStore(appContext).paradigm(word) }
            paradigm = result
            quiz = result?.let { ConjugationQuiz(Conjugation.drillPrompts(it)) }
            loaded = true
            render()
        }
    }

    private fun onPrimary(view: View) {
        val q = quiz ?: return
        Haptics.tick(view)
        when {
            q.isRoundFinished -> {
                // Summary: retry the misses.
                if (q.startRetry()) {
                    selectedChoice = null
                    render()
                }
            }
            q.isAnswered -> {
                q.advance()
                selectedChoice = null
                render()
            }
            else -> Unit
        }
    }

    private fun render() {
        val b = _binding ?: return
        val context = context ?: return
        val q = quiz
        val noParadigm = loaded && (q == null || q.total == 0)
        val finished = loaded && q != null && q.total > 0 && q.isRoundFinished
        val inQuiz = loaded && q != null && !q.isRoundFinished

        b.quizContainer.isVisible = inQuiz
        b.quizCardContent.isVisible = inQuiz
        b.choiceScroll.isVisible = inQuiz
        b.questionCard.isVisible = inQuiz
        b.quizCard.isVisible = finished
        b.summaryContainer.isVisible = finished
        b.tvEmpty.isVisible = noParadigm
        b.conjugationFooter.isVisible = loaded
        b.btnPrimary.isVisible = !noParadigm
        b.btnSecondary.isVisible = finished
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
            context.getString(R.string.conjugation_tense_present),
            question.formIndex + 1,
            q.total,
        )
        renderSegments(q, question.formIndex)
        b.tvPrompt.text = context.getString(R.string.conjugation_prompt, lemma)
        b.tvPronoun.text = question.pronoun
        b.tvMeaning.text = meaningFor(question.pronoun)

        val answered = q.isAnswered
        b.choiceGrid.removeAllViews()
        for (choice in question.choices) {
            val tv = newCell(choice, TextSize.Large)
            tv.gravity = Gravity.CENTER
            tv.minHeight = dp(56)
            when {
                answered && choice == question.answer -> tv.isActivated = true
                answered && choice == selectedChoice -> tv.setBackgroundResource(R.drawable.quiz_choice_wrong)
            }
            if (!answered) {
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
            if (i > 0) lp.marginStart = dp(5)
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
        b.btnPrimary.isVisible = missed.isNotEmpty()
        b.btnPrimary.isEnabled = true
        b.btnPrimary.alpha = 1f
        b.btnPrimary.text = if (missed.size == 1) {
            context.getString(R.string.conjugation_practise_again, q.pronoun(missed.first()))
        } else {
            context.getString(R.string.conjugation_practise_misses)
        }
    }

    private enum class TextSize { Large, Small }

    private fun newCell(text: String, size: TextSize): TextView = TextView(binding.root.context).apply {
        this.text = text
        setTextAppearance(if (size == TextSize.Large) R.style.TextMedium else R.style.TextSmall)
        setBackgroundResource(R.drawable.quiz_choice)
        setPadding(dp(12), dp(12), dp(12), dp(12))
        isClickable = true
    }

    private fun cellParams(grid: GridLayout): GridLayout.LayoutParams =
        GridLayout.LayoutParams(
            GridLayout.spec(GridLayout.UNDEFINED, 1f),
            GridLayout.spec(GridLayout.UNDEFINED, 1f),
        ).apply {
            width = 0
            setMargins(dp(4), dp(4), dp(4), dp(4))
        }

    private fun meaningFor(pronoun: String): String = getString(
        when (pronoun) {
            "ich" -> R.string.conjugation_meaning_ich
            "du" -> R.string.conjugation_meaning_du
            "er/sie/es" -> R.string.conjugation_meaning_er
            "wir" -> R.string.conjugation_meaning_wir
            "ihr" -> R.string.conjugation_meaning_ihr
            else -> R.string.conjugation_meaning_sie
        },
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun attrColor(@AttrRes attr: Int): Int {
        val tv = TypedValue()
        binding.root.context.theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    override fun onDestroyView() {
        _binding?.root?.removeCallbacks(autoAdvance)
        autoAdvanceScheduled = false
        super.onDestroyView()
        _binding = null
    }
}
