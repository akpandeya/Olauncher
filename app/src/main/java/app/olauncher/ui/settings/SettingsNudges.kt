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
import androidx.appcompat.app.AlertDialog
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

internal fun SettingsFragment.showAppListIfEnabled(flag: Int) {
    // Choosing an app for a swipe gesture switches that gesture on.
    if (flag == Constants.FLAG_SET_SWIPE_LEFT_APP) prefs.swipeLeftEnabled = true
    if (flag == Constants.FLAG_SET_SWIPE_RIGHT_APP) prefs.swipeRightEnabled = true
    viewModel.getAppList(true)
    findNavController().navigate(
        R.id.action_settingsFragment_to_appListFragment,
        bundleOf(Constants.Key.FLAG to flag)
    )
}

internal fun SettingsFragment.populateNudgeWater() {
    binding.nudgeWater.text = formatWaterInterval(prefs.nudgeWaterIntervalMin)
}

internal fun SettingsFragment.populateNudgeWalk() {
    binding.nudgeWalk.text = formatWalkInterval(prefs.nudgeWalkIntervalMin)
}

internal fun SettingsFragment.populateActiveHours() {
    binding.nudgeActiveHours.text = NudgeFormatting.formatActiveHours(
        prefs.nudgeWindowStartMin,
        prefs.nudgeWindowEndMin,
    )
}

internal fun SettingsFragment.formatWaterInterval(intervalMin: Int): String = when (intervalMin) {
    60 -> getString(R.string.nudge_every_1_h)
    90 -> getString(R.string.nudge_every_1_5_h)
    120 -> getString(R.string.nudge_every_2_h)
    180 -> getString(R.string.nudge_every_3_h)
    else -> getString(R.string.off)
}

internal fun SettingsFragment.formatWalkInterval(intervalMin: Int): String = when (intervalMin) {
    120 -> getString(R.string.nudge_every_2_h)
    180 -> getString(R.string.nudge_every_3_h)
    240 -> getString(R.string.nudge_every_4_h)
    else -> getString(R.string.off)
}

internal fun SettingsFragment.showNudgeWaterMenu(anchor: View) {
    val intervals = listOf(
        0 to getString(R.string.off),
        60 to getString(R.string.nudge_every_1_h),
        90 to getString(R.string.nudge_every_1_5_h),
        120 to getString(R.string.nudge_every_2_h),
        180 to getString(R.string.nudge_every_3_h),
    )
    anchor.showPopupMenu(
        configure = { menu ->
            intervals.forEachIndexed { index, pair ->
                menu.add(Menu.NONE, index, index, pair.second)
            }
        }
    ) { item ->
        val chosen = intervals.getOrNull(item.itemId)?.first ?: return@showPopupMenu
        updateNudgeInterval(Nudges.WATER, chosen)
    }
}

internal fun SettingsFragment.showNudgeWalkMenu(anchor: View) {
    val intervals = listOf(
        0 to getString(R.string.off),
        120 to getString(R.string.nudge_every_2_h),
        180 to getString(R.string.nudge_every_3_h),
        240 to getString(R.string.nudge_every_4_h),
    )
    anchor.showPopupMenu(
        configure = { menu ->
            intervals.forEachIndexed { index, pair ->
                menu.add(Menu.NONE, index, index, pair.second)
            }
        }
    ) { item ->
        val chosen = intervals.getOrNull(item.itemId)?.first ?: return@showPopupMenu
        updateNudgeInterval(Nudges.WALK, chosen)
    }
}

internal fun SettingsFragment.updateNudgeInterval(type: String, intervalMin: Int) {
    if (intervalMin <= 0) {
        when (type) {
            Nudges.WATER -> {
                prefs.nudgeWaterIntervalMin = 0
                NudgeScheduler.cancel(requireContext(), Nudges.WATER)
                Nudges.cancel(requireContext(), Nudges.WATER)
                populateNudgeWater()
            }
            Nudges.WALK -> {
                prefs.nudgeWalkIntervalMin = 0
                NudgeScheduler.cancel(requireContext(), Nudges.WALK)
                Nudges.cancel(requireContext(), Nudges.WALK)
                populateNudgeWalk()
            }
        }
        return
    }

    NotificationPermission.request(this) { isGranted ->
        if (!isAdded) return@request
        if (isGranted) {
            when (type) {
                Nudges.WATER -> {
                    prefs.nudgeWaterIntervalMin = intervalMin
                    NudgeScheduler.schedule(requireContext(), Nudges.WATER)
                    populateNudgeWater()
                }
                Nudges.WALK -> {
                    prefs.nudgeWalkIntervalMin = intervalMin
                    NudgeScheduler.schedule(requireContext(), Nudges.WALK)
                    populateNudgeWalk()
                }
            }
        } else {
            when (type) {
                Nudges.WATER -> {
                    prefs.nudgeWaterIntervalMin = 0
                    NudgeScheduler.cancel(requireContext(), Nudges.WATER)
                    Nudges.cancel(requireContext(), Nudges.WATER)
                    populateNudgeWater()
                }
                Nudges.WALK -> {
                    prefs.nudgeWalkIntervalMin = 0
                    NudgeScheduler.cancel(requireContext(), Nudges.WALK)
                    Nudges.cancel(requireContext(), Nudges.WALK)
                    populateNudgeWalk()
                }
            }
            requireContext().showToast(R.string.notifications_off_for_thumbline)
        }
    }
}

