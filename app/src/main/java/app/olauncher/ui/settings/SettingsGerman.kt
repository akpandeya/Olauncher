package app.olauncher.ui.settings

import app.olauncher.ui.*
import app.olauncher.ui.SettingsFragment
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
import app.olauncher.data.store.AiPracticeMessages
import app.olauncher.data.store.AiPracticeGate
import app.olauncher.data.store.AiResult
import app.olauncher.data.store.AiTestCall
import app.olauncher.data.store.KeyVault
import app.olauncher.data.store.PracticeItemsStore
import app.olauncher.data.store.PracticeQueue
import app.olauncher.data.store.WordsStore
import app.olauncher.helper.AiPracticeToggle
import app.olauncher.helper.NewsHarvestToggle
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

internal fun SettingsFragment.populateAiPractice() {
    binding.aiPracticeOnOff.text = getString(if (prefs.aiPracticeEnabled) R.string.on else R.string.off)
    val subtitleRes = AiPracticeMessages.subtitleRes(
        prefs.aiPracticeEnabled, prefs.aiBillingBlocked, prefs.aiKeyRejected, prefs.aiModel,
    )
    binding.aiPracticeSubtitle.text = subtitleRes?.let { getString(it) }
        ?: getString(R.string.ai_practice_subtitle, AiPracticeMessages.providerLabel(prefs.aiProvider))
}

internal fun SettingsFragment.showAiPracticeMenu(anchor: View) {
    val hasKey = !KeyVault(requireContext()).read().isNullOrBlank()
    anchor.showPopupMenu(
        configure = { menu ->
            menu.add(Menu.NONE, 0, 0, R.string.ai_practice_api_key)
            if (hasKey) menu.add(Menu.NONE, 1, 1, R.string.ai_practice_remove_key)
            menu.add(Menu.NONE, 2, 2, R.string.ai_practice_generate_now)
        }
    ) { item ->
        when (item.itemId) {
            0 -> showAiKeyDialog()
            1 -> {
                KeyVault(requireContext()).clear()
                requireContext().showToast(getString(R.string.ai_practice_key_removed))
                populateAiPractice()
            }
            2 -> generateAiPracticeNow()
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
    binding.newsSentencesLayout.visibility = if (prefs.germanEnabled) View.VISIBLE else View.GONE
    binding.newsSentencesOnOff.text = getString(if (prefs.newsSentencesEnabled) R.string.on else R.string.off)
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
