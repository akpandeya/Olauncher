package app.olauncher.ui.words

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import app.olauncher.R
import app.olauncher.data.store.AiChatSubject
import app.olauncher.data.store.DrillHelpCard
import app.olauncher.data.store.DrillHelpContent
import app.olauncher.data.store.DrillHelpRule
import app.olauncher.databinding.FragmentGrammarRuleBinding
import app.olauncher.helper.Haptics
import app.olauncher.ui.BaseFragment

/**
 * One rule's full help card, opened from the grammar-rules index (#792): the
 * shared #651 help-card rendering — chips, rule, first-exposure line (#808,
 * design §2: the index detail is a teaching view with no item, so the rule's
 * one-case list may name its case), forms table, example — through
 * [HelpCardViewBinder], with the state built by [DrillHelpCard.ruleIndexState]
 * (no item attached, so no answer cell and no miss line). The "Ask AI about
 * this rule" row is an ordinary chat entry (docs/design/ask-ai-chat.md
 * §Entry points): visible iff the practice gate is open and the provider
 * config valid — hidden, never greyed — and it opens the chat on the rule
 * subject, exactly like every other entry.
 */
class GrammarRuleFragment : BaseFragment() {

    private var _binding: FragmentGrammarRuleBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentGrammarRuleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = binding
        val context = b.root.context
        val rule = DrillHelpContent.rule(arguments?.getString("ruleId"))
        if (rule == null) {
            // Only the index ever navigates here, with a registry id; anything
            // else (a stale process-death restore) simply returns.
            findNavController().popBackStack()
            return
        }

        b.btnBack.setOnClickListener {
            val navController = findNavController()
            if (navController.currentDestination?.id == R.id.grammarRuleFragment) {
                navController.popBackStack()
            }
        }

        b.tvRuleTitle.text = rule.title
        val card = b.helpCard
        HelpCardViewBinder.bind(
            context,
            HelpCardViewBinder.CardViews(
                chips = card.helpChips,
                rule = card.tvHelpRule,
                firstExposure = card.tvHelpFirstExposure,
                table = card.helpTable,
                example = card.tvHelpExample,
                miss = card.tvHelpMiss,
            ),
            DrillHelpCard.ruleIndexState(rule),
            answer = null,
        )

        bindAskAiRow(b, context, rule)
    }

    /**
     * Binds the "Ask AI about this rule" row under the card: an ordinary chat
     * entry — the same gate as every entry ([AskAiEntryPoints.environment],
     * `AiPracticeGate.canRun` plus a valid provider config), hidden never
     * greyed, opening the rule subject's latest thread through
     * [AskAiEntryPoints.openChat]. Long-press opens the thread sheet, like
     * every entry.
     */
    private fun bindAskAiRow(b: FragmentGrammarRuleBinding, context: Context, rule: DrillHelpRule) {
        val ai = AskAiEntryPoints.environment(context)
        val subject = AiChatSubject(AiChatSubject.Kind.RULE, rule.id)
        if (!ai.visible) {
            b.askAiRow.isVisible = false
            return
        }
        b.askAiRow.text = AskAiEntryPoints.ruleRowText(context, ai.turns(subject))
        b.askAiRow.isVisible = true
        b.askAiRow.setOnClickListener { view ->
            Haptics.tick(view)
            AskAiEntryPoints.openChat(this, R.id.grammarRuleFragment, subject)
        }
        b.askAiRow.setOnLongClickListener { view ->
            Haptics.tick(view)
            AskAiThreadSheet.show(this, subject, rule.title, emptyList()) { threadId ->
                AskAiEntryPoints.open(this, R.id.grammarRuleFragment, subject, threadId)
            }
            true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
