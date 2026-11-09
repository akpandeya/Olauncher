package app.olauncher.ui.settings

import app.olauncher.ui.*
import app.olauncher.ui.SettingsFragment
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.graphics.Typeface
import android.provider.MediaStore
import android.provider.Settings
import android.text.InputFilter
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
import android.accessibilityservice.AccessibilityServiceInfo
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
import android.view.accessibility.AccessibilityManager
import android.widget.TextView
import app.olauncher.helper.DrawerScrollHelper
import app.olauncher.helper.DictionaryApps
import app.olauncher.helper.GermanDueRefreshWorker
import app.olauncher.helper.GermanSyncWorker
import app.olauncher.helper.Haptics
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
import androidx.work.Operation
import androidx.work.WorkManager
import app.olauncher.quiet.QuietRelease
import app.olauncher.quiet.ReleaseWorker
import app.olauncher.data.store.AutoBackupStatus
import app.olauncher.data.store.AutoBackupWorker
import app.olauncher.data.store.Backup
import app.olauncher.data.store.BackupData
import app.olauncher.data.store.AiChatStore
import app.olauncher.data.store.WordLookup
import app.olauncher.ui.words.AskAiEntryPoints
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
import app.olauncher.data.store.AiPracticeMessages
import app.olauncher.data.store.AiPracticeGate
import app.olauncher.data.store.AiResult
import app.olauncher.data.store.AiTestCall
import app.olauncher.data.store.GermanDeviceCredential
import app.olauncher.data.store.GermanWebApi
import app.olauncher.data.store.GermanWebBaseUrl
import app.olauncher.data.store.GermanWebOutbox
import app.olauncher.data.store.GermanWebSession
import app.olauncher.data.store.GermanWebSessionState
import app.olauncher.data.store.KeyVault
import app.olauncher.data.store.PracticeItemsStore
import app.olauncher.data.store.PracticeQueue
import app.olauncher.data.store.PairingResult
import app.olauncher.data.store.WordsStore
import app.olauncher.helper.AiPracticeToggle
import app.olauncher.helper.NewsFetchNow
import app.olauncher.helper.NewsHarvestPolicy
import app.olauncher.helper.NewsHarvestToggle
import app.olauncher.helper.NewsHarvestWorker
import app.olauncher.helper.NewsHarvestWorkerScheduler
import app.olauncher.helper.PracticeWorker
import app.olauncher.helper.PracticeWorkerScheduler
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
import java.time.LocalDate

internal fun SettingsFragment.toggleGerman() {
    if (!prefs.germanEnabled) {
        // Turning German on starts the three-step setup; it enables German only when finished.
        findNavController().navigate(R.id.action_settingsFragment_to_germanSetupFragment)
        return
    }
    prefs.germanEnabled = false
    syncWordShareEntries()
    WordScreenService.setEnabled(requireContext(), prefs.germanEnabled && prefs.germanScreenPick)
    NewsHarvestToggle.onGermanToggled(
        germanEnabled = false,
        newsSentencesEnabled = prefs.newsSentencesEnabled,
        scheduler = NewsHarvestWorkerScheduler(requireContext())
    )
    populateGerman()
}

internal fun SettingsFragment.toggleGermanScreenPick() {
    prefs.germanScreenPick = !prefs.germanScreenPick
    val on = prefs.germanEnabled && prefs.germanScreenPick
    WordScreenService.setEnabled(requireContext(), on)
    populateGerman()
    if (on) {
        dialog?.dismiss()
        dialog = requireContext().showMessageDialog(
            title = R.string.german_screen_pick,
            message = R.string.german_screen_pick_message,
            action = R.string.open_settings,
        ) {
            openAccessibilitySettings()
        }
    }
}

/** Opens the system accessibility settings; a failure keeps the app working. */
internal fun SettingsFragment.openAccessibilitySettings() {
    try {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    } catch (_: Exception) {
        requireContext().showToast(getString(R.string.unable_to_open_app))
    }
}

/**
 * The running "Thumbline: pick words" service as the system sees it, or null while it
 * is not enabled. The service's [AccessibilityServiceInfo.capabilities] say whether it
 * may take screenshots — a capability it only gets when the service was (re)started
 * after the config gained android:canTakeScreenshot.
 */
private fun wordScreenServiceInfo(context: Context): AccessibilityServiceInfo? {
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return null
    val serviceClass = WordScreenService::class.java.name
    return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        .firstOrNull { it.resolveInfo?.serviceInfo?.name == serviceClass }
}

