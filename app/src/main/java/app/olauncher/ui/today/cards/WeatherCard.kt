package app.olauncher.ui.today.cards

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import app.olauncher.MainViewModel
import app.olauncher.helper.Haptics
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.databinding.CardWeatherBinding
import app.olauncher.databinding.ItemWeatherHourBinding
import app.olauncher.glance.Forecast
import app.olauncher.glance.HeroFormatting
import app.olauncher.glance.Hour
import app.olauncher.glance.HourlyStrip
import app.olauncher.glance.RainOutlook
import app.olauncher.glance.RainOutlookLabels
import app.olauncher.glance.WeatherCache
import app.olauncher.glance.WeatherChartGeometry
import app.olauncher.glance.WeatherCodes
import app.olauncher.glance.WeatherLocation
import app.olauncher.glance.WeatherWorker
import app.olauncher.helper.OpenTargets
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.getUserHandleFromString
import app.olauncher.ui.today.TodayCard
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

object WeatherCardLogic {

    /** The card shows whenever weather is on; without a forecast yet it says so instead of vanishing. */
    fun isAvailable(weatherEnabled: Boolean): Boolean = weatherEnabled

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
    override val inHero: Boolean = true

    var cacheProvider: ((Context) -> WeatherCache)? = null
    var nowSupplier: () -> LocalDateTime = { LocalDateTime.now() }
    var appOpener: ((Fragment) -> Unit)? = null

    fun getCache(context: Context): WeatherCache {
        return cacheProvider?.invoke(context) ?: WeatherCache(context)
    }

    override fun isAvailable(context: Context): Boolean {
        return WeatherCardLogic.isAvailable(Prefs(context).weatherEnabled)
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

            OpenTargets.openWeatherApp(
                context, prefs,
                pickApp = {
                    viewModel?.getAppList(false)
                    val args = bundleOf(
                        Constants.Key.FLAG to Constants.FLAG_SET_WEATHER_APP,
                        Constants.Key.RENAME to false,
                    )
                    try {
                        fragment.findNavController().navigate(R.id.action_todayFragment_to_appListFragment, args)
                    } catch (e: Exception) {
                        fragment.findNavController().navigate(R.id.appListFragment, args)
                    }
                },
                launch = { target ->
                    viewModel?.selectedApp(
                        AppModel.App(
                            appLabel = target.label,
                            key = null,
                            appPackage = target.pkg,
                            activityClassName = target.className,
                            isNew = false,
                            user = getUserHandleFromString(context, target.user),
                        ),
                        Constants.FLAG_LAUNCH_APP
                    )
                },
            )
        }
    }
}

