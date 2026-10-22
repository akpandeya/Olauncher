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

private const val TAG = "SettingsBackup"

internal fun SettingsFragment.launchExportBackup() {
    viewModel.isAwaitingActivityResult = true
    exportBackupLauncher.launch(Backup.defaultBackupFileName())
}

internal fun SettingsFragment.exportBackup(uri: Uri) {
    val appContext = context?.applicationContext ?: return
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
        val success = try {
            Backup.export(appContext, uri)
            true
        } catch (e: Exception) {
            Log.w(TAG, "exportBackup failed", e)
            false
        }
        withContext(Dispatchers.Main) {
            appContext.showToast(if (success) R.string.backup_exported else R.string.backup_export_failed)
        }
    }
}

internal fun SettingsFragment.populateAutoBackup() {
    binding.autoBackup.text = getString(if (prefs.autoBackupEnabled) R.string.on else R.string.off)
    val status = AutoBackupStatus.of(prefs.autoBackupEnabled, prefs.autoBackupLastOk, prefs.autoBackupLastFailed)
    binding.autoBackupStatus.isVisible = status != AutoBackupStatus.HIDDEN
    binding.autoBackupStatus.text = when (status) {
        AutoBackupStatus.FAILED -> getString(R.string.nightly_backup_failed)
        AutoBackupStatus.LAST_OK -> getString(
            R.string.nightly_backup_last,
            java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT)
                .format(java.util.Date(prefs.autoBackupLastOk))
        )
        else -> getString(R.string.nightly_backup_waiting)
    }
}

internal fun SettingsFragment.toggleAutoBackup() {
    if (prefs.autoBackupEnabled) {
        prefs.autoBackupEnabled = false
        AutoBackupWorker.cancel(requireContext().applicationContext)
        populateAutoBackup()
        return
    }
    dialog?.dismiss()
    dialog = requireContext().showMessageDialog(
        title = R.string.nightly_backup,
        message = R.string.nightly_backup_message,
        action = R.string.allow,
    ) {
        launchAutoBackupFolderPicker()
    }
}

internal fun SettingsFragment.launchAutoBackupFolderPicker() {
    viewModel.isAwaitingActivityResult = true
    autoBackupFolderLauncher.launch(null)
}

internal fun SettingsFragment.enableAutoBackup(uri: Uri) {
    val appContext = requireContext().applicationContext
    try {
        appContext.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
    } catch (e: Exception) {
        Log.w(TAG, "enableAutoBackup failed", e)
        return
    }
    prefs.autoBackupTreeUri = uri.toString()
    prefs.autoBackupLastFailed = false
    prefs.autoBackupEnabled = true
    AutoBackupWorker.schedulePeriodic(appContext)
    AutoBackupWorker.runOnce(appContext)
    populateAutoBackup()
}

internal fun SettingsFragment.launchImportBackup() {
    viewModel.isAwaitingActivityResult = true
    importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
}

internal fun SettingsFragment.handleImportBackup(uri: Uri) {
    val appContext = context?.applicationContext ?: return
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
        val backupData = try {
            Backup.readBackup(appContext, uri)
        } catch (e: Exception) {
            Log.w(TAG, "handleImportBackup failed", e)
            null
        }
        withContext(Dispatchers.Main) {
            if (!isAdded) return@withContext
            if (backupData == null) {
                appContext.showToast(R.string.not_a_thumbline_backup)
                return@withContext
            }
            showImportConfirmationDialog(backupData)
        }
    }
}

internal fun SettingsFragment.showImportConfirmationDialog(backupData: BackupData) {
    val message = if (backupData.appVersion.isNotBlank()) {
        getString(R.string.backup_restore_confirm, backupData.appVersion)
    } else {
        getString(R.string.backup_restore_confirm_no_version)
    }
    dialog?.dismiss()
    dialog = requireContext().showMessageDialog(
        title = R.string.import_backup,
        message = message,
        action = R.string.replace,
    ) {
        confirmImportBackup(backupData)
    }
}

internal fun SettingsFragment.confirmImportBackup(backupData: BackupData) {
    val before = FrictionSettings(
        pauseEnabled = prefs.pauseEnabled,
        distractingApps = prefs.distractingApps,
        allowanceMin = prefs.pauseAllowanceMin,
        escalate = prefs.pauseEscalate,
        askWhy = prefs.pauseAskWhy,
        commitLock = prefs.commitLock,
    )
    val after = FrictionSettings.fromPrefsMap(backupData.prefs)
    if (CommitLock.restoreNeedsWait(prefs.commitLock, before, after)) {
        dialog?.dismiss()
        dialog = requireContext().showWaitDialog(
            message = getString(R.string.commit_lock_desc)
        ) {
            restoreBackupNow(backupData)
        }
    } else {
        restoreBackupNow(backupData)
    }
}

internal fun SettingsFragment.restoreBackupNow(backupData: BackupData) {
    val appContext = context?.applicationContext ?: return
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
        try {
            Backup.restore(appContext, backupData)
        } catch (e: Exception) {
            Log.w(TAG, "restoreBackupNow failed", e)
            withContext(Dispatchers.Main) {
                if (isAdded) {
                    appContext.showToast(R.string.not_a_thumbline_backup)
                }
            }
            return@launch
        }
        withContext(Dispatchers.Main) {
            if (!isAdded) return@withContext
            WordCaptureActivity.setEnabled(
                appContext, prefs.germanEnabled, WordLookup.isLookupAllowed(prefs.germanEnabled, prefs.germanLookup)
            )
            WordScreenService.setEnabled(appContext, prefs.germanEnabled && prefs.germanScreenPick)
            NudgeScheduler.rescheduleAll(appContext)
            UsageSnapshotWorker.syncWithDistractingApps(appContext, prefs.distractingApps)
            activity?.recreate()
        }
    }
}
