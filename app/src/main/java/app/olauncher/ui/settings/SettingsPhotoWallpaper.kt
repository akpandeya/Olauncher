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

internal fun SettingsFragment.openPhotoPicker() {
    viewModel.isAwaitingActivityResult = true
    pickMultipleMedia.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    )
}

internal fun SettingsFragment.openPhotoPickerForAdd() {
    viewModel.isAwaitingActivityResult = true
    pickMultipleMediaForAdd.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    )
}

internal fun SettingsFragment.handlePickedPhotos(uris: List<Uri>) {
    if (uris.size == 1) {
        requireContext().showToast(getString(R.string.saving_photo))
    } else {
        requireContext().showToast(getString(R.string.saving_photos, uris.size))
    }
    // Use the activity scope and app context so leaving Settings mid-save does not cancel it
    val appContext = requireContext().applicationContext
    requireActivity().lifecycleScope.launch {
        PhotoWallpaper.withEditLock {
        val savedCount = PhotoWallpaper.savePickedPhotos(appContext, uris)
        if (savedCount > 0) {
            prefs.photoWallpaperCount = savedCount
            prefs.photoWallpaperIndex = 0
            PhotoWallpaper.markChanged(prefs)
            if (_binding != null) populatePhotoWallpaperText()

            WallpaperRotateWorker.schedulePeriodic(appContext)
            WallpaperRotateWorker.runOnce(appContext)
            withContext(Dispatchers.IO) {
                PhotoWallpaper.applyCurrent(appContext, prefs)
            }
        } else {
            appContext.showToast(R.string.wallpaper_photos_save_failed)
        }
        }
    }
}

internal fun SettingsFragment.handleAddedPhotos(uris: List<Uri>) {
    if (uris.size == 1) {
        requireContext().showToast(getString(R.string.saving_photo))
    } else {
        requireContext().showToast(getString(R.string.saving_photos, uris.size))
    }
    val appContext = requireContext().applicationContext
    requireActivity().lifecycleScope.launch {
        PhotoWallpaper.withEditLock {
            val saved = PhotoWallpaper.appendPickedPhotos(appContext, uris, startIndex = prefs.photoWallpaperCount)
            if (saved > 0) {
                prefs.photoWallpaperCount += saved
                if (_binding != null) populatePhotoWallpaperText()
            } else {
                appContext.showToast(R.string.wallpaper_photos_save_failed)
            }
        }
    }
}

internal fun SettingsFragment.populatePhotoWallpaperText() {
    if (_binding == null) return
    val count = prefs.photoWallpaperCount
    binding.photoWallpaper.text = when {
        count == 0 -> getString(R.string.off)
        count == 1 -> getString(R.string.wallpaper_photo_count, 1)
        else -> getString(R.string.wallpaper_photos_count, count)
    }
}

internal fun SettingsFragment.showPhotoWallpaperMenu(anchor: View) {
    if (PhotoSet.rowTapAction(prefs.photoWallpaperCount) == PhotoSet.RowTapAction.OPEN_PICKER) {
        openPhotoPicker()
        return
    }
    anchor.showPopupMenu(
        R.menu.photo_wallpaper,
        configure = { menu ->
            val targetTitle = when (prefs.photoWallpaperTarget) {
                PhotoWallpaper.TARGET_HOME -> R.string.set_on_home
                PhotoWallpaper.TARGET_LOCK -> R.string.set_on_lock
                PhotoWallpaper.TARGET_BOTH -> R.string.set_on_both
                else -> R.string.set_on_home
            }
            menu.findItem(R.id.photoWallpaperTarget).setTitle(targetTitle)
            val currentInterval = RotationInterval.fromId(prefs.photoWallpaperInterval)
            val frequencyLabel = getString(SettingsFragment.intervalToStringRes(currentInterval))
            menu.findItem(R.id.photoWallpaperFrequency).title =
                getString(R.string.photo_wallpaper_frequency, frequencyLabel)
            menu.findItem(R.id.photoWallpaperHomeMenu).isChecked = prefs.photoWallpaperHomeMenu
        }
    ) { item ->
        when (item.itemId) {
            R.id.photoWallpaperAdd -> openPhotoPickerForAdd()
            R.id.photoWallpaperRemove -> showRemovePhotosDialog()
            R.id.photoWallpaperChange -> openPhotoPicker()
            R.id.photoWallpaperTarget -> cyclePhotoWallpaperTarget()
            R.id.photoWallpaperFrequency -> showPhotoWallpaperFrequencyDialog()
            R.id.photoWallpaperNext -> nextPhotoNow()
            R.id.photoWallpaperPrevious -> previousPhotoNow()
            R.id.photoWallpaperHomeMenu -> togglePhotoWallpaperHomeMenu()
            R.id.photoWallpaperOff -> {
                val appContext = requireContext().applicationContext
                requireActivity().lifecycleScope.launch {
                    PhotoWallpaper.withEditLock { turnOffPhotoWallpaper(appContext) }
                }
            }
        }
    }
}

