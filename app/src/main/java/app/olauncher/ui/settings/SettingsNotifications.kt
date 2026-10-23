package app.olauncher.ui.settings

import app.olauncher.helper.createChoiceDialog
import app.olauncher.helper.createMultiChoiceDialog
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

internal fun SettingsFragment.isNotificationAccessGranted(): Boolean {
    return NotificationManagerCompat.getEnabledListenerPackages(requireContext())
        .contains(requireContext().packageName)
}

internal fun SettingsFragment.populateHealthConnect() {
    binding.healthConnectOnOff.text = getString(if (prefs.healthConnectEnabled) R.string.on else R.string.off)
}

internal fun SettingsFragment.toggleHealthConnect() {
    if (prefs.healthConnectEnabled) {
        prefs.healthConnectEnabled = false
        populateHealthConnect()
        return
    }
    if (!HealthRepository.isAvailable(requireContext())) {
        requireContext().showToast(R.string.health_connect_unavailable)
        return
    }
    // Explain first, then ask. The switch only turns on after a grant.
    requireContext().showMessageDialog(
        R.string.use_health_connect,
        R.string.health_connect_explain,
        R.string.health_connect_allow
    ) {
        healthConnectPermissionLauncher.launch(HealthRepository.READ_PERMISSIONS)
    }
}

internal fun SettingsFragment.populateHoldNotifications() {
    val isOn = prefs.quietEnabled && isNotificationAccessGranted()
    binding.holdNotificationsOnOff.text = getString(if (isOn) R.string.on else R.string.off)
    binding.quietReleaseTimesLayout.visibility = if (isOn) View.VISIBLE else View.GONE
}

internal fun SettingsFragment.toggleHoldNotifications() {
    val accessGranted = isNotificationAccessGranted()
    val currentlyActive = prefs.quietEnabled && accessGranted
    if (currentlyActive) {
        prefs.quietEnabled = false
        populateHoldNotifications()
        ReleaseWorker.cancel(requireContext())
    } else {
        prefs.quietEnabled = true
        if (!accessGranted) {
            populateHoldNotifications()
            showHoldNotificationsAccessDialog()
        } else {
            populateHoldNotifications()
            ReleaseWorker.schedule(requireContext())
        }
    }
}

internal fun SettingsFragment.populateQuietReleaseTimes() {
    val times = QuietRelease.parseTimes(prefs.quietReleaseTimes)
    binding.quietReleaseTimes.text = QuietRelease.formatReleaseTimesRow(times)
}

internal fun SettingsFragment.showReleaseTimesEditor() {
    val times = QuietRelease.parseTimes(prefs.quietReleaseTimes).toMutableList()
    val items = mutableListOf<String>()
    for (time in times) {
        items.add(QuietRelease.formatTime(time))
    }
    if (times.size < 3) {
        items.add(getString(R.string.quiet_release_add_time))
    }

    val choiceDialog = requireContext().createChoiceDialog(
        title = R.string.quiet_release_times,
        items = items,
    ) { which ->
        if (which == times.size && times.size < 3) {
            showTimePicker(
                title = R.string.quiet_release_add_time,
                initialHour = 12,
                initialMinute = 0,
            ) { hour, minute ->
                val newTime = hour * 60 + minute
                if (newTime !in times) {
                    times.add(newTime)
                    val sorted = times.sorted()
                    prefs.quietReleaseTimes = QuietRelease.formatTimes(sorted)
                    populateQuietReleaseTimes()
                    if (prefs.quietEnabled) {
                        ReleaseWorker.schedule(requireContext())
                    }
                }
            }
        } else if (which in times.indices) {
            val selectedTime = times[which]
            if (times.size == 1) {
                showTimePicker(
                    title = R.string.quiet_release_edit_time,
                    initialHour = selectedTime / 60,
                    initialMinute = selectedTime % 60,
                ) { hour, minute ->
                    times[which] = hour * 60 + minute
                    val sorted = times.distinct().sorted()
                    prefs.quietReleaseTimes = QuietRelease.formatTimes(sorted)
                    populateQuietReleaseTimes()
                    if (prefs.quietEnabled) {
                        ReleaseWorker.schedule(requireContext())
                    }
                }
            } else {
                showTimeOptionsDialog(times, which)
            }
        }
    }

    dialog?.dismiss()
    dialog = choiceDialog
    choiceDialog.showRespectingStatusBar()
}

