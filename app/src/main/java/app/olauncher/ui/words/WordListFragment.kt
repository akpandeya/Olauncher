package app.olauncher.ui.words

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.fragment.findNavController
import app.olauncher.R
import app.olauncher.data.store.Word
import app.olauncher.data.store.WordList
import app.olauncher.data.store.WordListsLogic
import app.olauncher.data.store.WordsListLogic
import app.olauncher.databinding.FragmentWordListBinding
import app.olauncher.databinding.ItemWordBinding
import app.olauncher.helper.Haptics
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.hapticLongClick
import app.olauncher.helper.showPopupMenu
import app.olauncher.ui.BaseFragment
import app.olauncher.ui.today.attachPressScale
import app.olauncher.ui.today.cards.WordsCard
import app.olauncher.ui.today.cards.WordsCardLogic

/**
 * The words of one user list, or of one built-in meaning-source bucket. Exactly one of
 * the nav arguments is set: `listId` resolves via the store, `sourceBucket` (the enum
 * name of [WordListsLogic.SourceBucket]) re-derives its words on every render.
 */
class WordListFragment : BaseFragment() {

    private var _binding: FragmentWordListBinding? = null
    private val binding get() = _binding!!

    private val listId: String? by lazy { arguments?.getString("listId") }
    private val sourceBucket: WordListsLogic.SourceBucket? by lazy {
        arguments?.getString("sourceBucket")?.let { name ->
            WordListsLogic.SourceBucket.entries.firstOrNull { it.name == name }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentWordListBinding.inflate(inflater, container, false)
        return binding.root
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

        // The UI sets exactly one nav argument; if both arrive, the list wins so the
        // content and the Add-words footer below can never disagree.
        val list = listId?.let { id -> store.lists().firstOrNull { it.id == id } }
        val bucket = sourceBucket.takeIf { listId == null }
        val words: List<Word>
        val title: String
        if (bucket != null) {
            words = WordListsLogic.groupBySource(store.all()).first { it.first == bucket }.second
            title = context.getString(
                when (bucket) {
                    WordListsLogic.SourceBucket.WIKTIONARY -> R.string.word_lists_source_wiktionary
                    WordListsLogic.SourceBucket.USER -> R.string.word_lists_source_manual
                    WordListsLogic.SourceBucket.NONE -> R.string.word_row_no_meaning_yet
                }
            )
        } else {
            words = list?.let { store.wordsInList(it.id) }.orEmpty()
            title = list?.name ?: context.getString(R.string.word_list_title_fallback)
        }

        b.tvTitle.text = title
        b.tvEmpty.isVisible = words.isEmpty()
        b.wordsContainer.isVisible = words.isNotEmpty()

        // "Add words" only makes sense for a list that does not yet hold every saved word.
        b.btnAddWords.isVisible = list != null && store.all().any { list.id !in it.listIds }
        b.btnAddWords.setOnClickListener {
            Haptics.tick(it)
            list?.let { openList -> WordDialogs.showAddWordsDialog(context, openList) { render() } }
        }

        // Due words sit nearest the bottom, closest to the thumb.
        val accent = context.getColorFromAttr(R.attr.accentColor)
        b.wordsContainer.removeAllViews()
        for (word in WordsListLogic.sort(words).asReversed()) {
            val item = ItemWordBinding.inflate(layoutInflater, b.wordsContainer, false)
            item.root.attachPressScale()
            val status = WordsListLogic.status(word, today)
            item.tvWord.text = word.text
            item.tvMeaning.text = WordsCardLogic.formatMeaning(context, word.meaning, word.meaningSource)
            item.tvStatus.text = WordDialogs.formatStatus(context, status)
            if (status == WordsListLogic.Status.Due) item.tvStatus.setTextColor(accent)
            item.root.setOnClickListener {
                WordDialogs.showEditDialog(context, word, ::liveViewLifecycleOwner) { render() }
            }
            item.root.hapticLongClick {
                showWordPopup(item.tvWord, word, list)
            }
            b.wordsContainer.addView(item.root)
        }

        b.wordsScrollView.post {
            val scroll = _binding?.wordsScrollView ?: return@post
            scroll.scrollTo(0, scroll.getChildAt(0)?.bottom ?: 0)
        }
    }

    private fun showWordPopup(anchor: View, word: Word, list: WordList?) {
        val context = anchor.context
        anchor.showPopupMenu(
            configure = { menu ->
                menu.add(Menu.NONE, 1, Menu.NONE, R.string.words_edit)
                menu.add(Menu.NONE, 5, Menu.NONE, R.string.words_conjugate)
                if (list != null) menu.add(Menu.NONE, 2, Menu.NONE, R.string.word_list_remove_from_list)
                menu.add(Menu.NONE, 3, Menu.NONE, R.string.words_delete)
            }
        ) { item ->
            when (item.itemId) {
                1 -> WordDialogs.showEditDialog(context, word, ::liveViewLifecycleOwner) { render() }
                5 -> openConjugation(word)
                2 -> list?.let { openList ->
                    val store = WordsCard.getStore(context)
                    // Re-read so the row's snapshot cannot resurrect memberships changed elsewhere.
                    store.get(word.id)?.let { current ->
                        store.setLists(current.id, current.listIds - openList.id)
                    }
                    render()
                }
                3 -> WordDialogs.showDeleteDialog(context, word) { render() }
            }
        }
    }

    /** The drill handles non-verbs itself with a graceful one-liner, so this stays unconditional. */
    private fun openConjugation(word: Word) {
        val context = context ?: return
        Haptics.tick(binding.root)
        // Re-read so a word edited since this row rendered drills the current text.
        val current = WordsCard.getStore(context).get(word.id) ?: word
        val navController = findNavController()
        // Ignore a second tap delivered before the first navigation lands.
        if (navController.currentDestination?.id == R.id.wordListFragment) {
            navController.navigate(
                R.id.action_wordListFragment_to_conjugationFragment,
                Bundle().apply { putString("word", current.text) },
            )
        }
    }

    /** Null once the view is gone, so the lookup-finished observer stops with the screen. */
    private fun liveViewLifecycleOwner(): LifecycleOwner? = _binding?.let { viewLifecycleOwner }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
