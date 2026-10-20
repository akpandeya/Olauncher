package app.olauncher.listener

import android.content.Context
import android.view.MotionEvent
import android.view.View

internal open class ViewSwipeTouchListener(c: Context?, private val v: View) : BaseSwipeTouchListener(c) {

    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        when (motionEvent.action) {
            MotionEvent.ACTION_DOWN -> view.isPressed = true
            MotionEvent.ACTION_UP -> view.isPressed = false
        }
        return super.onTouch(view, motionEvent)
    }

    override fun dispatchLongClick() = onLongClick(v)
    override fun dispatchClick() = onClick(v)

    open fun onLongClick(view: View) {}
    open fun onClick(view: View) {}
}