internal fun SettingsFragment.showPhotoWallpaperFrequencyDialog() {
    val intervals = RotationInterval.entries
    val items = intervals.map { getString(SettingsFragment.intervalToStringRes(it)) }.toTypedArray()
    val currentInterval = RotationInterval.fromId(prefs.photoWallpaperInterval)
    val checkedIndex = intervals.indexOf(currentInterval)

    val choiceDialog = requireContext().createChoiceDialog(
        items = items.toList(),
        checkedIndex = checkedIndex,
    ) { which ->
        val interval = intervals[which]
        prefs.photoWallpaperInterval = interval.id
        WallpaperRotateWorker.schedulePeriodic(requireContext().applicationContext)
    }

    dialog?.dismiss()
    dialog = choiceDialog
    choiceDialog.showRespectingStatusBar()
}

internal fun SettingsFragment.showRemovePhotosDialog() {
    val count = prefs.photoWallpaperCount
    if (count <= 0) return

    val currentIndex = prefs.photoWallpaperIndex.coerceIn(0, count - 1)
    val labels = Array(count) { i ->
        if (i == currentIndex) {
            getString(R.string.photo_label_current, i + 1)
        } else {
            getString(R.string.photo_label, i + 1)
        }
    }
    val checked = BooleanArray(count)

    val removeDialog = requireContext().createMultiChoiceDialog(
        title = R.string.remove_photos,
        items = labels.toList(),
        checked = checked,
        action = R.string.remove,
    ) {
        val checkedIndices = checked.indices.filter { checked[it] }.toSet()
        if (checkedIndices.isEmpty()) return@createMultiChoiceDialog

        val appContext = requireContext().applicationContext
        requireActivity().lifecycleScope.launch {
            PhotoWallpaper.withEditLock {
                // Plan under the lock so it matches the set as it is now.
                val currentCount = prefs.photoWallpaperCount
                if (currentCount <= 0 || checkedIndices.any { it >= currentCount }) return@withEditLock
                val plan = PhotoSet.planRemoval(
                    currentCount,
                    prefs.photoWallpaperIndex,
                    checkedIndices
                )
                if (plan.newCount == 0) {
                    turnOffPhotoWallpaper(appContext)
                    return@withEditLock
                }
                withContext(Dispatchers.IO) {
                    val success = PhotoWallpaper.removePhotos(appContext, plan)
                    if (success) {
                        prefs.photoWallpaperCount = plan.newCount
                        prefs.photoWallpaperIndex = plan.newIndex
                        if (plan.currentRemoved) {
                            PhotoWallpaper.applyCurrent(appContext, prefs)
                        }
                    } else {
                        val recoveredCount = PhotoSet.countContiguous(PhotoWallpaper.getWallpaperDir(appContext))
                        prefs.photoWallpaperCount = recoveredCount
                        if (recoveredCount == 0) {
                            withContext(Dispatchers.Main) {
                                turnOffPhotoWallpaper(appContext)
                            }
                        } else {
                            prefs.photoWallpaperIndex = prefs.photoWallpaperIndex.coerceIn(0, recoveredCount - 1)
                        }
                        withContext(Dispatchers.Main) {
                            appContext.showToast(R.string.wallpaper_photos_remove_failed)
                        }
                    }
                }
                if (_binding != null) {
                    populatePhotoWallpaperText()
                }
            }
        }
    }

    dialog?.dismiss()
    dialog = removeDialog
    removeDialog.showRespectingStatusBar()
}

internal fun SettingsFragment.cyclePhotoWallpaperTarget() {
    prefs.photoWallpaperTarget = when (prefs.photoWallpaperTarget) {
        PhotoWallpaper.TARGET_HOME -> PhotoWallpaper.TARGET_LOCK
        PhotoWallpaper.TARGET_LOCK -> PhotoWallpaper.TARGET_BOTH
        else -> PhotoWallpaper.TARGET_HOME
    }
    val targetRes = when (prefs.photoWallpaperTarget) {
        PhotoWallpaper.TARGET_HOME -> R.string.set_on_home
        PhotoWallpaper.TARGET_LOCK -> R.string.set_on_lock
        PhotoWallpaper.TARGET_BOTH -> R.string.set_on_both
        else -> R.string.set_on_home
    }
    requireContext().showToast(targetRes)
    viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
        PhotoWallpaper.applyCurrent(requireContext(), prefs)
    }
}

internal fun SettingsFragment.nextPhotoNow() {
    val appContext = requireContext().applicationContext
    viewLifecycleOwner.lifecycleScope.launch {
        PhotoWallpaper.stepManually(appContext, prefs, forward = true)
    }
}

internal fun SettingsFragment.previousPhotoNow() {
    val appContext = requireContext().applicationContext
    viewLifecycleOwner.lifecycleScope.launch {
        PhotoWallpaper.stepManually(appContext, prefs, forward = false)
    }
}

internal fun SettingsFragment.togglePhotoWallpaperHomeMenu() {
    prefs.photoWallpaperHomeMenu = !prefs.photoWallpaperHomeMenu
    showToast(if (prefs.photoWallpaperHomeMenu) R.string.on else R.string.off)
}

internal fun SettingsFragment.turnOffPhotoWallpaper(context: Context = requireContext()) {
    WallpaperRotateWorker.cancel(context)
    PhotoWallpaper.deleteAll(context)
    prefs.photoWallpaperCount = 0
    prefs.photoWallpaperIndex = 0
    setPlainWallpaperByTheme(context, prefs.appTheme)
    if (_binding != null) populatePhotoWallpaperText()
}
