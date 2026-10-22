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

internal fun SettingsFragment.toggleSwipeLeft() {
    prefs.swipeLeftEnabled = !prefs.swipeLeftEnabled
    if (prefs.swipeLeftEnabled) {
        binding.swipeLeftApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
        requireContext().showToast(getString(R.string.swipe_left_app_enabled))
    } else {
        binding.swipeLeftApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
        requireContext().showToast(getString(R.string.swipe_left_app_disabled))
    }
}

internal fun SettingsFragment.toggleSwipeRight() {
    prefs.swipeRightEnabled = !prefs.swipeRightEnabled
    if (prefs.swipeRightEnabled) {
        binding.swipeRightApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
        requireContext().showToast(getString(R.string.swipe_right_app_enabled))
    } else {
        binding.swipeRightApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
        requireContext().showToast(getString(R.string.swipe_right_app_disabled))
    }
}

internal fun SettingsFragment.toggleStatusBar() {
    prefs.showStatusBar = !prefs.showStatusBar
    populateStatusBar()
}

internal fun SettingsFragment.populateStatusBar() {
    if (prefs.showStatusBar) {
        requireActivity().window.showStatusBar()
        binding.statusBar.text = getString(R.string.on)
    } else {
        requireActivity().window.hideStatusBar()
        binding.statusBar.text = getString(R.string.off)
    }
}

internal fun SettingsFragment.toggleDateTime(selected: Int) {
    prefs.dateTimeVisibility = selected
    populateDateTime()
    viewModel.toggleDateTime()
}

internal fun SettingsFragment.populateDateTime() {
    binding.dateTime.text = getString(
        when (prefs.dateTimeVisibility) {
            Constants.DateTime.DATE_ONLY -> R.string.date
            Constants.DateTime.ON -> R.string.on
            else -> R.string.off
        }
    )
}

internal fun SettingsFragment.showBatteryModeMenu(anchor: View) {
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

internal fun SettingsFragment.populateBatteryMode() {
    binding.batteryMode.text = getString(
        when (prefs.batteryMode) {
            Prefs.BATTERY_MODE_ALWAYS -> R.string.always
            Prefs.BATTERY_MODE_OFF -> R.string.off
            else -> R.string.auto
        }
    )
}

internal fun SettingsFragment.populateHomeSets() {
    binding.homeSets.text = getString(if (prefs.homeSets) R.string.on else R.string.off)
    binding.morningStart.text = formatMinutes(prefs.homeSetMorningStart)
    binding.dayStart.text = formatMinutes(prefs.homeSetDayStart)
    binding.eveningStart.text = formatMinutes(prefs.homeSetEveningStart)
}

internal fun SettingsFragment.formatMinutes(minutes: Int): String =
    String.format("%02d:%02d", minutes / 60, minutes % 60)

internal fun SettingsFragment.toggleHomeSets() {
    prefs.homeSets = !prefs.homeSets
    populateHomeSets()
    viewModel.refreshHome(false)
}

internal fun SettingsFragment.showHomeSetStartPicker(set: HomeSet, @StringRes title: Int) {
    val current = prefs.homeSetStart(set)
    showTimePicker(title, current / 60, current % 60) { hour, minute ->
        prefs.setHomeSetStart(set, HomeSets.minutesOfDay(hour, minute))
        populateHomeSets()
        viewModel.refreshHome(false)
    }
}

internal fun SettingsFragment.showHomeSetSlotsDialog(set: HomeSet) {
    val num = prefs.homeAppsNum.coerceIn(0, HomeSets.MAX_SLOTS)
    if (num == 0) {
        requireContext().showToast(R.string.home_set_no_slots)
        return
    }
    val empty = getString(R.string.home_set_slot_empty)
    val labels = Array(num) { i ->
        val name = prefs.getAppName(i + 1, set)
        "${i + 1}. ${name.ifEmpty { empty }}"
    }
    requireContext().createChoiceDialog(
        title = if (set == HomeSet.MORNING) R.string.edit_morning_apps else R.string.edit_evening_apps,
        items = labels.toList(),
    ) { which -> showAppListIfEnabled(HomeSets.flagFor(set, which + 1)) }
        .showRespectingStatusBar()
}

internal fun SettingsFragment.toggleInfoLine() {
    prefs.homeInfoCombined = !prefs.homeInfoCombined
    populateInfoLine()
    viewModel.toggleDateTime()
}

internal fun SettingsFragment.populateInfoLine() {
    binding.infoLine.text = getString(
        if (prefs.homeInfoCombined) R.string.info_line_combined else R.string.info_line_separate
    )
}

internal fun SettingsFragment.toggleContextLine() {
    prefs.homeContextLine = !prefs.homeContextLine
    populateContextLine()
    viewModel.toggleDateTime()
    if (!prefs.homeContextLine) return
    // Permissions are asked only now, when the line is first switched on. Denial leaves it working.
    val calendarGranted = ContextCompat.checkSelfPermission(
        requireContext(), Manifest.permission.READ_CALENDAR
    ) == PackageManager.PERMISSION_GRANTED
    if (calendarGranted) {
        askContextLineNotificationAccess()
    } else {
        dialog?.dismiss()
        dialog = requireContext().showMessageDialog(
            title = R.string.context_line,
            message = R.string.calendar_permission_message,
            action = R.string.allow,
        ) {
            contextLineCalendarLauncher.launch(Manifest.permission.READ_CALENDAR)
        }
    }
}

internal fun SettingsFragment.askContextLineNotificationAccess() {
    if (isNotificationAccessGranted()) return
    dialog?.dismiss()
    dialog = requireContext().showMessageDialog(
        title = R.string.context_line,
        message = R.string.context_line_access_message,
        action = R.string.allow,
    ) {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: Exception) {
            requireContext().showToast(getString(R.string.unable_to_open_app))
        }
    }
}

internal fun SettingsFragment.populateContextLine() {
    binding.contextLineValue.text = getString(if (prefs.homeContextLine) R.string.on else R.string.off)
}

internal fun SettingsFragment.toggleHomeNextEvent() {
    prefs.homeNextEvent = !prefs.homeNextEvent
    populateHomeNextEvent()
    if (!prefs.homeNextEvent) return
    // Permission is asked only now, when the line is switched on. Denial leaves it on.
    val calendarGranted = ContextCompat.checkSelfPermission(
        requireContext(), Manifest.permission.READ_CALENDAR
    ) == PackageManager.PERMISSION_GRANTED
    if (calendarGranted) return
    dialog?.dismiss()
    dialog = requireContext().showMessageDialog(
        title = R.string.home_next_event,
        message = R.string.calendar_permission_message,
        action = R.string.allow,
    ) {
        homeNextEventCalendarLauncher.launch(Manifest.permission.READ_CALENDAR)
    }
}

internal fun SettingsFragment.toggleHomeNotes() {
    prefs.homeNotes = !prefs.homeNotes
    populateHomeNotes()
}

internal fun SettingsFragment.populateHomeNotes() {
    binding.homeNotesValue.text = getString(if (prefs.homeNotes) R.string.on else R.string.off)
}

internal fun SettingsFragment.populateHomeNextEvent() {
    binding.homeNextEventValue.text = getString(if (prefs.homeNextEvent) R.string.on else R.string.off)
}