/**
 * The re-enable hint (#590): shown next to the screen-pick row while the German feature
 * is on and the running word service either cannot take screenshots yet or has not
 * written its first frame. It goes away once a screenshot succeeds
 * (WordScreenService sets CAPTURE_SCREENSHOT_OK right after the first frame write), so
 * the line is gated on the pref plus the live capability read from the
 * AccessibilityManager — the capability alone cannot say whether a frame ever landed.
 */
internal fun SettingsFragment.showScreenPickScreenshotHint(): Boolean {
    if (!prefs.germanEnabled || !prefs.germanScreenPick) return false
    val info = wordScreenServiceInfo(requireContext()) ?: return false
    val canScreenshot = info.capabilities and AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT != 0
    return !canScreenshot || !prefs.captureScreenshotOk
}

internal fun SettingsFragment.toggleGermanSpellSuggest() {
    prefs.germanSpellSuggest = !prefs.germanSpellSuggest
    populateGerman()
}

internal fun SettingsFragment.toggleGermanOfflineDictionary() {
    prefs.germanOfflineDictionary = !prefs.germanOfflineDictionary
    populateGerman()
}

internal fun SettingsFragment.toggleGermanLookup() {
    prefs.germanLookup = !prefs.germanLookup
    syncWordShareEntries()
    populateGerman()
}

internal fun SettingsFragment.syncWordShareEntries() {
    WordCaptureActivity.setEnabled(
        requireContext(), prefs.germanEnabled, WordLookup.isLookupAllowed(prefs.germanEnabled, prefs.germanLookup)
    )
}

internal fun SettingsFragment.toggleGermanPauseWord() {
    prefs.germanPauseWord = !prefs.germanPauseWord
    populateGerman()
}

internal fun SettingsFragment.toggleGermanUi() {
    prefs.germanUi = !prefs.germanUi
    populateGerman()
}

internal fun SettingsFragment.toggleAiPractice() {
    prefs.aiPracticeEnabled = !prefs.aiPracticeEnabled
    // On = the periodic top-up plus one immediate run; off = everything idle (#602, 9.9).
    AiPracticeToggle.apply(prefs.aiPracticeEnabled, PracticeWorkerScheduler(requireContext()))
    populateAiPractice()
    // Opt-in without a key: open the key dialog right away; the gate keeps every
    // call off until one is saved (to KeyVault, never to prefs).
    if (prefs.aiPracticeEnabled && KeyVault(requireContext()).read().isNullOrBlank()) {
        showAiKeyDialog()
    }
}

internal fun SettingsFragment.toggleNewsSentences() {
    prefs.newsSentencesEnabled = !prefs.newsSentencesEnabled
    NewsHarvestToggle.apply(prefs.newsSentencesEnabled, NewsHarvestWorkerScheduler(requireContext()))
    populateGerman()
}

/**
 * The Ask-AI conversations row (#835, task 06): the all-conversations list's
 * re-homed entry after the Words screen was removed. The row shows while the
 * chat gate is open ([AskAiEntryPoints.environment], the same condition the
 * Words header button used); a tap navigates to the list.
 */
internal fun SettingsFragment.openAskAiConversations(view: View) {
    // Hidden, never greyed: a stale tap on a row the gate has since closed
    // navigates nowhere instead of opening a screen that pops right back.
    if (!AskAiEntryPoints.environment(requireContext()).visible) return
    Haptics.tick(view)
    val navController = findNavController()
    if (navController.currentDestination?.id == R.id.settingsFragment) {
        navController.navigate(R.id.action_settingsFragment_to_askAiConversationsFragment)
    }
}

internal fun SettingsFragment.populateAiPractice() {
    binding.aiPracticeOnOff.text = getString(if (prefs.aiPracticeEnabled) R.string.on else R.string.off)
    // #794: the trailing chip leads with the key state — "Add API key" until one is
    // saved, "Generate now" after; the ⋮ chip (and the row long-press) holds key
    // management. isSet() is the cheap file check, no decrypt on every populate.
    val hasKey = KeyVault(requireContext()).isSet()
    binding.aiPracticeActionKey.text = getString(
        if (hasKey) R.string.ai_practice_generate_now else R.string.ai_practice_add_key
    )
    binding.aiPracticeActionMore.isVisible = hasKey
    val subtitleRes = AiPracticeMessages.subtitleRes(
        prefs.aiPracticeEnabled, prefs.aiBillingBlocked, prefs.aiKeyRejected, prefs.aiModel,
    )
    binding.aiPracticeSubtitle.text = subtitleRes?.let { getString(it) }
        ?: getString(R.string.ai_practice_subtitle, AiPracticeMessages.providerLabel(prefs.aiProvider))
}

