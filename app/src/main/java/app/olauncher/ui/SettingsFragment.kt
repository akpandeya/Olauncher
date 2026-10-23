package app.olauncher.ui

import app.olauncher.helper.StatusBarFade
import app.olauncher.helper.OlDialog
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.work.ExistingWorkPolicy
import app.olauncher.BuildConfig
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.FragmentSettingsBinding
import app.olauncher.helper.RotationInterval
import app.olauncher.helper.isAccessServiceEnabled
import app.olauncher.helper.isOlauncherDefault
import app.olauncher.helper.showToast
import app.olauncher.quiet.ReleaseWorker
import app.olauncher.wellbeing.WindDownWorker
import app.olauncher.ui.settings.SettingsRow
import app.olauncher.ui.settings.SettingsRowHolder
import app.olauncher.ui.settings.SettingsRowSpec
import app.olauncher.ui.settings.SettingsRowsAdapter
import app.olauncher.ui.settings.SettingsRowsHost
import app.olauncher.ui.settings.SettingsCatalog
import app.olauncher.ui.settings.SettingsViews
import app.olauncher.ui.settings.bindRow
import app.olauncher.ui.settings.setupRowsList
import app.olauncher.ui.settings.unbindRow
import app.olauncher.ui.settings.SettingsRowAvailability
import app.olauncher.ui.settings.SettingsSection
import app.olauncher.ui.settings.SettingsSections
import app.olauncher.ui.settings.applyTextSizeScale
import app.olauncher.ui.settings.askContextLineNotificationAccess
import app.olauncher.ui.settings.dispatchClick
import app.olauncher.ui.settings.enableAutoBackup
import app.olauncher.ui.settings.enableUseMyLocation
import app.olauncher.ui.settings.exportBackup
import app.olauncher.ui.settings.handleAddedPhotos
import app.olauncher.ui.settings.handleImportBackup
import app.olauncher.ui.settings.handlePickedPhotos
import app.olauncher.ui.settings.initClickListeners
import app.olauncher.ui.settings.initSearch
import app.olauncher.ui.settings.isNotificationAccessGranted
import app.olauncher.ui.settings.onSearchQueryChanged
import app.olauncher.ui.settings.populateAlignment
import app.olauncher.ui.settings.populateCommitLock
import app.olauncher.ui.settings.populateHealthConnect
import app.olauncher.ui.settings.populateHoldNotifications
import app.olauncher.ui.settings.populateLockSettings
import app.olauncher.ui.settings.populatePauseAllowance
import app.olauncher.ui.settings.populateQuietReleaseTimes
import app.olauncher.ui.settings.populateScreenTimeCue
import app.olauncher.ui.settings.populateScreenTimeOnOff
import app.olauncher.ui.settings.populateSearchContacts
import app.olauncher.ui.settings.populateShortcutIcons
import app.olauncher.ui.settings.populateSwipeApps
import app.olauncher.ui.settings.populateTodayCards
import app.olauncher.ui.settings.populateWindDown
import app.olauncher.ui.settings.renderSection
import app.olauncher.ui.settings.renderSectionList
import app.olauncher.ui.settings.setupSearchInsets
import app.olauncher.ui.settings.showAccessibilityDialog
import app.olauncher.ui.settings.showAppThemeMenu
import app.olauncher.ui.settings.showPhotoWallpaperMenu
import app.olauncher.ui.settings.showToast
import app.olauncher.ui.settings.showWeatherMenu
import app.olauncher.ui.settings.tickOnChange
import app.olauncher.ui.settings.toggleSwipeLeft
import app.olauncher.ui.settings.toggleSwipeRight
import app.olauncher.ui.today.cards.NextUpCard
import app.olauncher.wellbeing.HealthRepository
import app.olauncher.wellbeing.NotificationPermission

class SettingsFragment : BaseFragment(), View.OnClickListener, View.OnLongClickListener, SettingsRowsHost {

    internal lateinit var prefs: Prefs
    internal lateinit var viewModel: MainViewModel

    internal var _binding: SettingsViews? = null
    internal val binding get() = _binding!!
    internal var rowsAdapter: SettingsRowsAdapter? = null
    internal var dialog: AlertDialog? = null

    /** Calendar id and name pairs for the profile schedule screens; read once, off the main thread. */
    internal var profileCalendars: List<Pair<Long, String>>? = null

    internal var currentSectionKey: String? = null
    internal var initialTargetApplied = false

