package app.olauncher.ui

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.graphics.Typeface
import android.provider.MediaStore
import android.provider.Settings
import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.format.DateFormat
import android.text.style.AlignmentSpan
import android.util.TypedValue
import androidx.annotation.FontRes
import androidx.core.content.res.ResourcesCompat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import android.view.WindowManager
import android.Manifest
import android.content.pm.PackageManager
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import android.view.WindowInsets
import android.widget.TextView
import app.olauncher.helper.DrawerScrollHelper
import app.olauncher.helper.HandSide
import app.olauncher.ui.settings.SettingsRow
import app.olauncher.ui.settings.SettingsRowAvailability
import app.olauncher.ui.settings.SettingsSearch
import app.olauncher.ui.settings.SettingsSection
import app.olauncher.ui.settings.SettingsSections
import app.olauncher.ui.today.cards.NextUpCard
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.bundleOf
import app.olauncher.glance.Place
import app.olauncher.glance.WeatherApi
import app.olauncher.glance.WeatherLocation
import app.olauncher.glance.WeatherWorker
import java.util.Locale
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import app.olauncher.wellbeing.NudgeFormatting
import app.olauncher.wellbeing.NudgeScheduler
import app.olauncher.wellbeing.Nudges
import app.olauncher.wellbeing.NotificationPermission
import androidx.work.ExistingWorkPolicy
import app.olauncher.quiet.QuietRelease
import app.olauncher.quiet.ReleaseWorker
import app.olauncher.data.store.Backup
import app.olauncher.data.store.BackupData
import app.olauncher.BuildConfig
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.Accent
import android.util.Log
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.FontFamilyResources
import app.olauncher.data.Prefs
import app.olauncher.data.SearchEngine
import app.olauncher.data.SlotCount
import app.olauncher.data.ShortcutOffset
import app.olauncher.data.ShortcutIconStyle
import app.olauncher.data.ShortcutSize
import app.olauncher.ui.today.TodayCards
import app.olauncher.databinding.DialogFontFamilyBinding
import app.olauncher.databinding.DialogTextSizeBinding
import app.olauncher.databinding.FragmentSettingsBinding
import app.olauncher.helper.PhotoSet
import app.olauncher.helper.PhotoWallpaper
import app.olauncher.helper.RotationInterval
import app.olauncher.helper.WallpaperRotateWorker
import app.olauncher.helper.appUsagePermissionGranted
import app.olauncher.helper.createDialog
import app.olauncher.helper.showMessageDialog
import app.olauncher.helper.getAppsList
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.hideStatusBar
import app.olauncher.helper.isAccessServiceEnabled
import app.olauncher.helper.isEinkDisplay
import app.olauncher.helper.isOlauncherDefault
import app.olauncher.helper.isTablet
import app.olauncher.helper.openAppInfo
import app.olauncher.helper.openUrl
import app.olauncher.helper.setPlainWallpaperByTheme
import app.olauncher.helper.WordCaptureActivity
import app.olauncher.helper.OlDialog
import app.olauncher.helper.showMessageDialog
import app.olauncher.helper.showPopupMenu
import app.olauncher.helper.showStatusBar
import app.olauncher.helper.showToast
import app.olauncher.helper.showWaitDialog
import app.olauncher.wellbeing.CommitLock
import app.olauncher.wellbeing.DistractingApps
import app.olauncher.wellbeing.FrictionChange
import app.olauncher.wellbeing.FrictionSettings
import app.olauncher.wellbeing.PausePolicy
import app.olauncher.wellbeing.restoreNeedsWait
import app.olauncher.wellbeing.UsageSnapshotWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

class SettingsFragment : BaseFragment(), View.OnClickListener, View.OnLongClickListener {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private var dialog: AlertDialog? = null

