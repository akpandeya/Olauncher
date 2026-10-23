package app.olauncher.ui

import android.util.Log
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import app.olauncher.helper.NewAppTipPolicy
import app.olauncher.helper.StatusBarFade
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentAppDrawerBinding
import app.olauncher.helper.ArchivedAppsHelper
import app.olauncher.helper.HomeSets
import app.olauncher.helper.DrawerOrdering
import app.olauncher.helper.DrawerScrollHelper
import app.olauncher.helper.LetterPos
import app.olauncher.helper.hideKeyboard
import app.olauncher.data.store.WiktionaryApi
import kotlinx.coroutines.Job
import app.olauncher.helper.showKeyboard
import app.olauncher.helper.showToast
import app.olauncher.helper.showMessageDialog

private const val TAG = "AppDrawerFragment"

class AppDrawerFragment : BaseFragment() {

    internal lateinit var prefs: Prefs
    internal lateinit var adapter: AppDrawerAdapter
    internal lateinit var linearLayoutManager: LinearLayoutManager
    internal var searchTextView: TextView? = null
    internal val LOOKUP_DEBOUNCE_MS = 600L
    internal val LOOKUP_DEBOUNCE_ON_ENTER_MS = 0L
    internal var lookupJob: Job? = null
    /** Text being looked up and its result; a null result means still loading. */
    @Volatile
    internal var lookupState: Pair<String, WiktionaryApi.LookupResult?>? = null
    internal val SUGGEST_MAX_ONLINE_ITEMS = 5
    internal var suggestJob: Job? = null
    /** Saved German words; loaded when the drawer opens and after each save. */
    @Volatile
    internal var savedWordTexts: List<String> = emptyList()
    /** Online suggestions for the items of the current `:sw` query (the key); null result per item is not stored. */
    @Volatile
    internal var suggestState: Pair<List<String>, Map<String, List<String>>>? = null
    internal var cachedIsCjkKeyboard: Boolean? = null

    internal var flag = Constants.FLAG_LAUNCH_APP
    internal var canRename = false
    internal var currentAppList: List<AppModel>? = null
    internal var currentPrivateSpaceApps: List<AppModel>? = null
    internal var currentPrivateSpaceLocked: Boolean = true
    internal var currentPrivateSpaceAvailable: Boolean = false
    internal var letterPosList: List<LetterPos> = emptyList()
    internal var stripLetters: List<Char> = emptyList()
    internal var keyboardVisible = false
    private var keyboardWasShown = false
    private var lastOrdered: List<AppModel> = emptyList()
    internal var lastSelectedLetter: Char? = null

    internal val viewModel: MainViewModel by activityViewModels()
    internal var _binding: FragmentAppDrawerBinding? = null
    internal val binding get() = _binding!!

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
        refreshSavedWords()
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
            // The strip gets squashed above the keyboard, so hide it while the keyboard is open.
            val imeVisible = imeBottom > 0
            if (imeVisible != keyboardVisible) {
                keyboardVisible = imeVisible
                if (imeVisible) keyboardWasShown = true
                if (_binding != null) {
                    updateLetterStripVisibility()
                    updateListInsetForStrip()
                    if (!imeVisible) maybeShowNewAppTip(lastOrdered)
                }
            }
            insets
        }
        binding.root.requestApplyInsets()
        StatusBarFade.attach(binding.statusBarFade)
    }

    private fun initViews() {
        if (flag == Constants.FLAG_HIDDEN_APPS)
            binding.search.queryHint = getString(R.string.hidden_apps)
        else if (flag in Constants.FLAG_SET_HOME_APP_1..Constants.FLAG_SET_GESTURE_APP_DATE_TAP
            || flag in Constants.FLAG_SET_SHORTCUT_1..Constants.FLAG_SET_SHORTCUT_12
            || HomeSets.fromFlag(flag) != null)
            binding.search.queryHint = "Please select an app"
        try {
            searchTextView = binding.search.findViewById(R.id.search_src_text)
            searchTextView?.gravity = prefs.appLabelAlignment
        } catch (e: Exception) {
            Log.w(TAG, "initViews failed", e)
        }
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

    private fun maybeShowNewAppTip(ordered: List<AppModel>) {
        lastOrdered = ordered
        val show = NewAppTipPolicy.shouldShow(
            alreadySeen = prefs.newAppTipSeen,
            isAppLaunchDrawer = flag == Constants.FLAG_LAUNCH_APP,
            hasNewApp = ordered.any { it.isNew && !it.isArchived },
            keyboardVisible = keyboardVisible,
            searchFocused = binding.search.hasFocus(),
            keyboardWasShown = keyboardWasShown,
        )
        if (!show) return
        prefs.newAppTipSeen = true
        requireContext().showMessageDialog(R.string.new_app_tip_title, R.string.new_app_tip_message, R.string.done) {}
    }

    internal fun updateCombinedAppList() {
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
        maybeShowNewAppTip(ordered)
    }

    private fun initClickListeners() {
        binding.appRename.setOnClickListener {
            val name = binding.search.query.toString().trim()
            if (name.isEmpty()) {
                requireContext().showToast(getString(R.string.type_a_new_app_name_first))
                binding.search.showKeyboard()
                return@setOnClickListener
            }

            HomeSets.fromFlag(flag)?.let { prefs.setHomeSlotName(it.set, it.slot, name) }
            findNavController().popBackStack()
        }
    }

    internal fun checkMessageAndExit() {
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
        lookupJob?.cancel()
        lookupJob = null
        lookupState = null
        searchTextView = null
        lastSelectedLetter = null
        letterPosList = emptyList()
        stripLetters = emptyList()
        _binding = null
    }

}
