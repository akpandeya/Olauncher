package app.olauncher.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.view.setPadding
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.helper.Haptics
import app.olauncher.helper.hapticLongClick
import app.olauncher.helper.HomeSet
import app.olauncher.helper.HomeSets
import app.olauncher.data.ShortcutOffset
import app.olauncher.data.ShortcutSize
import app.olauncher.helper.HomeDateText
import app.olauncher.helper.HomeFit
import app.olauncher.helper.PosterClock
import app.olauncher.databinding.FragmentHomeBinding
import app.olauncher.glance.WeatherLocation
import app.olauncher.helper.BatteryFormatting
import app.olauncher.helper.BatteryReading
import app.olauncher.helper.ContextItem
import app.olauncher.helper.DefaultLauncherCheck
import app.olauncher.helper.GermanTime
import app.olauncher.helper.HomeTick
import app.olauncher.helper.AppTarget
import app.olauncher.helper.OpenTargets
import app.olauncher.helper.NowPlaying
import app.olauncher.helper.InfoLine
import app.olauncher.helper.Intention
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import app.olauncher.helper.appUsagePermissionGranted
import app.olauncher.helper.dpToPx
import app.olauncher.helper.getChangedAppTheme
import app.olauncher.helper.PhotoWallpaper
import app.olauncher.helper.RotationInterval
import app.olauncher.helper.setPlainWallpaperByTheme
import app.olauncher.helper.showToast
import app.olauncher.listener.ViewSwipeTouchListener
import app.olauncher.wellbeing.ScreenTimeCue
import app.olauncher.wellbeing.WindDownWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

class HomeFragment : BaseFragment(), View.OnClickListener, View.OnLongClickListener {

    internal lateinit var prefs: Prefs
    internal lateinit var viewModel: MainViewModel

    internal var timeTickReceiver: BroadcastReceiver? = null
    internal var renderedHomeSet: HomeSet? = null
    internal var batteryReceiver: BroadcastReceiver? = null
    private var lastBatteryReading: BatteryReading? = null
    internal var weatherPrefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    internal var latestScreenTime: String? = null
    private var lastRenderedEpochDay: Long? = null
    internal var nowPlaying: NowPlaying? = null
    internal var glowAnimator: ObjectAnimator? = null
    internal var glowShownColors: List<Int> = emptyList()
    internal var glowColorIndex = 0
    internal var glowColorAnimator: ValueAnimator? = null
    internal var glowRotating = false
    internal val glowRotationStep = Runnable { fadeToNextGlowColor() }
    internal var glowShownStrength: Int? = null
    internal var homeVisible = false
    internal val musicTick = Runnable { refreshContextLine() }
    internal var contextItem: ContextItem? = null
    internal var nextEventId: Long? = null
    internal var contextJob: Job? = null
    internal var wallpaperColorsListener: WallpaperManager.OnColorsChangedListener? = null
    internal val originalShadows = HashMap<TextView, FloatArray>()
    internal val originalShadowColors = HashMap<TextView, Int>()
    internal val originalTextColors = HashMap<TextView, Int>()
    internal var homeAppsPaddingStart = 0
    internal var homeAppsPaddingEnd = 0
    internal var _binding: FragmentHomeBinding? = null
    internal val binding get() = _binding!!

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
        homeVisible = true
        setHomeAlignment()
        populateHomeScreen(false)
        populateShortcuts()
        populateWeather()
        applyWeatherEffects()
        refreshWeatherMemory()
        applyTextContrast()
        WeatherLocation.refreshIfStale(requireContext())
        viewModel.isOlauncherDefault()
        if (prefs.showStatusBar) showStatusBar()
        else hideStatusBar()
        if (HomeTick.needsTick(
                prefs.homeSets,
                GermanTime.isWordsClockOn(prefs.germanEnabled, prefs.germanClock),
                prefs.homeContextLine,
                prefs.homeMusicPulse,
            )
        ) registerTimeTickReceiver()
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

    internal var onOpenRotationJob: Job? = null

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


