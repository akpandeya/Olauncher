package app.olauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import app.olauncher.helper.MusicPulse

/**
 * Material 3 style wavy progress line. The played part is a sine wave, and a faded straight
 * track runs across the full width. The wave only moves while [setRunning] is true; stopped,
 * it keeps its last phase and draws nothing new.
 */
class WavyProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val strokeWidthPx = 2f * density
    private val amplitudePx = 3f * density
    private val wavelengthPx = 24f * density
    /** Distance between path points; small enough for a smooth curve. */
    private val stepPx = 1.5f * density

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = Paint.Cap.ROUND
    }
    private val wavePath = Path()

    private var progress = 0f
    private var lineColor = 0
    private var running = false
    /** Motion time so far; kept when stopped so the wave does not jump on restart. */
    private var elapsedMs = 0L
    private var lastFrameMs = 0L

    private val frame = object : Runnable {
        override fun run() {
            if (!running) return
            val now = SystemClock.uptimeMillis()
            elapsedMs += now - lastFrameMs
            lastFrameMs = now
            invalidate()
            postOnAnimationDelayed(this, MusicPulse.WAVE_FRAME_INTERVAL_MS)
        }
    }

    /** Played fraction, 0 to 1. */
    fun setProgress(fraction: Float) {
        val clamped = fraction.coerceIn(0f, 1f)
        if (clamped == progress) return
        progress = clamped
        invalidate()
    }

    fun setLineColor(argb: Int) {
        if (argb == lineColor) return
        lineColor = argb
        wavePaint.color = argb
        trackPaint.color = MusicPulse.waveTrackColor(argb)
        invalidate()
    }

    /** Starts or stops the wave motion. Call with false whenever Home is hidden or music stops. */
    fun setRunning(run: Boolean) {
        if (running == run) return
        running = run
        removeCallbacks(frame)
        if (run) {
            lastFrameMs = SystemClock.uptimeMillis()
            postOnAnimation(frame)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val half = strokeWidthPx / 2f
        val start = half
        val end = width - half
        if (end <= start) return
        val centreY = height / 2f
        canvas.drawLine(start, centreY, end, centreY, trackPaint)
        val playedEnd = start + (end - start) * progress
        if (playedEnd <= start) return
        val phase = MusicPulse.wavePhase(elapsedMs)
        wavePath.rewind()
        wavePath.moveTo(start, centreY + MusicPulse.waveOffset(0f, wavelengthPx, amplitudePx, phase))
        var x = start + stepPx
        while (x < playedEnd) {
            wavePath.lineTo(x, centreY + MusicPulse.waveOffset(x - start, wavelengthPx, amplitudePx, phase))
            x += stepPx
        }
        wavePath.lineTo(playedEnd, centreY + MusicPulse.waveOffset(playedEnd - start, wavelengthPx, amplitudePx, phase))
        canvas.drawPath(wavePath, wavePaint)
    }

    override fun onDetachedFromWindow() {
        running = false
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }
}
