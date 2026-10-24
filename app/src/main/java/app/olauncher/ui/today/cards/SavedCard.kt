package app.olauncher.ui.today.cards

import android.app.Activity
import app.olauncher.helper.createChoiceDialog
import app.olauncher.helper.UndoMessage
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import app.olauncher.R
import app.olauncher.helper.ArchiveChange
import app.olauncher.helper.Haptics
import app.olauncher.data.store.SavedItem
import app.olauncher.data.store.SavedItemsStore
import app.olauncher.data.store.UrlExtractor
import app.olauncher.databinding.CardSavedBinding
import app.olauncher.databinding.ItemSavedBinding
import app.olauncher.helper.copyToClipboard
import app.olauncher.helper.hideStatusBar
import app.olauncher.helper.openUrl
import app.olauncher.helper.showToast
import app.olauncher.ui.today.TodayCard
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

object SavedCardLogic {

    fun formatHost(url: String): String {
        val host = UrlExtractor.getHost(url)
        val cleanHost = host.removePrefix("www.")
        return cleanHost.ifBlank { url }
    }

    fun formatRelativeDay(
        savedAt: Long,
        now: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val savedDate = Instant.ofEpochMilli(savedAt).atZone(zoneId).toLocalDate()
        val todayDate = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
        val diffDays = ChronoUnit.DAYS.between(savedDate, todayDate).coerceAtLeast(0)
        return if (diffDays == 0L) {
            "today"
        } else {
            "$diffDays d"
        }
    }

    fun formatSubtitle(
        url: String,
        savedAt: Long,
        now: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val host = formatHost(url)
        val relativeDay = formatRelativeDay(savedAt, now, zoneId)
        return "$host · $relativeDay"
    }

    fun sortActiveItems(items: List<SavedItem>): List<SavedItem> {
        return items.sortedWith(compareBy({ it.savedAt }, { it.id }))
    }

    fun sortArchivedItems(items: List<SavedItem>): List<SavedItem> {
        return items.sortedWith(
            compareByDescending<SavedItem> { it.archivedAt ?: it.savedAt }
                .thenByDescending { it.id }
        )
    }

    fun getVisibleActiveItems(
        items: List<SavedItem>,
        isExpanded: Boolean,
        maxCount: Int = SavedCard.MAX_ACTIVE_VISIBLE,
    ): List<SavedItem> {
        val sorted = sortActiveItems(items)
        return if (isExpanded || sorted.size <= maxCount) {
            sorted
        } else {
            sorted.takeLast(maxCount)
        }
    }

    fun getMoreCount(
        totalActiveCount: Int,
        maxCount: Int = SavedCard.MAX_ACTIVE_VISIBLE,
    ): Int {
        return if (totalActiveCount > maxCount) totalActiveCount - maxCount else 0
    }
}

object SavedCard : TodayCard {

    const val KEY = "saved"
    const val MAX_ACTIVE_VISIBLE = 20

    /** The Today card shows just the latest item until "+N more" is tapped. */
    const val CARD_VISIBLE = 1

    override val key: String = KEY
    override val titleRes: Int = R.string.saved_for_later
    override val emoji: String = "🔖"
    override val drawsOwnHeader: Boolean = true

    var isArchivedExpanded: Boolean = false
    var isActiveExpanded: Boolean = false

    var storeProvider: ((Context) -> SavedItemsStore)? = null

    fun getStore(context: Context): SavedItemsStore {
        return storeProvider?.invoke(context) ?: SavedItemsStore(context)
    }

    override fun createView(inflater: LayoutInflater, parent: ViewGroup, fragment: Fragment): View {
        val viewBinding = CardSavedBinding.inflate(inflater, parent, false)
        ViewCompat.setAccessibilityHeading(viewBinding.tvCardTitle, true)
        val cardView = SavedCardView(viewBinding)
        viewBinding.root.tag = cardView

        viewBinding.tvMore.setOnClickListener {
            Haptics.tick(it)
            isActiveExpanded = true
            cardView.render()
        }

        viewBinding.tvArchived.setOnClickListener {
            Haptics.tick(it)
            isArchivedExpanded = !isArchivedExpanded
            cardView.render()
        }

        cardView.render()
        return viewBinding.root
    }

    override fun onResume(view: View) {
        (view.tag as? SavedCardView)?.render()
    }
}

/** Per-view state of the Saved card; lives and dies with the card's root view. */
internal class SavedCardView(private val binding: CardSavedBinding) {

