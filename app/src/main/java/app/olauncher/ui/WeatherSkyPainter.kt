package app.olauncher.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import app.olauncher.helper.SunRays
import app.olauncher.helper.WeatherLookColors
import app.olauncher.helper.WeatherSim
import kotlin.math.roundToInt

/**
 * Renders sky layers (sun glow and rays, stars, clouds, haze) behind the glass.
 * Context-free and allocation-free in the steady state.
 * Ported from docs/design/home-weather-mock.html (<drawing>).
 */
class WeatherSkyPainter {

    private val unitRect = RectF(-1f, -1f, 1f, 1f)

    // Sun glow
    private val sunGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        applyAdditiveBlend(this)
    }

    // Sun rays
    private class PreparedRay(
        val rect: RectF,
        val startDeg: Float,
        val sweepDeg: Float,
        val paint: Paint,
    )

    private val preparedRays = ArrayList<PreparedRay>(SunRays.RAY_COUNT * 2)
    private var cachedRayLen: Float = -1f
    private var cachedRayStrength: Float = -1f

    // Stars
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = WeatherLookColors.STAR
    }

    // Clouds
    private val cloudLayer0Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val cloudLayer1Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private var cachedCloudColor: Int = 0

    // Fog / Haze
    private val hazeVeilPaint = Paint().apply {
        style = Paint.Style.FILL
        color = WeatherLookColors.FOG
    }
    private val hazeEllipsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        val fog = WeatherLookColors.FOG
        val fr = Color.red(fog)
        val fg = Color.green(fog)
        val fb = Color.blue(fog)
        shader = RadialGradient(
            0f, 0f, 1f,
            intArrayOf(Color.argb(255, fr, fg, fb), Color.argb(0, fr, fg, fb)),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    companion object {
        fun applyAdditiveBlend(paint: Paint) {
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                paint.blendMode = android.graphics.BlendMode.PLUS
            }
        }
    }

    private fun prepareRays(len: Float, strength: Float) {
        if (len == cachedRayLen && strength == cachedRayStrength && preparedRays.isNotEmpty()) {
            return
        }
        cachedRayLen = len
        cachedRayStrength = strength
        preparedRays.clear()

        val sunColor = WeatherLookColors.SUN
        val r = Color.red(sunColor)
        val g = Color.green(sunColor)
        val b = Color.blue(sunColor)

        for (i in 0 until SunRays.RAY_COUNT) {
            val ray = SunRays.rays[i]
            val rayLen = len * ray.lengthFactor
            val rect = RectF(-rayLen, -rayLen, rayLen, rayLen)
            for (pass in SunRays.PASSES) {
                val startDeg = Math.toDegrees((ray.angle - pass.halfAngle).toDouble()).toFloat()
                val sweepDeg = Math.toDegrees((2f * pass.halfAngle).toDouble()).toFloat()
                val peak = 0.10f * strength * ray.brightness * pass.alpha
                val p0 = (peak * 255f).roundToInt().coerceIn(0, 255)
                val p1 = (peak * 0.7f * 255f).roundToInt().coerceIn(0, 255)

                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    applyAdditiveBlend(this)
                    shader = RadialGradient(
                        0f, 0f, rayLen,
                        intArrayOf(Color.argb(p0, r, g, b), Color.argb(p1, r, g, b), Color.argb(0, r, g, b)),
                        floatArrayOf(0f, 0.35f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                }
                preparedRays.add(PreparedRay(rect, startDeg, sweepDeg, paint))
            }
        }
    }

    private fun updateCloudPaints(cloudColor: Int) {
        if (cachedCloudColor == cloudColor) return
        cachedCloudColor = cloudColor

        val cr = Color.red(cloudColor)
        val cg = Color.green(cloudColor)
        val cb = Color.blue(cloudColor)

        val a0 = (0.30f * 255f).roundToInt()
        val a06 = (0.30f * 0.6f * 255f).roundToInt()
        cloudLayer0Paint.shader = RadialGradient(
            0f, 0f, 1f,
            intArrayOf(Color.argb(a0, cr, cg, cb), Color.argb(a06, cr, cg, cb), Color.argb(0, cr, cg, cb)),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP,
        )

        val a1 = (0.42f * 255f).roundToInt()
        val a16 = (0.42f * 0.6f * 255f).roundToInt()
        cloudLayer1Paint.shader = RadialGradient(
            0f, 0f, 1f,
            intArrayOf(Color.argb(a1, cr, cg, cb), Color.argb(a16, cr, cg, cb), Color.argb(0, cr, cg, cb)),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    fun drawSun(canvas: Canvas, sim: WeatherSim, width: Float, height: Float) {
        val s = sim.params.sun
        if (s <= 0f || width <= 0f || height <= 0f) return

        val cx = width * 0.86f
        val cy = height * 0.05f
        val pulse = 0.9f + 0.1f * Math.sin((sim.sunPhase * 5f).toDouble()).toFloat()

        val sunColor = WeatherLookColors.SUN
        val r = Color.red(sunColor)
        val g = Color.green(sunColor)
        val b = Color.blue(sunColor)

        val a0 = (0.6f * s * pulse * 255f).roundToInt().coerceIn(0, 255)
        val a1 = (0.28f * s * 255f).roundToInt().coerceIn(0, 255)

        sunGlowPaint.shader = RadialGradient(
            cx, cy, width * 1.1f,
            intArrayOf(Color.argb(a0, r, g, b), Color.argb(a1, r, g, b), Color.argb(0, r, g, b)),
            floatArrayOf(0f, 0.18f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width, height, sunGlowPaint)

        drawSunRays(canvas, cx, cy, width * 1.2f, sim.sunPhase, s)
    }

    fun drawSunRays(canvas: Canvas, cx: Float, cy: Float, len: Float, turn: Float, strength: Float) {
        if (strength <= 0f || len <= 0f) return
        prepareRays(len, strength)

        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(Math.toDegrees(turn.toDouble()).toFloat())
        for (i in 0 until preparedRays.size) {
            val r = preparedRays[i]
            canvas.drawArc(r.rect, r.startDeg, r.sweepDeg, true, r.paint)
        }
        canvas.restore()
    }

    fun drawStars(canvas: Canvas, sim: WeatherSim) {
        val stars = sim.stars
        if (stars.isEmpty()) return

        for (i in 0 until stars.size) {
            val s = stars[i]
            val twinkle = 0.5f + 0.5f * Math.sin(s.phase.toDouble()).toFloat()
            val a = 0.3f + 0.65f * twinkle * twinkle
            starPaint.alpha = (a * 255f).roundToInt().coerceIn(0, 255)
            canvas.drawCircle(s.x, s.y, s.r, starPaint)
        }
    }

    fun drawClouds(canvas: Canvas, sim: WeatherSim) {
        val clouds = sim.clouds
        if (clouds.isEmpty()) return

        val color = WeatherLookColors.cloudColor(sim.look)
        updateCloudPaints(color)

        for (i in 0 until clouds.size) {
            val c = clouds[i]
            val paint = if (c.layer == 0) cloudLayer0Paint else cloudLayer1Paint
            for (j in 0 until c.puffs.size) {
                val p = c.puffs[j]
                canvas.save()
                canvas.translate(c.x + p.dx, c.y + p.dy)
                canvas.scale(p.r, p.r)
                canvas.drawRect(unitRect, paint)
                canvas.restore()
            }
        }
    }

    fun drawHaze(canvas: Canvas, sim: WeatherSim, width: Float, height: Float) {
        if (sim.params.fog > 0f) {
            hazeVeilPaint.alpha = (0.16f * sim.params.fog * 255f).roundToInt().coerceIn(0, 255)
            canvas.drawRect(0f, 0f, width, height, hazeVeilPaint)
        }

        val haze = sim.haze
        if (haze.isNotEmpty()) {
            for (i in 0 until haze.size) {
                val h = haze[i]
                canvas.save()
                canvas.translate(h.x, h.y)
                canvas.scale(h.rx, h.ry)
                hazeEllipsePaint.alpha = (h.alpha * 255f).roundToInt().coerceIn(0, 255)
                canvas.drawRect(unitRect, hazeEllipsePaint)
                canvas.restore()
            }
        }
    }
}
