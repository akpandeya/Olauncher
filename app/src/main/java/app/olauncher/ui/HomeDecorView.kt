package app.olauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.withScale
import androidx.core.graphics.withTranslation
import androidx.core.view.isGone
import androidx.core.view.isVisible
import app.olauncher.data.ThemePack
import app.olauncher.data.ThemePackCodec
import app.olauncher.helper.ThemePackDecor
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * A Diwali rocket: launch geometry and timing in animation-time ms, in mock-dp space.
 */
private class RocketLaunch(
    @JvmField val x: Float,
    @JvmField val startY: Float,
    @JvmField val apexY: Float,
    @JvmField val startMs: Long,
    @JvmField val durationMs: Long,
    @JvmField val patternIndex: Int,
)

/**
 * One dot of a burst, with its twinkle phase (mock: `.twk`, 1.1s alternating).
 */
private class BoomDot(
    @JvmField val x: Float,
    @JvmField val y: Float,
    @JvmField val radius: Float,
    @JvmField val color: Int,
    @JvmField val twinkleDelayS: Float,
)

/**
 * A willow spark tip that falls away after the boom (mock: `.spark`): start point in dp,
 * total fall in dp and ms.
 */
private class WillowSpark(
    @JvmField val x: Float,
    @JvmField val y: Float,
    @JvmField val radius: Float,
    @JvmField val dxFall: Float,
    @JvmField val dyFall: Float,
    @JvmField val fallMs: Float,
)

/**
 * A detonated burst. Geometry (paths, dot positions) is built once at detonation — a rare
 * event — so drawing is transform and alpha only, like the rest of the view.
 */
private class LiveBoom(
    @JvmField val x: Float,
    @JvmField val y: Float,
    @JvmField val startMs: Long,
    @JvmField val fadeMs: Long,
    @JvmField val raysPath: Path?,
    @JvmField val rayStrokeWidth: Float,
    @JvmField val rayColor: Int,
    @JvmField val dots: List<BoomDot>,
    @JvmField val centreRadius: Float,
    @JvmField val centreColor: Int,
    @JvmField val sparks: List<WillowSpark>?,
)

/**
 * Seasonal dressing over the home screen for decorated theme packs (Halloween, Diwali,
 * Christmas): a full-bleed, non-touchable Canvas overlay ported from the HTML mock
 * (docs/design/themes/mocks/index.html, `decorHTML`). Mock px units are dp. The view draws
 * below the text layer, so nothing it draws can hide labels; plain packs draw nothing.
 *
 * Animation is one self-rescheduling frame loop capped at [ThemePackDecor.MAX_FPS]; with
 * [configure] `animate = false` (e-ink, system animations off) a single static frame is drawn.
 * All paths are built in [configure] (rocket bursts at detonation); per-frame work is transform
 * and alpha only.
 */
class HomeDecorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private var family: String? = null
    private var mirror = false
    private var animateRequested = false
    private var configKey: String? = null
    private var animating = false
    private var animStartMs = 0L

    // Pack colours as ARGB ints; 0 snow means the pack has none.
    private var accent = 0
    private var accentSecondary = 0
    private var ink = 0
    private var inkMuted = 0
    private var background = 0
    private var snowColor = 0
    private var lightPack = false

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    // Draws the pre-built Shader shapes (Diwali flame glow, bottom light strip).
    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Geometry in mock viewBox units, built once. Positions are applied with canvas transforms.
    private val webFramePath = Path()
    private val webArcsPath = Path()
    private val spiderThreadPath = Path()
    private val spiderLegsPath = Path()
    private val batPath = Path()
    private val ghostBodyPath = Path()
    private val pumpkinStemPath = Path()
    private val pumpkinFacePath = Path()
    private val burstRaysPath = Path()
    private val lotusPath = Path()
    private val lotusBasePath = Path()
    private val flamePath = Path()
    private val flameInnerPath = Path()
    private val diyaBowlPath = Path()
    private val diyaRimPath = Path()
    private val lightsWavePath = Path()
    private val hatCrownPath = Path()
    private val treeTrianglesPath = Path()
    private val treeStarPath = Path()

    // Ellipse and round-rect shapes kept as preallocated rects for drawOval/drawRoundRect.
    private val ghostMouth = RectF()
    private val pumpkinBody = RectF()
    private val pumpkinRibOuter = RectF()
    private val pumpkinRibInner = RectF()
    private val diyaRim = RectF()
    private val treeTrunk = RectF()
    private val hatBand = RectF()
    private val giftBoxTall = RectF()
    private val giftLidTall = RectF()
    private val giftRibbonTall = RectF()
    private val giftBoxSmall = RectF()
    private val giftLidSmall = RectF()
    private val giftRibbonSmall = RectF()

    private val waveMatrix = Matrix()
    private val waveScaledPath = Path()

    // Pre-built Diwali shaders. The flame glow lives in the diya's mock units, so the draw-time
    // canvas scale (density x diya width) sizes it for every diya. The light strip spans the
    // view, so it is rebuilt when the view size changes (see [onSizeChanged]). The rocket
    // trail, head glow and muzzle flash are built in dp at fixed sizes (diwali-rockets-mock).
    private var flameGlowShader: Shader? = null
    private var lightStripShader: Shader? = null
    private var rocketTrailShader: Shader? = null
    private var rocketGlowShader: Shader? = null
    private var rocketFlashShader: Shader? = null

    // Diya row spread across the real view width (dp): an edge margin at each side, widths
    // alternating 42/36, the remaining width split into equal gaps. [diyaLayoutWidth]
    // remembers the width the current lefts (and rangoli dot centres) were computed for.
    private val diyaLefts = FloatArray(DIYA_COUNT)
    private val diyaWidths = floatArrayOf(42f, 36f, 42f, 36f, 42f, 36f)
    private var diyaLayoutWidth = 0f

    // Rangoli dots: a tiny arc of three dots in each of the five gaps between adjacent diyas.
    // X spans the view width (laid out with the diyas, mirrored with [mirror]); Y is an
    // offset from the baseline.
    private val rangoliDotXs = FloatArray(RANGOLI_GAP_COUNT * 3)
    private val rangoliDotYs = FloatArray(RANGOLI_GAP_COUNT * 3)
    private val rangoliDotDelays = FloatArray(RANGOLI_GAP_COUNT * 3)

    // Burst twinkle dot centres (60x60 viewBox) and their CSS animation delays, in seconds.
    private val burstDotXs = floatArrayOf(55f, 48.5f, 30f, 11.5f, 5f, 11.5f, 30f, 48.5f)
    private val burstDotYs = floatArrayOf(30f, 48.5f, 55f, 48.5f, 30f, 11.5f, 5f, 11.5f)
    private val burstDotDelays = floatArrayOf(-0.3f, -0.9f, -1.5f, -2.1f, -1.2f, -0.6f, -1.8f, -2.4f)

    // Diwali rockets: queued launches, rockets in flight, and live bursts. All times are
    // animation-time ms since [animStartMs]; state is cleared whenever the loop starts or
    // stops, so a restart begins with an empty sky.
    private val pendingRockets = ArrayList<RocketLaunch>()
    private val risingRockets = ArrayList<RocketLaunch>()
    private val liveBooms = ArrayList<LiveBoom>()
    private var lastSalvoMs = 0L

    // Christmas bulb positions in the 340-wide mock (y in the 34-tall band) and delays.
    private val bulbXs = floatArrayOf(42f, 85f, 127f, 212f, 255f, 297f)
    private val bulbYs = floatArrayOf(13f, 17.5f, 16.5f, 16.5f, 17.5f, 13f)

    private val frameRunnable = object : Runnable {
        override fun run() {
            if (!animating || !isVisible) return
            invalidate()
            postOnAnimationDelayed(this, ThemePackDecor.FRAME_INTERVAL_MS)
        }
    }

    /**
     * Shows the dressing for [pack] (hidden when the pack family is not decorated) and restarts
     * the frame loop only when something changed. [alignmentRight] mirrors the mock layout
     * (mock positions assume left-aligned text); [animate] false draws one static frame.
     */
    fun configure(pack: ThemePack, alignmentRight: Boolean, animate: Boolean) {
        val nextFamily = ThemePackDecor.familyFor(pack.id)
        if (nextFamily == null) {
            stop()
            return
        }
        val key = "${pack.id}|$alignmentRight|$animate"
        if (nextFamily == family && key == configKey) {
            isVisible = true
            return
        }
        configKey = key
        family = nextFamily
        mirror = alignmentRight
        animateRequested = animate
        // The pack changed: the sky starts empty (stale launch times would misfire).
        clearRockets()
        lightPack = pack.mode == ThemePackCodec.MODE_LIGHT
        accent = ThemePackCodec.parseHex(pack.accent)
        accentSecondary = ThemePackCodec.parseHex(pack.accentSecondary)
        ink = ThemePackCodec.parseHex(pack.ink)
        inkMuted = ThemePackCodec.parseHex(pack.inkMuted)
        background = ThemePackCodec.parseHex(pack.background)
        snowColor = pack.snow?.let { ThemePackCodec.parseHex(it) } ?: 0
        buildGeometry()
        buildLightStripShader()
        isVisible = true
        requestLayout()
        updateAnimation()
        invalidate()
    }

    /** Tears the dressing down: stops the loop and hides the view. */
    fun stop() {
        if (family == null && configKey == null && isGone) return
        family = null
        configKey = null
        animateRequested = false
        updateAnimation()
        isGone = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // The bottom light strip spans the view, so its shader follows the size.
        buildLightStripShader()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateAnimation()
    }

    override fun onDetachedFromWindow() {
        updateAnimation()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateAnimation()
    }

    /** Runs the loop only while a decorated family is shown, animation is on and the view shows. */
    private fun updateAnimation() {
        val shouldRun = family != null && animateRequested && isVisible &&
            isAttachedToWindow && windowVisibility == VISIBLE
        if (animating == shouldRun) return
        animating = shouldRun
        removeCallbacks(frameRunnable)
        // Rocket times are offsets from animStartMs, so a loop restart empties the sky.
        clearRockets()
        if (shouldRun) {
            animStartMs = SystemClock.uptimeMillis()
            postOnAnimationDelayed(frameRunnable, ThemePackDecor.FRAME_INTERVAL_MS)
        }
    }

    /** Empties the sky: queued and rising rockets and live bursts all go. */
    private fun clearRockets() {
        pendingRockets.clear()
        risingRockets.clear()
        liveBooms.clear()
        lastSalvoMs = 0L
    }

    /**
     * Fires a celebration salvo of 3-5 rockets (the double-tap fireworks). Ignored unless the
     * Diwali dressing is animating: other packs, e-ink and animations-off see nothing.
     */
    fun fireSalvo() {
        if (!animating || family != ThemePackDecor.FAMILY_DIWALI) return
        queueSalvo(ThemePackDecor.celebrationSalvoSize(), SystemClock.uptimeMillis() - animStartMs)
    }

    /** Queues [count] rockets staggered 180-430ms apart, starting at [nowMs] (mock: salvo). */
    private fun queueSalvo(count: Int, nowMs: Long) {
        var startMs = nowMs
        for (k in 0 until count) {
            if (k > 0) {
                startMs += ROCKET_STAGGER_MIN_MS + Random.nextLong(ROCKET_STAGGER_JITTER_MS)
            }
            pendingRockets.add(newRocket(startMs))
        }
    }

    /** One rocket: random spot in the right-hand sky, apex and flight time jittered per launch. */
    private fun newRocket(startMs: Long): RocketLaunch {
        val density = resources.displayMetrics.density.coerceAtLeast(0.1f)
        val viewW = width / density
        val viewH = height / density
        return RocketLaunch(
            x = viewW * (ROCKET_MIN_X_FRACTION +
                Random.nextFloat() * (ROCKET_MAX_X_FRACTION - ROCKET_MIN_X_FRACTION)),
            startY = viewH * ROCKET_START_Y_FRACTION,
            apexY = viewH * (ROCKET_MIN_APEX_FRACTION +
                Random.nextFloat() * (ROCKET_MAX_APEX_FRACTION - ROCKET_MIN_APEX_FRACTION)),
            startMs = startMs,
            durationMs = ROCKET_RISE_MIN_MS + Random.nextLong(ROCKET_RISE_JITTER_MS),
            patternIndex = Random.nextInt(ROCKET_PATTERN_COUNT),
        )
    }

    override fun onDraw(canvas: Canvas) {
        val fam = family ?: return
        val density = resources.displayMetrics.density.coerceAtLeast(0.1f)
        val viewW = width / density
        val viewH = height / density
        if (viewW <= 0f || viewH <= 0f) return
        val t = if (animating) (SystemClock.uptimeMillis() - animStartMs) / 1000f else 0f
        canvas.withScale(density, density) {
            when (fam) {
                ThemePackDecor.FAMILY_HALLOWEEN -> drawHalloween(canvas, viewW, viewH, t)
                ThemePackDecor.FAMILY_DIWALI -> drawDiwali(canvas, viewW, viewH, t)
                ThemePackDecor.FAMILY_CHRISTMAS -> drawChristmas(canvas, viewW, viewH, t)
            }
        }
    }

    /**
     * Left edge (dp) of an element [elementW] dp wide placed [offset] dp from the mock's left
     * or right edge; right-aligned home text mirrors the mock, which swaps the anchors.
     */
    private fun edgeX(anchoredRight: Boolean, offset: Float, elementW: Float, viewW: Float): Float =
        if (anchoredRight != mirror) viewW - offset - elementW else offset

    /** Top edge (dp) of an element [elementH] dp tall placed [offset] dp from top or bottom. */
    private fun edgeY(anchoredBottom: Boolean, offset: Float, elementH: Float, viewH: Float): Float =
        if (anchoredBottom) viewH - offset - elementH else offset

    // ---- Halloween ----

    private fun drawHalloween(canvas: Canvas, viewW: Float, viewH: Float, t: Float) {
        // Spider web top-right, 82dp, gently floating (mock: floaty 11s).
        val webBob = if (animating) {
            ThemePackDecor.bobOffsetDp(ThemePackDecor.phase(t, periodS = 11f), amplitudeDp = 4f)
        } else 0f
        canvas.withTranslation(
            edgeX(anchoredRight = true, offset = 0f, elementW = 82f, viewW = viewW),
            webBob,
        ) {
            canvas.withScale(82f / 86f, 82f / 86f) {
                strokePaint.color = ink
                strokePaint.strokeWidth = 1.1f
                strokePaint.alpha = (0.38f * 255).toInt()
                canvas.drawPath(webFramePath, strokePaint)
                canvas.drawPath(webArcsPath, strokePaint)
                strokePaint.strokeWidth = 1f
                strokePaint.alpha = (0.5f * 255).toInt()
                canvas.drawPath(spiderThreadPath, strokePaint)
                strokePaint.strokeWidth = 0.9f
                canvas.drawPath(spiderLegsPath, strokePaint)
                fillPaint.color = ink
                fillPaint.alpha = (0.62f * 255).toInt()
                canvas.drawCircle(56f, 54f, 3f, fillPaint)
                canvas.drawCircle(56f, 49.5f, 1.8f, fillPaint)
            }
        }

        drawBat(canvas, viewW, mockLeft = 226f, top = 58f, width = 36f, periodS = 9f, t)
        drawBat(canvas, viewW, mockLeft = 118f, top = 36f, width = 26f, periodS = 7f, t)

        // Ghost right side, floating softly (mock: floaty 6s, drop-shadow soft).
        val ghostW = 46f
        val ghostBob = if (animating) {
            ThemePackDecor.bobOffsetDp(ThemePackDecor.phase(t, periodS = 6f), amplitudeDp = 3f)
        } else 0f
        canvas.withTranslation(
            edgeX(anchoredRight = true, offset = 118f, elementW = ghostW, viewW = viewW),
            320f + ghostBob,
        ) {
            canvas.withScale(ghostW / 36f, ghostW / 36f) {
                fillPaint.color = Color.WHITE
                fillPaint.alpha = 255
                canvas.drawPath(ghostBodyPath, fillPaint)
                fillPaint.color = GHOST_FEATURES
                canvas.drawCircle(13f, 15f, 2.1f, fillPaint)
                canvas.drawCircle(23f, 15f, 2.1f, fillPaint)
                canvas.drawOval(ghostMouth, fillPaint)
            }
        }

        // Jack-o'-lantern bottom-right, static (mock: animation:none).
        val pumpkinW = 56f
        val pumpkinScale = pumpkinW / 46f
        val pumpkinH = pumpkinW * 38f / 46f
        canvas.withTranslation(
            edgeX(anchoredRight = true, offset = 14f, elementW = pumpkinW, viewW = viewW),
            edgeY(anchoredBottom = true, offset = 22f, elementH = pumpkinH, viewH = viewH),
        ) {
            canvas.withScale(pumpkinScale, pumpkinScale) {
                fillPaint.color = TRUNK_BROWN
                fillPaint.alpha = 255
                canvas.drawPath(pumpkinStemPath, fillPaint)
                fillPaint.color = accent
                canvas.drawOval(pumpkinBody, fillPaint)
                strokePaint.color = Color.BLACK
                strokePaint.strokeWidth = 1.4f
                strokePaint.alpha = (0.22f * 255).toInt()
                canvas.drawOval(pumpkinRibOuter, strokePaint)
                canvas.drawOval(pumpkinRibInner, strokePaint)
                fillPaint.color = if (lightPack) PUMPKIN_FACE_LIGHT else PUMPKIN_FACE_DARK
                fillPaint.alpha = 255
                canvas.drawPath(pumpkinFacePath, fillPaint)
            }
        }
    }

    private fun drawBat(canvas: Canvas, viewW: Float, mockLeft: Float, top: Float, width: Float, periodS: Float, t: Float) {
        var dx = 0f
        var dy = 0f
        if (animating) {
            val p = ThemePackDecor.phase(t, periodS)
            // Mock drift: translate(-7px, 4px) at mid-cycle, mirrored side to side.
            dx = ThemePackDecor.driftOffsetXDp(p, amplitudeDp = 7f)
            dy = ThemePackDecor.driftOffsetYDp(p, amplitudeDp = 4f)
        }
        canvas.withTranslation(
            edgeX(anchoredRight = false, offset = mockLeft, elementW = width, viewW = viewW) + dx,
            top + dy,
        ) {
            canvas.withScale(width / 40f, width / 40f) {
                fillPaint.color = accentSecondary
                fillPaint.alpha = 255
                canvas.drawPath(batPath, fillPaint)
                fillPaint.color = background
                canvas.drawCircle(18.6f, 8.6f, 0.8f, fillPaint)
                canvas.drawCircle(21.4f, 8.6f, 0.8f, fillPaint)
            }
        }
    }

    // ---- Diwali ----

    private fun drawDiwali(canvas: Canvas, viewW: Float, viewH: Float, t: Float) {
        // The diya row spreads across the actual screen width; rebuild only when it changes.
        if (diyaLayoutWidth != viewW) buildDiyaLayout(viewW)

        // Rockets are time-based animation state: no salvo, flight or burst in the static
        // frame (e-ink, animations off), and their times run on the animation clock.
        val animMs = if (animating) (t * 1000f).toLong() else 0L
        if (animating) updateRockets(animMs)

        // Light pooling along the bottom edge from the diya row (Diwali-only).
        lightStripShader?.let {
            gradientPaint.shader = it
            canvas.drawRect(0f, viewH - LIGHT_STRIP_HEIGHT_DP, viewW, viewH, gradientPaint)
            gradientPaint.shader = null
        }

        // Tiny rangoli dot arcs in the gaps between the diyas, gently twinkling.
        drawRangoliDots(canvas, viewW, viewH, t)

        // Burst positions adapted from the mock: the real clock is much larger than the
        // mock's, so the big top burst moves to the top-right and the left burst joins
        // the right side below the lotus, keeping decor out from under the text.
        drawBurst(canvas, viewW, left = 330f, top = 54f, size = 66f, periodS = 3f, delayS = 0f, t)
        drawBurst(canvas, viewW, left = 344f, top = 268f, size = 44f, periodS = 3.6f, delayS = 1.3f, t)
        drawBurst(canvas, viewW, left = 292f, top = 178f, size = 30f, periodS = 2.6f, delayS = 0.7f, t)

        // Lotus right side, static (mock: animation:none).
        val lotusW = 42f
        canvas.withTranslation(
            edgeX(anchoredRight = true, offset = 18f, elementW = lotusW, viewW = viewW),
            214f,
        ) {
            canvas.withScale(lotusW / 40f, lotusW / 40f) {
                fillPaint.color = accentSecondary
                fillPaint.alpha = (0.9f * 255).toInt()
                canvas.drawPath(lotusPath, fillPaint)
                strokePaint.color = accent
                strokePaint.strokeWidth = 1.2f
                strokePaint.alpha = (0.7f * 255).toInt()
                canvas.drawPath(lotusBasePath, strokePaint)
            }
        }

        // Diyas evenly across the bottom edge, flames flickering (mock: flicker 1.5s,
        // per-flame phase).
        for (i in diyaLefts.indices) {
            drawDiya(canvas, viewW, viewH, left = diyaLefts[i], width = diyaWidths[i], flameIndex = i, t)
        }

        // Rockets and bursts draw last, so trails and sparks sit over the static dressing.
        if (animating) drawRockets(canvas, animMs)
    }

    /**
     * Spreads the six diyas over the actual view width: a [DIYA_EDGE_MARGIN_DP] margin at
     * each edge, widths alternating 42/36, the remaining width split into equal gaps. The
     * rangoli dot arcs sit centred in each gap, so their X centres follow the same layout.
     * Mirroring swaps the 42/36 alternation via [edgeX]; the layout itself is symmetric.
     */
    private fun buildDiyaLayout(viewW: Float) {
        diyaLayoutWidth = viewW
        var used = 2f * DIYA_EDGE_MARGIN_DP
        for (w in diyaWidths) used += w
        val gap = ((viewW - used) / (DIYA_COUNT - 1)).coerceAtLeast(0f)
        var left = DIYA_EDGE_MARGIN_DP
        for (i in diyaLefts.indices) {
            diyaLefts[i] = left
            left += diyaWidths[i] + gap
        }
        var dot = 0
        for (gapIndex in 0 until RANGOLI_GAP_COUNT) {
            val gapCentre = (diyaLefts[gapIndex] + diyaWidths[gapIndex] + diyaLefts[gapIndex + 1]) / 2f
            for (d in 0 until 3) {
                rangoliDotXs[dot] = gapCentre + (d - 1) * RANGOLI_DOT_SPACING_DP
                dot++
            }
        }
    }

    /** Three accentSecondary dots in each gap between adjacent diyas, on a shallow arc. */
    private fun drawRangoliDots(canvas: Canvas, viewW: Float, viewH: Float, t: Float) {
        val baselineY = viewH - RANGOLI_BOTTOM_DP
        fillPaint.color = accentSecondary
        for (i in rangoliDotXs.indices) {
            val alpha = if (animating) {
                RANGOLI_DOT_ALPHA * ThemePackDecor.twinkleAlpha(
                    ThemePackDecor.phase(t, RANGOLI_TWINKLE_PERIOD_S, rangoliDotDelays[i])
                )
            } else RANGOLI_DOT_ALPHA
            fillPaint.alpha = (alpha * 255).toInt()
            val x = if (mirror) viewW - rangoliDotXs[i] else rangoliDotXs[i]
            canvas.drawCircle(x, baselineY + rangoliDotYs[i], RANGOLI_DOT_RADIUS_DP, fillPaint)
        }
    }

    private fun drawBurst(
        canvas: Canvas,
        viewW: Float,
        left: Float,
        top: Float,
        size: Float,
        periodS: Float,
        delayS: Float,
        t: Float,
    ) {
        val burstAlpha = if (animating) {
            ThemePackDecor.twinkleAlpha(ThemePackDecor.phase(t, periodS, delayS))
        } else 1f
        canvas.withTranslation(
            edgeX(anchoredRight = false, offset = left, elementW = size, viewW = viewW),
            top,
        ) {
            canvas.withScale(size / 60f, size / 60f) {
                strokePaint.color = accent
                strokePaint.strokeWidth = 1.4f
                strokePaint.alpha = (0.85f * burstAlpha * 255).toInt()
                canvas.drawPath(burstRaysPath, strokePaint)
                fillPaint.color = accent
                fillPaint.alpha = (burstAlpha * 255).toInt()
                canvas.drawCircle(30f, 30f, 2f, fillPaint)
                fillPaint.color = accentSecondary
                for (i in burstDotXs.indices) {
                    val dotAlpha = if (animating) {
                        burstAlpha * ThemePackDecor.twinkleAlpha(ThemePackDecor.phase(t, periodS, burstDotDelays[i]))
                    } else 1f
                    fillPaint.alpha = (dotAlpha * 255).toInt()
                    canvas.drawCircle(burstDotXs[i], burstDotYs[i], 1.6f, fillPaint)
                }
            }
        }
    }

    private fun drawDiya(
        canvas: Canvas,
        viewW: Float,
        viewH: Float,
        left: Float,
        width: Float,
        flameIndex: Int,
        t: Float,
    ) {
        val diyaH = width * 26f / 36f
        canvas.withTranslation(
            edgeX(anchoredRight = false, offset = left, elementW = width, viewW = viewW),
            edgeY(anchoredBottom = true, offset = 20f, elementH = diyaH, viewH = viewH),
        ) {
            canvas.withScale(width / 36f, width / 36f) {
                // Glow behind the flame: radial halo in mock units, sized by the canvas scale.
                flameGlowShader?.let {
                    gradientPaint.shader = it
                    canvas.drawCircle(18f, 7f, FLAME_GLOW_RADIUS_DP, gradientPaint)
                    gradientPaint.shader = null
                }
                // Flame flickers on its base pivot (mock: transform-origin 50% 100%).
                var flickerScale = 1f
                var flickerAlpha = 1f
                if (animating) {
                    val p = ThemePackDecor.phase(t, FLICKER_PERIOD_S, flameIndex * FLAME_PHASE_STEP_S)
                    flickerScale = ThemePackDecor.flickerScaleY(p)
                    flickerAlpha = ThemePackDecor.flickerAlpha(p)
                }
                canvas.withTranslation(18f, 10.5f) {
                    canvas.withScale(1f, flickerScale) {
                        canvas.withTranslation(-18f, -10.5f) {
                            fillPaint.color = accent
                            fillPaint.alpha = (flickerAlpha * 255).toInt()
                            canvas.drawPath(flamePath, fillPaint)
                            fillPaint.color = FLAME_INNER
                            fillPaint.alpha = ((if (lightPack) 0.35f else 0.95f) * flickerAlpha * 255).toInt()
                            canvas.drawPath(flameInnerPath, fillPaint)
                        }
                    }
                }
                // Bowl and rim.
                fillPaint.color = DIYA_BOWL
                fillPaint.alpha = 255
                canvas.drawPath(diyaBowlPath, fillPaint)
                fillPaint.color = DIYA_RIM
                canvas.drawOval(diyaRim, fillPaint)
            }
        }
    }

    // ---- Diwali rockets (diwali-rockets-mock.html) ----

    /**
     * Advances the rocket state machine in animation time: rolls the auto salvo every
     * [ThemePackDecor.ROCKET_SALVO_PERIOD_MS], launches queued rockets whose start time has
     * arrived, detonates finished flights and expires old bursts. Called from the frame loop
     * only; rockets never post their own callbacks.
     */
    private fun updateRockets(animMs: Long) {
        if (animMs - lastSalvoMs >= ThemePackDecor.ROCKET_SALVO_PERIOD_MS) {
            lastSalvoMs = animMs
            val evening = ThemePackDecor.isEveningHour(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
            queueSalvo(ThemePackDecor.salvoSizeFor(Random.nextFloat(), evening), animMs)
        }
        // Launches whose start time has arrived begin rising.
        val queued = pendingRockets.iterator()
        while (queued.hasNext()) {
            val rocket = queued.next()
            if (rocket.startMs <= animMs) {
                queued.remove()
                risingRockets.add(rocket)
            }
        }
        // A finished flight detonates — unless the sky is already at the burst cap, in which
        // case the rocket is skipped rather than queued (mock: boomLive() < MAX_LIVE).
        // Unlike the mock we let an over-cap rocket finish its climb before fizzling
        // without bursting (the mock skips the launch itself); visual-only difference,
        // kept because aborting a rising trail looks like a glitch.
        var flight = 0
        while (flight < risingRockets.size) {
            val rocket = risingRockets[flight]
            if (animMs >= rocket.startMs + rocket.durationMs) {
                risingRockets.removeAt(flight)
                if (liveBooms.size < ThemePackDecor.MAX_LIVE_BOOMS) {
                    liveBooms.add(buildBoom(rocket, animMs))
                }
            } else flight++
        }
        var burst = 0
        while (burst < liveBooms.size) {
            val boom = liveBooms[burst]
            if (animMs >= boom.startMs + boom.fadeMs) liveBooms.removeAt(burst) else burst++
        }
    }

    /**
     * Builds one burst's geometry once at detonation (rare, so allocations are fine here):
     * a random pattern from five, sized [ROCKET_BURST_SIZE_DP] with per-launch jitter. All
     * coordinates end up in absolute dp, so drawing is transform and alpha only.
     */
    private fun buildBoom(rocket: RocketLaunch, startMs: Long): LiveBoom {
        val burstSize = ROCKET_BURST_SIZE_DP * (0.85f + Random.nextFloat() * 0.3f)
        val u = burstSize / BOOM_BOX_UNITS // dp per mock unit (the mock viewBox is 60x60)
        val cx = rocket.x
        val cy = rocket.apexY
        return when (rocket.patternIndex % ROCKET_PATTERN_COUNT) {
            PATTERN_RING -> ringBoom(cx, cy, u, startMs)
            PATTERN_WILLOW -> willowBoom(cx, cy, u, startMs)
            PATTERN_PALM -> palmBoom(cx, cy, u, startMs)
            PATTERN_DOUBLE_RING -> doubleRingBoom(cx, cy, u, startMs)
            else -> chrysanthemumBoom(cx, cy, u, startMs)
        }
    }

    /** Chrysanthemum: 8-12 radial accent rays, twinkle dots at alternating tips, centre dot. */
    private fun chrysanthemumBoom(cx: Float, cy: Float, u: Float, startMs: Long): LiveBoom {
        val rays = Path()
        val dots = ArrayList<BoomDot>(6)
        val n = 8 + Random.nextInt(5)
        for (k in 0 until n) {
            val a = k * TWO_PI_F / n
            val j = 0.9f + Random.nextFloat() * 0.25f
            val innerX = cx + 7f * u * cos(a)
            val innerY = cy + 7f * u * sin(a)
            val tipX = cx + 25f * j * u * cos(a)
            val tipY = cy + 25f * j * u * sin(a)
            rays.moveTo(innerX, innerY)
            rays.lineTo(tipX, tipY)
            if (k % 2 == 1) {
                dots.add(BoomDot(tipX, tipY, 1.7f * u, accentSecondary, -Random.nextFloat() * BOOM_TWINKLE_PERIOD_S))
            }
        }
        return LiveBoom(cx, cy, startMs, fadeMs = 1500, rays, 1.5f * u, accent, dots, 2.1f * u, accent, null)
    }

    /** Ring: 11-14 glowing dots on a loose circle, every third accentSecondary, cream centre. */
    private fun ringBoom(cx: Float, cy: Float, u: Float, startMs: Long): LiveBoom {
        val n = 11 + Random.nextInt(4)
        val radius = (21f + Random.nextFloat() * 4f) * u
        val dots = ArrayList<BoomDot>(n)
        for (k in 0 until n) {
            val a = k * TWO_PI_F / n
            dots.add(
                BoomDot(
                    cx + radius * cos(a), cy + radius * sin(a),
                    (1.5f + Random.nextFloat() * 1.3f) * u,
                    if (k % 3 == 0) accentSecondary else accent,
                    -Random.nextFloat() * BOOM_TWINKLE_PERIOD_S,
                )
            )
        }
        return LiveBoom(cx, cy, startMs, fadeMs = 1600, null, 0f, 0, dots, 1.8f * u, ROCKET_CREAM, null)
    }

    /**
     * Willow: 6-8 drooping cream arcs; spark dots at the tips fall 20-42dp over 1.5-2.4s,
     * drifting up to ±7dp sideways, outliving the main boom.
     */
    private fun willowBoom(cx: Float, cy: Float, u: Float, startMs: Long): LiveBoom {
        val arcs = Path()
        val sparks = ArrayList<WillowSpark>(8)
        val n = 6 + Random.nextInt(3)
        for (k in 0 until n) {
            val a = k * TWO_PI_F / n + Random.nextFloat() * 0.2f
            val tipX = cx + 24f * u * cos(a)
            val tipY = cy + (18f * abs(sin(a)) + 4f) * u
            val midA = k * TWO_PI_F / n + 0.1f
            arcs.moveTo(cx, cy)
            arcs.quadTo(cx + 20f * u * cos(midA), cy + 6f * u * sin(midA), tipX, tipY)
            sparks.add(
                WillowSpark(
                    tipX, tipY, 1.6f * u,
                    (Random.nextFloat() - 0.5f) * 2f * WILLOW_DRIFT_DP,
                    WILLOW_FALL_MIN_DP + Random.nextFloat() * (WILLOW_FALL_MAX_DP - WILLOW_FALL_MIN_DP),
                    WILLOW_FALL_MIN_MS + Random.nextFloat() * (WILLOW_FALL_MAX_MS - WILLOW_FALL_MIN_MS),
                )
            )
        }
        return LiveBoom(cx, cy, startMs, fadeMs = 2200, arcs, 1.2f * u, ROCKET_CREAM, emptyList(), 0f, 0, sparks)
    }

    /** Palm: 5-6 thick curved accent fronds with accentSecondary crackle tips. */
    private fun palmBoom(cx: Float, cy: Float, u: Float, startMs: Long): LiveBoom {
        val fronds = Path()
        val dots = ArrayList<BoomDot>(6)
        val n = 5 + Random.nextInt(2)
        for (k in 0 until n) {
            val a = k * TWO_PI_F / n + Random.nextFloat() * 0.3f
            val midX = cx + 14f * u * cos(a)
            val midY = cy + 14f * u * sin(a)
            val tipX = cx + 26f * u * cos(a)
            val tipY = cy + 26f * u * sin(a)
            fronds.moveTo(cx, cy)
            fronds.quadTo(midX, midY, tipX, tipY)
            dots.add(BoomDot(tipX, tipY, 2.1f * u, accentSecondary, -Random.nextFloat() * BOOM_TWINKLE_PERIOD_S))
        }
        return LiveBoom(cx, cy, startMs, fadeMs = 1400, fronds, 2.4f * u, accent, dots, 2.4f * u, accentSecondary, null)
    }

    /** Double-ring: 8 inner accent dots and 14 outer accentSecondary dots in opposite phase. */
    private fun doubleRingBoom(cx: Float, cy: Float, u: Float, startMs: Long): LiveBoom {
        val outerRadius = (24f + Random.nextFloat() * 2f) * u
        val dots = ArrayList<BoomDot>(22)
        for (k in 0 until 8) {
            val a = k * TWO_PI_F / 8f
            dots.add(BoomDot(cx + 11f * u * cos(a), cy + 11f * u * sin(a), 1.8f * u, accent, 0f))
        }
        // Half a twinkle period apart twirls the rings in opposite phase (mock: delay -.55s).
        for (k in 0 until 14) {
            val a = k * TWO_PI_F / 14f + 0.2f
            dots.add(
                BoomDot(
                    cx + outerRadius * cos(a), cy + outerRadius * sin(a), 1.4f * u,
                    accentSecondary, -BOOM_TWINKLE_PERIOD_S / 2f,
                )
            )
        }
        return LiveBoom(cx, cy, startMs, fadeMs = 1700, null, 0f, 0, dots, 1.6f * u, ROCKET_CREAM, null)
    }

    /** Draws the rising trails and the live bursts over the static dressing. */
    private fun drawRockets(canvas: Canvas, animMs: Long) {
        for (rocket in risingRockets) drawRocketTrail(canvas, rocket, animMs)
        for (boom in liveBooms) drawBoom(canvas, boom, animMs)
    }

    /** Cream trail bar and warm glow dot riding the head of a rising rocket. */
    private fun drawRocketTrail(canvas: Canvas, rocket: RocketLaunch, animMs: Long) {
        val p = (animMs - rocket.startMs).toFloat() / rocket.durationMs
        if (p <= 0f || p >= 1f) return
        // Decelerating rise (mock: cubic ease-out).
        val eased = 1f - (1f - p).pow(3)
        val headY = rocket.startY + (rocket.apexY - rocket.startY) * eased
        // Mock opacity keyframes: in by 10%, held to 75%, then fading away.
        val alpha = when {
            p < 0.10f -> p / 0.10f
            p < 0.75f -> 1f
            p < 0.92f -> 1f - 0.45f * ((p - 0.75f) / 0.17f)
            else -> 0.55f * (1f - (p - 0.92f) / 0.08f)
        }
        val widthScale = if (p < 0.92f) 1f else 0.55f * (1f - (p - 0.92f) / 0.08f)
        val trailShader = rocketTrailShader ?: return
        val glowShader = rocketGlowShader ?: return
        gradientPaint.shader = trailShader
        gradientPaint.alpha = (alpha * 255).toInt()
        val halfWidth = ROCKET_TRAIL_WIDTH_DP / 2f * widthScale
        canvas.withTranslation(rocket.x, headY) {
            canvas.drawRect(-halfWidth, 0f, halfWidth, ROCKET_TRAIL_LENGTH_DP, gradientPaint)
        }
        gradientPaint.shader = glowShader
        canvas.drawCircle(rocket.x, headY, ROCKET_GLOW_RADIUS_DP, gradientPaint)
        gradientPaint.shader = null
        // The shared paint is reused by the static light strip and diya glow, which
        // expect full alpha; leave no fading trail alpha behind for them to inherit.
        gradientPaint.alpha = 255
    }

    /** One burst: mock `@keyframes boom` envelope over the pre-built geometry. */
    private fun drawBoom(canvas: Canvas, boom: LiveBoom, animMs: Long) {
        val p = (animMs - boom.startMs).toFloat() / boom.fadeMs
        if (p <= 0f || p >= 1f) return
        // Pop in fast, hold, then swell slightly as it fades.
        val scale = when {
            p < 0.12f -> 0.15f + 0.40f * (p / 0.12f)
            p < 0.30f -> 0.55f + 0.45f * ((p - 0.12f) / 0.18f)
            else -> 1f + 0.22f * ((p - 0.30f) / 0.70f)
        }
        val alpha = when {
            p < 0.12f -> p / 0.12f
            p < 0.30f -> 1f - 0.05f * ((p - 0.12f) / 0.18f)
            else -> 0.95f * (1f - (p - 0.30f) / 0.70f)
        }
        canvas.save()
        canvas.scale(scale, scale, boom.x, boom.y)

        // Muzzle flash for the first half second (mock: .flash).
        val flashP = (animMs - boom.startMs).toFloat() / ROCKET_FLASH_MS
        rocketFlashShader?.takeIf { flashP in 0f..1f }?.let {
            gradientPaint.shader = it
            gradientPaint.alpha = ((0.95f * (1f - flashP)) * 255).toInt()
            val flashScale = 0.2f + 1.4f * flashP
            canvas.save()
            canvas.scale(flashScale, flashScale, boom.x, boom.y)
            canvas.drawCircle(boom.x, boom.y, ROCKET_FLASH_RADIUS_DP, gradientPaint)
            canvas.restore()
            gradientPaint.shader = null
            // Same shared paint: restore full alpha for the strip/diya glow draws.
            gradientPaint.alpha = 255
        }

        boom.raysPath?.let {
            strokePaint.color = boom.rayColor
            strokePaint.strokeWidth = boom.rayStrokeWidth
            strokePaint.alpha = (alpha * 255).toInt()
            canvas.drawPath(it, strokePaint)
        }
        if (boom.centreRadius > 0f) {
            fillPaint.color = boom.centreColor
            fillPaint.alpha = (alpha * 255).toInt()
            canvas.drawCircle(boom.x, boom.y, boom.centreRadius, fillPaint)
        }
        val twinkleT = animMs / 1000f
        for (dot in boom.dots) {
            fillPaint.color = dot.color
            fillPaint.alpha = (
                alpha * ThemePackDecor.twinkleAlpha(
                    ThemePackDecor.phase(twinkleT, BOOM_TWINKLE_PERIOD_S, dot.twinkleDelayS),
                    )
                * 255
                ).toInt()
            canvas.drawCircle(dot.x, dot.y, dot.radius, fillPaint)
        }
        // Willow sparks fall from the tips and fade with their own clock.
        boom.sparks?.let { sparks ->
            fillPaint.color = ROCKET_CREAM
            for (spark in sparks) {
                val f = ((animMs - boom.startMs).toFloat() / spark.fallMs).coerceIn(0f, 1f)
                val sparkAlpha = if (f < 0.7f) {
                    0.95f - 0.25f * (f / 0.7f)
                } else {
                    0.70f * (1f - (f - 0.7f) / 0.3f)
                }
                fillPaint.alpha = (alpha * sparkAlpha * 255).toInt()
                canvas.drawCircle(
                    spark.x + spark.dxFall * f,
                    spark.y + spark.dyFall * f,
                    spark.radius,
                    fillPaint,
                )
            }
        }
        canvas.restore()
    }

    // ---- Christmas ----

    private fun drawChristmas(canvas: Canvas, viewW: Float, viewH: Float, t: Float) {
        // String lights across the top; the 340-wide mock wave stretches to the view width.
        strokePaint.color = inkMuted
        strokePaint.strokeWidth = 1.2f
        strokePaint.alpha = (0.55f * 255).toInt()
        if (viewW > 0f) {
            waveMatrix.setScale(viewW / 340f, 1f)
            lightsWavePath.transform(waveMatrix, waveScaledPath)
            canvas.drawPath(waveScaledPath, strokePaint)
        }
        for (i in bulbXs.indices) {
            val bulbColor = bulbColor(i)
            var x = bulbXs[i] * viewW / 340f
            if (mirror) x = viewW - x
            val y = bulbYs[i]
            fillPaint.color = bulbColor
            fillPaint.alpha = (0.22f * 255).toInt()
            canvas.drawCircle(x, y, 6.5f, fillPaint)
            fillPaint.alpha = if (animating) {
                (ThemePackDecor.twinkleAlpha(ThemePackDecor.phase(t, periodS = 2.6f, delayS = -i * 0.8f)) * 255).toInt()
            } else 255
            canvas.drawCircle(x, y, 3.4f, fillPaint)
        }

        // Santa hat top-left, tilted (mock: rotate(-16deg) about the box centre), static.
        canvas.withTranslation(
            edgeX(anchoredRight = false, offset = 8f, elementW = 60f, viewW = viewW),
            32f,
        ) {
            canvas.rotate(-16f, 30f, 22f)
            fillPaint.color = HAT_RED
            fillPaint.alpha = 255
            canvas.drawPath(hatCrownPath, fillPaint)
            fillPaint.color = Color.WHITE
            canvas.drawRoundRect(hatBand, 4.5f, 4.5f, fillPaint)
            canvas.drawCircle(53.5f, 13f, 5f, fillPaint)
        }

        // Tree right side, static (mock: animation:none).
        val treeW = 88f
        canvas.withTranslation(
            edgeX(anchoredRight = true, offset = 12f, elementW = treeW, viewW = viewW),
            278f,
        ) {
            canvas.withScale(treeW / 44f, treeW / 44f) {
                fillPaint.color = TREE_GREEN
                fillPaint.alpha = 255
                canvas.drawPath(treeTrianglesPath, fillPaint)
                fillPaint.color = TRUNK_BROWN
                canvas.drawRoundRect(treeTrunk, 1.5f, 1.5f, fillPaint)
                drawOrnament(canvas, 17f, 20f, accent)
                drawOrnament(canvas, 27f, 27f, accentSecondary)
                drawOrnament(canvas, 14f, 34f, accentSecondary)
                drawOrnament(canvas, 30f, 38f, accent)
                drawOrnament(canvas, 22f, 43f, accent)
                fillPaint.color = accentSecondary
                canvas.drawPath(treeStarPath, fillPaint)
            }
        }

        // Gifts bottom-right, static (mock: animation:none).
        val giftsW = 56f
        canvas.withTranslation(
            edgeX(anchoredRight = true, offset = 12f, elementW = giftsW, viewW = viewW),
            edgeY(anchoredBottom = true, offset = 20f, elementH = giftsW * 34f / 40f, viewH = viewH),
        ) {
            canvas.withScale(giftsW / 40f, giftsW / 40f) {
                fillPaint.color = accent
                fillPaint.alpha = 255
                canvas.drawRoundRect(giftBoxTall, 2f, 2f, fillPaint)
                canvas.drawRoundRect(giftLidTall, 2f, 2f, fillPaint)
                strokePaint.color = Color.BLACK
                strokePaint.strokeWidth = 1f
                strokePaint.alpha = (0.18f * 255).toInt()
                canvas.drawRoundRect(giftLidTall, 2f, 2f, strokePaint)
                fillPaint.color = accentSecondary
                canvas.drawRect(giftRibbonTall, fillPaint)
                canvas.drawRoundRect(giftBoxSmall, 1.5f, 1.5f, fillPaint)
                canvas.drawRoundRect(giftLidSmall, 1.5f, 1.5f, fillPaint)
                strokePaint.alpha = (0.15f * 255).toInt()
                canvas.drawRoundRect(giftLidSmall, 1.5f, 1.5f, strokePaint)
                fillPaint.color = accent
                canvas.drawRect(giftRibbonSmall, fillPaint)
            }
        }

        if (snowColor != 0) drawSnow(canvas, viewW, viewH, t)
    }

    private fun bulbColor(index: Int): Int = when (index % 3) {
        0 -> accent
        1 -> accentSecondary
        else -> if (snowColor != 0) snowColor else accent
    }

    private fun drawOrnament(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        fillPaint.color = color
        fillPaint.alpha = 255
        canvas.drawCircle(cx, cy, 2f, fillPaint)
    }

    private fun drawSnow(canvas: Canvas, viewW: Float, viewH: Float, t: Float) {
        fillPaint.color = snowColor
        for (i in 0 until SNOWFLAKE_COUNT) {
            val xFraction = (6 + i * 12) / 100f
            val x = (if (mirror) 1f - xFraction else xFraction) * viewW
            val progress = if (animating) {
                ThemePackDecor.snowProgress(t, periodS = SNOW_FALL_BASE_S + (i % 4) * 2.5f, delayS = -i * 1.7f)
            } else {
                (i + 0.5f) / SNOWFLAKE_COUNT
            }
            // Mock: flake starts 6px above the view and falls 740px (view height + 6 above + 14 below).
            val y = -6f + progress * (viewH + 20f)
            fillPaint.alpha = (0.8f * 255).toInt()
            canvas.drawCircle(x, y, 2.5f, fillPaint)
        }
    }

    // ---- Geometry (mock viewBox units) ----

    /** Rebuilds the Diwali bottom light strip shader for the current view size and accent. */
    private fun buildLightStripShader() {
        // Diwali-only: nothing draws the strip for other families, so skip the allocation.
        if (family != ThemePackDecor.FAMILY_DIWALI) {
            lightStripShader = null
            return
        }
        val density = resources.displayMetrics.density.coerceAtLeast(0.1f)
        val viewH = height / density
        if (viewH <= 0f) {
            lightStripShader = null
            return
        }
        // Built in dp like every mock unit; the draw-time density scale maps it to px.
        lightStripShader = LinearGradient(
            0f, viewH, 0f, viewH - LIGHT_STRIP_HEIGHT_DP,
            Color.argb((LIGHT_STRIP_ALPHA * 255).toInt(), Color.red(accent), Color.green(accent), Color.blue(accent)),
            Color.argb(0, Color.red(accent), Color.green(accent), Color.blue(accent)),
            Shader.TileMode.CLAMP,
        )
    }

    private fun buildGeometry() {
        // Spider web: straight frame strokes, then the hanging arcs.
        webFramePath.rewind()
        webFramePath.moveTo(86f, 0f)
        webFramePath.lineTo(86f, 86f)
        webFramePath.moveTo(86f, 0f)
        webFramePath.lineTo(0f, 0f)
        webFramePath.moveTo(86f, 0f)
        webFramePath.lineTo(40f, 46f)
        webArcsPath.rewind()
        webArcsPath.moveTo(86f, 22f)
        webArcsPath.quadTo(77f, 9f, 64f, 0f)
        webArcsPath.moveTo(86f, 44f)
        webArcsPath.quadTo(67f, 19f, 42f, 0f)
        webArcsPath.moveTo(86f, 66f)
        webArcsPath.quadTo(58f, 28f, 20f, 0f)
        spiderThreadPath.rewind()
        spiderThreadPath.moveTo(56f, 30f)
        spiderThreadPath.lineTo(56f, 52f)
        spiderLegsPath.rewind()
        spiderLegsPath.moveTo(53.5f, 52f)
        spiderLegsPath.lineTo(49f, 49f)
        spiderLegsPath.moveTo(53.5f, 55f)
        spiderLegsPath.lineTo(49f, 58f)
        spiderLegsPath.moveTo(58.5f, 52f)
        spiderLegsPath.lineTo(63f, 49f)
        spiderLegsPath.moveTo(58.5f, 55f)
        spiderLegsPath.lineTo(63f, 58f)

        // Bat: body oval, two scalloped wings, two ears (fill), eyes drawn separately.
        batPath.rewind()
        batPath.addOval(17f, 5.4f, 23f, 14.6f, Path.Direction.CW)
        batPath.moveTo(17.6f, 8f)
        batPath.cubicTo(13f, 3f, 7f, 3f, 2f, 7f)
        batPath.cubicTo(5f, 7f, 6f, 9f, 5f, 11f)
        batPath.cubicTo(8f, 11f, 9f, 13f, 8.5f, 15f)
        batPath.cubicTo(12.5f, 15.5f, 16f, 13f, 17.6f, 10.5f)
        batPath.close()
        batPath.moveTo(22.4f, 8f)
        batPath.cubicTo(27f, 3f, 33f, 3f, 38f, 7f)
        batPath.cubicTo(35f, 7f, 34f, 9f, 35f, 11f)
        batPath.cubicTo(32f, 11f, 31f, 13f, 31.5f, 15f)
        batPath.cubicTo(27.5f, 15.5f, 24f, 13f, 22.4f, 10.5f)
        batPath.close()
        batPath.moveTo(18.4f, 5.4f)
        batPath.lineTo(17.6f, 1.8f)
        batPath.lineTo(20.4f, 3.8f)
        batPath.lineTo(23.6f, 1.8f)
        batPath.lineTo(21.6f, 5.4f)
        batPath.close()

        // Ghost: rounded top, four scallops along the bottom.
        ghostBodyPath.rewind()
        ghostBodyPath.moveTo(18f, 3f)
        ghostBodyPath.cubicTo(10f, 3f, 5f, 10f, 5f, 18f)
        ghostBodyPath.lineTo(5f, 35f)
        ghostBodyPath.quadTo(7.5f, 31f, 10f, 35f)
        ghostBodyPath.quadTo(12.5f, 39f, 15f, 35f)
        ghostBodyPath.quadTo(17.5f, 31f, 20f, 35f)
        ghostBodyPath.quadTo(22.5f, 39f, 25f, 35f)
        ghostBodyPath.quadTo(27.5f, 31f, 31f, 35f)
        ghostBodyPath.lineTo(31f, 18f)
        ghostBodyPath.cubicTo(31f, 10f, 26f, 3f, 18f, 3f)
        ghostBodyPath.close()
        ghostMouth.set(18f - 1.6f, 21.5f - 2.2f, 18f + 1.6f, 21.5f + 2.2f)

        // Pumpkin: curved stem, ellipse body, two rib ellipses, carved face.
        pumpkinStemPath.rewind()
        pumpkinStemPath.moveTo(21.5f, 7.5f)
        pumpkinStemPath.cubicTo(21f, 4.5f, 22.5f, 2f, 25.5f, 1f)
        pumpkinStemPath.cubicTo(24f, 3.5f, 24f, 5.5f, 24.5f, 8f)
        pumpkinStemPath.close()
        pumpkinBody.set(23f - 19f, 22.5f - 13.5f, 23f + 19f, 22.5f + 13.5f)
        pumpkinRibOuter.set(23f - 11.5f, 22.5f - 13.5f, 23f + 11.5f, 22.5f + 13.5f)
        pumpkinRibInner.set(23f - 4.5f, 22.5f - 13.5f, 23f + 4.5f, 22.5f + 13.5f)
        pumpkinFacePath.rewind()
        pumpkinFacePath.moveTo(13f, 20.5f)
        pumpkinFacePath.lineTo(17.5f, 20.5f)
        pumpkinFacePath.lineTo(15.2f, 15.5f)
        pumpkinFacePath.close()
        pumpkinFacePath.moveTo(28.5f, 20.5f)
        pumpkinFacePath.lineTo(33f, 20.5f)
        pumpkinFacePath.lineTo(30.8f, 15.5f)
        pumpkinFacePath.close()
        pumpkinFacePath.moveTo(12f, 27.5f)
        pumpkinFacePath.lineTo(15.5f, 25f)
        pumpkinFacePath.lineTo(18.5f, 28f)
        pumpkinFacePath.lineTo(21.5f, 24.5f)
        pumpkinFacePath.lineTo(24.5f, 28f)
        pumpkinFacePath.lineTo(27.5f, 25f)
        pumpkinFacePath.lineTo(31f, 27.5f)
        pumpkinFacePath.lineTo(29.5f, 30.5f)
        pumpkinFacePath.lineTo(13.5f, 30.5f)
        pumpkinFacePath.close()

        // Firework burst: eight radial rays from the ring edge to the tips.
        burstRaysPath.rewind()
        burstRaysPath.moveTo(38f, 30f)
        burstRaysPath.lineTo(54f, 30f)
        burstRaysPath.moveTo(35.7f, 35.7f)
        burstRaysPath.lineTo(47f, 47f)
        burstRaysPath.moveTo(30f, 38f)
        burstRaysPath.lineTo(30f, 54f)
        burstRaysPath.moveTo(24.3f, 35.7f)
        burstRaysPath.lineTo(13f, 47f)
        burstRaysPath.moveTo(22f, 30f)
        burstRaysPath.lineTo(6f, 30f)
        burstRaysPath.moveTo(24.3f, 24.3f)
        burstRaysPath.lineTo(13f, 13f)
        burstRaysPath.moveTo(30f, 22f)
        burstRaysPath.lineTo(30f, 6f)
        burstRaysPath.moveTo(35.7f, 24.3f)
        burstRaysPath.lineTo(47f, 13f)

        // Lotus: five petals over a base arc.
        lotusPath.rewind()
        lotusPath.moveTo(20f, 3f)
        lotusPath.cubicTo(16.5f, 8f, 16.5f, 13f, 20f, 17.5f)
        lotusPath.cubicTo(23.5f, 13f, 23.5f, 8f, 20f, 3f)
        lotusPath.close()
        lotusPath.moveTo(11f, 7f)
        lotusPath.cubicTo(12.5f, 12f, 15.5f, 15.5f, 19.5f, 17.5f)
        lotusPath.cubicTo(13.5f, 18f, 9.5f, 14.5f, 9f, 9.5f)
        lotusPath.close()
        lotusPath.moveTo(29f, 7f)
        lotusPath.cubicTo(27.5f, 12f, 24.5f, 15.5f, 20.5f, 17.5f)
        lotusPath.cubicTo(26.5f, 18f, 30.5f, 14.5f, 31f, 9.5f)
        lotusPath.close()
        lotusPath.moveTo(3f, 12.5f)
        lotusPath.cubicTo(7.5f, 12.5f, 13f, 14.5f, 18f, 17.5f)
        lotusPath.cubicTo(11f, 19f, 5.5f, 17f, 3f, 12.5f)
        lotusPath.close()
        lotusPath.moveTo(37f, 12.5f)
        lotusPath.cubicTo(32.5f, 12.5f, 27f, 14.5f, 22f, 17.5f)
        lotusPath.cubicTo(29f, 19f, 34.5f, 17f, 37f, 12.5f)
        lotusPath.close()
        lotusBasePath.rewind()
        lotusBasePath.moveTo(8f, 20.5f)
        lotusBasePath.quadTo(20f, 24f, 32f, 20.5f)

        // Diya: outer and inner flame, bowl, rim ellipse.
        flamePath.rewind()
        flamePath.moveTo(18f, 1f)
        flamePath.cubicTo(14.8f, 4.8f, 14.5f, 7.8f, 18f, 10.5f)
        flamePath.cubicTo(21.5f, 7.8f, 21.2f, 4.8f, 18f, 1f)
        flamePath.close()
        flameInnerPath.rewind()
        flameInnerPath.moveTo(18f, 4.5f)
        flameInnerPath.cubicTo(16.6f, 6.2f, 16.5f, 7.6f, 18f, 8.9f)
        flameInnerPath.cubicTo(19.5f, 7.6f, 19.4f, 6.2f, 18f, 4.5f)
        flameInnerPath.close()
        diyaBowlPath.rewind()
        diyaBowlPath.moveTo(6f, 14f)
        diyaBowlPath.cubicTo(6f, 19.5f, 11f, 23f, 18f, 23f)
        diyaBowlPath.cubicTo(25f, 23f, 30f, 19.5f, 30f, 14f)
        diyaBowlPath.quadTo(24f, 16.4f, 18f, 16.4f)
        diyaBowlPath.quadTo(12f, 16.4f, 6f, 14f)
        diyaBowlPath.close()
        diyaRim.set(18f - 12f, 14f - 2.6f, 18f + 12f, 14f + 2.6f)

        // Diwali dressing: radial halo behind each flame. Built in the diya's mock units,
        // so the draw-time canvas scale sizes it for both diya widths. Diwali-only: only
        // drawDiya reads it, so skip the allocation for other families.
        if (family == ThemePackDecor.FAMILY_DIWALI) {
            val accentR = Color.red(accent)
            val accentG = Color.green(accent)
            val accentB = Color.blue(accent)
            flameGlowShader = RadialGradient(
                18f, 7f, FLAME_GLOW_RADIUS_DP,
                Color.argb((FLAME_GLOW_ALPHA * 255).toInt(), accentR, accentG, accentB),
                Color.argb(0, accentR, accentG, accentB),
                Shader.TileMode.CLAMP,
            )
        }

        // Rockets: cream trail fading tail-wards, warm head glow, muzzle flash. Diwali-only,
        // like the flame halo: nothing else reads them.
        if (family == ThemePackDecor.FAMILY_DIWALI) {
            val creamR = Color.red(ROCKET_CREAM)
            val creamG = Color.green(ROCKET_CREAM)
            val creamB = Color.blue(ROCKET_CREAM)
            rocketTrailShader = LinearGradient(
                0f, 0f, 0f, ROCKET_TRAIL_LENGTH_DP,
                ROCKET_CREAM,
                Color.argb(0, creamR, creamG, creamB),
                Shader.TileMode.CLAMP,
            )
            val accentR = Color.red(accent)
            val accentG = Color.green(accent)
            val accentB = Color.blue(accent)
            rocketGlowShader = RadialGradient(
                0f, 0f, ROCKET_GLOW_RADIUS_DP,
                Color.argb((ROCKET_GLOW_ALPHA * 255).toInt(), accentR, accentG, accentB),
                Color.argb(0, accentR, accentG, accentB),
                Shader.TileMode.CLAMP,
            )
            val flashR = Color.red(ROCKET_FLASH)
            val flashG = Color.green(ROCKET_FLASH)
            val flashB = Color.blue(ROCKET_FLASH)
            rocketFlashShader = RadialGradient(
                0f, 0f, ROCKET_FLASH_RADIUS_DP,
                Color.argb((ROCKET_FLASH_ALPHA * 255).toInt(), flashR, flashG, flashB),
                Color.argb(0, flashR, flashG, flashB),
                Shader.TileMode.CLAMP,
            )
        } else {
            rocketTrailShader = null
            rocketGlowShader = null
            rocketFlashShader = null
        }

        // Rangoli dot arcs: shallow arc shape and twinkle delays, staggered dot by dot.
        // The X centres follow the diya layout and are set in [buildDiyaLayout].
        var dot = 0
        for (gap in 0 until RANGOLI_GAP_COUNT) {
            for (d in 0 until 3) {
                rangoliDotYs[dot] = if (d == 1) -RANGOLI_DOT_RISE_DP else 0f
                rangoliDotDelays[dot] = -dot * RANGOLI_TWINKLE_STAGGER_S
                dot++
            }
        }

        // String lights: the wave is drawn stretched to the view width via [waveMatrix].
        lightsWavePath.rewind()
        lightsWavePath.moveTo(0f, 6f)
        lightsWavePath.quadTo(85f, 26f, 170f, 12f)
        lightsWavePath.quadTo(255f, 26f, 340f, 6f)

        // Santa hat: crown, band, pom.
        hatCrownPath.rewind()
        hatCrownPath.moveTo(9f, 34f)
        hatCrownPath.cubicTo(11f, 18f, 23f, 6f, 40f, 4f)
        hatCrownPath.cubicTo(50f, 3f, 55f, 8f, 53f, 14f)
        hatCrownPath.cubicTo(44f, 13f, 30f, 21f, 25f, 34f)
        hatCrownPath.close()
        hatBand.set(4f, 31f, 31f, 40f)

        // Tree: three stacked triangles, trunk, star.
        treeTrianglesPath.rewind()
        treeTrianglesPath.moveTo(22f, 3f)
        treeTrianglesPath.lineTo(31f, 17f)
        treeTrianglesPath.lineTo(13f, 17f)
        treeTrianglesPath.close()
        treeTrianglesPath.moveTo(22f, 12f)
        treeTrianglesPath.lineTo(36f, 32f)
        treeTrianglesPath.lineTo(8f, 32f)
        treeTrianglesPath.close()
        treeTrianglesPath.moveTo(22f, 23f)
        treeTrianglesPath.lineTo(40f, 47f)
        treeTrianglesPath.lineTo(4f, 47f)
        treeTrianglesPath.close()
        treeTrunk.set(19f, 47f, 26f, 55f)
        treeStarPath.rewind()
        treeStarPath.moveTo(22f, 0f)
        treeStarPath.lineTo(23.4f, 3.2f)
        treeStarPath.lineTo(26.8f, 3.6f)
        treeStarPath.lineTo(24.3f, 5.9f)
        treeStarPath.lineTo(25f, 9.3f)
        treeStarPath.lineTo(22f, 7.6f)
        treeStarPath.lineTo(19f, 9.3f)
        treeStarPath.lineTo(19.7f, 5.9f)
        treeStarPath.lineTo(17.2f, 3.6f)
        treeStarPath.lineTo(20.6f, 3.2f)
        treeStarPath.close()

        // Gifts: tall accent box with lid and ribbon, small accentSecondary box likewise.
        giftBoxTall.set(2f, 14f, 20f, 30f)
        giftLidTall.set(0f, 10f, 22f, 16f)
        giftRibbonTall.set(9.5f, 10f, 12.5f, 30f)
        giftBoxSmall.set(22f, 22f, 36f, 30f)
        giftLidSmall.set(20.5f, 19.5f, 37.5f, 23.5f)
        giftRibbonSmall.set(27.5f, 19.5f, 30.1f, 30f)
    }

    companion object {
        // Fixed mock colours (docs/design/themes/mocks/index.html, decorHTML).
        private const val GHOST_FEATURES = 0xFF2A1233.toInt()
        private const val PUMPKIN_FACE_LIGHT = 0xFF5C2A10.toInt()
        private const val PUMPKIN_FACE_DARK = 0xFFFFD54A.toInt()
        private const val DIYA_BOWL = 0xFF8A3B1E.toInt()
        private const val DIYA_RIM = 0xFFC96A2E.toInt()
        private const val FLAME_INNER = 0xFFFFE9A8.toInt()
        private const val HAT_RED = 0xFFD0343A.toInt()
        private const val TREE_GREEN = 0xFF2F7D46.toInt()
        private const val TRUNK_BROWN = 0xFF7A4A21.toInt()

        /** Flame flicker period and per-flame phase step, in seconds (mock: flicker 1.5s). */
        private const val FLICKER_PERIOD_S = 1.5f
        private const val FLAME_PHASE_STEP_S = 0.37f

        // Diwali dressing extras: flame halo, bottom light strip, rangoli dots, diya row.
        private const val DIYA_COUNT = 6
        private const val DIYA_EDGE_MARGIN_DP = 16f
        private const val FLAME_GLOW_RADIUS_DP = 11f
        private const val FLAME_GLOW_ALPHA = 0.18f
        private const val LIGHT_STRIP_HEIGHT_DP = 26f
        private const val LIGHT_STRIP_ALPHA = 0.10f
        private const val RANGOLI_GAP_COUNT = 5
        private const val RANGOLI_DOT_RADIUS_DP = 1.2f
        private const val RANGOLI_DOT_SPACING_DP = 4f
        private const val RANGOLI_DOT_RISE_DP = 1.8f
        private const val RANGOLI_BOTTOM_DP = 14f
        private const val RANGOLI_DOT_ALPHA = 0.30f
        private const val RANGOLI_TWINKLE_PERIOD_S = 2.6f
        private const val RANGOLI_TWINKLE_STAGGER_S = 0.5f

        // Diwali rockets (diwali-rockets-mock.html): flight area, timing, trail and bursts.
        // Positions are fractions of the view in mock-dp; sizes are dp in the mock's space.
        private const val ROCKET_CREAM = 0xFFFFD9A0.toInt()
        private const val ROCKET_FLASH = 0xFFFFE9A8.toInt()
        private const val ROCKET_START_Y_FRACTION = 0.86f
        private const val ROCKET_MIN_X_FRACTION = 0.35f
        private const val ROCKET_MAX_X_FRACTION = 0.88f
        private const val ROCKET_MIN_APEX_FRACTION = 0.12f
        private const val ROCKET_MAX_APEX_FRACTION = 0.33f
        private const val ROCKET_RISE_MIN_MS = 1500L
        private const val ROCKET_RISE_JITTER_MS = 500L
        private const val ROCKET_STAGGER_MIN_MS = 180L
        private const val ROCKET_STAGGER_JITTER_MS = 250L
        private const val ROCKET_TRAIL_WIDTH_DP = 3f
        private const val ROCKET_TRAIL_LENGTH_DP = 44f
        private const val ROCKET_GLOW_RADIUS_DP = 5f
        private const val ROCKET_GLOW_ALPHA = 0.5f
        private const val ROCKET_BURST_SIZE_DP = 54f
        private const val BOOM_BOX_UNITS = 60f
        private const val BOOM_TWINKLE_PERIOD_S = 1.1f
        private const val ROCKET_FLASH_MS = 500f
        private const val ROCKET_FLASH_RADIUS_DP = 10f
        private const val ROCKET_FLASH_ALPHA = 0.95f
        private const val WILLOW_FALL_MIN_DP = 20f
        private const val WILLOW_FALL_MAX_DP = 42f
        private const val WILLOW_DRIFT_DP = 7f
        private const val WILLOW_FALL_MIN_MS = 1500f
        private const val WILLOW_FALL_MAX_MS = 2400f
        private const val ROCKET_PATTERN_COUNT = 5
        private const val PATTERN_RING = 1
        private const val PATTERN_WILLOW = 2
        private const val PATTERN_PALM = 3
        private const val PATTERN_DOUBLE_RING = 4
        private const val TWO_PI_F = (2.0 * PI).toFloat()

        private const val SNOWFLAKE_COUNT = 8
        private const val SNOW_FALL_BASE_S = 7f
    }
}
