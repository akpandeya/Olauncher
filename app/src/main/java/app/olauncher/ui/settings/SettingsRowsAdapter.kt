package app.olauncher.ui.settings

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.databinding.ItemSettingsHeadingBinding
import app.olauncher.databinding.ItemSettingsRowSliderBinding
import app.olauncher.databinding.ItemSettingsRowStatusBinding
import app.olauncher.databinding.ItemSettingsRowSubtitleBinding
import app.olauncher.databinding.ItemSettingsRowValueBinding
import app.olauncher.databinding.ItemSettingsSubheadingBinding

/** The views of one Settings list item, whatever its shape. Views a shape lacks stay null. */
class SettingsRowHolder(
    root: View,
    val heading: TextView? = null,
    val title: TextView? = null,
    val value: TextView? = null,
    val subtitle: TextView? = null,
    val seekBar: SeekBar? = null,
    val actions: List<TextView> = emptyList(),
    val actionsContainer: View? = null,
) : RecyclerView.ViewHolder(root) {

    /** The row this holder shows now; null while it shows a heading or nothing. */
    var boundSpec: SettingsRowSpec? = null

    /** The row background and every text in it. The slider is left out: it handles its own touches. */
    fun tappableViews(): List<View> = listOfNotNull(itemView, title, value, subtitle) + actions

    /** Every id-carrying view of the bound row, by the id the row model gives it. */
    fun viewsById(spec: SettingsRowSpec): Map<Int, View> {
        val views = LinkedHashMap<Int, View>()
        fun put(id: Int, view: View?) {
            if (id != 0 && view != null) views[id] = view
        }
        put(spec.layoutId, itemView)
        put(spec.titleId, title)
        put(spec.valueId, value)
        put(spec.subtitleId, subtitle)
        put(spec.seekBarId, seekBar)
        put(spec.actionsContainerId, actionsContainer)
        spec.actionIds.forEachIndexed { index, id -> put(id, actions.getOrNull(index)) }
        return views
    }
}

/** What the adapter needs from its owner when a row appears or goes away. */
interface SettingsRowsHost {
    fun onRowBound(spec: SettingsRowSpec, holder: SettingsRowHolder)
    fun onRowUnbound(spec: SettingsRowSpec, holder: SettingsRowHolder)
    fun isRowAvailable(spec: SettingsRowSpec): Boolean
}

