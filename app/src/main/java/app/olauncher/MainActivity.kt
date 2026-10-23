package app.olauncher

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.res.Configuration
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import app.olauncher.data.store.WordLookup
import app.olauncher.quiet.QuietAccess
import app.olauncher.quiet.ReleaseWorker
import app.olauncher.data.Accent
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.data.ProfileFeature
import app.olauncher.data.ProfilePermission
import app.olauncher.data.ProfilePermissionRules
import app.olauncher.data.ProfilePicker
import app.olauncher.data.ProfilePickerOption
import app.olauncher.glance.WeatherLocation
import app.olauncher.glance.WeatherWorker
import app.olauncher.wellbeing.NotificationPermission
import app.olauncher.wellbeing.NudgeScheduler
import app.olauncher.wellbeing.Nudges
import app.olauncher.wellbeing.UsageSnapshotWorker
import app.olauncher.databinding.ActivityMainBinding
import app.olauncher.helper.AppListReloadPolicy
import app.olauncher.helper.AppListReloadTrigger
import app.olauncher.databinding.DialogOnboardingDefaultBinding
import app.olauncher.helper.BackRules
import app.olauncher.helper.HomeAppHelper
import app.olauncher.helper.Onboarding
import app.olauncher.helper.OnboardingState
import app.olauncher.helper.OnboardingStep
import app.olauncher.helper.OnboardingStore
import app.olauncher.helper.createDialog
import app.olauncher.helper.HomeReturnPolicy
import app.olauncher.helper.MotionPolicy
import app.olauncher.helper.WordCaptureActivity
import app.olauncher.helper.WordScreenService
import app.olauncher.helper.applyFontOverlay
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.hasBeenDays
import app.olauncher.helper.hasBeenHours
import app.olauncher.helper.hasBeenMinutes
import app.olauncher.helper.isDarkThemeOn
import app.olauncher.helper.isDaySince
import app.olauncher.helper.isDefaultLauncher
import app.olauncher.helper.OlDialog
import app.olauncher.helper.isEinkDisplay
import app.olauncher.helper.isOlauncherDefault
import app.olauncher.helper.isSystemAnimationsDisabled
import app.olauncher.helper.isTablet
import app.olauncher.helper.openUrl
import app.olauncher.helper.resetLauncherViaFakeActivity
import app.olauncher.helper.PhotoWallpaper
import app.olauncher.helper.setPlainWallpaper
import app.olauncher.helper.showLauncherSelector
import app.olauncher.helper.showMessageDialog
import app.olauncher.helper.showToast
import app.olauncher.ui.today.TodayWidgets
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_NAVIGATE_TO = "navigate_to"
        const val DESTINATION_TODAY = "todayFragment"
        private const val ONBOARDING_WATER_INTERVAL_MIN = 120
        var animatorsEnabledSupplier: () -> Boolean = { ValueAnimator.areAnimatorsEnabled() }
    }

    private lateinit var prefs: Prefs
    private lateinit var navController: NavController
    private lateinit var viewModel: MainViewModel
    private lateinit var binding: ActivityMainBinding
    private var timerJob: Job? = null
    private var isResumed = false
    private var profileReceiver: BroadcastReceiver? = null
    private var launcherAppsCallback: LauncherApps.Callback? = null
    private var messageDialog: OlDialog? = null
    private var onboardingRowsRefresh: (() -> Unit)? = null
    private var onboardingProfileChoice = ProfilePicker.initial
    private var onboardingProfileWantsWeather = false
    private var onboardingProfileWeatherEffects = false

    // Welcome step 3 launchers. Each switches its feature on only when the permission is granted.
    private val onboardingWeatherPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) enableOnboardingWeather()
        onboardingRowsRefresh?.invoke()
    }
    // A profile with weather asks here; weather and its effects switch on only if granted.
    private val onboardingProfileWeatherPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            enableOnboardingWeather()
            prefs.homeWeatherEffects = onboardingProfileWeatherEffects
        }
        onboardingRowsRefresh?.invoke()
    }
    private val onboardingCalendarPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) prefs.homeNextEvent = true
        onboardingRowsRefresh?.invoke()
    }
    private val onboardingNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) enableOnboardingWaterNudge()
        onboardingRowsRefresh?.invoke()
    }

    // Manual profile switch: permissions offered for features the profile would turn on.
    private var profilePendingId: String? = null
    private var profileMissingBefore = 0
    private var profileWantsListener = false
    private var profileNeedsAppSettings = false
    private var profileRecheckId: String? = null
    private var profileRecheckLeft = false
    private val profilePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> onProfilePermissionResult(result) }