    private var currentSectionKey: String? = null
    private var initialTargetApplied = false

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (_binding != null && !binding.settingsSearch.text.isNullOrEmpty()) {
                binding.settingsSearch.setText("")
            } else {
                showSectionList()
            }
        }
    }

    private val pickMultipleMedia = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MediaStore.getPickImagesMaxLimit())
    ) { uris ->
        if (uris.isNotEmpty()) {
            handlePickedPhotos(uris)
        }
    }

    private val pickMultipleMediaForAdd = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MediaStore.getPickImagesMaxLimit())
    ) { uris ->
        if (uris.isNotEmpty()) {
            handleAddedPhotos(uris)
        }
    }

    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            exportBackup(uri)
        }
    }

    private val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            handleImportBackup(uri)
        }
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            enableUseMyLocation()
        } else {
            requireContext().showToast(R.string.location_off_for_thumbline)
        }
    }

    private val contactsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        prefs.searchContacts = isGranted
        if (_binding != null) populateSearchContacts()
    }

    private var pendingCalendarCardIndex: Int? = null
    private var todayCardsDialog: AlertDialog? = null
    private var todayCardsCheckedState: BooleanArray? = null

    private val calendarPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val idx = pendingCalendarCardIndex
        pendingCalendarCardIndex = null
        val alert = todayCardsDialog
        if (isGranted) {
            if (idx != null && alert != null && alert.isShowing) {
                alert.listView?.setItemChecked(idx, true)
                todayCardsCheckedState?.set(idx, true)
            } else {
                prefs.setTodayCardEnabled(NextUpCard.KEY, true)
                populateTodayCards()
            }
        } else {
            if (idx != null && alert != null && alert.isShowing) {
                alert.listView?.setItemChecked(idx, false)
                todayCardsCheckedState?.set(idx, false)
            }
            requireContext().showToast(R.string.calendar_off_for_thumbline)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationPermission.register(this)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")
        viewModel.isOlauncherDefault()

        binding.homeAppsNum.text = prefs.homeAppsNum.toString()
        populatePhotoWallpaperText()
        populateKeyboardText()
        populateDrawerBottomSearch()
        populateDrawerNewFirst()
        populateDrawerLetterStrip()
        populateDrawerRecents()
        populateSearchContacts()
        populateScreenTimeOnOff()
        populateDistractingApps()
        populatePauseBeforeApps()
        populatePauseAllowance()
        populatePauseEscalate()
        populatePauseAskWhy()
        populateCommitLock()
        populateHoldNotifications()
        populateQuietReleaseTimes()
        populateLockSettings()
        // Home button for recents feature disabled
        // populateHomeButtonRecents()
        populateAppThemeText()
        populateAccent()
        populateFontFamily()
        populateTextSize()
        populateBoldFont()
        populateHaptics()
        populateAlignment()
        populateShortcutIcons()
        populateStatusBar()
        populateDateTime()
        populateBatteryMode()
        populateSearchEngine()
        populateSwipeApps()
        populateTodayCards()
        populateWeather()
        populateNudgeWater()
        populateNudgeWalk()
        populateNudgeSocial()
        populateActiveHours()
        populateGerman()
        populateFooter()
        initClickListeners()
        initRuntimeRows()
        initSearch()
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
        populatePauseAllowance()
        populateCommitLock()
        populateHoldNotifications()
        populateQuietReleaseTimes()
        if (prefs.quietEnabled && isNotificationAccessGranted()) {
            ReleaseWorker.schedule(requireContext(), ExistingWorkPolicy.KEEP)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        currentSectionKey?.let { outState.putString(KEY_SAVED_SECTION, it) }
    }

    private data class RuntimeSettingsRow(
        val sectionKey: String,
        val rowView: View,
        val labelView: TextView,
        val viewId: Int,
    )

    private var runtimeRows: List<RuntimeSettingsRow> = emptyList()

    private fun getFirstTextView(parent: View): TextView {
        if (parent is ViewGroup) {
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                if (child is TextView) return child
            }
        }
        error("Expected TextView in parent $parent")
    }

    private fun initRuntimeRows() {
        runtimeRows = listOf(
            // Home
            RuntimeSettingsRow(SettingsSections.KEY_HOME, binding.homeAppsNum.parent as View, getFirstTextView(binding.homeAppsNum.parent as View), R.id.homeAppsNum),
            RuntimeSettingsRow(SettingsSections.KEY_HOME, binding.dateTime.parent as View, getFirstTextView(binding.dateTime.parent as View), R.id.dateTime),
            RuntimeSettingsRow(SettingsSections.KEY_HOME, binding.batteryModeLayout, binding.batteryModeTitle, R.id.batteryMode),
            RuntimeSettingsRow(SettingsSections.KEY_HOME, binding.alignment.parent as View, getFirstTextView(binding.alignment.parent as View), R.id.alignment),
            RuntimeSettingsRow(SettingsSections.KEY_HOME, binding.swapSidesLayout, binding.swapSides, R.id.swapSides),

            // Shortcuts
            RuntimeSettingsRow(SettingsSections.KEY_SHORTCUTS, binding.shortcutIconsOnOffLayout, binding.shortcutIconsOnOffTitle, R.id.shortcutIconsOnOff),
            RuntimeSettingsRow(SettingsSections.KEY_SHORTCUTS, binding.shortcutCountLayout, binding.shortcutCountTitle, R.id.shortcutCount),
            RuntimeSettingsRow(SettingsSections.KEY_SHORTCUTS, binding.shortcutSizeLayout, binding.shortcutSizeTitle, R.id.shortcutSize),
            RuntimeSettingsRow(SettingsSections.KEY_SHORTCUTS, binding.shortcutSideLayout, binding.shortcutSideTitle, R.id.shortcutSide),
            RuntimeSettingsRow(SettingsSections.KEY_SHORTCUTS, binding.shortcutHeightLayout, binding.shortcutHeightTitle, R.id.shortcutHeight),
            RuntimeSettingsRow(SettingsSections.KEY_SHORTCUTS, binding.shortcutIconStyleLayout, binding.shortcutIconStyleTitle, R.id.shortcutIconStyle),

            // Drawer
            RuntimeSettingsRow(SettingsSections.KEY_DRAWER, binding.autoShowKeyboard.parent as View, getFirstTextView(binding.autoShowKeyboard.parent as View), R.id.autoShowKeyboard),
            RuntimeSettingsRow(SettingsSections.KEY_DRAWER, binding.drawerBottomSearchLayout, binding.drawerBottomSearchTitle, R.id.drawerBottomSearch),
            RuntimeSettingsRow(SettingsSections.KEY_DRAWER, binding.searchEngineLayout, binding.searchEngineTitle, R.id.searchEngine),
            RuntimeSettingsRow(SettingsSections.KEY_DRAWER, binding.drawerNewFirstLayout, binding.drawerNewFirstTitle, R.id.drawerNewFirst),
            RuntimeSettingsRow(SettingsSections.KEY_DRAWER, binding.drawerLetterStripLayout, binding.drawerLetterStripTitle, R.id.drawerLetterStrip),
            RuntimeSettingsRow(SettingsSections.KEY_DRAWER, binding.drawerRecentsLayout, binding.drawerRecentsTitle, R.id.drawerRecents),
            RuntimeSettingsRow(SettingsSections.KEY_DRAWER, binding.searchContactsLayout, binding.searchContactsTitle, R.id.searchContacts),

            // Wellbeing
            RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.distractingAppsLayout, binding.distractingAppsTitle, R.id.distractingApps),
            RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.pauseBeforeAppsLayout, binding.pauseBeforeAppsTitle, R.id.pauseBeforeAppsOnOff),
            RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.pauseAllowanceLayout, binding.pauseAllowanceTitle, R.id.pauseAllowance),
            RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.pauseEscalateLayout, binding.pauseEscalateTitle, R.id.pauseEscalateOnOff),
            RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.pauseAskWhyLayout, binding.pauseAskWhyTitle, R.id.pauseAskWhyOnOff),
            RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.commitLockLayout, binding.commitLockTitle, R.id.commitLockOnOff),
            RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.holdNotificationsLayout, binding.holdNotificationsTitle, R.id.holdNotificationsOnOff),
            RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.quietReleaseTimesLayout, binding.quietReleaseTimesTitle, R.id.quietReleaseTimes),
            RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.screenTimeLayout, getFirstTextView(binding.screenTimeLayout), R.id.screenTimeOnOff),
            RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.nudgeWaterLayout, binding.nudgeWaterTitle, R.id.nudgeWater),
            RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.nudgeWalkLayout, binding.nudgeWalkTitle, R.id.nudgeWalk),
            RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.nudgeSocialLayout, binding.nudgeSocialTitle, R.id.nudgeSocial),
            RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.nudgeActiveHoursLayout, binding.nudgeActiveHoursTitle, R.id.nudgeActiveHours),

            // Today
            RuntimeSettingsRow(SettingsSections.KEY_TODAY, binding.todayCardsLayout, binding.todayCardsTitle, R.id.todayCards),
            RuntimeSettingsRow(SettingsSections.KEY_TODAY, binding.weatherLayout, binding.weatherTitle, R.id.weather),

            // Deutsch
            RuntimeSettingsRow(SettingsSections.KEY_DEUTSCH, binding.germanLayout, binding.germanTitle, R.id.germanOnOff),
            RuntimeSettingsRow(SettingsSections.KEY_DEUTSCH, binding.germanClockLayout, binding.germanClockTitle, R.id.germanClock),
            RuntimeSettingsRow(SettingsSections.KEY_DEUTSCH, binding.germanLookupLayout, binding.germanLookupTitle, R.id.germanLookupOnOff),
            RuntimeSettingsRow(SettingsSections.KEY_DEUTSCH, binding.germanPauseWordLayout, binding.germanPauseWordTitle, R.id.germanPauseWordOnOff),
            RuntimeSettingsRow(SettingsSections.KEY_DEUTSCH, binding.germanUiLayout, binding.germanUiTitle, R.id.germanUiOnOff),

            // Appearance
            RuntimeSettingsRow(SettingsSections.KEY_APPEARANCE, binding.photoWallpaperLayout, binding.photoWallpaperTitle, R.id.photoWallpaper),
            RuntimeSettingsRow(SettingsSections.KEY_APPEARANCE, binding.statusBar.parent as View, getFirstTextView(binding.statusBar.parent as View), R.id.statusBar),
            RuntimeSettingsRow(SettingsSections.KEY_APPEARANCE, binding.appThemeText.parent as View, getFirstTextView(binding.appThemeText.parent as View), R.id.appThemeText),
            RuntimeSettingsRow(SettingsSections.KEY_APPEARANCE, binding.accent.parent as View, getFirstTextView(binding.accent.parent as View), R.id.accent),
            RuntimeSettingsRow(SettingsSections.KEY_APPEARANCE, binding.fontFamily.parent as View, getFirstTextView(binding.fontFamily.parent as View), R.id.fontFamily),
            RuntimeSettingsRow(SettingsSections.KEY_APPEARANCE, binding.boldFont.parent as View, getFirstTextView(binding.boldFont.parent as View), R.id.boldFont),
            RuntimeSettingsRow(SettingsSections.KEY_APPEARANCE, binding.textSizeValue.parent as View, getFirstTextView(binding.textSizeValue.parent as View), R.id.textSizeValue),
            RuntimeSettingsRow(SettingsSections.KEY_APPEARANCE, binding.haptics.parent as View, getFirstTextView(binding.haptics.parent as View), R.id.haptics),

            // Gestures
            RuntimeSettingsRow(SettingsSections.KEY_GESTURES, binding.swipeLeftApp.parent as View, getFirstTextView(binding.swipeLeftApp.parent as View), R.id.swipeLeftApp),
            RuntimeSettingsRow(SettingsSections.KEY_GESTURES, binding.swipeRightLayout, getFirstTextView(binding.swipeRightLayout), R.id.swipeRightApp),
            RuntimeSettingsRow(SettingsSections.KEY_GESTURES, binding.toggleLock.parent as View, getFirstTextView(binding.toggleLock.parent as View), R.id.toggleLock),

            // Backup
            RuntimeSettingsRow(SettingsSections.KEY_BACKUP, binding.exportBackupLayout, binding.exportBackup, R.id.exportBackup),
            RuntimeSettingsRow(SettingsSections.KEY_BACKUP, binding.importBackupLayout, binding.importBackup, R.id.importBackup),
        )

        for (item in runtimeRows) {
            if (!item.rowView.hasOnClickListeners()) {
                item.rowView.setOnClickListener {
                    binding.root.findViewById<View>(item.viewId)?.performClick()
                }
            }
            if (!item.labelView.hasOnClickListeners()) {
                item.labelView.setOnClickListener {
                    binding.root.findViewById<View>(item.viewId)?.performClick()
                }
            }
        }
    }

    private fun isRowAvailable(viewId: Int): Boolean = SettingsRowAvailability.isAvailable(
        viewId = viewId,
        shortcutsOn = prefs.homeShortcuts,
        germanOn = prefs.germanEnabled,
    )

    fun collectRows(): List<SettingsRow> {
        return runtimeRows.filter { isRowAvailable(it.viewId) }.map { item ->
            SettingsRow(
                label = item.labelView.text.toString(),
                sectionKey = item.sectionKey,
                viewId = item.viewId,
            )
        }
    }

    private fun setupSearchInsets() {
        binding.root.setOnApplyWindowInsetsListener { root, insets ->
            val imeBottom = insets.getInsets(WindowInsets.Type.ime()).bottom
            val navBarBottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            val searchMargin = (12 * resources.displayMetrics.density).toInt()
            val bottomPadding = if (imeBottom > 0) {
                DrawerScrollHelper.imeBottomPadding(
                    drawerBottomSearch = true,
                    imeBottom = imeBottom,
                    searchBottomMargin = searchMargin,
                )
            } else {
                navBarBottom
            }
            if (root.paddingBottom != bottomPadding) {
                root.setPadding(root.paddingLeft, root.paddingTop, root.paddingRight, bottomPadding)
            }
            insets
        }
        binding.root.requestApplyInsets()
    }

    private fun initSearch() {
        binding.settingsSearch.doAfterTextChanged { text ->
            onSearchQueryChanged(text?.toString().orEmpty())
        }
    }

    private fun onSearchQueryChanged(query: String) {
        if (query.isBlank()) {
            if (currentSectionKey == null) {
                showSectionList()
            }
            return
        }

        val matchingRows = SettingsSearch.match(query, collectRows())
        val matchingIds = matchingRows.map { it.viewId }.toSet()

        backCallback.isEnabled = true
        binding.settingsBackLayout.isVisible = false
        binding.thumblineHeaderCard.isVisible = false
        binding.sectionListLayout.isVisible = false
        binding.footer.isVisible = false

        for (item in runtimeRows) {
            item.rowView.isVisible = item.viewId in matchingIds
        }

        binding.homeSection.isVisible = runtimeRows.any { it.sectionKey == SettingsSections.KEY_HOME && it.viewId in matchingIds }
        binding.shortcutIconsSection.isVisible = runtimeRows.any { it.sectionKey == SettingsSections.KEY_SHORTCUTS && it.viewId in matchingIds }
        binding.drawerSection.isVisible = runtimeRows.any { it.sectionKey == SettingsSections.KEY_DRAWER && it.viewId in matchingIds }

        val wellbeingMatch = runtimeRows.any { it.sectionKey == SettingsSections.KEY_WELLBEING && it.viewId in matchingIds }
        binding.wellbeingSection.isVisible = wellbeingMatch
        if (wellbeingMatch) {
            val nudgeIds = setOf(R.id.nudgeWater, R.id.nudgeWalk, R.id.nudgeSocial, R.id.nudgeActiveHours)
            binding.nudgesSection.isVisible = nudgeIds.any { it in matchingIds }
        }

        binding.todaySection.isVisible = (R.id.todayCards in matchingIds)
        binding.glanceSection.isVisible = (R.id.weather in matchingIds)
        binding.deutschSection.isVisible = runtimeRows.any { it.sectionKey == SettingsSections.KEY_DEUTSCH && it.viewId in matchingIds }
        binding.appearanceSection.isVisible = runtimeRows.any { it.sectionKey == SettingsSections.KEY_APPEARANCE && it.viewId in matchingIds }
        binding.gesturesSection.isVisible = runtimeRows.any { it.sectionKey == SettingsSections.KEY_GESTURES && it.viewId in matchingIds }
        binding.backupSection.isVisible = runtimeRows.any { it.sectionKey == SettingsSections.KEY_BACKUP && it.viewId in matchingIds }

        binding.scrollView.scrollTo(0, 0)
    }

    fun showSection(section: SettingsSection) {
        currentSectionKey = section.key
        backCallback.isEnabled = true

        binding.settingsSearchContainer.isVisible = false

        binding.settingsBackLayout.isVisible = true
        binding.thumblineHeaderCard.isVisible = false
        binding.sectionListLayout.isVisible = false

        for (item in runtimeRows) {
            if (item.sectionKey == section.key) {
                item.rowView.isVisible = isRowAvailable(item.viewId)
            }
        }
        binding.nudgesSection.isVisible = true

        binding.homeSection.isVisible = (section == SettingsSections.HOME)
        binding.shortcutIconsSection.isVisible = (section == SettingsSections.SHORTCUTS)
        binding.drawerSection.isVisible = (section == SettingsSections.DRAWER)
        binding.wellbeingSection.isVisible = (section == SettingsSections.WELLBEING)
        binding.todaySection.isVisible = (section == SettingsSections.TODAY)
        binding.glanceSection.isVisible = (section == SettingsSections.TODAY)
        binding.deutschSection.isVisible = (section == SettingsSections.DEUTSCH)
        binding.appearanceSection.isVisible = (section == SettingsSections.APPEARANCE)
        binding.gesturesSection.isVisible = (section == SettingsSections.GESTURES)
        binding.backupSection.isVisible = (section == SettingsSections.BACKUP)

        binding.scrollView.scrollTo(0, 0)
    }

    fun showSectionList() {
        currentSectionKey = null
        val hasSearch = _binding != null && !binding.settingsSearch.text.isNullOrEmpty()
        backCallback.isEnabled = false

        binding.settingsSearchContainer.isVisible = true
        if (hasSearch) {
            binding.settingsSearch.setText("")
        }

        binding.settingsBackLayout.isVisible = false
        binding.thumblineHeaderCard.isVisible = true
        binding.sectionListLayout.isVisible = true
        binding.footer.isVisible = true

        for (item in runtimeRows) {
            item.rowView.isVisible = isRowAvailable(item.viewId)
        }
        binding.nudgesSection.isVisible = true

        binding.homeSection.isVisible = false
        binding.shortcutIconsSection.isVisible = false
        binding.drawerSection.isVisible = false
        binding.wellbeingSection.isVisible = false
        binding.todaySection.isVisible = false
        binding.glanceSection.isVisible = false
        binding.deutschSection.isVisible = false
        binding.appearanceSection.isVisible = false
        binding.gesturesSection.isVisible = false
        binding.backupSection.isVisible = false

        binding.scrollView.scrollTo(0, 0)
    }

    /** Only explicit fragment arguments ([ARG_SECTION] key or [ARG_ROW] view id) open a section. */
    private fun resolveTargetSection(): SettingsSection? {
        val args = arguments ?: return null
        return SettingsSections.resolveTarget(
            sectionKey = args.getString(ARG_SECTION),
            rowViewId = args.getInt(ARG_ROW, 0),
        )
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.settingsBack, R.id.settingsBackLayout -> showSectionList()
            R.id.sectionRowHome -> showSection(SettingsSections.HOME)
            R.id.sectionRowShortcuts -> showSection(SettingsSections.SHORTCUTS)
            R.id.sectionRowDrawer -> showSection(SettingsSections.DRAWER)
            R.id.sectionRowWellbeing -> showSection(SettingsSections.WELLBEING)
            R.id.sectionRowToday -> showSection(SettingsSections.TODAY)
            R.id.sectionRowDeutsch -> showSection(SettingsSections.DEUTSCH)
            R.id.sectionRowAppearance -> showSection(SettingsSections.APPEARANCE)
            R.id.sectionRowGestures -> showSection(SettingsSections.GESTURES)
            R.id.sectionRowBackup -> showSection(SettingsSections.BACKUP)
            R.id.olauncherHiddenApps -> showHiddenApps()
            R.id.distractingApps, R.id.distractingAppsTitle, R.id.distractingAppsLayout -> showDistractingAppsDialog()
            R.id.pauseBeforeAppsOnOff, R.id.pauseBeforeAppsTitle, R.id.pauseBeforeAppsLayout -> togglePauseBeforeApps()
            R.id.pauseAllowance, R.id.pauseAllowanceTitle, R.id.pauseAllowanceLayout -> showPauseAllowanceMenu(binding.pauseAllowance)
            R.id.pauseEscalateOnOff, R.id.pauseEscalateTitle, R.id.pauseEscalateLayout -> togglePauseEscalate()
            R.id.pauseAskWhyOnOff, R.id.pauseAskWhyTitle, R.id.pauseAskWhyLayout -> togglePauseAskWhy()
            R.id.commitLockOnOff, R.id.commitLockTitle, R.id.commitLockSubtitle, R.id.commitLockLayout -> toggleCommitLock()
            R.id.holdNotificationsOnOff, R.id.holdNotificationsTitle, R.id.holdNotificationsLayout -> toggleHoldNotifications()
            R.id.quietReleaseTimes, R.id.quietReleaseTimesTitle, R.id.quietReleaseTimesLayout -> showReleaseTimesEditor()
            R.id.screenTimeOnOff -> viewModel.showDialog.postValue(Constants.Dialog.DIGITAL_WELLBEING)
            R.id.appInfo -> openAppInfo(requireContext(), Process.myUserHandle(), BuildConfig.APPLICATION_ID)
            R.id.setLauncher -> viewModel.resetLauncherLiveData.call()
            R.id.toggleLock -> toggleLockMode()
            // Home button for recents feature disabled
            // R.id.homeButtonRecents -> toggleHomeButtonRecents()
            R.id.autoShowKeyboard -> toggleKeyboardText()
            R.id.drawerBottomSearch, R.id.drawerBottomSearchTitle, R.id.drawerBottomSearchLayout -> toggleDrawerBottomSearch()
            R.id.drawerNewFirst, R.id.drawerNewFirstTitle, R.id.drawerNewFirstLayout -> toggleDrawerNewFirst()
            R.id.drawerLetterStrip, R.id.drawerLetterStripTitle, R.id.drawerLetterStripLayout -> toggleDrawerLetterStrip()
            R.id.drawerRecents, R.id.drawerRecentsTitle, R.id.drawerRecentsLayout -> toggleDrawerRecents()
            R.id.searchContacts, R.id.searchContactsTitle, R.id.searchContactsLayout -> toggleSearchContacts()
            R.id.searchEngine, R.id.searchEngineTitle, R.id.searchEngineLayout -> showSearchEngineMenu(binding.searchEngine)
            R.id.homeAppsNum -> showHomeAppsNumMenu(view)
            R.id.alignment -> showAlignmentMenu(view)
            R.id.swapSides, R.id.swapSidesLayout -> swapSides()
            R.id.shortcutIconsOnOff, R.id.shortcutIconsOnOffTitle, R.id.shortcutIconsOnOffLayout -> toggleShortcutIcons()
            R.id.shortcutCount, R.id.shortcutCountTitle, R.id.shortcutCountLayout -> showShortcutCountDialog()
            R.id.shortcutSize, R.id.shortcutSizeTitle, R.id.shortcutSizeLayout -> showShortcutSizeDialog()
            R.id.shortcutSide, R.id.shortcutSideTitle, R.id.shortcutSideLayout -> toggleShortcutSide()
            R.id.shortcutHeight, R.id.shortcutHeightTitle, R.id.shortcutHeightLayout -> showShortcutHeightDialog()
            R.id.shortcutIconStyle, R.id.shortcutIconStyleTitle, R.id.shortcutIconStyleLayout -> showShortcutIconStyleDialog()
            R.id.statusBar -> toggleStatusBar()
            R.id.dateTime -> showDateTimeMenu(view)
            R.id.batteryMode, R.id.batteryModeTitle, R.id.batteryModeLayout -> showBatteryModeMenu(binding.batteryMode)
            R.id.appThemeText -> showAppThemeMenu(view, showSystem = false)
            R.id.accent -> showAccentMenu(view)
            R.id.fontFamily -> showFontDialog()
            R.id.textSizeValue -> showTextSizeDialog()
            R.id.boldFont -> toggleBoldFont()
            R.id.haptics -> toggleHaptics()

            R.id.photoWallpaper, R.id.photoWallpaperTitle, R.id.photoWallpaperLayout -> showPhotoWallpaperMenu(binding.photoWallpaper)
            R.id.swipeLeftApp -> showAppListIfEnabled(Constants.FLAG_SET_SWIPE_LEFT_APP)
            R.id.swipeRightApp, R.id.swipeRightLayout -> showSwipeRightMenu(binding.swipeRightApp)
            R.id.todayCards, R.id.todayCardsTitle, R.id.todayCardsLayout -> showTodayCardsDialog()
            R.id.weatherTitle, R.id.weatherLayout -> showCitySearchDialog()
            R.id.weather -> {
                if (prefs.weatherEnabled) {
                    showWeatherMenu(binding.weather)
                } else {
                    showCitySearchDialog()
                }
            }
            R.id.nudgeWater, R.id.nudgeWaterTitle, R.id.nudgeWaterLayout -> showNudgeWaterMenu(binding.nudgeWater)
            R.id.nudgeWalk, R.id.nudgeWalkTitle, R.id.nudgeWalkLayout -> showNudgeWalkMenu(binding.nudgeWalk)
            R.id.nudgeSocial, R.id.nudgeSocialTitle, R.id.nudgeSocialLayout -> showNudgeSocialMenu(binding.nudgeSocial)
            R.id.nudgeActiveHours, R.id.nudgeActiveHoursTitle, R.id.nudgeActiveHoursLayout -> showActiveHoursDialog()
            R.id.germanOnOff, R.id.germanTitle, R.id.germanLayout -> toggleGerman()
            R.id.germanClock, R.id.germanClockTitle, R.id.germanClockLayout -> showGermanClockMenu(binding.germanClock)
            R.id.germanLookupOnOff, R.id.germanLookupTitle, R.id.germanLookupSubtitle, R.id.germanLookupLayout -> toggleGermanLookup()
            R.id.germanPauseWordOnOff, R.id.germanPauseWordTitle, R.id.germanPauseWordLayout -> toggleGermanPauseWord()
            R.id.germanUiOnOff, R.id.germanUiTitle, R.id.germanUiLayout -> toggleGermanUi()
            R.id.exportBackup, R.id.exportBackupLayout -> launchExportBackup()
            R.id.importBackup, R.id.importBackupLayout -> launchImportBackup()
            R.id.footer -> requireContext().openUrl(Constants.URL_SOURCE)
        }
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
            R.id.swipeLeftApp -> toggleSwipeLeft()
            R.id.swipeRightApp, R.id.swipeRightLayout -> toggleSwipeRight()
            R.id.toggleLock -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        return true
    }

    private fun initClickListeners() {
        binding.olauncherHiddenApps.setOnClickListener(this)
        binding.appInfo.setOnClickListener(this)
        binding.setLauncher.setOnClickListener(this)
        binding.autoShowKeyboard.setOnClickListener(this)
        binding.drawerBottomSearch.setOnClickListener(this)
        binding.drawerBottomSearchTitle.setOnClickListener(this)
        binding.drawerBottomSearchLayout.setOnClickListener(this)
        binding.drawerNewFirst.setOnClickListener(this)
        binding.drawerNewFirstTitle.setOnClickListener(this)
        binding.drawerNewFirstLayout.setOnClickListener(this)
        binding.drawerLetterStrip.setOnClickListener(this)
        binding.drawerLetterStripTitle.setOnClickListener(this)
        binding.drawerLetterStripLayout.setOnClickListener(this)
        binding.drawerRecents.setOnClickListener(this)
        binding.drawerRecentsTitle.setOnClickListener(this)
        binding.drawerRecentsLayout.setOnClickListener(this)
        binding.searchContacts.setOnClickListener(this)
        binding.searchContactsTitle.setOnClickListener(this)
        binding.searchContactsLayout.setOnClickListener(this)
        binding.searchEngine.setOnClickListener(this)
        binding.searchEngineTitle.setOnClickListener(this)
        binding.searchEngineLayout.setOnClickListener(this)
        binding.photoWallpaper.setOnClickListener(this)
        binding.photoWallpaperTitle.setOnClickListener(this)
        binding.photoWallpaperLayout.setOnClickListener(this)
        binding.toggleLock.setOnClickListener(this)
        // Home button for recents feature disabled
        // binding.homeButtonRecents.setOnClickListener(this)
        binding.homeAppsNum.setOnClickListener(this)
        binding.screenTimeOnOff.setOnClickListener(this)
        binding.distractingApps.setOnClickListener(this)
        binding.distractingAppsTitle.setOnClickListener(this)
        binding.distractingAppsLayout.setOnClickListener(this)
        binding.pauseBeforeAppsOnOff.setOnClickListener(this)
        binding.pauseBeforeAppsTitle.setOnClickListener(this)
        binding.pauseBeforeAppsLayout.setOnClickListener(this)
        binding.pauseAllowance.setOnClickListener(this)
        binding.pauseAllowanceTitle.setOnClickListener(this)
        binding.pauseAllowanceLayout.setOnClickListener(this)
        binding.pauseEscalateOnOff.setOnClickListener(this)
        binding.pauseEscalateTitle.setOnClickListener(this)
        binding.pauseEscalateLayout.setOnClickListener(this)
        binding.pauseAskWhyOnOff.setOnClickListener(this)
        binding.pauseAskWhyTitle.setOnClickListener(this)
        binding.pauseAskWhyLayout.setOnClickListener(this)
        binding.commitLockOnOff.setOnClickListener(this)
        binding.commitLockTitle.setOnClickListener(this)
        binding.commitLockSubtitle.setOnClickListener(this)
        binding.commitLockLayout.setOnClickListener(this)
        binding.holdNotificationsOnOff.setOnClickListener(this)
        binding.holdNotificationsTitle.setOnClickListener(this)
        binding.holdNotificationsLayout.setOnClickListener(this)
        binding.quietReleaseTimes.setOnClickListener(this)
        binding.quietReleaseTimesTitle.setOnClickListener(this)
        binding.quietReleaseTimesLayout.setOnClickListener(this)
        binding.alignment.setOnClickListener(this)
        binding.swapSides.setOnClickListener(this)
        binding.swapSidesLayout.setOnClickListener(this)
        binding.shortcutIconsOnOff.setOnClickListener(this)
        binding.shortcutIconsOnOffTitle.setOnClickListener(this)
        binding.shortcutIconsOnOffLayout.setOnClickListener(this)
        binding.shortcutCount.setOnClickListener(this)
        binding.shortcutCountTitle.setOnClickListener(this)
        binding.shortcutCountLayout.setOnClickListener(this)
        binding.shortcutSize.setOnClickListener(this)
        binding.shortcutSizeTitle.setOnClickListener(this)
        binding.shortcutSizeLayout.setOnClickListener(this)
        binding.shortcutSide.setOnClickListener(this)
        binding.shortcutSideTitle.setOnClickListener(this)
        binding.shortcutSideLayout.setOnClickListener(this)
        binding.shortcutHeight.setOnClickListener(this)
        binding.shortcutHeightTitle.setOnClickListener(this)
        binding.shortcutHeightLayout.setOnClickListener(this)
        binding.shortcutIconStyle.setOnClickListener(this)
        binding.shortcutIconStyleTitle.setOnClickListener(this)
        binding.shortcutIconStyleLayout.setOnClickListener(this)
        binding.statusBar.setOnClickListener(this)
        binding.dateTime.setOnClickListener(this)
        binding.batteryMode.setOnClickListener(this)
        binding.batteryModeTitle.setOnClickListener(this)
        binding.batteryModeLayout.setOnClickListener(this)
        binding.swipeLeftApp.setOnClickListener(this)
        binding.swipeRightApp.setOnClickListener(this)
        binding.swipeRightLayout.setOnClickListener(this)
        binding.todayCards.setOnClickListener(this)
        binding.todayCardsTitle.setOnClickListener(this)
        binding.todayCardsLayout.setOnClickListener(this)
        binding.weather.setOnClickListener(this)
        binding.weatherTitle.setOnClickListener(this)
        binding.weatherLayout.setOnClickListener(this)
        binding.nudgeWater.setOnClickListener(this)
        binding.nudgeWaterTitle.setOnClickListener(this)
        binding.nudgeWaterLayout.setOnClickListener(this)
        binding.nudgeWalk.setOnClickListener(this)
        binding.nudgeWalkTitle.setOnClickListener(this)
        binding.nudgeWalkLayout.setOnClickListener(this)
        binding.nudgeSocial.setOnClickListener(this)
        binding.nudgeSocialTitle.setOnClickListener(this)
        binding.nudgeSocialLayout.setOnClickListener(this)
        binding.nudgeActiveHours.setOnClickListener(this)
        binding.nudgeActiveHoursTitle.setOnClickListener(this)
        binding.nudgeActiveHoursLayout.setOnClickListener(this)
        binding.germanOnOff.setOnClickListener(this)
        binding.germanTitle.setOnClickListener(this)
        binding.germanLayout.setOnClickListener(this)
        binding.germanClock.setOnClickListener(this)
        binding.germanClockTitle.setOnClickListener(this)
        binding.germanClockLayout.setOnClickListener(this)
        binding.germanLookupOnOff.setOnClickListener(this)
        binding.germanLookupTitle.setOnClickListener(this)
        binding.germanLookupSubtitle.setOnClickListener(this)
        binding.germanLookupLayout.setOnClickListener(this)
        binding.germanPauseWordOnOff.setOnClickListener(this)
        binding.germanPauseWordTitle.setOnClickListener(this)
        binding.germanPauseWordLayout.setOnClickListener(this)
        binding.germanUiOnOff.setOnClickListener(this)
        binding.germanUiTitle.setOnClickListener(this)
        binding.germanUiLayout.setOnClickListener(this)
        binding.exportBackup.setOnClickListener(this)
        binding.exportBackupLayout.setOnClickListener(this)
        binding.importBackup.setOnClickListener(this)
        binding.importBackupLayout.setOnClickListener(this)
        binding.appThemeText.setOnClickListener(this)
        binding.accent.setOnClickListener(this)
        binding.fontFamily.setOnClickListener(this)
        binding.textSizeValue.setOnClickListener(this)
        binding.boldFont.setOnClickListener(this)
        binding.haptics.setOnClickListener(this)
        binding.footer.setOnClickListener(this)
        binding.settingsBack.setOnClickListener(this)
        binding.settingsBackLayout.setOnClickListener(this)
        binding.sectionRowHome.setOnClickListener(this)
        binding.sectionRowShortcuts.setOnClickListener(this)
        binding.sectionRowDrawer.setOnClickListener(this)
        binding.sectionRowWellbeing.setOnClickListener(this)
        binding.sectionRowToday.setOnClickListener(this)
        binding.sectionRowDeutsch.setOnClickListener(this)
        binding.sectionRowAppearance.setOnClickListener(this)
        binding.sectionRowGestures.setOnClickListener(this)
        binding.sectionRowBackup.setOnClickListener(this)


        binding.photoWallpaper.setOnLongClickListener(this)
        binding.photoWallpaperTitle.setOnLongClickListener(this)
        binding.photoWallpaperLayout.setOnLongClickListener(this)
        binding.alignment.setOnLongClickListener(this)
        binding.weather.setOnLongClickListener(this)
        binding.weatherTitle.setOnLongClickListener(this)
        binding.weatherLayout.setOnLongClickListener(this)
        binding.appThemeText.setOnLongClickListener(this)
        binding.swipeLeftApp.setOnLongClickListener(this)
        binding.swipeRightApp.setOnLongClickListener(this)
        binding.swipeRightLayout.setOnLongClickListener(this)
        binding.toggleLock.setOnLongClickListener(this)
    }

    private fun initObservers() {
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

    private fun showHomeAppsNumMenu(anchor: View) {
        anchor.showPopupMenu(
            configure = { menu ->
                for (num in 0..8) menu.add(Menu.NONE, num, num, num.toString())
            }
        ) { item -> updateHomeAppsNum(item.itemId) }
    }

    private fun showDateTimeMenu(anchor: View) {
        anchor.showPopupMenu(R.menu.date_time) { item ->
            when (item.itemId) {
                R.id.dateTimeOn -> toggleDateTime(Constants.DateTime.ON)
                R.id.dateTimeOff -> toggleDateTime(Constants.DateTime.OFF)
                R.id.dateOnly -> toggleDateTime(Constants.DateTime.DATE_ONLY)
            }
        }
    }

    private fun showAlignmentMenu(anchor: View) {
        anchor.showPopupMenu(
            R.menu.alignment,
            configure = { menu ->
                val leftLabel = getString(R.string.left)
                val centerLabel = getString(R.string.center)
                val rightLabel = getString(R.string.right)
                menu.findItem(R.id.alignmentLeft)?.title = SpannableString(leftLabel).apply {
                    setSpan(
                        AlignmentSpan.Standard(Layout.Alignment.ALIGN_NORMAL),
                        0,
                        leftLabel.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                menu.findItem(R.id.alignmentCenter)?.title = SpannableString(centerLabel).apply {
                    setSpan(
                        AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                        0,
                        centerLabel.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                menu.findItem(R.id.alignmentRight)?.title = SpannableString(rightLabel).apply {
                    setSpan(
                        AlignmentSpan.Standard(Layout.Alignment.ALIGN_OPPOSITE),
                        0,
                        rightLabel.length,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
                menu.findItem(R.id.alignmentBottom)?.setTitle(
                    if (prefs.homeBottomAlignment) R.string.bottom_on else R.string.bottom_off
                )
            }
        ) { item ->
            when (item.itemId) {
                R.id.alignmentLeft -> viewModel.updateHomeAlignment(Gravity.START)
                R.id.alignmentCenter -> viewModel.updateHomeAlignment(Gravity.CENTER)
                R.id.alignmentRight -> viewModel.updateHomeAlignment(Gravity.END)
                R.id.alignmentBottom -> updateHomeBottomAlignment()
            }
        }
    }

    private fun showShortcutCountDialog() {
        val counts = (SlotCount.MIN..SlotCount.MAX).toList()
        val items = counts.map { it.toString() }.toTypedArray()
        val checkedIndex = counts.indexOf(prefs.shortcutCount)

        val builder = AlertDialog.Builder(requireContext())
            .setTitle(R.string.icons)
            .setSingleChoiceItems(items, checkedIndex) { dialogInterface, which ->
                prefs.shortcutCount = counts[which]
                populateShortcutIcons()
                viewModel.updateHomeAlignment(prefs.homeAlignment)
                dialogInterface.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)

        val alertDialog = builder.create()
        dialog?.dismiss()
        dialog = alertDialog
        val window = alertDialog.window
        if (window != null && !prefs.showStatusBar) {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            alertDialog.show()
            window.hideStatusBar()
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        } else {
            alertDialog.show()
        }
    }

    private fun showShortcutSizeDialog() {
        val sizes = listOf(
            ShortcutSize.SMALL,
            ShortcutSize.MEDIUM,
            ShortcutSize.LARGE,
        )
        val items = arrayOf(
            getString(R.string.icon_size_small),
            getString(R.string.icon_size_medium),
            getString(R.string.icon_size_large),
        )
        val checkedIndex = sizes.indexOf(prefs.shortcutSizeStep).coerceAtLeast(0)

        val builder = AlertDialog.Builder(requireContext())
            .setTitle(R.string.icon_size)
            .setSingleChoiceItems(items, checkedIndex) { dialogInterface, which ->
                prefs.shortcutSizeStep = sizes[which]
                populateShortcutIcons()
                viewModel.updateHomeAlignment(prefs.homeAlignment)
                dialogInterface.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)

        val alertDialog = builder.create()
        dialog?.dismiss()
        dialog = alertDialog
        val window = alertDialog.window
        if (window != null && !prefs.showStatusBar) {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            alertDialog.show()
            window.hideStatusBar()
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        } else {
            alertDialog.show()
        }
    }

    private fun showShortcutIconStyleDialog() {
        val styles = listOf(
            ShortcutIconStyle.ORIGINAL,
            ShortcutIconStyle.MONOCHROME,
            ShortcutIconStyle.TEXT_COLOUR,
        )
        val items = arrayOf(
            getString(R.string.icon_style_original),
            getString(R.string.icon_style_monochrome),
            getString(R.string.icon_style_text_colour),
        )
        val checkedIndex = styles.indexOf(prefs.shortcutIconStyle).coerceAtLeast(0)

        val builder = AlertDialog.Builder(requireContext())
            .setTitle(R.string.icon_style)
            .setSingleChoiceItems(items, checkedIndex) { dialogInterface, which ->
                prefs.shortcutIconStyle = styles[which]
                populateShortcutIcons()
                viewModel.updateHomeAlignment(prefs.homeAlignment)
                dialogInterface.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)

        val alertDialog = builder.create()
        dialog?.dismiss()
        dialog = alertDialog
        val window = alertDialog.window
        if (window != null && !prefs.showStatusBar) {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            alertDialog.show()
            window.hideStatusBar()
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        } else {
            alertDialog.show()
        }
    }

    // "System" stays hidden unless the row is long pressed
    private fun showAppThemeMenu(anchor: View, showSystem: Boolean) {
        anchor.showPopupMenu(
            R.menu.app_theme,
            configure = { menu -> menu.findItem(R.id.themeSystem).isVisible = showSystem }
        ) { item ->
            when (item.itemId) {
                R.id.themeLight -> updateTheme(AppCompatDelegate.MODE_NIGHT_NO)
                R.id.themeDark -> updateTheme(AppCompatDelegate.MODE_NIGHT_YES)
                R.id.themeSystem -> updateTheme(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
    }

    // Dialogs

    private fun showDialog(newDialog: OlDialog) {
        dialog?.dismiss()
        dialog = newDialog
        newDialog.showRespectingStatusBar()
    }

    private fun showTextSizeDialog() {
        var applied = false
        val initialScale = prefs.textSizeScale
        pendingTextSizeScale = initialScale
        var stepper: DialogTextSizeBinding? = null
        val dialog = requireContext().createDialog(
            title = R.string.text_size,
            action = R.string.apply,
            neutral = R.string.cancel,
            onAction = {
                applied = true
                applyTextSizeScale()
            },
            onNeutral = {
                // Cancelled
            }
        ) { container ->
            DialogTextSizeBinding.inflate(layoutInflater, container, false).also { stepper = it }.root
        }
        stepper?.apply {
            textSizeSample.isVisible = true
            updateTextSizeSample(textSizeSample, pendingTextSizeScale)
            textSizeCurrent.text = formatScale(pendingTextSizeScale)
            textSizeMinus.setOnClickListener { adjustTextSizePreview(-0.1f, this) }
            textSizePlus.setOnClickListener { adjustTextSizePreview(0.1f, this) }
        }
        dialog.setOnDismissListener {
            if (!applied) {
                pendingTextSizeScale = -1f
                populateTextSize()
            }
        }
        showDialog(dialog)
    }

    private fun showShortcutHeightDialog() {
        pendingShortcutOffsetStep = prefs.shortcutOffsetStep
        var stepper: DialogTextSizeBinding? = null
        val dialog = requireContext().createDialog(R.string.icons_height, R.string.okay) { container ->
            DialogTextSizeBinding.inflate(layoutInflater, container, false).also { stepper = it }.root
        }
        stepper?.apply {
            textSizeSample.isVisible = false
            textSizeCurrent.text = ShortcutOffset.format(pendingOrCurrentShortcutOffsetStep())
            textSizeMinus.setOnClickListener { adjustShortcutHeightPreview(-1, this) }
            textSizePlus.setOnClickListener { adjustShortcutHeightPreview(1, this) }
        }
        dialog.setOnDismissListener { applyShortcutOffsetStep() }
        showDialog(dialog)
    }

    // Prominent disclosure before sending the user to accessibility settings
    private fun showAccessibilityDialog() {
        val serviceEnabled = isAccessServiceEnabled(requireContext())
        showDialog(
            requireContext().createDialog(
                title = R.string.gestures,
                action = if (serviceEnabled) R.string.disable else R.string.enable,
                message = R.string.accessibility_disclosure,
                neutral = R.string.not_working,
                onNeutral = { requireContext().openUrl(Constants.URL_DOUBLE_TAP) },
                onAction = { openAccessibilityService() },
            )
        )
    }

    private fun toggleSwipeLeft() {
        prefs.swipeLeftEnabled = !prefs.swipeLeftEnabled
        if (prefs.swipeLeftEnabled) {
            binding.swipeLeftApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
            requireContext().showToast(getString(R.string.swipe_left_app_enabled))
        } else {
            binding.swipeLeftApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
            requireContext().showToast(getString(R.string.swipe_left_app_disabled))
        }
    }

    private fun toggleSwipeRight() {
        prefs.swipeRightEnabled = !prefs.swipeRightEnabled
        if (prefs.swipeRightEnabled) {
            binding.swipeRightApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
            requireContext().showToast(getString(R.string.swipe_right_app_enabled))
        } else {
            binding.swipeRightApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
            requireContext().showToast(getString(R.string.swipe_right_app_disabled))
        }
    }

    private fun toggleStatusBar() {
        prefs.showStatusBar = !prefs.showStatusBar
        populateStatusBar()
    }

    private fun populateStatusBar() {
        if (prefs.showStatusBar) {
            requireActivity().window.showStatusBar()
            binding.statusBar.text = getString(R.string.on)
        } else {
            requireActivity().window.hideStatusBar()
            binding.statusBar.text = getString(R.string.off)
        }
    }

    private fun toggleDateTime(selected: Int) {
        prefs.dateTimeVisibility = selected
        populateDateTime()
        viewModel.toggleDateTime()
    }

    private fun populateDateTime() {
        binding.dateTime.text = getString(
            when (prefs.dateTimeVisibility) {
                Constants.DateTime.DATE_ONLY -> R.string.date
                Constants.DateTime.ON -> R.string.on
                else -> R.string.off
            }
        )
    }

    private fun showBatteryModeMenu(anchor: View) {
        val options = listOf(
            Prefs.BATTERY_MODE_AUTO to getString(R.string.auto),
            Prefs.BATTERY_MODE_ALWAYS to getString(R.string.always),
            Prefs.BATTERY_MODE_OFF to getString(R.string.off),
        )
        anchor.showPopupMenu(
            configure = { menu ->
                options.forEach { (id, label) ->
                    menu.add(Menu.NONE, id, id, label)
                }
            }
        ) { item ->
            prefs.batteryMode = item.itemId
            populateBatteryMode()
            viewModel.toggleDateTime()
        }
    }

    private fun populateBatteryMode() {
        binding.batteryMode.text = getString(
            when (prefs.batteryMode) {
                Prefs.BATTERY_MODE_ALWAYS -> R.string.always
                Prefs.BATTERY_MODE_OFF -> R.string.off
                else -> R.string.auto
            }
        )
    }

    private fun showSearchEngineMenu(anchor: View) {
        val options = listOf(
            SearchEngine.PHONE_DEFAULT to getString(R.string.search_engine_phone_default),
            SearchEngine.GOOGLE to getString(R.string.search_engine_google),
            SearchEngine.DUCKDUCKGO to getString(R.string.search_engine_duckduckgo),
            SearchEngine.BING to getString(R.string.search_engine_bing),
            SearchEngine.CUSTOM to getString(R.string.search_engine_custom),
        )
        anchor.showPopupMenu(
            configure = { menu ->
                options.forEachIndexed { index, (_, label) ->
                    menu.add(Menu.NONE, index, index, label)
                }
            }
        ) { item ->
            val (engineId, _) = options.getOrNull(item.itemId) ?: return@showPopupMenu
            if (engineId == SearchEngine.CUSTOM) {
                showCustomSearchEngineDialog()
            } else {
                prefs.searchEngine = engineId
                populateSearchEngine()
            }
        }
    }

    private fun populateSearchEngine() {
        binding.searchEngine.text = getString(
            when (prefs.searchEngine) {
                SearchEngine.GOOGLE -> R.string.search_engine_google
                SearchEngine.DUCKDUCKGO -> R.string.search_engine_duckduckgo
                SearchEngine.BING -> R.string.search_engine_bing
                SearchEngine.CUSTOM -> R.string.search_engine_custom
                else -> R.string.search_engine_phone_default
            }
        )
    }

    private fun showCustomSearchEngineDialog() {
        var dialogEditText: AppCompatEditText? = null
        var customDialog: OlDialog? = null

        fun save() {
            val text = dialogEditText?.text?.toString().orEmpty()
            if (SearchEngine.isValidTemplate(text)) {
                prefs.searchEngineCustomUrl = text.trim()
                prefs.searchEngine = SearchEngine.CUSTOM
                populateSearchEngine()
            } else {
                showToast(R.string.search_engine_custom_invalid)
            }
        }

        val dialog = requireContext().createDialog(
            title = R.string.search_engine,
            action = R.string.save,
            onAction = {
                save()
            }
        ) { container ->
            val context = container.context
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
            val editText = AppCompatEditText(context).apply {
                hint = context.getString(R.string.search_engine_custom_hint)
                setText(prefs.searchEngineCustomUrl)
                setSingleLine(true)
                imeOptions = EditorInfo.IME_ACTION_DONE
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                setTextColor(context.getColorFromAttr(R.attr.primaryColor))
                setHintTextColor(context.getColorFromAttr(R.attr.primaryColorTrans80))
                background = null
                setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_DONE) {
                        customDialog?.dismiss()
                        save()
                        true
                    } else false
                }
            }
            dialogEditText = editText
            layout.addView(editText)
            layout
        }
        customDialog = dialog
        dialog.showRespectingStatusBar()
        dialogEditText?.apply {
            setSelection(text?.length ?: 0)
            post {
                requestFocus()
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun showToast(@StringRes resId: Int) {
        requireContext().showToast(resId)
    }

    private fun populateWeather() {
        binding.weather.text = if (prefs.weatherEnabled) {
            val place = WeatherLocation.placeName(requireContext())
            if (place.isNotBlank()) place else getString(R.string.off)
        } else {
            getString(R.string.off)
        }
    }

    private fun handleUseMyLocation() {
        if (WeatherLocation.hasPermission(requireContext())) {
            enableUseMyLocation()
        } else {
            requireContext().showMessageDialog(
                title = R.string.weather,
                message = R.string.weather_location_permission_message,
                action = R.string.allow,
            ) {
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
        }
    }

    private fun enableUseMyLocation() {
        prefs.weatherUseLocation = true
        prefs.weatherEnabled = true
        WeatherWorker.schedulePeriodic(requireContext())
        WeatherLocation.refreshIfStale(requireContext(), force = true)
        populateWeather()
    }

    private fun showWeatherMenu(anchor: View) {
        anchor.showPopupMenu(
            configure = { menu ->
                menu.add(Menu.NONE, 1, 1, getString(R.string.celsius))
                menu.add(Menu.NONE, 2, 2, getString(R.string.fahrenheit))
                if (prefs.weatherEnabled) {
                    menu.add(Menu.NONE, 3, 3, getString(R.string.off))
                }
            }
        ) { item ->
            when (item.itemId) {
                1 -> {
                    if (prefs.weatherUnit != Prefs.WEATHER_UNIT_C) {
                        prefs.weatherUnit = Prefs.WEATHER_UNIT_C
                        if (prefs.weatherEnabled) {
                            WeatherWorker.runOnce(requireContext())
                        }
                    }
                }
                2 -> {
                    if (prefs.weatherUnit != Prefs.WEATHER_UNIT_F) {
                        prefs.weatherUnit = Prefs.WEATHER_UNIT_F
                        if (prefs.weatherEnabled) {
                            WeatherWorker.runOnce(requireContext())
                        }
                    }
                }
                3 -> {
                    prefs.weatherEnabled = false
                    WeatherWorker.cancel(requireContext())
                    populateWeather()
                }
            }
        }
    }

    private fun showCitySearchDialog() {
        var dialogEditText: AppCompatEditText? = null
        var cityDialog: OlDialog? = null
        val dialog = requireContext().createDialog(
            title = R.string.weather,
            action = R.string.search,
            neutral = R.string.use_my_location,
            onNeutral = {
                handleUseMyLocation()
            },
            onAction = {
                val query = dialogEditText?.text?.toString()?.trim().orEmpty()
                if (query.isNotEmpty()) {
                    searchCity(query)
                }
            }
        ) { container ->
            val context = container.context
            val layout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
            val editText = AppCompatEditText(context).apply {
                hint = context.getString(R.string.city)
                setText(if (prefs.weatherEnabled) prefs.weatherPlaceName else "")
                setSingleLine(true)
                imeOptions = EditorInfo.IME_ACTION_SEARCH
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
                setTextColor(context.getColorFromAttr(R.attr.primaryColor))
                setHintTextColor(context.getColorFromAttr(R.attr.primaryColorTrans80))
                background = null
                setOnEditorActionListener { _, actionId, _ ->
                    if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                        val q = text?.toString()?.trim().orEmpty()
                        if (q.isNotEmpty()) {
                            cityDialog?.dismiss()
                            searchCity(q)
                        }
                        true
                    } else false
                }
            }
            dialogEditText = editText
            layout.addView(editText)
            layout
        }
        cityDialog = dialog
        dialog.showRespectingStatusBar()
        dialogEditText?.apply {
            setSelection(text?.length ?: 0)
            post {
                requestFocus()
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun searchCity(query: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val places = withContext(Dispatchers.IO) {
                WeatherApi.searchPlaces(query)
            }
            if (!isAdded) return@launch
            if (places.isEmpty()) {
                requireContext().showToast(getString(R.string.weather_no_results))
                return@launch
            }
            showPlaceSelectionDialog(places)
        }
    }

    private fun showPlaceSelectionDialog(places: List<Place>) {
        val items = places.map { it.displayName() }.toTypedArray()
        val checkedIndex = 0

        val builder = AlertDialog.Builder(requireContext())
            .setTitle(R.string.weather)
            .setSingleChoiceItems(items, checkedIndex) { dialogInterface, which ->
                val place = places[which]
                prefs.weatherPlaceName = place.name
                prefs.weatherLat = String.format(Locale.US, "%.2f", place.lat)
                prefs.weatherLon = String.format(Locale.US, "%.2f", place.lon)
                prefs.weatherUseLocation = false
                prefs.weatherEnabled = true
                WeatherWorker.schedulePeriodic(requireContext())
                WeatherWorker.runOnce(requireContext())
                populateWeather()
                dialogInterface.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)

        val alertDialog = builder.create()
        dialog?.dismiss()
        dialog = alertDialog
        val window = alertDialog.window
        if (window != null && !prefs.showStatusBar) {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            alertDialog.show()
            window.hideStatusBar()
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        } else {
            alertDialog.show()
        }
    }

    private fun showHiddenApps() {
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

    private fun openAccessibilityService() {
        // prefs.lockModeOn = true
        populateLockSettings()
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun toggleLockMode() {
        if (!prefs.lockModeOn && !isAccessServiceEnabled(requireContext())) {
            showAccessibilityDialog()
            return
        }
        prefs.lockModeOn = !prefs.lockModeOn
        populateLockSettings()
    }

    private fun updateHomeAppsNum(num: Int) {
        binding.homeAppsNum.text = num.toString()
        prefs.homeAppsNum = num
        viewModel.refreshHome(true)
    }

    private var pendingTextSizeScale: Float = -1f

    private fun pendingOrCurrentTextSizeScale(): Float =
        if (pendingTextSizeScale > 0) pendingTextSizeScale else prefs.textSizeScale

    private fun formatScale(scale: Float): String = String.format("%.1f", scale)

    private fun adjustTextSizePreview(delta: Float, dialogBinding: DialogTextSizeBinding) {
        val maxScale = if (isTablet(requireContext())) 2.0f else 1.5f
        val current = pendingOrCurrentTextSizeScale()
        val newScale = Math.round((current + delta) * 10f) / 10f
        val clamped = newScale.coerceIn(0.5f, maxScale)
        if (clamped == current) return
        pendingTextSizeScale = clamped
        val formatted = formatScale(clamped)
        binding.textSizeValue.text = formatted
        dialogBinding.textSizeCurrent.text = formatted
        updateTextSizeSample(dialogBinding.textSizeSample, clamped)
    }

    private fun updateTextSizeSample(sampleView: TextView, scale: Float) {
        val typedValue = TypedValue()
        resources.getValue(R.dimen.text_large, typedValue, true)
        val baseSp = TypedValue.complexToFloat(typedValue.data)
        val basePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, baseSp, resources.displayMetrics)
        sampleView.setTextSize(TypedValue.COMPLEX_UNIT_PX, basePx * scale)
    }

    private fun applyTextSizeScale() {
        if (pendingTextSizeScale < 0 || prefs.textSizeScale == pendingTextSizeScale) {
            pendingTextSizeScale = -1f
            return
        }
        prefs.textSizeScale = pendingTextSizeScale
        pendingTextSizeScale = -1f
        val activity = activity ?: return
        if (activity.isChangingConfigurations.not())
            activity.recreate()
    }

    private var pendingShortcutOffsetStep: Int = -1

    private fun pendingOrCurrentShortcutOffsetStep(): Int =
        if (pendingShortcutOffsetStep in ShortcutOffset.MIN_STEP..ShortcutOffset.MAX_STEP) {
            pendingShortcutOffsetStep
        } else {
            prefs.shortcutOffsetStep
        }

    private fun adjustShortcutHeightPreview(delta: Int, dialogBinding: DialogTextSizeBinding) {
        val current = pendingOrCurrentShortcutOffsetStep()
        val clamped = ShortcutOffset.clamp(current + delta)
        if (clamped == current) return
        pendingShortcutOffsetStep = clamped
        binding.shortcutHeight.text = ShortcutOffset.format(clamped)
        dialogBinding.textSizeCurrent.text = ShortcutOffset.format(clamped)
    }

    private fun applyShortcutOffsetStep() {
        if (pendingShortcutOffsetStep < 0 || prefs.shortcutOffsetStep == pendingShortcutOffsetStep) {
            pendingShortcutOffsetStep = -1
            populateShortcutIcons()
            return
        }
        prefs.shortcutOffsetStep = pendingShortcutOffsetStep
        pendingShortcutOffsetStep = -1
        populateShortcutIcons()
        viewModel.updateHomeAlignment(prefs.homeAlignment)
    }

    private fun toggleKeyboardText() {
        if (prefs.autoShowKeyboard && prefs.keyboardMessageShown.not()) {
            viewModel.showDialog.postValue(Constants.Dialog.KEYBOARD)
            prefs.keyboardMessageShown = true
        } else {
            prefs.autoShowKeyboard = !prefs.autoShowKeyboard
            populateKeyboardText()
        }
    }

    private fun updateTheme(appTheme: Int) {
        if (AppCompatDelegate.getDefaultNightMode() == appTheme) return
        prefs.appTheme = appTheme
        populateAppThemeText(appTheme)
        setAppTheme(appTheme)
    }

    private fun setAppTheme(theme: Int) {
        if (AppCompatDelegate.getDefaultNightMode() == theme) return
        if (prefs.photoWallpaperCount > 0) {
            PhotoWallpaper.applyCurrent(requireContext(), prefs)
        } else {
            setPlainWallpaperByTheme(requireContext(), theme)
        }
        requireActivity().recreate()
    }

    private fun openPhotoPicker() {
        viewModel.isAwaitingActivityResult = true
        pickMultipleMedia.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun openPhotoPickerForAdd() {
        viewModel.isAwaitingActivityResult = true
        pickMultipleMediaForAdd.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun handlePickedPhotos(uris: List<Uri>) {
        if (uris.size == 1) {
            requireContext().showToast(getString(R.string.saving_photo))
        } else {
            requireContext().showToast(getString(R.string.saving_photos, uris.size))
        }
        // Use the activity scope and app context so leaving Settings mid-save does not cancel it
        val appContext = requireContext().applicationContext
        requireActivity().lifecycleScope.launch {
            PhotoWallpaper.withEditLock {
            val savedCount = PhotoWallpaper.savePickedPhotos(appContext, uris)
            if (savedCount > 0) {
                prefs.photoWallpaperCount = savedCount
                prefs.photoWallpaperIndex = 0
                PhotoWallpaper.markChanged(prefs)
                if (_binding != null) populatePhotoWallpaperText()

                WallpaperRotateWorker.schedulePeriodic(appContext)
                WallpaperRotateWorker.runOnce(appContext)
                withContext(Dispatchers.IO) {
                    PhotoWallpaper.applyCurrent(appContext, prefs)
                }
            } else {
                appContext.showToast(R.string.wallpaper_photos_save_failed)
            }
            }
        }
    }

    private fun handleAddedPhotos(uris: List<Uri>) {
        if (uris.size == 1) {
            requireContext().showToast(getString(R.string.saving_photo))
        } else {
            requireContext().showToast(getString(R.string.saving_photos, uris.size))
        }
        val appContext = requireContext().applicationContext
        requireActivity().lifecycleScope.launch {
            PhotoWallpaper.withEditLock {
                val saved = PhotoWallpaper.appendPickedPhotos(appContext, uris, startIndex = prefs.photoWallpaperCount)
                if (saved > 0) {
                    prefs.photoWallpaperCount += saved
                    if (_binding != null) populatePhotoWallpaperText()
                } else {
                    appContext.showToast(R.string.wallpaper_photos_save_failed)
                }
            }
        }
    }

    private fun populatePhotoWallpaperText() {
        if (_binding == null) return
        val count = prefs.photoWallpaperCount
        binding.photoWallpaper.text = when {
            count == 0 -> getString(R.string.off)
            count == 1 -> getString(R.string.wallpaper_photo_count, 1)
            else -> getString(R.string.wallpaper_photos_count, count)
        }
    }

    private fun showPhotoWallpaperMenu(anchor: View) {
        if (PhotoSet.rowTapAction(prefs.photoWallpaperCount) == PhotoSet.RowTapAction.OPEN_PICKER) {
            openPhotoPicker()
            return
        }
        anchor.showPopupMenu(
            R.menu.photo_wallpaper,
            configure = { menu ->
                val targetTitle = when (prefs.photoWallpaperTarget) {
                    PhotoWallpaper.TARGET_HOME -> R.string.set_on_home
                    PhotoWallpaper.TARGET_LOCK -> R.string.set_on_lock
                    PhotoWallpaper.TARGET_BOTH -> R.string.set_on_both
                    else -> R.string.set_on_home
                }
                menu.findItem(R.id.photoWallpaperTarget).setTitle(targetTitle)
                val currentInterval = RotationInterval.fromId(prefs.photoWallpaperInterval)
                val frequencyLabel = getString(intervalToStringRes(currentInterval))
                menu.findItem(R.id.photoWallpaperFrequency).title =
                    getString(R.string.photo_wallpaper_frequency, frequencyLabel)
                menu.findItem(R.id.photoWallpaperHomeMenu).isChecked = prefs.photoWallpaperHomeMenu
            }
        ) { item ->
            when (item.itemId) {
                R.id.photoWallpaperAdd -> openPhotoPickerForAdd()
                R.id.photoWallpaperRemove -> showRemovePhotosDialog()
                R.id.photoWallpaperChange -> openPhotoPicker()
                R.id.photoWallpaperTarget -> cyclePhotoWallpaperTarget()
                R.id.photoWallpaperFrequency -> showPhotoWallpaperFrequencyDialog()
                R.id.photoWallpaperNext -> nextPhotoNow()
                R.id.photoWallpaperPrevious -> previousPhotoNow()
                R.id.photoWallpaperHomeMenu -> togglePhotoWallpaperHomeMenu()
                R.id.photoWallpaperOff -> {
                    val appContext = requireContext().applicationContext
                    requireActivity().lifecycleScope.launch {
                        PhotoWallpaper.withEditLock { turnOffPhotoWallpaper(appContext) }
                    }
                }
            }
        }
    }

    private fun showPhotoWallpaperFrequencyDialog() {
        val intervals = RotationInterval.entries
        val items = intervals.map { getString(intervalToStringRes(it)) }.toTypedArray()
        val currentInterval = RotationInterval.fromId(prefs.photoWallpaperInterval)
        val checkedIndex = intervals.indexOf(currentInterval)

        val builder = AlertDialog.Builder(requireContext())
            .setSingleChoiceItems(items, checkedIndex) { dialogInterface, which ->
                val interval = intervals[which]
                prefs.photoWallpaperInterval = interval.id
                WallpaperRotateWorker.schedulePeriodic(requireContext().applicationContext)
                dialogInterface.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)

        val alertDialog = builder.create()
        dialog?.dismiss()
        dialog = alertDialog
        val window = alertDialog.window
        if (window != null && !prefs.showStatusBar) {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            alertDialog.show()
            window.hideStatusBar()
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        } else {
            alertDialog.show()
        }
    }

    private fun showRemovePhotosDialog() {
        val count = prefs.photoWallpaperCount
        if (count <= 0) return

        val currentIndex = prefs.photoWallpaperIndex.coerceIn(0, count - 1)
        val labels = Array(count) { i ->
            if (i == currentIndex) {
                getString(R.string.photo_label_current, i + 1)
            } else {
                getString(R.string.photo_label, i + 1)
            }
        }
        val checked = BooleanArray(count)

        val builder = AlertDialog.Builder(requireContext())
            .setTitle(R.string.remove_photos)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(R.string.remove) { _, _ ->
                val checkedIndices = checked.indices.filter { checked[it] }.toSet()
                if (checkedIndices.isEmpty()) return@setPositiveButton

                val appContext = requireContext().applicationContext
                requireActivity().lifecycleScope.launch {
                    PhotoWallpaper.withEditLock {
                        // Plan under the lock so it matches the set as it is now.
                        val currentCount = prefs.photoWallpaperCount
                        if (currentCount <= 0 || checkedIndices.any { it >= currentCount }) return@withEditLock
                        val plan = PhotoSet.planRemoval(
                            currentCount,
                            prefs.photoWallpaperIndex,
                            checkedIndices
                        )
                        if (plan.newCount == 0) {
                            turnOffPhotoWallpaper(appContext)
                            return@withEditLock
                        }
                        withContext(Dispatchers.IO) {
                            val success = PhotoWallpaper.removePhotos(appContext, plan)
                            if (success) {
                                prefs.photoWallpaperCount = plan.newCount
                                prefs.photoWallpaperIndex = plan.newIndex
                                if (plan.currentRemoved) {
                                    PhotoWallpaper.applyCurrent(appContext, prefs)
                                }
                            } else {
                                val recoveredCount = PhotoSet.countContiguous(PhotoWallpaper.getWallpaperDir(appContext))
                                prefs.photoWallpaperCount = recoveredCount
                                if (recoveredCount == 0) {
                                    withContext(Dispatchers.Main) {
                                        turnOffPhotoWallpaper(appContext)
                                    }
                                } else {
                                    prefs.photoWallpaperIndex = prefs.photoWallpaperIndex.coerceIn(0, recoveredCount - 1)
                                }
                                withContext(Dispatchers.Main) {
                                    appContext.showToast(R.string.wallpaper_photos_remove_failed)
                                }
                            }
                        }
                        if (_binding != null) {
                            populatePhotoWallpaperText()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)

        val alertDialog = builder.create()
        dialog?.dismiss()
        dialog = alertDialog
        val window = alertDialog.window
        if (window != null && !prefs.showStatusBar) {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            alertDialog.show()
            window.hideStatusBar()
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        } else {
            alertDialog.show()
        }
    }

    private fun cyclePhotoWallpaperTarget() {
        prefs.photoWallpaperTarget = when (prefs.photoWallpaperTarget) {
            PhotoWallpaper.TARGET_HOME -> PhotoWallpaper.TARGET_LOCK
            PhotoWallpaper.TARGET_LOCK -> PhotoWallpaper.TARGET_BOTH
            else -> PhotoWallpaper.TARGET_HOME
        }
        val targetRes = when (prefs.photoWallpaperTarget) {
            PhotoWallpaper.TARGET_HOME -> R.string.set_on_home
            PhotoWallpaper.TARGET_LOCK -> R.string.set_on_lock
            PhotoWallpaper.TARGET_BOTH -> R.string.set_on_both
            else -> R.string.set_on_home
        }
        requireContext().showToast(targetRes)
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            PhotoWallpaper.applyCurrent(requireContext(), prefs)
        }
    }

    private fun nextPhotoNow() {
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            PhotoWallpaper.stepManually(appContext, prefs, forward = true)
        }
    }

    private fun previousPhotoNow() {
        val appContext = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            PhotoWallpaper.stepManually(appContext, prefs, forward = false)
        }
    }

    private fun togglePhotoWallpaperHomeMenu() {
        prefs.photoWallpaperHomeMenu = !prefs.photoWallpaperHomeMenu
        showToast(if (prefs.photoWallpaperHomeMenu) R.string.on else R.string.off)
    }

    private fun turnOffPhotoWallpaper(context: Context = requireContext()) {
        WallpaperRotateWorker.cancel(context)
        PhotoWallpaper.deleteAll(context)
        prefs.photoWallpaperCount = 0
        prefs.photoWallpaperIndex = 0
        setPlainWallpaperByTheme(context, prefs.appTheme)
        if (_binding != null) populatePhotoWallpaperText()
    }

    private fun populateAppThemeText(appTheme: Int = prefs.appTheme) {
        when (appTheme) {
            AppCompatDelegate.MODE_NIGHT_YES -> binding.appThemeText.text = getString(R.string.dark)
            AppCompatDelegate.MODE_NIGHT_NO -> binding.appThemeText.text = getString(R.string.light)
            else -> binding.appThemeText.text = getString(R.string.system_default)
        }
    }

    private fun populateAccent() {
        binding.accent.text = when (Accent.fromId(prefs.accent)) {
            Accent.OFF -> getString(R.string.off)
            Accent.SYSTEM -> getString(R.string.accent_system)
            Accent.SAGE -> getString(R.string.accent_sage)
            Accent.SAND -> getString(R.string.accent_sand)
            Accent.SKY -> getString(R.string.accent_sky)
            Accent.ROSE -> getString(R.string.accent_rose)
        }
    }

    private fun selectAccent(accent: Accent) {
        if (prefs.accent == accent.id) return
        prefs.accent = accent.id
        populateAccent()
        requireActivity().recreate()
    }

    private fun showAccentMenu(anchor: View) {
        val options = listOf(
            Accent.OFF to getString(R.string.off),
            Accent.SYSTEM to getString(R.string.accent_system),
            Accent.SAGE to getString(R.string.accent_sage),
            Accent.SAND to getString(R.string.accent_sand),
            Accent.SKY to getString(R.string.accent_sky),
            Accent.ROSE to getString(R.string.accent_rose),
        )

        anchor.showPopupMenu(
            configure = { menu ->
                options.forEach { (accent, label) ->
                    menu.add(Menu.NONE, accent.id, accent.id, label)
                }
            }
        ) { item ->
            selectAccent(Accent.fromId(item.itemId))
        }
    }

    private fun populateFontFamily() {
        val resource = FontFamilyResources.forId(prefs.fontFamily)
        binding.fontFamily.text = getString(resource.labelRes)
    }

    private fun selectFontFamily(family: Int) {
        if (prefs.fontFamily == family) return
        prefs.fontFamily = family
        populateFontFamily()
        requireActivity().recreate()
    }

    private fun showFontDialog() {
        val interTypeface = loadFontOrNull(FontFamilyResources.INTER.fontRes)
        val plexTypeface = loadFontOrNull(FontFamilyResources.PLEX.fontRes)

        var fontDialog: OlDialog? = null
        val dialog = requireContext().createDialog(
            title = R.string.font,
            action = R.string.cancel,
        ) { container ->
            val fontBinding = DialogFontFamilyBinding.inflate(layoutInflater, container, false)
            fontBinding.fontOptionSystem.typeface = Typeface.DEFAULT
            fontBinding.fontOptionInter.typeface = interTypeface ?: Typeface.DEFAULT
            fontBinding.fontOptionPlex.typeface = plexTypeface ?: Typeface.DEFAULT

            fontBinding.fontOptionSystem.setOnClickListener {
                fontDialog?.dismiss()
                selectFontFamily(Constants.FontFamily.SYSTEM)
            }
            fontBinding.fontOptionInter.setOnClickListener {
                fontDialog?.dismiss()
                selectFontFamily(Constants.FontFamily.INTER)
            }
            fontBinding.fontOptionPlex.setOnClickListener {
                fontDialog?.dismiss()
                selectFontFamily(Constants.FontFamily.PLEX)
            }
            fontBinding.root
        }
        fontDialog = dialog
        showDialog(dialog)
    }

    private fun loadFontOrNull(@FontRes fontRes: Int?): Typeface? {
        if (fontRes == null) return null
        return try {
            ResourcesCompat.getFont(requireContext(), fontRes)
        } catch (e: Exception) {
            Log.w("SettingsFragment", "Failed to load font $fontRes", e)
            null
        }
    }

    private fun populateTextSize() {
        binding.textSizeValue.text = formatScale(prefs.textSizeScale)
    }

    private fun toggleBoldFont() {
        prefs.boldFont = !prefs.boldFont
        populateBoldFont()
        requireActivity().recreate()
    }

    private fun populateBoldFont() {
        binding.boldFont.text = getString(if (prefs.boldFont) R.string.on else R.string.off)
    }

    private fun toggleHaptics() {
        prefs.haptics = !prefs.haptics
        populateHaptics()
    }

    private fun populateHaptics() {
        binding.haptics.text = getString(if (prefs.haptics) R.string.on else R.string.off)
    }

    private fun populateScreenTimeOnOff() {
        if (requireContext().appUsagePermissionGranted()) binding.screenTimeOnOff.text = getString(R.string.on)
        else binding.screenTimeOnOff.text = getString(R.string.off)
    }

    private fun populateDistractingApps() {
        val count = prefs.distractingApps.size
        binding.distractingApps.text = if (count == 1) {
            getString(R.string.distracting_app_count, count)
        } else {
            getString(R.string.distracting_apps_count, count)
        }
    }

    private fun populatePauseBeforeApps() {
        binding.pauseBeforeAppsOnOff.text = getString(if (prefs.pauseEnabled) R.string.on else R.string.off)
    }

    private fun togglePauseBeforeApps() {
        if (prefs.pauseEnabled) {
            val change = FrictionChange.Pause(false)
            if (CommitLock.needsWait(prefs.commitLock, change)) {
                dialog?.dismiss()
                dialog = requireContext().showWaitDialog(
                    message = getString(R.string.commit_lock_desc)
                ) {
                    prefs.pauseEnabled = false
                    populatePauseBeforeApps()
                }
                return
            }
            prefs.pauseEnabled = false
            populatePauseBeforeApps()
        } else {
            if (prefs.firstPauseEnable) {
                prefs.firstPauseEnable = false
                prefs.pauseEnabled = true
                populatePauseBeforeApps()
                if (!requireContext().appUsagePermissionGranted()) {
                    showFirstTimePausePermissionDialog()
                }
            } else {
                prefs.pauseEnabled = true
                populatePauseBeforeApps()
            }
        }
    }

    private fun showFirstTimePausePermissionDialog() {
        val dialog = requireContext().createDialog(
            title = R.string.distracting_apps,
            action = R.string.allow,
            message = R.string.pause_usage_access_message,
            neutral = R.string.skip,
            onAction = {
                try {
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                } catch (_: Exception) {
                    requireContext().showToast(getString(R.string.unable_to_open_app))
                }
            },
            onNeutral = {}
        )
        dialog.showRespectingStatusBar()
    }

    private fun populatePauseAllowance() {
        val min = PausePolicy.effectiveAllowanceMin(
            savedMin = prefs.pauseAllowanceMin,
            usageAccessGranted = requireContext().appUsagePermissionGranted(),
        )
        binding.pauseAllowance.text = if (min <= 0) {
            getString(R.string.off)
        } else {
            getString(R.string.pause_allowance_min, min)
        }
    }

    private fun showPauseAllowanceMenu(anchor: View) {
        val choices = listOf(0, 15, 30, 45, 60, 90, 120)
        val items = choices.map { min ->
            min to if (min == 0) getString(R.string.off) else getString(R.string.pause_allowance_min, min)
        }
        anchor.showPopupMenu(
            configure = { menu ->
                items.forEachIndexed { index, pair ->
                    menu.add(Menu.NONE, index, index, pair.second)
                }
            }
        ) { item ->
            val chosen = items.getOrNull(item.itemId)?.first ?: return@showPopupMenu
            val change = FrictionChange.Allowance(
                currentMin = PausePolicy.effectiveAllowanceMin(
                    savedMin = prefs.pauseAllowanceMin,
                    usageAccessGranted = requireContext().appUsagePermissionGranted(),
                ),
                updatedMin = chosen,
            )
            val applyAllowance = {
                if (chosen <= 0) {
                    prefs.pauseAllowanceMin = 0
                    populatePauseAllowance()
                } else if (!requireContext().appUsagePermissionGranted()) {
                    // Keep the chosen value; it only applies once usage access is granted.
                    prefs.pauseAllowanceMin = chosen
                    populatePauseAllowance()
                    showFirstTimePausePermissionDialog()
                } else {
                    prefs.pauseAllowanceMin = chosen
                    populatePauseAllowance()
                }
            }

            if (CommitLock.needsWait(prefs.commitLock, change)) {
                dialog?.dismiss()
                dialog = requireContext().showWaitDialog(
                    message = getString(R.string.commit_lock_desc)
                ) {
                    applyAllowance()
                }
            } else {
                applyAllowance()
            }
        }
    }

    private fun populatePauseEscalate() {
        binding.pauseEscalateOnOff.text = getString(if (prefs.pauseEscalate) R.string.on else R.string.off)
    }

    private fun togglePauseEscalate() {
        if (prefs.pauseEscalate) {
            val change = FrictionChange.Escalate(false)
            if (CommitLock.needsWait(prefs.commitLock, change)) {
                dialog?.dismiss()
                dialog = requireContext().showWaitDialog(
                    message = getString(R.string.commit_lock_desc)
                ) {
                    prefs.pauseEscalate = false
                    populatePauseEscalate()
                }
                return
            }
            prefs.pauseEscalate = false
            populatePauseEscalate()
        } else {
            prefs.pauseEscalate = true
            populatePauseEscalate()
        }
    }

    private fun populatePauseAskWhy() {
        binding.pauseAskWhyOnOff.text = getString(if (prefs.pauseAskWhy) R.string.on else R.string.off)
    }

    private fun togglePauseAskWhy() {
        if (prefs.pauseAskWhy) {
            val change = FrictionChange.AskWhy(false)
            if (CommitLock.needsWait(prefs.commitLock, change)) {
                dialog?.dismiss()
                dialog = requireContext().showWaitDialog(
                    message = getString(R.string.commit_lock_desc)
                ) {
                    prefs.pauseAskWhy = false
                    populatePauseAskWhy()
                }
                return
            }
            prefs.pauseAskWhy = false
            populatePauseAskWhy()
        } else {
            prefs.pauseAskWhy = true
            populatePauseAskWhy()
        }
    }

    private fun populateCommitLock() {
        binding.commitLockOnOff.text = getString(if (prefs.commitLock) R.string.on else R.string.off)
    }

    private fun toggleCommitLock() {
        if (prefs.commitLock) {
            val change = FrictionChange.Lock(false)
            if (CommitLock.needsWait(prefs.commitLock, change)) {
                dialog?.dismiss()
                dialog = requireContext().showWaitDialog(
                    message = getString(R.string.commit_lock_desc)
                ) {
                    prefs.commitLock = false
                    populateCommitLock()
                }
                return
            }
            prefs.commitLock = false
            populateCommitLock()
        } else {
            prefs.commitLock = true
            populateCommitLock()
        }
    }

    private fun isNotificationAccessGranted(): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(requireContext())
            .contains(requireContext().packageName)
    }

    private fun populateHoldNotifications() {
        val isOn = prefs.quietEnabled && isNotificationAccessGranted()
        binding.holdNotificationsOnOff.text = getString(if (isOn) R.string.on else R.string.off)
    }

    private fun toggleHoldNotifications() {
        val accessGranted = isNotificationAccessGranted()
        val currentlyActive = prefs.quietEnabled && accessGranted
        if (currentlyActive) {
            prefs.quietEnabled = false
            populateHoldNotifications()
            ReleaseWorker.cancel(requireContext())
        } else {
            prefs.quietEnabled = true
            if (!accessGranted) {
                populateHoldNotifications()
                showHoldNotificationsAccessDialog()
            } else {
                populateHoldNotifications()
                ReleaseWorker.schedule(requireContext())
            }
        }
    }

    private fun populateQuietReleaseTimes() {
        val times = QuietRelease.parseTimes(prefs.quietReleaseTimes)
        binding.quietReleaseTimes.text = QuietRelease.formatReleaseTimesRow(times)
    }

    private fun showReleaseTimesEditor() {
        val times = QuietRelease.parseTimes(prefs.quietReleaseTimes).toMutableList()
        val items = mutableListOf<String>()
        for (time in times) {
            items.add(QuietRelease.formatTime(time))
        }
        if (times.size < 3) {
            items.add(getString(R.string.quiet_release_add_time))
        }

        val builder = AlertDialog.Builder(requireContext())
            .setTitle(R.string.quiet_release_times)
            .setItems(items.toTypedArray()) { _, which ->
                if (which == times.size && times.size < 3) {
                    showTimePicker(
                        title = R.string.quiet_release_add_time,
                        initialHour = 12,
                        initialMinute = 0,
                    ) { hour, minute ->
                        val newTime = hour * 60 + minute
                        if (newTime !in times) {
                            times.add(newTime)
                            val sorted = times.sorted()
                            prefs.quietReleaseTimes = QuietRelease.formatTimes(sorted)
                            populateQuietReleaseTimes()
                            if (prefs.quietEnabled) {
                                ReleaseWorker.schedule(requireContext())
                            }
                        }
                    }
                } else if (which in times.indices) {
                    val selectedTime = times[which]
                    if (times.size == 1) {
                        showTimePicker(
                            title = R.string.quiet_release_edit_time,
                            initialHour = selectedTime / 60,
                            initialMinute = selectedTime % 60,
                        ) { hour, minute ->
                            times[which] = hour * 60 + minute
                            val sorted = times.distinct().sorted()
                            prefs.quietReleaseTimes = QuietRelease.formatTimes(sorted)
                            populateQuietReleaseTimes()
                            if (prefs.quietEnabled) {
                                ReleaseWorker.schedule(requireContext())
                            }
                        }
                    } else {
                        showTimeOptionsDialog(times, which)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)

        val alertDialog = builder.create()
        dialog?.dismiss()
        dialog = alertDialog
        val window = alertDialog.window
        if (window != null && !prefs.showStatusBar) {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            alertDialog.show()
            window.hideStatusBar()
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        } else {
            alertDialog.show()
        }
    }

    private fun showTimeOptionsDialog(times: MutableList<Int>, index: Int) {
        val selectedTime = times[index]
        val options = arrayOf(
            getString(R.string.quiet_release_edit_time),
            getString(R.string.quiet_release_remove_time),
        )
        val builder = AlertDialog.Builder(requireContext())
            .setTitle(QuietRelease.formatTime(selectedTime))
            .setItems(options) { _, whichOption ->
                when (whichOption) {
                    0 -> {
                        showTimePicker(
                            title = R.string.quiet_release_edit_time,
                            initialHour = selectedTime / 60,
                            initialMinute = selectedTime % 60,
                        ) { hour, minute ->
                            times[index] = hour * 60 + minute
                            val sorted = times.distinct().sorted()
                            prefs.quietReleaseTimes = QuietRelease.formatTimes(sorted)
                            populateQuietReleaseTimes()
                            if (prefs.quietEnabled) {
                                ReleaseWorker.schedule(requireContext())
                            }
                        }
                    }
                    1 -> {
                        times.removeAt(index)
                        val sorted = times.distinct().sorted()
                        prefs.quietReleaseTimes = QuietRelease.formatTimes(sorted)
                        populateQuietReleaseTimes()
                        if (prefs.quietEnabled) {
                            ReleaseWorker.schedule(requireContext())
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)

        val alertDialog = builder.create()
        dialog?.dismiss()
        dialog = alertDialog
        val window = alertDialog.window
        if (window != null && !prefs.showStatusBar) {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            alertDialog.show()
            window.hideStatusBar()
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        } else {
            alertDialog.show()
        }
    }

    private fun showHoldNotificationsAccessDialog() {
        dialog?.dismiss()
        dialog = requireContext().showMessageDialog(
            title = R.string.hold_notifications_from_distracting_apps,
            message = R.string.hold_notifications_access_message,
            action = R.string.allow,
        ) {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (_: Exception) {
                requireContext().showToast(getString(R.string.unable_to_open_app))
            }
        }
    }

    private fun showDistractingAppsDialog() {
        viewLifecycleOwner.lifecycleScope.launch {
            val apps = getAppsList(requireContext(), prefs, includeRegularApps = true, includeHiddenApps = true)
                .filterIsInstance<AppModel.App>()
                .distinctBy { it.appPackage }
                .sortedWith(compareBy(Collator.getInstance()) { it.appLabel })

            if (!isAdded) return@launch

            val installedPackages = apps.map { it.appPackage }.toSet()
            val selectedPackages = DistractingApps.initialSelection(
                installedPackages = installedPackages,
                saved = prefs.distractingApps,
                hasSaved = prefs.hasSavedDistractingApps()
            ).toMutableSet()

            val labels = apps.map { it.appLabel }.toTypedArray()
            val checked = BooleanArray(apps.size) { i -> apps[i].appPackage in selectedPackages }

            val builder = AlertDialog.Builder(requireContext())
                .setTitle(R.string.distracting_apps)
                .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                    checked[which] = isChecked
                    val pkg = apps[which].appPackage
                    if (isChecked) {
                        selectedPackages.add(pkg)
                    } else {
                        selectedPackages.remove(pkg)
                    }
                }
                .setPositiveButton(R.string.okay) { _, _ ->
                    val change = FrictionChange.DistractingApps(
                        current = prefs.distractingApps,
                        updated = selectedPackages,
                    )
                    if (CommitLock.needsWait(prefs.commitLock, change)) {
                        dialog?.dismiss()
                        val appContext = requireContext().applicationContext
                        dialog = requireContext().showWaitDialog(
                            message = getString(R.string.commit_lock_desc)
                        ) {
                            prefs.distractingApps = selectedPackages
                            UsageSnapshotWorker.syncWithDistractingApps(appContext, selectedPackages)
                            if (_binding != null) populateDistractingApps()
                        }
                    } else {
                        prefs.distractingApps = selectedPackages
                        UsageSnapshotWorker.syncWithDistractingApps(requireContext().applicationContext, selectedPackages)
                        populateDistractingApps()
                    }
                }
                .setNegativeButton(R.string.cancel, null)

            val alertDialog = builder.create()
            dialog?.dismiss()
            dialog = alertDialog
            val window = alertDialog.window
            if (window != null && !prefs.showStatusBar) {
                window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                alertDialog.show()
                window.hideStatusBar()
                window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            } else {
                alertDialog.show()
            }
        }
    }

    private fun populateKeyboardText() {
        if (prefs.autoShowKeyboard) binding.autoShowKeyboard.text = getString(R.string.on)
        else binding.autoShowKeyboard.text = getString(R.string.off)
    }

    private fun populateDrawerBottomSearch() {
        if (prefs.drawerBottomSearch) binding.drawerBottomSearch.text = getString(R.string.on)
        else binding.drawerBottomSearch.text = getString(R.string.off)
    }

    private fun toggleDrawerBottomSearch() {
        prefs.drawerBottomSearch = !prefs.drawerBottomSearch
        populateDrawerBottomSearch()
    }

    private fun populateDrawerNewFirst() {
        if (prefs.drawerNewFirst) binding.drawerNewFirst.text = getString(R.string.on)
        else binding.drawerNewFirst.text = getString(R.string.off)
    }

    private fun toggleDrawerNewFirst() {
        prefs.drawerNewFirst = !prefs.drawerNewFirst
        populateDrawerNewFirst()
    }

    private fun populateDrawerLetterStrip() {
        if (prefs.drawerLetterStrip) binding.drawerLetterStrip.text = getString(R.string.on)
        else binding.drawerLetterStrip.text = getString(R.string.off)
    }

    private fun toggleDrawerLetterStrip() {
        prefs.drawerLetterStrip = !prefs.drawerLetterStrip
        populateDrawerLetterStrip()
    }

    private fun populateDrawerRecents() {
        if (prefs.drawerRecents) binding.drawerRecents.text = getString(R.string.on)
        else binding.drawerRecents.text = getString(R.string.off)
    }

    private fun toggleDrawerRecents() {
        prefs.drawerRecents = !prefs.drawerRecents
        populateDrawerRecents()
    }

    private fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private fun populateSearchContacts() {
        if (prefs.searchContacts && !hasContactsPermission()) prefs.searchContacts = false
        binding.searchContacts.text = getString(if (prefs.searchContacts) R.string.on else R.string.off)
    }

    private fun toggleSearchContacts() {
        if (prefs.searchContacts) {
            prefs.searchContacts = false
            populateSearchContacts()
        } else if (hasContactsPermission()) {
            prefs.searchContacts = true
            populateSearchContacts()
        } else {
            requireContext().showMessageDialog(
                title = R.string.search_contacts,
                message = R.string.search_contacts_permission_message,
                action = R.string.allow,
            ) {
                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }
    }

    private fun updateHomeBottomAlignment() {
        if (viewModel.isOlauncherDefault.value != true) {
            requireContext().showToast(getString(R.string.please_set_olauncher_as_default_first), Toast.LENGTH_LONG)
            return
        }
        prefs.homeBottomAlignment = !prefs.homeBottomAlignment
        populateAlignment()
        viewModel.updateHomeAlignment(prefs.homeAlignment)
    }

    private fun populateAlignment() {
        when (prefs.homeAlignment) {
            Gravity.START -> binding.alignment.text = getString(R.string.left)
            Gravity.CENTER -> binding.alignment.text = getString(R.string.center)
            Gravity.END -> binding.alignment.text = getString(R.string.right)
        }
    }

    private fun swapSides() {
        val layout = HandSide.swap(prefs.shortcutSideStart, prefs.homeAlignment)
        prefs.shortcutSideStart = layout.shortcutSideStart
        viewModel.updateHomeAlignment(layout.alignment)
        populateAlignment()
        populateShortcutIcons()
    }

    private fun toggleShortcutIcons() {
        prefs.homeShortcuts = !prefs.homeShortcuts
        populateShortcutIcons()
        viewModel.updateHomeAlignment(prefs.homeAlignment)
    }

    private fun toggleShortcutSide() {
        prefs.shortcutSideStart = !prefs.shortcutSideStart
        populateShortcutIcons()
        viewModel.updateHomeAlignment(prefs.homeAlignment)
    }

    private fun populateShortcutIcons() {
        val shortcutsOn = prefs.homeShortcuts
        binding.shortcutIconsOnOff.text = getString(if (shortcutsOn) R.string.on else R.string.off)
        binding.shortcutCountLayout.visibility = if (shortcutsOn) View.VISIBLE else View.GONE
        binding.shortcutSizeLayout.visibility = if (shortcutsOn) View.VISIBLE else View.GONE
        binding.shortcutSideLayout.visibility = if (shortcutsOn) View.VISIBLE else View.GONE
        binding.shortcutHeightLayout.visibility = if (shortcutsOn) View.VISIBLE else View.GONE
        binding.shortcutIconStyleLayout.visibility = if (shortcutsOn) View.VISIBLE else View.GONE

        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density.takeIf { it > 0f } ?: 1f
        val columnHeightDp = (displayMetrics.heightPixels / density).toInt()
        val viewSizeDp = ShortcutSize.slotSizeDp(prefs.shortcutSizeStep)
        val bottomMarginDp = ShortcutOffset.bottomMarginDp(prefs.shortcutOffsetStep)
        val requested = prefs.shortcutCount
        val fitting = SlotCount.fitting(requested, columnHeightDp, viewSizeDp, bottomMarginDp)
        binding.shortcutCount.text = if (fitting < requested) {
            getString(R.string.icons_fit, requested, fitting)
        } else {
            requested.toString()
        }

        binding.shortcutSize.text = when (prefs.shortcutSizeStep) {
            ShortcutSize.SMALL -> getString(R.string.icon_size_small)
            ShortcutSize.MEDIUM -> getString(R.string.icon_size_medium)
            ShortcutSize.LARGE -> getString(R.string.icon_size_large)
            else -> getString(R.string.icon_size_medium)
        }
        binding.shortcutSide.text = getString(if (prefs.shortcutSideStart) R.string.left else R.string.right)
        binding.shortcutHeight.text = ShortcutOffset.format(prefs.shortcutOffsetStep)
        binding.shortcutIconStyle.text = when (prefs.shortcutIconStyle) {
            ShortcutIconStyle.ORIGINAL -> getString(R.string.icon_style_original)
            ShortcutIconStyle.MONOCHROME -> getString(R.string.icon_style_monochrome)
            ShortcutIconStyle.TEXT_COLOUR -> getString(R.string.icon_style_text_colour)
            else -> getString(R.string.icon_style_original)
        }
    }

    // Home button for recents feature disabled
    // private fun toggleHomeButtonRecents() {
    //     if (!prefs.homeButtonShowRecents && !isAccessServiceEnabled(requireContext())) {
    //         showAccessibilityDialog()
    //         return
    //     }
    //     prefs.homeButtonShowRecents = !prefs.homeButtonShowRecents
    //     populateHomeButtonRecents()
    // }

    // private fun populateHomeButtonRecents() {
    //     binding.homeButtonRecents.text = getString(
    //         if (prefs.homeButtonShowRecents && isAccessServiceEnabled(requireContext())) R.string.on
    //         else R.string.off
    //     )
    // }

    private fun populateLockSettings() {
        binding.toggleLock.text = getString(
            if (prefs.lockModeOn && isAccessServiceEnabled(requireContext())) R.string.on
            else R.string.off
        )
    }

    private fun populateSwipeApps() {
        binding.swipeLeftApp.text = prefs.appNameSwipeLeft
        binding.swipeRightApp.text = if (prefs.swipeRightAction == Prefs.SWIPE_RIGHT_ACTION_TODAY) {
            getString(R.string.today_page)
        } else {
            prefs.appNameSwipeRight.ifEmpty { getString(R.string.choose_app) }
        }
        if (!prefs.swipeLeftEnabled)
            binding.swipeLeftApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
        else
            binding.swipeLeftApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
        if (!prefs.swipeRightEnabled)
            binding.swipeRightApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
        else
            binding.swipeRightApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
    }

    private fun populateTodayCards() {
        val total = TodayCards.all.size
        if (total == 0) {
            binding.todayCards.text = ""
        } else {
            val enabled = TodayCards.all.count { prefs.isTodayCardEnabled(it.key) }
            binding.todayCards.text = "$enabled/$total"
        }
    }

    private fun showSwipeRightMenu(anchor: View) {
        if (!prefs.swipeRightEnabled) {
            requireContext().showToast(getString(R.string.long_press_to_enable))
            return
        }
        anchor.showPopupMenu(
            configure = { menu ->
                menu.add(Menu.NONE, 1, 1, R.string.today_page)
                menu.add(Menu.NONE, 2, 2, R.string.choose_app)
            }
        ) { item ->
            when (item.itemId) {
                1 -> {
                    prefs.swipeRightAction = Prefs.SWIPE_RIGHT_ACTION_TODAY
                    populateSwipeApps()
                }
                2 -> {
                    showAppListIfEnabled(Constants.FLAG_SET_SWIPE_RIGHT_APP)
                }
            }
        }
    }

    private fun showTodayCardsDialog() {
        val cards = TodayCards.all
        val titles = cards.map { getString(it.titleRes) }.toTypedArray()
        val checked = BooleanArray(cards.size) { i -> prefs.isTodayCardEnabled(cards[i].key) }
        todayCardsCheckedState = checked

        val builder = AlertDialog.Builder(requireContext())
            .setTitle(R.string.today_cards)
            .setMultiChoiceItems(titles, checked) { dialogInterface, which, isChecked ->
                val card = cards[which]
                if (card.key == NextUpCard.KEY && isChecked) {
                    if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                        (dialogInterface as? AlertDialog)?.listView?.setItemChecked(which, false)
                        checked[which] = false
                        requireContext().showMessageDialog(
                            title = R.string.next_up,
                            message = R.string.calendar_permission_message,
                            action = R.string.allow,
                        ) {
                            pendingCalendarCardIndex = which
                            calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                        }
                        return@setMultiChoiceItems
                    }
                }
                checked[which] = isChecked
            }
            .setPositiveButton(R.string.okay) { _, _ ->
                cards.forEachIndexed { i, card ->
                    prefs.setTodayCardEnabled(card.key, checked[i])
                }
                populateTodayCards()
            }
            .setNegativeButton(R.string.cancel, null)

        val alertDialog = builder.create()
        alertDialog.setOnDismissListener {
            if (todayCardsDialog === alertDialog) {
                todayCardsDialog = null
                todayCardsCheckedState = null
            }
        }
        dialog?.dismiss()
        dialog = alertDialog
        todayCardsDialog = alertDialog
        val window = alertDialog.window
        if (window != null && !prefs.showStatusBar) {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            alertDialog.show()
            window.hideStatusBar()
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        } else {
            alertDialog.show()
        }
    }

//    private fun populateDigitalWellbeing() {
//        binding.digitalWellbeing.isVisible = requireContext().isPackageInstalled(Constants.DIGITAL_WELLBEING_PACKAGE_NAME).not()
//                && requireContext().isPackageInstalled(Constants.DIGITAL_WELLBEING_SAMSUNG_PACKAGE_NAME).not()
//                && prefs.hideDigitalWellbeing.not()
//    }

    private fun showAppListIfEnabled(flag: Int) {
        if ((flag == Constants.FLAG_SET_SWIPE_LEFT_APP) and !prefs.swipeLeftEnabled) {
            requireContext().showToast(getString(R.string.long_press_to_enable))
            return
        }
        if ((flag == Constants.FLAG_SET_SWIPE_RIGHT_APP) and !prefs.swipeRightEnabled) {
            requireContext().showToast(getString(R.string.long_press_to_enable))
            return
        }
        viewModel.getAppList(true)
        findNavController().navigate(
            R.id.action_settingsFragment_to_appListFragment,
            bundleOf(Constants.Key.FLAG to flag)
        )
    }

    private fun populateNudgeWater() {
        binding.nudgeWater.text = formatWaterInterval(prefs.nudgeWaterIntervalMin)
    }

    private fun populateNudgeWalk() {
        binding.nudgeWalk.text = formatWalkInterval(prefs.nudgeWalkIntervalMin)
    }

    private fun populateActiveHours() {
        binding.nudgeActiveHours.text = NudgeFormatting.formatActiveHours(
            prefs.nudgeWindowStartMin,
            prefs.nudgeWindowEndMin,
        )
    }

    private fun formatWaterInterval(intervalMin: Int): String = when (intervalMin) {
        60 -> getString(R.string.nudge_every_1_h)
        90 -> getString(R.string.nudge_every_1_5_h)
        120 -> getString(R.string.nudge_every_2_h)
        180 -> getString(R.string.nudge_every_3_h)
        else -> getString(R.string.off)
    }

    private fun formatWalkInterval(intervalMin: Int): String = when (intervalMin) {
        120 -> getString(R.string.nudge_every_2_h)
        180 -> getString(R.string.nudge_every_3_h)
        240 -> getString(R.string.nudge_every_4_h)
        else -> getString(R.string.off)
    }

    private fun showNudgeWaterMenu(anchor: View) {
        val intervals = listOf(
            0 to getString(R.string.off),
            60 to getString(R.string.nudge_every_1_h),
            90 to getString(R.string.nudge_every_1_5_h),
            120 to getString(R.string.nudge_every_2_h),
            180 to getString(R.string.nudge_every_3_h),
        )
        anchor.showPopupMenu(
            configure = { menu ->
                intervals.forEachIndexed { index, pair ->
                    menu.add(Menu.NONE, index, index, pair.second)
                }
            }
        ) { item ->
            val chosen = intervals.getOrNull(item.itemId)?.first ?: return@showPopupMenu
            updateNudgeInterval(Nudges.WATER, chosen)
        }
    }

    private fun showNudgeWalkMenu(anchor: View) {
        val intervals = listOf(
            0 to getString(R.string.off),
            120 to getString(R.string.nudge_every_2_h),
            180 to getString(R.string.nudge_every_3_h),
            240 to getString(R.string.nudge_every_4_h),
        )
        anchor.showPopupMenu(
            configure = { menu ->
                intervals.forEachIndexed { index, pair ->
                    menu.add(Menu.NONE, index, index, pair.second)
                }
            }
        ) { item ->
            val chosen = intervals.getOrNull(item.itemId)?.first ?: return@showPopupMenu
            updateNudgeInterval(Nudges.WALK, chosen)
        }
    }

    private fun updateNudgeInterval(type: String, intervalMin: Int) {
        if (intervalMin <= 0) {
            when (type) {
                Nudges.WATER -> {
                    prefs.nudgeWaterIntervalMin = 0
                    NudgeScheduler.cancel(requireContext(), Nudges.WATER)
                    Nudges.cancel(requireContext(), Nudges.WATER)
                    populateNudgeWater()
                }
                Nudges.WALK -> {
                    prefs.nudgeWalkIntervalMin = 0
                    NudgeScheduler.cancel(requireContext(), Nudges.WALK)
                    Nudges.cancel(requireContext(), Nudges.WALK)
                    populateNudgeWalk()
                }
            }
            return
        }

        NotificationPermission.request(this) { isGranted ->
            if (!isAdded) return@request
            if (isGranted) {
                when (type) {
                    Nudges.WATER -> {
                        prefs.nudgeWaterIntervalMin = intervalMin
                        NudgeScheduler.schedule(requireContext(), Nudges.WATER)
                        populateNudgeWater()
                    }
                    Nudges.WALK -> {
                        prefs.nudgeWalkIntervalMin = intervalMin
                        NudgeScheduler.schedule(requireContext(), Nudges.WALK)
                        populateNudgeWalk()
                    }
                }
            } else {
                when (type) {
                    Nudges.WATER -> {
                        prefs.nudgeWaterIntervalMin = 0
                        NudgeScheduler.cancel(requireContext(), Nudges.WATER)
                        Nudges.cancel(requireContext(), Nudges.WATER)
                        populateNudgeWater()
                    }
                    Nudges.WALK -> {
                        prefs.nudgeWalkIntervalMin = 0
                        NudgeScheduler.cancel(requireContext(), Nudges.WALK)
                        Nudges.cancel(requireContext(), Nudges.WALK)
                        populateNudgeWalk()
                    }
                }
                requireContext().showToast(R.string.notifications_off_for_thumbline)
            }
        }
    }

    private fun populateNudgeSocial() {
        binding.nudgeSocial.text = formatSocialThreshold(prefs.nudgeSocialThresholdMin)
    }

    private fun formatSocialThreshold(thresholdMin: Int): String = when (thresholdMin) {
        15, 30, 45, 60, 90 -> getString(R.string.nudge_social_after_min, thresholdMin)
        else -> getString(R.string.off)
    }

    private fun showNudgeSocialMenu(anchor: View) {
        val thresholds = listOf(0, 15, 30, 45, 60, 90)
        val items = thresholds.map { min ->
            min to if (min == 0) getString(R.string.off) else getString(R.string.nudge_social_after_min, min)
        }
        anchor.showPopupMenu(
            configure = { menu ->
                items.forEachIndexed { index, pair ->
                    menu.add(Menu.NONE, index, index, pair.second)
                }
            }
        ) { item ->
            val chosen = items.getOrNull(item.itemId)?.first ?: return@showPopupMenu
            updateSocialNudgeThreshold(chosen)
        }
    }

    private fun updateSocialNudgeThreshold(thresholdMin: Int) {
        if (thresholdMin <= 0) {
            prefs.nudgeSocialThresholdMin = 0
            prefs.nudgeSocialLastBucket = -1
            NudgeScheduler.cancel(requireContext(), Nudges.SOCIAL)
            Nudges.cancel(requireContext(), Nudges.SOCIAL)
            populateNudgeSocial()
            return
        }

        NotificationPermission.request(this) { isGranted ->
            if (!isAdded) return@request
            if (!isGranted) {
                prefs.nudgeSocialThresholdMin = 0
                prefs.nudgeSocialLastBucket = -1
                NudgeScheduler.cancel(requireContext(), Nudges.SOCIAL)
                Nudges.cancel(requireContext(), Nudges.SOCIAL)
                populateNudgeSocial()
                requireContext().showToast(R.string.notifications_off_for_thumbline)
                return@request
            }

            if (!requireContext().appUsagePermissionGranted()) {
                prefs.nudgeSocialThresholdMin = 0
                prefs.nudgeSocialLastBucket = -1
                NudgeScheduler.cancel(requireContext(), Nudges.SOCIAL)
                Nudges.cancel(requireContext(), Nudges.SOCIAL)
                populateNudgeSocial()
                requireContext().showToast(R.string.usage_access_off_for_thumbline)
                showSocialNudgeUsageAccessDialog()
                return@request
            }

            prefs.nudgeSocialThresholdMin = thresholdMin
            prefs.nudgeSocialLastBucket = -1
            NudgeScheduler.scheduleSocial(requireContext())
            populateNudgeSocial()
        }
    }

    private fun showSocialNudgeUsageAccessDialog() {
        requireContext().showMessageDialog(
            R.string.nudge_social,
            R.string.nudge_social_usage_access_message,
            R.string.allow,
        ) {
            try {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (_: Exception) {
                requireContext().showToast(getString(R.string.unable_to_open_app))
            }
        }
    }

    private fun showActiveHoursDialog() {
        val currentStart = prefs.nudgeWindowStartMin
        val currentEnd = prefs.nudgeWindowEndMin
        showTimePicker(
            title = R.string.nudge_start_time,
            initialHour = currentStart / 60,
            initialMinute = currentStart % 60,
        ) { startHour, startMinute ->
            val chosenStart = startHour * 60 + startMinute
            showTimePicker(
                title = R.string.nudge_end_time,
                initialHour = currentEnd / 60,
                initialMinute = currentEnd % 60,
            ) { endHour, endMinute ->
                val chosenEnd = endHour * 60 + endMinute
                if (NudgeFormatting.isValidActiveHours(chosenStart, chosenEnd)) {
                    prefs.nudgeWindowStartMin = chosenStart
                    prefs.nudgeWindowEndMin = chosenEnd
                    populateActiveHours()
                    NudgeScheduler.rescheduleAll(requireContext())
                } else {
                    requireContext().showToast(R.string.nudge_active_hours_invalid)
                }
            }
        }
    }

    private fun showTimePicker(
        @StringRes title: Int,
        initialHour: Int,
        initialMinute: Int,
        onTimeSet: (Int, Int) -> Unit,
    ) {
        val picker = TimePickerDialog(
            requireContext(),
            { _, hour, minute -> onTimeSet(hour, minute) },
            initialHour,
            initialMinute,
            DateFormat.is24HourFormat(requireContext()),
        )
        picker.setTitle(getString(title))
        val window = picker.window
        if (window != null && !prefs.showStatusBar) {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            picker.show()
            window.hideStatusBar()
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        } else {
            picker.show()
        }
    }

    private fun launchExportBackup() {
        viewModel.isAwaitingActivityResult = true
        exportBackupLauncher.launch(Backup.defaultBackupFileName())
    }

    private fun exportBackup(uri: Uri) {
        val appContext = context?.applicationContext ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val success = try {
                Backup.export(appContext, uri)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
            withContext(Dispatchers.Main) {
                appContext.showToast(if (success) R.string.backup_exported else R.string.backup_export_failed)
            }
        }
    }

    private fun launchImportBackup() {
        viewModel.isAwaitingActivityResult = true
        importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
    }

    private fun handleImportBackup(uri: Uri) {
        val appContext = context?.applicationContext ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val backupData = try {
                Backup.readBackup(appContext, uri)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                if (backupData == null) {
                    appContext.showToast(R.string.not_a_thumbline_backup)
                    return@withContext
                }
                showImportConfirmationDialog(backupData)
            }
        }
    }

    private fun showImportConfirmationDialog(backupData: BackupData) {
        val message = if (backupData.appVersion.isNotBlank()) {
            getString(R.string.backup_restore_confirm, backupData.appVersion)
        } else {
            getString(R.string.backup_restore_confirm_no_version)
        }
        dialog?.dismiss()
        dialog = requireContext().showMessageDialog(
            title = R.string.import_backup,
            message = message,
            action = R.string.replace,
        ) {
            confirmImportBackup(backupData)
        }
    }

    private fun confirmImportBackup(backupData: BackupData) {
        val before = FrictionSettings(
            pauseEnabled = prefs.pauseEnabled,
            distractingApps = prefs.distractingApps,
            allowanceMin = prefs.pauseAllowanceMin,
            escalate = prefs.pauseEscalate,
            askWhy = prefs.pauseAskWhy,
            commitLock = prefs.commitLock,
        )
        val after = FrictionSettings.fromPrefsMap(backupData.prefs)
        if (CommitLock.restoreNeedsWait(prefs.commitLock, before, after)) {
            dialog?.dismiss()
            dialog = requireContext().showWaitDialog(
                message = getString(R.string.commit_lock_desc)
            ) {
                restoreBackupNow(backupData)
            }
        } else {
            restoreBackupNow(backupData)
        }
    }

    private fun restoreBackupNow(backupData: BackupData) {
        val appContext = context?.applicationContext ?: return
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                Backup.restore(appContext, backupData)
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        appContext.showToast(R.string.not_a_thumbline_backup)
                    }
                }
                return@launch
            }
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                WordCaptureActivity.setEnabled(appContext, prefs.germanEnabled)
                NudgeScheduler.rescheduleAll(appContext)
                UsageSnapshotWorker.syncWithDistractingApps(appContext, prefs.distractingApps)
                activity?.recreate()
            }
        }
    }

    private fun toggleGerman() {
        prefs.germanEnabled = !prefs.germanEnabled
        WordCaptureActivity.setEnabled(requireContext(), prefs.germanEnabled)
        populateGerman()
    }

    private fun toggleGermanLookup() {
        prefs.germanLookup = !prefs.germanLookup
        populateGerman()
    }

    private fun toggleGermanPauseWord() {
        prefs.germanPauseWord = !prefs.germanPauseWord
        populateGerman()
    }

    private fun toggleGermanUi() {
        prefs.germanUi = !prefs.germanUi
        populateGerman()
    }

    private fun populateGerman() {
        binding.germanOnOff.text = getString(if (prefs.germanEnabled) R.string.on else R.string.off)
        binding.germanClockLayout.visibility = if (prefs.germanEnabled) View.VISIBLE else View.GONE
        binding.germanClock.text = when (prefs.germanClock) {
            Prefs.GERMAN_CLOCK_DEUTSCH -> getString(R.string.deutsch_de)
            Prefs.GERMAN_CLOCK_IN_WORTEN -> getString(R.string.in_worten_de)
            else -> getString(R.string.system_default)
        }
        binding.germanLookupLayout.visibility = if (prefs.germanEnabled) View.VISIBLE else View.GONE
        binding.germanLookupOnOff.text = getString(if (prefs.germanLookup) R.string.on else R.string.off)
        binding.germanPauseWordLayout.visibility = if (prefs.germanEnabled) View.VISIBLE else View.GONE
        binding.germanPauseWordOnOff.text = getString(if (prefs.germanPauseWord) R.string.on else R.string.off)
        binding.germanUiLayout.visibility = if (prefs.germanEnabled) View.VISIBLE else View.GONE
        binding.germanUiOnOff.text = getString(if (prefs.germanUi) R.string.on else R.string.off)
    }

    private fun showGermanClockMenu(anchor: View) {
        val options = listOf(
            Prefs.GERMAN_CLOCK_SYSTEM to getString(R.string.system_default),
            Prefs.GERMAN_CLOCK_DEUTSCH to getString(R.string.deutsch_de),
            Prefs.GERMAN_CLOCK_IN_WORTEN to getString(R.string.in_worten_de),
        )
        anchor.showPopupMenu(
            configure = { menu ->
                options.forEach { (id, label) ->
                    menu.add(Menu.NONE, id, id, label)
                }
            }
        ) { item ->
            prefs.germanClock = item.itemId
            populateGerman()
        }
    }

    private fun populateFooter() {
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
