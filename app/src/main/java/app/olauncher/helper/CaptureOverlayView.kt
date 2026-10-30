package app.olauncher.helper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import app.olauncher.data.store.OverlayGestures
import app.olauncher.data.store.OverlayTransform
import app.olauncher.data.store.Point
import app.olauncher.data.store.SavedMarks
import app.olauncher.data.store.ScreenBox
import app.olauncher.data.store.ScreenWord
import app.olauncher.data.store.Selection
import app.olauncher.data.store.WordHitTest
import kotlin.math.abs
import kotlin.math.max

/**
 * The Circle-to-Search overlay (#592): draws the frozen frame dimmed, highlights the
 * current selection and marks saved words with a dot. It owns the gesture handling but
 * none of the geometry decisions — taps, strokes and line extensions are delegated to
 * [WordHitTest]; the coordinate mapping ([OverlayTransform]) and the close-swipe
 * classifier ([OverlayGestures]) live in data/store, this view only wires and draws.
 *
 * Coordinate spaces: word boxes arrive in accessibility screen pixels, the frame bitmap
 * may differ slightly from the display (within ScreenMapping's tolerance). The bitmap is
 * fitted — never cropped — into the view (letterboxed), and touch points are mapped back
 * through the same transform before hit-testing.
 */
class CaptureOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /**
     * The pending words the owner has picked: drawn as a translucent fill with an outline.
     * Set by the owner from its shared pending set, so the overlay shows every pending pick.
     */
    var pendingWords: Set<ScreenWord> = emptySet()
        set(value) {
            field = value
            invalidate()
        }

    /** The word the meaning card is showing; its outline is drawn thicker than the rest. */
    var focusedWord: ScreenWord? = null
        set(value) {
            field = value
            invalidate()
        }

    /** Accent colour of the selection highlight and the saved-word dots. */
    var accentColor: Int = Color.WHITE
        set(value) {
            field = value
            invalidate()
        }

    /** Fired on a tap: the word under the finger, or null when empty space was tapped. */
    var onTap: ((ScreenWord?) -> Unit)? = null

    /** Fired with the words a stroke/circle or a long-press drag covers (add-only semantics). */
    var onAddSelection: ((Selection) -> Unit)? = null

    /** Fired on a fast downward swipe: the owner closes the capture. */
    var onCloseSwipe: (() -> Unit)? = null

    /** The in-progress long-press line drag, drawn as a transient highlight until committed. */
    private var dragSelection: Selection? = null

    private var frame: Bitmap? = null
    private var words: List<ScreenWord> = emptyList()
    private var savedKeys: Set<String> = emptySet()
    private var scaleX = 1f
    private var scaleY = 1f

    // Fitted frame placement and screen↔view mapping; null until the view has a size.
    private var transform: OverlayTransform? = null

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressMillis = ViewConfiguration.getLongPressTimeout().toLong()

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val scrimPaint = Paint().apply { color = SCRIM_COLOR }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = STROKE_WIDTH * density
    }
    private val highlightFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Gesture state.
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var longPress = false
    private val strokePoints = ArrayList<Point>()

    /**
     * A held finger on a word: anchors the selection on the pressed word — the head is
     * always the word under the finger, never a leftover selection — so a following drag
     * extends whole lines from it. A hold on empty space never anchors: the drag stays a
     * normal stroke instead of extending from the stale selection.
     */
    private val longPressRunnable = Runnable {
        val word = WordHitTest.tap(words, toScreenPoint(downX, downY), screenSlop())
        if (word != null) {
            longPress = true
            dragSelection = Selection.Single(word)
            invalidate()
        }
    }

    /** Hands the frozen frame and the boxed words to the overlay. Call after layout or rely on redraw. */
    fun setFrame(bitmap: Bitmap, words: List<ScreenWord>, scaleX: Float, scaleY: Float) {
        this.frame = bitmap
        this.words = words
        this.scaleX = scaleX
        this.scaleY = scaleY
        updateTransform()
        invalidate()
    }

    /** Drops the frame (before the owner recycles the bitmap, e.g. when opening the reader). */
    fun releaseFrame() {
        frame = null
        words = emptyList()
        dragSelection = null
        invalidate()
    }

    /** Marks the saved words: one dot per word whose key ([SavedMarks.keyOf]) is in [texts]. */
    fun setSavedTexts(texts: Collection<String>) {
        savedKeys = texts.mapTo(HashSet()) { it.lowercase() }
        invalidate()
    }

    /** The word's box in this view's coordinates (the space the meaning card is placed in). */
    fun viewBoxOf(word: ScreenWord): ScreenBox? {
        val box = word.box ?: return null
        return transform?.toView(box)
    }

    // -- drawing -------------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val bitmap = frame ?: return
        if (width == 0 || height == 0) return
        updateTransform()
        val t = transform ?: return
        val right = t.drawLeft + bitmap.width * t.fitScale
        val bottom = t.drawTop + bitmap.height * t.fitScale
        canvas.drawBitmap(bitmap, null, RectF(t.drawLeft, t.drawTop, right, bottom), bitmapPaint)
        // The dimmed screenshot: one uniform scrim over the bitmap and the letterbox bars.
        canvas.drawColor(SCRIM_COLOR)

        dotPaint.color = accentColor
        for (word in words) {
            val box = word.box ?: continue
            // Same key as the save: "Regierung." must light up for saved "Regierung".
            if (SavedMarks.keyOf(word) !in savedKeys) continue
            val rect = toViewRect(box, t)
            val radius = 3 * density
            canvas.drawCircle(rect.right, rect.bottom, radius, dotPaint)
        }

        highlightPaint.color = accentColor
        highlightFillPaint.color = accentColor
        highlightFillPaint.alpha = HIGHLIGHT_FILL_ALPHA
        // Pending picks keep the filled+outlined look; the transient long-press drag is
        // drawn the same way but only until the owner commits it on finger-up.
        for (word in words) {
            if (word !in pendingWords) continue
            drawPendingFill(canvas, t, word)
        }
        dragFillWords(dragSelection?.words, pendingWords).forEach { drawPendingFill(canvas, t, it) }
        // The focused word (the meaning card's word) gets a thicker outline on top.
        focusedWord?.let { word ->
            val rect = highlightRect(t, word) ?: return@let
            highlightPaint.strokeWidth = FOCUS_STROKE * density
            canvas.drawRoundRect(rect, 6 * density, 6 * density, highlightPaint)
            highlightPaint.strokeWidth = STROKE_WIDTH * density
        }
    }

    private fun drawPendingFill(canvas: Canvas, t: OverlayTransform, word: ScreenWord) {
        val rect = highlightRect(t, word) ?: return
        canvas.drawRoundRect(rect, 6 * density, 6 * density, highlightFillPaint)
        canvas.drawRoundRect(rect, 6 * density, 6 * density, highlightPaint)
    }

    private fun highlightRect(t: OverlayTransform, word: ScreenWord): RectF? {
        val box = word.box ?: return null
        val rect = toViewRect(box, t)
        val pad = 2 * density
        rect.inset(-pad, -pad)
        return rect
    }

    private fun updateTransform() {
        val bitmap = frame ?: return
        if (width == 0 || height == 0) return
        transform = OverlayTransform(width, height, bitmap.width, bitmap.height, scaleX, scaleY)
    }

    private fun toViewRect(box: ScreenBox, t: OverlayTransform): RectF {
        // The tested transform is the only implementation — a float re-implementation
        // here could drift from it (e.g. a swapped kx/ky) while the tests stay green.
        val view = t.toViewF(box)
        return RectF(view[0], view[1], view[2], view[3])
    }

    // -- gestures ------------------------------------------------------------------------

    /**
     * Down/move/up tracking; on up a still finger is a tap ([WordHitTest.tap]) — fast or
     * held past the long-press timeout alike — and a dragged finger a stroke
     * ([WordHitTest.stroke]) reported to [onAddSelection]. A long-press drag extends the
     * selection by whole lines as the finger moves, anchored on the word under the press,
     * and commits it on finger-up. A tap is reported to [onTap] — null for empty space —
     * and a stroke that touches nothing reports nothing. Text without word boxes cannot be
     * hit at all, so such a tap is indistinguishable from empty space (a node set with no
     * boxed words never reaches this view; the activity opens the reader instead).
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                moved = false
                longPress = false
                strokePoints.clear()
                strokePoints += toScreenPoint(event.x, event.y)
                postDelayed(longPressRunnable, longPressMillis)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!moved && distanceFromDown(event) > touchSlop) {
                    moved = true
                    removeCallbacks(longPressRunnable)
                }
                when {
                    longPress -> extendSelectionByLine(event)
                    moved -> recordStrokePoint(event)
                }
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                if (longPress && moved) {
                    // Long-press drag: line extensions were fed continuously during the
                    // drag and drawn as a transient highlight; commit them once on
                    // finger-up (add-only) and drop the transient.
                    dragSelection?.let { onAddSelection?.invoke(it) }
                    dragSelection = null
                    strokePoints.clear()
                    return true
                }
                // A slow tap (long-press fired, finger never moved) resolves like a
                // normal tap — the runnable above already re-anchored the selection.
                if (!moved) performClick()
                finishGesture(event)
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                dragSelection = null
                strokePoints.clear()
            }
        }
        return true
    }

    /** Keep lint's click contract: taps are resolved in [finishGesture], this only appeases a11y. */
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun finishGesture(event: MotionEvent) {
        val elapsed = event.eventTime - event.downTime
        if (isCloseSwipe(event, elapsed)) {
            strokePoints.clear()
            onCloseSwipe?.invoke()
            return
        }
        if (!moved) {
            val word = WordHitTest.tap(words, toScreenPoint(event.x, event.y), screenSlop())
            onTap?.invoke(word)
        } else {
            // The up point completes a short stroke that never sampled a second point.
            val last = strokePoints.lastOrNull()
            val up = toScreenPoint(event.x, event.y)
            if (last != up) strokePoints += up
            WordHitTest.stroke(words, strokePoints)?.let { onAddSelection?.invoke(it) }
        }
        strokePoints.clear()
    }

    private fun extendSelectionByLine(event: MotionEvent) {
        val current = dragSelection ?: return
        val extended = WordHitTest.extendByLine(current, toScreenPoint(event.x, event.y), words)
        if (extended != current) {
            dragSelection = extended
            invalidate()
        }
    }

    private fun recordStrokePoint(event: MotionEvent) {
        val point = toScreenPoint(event.x, event.y)
        val last = strokePoints.lastOrNull() ?: return
        val minStep = max(2, screenSlop() / 2)
        val dx = point.x - last.x
        val dy = point.y - last.y
        if (dx * dx + dy * dy > minStep * minStep) strokePoints += point
    }

    private fun isCloseSwipe(event: MotionEvent, elapsedMillis: Long): Boolean =
        moved && OverlayGestures.isCloseSwipe(
            viewHeight = height,
            downX = downX,
            downY = downY,
            upX = event.x,
            upY = event.y,
            elapsedMillis = elapsedMillis,
        )

    // -- coordinate transforms -----------------------------------------------------------

    private fun toScreenPoint(x: Float, y: Float): Point =
        transform?.toScreen(x, y) ?: Point(0, 0)

    /** The touch slop expressed in accessibility screen pixels, as the hit-test expects. */
    private fun screenSlop(): Int = transform?.slopToScreen(touchSlop) ?: 1

    private fun distanceFromDown(event: MotionEvent): Float {
        val dx = event.x - downX
        val dy = event.y - downY
        return max(abs(dx), abs(dy))
    }

    companion object {
        /** ~40 % black scrim over the frozen frame. */
        private const val SCRIM_COLOR = 0x66000000.toInt()
        private const val HIGHLIGHT_FILL_ALPHA = 60
        /** Outline width in dp for a pending word and for the focused (card) word. */
        private const val STROKE_WIDTH = 3f
        private const val FOCUS_STROKE = 5f
    }
}

/**
 * The words the transient long-press drag pass paints. A word that is already pending
 * keeps its fill from the pending loop — painting it again stacks the translucent alphas
 * and reads darker than its neighbours — so the drag pass skips it.
 */
internal fun dragFillWords(dragWords: List<ScreenWord>?, pendingWords: Set<ScreenWord>): List<ScreenWord> =
    dragWords.orEmpty().filter { it !in pendingWords }