/** Draws the Settings list: headings and the rows of [SettingsCatalog], one adapter for every page. */
class SettingsRowsAdapter(
    private val host: SettingsRowsHost,
) : ListAdapter<SettingsItem, SettingsRowHolder>(Diff) {

    override fun getItemViewType(position: Int): Int = when (val item = getItem(position)) {
        is SettingsItem.Heading -> if (item.style == SettingsHeadingStyle.CARD) VIEW_HEADING else VIEW_SUBHEADING
        is SettingsItem.Row -> when (item.spec.shape) {
            SettingsRowShape.VALUE -> VIEW_VALUE
            SettingsRowShape.SUBTITLE -> VIEW_SUBTITLE
            SettingsRowShape.SLIDER -> VIEW_SLIDER
            SettingsRowShape.STATUS -> VIEW_STATUS
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SettingsRowHolder {
        val holder = createHolder(parent, viewType)
        val offLabel = parent.context.getString(R.string.off)
        holder.value?.let { dimWhenOff(it, offLabel) }
        return holder
    }

    /** Draws "Off" dimmer than any other value, whoever sets the text. */
    private fun dimWhenOff(view: TextView, offLabel: String) {
        view.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                view.alpha = SettingsValueTone.alphaFor(s, offLabel)
            }
        })
    }

    private fun createHolder(parent: ViewGroup, viewType: Int): SettingsRowHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_HEADING -> ItemSettingsHeadingBinding.inflate(inflater, parent, false).let {
                SettingsRowHolder(it.root, heading = it.settingsHeading)
            }
            VIEW_SUBHEADING -> ItemSettingsSubheadingBinding.inflate(inflater, parent, false).let {
                SettingsRowHolder(it.root, heading = it.settingsSubheading)
            }
            VIEW_SUBTITLE -> ItemSettingsRowSubtitleBinding.inflate(inflater, parent, false).let {
                SettingsRowHolder(
                    it.root,
                    title = it.settingsRowTitle,
                    value = it.settingsRowValue,
                    subtitle = it.settingsRowSubtitle,
                    actions = listOf(it.settingsRowActionFirst, it.settingsRowActionSecond),
                    actionsContainer = it.settingsRowActions,
                )
            }
            VIEW_SLIDER -> ItemSettingsRowSliderBinding.inflate(inflater, parent, false).let {
                SettingsRowHolder(it.root, title = it.settingsRowTitle, value = it.settingsRowValue, seekBar = it.settingsRowSeekBar)
            }
            VIEW_STATUS -> ItemSettingsRowStatusBinding.inflate(inflater, parent, false).let {
                SettingsRowHolder(it.root, value = it.settingsRowStatus)
            }
            else -> ItemSettingsRowValueBinding.inflate(inflater, parent, false).let {
                SettingsRowHolder(it.root, title = it.settingsRowTitle, value = it.settingsRowValue)
            }
        }
    }

    override fun onBindViewHolder(holder: SettingsRowHolder, position: Int) {
        holder.boundSpec?.let { host.onRowUnbound(it, holder) }
        holder.boundSpec = null
        when (val item = getItem(position)) {
            is SettingsItem.Heading -> {
                val heading = holder.heading ?: return
                heading.setText(item.titleRes)
                heading.id = if (item.id != 0) item.id else View.NO_ID
            }
            is SettingsItem.Row -> bindRow(holder, item.spec)
        }
    }

    private fun bindRow(holder: SettingsRowHolder, spec: SettingsRowSpec) {
        val root = holder.itemView
        root.id = idOrNone(spec.layoutId)
        holder.title?.let {
            it.id = idOrNone(spec.titleId)
            it.setText(spec.titleRes)
        }
        holder.subtitle?.let {
            it.id = idOrNone(spec.subtitleId)
            if (spec.subtitleRes != 0) it.setText(spec.subtitleRes) else it.text = ""
            it.visibility = View.VISIBLE
        }
        holder.value?.let {
            it.id = idOrNone(spec.valueId)
            it.text = ""
            // A title-only row (an action) has no value to draw.
            it.visibility = if (spec.valueId != 0 || spec.shape == SettingsRowShape.STATUS) View.VISIBLE else View.GONE
        }
        holder.seekBar?.let {
            it.id = idOrNone(spec.seekBarId)
            it.contentDescription = it.context.getString(spec.titleRes)
            holder.title?.labelFor = it.id
        }
        holder.actionsContainer?.let { container ->
            container.id = idOrNone(spec.actionsContainerId)
            container.visibility = if (spec.actionIds.isEmpty()) View.GONE else View.VISIBLE
            holder.actions.forEachIndexed { index, view ->
                view.id = idOrNone(spec.actionIds.getOrNull(index) ?: 0)
                val label = spec.actionLabelRes.getOrNull(index)
                if (label != null) view.setText(label) else view.text = ""
                view.visibility = if (index < spec.actionIds.size) View.VISIBLE else View.GONE
            }
        }
        // A status line starts hidden and is shown by its own populate function.
        root.visibility = if (spec.shape == SettingsRowShape.STATUS) View.GONE
        else if (host.isRowAvailable(spec)) View.VISIBLE else View.GONE
        holder.boundSpec = spec
        host.onRowBound(spec, holder)
    }

    override fun onViewRecycled(holder: SettingsRowHolder) {
        holder.boundSpec?.let { host.onRowUnbound(it, holder) }
        holder.boundSpec = null
        super.onViewRecycled(holder)
    }

    private fun idOrNone(id: Int): Int = if (id != 0) id else View.NO_ID

    private object Diff : DiffUtil.ItemCallback<SettingsItem>() {
        override fun areItemsTheSame(old: SettingsItem, new: SettingsItem): Boolean = when {
            old is SettingsItem.Row && new is SettingsItem.Row -> old.spec.key == new.spec.key
            old is SettingsItem.Heading && new is SettingsItem.Heading -> old.titleRes == new.titleRes && old.style == new.style
            else -> false
        }

        override fun areContentsTheSame(old: SettingsItem, new: SettingsItem): Boolean = old == new
    }

    private companion object {
        const val VIEW_HEADING = 0
        const val VIEW_SUBHEADING = 1
        const val VIEW_VALUE = 2
        const val VIEW_SUBTITLE = 3
        const val VIEW_SLIDER = 4
        const val VIEW_STATUS = 5
    }
}
