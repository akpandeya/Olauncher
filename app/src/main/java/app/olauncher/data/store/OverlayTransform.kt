package app.olauncher.data.store

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The capture overlay's coordinate mapping between the frozen frame's accessibility
 * screen pixels and the view's pixels. The frame is fitted — never cropped — into the
 * view (letterboxed), and both directions of the mapping go through the same fit, so a
 * box round-trips unchanged. The accessibility scale (frame vs. display, from
 * [ScreenMapping.Verified]) is applied on top of the fit. Context-free and
 * JVM-testable; no Android types.
 */
class OverlayTransform(
    viewWidth: Int,
    viewHeight: Int,
    frameWidth: Int,
    frameHeight: Int,
    private val scaleX: Float,
    private val scaleY: Float,
) {
    /** The frame is fitted into the view; the letterbox bars fill what is left. */
    val fitScale: Float = min(viewWidth.toFloat() / frameWidth, viewHeight.toFloat() / frameHeight)

    /** Top-left corner of the fitted frame inside the view (centred letterbox). */
    val drawLeft: Float = (viewWidth - frameWidth * fitScale) / 2f
    val drawTop: Float = (viewHeight - frameHeight * fitScale) / 2f

    // Accessibility screen → view: view = a11y * k + draw offset.
    val kx = fitScale / scaleX
    val ky = fitScale / scaleY

    /** An accessibility box in this view's coordinates (the card-placement space). */
    fun toView(box: ScreenBox): ScreenBox = ScreenBox(
        (box.left * kx + drawLeft).roundToInt(),
        (box.top * ky + drawTop).roundToInt(),
        (box.right * kx + drawLeft).roundToInt(),
        (box.bottom * ky + drawTop).roundToInt(),
    )

    /** The same mapping as [toView] without the rounding (the drawing space). */
    fun toViewF(box: ScreenBox): FloatArray = floatArrayOf(
        box.left * kx + drawLeft,
        box.top * ky + drawTop,
        box.right * kx + drawLeft,
        box.bottom * ky + drawTop,
    )

    /** A view point in accessibility screen pixels (the space hit-testing expects). */
    fun toScreen(x: Float, y: Float): Point = Point(
        x = ((x - drawLeft) * scaleX / fitScale).roundToInt(),
        y = ((y - drawTop) * scaleY / fitScale).roundToInt(),
    )

    /** A distance in view pixels (e.g. the touch slop) in accessibility screen pixels. */
    fun slopToScreen(px: Int): Int = max(1, (px * scaleX / fitScale).roundToInt())
}
