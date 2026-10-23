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

internal fun SettingsFragment.toggleGerman() {
    prefs.germanEnabled = !prefs.germanEnabled
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
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (_: Exception) {
                requireContext().showToast(getString(R.string.unable_to_open_app))
            }
        }
    }
}

internal fun SettingsFragment.toggleGermanSpellSuggest() {
    prefs.germanSpellSuggest = !prefs.germanSpellSuggest
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

    binding.germanLookupLayout.visibility = if (prefs.germanEnabled) View.VISIBLE else View.GONE
    binding.germanLookupOnOff.text = getString(if (prefs.germanLookup) R.string.on else R.string.off)
    binding.germanSpellSuggestLayout.visibility = if (prefs.germanEnabled) View.VISIBLE else View.GONE
    binding.germanSpellSuggestOnOff.text = getString(if (prefs.germanSpellSuggest) R.string.on else R.string.off)
    binding.germanScreenPickLayout.visibility = if (prefs.germanEnabled) View.VISIBLE else View.GONE
    binding.germanScreenPickOnOff.text = getString(if (prefs.germanScreenPick) R.string.on else R.string.off)
    binding.germanPauseWordLayout.visibility = if (prefs.germanEnabled) View.VISIBLE else View.GONE
    binding.germanPauseWordOnOff.text = getString(if (prefs.germanPauseWord) R.string.on else R.string.off)
    binding.germanUiLayout.visibility = if (prefs.germanEnabled) View.VISIBLE else View.GONE
    binding.germanUiOnOff.text = getString(if (prefs.germanUi) R.string.on else R.string.off)
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
