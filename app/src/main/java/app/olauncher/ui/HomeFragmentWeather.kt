package app.olauncher.ui

import android.content.SharedPreferences
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.helper.HomeMetaLine
import app.olauncher.glance.WeatherCache
import app.olauncher.glance.WeatherFormatting
import app.olauncher.helper.WeatherEffect
import app.olauncher.helper.WeatherEffects
import java.util.Locale

internal fun HomeFragment.combinedWeatherText(): String? {
    val context = context ?: return null
    if (!prefs.weatherEnabled) return null
    val cached = WeatherCache(context).read() ?: return null
    return WeatherFormatting.homeLine(cached.first, cached.second)
}

/** Puts the weather under the date when both do not fit on one row. */
internal fun HomeFragment.layoutDateWeatherRow() {
    val binding = _binding ?: return
    fun widthOf(view: TextView): Float {
        if (!view.isVisible) return 0f
        val text = view.text?.toString().orEmpty().uppercase(Locale.getDefault())
        return view.paint.measureText(text) + view.paddingStart + view.paddingEnd
    }
    val stacked = HomeMetaLine.shouldStack(
        dateWidthPx = widthOf(binding.date),
        separatorWidthPx = binding.weatherSeparator.paint.measureText("·") +
            binding.weatherSeparator.paddingStart + binding.weatherSeparator.paddingEnd,
        weatherWidthPx = widthOf(binding.weather),
        weatherShown = binding.weather.isVisible && binding.date.isVisible,
        availableWidthPx = topTextAvailableWidthPx(),
    )
    binding.dateWeatherRow.orientation = if (stacked) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
    binding.weatherSeparator.isVisible = HomeMetaLine.showSeparator(
        binding.date.isVisible, binding.weather.isVisible, stacked,
    )
}

internal fun HomeFragment.initWeatherListener() {
    weatherPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Prefs.WEATHER_FETCHED_AT || key == Prefs.WEATHER_ENABLED) {
            activity?.runOnUiThread {
                if (_binding != null) {
                    if (prefs.homeInfoCombined) populateDateTime()
                    populateWeather()
                    applyWeatherEffects()
                }
            }
        }
    }
    prefs.registerPreferenceChangeListener(weatherPrefsListener!!)
}

/** Sets the weather layer from the cached forecast. Hidden unless Weather effects is on and a forecast is cached. */
internal fun HomeFragment.applyWeatherEffects() {
    val binding = _binding ?: return
    val context = context ?: return
    val forecast = WeatherCache(context).read()?.first
    val effect = WeatherEffects.select(prefs.homeWeatherEffects, forecast?.nowCode)
    binding.weatherEffects.setEffect(effect, WeatherEffects.tint(effect, forecast?.isDay ?: true))
    binding.weatherEffects.isVisible = effect != WeatherEffect.NONE
    binding.weatherEffects.setRunning(homeVisible && effect != WeatherEffect.NONE)
}

internal fun HomeFragment.populateWeather() {
    val binding = _binding ?: return
    val context = context ?: return
    if (prefs.homeInfoCombined || !prefs.weatherEnabled || prefs.dateTimeVisibility == Constants.DateTime.OFF) {
        binding.weather.isVisible = false
        binding.weatherSeparator.isVisible = false
        return
    }
    val cached = WeatherCache(context).read()
    val line = if (cached != null) {
        WeatherFormatting.homeLine(cached.first, cached.second)
    } else {
        null
    }

    if (line != null) {
        binding.weather.text = line
        binding.weather.isVisible = true
    } else {
        binding.weather.isVisible = false
    }
    // Only show the dot between the two when both are visible side by side.
    layoutDateWeatherRow()
}
