package app.olauncher.ui.today.cards

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.view.View
import androidx.core.view.isVisible
import app.olauncher.R
import app.olauncher.databinding.CardWeatherBinding
import app.olauncher.glance.WeatherCardReadability
import kotlin.math.roundToInt

/**
 * Pure bounding box representing a keep-out zone on the weather card.
 * Context-free, safe for JVM tests.
 */
data class KeepOutRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = maxOf(0f, right - left)
    val height: Float get() = maxOf(0f, bottom - top)

    /** Expands this rect outward by [padding] on all four sides. */
    fun pad(padding: Float): KeepOutRect = KeepOutRect(
        left = left - padding,
        top = top - padding,
        right = right + padding,
        bottom = bottom + padding,
    )
}

/**
 * Helper that collects keep-out rects for card text views and provides soft DST_OUT cut-out.
 *
 * Source of truth: docs/design/today-weather.html lines 1110–1134 and 1295–1305.
 */
object WeatherKeepOut {

    const val PAD: Float = WeatherCardReadability.KEEP_OUT_PAD.toFloat()
    const val SIGMA: Float = WeatherCardReadability.KEEP_OUT_SIGMA.toFloat()
    const val PEAK_ALPHA: Float = WeatherCardReadability.KEEP_OUT_PEAK.toFloat()

    /** Creates a [KeepOutRect] from top-left origin, width and height. Pure Kotlin. */
    fun fromBounds(left: Float, top: Float, width: Float, height: Float, pad: Float = 0f): KeepOutRect {
        val rect = KeepOutRect(left, top, left + width, top + height)
        return if (pad != 0f) rect.pad(pad) else rect
    }

    /** Pads a collection of [KeepOutRect]s by [pad]. Pure Kotlin. */
    fun padRects(rects: List<KeepOutRect>, pad: Float = PAD): List<KeepOutRect> =
        rects.map { it.pad(pad) }

    /**
     * Collects all text views on the card that need keep-out protection:
     * temperature, condition, rain line, place line, and hourly labels and temps.
     */
    fun collectKeepOutViews(binding: CardWeatherBinding): List<View> {
        val views = ArrayList<View>()
        if (binding.tvTemp.isVisible) views.add(binding.tvTemp)
        if (binding.tvLine1.isVisible) views.add(binding.tvLine1)
        if (binding.tvLine2.isVisible) views.add(binding.tvLine2)
        if (binding.tvLine3.isVisible) views.add(binding.tvLine3)

        val row = binding.hourlyRow
        if (row.isVisible) {
            for (i in 0 until row.childCount) {
                val child = row.getChildAt(i)
                val label = child.findViewById<View>(R.id.tvHourLabel)
                val temp = child.findViewById<View>(R.id.tvHourTemp)
                if (label != null && label.isVisible) views.add(label)
                if (temp != null && temp.isVisible) views.add(temp)
            }
        }
        return views
    }

    /**
     * Measures the on-card rects of [views] relative to [cardRoot], padded by [pad].
     */
    fun collectRects(
        cardRoot: View,
        views: List<View>,
        pad: Float = PAD,
    ): List<KeepOutRect> {
        if (views.isEmpty()) return emptyList()
        val rootLoc = IntArray(2)
        cardRoot.getLocationInWindow(rootLoc)

        val viewLoc = IntArray(2)
        val rects = ArrayList<KeepOutRect>(views.size)
        for (view in views) {
            if (!view.isShown || view.width <= 0 || view.height <= 0) continue
            view.getLocationInWindow(viewLoc)
            val l = (viewLoc[0] - rootLoc[0]).toFloat()
            val t = (viewLoc[1] - rootLoc[1]).toFloat()
            val raw = KeepOutRect(l, t, l + view.width.toFloat(), t + view.height.toFloat())
            rects.add(if (pad != 0f) raw.pad(pad) else raw)
        }
        return rects
    }

    /**
     * Collects on-card rects of all weather text views in [binding], padded by [pad].
     */
    fun collectRects(binding: CardWeatherBinding, pad: Float = PAD): List<KeepOutRect> =
        collectRects(binding.root, collectKeepOutViews(binding), pad)

    /**
     * Creates a Paint configured for PorterDuff.Mode.DST_OUT with BlurMaskFilter and peak alpha.
     */
    fun createKeepOutPaint(
        sigma: Float = SIGMA,
        peakAlpha: Float = PEAK_ALPHA,
    ): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        val alphaInt = (peakAlpha.coerceIn(0f, 1f) * 255f).roundToInt()
        color = Color.argb(alphaInt, 0, 0, 0)
        if (sigma > 0f) {
            maskFilter = BlurMaskFilter(sigma, BlurMaskFilter.Blur.NORMAL)
        }
    }

    /**
     * Cuts a soft hole into the canvas destination layer for each of [rects].
     */
    fun applyKeepOut(
        canvas: Canvas,
        rects: List<KeepOutRect>,
        paint: Paint = createKeepOutPaint(),
        cornerRadius: Float = PAD,
    ) {
        if (rects.isEmpty()) return
        for (r in rects) {
            canvas.drawRoundRect(r.left, r.top, r.right, r.bottom, cornerRadius, cornerRadius, paint)
        }
    }
}
