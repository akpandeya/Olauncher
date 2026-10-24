package app.olauncher.ui.today.cards

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import app.olauncher.MainViewModel
import app.olauncher.helper.Haptics
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.databinding.CardHeldBinding
import app.olauncher.databinding.ItemHeldBinding
import app.olauncher.quiet.HeldItem
import app.olauncher.quiet.HeldStore
import app.olauncher.ui.today.TodayCard
import app.olauncher.ui.today.TodayOrder
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

data class HeldAppSummary(
    val pkg: String,
    val label: String,
    val count: Int,
    val latestPostTime: Long,
    val latestTitle: String,
    val latestText: String,
)

object HeldCardLogic {

    // Held items stay visible even if quiet mode is off or access was revoked.
    fun isAvailable(hasHeldItems: Boolean): Boolean = hasHeldItems

    fun groupAndSort(
        items: List<HeldItem>,
        nowMillis: Long = System.currentTimeMillis(),
        cutoffMillis: Long = nowMillis - HeldStore.FORTY_EIGHT_HOURS_MS,
    ): List<HeldAppSummary> {
        val validItems = items.filter { it.postTime >= cutoffMillis }
        if (validItems.isEmpty()) return emptyList()

        val grouped = validItems.groupBy { it.pkg }
        return grouped.map { (pkg, groupItems) ->
            val latestItem = groupItems.maxByOrNull { it.postTime }!!
            val label = groupItems.firstOrNull { it.label.isNotBlank() }?.label?.trim()
                ?.takeIf { it.isNotEmpty() } ?: latestItem.pkg
            HeldAppSummary(
                pkg = pkg,
                label = label,
                count = groupItems.size,
                latestPostTime = latestItem.postTime,
                latestTitle = latestItem.title,
                latestText = latestItem.text,
            )
        }.sortedWith(
            compareByDescending<HeldAppSummary> { it.latestPostTime }.thenBy { it.pkg }
        )
    }

    fun formatTime(postTime: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val zonedDateTime = Instant.ofEpochMilli(postTime).atZone(zone)
        return String.format(Locale.ROOT, "%02d:%02d", zonedDateTime.hour, zonedDateTime.minute)
    }

    fun formatContent(title: String, text: String): String {
        val cleanTitle = title.replace('\n', ' ').replace('\r', ' ').trim()
        val cleanText = text.replace('\n', ' ').replace('\r', ' ').trim()
        return when {
            cleanTitle.isNotEmpty() && cleanText.isNotEmpty() -> "$cleanTitle $cleanText"
            cleanTitle.isNotEmpty() -> cleanTitle
            cleanText.isNotEmpty() -> cleanText
            else -> ""
        }
    }

    const val DEFAULT_HEADER_TEMPLATE = "%1\$s — %2\$d held · latest %3\$s"

    /** First line, e.g. "YouTube — 2 held · latest 03:19". */
    fun formatHeaderLine(
        summary: HeldAppSummary,
        zone: ZoneId = ZoneId.systemDefault(),
        template: String = DEFAULT_HEADER_TEMPLATE,
    ): String = String.format(
        Locale.ROOT, template, summary.label, summary.count, formatTime(summary.latestPostTime, zone)
    )

    /** First line of the Today card, e.g. "YouTube · latest 03:19"; the count is in the card title. */
    fun formatCardLine(
        summary: HeldAppSummary,
        zone: ZoneId = ZoneId.systemDefault(),
        template: String = DEFAULT_CARD_TEMPLATE,
    ): String = String.format(Locale.ROOT, template, summary.label, formatTime(summary.latestPostTime, zone))

    const val DEFAULT_CARD_TEMPLATE = "%1\$s · latest %2\$s"

    /** Second line: the latest title, falling back to its text; empty if neither. */
    fun formatLatestLine(summary: HeldAppSummary): String {
        val title = formatContent(summary.latestTitle, "")
        return title.ifEmpty { formatContent("", summary.latestText) }
    }
}

object HeldCard : TodayCard {

    const val KEY = "held"

    override val key: String = KEY
    override val titleRes: Int = R.string.held
    override val emoji: String = "🔔"
    override val drawsOwnHeader: Boolean = true
    override val tinted: Boolean = true
    override val defaultEnabled: Boolean = true

    var storeProvider: ((Context) -> HeldStore)? = null
    var nowSupplier: () -> Long = { System.currentTimeMillis() }
    var zoneSupplier: () -> ZoneId = { ZoneId.systemDefault() }
    var appOpener: ((Fragment, String, String) -> Unit)? = null

