package app.olauncher.ui.today

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentTodayBinding
import app.olauncher.helper.dpToPx
import app.olauncher.listener.OnSwipeTouchListener
import app.olauncher.ui.BaseFragment

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
        binding.emptyView.setOnTouchListener(swipeListener)
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

        if (enabledCards.isEmpty()) {
            binding.emptyView.isVisible = true
            binding.todayScrollView.isVisible = false
        } else {
            binding.emptyView.isVisible = false
            binding.todayScrollView.isVisible = true

            for (card in enabledCards) {

                val titleView = TextView(context).apply {
                    // Quiet, spaced-out headers so the content, not the labels, carries each section.
                    setTextAppearance(R.style.TextSmallLight)
                    setAllCaps(true)
                    letterSpacing = 0.1f
                    textSize = 12f
                    text = getString(card.titleRes)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = 28.dpToPx()
                        bottomMargin = 4.dpToPx()
                    }
                }
                binding.cardsContainer.addView(titleView)

                val cardView = card.createView(layoutInflater, binding.cardsContainer, this)
                if (cardView.parent == null) {
                    binding.cardsContainer.addView(cardView)
                }
                activeCards.add(card to cardView)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        activeCards.clear()
        _binding = null
    }
}
