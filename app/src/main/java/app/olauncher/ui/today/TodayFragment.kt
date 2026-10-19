package app.olauncher.ui.today

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentTodayBinding
import app.olauncher.databinding.ItemTodayPanelBinding
import app.olauncher.glance.HeroFormatting
import app.olauncher.helper.Intention
import app.olauncher.listener.OnSwipeTouchListener
import app.olauncher.ui.BaseFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.Locale

class TodayFragment : BaseFragment() {

    private var _binding: FragmentTodayBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: Prefs
    private var panelsJob: Job? = null

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
        initSwipeTouchListener()
        initIntention()
        populateCards()
    }

    override fun onResume() {
        super.onResume()
        refreshIntention()
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
        prefs.intentionDay = Intention.dayOf(LocalDateTime.now())
        _binding?.etIntention?.setText(text)
    }

    private fun populateCards() {
        val context = context ?: return
        val enabledCards = TodayCards.all.filter {
            prefs.isTodayCardEnabled(it.key) && it.isAvailable(context)
        }

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
        panelsJob?.cancel()
        panelsJob = viewLifecycleOwner.lifecycleScope.launch {
            val ordered = withContext(Dispatchers.IO) { planPanels(appContext, panelCards, smart) }
            showPanels(ordered)
        }
    }

    /** Panels with something to show, highest priority first when the order is smart. */
    private fun planPanels(context: Context, panelCards: List<TodayCard>, smart: Boolean): List<TodayCard> {
        // A card with nothing to show gets no panel and no header.
        val withContent = panelCards.filter { it.hasContent(context) }
        if (!smart) return withContent
        val now = ZonedDateTime.now()
        val byKey = withContent.associateBy { it.key }
        return TodayOrder.order(withContent.map { Ranked(it.key, it.priority(context, now)) })
            .mapNotNull { byKey[it.key] }
    }

    private fun showPanels(ordered: List<TodayCard>) {
        val binding = _binding ?: return
        // Swap the panels in one go, so a refresh does not flash an empty page.
        binding.cardsContainer.removeAllViews()
        // Stack from the bottom up: the first (highest priority) card is the lowest panel.
        for (card in ordered.asReversed()) {
            val panel = ItemTodayPanelBinding.inflate(layoutInflater, binding.cardsContainer, false)
            val title = getString(card.titleRes)
            panel.tvPanelTitle.text = if (card.emoji.isEmpty()) title else "${card.emoji}  $title"
            androidx.core.view.ViewCompat.setAccessibilityHeading(panel.tvPanelTitle, true)

            val cardView = card.createView(layoutInflater, panel.panelContent, this)
            if (cardView.parent == null) panel.panelContent.addView(cardView)
            binding.cardsContainer.addView(panel.root)
        }

        // When the cards overflow, open at the bottom, where the top-priority card is.
        binding.todayScrollView.post {
            _binding?.todayScrollView?.fullScroll(View.FOCUS_DOWN)
        }
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
