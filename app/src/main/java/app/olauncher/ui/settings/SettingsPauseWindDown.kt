package app.olauncher.ui.settings

import app.olauncher.helper.createChoiceDialog
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

internal fun SettingsFragment.populateScreenTimeCue() {
    binding.screenTimeStyle.text = getString(
        if (prefs.screenTimeStyle == ScreenTimeCue.STYLE_CUE) R.string.screen_time_style_cue
        else R.string.screen_time_style_number
    )
    binding.screenTimeTarget.text = getString(R.string.screen_time_target_hours, prefs.screenTimeTargetMin / 60)
}

internal fun SettingsFragment.showScreenTimeStyleMenu(anchor: View) {
    val items = listOf(
        ScreenTimeCue.STYLE_NUMBER to getString(R.string.screen_time_style_number),
        ScreenTimeCue.STYLE_CUE to getString(R.string.screen_time_style_cue),
    )
    anchor.showPopupMenu(
        configure = { menu ->
            items.forEachIndexed { index, pair -> menu.add(Menu.NONE, index, index, pair.second) }
        }
    ) { item ->
        val chosen = items.getOrNull(item.itemId)?.first ?: return@showPopupMenu
        prefs.screenTimeStyle = chosen
        populateScreenTimeCue()
        // The cue needs usage access; ask once, a denial just leaves the cue off.
        if (chosen == ScreenTimeCue.STYLE_CUE && !requireContext().appUsagePermissionGranted()) {
            viewModel.showDialog.postValue(Constants.Dialog.DIGITAL_WELLBEING)
        }
    }
}

internal fun SettingsFragment.showScreenTimeTargetMenu(anchor: View) {
    val choices = ScreenTimeCue.TARGET_CHOICES_MIN
    anchor.showPopupMenu(
        configure = { menu ->
            choices.forEachIndexed { index, min ->
                menu.add(Menu.NONE, index, index, getString(R.string.screen_time_target_hours, min / 60))
            }
        }
    ) { item ->
        val chosen = choices.getOrNull(item.itemId) ?: return@showPopupMenu
        prefs.screenTimeTargetMin = chosen
        populateScreenTimeCue()
    }
}

internal fun SettingsFragment.populateScreenTimeOnOff() {
    if (requireContext().appUsagePermissionGranted()) binding.screenTimeOnOff.text = getString(R.string.on)
    else binding.screenTimeOnOff.text = getString(R.string.off)
}

internal fun SettingsFragment.populateDistractingApps() {
    val count = prefs.distractingApps.size
    binding.distractingApps.text = if (count == 1) {
        getString(R.string.distracting_app_count, count)
    } else {
        getString(R.string.distracting_apps_count, count)
    }
}

internal fun SettingsFragment.populatePauseBeforeApps() {
    val snoozeUntil = prefs.pauseSnoozeUntil
    binding.pauseBeforeAppsOnOff.text = if (prefs.pauseEnabled && PauseSnooze.isActive(snoozeUntil, System.currentTimeMillis())) {
        getString(R.string.pause_off_until, PauseSnooze.formatUntil(snoozeUntil, ZoneId.systemDefault()))
    } else {
        getString(if (prefs.pauseEnabled) R.string.on else R.string.off)
    }
}

internal fun SettingsFragment.showPauseOffChoices(anchor: View) {
    anchor.showPopupMenu(
        configure = { menu ->
            menu.add(Menu.NONE, 1, 1, getString(R.string.snooze_hour))
            menu.add(Menu.NONE, 2, 2, getString(R.string.snooze_tomorrow))
            menu.add(Menu.NONE, 3, 3, getString(R.string.snooze_turn_off))
        }
    ) { item ->
        val choice = when (item.itemId) {
            1 -> PauseSnooze.Choice.HOUR
            2 -> PauseSnooze.Choice.TOMORROW
            3 -> null
            else -> return@showPopupMenu
        }
        val apply: () -> Unit = {
            if (choice == null) {
                prefs.pauseEnabled = false
            } else {
                prefs.pauseSnoozeUntil = PauseSnooze.until(choice, System.currentTimeMillis(), ZoneId.systemDefault())
            }
            if (_binding != null) populatePauseBeforeApps()
        }
        val change = if (choice == null) {
            FrictionChange.Pause(false)
        } else {
            FrictionChange.Snooze(PauseSnooze.until(choice, System.currentTimeMillis(), ZoneId.systemDefault()))
        }
        if (CommitLock.needsWait(prefs.commitLock, change)) {
            dialog?.dismiss()
            dialog = requireContext().showWaitDialog(
                message = getString(R.string.commit_lock_desc)
            ) { apply() }
        } else {
            apply()
        }
    }
}

