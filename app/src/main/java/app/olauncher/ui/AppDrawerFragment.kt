package app.olauncher.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.text.Spannable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.animation.AnimationUtils
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Recycler
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.data.SearchEngine
import app.olauncher.data.SearchTarget
import app.olauncher.databinding.FragmentAppDrawerBinding
import app.olauncher.helper.AndroidContactSource
import app.olauncher.helper.ArchivedAppsHelper
import app.olauncher.helper.ContactSource
import app.olauncher.helper.HomeAppHelper
import app.olauncher.wellbeing.UsageSnapshotWorker
import app.olauncher.helper.DrawerOrdering
import app.olauncher.helper.DrawerScrollHelper
import app.olauncher.helper.RecentApps
import app.olauncher.helper.EnterPolicy
import app.olauncher.helper.Haptics
import app.olauncher.helper.LetterIndex
import app.olauncher.helper.LetterPos
import app.olauncher.helper.copyToClipboard
import app.olauncher.helper.deletePinnedShortcut
import app.olauncher.helper.hideKeyboard
import app.olauncher.helper.isEinkDisplay
import app.olauncher.helper.isSystemAnimationsDisabled
import app.olauncher.helper.isSystemApp
import app.olauncher.helper.openAppInfo
import app.olauncher.helper.openSearch
import app.olauncher.helper.openUrl
import app.olauncher.helper.showKeyboard
import app.olauncher.helper.showToast
import app.olauncher.helper.uninstall

class AppDrawerFragment : BaseFragment() {

    private lateinit var prefs: Prefs
    private lateinit var adapter: AppDrawerAdapter
    private lateinit var linearLayoutManager: LinearLayoutManager
    private var searchTextView: TextView? = null
    private var cachedIsCjkKeyboard: Boolean? = null

    private var flag = Constants.FLAG_LAUNCH_APP
    private var canRename = false
    private var currentAppList: List<AppModel>? = null
    private var currentPrivateSpaceApps: List<AppModel>? = null
    private var currentPrivateSpaceLocked: Boolean = true
    private var currentPrivateSpaceAvailable: Boolean = false
    private var letterPosList: List<LetterPos> = emptyList()
    private var lastSelectedLetter: Char? = null

    private val viewModel: MainViewModel by activityViewModels()
    private var _binding: FragmentAppDrawerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentAppDrawerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        arguments?.let {
            flag = it.getInt(Constants.Key.FLAG, Constants.FLAG_LAUNCH_APP)
            canRename = it.getBoolean(Constants.Key.RENAME, false)
        }