    fun getStore(context: Context): HeldStore {
        return storeProvider?.invoke(context) ?: HeldStore(context)
    }

    override fun isAvailable(context: Context): Boolean {
        val now = nowSupplier()
        val hasHeldItems = HeldCardLogic.groupAndSort(getStore(context).all(), now).isNotEmpty()
        return HeldCardLogic.isAvailable(hasHeldItems)
    }

    override fun priority(context: Context, now: ZonedDateTime): Int {
        val anyHeld = HeldCardLogic.groupAndSort(getStore(context).all(), now.toInstant().toEpochMilli()).isNotEmpty()
        return TodayOrder.heldPriority(anyHeld, super.priority(context, now))
    }

    override fun createView(inflater: LayoutInflater, parent: ViewGroup, fragment: Fragment): View {
        val viewBinding = CardHeldBinding.inflate(inflater, parent, false)
        ViewCompat.setAccessibilityHeading(viewBinding.tvCardTitle, true)
        val cardView = HeldCardView(viewBinding, fragment)
        viewBinding.root.tag = cardView

        viewBinding.root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                cardView.release()
            }
        })

        viewBinding.tvClearAll.setOnClickListener {
            Haptics.tick(it)
            val context = viewBinding.root.context ?: return@setOnClickListener
            getStore(context).clearAll()
            cardView.render()
        }

        cardView.render()
        return viewBinding.root
    }

    override fun onResume(view: View) {
        (view.tag as? HeldCardView)?.render()
    }

    fun openApp(fragment: Fragment, pkg: String, label: String) {
        appOpener?.invoke(fragment, pkg, label) ?: run {
            val activity = fragment.activity ?: return
            val viewModel = try {
                ViewModelProvider(activity)[MainViewModel::class.java]
            } catch (_: Exception) {
                null
            }
            val existingApp = viewModel?.appList?.value?.filterIsInstance<AppModel.App>()
                ?.firstOrNull { it.appPackage == pkg }

            val appModel = existingApp ?: run {
                val pm = fragment.requireContext().packageManager
                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                val className = launchIntent?.component?.className
                AppModel.App(
                    appLabel = label,
                    key = null,
                    appPackage = pkg,
                    activityClassName = className,
                    isNew = false,
                    user = android.os.Process.myUserHandle()
                )
            }
            viewModel?.selectedApp(appModel, Constants.FLAG_LAUNCH_APP)
        }
    }
}

/** Per-view state of the Held card; lives and dies with the card's root view. */
internal class HeldCardView(
    private val binding: CardHeldBinding,
    fragment: Fragment,
) {
    private var fragment: Fragment? = fragment

    fun release() {
        fragment = null
    }

    fun render() {
        val b = binding
        val context = b.root.context ?: return
        val store = HeldCard.getStore(context)
        val now = HeldCard.nowSupplier()
        store.pruneOlderThan(now - HeldStore.FORTY_EIGHT_HOURS_MS)
        val items = store.all()
        val summaries = HeldCardLogic.groupAndSort(items, now)

        if (summaries.isEmpty()) {
            b.tvEmpty.isVisible = true
            b.itemsContainer.removeAllViews()
            b.tvClearAll.isVisible = false
            return
        }

        b.tvEmpty.isVisible = false
        b.tvClearAll.isVisible = true
        b.itemsContainer.removeAllViews()

        val inflater = LayoutInflater.from(context)
        val zone = HeldCard.zoneSupplier()
        val fragment = fragment

        b.tvCardTitle.text = context.getString(R.string.held_card_title, summaries.sumOf { it.count })

        // Just the latest held item; the rest are one tap away through the app itself.
        for (summary in summaries.take(1)) {
            val itemBinding = ItemHeldBinding.inflate(inflater, b.itemsContainer, false)
            itemBinding.tvHeldLine.text = HeldCardLogic.formatCardLine(
                summary, zone, context.getString(R.string.held_card_latest)
            )
            val latest = HeldCardLogic.formatLatestLine(summary)
            itemBinding.tvHeldLatest.text = latest
            itemBinding.tvHeldLatest.isVisible = latest.isNotEmpty()
            if (fragment != null && summary.pkg.isNotBlank()) {
                itemBinding.root.setOnClickListener {
                    Haptics.tick(it)
                    store.clear(summary.pkg)
                    render()
                    HeldCard.openApp(fragment, summary.pkg, summary.label)
                }
            } else {
                itemBinding.root.isClickable = false
                itemBinding.root.isFocusable = false
            }
            b.itemsContainer.addView(itemBinding.root)
        }
    }
}