/**
 * The key-management menu (#794): "Update key" / "Remove key" behind the ⋮ chip or
 * the row long-press, once a key is set. With no key it offers "Add API key" only.
 */
internal fun SettingsFragment.showAiPracticeMenu(anchor: View) {
    val hasKey = KeyVault(requireContext()).isSet()
    anchor.showPopupMenu(
        configure = { menu ->
            if (hasKey) {
                menu.add(Menu.NONE, 0, 0, R.string.ai_practice_update_key)
                menu.add(Menu.NONE, 1, 1, R.string.ai_practice_remove_key)
            } else {
                menu.add(Menu.NONE, 0, 0, R.string.ai_practice_add_key)
            }
        }
    ) { item ->
        when (item.itemId) {
            0 -> showAiKeyDialog()
            1 -> {
                KeyVault(requireContext()).clear()
                requireContext().showToast(getString(R.string.ai_practice_key_removed))
                populateAiPractice()
            }
        }
    }
}

/** "Generate now" (#602, 9.9): one immediate PracticeWorker run, with the queued word count. */
internal fun SettingsFragment.generateAiPracticeNow() {
    val context = requireContext()
    PracticeWorker.runOnce(context)
    lifecycleScope.launch {
        val queued = withContext(Dispatchers.IO) {
            PracticeQueue.next(
                words = WordsStore(context).all(),
                items = PracticeItemsStore(context).all(),
                today = LocalDate.now(),
                cap = prefs.aiDailyWordCap,
                random = kotlin.random.Random.Default,
            )
        }
        context.showToast(getString(R.string.ai_practice_generate_queued, queued.size))
    }
}

/**
 * "Fetch now" (#794): one immediate [NewsHarvestWorker] run. The tap is the user's
 * ask, so [NewsHarvestWorker.fetchNow] drops the Wi-Fi gate the periodic run keeps;
 * the run itself still obeys [NewsHarvestPolicy.mayRun], the daily cap and the run
 * guard, all decided Context-free in [NewsFetchNow]. A follow-up toast reports what
 * the harvest fetched.
 */
internal fun SettingsFragment.fetchNewsSentencesNow() {
    val context = requireContext()
    val today = LocalDate.now().toEpochDay()
    val articlesLeft = NewsHarvestPolicy.articlesLeftToday(
        Prefs.NEWS_DAILY_ARTICLE_CAP, prefs.newsArticlesFetchedToday(today),
    )
    when (val plan = NewsFetchNow.plan(prefs.germanEnabled, prefs.newsSentencesEnabled, articlesLeft)) {
        NewsFetchNow.Plan.Off -> context.showToast(getString(R.string.news_fetch_now_off))
        NewsFetchNow.Plan.CapUsed -> context.showToast(
            getString(R.string.news_fetch_now_cap_used, Prefs.NEWS_DAILY_ARTICLE_CAP)
        )
        is NewsFetchNow.Plan.Run -> {
            val fetchedBefore = prefs.newsArticlesFetchedToday(today)
            val enqueue = NewsHarvestWorker.fetchNow(context)
            context.showToast(getString(R.string.news_fetch_now_queued, plan.articles))
            // Wait for the enqueue to land: before it does, the unique name can still
            // show the previous, finished fetch and the toast would report that run.
            val enqueueState = enqueue.state
            enqueueState.observe(viewLifecycleOwner) { state ->
                if (state is Operation.State.IN_PROGRESS) return@observe
                enqueueState.removeObservers(viewLifecycleOwner)
                if (state is Operation.State.SUCCESS) toastNewsFetchResult(context, fetchedBefore, today)
            }
        }
    }
}

/**
 * Toasts what the manual harvest fetched once its work finishes — every "fetch now"
 * work of the unique name is terminal, the same wait the word lookup refresh uses.
 * When KEEP kept an already-pending run, that is the run whose finish
 * this waits for.
 */
private fun SettingsFragment.toastNewsFetchResult(
    context: Context,
    articlesFetchedBefore: Int,
    todayEpochDay: Long,
) {
    val liveData = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkLiveData(NewsHarvestWorker.WORK_NAME_FETCH_NOW)
    liveData.observe(viewLifecycleOwner) { infos ->
        if (infos.isEmpty() || !infos.all { it.state.isFinished }) return@observe
        liveData.removeObservers(viewLifecycleOwner)
        when (val finished = NewsFetchNow.finished(prefs.newsArticlesFetchedToday(todayEpochDay) - articlesFetchedBefore)) {
            is NewsFetchNow.Finished.Fetched ->
                context.showToast(getString(R.string.news_fetch_now_fetched, finished.articles))
            NewsFetchNow.Finished.NothingNew ->
                context.showToast(getString(R.string.news_fetch_now_nothing_new))
        }
    }
}

