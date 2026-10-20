package app.olauncher.ui

import android.Manifest
import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.ContentUris
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.content.res.Configuration
import android.os.BatteryManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import app.olauncher.helper.HomeAccessibilityAction
import app.olauncher.helper.HomeAccessibilityActions
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.helper.HomeSet
import app.olauncher.helper.HomeSets
import app.olauncher.helper.Gesture
import app.olauncher.helper.GestureHints
import app.olauncher.data.ShortcutAction
import app.olauncher.data.ShortcutOffset
import app.olauncher.data.ShortcutSize
import app.olauncher.data.SlotCount
import app.olauncher.helper.HomeFit
import app.olauncher.helper.HomeMetaLine
import app.olauncher.helper.HomeWash
import app.olauncher.helper.PosterClock
import app.olauncher.databinding.FragmentHomeBinding
import app.olauncher.glance.CalendarRepository
import app.olauncher.glance.HomeNextEvent
import app.olauncher.glance.HomeNextEventLine
import app.olauncher.glance.NextUpPlanner
import app.olauncher.glance.WeatherCache
import app.olauncher.glance.WeatherFormatting
import app.olauncher.glance.WeatherLocation
import app.olauncher.helper.BatteryFormatting
import app.olauncher.helper.ContextItem
import app.olauncher.helper.ContextLabels
import app.olauncher.helper.ContextLine
import app.olauncher.helper.ContrastPolicy
import app.olauncher.helper.DefaultLauncherCheck
import app.olauncher.helper.GermanTime
import app.olauncher.helper.AppTarget
import app.olauncher.helper.Haptics
import app.olauncher.helper.OpenTargets
import app.olauncher.helper.NowPlaying
import app.olauncher.helper.InfoLine
import app.olauncher.helper.Intention
import java.time.LocalDateTime
import app.olauncher.helper.TouchTarget
import app.olauncher.helper.appUsagePermissionGranted
import app.olauncher.helper.dpToPx
import app.olauncher.helper.expandNotificationDrawer
import app.olauncher.helper.getChangedAppTheme
import app.olauncher.helper.getUserHandleFromString
import app.olauncher.helper.isPackageInstalled
import app.olauncher.helper.openAlarmApp
import app.olauncher.helper.openCalendar
import app.olauncher.helper.openCameraApp
import app.olauncher.helper.openDialerApp
import app.olauncher.helper.openGalleryApp
import app.olauncher.helper.openMessagesApp
import app.olauncher.helper.PhotoWallpaper
import app.olauncher.helper.RotationInterval
import app.olauncher.helper.WallpaperHomeMenu
import app.olauncher.helper.setPlainWallpaperByTheme
import app.olauncher.helper.showPopupMenu
import app.olauncher.helper.showToast
import app.olauncher.listener.OnSwipeTouchListener
import app.olauncher.listener.ViewSwipeTouchListener
import app.olauncher.wellbeing.ScreenTimeCue
import app.olauncher.wellbeing.WindDownWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HomeFragment : BaseFragment(), View.OnClickListener, View.OnLongClickListener {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel

    private var timeTickReceiver: BroadcastReceiver? = null
    private var renderedHomeSet: HomeSet? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private var weatherPrefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var latestScreenTime: String? = null
    private var contextTickReceiver: BroadcastReceiver? = null
    private var nowPlaying: NowPlaying? = null
    private var contextItem: ContextItem? = null
    private var nextEventId: Long? = null
    private var contextJob: Job? = null
    private var wallpaperColorsListener: WallpaperManager.OnColorsChangedListener? = null
    private val originalShadows = HashMap<TextView, FloatArray>()
    private val originalShadowColors = HashMap<TextView, Int>()
    private var homeAppsPaddingStart = 0
    private var homeAppsPaddingEnd = 0
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")


        homeAppsPaddingStart = binding.homeAppsLayout.paddingStart
        homeAppsPaddingEnd = binding.homeAppsLayout.paddingEnd
        initObservers()
        setupShortcutViews()
        setHomeAlignment(prefs.homeAlignment)
        initSwipeTouchListener()
        initClickListeners()
        initAccessibilityActions()
        initWeatherListener()
        binding.dateTimeLayout.addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) {
                capClockSize()
                applyHomeFit()
            }
            if (bottom != oldBottom) binding.homeWash.post { sizeHomeWash() }
        }
    }

    override fun onResume() {
        super.onResume()
        setHomeAlignment()
        populateHomeScreen(false)
        populateShortcuts()
        populateWeather()
        applyTextContrast()
        WeatherLocation.refreshIfStale(requireContext())
        viewModel.isOlauncherDefault()
        if (prefs.showStatusBar) showStatusBar()
        else hideStatusBar()
        if (prefs.homeSets || GermanTime.isWordsClockOn(prefs.germanEnabled, prefs.germanClock)) registerTimeTickReceiver()
        registerBatteryReceiver()
        startContextLine()
        populateNextEventLine()
        // Fixes a missed wind-down start/end (grey wallpaper, whole-phone grey) and reschedules.
        if (prefs.windDown || prefs.windDownGreyApplied || prefs.systemGreySaved != null) {
            WindDownWorker.syncAsync(requireContext())
        }
        if (prefs.photoWallpaperCount > 0 &&
            RotationInterval.fromId(prefs.photoWallpaperInterval) == RotationInterval.ON_OPEN
        ) {
            if (onOpenRotationJob?.isActive == true) return
            val appContext = requireContext().applicationContext
            onOpenRotationJob = viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                if (PhotoWallpaper.rotateIfDue(prefs)) {
                    PhotoWallpaper.applyCurrent(appContext, prefs)
                }
            }
        }
    }

    private var onOpenRotationJob: Job? = null

    override fun onStart() {
        super.onStart()
        val context = context ?: return
        val listener = WallpaperManager.OnColorsChangedListener { _, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) applyTextContrast()
        }
        wallpaperColorsListener = listener
        WallpaperManager.getInstance(context)
            .addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
    }

    override fun onStop() {
        super.onStop()
        val listener = wallpaperColorsListener ?: return
        context?.let { WallpaperManager.getInstance(it).removeOnColorsChangedListener(listener) }
        wallpaperColorsListener = null
    }

    private fun applyTextContrast() {
        if (_binding == null) return
        val context = context ?: return
        val themeIsDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val autoContrast = prefs.homeTextContrast != Prefs.TEXT_CONTRAST_THEME
        val colors: WallpaperColors? = if (!autoContrast) {
            null
        } else {
            try {
                WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            } catch (e: Exception) {
                null
            }
        }
        val supportsDarkText: Boolean? =
            colors?.let { (it.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0 }
        val treatment = ContrastPolicy.decide(supportsDarkText, themeIsDark)
        binding.homeWash.isVisible = HomeWash.shouldShow(
            autoContrast = autoContrast,
            themeIsDark = themeIsDark,
            supportsDarkText = supportsDarkText,
            wallpaperColors = listOfNotNull(colors?.primaryColor, colors?.secondaryColor, colors?.tertiaryColor)
                .map { it.toArgb() },
        )
        sizeHomeWash()
        val views = listOf(
            binding.clock, binding.clockWords, binding.date, binding.weatherSeparator,
            binding.weather, binding.contextLine, binding.nextEventLine, binding.intentionLine, binding.tvScreenTime,
            binding.homeApp1, binding.homeApp2, binding.homeApp3, binding.homeApp4,
            binding.homeApp5, binding.homeApp6, binding.homeApp7, binding.homeApp8,
        )
        views.forEach { view ->
            if (view !in originalShadows) {
                originalShadows[view] = floatArrayOf(view.shadowRadius, view.shadowDx, view.shadowDy)
                originalShadowColors[view] = view.shadowColor
            }
            val original = originalShadows.getValue(view)
            if (treatment.shadowRadiusDp > 0) {
                val base = if (themeIsDark) Color.BLACK else Color.WHITE
                val shadowColor = Color.argb((treatment.shadowAlpha * 255).toInt(), Color.red(base), Color.green(base), Color.blue(base))
                view.setShadowLayer(treatment.shadowRadiusDp.dpToPx().toFloat(), 0f, original[2], shadowColor)
            } else {
                view.setShadowLayer(original[0], original[1], original[2], originalShadowColors.getValue(view))
            }
            treatment.color?.let { view.setTextColor(it) }
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterTimeTickReceiver()
        unregisterBatteryReceiver()
        stopContextLine()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.lock -> {}
            // Home button for recents feature disabled
            // R.id.recents -> {}
            R.id.clock, R.id.clockWords -> openClockApp()
            R.id.date -> if (prefs.homeInfoCombined) openToday() else openCalendarApp()
            R.id.weather -> openWeatherApp()
            R.id.contextLine -> openContextItem()
            R.id.nextEventLine -> openNextEvent()
            R.id.setDefaultLauncher -> viewModel.resetLauncherLiveData.call()
            R.id.tvScreenTime -> openScreenTimeDigitalWellbeing()

            else -> {
                try { // Launch app
                    val appLocation = view.tag.toString().toInt()
                    homeAppClicked(appLocation, view)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun launchTarget(target: AppTarget) =
        launchApp(target.label, target.pkg, target.className, target.user)

    private fun openClockApp() =
        OpenTargets.openClockApp(requireContext(), prefs, ::launchTarget)

    private fun openCalendarApp() =
        OpenTargets.openCalendarApp(requireContext(), prefs, ::launchTarget)

    private fun openWeatherApp() =
        OpenTargets.openWeatherApp(
            requireContext(), prefs,
            pickApp = { showAppList(Constants.FLAG_SET_WEATHER_APP) },
            launch = ::launchTarget,
        )

    override fun onLongClick(view: View): Boolean {
        when (view.id) {
            R.id.homeApp1, R.id.homeApp2, R.id.homeApp3, R.id.homeApp4,
            R.id.homeApp5, R.id.homeApp6, R.id.homeApp7, R.id.homeApp8 -> {
                val slot = homeAppViews().indexOfFirst { it.id == view.id } + 1
                val set = prefs.activeHomeSet()
                showAppList(HomeSets.flagFor(set, slot), prefs.getAppName(slot, set).isNotEmpty(), true)
            }
            R.id.clock, R.id.clockWords -> {
                showAppList(Constants.FLAG_SET_CLOCK_APP)
                prefs.clockAppPackage = ""
                prefs.clockAppClassName = ""
                prefs.clockAppUser = ""
            }

            R.id.date -> {
                showAppList(Constants.FLAG_SET_CALENDAR_APP)
                prefs.calendarAppPackage = ""
                prefs.calendarAppClassName = ""
                prefs.calendarAppUser = ""
            }

            R.id.weather -> {
                showAppList(Constants.FLAG_SET_WEATHER_APP)
                prefs.weatherAppPackage = ""
                prefs.weatherAppClassName = ""
                prefs.weatherAppUser = ""
            }

            R.id.tvScreenTime -> {
                showAppList(Constants.FLAG_SET_SCREEN_TIME_APP)
                prefs.screenTimeAppPackage = ""
                prefs.screenTimeAppClassName = ""
                prefs.screenTimeAppUser = ""
            }

            R.id.setDefaultLauncher -> {
                prefs.hideSetDefaultLauncher = true
                binding.setDefaultLauncher.visibility = View.GONE
                if (viewModel.isOlauncherDefault.value != true) {
                    requireContext().showToast(R.string.set_as_default_launcher)
                    findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
                }
            }
        }
        return true
    }

    private fun showGestureHint() {
        val enabled = buildSet {
            if (prefs.swipeRightEnabled && prefs.swipeRightAction == Prefs.SWIPE_RIGHT_ACTION_TODAY) add(Gesture.SWIPE_RIGHT)
            if (prefs.swipeLeftEnabled) add(Gesture.SWIPE_LEFT)
            if (prefs.lockModeOn) add(Gesture.DOUBLE_TAP)
            add(Gesture.LONG_PRESS)
        }
        val today = prefs.epochDaySupplier()
        val hint = GestureHints.next(prefs.gestureHintIndex, prefs.gestureHintDay, today, enabled) ?: return
        prefs.gestureHintIndex = hint.position + 1
        prefs.gestureHintDay = today
        binding.firstRunTips.text = when (hint.gesture) {
            Gesture.SWIPE_RIGHT -> getString(R.string.hint_swipe_right_today)
            Gesture.SWIPE_LEFT -> getString(R.string.hint_swipe_left_app, prefs.appNameSwipeLeft)
            Gesture.DOUBLE_TAP -> getString(R.string.hint_double_tap_lock)
            Gesture.LONG_PRESS -> getString(R.string.hint_long_press_settings)
        }
        // The index already moved past this hint, so a tap only hides it; the next one comes tomorrow.
        binding.firstRunTips.setOnClickListener {
            binding.firstRunTips.visibility = View.GONE
        }
        binding.firstRunTips.visibility = View.VISIBLE
    }

    private fun initObservers() {
        if (prefs.firstSettingsOpen) {
            binding.firstRunTips.visibility = View.VISIBLE
            binding.setDefaultLauncher.visibility = View.GONE
        } else binding.firstRunTips.visibility = View.GONE

        viewModel.refreshHome.observe(viewLifecycleOwner) {
            populateHomeScreen(it)
        }
        viewModel.isOlauncherDefault.observe(viewLifecycleOwner, Observer {
            // Do not clear the saved Bottom preference here; setHomeAlignment() only
            // stops applying it while Thumbline is not the default launcher.
            setHomeAlignment()
            if (prefs.firstSettingsOpen) return@Observer
            val showLauncherPrompt = it.not() && prefs.hideSetDefaultLauncher.not()
            binding.setDefaultLauncher.isVisible = showLauncherPrompt
            // The gesture hint shares the prompt's spot at the bottom; the prompt wins.
            if (showLauncherPrompt) binding.firstRunTips.visibility = View.GONE
            else showGestureHint()
        })
        viewModel.homeAppAlignment.observe(viewLifecycleOwner) {
            setHomeAlignment(it)
        }
        viewModel.toggleDateTime.observe(viewLifecycleOwner) {
            populateDateTime()
            applyTextContrast()
        }
        viewModel.screenTimeValue.observe(viewLifecycleOwner) {
            it?.let {
                latestScreenTime = it
                binding.tvScreenTime.text = it
                if (prefs.homeInfoCombined) populateDateTime()
            }
        }
        viewModel.screenTimeMinutes.observe(viewLifecycleOwner) { applyScreenTimeCue() }
        // Home button for recents feature disabled
        // viewModel.showRecentApps.observe(viewLifecycleOwner) {
        //     binding.recents.performClick()
        // }
    }

    private fun initSwipeTouchListener() {
        val context = requireContext()
        binding.mainLayout.setOnTouchListener(getSwipeGestureListener(context))
        binding.homeApp1.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp1))
        binding.homeApp2.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp2))
        binding.homeApp3.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp3))
        binding.homeApp4.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp4))
        binding.homeApp5.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp5))
        binding.homeApp6.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp6))
        binding.homeApp7.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp7))
        binding.homeApp8.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp8))
    }

    /** Gives every gesture a TalkBack custom action. Read from prefs on each query, so it never goes stale. */
    private fun initAccessibilityActions() {
        ViewCompat.setAccessibilityDelegate(binding.mainLayout, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                if (_binding == null) return
                HomeAccessibilityActions.enabled(
                    lockModeOn = prefs.lockModeOn,
                    swipeLeftEnabled = prefs.swipeLeftEnabled,
                    // Swipe-right set to Today is already covered by the Open Today action.
                    swipeRightEnabled = prefs.swipeRightEnabled && prefs.swipeRightAction != Prefs.SWIPE_RIGHT_ACTION_TODAY,
                ).forEach { action ->
                    val label = when (action) {
                        HomeAccessibilityAction.OPEN_APPS -> getString(R.string.a11y_action_open_apps)
                        HomeAccessibilityAction.OPEN_TODAY -> getString(R.string.a11y_action_open_today)
                        HomeAccessibilityAction.SETTINGS -> getString(R.string.a11y_action_settings)
                        HomeAccessibilityAction.LOCK_SCREEN -> getString(R.string.a11y_action_lock_screen)
                        HomeAccessibilityAction.SWIPE_LEFT_APP ->
                            getString(R.string.a11y_action_swipe_left_app, prefs.appNameSwipeLeft)
                        HomeAccessibilityAction.SWIPE_RIGHT_APP ->
                            getString(R.string.a11y_action_swipe_right_app, prefs.appNameSwipeRight)
                    }
                    info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(accessibilityActionId(action), label))
                }
            }

            override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                val match = HomeAccessibilityAction.values().firstOrNull { accessibilityActionId(it) == action }
                    ?: return super.performAccessibilityAction(host, action, args)
                if (_binding == null) return false
                when (match) {
                    HomeAccessibilityAction.OPEN_APPS -> onHomeSwipeUp()
                    HomeAccessibilityAction.OPEN_TODAY -> openToday()
                    HomeAccessibilityAction.SETTINGS -> openSettings()
                    HomeAccessibilityAction.LOCK_SCREEN -> if (prefs.lockModeOn) binding.lock.performClick()
                    HomeAccessibilityAction.SWIPE_LEFT_APP -> openSwipeLeftApp()
                    HomeAccessibilityAction.SWIPE_RIGHT_APP -> openSwipeRightApp()
                }
                return true
            }
        })
    }

    private fun accessibilityActionId(action: HomeAccessibilityAction): Int = when (action) {
        HomeAccessibilityAction.OPEN_APPS -> R.id.a11y_action_open_apps
        HomeAccessibilityAction.OPEN_TODAY -> R.id.a11y_action_open_today
        HomeAccessibilityAction.SETTINGS -> R.id.a11y_action_settings
        HomeAccessibilityAction.LOCK_SCREEN -> R.id.a11y_action_lock_screen
        HomeAccessibilityAction.SWIPE_LEFT_APP -> R.id.a11y_action_swipe_left_app
        HomeAccessibilityAction.SWIPE_RIGHT_APP -> R.id.a11y_action_swipe_right_app
    }

    private fun initClickListeners() {
        binding.lock.setOnClickListener(this)
        // Home button for recents feature disabled
        // binding.recents.setOnClickListener(this)
        binding.clock.setOnClickListener(this)
        binding.clockWords.setOnClickListener(this)
        binding.date.setOnClickListener(this)
        binding.weather.setOnClickListener(this)
        binding.contextLine.setOnClickListener(this)
        binding.contextLine.setOnLongClickListener {
            (contextItem as? ContextItem.Media)?.let { openMediaApp(it) }
            contextItem is ContextItem.Media
        }
        binding.nextEventLine.setOnClickListener(this)
        binding.clock.setOnLongClickListener(this)
        binding.clockWords.setOnLongClickListener(this)
        binding.date.setOnLongClickListener(this)
        binding.weather.setOnLongClickListener(this)
        binding.setDefaultLauncher.setOnClickListener(this)
        binding.setDefaultLauncher.setOnLongClickListener(this)
        binding.tvScreenTime.setOnClickListener(this)
        binding.tvScreenTime.setOnLongClickListener(this)

        // These fire only on d-pad/keyboard events; touch is consumed by ViewSwipeTouchListener
        binding.homeApp1.setOnClickListener(this)
        binding.homeApp2.setOnClickListener(this)
        binding.homeApp3.setOnClickListener(this)
        binding.homeApp4.setOnClickListener(this)
        binding.homeApp5.setOnClickListener(this)
        binding.homeApp6.setOnClickListener(this)
        binding.homeApp7.setOnClickListener(this)
        binding.homeApp8.setOnClickListener(this)
        binding.homeApp1.setOnLongClickListener(this)
        binding.homeApp2.setOnLongClickListener(this)
        binding.homeApp3.setOnLongClickListener(this)
        binding.homeApp4.setOnLongClickListener(this)
        binding.homeApp5.setOnLongClickListener(this)
        binding.homeApp6.setOnLongClickListener(this)
        binding.homeApp7.setOnLongClickListener(this)
        binding.homeApp8.setOnLongClickListener(this)
    }

    private fun setHomeAlignment(horizontalGravity: Int = prefs.homeAlignment) {
        binding.shortcutsLayout.isVisible = prefs.homeShortcuts
        val sideGravity = if (prefs.shortcutSideStart) Gravity.START else Gravity.END
        (binding.shortcutsLayout.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
            params.gravity = Gravity.BOTTOM or sideGravity
            params.marginStart = if (prefs.shortcutSideStart) ShortcutOffset.SIDE_INSET_DP.dpToPx() else 0
            params.marginEnd = if (prefs.shortcutSideStart) 0 else ShortcutOffset.SIDE_INSET_DP.dpToPx()
            params.bottomMargin = ShortcutOffset.bottomMarginDp(prefs.shortcutOffsetStep).dpToPx()
            binding.shortcutsLayout.layoutParams = params
        }
        populateShortcuts()

        val extraPadding = if (prefs.homeShortcuts) ShortcutSize.columnAllowanceDp(prefs.shortcutSizeStep).dpToPx() else 0
        val paddingStart = homeAppsPaddingStart + if (prefs.shortcutSideStart) extraPadding else 0
        val paddingEnd = homeAppsPaddingEnd + if (prefs.shortcutSideStart) 0 else extraPadding
        binding.homeAppsLayout.setPaddingRelative(
            paddingStart,
            binding.homeAppsLayout.paddingTop,
            paddingEnd,
            binding.homeAppsLayout.paddingBottom
        )

        (binding.firstRunTips.layoutParams as? FrameLayout.LayoutParams)?.let { tipsParams ->
            tipsParams.marginStart = 24.dpToPx() + if (prefs.homeShortcuts && prefs.shortcutSideStart) ShortcutSize.columnAllowanceDp(prefs.shortcutSizeStep).dpToPx() else 0
            binding.firstRunTips.layoutParams = tipsParams
        }

        val useBottom = DefaultLauncherCheck.useBottomAlignment(prefs.homeBottomAlignment, viewModel.isOlauncherDefault.value)
        val verticalGravity = if (useBottom) Gravity.BOTTOM else Gravity.CENTER_VERTICAL
        binding.homeAppsLayout.gravity = horizontalGravity or verticalGravity
        binding.dateTimeLayout.gravity = horizontalGravity
        binding.clockWords.gravity = horizontalGravity
        binding.weather.gravity = horizontalGravity
        binding.dateWeatherRow.gravity = horizontalGravity
        binding.homeApp1.gravity = horizontalGravity
        binding.homeApp2.gravity = horizontalGravity
        binding.homeApp3.gravity = horizontalGravity
        binding.homeApp4.gravity = horizontalGravity
        binding.homeApp5.gravity = horizontalGravity
        binding.homeApp6.gravity = horizontalGravity
        binding.homeApp7.gravity = horizontalGravity
        binding.homeApp8.gravity = horizontalGravity
        populateScreenTime()
    }

    private fun getFittingShortcutCount(): Int {
        val displayMetrics = resources.displayMetrics
        val density = displayMetrics.density.takeIf { it > 0f } ?: 1f
        val columnHeightDp = (displayMetrics.heightPixels / density).toInt()
        val viewSizeDp = ShortcutSize.slotSizeDp(prefs.shortcutSizeStep)
        val bottomMarginDp = ShortcutOffset.bottomMarginDp(prefs.shortcutOffsetStep)
        return SlotCount.fitting(prefs.shortcutCount, columnHeightDp, viewSizeDp, bottomMarginDp)
    }

    private fun setupShortcutViews() {
        val context = context ?: return
        val count = getFittingShortcutCount()
        val viewSizeDp = ShortcutSize.viewSizeDp(prefs.shortcutSizeStep)
        val viewSizePx = viewSizeDp.dpToPx()
        val targetSizePx = TouchTarget.targetSizePx(viewSizePx, TouchTarget.MIN_TARGET_DP.dpToPx())
        binding.shortcutsLayout.removeAllViews()
        for (slot in 1..count) {
            val imageView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    targetSizePx,
                    targetSizePx,
                )
                val typedValue = TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, typedValue, true)
                setBackgroundResource(typedValue.resourceId)
                tag = slot
                isClickable = true
                isFocusable = true
                bindShortcutIcon(context, this, slot)
                setOnTouchListener(getShortcutSwipeTouchListener(context, this, slot))
                // These fire only on d-pad/keyboard/accessibility events; touch is consumed by ViewSwipeTouchListener
                setOnClickListener {
                    Haptics.tick(this)
                    shortcutClicked(slot)
                }
                setOnLongClickListener {
                    Haptics.longPress(this)
                    showShortcutMenu(slot)
                    true
                }
            }
            binding.shortcutsLayout.addView(imageView)
        }
    }

    private fun populateShortcuts() {
        val context = context ?: return
        val count = getFittingShortcutCount()
        val viewSizePx = ShortcutSize.viewSizeDp(prefs.shortcutSizeStep).dpToPx()
        val expectedViewSizePx = TouchTarget.targetSizePx(viewSizePx, TouchTarget.MIN_TARGET_DP.dpToPx())
        val firstChild = binding.shortcutsLayout.getChildAt(0)
        val currentViewSizePx = firstChild?.layoutParams?.width ?: 0
        if (binding.shortcutsLayout.childCount != count || (count > 0 && currentViewSizePx != expectedViewSizePx)) {
            setupShortcutViews()
            return
        }
        for (i in 0 until binding.shortcutsLayout.childCount) {
            val view = binding.shortcutsLayout.getChildAt(i) as? ImageView ?: continue
            val slot = (view.tag as? Int) ?: (i + 1)
            bindShortcutIcon(context, view, slot)
        }
    }

    private fun bindShortcutIcon(context: Context, view: ImageView, slot: Int) {
        val viewSizeDp = ShortcutSize.viewSizeDp(prefs.shortcutSizeStep)
        val (drawable, paddingPx, isAppIcon) = ShortcutIconRenderer.render(context, slot, prefs, viewSizeDp)
        val extraPaddingPx = TouchTarget.extraPaddingPx(viewSizeDp.dpToPx(), TouchTarget.MIN_TARGET_DP.dpToPx())
        val totalPaddingPx = paddingPx + extraPaddingPx
        view.setPadding(totalPaddingPx, totalPaddingPx, totalPaddingPx, totalPaddingPx)
        view.setImageDrawable(drawable)
        view.contentDescription = ShortcutIconRenderer.getLabel(context, slot, prefs, isAppIcon)
        // Extra slots (7..MAX_SHORTCUT_SLOTS) show a faint placeholder until an app icon resolves
        val isPlaceholder = !isAppIcon && prefs.getShortcutAction(slot) == null
        view.alpha = if (isPlaceholder) 0.4f else 0.85f
    }

    private fun getShortcutSwipeTouchListener(context: Context, view: View, slot: Int): View.OnTouchListener {
        return object : ViewSwipeTouchListener(context, view) {
            override fun onSwipeLeft() {
                super.onSwipeLeft()
                openSwipeLeftApp()
            }

            override fun onSwipeRight() {
                super.onSwipeRight()
                openSwipeRightApp()
            }

            override fun onSwipeUp() {
                super.onSwipeUp()
                onHomeSwipeUp()
            }

            override fun onSwipeDown() {
                super.onSwipeDown()
                onHomeSwipeDown(context)
            }

            override fun onLongClick(view: View) {
                super.onLongClick(view)
                Haptics.longPress(view)
                showShortcutMenu(slot)
            }

            override fun onClick(view: View) {
                super.onClick(view)
                Haptics.tick(view)
                shortcutClicked(slot)
            }
        }
    }

    private fun shortcutClicked(slot: Int) {
        val context = context ?: return
        if (prefs.getShortcutMode(slot) == Constants.SHORTCUT_MODE_APP && prefs.getShortcutPackage(slot).isNotEmpty()) {
            launchApp(
                appName = prefs.getShortcutLabel(slot),
                packageName = prefs.getShortcutPackage(slot),
                activityClassName = prefs.getShortcutClass(slot),
                userString = prefs.getShortcutUser(slot)
            )
            return
        }
        when (prefs.getShortcutAction(slot)) {
            ShortcutAction.SEARCH -> showAppList(Constants.FLAG_LAUNCH_APP)
            ShortcutAction.GALLERY -> openGalleryApp(context)
            ShortcutAction.CAMERA -> openCameraApp(context)
            ShortcutAction.MESSAGES -> openMessagesApp(context)
            ShortcutAction.PHONE -> openDialerApp(context)
            ShortcutAction.SETTINGS -> findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
            null -> showLongPressToast()
        }
    }

    private fun showShortcutMenu(slot: Int) {
        val context = context ?: return
        val options = arrayOf(
            getString(R.string.shortcut_choose_app),
            getString(R.string.shortcut_choose_builtin),
            getString(R.string.shortcut_reset),
        )
        AlertDialog.Builder(context)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showAppList(Constants.FLAG_SET_SHORTCUT_1 + slot - 1)
                    1 -> showBuiltinShortcutMenu(slot)
                    else -> {
                        prefs.resetShortcut(slot)
                        populateShortcuts()
                    }
                }
            }
            .show()
    }

    private fun showBuiltinShortcutMenu(slot: Int) {
        val context = context ?: return
        val actions = ShortcutAction.values()
        val labels = actions.map { getString(it.labelRes) }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle(R.string.shortcut_choose_builtin)
            .setItems(labels) { _, which ->
                prefs.setShortcutBuiltin(slot, actions[which].id)
                populateShortcuts()
            }
            .show()
    }

    private fun populateDateTime() {
        binding.dateTimeLayout.isVisible = prefs.dateTimeVisibility != Constants.DateTime.OFF
        val isTimeVisible = Constants.DateTime.isTimeVisible(prefs.dateTimeVisibility)
        val isDateVisible = Constants.DateTime.isDateVisible(prefs.dateTimeVisibility)

        val isGerman = prefs.germanEnabled && prefs.germanClock != Prefs.GERMAN_CLOCK_SYSTEM
        val isWordsClock = GermanTime.isWordsClockOn(prefs.germanEnabled, prefs.germanClock)

        if (isWordsClock) {
            binding.clock.isVisible = false
            binding.clockWords.isVisible = isTimeVisible
            updateClockWords()
        } else {
            binding.clockWords.isVisible = false
            binding.clock.isVisible = isTimeVisible
        }

        val dateFormat = if (isGerman) {
            SimpleDateFormat("EEEE, d. MMMM", Locale.GERMAN)
        } else {
            SimpleDateFormat("EEE, d MMM", Locale.getDefault())
        }
        val dateText = dateFormat.format(Date())

        val batteryIntent = context?.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100) / scale else -1
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val charging = plugged != 0

        if (prefs.homeInfoCombined) {
            val combinedDate = if (isGerman) {
                SimpleDateFormat("EEEE d. MMMM", Locale.GERMAN)
            } else {
                SimpleDateFormat("EEE d MMM", Locale.getDefault())
            }.format(Date())
            val screenTimeGranted = context?.appUsagePermissionGranted() == true
            if (screenTimeGranted) viewModel.getTodaysScreenTime()
            val showScreenTimeNumber = screenTimeGranted && !isScreenTimeCueOn()
            binding.date.text = InfoLine.compose(
                date = combinedDate,
                weather = combinedWeatherText(),
                battery = BatteryFormatting.infoText(percent, charging, prefs.batteryMode, prefs.showStatusBar),
                screenTime = if (showScreenTimeNumber) latestScreenTime else null
            )
            binding.tvScreenTime.isVisible = false
        } else {
            binding.date.text = BatteryFormatting.dateLine(
                date = dateText,
                percent = percent,
                charging = charging,
                mode = prefs.batteryMode,
                statusBarShown = prefs.showStatusBar
            )
            populateScreenTime()
        }
        binding.date.isVisible = isDateVisible
        populateWeather()
        refreshContextLine()
        refreshIntentionLine()
    }

    private fun refreshIntentionLine() {
        if (_binding == null) return
        val text = if (prefs.intentionOnHome) {
            Intention.current(prefs.intentionText, prefs.intentionDay, LocalDateTime.now())
        } else null
        binding.intentionLine.text = text
        binding.intentionLine.isVisible = text != null
    }

    private fun startContextLine() {
        if (!prefs.homeContextLine) {
            binding.contextLine.isVisible = false
            return
        }
        val context = requireContext()
        if (nowPlaying == null) {
            nowPlaying = NowPlaying(context.applicationContext) {
                // Media callbacks may arrive off the main thread.
                view?.post { refreshContextLine() }
            }
        }
        nowPlaying?.start()
        if (contextTickReceiver == null) {
            contextTickReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_TIME_TICK) refreshContextLine()
                }
            }
            context.registerReceiver(contextTickReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
        }
        refreshContextLine()
    }

    private fun stopContextLine() {
        nowPlaying?.stop()
        contextJob?.cancel()
        contextTickReceiver?.let {
            try {
                requireContext().unregisterReceiver(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            contextTickReceiver = null
        }
    }

    private fun refreshContextLine() {
        if (_binding == null) return
        if (!prefs.homeContextLine) {
            contextItem = null
            binding.contextLine.isVisible = false
            return
        }
        val appContext = context?.applicationContext ?: return
        val media = nowPlaying?.current()
        val labels = ContextLabels(
            inMinutes = getString(R.string.context_line_in_minutes),
            now = getString(R.string.context_line_now),
        )
        contextJob?.cancel()
        contextJob = viewLifecycleOwner.lifecycleScope.launch {
            val now = Instant.now()
            val event = if (media != null) null else {
                val events = try {
                    CalendarRepository(appContext).next(now.toEpochMilli(), hours = 1, limit = 8)
                } catch (_: Exception) {
                    emptyList()
                }
                ContextLine.relevantEvent(events, now)
            }
            if (_binding == null) return@launch
            val item = ContextLine.pick(media, event, now, labels)
            contextItem = item
            binding.contextLine.text = item?.text
            binding.contextLine.contentDescription = (item as? ContextItem.Media)?.let {
                getString(if (it.playing) R.string.context_line_pause_title else R.string.context_line_play_title, it.title)
            }
            binding.contextLine.isVisible = item != null
        }
    }

    private fun populateNextEventLine() {
        if (_binding == null) return
        val ctx = context ?: return
        val appContext = ctx.applicationContext
        val prefOn = prefs.homeNextEvent
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (!HomeNextEvent.shouldShow(prefOn, granted, HomeNextEventLine("", 0))) {
            nextEventId = null
            binding.nextEventLine.isVisible = false
            return
        }
        val nowTemplate = getString(R.string.next_up_now)
        val is24Hour = DateFormat.is24HourFormat(ctx)
        viewLifecycleOwner.lifecycleScope.launch {
            val now = Instant.now()
            val line = try {
                val events = CalendarRepository(appContext)
                    .next(now.toEpochMilli(), hours = 24, limit = NextUpPlanner.FETCH_LIMIT)
                HomeNextEvent.line(events, now, ZoneId.systemDefault(), is24Hour, nowTemplate)
            } catch (_: Exception) {
                null
            }
            if (_binding == null) return@launch
            nextEventId = line?.eventId
            binding.nextEventLine.text = line?.text
            binding.nextEventLine.isVisible = HomeNextEvent.shouldShow(prefOn, granted, line)
        }
    }

    private fun openNextEvent() {
        val context = context ?: return
        val id = nextEventId ?: return
        if (!OpenTargets.openEvent(context, id)) {
            context.showToast(getString(R.string.unable_to_open_app))
        }
    }

    private fun openContextItem() {
        val context = context ?: return
        try {
            when (val item = contextItem) {
                is ContextItem.Media -> nowPlaying?.toggle(item.packageName)
                is ContextItem.Event -> startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        data = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, item.eventId)
                    }
                )
                null -> {}
            }
        } catch (_: Exception) {
            context.showToast(getString(R.string.unable_to_open_app))
        }
    }

    private fun openMediaApp(item: ContextItem.Media) {
        val context = context ?: return
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(item.packageName)
            if (intent != null) startActivity(intent)
        } catch (_: Exception) {
        }
    }

    private fun openToday() {
        try {
            findNavController().navigate(R.id.action_mainFragment_to_todayFragment)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun combinedWeatherText(): String? {
        val context = context ?: return null
        if (!prefs.weatherEnabled) return null
        val cached = WeatherCache(context).read() ?: return null
        return WeatherFormatting.homeLine(cached.first, cached.second)
    }

    private fun updateClockWords() {
        if (_binding == null) return
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        binding.clockWords.text = GermanTime.words(hour, minute)
    }

    private fun refreshHomeSetIfChanged() {
        if (_binding == null) return
        if (prefs.activeHomeSet() != renderedHomeSet) populateHomeScreen(false)
    }

    private fun registerTimeTickReceiver() {
        if (timeTickReceiver == null) {
            timeTickReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_TIME_TICK) {
                        if (GermanTime.isWordsClockOn(prefs.germanEnabled, prefs.germanClock)) updateClockWords()
                        refreshHomeSetIfChanged()
                    }
                }
            }
            requireContext().registerReceiver(timeTickReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
        }
    }

    private fun unregisterTimeTickReceiver() {
        timeTickReceiver?.let {
            try {
                requireContext().unregisterReceiver(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            timeTickReceiver = null
        }
    }

    private fun registerBatteryReceiver() {
        if (batteryReceiver == null) {
            batteryReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    populateDateTime()
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            requireContext().registerReceiver(batteryReceiver, filter)
        }
    }

    private fun unregisterBatteryReceiver() {
        batteryReceiver?.let {
            try {
                requireContext().unregisterReceiver(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            batteryReceiver = null
        }
    }

    private fun isScreenTimeCueOn(): Boolean =
        prefs.screenTimeStyle == ScreenTimeCue.STYLE_CUE && context?.appUsagePermissionGranted() == true

    /** Dims the clock and home app names once today's screen time reaches the target. Needs usage access. */
    private fun applyScreenTimeCue() {
        if (_binding == null) return
        val alpha = if (isScreenTimeCueOn()) {
            ScreenTimeCue.alpha(viewModel.screenTimeMinutes.value ?: 0, prefs.screenTimeTargetMin)
        } else {
            ScreenTimeCue.ALPHA_NORMAL
        }
        listOf(
            binding.clock, binding.clockWords,
            binding.homeApp1, binding.homeApp2, binding.homeApp3, binding.homeApp4,
            binding.homeApp5, binding.homeApp6, binding.homeApp7, binding.homeApp8,
        ).forEach { it.alpha = alpha }
    }

    private fun populateScreenTime() {
        if (isScreenTimeCueOn()) {
            viewModel.getTodaysScreenTime()
            binding.tvScreenTime.isVisible = false
            applyScreenTimeCue()
            return
        }
        applyScreenTimeCue()
        if (prefs.homeInfoCombined) {
            binding.tvScreenTime.isVisible = false
            return
        }
        if (requireContext().appUsagePermissionGranted().not()) return

        viewModel.getTodaysScreenTime()
        binding.tvScreenTime.visibility = View.VISIBLE

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val horizontalMargin = if (isLandscape) 64.dpToPx() else 10.dpToPx()
        // Line the text's top up with the clock/date block (its layout margin) minus our own padding.
        val screenTimePaddingDp = 10
        val dateTimeTopMarginDp = if (isLandscape) 48 else 56
        val marginTop = (dateTimeTopMarginDp - screenTimePaddingDp).dpToPx()
        val targetGravity = if (prefs.homeAlignment == Gravity.END) Gravity.START else Gravity.END
        val extraStartMargin = if (prefs.homeShortcuts && prefs.shortcutSideStart && targetGravity == Gravity.START) ShortcutSize.columnAllowanceDp(prefs.shortcutSizeStep).dpToPx() else 0
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = marginTop
            marginStart = horizontalMargin + extraStartMargin
            marginEnd = horizontalMargin
            gravity = targetGravity
        }
        binding.tvScreenTime.layoutParams = params
        binding.tvScreenTime.setPadding(screenTimePaddingDp.dpToPx())
    }

    private fun populateHomeScreen(appCountUpdated: Boolean) {
        if (appCountUpdated) hideHomeApps()
        populateDateTime()

        populateScreenTime()

        val set = prefs.activeHomeSet()
        renderedHomeSet = set
        val homeAppsNum = prefs.homeAppsNum
        if (homeAppsNum == 0) return

        capClockSize()
        val views = homeAppViews()
        val fitting = fittingHomeAppCount(homeAppsNum)
        for (slot in 1..homeAppsNum.coerceAtMost(views.size)) {
            val view = views[slot - 1]
            view.visibility = if (slot <= fitting) View.VISIBLE else View.GONE
            val shown = setHomeAppText(
                view,
                prefs.getAppName(slot, set),
                prefs.getAppPackage(slot, set),
                prefs.getAppUser(slot, set),
                prefs.getIsShortcut(slot, set),
                prefs.getShortcutId(slot, set),
            )
            if (!shown) prefs.clearHomeSlotApp(set, slot)
        }
    }

    /** Keeps the clock block under the max share of the screen height, whatever the font scale. */
    private fun capClockSize() {
        val screenPx = resources.displayMetrics.heightPixels.toFloat()
        val maxPx = screenPx * resources.getFloat(R.dimen.clock_max_height_fraction)
        fun capped(view: TextView, sizePx: Float, lines: Int) {
            val fm = view.paint.fontMetrics
            val lineRatio = (fm.descent - fm.ascent) / view.paint.textSize.coerceAtLeast(1f)
            val limit = maxPx / (lines * lineRatio.coerceAtLeast(1f))
            view.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx.coerceAtMost(limit))
        }
        capped(binding.clockWords, resources.getDimension(R.dimen.time_words_size), 2)

        // Poster clock: as big as the dimen asks, shrunk so "23:59" fits the row at any font scale.
        val clock = binding.clock
        val desiredPx = resources.getDimension(R.dimen.time_size)
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, desiredPx)
        val fm = clock.paint.fontMetrics
        val lineRatio = (fm.descent - fm.ascent) / desiredPx.coerceAtLeast(1f)
        val sampleWidth = clock.paint.measureText(PosterClock.WIDEST_SAMPLE) +
            clock.paddingStart + clock.paddingEnd
        val fitted = PosterClock.fitTextSizePx(
            desiredPx = desiredPx,
            sampleWidthAtDesiredPx = sampleWidth,
            availableWidthPx = topTextAvailableWidthPx(),
            maxLineHeightPx = maxPx,
            lineHeightRatio = lineRatio,
        )
        clock.setTextSize(TypedValue.COMPLEX_UNIT_PX, fitted)
    }

    /** Width the clock and info lines can use: the screen minus the top block's side margins. */
    private fun topTextAvailableWidthPx(): Float {
        val lp = binding.dateTimeLayout.layoutParams as ViewGroup.MarginLayoutParams
        val parentWidth = binding.mainLayout.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        return (parentWidth - lp.marginStart - lp.marginEnd -
            binding.dateTimeLayout.paddingStart - binding.dateTimeLayout.paddingEnd).toFloat()
    }

    /** Puts the weather under the date when both do not fit on one row. */
    private fun layoutDateWeatherRow() {
        val binding = _binding ?: return
        fun widthOf(view: TextView): Float {
            if (!view.isVisible) return 0f
            val text = view.text?.toString().orEmpty().uppercase(Locale.getDefault())
            return view.paint.measureText(text) + view.paddingStart + view.paddingEnd
        }
        val stacked = HomeMetaLine.shouldStack(
            dateWidthPx = widthOf(binding.date),
            separatorWidthPx = binding.weatherSeparator.paint.measureText("·") +
                binding.weatherSeparator.paddingStart + binding.weatherSeparator.paddingEnd,
            weatherWidthPx = widthOf(binding.weather),
            weatherShown = binding.weather.isVisible && binding.date.isVisible,
            availableWidthPx = topTextAvailableWidthPx(),
        )
        binding.dateWeatherRow.orientation = if (stacked) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        binding.weatherSeparator.isVisible = HomeMetaLine.showSeparator(
            binding.date.isVisible, binding.weather.isVisible, stacked,
        )
    }

    /** Stretches the wash from the top of the screen to just under the top text, then fades it out. */
    private fun sizeHomeWash() {
        val binding = _binding ?: return
        val bottom = if (binding.dateTimeLayout.isVisible) binding.dateTimeLayout.bottom else 0
        val height = if (bottom > 0) bottom + resources.getDimensionPixelSize(R.dimen.home_wash_fade) else 0
        val lp = binding.homeWash.layoutParams
        if (lp.height != height) {
            lp.height = height
            binding.homeWash.layoutParams = lp
        }
    }

    /** How many of [wanted] home app rows fit under the clock block without overlapping it. */
    private fun fittingHomeAppCount(wanted: Int): Int {
        val metrics = resources.displayMetrics
        val density = metrics.density.takeIf { it > 0f } ?: 1f
        val first = binding.homeApp1
        val fm = first.paint.fontMetrics
        val rowPx = (fm.descent - fm.ascent) + first.paddingTop + first.paddingBottom
        val clockBottomPx = if (binding.dateTimeLayout.isVisible) {
            val lp = binding.dateTimeLayout.layoutParams as ViewGroup.MarginLayoutParams
            lp.topMargin + binding.dateTimeLayout.height
        } else 0
        val topPx = maxOf(clockBottomPx.toFloat(), resources.getDimension(R.dimen.home_apps_top_min))
        val reservedPx = topPx + resources.getDimension(R.dimen.home_apps_bottom_reserved)
        return HomeFit.visibleCount(
            metrics.heightPixels / density, rowPx / density, reservedPx / density, wanted,
        )
    }

    private fun applyHomeFit() {
        val num = prefs.homeAppsNum
        if (num == 0) return
        val fitting = fittingHomeAppCount(num)
        homeAppViews().forEachIndexed { index, view ->
            if (index < num) view.visibility = if (index + 1 <= fitting) View.VISIBLE else View.GONE
        }
    }

    private fun homeAppViews(): List<TextView> = listOf(
        binding.homeApp1, binding.homeApp2, binding.homeApp3, binding.homeApp4,
        binding.homeApp5, binding.homeApp6, binding.homeApp7, binding.homeApp8,
    )

    private fun setHomeAppText(
        textView: TextView,
        appName: String,
        packageName: String,
        userString: String,
        isShortcut: Boolean,
        shortcutId: String?,
    ): Boolean {
        // Get user handle for the app/shortcut
        val userHandle = getUserHandleFromString(requireContext(), userString)

        // If it's a shortcut, verify it still exists
        if (isShortcut) {
            val launcherApps = requireContext().getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

            // Query for the specific shortcut
            val query = LauncherApps.ShortcutQuery().apply {
                setPackage(packageName)
                setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
            }

            try {
                val shortcuts = launcherApps.getShortcuts(query, userHandle)
                // Check if our shortcut still exists
                if (shortcuts?.any { it.id == shortcutId } == true) {
                    textView.text = appName
                    return true
                }
                textView.text = ""
                return false
            } catch (e: Exception) {
                e.printStackTrace()
                textView.text = ""
                return false
            }
        }

        // Regular app check
        if (isPackageInstalled(requireContext(), packageName, userString)) {
            textView.text = appName
            return true
        }
        textView.text = ""
        return false
    }

    private fun hideHomeApps() {
        binding.homeApp1.visibility = View.GONE
        binding.homeApp2.visibility = View.GONE
        binding.homeApp3.visibility = View.GONE
        binding.homeApp4.visibility = View.GONE
        binding.homeApp5.visibility = View.GONE
        binding.homeApp6.visibility = View.GONE
        binding.homeApp7.visibility = View.GONE
        binding.homeApp8.visibility = View.GONE
    }

    private fun launchAppOrShortcut(
        appName: String,
        packageName: String,
        activityClassName: String?,
        shortcutId: String?,
        isShortcut: Boolean,
        userString: String,
        fallback: (() -> Unit)? = null,
    ) {
        if (appName.isEmpty()) {
            showLongPressToast()
            return
        }
        if (isShortcut && !shortcutId.isNullOrEmpty()) {
            launchShortcut(
                packageName = packageName,
                shortcutId = shortcutId,
                shortcutLabel = appName,
                userString = userString
            )
        } else if (packageName.isNotEmpty()) {
            launchApp(
                appName = appName,
                packageName = packageName,
                activityClassName = activityClassName,
                userString = userString
            )
        } else {
            fallback?.invoke()
        }
    }

    private fun launchShortcut(shortcutId: String, packageName: String, shortcutLabel: String, userString: String) {
        viewModel.selectedApp(
            AppModel.PinnedShortcut(
                shortcutId = shortcutId,
                appLabel = shortcutLabel,
                user = getUserHandleFromString(requireContext(), userString),
                key = null,
                appPackage = packageName,
                isNew = false,
            ),
            Constants.FLAG_LAUNCH_APP
        )
    }

    private fun launchApp(appName: String, packageName: String, activityClassName: String?, userString: String) {
        val userHandle = getUserHandleFromString(requireContext(), userString)
        val launcher = requireContext().getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        val isArchived = launcher.getActivityList(packageName, userHandle).firstOrNull()?.applicationInfo?.isArchived == true
        viewModel.selectedApp(
            AppModel.App(
                appLabel = appName,
                key = null,
                appPackage = packageName,
                activityClassName = activityClassName,
                isNew = false,
                user = userHandle,
                isArchived = isArchived,
            ),
            Constants.FLAG_LAUNCH_APP
        )
    }

    private fun homeAppClicked(location: Int, view: View? = null) {
        val appName = prefs.getAppName(location)
        if (appName.isNotEmpty()) {
            Haptics.tick(view ?: binding.root)
        }
        launchAppOrShortcut(
            appName = appName,
            packageName = prefs.getAppPackage(location),
            activityClassName = prefs.getAppActivityClassName(location),
            shortcutId = prefs.getShortcutId(location),
            isShortcut = prefs.getIsShortcut(location),
            userString = prefs.getAppUser(location)
        )
    }

    private fun onHomeSwipeUp() {
        Haptics.tick(binding.root)
        showAppList(Constants.FLAG_LAUNCH_APP)
    }

    private fun onHomeSwipeDown(context: Context = requireContext()) {
        Haptics.tick(binding.root)
        expandNotificationDrawer(context)
    }

    private fun openSwipeRightApp() {
        if (!prefs.swipeRightEnabled) return
        Haptics.tick(binding.root)
        if (prefs.swipeRightAction == Prefs.SWIPE_RIGHT_ACTION_TODAY) {
            try {
                findNavController().navigate(R.id.action_mainFragment_to_todayFragment)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return
        }
        launchAppOrShortcut(
            appName = prefs.appNameSwipeRight,
            packageName = prefs.appPackageSwipeRight,
            activityClassName = prefs.appActivityClassNameRight,
            shortcutId = prefs.shortcutIdSwipeRight,
            isShortcut = prefs.isShortcutSwipeRight,
            userString = prefs.appUserSwipeRight,
            fallback = { openDialerApp(requireContext()) }
        )
    }

    private fun openSwipeLeftApp() {
        if (!prefs.swipeLeftEnabled) return
        Haptics.tick(binding.root)
        launchAppOrShortcut(
            appName = prefs.appNameSwipeLeft,
            packageName = prefs.appPackageSwipeLeft,
            activityClassName = prefs.appActivityClassNameSwipeLeft,
            shortcutId = prefs.shortcutIdSwipeLeft,
            isShortcut = prefs.isShortcutSwipeLeft,
            userString = prefs.appUserSwipeLeft,
            fallback = { openCameraApp(requireContext()) }
        )
    }

    private fun showAppList(flag: Int, rename: Boolean = false, includeHiddenApps: Boolean = false) {
        viewModel.getAppList(includeHiddenApps)
        try {
            findNavController().navigate(
                R.id.action_mainFragment_to_appListFragment,
                bundleOf(
                    Constants.Key.FLAG to flag,
                    Constants.Key.RENAME to rename
                )
            )
        } catch (e: Exception) {
            findNavController().navigate(
                R.id.appListFragment,
                bundleOf(
                    Constants.Key.FLAG to flag,
                    Constants.Key.RENAME to rename
                )
            )
            e.printStackTrace()
        }
    }

    private fun showStatusBar() {
        requireActivity().window.insetsController?.show(WindowInsets.Type.statusBars())
    }

    private fun hideStatusBar() {
        requireActivity().window.insetsController?.hide(WindowInsets.Type.statusBars())
    }

    private fun changeAppTheme() {
        val changedAppTheme = getChangedAppTheme(requireContext(), prefs.appTheme)
        prefs.appTheme = changedAppTheme
        if (prefs.photoWallpaperCount > 0) {
            PhotoWallpaper.applyCurrent(requireContext(), prefs)
        } else {
            setPlainWallpaperByTheme(requireContext(), changedAppTheme)
        }
        requireActivity().recreate()
    }

    private fun openScreenTimeDigitalWellbeing() {
        if (prefs.screenTimeAppPackage.isNotBlank()) {
            launchApp(
                "Screen Time",
                prefs.screenTimeAppPackage,
                prefs.screenTimeAppClassName,
                prefs.screenTimeAppUser
            )
            return
        }
        val intent = Intent()
        try {
            intent.setClassName(
                Constants.DIGITAL_WELLBEING_PACKAGE_NAME,
                Constants.DIGITAL_WELLBEING_ACTIVITY
            )
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                intent.setClassName(
                    Constants.DIGITAL_WELLBEING_SAMSUNG_PACKAGE_NAME,
                    Constants.DIGITAL_WELLBEING_SAMSUNG_ACTIVITY
                )
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showLongPressToast() = requireContext().showToast(getString(R.string.long_press_to_select_app))

    private fun textOnClick(view: View) = onClick(view)

    private fun textOnLongClick(view: View) = onLongClick(view)

    private fun getSwipeGestureListener(context: Context): View.OnTouchListener {
        return object : OnSwipeTouchListener(context) {
            override fun onSwipeLeft() {
                super.onSwipeLeft()
                openSwipeLeftApp()
            }

            override fun onSwipeRight() {
                super.onSwipeRight()
                openSwipeRightApp()
            }

            override fun onSwipeUp() {
                super.onSwipeUp()
                onHomeSwipeUp()
            }

            override fun onSwipeDown() {
                super.onSwipeDown()
                onHomeSwipeDown(requireContext())
            }

            override fun onLongClick() {
                super.onLongClick()
                if (WallpaperHomeMenu.isShown(prefs.photoWallpaperHomeMenu, prefs.photoWallpaperCount)) {
                    Haptics.longPress(binding.mainLayout)
                    showWallpaperHomeMenu(downY)
                } else {
                    openSettings()
                }
            }

            override fun onDoubleClick() {
                super.onDoubleClick()
                if (!prefs.lockModeOn) return
                binding.lock.performClick()
            }

            override fun onClick() {
                super.onClick()
                viewModel.checkForMessages.call()
            }
        }
    }

    // Anchored to a 1px-high strip at the press height: anchoring to the
    // full-screen layout drops the menu below the bottom edge, off screen.
    private fun showWallpaperHomeMenu(y: Float) {
        val appContext = requireContext().applicationContext
        val anchor = View(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
                topMargin = y.toInt()
            }
        }
        binding.mainLayout.addView(anchor)
        // Wait one layout pass so the anchor has its position.
        anchor.post {
            anchor.showPopupMenu(
                configure = { menu ->
                    menu.add(Menu.NONE, R.string.next_wallpaper, 0, R.string.next_wallpaper)
                    menu.add(Menu.NONE, R.string.previous_wallpaper, 1, R.string.previous_wallpaper)
                    menu.add(Menu.NONE, R.string.settings, 2, R.string.settings)
                }
            ) { item ->
                when (item.itemId) {
                    R.string.next_wallpaper -> {
                        viewLifecycleOwner.lifecycleScope.launch {
                            PhotoWallpaper.stepManually(appContext, prefs, forward = true)
                        }
                    }
                    R.string.previous_wallpaper -> {
                        viewLifecycleOwner.lifecycleScope.launch {
                            PhotoWallpaper.stepManually(appContext, prefs, forward = false)
                        }
                    }
                    R.string.settings -> openSettings()
                }
            }.setOnDismissListener { (anchor.parent as? ViewGroup)?.removeView(anchor) }
        }
    }

    private fun openSettings() {
        try {
            findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
            viewModel.firstOpen(false)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getViewSwipeTouchListener(context: Context, view: View): View.OnTouchListener {
        return object : ViewSwipeTouchListener(context, view) {
            override fun onSwipeLeft() {
                super.onSwipeLeft()
                openSwipeLeftApp()
            }

            override fun onSwipeRight() {
                super.onSwipeRight()
                openSwipeRightApp()
            }

            override fun onSwipeUp() {
                super.onSwipeUp()
                onHomeSwipeUp()
            }

            override fun onSwipeDown() {
                super.onSwipeDown()
                onHomeSwipeDown(requireContext())
            }

            override fun onLongClick(view: View) {
                super.onLongClick(view)
                textOnLongClick(view)
            }

            override fun onClick(view: View) {
                super.onClick(view)
                textOnClick(view)
            }
        }
    }

    private fun initWeatherListener() {
        weatherPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == Prefs.WEATHER_FETCHED_AT || key == Prefs.WEATHER_ENABLED) {
                activity?.runOnUiThread {
                    if (_binding != null) {
                        if (prefs.homeInfoCombined) populateDateTime()
                        populateWeather()
                    }
                }
            }
        }
        prefs.registerPreferenceChangeListener(weatherPrefsListener!!)
    }

    private fun populateWeather() {
        val binding = _binding ?: return
        val context = context ?: return
        if (prefs.homeInfoCombined || !prefs.weatherEnabled || prefs.dateTimeVisibility == Constants.DateTime.OFF) {
            binding.weather.isVisible = false
            binding.weatherSeparator.isVisible = false
            return
        }
        val cached = WeatherCache(context).read()
        val line = if (cached != null) {
            WeatherFormatting.homeLine(cached.first, cached.second)
        } else {
            null
        }

        if (line != null) {
            binding.weather.text = line
            binding.weather.isVisible = true
        } else {
            binding.weather.isVisible = false
        }
        // Only show the dot between the two when both are visible side by side.
        layoutDateWeatherRow()
    }

    override fun onDestroyView() {
        originalShadows.clear()
        originalShadowColors.clear()
        unregisterTimeTickReceiver()
        unregisterBatteryReceiver()
        weatherPrefsListener?.let {
            prefs.unregisterPreferenceChangeListener(it)
            weatherPrefsListener = null
        }
        super.onDestroyView()
        _binding = null
    }
}