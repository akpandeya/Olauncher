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

internal fun SettingsFragment.showSearchEngineMenu(anchor: View) {
    val options = listOf(
        SearchEngine.PHONE_DEFAULT to getString(R.string.search_engine_phone_default),
        SearchEngine.GOOGLE to getString(R.string.search_engine_google),
        SearchEngine.DUCKDUCKGO to getString(R.string.search_engine_duckduckgo),
        SearchEngine.BING to getString(R.string.search_engine_bing),
        SearchEngine.CUSTOM to getString(R.string.search_engine_custom),
    )
    anchor.showPopupMenu(
        configure = { menu ->
            options.forEachIndexed { index, (_, label) ->
                menu.add(Menu.NONE, index, index, label)
            }
        }
    ) { item ->
        val (engineId, _) = options.getOrNull(item.itemId) ?: return@showPopupMenu
        if (engineId == SearchEngine.CUSTOM) {
            showCustomSearchEngineDialog()
        } else {
            prefs.searchEngine = engineId
            populateSearchEngine()
        }
    }
}

internal fun SettingsFragment.populateSearchEngine() {
    binding.searchEngine.text = getString(
        when (prefs.searchEngine) {
            SearchEngine.GOOGLE -> R.string.search_engine_google
            SearchEngine.DUCKDUCKGO -> R.string.search_engine_duckduckgo
            SearchEngine.BING -> R.string.search_engine_bing
            SearchEngine.CUSTOM -> R.string.search_engine_custom
            else -> R.string.search_engine_phone_default
        }
    )
}

internal fun SettingsFragment.showCustomSearchEngineDialog() {
    var dialogEditText: AppCompatEditText? = null
    var customDialog: OlDialog? = null

    fun save() {
        val text = dialogEditText?.text?.toString().orEmpty()
        if (SearchEngine.isValidTemplate(text)) {
            prefs.searchEngineCustomUrl = text.trim()
            prefs.searchEngine = SearchEngine.CUSTOM
            populateSearchEngine()
        } else {
            showToast(R.string.search_engine_custom_invalid)
        }
    }

    val dialog = requireContext().createDialog(
        title = R.string.search_engine,
        action = R.string.save,
        onAction = {
            save()
        }
    ) { container ->
        val context = container.context
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        val editText = AppCompatEditText(context).apply {
            hint = context.getString(R.string.search_engine_custom_hint)
            setText(prefs.searchEngineCustomUrl)
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setTextColor(context.getColorFromAttr(R.attr.primaryColor))
            setHintTextColor(context.getColorFromAttr(R.attr.primaryColorTrans80))
            background = null
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    customDialog?.dismiss()
                    save()
                    true
                } else false
            }
        }
        dialogEditText = editText
        layout.addView(editText)
        layout
    }
    customDialog = dialog
    dialog.showRespectingStatusBar()
    dialogEditText?.apply {
        setSelection(text?.length ?: 0)
        post {
            requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }
}

internal fun SettingsFragment.showToast(@StringRes resId: Int) {
    requireContext().showToast(resId)
}

internal fun SettingsFragment.populateWeather() {
    binding.weather.text = if (prefs.weatherEnabled) {
        val place = WeatherLocation.placeName(requireContext())
        if (place.isNotBlank()) place else getString(R.string.off)
    } else {
        getString(R.string.off)
    }
}