/**
 * The API key dialog (#602, 9.9): a masked key field, the provider with base URL and
 * model behind an Advanced toggle, a Test button (one minimal call through the
 * provider, result in the status line — the key is never shown) and Save, which
 * writes the key to [KeyVault], never to prefs.
 */
internal fun SettingsFragment.showAiKeyDialog() {
    val context = requireContext()
    val vault = KeyVault(context)
    var keyInput: AppCompatEditText? = null
    var baseUrlInput: AppCompatEditText? = null
    var modelInput: AppCompatEditText? = null
    var status: TextView? = null
    var advancedPanel: LinearLayout? = null
    var dialog: OlDialog? = null

    fun showDialogError(message: CharSequence) {
        status?.text = message
    }

    fun test() {
        val statusView = status ?: return
        val key = keyInput?.text?.toString()?.trim().orEmpty().ifBlank { vault.read().orEmpty() }
        val baseUrl = baseUrlInput?.text?.toString()?.trim().orEmpty().ifBlank { prefs.aiBaseUrl }
        val model = modelInput?.text?.toString()?.trim().orEmpty().ifBlank { prefs.aiModel }
        // The gate guards every network call: off, no key or no model means no
        // Test call — and a missing model is named, with Advanced opened so the
        // field is right there (review R1 #7; no default model is chosen here).
        when (AiPracticeGate.block(prefs.aiPracticeEnabled, key.isNotBlank(), model)) {
            AiPracticeGate.Block.DISABLED -> statusView.text = context.getString(R.string.ai_practice_turned_off)
            AiPracticeGate.Block.NO_KEY -> statusView.text = context.getString(R.string.ai_practice_key_required)
            AiPracticeGate.Block.NO_MODEL -> {
                statusView.text = context.getString(R.string.ai_practice_model_required)
                advancedPanel?.isVisible = true
            }
            null -> {
                statusView.text = context.getString(R.string.ai_practice_testing)
                lifecycleScope.launch {
                    val startedAt = System.currentTimeMillis()
                    val result = withContext(Dispatchers.IO) {
                        AiTestCall.run(prefs.aiProvider, baseUrl, model, key)
                    }
                    val seconds = AiPracticeMessages.secondsText(System.currentTimeMillis() - startedAt)
                    statusView.text = when (result) {
                        is AiResult.Ok -> getString(R.string.ai_practice_test_works, model, seconds)
                        is AiResult.Error -> getString(AiPracticeMessages.labelRes(result.kind))
                    }
                }
            }
        }
    }

    fun save() {
        val key = keyInput?.text?.toString()?.trim().orEmpty()
        val baseUrl = baseUrlInput?.text?.toString()?.trim().orEmpty()
        val model = modelInput?.text?.toString()?.trim().orEmpty()
        if (key.isEmpty()) {
            showDialogError(context.getString(R.string.ai_practice_key_required))
            return
        }
        // The Keystore can be temporarily unavailable; a failed save surfaces in
        // the dialog instead of crashing Settings (review R1 #8).
        val saved = runCatching { vault.save(key) }
        if (saved.isFailure) {
            showDialogError(context.getString(R.string.ai_practice_key_save_failed))
            return
        }
        // A freshly saved key ends a key rejection: the next run may call again
        // (review R1 #6).
        prefs.aiKeyRejected = false
        if (baseUrl.startsWith("https://")) prefs.aiBaseUrl = baseUrl
        if (model.isNotEmpty()) prefs.aiModel = model
        // A first key while the switch is on: the first top-up can start now.
        AiPracticeToggle.apply(prefs.aiPracticeEnabled, PracticeWorkerScheduler(context))
        populateAiPractice()
        context.showToast(getString(R.string.ai_practice_key_saved))
        dialog?.dismiss()
    }

    dialog = context.createDialog(
        title = R.string.ai_practice_api_key,
        action = R.string.save,
        onAction = { save() },
    ) { container ->
        val dialogContext = container.context

        val statusView = TextView(dialogContext).apply {
            setTextColor(dialogContext.getColorFromAttr(R.attr.primaryColor))
            if (prefs.aiBillingBlocked) setText(R.string.ai_practice_billing_blocked)
        }
        status = statusView

        val providerLine = TextView(dialogContext).apply {
            setTextColor(dialogContext.getColorFromAttr(R.attr.primaryColor))
            text = getString(R.string.ai_practice_provider, AiPracticeMessages.providerLabel(prefs.aiProvider))
        }

        val keyField = AppCompatEditText(dialogContext).apply {
            hint = dialogContext.getString(R.string.ai_practice_key_hint)
            setSingleLine(true)
            // Masked: the key is never shown, not even to its owner.
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setTextColor(dialogContext.getColorFromAttr(R.attr.primaryColor))
            setHintTextColor(dialogContext.getColorFromAttr(R.attr.primaryColorTrans80))
            background = null
        }
        keyInput = keyField

        val baseUrlField = AppCompatEditText(dialogContext).apply {
            setHint(R.string.ai_practice_base_url)
            setText(prefs.aiBaseUrl)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setTextColor(dialogContext.getColorFromAttr(R.attr.primaryColor))
            setHintTextColor(dialogContext.getColorFromAttr(R.attr.primaryColorTrans80))
            background = null
        }
        baseUrlInput = baseUrlField

        val modelField = AppCompatEditText(dialogContext).apply {
            setHint(R.string.ai_practice_model)
            setText(prefs.aiModel)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(dialogContext.getColorFromAttr(R.attr.primaryColor))
            setHintTextColor(dialogContext.getColorFromAttr(R.attr.primaryColorTrans80))
            background = null
        }
        modelInput = modelField

        val advancedView = LinearLayout(dialogContext).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(baseUrlField)
            addView(modelField)
        }
        advancedPanel = advancedView

        val advancedToggle = TextView(dialogContext).apply {
            setText(R.string.ai_practice_advanced)
            setTextColor(dialogContext.getColorFromAttr(R.attr.primaryColor))
            background = null
            setOnClickListener { advancedView.isVisible = !advancedView.isVisible }
        }

        val testButton = TextView(dialogContext).apply {
            setText(R.string.ai_practice_test)
            setTextColor(dialogContext.getColorFromAttr(R.attr.primaryColor))
            background = null
            setOnClickListener { test() }
        }

        LinearLayout(dialogContext).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            addView(statusView)
            addView(providerLine)
            addView(keyField)
            addView(advancedToggle)
            addView(advancedView)
            addView(testButton)
        }
    }
    dialog.showRespectingStatusBar()
    keyInput?.apply {
        setSelection(text?.length ?: 0)
        post {
            requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }
}

