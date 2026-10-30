package app.olauncher.ui.words

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.data.store.AiChatEngine
import app.olauncher.data.store.AiChatGrounding
import app.olauncher.data.store.AiChatGroundingPayload
import app.olauncher.data.store.AiChatMessage
import app.olauncher.data.store.AiChatOpen
import app.olauncher.data.store.AiChatState
import app.olauncher.data.store.AiChatStore
import app.olauncher.data.store.AiChatSubject
import app.olauncher.data.store.AiChatCodec
import app.olauncher.data.store.AiProvider
import app.olauncher.data.store.AiProviderConfig
import app.olauncher.data.store.AiProviders
import app.olauncher.data.store.AiResult
import app.olauncher.data.store.DrillHelpContent
import app.olauncher.data.store.KeySource
import app.olauncher.data.store.KeyVault
import app.olauncher.databinding.FragmentAiChatBinding
import app.olauncher.databinding.ItemAiChatAiBinding
import app.olauncher.databinding.ItemAiChatErrorBinding
import app.olauncher.databinding.ItemAiChatMarkerBinding
import app.olauncher.databinding.ItemAiChatNoteBinding
import app.olauncher.databinding.ItemAiChatThinkingBinding
import app.olauncher.databinding.ItemAiChatUserBinding
import app.olauncher.helper.Haptics
import app.olauncher.helper.showMessageDialog
import app.olauncher.ui.BaseFragment
import app.olauncher.ui.askai.AiChatUiLogic
import app.olauncher.ui.askai.AiChatViewModel
import kotlinx.coroutines.launch

/**
 * The Ask-AI chat screen (#699, docs/design/ask-ai-chat.md §Chat screen): a
 * pinned header, the collapsible grounding card, the transcript with compaction
 * markers and failed-turn error lines, and a pinned composer. All behaviour —
 * send, retry, cancel, compaction — lives in [AiChatViewModel] /
 * [AiChatEngine]; this fragment only renders [AiChatUiLogic]'s decisions and
 * never touches the round or the word store: chat is post-answer only, back
 * returns the summary exactly as it was.
 */
class AiChatFragment : BaseFragment() {

    private var _binding: FragmentAiChatBinding? = null
    private val binding get() = _binding!!

    private var viewModel: AiChatViewModel? = null

    /** A fresh session's opening groundings, shown until the engine holds them. */
    private var openGroundings: List<AiChatGrounding> = emptyList()

    /** The grounding a resume carries for its next first send (a new miss). */
    private var resumeGrounding: AiChatGrounding? = null

    /** The alternative candidate while a fresh both-candidates thread is unfiled. */
    private var provisionalProposal: AiChatSubject? = null

    /** Null until computed; the default is fresh thread or resume with new miss → open. */
    private var groundingExpanded: Boolean? = null
    private var markerExpanded = false
    private var announcedTurns = false
    private var lastRenderedRowCount = -1

    /** The groundings currently rendered on the card, kept for header toggles. */
    private var renderedGroundings: List<AiChatGrounding> = emptyList()

    /** True when the session was opened as a resume (thread id argument). */
    private var openedResumed = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAiChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val args = requireArguments()
        val subject = AiChatSubject(
            kindFromArg(args.getString(ARG_SUBJECT_KIND)),
            args.getString(ARG_SUBJECT_REF).orEmpty(),
        )
        val threadId = args.getString(ARG_THREAD_ID)
        val argGroundings = AiChatCodec.decodeGroundingsJson(args.getString(ARG_GROUNDINGS_JSON))
        provisionalProposal = proposalFromArgs(args)

        if (viewModel == null) {
            val provider = chatProvider() ?: run {
                // The entry gate keeps invalid config out; this is only a guard.
                findNavController().popBackStack()
                return
            }
            val engine = AiChatEngine(AiChatStore(requireContext().applicationContext), provider)
            viewModel = ViewModelProvider(this, chatFactory(engine))[AiChatViewModel::class.java]
        }
        val vm = viewModel ?: return

        if (threadId != null) {
            // A resume keeps the store's transcript; a carried grounding joins the
            // NEXT send (design §Chat screen: expanded on "a resume carrying a new
            // miss") and shows on the card right away.
            resumeGrounding = argGroundings.lastOrNull()
            openedResumed = true
        } else {
            openGroundings = argGroundings
        }

