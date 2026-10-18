package app.olauncher.ui.today.cards

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import app.olauncher.MainViewModel
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.CardWeatherBinding
import app.olauncher.glance.Forecast
import app.olauncher.glance.RainOutlook
import app.olauncher.glance.RainOutlookLabels
import app.olauncher.glance.WeatherCache
import app.olauncher.glance.WeatherCodes
import app.olauncher.glance.WeatherLocation
import app.olauncher.helper.getUserHandleFromString
import app.olauncher.ui.today.TodayCard
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

object WeatherCardLogic {

    fun isAvailable(weatherEnabled: Boolean, hasCache: Boolean): Boolean {
        return weatherEnabled && hasCache
    }

    fun formatLine1(
        nowTemp: Double,
        nowCode: Int,
        isDay: Boolean,
        todayMax: Double,
        todayMin: Double,
    ): String {
        val temp = nowTemp.roundToInt()
        val condition = WeatherCodes.text(nowCode, isDay)
        val max = todayMax.roundToInt()
        val min = todayMin.roundToInt()
        return "$temp° $condition · $max° / $min°"
    }

    fun formatLine1(forecast: Forecast): String = formatLine1(
        nowTemp = forecast.nowTemp,
        nowCode = forecast.nowCode,
        isDay = forecast.isDay,
        todayMax = forecast.todayMax,
        todayMin = forecast.todayMin,
    )

    fun formatLine3(
        placeName: String,
        fetchedAt: Long,
        asOfTemplate: String,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): String {
        val timeStr = if (fetchedAt > 0L) {
            Instant.ofEpochMilli(fetchedAt)
                .atZone(zoneId)
                .format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))
        } else {
            "--:--"
        }
        val prefix = placeName.trim().takeIf { it.isNotEmpty() }?.let { "$it · " } ?: ""
        return prefix + asOfTemplate.format(timeStr)
    }
}

object WeatherCard : TodayCard {

    const val KEY = "weather"

    override val key: String = KEY
    override val titleRes: Int = R.string.weather

    var cacheProvider: ((Context) -> WeatherCache)? = null
    var nowSupplier: () -> LocalDateTime = { LocalDateTime.now() }
    var appOpener: ((Fragment) -> Unit)? = null

    fun getCache(context: Context): WeatherCache {
        return cacheProvider?.invoke(context) ?: WeatherCache(context)
    }

    override fun isAvailable(context: Context): Boolean {
        val prefs = Prefs(context)
        val hasCache = getCache(context).read() != null
        return WeatherCardLogic.isAvailable(prefs.weatherEnabled, hasCache)
    }

    override fun createView(inflater: LayoutInflater, parent: ViewGroup, fragment: Fragment): View {
        val binding = CardWeatherBinding.inflate(inflater, parent, false)
        val cardView = WeatherCardView(binding) {
            openWeatherApp(fragment)
        }
        binding.root.tag = cardView
        cardView.render()
        return binding.root
    }

    override fun onResume(view: View) {
        (view.tag as? WeatherCardView)?.render()
    }

    fun openWeatherApp(fragment: Fragment) {
        appOpener?.invoke(fragment) ?: run {
            val context = fragment.context ?: return
            val prefs = Prefs(context)
            val activity = fragment.activity ?: return
            val viewModel = try {
                ViewModelProvider(activity)[MainViewModel::class.java]
            } catch (e: Exception) {
                null
            }

            if (prefs.weatherAppPackage.isBlank()) {
                viewModel?.getAppList(false)
                try {
                    fragment.findNavController().navigate(
                        R.id.action_todayFragment_to_appListFragment,
                        bundleOf(
                            Constants.Key.FLAG to Constants.FLAG_SET_WEATHER_APP,
                            Constants.Key.RENAME to false,
                        )
                    )
                } catch (e: Exception) {
                    fragment.findNavController().navigate(
                        R.id.appListFragment,
                        bundleOf(
                            Constants.Key.FLAG to Constants.FLAG_SET_WEATHER_APP,
                            Constants.Key.RENAME to false,
                        )
                    )
                }
            } else {
                viewModel?.selectedApp(
                    AppModel.App(
                        appLabel = context.getString(R.string.weather),
                        key = null,
                        appPackage = prefs.weatherAppPackage,
                        activityClassName = prefs.weatherAppClassName,
                        isNew = false,
                        user = getUserHandleFromString(context, prefs.weatherAppUser),
                    ),
                    Constants.FLAG_LAUNCH_APP
                )
            }
        }
    }
}

internal class WeatherCardView(
    val binding: CardWeatherBinding,
    private val onOpenWeather: () -> Unit = {},
) {
    init {
        binding.root.setOnClickListener { onOpenWeather() }
    }

    fun render() {
        val context = binding.root.context ?: return
        val prefs = Prefs(context)
        val cache = WeatherCard.getCache(context)
        val cached = cache.read() ?: return
        val (forecast, fetchedAt) = cached

        binding.tvLine1.text = WeatherCardLogic.formatLine1(forecast)
        val now = WeatherCard.nowSupplier()
        binding.tvLine2.text = RainOutlook.summary(
            forecast.hours,
            RainOutlookLabels(
                dryNext12h = context.getString(R.string.weather_dry_12h),
                rainAllDay = context.getString(R.string.weather_rain_all_day),
                rainUntil = context.getString(R.string.weather_rain_until),
                rainFrom = context.getString(R.string.weather_rain_from),
            ),
            now,
        )
        binding.tvLine3.text = WeatherCardLogic.formatLine3(
            placeName = WeatherLocation.placeName(context),
            fetchedAt = fetchedAt,
            asOfTemplate = context.getString(R.string.weather_as_of),
        )
    }
}