/**
 * The web-account row (#835, task 02): the tap depends on the session state.
 * Signed out or lapsed opens the pairing dialog; signed in opens the web app
 * or signs out. The row's value line itself comes from [GermanWebAccountRow].
 */
internal fun SettingsFragment.onGermanWebAccountTap(anchor: View) {
    when (GermanWebSession(requireContext()).state()) {
        GermanWebSessionState.SignedOut, GermanWebSessionState.Lapsed -> showGermanWebPairDialog()
        is GermanWebSessionState.SignedIn -> showGermanWebAccountMenu(anchor)
    }
}

/** "Open web app" / "Sign out" while signed in; revoking the device happens on the web. */
internal fun SettingsFragment.showGermanWebAccountMenu(anchor: View) {
    anchor.showPopupMenu(
        configure = { menu ->
            menu.add(Menu.NONE, 0, 0, R.string.german_web_open)
            menu.add(Menu.NONE, 1, 1, R.string.german_web_sign_out)
        }
    ) { item ->
        when (item.itemId) {
            0 -> openGermanWebApp()
            1 -> confirmGermanWebSignOut()
        }
    }
}

/** Opens the web app's Konto page, where the user's devices are listed and revoked. */
internal fun SettingsFragment.openGermanWebApp() {
    val base = GermanWebSession(requireContext()).baseUrl() ?: return
    requireContext().openUrl("$base/#/konto")
}

/**
 * Sign-out confirmation. One line says what it does: forget the device on this
 * phone only — revoking it happens in the web app's Konto. Local words and
 * captures are untouched ([GermanWebSession.signOut] drops the credential and
 * the two pairing prefs, nothing else).
 */
internal fun SettingsFragment.confirmGermanWebSignOut() {
    dialog?.dismiss()
    dialog = requireContext().showMessageDialog(
        title = R.string.german_web_sign_out_title,
        message = R.string.german_web_sign_out_message,
        action = R.string.confirm,
    ) {
        GermanWebSession(requireContext()).signOut()
        // The cached web due set belongs to the signed-in session (#835, task 04).
        GermanDueRefreshWorker.onSessionEnded(requireContext())
        populateGerman()
    }
}

/**
 * The "Move progress to the web app" row (#835, task 05): the guided path to the
 * web import (gambitflow #859 converts the launcher's own backup file in the
 * browser, so the launcher writes no new format). The dialog walks the three
 * steps; "Save backup" reuses the export from the backup section, and a
 * successful export records the day and offers Konto ([onGermanWebProgressExported]).
 * The texts come from [GermanWebProgressRow].
 */
