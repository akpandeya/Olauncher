package app.olauncher.ui.words

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import app.olauncher.data.store.GrammarFurtherReading
import app.olauncher.databinding.FragmentGrammarRuleBinding
import app.olauncher.databinding.ItemFurtherReadingLinkBinding
import app.olauncher.helper.Haptics
import app.olauncher.helper.showToast
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
 * subject, exactly like every other entry. Below it, the "Further reading"
 * section lists the hand-checked links of the rule's topic group (#810,
 * [GrammarFurtherReading]), hidden when the group has none.
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
        bindFurtherReading(b, context, rule)
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

    /**
     * Binds the further-reading section under the Ask-AI row (#810): one row
     * per hand-checked link of the rule's topic group ([GrammarFurtherReading],
     * keyed on the group key). Hidden entirely when the group has no links —
     * never an empty section. A row tap opens the link's https URL with
     * [Intent.ACTION_VIEW] and fails quietly when no browser is installed.
     */
    private fun bindFurtherReading(b: FragmentGrammarRuleBinding, context: Context, rule: DrillHelpRule) {
        val links = GrammarFurtherReading.forRule(rule.id)
        if (links.isEmpty()) {
            b.furtherReadingPanel.isVisible = false
            return
        }
        b.furtherReadingLinks.removeAllViews()
        for (link in links) {
            val row = ItemFurtherReadingLinkBinding.inflate(layoutInflater, b.furtherReadingLinks, false)
            row.tvLinkSource.text = link.source
            row.tvLinkAngle.text = link.angle
            row.root.setOnClickListener { view ->
                Haptics.tick(view)
                openFurtherReading(context, link.url)
            }
            b.furtherReadingLinks.addView(row.root)
        }
        b.furtherReadingPanel.isVisible = true
    }

    /**
     * Opens a further-reading link in the browser (#810). Fails quietly: with
     * no browser app a short message says so instead of a crash.
     */
    private fun openFurtherReading(context: Context, url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            context.showToast(R.string.grammar_further_reading_no_browser)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
