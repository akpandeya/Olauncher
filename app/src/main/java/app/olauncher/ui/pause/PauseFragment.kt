package app.olauncher.ui.pause

import androidx.core.view.ViewCompat
import app.olauncher.helper.HomeAccessibilityActions
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import androidx.activity.OnBackPressedCallback
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Prefs
import app.olauncher.data.store.Leitner
import app.olauncher.data.store.PauseWordPicker
import app.olauncher.data.store.WordsStore
import app.olauncher.databinding.FragmentPauseBinding
import app.olauncher.helper.Haptics
import app.olauncher.helper.hapticLongClick
import app.olauncher.helper.OlDialog
import app.olauncher.helper.appUsagePermissionGranted
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.getUserHandleFromString
import app.olauncher.helper.showPopupMenu
import app.olauncher.helper.showWaitDialog
import app.olauncher.quiet.QuietRelease
import app.olauncher.ui.settings.SettingsDeepLinks
import app.olauncher.ui.today.WordReveal
import app.olauncher.wellbeing.CommitLock
import app.olauncher.wellbeing.DistractingApps
import app.olauncher.wellbeing.FrictionChange
import app.olauncher.wellbeing.PauseLog
import app.olauncher.wellbeing.PausePolicy
import app.olauncher.wellbeing.PauseSnooze
import app.olauncher.wellbeing.PauseWhy
import app.olauncher.wellbeing.UsageRepository
import app.olauncher.wellbeing.UsageSnapshotWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

class PauseFragment : Fragment() {

    companion object {
        var storeProvider: ((Context) -> WordsStore)? = null
        var usageRepositoryProvider: ((Context) -> UsageRepository)? = null
        var pauseLogProvider: ((Context) -> PauseLog)? = null
        var epochDaySupplier: () -> Long = { LocalDate.now().toEpochDay() }
        var isQuietOnSupplier: ((Context, Prefs) -> Boolean)? = null
        var nowSupplier: () -> Long = { System.currentTimeMillis() }
        var zoneSupplier: () -> ZoneId = { ZoneId.systemDefault() }
        var isUsageAccessGrantedSupplier: ((Context) -> Boolean)? = null
    }

    private fun isUsageAccessGranted(context: Context): Boolean {
        return isUsageAccessGrantedSupplier?.invoke(context)
            ?: context.appUsagePermissionGranted()
    }

    private var _binding: FragmentPauseBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var prefs: Prefs
    private var countdownJob: Job? = null
    private var wordReveal: WordReveal? = null
    private var countdownRemaining: Int = -1
    private var selectedWhy: PauseWhy? = null
    private var waitDialog: OlDialog? = null

