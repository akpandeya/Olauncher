package app.olauncher.ui.words

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.navigation.fragment.findNavController
import app.olauncher.R
import app.olauncher.data.store.DrillHelpContent
import app.olauncher.data.store.DrillHelpRule
import app.olauncher.data.store.GrammarRulesIndex
import app.olauncher.databinding.FragmentGrammarRulesBinding
import app.olauncher.databinding.ItemGrammarRuleBinding
import app.olauncher.helper.Haptics
import app.olauncher.ui.BaseFragment

/**
 * The grammar-rules index (#792, docs/design/german-hidden-surfaces.md §4.1):
 * every authored help-card rule of [DrillHelpContent] in one browsable list —
 * title plus the one-line plain-English rule — narrowed live by a title
 * search ([GrammarRulesIndex], Context-free and JVM tested). A row tap opens
 * the rule's full help card on the detail screen. Reached from the Grammar
 * rules entry in the Drills hub; no separate screen links here and the miss
 * help card does not.
 */
class GrammarRulesFragment : BaseFragment() {

    private var _binding: FragmentGrammarRulesBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentGrammarRulesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.etRuleSearch.doAfterTextChanged {
            binding.btnClearSearch.isVisible = !it.isNullOrEmpty()
            render()
        }
        binding.etRuleSearch.setOnEditorActionListener { v, _, _ ->
            (v.context.getSystemService(InputMethodManager::class.java))
                ?.hideSoftInputFromWindow(v.windowToken, 0)
            true
        }
        binding.btnClearSearch.setOnClickListener { binding.etRuleSearch.text?.clear() }
        render()
    }

    private fun render() {
        val b = _binding ?: return
        val context = b.root.context
        val all = DrillHelpContent.allRules()
        val query = b.etRuleSearch.text?.toString().orEmpty()
        val shown = GrammarRulesIndex.filter(all, query)

        b.tvCount.text = if (query.isNotBlank()) {
            context.getString(R.string.words_search_count, shown.size, all.size)
        } else {
            context.resources.getQuantityString(R.plurals.grammar_rules_count, all.size, all.size)
        }
        b.rulesPanel.isVisible = shown.isNotEmpty()
        b.tvNoMatch.isVisible = shown.isEmpty()
        b.tvNoMatch.text = context.getString(R.string.grammar_rules_search_no_match, query.trim())

        b.rulesPanel.removeAllViews()
        for (rule in shown) {
            val item = ItemGrammarRuleBinding.inflate(layoutInflater, b.rulesPanel, false)
            item.tvRuleTitle.text = rule.title
            item.tvRuleLine.text = rule.rule
            item.root.setOnClickListener { openRule(it, rule.id) }
            b.rulesPanel.addView(item.root)
        }
    }

    /** Opens the rule's detail screen; ignores a second tap before navigation lands. */
    private fun openRule(view: View, ruleId: String) {
        Haptics.tick(view)
        val navController = findNavController()
        if (navController.currentDestination?.id == R.id.grammarRulesFragment) {
            navController.navigate(
                R.id.action_grammarRulesFragment_to_grammarRuleFragment,
                Bundle().apply { putString("ruleId", ruleId) },
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
