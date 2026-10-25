package app.olauncher.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import app.olauncher.helper.BeadState
import app.olauncher.helper.EffectParams
import app.olauncher.helper.EffectSpec
import app.olauncher.helper.EffectTint
import app.olauncher.helper.Mulberry32
import app.olauncher.helper.RainDrop
import app.olauncher.helper.WeatherEffects
import app.olauncher.helper.WeatherLook
import app.olauncher.helper.WeatherLookColors
import app.olauncher.helper.WeatherLookParams
import app.olauncher.helper.WeatherLooks
import app.olauncher.helper.WeatherSim
import app.olauncher.helper.WeatherSimClock
import app.olauncher.helper.WeatherSimPoint
import app.olauncher.helper.WeatherSimRect
import java.util.Collections
import kotlin.math.roundToInt

/**
 * Full-screen weather animation drawn with plain Canvas over the wallpaper.
 * It only animates while [setRunning] is true (Home visible) and never takes touches.
 * Frames are at most [WeatherEffects.MAX_FPS] per second.
 */
class WeatherEffectView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    var sim: WeatherSim? = null
        private set

    var simClock: WeatherSimClock? = null
        private set

    private var currentLook: WeatherLook? = null
    private var currentCode: Int? = null
    private var currentIsDay: Boolean = true
    private var currentParams: WeatherLookParams? = null
    private var keepOutRects: List<WeatherSimRect> = emptyList()

    // Normalised y ranges (top1, bottom1, top2, bottom2) where glass drops are faded out for text.
    private var textBand = floatArrayOf(0f, 0f, 0f, 0f)
    private var running = false
    private var lastFrameNanos: Long = 0L

    private var lastSimLook: WeatherLook? = null
    private var lastSimParams: WeatherLookParams? = null
    private var lastSimWidth: Float = 0f
    private var lastSimHeight: Float = 0f

    // Frost & Hail
    private var frostStrength: Float = 0f
    private var isHail: Boolean = false
    private var frostBitmap: Bitmap? = null
    private val frostPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val frostDstRect = RectF()

    private class HailPellet(
        var x: Float = 0f,
        var y: Float = 0f,
        var r: Float = 0f,
        var vy: Float = 0f,
    )

    private val hailPellets = Array(35) { HailPellet() }
    private val hailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        alpha = (0.75f * 255f).roundToInt()
    }

    // Flash
    private val flashPaint = Paint().apply {
        style = Paint.Style.FILL
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }

    // Glints (sheen spots)
    private val glintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val glintPinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Glass drops
    private val dropShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val dropShadowRect = RectF()
    private val dropRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val dropRimRect = RectF()
    private val dropHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val unitCircleRect = RectF(-1f, -1f, 1f, 1f)
    private val dropBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        shader = RadialGradient(
            0f, -0.25f, 1f,
            intArrayOf(
                Color.argb((0.05f * 255f).roundToInt(), 255, 255, 255),
                Color.argb((0.10f * 255f).roundToInt(), 200, 220, 255),
                Color.argb((0.30f * 255f).roundToInt(), 230, 240, 255),
            ),
            floatArrayOf(0f, 0.65f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    private val sortedDrops = ArrayList<RainDrop>()
    private val dropComparator = Comparator<RainDrop> { a, b -> a.r.compareTo(b.r) }

    // Flakes
    private val flakeHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val flakeCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Bolt
    private val boltPath = Path()
    private val boltGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val boltCorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    // Impacts
    private val impactRect = RectF()
    private val impactEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val impactRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val splashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val beadShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val beadBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val beadEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val beadHighlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val stepAction: (Float) -> Unit = { dt ->
        sim?.updateHome(dt)
        updateHail(dt)
    }

    private val frame = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.elapsedRealtimeNanos()
            val dtNanos = if (lastFrameNanos > 0L) now - lastFrameNanos else 0L
            lastFrameNanos = now
            val dtSeconds = dtNanos / 1_000_000_000f

            val clock = simClock
            if (clock != null) {
                clock.tick(dtSeconds, stepAction)
            }
            invalidate()
            postOnAnimationDelayed(this, WeatherEffects.FRAME_INTERVAL_MS)
        }
    }

    fun setWeather(look: WeatherLook?, code: Int? = null, isDay: Boolean = true) {
        val resolvedLook = look ?: if (code != null) WeatherLooks.forForecast(code, isDay) else null
        val resolvedParams = if (code != null) {
            WeatherLooks.paramsFor(code, isDay) ?: resolvedLook?.params
        } else {
            resolvedLook?.params
        }

        val spec = if (code != null) WeatherEffects.specFor(code) else null
        val effectParams = if (spec != null) EffectParams.of(spec) else null
        val frost = effectParams?.frost ?: 0f
        val hail = (effectParams?.hail ?: 0f) > 0.5f

        val lookOrParamsChanged = resolvedLook != currentLook || resolvedParams != currentParams
        val specChanged = frost != frostStrength || hail != isHail

        currentLook = resolvedLook
        currentCode = code
        currentIsDay = isDay
        currentParams = resolvedParams
        frostStrength = frost
        isHail = hail

        if (lookOrParamsChanged) {
            rebuildSim()
        } else if (specChanged) {
            updateFrostBitmap()
            if (isHail) initHail()
            invalidate()
        }
    }

    fun setLook(look: WeatherLook?, code: Int? = null, isDay: Boolean = true) {
        setWeather(look, code, isDay)
    }

    fun setEffect(spec: EffectSpec, tint: EffectTint) {
        // Compatibility method
        val look = when (spec.effect) {
            app.olauncher.helper.WeatherEffect.SUN -> WeatherLook.CLEAR
            app.olauncher.helper.WeatherEffect.CLOUDS -> WeatherLook.PARTLY
            app.olauncher.helper.WeatherEffect.FOG -> WeatherLook.FOG
            app.olauncher.helper.WeatherEffect.RAIN -> WeatherLook.LIGHT_RAIN
            app.olauncher.helper.WeatherEffect.SNOW -> WeatherLook.SNOW
            app.olauncher.helper.WeatherEffect.STORM -> WeatherLook.STORM
            app.olauncher.helper.WeatherEffect.NONE -> null
        }
        setWeather(look, null, true)
    }

    fun setKeepOutRects(rects: List<WeatherSimRect>) {
        if (keepOutRects == rects) return
        keepOutRects = rects
        sim?.keepOut = rects
    }

    /** Pixel y ranges of the clock block and the app list; glass drops fade out over them. */
    fun setTextBands(top1: Int, bottom1: Int, top2: Int, bottom2: Int) {
        val h = height.toFloat().coerceAtLeast(1f)
        val next = floatArrayOf(top1 / h, bottom1 / h, top2 / h, bottom2 / h)
        if (next.contentEquals(textBand)) return
        textBand = next
        invalidate()
    }

    fun setRunning(run: Boolean) {
        if (running == run) return
        running = run
        removeCallbacks(frame)
        if (run) {
            lastFrameNanos = SystemClock.elapsedRealtimeNanos()
            post(frame)
        } else {
            lastFrameNanos = 0L
            sim?.stillFrame()
            invalidate()
        }
    }

    fun rebuildSim() {
        val look = currentLook
        val params = currentParams
        val w = width.toFloat()
        val h = height.toFloat()

        if (look == null || params == null || w <= 0f || h <= 0f) {
            sim = null
            simClock = null
            lastSimLook = null
            lastSimParams = null
            lastSimWidth = 0f
            lastSimHeight = 0f
            frostBitmap?.recycle()
            frostBitmap = null
            invalidate()
            return
        }

        if (look == lastSimLook && params == lastSimParams && w == lastSimWidth && h == lastSimHeight && sim != null) {
            return
        }

        lastSimLook = look
        lastSimParams = params
        lastSimWidth = w
        lastSimHeight = h

        val seed = ((currentCode ?: 0).toLong() * 31L + (if (currentIsDay) 1L else 0L)) * 31L + (w.toLong() shl 16) + h.toLong()
        val newSim = WeatherSim(
            look = look,
            width = w,
            height = h,
            seed = seed,
            params = params,
            keepOut = keepOutRects,
        )
        sim = newSim
        // Fixed-step clock with 6s warm-up (180 steps of 1/30 s)
        simClock = WeatherSimClock(newSim, autoWarmUp = true)

        updateFrostBitmap()
        if (isHail) {
            initHail()
        }

        if (!running) {
            newSim.stillFrame()
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) {
            rebuildSim()
        }
    }

    private fun initHail() {
        val s = sim ?: return
        val rng = s.rng
        val w = width.toFloat().coerceAtLeast(1f)
        val h = height.toFloat().coerceAtLeast(1f)
        for (i in hailPellets.indices) {
            val p = hailPellets[i]
            p.x = WeatherSim.randRange(rng, 0f, w)
            p.y = WeatherSim.randRange(rng, -20f, h)
            p.r = WeatherSim.randRange(rng, 1.2f, 2.5f)
            p.vy = WeatherSim.randRange(rng, 700f, 1200f)
        }
    }

    private fun updateHail(dt: Float) {
        if (!isHail || width <= 0 || height <= 0) return
        val s = sim ?: return
        val rng = s.rng
        val wind = s.wind
        val w = width.toFloat()
        val h = height.toFloat()
        for (i in hailPellets.indices) {
            val p = hailPellets[i]
            p.y += p.vy * dt
            p.x += wind * 60f * dt
            if (p.y - p.r > h) {
                p.y = -p.r - WeatherSim.randRange(rng, 0f, 40f)
                p.x = WeatherSim.randRange(rng, 0f, w)
            }
        }
    }

    private fun updateFrostBitmap() {
        val w = width
        val h = height
        if (frostStrength <= 0f || w <= 0 || h <= 0) {
            frostBitmap?.recycle()
            frostBitmap = null
            return
        }

        val bw = 240
        val bh = ((240f * h) / w).roundToInt().coerceAtLeast(1)
        val pixels = IntArray(bw * bh)
        val frost = frostStrength

        for (by in 0 until bh) {
            val v = by.toFloat() / (bh - 1).coerceAtLeast(1)
            val dy = Math.abs(v - 0.5f) * 2f
            for (bx in 0 until bw) {
                val u = bx.toFloat() / (bw - 1).coerceAtLeast(1)
                val dx = Math.abs(u - 0.5f) * 2f
                val edge = Math.max(dx, dy)
                if (edge > 0.7f) {
                    val t = ((edge - 0.7f) / 0.3f).coerceIn(0f, 1f)
                    val edgeSmooth = t * t * (3f - 2f * t)
                    val qx = u * 9f
                    val qy = v * (bh.toFloat() / bw.toFloat()) * 9f
                    val n = (Math.sin(qx * 12.9898 + qy * 78.233) * 43758.5453).let { Math.abs(it - Math.floor(it)) }.toFloat()
                    val ice = edgeSmooth * (0.5f + 0.5f * n)
                    val alpha = (frost * ice * 0.55f * 255f).roundToInt().coerceIn(0, 255)
                    pixels[by * bw + bx] = Color.argb(alpha, 255, 255, 255)
                }
            }
        }

        frostBitmap?.recycle()
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        bmp.setPixels(pixels, 0, bw, 0, 0, bw, bh)
        frostBitmap = bmp
    }

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        if (e0 >= e1) return if (x < e0) 0f else 1f
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun inBand(y: Float, lo: Float, hi: Float): Float {
        if (lo >= hi) return 0f
        return smoothstep(lo - 0.02f, lo, y) * (1f - smoothstep(hi, hi + 0.02f, y))
    }

    private fun textBandMask(yNorm: Float): Float {
        val b1 = inBand(yNorm, textBand[0], textBand[1])
        val b2 = inBand(yNorm, textBand[2], textBand[3])
        return (1f - b1) * (1f - b2)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val s = sim ?: return
        if (width <= 0 || height <= 0) return

        // -------------------------------------------------------------
        // Sky layers (sun, stars, clouds, haze) - to be added by task 03
        // -------------------------------------------------------------

        // 1. Flash (behind the glass, so drops stay on top)
        if (s.flash > 0f) {
            drawFlash(canvas, s.flash)
        }

        // 2. Sheen spots
        if (s.sheenSpots.isNotEmpty()) {
            drawGlints(canvas, s)
        }

        // Frost (icy edge vignette)
        if (frostStrength > 0f && frostBitmap != null) {
            frostDstRect.set(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawBitmap(frostBitmap!!, null, frostDstRect, frostPaint)
        }

        // 3. Glass drops
        if (s.drops.isNotEmpty()) {
            drawDrops(canvas, s)
        }

        // 4. Flakes
        if (s.flakes.isNotEmpty()) {
            drawFlakes(canvas, s)
        }

        // Hail pellets
        if (isHail) {
            for (i in hailPellets.indices) {
                val p = hailPellets[i]
                canvas.drawCircle(p.x, p.y, p.r, hailPaint)
            }
        }

        // 5. Bolt
        val bp = s.boltPath
        if (bp != null && s.flash > 0.3f) {
            drawBolt(canvas, bp, s.flash)
        }

        // 6. Impacts
        if (s.impacts.isNotEmpty()) {
            drawImpacts(canvas, s)
        }
    }

    private fun drawFlash(canvas: Canvas, f: Float) {
        flashPaint.color = WeatherLookColors.FLASH
        flashPaint.alpha = (0.38f * f * 255f).roundToInt().coerceIn(0, 255)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), flashPaint)
    }

    private fun drawGlints(canvas: Canvas, sim: WeatherSim) {
        val glints = sim.sheenSpots
        for (i in 0 until glints.size) {
            val g = glints[i]
            val t = g.age / WeatherSim.GLINT_SECONDS
            if (t >= 1f) continue
            val fade = (1f - t) * (1f - t)
            glintPaint.color = WeatherLookColors.SHEEN
            glintPaint.alpha = (0.10f * fade * 255f).roundToInt().coerceIn(0, 255)
            canvas.drawCircle(g.x, g.y, g.r, glintPaint)
            if (t < 0.35f) {
                glintPinPaint.color = Color.WHITE
                glintPinPaint.alpha = (0.45f * (1f - t / 0.35f) * 255f).roundToInt().coerceIn(0, 255)
                canvas.drawCircle(g.x - g.r * 0.3f, g.y, 0.9f, glintPinPaint)
            }
        }
    }

    private fun drawDrops(canvas: Canvas, sim: WeatherSim) {
        sortedDrops.clear()
        sortedDrops.addAll(sim.drops)
        Collections.sort(sortedDrops, dropComparator)
        val h = height.toFloat().coerceAtLeast(1f)
        for (i in 0 until sortedDrops.size) {
            val d = sortedDrops[i]
            val mask = textBandMask(d.y / h)
            if (mask > 0.005f) {
                drawDrop(canvas, d, mask)
            }
        }
    }

    private fun drawDrop(canvas: Canvas, d: RainDrop, mask: Float) {
        val r = d.r
        val speed = Math.hypot(d.vx.toDouble(), d.vy.toDouble()).toFloat()
        val stretch = 1f + Math.min(speed / 220f, 0.35f)
        val rx = r * 0.92f
        val ry = r * stretch

        canvas.save()
        canvas.translate(d.x, d.y)
        canvas.rotate(-Math.toDegrees(d.tilt.toDouble()).toFloat())

        // Shadow under the drop
        val scx = r * 0.12f
        val scy = ry * 0.55f + 1f
        val srx = rx * 0.95f
        val sry = ry * 0.7f
        dropShadowRect.set(scx - srx, scy - sry, scx + srx, scy + sry)
        dropShadowPaint.color = Color.argb((0.28f * mask * 255f).roundToInt(), 0, 0, 0)
        canvas.drawOval(dropShadowRect, dropShadowPaint)

        // Soft body: clear in the middle, brighter low edge
        canvas.save()
        canvas.scale(rx, ry)
        dropBodyPaint.alpha = (mask * 255f).roundToInt()
        canvas.drawOval(unitCircleRect, dropBodyPaint)
        canvas.restore()

        // Darker rim
        dropRimRect.set(-rx, -ry, rx, ry)
        dropRimPaint.strokeWidth = Math.max(0.8f, r * 0.14f)
        dropRimPaint.color = Color.argb((0.45f * mask * 255f).roundToInt(), 0, 6, 20)
        canvas.drawOval(dropRimRect, dropRimPaint)

        // Specular highlight, top left
        val hx = -rx * 0.36f
        val hy = -ry * 0.4f
        val hr = Math.max(0.8f, r * 0.2f)
        dropHighlightPaint.color = Color.argb((0.92f * mask * 255f).roundToInt(), 255, 255, 255)
        canvas.drawCircle(hx, hy, hr, dropHighlightPaint)

        canvas.restore()
    }

    private fun drawFlakes(canvas: Canvas, sim: WeatherSim) {
        val flakes = sim.flakes
        for (i in 0 until flakes.size) {
            val f = flakes[i]
            val a = if (f.settled) {
                0.9f * (1f - f.melt / WeatherSim.MELT_SECONDS).coerceAtLeast(0f)
            } else {
                0.45f + 0.45f * f.depth
            }
            val halo = if (f.settled) f.r * (2f + f.melt) else f.r * 2f
            flakeHaloPaint.color = WeatherLookColors.SNOW
            flakeHaloPaint.alpha = (a * 0.22f * 255f).roundToInt().coerceIn(0, 255)
            canvas.drawCircle(f.x, f.y, halo, flakeHaloPaint)
            flakeCorePaint.color = WeatherLookColors.SNOW
            flakeCorePaint.alpha = (a * 255f).roundToInt().coerceIn(0, 255)
            canvas.drawCircle(f.x, f.y, f.r, flakeCorePaint)
        }
    }

    private fun drawBolt(canvas: Canvas, pts: List<WeatherSimPoint>, f: Float) {
        if (pts.isEmpty()) return
        boltPath.rewind()
        boltPath.moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) {
            boltPath.lineTo(pts[i].x, pts[i].y)
        }
        boltGlowPaint.color = WeatherLookColors.FLASH
        boltGlowPaint.alpha = (0.35f * f * 255f).roundToInt().coerceIn(0, 255)
        canvas.drawPath(boltPath, boltGlowPaint)

        boltCorePaint.color = Color.WHITE
        boltCorePaint.alpha = (0.9f * f * 255f).roundToInt().coerceIn(0, 255)
        canvas.drawPath(boltPath, boltCorePaint)
    }

    private fun drawImpacts(canvas: Canvas, sim: WeatherSim) {
        val impacts = sim.impacts
        for (i in 0 until impacts.size) {
            val im = impacts[i]
            val ring = im.ring()
            if (ring != null) {
                val edgeR = ring.r + 0.8f
                impactRect.set(
                    im.x - edgeR,
                    (im.y + 0.6f) - edgeR * 0.9f,
                    im.x + edgeR,
                    (im.y + 0.6f) + edgeR * 0.9f,
                )
                impactEdgePaint.color = WeatherLookColors.IMPACT_EDGE
                impactEdgePaint.alpha = (0.3f * ring.strength * 255f).roundToInt().coerceIn(0, 255)
                canvas.drawOval(impactRect, impactEdgePaint)

                impactRect.set(
                    im.x - ring.r,
                    im.y - ring.r * 0.9f,
                    im.x + ring.r,
                    im.y + ring.r * 0.9f,
                )
                impactRingPaint.color = WeatherLookColors.IMPACT
                impactRingPaint.alpha = (0.65f * ring.strength * 255f).roundToInt().coerceIn(0, 255)
                canvas.drawOval(impactRect, impactRingPaint)
            }
            if (im.age < WeatherSim.SPLASH_SECONDS) {
                splashPaint.color = Color.WHITE
                splashPaint.alpha = (0.7f * (1f - im.age / WeatherSim.SPLASH_SECONDS) * 255f).roundToInt().coerceIn(0, 255)
                canvas.drawCircle(im.x, im.y, 1.8f, splashPaint)
            }
            for (j in 0 until im.beads.size) {
                val p = im.bead(im.beads[j])
                if (p != null) {
                    drawBead(canvas, p)
                }
            }
        }
    }

    private fun drawBead(canvas: Canvas, p: BeadState) {
        val a = 0.9f * p.strength
        beadShadowPaint.color = Color.BLACK
        beadShadowPaint.alpha = (0.22f * a * 255f).roundToInt().coerceIn(0, 255)
        canvas.drawCircle(p.x + 0.3f, p.y + p.r * 0.5f, p.r, beadShadowPaint)

        beadBodyPaint.color = Color.rgb(215, 230, 255)
        beadBodyPaint.alpha = (0.35f * a * 255f).roundToInt().coerceIn(0, 255)
        canvas.drawCircle(p.x, p.y, p.r, beadBodyPaint)

        beadEdgePaint.color = WeatherLookColors.IMPACT_EDGE
        beadEdgePaint.alpha = (0.35f * a * 255f).roundToInt().coerceIn(0, 255)
        canvas.drawCircle(p.x, p.y, p.r, beadEdgePaint)

        beadHighlightPaint.color = Color.WHITE
        beadHighlightPaint.alpha = (0.95f * a * 255f).roundToInt().coerceIn(0, 255)
        canvas.drawCircle(p.x - p.r * 0.35f, p.y - p.r * 0.35f, Math.max(0.5f, p.r * 0.35f), beadHighlightPaint)
    }

    override fun onDetachedFromWindow() {
        setRunning(false)
        frostBitmap?.recycle()
        frostBitmap = null
        super.onDetachedFromWindow()
    }
}
