package app.olauncher.ui

import app.olauncher.ui.settings.adjustShortcutHeightPreview
import app.olauncher.ui.settings.adjustTextSizePreview
import app.olauncher.ui.settings.applyShortcutOffsetStep
import app.olauncher.ui.settings.applyTextSizeScale
import app.olauncher.ui.settings.askContextLineNotificationAccess
import app.olauncher.ui.settings.confirmImportBackup
import app.olauncher.ui.settings.copySystemGreyCommand
import app.olauncher.ui.settings.cycleHomeDarkness
import app.olauncher.ui.settings.cyclePhotoWallpaperTarget
import app.olauncher.ui.settings.cycleTextBrightness
import app.olauncher.ui.settings.enableAutoBackup
import app.olauncher.ui.settings.enableUseMyLocation
import app.olauncher.ui.settings.exportBackup
import app.olauncher.ui.settings.formatMinutes
import app.olauncher.ui.settings.formatScale
import app.olauncher.ui.settings.formatSocialThreshold
import app.olauncher.ui.settings.formatWalkInterval
import app.olauncher.ui.settings.formatWaterInterval
import app.olauncher.ui.settings.handleAddedPhotos
import app.olauncher.ui.settings.handleImportBackup
import app.olauncher.ui.settings.handlePickedPhotos
import app.olauncher.ui.settings.handleUseMyLocation
import app.olauncher.ui.settings.hasContactsPermission
import app.olauncher.ui.settings.isNotificationAccessGranted
import app.olauncher.ui.settings.launchAutoBackupFolderPicker
import app.olauncher.ui.settings.launchExportBackup
import app.olauncher.ui.settings.launchImportBackup
import app.olauncher.ui.settings.loadFontOrNull
import app.olauncher.ui.settings.nextPhotoNow
import app.olauncher.ui.settings.openPhotoPicker
import app.olauncher.ui.settings.openPhotoPickerForAdd
import app.olauncher.ui.settings.pendingOrCurrentShortcutOffsetStep
import app.olauncher.ui.settings.pendingOrCurrentTextSizeScale
import app.olauncher.ui.settings.populateAccent
import app.olauncher.ui.settings.populateActiveHours
import app.olauncher.ui.settings.populateAlignment
import app.olauncher.ui.settings.populateAppThemeText
import app.olauncher.ui.settings.populateAutoBackup
import app.olauncher.ui.settings.populateBatteryMode
import app.olauncher.ui.settings.populateBoldFont
import app.olauncher.ui.settings.populateCommitLock
import app.olauncher.ui.settings.populateContextLine
import app.olauncher.ui.settings.populateCurrencyRates
import app.olauncher.ui.settings.populateDateTime
import app.olauncher.ui.settings.populateDistractingApps
import app.olauncher.ui.settings.populateDrawerBottomSearch
import app.olauncher.ui.settings.populateDrawerLetterStrip
import app.olauncher.ui.settings.populateDrawerNewFirst
import app.olauncher.ui.settings.populateDrawerRecents
import app.olauncher.ui.settings.populateFontFamily
import app.olauncher.ui.settings.populateGerman
import app.olauncher.ui.settings.populateGlowStrength
import app.olauncher.ui.settings.populateHaptics
import app.olauncher.ui.settings.populateHealthConnect
import app.olauncher.ui.settings.populateHoldNotifications
import app.olauncher.ui.settings.populateHomeDarkness
import app.olauncher.ui.settings.populateHomeNextEvent
import app.olauncher.ui.settings.populateHomeSets
import app.olauncher.ui.settings.populateInfoLine
import app.olauncher.ui.settings.populateIntentionOnHome
import app.olauncher.ui.settings.populateKeyboardText
import app.olauncher.ui.settings.populateLockSettings
import app.olauncher.ui.settings.populateMusicPulse
import app.olauncher.ui.settings.populateNudgeSocial
import app.olauncher.ui.settings.populateNudgeWalk
import app.olauncher.ui.settings.populateNudgeWater
import app.olauncher.ui.settings.populatePauseAllowance
import app.olauncher.ui.settings.populatePauseAskWhy
import app.olauncher.ui.settings.populatePauseBeforeApps
import app.olauncher.ui.settings.populatePauseEscalate
import app.olauncher.ui.settings.populatePhotoWallpaperText
import app.olauncher.ui.settings.populateQuietReleaseTimes
import app.olauncher.ui.settings.populateScreenTimeCue
import app.olauncher.ui.settings.populateScreenTimeOnOff
import app.olauncher.ui.settings.populateSearchContacts
import app.olauncher.ui.settings.populateSearchEngine
import app.olauncher.ui.settings.populateShortcutIcons
import app.olauncher.ui.settings.populateStatusBar
import app.olauncher.ui.settings.populateSwipeApps
import app.olauncher.ui.settings.populateSystemGrey
import app.olauncher.ui.settings.populateTextBrightness
import app.olauncher.ui.settings.populateTextContrast
import app.olauncher.ui.settings.populateTextSize
import app.olauncher.ui.settings.populateTodayCards
import app.olauncher.ui.settings.populateTodayOrder
import app.olauncher.ui.settings.populateWeather
import app.olauncher.ui.settings.populateWeatherEffects
import app.olauncher.ui.settings.populateWindDown
import app.olauncher.ui.settings.populateWindDownGrey
import app.olauncher.ui.settings.previousPhotoNow
import app.olauncher.ui.settings.restoreBackupNow
import app.olauncher.ui.settings.searchCity
import app.olauncher.ui.settings.selectAccent
import app.olauncher.ui.settings.selectFontFamily
import app.olauncher.ui.settings.setAppTheme
import app.olauncher.ui.settings.setupGlowStrength
import app.olauncher.ui.settings.showAccentMenu
import app.olauncher.ui.settings.showAccessibilityDialog
import app.olauncher.ui.settings.showActiveHoursDialog
import app.olauncher.ui.settings.showAlignmentMenu
import app.olauncher.ui.settings.showAppListIfEnabled
import app.olauncher.ui.settings.showAppThemeMenu
import app.olauncher.ui.settings.showBatteryModeMenu
import app.olauncher.ui.settings.showCitySearchDialog
import app.olauncher.ui.settings.showCustomSearchEngineDialog
import app.olauncher.ui.settings.showDateTimeMenu
import app.olauncher.ui.settings.showDialog
import app.olauncher.ui.settings.showDistractingAppsDialog
import app.olauncher.ui.settings.showFirstTimePausePermissionDialog
import app.olauncher.ui.settings.showFontDialog
import app.olauncher.ui.settings.showGermanClockMenu
import app.olauncher.ui.settings.showHoldNotificationsAccessDialog
import app.olauncher.ui.settings.showHomeAppsNumMenu
import app.olauncher.ui.settings.showHomeSetSlotsDialog
import app.olauncher.ui.settings.showHomeSetStartPicker
import app.olauncher.ui.settings.showImportConfirmationDialog
import app.olauncher.ui.settings.showNudgeSocialMenu
import app.olauncher.ui.settings.showNudgeWalkMenu
import app.olauncher.ui.settings.showNudgeWaterMenu
import app.olauncher.ui.settings.showPauseAllowanceMenu
import app.olauncher.ui.settings.showPauseOffChoices
import app.olauncher.ui.settings.showPhotoWallpaperFrequencyDialog
import app.olauncher.ui.settings.showPhotoWallpaperMenu
import app.olauncher.ui.settings.showPlaceSelectionDialog
import app.olauncher.ui.settings.showReleaseTimesEditor
import app.olauncher.ui.settings.showRemovePhotosDialog
import app.olauncher.ui.settings.showScreenTimeStyleMenu
import app.olauncher.ui.settings.showScreenTimeTargetMenu
import app.olauncher.ui.settings.showSearchEngineMenu
import app.olauncher.ui.settings.showShortcutCountDialog
import app.olauncher.ui.settings.showShortcutHeightDialog
import app.olauncher.ui.settings.showShortcutIconStyleDialog
import app.olauncher.ui.settings.showShortcutSizeDialog
import app.olauncher.ui.settings.showSocialNudgeUsageAccessDialog
import app.olauncher.ui.settings.showSwipeRightMenu
import app.olauncher.ui.settings.showTextSizeDialog
import app.olauncher.ui.settings.showTimeOptionsDialog
import app.olauncher.ui.settings.showTimePicker
import app.olauncher.ui.settings.showToast
import app.olauncher.ui.settings.showTodayCardsDialog
import app.olauncher.ui.settings.showWeatherMenu
import app.olauncher.ui.settings.showWindDownMenu
import app.olauncher.ui.settings.showWindDownTimePicker
import app.olauncher.ui.settings.swapSides
import app.olauncher.ui.settings.syncWordShareEntries
import app.olauncher.ui.settings.toggleAutoBackup
import app.olauncher.ui.settings.toggleBoldFont
import app.olauncher.ui.settings.toggleCommitLock
import app.olauncher.ui.settings.toggleContextLine
import app.olauncher.ui.settings.toggleCurrencyRates
import app.olauncher.ui.settings.toggleDateTime
import app.olauncher.ui.settings.toggleDrawerBottomSearch
import app.olauncher.ui.settings.toggleDrawerLetterStrip
import app.olauncher.ui.settings.toggleDrawerNewFirst
import app.olauncher.ui.settings.toggleDrawerRecents
import app.olauncher.ui.settings.toggleGerman
import app.olauncher.ui.settings.toggleGermanLookup
import app.olauncher.ui.settings.toggleGermanPauseWord
import app.olauncher.ui.settings.toggleGermanScreenPick
import app.olauncher.ui.settings.toggleGermanSpellSuggest
import app.olauncher.ui.settings.toggleGermanUi
import app.olauncher.ui.settings.toggleHaptics
import app.olauncher.ui.settings.toggleHealthConnect
import app.olauncher.ui.settings.toggleHoldNotifications
import app.olauncher.ui.settings.toggleHomeNextEvent
import app.olauncher.ui.settings.toggleHomeSets
import app.olauncher.ui.settings.toggleInfoLine
import app.olauncher.ui.settings.toggleIntentionOnHome
import app.olauncher.ui.settings.toggleKeyboardText
import app.olauncher.ui.settings.toggleMusicPulse
import app.olauncher.ui.settings.togglePauseAskWhy
import app.olauncher.ui.settings.togglePauseBeforeApps
import app.olauncher.ui.settings.togglePauseEscalate
import app.olauncher.ui.settings.togglePhotoWallpaperHomeMenu
import app.olauncher.ui.settings.toggleSearchContacts
import app.olauncher.ui.settings.toggleShortcutIcons
import app.olauncher.ui.settings.toggleShortcutSide
import app.olauncher.ui.settings.toggleStatusBar
import app.olauncher.ui.settings.toggleSwipeLeft
import app.olauncher.ui.settings.toggleSwipeRight
import app.olauncher.ui.settings.toggleSystemGrey
import app.olauncher.ui.settings.toggleTextContrast
import app.olauncher.ui.settings.toggleTodayOrder
import app.olauncher.ui.settings.toggleWeatherEffects
import app.olauncher.ui.settings.toggleWindDown
import app.olauncher.ui.settings.toggleWindDownGrey
import app.olauncher.ui.settings.turnOffPhotoWallpaper
import app.olauncher.ui.settings.updateHomeBottomAlignment
import app.olauncher.ui.settings.updateNudgeInterval
import app.olauncher.ui.settings.updateSocialNudgeThreshold
import app.olauncher.ui.settings.updateTextSizeSample
import app.olauncher.ui.settings.updateTheme
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
import android.widget.SeekBar
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
import app.olauncher.helper.MusicPulse
import app.olauncher.helper.HandSide
import app.olauncher.helper.HomeSet
import app.olauncher.helper.HomeSets
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
import app.olauncher.wellbeing.HealthRepository
import app.olauncher.wellbeing.NudgeFormatting
import app.olauncher.wellbeing.NudgeScheduler
import app.olauncher.wellbeing.Nudges
import app.olauncher.wellbeing.NotificationPermission
import androidx.work.ExistingWorkPolicy
import app.olauncher.quiet.QuietRelease
import app.olauncher.quiet.ReleaseWorker
import app.olauncher.data.store.AutoBackupStatus
import app.olauncher.data.store.AutoBackupWorker
import app.olauncher.data.store.Backup
import app.olauncher.data.store.BackupData
import app.olauncher.data.store.WordLookup
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
import app.olauncher.helper.RatesWorker
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
import app.olauncher.wellbeing.SystemGrey
import app.olauncher.wellbeing.WindDownWorker
import app.olauncher.helper.WordCaptureActivity
import app.olauncher.helper.WordScreenService
import app.olauncher.helper.OlDialog
import app.olauncher.helper.showMessageDialog
import app.olauncher.helper.showPopupMenu
import app.olauncher.helper.showStatusBar
import app.olauncher.helper.copyToClipboard
import app.olauncher.helper.showToast
import app.olauncher.helper.showWaitDialog
import app.olauncher.wellbeing.CommitLock
import app.olauncher.wellbeing.DistractingApps
import app.olauncher.wellbeing.FrictionChange
import app.olauncher.wellbeing.FrictionSettings
import app.olauncher.wellbeing.PausePolicy
import app.olauncher.wellbeing.PauseSnooze
import java.time.ZoneId
import app.olauncher.wellbeing.ScreenTimeCue
import app.olauncher.wellbeing.restoreNeedsWait
import app.olauncher.wellbeing.UsageSnapshotWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