internal fun SettingsFragment.togglePauseBeforeApps() {
    if (prefs.pauseEnabled) {
        if (PauseSnooze.isActive(prefs.pauseSnoozeUntil, System.currentTimeMillis())) {
            // Tapping while on a break ends the break (tightening, no wait).
            prefs.pauseSnoozeUntil = 0L
            populatePauseBeforeApps()
        } else {
            showPauseOffChoices(binding.pauseBeforeAppsOnOff)
        }
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

internal fun SettingsFragment.showFirstTimePausePermissionDialog() {
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

internal fun SettingsFragment.populatePauseAllowance() {
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

internal fun SettingsFragment.showPauseAllowanceMenu(anchor: View) {
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

internal fun SettingsFragment.populatePauseEscalate() {
    binding.pauseEscalateOnOff.text = getString(if (prefs.pauseEscalate) R.string.on else R.string.off)
}

internal fun SettingsFragment.togglePauseEscalate() {
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

internal fun SettingsFragment.populatePauseAskWhy() {
    binding.pauseAskWhyOnOff.text = getString(if (prefs.pauseAskWhy) R.string.on else R.string.off)
}

internal fun SettingsFragment.togglePauseAskWhy() {
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

internal fun SettingsFragment.populateWindDown() {
    binding.windDown.text = if (prefs.windDown) {
        "${formatMinutes(prefs.windDownStart)}\u2013${formatMinutes(prefs.windDownEnd)}"
    } else {
        getString(R.string.off)
    }
    val visibility = if (prefs.windDown) View.VISIBLE else View.GONE
    binding.windDownGreyLayout.visibility = visibility
    binding.systemGreyLayout.visibility = visibility
}

internal fun SettingsFragment.populateWindDownGrey() {
    binding.windDownGreyOnOff.text = getString(if (prefs.windDownGrey) R.string.on else R.string.off)
}

internal fun SettingsFragment.toggleWindDownGrey() {
    prefs.windDownGrey = !prefs.windDownGrey
    populateWindDownGrey()
    WindDownWorker.syncAsync(requireContext())
}

internal fun SettingsFragment.populateSystemGrey() {
    val granted = SystemGrey.isGranted(requireContext())
    binding.systemGreyOnOff.text = getString(if (prefs.windDownSystemGrey) R.string.on else R.string.off)
    binding.systemGreyStatus.text = getString(if (granted) R.string.system_grey_ready else R.string.system_grey_needs_grant)
    binding.systemGreyActions.visibility = if (granted) View.GONE else View.VISIBLE
}

internal fun SettingsFragment.toggleSystemGrey() {
    prefs.windDownSystemGrey = !prefs.windDownSystemGrey
    populateSystemGrey()
    // Off restores the colour settings right away; on applies grey if wind-down is already active.
    WindDownWorker.syncAsync(requireContext())
}

internal fun SettingsFragment.copySystemGreyCommand() {
    requireContext().copyToClipboard(SystemGrey.GRANT_COMMAND)
    requireContext().showToast(R.string.system_grey_copied)
}

internal fun SettingsFragment.showWindDownMenu() {
    val labels = arrayOf(
        getString(if (prefs.windDown) R.string.wind_down_turn_off else R.string.wind_down_turn_on),
        getString(R.string.wind_down_starts) + "  " + formatMinutes(prefs.windDownStart),
        getString(R.string.wind_down_ends) + "  " + formatMinutes(prefs.windDownEnd),
    )
    dialog?.dismiss()
    val menuDialog = requireContext().createChoiceDialog(
        title = R.string.wind_down,
        message = R.string.wind_down_desc,
        items = labels.toList(),
    ) { which ->
        when (which) {
            0 -> toggleWindDown()
            1 -> showWindDownTimePicker(isStart = true)
            2 -> showWindDownTimePicker(isStart = false)
        }
    }
    dialog = menuDialog
    menuDialog.showRespectingStatusBar()
}

internal fun SettingsFragment.toggleWindDown() {
    if (prefs.windDown) {
        val change = FrictionChange.WindDownToggle(false)
        if (CommitLock.needsWait(prefs.commitLock, change)) {
            dialog?.dismiss()
            dialog = requireContext().showWaitDialog(
                message = getString(R.string.commit_lock_desc)
            ) {
                prefs.windDown = false
                populateWindDown()
                viewModel.refreshHome(false)
            }
            return
        }
        prefs.windDown = false
    } else {
        prefs.windDown = true
    }
    populateWindDown()
    viewModel.refreshHome(false)
}

internal fun SettingsFragment.showWindDownTimePicker(isStart: Boolean) {
    val current = if (isStart) prefs.windDownStart else prefs.windDownEnd
    showTimePicker(
        if (isStart) R.string.wind_down_starts else R.string.wind_down_ends,
        current / 60,
        current % 60,
    ) { hour, minute ->
        val minutes = HomeSets.minutesOfDay(hour, minute)
        if (isStart) prefs.windDownStart = minutes else prefs.windDownEnd = minutes
        populateWindDown()
        viewModel.refreshHome(false)
    }
}

internal fun SettingsFragment.populateCommitLock() {
    binding.commitLockOnOff.text = getString(if (prefs.commitLock) R.string.on else R.string.off)
}

internal fun SettingsFragment.toggleCommitLock() {
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
