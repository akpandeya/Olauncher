package app.olauncher.ui

import android.content.SharedPreferences
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import app.olauncher.MainActivity
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.helper.HomeMetaLine
import app.olauncher.glance.WeatherCache
import app.olauncher.glance.WeatherFormatting
import app.olauncher.helper.WeatherEffect
import app.olauncher.helper.WeatherEffects
import app.olauncher.helper.WeatherLooks
import app.olauncher.helper.WeatherSimRect
import androidx.lifecycle.lifecycleScope
import app.olauncher.glance.Forecast
import app.olauncher.helper.WeatherJsonCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private var weatherMemory: WeatherJsonCache<Pair<Forecast, Long>>? = null

/** In-memory forecast; the disk is read only by [refreshWeatherMemory]. */
private fun cachedForecast(): Pair<Forecast, Long>? = weatherMemory?.peek()

/** Reads the cache file off the main thread when WeatherWorker has written a newer one, then repaints. */
internal fun HomeFragment.refreshWeatherMemory() {
    val appContext = context?.applicationContext ?: return
    val memory = weatherMemory
        ?: WeatherJsonCache { WeatherCache(appContext).read() }.also { weatherMemory = it }
    val fetchedAt = prefs.weatherFetchedAt
    if (memory.isCurrent(fetchedAt)) return
    viewLifecycleOwner.lifecycleScope.launch {
        val before = memory.peek()
        val after = withContext(Dispatchers.IO) { memory.get(fetchedAt) }
        if (after != before && _binding != null) {
            if (prefs.homeInfoCombined) populateDateTime()
            populateWeather()
            applyWeatherEffects()
        }
    }
}

internal fun HomeFragment.combinedWeatherText(): String? {
    if (!prefs.weatherEnabled) return null
    val cached = cachedForecast() ?: return null
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
                    refreshWeatherMemory()
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
    val forecast = cachedForecast()?.first
    val code = if (prefs.homeWeatherEffects) forecast?.nowCode else null
    val isDay = forecast?.isDay ?: true
    val look = if (code != null) WeatherLooks.forForecast(code, isDay) else null
    val view = binding.weatherEffects
    view.isVisible = look != null
    view.setWeather(look = look, code = code, isDay = isDay)
    // With system animations off the view stays visible and draws one still frame.
    view.setRunning(WeatherEffects.shouldAnimate(homeVisible, look, MainActivity.animatorsEnabledSupplier()))

    fun updateBandsAndKeepOut() {
        if (_binding == null) return
        val clock = binding.dateTimeLayout
        val apps = binding.homeAppsLayout
        view.setTextBands(clock.top, clock.bottom, apps.top, apps.bottom)

        val rects = mutableListOf<WeatherSimRect>()
        fun addRect(v: android.view.View) {
            if (!v.isVisible || v.width <= 0 || v.height <= 0) return
            val locV = IntArray(2)
            val locW = IntArray(2)
            v.getLocationInWindow(locV)
            view.getLocationInWindow(locW)
            rects.add(
                WeatherSimRect(
                    x = (locV[0] - locW[0]).toFloat(),
                    y = (locV[1] - locW[1]).toFloat(),
                    w = v.width.toFloat(),
                    h = v.height.toFloat(),
                )
            )
        }

        if (clock.isVisible) {
            addRect(clock)
        }
        if (apps.isVisible) {
            var addedAnyChild = false
            for (i in 0 until apps.childCount) {
                val child = apps.getChildAt(i)
                if (child.isVisible && child.width > 0 && child.height > 0) {
                    addRect(child)
                    addedAnyChild = true
                }
            }
            if (!addedAnyChild) {
                addRect(apps)
            }
        }
        val shortcuts = binding.shortcutsLayout
        if (shortcuts.isVisible) {
            addRect(shortcuts)
        }
        view.setKeepOutRects(rects)
    }

    if (view.width > 0 && view.height > 0) {
        updateBandsAndKeepOut()
    }
    view.post {
        updateBandsAndKeepOut()
    }
}

internal fun HomeFragment.populateWeather() {
    val binding = _binding ?: return
    if (prefs.homeInfoCombined || !prefs.weatherEnabled || prefs.dateTimeVisibility == Constants.DateTime.OFF) {
        binding.weather.isVisible = false
        binding.weatherSeparator.isVisible = false
        return
    }
    val cached = cachedForecast()
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
