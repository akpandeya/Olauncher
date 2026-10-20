package app.olauncher.listener

import android.content.Context
import android.view.MotionEvent
import android.view.View

/*
Swipe, double tap and long press touch listener for a view
Source: https://www.tutorialspoint.com/how-to-handle-swipe-gestures-in-kotlin
*/

internal open class OnSwipeTouchListener(c: Context?) : BaseSwipeTouchListener(c) {

    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        if (motionEvent.action == MotionEvent.ACTION_UP)
            longPressOn = false
        return super.onTouch(view, motionEvent)
    }

    override fun dispatchLongClick() = onLongClick()
    override fun dispatchClick() = onClick()
    override fun dispatchDoubleClick() = onDoubleClick()

    open fun onLongClick() {}
    open fun onDoubleClick() {}
    open fun onTripleClick() {}
    open fun onClick() {}
}
