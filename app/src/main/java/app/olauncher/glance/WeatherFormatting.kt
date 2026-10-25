package app.olauncher.glance

import kotlin.math.roundToInt

data class ChartPoint(
    val x: Float,
    val y: Float,
)

data class RainBar(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val isVisible: Boolean get() = height > 0f
}

data class CurveSegment(
    val start: ChartPoint,
    val cp1: ChartPoint,
    val cp2: ChartPoint,
    val end: ChartPoint,
)

data class WeatherChartGeometry(
    val points: List<ChartPoint>,
    val bars: List<RainBar>,
    val segments: List<CurveSegment> = emptyList(),
) {
    companion object {
        const val DEFAULT_SLOT_COUNT = 6
        const val FULL_RAIN_PROBABILITY = 30

        fun compute(
            hours: List<Hour>,
            width: Float,
            height: Float,
            slotCount: Int = DEFAULT_SLOT_COUNT,
            curveTop: Float = 4f,
            curveBottom: Float = 18f,
            barBottom: Float = height - 2f,
            maxBarHeight: Float = 10f,
            barWidth: Float = 4f,
        ): WeatherChartGeometry {
            if (hours.isEmpty() || width <= 0f || height <= 0f || slotCount <= 0) {
                return WeatherChartGeometry(emptyList(), emptyList(), emptyList())
            }

            val count = minOf(hours.size, slotCount)
            val slotWidth = width / slotCount.toFloat()

            // Calculate rain bars
            val bars = ArrayList<RainBar>(count)
            for (i in 0 until count) {
                val hour = hours[i]
                val centerX = (i + 0.5f) * slotWidth
                val rainFraction = (hour.precipProb.toFloat() / FULL_RAIN_PROBABILITY.toFloat()).coerceIn(0f, 1f)
                val barHeight = maxBarHeight * rainFraction
                val top = barBottom - barHeight
                val left = centerX - barWidth / 2f
                val right = centerX + barWidth / 2f
                bars.add(RainBar(left = left, top = top, right = right, bottom = barBottom))
            }

            // Calculate temperature points
            val temps = hours.take(count).map { it.temp }
            val minTemp = temps.minOrNull() ?: 0.0
            val maxTemp = temps.maxOrNull() ?: 0.0
            val range = maxTemp - minTemp

            val points = ArrayList<ChartPoint>(count)
            val centerY = (curveTop + curveBottom) / 2f

            for (i in 0 until count) {
                val centerX = (i + 0.5f) * slotWidth
                val y = if (range <= 0.0 || range.isNaN()) {
                    centerY
                } else {
                    val fraction = ((temps[i] - minTemp) / range).toFloat()
                    curveBottom - fraction * (curveBottom - curveTop)
                }
                points.add(ChartPoint(centerX, y))
            }

            val segments = computeCurveSegments(points, curveTop, curveBottom)

            return WeatherChartGeometry(points = points, bars = bars, segments = segments)
        }

        fun computeCurveSegments(
            points: List<ChartPoint>,
            minY: Float = Float.NEGATIVE_INFINITY,
            maxY: Float = Float.POSITIVE_INFINITY,
        ): List<CurveSegment> {
            if (points.size < 2) return emptyList()

            val segments = ArrayList<CurveSegment>(points.size - 1)
            val topBound = minOf(minY, maxY)
            val bottomBound = maxOf(minY, maxY)

            for (i in 0 until points.size - 1) {
                val p0 = points[maxOf(i - 1, 0)]
                val p1 = points[i]
                val p2 = points[i + 1]
                val p3 = points[minOf(i + 2, points.size - 1)]

                val cp1X = p1.x + (p2.x - p0.x) / 6f
                var cp1Y = p1.y + (p2.y - p0.y) / 6f
                val cp2X = p2.x - (p3.x - p1.x) / 6f
                var cp2Y = p2.y - (p3.y - p1.y) / 6f

                if (minY.isFinite() && maxY.isFinite()) {
                    cp1Y = cp1Y.coerceIn(topBound, bottomBound)
                    cp2Y = cp2Y.coerceIn(topBound, bottomBound)
                }

                segments.add(
                    CurveSegment(
                        start = p1,
                        cp1 = ChartPoint(cp1X, cp1Y),
                        cp2 = ChartPoint(cp2X, cp2Y),
                        end = p2,
                    )
                )
            }
            return segments
        }
    }
}

object WeatherFormatting {

    const val MAX_AGE_MILLIS = 6 * 60 * 60 * 1000L

    @JvmOverloads
    fun homeLine(
        forecast: Forecast?,
        fetchedAt: Long,
        now: Long = System.currentTimeMillis(),
    ): String? {
        if (forecast == null || fetchedAt <= 0L) return null
        if (forecast.nowTemp.isNaN()) return null
        if (now - fetchedAt > MAX_AGE_MILLIS) return null
        val temp = forecast.nowTemp.roundToInt()
        return "$temp°"
    }

    fun chartGeometry(
        hours: List<Hour>,
        width: Float,
        height: Float,
        slotCount: Int = WeatherChartGeometry.DEFAULT_SLOT_COUNT,
        curveTop: Float = 4f,
        curveBottom: Float = 18f,
        barBottom: Float = height - 2f,
        maxBarHeight: Float = 10f,
        barWidth: Float = 4f,
    ): WeatherChartGeometry = WeatherChartGeometry.compute(
        hours = hours,
        width = width,
        height = height,
        slotCount = slotCount,
        curveTop = curveTop,
        curveBottom = curveBottom,
        barBottom = barBottom,
        maxBarHeight = maxBarHeight,
        barWidth = barWidth,
    )
}