class SettingsFragment : BaseFragment(), View.OnClickListener, View.OnLongClickListener {

    internal lateinit var prefs: Prefs
    internal lateinit var viewModel: MainViewModel

    internal var _binding: FragmentSettingsBinding? = null
    internal val binding get() = _binding!!
    internal var dialog: AlertDialog? = null

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
    internal var todayCardsDialog: AlertDialog? = null
    internal var todayCardsCheckedState: BooleanArray? = null

    internal val calendarPermissionLauncher = registerForActivityResult(
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
        populateHomeSets()
        populatePhotoWallpaperText()
        populateKeyboardText()
        populateDrawerBottomSearch()
        populateDrawerNewFirst()
        populateDrawerLetterStrip()
        populateDrawerRecents()
        populateAutoBackup()
        populateSearchContacts()
        populateCurrencyRates()
        populateScreenTimeOnOff()
        populateScreenTimeCue()
        populateDistractingApps()
        populatePauseBeforeApps()
        populatePauseAllowance()
        populatePauseEscalate()
        populatePauseAskWhy()
        populateCommitLock()
        populateWindDown()
        populateWindDownGrey()
        populateSystemGrey()
        populateHoldNotifications()
        populateHealthConnect()
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
        populateTextContrast()
        populateHomeDarkness()
        populateTextBrightness()
        populateMusicPulse()
        setupGlowStrength()
        populateWeatherEffects()
        populateAlignment()
        populateShortcutIcons()
        populateStatusBar()
        populateDateTime()
        populateBatteryMode()
        populateInfoLine()
        populateContextLine()
        populateHomeNextEvent()
        populateSearchEngine()
        populateSwipeApps()
        populateTodayCards()
        populateTodayOrder()
        populateIntentionOnHome()
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

    private data class RuntimeSettingsRow(
        val sectionKey: String,
        val rowView: View,
        val labelView: TextView,
        val viewId: Int,
    )

    private var runtimeRows: List<RuntimeSettingsRow> = emptyList()

    internal fun getFirstTextView(parent: View): TextView {
        if (parent is ViewGroup) {
            for (i in 0 until parent.childCount) {
                val child = parent.getChildAt(i)
                if (child is TextView) return child
            }
        }
        error("Expected TextView in parent $parent")
    }

    internal fun initRuntimeRows() {
        runtimeRows = listOf(
            // Home
            RuntimeSettingsRow(SettingsSections.KEY_HOME, binding.homeAppsNum.parent as View, getFirstTextView(binding.homeAppsNum.parent as View), R.id.homeAppsNum),
            RuntimeSettingsRow(SettingsSections.KEY_HOME, binding.dateTime.parent as View, getFirstTextView(binding.dateTime.parent as View), R.id.dateTime),
            RuntimeSettingsRow(SettingsSections.KEY_HOME, binding.batteryModeLayout, binding.batteryModeTitle, R.id.batteryMode),
            RuntimeSettingsRow(SettingsSections.KEY_HOME, binding.infoLineLayout, binding.infoLineTitle, R.id.infoLine),
            RuntimeSettingsRow(SettingsSections.KEY_HOME, binding.homeSetsLayout, binding.homeSetsTitle, R.id.homeSets),
            RuntimeSettingsRow(SettingsSections.KEY_HOME, binding.editMorningAppsLayout, binding.editMorningAppsTitle, R.id.editMorningAppsLayout),
            RuntimeSettingsRow(SettingsSections.KEY_HOME, binding.editEveningAppsLayout, binding.editEveningAppsTitle, R.id.editEveningAppsLayout),
            RuntimeSettingsRow(SettingsSections.KEY_HOME, binding.morningStartLayout, binding.morningStartTitle, R.id.morningStart),
            RuntimeSettingsRow(SettingsSections.KEY_HOME, binding.dayStartLayout, binding.dayStartTitle, R.id.dayStart),
            RuntimeSettingsRow(SettingsSections.KEY_HOME, binding.eveningStartLayout, binding.eveningStartTitle, R.id.eveningStart),
            RuntimeSettingsRow(SettingsSections.KEY_HOME, binding.contextLineLayout, binding.contextLineTitle, R.id.contextLineValue),
            RuntimeSettingsRow(SettingsSections.KEY_HOME, binding.homeNextEventLayout, binding.homeNextEventTitle, R.id.homeNextEventValue),
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
            RuntimeSettingsRow(SettingsSections.KEY_DRAWER, binding.currencyRatesLayout, binding.currencyRatesTitle, R.id.currencyRates),

            // Wellbeing
            RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.distractingAppsLayout, binding.distractingAppsTitle, R.id.distractingApps),
            RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.pauseBeforeAppsLayout, binding.pauseBeforeAppsTitle, R.id.pauseBeforeAppsOnOff),
            RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.pauseAllowanceLayout, binding.pauseAllowanceTitle, R.id.pauseAllowance),
            RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.pauseEscalateLayout, binding.pauseEscalateTitle, R.id.pauseEscalateOnOff),
            RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.pauseAskWhyLayout, binding.pauseAskWhyTitle, R.id.pauseAskWhyOnOff),
            RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.windDownLayout, binding.windDownTitle, R.id.windDown),
            RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.windDownGreyLayout, binding.windDownGreyTitle, R.id.windDownGreyOnOff),
            RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.systemGreyLayout, binding.systemGreyTitle, R.id.systemGreyOnOff),
            RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.commitLockLayout, binding.commitLockTitle, R.id.commitLockOnOff),
            RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.holdNotificationsLayout, binding.holdNotificationsTitle, R.id.holdNotificationsOnOff),
            RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.healthConnectLayout, binding.healthConnectTitle, R.id.healthConnectOnOff),
            RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.quietReleaseTimesLayout, binding.quietReleaseTimesTitle, R.id.quietReleaseTimes),
            RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.screenTimeLayout, getFirstTextView(binding.screenTimeLayout), R.id.screenTimeOnOff),
            RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.screenTimeStyleLayout, binding.screenTimeStyleTitle, R.id.screenTimeStyle),
            RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.screenTimeTargetLayout, binding.screenTimeTargetTitle, R.id.screenTimeTarget),
            RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.nudgeWaterLayout, binding.nudgeWaterTitle, R.id.nudgeWater),
            RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.nudgeWalkLayout, binding.nudgeWalkTitle, R.id.nudgeWalk),
            RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.nudgeSocialLayout, binding.nudgeSocialTitle, R.id.nudgeSocial),
            RuntimeSettingsRow(SettingsSections.KEY_WELLBEING, binding.nudgeActiveHoursLayout, binding.nudgeActiveHoursTitle, R.id.nudgeActiveHours),

            // Today
            RuntimeSettingsRow(SettingsSections.KEY_TODAY, binding.todayCardsLayout, binding.todayCardsTitle, R.id.todayCards),
            RuntimeSettingsRow(SettingsSections.KEY_TODAY, binding.todayOrderLayout, binding.todayOrderTitle, R.id.todayOrder),
            RuntimeSettingsRow(SettingsSections.KEY_TODAY, binding.intentionOnHomeLayout, binding.intentionOnHomeTitle, R.id.intentionOnHome),
            RuntimeSettingsRow(SettingsSections.KEY_TODAY, binding.weatherLayout, binding.weatherTitle, R.id.weather),

            // Deutsch
            RuntimeSettingsRow(SettingsSections.KEY_DEUTSCH, binding.germanLayout, binding.germanTitle, R.id.germanOnOff),
            RuntimeSettingsRow(SettingsSections.KEY_DEUTSCH, binding.germanClockLayout, binding.germanClockTitle, R.id.germanClock),
            RuntimeSettingsRow(SettingsSections.KEY_DEUTSCH, binding.germanLookupLayout, binding.germanLookupTitle, R.id.germanLookupOnOff),
            RuntimeSettingsRow(SettingsSections.KEY_DEUTSCH, binding.germanSpellSuggestLayout, binding.germanSpellSuggestTitle, R.id.germanSpellSuggestOnOff),
            RuntimeSettingsRow(SettingsSections.KEY_DEUTSCH, binding.germanScreenPickLayout, binding.germanScreenPickTitle, R.id.germanScreenPickOnOff),
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
            RuntimeSettingsRow(SettingsSections.KEY_APPEARANCE, binding.textContrastLayout, binding.textContrastTitle, R.id.textContrast),
            RuntimeSettingsRow(SettingsSections.KEY_APPEARANCE, binding.homeWashLayout, binding.homeWashTitle, R.id.homeDarkness),
            RuntimeSettingsRow(SettingsSections.KEY_APPEARANCE, binding.textBrightnessLayout, binding.textBrightnessTitle, R.id.textBrightness),
            RuntimeSettingsRow(SettingsSections.KEY_APPEARANCE, binding.musicPulseLayout, binding.musicPulseTitle, R.id.musicPulse),
            RuntimeSettingsRow(SettingsSections.KEY_APPEARANCE, binding.glowStrengthLayout, binding.glowStrengthTitle, R.id.glowStrength),
            RuntimeSettingsRow(SettingsSections.KEY_APPEARANCE, binding.weatherEffectsLayout, binding.weatherEffectsTitle, R.id.weatherEffects),

            // Gestures
            RuntimeSettingsRow(SettingsSections.KEY_GESTURES, binding.swipeLeftApp.parent as View, getFirstTextView(binding.swipeLeftApp.parent as View), R.id.swipeLeftApp),
            RuntimeSettingsRow(SettingsSections.KEY_GESTURES, binding.swipeRightLayout, getFirstTextView(binding.swipeRightLayout), R.id.swipeRightApp),
            RuntimeSettingsRow(SettingsSections.KEY_GESTURES, binding.toggleLock.parent as View, getFirstTextView(binding.toggleLock.parent as View), R.id.toggleLock),

            // Backup
            RuntimeSettingsRow(SettingsSections.KEY_BACKUP, binding.exportBackupLayout, binding.exportBackup, R.id.exportBackup),
            RuntimeSettingsRow(SettingsSections.KEY_BACKUP, binding.autoBackupLayout, binding.autoBackupTitle, R.id.autoBackup),
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

    internal fun isRowAvailable(viewId: Int): Boolean = SettingsRowAvailability.isAvailable(
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

    internal fun setupSearchInsets() {
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

    internal fun initSearch() {
        binding.settingsSearch.doAfterTextChanged { text ->
            onSearchQueryChanged(text?.toString().orEmpty())
        }
    }

    internal fun onSearchQueryChanged(query: String) {
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
        binding.licenseNote.isVisible = false

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

        binding.todaySection.isVisible = (R.id.todayCards in matchingIds || R.id.todayOrder in matchingIds || R.id.intentionOnHome in matchingIds)
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
        binding.licenseNote.isVisible = true

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
    internal fun resolveTargetSection(): SettingsSection? {
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
            R.id.windDown, R.id.windDownTitle, R.id.windDownLayout -> showWindDownMenu()
            R.id.windDownGreyOnOff, R.id.windDownGreyTitle, R.id.windDownGreyLayout -> toggleWindDownGrey()
            R.id.systemGreyOnOff, R.id.systemGreyTitle, R.id.systemGreyLayout -> toggleSystemGrey()
            R.id.systemGreyCopy -> copySystemGreyCommand()
            R.id.systemGreyBedtime -> SystemGrey.openBedtimeMode(requireContext())
            R.id.commitLockOnOff, R.id.commitLockTitle, R.id.commitLockSubtitle, R.id.commitLockLayout -> toggleCommitLock()
            R.id.holdNotificationsOnOff, R.id.holdNotificationsTitle, R.id.holdNotificationsLayout -> toggleHoldNotifications()
            R.id.healthConnectOnOff, R.id.healthConnectTitle, R.id.healthConnectLayout -> toggleHealthConnect()
            R.id.quietReleaseTimes, R.id.quietReleaseTimesTitle, R.id.quietReleaseTimesLayout -> showReleaseTimesEditor()
            R.id.screenTimeOnOff -> viewModel.showDialog.postValue(Constants.Dialog.DIGITAL_WELLBEING)
            R.id.screenTimeStyle, R.id.screenTimeStyleTitle, R.id.screenTimeStyleLayout -> showScreenTimeStyleMenu(binding.screenTimeStyle)
            R.id.screenTimeTarget, R.id.screenTimeTargetTitle, R.id.screenTimeTargetLayout -> showScreenTimeTargetMenu(binding.screenTimeTarget)
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
            R.id.currencyRates, R.id.currencyRatesTitle, R.id.currencyRatesLayout -> toggleCurrencyRates()
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
            R.id.infoLine, R.id.infoLineTitle, R.id.infoLineLayout -> toggleInfoLine()
            R.id.homeSets, R.id.homeSetsTitle, R.id.homeSetsLayout -> toggleHomeSets()
            R.id.editMorningAppsTitle, R.id.editMorningAppsLayout -> showHomeSetSlotsDialog(HomeSet.MORNING)
            R.id.editEveningAppsTitle, R.id.editEveningAppsLayout -> showHomeSetSlotsDialog(HomeSet.EVENING)
            R.id.morningStart, R.id.morningStartTitle, R.id.morningStartLayout -> showHomeSetStartPicker(HomeSet.MORNING, R.string.morning_starts)
            R.id.dayStart, R.id.dayStartTitle, R.id.dayStartLayout -> showHomeSetStartPicker(HomeSet.DAY, R.string.day_starts)
            R.id.eveningStart, R.id.eveningStartTitle, R.id.eveningStartLayout -> showHomeSetStartPicker(HomeSet.EVENING, R.string.evening_starts)
            R.id.contextLineValue, R.id.contextLineTitle, R.id.contextLineLayout -> toggleContextLine()
            R.id.homeNextEventValue, R.id.homeNextEventTitle, R.id.homeNextEventLayout -> toggleHomeNextEvent()
            R.id.appThemeText -> showAppThemeMenu(view, showSystem = false)
            R.id.accent -> showAccentMenu(view)
            R.id.fontFamily -> showFontDialog()
            R.id.textSizeValue -> showTextSizeDialog()
            R.id.boldFont -> toggleBoldFont()
            R.id.haptics -> toggleHaptics()
            R.id.textContrast, R.id.textContrastTitle, R.id.textContrastLayout -> toggleTextContrast()
            R.id.homeDarkness, R.id.homeWashTitle, R.id.homeWashLayout -> cycleHomeDarkness()
            R.id.textBrightness, R.id.textBrightnessTitle, R.id.textBrightnessLayout -> cycleTextBrightness()
            R.id.musicPulse, R.id.musicPulseTitle, R.id.musicPulseLayout -> toggleMusicPulse()
            R.id.weatherEffects, R.id.weatherEffectsTitle, R.id.weatherEffectsLayout -> toggleWeatherEffects()

            R.id.photoWallpaper, R.id.photoWallpaperTitle, R.id.photoWallpaperLayout -> showPhotoWallpaperMenu(binding.photoWallpaper)
            R.id.swipeLeftApp -> showAppListIfEnabled(Constants.FLAG_SET_SWIPE_LEFT_APP)
            R.id.swipeRightApp, R.id.swipeRightLayout -> showSwipeRightMenu(binding.swipeRightApp)
            R.id.todayCards, R.id.todayCardsTitle, R.id.todayCardsLayout -> showTodayCardsDialog()
            R.id.todayOrder, R.id.todayOrderTitle, R.id.todayOrderLayout -> toggleTodayOrder()
            R.id.intentionOnHome, R.id.intentionOnHomeTitle, R.id.intentionOnHomeLayout -> toggleIntentionOnHome()
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
            R.id.germanSpellSuggestOnOff, R.id.germanSpellSuggestTitle, R.id.germanSpellSuggestSubtitle, R.id.germanSpellSuggestLayout -> toggleGermanSpellSuggest()
            R.id.germanScreenPickOnOff, R.id.germanScreenPickTitle, R.id.germanScreenPickSubtitle, R.id.germanScreenPickLayout -> toggleGermanScreenPick()
            R.id.germanPauseWordOnOff, R.id.germanPauseWordTitle, R.id.germanPauseWordLayout -> toggleGermanPauseWord()
            R.id.germanUiOnOff, R.id.germanUiTitle, R.id.germanUiLayout -> toggleGermanUi()
            R.id.exportBackup, R.id.exportBackupLayout -> launchExportBackup()
            R.id.importBackup, R.id.importBackupLayout -> launchImportBackup()
            R.id.autoBackup, R.id.autoBackupTitle, R.id.autoBackupLayout -> toggleAutoBackup()
            R.id.autoBackupStatus -> if (prefs.autoBackupLastFailed) launchAutoBackupFolderPicker()
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

    internal fun initClickListeners() {
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
        binding.currencyRates.setOnClickListener(this)
        binding.currencyRatesTitle.setOnClickListener(this)
        binding.currencyRatesLayout.setOnClickListener(this)
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
        binding.screenTimeStyle.setOnClickListener(this)
        binding.screenTimeStyleTitle.setOnClickListener(this)
        binding.screenTimeStyleLayout.setOnClickListener(this)
        binding.screenTimeTarget.setOnClickListener(this)
        binding.screenTimeTargetTitle.setOnClickListener(this)
        binding.screenTimeTargetLayout.setOnClickListener(this)
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
        binding.windDown.setOnClickListener(this)
        binding.windDownTitle.setOnClickListener(this)
        binding.windDownLayout.setOnClickListener(this)
        binding.windDownGreyOnOff.setOnClickListener(this)
        binding.windDownGreyTitle.setOnClickListener(this)
        binding.windDownGreyLayout.setOnClickListener(this)
        binding.systemGreyOnOff.setOnClickListener(this)
        binding.systemGreyTitle.setOnClickListener(this)
        binding.systemGreyLayout.setOnClickListener(this)
        binding.systemGreyCopy.setOnClickListener(this)
        binding.systemGreyBedtime.setOnClickListener(this)
        binding.commitLockOnOff.setOnClickListener(this)
        binding.commitLockTitle.setOnClickListener(this)
        binding.commitLockSubtitle.setOnClickListener(this)
        binding.commitLockLayout.setOnClickListener(this)
        binding.healthConnectOnOff.setOnClickListener(this)
        binding.healthConnectTitle.setOnClickListener(this)
        binding.healthConnectLayout.setOnClickListener(this)
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
        binding.infoLine.setOnClickListener(this)
        binding.infoLineTitle.setOnClickListener(this)
        binding.infoLineLayout.setOnClickListener(this)
        binding.homeSetsLayout.setOnClickListener(this)
        binding.homeSetsTitle.setOnClickListener(this)
        binding.editMorningAppsLayout.setOnClickListener(this)
        binding.editMorningAppsTitle.setOnClickListener(this)
        binding.editEveningAppsLayout.setOnClickListener(this)
        binding.editEveningAppsTitle.setOnClickListener(this)
        binding.morningStartLayout.setOnClickListener(this)
        binding.morningStartTitle.setOnClickListener(this)
        binding.dayStartLayout.setOnClickListener(this)
        binding.dayStartTitle.setOnClickListener(this)
        binding.eveningStartLayout.setOnClickListener(this)
        binding.eveningStartTitle.setOnClickListener(this)
        binding.homeSets.setOnClickListener(this)
        binding.morningStart.setOnClickListener(this)
        binding.dayStart.setOnClickListener(this)
        binding.eveningStart.setOnClickListener(this)
        binding.contextLineValue.setOnClickListener(this)
        binding.contextLineTitle.setOnClickListener(this)
        binding.contextLineLayout.setOnClickListener(this)
        binding.homeNextEventValue.setOnClickListener(this)
        binding.homeNextEventTitle.setOnClickListener(this)
        binding.homeNextEventLayout.setOnClickListener(this)
        binding.swipeLeftApp.setOnClickListener(this)
        binding.swipeRightApp.setOnClickListener(this)
        binding.swipeRightLayout.setOnClickListener(this)
        binding.todayCards.setOnClickListener(this)
        binding.todayCardsTitle.setOnClickListener(this)
        binding.todayCardsLayout.setOnClickListener(this)
        binding.todayOrder.setOnClickListener(this)
        binding.todayOrderTitle.setOnClickListener(this)
        binding.todayOrderLayout.setOnClickListener(this)
        binding.intentionOnHome.setOnClickListener(this)
        binding.intentionOnHomeTitle.setOnClickListener(this)
        binding.intentionOnHomeLayout.setOnClickListener(this)
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
        binding.germanSpellSuggestOnOff.setOnClickListener(this)
        binding.germanSpellSuggestTitle.setOnClickListener(this)
        binding.germanSpellSuggestSubtitle.setOnClickListener(this)
        binding.germanSpellSuggestLayout.setOnClickListener(this)
        binding.germanScreenPickOnOff.setOnClickListener(this)
        binding.germanScreenPickTitle.setOnClickListener(this)
        binding.germanScreenPickSubtitle.setOnClickListener(this)
        binding.germanScreenPickLayout.setOnClickListener(this)
        binding.germanPauseWordOnOff.setOnClickListener(this)
        binding.germanPauseWordTitle.setOnClickListener(this)
        binding.germanPauseWordLayout.setOnClickListener(this)
        binding.germanUiOnOff.setOnClickListener(this)
        binding.germanUiTitle.setOnClickListener(this)
        binding.germanUiLayout.setOnClickListener(this)
        binding.exportBackup.setOnClickListener(this)
        binding.exportBackupLayout.setOnClickListener(this)
        binding.autoBackup.setOnClickListener(this)
        binding.autoBackupTitle.setOnClickListener(this)
        binding.autoBackupLayout.setOnClickListener(this)
        binding.autoBackupStatus.setOnClickListener(this)
        binding.importBackup.setOnClickListener(this)
        binding.importBackupLayout.setOnClickListener(this)
        binding.appThemeText.setOnClickListener(this)
        binding.accent.setOnClickListener(this)
        binding.fontFamily.setOnClickListener(this)
        binding.textSizeValue.setOnClickListener(this)
        binding.boldFont.setOnClickListener(this)
        binding.haptics.setOnClickListener(this)
        binding.textContrast.setOnClickListener(this)
        binding.textContrastTitle.setOnClickListener(this)
        binding.textContrastLayout.setOnClickListener(this)
        binding.homeDarkness.setOnClickListener(this)
        binding.homeWashTitle.setOnClickListener(this)
        binding.homeWashLayout.setOnClickListener(this)
        binding.textBrightness.setOnClickListener(this)
        binding.textBrightnessTitle.setOnClickListener(this)
        binding.textBrightnessLayout.setOnClickListener(this)
        binding.musicPulse.setOnClickListener(this)
        binding.musicPulseTitle.setOnClickListener(this)
        binding.musicPulseLayout.setOnClickListener(this)
        binding.weatherEffects.setOnClickListener(this)
        binding.weatherEffectsTitle.setOnClickListener(this)
        binding.weatherEffectsLayout.setOnClickListener(this)
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
