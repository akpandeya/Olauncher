package app.olauncher.ui

import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Filter
import android.widget.Filterable
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.databinding.AdapterAppDrawerBinding
import app.olauncher.databinding.AdapterPrivateSpaceHeaderBinding
import app.olauncher.databinding.AdapterRecentHeaderBinding
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.hapticLongClick
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.isSystemApp
import app.olauncher.helper.showAppActionPopup
import app.olauncher.helper.showKeyboard
import app.olauncher.helper.AppMatcher
import app.olauncher.helper.ArchivedAppsHelper
import app.olauncher.helper.ContactSource
import app.olauncher.helper.DrawerAdapterLogic
import app.olauncher.helper.DrawerFilterPayload
import app.olauncher.helper.DrawerFilterState
import app.olauncher.helper.DrawerSearch
import app.olauncher.helper.DrawerSearchInput
import app.olauncher.helper.LetterStripTarget
import app.olauncher.helper.RecentApp
import android.util.Log

private const val TAG = "AppDrawerAdapter"

class AppDrawerAdapter(
    private var flag: Int,
    private val appLabelGravity: Int,
    private val appClickListener: (AppModel) -> Unit,
    private val appInfoListener: (AppModel) -> Unit,
    private val appDeleteListener: (AppModel) -> Unit,
    private val appHideListener: (AppModel, Int) -> Unit,
    private val appRenameListener: (AppModel, String) -> Unit,
    private val privateSpaceToggleListener: () -> Unit = {},
    private val privateSpaceSettingsListener: () -> Unit = {},
    private val context: Context? = null,
    var bottomSearch: Boolean = false,
    var drawerRecents: Boolean = true,
    var recentAppsProvider: (() -> List<RecentApp>)? = null,
    var hiddenAppsProvider: (() -> Set<String>)? = null,
    var appAddToHomeListener: ((AppModel) -> Unit)? = null,
    var appDistractingListener: ((AppModel) -> Unit)? = null,
    var distractingAppsProvider: (() -> Set<String>)? = null,
    var contactSourceProvider: (() -> ContactSource?)? = null,
    var calendarRowsProvider: ((String) -> List<AppModel.Result>)? = null,
    var currencyRowsProvider: ((String) -> List<AppModel.Result>)? = null,
    var contactLongClickListener: ((AppModel.Result) -> Unit)? = null,
    var germanEnabledProvider: (() -> Boolean)? = null,
    var spellRowsProvider: ((List<String>) -> List<AppModel.Result>)? = null,
    var lookUpRowsProvider: ((String) -> List<AppModel.Result>)? = null,
    var wiktionaryLongClickListener: ((AppModel.Result) -> Unit)? = null,
) : ListAdapter<AppModel, RecyclerView.ViewHolder>(DIFF_CALLBACK), Filterable {

    companion object {
        const val VIEW_TYPE_APP = 0
        const val VIEW_TYPE_PRIVATE_HEADER = 1
        const val VIEW_TYPE_RESULT = 2
        const val VIEW_TYPE_RECENT_HEADER = 3

        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<AppModel>() {
            override fun areItemsTheSame(oldItem: AppModel, newItem: AppModel): Boolean = when {
                oldItem is AppModel.App && newItem is AppModel.App ->
                    oldItem.appPackage == newItem.appPackage && oldItem.user == newItem.user && oldItem.isRecent == newItem.isRecent

                oldItem is AppModel.PinnedShortcut && newItem is AppModel.PinnedShortcut ->
                    oldItem.identity == newItem.identity

                oldItem is AppModel.PrivateSpaceHeader && newItem is AppModel.PrivateSpaceHeader -> true

                oldItem is AppModel.Result && newItem is AppModel.Result ->
                    oldItem.text == newItem.text && oldItem.action == newItem.action

                oldItem is AppModel.RecentHeader && newItem is AppModel.RecentHeader -> true

                else -> false
            }

            override fun areContentsTheSame(oldItem: AppModel, newItem: AppModel): Boolean =
                false
        }
    }

    var searchQuery: String = ""
    private var autoLaunch = true
    private var isBangSearch = false
    var allowAutoLaunch = true
    private var appFilteredScores: List<Int> = emptyList()
    private val appFilter = createAppFilter()
    private val myUserHandle = android.os.Process.myUserHandle()
    private var adapterContext: Context? = context

    val hasResultRow: Boolean
        get() = currentList.any { it is AppModel.Result }

    /** Replaced as a whole only, never mutated in place (read by the filter worker thread). */
    @Volatile
    var appsList: List<AppModel> = emptyList()
        private set
    var recyclerView: RecyclerView? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        this.recyclerView = null
    }

    override fun getItemViewType(position: Int): Int {
        if (position !in 0 until itemCount) return VIEW_TYPE_APP
        return when (getItem(position)) {
            is AppModel.PrivateSpaceHeader -> VIEW_TYPE_PRIVATE_HEADER
            is AppModel.Result -> VIEW_TYPE_RESULT
            is AppModel.RecentHeader -> VIEW_TYPE_RECENT_HEADER
            else -> VIEW_TYPE_APP
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if (adapterContext == null) {
            adapterContext = parent.context
        }
        return when (viewType) {
            VIEW_TYPE_PRIVATE_HEADER -> PrivateSpaceHeaderViewHolder(
                AdapterPrivateSpaceHeaderBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            VIEW_TYPE_RESULT -> ResultViewHolder(
                AdapterAppDrawerBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            VIEW_TYPE_RECENT_HEADER -> RecentHeaderViewHolder(
                AdapterRecentHeaderBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

            else -> ViewHolder(
                AdapterAppDrawerBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        try {
            if (position !in 0 until itemCount) return
            val appModel = getItem(position)
            when (holder) {
                is PrivateSpaceHeaderViewHolder -> {
                    holder.bind(
                        appLabelGravity,
                        privateSpaceToggleListener,
                        privateSpaceSettingsListener,
                    )
                }

                is ResultViewHolder -> {
                    if (appModel is AppModel.Result) {
                        holder.bind(
                            appLabelGravity,
                            appModel,
                            appClickListener,
                            contactLongClickListener,
                            wiktionaryLongClickListener,
                        )
                    }
                }

                is RecentHeaderViewHolder -> {
                    if (appModel is AppModel.RecentHeader) {
                        holder.bind(
                            appLabelGravity,
                            appModel,
                        )
                    }
                }

                is ViewHolder -> {
                    val isHidden = flag == Constants.FLAG_HIDDEN_APPS ||
                        (hiddenAppsProvider?.invoke()?.contains(appModel.appPackage + "|" + appModel.user.toString()) == true)
                    val isDistracting = distractingAppsProvider?.invoke()?.contains(appModel.appPackage) == true
                    holder.bind(
                        flag,
                        appLabelGravity,
                        myUserHandle,
                        appModel,
                        searchQuery,
                        appClickListener,
                        appDeleteListener,
                        appInfoListener,
                        appHideListener,
                        appRenameListener,
                        appAddToHomeListener,
                        appDistractingListener,
                        isHidden,
                        isDistracting,
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "onBindViewHolder failed", e)
        }
    }

    override fun getFilter(): Filter = this.appFilter

    private fun createAppFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(charSearch: CharSequence?): FilterResults {
                val ctx = context ?: adapterContext
                val result = DrawerSearch.run(
                    DrawerSearchInput(
                        query = charSearch,
                        apps = appsList,
                        flag = flag,
                        bottomSearch = bottomSearch,
                        drawerRecents = drawerRecents,
                        allowAutoLaunch = allowAutoLaunch,
                        germanEnabled = { germanEnabledProvider?.invoke() ?: false },
                        recentApps = { recentAppsProvider?.invoke() ?: emptyList() },
                        hiddenApps = { hiddenAppsProvider?.invoke() ?: emptySet() },
                        recentCaption = ctx?.getString(R.string.recent) ?: "Recent",
                        callRowText = { ctx?.getString(R.string.call_number, it) ?: "Call $it" },
                        saveWordRowText = { ctx?.getString(R.string.save_word_row, it) ?: "Save word: $it" },
                        noteRowText = { ctx?.getString(R.string.note_row, it) ?: "Save note: $it" },
                        commandHelpRowText ={ word -> commandHelpText(ctx, word) },
                        cantReadRowText = { ctx?.getString(R.string.command_cant_read, it) ?: "Can't read '$it'" },
                        eventNoTitleRowText = { ctx?.getString(R.string.command_event_no_title) ?: "Can't add an event without a title" },
                        saveWordsRowText = { count, shown ->
                            ctx?.getString(R.string.save_words_row, count, shown) ?: "Save $count words: $shown"
                        },
                        contactSource = { contactSourceProvider?.invoke() },
                        calendarRows = { calendarRowsProvider?.invoke(it) ?: emptyList() },
                        currencyRows = { currencyRowsProvider?.invoke(it) ?: emptyList() },
                        spellRows = { spellRowsProvider?.invoke(it) ?: emptyList() },
                        lookUpRows = { lookUpRowsProvider?.invoke(it) ?: emptyList() },
                    )
                )
                return FilterResults().apply {
                    values = DrawerFilterPayload.from(result)
                }
            }

            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                val state = DrawerAdapterLogic.publish(
                    constraint,
                    results?.values as? DrawerFilterPayload,
                    DrawerFilterState(emptyList(), appFilteredScores, searchQuery, isBangSearch, autoLaunch),
                )
                val items = state.items
                appFilteredScores = state.scores
                searchQuery = state.searchQuery
                isBangSearch = state.isBangSearch
                autoLaunch = state.autoLaunch
                (recyclerView?.layoutManager as? LinearLayoutManager)?.let { lm ->
                    lm.stackFromEnd = bottomSearch
                }
                submitList(items) {
                    DrawerAdapterLogic.scrollTarget(bottomSearch, items.size)?.let {
                        recyclerView?.scrollToPosition(it)
                    }
                    autoLaunch()
                }
            }
        }
    }

    private fun autoLaunch() {
        try {
            val strongMatches = DrawerAdapterLogic.strongMatches(appFilteredScores)
            val first = DrawerAdapterLogic.autoLaunchCandidate(currentList, bottomSearch)
            if (ArchivedAppsHelper.canAutoLaunch(
                    query = searchQuery,
                    hasResultRow = hasResultRow,
                    strongMatches = strongMatches,
                    autoLaunch = autoLaunch,
                    isBangSearch = isBangSearch,
                    flag = flag,
                    firstItem = first
                )
            ) {
                appClickListener(first!!)
            }
        } catch (e: Exception) {
            Log.w(TAG, "autoLaunch failed", e)
        }
    }

    fun setAppList(incoming: List<AppModel>) {
        // Copy, then add empty app for bottom padding in recyclerview
        val newList = incoming + AppModel.App(
            appLabel = "",
            key = null,
            appPackage = "",
            activityClassName = "",
            isNew = false,
            user = android.os.Process.myUserHandle()
        )
        this.appsList = newList
        this.appFilteredScores = emptyList()
        this.searchQuery = ""
        submitList(newList)
    }

    /** Removes an app from the full and displayed lists without mutating either in place. */
    fun removeApp(app: AppModel) {
        appsList = DrawerAdapterLogic.removeApp(appsList, app)
        submitList(DrawerAdapterLogic.removeApp(currentList, app))
    }

    fun launchFirstInList() {
        getTopApp()?.let { appClickListener(it) }
    }

    /** Best app match nearest the search box, skipping result rows and headers. */
    fun getTopApp(): AppModel? {
        val isApp = { it: AppModel -> it !is AppModel.PrivateSpaceHeader && it !is AppModel.Result && it !is AppModel.RecentHeader && it.appLabel.isNotBlank() }
        return if (bottomSearch) currentList.lastOrNull(isApp) else currentList.firstOrNull(isApp)
    }

    fun getTopItem(): AppModel? =
        if (bottomSearch) {
            currentList.lastOrNull { it !is AppModel.RecentHeader && it.appLabel.isNotBlank() }
        } else {
            currentList.firstOrNull { it !is AppModel.RecentHeader && it.appLabel.isNotBlank() }
        }

    fun getTargetPositionForOrderedIndex(orderedIndex: Int): Int {
        val recentRows = currentList.count { it is AppModel.RecentHeader || (it is AppModel.App && it.isRecent) }
        return LetterStripTarget.position(orderedIndex, bottomSearch, currentList.size, recentRows)
    }

    fun getTopScore(): Int? =
        appFilteredScores.firstOrNull()

    fun executeItem(appModel: AppModel) {
        appClickListener(appModel)
    }

    class RecentHeaderViewHolder(private val binding: AdapterRecentHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(
            appLabelGravity: Int,
            header: AppModel.RecentHeader,
        ) = with(binding) {
            recentCaption.gravity = appLabelGravity or Gravity.CENTER_VERTICAL
            if (header.title.isNotEmpty()) {
                recentCaption.text = header.title
            }
        }
    }

    class ResultViewHolder(private val binding: AdapterAppDrawerBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(
            appLabelGravity: Int,
            result: AppModel.Result,
            clickListener: (AppModel) -> Unit,
            longClickListener: ((AppModel.Result) -> Unit)? = null,
            wiktionaryLongClickListener: ((AppModel.Result) -> Unit)? = null,
        ) = with(binding) {
            renameLayout.visibility = View.GONE
            otherProfileIndicator.visibility = View.GONE
            appTitle.visibility = View.VISIBLE
            appTitle.text = result.text
            appTitle.alpha = if (result.action is AppModel.Result.Action.Error) 0.5f else 1f
            appTitle.gravity = appLabelGravity or Gravity.CENTER_VERTICAL
            appTitle.setOnClickListener { clickListener(result) }
            val onLong: View.OnLongClickListener? = when {
                result.action is AppModel.Result.Action.Contact && longClickListener != null ->
                    View.OnLongClickListener { longClickListener(result); true }
                result.action is AppModel.Result.Action.OpenWiktionary && wiktionaryLongClickListener != null ->
                    View.OnLongClickListener { wiktionaryLongClickListener(result); true }
                else -> null
            }
            appTitle.setOnLongClickListener(onLong)
            root.setOnClickListener { clickListener(result) }
            root.setOnLongClickListener(onLong)
        }
    }

    class PrivateSpaceHeaderViewHolder(private val binding: AdapterPrivateSpaceHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(
            appLabelGravity: Int,
            toggleListener: () -> Unit,
            settingsListener: () -> Unit,
        ) = with(binding) {
            privateSpaceTitle.gravity = appLabelGravity
            privateSpaceTitle.setOnClickListener { toggleListener() }
            privateSpaceTitle.setOnLongClickListener {
                settingsListener()
                true
            }
        }
    }

    class ViewHolder(private val binding: AdapterAppDrawerBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(
            flag: Int,
            appLabelGravity: Int,
            myUserHandle: UserHandle,
            appModel: AppModel,
            searchQuery: String,
            clickListener: (AppModel) -> Unit,
            appDeleteListener: (AppModel) -> Unit,
            appInfoListener: (AppModel) -> Unit,
            appHideListener: (AppModel, Int) -> Unit,
            appRenameListener: (AppModel, String) -> Unit,
            appAddToHomeListener: ((AppModel) -> Unit)?,
            appDistractingListener: ((AppModel) -> Unit)?,
            isHidden: Boolean,
            isDistracting: Boolean,
        ) = with(binding) {
            renameLayout.visibility = View.GONE
            appTitle.visibility = View.VISIBLE

            // Show indicators in title based on app type and state
            val formattedTitle = ArchivedAppsHelper.formatAppTitle(appModel)
            val accentColor = root.context.getColorFromAttr(R.attr.accentColor)
            val primaryColor = root.context.getColorFromAttr(R.attr.primaryColor)
            val hasAccent = accentColor != primaryColor

            val ranges = if (searchQuery.isNotBlank()) {
                AppMatcher.matchRanges(appModel.appLabel, searchQuery)
            } else {
                emptyList()
            }

            val hasStar = appModel.isNew && !appModel.isArchived
            val starIndex = if (hasStar) formattedTitle.lastIndexOf('✦') else -1

            if (ranges.isNotEmpty() || starIndex >= 0) {
                val spannable = SpannableString(formattedTitle)
                for (range in ranges) {
                    val start = range.first.coerceIn(0, appModel.appLabel.length)
                    val end = (range.last + 1).coerceIn(start, appModel.appLabel.length)
                    if (start < end) {
                        spannable.setSpan(
                            UnderlineSpan(),
                            start,
                            end,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        if (hasAccent) {
                            spannable.setSpan(
                                ForegroundColorSpan(accentColor),
                                start,
                                end,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }
                    }
                }
                if (starIndex >= 0) {
                    spannable.setSpan(
                        ForegroundColorSpan(accentColor),
                        starIndex,
                        starIndex + 1,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                appTitle.text = spannable
            } else {
                appTitle.text = formattedTitle
            }
            appTitle.gravity = appLabelGravity or Gravity.CENTER_VERTICAL
            otherProfileIndicator.isVisible = appModel.user != myUserHandle

            appTitle.setOnClickListener { clickListener(appModel) }

            fun showRenameEdit() {
                if (appModel.appPackage.isNotEmpty()) {
                    etAppRename.hint = getAppName(etAppRename.context, appModel.appPackage, appModel.user)
                    etAppRename.setText(appModel.appLabel)
                    etAppRename.setSelectAllOnFocus(true)
                    renameLayout.visibility = View.VISIBLE
                    appTitle.visibility = View.INVISIBLE
                    etAppRename.showKeyboard()
                    etAppRename.imeOptions = EditorInfo.IME_ACTION_DONE
                }
            }

            appTitle.hapticLongClick {
                if (appModel.appPackage.isNotEmpty()) {
                    val actions = AppActionList.build(
                        isArchived = appModel.isArchived,
                        isHidden = isHidden,
                        isDistracting = isDistracting,
                        flag = flag,
                    )
                    appTitle.showAppActionPopup(appModel.appLabel, actions) { action ->
                        when (action.id) {
                            AppAction.Id.CHANGE_APP, AppAction.Id.CHOOSE_BUILTIN, AppAction.Id.RESET_SHORTCUT,
                            AppAction.Id.REMOVE_FROM_HOME -> {} // home-only
                            AppAction.Id.ADD_TO_HOME -> appAddToHomeListener?.invoke(appModel)
                            AppAction.Id.RENAME -> showRenameEdit()
                            AppAction.Id.HIDE -> appHideListener(appModel, bindingAdapterPosition)
                            AppAction.Id.DISTRACTING -> appDistractingListener?.invoke(appModel)
                            AppAction.Id.APP_INFO -> appInfoListener(appModel)
                            AppAction.Id.UNINSTALL -> appDeleteListener(appModel)
                        }
                    }
                }
            }

            // Configure rename behavior
            etAppRename.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                appTitle.visibility = if (hasFocus) View.INVISIBLE else View.VISIBLE
            }
            etAppRename.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    etAppRename.hint = getAppName(etAppRename.context, appModel.appPackage, appModel.user)
                }

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    etAppRename.hint = ""
                }
            })
            etAppRename.setOnEditorActionListener { _, actionCode, _ ->
                if (actionCode == EditorInfo.IME_ACTION_DONE) {
                    val renameLabel = etAppRename.text.toString().trim()
                    if (renameLabel.isNotBlank() && appModel.appPackage.isNotBlank()) {
                        appRenameListener(appModel, renameLabel)
                        renameLayout.visibility = View.GONE
                        appTitle.visibility = View.VISIBLE
                    }
                    true
                }
                false
            }
            tvSaveRename.setOnClickListener {
                etAppRename.hideKeyboard()
                val renameLabel = etAppRename.text.toString().trim()
                if (renameLabel.isNotBlank() && appModel.appPackage.isNotBlank()) {
                    appRenameListener(appModel, renameLabel)
                    renameLayout.visibility = View.GONE
                    appTitle.visibility = View.VISIBLE
                } else {
                    appRenameListener(
                        appModel,
                        getAppName(etAppRename.context, appModel.appPackage, appModel.user)
                    )
                    renameLayout.visibility = View.GONE
                    appTitle.visibility = View.VISIBLE
                }
            }
            appRenameClose.setOnClickListener {
                renameLayout.visibility = View.GONE
                appTitle.visibility = View.VISIBLE
            }
        }

        private fun getAppName(context: Context, appPackage: String, user: UserHandle): String {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            return try {
                val activityList = launcherApps.getActivityList(appPackage, user)
                if (activityList.isNotEmpty()) {
                    activityList.first().label.toString()
                } else {
                    val packageManager = context.packageManager
                    packageManager.getApplicationLabel(
                        packageManager.getApplicationInfo(appPackage, 0)
                    ).toString()
                }
            } catch (_: Exception) {
                "" // As a fallback, display an empty string.
            }
        }
    }
}

private fun commandHelpText(ctx: android.content.Context?, word: String): String {
    val res = when (word) {
        "t" -> R.string.command_help_timer
        "a" -> R.string.command_help_alarm
        "e" -> R.string.command_help_event
        "n" -> R.string.command_help_note
        "sw" -> R.string.command_help_save_words
        "w" -> R.string.command_help_look_up
        else -> return ":$word"
    }
    return ctx?.getString(res) ?: ":$word"
}