        binding.btnClose.setOnClickListener { findNavController().popBackStack() }
        binding.btnSend.setOnClickListener { sendDraft() }
        binding.etQuestion.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = refreshSendEnabled()
        })
        binding.etQuestion.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendDraft()
                true
            } else {
                false
            }
        }
        binding.groundingHeader.setOnClickListener {
            Haptics.tick(binding.root)
            groundingExpanded =
                !(groundingExpanded ?: defaultGroundingExpanded(openedResumed && resumeGrounding == null))
            applyGrounding()
        }
        binding.tvUsage.setOnClickListener { showUsageDetail() }

        if (threadId != null) {
            vm.open(AiChatOpen.Resumed(threadId))
        } else {
            vm.open(AiChatOpen.Fresh(subject, openGroundings, provisionalProposal))
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vm.state.collect { state -> render(state, threadId != null) }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            vm.restoredDrafts.collect { draft ->
                binding.etQuestion.setText(draft)
                binding.etQuestion.setSelection(draft.length)
            }
        }
    }

    private fun sendDraft() {
        val vm = viewModel ?: return
        val question = binding.etQuestion.text?.toString()?.trim().orEmpty()
        if (question.isEmpty()) return
        if (vm.state.value.pendingQuestion != null) return
        Haptics.tick(binding.root)
        // A resumed thread's new miss rides its first send; a fresh thread's
        // groundings were already handed to the engine with open().
        vm.send(question, resumeGrounding)
        binding.etQuestion.setText("")
    }

    // ---- rendering -----------------------------------------------------------------

    private fun render(state: AiChatState, resumed: Boolean) {
        val vm = viewModel ?: return
        // The carried grounding leaves the screen's hands once the engine took it in.
        val carried = resumeGrounding
        if (carried != null && state.thread?.groundings?.any { it.id == carried.id } == true) {
            resumeGrounding = null
        }
        val provisional = provisionalProposal != null &&
            (state.thread == null || state.thread.messages.none { it.role == AiChatMessage.Role.ASSISTANT })

        renderHeader(state, provisional)
        renderGrounding(state, resumed, carried)
        renderMessages(state)
        renderUsage(state)

        refreshSendEnabled(state.pendingQuestion != null)

        if (!announcedTurns) {
            announcedTurns = true
            binding.messagesScroll.announceForAccessibility(
                getString(R.string.chat_a11y_announce, AiChatUiLogic.deliveredTurns(state))
            )
        }
    }

    private fun renderHeader(state: AiChatState, provisional: Boolean) {
        binding.tvSubject.text = when (val header = AiChatUiLogic.header(state, provisional)) {
            is AiChatUiLogic.Header.NewConversation -> getString(R.string.chat_new_conversation)
            is AiChatUiLogic.Header.Word -> header.text
            is AiChatUiLogic.Header.Rule -> header.title
            is AiChatUiLogic.Header.Topic -> getString(header.labelRes)
        }
    }

    private fun renderGrounding(state: AiChatState, resumed: Boolean, carried: AiChatGrounding?) {
        val base = state.thread?.groundings ?: openGroundings
        val groundings = if (carried != null && base.none { it.id == carried.id }) base + carried else base
        binding.groundingCard.isVisible = groundings.isNotEmpty()
        if (groundings.isEmpty()) {
            renderedGroundings = emptyList()
            return
        }
        if (groundingExpanded == null) {
            groundingExpanded = defaultGroundingExpanded(resumed && carried == null)
        }
        renderedGroundings = groundings
        applyGrounding()
    }

    /** Applies the collapse state to the already-computed grounding card. */
    private fun applyGrounding() {
        if (renderedGroundings.isEmpty()) return
        renderGroundingBody(renderedGroundings)
        val expanded = groundingExpanded == true
        binding.groundingBody.isVisible = expanded
        binding.tvGroundingChevron.text =
            getString(if (expanded) R.string.chat_chevron_expanded else R.string.chat_chevron_collapsed)
    }

    /** Fresh thread or a resume carrying a new miss → open; otherwise collapsed. */
    private fun defaultGroundingExpanded(resumedWithoutNewMiss: Boolean): Boolean = !resumedWithoutNewMiss

    private fun renderGroundingBody(groundings: List<AiChatGrounding>) {
        val context = binding.root.context
        binding.groundingBody.removeAllViews()
        for (grounding in groundings) {
            when (val payload = grounding.payload) {
                is AiChatGroundingPayload.Word -> {
                    binding.groundingBody.addView(
                        chipView(
                            context,
                            if (payload.meaning.isBlank()) {
                                getString(R.string.chat_grounding_word_chip_no_meaning, payload.text)
                            } else {
                                getString(R.string.chat_grounding_word_chip, payload.text, payload.meaning)
                            }
                        )
                    )
                    if (payload.context.isNotBlank()) {
                        binding.groundingBody.addView(
                            bodyView(context, getString(R.string.chat_grounding_word_context, payload.context), muted = true)
                        )
                    }
                }
                is AiChatGroundingPayload.Miss -> {
                    payload.chips.forEach { chip -> binding.groundingBody.addView(chipView(context, chip)) }
                    payload.ruleId
                        ?.let { DrillHelpContent.rule(it)?.title ?: it }
                        ?.let { binding.groundingBody.addView(chipView(context, getString(R.string.chat_rule_chip, it))) }
                    binding.groundingBody.addView(
                        bodyView(context, getString(R.string.chat_grounding_miss_prompt, payload.prompt), muted = false)
                    )
                    binding.groundingBody.addView(
                        bodyView(
                            context,
                            getString(R.string.chat_grounding_miss_line, payload.picked, payload.answer),
                            muted = false,
                            danger = true,
                        )
                    )
                }
                is AiChatGroundingPayload.Practice -> {
                    binding.groundingBody.addView(chipView(context, getString(R.string.chat_grounding_practice_chip)))
                    binding.groundingBody.addView(bodyView(context, payload.prompt, muted = false))
                    binding.groundingBody.addView(
                        bodyView(context, getString(R.string.chat_grounding_practice_answer, payload.answer), muted = true)
                    )
                }
            }
        }
    }

    private fun renderMessages(state: AiChatState) {
        val rows = AiChatUiLogic.rows(state)
        binding.messagesList.removeAllViews()
        for (row in rows) {
            when (row) {
                is AiChatUiLogic.Row.UserTile -> {
                    val item = ItemAiChatUserBinding.inflate(layoutInflater, binding.messagesList, false)
                    item.tvUserTile.text = row.text
                    item.tvUserTile.alpha = if (row.failed) FAILED_TILE_ALPHA else 1f
                    binding.messagesList.addView(wrapEnd(item.root))
                }
                is AiChatUiLogic.Row.AssistantPanel -> {
                    val item = ItemAiChatAiBinding.inflate(layoutInflater, binding.messagesList, false)
                    item.tvAssistantPanel.text = row.text
                    binding.messagesList.addView(item.root)
                }
                is AiChatUiLogic.Row.ErrorLine -> {
                    val item = ItemAiChatErrorBinding.inflate(layoutInflater, binding.messagesList, false)
                    item.tvError.text = if (row.kind != null) {
                        getString(R.string.chat_not_sent_line, errorText(row.kind))
                    } else {
                        getString(R.string.chat_not_sent)
                    }
                    // AUTH recovers through the AI settings; every other kind and
                    // the ask-again path offer only the turn button.
                    item.btnRecovery.isVisible = row.kind == AiResult.ErrorKind.AUTH
                    if (row.kind == AiResult.ErrorKind.AUTH) {
                        item.btnRecovery.setOnClickListener {
                            findNavController().navigate(R.id.action_global_settingsFragment)
                        }
                    }
                    item.btnRetry.text = getString(if (row.retryable) R.string.chat_retry else R.string.chat_ask_again)
                    item.btnRetry.setOnClickListener {
                        val vm = viewModel ?: return@setOnClickListener
                        Haptics.tick(item.root)
                        // The newest failed turn retries in place; an older one
                        // asks again with a new turn quoting the question.
                        if (row.retryable) vm.retry() else vm.send(row.question, null)
                    }
                    binding.messagesList.addView(item.root)
                }
                is AiChatUiLogic.Row.CompactionMarker -> {
                    val item = ItemAiChatMarkerBinding.inflate(layoutInflater, binding.messagesList, false)
                    item.tvMarker.text = getString(
                        if (row.summarised) R.string.chat_marker_summarised else R.string.chat_marker_omitted
                    )
                    item.markerBody.isVisible = markerExpanded
                    item.tvSummaryText.isVisible = row.summaryText != null
                    item.tvSummaryText.text = row.summaryText.orEmpty()
                    item.originalsList.removeAllViews()
                    row.originals.forEach { turn ->
                        item.originalsList.addView(
                            bodyView(binding.root.context, getString(R.string.chat_original_asked, turn.question), muted = true)
                        )
                        item.originalsList.addView(
                            bodyView(binding.root.context, getString(R.string.chat_original_replied, turn.answer), muted = true)
                        )
                    }
                    item.tvMarker.setOnClickListener {
                        Haptics.tick(item.tvMarker)
                        markerExpanded = !markerExpanded
                        item.markerBody.isVisible = markerExpanded
                    }
                    binding.messagesList.addView(item.root)
                }
                is AiChatUiLogic.Row.Thinking -> {
                    val item = ItemAiChatThinkingBinding.inflate(layoutInflater, binding.messagesList, false)
                    item.btnCancel.setOnClickListener {
                        val vm = viewModel ?: return@setOnClickListener
                        Haptics.tick(item.root)
                        vm.cancel()
                    }
                    binding.messagesList.addView(item.root)
                }
                is AiChatUiLogic.Row.StoreError -> {
                    val item = ItemAiChatNoteBinding.inflate(layoutInflater, binding.messagesList, false)
                    item.tvNote.setText(R.string.chat_store_error)
                    binding.messagesList.addView(item.root)
                }
            }
        }
        if (rows.size != lastRenderedRowCount) {
            lastRenderedRowCount = rows.size
            binding.messagesScroll.post { binding.messagesScroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun renderUsage(state: AiChatState) {
        val tokens = AiChatUiLogic.usageTokens(state)
        binding.tvUsage.isVisible = tokens != null
        if (tokens == null) return
        val tokenText = if (tokens >= 1000) {
            getString(R.string.chat_usage_tokens_thousands, AiChatUiLogic.formatTokenCount(tokens))
        } else {
            getString(R.string.chat_usage_tokens, tokens)
        }
        binding.tvUsage.text = getString(R.string.chat_usage_line, tokenText, AiChatUiLogic.deliveredTurns(state))
    }

    private fun showUsageDetail() {
        val usage = viewModel?.state?.value?.lastUsage ?: return
        requireContext().showMessageDialog(
            R.string.chat_usage_detail_title,
            getString(R.string.chat_usage_detail, usage.inputTokens, usage.outputTokens),
            R.string.close,
        ) { }
    }

    private fun refreshSendEnabled(waiting: Boolean? = null) {
        val vm = viewModel ?: return
        val isWaiting = waiting ?: (vm.state.value.pendingQuestion != null)
        binding.btnSend.isEnabled = !isWaiting && !binding.etQuestion.text.isNullOrBlank()
        binding.btnSend.alpha = if (binding.btnSend.isEnabled) 1f else SEND_DISABLED_ALPHA
    }

    private fun errorText(kind: AiResult.ErrorKind): String = getString(
        when (kind) {
            AiResult.ErrorKind.RATE_LIMIT -> R.string.chat_error_rate_limit
            AiResult.ErrorKind.NETWORK -> R.string.chat_error_network
            AiResult.ErrorKind.SERVER -> R.string.chat_error_server
            AiResult.ErrorKind.BAD_RESPONSE -> R.string.chat_error_bad_response
            AiResult.ErrorKind.AUTH -> R.string.chat_error_auth
            AiResult.ErrorKind.BILLING -> R.string.chat_error_billing
        }
    )

    // ---- grounding card block builders ---------------------------------------------

    private fun chipView(context: Context, text: String): TextView {
        val chip = TextView(context)
        chip.setTextAppearance(R.style.TodayChip_Small)
        chip.text = text
        chip.background = supportDrawable(context, R.drawable.today_chip)
        chip.setPadding(dp(12), dp(6), dp(12), dp(6))
        chip.layoutParams = listParams(width = LinearLayout.LayoutParams.WRAP_CONTENT)
        return chip
    }

    private fun bodyView(context: Context, text: String, muted: Boolean, danger: Boolean = false): TextView {
        val body = TextView(context)
        body.setTextAppearance(if (muted) R.style.TodayCardBodyLight else R.style.TodayCardBody)
        body.text = text
        body.setLineSpacing(1f, 1.15f)
        if (danger) body.setTextColor(ContextCompat.getColor(context, dangerColorRes(context)))
        body.layoutParams = listParams(width = LinearLayout.LayoutParams.MATCH_PARENT)
        return body
    }

    private fun dangerColorRes(context: Context): Int {
        val value = TypedValue()
        context.theme.resolveAttribute(R.attr.dangerColor, value, true)
        return if (value.resourceId != 0) value.resourceId else value.data
    }

    private fun supportDrawable(context: Context, res: Int): Drawable? = ContextCompat.getDrawable(context, res)

    private fun listParams(width: Int): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(8)
        return lp
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun wrapEnd(view: View): View {
        val lp = view.layoutParams as? LinearLayout.LayoutParams
            ?: LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        lp.gravity = Gravity.END
        lp.topMargin = dp(8)
        view.layoutParams = lp
        return view
    }

    // ---- wiring ---------------------------------------------------------------------

    /** The provider chat rides the same config as every AI call; null when invalid. */
    private fun chatProvider(): AiProvider? {
        val context = requireContext().applicationContext
        val prefs = Prefs(context)
        val vault = KeyVault(context)
        val keySource = KeySource { vault.read() }
        return AiProviders.create(
            AiProviderConfig(prefs.aiProvider, prefs.aiBaseUrl, prefs.aiModel),
            keySource,
        )
    }

    private fun chatFactory(engine: AiChatEngine): ViewModelProvider.Factory {
        return object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AiChatViewModel(engine) as T
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_SUBJECT_KIND = "subjectKind"
        private const val ARG_SUBJECT_REF = "subjectRef"
        private const val ARG_THREAD_ID = "threadId"
        private const val ARG_PROPOSAL_KIND = "proposalKind"
        private const val ARG_PROPOSAL_REF = "proposalRef"
        private const val ARG_GROUNDINGS_JSON = "groundingsJson"

        private const val FAILED_TILE_ALPHA = 0.55f
        private const val SEND_DISABLED_ALPHA = 0.4f

        /**
         * The navigation arguments the entry points (summary chips, word-card
         * row) build: a fresh open by subject with its groundings, or a resume by
         * thread id; [proposal] is set only for a fresh both-candidates thread.
         */
        fun bundle(
            subject: AiChatSubject,
            threadId: String? = null,
            proposal: AiChatSubject? = null,
            groundings: List<AiChatGrounding> = emptyList(),
        ): Bundle {
            return Bundle().apply {
                putString(ARG_SUBJECT_KIND, subject.kind.name.lowercase())
                putString(ARG_SUBJECT_REF, subject.ref)
                putString(ARG_THREAD_ID, threadId)
                putString(ARG_PROPOSAL_KIND, proposal?.let { it.kind.name.lowercase() })
                putString(ARG_PROPOSAL_REF, proposal?.ref)
                putString(ARG_GROUNDINGS_JSON, AiChatCodec.encodeGroundingsJson(groundings))
            }
        }

        private fun kindFromArg(raw: String?): AiChatSubject.Kind {
            return AiChatSubject.Kind.entries.firstOrNull { it.name.lowercase() == raw }
                ?: AiChatSubject.Kind.TOPIC
        }

        private fun proposalFromArgs(args: Bundle): AiChatSubject? {
            val kind = args.getString(ARG_PROPOSAL_KIND) ?: return null
            val ref = args.getString(ARG_PROPOSAL_REF) ?: return null
            return AiChatSubject(kindFromArg(kind), ref)
        }
    }
}