internal fun SettingsFragment.populateNudgeSocial() {
    binding.nudgeSocial.text = formatSocialThreshold(prefs.nudgeSocialThresholdMin)
}

internal fun SettingsFragment.formatSocialThreshold(thresholdMin: Int): String = when (thresholdMin) {
    15, 30, 45, 60, 90 -> getString(R.string.nudge_social_after_min, thresholdMin)
    else -> getString(R.string.off)
}

internal fun SettingsFragment.showNudgeSocialMenu(anchor: View) {
    val thresholds = listOf(0, 15, 30, 45, 60, 90)
    val items = thresholds.map { min ->
        min to if (min == 0) getString(R.string.off) else getString(R.string.nudge_social_after_min, min)
    }
    anchor.showPopupMenu(
        configure = { menu ->
            items.forEachIndexed { index, pair ->
                menu.add(Menu.NONE, index, index, pair.second)
            }
        }
    ) { item ->
        val chosen = items.getOrNull(item.itemId)?.first ?: return@showPopupMenu
        updateSocialNudgeThreshold(chosen)
    }
}

internal fun SettingsFragment.updateSocialNudgeThreshold(thresholdMin: Int) {
    if (thresholdMin <= 0) {
        prefs.nudgeSocialThresholdMin = 0
        prefs.nudgeSocialLastBucket = -1
        NudgeScheduler.cancel(requireContext(), Nudges.SOCIAL)
        Nudges.cancel(requireContext(), Nudges.SOCIAL)
        populateNudgeSocial()
        return
    }

    NotificationPermission.request(this) { isGranted ->
        if (!isAdded) return@request
        if (!isGranted) {
            prefs.nudgeSocialThresholdMin = 0
            prefs.nudgeSocialLastBucket = -1
            NudgeScheduler.cancel(requireContext(), Nudges.SOCIAL)
            Nudges.cancel(requireContext(), Nudges.SOCIAL)
            populateNudgeSocial()
            requireContext().showToast(R.string.notifications_off_for_thumbline)
            return@request
        }

        if (!requireContext().appUsagePermissionGranted()) {
            prefs.nudgeSocialThresholdMin = 0
            prefs.nudgeSocialLastBucket = -1
            NudgeScheduler.cancel(requireContext(), Nudges.SOCIAL)
            Nudges.cancel(requireContext(), Nudges.SOCIAL)
            populateNudgeSocial()
            requireContext().showToast(R.string.usage_access_off_for_thumbline)
            showSocialNudgeUsageAccessDialog()
            return@request
        }

        prefs.nudgeSocialThresholdMin = thresholdMin
        prefs.nudgeSocialLastBucket = -1
        NudgeScheduler.scheduleSocial(requireContext())
        populateNudgeSocial()
    }
}

internal fun SettingsFragment.showSocialNudgeUsageAccessDialog() {
    requireContext().showMessageDialog(
        R.string.nudge_social,
        R.string.nudge_social_usage_access_message,
        R.string.allow,
    ) {
        try {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } catch (_: Exception) {
            requireContext().showToast(getString(R.string.unable_to_open_app))
        }
    }
}

internal fun SettingsFragment.showActiveHoursDialog() {
    val currentStart = prefs.nudgeWindowStartMin
    val currentEnd = prefs.nudgeWindowEndMin
    showTimePicker(
        title = R.string.nudge_start_time,
        initialHour = currentStart / 60,
        initialMinute = currentStart % 60,
    ) { startHour, startMinute ->
        val chosenStart = startHour * 60 + startMinute
        showTimePicker(
            title = R.string.nudge_end_time,
            initialHour = currentEnd / 60,
            initialMinute = currentEnd % 60,
        ) { endHour, endMinute ->
            val chosenEnd = endHour * 60 + endMinute
            if (NudgeFormatting.isValidActiveHours(chosenStart, chosenEnd)) {
                prefs.nudgeWindowStartMin = chosenStart
                prefs.nudgeWindowEndMin = chosenEnd
                populateActiveHours()
                NudgeScheduler.rescheduleAll(requireContext())
            } else {
                requireContext().showToast(R.string.nudge_active_hours_invalid)
            }
        }
    }
}

internal fun SettingsFragment.showTimePicker(
    @StringRes title: Int,
    initialHour: Int,
    initialMinute: Int,
    onTimeSet: (Int, Int) -> Unit,
) {
    val picker = TimePickerDialog(
        requireContext(),
        { _, hour, minute -> onTimeSet(hour, minute) },
        initialHour,
        initialMinute,
        DateFormat.is24HourFormat(requireContext()),
    )
    picker.setTitle(getString(title))
    val window = picker.window
    if (window != null && !prefs.showStatusBar) {
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        picker.show()
        window.hideStatusBar()
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    } else {
        picker.show()
    }
}