    private val appPackage: String by lazy { arguments?.getString("package").orEmpty() }
    private val activityClassName: String? by lazy { arguments?.getString("class") }
    private val userString: String by lazy { arguments?.getString("user").orEmpty() }
    private val appLabel: String by lazy { arguments?.getString("label").orEmpty() }
    private val shortcutId: String? by lazy { arguments?.getString("shortcutId") }
    private val isArchived: Boolean by lazy { arguments?.getBoolean("isArchived", false) ?: false }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentPauseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())

        binding.appName.text = appLabel

        // Edge-to-edge: keep the buttons at least 96dp above the nav bar so Not now is at thumb height.
        val minBottomPadding = (96 * resources.displayMetrics.density).toInt()
        binding.pauseContent.setOnApplyWindowInsetsListener { content, insets ->
            val navBarBottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            val bottom = navBarBottom + minBottomPadding
            if (content.paddingBottom != bottom) {
                content.setPadding(content.paddingLeft, content.paddingTop, content.paddingRight, bottom)
            }
            insets
        }
        binding.pauseContent.requestApplyInsets()

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    onNotNow()
                }
            }
        )

        updateNotNowLabel()
        binding.btnNotNow.setOnClickListener {
            onNotNow()
        }

        binding.btnOpen.setOnClickListener {
            openApp()
        }

        binding.btnOpen.hapticLongClick {
            offerStopPausing(binding.btnOpen)
        }

        binding.btnOpenContainer.hapticLongClick {
            offerStopPausing(binding.btnOpen)
        }

        binding.openSettings.setOnClickListener {
            Haptics.tick(it)
            openSettingsOnPause()
        }

        setupWhyChoices()
        setupPauseWord()
        updateNotice()
        startDecisionAndCountdown()
    }

    override fun onResume() {
        super.onResume()
        val context = context ?: return
        if (_binding != null && binding.openSettings.isVisible && isUsageAccessGranted(context)) {
            startDecisionAndCountdown()
        }
    }

    private fun openSettingsOnPause() {
        if (!isAdded || _binding == null) return
        val navController = findNavController()
        // Ignore a second tap once we already left the Pause screen.
        if (navController.currentDestination?.id != R.id.pauseFragment) return
        navController.navigate(
            R.id.settingsFragment,
            SettingsDeepLinks.pauseBundle(),
        )
    }

    private fun startDecisionAndCountdown() {
        countdownJob?.cancel()
        countdownJob = viewLifecycleOwner.lifecycleScope.launch {
            updateOpenButton(prefs.pauseSeconds)

            val context = context ?: return@launch
            val baseSeconds = prefs.pauseSeconds
            val usageGranted = isUsageAccessGranted(context)
            val allowanceMin = PausePolicy.effectiveAllowanceMin(
                savedMin = prefs.pauseAllowanceMin,
                usageAccessGranted = usageGranted,
            )
            val escalate = prefs.pauseEscalate
            val distractingPkgs = if (prefs.hasSavedDistractingApps()) {
                prefs.distractingApps
            } else {
                DistractingApps.DEFAULT_PACKAGES
            }

            val (usageLine, decision) = withContext(Dispatchers.IO) {
                if (usageGranted) {
                    val repository = usageRepositoryProvider?.invoke(context) ?: UsageRepository(context)
                    val opens = repository.openCountToday(appPackage) ?: 0
                    val appMillis = repository.foregroundMillisToday(setOf(appPackage)) ?: 0L
                    val sharedMillis = repository.foregroundMillisToday(distractingPkgs)
                    val minutesToday = sharedMillis?.let { (it / 60_000L).toInt() }
                    val decision = PausePolicy.delaySeconds(
                        base = baseSeconds,
                        opensToday = PausePolicy.opensIncludingThisOne(opens),
                        minutesToday = minutesToday,
                        allowanceMin = allowanceMin,
                        escalate = escalate,
                    )
                    val line = PauseFormatting.formatUsageStatsLine(opens, appMillis)
                    line to decision
                } else {
                    val opens = prefs.ownOpenCount(appPackage)
                    val decision = PausePolicy.delaySeconds(
                        base = baseSeconds,
                        opensToday = PausePolicy.opensIncludingThisOne(opens),
                        minutesToday = null,
                        allowanceMin = allowanceMin,
                        escalate = escalate,
                    )
                    val line = PauseFormatting.formatThumblineOpensLine(opens)
                    line to decision
                }
            }

            if (_binding == null) return@launch
            binding.usageStats.text = usageLine
            binding.openSettings.isVisible = !usageGranted

            if (decision.overAllowance && allowanceMin > 0) {
                binding.overAllowance.text = getString(R.string.pause_over_allowance, PauseFormatting.overAllowanceMinutes(allowanceMin))
                binding.overAllowance.isVisible = true
            } else {
                binding.overAllowance.isVisible = false
            }

            val totalSeconds = decision.seconds
            if (totalSeconds <= 0) {
                updateOpenButton(0, announce = true)
                return@launch
            }

            for (remaining in totalSeconds downTo 1) {
                updateOpenButton(
                    remaining,
                    announce = HomeAccessibilityActions.shouldAnnounceCountdown(remaining, totalSeconds),
                )
                delay(1000L)
            }
            if (_binding != null) {
                updateOpenButton(0, announce = true)
                Haptics.confirm(binding.btnOpen)
            }
        }
    }

    private fun setupWhyChoices() {
        if (!prefs.pauseAskWhy) {
            binding.whyChoicesContainer.isVisible = false
            return
        }

        binding.whyChoicesContainer.isVisible = true
        updateWhyChoicesVisuals()

        binding.choiceReply.setOnClickListener {
            selectWhy(PauseWhy.REPLY)
        }
        binding.choiceLookup.setOnClickListener {
            selectWhy(PauseWhy.LOOKUP)
        }
        binding.choiceBored.setOnClickListener {
            selectWhy(PauseWhy.BORED)
        }
    }

    private fun selectWhy(why: PauseWhy) {
        selectedWhy = why
        updateWhyChoicesVisuals()
        updateNotNowLabel()
        updateOpenButtonState()
    }

    private fun updateWhyChoicesVisuals() {
        if (_binding == null) return
        val selected = selectedWhy
        val reply = binding.choiceReply
        val lookup = binding.choiceLookup
        val bored = binding.choiceBored

        val accentColor = context?.getColorFromAttr(R.attr.accentColor) ?: reply.currentTextColor
        val primaryColor = context?.getColorFromAttr(R.attr.primaryColor) ?: reply.currentTextColor

        if (selected == null) {
            reply.alpha = 0.7f
            reply.isSelected = false
            reply.setTextColor(primaryColor)
            lookup.alpha = 0.7f
            lookup.isSelected = false
            lookup.setTextColor(primaryColor)
            bored.alpha = 0.7f
            bored.isSelected = false
            bored.setTextColor(primaryColor)
        } else {
            reply.alpha = if (selected == PauseWhy.REPLY) 1.0f else 0.4f
            reply.isSelected = (selected == PauseWhy.REPLY)
            reply.setTextColor(if (selected == PauseWhy.REPLY) accentColor else primaryColor)
            lookup.alpha = if (selected == PauseWhy.LOOKUP) 1.0f else 0.4f
            lookup.isSelected = (selected == PauseWhy.LOOKUP)
            lookup.setTextColor(if (selected == PauseWhy.LOOKUP) accentColor else primaryColor)
            bored.alpha = if (selected == PauseWhy.BORED) 1.0f else 0.4f
            bored.isSelected = (selected == PauseWhy.BORED)
            bored.setTextColor(if (selected == PauseWhy.BORED) accentColor else primaryColor)
        }
    }

    private fun updateNotNowLabel() {
        if (_binding == null) return
        if (selectedWhy == PauseWhy.BORED) {
            binding.btnNotNow.text = getString(R.string.pause_not_now_bored)
        } else {
            binding.btnNotNow.text = getString(
                if (prefs.isGermanUi) R.string.not_now_de else R.string.not_now
            )
        }
    }

    private fun updateOpenButton(remaining: Int, announce: Boolean = false) {
        countdownRemaining = remaining
        if (_binding == null) return
        // Polite live region only for the announced updates, so TalkBack stays quiet every other second.
        ViewCompat.setAccessibilityLiveRegion(
            binding.btnOpen,
            if (announce) ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE
            else ViewCompat.ACCESSIBILITY_LIVE_REGION_NONE,
        )
        updateOpenButtonState()
    }

    private fun updateOpenButtonState() {
        if (_binding == null) return
        val useGerman = prefs.isGermanUi
        val countdownActive = countdownRemaining > 0
        val whyRequired = prefs.pauseAskWhy
        val whySelected = selectedWhy != null

        val isReady = !countdownActive && (!whyRequired || whySelected)

        binding.btnOpen.isEnabled = isReady
        // Open stays quieter than Not now (70%) even when ready.
        binding.btnOpen.alpha = if (isReady) 0.7f else 0.4f

        val accentColor = context?.getColorFromAttr(R.attr.accentColor) ?: binding.btnOpen.currentTextColor
        val primaryColor = context?.getColorFromAttr(R.attr.primaryColor) ?: binding.btnOpen.currentTextColor
        binding.btnOpen.setTextColor(if (countdownActive) primaryColor else accentColor)

        binding.btnOpen.text = if (countdownActive) {
            getString(
                if (useGerman) R.string.pause_open_countdown_de else R.string.pause_open_countdown,
                countdownRemaining
            )
        } else {
            getString(
                if (useGerman) R.string.pause_open_de else R.string.pause_open
            )
        }
    }

    private fun getPauseLog(): PauseLog {
        // Shared log: day rollover writes the previous day to usage_days.json.
        val appContext = context?.applicationContext
        return (appContext?.let { pauseLogProvider?.invoke(it) }) ?: prefs.pauseLog
    }

    private fun onNotNow() {
        if (_binding != null) {
            Haptics.confirm(binding.btnNotNow)
        }
        countdownJob?.cancel()
        getPauseLog().recordNotNow()
        popToHome()
    }

    private fun openApp(bypassGating: Boolean = false) {
        if (!bypassGating) {
            if (prefs.pauseAskWhy && selectedWhy == null) return
            if (countdownRemaining > 0) return
        }
        val appContext = context?.applicationContext ?: return
        countdownJob?.cancel()
        getPauseLog().recordOpen(selectedWhy)
        val appModel = buildAppModel(appContext)
        viewModel.launchAfterPause(appModel)
        popToHome()
    }

    private fun offerStopPausing(anchor: View) {
        anchor.showPopupMenu(
            configure = { menu ->
                // Header so the choices read as "Stop pausing <app>: For 1 hour / ...".
                menu.add(Menu.NONE, 0, 0, getString(R.string.stop_pausing_app, appLabel)).isEnabled = false
                menu.add(Menu.NONE, 1, 1, getString(R.string.snooze_hour))
                menu.add(Menu.NONE, 2, 2, getString(R.string.snooze_tomorrow))
                menu.add(Menu.NONE, 3, 3, getString(R.string.snooze_for_good))
            }
        ) { item ->
            val choice = when (item.itemId) {
                1 -> PauseSnooze.Choice.HOUR
                2 -> PauseSnooze.Choice.TOMORROW
                3 -> null
                else -> return@showPopupMenu
            }
            val context = context ?: return@showPopupMenu
            val appContext = context.applicationContext
            val apply: () -> Unit = {
                if (choice == null) {
                    removeThisAppFromDistracting(appContext)
                } else {
                    val now = nowSupplier()
                    val until = PauseSnooze.until(choice, now, zoneSupplier())
                    prefs.pauseAppSnooze = PauseSnooze.withAppSnooze(prefs.pauseAppSnooze, appPackage, until, now)
                }
            }
            val change = if (choice == null) {
                FrictionChange.RemoveDistractingApp(appPackage)
            } else {
                FrictionChange.Snooze(PauseSnooze.until(choice, nowSupplier(), zoneSupplier()))
            }
            if (CommitLock.needsWait(prefs.commitLock, change)) {
                waitDialog?.dismiss()
                waitDialog = context.showWaitDialog(
                    message = getString(R.string.commit_lock_desc)
                ) {
                    // The change is the confirmed one: never lose it.
                    apply()
                    if (!isAdded || _binding == null) return@showWaitDialog
                    openApp(bypassGating = true)
                }
            } else {
                apply()
                openApp(bypassGating = true)
            }
        }
    }

    private fun removeThisAppFromDistracting(appContext: Context) {
        val remaining = prefs.distractingApps - appPackage
        prefs.distractingApps = remaining
        UsageSnapshotWorker.syncWithDistractingApps(appContext, remaining)
    }

    private fun buildAppModel(appContext: Context): AppModel {
        val userHandle = getUserHandleFromString(appContext, userString)
        return if (!shortcutId.isNullOrEmpty()) {
            AppModel.PinnedShortcut(
                shortcutId = shortcutId!!,
                appLabel = appLabel,
                user = userHandle,
                key = null,
                appPackage = appPackage,
                isNew = false,
                isArchived = isArchived
            )
        } else {
            AppModel.App(
                appLabel = appLabel,
                key = null,
                appPackage = appPackage,
                activityClassName = activityClassName,
                isNew = false,
                user = userHandle,
                isArchived = isArchived
            )
        }
    }

    private fun getStore(context: Context): WordsStore {
        return storeProvider?.invoke(context) ?: WordsStore(context)
    }

    private fun setupPauseWord() {
        if (!prefs.germanEnabled || !prefs.germanPauseWord) {
            binding.wordReveal.root.isVisible = false
            return
        }

        val context = context ?: return
        val store = getStore(context)
        val today = epochDaySupplier()
        val dueWords = store.due(today)
        val pickedWord = PauseWordPicker.pick(dueWords, prefs.germanPauseLastWordId)

        if (pickedWord == null) {
            binding.wordReveal.root.isVisible = false
            return
        }

        prefs.germanPauseLastWordId = pickedWord.id
        binding.wordReveal.root.isVisible = true

        val helper = wordReveal ?: WordReveal(
            binding = binding.wordReveal,
            onAnswer = { word, knewIt ->
                val ctx = this@PauseFragment.context
                if (ctx != null) {
                    val s = getStore(ctx)
                    val currentToday = epochDaySupplier()
                    val updated = if (knewIt) {
                        Leitner.knowIt(word, currentToday)
                    } else {
                        Leitner.again(word, currentToday)
                    }
                    s.update(updated)
                }
                if (_binding != null) {
                    binding.wordReveal.root.isVisible = false
                }
            },
            onWordUpdated = { updated ->
                val ctx = this@PauseFragment.context
                if (ctx != null) {
                    val s = getStore(ctx)
                    s.update(updated)
                    wordReveal?.bind(updated)
                }
            },
            onWordDeleted = { word ->
                val ctx = this@PauseFragment.context
                if (ctx != null) {
                    val s = getStore(ctx)
                    s.delete(word.id)
                }
                if (_binding != null) {
                    binding.wordReveal.root.isVisible = false
                }
            }
        ).also { wordReveal = it }

        helper.bind(pickedWord)
    }

    private fun updateNotice() {
        val context = context ?: return
        val quietOn = isQuietOnSupplier?.invoke(context, prefs)
            ?: (prefs.quietEnabled && isNotificationAccessGranted(context))
        if (quietOn) {
            val now = nowSupplier()
            val zone = zoneSupplier()
            val times = QuietRelease.parseTimes(prefs.quietReleaseTimes)
            val formattedTime = QuietRelease.nextReleaseFormatted(now, zone, times)
            binding.notice.text = getString(R.string.pause_notifications_held_until, formattedTime)
        } else {
            binding.notice.text = getString(R.string.pause_notification_notice)
        }
    }

    private fun isNotificationAccessGranted(context: Context): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
    }

    private fun popToHome() {
        findNavController().popBackStack(R.id.mainFragment, false)
    }

    override fun onDestroyView() {
        waitDialog?.dismiss()
        waitDialog = null
        countdownJob?.cancel()
        wordReveal = null
        selectedWhy = null
        countdownRemaining = -1
        _binding = null
        super.onDestroyView()
    }
}
