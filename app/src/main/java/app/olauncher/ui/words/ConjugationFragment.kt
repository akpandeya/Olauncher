package app.olauncher.ui.words

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import app.olauncher.R
import app.olauncher.data.store.Conjugation
import app.olauncher.data.store.DrillPrompt
import app.olauncher.data.store.OfflineGermanStore
import app.olauncher.data.store.VerbParadigm
import app.olauncher.databinding.FragmentConjugationBinding
import app.olauncher.helper.Haptics
import app.olauncher.ui.BaseFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen conjugation drill for the `word` nav argument: the offline
 * dictionary's present-tense cells, prompted one pronoun at a time. A tap on
 * the card reveals the form, Next advances, and the page ends on a one-line
 * summary with a restart. A word with no verb entry (nouns, unknown words)
 * gets a graceful one-liner instead of a drill. System back exits normally.
 */
class ConjugationFragment : BaseFragment() {

    private var _binding: FragmentConjugationBinding? = null
    private val binding get() = _binding!!

    private var paradigm: VerbParadigm? = null
    private var prompts: List<DrillPrompt> = emptyList()
    private var index = 0
    private var revealed = false
    private var loadStarted = false
    private var loaded = false
    private var lastAdvanceAt = 0L

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
        binding.drillCard.setOnClickListener {
            if (revealed || index >= prompts.size) return@setOnClickListener
            Haptics.tick(it)
            revealed = true
            render()
        }
        binding.btnNext.setOnClickListener {
            Haptics.tick(it)
            advance()
        }
        binding.btnRestart.setOnClickListener {
            Haptics.tick(it)
            prompts = paradigm?.let(Conjugation::drillPrompts).orEmpty()
            index = 0
            revealed = false
            render()
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
            prompts = result?.let(Conjugation::drillPrompts).orEmpty()
            index = 0
            revealed = false
            loaded = true
            render()
        }
    }

    private fun advance() {
        // A tap lands on the *next* card once render() advances; dropping taps
        // within the debounce keeps rapid tapping from skipping unseen forms.
        val now = SystemClock.uptimeMillis()
        if (now - lastAdvanceAt < ADVANCE_DEBOUNCE_MS) return
        lastAdvanceAt = now
        if (index >= prompts.size) return
        index++
        revealed = false
        render()
    }

    private fun render() {
        val b = _binding ?: return
        val context = context ?: return
        val inDrill = loaded && index < prompts.size
        val finished = loaded && prompts.isNotEmpty() && index >= prompts.size
        val noParadigm = loaded && prompts.isEmpty()

        b.drillCard.isVisible = inDrill
        b.summaryContainer.isVisible = finished
        b.tvEmpty.isVisible = noParadigm
        // The footer's entries are drill controls; the graceful state hides it whole.
        b.conjugationFooter.isVisible = !noParadigm && loaded
        b.tvLemma.isVisible = inDrill
        b.tvProgress.isVisible = inDrill
        b.btnNext.isVisible = inDrill
        b.btnRestart.isVisible = finished

        if (noParadigm) {
            b.tvEmpty.text = context.getString(
                R.string.conjugation_not_in_dictionary,
                arguments?.getString("word").orEmpty(),
            )
            return
        }
        if (finished) {
            b.tvSummary.text = context.getString(R.string.conjugation_complete, prompts.size)
            return
        }
        if (!inDrill) return
        val prompt = prompts[index]
        b.tvPronoun.text = prompt.pronoun
        b.tvForm.text = prompt.form
        b.tvForm.isVisible = revealed
        b.tvLemma.text = paradigm?.lemma.orEmpty()
        b.tvProgress.text = context.getString(R.string.conjugation_progress, index + 1, prompts.size)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private companion object {
        const val ADVANCE_DEBOUNCE_MS = 300L
    }
}
