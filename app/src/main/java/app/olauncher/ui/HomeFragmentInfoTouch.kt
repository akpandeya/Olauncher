package app.olauncher.ui

import android.graphics.Rect
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View
import android.view.ViewGroup
import app.olauncher.helper.TouchTarget
import app.olauncher.helper.dpToPx

/**
 * Grows the tap area of the music and event lines to 48dp without touching layout: the lines keep
 * their 36dp look, only the parent's touch delegate reaches further up and down.
 */
internal fun HomeFragment.initInfoLineTouchTargets() {
    val parent = binding.nextEventLine.parent as? ViewGroup ?: return
    val lines = listOf(binding.nextEventLine, binding.contextLine)
    val refresh = {
        val group = InfoLineTouchDelegates(parent)
        val minPx = TouchTarget.MIN_TARGET_DP.dpToPx()
        for (line in lines) {
            if (line.visibility != View.VISIBLE || line.height <= 0) continue
            val extra = TouchTarget.extraPaddingPx(line.height, minPx)
            if (extra == 0) continue
            val bounds = Rect(line.left, line.top - extra, line.right, line.bottom + extra)
            group.add(TouchDelegate(bounds, line))
        }
        parent.touchDelegate = if (group.isEmpty()) null else group
    }
    val listener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> refresh() }
    lines.forEach { it.addOnLayoutChangeListener(listener) }
    parent.addOnLayoutChangeListener(listener)
    refresh()
}

/** A parent holds one touch delegate, so this passes each touch gesture to the delegate it started in. */
private class InfoLineTouchDelegates(parent: View) : TouchDelegate(Rect(), parent) {
    private val delegates = mutableListOf<TouchDelegate>()
    private var active: TouchDelegate? = null

    fun add(delegate: TouchDelegate) {
        delegates.add(delegate)
    }

    fun isEmpty() = delegates.isEmpty()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            active = delegates.firstOrNull { it.onTouchEvent(event) }
            return active != null
        }
        val target = active ?: return false
        val handled = target.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) active = null
        return handled
    }
}