internal fun SettingsFragment.showTimeOptionsDialog(times: MutableList<Int>, index: Int) {
    val selectedTime = times[index]
    val options = arrayOf(
        getString(R.string.quiet_release_edit_time),
        getString(R.string.quiet_release_remove_time),
    )
    val choiceDialog = requireContext().createChoiceDialog(
        titleText = QuietRelease.formatTime(selectedTime),
        items = options.toList(),
    ) { whichOption ->
        when (whichOption) {
            0 -> {
                showTimePicker(
                    title = R.string.quiet_release_edit_time,
                    initialHour = selectedTime / 60,
                    initialMinute = selectedTime % 60,
                ) { hour, minute ->
                    times[index] = hour * 60 + minute
                    val sorted = times.distinct().sorted()
                    prefs.quietReleaseTimes = QuietRelease.formatTimes(sorted)
                    populateQuietReleaseTimes()
                    if (prefs.quietEnabled) {
                        ReleaseWorker.schedule(requireContext())
                    }
                }
            }
            1 -> {
                times.removeAt(index)
                val sorted = times.distinct().sorted()
                prefs.quietReleaseTimes = QuietRelease.formatTimes(sorted)
                populateQuietReleaseTimes()
                if (prefs.quietEnabled) {
                    ReleaseWorker.schedule(requireContext())
                }
            }
        }
    }

    dialog?.dismiss()
    dialog = choiceDialog
    choiceDialog.showRespectingStatusBar()
}

internal fun SettingsFragment.showHoldNotificationsAccessDialog() {
    dialog?.dismiss()
    dialog = requireContext().showMessageDialog(
        title = R.string.hold_notifications_from_distracting_apps,
        message = R.string.hold_notifications_access_message,
        action = R.string.allow,
    ) {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: Exception) {
            requireContext().showToast(getString(R.string.unable_to_open_app))
        }
    }
}

internal fun SettingsFragment.showDistractingAppsDialog() {
    viewLifecycleOwner.lifecycleScope.launch {
        val apps = getAppsList(requireContext(), prefs, includeRegularApps = true, includeHiddenApps = true)
            .filterIsInstance<AppModel.App>()
            .distinctBy { it.appPackage }
            .sortedWith(compareBy(Collator.getInstance()) { it.appLabel })

        if (!isAdded) return@launch

        val installedPackages = apps.map { it.appPackage }.toSet()
        val selectedPackages = DistractingApps.initialSelection(
            installedPackages = installedPackages,
            saved = prefs.distractingApps,
            hasSaved = prefs.hasSavedDistractingApps()
        ).toMutableSet()

        val labels = apps.map { it.appLabel }.toTypedArray()
        val checked = BooleanArray(apps.size) { i -> apps[i].appPackage in selectedPackages }

        val distractingDialog = requireContext().createMultiChoiceDialog(
            title = R.string.distracting_apps,
            items = labels.toList(),
            checked = checked,
            action = R.string.okay,
            onToggle = { which, isChecked ->
                val pkg = apps[which].appPackage
                if (isChecked) {
                    selectedPackages.add(pkg)
                } else {
                    selectedPackages.remove(pkg)
                }
                true
            },
        ) {
            val change = FrictionChange.DistractingApps(
                current = prefs.distractingApps,
                updated = selectedPackages,
            )
            if (CommitLock.needsWait(prefs.commitLock, change)) {
                dialog?.dismiss()
                val appContext = requireContext().applicationContext
                dialog = requireContext().showWaitDialog(
                    message = getString(R.string.commit_lock_desc)
                ) {
                    prefs.distractingApps = selectedPackages
                    UsageSnapshotWorker.syncWithDistractingApps(appContext, selectedPackages)
                    if (_binding != null) populateDistractingApps()
                }
            } else {
                prefs.distractingApps = selectedPackages
                UsageSnapshotWorker.syncWithDistractingApps(requireContext().applicationContext, selectedPackages)
                populateDistractingApps()
            }
        }

        dialog?.dismiss()
        dialog = distractingDialog
        distractingDialog.showRespectingStatusBar()
    }
}
