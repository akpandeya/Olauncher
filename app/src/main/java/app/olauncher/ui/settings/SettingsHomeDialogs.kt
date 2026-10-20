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

internal fun SettingsFragment.showHomeAppsNumMenu(anchor: View) {
    anchor.showPopupMenu(
        configure = { menu ->
            for (num in 0..8) menu.add(Menu.NONE, num, num, num.toString())
        }
    ) { item -> updateHomeAppsNum(item.itemId) }
}

internal fun SettingsFragment.showDateTimeMenu(anchor: View) {
    anchor.showPopupMenu(R.menu.date_time) { item ->
        when (item.itemId) {
            R.id.dateTimeOn -> toggleDateTime(Constants.DateTime.ON)
            R.id.dateTimeOff -> toggleDateTime(Constants.DateTime.OFF)
            R.id.dateOnly -> toggleDateTime(Constants.DateTime.DATE_ONLY)
        }
    }
}

internal fun SettingsFragment.showAlignmentMenu(anchor: View) {
    anchor.showPopupMenu(
        R.menu.alignment,
        configure = { menu ->
            val leftLabel = getString(R.string.left)
            val centerLabel = getString(R.string.center)
            val rightLabel = getString(R.string.right)
            menu.findItem(R.id.alignmentLeft)?.title = SpannableString(leftLabel).apply {
                setSpan(
                    AlignmentSpan.Standard(Layout.Alignment.ALIGN_NORMAL),
                    0,
                    leftLabel.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            menu.findItem(R.id.alignmentCenter)?.title = SpannableString(centerLabel).apply {
                setSpan(
                    AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER),
                    0,
                    centerLabel.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            menu.findItem(R.id.alignmentRight)?.title = SpannableString(rightLabel).apply {
                setSpan(
                    AlignmentSpan.Standard(Layout.Alignment.ALIGN_OPPOSITE),
                    0,
                    rightLabel.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            menu.findItem(R.id.alignmentBottom)?.setTitle(
                if (prefs.homeBottomAlignment) R.string.bottom_on else R.string.bottom_off
            )
        }
    ) { item ->
        when (item.itemId) {
            R.id.alignmentLeft -> viewModel.updateHomeAlignment(Gravity.START)
            R.id.alignmentCenter -> viewModel.updateHomeAlignment(Gravity.CENTER)
            R.id.alignmentRight -> viewModel.updateHomeAlignment(Gravity.END)
            R.id.alignmentBottom -> updateHomeBottomAlignment()
        }
    }
}

internal fun SettingsFragment.showShortcutCountDialog() {
    val counts = (SlotCount.MIN..SlotCount.MAX).toList()
    val items = counts.map { it.toString() }.toTypedArray()
    val checkedIndex = counts.indexOf(prefs.shortcutCount)

    val builder = AlertDialog.Builder(requireContext())
        .setTitle(R.string.icons)
        .setSingleChoiceItems(items, checkedIndex) { dialogInterface, which ->
            prefs.shortcutCount = counts[which]
            populateShortcutIcons()
            viewModel.updateHomeAlignment(prefs.homeAlignment)
            dialogInterface.dismiss()
        }
        .setNegativeButton(R.string.cancel, null)

    val alertDialog = builder.create()
    dialog?.dismiss()
    dialog = alertDialog
    val window = alertDialog.window
    if (window != null && !prefs.showStatusBar) {
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        alertDialog.show()
        window.hideStatusBar()
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    } else {
        alertDialog.show()
    }
}

internal fun SettingsFragment.showShortcutSizeDialog() {
    val sizes = listOf(
        ShortcutSize.SMALL,
        ShortcutSize.MEDIUM,
        ShortcutSize.LARGE,
    )
    val items = arrayOf(
        getString(R.string.icon_size_small),
        getString(R.string.icon_size_medium),
        getString(R.string.icon_size_large),
    )
    val checkedIndex = sizes.indexOf(prefs.shortcutSizeStep).coerceAtLeast(0)

    val builder = AlertDialog.Builder(requireContext())
        .setTitle(R.string.icon_size)
        .setSingleChoiceItems(items, checkedIndex) { dialogInterface, which ->
            prefs.shortcutSizeStep = sizes[which]
            populateShortcutIcons()
            viewModel.updateHomeAlignment(prefs.homeAlignment)
            dialogInterface.dismiss()
        }
        .setNegativeButton(R.string.cancel, null)

    val alertDialog = builder.create()
    dialog?.dismiss()
    dialog = alertDialog
    val window = alertDialog.window
    if (window != null && !prefs.showStatusBar) {
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        alertDialog.show()
        window.hideStatusBar()
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    } else {
        alertDialog.show()
    }
}

internal fun SettingsFragment.showShortcutIconStyleDialog() {
    val styles = listOf(
        ShortcutIconStyle.ORIGINAL,
        ShortcutIconStyle.MONOCHROME,
        ShortcutIconStyle.TEXT_COLOUR,
    )
    val items = arrayOf(
        getString(R.string.icon_style_original),
        getString(R.string.icon_style_monochrome),
        getString(R.string.icon_style_text_colour),
    )
    val checkedIndex = styles.indexOf(prefs.shortcutIconStyle).coerceAtLeast(0)

    val builder = AlertDialog.Builder(requireContext())
        .setTitle(R.string.icon_style)
        .setSingleChoiceItems(items, checkedIndex) { dialogInterface, which ->
            prefs.shortcutIconStyle = styles[which]
            populateShortcutIcons()
            viewModel.updateHomeAlignment(prefs.homeAlignment)
            dialogInterface.dismiss()
        }
        .setNegativeButton(R.string.cancel, null)

    val alertDialog = builder.create()
    dialog?.dismiss()
    dialog = alertDialog
    val window = alertDialog.window
    if (window != null && !prefs.showStatusBar) {
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        alertDialog.show()
        window.hideStatusBar()
        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
    } else {
        alertDialog.show()
    }
}

// "System" stays hidden unless the row is long pressed
internal fun SettingsFragment.showAppThemeMenu(anchor: View, showSystem: Boolean) {
    anchor.showPopupMenu(
        R.menu.app_theme,
        configure = { menu -> menu.findItem(R.id.themeSystem).isVisible = showSystem }
    ) { item ->
        when (item.itemId) {
            R.id.themeLight -> updateTheme(AppCompatDelegate.MODE_NIGHT_NO)
            R.id.themeDark -> updateTheme(AppCompatDelegate.MODE_NIGHT_YES)
            R.id.themeSystem -> updateTheme(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
}

// Dialogs

internal fun SettingsFragment.showDialog(newDialog: OlDialog) {
    dialog?.dismiss()
    dialog = newDialog
    newDialog.showRespectingStatusBar()
}

internal fun SettingsFragment.showTextSizeDialog() {
    var applied = false
    val initialScale = prefs.textSizeScale
    pendingTextSizeScale = initialScale
    var stepper: DialogTextSizeBinding? = null
    val dialog = requireContext().createDialog(
        title = R.string.text_size,
        action = R.string.apply,
        neutral = R.string.cancel,
        onAction = {
            applied = true
            applyTextSizeScale()
        },
        onNeutral = {
            // Cancelled
        }
    ) { container ->
        DialogTextSizeBinding.inflate(layoutInflater, container, false).also { stepper = it }.root
    }
    stepper?.apply {
        textSizeSample.isVisible = true
        updateTextSizeSample(textSizeSample, pendingTextSizeScale)
        textSizeCurrent.text = formatScale(pendingTextSizeScale)
        textSizeMinus.setOnClickListener { adjustTextSizePreview(-0.1f, this) }
        textSizePlus.setOnClickListener { adjustTextSizePreview(0.1f, this) }
    }
    dialog.setOnDismissListener {
        if (!applied) {
            pendingTextSizeScale = -1f
            populateTextSize()
        }
    }
    showDialog(dialog)
}

internal fun SettingsFragment.showShortcutHeightDialog() {
    pendingShortcutOffsetStep = prefs.shortcutOffsetStep
    var stepper: DialogTextSizeBinding? = null
    val dialog = requireContext().createDialog(R.string.icons_height, R.string.okay) { container ->
        DialogTextSizeBinding.inflate(layoutInflater, container, false).also { stepper = it }.root
    }
    stepper?.apply {
        textSizeSample.isVisible = false
        textSizeCurrent.text = ShortcutOffset.format(pendingOrCurrentShortcutOffsetStep())
        textSizeMinus.setOnClickListener { adjustShortcutHeightPreview(-1, this) }
        textSizePlus.setOnClickListener { adjustShortcutHeightPreview(1, this) }
    }
    dialog.setOnDismissListener { applyShortcutOffsetStep() }
    showDialog(dialog)
}

// Prominent disclosure before sending the user to accessibility settings
internal fun SettingsFragment.showAccessibilityDialog() {
    val serviceEnabled = isAccessServiceEnabled(requireContext())
    showDialog(
        requireContext().createDialog(
            title = R.string.gestures,
            action = if (serviceEnabled) R.string.disable else R.string.enable,
            message = R.string.accessibility_disclosure,
            neutral = R.string.not_working,
            onNeutral = { requireContext().openUrl(Constants.URL_DOUBLE_TAP) },
            onAction = { openAccessibilityService() },
        )
    )
}

