package app.olauncher.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.LauncherApps
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.Bundle
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
import androidx.core.os.bundleOf
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
import app.olauncher.data.ShortcutAction
import app.olauncher.data.ShortcutOffset
import app.olauncher.data.ShortcutSize
import app.olauncher.data.SlotCount
import app.olauncher.databinding.FragmentHomeBinding
import app.olauncher.glance.WeatherCache
import app.olauncher.glance.WeatherFormatting
import app.olauncher.glance.WeatherLocation
import app.olauncher.helper.BatteryFormatting
import app.olauncher.helper.DefaultLauncherCheck
import app.olauncher.helper.GermanTime
import app.olauncher.helper.Haptics
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HomeFragment : BaseFragment(), View.OnClickListener, View.OnLongClickListener {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel

    private var timeTickReceiver: BroadcastReceiver? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private var weatherPrefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
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
        initWeatherListener()
    }

    override fun onResume() {
        super.onResume()
        setHomeAlignment()
        populateHomeScreen(false)
        populateShortcuts()
        populateWeather()
        WeatherLocation.refreshIfStale(requireContext())
        viewModel.isOlauncherDefault()
        if (prefs.showStatusBar) showStatusBar()
        else hideStatusBar()
        if (GermanTime.isWordsClockOn(prefs.germanEnabled, prefs.germanClock)) registerTimeTickReceiver()
        registerBatteryReceiver()
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

    override fun onPause() {
        super.onPause()
        unregisterTimeTickReceiver()
        unregisterBatteryReceiver()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.lock -> {}
            // Home button for recents feature disabled
            // R.id.recents -> {}
            R.id.clock, R.id.clockWords -> openClockApp()
            R.id.date -> openCalendarApp()
            R.id.weather -> openWeatherApp()
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

    private fun openClockApp() {
        if (prefs.clockAppPackage.isBlank())
            openAlarmApp(requireContext())
        else
            launchApp(
                "Clock",
                prefs.clockAppPackage,
                prefs.clockAppClassName,
                prefs.clockAppUser
            )
    }

    private fun openCalendarApp() {
        if (prefs.calendarAppPackage.isBlank())
            openCalendar(requireContext())
        else
            launchApp(
                "Calendar",
                prefs.calendarAppPackage,
                prefs.calendarAppClassName,
                prefs.calendarAppUser
            )
    }

    private fun openWeatherApp() {
        if (prefs.weatherAppPackage.isBlank())
            showAppList(Constants.FLAG_SET_WEATHER_APP)
        else
            launchApp(
                getString(R.string.weather),
                prefs.weatherAppPackage,
                prefs.weatherAppClassName,
                prefs.weatherAppUser
            )
    }

    override fun onLongClick(view: View): Boolean {
        when (view.id) {
            R.id.homeApp1 -> showAppList(Constants.FLAG_SET_HOME_APP_1, prefs.appName1.isNotEmpty(), true)
            R.id.homeApp2 -> showAppList(Constants.FLAG_SET_HOME_APP_2, prefs.appName2.isNotEmpty(), true)
            R.id.homeApp3 -> showAppList(Constants.FLAG_SET_HOME_APP_3, prefs.appName3.isNotEmpty(), true)
            R.id.homeApp4 -> showAppList(Constants.FLAG_SET_HOME_APP_4, prefs.appName4.isNotEmpty(), true)
            R.id.homeApp5 -> showAppList(Constants.FLAG_SET_HOME_APP_5, prefs.appName5.isNotEmpty(), true)
            R.id.homeApp6 -> showAppList(Constants.FLAG_SET_HOME_APP_6, prefs.appName6.isNotEmpty(), true)
            R.id.homeApp7 -> showAppList(Constants.FLAG_SET_HOME_APP_7, prefs.appName7.isNotEmpty(), true)
            R.id.homeApp8 -> showAppList(Constants.FLAG_SET_HOME_APP_8, prefs.appName8.isNotEmpty(), true)
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
            if (binding.firstRunTips.isVisible) return@Observer
            binding.setDefaultLauncher.isVisible = it.not() && prefs.hideSetDefaultLauncher.not()
        })
        viewModel.homeAppAlignment.observe(viewLifecycleOwner) {
            setHomeAlignment(it)
        }
        viewModel.toggleDateTime.observe(viewLifecycleOwner) {
            populateDateTime()
        }
        viewModel.screenTimeValue.observe(viewLifecycleOwner) {
            it?.let { binding.tvScreenTime.text = it }
        }
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

    private fun initClickListeners() {
        binding.lock.setOnClickListener(this)
        // Home button for recents feature disabled
        // binding.recents.setOnClickListener(this)
        binding.clock.setOnClickListener(this)
        binding.clockWords.setOnClickListener(this)
        binding.date.setOnClickListener(this)
        binding.weather.setOnClickListener(this)
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
        val viewSizeDp = ShortcutSize.viewSizeDp(prefs.shortcutSizeStep)
        val bottomMarginDp = ShortcutOffset.bottomMarginDp(prefs.shortcutOffsetStep)
        return SlotCount.fitting(prefs.shortcutCount, columnHeightDp, viewSizeDp, bottomMarginDp)
    }

    private fun setupShortcutViews() {
        val context = context ?: return
        val count = getFittingShortcutCount()
        val viewSizeDp = ShortcutSize.viewSizeDp(prefs.shortcutSizeStep)
        val viewSizePx = viewSizeDp.dpToPx()
        binding.shortcutsLayout.removeAllViews()
        for (slot in 1..count) {
            val imageView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    viewSizePx,
                    viewSizePx,
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
        val expectedViewSizePx = ShortcutSize.viewSizeDp(prefs.shortcutSizeStep).dpToPx()
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
        view.setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
        view.setImageDrawable(drawable)
        view.contentDescription = ShortcutIconRenderer.getLabel(context, slot, prefs, isAppIcon)
        // Extra slots (7..MAX_SHORTCUT_SLOTS) show a faint placeholder until an app icon resolves
        val isPlaceholder = !isAppIcon && prefs.getShortcutAction(slot) == null
        view.alpha = if (isPlaceholder) 0.4f else 1.0f
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

        binding.date.text = BatteryFormatting.dateLine(
            date = dateText,
            percent = percent,
            charging = charging,
            mode = prefs.batteryMode,
            statusBarShown = prefs.showStatusBar
        )
        binding.date.isVisible = isDateVisible
        populateWeather()
    }

    private fun updateClockWords() {
        if (_binding == null) return
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        binding.clockWords.text = GermanTime.words(hour, minute)
    }

    private fun registerTimeTickReceiver() {
        if (timeTickReceiver == null) {
            timeTickReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (intent?.action == Intent.ACTION_TIME_TICK) {
                        updateClockWords()
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

    private fun populateScreenTime() {
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

        val homeAppsNum = prefs.homeAppsNum
        if (homeAppsNum == 0) return

        binding.homeApp1.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp1, prefs.appName1, prefs.appPackage1, prefs.appUser1, prefs.isShortcut1, prefs.shortcutId1)) {
            prefs.appName1 = ""
            prefs.appPackage1 = ""
        }
        if (homeAppsNum == 1) return

        binding.homeApp2.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp2, prefs.appName2, prefs.appPackage2, prefs.appUser2, prefs.isShortcut2, prefs.shortcutId2)) {
            prefs.appName2 = ""
            prefs.appPackage2 = ""
        }
        if (homeAppsNum == 2) return

        binding.homeApp3.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp3, prefs.appName3, prefs.appPackage3, prefs.appUser3, prefs.isShortcut3, prefs.shortcutId3)) {
            prefs.appName3 = ""
            prefs.appPackage3 = ""
        }
        if (homeAppsNum == 3) return

        binding.homeApp4.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp4, prefs.appName4, prefs.appPackage4, prefs.appUser4, prefs.isShortcut4, prefs.shortcutId4)) {
            prefs.appName4 = ""
            prefs.appPackage4 = ""
        }
        if (homeAppsNum == 4) return

        binding.homeApp5.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp5, prefs.appName5, prefs.appPackage5, prefs.appUser5, prefs.isShortcut5, prefs.shortcutId5)) {
            prefs.appName5 = ""
            prefs.appPackage5 = ""
        }
        if (homeAppsNum == 5) return

        binding.homeApp6.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp6, prefs.appName6, prefs.appPackage6, prefs.appUser6, prefs.isShortcut6, prefs.shortcutId6)) {
            prefs.appName6 = ""
            prefs.appPackage6 = ""
        }
        if (homeAppsNum == 6) return

        binding.homeApp7.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp7, prefs.appName7, prefs.appPackage7, prefs.appUser7, prefs.isShortcut7, prefs.shortcutId7)) {
            prefs.appName7 = ""
            prefs.appPackage7 = ""
        }
        if (homeAppsNum == 7) return

        binding.homeApp8.visibility = View.VISIBLE
        if (!setHomeAppText(binding.homeApp8, prefs.appName8, prefs.appPackage8, prefs.appUser8, prefs.isShortcut8, prefs.shortcutId8)) {
            prefs.appName8 = ""
            prefs.appPackage8 = ""
        }
    }

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
        if (!prefs.weatherEnabled || prefs.dateTimeVisibility == Constants.DateTime.OFF) {
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
        // Only show the dot between the two when both are visible.
        binding.weatherSeparator.isVisible = binding.weather.isVisible && binding.date.isVisible
    }

    override fun onDestroyView() {
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