    fun render() {
        val b = binding
        val context = b.root.context ?: return
        val store = SavedCard.getStore(context)

        val activeItems = store.active()
        val archivedItems = store.archived()

        val totalActive = activeItems.size
        val totalArchived = archivedItems.size

        b.tvCardTitle.text = context.getString(R.string.saved_card_title, totalActive)

        // Empty state: shown when there are no active items
        b.tvEmpty.isVisible = totalActive == 0

        // Active items: just the latest, or all once "+N more" is tapped
        val visibleActive = SavedCardLogic.getVisibleActiveItems(
            items = activeItems,
            isExpanded = SavedCard.isActiveExpanded,
            maxCount = SavedCard.CARD_VISIBLE
        )
        val moreCount = SavedCardLogic.getMoreCount(totalActive, SavedCard.CARD_VISIBLE)

        val latest = SavedCardLogic.sortActiveItems(activeItems).lastOrNull()
        b.tvOpenLatest.isVisible = latest != null
        b.tvOpenLatest.setOnClickListener {
            Haptics.tick(it)
            latest?.let { item -> openItem(context, item) }
        }

        b.tvMore.isVisible = !SavedCard.isActiveExpanded && moreCount > 0
        if (b.tvMore.isVisible) {
            b.tvMore.text = context.getString(R.string.saved_more, moreCount)
        }

        b.activeContainer.removeAllViews()
        val inflater = LayoutInflater.from(context)
        for (item in visibleActive) {
            val itemBinding = ItemSavedBinding.inflate(inflater, b.activeContainer, false)
            itemBinding.tvTitle.text = item.title.ifBlank { SavedCardLogic.formatHost(item.url) }
            itemBinding.tvSubtitle.text = SavedCardLogic.formatSubtitle(item.url, item.savedAt)
            itemBinding.root.setOnClickListener {
                Haptics.tick(it)
                openItem(context, item)
            }
            itemBinding.root.setOnLongClickListener {
                showActiveItemDialog(context, item)
                true
            }
            b.activeContainer.addView(itemBinding.root)
        }

        // Archived footer and items
        b.tvArchived.isVisible = totalArchived > 0
        if (totalArchived > 0) {
            b.tvArchived.text = context.getString(R.string.saved_archived, totalArchived)
        } else {
            SavedCard.isArchivedExpanded = false
        }

        b.archivedContainer.isVisible = totalArchived > 0 && SavedCard.isArchivedExpanded
        b.archivedContainer.removeAllViews()
        if (b.archivedContainer.isVisible) {
            val sortedArchived = SavedCardLogic.sortArchivedItems(archivedItems)
            for (item in sortedArchived) {
                val itemBinding = ItemSavedBinding.inflate(inflater, b.archivedContainer, false)
                itemBinding.tvTitle.text = item.title.ifBlank { SavedCardLogic.formatHost(item.url) }
                itemBinding.tvSubtitle.text = SavedCardLogic.formatSubtitle(item.url, item.savedAt)
                itemBinding.root.setOnClickListener {
                    Haptics.tick(it)
                    openItem(context, item)
                }
                itemBinding.root.setOnLongClickListener {
                    showArchivedItemDialog(context, item)
                    true
                }
                b.archivedContainer.addView(itemBinding.root)
            }
        }
    }

    private fun openItem(context: Context, item: SavedItem) {
        try {
            context.openUrl(item.url)
        } catch (e: Exception) {
            context.showToast(R.string.saved_unable_to_open)
        }
    }

    private fun showActiveItemDialog(context: Context, item: SavedItem) {
        val options = arrayOf(
            context.getString(R.string.saved_archive),
            context.getString(R.string.saved_delete),
            context.getString(R.string.saved_copy_link)
        )
        context.createChoiceDialog(items = options.toList()) { which ->
            when (which) {
                0 -> {
                    val store = SavedCard.getStore(context)
                    val change = ArchiveChange(item.id, item.archivedAt)
                    store.archive(item.id)
                    SavedCard.isArchivedExpanded = true
                    render()
                    UndoMessage.show(context as? Activity, context.getString(R.string.saved_archived_message)) {
                        store.restoreArchivedAt(change.id, change.previousArchivedAt)
                        render()
                    }
                }
                1 -> {
                    SavedCard.getStore(context).delete(item.id)
                    render()
                }
                2 -> {
                    context.copyToClipboard(item.url)
                    context.showToast(R.string.copied)
                }
            }
        }.showRespectingStatusBar()
    }

    private fun showArchivedItemDialog(context: Context, item: SavedItem) {
        val options = arrayOf(
            context.getString(R.string.saved_restore),
            context.getString(R.string.saved_delete)
        )
        context.createChoiceDialog(items = options.toList()) { which ->
            when (which) {
                0 -> {
                    SavedCard.getStore(context).unarchive(item.id)
                    render()
                }
                1 -> {
                    SavedCard.getStore(context).delete(item.id)
                    render()
                }
            }
        }.showRespectingStatusBar()
    }
}
