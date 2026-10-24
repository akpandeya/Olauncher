package app.olauncher.ui.words

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import app.olauncher.R
import app.olauncher.data.store.WordList
import app.olauncher.data.store.WordListsLogic
import app.olauncher.data.store.WordListsSummary
import app.olauncher.databinding.FragmentWordListsBinding
import app.olauncher.databinding.ItemWordListCardBinding
import app.olauncher.helper.Haptics
import app.olauncher.helper.hapticLongClick
import app.olauncher.helper.showMessageDialog
import app.olauncher.helper.showPopupMenu
import app.olauncher.ui.BaseFragment
import app.olauncher.ui.today.cards.WordsCard
import app.olauncher.ui.today.cards.WordsCardLogic

/** The user's word lists plus the built-in grouping by meaning source. */
class WordListsFragment : BaseFragment() {

    private var _binding: FragmentWordListsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentWordListsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val newList = { v: View ->
            Haptics.tick(v)
            WordDialogs.showListNameDialog(v.context, list = null) { render() }
        }
        binding.btnNewList.setOnClickListener(newList)
        binding.btnEmptyAdd.setOnClickListener(newList)
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
        val words = store.all()
        val lists = store.lists()

        val empty = words.isEmpty() && lists.isEmpty()
        b.tvEmpty.isVisible = empty
        b.btnEmptyAdd.isVisible = empty
        b.tvCounts.text = context.getString(
            R.string.word_lists_count_line,
            WordsCardLogic.formatWordsCount(context, words.size),
            if (lists.size == 1) {
                context.getString(R.string.word_lists_count_single)
            } else {
                context.getString(R.string.word_lists_count_plural, lists.size)
            },
        )

        // "All words": total and due today
        b.allWordsRow.removeAllViews()
        if (!empty) {
            val allDue = WordListsSummary.dueCount(words, today)
            val all = ItemWordListCardBinding.inflate(layoutInflater, b.allWordsRow, false)
            all.tvName.setText(R.string.word_lists_all_words)
            all.tvSubtitle.text = if (allDue > 0) {
                context.getString(
                    R.string.word_lists_all_words_subtitle,
                    WordsCardLogic.formatWordsCount(context, words.size),
                    allDue,
                )
            } else {
                context.getString(
                    R.string.word_lists_all_words_subtitle_done,
                    WordsCardLogic.formatWordsCount(context, words.size),
                )
            }
            all.tvStatus.isVisible = false
            all.root.setOnClickListener {
                Haptics.tick(it)
                openAllWords()
            }
            b.allWordsRow.addView(all.root)
        }

        b.listsPanel.isVisible = lists.isNotEmpty()
        b.listsPanel.removeAllViews()
        for (list in lists) {
            val members = WordListsSummary.membersOf(words, list.id)
            val item = ItemWordListCardBinding.inflate(layoutInflater, b.listsPanel, false)
            item.tvName.text = list.name
            item.tvSubtitle.text = WordsCardLogic.formatWordsCount(context, members.size)
            bindDueStatus(item, context, WordListsSummary.dueCount(members, today))
            item.root.setOnClickListener {
                Haptics.tick(it)
                openWordList(bundleOf("listId" to list.id))
            }
            item.root.hapticLongClick {
                showListPopup(item.tvName, list)
            }
            b.listsPanel.addView(item.root)
        }

        val buckets = WordListsLogic.groupBySource(words).filter { it.second.isNotEmpty() }
        b.sourcesPanel.isVisible = buckets.isNotEmpty()
        b.tvSourcesHeading.isVisible = buckets.isNotEmpty()
        b.sourcesPanel.removeAllViews()
        for ((bucket, bucketWords) in buckets) {
            val item = ItemWordListCardBinding.inflate(layoutInflater, b.sourcesPanel, false)
            item.tvName.text = context.getString(
                when (bucket) {
                    WordListsLogic.SourceBucket.WIKTIONARY -> R.string.word_lists_source_wiktionary
                    WordListsLogic.SourceBucket.USER -> R.string.word_lists_source_manual
                    WordListsLogic.SourceBucket.NONE -> R.string.word_row_no_meaning_yet
                }
            )
            item.tvSubtitle.text = WordsCardLogic.formatWordsCount(context, bucketWords.size)
            bindDueStatus(item, context, WordListsSummary.dueCount(bucketWords, today))
            item.root.setOnClickListener {
                Haptics.tick(it)
                openWordList(bundleOf("sourceBucket" to bucket.name))
            }
            b.sourcesPanel.addView(item.root)
        }

        b.listsScrollView.post {
            val scroll = _binding?.listsScrollView ?: return@post
            scroll.scrollTo(0, scroll.getChildAt(0)?.bottom ?: 0)
        }
    }

    private fun bindDueStatus(item: ItemWordListCardBinding, context: android.content.Context, due: Int) {
        item.tvStatus.text = if (due > 0) {
            context.getString(R.string.word_lists_card_due, due)
        } else {
            context.getString(R.string.word_lists_card_all_done)
        }
    }

    private fun openAllWords() {
        val navController = findNavController()
        if (navController.currentDestination?.id == R.id.wordListsFragment) {
            navController.navigate(R.id.action_wordListsFragment_to_wordsFragment)
        }
    }

    /** Opens the list page for a user list (`listId`) or a source bucket (`sourceBucket`). */
    private fun openWordList(args: Bundle) {
        val navController = findNavController()
        // Ignore a second tap delivered before the first navigation lands.
        if (navController.currentDestination?.id == R.id.wordListsFragment) {
            navController.navigate(R.id.action_wordListsFragment_to_wordListFragment, args)
        }
    }

    private fun showListPopup(anchor: View, list: WordList) {
        val context = anchor.context
        anchor.showPopupMenu(
            configure = { menu ->
                menu.add(Menu.NONE, 1, Menu.NONE, R.string.rename)
                menu.add(Menu.NONE, 2, Menu.NONE, R.string.words_delete)
            }
        ) { item ->
            when (item.itemId) {
                1 -> WordDialogs.showListNameDialog(context, list) { render() }
                2 -> context.showMessageDialog(
                    title = R.string.words_delete,
                    message = context.getString(R.string.word_lists_delete_confirm, list.name),
                    action = R.string.words_delete,
                ) {
                    WordsCard.getStore(context).deleteList(list.id)
                    render()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