internal fun SettingsFragment.handleUseMyLocation() {
    if (WeatherLocation.hasPermission(requireContext())) {
        enableUseMyLocation()
    } else {
        requireContext().showMessageDialog(
            title = R.string.weather,
            message = R.string.weather_location_permission_message,
            action = R.string.allow,
        ) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
}

internal fun SettingsFragment.enableUseMyLocation() {
    prefs.weatherUseLocation = true
    prefs.weatherEnabled = true
    WeatherWorker.schedulePeriodic(requireContext())
    WeatherLocation.refreshIfStale(requireContext(), force = true)
    populateWeather()
}

internal fun SettingsFragment.showWeatherMenu(anchor: View) {
    anchor.showPopupMenu(
        configure = { menu ->
            menu.add(Menu.NONE, 1, 1, getString(R.string.celsius))
            menu.add(Menu.NONE, 2, 2, getString(R.string.fahrenheit))
            if (prefs.weatherEnabled) {
                menu.add(Menu.NONE, 3, 3, getString(R.string.off))
            }
        }
    ) { item ->
        when (item.itemId) {
            1 -> {
                if (prefs.weatherUnit != Prefs.WEATHER_UNIT_C) {
                    prefs.weatherUnit = Prefs.WEATHER_UNIT_C
                    if (prefs.weatherEnabled) {
                        WeatherWorker.runOnce(requireContext())
                    }
                }
            }
            2 -> {
                if (prefs.weatherUnit != Prefs.WEATHER_UNIT_F) {
                    prefs.weatherUnit = Prefs.WEATHER_UNIT_F
                    if (prefs.weatherEnabled) {
                        WeatherWorker.runOnce(requireContext())
                    }
                }
            }
            3 -> {
                prefs.weatherEnabled = false
                WeatherWorker.cancel(requireContext())
                populateWeather()
            }
        }
    }
}

internal fun SettingsFragment.showCitySearchDialog() {
    var dialogEditText: AppCompatEditText? = null
    var cityDialog: OlDialog? = null
    val dialog = requireContext().createDialog(
        title = R.string.weather,
        action = R.string.search,
        neutral = R.string.use_my_location,
        onNeutral = {
            handleUseMyLocation()
        },
        onAction = {
            val query = dialogEditText?.text?.toString()?.trim().orEmpty()
            if (query.isNotEmpty()) {
                searchCity(query)
            }
        }
    ) { container ->
        val context = container.context
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        val editText = AppCompatEditText(context).apply {
            hint = context.getString(R.string.city)
            setText(if (prefs.weatherEnabled) prefs.weatherPlaceName else "")
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setTextColor(context.getColorFromAttr(R.attr.primaryColor))
            setHintTextColor(context.getColorFromAttr(R.attr.primaryColorTrans80))
            background = null
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                    val q = text?.toString()?.trim().orEmpty()
                    if (q.isNotEmpty()) {
                        cityDialog?.dismiss()
                        searchCity(q)
                    }
                    true
                } else false
            }
        }
        dialogEditText = editText
        layout.addView(editText)
        layout
    }
    cityDialog = dialog
    dialog.showRespectingStatusBar()
    dialogEditText?.apply {
        setSelection(text?.length ?: 0)
        post {
            requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }
}

internal fun SettingsFragment.searchCity(query: String) {
    viewLifecycleOwner.lifecycleScope.launch {
        val places = withContext(Dispatchers.IO) {
            WeatherApi.searchPlaces(query)
        }
        if (!isAdded) return@launch
        if (places.isEmpty()) {
            requireContext().showToast(getString(R.string.weather_no_results))
            return@launch
        }
        showPlaceSelectionDialog(places)
    }
}

internal fun SettingsFragment.showPlaceSelectionDialog(places: List<Place>) {
    val items = places.map { it.displayName() }.toTypedArray()
    val checkedIndex = 0

    val builder = AlertDialog.Builder(requireContext())
        .setTitle(R.string.weather)
        .setSingleChoiceItems(items, checkedIndex) { dialogInterface, which ->
            val place = places[which]
            prefs.weatherPlaceName = place.name
            prefs.weatherLat = String.format(Locale.US, "%.2f", place.lat)
            prefs.weatherLon = String.format(Locale.US, "%.2f", place.lon)
            prefs.weatherUseLocation = false
            prefs.weatherEnabled = true
            WeatherWorker.schedulePeriodic(requireContext())
            WeatherWorker.runOnce(requireContext())
            populateWeather()
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
