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
 * Material 3 style wavy progress line. The played part is a sine wave. A semi-opaque straight track
 * covers the unplayed part for the whole song, with a short vertical divider where the two meet.
 * Both have a thin dark outline so they stay visible on light
 * wallpapers. The wave only moves while [setRunning] is true; stopped, it keeps its last phase
 * and draws nothing new.
 */
class WavyProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val strokeWidthPx = MusicPulse.WAVE_STROKE_DP * density
    private val outlineWidthPx = strokeWidthPx + 2 * MusicPulse.WAVE_OUTLINE_DP * density
    private val trackStrokeWidthPx = MusicPulse.WAVE_TRACK_STROKE_DP * density
    private val trackOutlineWidthPx = trackStrokeWidthPx + 2 * MusicPulse.WAVE_OUTLINE_DP * density
    private val amplitudePx = MusicPulse.WAVE_AMPLITUDE_DP * density
    private val wavelengthPx = MusicPulse.WAVE_LENGTH_DP * density
    private val dividerWidthPx = MusicPulse.WAVE_DIVIDER_WIDTH_DP * density
    private val dividerOutlineWidthPx = dividerWidthPx + 2 * MusicPulse.WAVE_OUTLINE_DP * density
    /** Half the handle's length between its round ends. With the caps, the handle is WAVE_DIVIDER_HEIGHT_DP tall. */
    private val dividerLineHalfPx = (MusicPulse.WAVE_DIVIDER_HEIGHT_DP - MusicPulse.WAVE_DIVIDER_WIDTH_DP) / 2f * density
    /** Distance from the divider's centre to where the wave and the track stop or start: half the handle, outlines and the clear gap. */
    private val dividerClearancePx = (
        MusicPulse.WAVE_DIVIDER_WIDTH_DP / 2f + MusicPulse.WAVE_OUTLINE_DP +
            MusicPulse.WAVE_DIVIDER_GAP_DP + MusicPulse.WAVE_OUTLINE_DP
        ) * density
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
        strokeWidth = trackStrokeWidthPx
        strokeCap = Paint.Cap.ROUND
    }
    /** Wider dark strokes drawn under the wave and the track as a contrast edge. */
    private val waveOutlinePaint = Paint(wavePaint).apply { strokeWidth = outlineWidthPx }
    private val trackOutlinePaint = Paint(trackPaint).apply { strokeWidth = trackOutlineWidthPx }
    private val dividerPaint = Paint(wavePaint).apply { strokeWidth = dividerWidthPx }
    private val dividerOutlinePaint = Paint(wavePaint).apply { strokeWidth = dividerOutlineWidthPx }
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
        waveOutlinePaint.color = MusicPulse.waveOutlineColor(wavePaint.color)
        trackPaint.color = MusicPulse.waveTrackColor(argb)
        trackOutlinePaint.color = MusicPulse.waveOutlineColor(argb)
        dividerPaint.color = argb
        dividerOutlinePaint.color = MusicPulse.waveOutlineColor(argb)
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
        val half = outlineWidthPx / 2f
        val start = half
        val end = width - half
        if (end <= start) return
        val centreY = height / 2f
        val playedEnd = start + (end - start) * progress
        val divider = MusicPulse.waveDividerVisible(progress)
        // With a divider, the wave and the track stop short of it so their outlines do not run into it.
        val clearance = if (divider) dividerClearancePx else 0f
        val trackStart = playedEnd + clearance
        // Only the unplayed part gets the straight track.
        if (trackStart < end) {
            canvas.drawLine(trackStart, centreY, end, centreY, trackOutlinePaint)
            canvas.drawLine(trackStart, centreY, end, centreY, trackPaint)
        }
        if (playedEnd - clearance > start) drawWave(canvas, start, playedEnd - clearance, centreY)
        // Drawn last so the divider sits on top of the wave and the track.
        if (divider) {
            canvas.drawLine(playedEnd, centreY - dividerLineHalfPx, playedEnd, centreY + dividerLineHalfPx, dividerOutlinePaint)
            canvas.drawLine(playedEnd, centreY - dividerLineHalfPx, playedEnd, centreY + dividerLineHalfPx, dividerPaint)
        }
    }

    private fun drawWave(canvas: Canvas, start: Float, playedEnd: Float, centreY: Float) {
        val phase = MusicPulse.wavePhase(elapsedMs)
        wavePath.rewind()
        wavePath.moveTo(start, centreY + MusicPulse.waveOffset(0f, wavelengthPx, amplitudePx, phase))
        var x = start + stepPx
        while (x < playedEnd) {
            wavePath.lineTo(x, centreY + MusicPulse.waveOffset(x - start, wavelengthPx, amplitudePx, phase))
            x += stepPx
        }
        wavePath.lineTo(playedEnd, centreY + MusicPulse.waveOffset(playedEnd - start, wavelengthPx, amplitudePx, phase))
        canvas.drawPath(wavePath, waveOutlinePaint)
        canvas.drawPath(wavePath, wavePaint)
    }

    override fun onDetachedFromWindow() {
        running = false
        removeCallbacks(frame)
        super.onDetachedFromWindow()
    }
}
