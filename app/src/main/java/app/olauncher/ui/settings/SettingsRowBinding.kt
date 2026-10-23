package app.olauncher.ui.settings

import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import app.olauncher.R
import app.olauncher.ui.SettingsFragment
import java.util.Locale

/**
 * Views that react to a long press, by id. Every other row view only reacts to a tap.
 */
private val LONG_CLICK_IDS = setOf(
    R.id.photoWallpaper, R.id.photoWallpaperTitle, R.id.photoWallpaperLayout,
    R.id.alignment,
    R.id.weather, R.id.weatherTitle, R.id.weatherLayout,
    R.id.appThemeText,
    R.id.swipeLeftApp, R.id.swipeRightApp, R.id.swipeRightLayout,
    R.id.toggleLock,
)

internal fun SettingsFragment.setupRowsList() {
    val adapter = SettingsRowsAdapter(this)
    rowsAdapter = adapter
    binding.settingsRows.apply {
        layoutManager = LinearLayoutManager(requireContext())
        this.adapter = adapter
        itemAnimator = null
    }
}

/** Called when the adapter shows [spec]: remember its views, wire taps, fill in its value. */
internal fun SettingsFragment.bindRow(spec: SettingsRowSpec, holder: SettingsRowHolder) {
    binding.register(spec, holder)
    val onTap = View.OnClickListener { tapped ->
        // A title or row background without a handler of its own acts like its value.
        val handled = dispatchClick(tapped)
        if (!handled && tapped.id != spec.primaryId) binding.shownView(spec.primaryId)?.performClick()
    }
    for (view in holder.tappableViews()) {
        view.setOnClickListener(onTap)
        view.setOnLongClickListener(if (view.id in LONG_CLICK_IDS) this else null)
    }
    populateRow(spec)
    populateAllRows()
}

internal fun SettingsFragment.unbindRow(spec: SettingsRowSpec, holder: SettingsRowHolder) {
    binding.unregister(spec, holder)
}

/** Writes the current pref state into [spec]'s view when it is a plain on/off row. */
internal fun SettingsFragment.populateRow(spec: SettingsRowSpec) {
    val pref = spec.pref ?: return
    populateBooleanRow(spec, pref)
}

/**
 * Writes every row's value and wires every slider. Runs each time a row binds, so a row shows
 * its value and works from the first frame. Rows that are not on screen write to stand-in
 * views, which is harmless.
 */
internal fun SettingsFragment.populateAllRows() {
    populateHomeSets()
    populateProfileSchedule()
    populateProfileSwitch()
    populatePhotoWallpaperText()
    populateKeyboardText()
    populateAutoBackup()
    populateSearchContacts()
    populateCurrencyRates()
    populateScreenTimeOnOff()
    populateScreenTimeCue()
    populateDistractingApps()
    populatePauseBeforeApps()
    populatePauseAllowance()
    populatePauseEscalate()
    populatePauseAskWhy()
    populateCommitLock()
    populateWindDown()
    populateWindDownGrey()
    populateSystemGrey()
    populateHoldNotifications()
    populateHealthConnect()
    populateQuietReleaseTimes()
    populateLockSettings()
    populateAppThemeText()
    populateAccent()
    populateFontFamily()
    populateTextSize()
    populateBoldFont()
    setupHapticSliders()
    populateTextContrast()
    populateHomeDarkness()
    populateTextBrightness()
    populateMusicPulse()
    setupGlowStrength()
    populateWeatherEffects()
    populateAlignment()
    populateShortcutIcons()
    populateStatusBar()
    populateDateTime()
    populateBatteryMode()
    populateInfoLine()
    populateContextLine()
    populateHomeNextEvent()
    populateHomeNotes()
    populateSearchEngine()
    populateSwipeApps()
    populateTodayCards()
    populateTodayOrder()
    populateWeather()
    populateNudgeWater()
    populateNudgeWalk()
    populateNudgeSocial()
    populateActiveHours()
    populateGerman()
    binding.homeAppsNum.text = String.format(Locale.getDefault(), "%d", prefs.homeAppsNum)
}

/** A plain on/off row: the value reads On or Off from its pref. */
internal fun SettingsFragment.populateBooleanRow(spec: SettingsRowSpec, pref: BooleanSettingPref) {
    binding.textView(spec.valueId).text = getString(if (pref.get(prefs)) R.string.on else R.string.off)
}

/** Flips the pref of a plain on/off row and shows the new state. */
internal fun SettingsFragment.toggleBooleanRow(spec: SettingsRowSpec) {
    val pref = spec.pref ?: return
    pref.set(prefs, !pref.get(prefs))
    populateBooleanRow(spec, pref)
}
