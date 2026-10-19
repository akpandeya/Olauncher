package app.olauncher.ui.today

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentTodayBinding
import app.olauncher.databinding.ItemTodayPanelBinding
import app.olauncher.glance.HeroFormatting
import app.olauncher.listener.OnSwipeTouchListener
import app.olauncher.ui.BaseFragment
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.Locale

class TodayFragment : BaseFragment() {

    private var _binding: FragmentTodayBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: Prefs
    private val activeCards = mutableListOf<Pair<TodayCard, View>>()

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
        populateCards()
    }

    override fun onResume() {
        super.onResume()
        populateCards()
        activeCards.forEach { (card, cardView) -> card.onResume(cardView) }
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

    private fun populateCards() {
        val context = context ?: return
        val enabledCards = TodayCards.all.filter {
            prefs.isTodayCardEnabled(it.key) && it.isAvailable(context)
        }

        activeCards.clear()
        binding.cardsContainer.removeAllViews()
        binding.heroWeatherHost.removeAllViews()
        populateHeroDate()

        val (heroCards, panelCards) = enabledCards.partition { it.inHero }

        for (card in heroCards) {
            val cardView = card.createView(layoutInflater, binding.heroWeatherHost, this)
            if (cardView.parent == null) binding.heroWeatherHost.addView(cardView)
            activeCards.add(card to cardView)
        }
        binding.heroWeatherHost.isVisible = heroCards.isNotEmpty()

        // A card with nothing to show gets no panel and no header.
        val withContent = panelCards.filter { it.hasContent(context) }
        val ordered = if (prefs.todayOrderSmart) {
            val now = ZonedDateTime.now()
            val byKey = withContent.associateBy { it.key }
            TodayOrder.order(withContent.map { Ranked(it.key, it.priority(context, now)) })
                .mapNotNull { byKey[it.key] }
        } else {
            withContent
        }
        // Stack from the bottom up: the first (highest priority) card is the lowest panel.
        for (card in ordered.asReversed()) {
            val panel = ItemTodayPanelBinding.inflate(layoutInflater, binding.cardsContainer, false)
            val title = getString(card.titleRes)
            panel.tvPanelTitle.text = if (card.emoji.isEmpty()) title else "${card.emoji}  $title"

            val cardView = card.createView(layoutInflater, panel.panelContent, this)
            if (cardView.parent == null) panel.panelContent.addView(cardView)
            binding.cardsContainer.addView(panel.root)
            activeCards.add(card to cardView)
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
        activeCards.clear()
        _binding = null
    }
}
