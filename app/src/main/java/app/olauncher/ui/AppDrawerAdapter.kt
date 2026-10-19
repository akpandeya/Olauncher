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
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.isSystemApp
import app.olauncher.helper.showKeyboard
import app.olauncher.helper.AppMatcher
import app.olauncher.helper.ArchivedAppsHelper
import app.olauncher.helper.Calc
import app.olauncher.helper.UnitConvert
import app.olauncher.helper.ContactSearch
import app.olauncher.helper.ContactSource
import app.olauncher.helper.DialMatch
import app.olauncher.helper.DrawerOrdering
import app.olauncher.helper.RecentApp
import app.olauncher.helper.RecentApps

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
    private data class FilterPayload(
        val items: MutableList<AppModel>,
        val scores: List<Int>,
    )
    private val appFilter = createAppFilter()
    private val myUserHandle = android.os.Process.myUserHandle()
    private var adapterContext: Context? = context

    val hasResultRow: Boolean
        get() = appFilteredList.any { it is AppModel.Result }

    var appsList: MutableList<AppModel> = mutableListOf()
    var appFilteredList: MutableList<AppModel> = mutableListOf()
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
        return when (appFilteredList.getOrNull(position)) {
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
            if (appFilteredList.isEmpty() || position == RecyclerView.NO_POSITION) return
            val appModel = appFilteredList[holder.bindingAdapterPosition]
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
            e.printStackTrace()
        }
    }

    override fun getFilter(): Filter = this.appFilter

    private fun createAppFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(charSearch: CharSequence?): FilterResults {
                val queryStr = charSearch?.toString().orEmpty()
                searchQuery = queryStr
                isBangSearch = charSearch?.startsWith("!") ?: false
                autoLaunch = allowAutoLaunch && (charSearch?.startsWith(" ")?.not() ?: true)

                val filterResults = FilterResults()
                if (charSearch.isNullOrBlank()) {
                    val recentAppsToDisplay = if (flag == Constants.FLAG_LAUNCH_APP && drawerRecents) {
                        val recentDecoded = recentAppsProvider?.invoke() ?: emptyList()
                        val hiddenApps = hiddenAppsProvider?.invoke() ?: emptySet()
                        recentDecoded.mapNotNull { entry ->
                            val matching = appsList.filterIsInstance<AppModel.App>().firstOrNull {
                                it.appPackage == entry.packageName &&
                                it.user.toString() == entry.user &&
                                !it.isArchived &&
                                it.appLabel.isNotBlank() &&
                                !hiddenApps.contains(it.appPackage + "|" + it.user.toString())
                            } ?: return@mapNotNull null
                            matching.copy(isRecent = true)
                        }.take(3)
                    } else {
                        emptyList()
                    }

                    val ctx = context ?: adapterContext
                    val caption = ctx?.getString(R.string.recent) ?: "Recent"
                    val blankItems = RecentApps.forEmptySearch(
                        appsList = appsList,
                        recentApps = recentAppsToDisplay,
                        recentCaption = caption,
                        bottomSearch = bottomSearch,
                    ).toMutableList()

                    filterResults.values = FilterPayload(blankItems, emptyList())
                    return filterResults
                }

                val query = charSearch.toString()
                val scoredApps = appsList.mapNotNull { app ->
                    if (app is AppModel.PrivateSpaceHeader || app is AppModel.Result || app is AppModel.RecentHeader) return@mapNotNull null
                    val score = AppMatcher.score(app.appLabel, query) ?: return@mapNotNull null
                    app to score
                }.sortedWith(
                    compareByDescending<Pair<AppModel, Int>> { it.second }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.first.appLabel }
                )

                val resultRows = mutableListOf<AppModel.Result>()
                if (flag == Constants.FLAG_LAUNCH_APP) {
                    val calcResult = if (DialMatch.showCalcRow(query)) Calc.evaluate(query) else null
                    if (calcResult != null) {
                        resultRows.add(
                            AppModel.Result(
                                text = "= $calcResult",
                                action = AppModel.Result.Action.Copy(calcResult)
                            )
                        )
                    }
                    val unitResult = if (calcResult == null) UnitConvert.evaluate(query) else null
                    if (unitResult != null) {
                        resultRows.add(
                            AppModel.Result(
                                text = "= $unitResult",
                                action = AppModel.Result.Action.Copy(unitResult)
                            )
                        )
                    }
                    try {
                        currencyRowsProvider?.invoke(query)?.let { resultRows.addAll(it) }
                    } catch (_: Exception) {
                        // No currency rows on failure.
                    }
                    val dialNumber = if (DialMatch.showDialRow(query)) DialMatch.number(query) else null
                    if (dialNumber != null) {
                        val ctx = context ?: adapterContext
                        val callText = ctx?.getString(R.string.call_number, dialNumber) ?: "Call $dialNumber"
                        resultRows.add(
                            AppModel.Result(
                                text = callText,
                                action = AppModel.Result.Action.Dial(dialNumber)
                            )
                        )
                    }
                    // Runs on the Filter worker thread; a newer query supersedes this one.
                    val source = try { contactSourceProvider?.invoke() } catch (_: Exception) { null }
                    if (source != null) {
                        try {
                            ContactSearch.search(source, query).forEach {
                                resultRows.add(ContactSearch.toResult(it))
                            }
                        } catch (_: Exception) {
                            // Permission revoked or provider failure: no contact rows.
                        }
                    }
                    try {
                        calendarRowsProvider?.invoke(query)?.let { resultRows.addAll(it) }
                    } catch (_: Exception) {
                        // Permission revoked or provider failure: no event rows.
                    }
                }

                val rawItems = resultRows + scoredApps.map { it.first }
                val items = DrawerOrdering.forSearchPosition(rawItems, bottomSearch).toMutableList()
                val scores = scoredApps.map { it.second }
                filterResults.values = FilterPayload(items, scores)
                return filterResults
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                searchQuery = constraint?.toString().orEmpty()
                val payload = results?.values as? FilterPayload
                if (payload != null) {
                    appFilteredList = payload.items
                    appFilteredScores = payload.scores
                } else {
                    appFilteredList = (results?.values as? MutableList<AppModel>) ?: mutableListOf()
                    appFilteredScores = emptyList()
                }
                (recyclerView?.layoutManager as? LinearLayoutManager)?.let { lm ->
                    lm.stackFromEnd = bottomSearch
                }
                submitList(appFilteredList) {
                    if (appFilteredList.isNotEmpty()) {
                        val targetPosition = if (bottomSearch) appFilteredList.size - 1 else 0
                        recyclerView?.scrollToPosition(targetPosition)
                    }
                    autoLaunch()
                }
            }
        }
    }

    private fun autoLaunch() {
        try {
            val strongMatches = appFilteredScores.count { it >= AppMatcher.SCORE_SUBSTRING }
            val first = if (bottomSearch) appFilteredList.lastOrNull() else appFilteredList.firstOrNull()
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
            e.printStackTrace()
        }
    }

    fun setAppList(appsList: MutableList<AppModel>) {
        // Add empty app for bottom padding in recyclerview and assign to list
        appsList.add(
            AppModel.App(
                appLabel = "",
                key = null,
                appPackage = "",
                activityClassName = "",
                isNew = false,
                user = android.os.Process.myUserHandle()
            )
        )
        this.appsList = appsList
        this.appFilteredList = appsList
        this.appFilteredScores = emptyList()
        this.searchQuery = ""
        submitList(appsList)
    }

    fun launchFirstInList() {
        getTopApp()?.let { appClickListener(it) }
    }

    /** Best app match nearest the search box, skipping result rows and headers. */
    fun getTopApp(): AppModel? {
        val isApp = { it: AppModel -> it !is AppModel.PrivateSpaceHeader && it !is AppModel.Result && it !is AppModel.RecentHeader && it.appLabel.isNotBlank() }
        return if (bottomSearch) appFilteredList.lastOrNull(isApp) else appFilteredList.firstOrNull(isApp)
    }

    fun getTopItem(): AppModel? =
        if (bottomSearch) {
            appFilteredList.lastOrNull { it !is AppModel.RecentHeader && it.appLabel.isNotBlank() }
        } else {
            appFilteredList.firstOrNull { it !is AppModel.RecentHeader && it.appLabel.isNotBlank() }
        }

    fun getTargetPositionForOrderedIndex(orderedIndex: Int): Int {
        if (itemCount <= 0) return 0
        return if (bottomSearch) {
            // Full list is reversed, then the recents block sits after it.
            (appsList.size - 1 - orderedIndex).coerceIn(0, itemCount - 1)
        } else {
            // Recents block (header + recents) sits before the full list.
            val offset = appFilteredList.count { it is AppModel.RecentHeader || (it is AppModel.App && it.isRecent) }
            (orderedIndex + offset).coerceIn(0, itemCount - 1)
        }
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
        ) = with(binding) {
            renameLayout.visibility = View.GONE
            otherProfileIndicator.visibility = View.GONE
            appTitle.visibility = View.VISIBLE
            appTitle.text = result.text
            appTitle.gravity = appLabelGravity or Gravity.CENTER_VERTICAL
            appTitle.setOnClickListener { clickListener(result) }
            val onLong: View.OnLongClickListener? =
                if (result.action is AppModel.Result.Action.Contact && longClickListener != null) {
                    View.OnLongClickListener { longClickListener(result); true }
                } else null
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

            appTitle.setOnLongClickListener {
                if (appModel.appPackage.isNotEmpty()) {
                    root.hideKeyboard()
                    val sheet = AppActionsSheet(
                        context = root.context,
                        appModel = appModel,
                        isHidden = isHidden,
                        isDistracting = isDistracting,
                        flag = flag,
                        appLabelGravity = appLabelGravity,
                        onAddToHome = { appAddToHomeListener?.invoke(appModel) },
                        onRename = { showRenameEdit() },
                        onHide = { appHideListener(appModel, bindingAdapterPosition) },
                        onToggleDistracting = { appDistractingListener?.invoke(appModel) },
                        onAppInfo = { appInfoListener(appModel) },
                        onUninstall = { appDeleteListener(appModel) },
                    )
                    sheet.showRespectingStatusBar()
                }
                true
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
