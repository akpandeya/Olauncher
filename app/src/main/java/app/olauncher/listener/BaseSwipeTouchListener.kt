package app.olauncher.listener

import android.content.Context
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import app.olauncher.data.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import android.util.Log

private const val TAG = "BaseSwipeTouchListener"

/*
Shared swipe, double tap and long press gesture handling for a view.
The long press delay runs in a scope owned by the listener; pending long presses are cancelled
when the touched view detaches from its window.
Source: https://www.tutorialspoint.com/how-to-handle-swipe-gestures-in-kotlin
*/

internal abstract class BaseSwipeTouchListener(c: Context?) : OnTouchListener {
    protected var longPressOn = false

    /** Where the current gesture started, in the touched view's coordinates. */
    protected var downY = 0f
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watchedView: View? = null
    private val gestureDetector = GestureDetector(c, GestureListener())

    private val detachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {}
        override fun onViewDetachedFromWindow(v: View) {
            scope.coroutineContext[Job]?.children?.forEach { it.cancel() }
        }
    }

    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        if (watchedView !== view) {
            watchedView?.removeOnAttachStateChangeListener(detachListener)
            view.addOnAttachStateChangeListener(detachListener)
            watchedView = view
        }
        return gestureDetector.onTouchEvent(motionEvent)
    }

    protected abstract fun dispatchLongClick()
    protected abstract fun dispatchClick()
    protected open fun dispatchDoubleClick() {}

    open fun onSwipeRight() {}
    open fun onSwipeLeft() {}
    open fun onSwipeUp() {}
    open fun onSwipeDown() {}

    private inner class GestureListener : SimpleOnGestureListener() {
        private val SWIPE_THRESHOLD: Int = 100
        private val SWIPE_VELOCITY_THRESHOLD: Int = 100

        override fun onDown(e: MotionEvent): Boolean {
            downY = e.y
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            dispatchClick()
            return super.onSingleTapUp(e)
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            dispatchDoubleClick()
            return super.onDoubleTap(e)
        }

        override fun onLongPress(e: MotionEvent) {
            longPressOn = true
            scope.launch {
                delay(Constants.LONG_PRESS_DELAY_MS)
                if (isActive && longPressOn)
                    dispatchLongClick()
            }
            super.onLongPress(e)
        }

        override fun onFling(
            event1: MotionEvent?,
            event2: MotionEvent,
            velocityX: Float,
            velocityY: Float,
        ): Boolean {
            try {
                val diffY = event2.y - (event1?.y ?: 0F)
                val diffX = event2.x - (event1?.x ?: 0F)
                if (abs(diffX) > abs(diffY)) {
                    if (abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) onSwipeRight() else onSwipeLeft()
                    }
                } else {
                    if (abs(diffY) > SWIPE_THRESHOLD && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffY < 0) onSwipeUp() else onSwipeDown()
                    }
                }
            } catch (exception: Exception) {
                Log.w(TAG, "onFling failed", exception)
            }
            return false
        }
    }
}