        setupDrawerLayout()
        setupLetterStripLayout()
        initViews()
        initSearch()
        initAdapter()
        initObservers()
        initClickListeners()
    }

    private fun setupDrawerLayout() {
        if (!prefs.drawerBottomSearch) return

        val rv = binding.recyclerView
        val searchContainer = binding.searchContainer

        val transition = binding.drawerLayout.layoutTransition
        binding.drawerLayout.layoutTransition = null
        binding.drawerLayout.removeAllViews()

        val rvParams = rv.layoutParams as LinearLayout.LayoutParams
        rvParams.height = 0
        rvParams.weight = 1f
        rvParams.topMargin = (24 * resources.displayMetrics.density).toInt()
        val originalBottomMargin = rvParams.bottomMargin
        rvParams.bottomMargin = 0
        rv.layoutParams = rvParams

        val searchParams = searchContainer.layoutParams as LinearLayout.LayoutParams
        searchParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        searchParams.weight = 0f
        searchParams.topMargin = 0
        searchParams.bottomMargin = originalBottomMargin
        searchContainer.layoutParams = searchParams

        binding.drawerLayout.addView(rv)
        binding.drawerLayout.addView(searchContainer)
        binding.drawerLayout.layoutTransition = transition

        // The activity is edge-to-edge, so the window is not resized for the keyboard.
        // Pad the drawer by the IME inset to keep the bottom search row above the keyboard.
        binding.root.setOnApplyWindowInsetsListener { root, insets ->
            val imeBottom = insets.getInsets(WindowInsets.Type.ime()).bottom
            val padding = DrawerScrollHelper.imeBottomPadding(
                drawerBottomSearch = true,
                imeBottom = imeBottom,
                searchBottomMargin = originalBottomMargin,
            )
            if (root.paddingBottom != padding)
                root.setPadding(root.paddingLeft, root.paddingTop, root.paddingRight, padding)
            insets
        }
        binding.root.requestApplyInsets()
    }

    private fun initViews() {
        if (flag == Constants.FLAG_HIDDEN_APPS)
            binding.search.queryHint = getString(R.string.hidden_apps)
        else if (flag in Constants.FLAG_SET_HOME_APP_1..Constants.FLAG_SET_WEATHER_APP
            || flag in Constants.FLAG_SET_SHORTCUT_1..Constants.FLAG_SET_SHORTCUT_12)
            binding.search.queryHint = "Please select an app"
        try {
            searchTextView = binding.search.findViewById(R.id.search_src_text)
            searchTextView?.gravity = prefs.appLabelAlignment
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initSearch() {
        binding.search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                fun openSearchTarget() {
                    when (val target = SearchEngine.resolve(prefs.searchEngine, prefs.searchEngineCustomUrl, query.orEmpty())) {
                        is SearchTarget.PhoneDefault -> {
                            try {
                                requireContext().openSearch(target.query)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        is SearchTarget.Url -> {
                            try {
                                requireContext().openUrl(target.url)
                            } catch (e: ActivityNotFoundException) {
                                e.printStackTrace()
                                try {
                                    requireContext().openSearch(query)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }

                if (query.isNullOrBlank() && adapter.itemCount > 0) {
                    // Blank query: keep existing behaviour (open first app in list).
                    adapter.launchFirstInList()
                    return true
                }

                val topItem = adapter.getTopItem()
                val topScore = adapter.getTopScore()
                when (val action = EnterPolicy.resolve(query, topItem, topScore)) {
                    is EnterPolicy.Action.ExecuteResult -> {
                        adapter.executeItem(action.result)
                    }
                    is EnterPolicy.Action.LaunchApp -> {
                        adapter.executeItem(action.app)
                    }
                    is EnterPolicy.Action.WebSearch -> {
                        openSearchTarget()
                    }
                }
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                try {
                    adapter.allowAutoLaunch = !isSearchComposing()
                    adapter.filter.filter(newText)
                    binding.appRename.visibility =
                        if (canRename && newText.isNotBlank()) View.VISIBLE else View.GONE
                    updateLetterStripVisibility(newText)
                    return true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return false
            }
        })
    }

    private fun isSearchComposing(): Boolean {
        val text = searchTextView?.text
        if (text !is Spannable) return false
        val start = BaseInputConnection.getComposingSpanStart(text)
        val end = BaseInputConnection.getComposingSpanEnd(text)
        if (start !in 0 until end) return false
        return isCjkKeyboard()
    }

    private fun isCjkKeyboard(): Boolean {
        cachedIsCjkKeyboard?.let { return it }
        val result = try {
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val subtype = imm.currentInputMethodSubtype
            val language = when {
                subtype == null -> ""
                subtype.languageTag.isNotEmpty() -> subtype.languageTag // e.g. "zh-CN", "ja-JP", "en-US"
                else -> subtype.locale // deprecated fallback, e.g. "zh_CN"
            }
            language.startsWith("zh") || language.startsWith("ja") || language.startsWith("ko")
        } catch (e: Exception) {
            false
        }
        cachedIsCjkKeyboard = result
        return result
    }

    private fun contactSourceOrNull(): ContactSource? {
        val ctx = context ?: return null
        if (!prefs.searchContacts) return null
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            prefs.searchContacts = false
            return null
        }
        return AndroidContactSource(ctx)
    }

    private fun dialContact(lookupUri: String) {
        val source = contactSourceOrNull()
        val number = try { source?.primaryNumber(lookupUri) } catch (_: Exception) { null }
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                if (!number.isNullOrBlank()) data = Uri.fromParts("tel", number, null)
            }
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        findNavController().popBackStack(R.id.mainFragment, false)
    }

    private fun initAdapter() {
        adapter = AppDrawerAdapter(
            flag,
            prefs.appLabelAlignment,
            appClickListener = { appModel ->
                when (appModel) {
                    is AppModel.Result -> {
                        when (val action = appModel.action) {
                            is AppModel.Result.Action.Copy -> {
                                requireContext().copyToClipboard(action.value)
                                requireContext().showToast(R.string.copied)
                                findNavController().popBackStack(R.id.mainFragment, false)
                            }

                            is AppModel.Result.Action.Contact -> {
                                try {
                                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(action.lookupUri)))
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                findNavController().popBackStack(R.id.mainFragment, false)
                            }

                            is AppModel.Result.Action.Dial -> {
                                try {
                                    val intent = Intent(Intent.ACTION_DIAL).apply {
                                        data = Uri.fromParts("tel", action.number, null)
                                    }
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                findNavController().popBackStack(R.id.mainFragment, false)
                            }
                        }
                    }

                    is AppModel.PrivateSpaceHeader, is AppModel.RecentHeader -> {}
                    else -> {
                        if (flag == Constants.FLAG_LAUNCH_APP || flag == Constants.FLAG_HIDDEN_APPS)
                            Haptics.tick(binding.root)
                        viewModel.selectedApp(appModel, flag)
                        if (flag == Constants.FLAG_LAUNCH_APP || flag == Constants.FLAG_HIDDEN_APPS)
                            findNavController().popBackStack(R.id.mainFragment, false)
                        else
                            findNavController().popBackStack()
                    }
                }
            },
            appInfoListener = {
                openAppInfo(
                    requireContext(),
                    it.user,
                    it.appPackage
                )
                findNavController().popBackStack(R.id.mainFragment, false)
            },
            appDeleteListener = { appModel ->
                when (appModel) {
                    is AppModel.Result, is AppModel.PrivateSpaceHeader, is AppModel.RecentHeader -> {}
                    is AppModel.PinnedShortcut ->
                        requireContext().deletePinnedShortcut(
                            packageName = appModel.appPackage,
                            shortcutIdToDelete = appModel.shortcutId,
                            user = appModel.user,
                        )

                    is AppModel.App -> {
                        if (appModel.user != Process.myUserHandle()) {
                            openAppInfo(requireContext(), appModel.user, appModel.appPackage)
                        } else if (requireContext().isSystemApp(appModel.appPackage, appModel.user)) {
                            requireContext().showToast(getString(R.string.system_app_cannot_delete))
                            openAppInfo(requireContext(), appModel.user, appModel.appPackage)
                        } else {
                            requireContext().uninstall(appModel.appPackage)
                        }
                    }
                }
                viewModel.getAppList()
            },
            appHideListener = { appModel, position ->
                if (appModel.isArchived) {
                    return@AppDrawerAdapter
                }
                if (appModel is AppModel.PinnedShortcut) {
                    requireContext().showToast("Hiding pinned shortcuts is not supported")
                    return@AppDrawerAdapter
                }
                if (appModel is AppModel.Result || appModel is AppModel.RecentHeader) {
                    return@AppDrawerAdapter
                }
                adapter.appFilteredList.removeAt(position)
                adapter.notifyItemRemoved(position)
                adapter.appsList.removeAll { it.appPackage == appModel.appPackage && it.user == appModel.user }

                val newSet = mutableSetOf<String>()
                newSet.addAll(prefs.hiddenApps)
                if (flag == Constants.FLAG_HIDDEN_APPS)
                    newSet.remove(appModel.appPackage + "|" + appModel.user.toString())
                else
                    newSet.add(appModel.appPackage + "|" + appModel.user.toString())

                prefs.hiddenApps = newSet
                if (newSet.isEmpty())
                    findNavController().popBackStack()
                if (prefs.firstHide) {
                    binding.search.hideKeyboard()
                    prefs.firstHide = false
                    viewModel.showDialog.postValue(Constants.Dialog.HIDDEN)
                    findNavController().navigate(R.id.action_appListFragment_to_settingsFragment2)
                }
                viewModel.getAppList()
                viewModel.getHiddenApps()
            },
            appRenameListener = { appModel, renameLabel ->
                if (appModel.isArchived) {
                    return@AppDrawerAdapter
                }
                val identifier = when (appModel) {
                    is AppModel.PinnedShortcut -> appModel.identity
                    is AppModel.App -> appModel.appPackage
                    else -> return@AppDrawerAdapter
                }
                prefs.setAppRenameLabel(identifier, renameLabel)
                viewModel.getAppList()
            },
            privateSpaceToggleListener = {
                viewModel.togglePrivateSpaceLock()
            },
            privateSpaceSettingsListener = {
                viewModel.openPrivateSpaceSettings()
                findNavController().popBackStack(R.id.mainFragment, false)
            },
            context = requireContext(),
            bottomSearch = prefs.drawerBottomSearch,
            drawerRecents = prefs.drawerRecents,
            contactSourceProvider = { contactSourceOrNull() },
            contactLongClickListener = { result ->
                val action = result.action as? AppModel.Result.Action.Contact
                if (action != null) dialContact(action.lookupUri)
            },
            recentAppsProvider = { RecentApps.decode(prefs.recentApps) },
            hiddenAppsProvider = { prefs.hiddenApps },
            appAddToHomeListener = { appModel ->
                when (HomeAppHelper.addToHome(prefs, appModel)) {
                    is HomeAppHelper.AddResult.Added -> {
                        requireContext().showToast(R.string.added_to_home)
                        viewModel.refreshHome(true)
                    }
                    is HomeAppHelper.AddResult.AlreadyOnHome ->
                        requireContext().showToast(R.string.already_on_home)
                    is HomeAppHelper.AddResult.HomeFull ->
                        requireContext().showToast(R.string.home_screen_full)
                }
            },
            appDistractingListener = { appModel ->
                val pkg = appModel.appPackage
                if (pkg.isNotBlank()) {
                    val current = prefs.distractingApps.toMutableSet()
                    if (current.contains(pkg)) current.remove(pkg) else current.add(pkg)
                    prefs.distractingApps = current
                    UsageSnapshotWorker.syncWithDistractingApps(requireContext().applicationContext, current)
                }
            },
            distractingAppsProvider = { prefs.distractingApps },
        )

        linearLayoutManager = object : LinearLayoutManager(requireContext()) {
            override fun scrollVerticallyBy(
                dx: Int,
                recycler: Recycler,
                state: RecyclerView.State,
            ): Int {
                val scrollRange = super.scrollVerticallyBy(dx, recycler, state)
                val overScroll = dx - scrollRange
                if (overScroll < -10 && binding.recyclerView.scrollState == RecyclerView.SCROLL_STATE_DRAGGING)
                    checkMessageAndExit()
                return scrollRange
            }
        }

        binding.recyclerView.layoutManager = linearLayoutManager
        binding.recyclerView.adapter = adapter
        binding.recyclerView.addOnScrollListener(getRecyclerViewOnScrollListener())
        binding.recyclerView.itemAnimator = null
        if (requireContext().isEinkDisplay())
            binding.recyclerView.overScrollMode = View.OVER_SCROLL_NEVER
        else if (requireContext().isSystemAnimationsDisabled().not())
            binding.recyclerView.layoutAnimation =
                AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_anim_from_bottom)
    }

    private fun initObservers() {
        viewModel.firstOpen.observe(viewLifecycleOwner) {
        }
        if (flag == Constants.FLAG_HIDDEN_APPS) {
            viewModel.hiddenApps.observe(viewLifecycleOwner) {
                it?.let { apps ->
                    val ordered = DrawerOrdering.order(apps, prefs.drawerNewFirst)
                    adapter.setAppList(ordered.toMutableList())
                    populateLetterStrip(ordered)
                }
            }
        } else {
            viewModel.appList.observe(viewLifecycleOwner) {
                currentAppList = it
                updateCombinedAppList()
            }
            if (flag == Constants.FLAG_LAUNCH_APP) {
                viewModel.privateSpaceAvailable.observe(viewLifecycleOwner) {
                    currentPrivateSpaceAvailable = it
                    updateCombinedAppList()
                }
                viewModel.privateSpaceLocked.observe(viewLifecycleOwner) {
                    currentPrivateSpaceLocked = it
                    updateCombinedAppList()
                }
                viewModel.privateSpaceApps.observe(viewLifecycleOwner) {
                    currentPrivateSpaceApps = it
                    updateCombinedAppList()
                }
            }
        }
    }

    private fun updateCombinedAppList() {
        val apps = currentAppList ?: return
        val combined = apps.toMutableList()

        if (flag == Constants.FLAG_LAUNCH_APP && currentPrivateSpaceAvailable) {
            combined.add(AppModel.PrivateSpaceHeader(isLocked = currentPrivateSpaceLocked))
            if (!currentPrivateSpaceLocked) {
                currentPrivateSpaceApps?.let { combined.addAll(it) }
            }
        }

        val filtered = ArchivedAppsHelper.filterForPicker(combined, flag)
        val ordered = DrawerOrdering.order(filtered, prefs.drawerNewFirst)
        adapter.setAppList(ordered.toMutableList())
        adapter.filter.filter(binding.search.query)
        populateLetterStrip(ordered)
    }

    private fun initClickListeners() {
        binding.appRename.setOnClickListener {
            val name = binding.search.query.toString().trim()
            if (name.isEmpty()) {
                requireContext().showToast(getString(R.string.type_a_new_app_name_first))
                binding.search.showKeyboard()
                return@setOnClickListener
            }

            when (flag) {
                Constants.FLAG_SET_HOME_APP_1 -> prefs.appName1 = name
                Constants.FLAG_SET_HOME_APP_2 -> prefs.appName2 = name
                Constants.FLAG_SET_HOME_APP_3 -> prefs.appName3 = name
                Constants.FLAG_SET_HOME_APP_4 -> prefs.appName4 = name
                Constants.FLAG_SET_HOME_APP_5 -> prefs.appName5 = name
                Constants.FLAG_SET_HOME_APP_6 -> prefs.appName6 = name
                Constants.FLAG_SET_HOME_APP_7 -> prefs.appName7 = name
                Constants.FLAG_SET_HOME_APP_8 -> prefs.appName8 = name
            }
            findNavController().popBackStack()
        }
    }

    private fun getRecyclerViewOnScrollListener(): RecyclerView.OnScrollListener {
        return object : RecyclerView.OnScrollListener() {

            var atSearch = false

            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                val isAtSearch = DrawerScrollHelper.isAtSearch(
                    prefs.drawerBottomSearch,
                    canScrollUp = recyclerView.canScrollVertically(-1),
                    canScrollDown = recyclerView.canScrollVertically(1)
                )
                val isAtFarEnd = DrawerScrollHelper.isAtFarEnd(
                    prefs.drawerBottomSearch,
                    canScrollUp = recyclerView.canScrollVertically(-1),
                    canScrollDown = recyclerView.canScrollVertically(1)
                )

                when (newState) {
                    RecyclerView.SCROLL_STATE_DRAGGING -> {
                        atSearch = isAtSearch
                        if (atSearch)
                            binding.search.hideKeyboard()
                    }

                    RecyclerView.SCROLL_STATE_IDLE -> {
                        if (isAtFarEnd)
                            binding.search.hideKeyboard()
                        else if (isAtSearch)
                            if (!atSearch && isRemoving.not())
                                binding.search.showKeyboard(prefs.autoShowKeyboard)
                    }
                }
            }
        }
    }

    private fun checkMessageAndExit() {
        findNavController().popBackStack()
        if (flag == Constants.FLAG_LAUNCH_APP)
            viewModel.checkForMessages.call()
    }

    override fun onStart() {
        super.onStart()
        cachedIsCjkKeyboard = null
        adapter.bottomSearch = prefs.drawerBottomSearch
        adapter.drawerRecents = prefs.drawerRecents
        setupLetterStripLayout()
        if (currentAppList != null) updateCombinedAppList()
        updateLetterStripVisibility()
        binding.search.showKeyboard(prefs.autoShowKeyboard)
    }

    override fun onStop() {
        binding.search.hideKeyboard()
        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchTextView = null
        lastSelectedLetter = null
        letterPosList = emptyList()
        _binding = null
    }

    private fun setupLetterStripLayout() {
        val isStart = prefs.shortcutSideStart
        val containerParams = binding.letterStripContainer.layoutParams as FrameLayout.LayoutParams
        containerParams.gravity = Gravity.BOTTOM or (if (isStart) Gravity.START else Gravity.END)
        binding.letterStripContainer.layoutParams = containerParams
        // With the search row at the bottom, keep the strip above it.
        val search = binding.searchContainer
        val clearSearch = {
            val extra = if (prefs.drawerBottomSearch && search.height > 0) {
                val lp = search.layoutParams as ViewGroup.MarginLayoutParams
                search.height + lp.bottomMargin
            } else 0
            val lp = binding.letterStripContainer.layoutParams as FrameLayout.LayoutParams
            if (lp.bottomMargin != extra) {
                lp.bottomMargin = extra
                binding.letterStripContainer.layoutParams = lp
            }
        }
        clearSearch()
        if (prefs.drawerBottomSearch) search.post { _binding?.let { clearSearch() } }

        val zone = binding.letterStripZone
        zone.removeAllViews()
        if (isStart) {
            zone.addView(binding.letterStrip)
            zone.addView(binding.letterPreview)
        } else {
            zone.addView(binding.letterPreview)
            zone.addView(binding.letterStrip)
        }
    }

    private fun updateLetterStripVisibility(query: String? = binding.search.query?.toString()) {
        val show = prefs.drawerLetterStrip && query.isNullOrBlank() && letterPosList.isNotEmpty()
        binding.letterStripContainer.isVisible = show
        if (!show) {
            binding.letterPreview.visibility = View.INVISIBLE
        }
    }

    private fun populateLetterStrip(ordered: List<AppModel>) {
        if (!prefs.drawerLetterStrip) {
            binding.letterStripContainer.isVisible = false
            updateListInsetForStrip()
            return
        }

        // Keep positions in the full adapter list: skip headers/results but remember their real index.
        val realIndices = ordered.indices.filter {
            ordered[it] !is AppModel.PrivateSpaceHeader && ordered[it] !is AppModel.Result && ordered[it] !is AppModel.RecentHeader
        }
        letterPosList = LetterIndex.build(realIndices.map { ordered[it].appLabel })
            .map { LetterPos(it.letter, realIndices[it.index]) }

        binding.letterStrip.removeAllViews()
        for (pos in letterPosList) {
            val tv = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    0,
                    1f,
                )
                gravity = Gravity.CENTER
                includeFontPadding = false
                setPadding(0, 0, 0, 0)
                TextViewCompat.setTextAppearance(this, R.style.TextSmall)
                text = pos.letter.toString()
                isClickable = false
                isFocusable = false
            }
            binding.letterStrip.addView(tv)
        }

        setupLetterStripTouchListener()
        updateLetterStripVisibility()
        updateListInsetForStrip()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupLetterStripTouchListener() {
        binding.letterStrip.setOnTouchListener { view, event ->
            if (letterPosList.isEmpty()) return@setOnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    val letterIndex = LetterIndex.letterAt(event.y, view.height, letterPosList.size)
                    if (letterIndex in letterPosList.indices) {
                        val pos = letterPosList[letterIndex]
                        if (pos.letter != lastSelectedLetter) {
                            lastSelectedLetter = pos.letter
                            Haptics.tick(binding.root)
                            binding.letterPreview.text = pos.letter.toString()
                            binding.letterPreview.visibility = View.VISIBLE
                            scrollToLetter(pos)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    lastSelectedLetter = null
                    binding.letterPreview.visibility = View.INVISIBLE
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    true
                }
                else -> false
            }
        }
    }

    private fun scrollToLetter(pos: LetterPos) {
        val totalCount = adapter.itemCount
        if (totalCount <= 0) return

        val targetPosition = adapter.getTargetPositionForOrderedIndex(pos.index)

        val rv = binding.recyclerView
        val offset = LetterIndex.scrollOffsetPx(
            bottomSearch = prefs.drawerBottomSearch,
            listHeightPx = rv.height - rv.paddingTop - rv.paddingBottom,
            rowHeightPx = rv.getChildAt(0)?.height ?: 0,
        )
        linearLayoutManager.scrollToPositionWithOffset(targetPosition, offset)
    }

    /** Pad the list on the strip side so app names never sit under the strip. */
    private fun updateListInsetForStrip() {
        val rv = binding.recyclerView
        val lp = rv.layoutParams as ViewGroup.MarginLayoutParams
        val stripOn = prefs.drawerLetterStrip && letterPosList.isNotEmpty()
        val isStart = prefs.shortcutSideStart
        val inset = if (stripOn) {
            LetterIndex.listInsetPx(
                stripWidthPx = binding.letterStrip.minimumWidth,
                rowPaddingPx = resources.getDimensionPixelSize(R.dimen.drawer_row_padding_horizontal),
                listMarginPx = if (isStart) lp.marginStart else lp.marginEnd,
            )
        } else 0
        rv.setPaddingRelative(
            if (isStart) inset else 0,
            rv.paddingTop,
            if (isStart) 0 else inset,
            rv.paddingBottom,
        )
    }
}
