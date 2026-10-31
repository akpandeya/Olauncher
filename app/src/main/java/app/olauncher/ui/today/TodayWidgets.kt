package app.olauncher.ui.today

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.util.SizeF
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.helper.getColorFromAttr
import app.olauncher.helper.showMessageDialog
import app.olauncher.helper.showPopupMenu
import kotlin.math.abs
import kotlin.math.roundToInt

/** Owns only Today's widget IDs; built-in cards and their preferences remain independent. */
internal class TodayWidgets(
    private val fragment: Fragment,
    restoring: Boolean,
    private val refresh: () -> Unit,
    private val bind: ActivityResultLauncher<Intent>,
    private val markAwaitingResult: () -> Unit,
    private val clearAwaitingResult: () -> Unit,
) {
    private val context = fragment.requireContext()
    private val prefs = Prefs(context)
    private val manager = AppWidgetManager.getInstance(context)
    // Activity's AppCompat inflater substitutes views that RemoteViews cannot update.
    private val hostContext = context.applicationContext
    private val host = object : AppWidgetHost(hostContext, HOST_ID) {
        override fun onCreateView(context: Context, id: Int, info: AppWidgetProviderInfo): AppWidgetHostView =
            LongPressWidgetView(context)
    }

    fun onConfigureResult(result: ActivityResult) {
        clearAwaitingResult()
        if (result.resultCode == Activity.RESULT_OK) complete() else discardPending()
    }

    fun onBindResult(result: ActivityResult) {
        clearAwaitingResult()
        if (result.resultCode == Activity.RESULT_OK) configureOrComplete() else discardPending()
    }

    init {
        // A fresh launch cannot still have an outstanding picker. A restored activity can.
        if (!restoring) discardPending()
        val owned = prefs.todayWidgetIds.toSet() + prefs.todayPendingWidgetId
        val orphans = host.appWidgetIds.filter { it !in owned }
        orphans.forEach(host::deleteAppWidgetId)
        // The host pools freed ids for reuse, so stale sizes must not outlive their widgets.
        if (orphans.isNotEmpty()) prefs.todayWidgetSizes = prefs.todayWidgetSizes - orphans.toSet()
    }

    fun start() = host.startListening()
    fun stop() = host.stopListening()

    /**
     * "Add widget": the in-app picker lists the installed providers. The id is
     * allocated only when one is picked, so cancelling leaves nothing to clean
     * up; a provider the system refuses to pre-bind falls back to the system
     * bind-confirmation dialog.
     */
    fun add() {
        if (prefs.todayPendingWidgetId > 0) return
        WidgetPickerDialog.show(context, { fragment.isAdded && !fragment.requireActivity().isFinishing }) { entry ->
            safely {
                val id = host.allocateAppWidgetId()
                prefs.todayPendingWidgetId = id
                val provider = entry.info.provider
                val allowed = if (entry.profile != null) manager.bindAppWidgetIdIfAllowed(id, entry.profile, provider, null)
                    else manager.bindAppWidgetIdIfAllowed(id, provider)
                if (allowed) {
                    configureOrComplete()
                } else {
                    markAwaitingResult()
                    bind.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
                        if (entry.profile != null) putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, entry.profile)
                    })
                }
            }
        }
    }

    private fun configureOrComplete() {
        val id = prefs.todayPendingWidgetId
        val info = manager.getAppWidgetInfo(id)
        if (id <= 0 || info == null) {
            fail()
            return
        }
        if (info.configure == null) complete() else safely {
            markAwaitingResult()
            // The system's IntentSender permits non-exported configuration activities and
            // launches them in the provider's profile rather than the launcher's profile.
            host.startAppWidgetConfigureActivityForResult(
                fragment.requireActivity(), id, 0, REQUEST_CONFIGURE, null,
            )
        }
    }

    private fun complete() {
        val id = prefs.todayPendingWidgetId
        if (id <= 0 || manager.getAppWidgetInfo(id) == null) {
            fail()
            return
        }
        prefs.todayWidgetIds = (prefs.todayWidgetIds + id).distinct()
        prefs.todayPendingWidgetId = -1
        clearAwaitingResult()
        refresh()
    }

    private fun discardPending() {
        val id = prefs.todayPendingWidgetId
        if (id > 0 && id !in prefs.todayWidgetIds) host.deleteAppWidgetId(id)
        prefs.todayPendingWidgetId = -1
        clearAwaitingResult()
    }

    private fun fail() {
        discardPending()
        Toast.makeText(context, R.string.today_widget_failed, Toast.LENGTH_LONG).show()
    }

    private inline fun safely(action: () -> Unit) {
        try {
            action()
        } catch (_: ActivityNotFoundException) {
            fail()
        } catch (_: SecurityException) {
            fail()
        } catch (_: IllegalArgumentException) {
            fail()
        }
    }

    fun showCardOptions(anchor: View, key: String, panelKeys: List<String>) {
        val allKeys = TodayCards.all.filterNot { it.inHero }.map { it.key } + cards().map { it.key }
        val order = (prefs.todayPanelOrder + allKeys).distinct()
        val pinned = key in prefs.todayPinnedKeys
        val moves = TodayOrder.panelMoves(order, key, panelKeys)
        anchor.showPopupMenu(
            configure = { menu ->
                menu.add(Menu.NONE, ACTION_TOGGLE_PIN, Menu.NONE, if (pinned) R.string.today_card_unpin else R.string.today_card_pin)
                if (!prefs.todayOrderSmart) {
                    if (moves.up != null) {
                        menu.add(Menu.NONE, ACTION_MOVE_UP, Menu.NONE, R.string.today_card_up)
                    }
                    if (moves.down != null) {
                        menu.add(Menu.NONE, ACTION_MOVE_DOWN, Menu.NONE, R.string.today_card_down)
                    }
                }
                val id = TodayOrder.widgetId(key)
                val info = id?.let { manager.getAppWidgetInfo(it) }
                val resizable = info != null && info.resizeMode and (
                    AppWidgetProviderInfo.RESIZE_VERTICAL or AppWidgetProviderInfo.RESIZE_HORIZONTAL
                    ) != 0
                if (info != null && resizable) {
                    menu.add(Menu.NONE, ACTION_RESIZE, Menu.NONE, R.string.today_widget_resize)
                }
            },
        ) { item ->
            when (item.itemId) {
                ACTION_TOGGLE_PIN -> {
                    prefs.todayPinnedKeys = if (pinned) prefs.todayPinnedKeys - key else prefs.todayPinnedKeys + key
                    refresh()
                }
                ACTION_MOVE_UP -> {
                    moves.up?.let { prefs.todayPanelOrder = it }
                    refresh()
                }
                ACTION_MOVE_DOWN -> {
                    moves.down?.let { prefs.todayPanelOrder = it }
                    refresh()
                }
                ACTION_RESIZE -> {
                    TodayOrder.widgetId(key)?.let { resizingId = it }
                    refresh()
                }
            }
        }
    }

    fun remove(id: Int) {
        context.showMessageDialog(R.string.today_widget_remove, R.string.today_widget_remove_message, R.string.today_widget_remove) {
            prefs.todayWidgetIds = prefs.todayWidgetIds - id
            val key = TodayOrder.widgetKey(id)
            prefs.todayPanelOrder = prefs.todayPanelOrder - key
            prefs.todayPinnedKeys = prefs.todayPinnedKeys - key
            prefs.todayWidgetSizes = prefs.todayWidgetSizes - id
            if (resizingId == id) resizingId = null
            host.deleteAppWidgetId(id)
            refresh()
        }
    }

    fun cards(): List<TodayCard> = prefs.todayWidgetIds.mapIndexed { index, id -> WidgetCard(id, index) }

    /** Widget id currently showing its resize handle, or null when no card is being resized. */
    private var resizingId: Int? = null

    private inner class WidgetCard(val appWidgetId: Int, val index: Int) : TodayCard {
        override val key = TodayOrder.widgetKey(appWidgetId)
        override val titleRes = R.string.today_widget
        override val gridSpan: Int? get() = prefs.todayWidgetSizes[appWidgetId]?.widthSpan ?: TodayWidgetResize.COLUMNS
        override fun title(context: Context): CharSequence =
            manager.getAppWidgetInfo(appWidgetId)?.loadLabel(context.packageManager)
                ?: context.getString(R.string.today_widget)

        // Widgets sit between Next up and Held; ties keep the incoming order (Next up, widgets, Held).
        override fun priority(context: Context, now: java.time.ZonedDateTime): Int =
            TodayOrder.defaultPriority(TodayCards.all.size - 2)

        override fun createView(inflater: LayoutInflater, parent: ViewGroup, fragment: Fragment): View {
            val info = manager.getAppWidgetInfo(appWidgetId)
            if (info == null) return TextView(context).apply {
                setText(R.string.today_widget_unavailable)
                setOnLongClickListener { remove(appWidgetId); true }
            }
            val density = context.resources.displayMetrics.density
            val range = TodayWidgetResize.range(
                defaultDp = info.minHeight,
                minResizeDp = info.minResizeHeight,
                maxResizeDp = info.maxResizeHeight,
            )
            val heightDp = (prefs.todayWidgetSizes[appWidgetId]?.heightDp ?: range.defaultDp)
                .coerceIn(range.minDp, range.maxDp)
            val hostView = host.createView(hostContext, appWidgetId, info)

            fun setHeight(dp: Int) {
                val px = (dp * density).roundToInt() + hostView.paddingTop + hostView.paddingBottom
                val lp = hostView.layoutParams
                // Mutate the existing params: replacing them with a base LayoutParams while
                // attached would crash FrameLayout's measure with a ClassCastException.
                if (lp == null) {
                    // Not attached yet (createView); a fresh params object is safe pre-addView.
                    hostView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px)
                } else {
                    lp.height = px
                    hostView.layoutParams = lp
                }
            }

            setHeight(heightDp)
            hostView.setOnLongClickListener { remove(appWidgetId); true }
            hostView.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
                if (right - left != oldRight - oldLeft) {
                    hostView.updateAppWidgetSize(
                        Bundle(),
                        listOf(SizeF((right - left) / density, hostView.layoutParams.height / density)),
                    )
                }
            }
            if (resizingId != appWidgetId) return hostView
            return resizeWrapper(
                hostView,
                range,
                density,
                context.getColorFromAttr(R.attr.accentColor),
                resizeHeight = info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0,
                resizeWidth = info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0,
                minResizeWidthDp = info.minResizeWidth,
                maxResizeWidthDp = info.maxResizeWidth,
                setHeight = ::setHeight,
            )
        }

        /**
         * Card in resize mode: the stock launcher's resize-frame pattern — an accent frame
         * around the card, drag strips on the edges the provider allows (bottom for height,
         * end edge for width) and a live readout. A drag commits the snapped size and leaves
         * resize mode; a bare tap cancels.
         */
        private fun resizeWrapper(
            hostView: AppWidgetHostView,
            range: TodayWidgetResize.Range,
            density: Float,
            accentColor: Int,
            resizeHeight: Boolean,
            resizeWidth: Boolean,
            minResizeWidthDp: Int,
            maxResizeWidthDp: Int,
            setHeight: (Int) -> Unit,
        ): View = FrameLayout(hostContext).apply {
            addView(hostView)

            // The widget-host context carries the manifest theme but not the activity's style
            // overlays: attrs resolve to base values (accent → ink, not the user's accent) and
            // themed styles crash on some resources. Build the resize chrome from explicit
            // dimensions and colours resolved in the fragment context instead.
            val scrim = ContextCompat.getColor(hostContext, R.color.scrimHomeTopFade)
            val handleScrim = ContextCompat.getColor(hostContext, R.color.scrimHandle)
            fun pill(fill: Int, strokePx: Int, strokeColor: Int): GradientDrawable =
                GradientDrawable().apply {
                    setColor(fill)
                    if (strokePx > 0) setStroke(strokePx, strokeColor)
                    cornerRadius = resources.getDimension(R.dimen.radiusPill)
                }

            background = GradientDrawable().apply {
                setColor(scrim)
                setStroke(resources.getDimensionPixelSize(R.dimen.strokeThin), accentColor)
                cornerRadius = resources.getDimension(R.dimen.radiusMedium)
            }

            // The widget's panel is the card sitting in the grid: its span is what a side
            // drag changes, and its grid parent supplies the cell width the span snaps to.
            fun panelInGrid(): Pair<View, WidgetGridView>? {
                var view: View = this
                while (view.layoutParams !is WidgetGridView.LayoutParams) {
                    view = view.parent as? View ?: return null
                }
                val grid = view.parent as? WidgetGridView ?: return null
                return view to grid
            }

            fun commitSize(widthSpan: Int?, heightDp: Int) {
                prefs.todayWidgetSizes =
                    prefs.todayWidgetSizes + (appWidgetId to TodayWidgetResize.Size(widthSpan, heightDp))
            }

            fun exitResize() {
                resizingId = null
                refresh()
            }

            // Live readout: hint before the first grab, snapped height while dragging. Hidden
            // on very short cards, where the chip would cover the card or clip out of it.
            val readout = TextView(hostContext).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.text_body))
                setTextColor(ContextCompat.getColor(hostContext, R.color.white))
                background = pill(
                    handleScrim,
                    resources.getDimensionPixelSize(R.dimen.strokeHairline),
                    accentColor,
                )
                setPadding(
                    (14 * density).roundToInt(), (6 * density).roundToInt(),
                    (14 * density).roundToInt(), (6 * density).roundToInt(),
                )
                text = context.getString(R.string.today_widget_resize_hint)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
                ).apply { bottomMargin = (44 * density).roundToInt() }
            }
            addView(readout)
            // A short card enters resize mode with the chip already hidden; the hint
            // stays intact on tall cards by not routing this through syncReadout.
            readout.visibility =
                if (currentHeightDp(hostView, density) >= READOUT_MIN_DP) View.VISIBLE else View.GONE

            var shownDp = -1

            /** [displayDp] is the dragged dimension's value; [basisDp] decides visibility, so a
             *  width drag on a short card does not hide the chip via the height threshold. */
            fun syncReadout(displayDp: Int, basisDp: Int) {
                if (basisDp >= READOUT_MIN_DP) {
                    if (shownDp != displayDp) {
                        shownDp = displayDp
                        readout.text = context.getString(R.string.today_widget_resize_dp, displayDp)
                    }
                    readout.visibility = View.VISIBLE
                } else {
                    readout.visibility = View.GONE
                }
            }

            if (resizeHeight) {
                val handle = FrameLayout(hostContext).apply {
                    contentDescription = context.getString(R.string.today_widget_resize)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (32 * density).roundToInt(),
                        Gravity.BOTTOM,
                    )
                    addView(View(hostContext).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            (120 * density).roundToInt(), (28 * density).roundToInt(), Gravity.CENTER,
                        )
                        background = pill(handleScrim, 0, 0)
                    })
                    addView(View(hostContext).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            (72 * density).roundToInt(), (8 * density).roundToInt(), Gravity.CENTER,
                        )
                        background = pill(accentColor, 0, 0)
                    })
                    var startY = 0f
                    var startDp = 0
                    var liveDp = 0
                    var moved = false
                    val touchSlop = ViewConfiguration.get(hostContext).scaledTouchSlop
                    setOnTouchListener { handleView, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                startY = event.rawY
                                startDp = currentHeightDp(hostView, density)
                                liveDp = TodayWidgetResize.snap(startDp, range)
                                moved = false
                                syncReadout(liveDp, liveDp)
                                handleView.parent.requestDisallowInterceptTouchEvent(true)
                                true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                // Jitter within the touch slop is not a drag yet; committing a
                                // snap nudge from it would be the bare-tap bug again.
                                if (!moved && abs(event.rawY - startY) <= touchSlop) return@setOnTouchListener true
                                moved = true
                                val draggedDp = (startDp + (event.rawY - startY) / density).roundToInt()
                                liveDp = TodayWidgetResize.snap(draggedDp, range)
                                setHeight(liveDp)
                                syncReadout(liveDp, liveDp)
                                true
                            }
                            MotionEvent.ACTION_UP -> {
                                handleView.parent.requestDisallowInterceptTouchEvent(false)
                                if (moved) {
                                    commitSize(prefs.todayWidgetSizes[appWidgetId]?.widthSpan, liveDp)
                                    // No replacement API: deprecated in API 36; this one-off
                                    // resize announcement is intentional, so keep it and suppress.
                                    @Suppress("DEPRECATION")
                                    handleView.announceForAccessibility(
                                        context.getString(R.string.today_widget_resize_dp, liveDp)
                                    )
                                    val widthPx = hostView.width.takeIf { it > 0 } ?: hostView.layoutParams.width
                                    hostView.updateAppWidgetSize(
                                        Bundle(),
                                        listOf(SizeF(widthPx / density, hostView.layoutParams.height / density)),
                                    )
                                } else {
                                    // Bare tap: no drag happened, so don't silently snap the height.
                                    setHeight(startDp)
                                }
                                exitResize()
                                true
                            }
                            MotionEvent.ACTION_CANCEL -> {
                                handleView.parent.requestDisallowInterceptTouchEvent(false)
                                setHeight(startDp)
                                exitResize()
                                true
                            }
                            else -> false
                        }
                    }
                }
                addView(handle)

                // Forward chip touches to the strip: the readout floats over the widget, and a
                // drag starting on it must not fall through to the widget's own views.
                readout.setOnTouchListener { _, event -> handle.dispatchTouchEvent(event) }

                // Bring the drag strip on screen when resize mode starts: the strip sits at the
                // card's bottom edge, which may be below the fold. One-shot after layout — a
                // persistent hook would re-scroll on every height change during the drag.
                viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                    override fun onPreDraw(): Boolean {
                        viewTreeObserver.removeOnPreDrawListener(this)
                        requestRectangleOnScreen(Rect(0, height - handle.layoutParams.height, width, height))
                        return true
                    }
                })
            }

            if (resizeWidth) {
                // Side drag strip on the end edge: width spans grid columns, clamped to the
                // provider's declared width range. Mirrored to the start edge in RTL.
                val side = FrameLayout(hostContext).apply {
                    contentDescription = context.getString(R.string.today_widget_resize)
                    layoutParams = FrameLayout.LayoutParams(
                        (32 * density).roundToInt(),
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.END,
                    )
                    addView(View(hostContext).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            (28 * density).roundToInt(), (120 * density).roundToInt(), Gravity.CENTER,
                        )
                        background = pill(handleScrim, 0, 0)
                    })
                    addView(View(hostContext).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            (8 * density).roundToInt(), (72 * density).roundToInt(), Gravity.CENTER,
                        )
                        background = pill(accentColor, 0, 0)
                    })
                    var startX = 0f
                    var startSpan = 0
                    var liveSpan = 0
                    var moved = false
                    var cellPx = 0f
                    var dragSign = 1f
                    var spanRange = TodayWidgetResize.SpanRange(1, TodayWidgetResize.COLUMNS)
                    var panel: View? = null
                    val touchSlop = ViewConfiguration.get(hostContext).scaledTouchSlop

                    fun widthDp(span: Int): Int = (span * cellPx / density).roundToInt()

                    setOnTouchListener { handleView, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                val found = panelInGrid()
                                val grid = found?.second
                                cellPx = if (grid != null && grid.width > 0) {
                                    (grid.width - grid.paddingLeft - grid.paddingRight)
                                        .toFloat() / TodayWidgetResize.COLUMNS
                                } else {
                                    0f
                                }
                                if (cellPx <= 0f) return@setOnTouchListener true
                                panel = found!!.first
                                startSpan = (panel!!.layoutParams as WidgetGridView.LayoutParams)
                                    .span.coerceIn(1, TodayWidgetResize.COLUMNS)
                                spanRange = TodayWidgetResize.spanRange(
                                    minResizeWidthDp, maxResizeWidthDp, cellPx / density,
                                )
                                startX = event.rawX
                                liveSpan = startSpan
                                moved = false
                                // The end edge follows the finger outward; mirrored in RTL.
                                dragSign = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) -1f else 1f
                                syncReadout(widthDp(liveSpan), currentHeightDp(hostView, density))
                                handleView.parent.requestDisallowInterceptTouchEvent(true)
                                true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                if (!moved && abs(event.rawX - startX) <= touchSlop) return@setOnTouchListener true
                                moved = true
                                val draggedWidthDp =
                                    (startSpan * cellPx + dragSign * (event.rawX - startX)) / density
                                val span = TodayWidgetResize.spanFor(draggedWidthDp, cellPx / density, spanRange)
                                if (span != liveSpan) {
                                    liveSpan = span
                                    (panel?.layoutParams as? WidgetGridView.LayoutParams)?.span = span
                                    panel?.requestLayout()
                                }
                                syncReadout(widthDp(liveSpan), currentHeightDp(hostView, density))
                                true
                            }
                            MotionEvent.ACTION_UP -> {
                                handleView.parent.requestDisallowInterceptTouchEvent(false)
                                if (moved) {
                                    commitSize(liveSpan, currentHeightDp(hostView, density))
                                    // No replacement API: deprecated in API 36; this one-off
                                    // resize announcement is intentional, so keep it and suppress.
                                    @Suppress("DEPRECATION")
                                    handleView.announceForAccessibility(
                                        context.getString(R.string.today_widget_resize_dp, widthDp(liveSpan))
                                    )
                                    // The rebuilt layout reports the new width through the host
                                    // view's own layout-change listener.
                                    exitResize()
                                } else {
                                    // Bare tap: restore the span and leave resize mode, like the
                                    // bottom strip — otherwise width-only widgets could never cancel.
                                    (panel?.layoutParams as? WidgetGridView.LayoutParams)?.span = startSpan
                                    panel?.requestLayout()
                                    exitResize()
                                }
                                true
                            }
                            MotionEvent.ACTION_CANCEL -> {
                                handleView.parent.requestDisallowInterceptTouchEvent(false)
                                (panel?.layoutParams as? WidgetGridView.LayoutParams)?.span = startSpan
                                panel?.requestLayout()
                                exitResize()
                                true
                            }
                            else -> false
                        }
                    }
                }
                addView(side)
                // The chip sits at the bottom, so it forwards to the bottom strip when there
                // is one; on width-only widgets it forwards to the side strip instead.
                if (!resizeHeight) readout.setOnTouchListener { _, event -> side.dispatchTouchEvent(event) }
            }
        }

        /** The card's height in dp, without the host view's own padding. */
        private fun currentHeightDp(hostView: AppWidgetHostView, density: Float): Int =
            ((hostView.layoutParams.height - hostView.paddingTop - hostView.paddingBottom) / density).roundToInt()
    }

    companion object {
        const val REQUEST_CONFIGURE = 421
        const val CONFIGURE_RESULT = "today_widget_configure_result"
        const val RESULT_CODE = "result_code"
        private const val HOST_ID = 421
        private const val ACTION_TOGGLE_PIN = 1
        private const val ACTION_MOVE_UP = 2
        private const val ACTION_MOVE_DOWN = 3
        private const val ACTION_RESIZE = 4
        /** Below this height (dp) the resize readout chip would cover the card or clip. */
        private const val READOUT_MIN_DP = 96
    }
}

/** Detect long presses even when a provider's button consumes touch events. */
private class LongPressWidgetView(context: Context) : AppWidgetHostView(context) {
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var consumed = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPress = Runnable {
        val cancel = MotionEvent.obtain(
            downTime,
            SystemClock.uptimeMillis(),
            MotionEvent.ACTION_CANCEL,
            downX,
            downY,
            0,
        )
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
        consumed = performLongClick()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                consumed = false
                downTime = event.downTime
                downX = event.x
                downY = event.y
                postDelayed(longPress, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> if (abs(event.x - downX) > slop || abs(event.y - downY) > slop) removeCallbacks(longPress)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> removeCallbacks(longPress)
        }
        return consumed || super.dispatchTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPress)
        super.onDetachedFromWindow()
    }
}
