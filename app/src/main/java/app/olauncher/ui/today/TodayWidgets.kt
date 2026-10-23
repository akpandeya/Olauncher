package app.olauncher.ui.today

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.os.UserHandle
import android.util.SizeF
import android.view.Gravity
import android.view.Menu
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.fragment.app.Fragment
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.helper.showMessageDialog
import app.olauncher.helper.showPopupMenu
import kotlin.math.abs
import kotlin.math.roundToInt

/** Owns only Today's widget IDs; built-in cards and their preferences remain independent. */
internal class TodayWidgets(
    private val fragment: Fragment,
    restoring: Boolean,
    private val refresh: () -> Unit,
    private val picker: ActivityResultLauncher<Intent>,
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

    fun onPickerResult(result: ActivityResult) {
        val id = prefs.todayPendingWidgetId
        clearAwaitingResult()
        if (result.resultCode != Activity.RESULT_OK || id <= 0) {
            discardPending()
        } else if (manager.getAppWidgetInfo(id) != null) {
            configureOrComplete()
        } else {
            val provider = result.data?.getParcelableExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, ComponentName::class.java)
            val profile = result.data?.getParcelableExtra(Intent.EXTRA_USER, UserHandle::class.java)
            if (provider == null) fail() else safely {
                val allowed = if (profile != null) manager.bindAppWidgetIdIfAllowed(id, profile, provider, null)
                    else manager.bindAppWidgetIdIfAllowed(id, provider)
                if (allowed) configureOrComplete() else {
                    markAwaitingResult()
                    bind.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
                        if (profile != null) putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, profile)
                    })
                }
            }
        }
    }

    init {
        // A fresh launch cannot still have an outstanding picker. A restored activity can.
        if (!restoring) discardPending()
        val owned = prefs.todayWidgetIds.toSet() + prefs.todayPendingWidgetId
        val orphans = host.appWidgetIds.filter { it !in owned }
        orphans.forEach(host::deleteAppWidgetId)
        // The host pools freed ids for reuse, so stale heights must not outlive their widgets.
        if (orphans.isNotEmpty()) prefs.todayWidgetHeights = prefs.todayWidgetHeights - orphans.toSet()
    }

    fun start() = host.startListening()
    fun stop() = host.stopListening()

    fun add() {
        if (prefs.todayPendingWidgetId > 0) return
        context.showMessageDialog(R.string.today_add_widget, R.string.today_widget_explanation, R.string.today_add_widget) {
            safely {
                val id = host.allocateAppWidgetId()
                prefs.todayPendingWidgetId = id
                markAwaitingResult()
                picker.launch(Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
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
        val moves = TodayOrder.panelMoves(order, key, pinned, panelKeys)
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
                if (info != null && info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0) {
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
            prefs.todayWidgetHeights = prefs.todayWidgetHeights - id
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
        override fun title(context: Context): CharSequence =
            manager.getAppWidgetInfo(appWidgetId)?.loadLabel(context.packageManager)
                ?: context.getString(R.string.today_widget)

        override fun priority(context: Context, now: java.time.ZonedDateTime): Int =
            TodayOrder.defaultPriority(TodayCards.all.size + index)

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
            val heightDp = (prefs.todayWidgetHeights[appWidgetId] ?: range.defaultDp).coerceIn(range.minDp, range.maxDp)
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
            return resizeWrapper(hostView, range, density, ::setHeight)
        }

        /** Container with a bottom drag handle; committing stores the height and leaves resize mode. */
        private fun resizeWrapper(
            hostView: AppWidgetHostView,
            range: TodayWidgetResize.Range,
            density: Float,
            setHeight: (Int) -> Unit,
        ): View = FrameLayout(hostContext).apply {
            addView(hostView)
            val handle = View(hostContext).apply {
                setBackgroundResource(R.drawable.widget_resize_handle)
                contentDescription = context.getString(R.string.today_widget_resize)
                layoutParams = FrameLayout.LayoutParams(
                    (48 * density).roundToInt(),
                    (28 * density).roundToInt(),
                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
                ).apply { bottomMargin = (8 * density).roundToInt() }
                var startY = 0f
                var startDp = 0
                var liveDp = 0
                setOnTouchListener { handleView, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            startY = event.rawY
                            startDp = currentHeightDp(hostView, density)
                            liveDp = TodayWidgetResize.snap(startDp, range)
                            handleView.parent.requestDisallowInterceptTouchEvent(true)
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val draggedDp = (startDp + (event.rawY - startY) / density).roundToInt()
                            liveDp = TodayWidgetResize.snap(draggedDp, range)
                            setHeight(liveDp)
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            handleView.parent.requestDisallowInterceptTouchEvent(false)
                            prefs.todayWidgetHeights = prefs.todayWidgetHeights + (appWidgetId to liveDp)
                            resizingId = null
                            val widthPx = hostView.width.takeIf { it > 0 } ?: hostView.layoutParams.width
                            hostView.updateAppWidgetSize(
                                Bundle(),
                                listOf(SizeF(widthPx / density, hostView.layoutParams.height / density)),
                            )
                            refresh()
                            true
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            handleView.parent.requestDisallowInterceptTouchEvent(false)
                            setHeight(startDp)
                            resizingId = null
                            refresh()
                            true
                        }
                        else -> false
                    }
                }
            }
            addView(handle)
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
