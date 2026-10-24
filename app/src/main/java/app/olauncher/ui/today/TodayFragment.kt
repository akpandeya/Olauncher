package app.olauncher.ui.today

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import app.olauncher.helper.StatusBarFade
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.data.store.IntentionHistory
import app.olauncher.data.store.IntentionHistoryStore
import app.olauncher.databinding.FragmentTodayBinding
import app.olauncher.databinding.ItemTodayPanelBinding
import app.olauncher.databinding.ItemWidgetSlotBinding
import app.olauncher.glance.HeroFormatting
import app.olauncher.helper.Intention
import app.olauncher.listener.OnSwipeTouchListener
import app.olauncher.ui.BaseFragment
import app.olauncher.ui.today.cards.HeldCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class TodayFragment : BaseFragment() {

    private var _binding: FragmentTodayBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private lateinit var todayWidgets: TodayWidgets
    private var panelsJob: Job? = null
    private var pastIntentionsOpen = false
    private var panelsShown = false

    private val widgetBindLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            todayWidgets.onBindResult(it)
        }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        todayWidgets = TodayWidgets(
            fragment = this,
            restoring = savedInstanceState != null,
            refresh = { if (_binding != null) populateCards() },
            bind = widgetBindLauncher,
            markAwaitingResult = { viewModel.isAwaitingActivityResult = true },
            clearAwaitingResult = { viewModel.isAwaitingActivityResult = false },
        )
        requireActivity().supportFragmentManager.setFragmentResultListener(
            TodayWidgets.CONFIGURE_RESULT, this,
        ) { _, result ->
            todayWidgets.onConfigureResult(
                ActivityResult(result.getInt(TodayWidgets.RESULT_CODE), null),
            )
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentTodayBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        binding.addWidget.setOnClickListener { todayWidgets.add() }
        StatusBarFade.attach(binding.statusBarFade)
        initSwipeTouchListener()
        // Lift the page above the keyboard so the Add note field stays visible.
        binding.todayScrollView.clipToPadding = false
        ViewCompat.setOnApplyWindowInsetsListener(binding.todayScrollView) { v, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottom = ImeInset.bottomPadding(insets.isVisible(WindowInsetsCompat.Type.ime()), ime.bottom)
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, bottom)
            insets
        }
        initIntention()
        binding.tvPastIntentions.setOnClickListener {
            pastIntentionsOpen = !pastIntentionsOpen
            refreshPastIntentions()
        }
        populateCards()
    }

    override fun onStart() {
        super.onStart()
        todayWidgets.start()
    }

    override fun onStop() {
        todayWidgets.stop()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refreshIntention()
        refreshPastIntentions()
        // Rebuilds every card; each card renders itself in createView.
        populateCards()
    }

    private fun initSwipeTouchListener() {
        val swipeListener = object : OnSwipeTouchListener(requireContext()) {
            override fun onSwipeLeft() {
                super.onSwipeLeft()
                findNavController().popBackStack()
            }
        }
        binding.root.setOnTouchListener(swipeListener)
        binding.todayScrollView.setOnTouchListener { v, event ->
            swipeListener.onTouch(v, event)
            false
        }
    }

    private fun initIntention() {
        refreshIntention()
        updateIntentionFieldLook()
        binding.etIntention.doAfterTextChanged { updateIntentionFieldLook() }
        binding.etIntention.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveIntention(v.text?.toString())
                v.clearFocus()
                (v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                    ?.hideSoftInputFromWindow(v.windowToken, 0)
                true
            } else false
        }
    }

    /** Empty field gets a faint underline and a pencil hint; a filled one stays clean. */
    private fun updateIntentionFieldLook() {
        val field = _binding?.etIntention ?: return
        val empty = Intention.showsFieldHint(field.text)
        field.setBackgroundResource(if (empty) R.drawable.bg_intention_field else 0)
        field.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, if (empty) R.drawable.ic_pencil else 0, 0)
    }

    /** Shows today's intention; after the 04:00 rollover yesterday's text is cleared from the field. */
    private fun refreshIntention() {
        val field = _binding?.etIntention ?: return
        if (field.hasFocus()) return
        val current = Intention.current(prefs.intentionText, prefs.intentionDay, LocalDateTime.now()).orEmpty()
        if (field.text?.toString() != current) field.setText(current)
    }

    private fun saveIntention(raw: String?) {
        val text = Intention.clean(raw)
        prefs.intentionText = text
        val day = Intention.dayOf(LocalDateTime.now())
        prefs.intentionDay = day
        IntentionHistoryStore(requireContext().filesDir).put(day, text)
        _binding?.etIntention?.setText(text)
        refreshPastIntentions()
    }

    /** Collapsible list of earlier days' intentions; hidden when there are none. */
    private fun refreshPastIntentions() {
        val b = _binding ?: return
        val today = Intention.dayOf(LocalDateTime.now())
        val store = IntentionHistoryStore(requireContext().filesDir)
        val past = IntentionHistory.pastEntries(store.load(prefs.intentionText, prefs.intentionDay), today)
        b.tvPastIntentions.isVisible = past.isNotEmpty()
        val showList = past.isNotEmpty() && pastIntentionsOpen
        b.tvPastIntentionsList.isVisible = showList
        b.tvPastIntentions.setText(if (showList) R.string.intention_past_header_open else R.string.intention_past_header)
        if (!showList) return
        val format = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
        b.tvPastIntentionsList.text = past.joinToString("\n") {
            "${LocalDate.ofEpochDay(it.day).format(format)} · ${it.text}"
        }
    }

    private fun populateCards() {
        val context = context ?: return
        val builtIn = TodayCards.all.filter {
            prefs.isTodayCardEnabled(it.key) && it.isAvailable(context)
        }
        // Widgets sit between Next up and Held, as in the design.
        val heldAt = builtIn.indexOfFirst { it.key == HeldCard.KEY }.takeIf { it >= 0 } ?: builtIn.size
        val enabledCards = builtIn.take(heldAt) + todayWidgets.cards() + builtIn.drop(heldAt)
        binding.addWidget.isVisible = todayWidgets.cards().isNotEmpty()

        binding.heroWeatherHost.removeAllViews()
        populateHeroDate()

        val (heroCards, panelCards) = enabledCards.partition { it.inHero }

        for (card in heroCards) {
            val cardView = card.createView(layoutInflater, binding.heroWeatherHost, this)
            if (cardView.parent == null) binding.heroWeatherHost.addView(cardView)
        }
        binding.heroWeatherHost.isVisible = heroCards.isNotEmpty()

        // hasContent and priority may read the calendar provider (blocking), so plan off the main thread.
        val appContext = context.applicationContext
        val smart = prefs.todayOrderSmart
        val savedOrder = prefs.todayPanelOrder
        val pinnedKeys = prefs.todayPinnedKeys
        panelsJob?.cancel()
        panelsJob = viewLifecycleOwner.lifecycleScope.launch {
            val plan = withContext(Dispatchers.IO) {
                planPanels(appContext, panelCards, smart, savedOrder, pinnedKeys)
            }
            showPanels(plan)
        }
    }

    /** Panels with something to show: pinned ones for the top, the rest highest priority first when the order is smart. */
    private fun planPanels(
        context: Context,
        panelCards: List<TodayCard>,
        smart: Boolean,
        savedOrder: List<String>,
        manuallyPinnedKeys: Set<String>,
    ): PanelPlan {
        // A card with nothing to show gets no panel and no header.
        val withContent = panelCards.filter { it.hasContent(context) }
        val now = ZonedDateTime.now()
        val byKey = withContent.associateBy { it.key }
        val automaticallyPinnedKeys = if (smart) {
            withContent.filter { it.pinnedToTop(context, now) }.map { it.key }.toSet()
        } else {
            emptySet()
        }
        val pinnedKeys = manuallyPinnedKeys + automaticallyPinnedKeys
        val split = TodayOrder.arrange(
            cards = withContent.map { Ranked(it.key, it.priority(context, now)) },
            savedOrder = savedOrder,
            pinnedKeys = pinnedKeys,
            smart = smart,
        )
        return PanelPlan(
            pinned = split.pinned.mapNotNull { byKey[it] },
            stacked = split.stacked.mapNotNull { byKey[it] },
        )
    }

    private data class PanelPlan(val pinned: List<TodayCard>, val stacked: List<TodayCard>)

    private fun showPanels(plan: PanelPlan) {
        val binding = _binding ?: return
        // Keep the reader's place across refreshes (pin/move/resize all rebuild); only the
        // first fill decides where the page opens.
        val scrollY = binding.todayScrollView.scrollY
        // Swap the panels in one go, so a refresh does not flash an empty page.
        binding.pinnedCardsContainer.removeAllViews()
        populatePanels(plan.pinned, binding.pinnedCardsContainer, plan.pinned.map { it.key })
        binding.pinnedCardsContainer.isVisible = plan.pinned.isNotEmpty()

        binding.cardsContainer.removeAllViews()
        populatePanels(withWidgetSlot(plan.stacked), binding.cardsContainer, plan.stacked.map { it.key })

        // Keep the reader's place on refreshes; the first fill opens the page at the top.
        if (panelsShown) {
            binding.todayScrollView.post {
                _binding?.todayScrollView?.scrollY = scrollY
            }
            return
        }
        panelsShown = true
    }

    /** Until a widget is added, a dashed "Your widget" card sits at the bottom of the page. */
    private fun withWidgetSlot(stacked: List<TodayCard>): List<TodayCard> {
        if (todayWidgets.cards().isNotEmpty()) return stacked
        return stacked + WidgetSlotRow
    }

    /** Marks where the widget placeholder goes; it is not a real card and has no panel. */
    private object WidgetSlotRow : TodayCard {
        override val key = "widget_slot"
        override val titleRes = R.string.today_widget
        override fun createView(inflater: LayoutInflater, parent: ViewGroup, fragment: Fragment): View =
            throw UnsupportedOperationException("drawn by TodayFragment")
    }

    /**
     * Builds the panel rows: built-in cards stay full-width rows in order; runs of widget
     * cards are packed into a grid, where they share rows by their column spans.
     */
    private fun populatePanels(cards: List<TodayCard>, container: ViewGroup, groupKeys: List<String>) {
        var index = 0
        while (index < cards.size) {
            val card = cards[index]
            when {
                card === WidgetSlotRow -> {
                    val slot = ItemWidgetSlotBinding.inflate(layoutInflater, container, false)
                    slot.tvSlotAdd.setOnClickListener { todayWidgets.add() }
                    container.addView(slot.root)
                    index++
                }
                card.gridSpan(requireContext()) != null -> {
                    var end = index
                    while (end < cards.size && cards[end].gridSpan(requireContext()) != null) end++
                    container.addView(
                        widgetGrid(cards.subList(index, end), groupKeys),
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                    index = end
                }
                cards.getOrNull(index + 1)?.let { next ->
                    TodayOrder.pairsAsHalfWidth(
                        card.halfWidth, card.gridSpan(requireContext()),
                        next.halfWidth, next.gridSpan(requireContext()),
                    )
                } == true -> {
                    container.addView(halfWidthRow(card, cards[index + 1], groupKeys))
                    index += 2
                }
                else -> {
                    container.addView(inflatePanel(container, card, groupKeys))
                    index++
                }
            }
        }
    }

    /** Two half-width cards side by side, equal height. */
    private fun halfWidthRow(left: TodayCard, right: TodayCard, groupKeys: List<String>): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
        }
        val gap = (5 * resources.displayMetrics.density).toInt()
        for ((card, isLeft) in listOf(left to true, right to false)) {
            val panel = inflatePanel(row, card, groupKeys)
            val source = panel.layoutParams as ViewGroup.MarginLayoutParams
            panel.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                bottomMargin = source.bottomMargin
                if (isLeft) marginEnd = gap else marginStart = gap
            }
            row.addView(panel)
        }
        return row
    }

    private fun widgetGrid(cards: List<TodayCard>, groupKeys: List<String>): WidgetGridView {
        val grid = WidgetGridView(requireContext())
        for (card in cards) {
            val panel = inflatePanel(grid, card, groupKeys)
            val span = (card.gridSpan(requireContext()) ?: TodayWidgetResize.COLUMNS).coerceIn(1, TodayWidgetResize.COLUMNS)
            panel.layoutParams = WidgetGridView.LayoutParams(
                panel.layoutParams as ViewGroup.MarginLayoutParams,
                span,
            )
            grid.addView(panel)
        }
        return grid
    }

    private fun inflatePanel(parent: ViewGroup, card: TodayCard, groupKeys: List<String>): View {
        val panel = ItemTodayPanelBinding.inflate(layoutInflater, parent, false)
        val title = card.title(requireContext())
        panel.tvPanelTitle.text = title
        // Cards with their own heading (counts or controls in it) hide the panel's.
        panel.tvPanelTitle.isVisible = !card.drawsOwnHeader
        if (card.tinted) panel.root.setBackgroundResource(R.drawable.today_panel_held)
        // Grid panels get narrow: titles take one line and make room by ellipsizing.
        panel.tvPanelTitle.maxLines = 1
        panel.tvPanelTitle.ellipsize = android.text.TextUtils.TruncateAt.END
        androidx.core.view.ViewCompat.setAccessibilityHeading(panel.tvPanelTitle, true)
        val widgetId = TodayOrder.widgetId(card.key)
        if (widgetId != null) {
            panel.tvPanelTitle.isClickable = true
            panel.tvPanelTitle.contentDescription = getString(R.string.today_card_options, title)
            panel.tvPanelTitle.setCompoundDrawablesRelativeWithIntrinsicBounds(
                0,
                0,
                android.R.drawable.ic_menu_more,
                0,
            )
            panel.tvPanelTitle.compoundDrawablePadding = (8 * resources.displayMetrics.density).toInt()
            panel.tvPanelTitle.setOnClickListener {
                todayWidgets.showCardOptions(it, card.key, groupKeys)
            }
            panel.tvPanelTitle.setOnLongClickListener {
                todayWidgets.remove(widgetId)
                true
            }
        } else if (card.tileSpan != null && card.gridSpan(requireContext()) != null) {
            // Tile panels get the same Pin / Move menu as widgets, so they can be put next to one.
            panel.tvPanelTitle.isClickable = true
            panel.tvPanelTitle.contentDescription = getString(R.string.today_card_options, title)
            panel.tvPanelTitle.setCompoundDrawablesRelativeWithIntrinsicBounds(
                0,
                0,
                android.R.drawable.ic_menu_more,
                0,
            )
            panel.tvPanelTitle.compoundDrawablePadding = (8 * resources.displayMetrics.density).toInt()
            panel.tvPanelTitle.setOnClickListener {
                todayWidgets.showCardOptions(it, card.key, groupKeys)
            }
        }

        val cardView = card.createView(layoutInflater, panel.panelContent, this)
        if (cardView.parent == null) panel.panelContent.addView(cardView)
        return panel.root
    }

    private fun populateHeroDate() {
        val isGerman = prefs.germanEnabled && prefs.germanClock != Prefs.GERMAN_CLOCK_SYSTEM
        val locale = if (isGerman) Locale.GERMAN else Locale.getDefault()
        val today = LocalDate.now()
        binding.tvHeroDay.text = HeroFormatting.dayName(today, locale)
        binding.tvHeroDate.text = HeroFormatting.dateLine(today, locale)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        panelsJob?.cancel()
        _binding = null
    }
}