internal fun SettingsFragment.showGermanWebProgressDialog() {
    val message = GermanWebProgressRow.dialogMessageFor(prefs.germanWebProgressExportedDay)
    dialog?.dismiss()
    dialog = requireContext().showMessageDialog(
        title = R.string.german_web_move_progress,
        message = getString(message.stringRes, *message.formatArgs.toTypedArray()),
        action = R.string.german_web_move_progress_save,
    ) {
        germanWebProgressExportRequested = true
        launchExportBackup()
    }
}

/**
 * A successful export started from the "Move progress to the web app" row: the
 * day is recorded (guidance only — nothing in the app is gated on it) and the
 * user is offered the import page. Without a paired server the action becomes
 * "Connect" and opens pairing instead; the export itself already landed.
 */
internal fun SettingsFragment.onGermanWebProgressExported() {
    if (!isAdded || _binding == null) return
    prefs.germanWebProgressExportedDay = LocalDate.now().toEpochDay()
    populateGerman()
    val base = GermanWebSession(requireContext()).baseUrl()
    dialog?.dismiss()
    dialog = requireContext().showMessageDialog(
        title = R.string.german_web_move_progress,
        message = R.string.german_web_move_progress_offer,
        action = if (base != null) R.string.german_web_move_progress_open_konto else R.string.german_web_connect,
    ) {
        if (base != null) openGermanWebApp() else showGermanWebPairDialog()
    }
}

/**
 * The pairing dialog (#835): the server address (nothing is hardcoded — the
 * launcher pairs with whatever server the user types here) and the one-time
 * 8-character code from the web app (Konto → Gerät hinzufügen). Connect stays
 * disabled until the address normalises and the code has 8 characters; every
 * failure keeps the dialog open with its own one-line message. The code and
 * the device token never appear in an error, a toast or a log.
 */
