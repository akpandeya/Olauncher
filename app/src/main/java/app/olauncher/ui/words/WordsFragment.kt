package app.olauncher.ui.words

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.navigation.fragment.findNavController
import androidx.work.WorkManager
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.data.store.ReviewComebacks
import app.olauncher.data.store.ReviewEventsStore
import app.olauncher.data.store.StudyGrid
import app.olauncher.data.store.Word
import app.olauncher.data.store.WordEdit
import app.olauncher.data.store.WordLookup
import app.olauncher.data.store.WordSearch
import app.olauncher.data.store.WordsListLogic
import app.olauncher.databinding.DialogEditWordBinding
import app.olauncher.databinding.FragmentWordsBinding
import app.olauncher.databinding.ItemWordBinding
import app.olauncher.helper.DrawerScrollHelper
import app.olauncher.helper.DictionaryApps
import app.olauncher.helper.Haptics
import app.olauncher.helper.createDialog
import app.olauncher.helper.dpToPx
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.hapticLongClick
import app.olauncher.helper.showMessageDialog
import app.olauncher.helper.showPopupMenu
import app.olauncher.helper.showToast
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
            item.tvStatus.text = formatStatus(context, status)
            if (status == WordsListLogic.Status.Due) item.tvStatus.setTextColor(accent)
            item.root.setOnClickListener { showEditDialog(context, word) }
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

    private fun formatStatus(context: Context, status: WordsListLogic.Status): String = when (status) {
        WordsListLogic.Status.Due -> context.getString(R.string.words_status_due)
        is WordsListLogic.Status.DueIn -> context.getString(R.string.words_status_due_in, status.days)
        WordsListLogic.Status.Learned -> context.getString(R.string.words_status_learned)
    }

    private fun showWordPopup(anchor: View, word: Word) {
        val context = anchor.context
        anchor.showPopupMenu(
            configure = { menu ->
                menu.add(Menu.NONE, 1, Menu.NONE, R.string.words_edit)
                menu.add(Menu.NONE, 2, Menu.NONE, DictionaryApps.actionLabel(context, Prefs(context).germanDictionaryApp))
                menu.add(Menu.NONE, 3, Menu.NONE, R.string.words_delete)
            }
        ) { item ->
            when (item.itemId) {
                1 -> showEditDialog(context, word)
                2 -> DictionaryApps.open(context, word.text)
                3 -> context.showMessageDialog(
                    title = R.string.words_delete,
                    message = context.getString(R.string.words_delete_confirm, word.text),
                    action = R.string.words_delete,
                ) {
                    WordsCard.getStore(context).delete(word.id)
                    render()
                }
            }
        }
    }

    private fun showEditDialog(context: Context, listedWord: Word) {
        val store = WordsCard.getStore(context)
        // A lookup may have filled the meaning since the list was drawn, so edit the saved copy.
        val word = store.get(listedWord.id) ?: return render()
        val prefs = Prefs(context)
        val lookupAllowed = WordLookup.isLookupAllowed(prefs.germanEnabled, prefs.germanLookup)
        var dialogBinding: DialogEditWordBinding? = null

        val dialog = context.createDialog(
            title = R.string.words_edit,
            action = R.string.save,
            neutral = if (lookupAllowed) R.string.words_look_up_again else 0,
            onNeutral = {
                lookUpAgain(context, WordEdit.resetMeaning(word))
            },
            onAction = {
                dialogBinding?.let { db ->
                    val newText = db.etWord.text?.toString().orEmpty()
                    if (WordEdit.clashesWithOtherWord(newText, word.id, store.all())) {
                        context.showToast(context.getString(R.string.words_already_saved, newText.trim()))
                        return@let
                    }
                    val outcome = WordEdit.apply(
                        word,
                        newText = newText,
                        newMeaning = db.etMeaning.text?.toString().orEmpty(),
                        lookupAllowed = lookupAllowed
                    )
                    if (outcome.needsLookup && lookupAllowed) {
                        lookUpAgain(context, outcome.word)
                    } else {
                        store.update(outcome.word)
                        render()
                    }
                }
            }
        ) { container ->
            DialogEditWordBinding.inflate(LayoutInflater.from(container.context), container, false).also { db ->
                dialogBinding = db
                db.etWord.setText(word.text)
                db.etMeaning.setText(word.meaning)
                val sourceRes = when {
                    word.meaning.isBlank() -> 0
                    word.meaningSource.equals("wiktionary", ignoreCase = true) -> R.string.words_source_wiktionary
                    else -> R.string.words_source_user
                }
                db.tvSource.isVisible = sourceRes != 0
                if (sourceRes != 0) db.tvSource.setText(sourceRes)
                db.etMeaning.focusAtEnd(context)
            }.root
        }
        dialog.showRespectingStatusBar()
    }

    private fun lookUpAgain(context: Context, word: Word) {
        WordsCard.getStore(context).update(word)
        WordLookup.enqueue(context, word.id)
        context.showToast(R.string.words_looking_up)
        render()
        refreshWhenLookupFinishes(context, word.id)
    }

    private fun refreshWhenLookupFinishes(context: Context, wordId: Long) {
        val owner = _binding?.let { viewLifecycleOwner } ?: return
        val work = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkLiveData(WordLookup.uniqueWorkName(wordId))
        work.observe(owner) { infos ->
            if (infos.isNotEmpty() && infos.all { it.state.isFinished }) {
                work.removeObservers(owner)
                render()
            }
        }
    }

    private fun AppCompatEditText.focusAtEnd(context: Context) {
        setSelection(text?.length ?: 0)
        post {
            requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private companion object {
        const val KEYBOARD_GAP_DP = 12
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
