package app.olauncher.ui.today.cards

import android.content.Context
import android.content.res.Configuration
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import androidx.core.view.isVisible
import app.olauncher.R
import app.olauncher.glance.RAIN_LAYERS_FAR
import app.olauncher.glance.RAIN_LAYERS_MID
import app.olauncher.glance.RAIN_LAYERS_NEAR
import app.olauncher.glance.RainMotionGate
import app.olauncher.glance.RainStreakLayer
import app.olauncher.glance.RainStreakPlan
import app.olauncher.glance.WeatherCardReadability
import app.olauncher.glance.WeatherCardStyle
import app.olauncher.helper.SystemMotion
import app.olauncher.helper.WeatherLook
import kotlin.math.roundToInt

/**
 * Renders rain streaks and bottom ripples for rainy weather looks (LIGHT_RAIN, HEAVY_RAIN, STORM).
 *
 * Source of truth: docs/design/today-weather.html lines 213–239 and 915–995.
 */
class WeatherRainView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val density: Float
        get() = resources.displayMetrics.density

    var currentLook: WeatherLook? = null
        private set

    var plan: RainStreakPlan = RainStreakPlan.EMPTY
        private set

    var keepOutRects: List<KeepOutRect> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    var keepOutSupplier: (() -> List<KeepOutRect>)? = null

    var isAnimating: Boolean = false
        private set

    var animTimeSeconds: Float = 0f
        private set

    var animatorsEnabledOverride: Boolean? = null
    var cardVisibleOverride: Boolean? = null

    private var isAttached: Boolean = false
    private var isAggregatedVisible: Boolean = true
    private var lastFrameNanos: Long = 0L

    private var lastPlanLook: WeatherLook? = null
    private var lastPlanWidth: Int = 0
    private var lastPlanHeight: Int = 0

    private var currentStyle: WeatherCardStyle? = null
    private var lastConfigThemeIsDark: Boolean? = null

    private val clipPath = Path()
    private val clipRectF = RectF()
    private val streakRectF = RectF()
    private val rippleRectF = RectF()

    private val layerPaint = Paint().apply {
        alpha = (RainStreakPlan.RAIN_OPACITY * 255f).roundToInt()
    }

    private val farPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val midPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val nearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    private var keepOutPaint: Paint = WeatherKeepOut.createKeepOutPaint(
        sigma = WeatherKeepOut.SIGMA * density,
        peakAlpha = WeatherKeepOut.PEAK_ALPHA,
    )

    init {
        // Accessibility is handled by the weather card as a whole.
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun isCardVisible(): Boolean {
        cardVisibleOverride?.let { return it }
        return isAttached && isAggregatedVisible && (visibility == VISIBLE) && (windowVisibility == VISIBLE)
    }

    val animatorsEnabled: Boolean
        get() = animatorsEnabledOverride ?: SystemMotion.isAnimationsEnabled(context)

    fun setLook(look: WeatherLook?) {
        val changed = currentLook != look
        currentLook = look
        val isRain = RainMotionGate.isRainLook(look)
        isVisible = isRain
        if (changed) {
            rebuildPlan()
            updateAnimationState()
        }
    }

    fun onResume() {
        updateAnimationState()
    }

    fun rebuildPlan() {
        val look = currentLook
        if (look == null || !RainMotionGate.isRainLook(look) || width <= 0 || height <= 0) {
            plan = RainStreakPlan.EMPTY
            lastPlanLook = null
            lastPlanWidth = 0
            lastPlanHeight = 0
            invalidate()
            return
        }

        if (look == lastPlanLook && width == lastPlanWidth && height == lastPlanHeight && plan != RainStreakPlan.EMPTY) {
            return
        }

        lastPlanLook = look
        lastPlanWidth = width
        lastPlanHeight = height

        val d = density
        val widthDp = width / d
        val heightDp = height / d
        val seed = RainMotionGate.seedFor(look, widthDp, heightDp)

        plan = RainStreakPlan.plan(look, widthDp, heightDp, seed)
        updatePaints()
        invalidate()
    }

    private fun updatePaints() {
        val look = currentLook ?: return
        val themeIsDark = isThemeDark()
        val style = WeatherCardReadability.styleFor(look, themeIsDark)
        currentStyle = style
        lastConfigThemeIsDark = themeIsDark

        val d = density

        // Far layer: linear-gradient(transparent, var(--rain-far)), filter: blur(.6px)
        val hFar = RAIN_LAYERS_FAR.length * d
        val colorFar = style.rainFar
        val colorFarTrans = colorFar and 0x00FFFFFF
        farPaint.shader = LinearGradient(0f, 0f, 0f, hFar, colorFarTrans, colorFar, Shader.TileMode.CLAMP)
        if (d > 0f) {
            farPaint.maskFilter = BlurMaskFilter(0.6f * d, BlurMaskFilter.Blur.NORMAL)
        }

        // Mid layer: linear-gradient(transparent, var(--rain-mid))
        val hMid = RAIN_LAYERS_MID.length * d
        val colorMid = style.rainMid
        val colorMidTrans = colorMid and 0x00FFFFFF
        midPaint.shader = LinearGradient(0f, 0f, 0f, hMid, colorMidTrans, colorMid, Shader.TileMode.CLAMP)
        midPaint.maskFilter = null

        // Near layer: linear-gradient(transparent, var(--rain-near))
        val hNear = RAIN_LAYERS_NEAR.length * d
        val colorNear = style.rainNear
        val colorNearTrans = colorNear and 0x00FFFFFF
        nearPaint.shader = LinearGradient(0f, 0f, 0f, hNear, colorNearTrans, colorNear, Shader.TileMode.CLAMP)
        nearPaint.maskFilter = null

        // Ripples: 1px border var(--rain-near)
        ripplePaint.strokeWidth = 1f * d

        // Keep-out soft hole paint
        keepOutPaint = WeatherKeepOut.createKeepOutPaint(
            sigma = WeatherKeepOut.SIGMA * d,
            peakAlpha = WeatherKeepOut.PEAK_ALPHA,
        )
    }

    private fun isThemeDark(): Boolean {
        return (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
    }

    fun updateAnimationState() {
        val look = currentLook
        val isRain = RainMotionGate.isRainLook(look)
        val visible = isCardVisible()
        val enabled = animatorsEnabled
        val should = RainMotionGate.shouldAnimate(isRain, visible, enabled)

        if (should != isAnimating) {
            isAnimating = should
            if (should) {
                lastFrameNanos = 0L
                postInvalidateOnAnimation()
            } else {
                invalidate()
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isAttached = true
        updateAnimationState()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        isAttached = false
        updateAnimationState()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        isAggregatedVisible = isVisible
        updateAnimationState()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updateAnimationState()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildPlan()
        updateAnimationState()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val look = currentLook ?: return
        if (!RainMotionGate.isRainLook(look) || width <= 0 || height <= 0 || plan == RainStreakPlan.EMPTY) {
            return
        }

        if (lastConfigThemeIsDark != isThemeDark() || currentStyle == null) {
            updatePaints()
        }

        val d = density
        val widthDp = width / d
        val heightDp = height / d

        val animating = RainMotionGate.shouldAnimate(look, isCardVisible(), animatorsEnabled)
        if (animating) {
            val now = System.nanoTime()
            if (lastFrameNanos > 0L) {
                val dt = (now - lastFrameNanos) / 1_000_000_000f
                animTimeSeconds += dt.coerceIn(0f, 0.25f)
            }
            lastFrameNanos = now
        } else {
            lastFrameNanos = 0L
        }

        // Streaks and ripples clip to the card's rounded bounds.
        val cornerRadius = resources.getDimension(R.dimen.radiusMedium)
        clipPath.reset()
        clipRectF.set(0f, 0f, width.toFloat(), height.toFloat())
        clipPath.addRoundRect(clipRectF, cornerRadius, cornerRadius, Path.Direction.CW)
        canvas.clipPath(clipPath)

        // Overall layer opacity 0.7
        val saveCount = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), layerPaint)

        // Draw streaks: far (blurred), mid, near
        drawStreakLayer(canvas, plan.far, d, widthDp, farPaint)
        drawStreakLayer(canvas, plan.mid, d, widthDp, midPaint)
        drawStreakLayer(canvas, plan.near, d, widthDp, nearPaint)

        // Draw ripples
        drawRipples(canvas, d, widthDp, heightDp)

        // Cut soft keep-out holes behind text
        val rects = if (keepOutRects.isNotEmpty()) keepOutRects else keepOutSupplier?.invoke().orEmpty()
        if (rects.isNotEmpty()) {
            WeatherKeepOut.applyKeepOut(canvas, rects, keepOutPaint, cornerRadius = WeatherKeepOut.PAD * d)
        }

        canvas.restoreToCount(saveCount)

        // Animate continuously only when all motion gates are open; otherwise stop.
        if (animating) {
            postInvalidateOnAnimation()
        }
    }

    private fun drawStreakLayer(
        canvas: Canvas,
        layer: RainStreakLayer,
        d: Float,
        widthDp: Float,
        paint: Paint,
    ) {
        if (layer.streaks.isEmpty()) return
        val streakW = layer.width * d
        val streakH = layer.length * d
        val streakRadius = 2f * d
        val travelDp = plan.travel

        for (streak in layer.streaks) {
            if (streak.dur <= 0f) continue
            val elapsed = animTimeSeconds - streak.delay
            val mod = elapsed % streak.dur
            val p = if (mod < 0f) (mod + streak.dur) / streak.dur else mod / streak.dur

            val startXDp = (streak.left / 100f) * widthDp
            val startYDp = -60f
            val curXDp = startXDp + layer.dx * p
            val curYDp = startYDp + travelDp * p

            val curX = curXDp * d
            val curY = curYDp * d

            canvas.save()
            canvas.translate(curX, curY)
            canvas.rotate(layer.tilt, streakW / 2f, streakH / 2f)
            streakRectF.set(0f, 0f, streakW, streakH)
            canvas.drawRoundRect(streakRectF, streakRadius, streakRadius, paint)
            canvas.restore()
        }
    }

    private fun drawRipples(
        canvas: Canvas,
        d: Float,
        widthDp: Float,
        heightDp: Float,
    ) {
        if (plan.ripples.isEmpty()) return
        val style = currentStyle ?: return
        val baseColor = style.rainNear
        val baseAlpha = android.graphics.Color.alpha(baseColor)
        val colorRgb = baseColor and 0x00FFFFFF

        for (ripple in plan.ripples) {
            if (ripple.dur <= 0f) continue
            val elapsed = animTimeSeconds - ripple.delay
            val mod = elapsed % ripple.dur
            val rawP = if (mod < 0f) (mod + ripple.dur) / ripple.dur else mod / ripple.dur

            // ease-out: 1 - (1 - t)^2
            val easedP = 1f - (1f - rawP) * (1f - rawP)
            val scale = 0.1f + 0.9f * easedP
            val alphaMul = 0.9f * (1f - easedP)

            val alphaInt = (baseAlpha * alphaMul).roundToInt().coerceIn(0, 255)
            if (alphaInt <= 0) continue

            ripplePaint.color = (alphaInt shl 24) or colorRgb

            val centerXDp = (ripple.left / 100f) * widthDp
            val centerYDp = heightDp - ripple.bottom - 4f

            val centerX = centerXDp * d
            val centerY = centerYDp * d

            canvas.save()
            canvas.translate(centerX, centerY)
            canvas.scale(scale, scale)
            rippleRectF.set(-15f * d, -4f * d, 15f * d, 4f * d)
            canvas.drawOval(rippleRectF, ripplePaint)
            canvas.restore()
        }
    }
}