//    override fun onBackPressed() {
//        if (navController.currentDestination?.id != R.id.mainFragment)
//            super.onBackPressed()
//    }

    override fun attachBaseContext(context: Context) {
        val newConfig = Configuration(context.resources.configuration)
        newConfig.fontScale = app.olauncher.helper.effectiveFontScale(newConfig.fontScale, Prefs(context).textSizeScale)
        applyOverrideConfiguration(newConfig)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        prefs.checkRollovers()
        UsageSnapshotWorker.syncWithDistractingApps(this, prefs.distractingApps)
        WordCaptureActivity.setEnabled(this, prefs.germanEnabled, WordLookup.isLookupAllowed(prefs.germanEnabled, prefs.germanLookup))
        WordScreenService.setEnabled(this, prefs.germanEnabled && prefs.germanScreenPick)
        WorkManager.getInstance(this).cancelUniqueWork(Constants.WALLPAPER_WORKER_NAME)
        prefs.dailyWallpaper = false
        NudgeScheduler.ensureAll(this)
        if (prefs.quietEnabled && QuietAccess.isNotificationAccessGranted(this)) {
            ReleaseWorker.schedule(this, ExistingWorkPolicy.KEEP)
        }
        if (isEinkDisplay()) prefs.appTheme = AppCompatDelegate.MODE_NIGHT_NO
        AppCompatDelegate.setDefaultNightMode(prefs.appTheme)
        super.onCreate(savedInstanceState)
        prefs.applyFontOverlay(theme)
        val accentOverlay = Accent.resolve(Accent.fromId(prefs.accent), 0) {
            when (it) {
                Accent.SYSTEM -> R.style.AccentSystemOverlay
                Accent.WALLPAPER -> 0
                Accent.SAGE -> R.style.AccentSageOverlay
                Accent.SAND -> R.style.AccentSandOverlay
                Accent.SKY -> R.style.AccentSkyOverlay
                else -> R.style.AccentRoseOverlay
            }
        }
        if (accentOverlay != 0) theme.applyStyle(accentOverlay, true)
        if (isEinkDisplay() || isSystemAnimationsDisabled()) theme.applyStyle(R.style.NoAnimationOverlay, true)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        navController = this.findNavController(R.id.nav_host_fragment)
        applyNavigationMotion()
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        // Enabled only off home, so back on home stays with the system (predictive-back preview)
        val onBackPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                navController.popBackStack()
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
        navController.addOnDestinationChangedListener { _, destination, _ ->
            // Back from the home app picker: bring the welcome step back.
            if (destination.id == R.id.mainFragment && messageDialog?.isShowing != true) {
                val saved = OnboardingStore.stateFor(prefs.onboardingState)
                if (prefs.onboardingState == OnboardingStep.PICK_HOME_APPS.name)
                    showOnboardingStep(saved)
            }
            onBackPressedCallback.isEnabled = BackRules.shouldConsumeBack(
                isHome = destination.id == R.id.mainFragment,
                searchOpen = false
            )
        }

        val welcomeShown = savedInstanceState == null &&
            OnboardingStore.shouldShow(prefs.onboardingState, prefs.firstOpen)
        if (welcomeShown)
            showOnboardingStep(OnboardingStore.stateFor(prefs.onboardingState))
        else if (prefs.onboardingState.isEmpty())
            prefs.onboardingState = OnboardingStore.COMPLETE

        if (prefs.firstOpen) {
            viewModel.firstOpen(true)
            prefs.firstOpen = false
            prefs.firstOpenTime = System.currentTimeMillis()
            viewModel.setDefaultClockApp()
            if (OnboardingStore.shouldAutoRequestHomeRole(welcomeShown))
                viewModel.resetLauncherLiveData.call()
        }

        initObservers(viewModel)
        // onResume loads the app list right after onCreate.
        registerShortcutCallback()
        setupOrientation()

        window.addFlags(FLAG_LAYOUT_NO_LIMITS)

        profileReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                viewModel.isPrivateSpaceToggling = false
                viewModel.getPrivateSpaceAppList()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_PROFILE_UNAVAILABLE)
        }
        registerReceiver(profileReceiver, filter)
        // On restore the system re-delivers the original intent; handle the Today request once.
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        restartLauncherOrCheckTheme()
    }

    override fun onResume() {
        super.onResume()
        applyNavigationMotion()
        prefs.checkRollovers()
        isResumed = true
        viewModel.isPrivateSpaceToggling = false
        // A picker result can launch bind/configuration before this intermediate resume.
        viewModel.isAwaitingActivityResult = hasPendingTodayWidget
        reloadAppList(AppListReloadTrigger.RESUME)
        advanceWelcomeIfDefault()
        NudgeScheduler.syncSocial(this)
        finishProfileRecheck()
    }

    /**
     * Offers the permissions a profile's features need, when a manual switch left some features off.
     * One dialog lists them; Allow asks for each in turn. A refusal is remembered per permission,
     * so it is not asked again unless [ignoreDeclined] (the "Allow missing features" item).
     * Features stay off on refusal; the rest of the profile already applies.
     */
    fun offerProfilePermissions(profileId: String, ignoreDeclined: Boolean = false) {
        val profile = prefs.profiles().firstOrNull { it.id == profileId } ?: return
        val missing = ProfilePermissionRules.missing(profile.settings, prefs.currentProfilePermissions())
        val declined = ProfilePermissionRules.parseDeclined(prefs.profilePermissionDeclined)
        val ask = ProfilePermissionRules.toAsk(missing, if (ignoreDeclined) emptySet() else declined)
        if (ask.isEmpty()) return
        val lines = ProfilePermissionRules.featuresFor(missing, ask).joinToString("\n") { "• " + getString(profileFeatureLabel(it)) }
        var allowed = false
        messageDialog?.dismiss()
        val shown = showMessageDialog(
            R.string.profile_permissions_title,
            getString(R.string.profile_permissions_message, profile.name, lines),
            R.string.allow,
        ) {
            allowed = true
            requestProfilePermissions(profileId, ask, missing.size)
        }
        shown.setOnDismissListener { if (!allowed) markProfilePermissionsDeclined(ask) }
        messageDialog = shown
    }

    private fun profileFeatureLabel(feature: ProfileFeature): Int = when (feature) {
        ProfileFeature.WEATHER -> R.string.profile_feature_weather
        ProfileFeature.CONTACTS_SEARCH -> R.string.profile_feature_contacts
        ProfileFeature.NEXT_EVENT -> R.string.profile_feature_next_event
        ProfileFeature.MUSIC_LINE -> R.string.profile_feature_music_line
        ProfileFeature.MUSIC_PULSE -> R.string.profile_feature_music_pulse
        ProfileFeature.QUIET -> R.string.profile_feature_quiet
    }

    private fun runtimePermissionOf(permission: ProfilePermission): String? = when (permission) {
        ProfilePermission.LOCATION -> Manifest.permission.ACCESS_COARSE_LOCATION
        ProfilePermission.CONTACTS -> Manifest.permission.READ_CONTACTS
        ProfilePermission.CALENDAR -> Manifest.permission.READ_CALENDAR
        ProfilePermission.NOTIFICATION_ACCESS -> null
    }

    private fun markProfilePermissionsDeclined(permissions: Collection<ProfilePermission>) {
        val declined = ProfilePermissionRules.parseDeclined(prefs.profilePermissionDeclined) + permissions
        prefs.profilePermissionDeclined = ProfilePermissionRules.declinedNames(declined)
    }

    private fun requestProfilePermissions(profileId: String, ask: List<ProfilePermission>, missingBefore: Int) {
        profilePendingId = profileId
        profileMissingBefore = missingBefore
        val declined = ProfilePermissionRules.parseDeclined(prefs.profilePermissionDeclined)
        // Android shows no prompt after two refusals; those go to app settings instead.
        val blocked = ask.mapNotNull { p ->
            runtimePermissionOf(p)?.takeIf {
                p in declined && !shouldShowRequestPermissionRationale(it)
            }?.let { p }
        }
        val runtime = ask.filter { it !in blocked }.mapNotNull { runtimePermissionOf(it) }
        profileWantsListener = ProfilePermission.NOTIFICATION_ACCESS in ask
        profileNeedsAppSettings = blocked.isNotEmpty()
        if (runtime.isEmpty()) continueProfileRequest() else profilePermissionLauncher.launch(runtime.toTypedArray())
    }

    private fun onProfilePermissionResult(result: Map<String, Boolean>) {
        val refused = ProfilePermission.values().filter { p ->
            runtimePermissionOf(p)?.let { result[it] == false } == true
        }
        markProfilePermissionsDeclined(refused)
        continueProfileRequest()
    }

    /** After the runtime prompts: app settings for blocked ones, then notification access, then apply. */
    private fun continueProfileRequest() {
        val id = profilePendingId ?: return
        if (profileNeedsAppSettings) {
            profileNeedsAppSettings = false
            if (openProfileExternalSettings(id, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", packageName, null)))) return
        }
        if (profileWantsListener && !QuietAccess.isNotificationAccessGranted(this)) {
            profileWantsListener = false
            if (openProfileExternalSettings(id, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))) return
        }
        profileWantsListener = false
        reapplyProfileAfterPermissions(id)
    }

    private fun openProfileExternalSettings(profileId: String, intent: Intent): Boolean = try {
        profileRecheckId = profileId
        profileRecheckLeft = false
        startActivity(intent)
        true
    } catch (_: Exception) {
        profileRecheckId = null
        false
    }

    /** Back from system settings: apply the profile again if that granted something. */
    private fun finishProfileRecheck() {
        val id = profileRecheckId ?: return
        if (!profileRecheckLeft) return
        profileRecheckId = null
        profileRecheckLeft = false
        if (profileWantsListener) {
            // Still to do: notification access after app settings.
            continueProfileRequest()
            return
        }
        reapplyProfileAfterPermissions(id)
    }

    private fun reapplyProfileAfterPermissions(profileId: String) {
        profilePendingId = null
        val profile = prefs.profiles().firstOrNull { it.id == profileId } ?: return
        val granted = prefs.currentProfilePermissions()
        val stillMissing = ProfilePermissionRules.missing(profile.settings, granted)
        if (stillMissing.size >= profileMissingBefore) {
            // Nothing new was allowed: remember a refused notification access, leave the settings alone.
            if (ProfileFeature.MUSIC_LINE in stillMissing || ProfileFeature.MUSIC_PULSE in stillMissing || ProfileFeature.QUIET in stillMissing) {
                markProfilePermissionsDeclined(listOf(ProfilePermission.NOTIFICATION_ACCESS))
            }
            return
        }
        prefs.switchProfile(profileId)
        if (prefs.weatherEnabled && WeatherLocation.hasPermission(this)) enableOnboardingWeather()
        if (prefs.quietEnabled && QuietAccess.isNotificationAccessGranted(this)) ReleaseWorker.schedule(this, ExistingWorkPolicy.KEEP)
        viewModel.refreshHome(true)
    }

    /** A welcome still on step 1 moves on once the user has made Thumbline the default launcher. */
    private fun advanceWelcomeIfDefault() {
        if (prefs.onboardingState != OnboardingStep.SET_DEFAULT_LAUNCHER.name) return
        val current = OnboardingStore.stateFor(prefs.onboardingState)
        val next = OnboardingStore.advancedIfDefault(current, isOlauncherDefault(this))
        if (next != current) showOnboardingStep(next)
    }

    private fun reloadAppList(trigger: AppListReloadTrigger) {
        if (AppListReloadPolicy.shouldReload(trigger, viewModel.appList.value != null)) {
            viewModel.getAppList()
        }
    }

    fun applyNavigationMotion() {
        val animatorsEnabled = animatorsEnabledSupplier() && !isEinkDisplay()
        val durationMs = MotionPolicy.durationMs(MotionPolicy.DRAWER_DURATION_MS, animatorsEnabled)
        val enabled = animatorsEnabled && durationMs > 0L
        applyNavActionAnimations(enabled)
    }

    private fun applyNavActionAnimations(enabled: Boolean) {
        if (!::navController.isInitialized) return

        val drawerEnter = if (enabled) R.animator.drawer_enter else 0
        val drawerExit = if (enabled) R.animator.drawer_exit else 0
        val todayEnter = if (enabled) R.animator.today_enter else 0
        val todayExit = if (enabled) R.animator.today_exit else 0
        val fadeEnter = if (enabled) R.animator.fade_enter else 0
        val fadeExit = if (enabled) R.animator.fade_exit else 0

        // Drawer
        updateActionAnimations(
            destinationId = R.id.mainFragment,
            actionId = R.id.action_mainFragment_to_appListFragment,
            enterAnim = drawerEnter,
            exitAnim = 0,
            popEnterAnim = 0,
            popExitAnim = drawerExit,
        )
        updateActionAnimations(
            destinationId = R.id.todayFragment,
            actionId = R.id.action_todayFragment_to_appListFragment,
            enterAnim = drawerEnter,
            exitAnim = 0,
            popEnterAnim = 0,
            popExitAnim = drawerExit,
        )

        // Today
        updateActionAnimations(
            destinationId = R.id.mainFragment,
            actionId = R.id.action_mainFragment_to_todayFragment,
            enterAnim = todayEnter,
            exitAnim = 0,
            popEnterAnim = 0,
            popExitAnim = todayExit,
        )

        // Settings and Pause keep their current behavior (fade when animations on, instant when off)
        updateActionAnimations(
            destinationId = R.id.mainFragment,
            actionId = R.id.action_mainFragment_to_settingsFragment,
            enterAnim = fadeEnter,
            exitAnim = fadeExit,
            popEnterAnim = fadeEnter,
            popExitAnim = fadeExit,
        )
        updateActionAnimations(
            destinationId = R.id.mainFragment,
            actionId = R.id.action_mainFragment_to_pauseFragment,
            enterAnim = fadeEnter,
            exitAnim = fadeExit,
            popEnterAnim = fadeEnter,
            popExitAnim = fadeExit,
        )
        updateActionAnimations(
            destinationId = R.id.appListFragment,
            actionId = R.id.action_appListFragment_to_settingsFragment2,
            enterAnim = fadeEnter,
            exitAnim = fadeExit,
            popEnterAnim = fadeEnter,
            popExitAnim = fadeExit,
        )
        updateActionAnimations(
            destinationId = R.id.appListFragment,
            actionId = R.id.action_appListFragment_to_pauseFragment,
            enterAnim = fadeEnter,
            exitAnim = fadeExit,
            popEnterAnim = fadeEnter,
            popExitAnim = fadeExit,
        )
        updateActionAnimations(
            destinationId = R.id.settingsFragment,
            actionId = R.id.action_settingsFragment_to_appListFragment,
            enterAnim = fadeEnter,
            exitAnim = fadeExit,
            popEnterAnim = fadeEnter,
            popExitAnim = fadeExit,
        )
        updateActionAnimations(
            destinationId = R.id.todayFragment,
            actionId = R.id.action_todayFragment_to_wordsFragment,
            enterAnim = fadeEnter,
            exitAnim = fadeExit,
            popEnterAnim = fadeEnter,
            popExitAnim = fadeExit,
        )
    }

    private fun updateActionAnimations(
        destinationId: Int,
        actionId: Int,
        enterAnim: Int,
        exitAnim: Int,
        popEnterAnim: Int,
        popExitAnim: Int,
    ) {
        val destination = navController.graph.findNode(destinationId) ?: return
        val action = destination.getAction(actionId) ?: return
        val currentOptions = action.navOptions
        val builder = NavOptions.Builder()
            .setEnterAnim(enterAnim)
            .setExitAnim(exitAnim)
            .setPopEnterAnim(popEnterAnim)
            .setPopExitAnim(popExitAnim)
        if (currentOptions != null) {
            builder.setLaunchSingleTop(currentOptions.shouldLaunchSingleTop())
            builder.setRestoreState(currentOptions.shouldRestoreState())
            if (currentOptions.popUpToId != -1) {
                builder.setPopUpTo(
                    currentOptions.popUpToId,
                    currentOptions.isPopUpToInclusive(),
                    currentOptions.shouldPopUpToSaveState(),
                )
            }
        }
        action.navOptions = builder.build()
    }

    private fun registerShortcutCallback() {
        val launcherApps = getSystemService(LauncherApps::class.java)
        launcherAppsCallback = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: android.os.UserHandle) =
                reloadAppList(AppListReloadTrigger.PACKAGE_REMOVED)

            override fun onPackageAdded(packageName: String, user: android.os.UserHandle) =
                reloadAppList(AppListReloadTrigger.PACKAGE_ADDED)

            override fun onPackageChanged(packageName: String, user: android.os.UserHandle) =
                reloadAppList(AppListReloadTrigger.PACKAGE_CHANGED)

            override fun onPackagesAvailable(
                packageNames: Array<out String>,
                user: android.os.UserHandle,
                replacing: Boolean,
            ) = reloadAppList(AppListReloadTrigger.PACKAGES_AVAILABILITY_CHANGED)

            override fun onPackagesUnavailable(
                packageNames: Array<out String>,
                user: android.os.UserHandle,
                replacing: Boolean,
            ) = reloadAppList(AppListReloadTrigger.PACKAGES_AVAILABILITY_CHANGED)

            override fun onShortcutsChanged(
                packageName: String,
                shortcuts: MutableList<ShortcutInfo>,
                user: android.os.UserHandle,
            ) {
                reloadAppList(AppListReloadTrigger.SHORTCUTS_CHANGED)
            }
        }
        launcherApps.registerCallback(launcherAppsCallback!!)
    }

    override fun onStop() {
        isResumed = false
        if (profileRecheckId != null) profileRecheckLeft = true
        backToHomeScreen()
        super.onStop()
    }

    override fun onUserLeaveHint() {
        backToHomeScreen()
        super.onUserLeaveHint()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Keep the result owner alive until widget setup has completed or been cancelled.
        if (!hasPendingTodayWidget) {
            viewModel.isAwaitingActivityResult = false
            backToHomeScreen()
        }
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val destination = intent?.getStringExtra(EXTRA_NAVIGATE_TO) ?: return
        if (destination == DESTINATION_TODAY) {
            intent.removeExtra(EXTRA_NAVIGATE_TO)
            binding.root.post {
                navigateToToday()
            }
        }
    }

    private fun navigateToToday() {
        try {
            if (navController.currentDestination?.id == R.id.todayFragment) return
            if (navController.currentDestination?.id == R.id.mainFragment) {
                navController.navigate(R.id.action_mainFragment_to_todayFragment)
            } else {
                navController.popBackStack(R.id.mainFragment, false)
                navController.navigate(R.id.action_mainFragment_to_todayFragment)
            }
        } catch (_: Exception) {
            try {
                navController.navigate(R.id.todayFragment)
            } catch (_: Exception) {
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppCompatDelegate.setDefaultNightMode(prefs.appTheme)
        if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
            if (prefs.photoWallpaperCount > 0) {
                PhotoWallpaper.applyCurrent(this, prefs)
            } else {
                setPlainWallpaper()
            }
            recreate()
        }
    }

    private fun initObservers(viewModel: MainViewModel) {
        viewModel.launcherResetFailed.observe(this) {
            openLauncherChooser(it)
        }
        viewModel.resetLauncherLiveData.observe(this) {
            if (isDefaultLauncher())
                resetLauncherViaFakeActivity()
            else
                showLauncherSelector(Constants.REQUEST_CODE_LAUNCHER_SELECTOR)
        }
        viewModel.checkForMessages.observe(this) {
            checkForMessages()
        }
        viewModel.showDialog.observe(this) {
            when (it) {
                Constants.Dialog.HIDDEN -> {
                    showMessage(R.string.hidden_apps, R.string.hidden_apps_message, R.string.okay) {
                    }
                }

                Constants.Dialog.KEYBOARD -> {
                    showMessage(R.string.app_name, R.string.keyboard_message, R.string.okay) {
                    }
                }

                Constants.Dialog.DIGITAL_WELLBEING -> {
                    showMessage(R.string.screen_time, R.string.app_usage_message, R.string.permission) {
                        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                }
            }
        }
        viewModel.pauseLaunch.observe(this) { appModel ->
            if (navController.currentDestination?.id == R.id.pauseFragment) return@observe
            val bundle = bundleOf(
                "package" to appModel.appPackage,
                "class" to (appModel as? AppModel.App)?.activityClassName,
                "user" to appModel.user.toString(),
                "label" to appModel.appLabel,
                "shortcutId" to (appModel as? AppModel.PinnedShortcut)?.shortcutId,
                "isArchived" to appModel.isArchived
            )
            try {
                when (navController.currentDestination?.id) {
                    R.id.appListFragment -> navController.navigate(R.id.action_appListFragment_to_pauseFragment, bundle)
                    R.id.mainFragment -> navController.navigate(R.id.action_mainFragment_to_pauseFragment, bundle)
                    else -> navController.navigate(R.id.pauseFragment, bundle)
                }
            } catch (_: Exception) {
                navController.navigate(R.id.pauseFragment, bundle)
            }
        }
    }

    /** First-run welcome, four steps; Skip finishes onboarding from any of them. */
    private fun showOnboardingStep(state: OnboardingState) {
        if (state.isComplete) return
        val step = state.currentStep
        val isPickStep = step == OnboardingStep.PICK_HOME_APPS
        val isFeaturesStep = step == OnboardingStep.OPTIONAL_FEATURES
        messageDialog?.dismiss()
        // Persist progress now so a restart resumes here instead of looking like an old install.
        prefs.onboardingState = OnboardingStore.encode(state)
        val dialog = createDialog(
            title = R.string.onboarding_title,
            action = R.string.onboarding_next,
            message = when (step) {
                OnboardingStep.SET_DEFAULT_LAUNCHER -> R.string.onboarding_default_launcher_message
                OnboardingStep.PICK_HOME_APPS -> R.string.onboarding_pick_apps_message
                OnboardingStep.PICK_PROFILE -> R.string.onboarding_profile_message
                OnboardingStep.OPTIONAL_FEATURES -> R.string.onboarding_features_message
            },
            neutral = R.string.skip,
            onNeutral = { prefs.onboardingState = OnboardingStore.encode(Onboarding.skipped(state)) },
            onAction = {
                if (step == OnboardingStep.PICK_PROFILE) {
                    applyOnboardingProfile()
                }
                val next = OnboardingStore.nextPressed(state)
                prefs.onboardingState = OnboardingStore.encode(next)
                if (!next.isComplete) showOnboardingStep(next)
                if (step == OnboardingStep.PICK_PROFILE) askProfileWeatherIfNeeded()
            },
            content = { parent ->
                if (isFeaturesStep) {
                    buildOnboardingFeatureRows()
                } else if (step == OnboardingStep.PICK_PROFILE) {
                    buildOnboardingProfileRows()
                } else if (isPickStep) {
                    val binding = DialogOnboardingDefaultBinding.inflate(layoutInflater, parent, false)
                    binding.tvOpenHomeSettings.setText(R.string.onboarding_pick_apps_action)
                    binding.tvOpenHomeSettings.setOnClickListener { openHomeAppPicker() }
                    binding.root
                } else {
                    val binding = DialogOnboardingDefaultBinding.inflate(layoutInflater, parent, false)
                    binding.tvOpenHomeSettings.setOnClickListener { openHomeSettings() }
                    binding.root
                }
            },
        )
        dialog.showRespectingStatusBar()
        messageDialog = dialog
    }

    /**
     * Applies the chosen profile. Weather needs the location permission, so when the profile wants
     * weather and the permission is missing, weather stays as it was until the user allows it.
     */
    private fun applyOnboardingProfile() {
        val id = ProfilePicker.profileIdToApply(onboardingProfileChoice) ?: return
        prefs.switchProfile(id, weatherGranted = WeatherLocation.hasPermission(this))
        val profiles = prefs.profiles()
        onboardingProfileWantsWeather = ProfilePicker.wantsWeather(onboardingProfileChoice, profiles)
        onboardingProfileWeatherEffects = ProfilePicker.weatherEffectsOf(onboardingProfileChoice, profiles)
        onboardingProfileChoice = ProfilePicker.initial
    }

    private fun askProfileWeatherIfNeeded() {
        val wanted = onboardingProfileWantsWeather
        onboardingProfileWantsWeather = false
        if (!wanted || WeatherLocation.hasPermission(this)) return
        showMessageDialog(R.string.weather, R.string.weather_location_permission_message, R.string.allow) {
            onboardingProfileWeatherPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    /** One tappable row per profile, plus "Keep defaults"; the chosen row is marked and applied on Next. */
    private fun buildOnboardingProfileRows(): android.view.View {
        onboardingProfileChoice = ProfilePicker.initial
        val column = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL }
        val rows = ProfilePickerOption.values().map { option ->
            val label = when (option) {
                ProfilePickerOption.BARE -> R.string.onboarding_profile_bare
                ProfilePickerOption.FOCUS -> R.string.onboarding_profile_focus
                ProfilePickerOption.RICH -> R.string.onboarding_profile_rich
                ProfilePickerOption.KEEP_DEFAULTS -> R.string.onboarding_profile_keep
            }
            Triple(option, label, DialogOnboardingDefaultBinding.inflate(layoutInflater, column, false))
        }
        fun refresh() = rows.forEach { (option, label, row) ->
            val text = getString(label)
            row.tvOpenHomeSettings.text =
                if (option == onboardingProfileChoice) getString(R.string.onboarding_profile_selected, text) else text
        }
        rows.forEach { (option, _, row) ->
            row.tvOpenHomeSettings.setOnClickListener { onboardingProfileChoice = option; refresh() }
            column.addView(row.root)
        }
        refresh()
        return column
    }

    /** One tappable row per optional feature; a row shows "is on" once its setting is on. */
    private fun buildOnboardingFeatureRows(): android.view.View {
        val column = android.widget.LinearLayout(this).apply { orientation = android.widget.LinearLayout.VERTICAL }
        fun addRow(@androidx.annotation.StringRes label: Int, isOn: () -> Boolean, turnOn: () -> Unit): () -> Unit {
            val row = DialogOnboardingDefaultBinding.inflate(layoutInflater, column, false)
            val refresh = {
                row.tvOpenHomeSettings.text = getString(
                    if (isOn()) R.string.onboarding_feature_is_on else R.string.onboarding_feature_turn_on,
                    getString(label),
                )
            }
            row.tvOpenHomeSettings.setOnClickListener { if (!isOn()) turnOn() }
            refresh()
            column.addView(row.root)
            return refresh
        }
        val refreshers = listOf(
            addRow(R.string.weather, { prefs.weatherEnabled }, ::askOnboardingWeather),
            addRow(R.string.home_next_event, { prefs.homeNextEvent }, ::askOnboardingCalendar),
            addRow(R.string.onboarding_wellbeing_label, { prefs.nudgeWaterIntervalMin > 0 }, ::askOnboardingWellbeing),
        )
        onboardingRowsRefresh = { refreshers.forEach { it() } }
        return column
    }

    // The actions below mirror the Settings toggles (weather location, next event on home, water nudge).
    private fun enableOnboardingWeather() {
        prefs.weatherUseLocation = true
        prefs.weatherEnabled = true
        WeatherWorker.schedulePeriodic(this)
        WeatherLocation.refreshIfStale(this, force = true)
    }

    private fun enableOnboardingWaterNudge() {
        prefs.nudgeWaterIntervalMin = ONBOARDING_WATER_INTERVAL_MIN
        NudgeScheduler.schedule(this, Nudges.WATER)
    }

    private fun askOnboardingWeather() {
        if (WeatherLocation.hasPermission(this)) {
            enableOnboardingWeather()
            onboardingRowsRefresh?.invoke()
            return
        }
        showMessageDialog(R.string.weather, R.string.weather_location_permission_message, R.string.allow) {
            onboardingWeatherPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    private fun askOnboardingCalendar() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            prefs.homeNextEvent = true
            onboardingRowsRefresh?.invoke()
            return
        }
        showMessageDialog(R.string.home_next_event, R.string.calendar_permission_message, R.string.allow) {
            onboardingCalendarPermission.launch(Manifest.permission.READ_CALENDAR)
        }
    }

    private fun askOnboardingWellbeing() {
        if (NotificationPermission.isGranted(this)) {
            enableOnboardingWaterNudge()
            onboardingRowsRefresh?.invoke()
            return
        }
        showMessageDialog(R.string.nudges, R.string.notification_permission_message, R.string.allow) {
            onboardingNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /** Opens the same app list a long-press on a home slot opens, on the first free slot. */
    private fun openHomeAppPicker() {
        messageDialog?.dismiss()
        val slot = HomeAppHelper.findTargetSlot(prefs) ?: 1
        viewModel.getAppList(false)
        try {
            navController.navigate(
                R.id.action_mainFragment_to_appListFragment,
                bundleOf(Constants.Key.FLAG to slot, Constants.Key.RENAME to false),
            )
        } catch (_: Exception) {
            // Not on home; the welcome returns when home is shown again.
        }
    }

    private fun openHomeSettings() {
        try {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        } catch (_: Exception) {
            // No home settings screen on this device; the app keeps working.
        }
    }

    private fun showMessage(title: Int, message: Int, action: Int, clickListener: () -> Unit) {
        messageDialog?.dismiss()
        messageDialog = showMessageDialog(title, message, action, clickListener)
    }

    private fun checkForMessages() {
        if (prefs.firstOpenTime == 0L)
            prefs.firstOpenTime = System.currentTimeMillis()

        val calendar = Calendar.getInstance()
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        if (dayOfYear == 1 && dayOfYear != prefs.shownOnDayOfYear) {
            prefs.shownOnDayOfYear = dayOfYear
            showMessage(R.string.hey, R.string.new_year_wish, R.string.cheers) {}
            return
        } else if (dayOfYear == 32 && dayOfYear != prefs.shownOnDayOfYear) {
            prefs.shownOnDayOfYear = dayOfYear
            showMessage(R.string.hey, R.string.new_year_wish_1, R.string.cheers) {}
            return
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun setupOrientation() {
        if (isTablet(this))
            return
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    private val hasPendingTodayWidget: Boolean
        get() = navController.currentDestination?.id == R.id.todayFragment && prefs.todayPendingWidgetId > 0

    private fun backToHomeScreen() {
        if (viewModel.isPrivateSpaceToggling || viewModel.isAwaitingActivityResult) return
        messageDialog?.dismiss()
        if (HomeReturnPolicy.shouldPopToHome(
                isOnHome = navController.currentDestination?.id == R.id.mainFragment,
                privateSpaceToggling = viewModel.isPrivateSpaceToggling,
                awaitingActivityResult = viewModel.isAwaitingActivityResult,
                pendingWidgetId = if (hasPendingTodayWidget) prefs.todayPendingWidgetId else -1,
            )
        )
            navController.popBackStack(R.id.mainFragment, false)
    }

    private fun setPlainWallpaper() {
        if (this.isDarkThemeOn())
            setPlainWallpaper(this, android.R.color.black)
        else setPlainWallpaper(this, android.R.color.white)
    }

    private fun openLauncherChooser(resetFailed: Boolean) {
        if (resetFailed) {
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            startActivity(intent)
        }
    }

    private fun restartLauncherOrCheckTheme(forceRestart: Boolean = false) {
        if (forceRestart || prefs.launcherRestartTimestamp.hasBeenHours(4)) {
            prefs.launcherRestartTimestamp = System.currentTimeMillis()
            cacheDir.deleteRecursively()
            recreate()
        } else
            checkTheme()
    }

    private fun checkTheme() {
        timerJob?.cancel()
        timerJob = lifecycleScope.launch {
            delay(200)
            if ((prefs.appTheme == AppCompatDelegate.MODE_NIGHT_YES && getColorFromAttr(R.attr.primaryColor) != getColor(R.color.white))
                || (prefs.appTheme == AppCompatDelegate.MODE_NIGHT_NO && getColorFromAttr(R.attr.primaryColor) != getColor(R.color.black))
            )
                restartLauncherOrCheckTheme(true)
        }
    }

    override fun onDestroy() {
        messageDialog?.dismiss()
        messageDialog = null
        launcherAppsCallback?.let {
            getSystemService(LauncherApps::class.java).unregisterCallback(it)
        }
        profileReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            TodayWidgets.REQUEST_CONFIGURE -> {
                supportFragmentManager.setFragmentResult(
                    TodayWidgets.CONFIGURE_RESULT,
                    bundleOf(TodayWidgets.RESULT_CODE to resultCode),
                )
            }

            Constants.REQUEST_CODE_ENABLE_ADMIN -> {
                if (resultCode == Activity.RESULT_OK)
                    prefs.lockModeOn = true
            }

            Constants.REQUEST_CODE_LAUNCHER_SELECTOR -> {
                if (resultCode == Activity.RESULT_OK)
                    resetLauncherViaFakeActivity()
            }
        }
    }
}