    override fun onPause() {
        super.onPause()
        homeVisible = false
        _binding?.weatherEffects?.setRunning(false)
        unregisterTimeTickReceiver()
        unregisterBatteryReceiver()
        stopContextLine()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.lock -> Haptics.confirm(view)
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

    internal fun launchTarget(target: AppTarget) =
        launchApp(target.label, target.pkg, target.className, target.user)

    internal fun openClockApp() =
        OpenTargets.openClockApp(requireContext(), prefs, ::launchTarget)

    internal fun openCalendarApp() =
        OpenTargets.openCalendarApp(requireContext(), prefs, ::launchTarget)

    internal fun openWeatherApp() =
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


    internal fun initObservers() {
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



    internal fun initClickListeners() {
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
        binding.clock.hapticLongClick { onLongClick(binding.clock) }
        binding.clockWords.hapticLongClick { onLongClick(binding.clockWords) }
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
        binding.homeApp1.hapticLongClick { onLongClick(binding.homeApp1) }
        binding.homeApp2.hapticLongClick { onLongClick(binding.homeApp2) }
        binding.homeApp3.hapticLongClick { onLongClick(binding.homeApp3) }
        binding.homeApp4.hapticLongClick { onLongClick(binding.homeApp4) }
        binding.homeApp5.hapticLongClick { onLongClick(binding.homeApp5) }
        binding.homeApp6.hapticLongClick { onLongClick(binding.homeApp6) }
        binding.homeApp7.hapticLongClick { onLongClick(binding.homeApp7) }
        binding.homeApp8.hapticLongClick { onLongClick(binding.homeApp8) }
    }

    internal fun setHomeAlignment(horizontalGravity: Int = prefs.homeAlignment) {
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




    internal fun populateDateTime() {
        lastRenderedEpochDay = LocalDate.now().toEpochDay()
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

        val reading = currentBatteryReading()
        lastBatteryReading = reading
        composeDateLine(reading)
        if (prefs.homeInfoCombined) {
            if (context?.appUsagePermissionGranted() == true) viewModel.getTodaysScreenTime()
            binding.tvScreenTime.isVisible = false
        } else {
            populateScreenTime()
        }
        binding.date.isVisible = isDateVisible
        populateWeather()
        refreshContextLine()
        refreshIntentionLine()
    }

    /** Sets the date line text from cached values only: no screen-time, weather or calendar calls. */
    private fun composeDateLine(reading: BatteryReading) {
        val isGerman = prefs.germanEnabled && prefs.germanClock != Prefs.GERMAN_CLOCK_SYSTEM
        val percent = reading.percent
        val charging = reading.charging
        if (prefs.homeInfoCombined) {
            val combinedDate = HomeDateText.format(LocalDate.now(), isGerman, true, Locale.getDefault())
            val showScreenTimeNumber = context?.appUsagePermissionGranted() == true && !isScreenTimeCueOn()
            binding.date.text = InfoLine.compose(
                date = combinedDate,
                weather = combinedWeatherText(),
                battery = BatteryFormatting.infoText(percent, charging, prefs.batteryMode, prefs.showStatusBar),
                screenTime = if (showScreenTimeNumber) latestScreenTime else null
            )
        } else {
            binding.date.text = BatteryFormatting.dateLine(
                date = HomeDateText.format(LocalDate.now(), isGerman, false, Locale.getDefault()),
                percent = percent,
                charging = charging,
                mode = prefs.batteryMode,
                statusBarShown = prefs.showStatusBar
            )
        }
    }

    private fun readingFrom(intent: Intent?): BatteryReading {
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100) / scale else -1
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return BatteryReading(percent, plugged != 0)
    }

    private fun currentBatteryReading(): BatteryReading =
        readingFrom(context?.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))

    private fun onBatteryBroadcast(intent: Intent?) {
        if (_binding == null) return
        val source = if (intent?.action == Intent.ACTION_BATTERY_CHANGED) intent else null
        val reading = if (source != null) readingFrom(source) else currentBatteryReading()
        if (!BatteryFormatting.readingChanged(lastBatteryReading, reading)) return
        lastBatteryReading = reading
        composeDateLine(reading)
    }

    internal fun refreshIntentionLine() {
        if (_binding == null) return
        val text = if (prefs.intentionOnHome) {
            Intention.current(prefs.intentionText, prefs.intentionDay, LocalDateTime.now())
        } else null
        binding.intentionLine.text = text
        binding.intentionLine.isVisible = text != null
    }




    internal fun openToday() {
        try {
            findNavController().navigate(R.id.action_mainFragment_to_todayFragment)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    internal fun updateClockWords() {
        if (_binding == null) return
        val now = LocalTime.now()
        binding.clockWords.text = GermanTime.words(now.hour, now.minute)
    }

    internal fun refreshHomeSetIfChanged() {
        if (_binding == null) return
        if (prefs.activeHomeSet() != renderedHomeSet) populateHomeScreen(false)
    }

    internal fun registerTimeTickReceiver() {
        if (timeTickReceiver == null) {
            timeTickReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_TIME_TICK) {
                        if (GermanTime.isWordsClockOn(prefs.germanEnabled, prefs.germanClock)) updateClockWords()
                        refreshHomeSetIfChanged()
                        if (prefs.homeContextLine || prefs.homeMusicPulse) refreshContextLine()
                        if (_binding != null && HomeTick.dayChanged(lastRenderedEpochDay, LocalDate.now().toEpochDay())) populateDateTime()
                    }
                }
            }
            requireContext().registerReceiver(timeTickReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
        }
    }

    internal fun unregisterTimeTickReceiver() {
        timeTickReceiver?.let {
            try {
                requireContext().unregisterReceiver(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            timeTickReceiver = null
        }
    }

    internal fun registerBatteryReceiver() {
        if (batteryReceiver == null) {
            batteryReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    onBatteryBroadcast(intent)
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

    internal fun unregisterBatteryReceiver() {
        batteryReceiver?.let {
            try {
                requireContext().unregisterReceiver(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            batteryReceiver = null
        }
        lastBatteryReading = null
    }

    internal fun isScreenTimeCueOn(): Boolean =
        prefs.screenTimeStyle == ScreenTimeCue.STYLE_CUE && context?.appUsagePermissionGranted() == true

    /** Dims the clock and home app names once today's screen time reaches the target. Needs usage access. */
    internal fun applyScreenTimeCue() {
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

    internal fun populateScreenTime() {
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

    internal fun populateHomeScreen(appCountUpdated: Boolean) {
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
    internal fun capClockSize() {
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
    internal fun topTextAvailableWidthPx(): Float {
        val lp = binding.dateTimeLayout.layoutParams as ViewGroup.MarginLayoutParams
        val parentWidth = binding.mainLayout.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        return (parentWidth - lp.marginStart - lp.marginEnd -
            binding.dateTimeLayout.paddingStart - binding.dateTimeLayout.paddingEnd).toFloat()
    }


    /** Stretches the wash from the top of the screen to just under the top text, then fades it out. Strong covers the whole screen. */
    internal fun sizeHomeWash() {
        val binding = _binding ?: return
        val bottom = if (binding.dateTimeLayout.isVisible) binding.dateTimeLayout.bottom else 0
        val height = when {
            prefs.homeWashLevel == Prefs.WASH_STRONG -> ViewGroup.LayoutParams.MATCH_PARENT
            bottom > 0 -> bottom + resources.getDimensionPixelSize(R.dimen.home_wash_fade)
            else -> 0
        }
        val lp = binding.homeWash.layoutParams
        if (lp.height != height) {
            lp.height = height
            binding.homeWash.layoutParams = lp
        }
    }

    /** How many of [wanted] home app rows fit under the clock block without overlapping it. */
    internal fun fittingHomeAppCount(wanted: Int): Int {
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

    internal fun applyHomeFit() {
        val num = prefs.homeAppsNum
        if (num == 0) return
        val fitting = fittingHomeAppCount(num)
        homeAppViews().forEachIndexed { index, view ->
            if (index < num) view.visibility = if (index + 1 <= fitting) View.VISIBLE else View.GONE
        }
    }

    internal fun homeAppViews(): List<TextView> = listOf(
        binding.homeApp1, binding.homeApp2, binding.homeApp3, binding.homeApp4,
        binding.homeApp5, binding.homeApp6, binding.homeApp7, binding.homeApp8,
    )


    internal fun hideHomeApps() {
        binding.homeApp1.visibility = View.GONE
        binding.homeApp2.visibility = View.GONE
        binding.homeApp3.visibility = View.GONE
        binding.homeApp4.visibility = View.GONE
        binding.homeApp5.visibility = View.GONE
        binding.homeApp6.visibility = View.GONE
        binding.homeApp7.visibility = View.GONE
        binding.homeApp8.visibility = View.GONE
    }




    internal fun showAppList(flag: Int, rename: Boolean = false, includeHiddenApps: Boolean = false) {
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

    internal fun showStatusBar() {
        requireActivity().window.insetsController?.show(WindowInsets.Type.statusBars())
    }

    internal fun hideStatusBar() {
        requireActivity().window.insetsController?.hide(WindowInsets.Type.statusBars())
    }

    internal fun changeAppTheme() {
        val changedAppTheme = getChangedAppTheme(requireContext(), prefs.appTheme)
        prefs.appTheme = changedAppTheme
        if (prefs.photoWallpaperCount > 0) {
            PhotoWallpaper.applyCurrent(requireContext(), prefs)
        } else {
            setPlainWallpaperByTheme(requireContext(), changedAppTheme)
        }
        requireActivity().recreate()
    }

    internal fun openScreenTimeDigitalWellbeing() {
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

    internal fun showLongPressToast() = requireContext().showToast(getString(R.string.long_press_to_select_app))







    override fun onDestroyView() {
        glowAnimator?.cancel()
        glowAnimator = null
        // The next view starts without a glow background, so forget what was drawn.
        stopGlowRotation()
        glowShownColors = emptyList()
        glowShownStrength = null
        _binding?.root?.removeCallbacks(musicTick)
        _binding?.mediaProgress?.setRunning(false)
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