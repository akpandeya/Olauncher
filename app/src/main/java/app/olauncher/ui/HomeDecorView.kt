package app.olauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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

/**
 * Seasonal dressing over the home screen for decorated theme packs (Halloween, Diwali,
 * Christmas): a full-bleed, non-touchable Canvas overlay ported from the HTML mock
 * (docs/design/themes/mocks/index.html, `decorHTML`). Mock px units are dp. The view draws
 * below the text layer, so nothing it draws can hide labels; plain packs draw nothing.
 *
 * Animation is one self-rescheduling frame loop capped at [ThemePackDecor.MAX_FPS]; with
 * [configure] `animate = false` (e-ink, system animations off) a single static frame is drawn.
 * All paths are built in [configure]; per-frame work is transform and alpha only.
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

    // Burst twinkle dot centres (60x60 viewBox) and their CSS animation delays, in seconds.
    private val burstDotXs = floatArrayOf(55f, 48.5f, 30f, 11.5f, 5f, 11.5f, 30f, 48.5f)
    private val burstDotYs = floatArrayOf(30f, 48.5f, 55f, 48.5f, 30f, 11.5f, 5f, 11.5f)
    private val burstDotDelays = floatArrayOf(-0.3f, -0.9f, -1.5f, -2.1f, -1.2f, -0.6f, -1.8f, -2.4f)

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
        lightPack = pack.mode == ThemePackCodec.MODE_LIGHT
        accent = ThemePackCodec.parseHex(pack.accent)
        accentSecondary = ThemePackCodec.parseHex(pack.accentSecondary)
        ink = ThemePackCodec.parseHex(pack.ink)
        inkMuted = ThemePackCodec.parseHex(pack.inkMuted)
        background = ThemePackCodec.parseHex(pack.background)
        snowColor = pack.snow?.let { ThemePackCodec.parseHex(it) } ?: 0
        buildGeometry()
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
        if (shouldRun) {
            animStartMs = SystemClock.uptimeMillis()
            postOnAnimationDelayed(frameRunnable, ThemePackDecor.FRAME_INTERVAL_MS)
        }
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

        // Diyas along the bottom edge, flames flickering (mock: flicker 1.5s, per-flame phase).
        drawDiya(canvas, viewW, viewH, left = 26f, width = 42f, flameIndex = 0, t)
        drawDiya(canvas, viewW, viewH, left = 108f, width = 36f, flameIndex = 1, t)
        drawDiya(canvas, viewW, viewH, left = 188f, width = 42f, flameIndex = 2, t)
        drawDiya(canvas, viewW, viewH, left = 268f, width = 36f, flameIndex = 3, t)
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
                // Glow behind the flame.
                fillPaint.color = accent
                fillPaint.alpha = (0.22f * 255).toInt()
                canvas.drawCircle(18f, 7f, 6f, fillPaint)
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

        private const val SNOWFLAKE_COUNT = 8
        private const val SNOW_FALL_BASE_S = 7f
    }
}