internal fun SettingsFragment.showGermanWebPairDialog() {
    val context = requireContext()
    val session = GermanWebSession(context)
    var serverInput: AppCompatEditText? = null
    var codeInput: AppCompatEditText? = null
    var status: TextView? = null
    var connectButton: TextView? = null
    var dialogRef: OlDialog? = null

    fun showDialogError(message: CharSequence) {
        status?.text = message
    }

    fun serverAddress(): String? =
        GermanWebBaseUrl.normalize(serverInput?.text?.toString().orEmpty())

    fun connectReady(): Boolean =
        serverAddress() != null &&
            codeInput?.text?.toString()?.trim()?.length == GermanWebApi.PAIRING_CODE_LENGTH

    /** Connect follows the fields; the one-line hint names the first thing to fix. */
    fun refreshConnectState() {
        val ready = connectReady()
        connectButton?.apply {
            isEnabled = ready
            alpha = if (ready) 1f else 0.5f
        }
        if (!ready) {
            status?.text = if (serverAddress() == null) {
                context.getString(R.string.german_web_server_invalid)
            } else {
                context.getString(R.string.german_web_code_length)
            }
        }
    }

    fun connect() {
        val address = serverAddress() ?: return
        val code = codeInput?.text?.toString().orEmpty()
        showDialogError(context.getString(R.string.german_web_connecting))
        lifecycleScope.launch {
            val api = GermanWebApi(address)
            val label = "Thumbline (${Build.MODEL})".take(GermanWebApi.MAX_LABEL_LENGTH)
            val result = withContext(Dispatchers.IO) { api.exchangePairingCode(code, label) }
            when (result) {
                is PairingResult.Paired -> {
                    // The Keystore can be temporarily unavailable; a failed save
                    // surfaces in the dialog instead of half-pairing (AI key pattern).
                    val saved = runCatching {
                        session.onPaired(
                            GermanDeviceCredential(result.token, result.deviceId, result.username),
                            address,
                        )
                    }
                    if (saved.isFailure) {
                        showDialogError(context.getString(R.string.german_web_save_failed))
                        return@launch
                    }
                    // Captures queued while lapsed (and kept for after re-pairing)
                    // can go out now: one unique replay run.
                    GermanSyncWorker.schedule(context)
                    // The due-set cache starts filling and stays fresh (#835, task 04).
                    GermanDueRefreshWorker.schedulePeriodic(context)
                    dialogRef?.dismiss()
                    // A cancelled dialog or a destroyed fragment must not crash.
                    if (isAdded && _binding != null) {
                        context.showToast(getString(R.string.german_web_connected_toast, result.username))
                        populateGerman()
                    }
                }
                PairingResult.InvalidCode -> showDialogError(context.getString(R.string.german_web_invalid_code))
                PairingResult.TooManyAttempts -> showDialogError(context.getString(R.string.german_web_too_many_attempts))
                is PairingResult.NetworkError -> showDialogError(context.getString(R.string.german_web_network_error))
                is PairingResult.ServerError -> showDialogError(context.getString(R.string.german_web_server_error))
            }
        }
    }

    dialogRef = context.createDialog(
        title = R.string.german_web_pair_title,
        // Connect lives in the content, not the action row: the dialog's action
        // always closes it, and a failed exchange must keep it open.
        action = 0,
    ) { container ->
        val dialogContext = container.context

        val hintView = TextView(dialogContext).apply {
            setTextColor(dialogContext.getColorFromAttr(R.attr.primaryColor))
            setText(R.string.german_web_pair_hint)
        }

        val serverField = AppCompatEditText(dialogContext).apply {
            hint = dialogContext.getString(R.string.german_web_server_hint)
            // The last paired address, so pairing again after a lapse or sign-out
            // starts with it; blank while never paired.
            setText(session.baseUrl().orEmpty())
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setTextColor(dialogContext.getColorFromAttr(R.attr.primaryColor))
            setHintTextColor(dialogContext.getColorFromAttr(R.attr.primaryColorTrans80))
            background = null
        }
        serverInput = serverField

        val statusView = TextView(dialogContext).apply {
            setTextColor(dialogContext.getColorFromAttr(R.attr.primaryColor))
        }
        status = statusView

        val codeField = AppCompatEditText(dialogContext).apply {
            hint = dialogContext.getString(R.string.german_web_code_hint)
            setSingleLine(true)
            // All caps, 8 characters, visible-password input: the one-time code
            // is shown as typed but the keyboard never learns it.
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            filters = arrayOf(
                InputFilter.LengthFilter(GermanWebApi.PAIRING_CODE_LENGTH),
                InputFilter.AllCaps(),
            )
            setTextColor(dialogContext.getColorFromAttr(R.attr.primaryColor))
            setHintTextColor(dialogContext.getColorFromAttr(R.attr.primaryColorTrans80))
            background = null
        }
        codeInput = codeField

        val connectView = TextView(dialogContext).apply {
            setText(R.string.german_web_connect)
            setTextColor(dialogContext.getColorFromAttr(R.attr.primaryColor))
            background = null
            setOnClickListener { connect() }
        }
        connectButton = connectView

        serverField.doAfterTextChanged { refreshConnectState() }
        codeField.doAfterTextChanged { refreshConnectState() }

        LinearLayout(dialogContext).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            addView(hintView)
            addView(serverField)
            addView(statusView)
            addView(codeField)
            addView(connectView)
        }
    }
    dialogRef.showRespectingStatusBar()
    refreshConnectState()
}

