package app.olauncher.ui.today.cards

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.databinding.CardHeldBinding
import app.olauncher.databinding.ItemHeldBinding
import app.olauncher.quiet.HeldItem
import app.olauncher.quiet.HeldStore
import app.olauncher.ui.today.TodayCard
import java.time.Instant
import java.time.ZoneId
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

    fun formatAppLine(
        summary: HeldAppSummary,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val time = formatTime(summary.latestPostTime, zone)
        val content = formatContent(summary.latestTitle, summary.latestText)
        val timeAndContent = if (content.isNotEmpty()) "$time $content" else time
        return "${summary.label} · ${summary.count} · $timeAndContent"
    }
}

object HeldCard : TodayCard {

    const val KEY = "held"

    override val key: String = KEY
    override val titleRes: Int = R.string.held
    override val emoji: String = "🔔"
    override val defaultEnabled: Boolean = true

    var storeProvider: ((Context) -> HeldStore)? = null
    var nowSupplier: () -> Long = { System.currentTimeMillis() }
    var zoneSupplier: () -> ZoneId = { ZoneId.systemDefault() }
    var appOpener: ((Fragment, String, String) -> Unit)? = null

    internal var binding: CardHeldBinding? = null
    internal var attachedFragment: Fragment? = null

    fun getStore(context: Context): HeldStore {
        return storeProvider?.invoke(context) ?: HeldStore(context)
    }

    override fun isAvailable(context: Context): Boolean {
        val now = nowSupplier()
        val hasHeldItems = HeldCardLogic.groupAndSort(getStore(context).all(), now).isNotEmpty()
        return HeldCardLogic.isAvailable(hasHeldItems)
    }

    override fun createView(inflater: LayoutInflater, parent: ViewGroup, fragment: Fragment): View {
        val viewBinding = CardHeldBinding.inflate(inflater, parent, false)
        binding = viewBinding
        attachedFragment = fragment

        viewBinding.root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                if (binding === viewBinding) {
                    binding = null
                    attachedFragment = null
                }
            }
        })

        viewBinding.tvClearAll.setOnClickListener {
            val context = viewBinding.root.context ?: return@setOnClickListener
            getStore(context).clearAll()
            render()
        }

        render()
        return viewBinding.root
    }

    override fun onResume() {
        render()
    }

    override fun onResume(view: View) {
        if (binding == null) {
            try {
                binding = CardHeldBinding.bind(view)
            } catch (_: Exception) {
            }
        }
        render()
    }

    fun render() {
        val b = binding ?: return
        val context = b.root.context ?: return
        val store = getStore(context)
        val now = nowSupplier()
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
        val zone = zoneSupplier()
        val fragment = attachedFragment

        for (summary in summaries) {
            val itemBinding = ItemHeldBinding.inflate(inflater, b.itemsContainer, false)
            itemBinding.tvHeldLine.text = HeldCardLogic.formatAppLine(summary, zone)
            itemBinding.root.setOnClickListener {
                store.clear(summary.pkg)
                render()
                if (fragment != null) {
                    openApp(fragment, summary.pkg, summary.label)
                }
            }
            b.itemsContainer.addView(itemBinding.root)
        }
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
