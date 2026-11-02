package app.olauncher.ui.words

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import app.olauncher.R
import app.olauncher.data.store.AiChatStore
import app.olauncher.databinding.FragmentAskAiConversationsBinding
import app.olauncher.databinding.ItemAskAiConversationBinding
import app.olauncher.helper.Haptics
import app.olauncher.helper.getColorFromAttr
import app.olauncher.ui.BaseFragment
import app.olauncher.ui.askai.AiChatUiLogic
import app.olauncher.ui.askai.AskAiEntries
import app.olauncher.ui.today.cards.WordsCard

/**
 * The all-conversations list (#793, docs/design/german-hidden-surfaces.md §4.2,
 * mock frames 4-5): every stored Ask-AI thread, newest first, across word, rule
 * and topic subjects — kind label, subject title, "Chat · n turns · date" meta
 * and a one-line preview of the last message. A row tap resumes that thread in
 * [AiChatFragment]; the "＋ New conversation" pill starts a fresh one for the
 * newest thread's subject (the list itself has no single subject to reuse — the
 * thread sheet's button reuses its sheet's). Reached from the Chats button in
 * the Words screen header, next to Drills — the on-demand entry the Ask-AI
 * design lacked. All ordering and labelling decisions live in [AskAiEntries]
 * (Context-free, JVM tested); this fragment only reads the store and navigates.
 */
class AskAiConversationsFragment : BaseFragment() {

    private var _binding: FragmentAskAiConversationsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAskAiConversationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        // Re-render on return: a chat opened from here may have added turns.
        render()
    }

    private fun render() {
        val b = _binding ?: return
        val context = context ?: return

        // The same gate as every chat entry ([AskAiEntries.visible] via
        // [AskAiEntryPoints.environment]): hidden, never greyed. The Words
        // header button is already gated, so this only guards a stale screen —
        // the key was removed or the switch turned off while it sat in back stack.
        if (!AskAiEntryPoints.environment(context).visible) {
            findNavController().popBackStack()
            return
        }

        val threads = AiChatStore(context).threads()
        val rows = AskAiEntries.conversationRows(threads)
        val savedWords = WordsCard.getStore(context).all()

        b.tvCount.text = rows.size.toString()
        b.conversationsList.isVisible = rows.isNotEmpty()
        b.tvEmpty.isVisible = rows.isEmpty()

        // A fresh thread must be filed under a subject (design §Threads), and
        // the list has none of its own — so the affordance resolves the newest
        // thread's subject, and hides while no thread exists. (The mock's empty
        // state keeps a muted pill; a pill that cannot pick a subject would be
        // dead, so it hides instead.)
        val newSubject = AskAiEntries.newConversationSubject(threads)
        b.btnNewConversation.isVisible = newSubject != null
        newSubject?.let { subject ->
            b.btnNewConversation.setOnClickListener { view ->
                Haptics.tick(view)
                AskAiEntryPoints.open(this, R.id.askAiConversationsFragment, subject, threadId = null)
            }
        }

        b.conversationsList.removeAllViews()
        for (row in rows) {
            val item = ItemAskAiConversationBinding.inflate(layoutInflater, b.conversationsList, false)
            item.tvKind.text = getString(AskAiEntries.conversationKindLabelRes(row.subject.kind))
            item.tvSubjectTitle.text = AskAiEntryPoints.subjectTitle(context, savedWords, row.subject)
            item.tvMeta.text = getString(
                R.string.ask_ai_conversations_row_meta,
                row.turns,
                AskAiThreadSheet.dateLabel(context, row.updatedAt),
            )
            renderPreview(item.tvPreview, row)
            item.root.setOnClickListener { view ->
                Haptics.tick(view)
                AskAiEntryPoints.open(this, R.id.askAiConversationsFragment, row.subject, row.threadId)
            }
            b.conversationsList.addView(item.root)
        }
    }

    /**
     * The one-line tail of the thread: the last message, or — when the newest
     * send failed — its persisted "Not sent · …" line in the danger colour,
     * exactly as the chat screen renders the tail (§Reliability error table).
     */
    private fun renderPreview(preview: TextView, row: AskAiEntries.ConversationRow) {
        if (row.previewFailed) {
            preview.setTextColor(requireContext().getColorFromAttr(R.attr.dangerColor))
            preview.text = if (row.previewErrorKind != null) {
                getString(R.string.chat_not_sent_line, getString(AiChatUiLogic.errorTextRes(row.previewErrorKind)))
            } else {
                getString(R.string.chat_not_sent)
            }
        } else {
            preview.isVisible = row.preview.isNotBlank()
            preview.text = row.preview
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