internal fun SettingsFragment.populateGerman() {
    binding.germanOnOff.text = getString(if (prefs.germanEnabled) R.string.on else R.string.off)
    binding.dictionaryAppLayout.visibility = if (prefs.germanEnabled) View.VISIBLE else View.GONE
    binding.dictionaryApp.text = if (prefs.germanDictionaryApp.isBlank()) getString(R.string.dictionary_wiktionary)
        else DictionaryApps.label(requireContext(), prefs.germanDictionaryApp)
    binding.germanClockLayout.visibility = if (prefs.germanEnabled) View.VISIBLE else View.GONE
    binding.germanClock.text = when (prefs.germanClock) {
        Prefs.GERMAN_CLOCK_DEUTSCH -> getString(R.string.deutsch_de)
        Prefs.GERMAN_CLOCK_IN_WORTEN -> getString(R.string.in_worten_de)
        else -> getString(R.string.system_default)
    }

    binding.germanOfflineDictionaryLayout.visibility = if (prefs.germanEnabled) View.VISIBLE else View.GONE
    binding.germanOfflineDictionaryOnOff.text = getString(if (prefs.germanOfflineDictionary) R.string.on else R.string.off)
    binding.germanLookupLayout.visibility = if (prefs.germanEnabled) View.VISIBLE else View.GONE
    binding.germanLookupOnOff.text = getString(if (prefs.germanLookup) R.string.on else R.string.off)
    binding.germanSpellSuggestLayout.visibility = if (prefs.germanEnabled) View.VISIBLE else View.GONE
    binding.germanSpellSuggestOnOff.text = getString(if (prefs.germanSpellSuggest) R.string.on else R.string.off)
    binding.germanScreenPickLayout.visibility = if (prefs.germanEnabled) View.VISIBLE else View.GONE
    binding.germanScreenPickScreenshotLayout.isVisible = showScreenPickScreenshotHint()
    binding.germanScreenPickOnOff.text = getString(if (prefs.germanScreenPick) R.string.on else R.string.off)
    binding.germanPauseWordLayout.visibility = if (prefs.germanEnabled) View.VISIBLE else View.GONE
    binding.germanPauseWordOnOff.text = getString(if (prefs.germanPauseWord) R.string.on else R.string.off)
    binding.germanUiLayout.visibility = if (prefs.germanEnabled) View.VISIBLE else View.GONE
    binding.germanUiOnOff.text = getString(if (prefs.germanUi) R.string.on else R.string.off)
    binding.aiPracticeLayout.visibility = if (prefs.germanEnabled) View.VISIBLE else View.GONE
    populateAiPractice()
    // The Ask-AI conversations row (#835, task 06): the same gate as every chat
    // entry — hidden, never greyed, when the practice switch is off or the key is
    // missing. The value line says how many conversations exist.
    binding.askAiConversationsLayout.visibility =
        if (prefs.germanEnabled && AskAiEntryPoints.environment(requireContext()).visible) View.VISIBLE else View.GONE
    binding.askAiConversations.text = AiChatStore(requireContext()).threads().size.toString()
    binding.newsSentencesLayout.visibility = if (prefs.germanEnabled) View.VISIBLE else View.GONE
    binding.newsSentencesOnOff.text = getString(if (prefs.newsSentencesEnabled) R.string.on else R.string.off)
    // #794: the trailing "Fetch now" chip shows only while the feature runs; the row
    // itself is hidden while German is off.
    binding.newsSentencesActions.isVisible = prefs.newsSentencesEnabled
    // The web-account row (#835): the value line is the session state; the
    // signed-in line also names the server host, and the sync counts (task 03)
    // are appended while captures wait or were dropped.
    binding.germanWebAccountLayout.visibility = if (prefs.germanEnabled) View.VISIBLE else View.GONE
    val webSession = GermanWebSession(requireContext())
    val webHost = webSession.baseUrl()?.let { Uri.parse(it).host }
    val webOutbox = GermanWebOutbox(requireContext().noBackupFilesDir)
    val webValue = GermanWebAccountRow.valueFor(webSession.state(), webHost)
    binding.germanWebAccount.text = buildString {
        append(getString(webValue.stringRes, *webValue.formatArgs.toTypedArray()))
        GermanWebAccountRow.countLines(webOutbox.pendingCount(), webOutbox.failedCount()).forEach { line ->
            append(' ').append(getString(line.stringRes, *line.formatArgs.toTypedArray()))
        }
    }
    // The web-progress row (#835, task 05): the value line says when the guided
    // export last landed ("Saved on <date>"); blank until then.
    binding.germanWebProgressLayout.visibility = if (prefs.germanEnabled) View.VISIBLE else View.GONE
    binding.germanWebProgress.text = GermanWebProgressRow.valueFor(prefs.germanWebProgressExportedDay)
        ?.let { getString(it.stringRes, *it.formatArgs.toTypedArray()) }
        .orEmpty()
}

internal fun SettingsFragment.showDictionaryAppMenu(anchor: View) {
    val apps = DictionaryApps.textApps(requireContext())
    anchor.showPopupMenu(
        configure = { menu ->
            menu.add(Menu.NONE, 0, 0, R.string.dictionary_wiktionary)
            apps.forEachIndexed { index, app -> menu.add(Menu.NONE, index + 1, index + 1, app.label) }
            menu.add(Menu.NONE, apps.size + 1, apps.size + 1, R.string.dictionary_other_app)
        }
    ) { item ->
        when (item.itemId) {
            0 -> {
                prefs.germanDictionaryApp = ""
                populateGerman()
            }
            apps.size + 1 -> showDictionaryLaunchMenu(anchor)
            else -> {
                prefs.germanDictionaryApp = apps[item.itemId - 1].packageName
                populateGerman()
            }
        }
    }
}

private fun SettingsFragment.showDictionaryLaunchMenu(anchor: View) {
    val apps = DictionaryApps.launchableApps(requireContext())
    anchor.showPopupMenu(
        configure = { menu ->
            apps.forEachIndexed { index, app -> menu.add(Menu.NONE, index, index, app.label) }
        }
    ) { item ->
        prefs.germanDictionaryApp = apps[item.itemId].packageName
        populateGerman()
    }
}

internal fun SettingsFragment.showGermanClockMenu(anchor: View) {
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
