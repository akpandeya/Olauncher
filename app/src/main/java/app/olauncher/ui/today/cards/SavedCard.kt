package app.olauncher.ui.today.cards

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import app.olauncher.R
import app.olauncher.data.Prefs
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

    override val key: String = KEY
    override val titleRes: Int = R.string.saved_for_later

    var isArchivedExpanded: Boolean = false
    var isActiveExpanded: Boolean = false

    var storeProvider: ((Context) -> SavedItemsStore)? = null

    private var binding: CardSavedBinding? = null

    fun getStore(context: Context): SavedItemsStore {
        return storeProvider?.invoke(context) ?: SavedItemsStore(context)
    }

    override fun createView(inflater: LayoutInflater, parent: ViewGroup, fragment: Fragment): View {
        val viewBinding = CardSavedBinding.inflate(inflater, parent, false)
        binding = viewBinding

        viewBinding.root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                if (binding === viewBinding) {
                    binding = null
                }
            }
        })

        viewBinding.tvMore.setOnClickListener {
            isActiveExpanded = true
            render()
        }

        viewBinding.tvArchived.setOnClickListener {
            isArchivedExpanded = !isArchivedExpanded
            render()
        }

        render()
        return viewBinding.root
    }

    override fun onResume() {
        render()
    }

    fun render() {
        val b = binding ?: return
        val context = b.root.context ?: return
        val store = getStore(context)

        val activeItems = store.active()
        val archivedItems = store.archived()

        val totalActive = activeItems.size
        val totalArchived = archivedItems.size

        // Empty state: shown when there are no active items
        b.tvEmpty.isVisible = totalActive == 0

        // Active items (newest nearest the bottom, at most 20 unless expanded)
        val visibleActive = SavedCardLogic.getVisibleActiveItems(
            items = activeItems,
            isExpanded = isActiveExpanded,
            maxCount = MAX_ACTIVE_VISIBLE
        )
        val moreCount = SavedCardLogic.getMoreCount(totalActive, MAX_ACTIVE_VISIBLE)

        b.tvMore.isVisible = !isActiveExpanded && moreCount > 0
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
            isArchivedExpanded = false
        }

        b.archivedContainer.isVisible = totalArchived > 0 && isArchivedExpanded
        b.archivedContainer.removeAllViews()
        if (b.archivedContainer.isVisible) {
            val sortedArchived = SavedCardLogic.sortArchivedItems(archivedItems)
            for (item in sortedArchived) {
                val itemBinding = ItemSavedBinding.inflate(inflater, b.archivedContainer, false)
                itemBinding.tvTitle.text = item.title.ifBlank { SavedCardLogic.formatHost(item.url) }
                itemBinding.tvSubtitle.text = SavedCardLogic.formatSubtitle(item.url, item.savedAt)
                itemBinding.root.setOnClickListener {
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
        val builder = AlertDialog.Builder(context)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        getStore(context).archive(item.id)
                        isArchivedExpanded = true
                        render()
                    }
                    1 -> {
                        getStore(context).delete(item.id)
                        render()
                    }
                    2 -> {
                        context.copyToClipboard(item.url)
                        context.showToast(R.string.copied)
                    }
                }
            }
        showDialogRespectingStatusBar(context, builder)
    }

    private fun showArchivedItemDialog(context: Context, item: SavedItem) {
        val options = arrayOf(
            context.getString(R.string.saved_restore),
            context.getString(R.string.saved_delete)
        )
        val builder = AlertDialog.Builder(context)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        getStore(context).unarchive(item.id)
                        render()
                    }
                    1 -> {
                        getStore(context).delete(item.id)
                        render()
                    }
                }
            }
        showDialogRespectingStatusBar(context, builder)
    }

    private fun showDialogRespectingStatusBar(context: Context, builder: AlertDialog.Builder): AlertDialog {
        val dialog = builder.create()
        val window = dialog.window
        if (window == null || Prefs(context).showStatusBar) {
            dialog.show()
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            dialog.show()
            window.hideStatusBar()
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
        return dialog
    }
}