internal class WeatherCardView(
    val binding: CardWeatherBinding,
    private val onOpenWeather: () -> Unit = {},
) {
    init {
        binding.root.setOnClickListener {
            Haptics.tick(it)
            // Without a forecast the tap tries again instead of opening the weather app.
            if (binding.tvUnavailable.isVisible) WeatherWorker.runOnce(it.context) else onOpenWeather()
        }
    }

    fun render() {
        val context = binding.root.context ?: return
        val prefs = Prefs(context)
        val cache = WeatherCard.getCache(context)
        val cached = cache.read()
        binding.llForecast.isVisible = cached != null
        binding.tvUnavailable.isVisible = cached == null
        if (cached == null) return
        val (forecast, fetchedAt) = cached

        val iconRes = HeroFormatting.nowIconRes(forecast)
        if (iconRes != null) {
            binding.ivIcon.setImageResource(iconRes)
            binding.ivIcon.isVisible = true
        } else {
            binding.ivIcon.setImageDrawable(null)
            binding.ivIcon.isVisible = false
        }
        binding.tvTemp.text = HeroFormatting.nowTemperature(forecast)
        binding.tvLine1.text = HeroFormatting.conditionLine(forecast)
        val now = forecast.cityNow(WeatherCard.nowSupplier())
        renderHours(forecast, now)
        val rainLine = RainOutlook.summary(
            forecast.hours,
            RainOutlookLabels(
                dryNext12h = context.getString(R.string.weather_dry_12h),
                rainAllDay = context.getString(R.string.weather_rain_all_day),
                rainUntil = context.getString(R.string.weather_rain_until),
                rainFrom = context.getString(R.string.weather_rain_from),
            ),
            now,
        )
        binding.tvLine2.text = rainLine ?: ""
        binding.tvLine2.visibility = if (rainLine == null) View.GONE else View.VISIBLE
        binding.tvLine3.text = WeatherCardLogic.formatLine3(
            placeName = WeatherLocation.placeName(context),
            fetchedAt = fetchedAt,
            asOfTemplate = context.getString(R.string.weather_as_of),
        )
    }

    private fun renderHours(forecast: Forecast, now: LocalDateTime) {
        val row = binding.hourlyRow
        row.removeAllViews()
        val inflater = LayoutInflater.from(row.context)
        val hours = HourlyStrip.next(forecast.hours, now)
        for (hour in hours) {
            val item = ItemWeatherHourBinding.inflate(inflater, row, false)
            item.tvHourLabel.text = HeroFormatting.hourLabel(hour)
            val iconRes = HeroFormatting.hourIconRes(hour)
            if (iconRes != null) {
                item.ivHourIcon.setImageResource(iconRes)
                item.ivHourIcon.isVisible = true
            } else {
                item.ivHourIcon.setImageDrawable(null)
                item.ivHourIcon.isVisible = false
            }
            item.tvHourTemp.text = HeroFormatting.hourTemperature(hour)
            row.addView(item.root)
        }
        row.isVisible = row.childCount > 0
        binding.weatherChart.setHours(hours)
        binding.weatherChart.isVisible = hours.isNotEmpty()
    }
}

/**
 * Draws a smooth temperature curve and rain bars for the hourly forecast strip.
 */
class WeatherChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density

    private val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val barRect = RectF()
    private val curvePath = Path()
    private var hours: List<Hour> = emptyList()

    fun setHours(hours: List<Hour>) {
        this.hours = hours
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val heightPx = (HEIGHT_DP * density).roundToInt()
        setMeasuredDimension(
            resolveSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(heightPx, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (hours.isEmpty() || width <= 0 || height <= 0) return

        val ink = context.getColorFromAttr(R.attr.primaryColor)
        val faint = context.getColorFromAttr(R.attr.primaryColorTrans50)

        curvePaint.color = ink
        barPaint.color = faint

        val geometry = WeatherChartGeometry.compute(
            hours = hours,
            width = width.toFloat(),
            height = height.toFloat(),
            slotCount = WeatherChartGeometry.DEFAULT_SLOT_COUNT,
            curveTop = 4f * density,
            curveBottom = 18f * density,
            barBottom = height.toFloat() - 2f * density,
            maxBarHeight = 11f * density,
            barWidth = 4f * density,
        )

        // Draw rain bars
        val radius = 2f * density
        for (bar in geometry.bars) {
            if (bar.height > 0f) {
                barRect.set(bar.left, bar.top, bar.right, bar.bottom)
                canvas.drawRoundRect(barRect, radius, radius, barPaint)
            }
        }

        // Draw temperature curve
        if (geometry.points.isNotEmpty()) {
            if (geometry.points.size == 1) {
                canvas.drawPoint(geometry.points[0].x, geometry.points[0].y, curvePaint)
            } else {
                curvePath.rewind()
                curvePath.moveTo(geometry.points[0].x, geometry.points[0].y)
                for (segment in geometry.segments) {
                    curvePath.cubicTo(
                        segment.cp1.x, segment.cp1.y,
                        segment.cp2.x, segment.cp2.y,
                        segment.end.x, segment.end.y,
                    )
                }
                canvas.drawPath(curvePath, curvePaint)
            }
        }
    }

    companion object {
        const val HEIGHT_DP = 36
    }
}