    internal val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (_binding != null && !binding.settingsSearch.text.isNullOrEmpty()) {
                binding.settingsSearch.setText("")
            } else {
                showSectionList()
            }
        }
    }

    internal val pickMultipleMedia = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MediaStore.getPickImagesMaxLimit())
    ) { uris ->
        if (uris.isNotEmpty()) {
            handlePickedPhotos(uris)
        }
    }

    internal val pickMultipleMediaForAdd = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MediaStore.getPickImagesMaxLimit())
    ) { uris ->
        if (uris.isNotEmpty()) {
            handleAddedPhotos(uris)
        }
    }

    internal val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            exportBackup(uri)
        }
    }

    internal val autoBackupFolderLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            enableAutoBackup(uri)
        }
    }

    internal val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            handleImportBackup(uri)
        }
    }

    internal val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            enableUseMyLocation()
        } else {
            requireContext().showToast(R.string.location_off_for_thumbline)
        }
    }

    internal val healthConnectPermissionLauncher = registerForActivityResult(
        HealthRepository.permissionContract()
    ) { granted ->
        // Partial grants are fine; no grant at all leaves the switch off.
        val anyGranted = granted.isNotEmpty()
        prefs.healthConnectEnabled = anyGranted
        if (!anyGranted) requireContext().showToast(R.string.health_connect_denied)
        if (_binding != null) populateHealthConnect()
    }

    internal val contactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        prefs.searchContacts = isGranted
        if (_binding != null) populateSearchContacts()
    }

    internal var pendingCalendarCardIndex: Int? = null
    internal var todayCardsDialog: OlDialog? = null
    internal var todayCardsCheckedState: BooleanArray? = null

    internal val calendarPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val idx = pendingCalendarCardIndex
        pendingCalendarCardIndex = null
        val alert = todayCardsDialog
        if (isGranted) {
            if (idx != null && alert != null && alert.isShowing) {
                alert.setItemChecked(idx, true)
            } else {
                prefs.setTodayCardEnabled(NextUpCard.KEY, true)
                populateTodayCards()
            }
        } else {
            if (idx != null && alert != null && alert.isShowing) {
                alert.setItemChecked(idx, false)
            }
            requireContext().showToast(R.string.calendar_off_for_thumbline)
        }
    }

    internal val contextLineCalendarLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Granted or not, the line stays on; ask for notification access next.
        if (_binding != null) askContextLineNotificationAccess()
    }

    // A denial leaves the switch on and changes nothing else.
    internal val homeNextEventCalendarLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationPermission.register(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = SettingsViews(FragmentSettingsBinding.inflate(inflater, container, false))
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")
        viewModel.isOlauncherDefault()

        setupRowsList()
        // Idempotent: also fixes a missed start/end and reschedules after a time or toggle change.
        WindDownWorker.syncAsync(requireContext())
        populateFooter()
        initClickListeners()
        initSearch()
        StatusBarFade.attach(binding.statusBarFade)
        setupSearchInsets()
        initObservers()

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)

        val restored = savedInstanceState != null || initialTargetApplied
        val restoredKey = if (savedInstanceState != null) {
            savedInstanceState.getString(KEY_SAVED_SECTION)
        } else {
            currentSectionKey
        }
        val targetSection = SettingsSections.initialSection(
            restored = restored,
            restoredSectionKey = restoredKey,
            explicitTarget = { resolveTargetSection() },
        )
        initialTargetApplied = true

        if (targetSection != null) {
            showSection(targetSection)
        } else {
            showSectionList()
        }
    }

    override fun onResume() {
        super.onResume()
        populateScreenTimeOnOff()
        populateScreenTimeCue()
        populatePauseAllowance()
        populateCommitLock()
        populateWindDown()
        populateHoldNotifications()
        populateHealthConnect()
        populateQuietReleaseTimes()
        if (prefs.quietEnabled && isNotificationAccessGranted()) {
            ReleaseWorker.schedule(requireContext(), ExistingWorkPolicy.KEEP)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        currentSectionKey?.let { outState.putString(KEY_SAVED_SECTION, it) }
    }

    internal fun isRowAvailable(viewId: Int): Boolean = SettingsRowAvailability.isAvailable(
        viewId = viewId,
        shortcutsOn = prefs.homeShortcuts,
        germanOn = prefs.germanEnabled,
    )

    /** Searchable rows that are not switched off by a parent setting, labelled as the user sees them. */
    fun collectRows(): List<SettingsRow> = SettingsCatalog.rows
        .filter { it.searchable && isRowAvailable(it.primaryId) }
        .map { SettingsRow(label = getString(it.titleRes), sectionKey = it.sectionKey, viewId = it.primaryId) }

    override fun isRowAvailable(spec: SettingsRowSpec): Boolean = isRowAvailable(spec.primaryId)

    override fun onRowBound(spec: SettingsRowSpec, holder: SettingsRowHolder) = bindRow(spec, holder)

    override fun onRowUnbound(spec: SettingsRowSpec, holder: SettingsRowHolder) = unbindRow(spec, holder)

    fun showSection(section: SettingsSection) = renderSection(section)

    fun showSectionList() = renderSectionList()

    /** Only explicit fragment arguments ([ARG_SECTION] key or [ARG_ROW] view id) open a section. */
    internal fun resolveTargetSection(): SettingsSection? {
        val args = arguments ?: return null
        return SettingsSections.resolveTarget(
            sectionKey = args.getString(ARG_SECTION),
            rowViewId = args.getInt(ARG_ROW, 0),
        )
    }

    override fun onClick(view: View) {
        dispatchClick(view)
    }

    override fun onLongClick(view: View): Boolean {
        when (view.id) {
            R.id.photoWallpaper, R.id.photoWallpaperTitle, R.id.photoWallpaperLayout -> {
                showPhotoWallpaperMenu(binding.photoWallpaper)
            }
            R.id.alignment -> {
                prefs.appLabelAlignment = prefs.homeAlignment
                findNavController().navigate(R.id.action_settingsFragment_to_appListFragment)
                requireContext().showToast(getString(R.string.alignment_changed))
            }

            R.id.weather, R.id.weatherTitle, R.id.weatherLayout -> {
                showWeatherMenu(binding.weather)
            }
            R.id.appThemeText -> showAppThemeMenu(view, showSystem = true)
            R.id.swipeLeftApp -> tickOnChange(view) { toggleSwipeLeft() }
            R.id.swipeRightApp, R.id.swipeRightLayout -> tickOnChange(view) { toggleSwipeRight() }
            R.id.toggleLock -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        return true
    }

    internal fun initObservers() {
        prefs.firstSettingsOpen = false
        viewModel.isOlauncherDefault.observe(viewLifecycleOwner) {
            if (it) {
                binding.setLauncher.text = getString(R.string.change_default_launcher)
                prefs.toShowHintCounter += 1
            }
        }
        viewModel.homeAppAlignment.observe(viewLifecycleOwner) {
            populateAlignment()
            populateShortcutIcons()
        }
        viewModel.updateSwipeApps.observe(viewLifecycleOwner) {
            populateSwipeApps()
        }
    }

    // Popup menus

    internal fun showHiddenApps() {
        if (prefs.hiddenApps.isEmpty()) {
            requireContext().showToast(getString(R.string.no_hidden_apps))
            return
        }
        viewModel.getHiddenApps()
        findNavController().navigate(
            R.id.action_settingsFragment_to_appListFragment,
            bundleOf(Constants.Key.FLAG to Constants.FLAG_HIDDEN_APPS)
        )
    }

    internal fun openAccessibilityService() {
        // prefs.lockModeOn = true
        populateLockSettings()
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    internal fun toggleLockMode() {
        if (!prefs.lockModeOn && !isAccessServiceEnabled(requireContext())) {
            showAccessibilityDialog()
            return
        }
        prefs.lockModeOn = !prefs.lockModeOn
        populateLockSettings()
    }

    internal fun updateHomeAppsNum(num: Int) {
        binding.homeAppsNum.text = num.toString()
        prefs.homeAppsNum = num
        viewModel.refreshHome(true)
    }

    internal var pendingTextSizeScale: Float = -1f

    internal var pendingShortcutOffsetStep: Int = -1

    internal fun populateFooter() {
        binding.footer.paintFlags = binding.footer.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
        binding.footer.text = getString(R.string.settings_footer, BuildConfig.VERSION_NAME, "Olauncher")
    }

    override fun onDestroyView() {
        // Dismissing the text size dialog applies any pending scale via its dismiss listener
        todayCardsDialog = null
        todayCardsCheckedState = null
        pendingCalendarCardIndex = null
        dialog?.dismiss()
        dialog = null
        applyTextSizeScale()
        _binding?.settingsRows?.adapter = null
        rowsAdapter = null
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        viewModel.checkForMessages.call()
        super.onDestroy()
    }

    companion object {
        const val KEY_SAVED_SECTION = "saved_section_key"
        const val ARG_SECTION = "section"
        const val ARG_ROW = "row"

        @StringRes
        fun intervalToStringRes(interval: RotationInterval): Int = when (interval) {
            RotationInterval.ON_OPEN -> R.string.wallpaper_frequency_on_open
            RotationInterval.MINUTES_15 -> R.string.wallpaper_frequency_15_min
            RotationInterval.MINUTES_30 -> R.string.wallpaper_frequency_30_min
            RotationInterval.HOURS_1 -> R.string.wallpaper_frequency_1_hour
            RotationInterval.HOURS_3 -> R.string.wallpaper_frequency_3_hours
            RotationInterval.HOURS_6 -> R.string.wallpaper_frequency_6_hours
            RotationInterval.HOURS_12 -> R.string.wallpaper_frequency_12_hours
            RotationInterval.DAILY -> R.string.wallpaper_frequency_daily
            RotationInterval.WEEKLY -> R.string.wallpaper_frequency_weekly
            RotationInterval.NEVER -> R.string.wallpaper_frequency_never
        }
    }
}
