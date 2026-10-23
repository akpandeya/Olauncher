package app.olauncher.ui.words

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.fragment.findNavController
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.data.store.ReviewComebacks
import app.olauncher.data.store.ReviewEventsStore
import app.olauncher.data.store.StudyGrid
import app.olauncher.data.store.Word
import app.olauncher.data.store.WordSearch
import app.olauncher.data.store.WordsListLogic
import app.olauncher.databinding.FragmentWordsBinding
import app.olauncher.databinding.ItemWordBinding
import app.olauncher.helper.DrawerScrollHelper
import app.olauncher.helper.DictionaryApps
import app.olauncher.helper.Haptics
import app.olauncher.helper.dpToPx
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.hapticLongClick
import app.olauncher.helper.showPopupMenu
import app.olauncher.ui.BaseFragment
import app.olauncher.ui.today.cards.WordsCard
import app.olauncher.ui.today.cards.WordsCardLogic
import java.time.LocalDate
import java.time.ZoneId

/** Every saved word, where meanings fetched from Wiktionary can be checked and corrected. */
class WordsFragment : BaseFragment() {

    private var _binding: FragmentWordsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentWordsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.etSearch.doAfterTextChanged {
            binding.btnClearSearch.isVisible = !it.isNullOrEmpty()
            render()
        }
        binding.etSearch.setOnEditorActionListener { v, _, _ ->
            (v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(v.windowToken, 0)
            true
        }
        binding.btnClearSearch.setOnClickListener { binding.etSearch.text?.clear() }

        // The activity is edge-to-edge, so the window is not resized for the keyboard.
        // Pad the page by the IME inset so the search field stays just above the keyboard.
        val keyboardGap = binding.wordsFooter.paddingBottom - KEYBOARD_GAP_DP.dpToPx()
        binding.root.setOnApplyWindowInsetsListener { root, insets ->
            val imeBottom = insets.getInsets(WindowInsets.Type.ime()).bottom
            val padding = DrawerScrollHelper.imeBottomPadding(
                drawerBottomSearch = true,
                imeBottom = imeBottom,
                searchBottomMargin = keyboardGap,
            )
            if (root.paddingBottom != padding) {
                root.setPadding(root.paddingLeft, root.paddingTop, root.paddingRight, padding)
                scrollToDueWords()
            }
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val b = _binding ?: return
        val context = context ?: return
        val store = WordsCard.getStore(context)
        val today = WordsCard.epochDaySupplier()
        val stats = store.stats(today)
        val words = store.all()

        val query = b.etSearch.text?.toString().orEmpty()
        val shown = WordSearch.filter(words, query)
        val searching = query.isNotBlank()

        b.tvStats.text = WordsCardLogic.formatFooter(context, stats.total, stats.due, stats.learned)
        b.tvStats.isVisible = words.isNotEmpty()
        b.searchBar.isVisible = words.isNotEmpty()
        b.btnReview.isVisible = WordsCardLogic.showReview(stats.due)
        b.btnReview.text = WordsCardLogic.reviewLabel(context.getString(R.string.words_review), stats.due)
        b.btnReview.setOnClickListener {
            Haptics.tick(it)
            // Ignore a second tap delivered before the first navigation lands.
            if (findNavController().currentDestination?.id == R.id.wordsFragment) {
                findNavController().navigate(R.id.action_wordsFragment_to_reviewFragment)
            }
        }
        b.btnLists.isVisible = words.isNotEmpty()
        b.btnLists.setOnClickListener {
            Haptics.tick(it)
            // Ignore a second tap delivered before the first navigation lands.
            if (findNavController().currentDestination?.id == R.id.wordsFragment) {
                findNavController().navigate(R.id.action_wordsFragment_to_wordListsFragment)
            }
        }
        renderStats(b, context, today)
        b.tvMatchCount.isVisible = searching
        b.tvMatchCount.text = context.getString(R.string.words_search_count, shown.size, words.size)
        b.tvEmpty.isVisible = shown.isEmpty()
        b.tvEmpty.text = if (words.isEmpty()) {
            context.getString(R.string.words_empty_state)
        } else {
            context.getString(R.string.words_search_no_match, query.trim())
        }
        b.wordsContainer.isVisible = shown.isNotEmpty()

        // Due words sit nearest the bottom, closest to the thumb.
        val accent = context.getColorFromAttr(R.attr.accentColor)
        b.wordsContainer.removeAllViews()
        for (word in WordsListLogic.sort(shown).asReversed()) {
            val item = ItemWordBinding.inflate(layoutInflater, b.wordsContainer, false)
            val status = WordsListLogic.status(word, today)
            item.tvWord.text = word.text
            item.tvMeaning.text = WordsCardLogic.formatMeaning(context, word.meaning, word.meaningSource)
            item.tvStatus.text = WordDialogs.formatStatus(context, status)
            if (status == WordsListLogic.Status.Due) item.tvStatus.setTextColor(accent)
            item.root.setOnClickListener {
                WordDialogs.showEditDialog(context, word, ::liveViewLifecycleOwner) { render() }
            }
            item.root.hapticLongClick {
                showWordPopup(item.tvWord, word)
            }
            b.wordsContainer.addView(item.root)
        }

        scrollToDueWords()
    }

    /** The 12-week dot grid and the comeback counter, from the review event log. */
    private fun renderStats(b: FragmentWordsBinding, context: Context, today: Long) {
        val events = ReviewEventsStore(context).all()
        if (events.isEmpty()) {
            b.statsPanel.isVisible = false
            return
        }
        b.statsPanel.isVisible = true
        val zone = ZoneId.systemDefault()
        val todayDate = LocalDate.ofEpochDay(today)
        // The count and window text derive from the grid cells so they can
        // never disagree with the dots on screen.
        val cells = StudyGrid.build(events, todayDate, zone)
        val studiedCount = cells.count { it.studied }
        b.studyDotGrid.submit(
            cells,
            studiedCount = studiedCount,
            windowDays = cells.size,
        )
        b.tvComebacks.text = context.getString(
            R.string.words_stats_comebacks,
            ReviewComebacks.count(events, zone),
            studiedCount,
        )
    }

    /** Due words are at the bottom. scrollTo, not fullScroll, so the search field keeps focus. */
    private fun scrollToDueWords() {
        _binding?.wordsScrollView?.post {
            val scroll = _binding?.wordsScrollView ?: return@post
            scroll.scrollTo(0, scroll.getChildAt(0)?.bottom ?: 0)
        }
    }

    private fun showWordPopup(anchor: View, word: Word) {
        val context = anchor.context
        anchor.showPopupMenu(
            configure = { menu ->
                menu.add(Menu.NONE, 1, Menu.NONE, R.string.words_edit)
                menu.add(Menu.NONE, 2, Menu.NONE, DictionaryApps.actionLabel(context, Prefs(context).germanDictionaryApp))
                menu.add(Menu.NONE, 4, Menu.NONE, R.string.word_lists_popup_lists)
                menu.add(Menu.NONE, 3, Menu.NONE, R.string.words_delete)
            }
        ) { item ->
            when (item.itemId) {
                1 -> WordDialogs.showEditDialog(context, word, ::liveViewLifecycleOwner) { render() }
                2 -> DictionaryApps.open(context, word.text)
                4 -> WordDialogs.showListsDialog(context, word) { render() }
                3 -> WordDialogs.showDeleteDialog(context, word) { render() }
            }
        }
    }

    /** Null once the view is gone, so the lookup-finished observer stops with the screen. */
    private fun liveViewLifecycleOwner(): LifecycleOwner? = _binding?.let { viewLifecycleOwner }

    private companion object {
        const val KEYBOARD_GAP_DP = 12
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
