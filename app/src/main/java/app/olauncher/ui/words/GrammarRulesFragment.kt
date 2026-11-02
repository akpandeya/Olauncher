package app.olauncher.ui.words

import android.content.Context
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
import app.olauncher.data.store.GrammarRuleGroups
import app.olauncher.data.store.GrammarRulesIndex
import app.olauncher.databinding.FragmentGrammarRulesBinding
import app.olauncher.databinding.ItemGrammarRuleBinding
import app.olauncher.databinding.ItemGrammarRuleGroupHeaderBinding
import app.olauncher.helper.Haptics
import app.olauncher.ui.BaseFragment

/**
 * The grammar-rules index (#792, docs/design/german-hidden-surfaces.md §4.1):
 * every authored help-card rule of [DrillHelpContent] in one browsable list —
 * title plus the one-line plain-English rule — grouped by topic in a suggested
 * learning order ([GrammarRuleGroups], #809) under a section header per group.
 * A title search ([GrammarRulesIndex], Context-free and JVM tested) keeps the
 * group order and headers and hides the groups it finds nothing in. A row tap
 * opens the rule's full help card on the detail screen. Reached from the Grammar
 * rules header button in the Drills hub; no separate screen links here and the
 * miss help card does not.
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
        val sections = GrammarRulesIndex.filterGrouped(GrammarRuleGroups.ordered, all, query)
        val shown = sections.sumOf { it.rules.size }

        b.tvCount.text = if (query.isNotBlank()) {
            context.getString(R.string.words_search_count, shown, all.size)
        } else {
            context.resources.getQuantityString(R.plurals.grammar_rules_count, all.size, all.size)
        }
        b.rulesPanel.isVisible = sections.isNotEmpty()
        b.tvNoMatch.isVisible = sections.isEmpty()
        b.tvNoMatch.text = context.getString(R.string.grammar_rules_search_no_match, query.trim())

        b.rulesPanel.removeAllViews()
        for (section in sections) {
            val header = ItemGrammarRuleGroupHeaderBinding.inflate(layoutInflater, b.rulesPanel, false)
            header.tvGroupTitle.text = groupLabel(context, section.group.key)
            b.rulesPanel.addView(header.root)
            for (rule in section.rules) {
                val item = ItemGrammarRuleBinding.inflate(layoutInflater, b.rulesPanel, false)
                item.tvRuleTitle.text = rule.title
                item.tvRuleLine.text = rule.rule
                item.root.setOnClickListener { openRule(it, rule.id) }
                b.rulesPanel.addView(item.root)
            }
        }
    }

    /**
     * The group's user-facing topic label, `grammar_rules_group_<key>` in
     * strings.xml (GrammarRuleGroupsTest checks every key has one). Falls back
     * to the key itself only if the string were missing, so a broken group
     * still shows something identifiable.
     */
    private fun groupLabel(context: Context, key: String): String {
        val id = context.resources.getIdentifier("grammar_rules_group_$key", "string", context.packageName)
        return if (id != 0) context.getString(id) else key
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
