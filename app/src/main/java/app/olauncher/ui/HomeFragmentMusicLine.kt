package app.olauncher.ui

import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ContentUris
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.provider.CalendarContract
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import app.olauncher.R
import app.olauncher.glance.CalendarRepository
import app.olauncher.helper.ContextItem
import app.olauncher.helper.ContextLabels
import app.olauncher.helper.ContextLine
import app.olauncher.helper.MediaInfo
import app.olauncher.helper.MusicPulse
import app.olauncher.helper.NowPlaying
import app.olauncher.helper.showToast
import kotlinx.coroutines.launch
import java.time.Instant

internal fun HomeFragment.startContextLine() {
    if (!prefs.homeContextLine && !prefs.homeMusicPulse) {
        binding.contextLine.isVisible = false
        return
    }
    val context = requireContext()
    if (nowPlaying == null) {
        nowPlaying = NowPlaying(context.applicationContext) {
            // Media callbacks may arrive off the main thread.
            view?.post { refreshContextLine() }
        }
    }
    nowPlaying?.start()
    if (contextTickReceiver == null) {
        contextTickReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_TIME_TICK) refreshContextLine()
            }
        }
        context.registerReceiver(contextTickReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
    }
    refreshContextLine()
}

internal fun HomeFragment.stopContextLine() {
    nowPlaying?.stop()
    stopMusicPulse()
    contextJob?.cancel()
    contextTickReceiver?.let {
        try {
            requireContext().unregisterReceiver(it)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        contextTickReceiver = null
    }
}

internal fun HomeFragment.refreshContextLine() {
    if (_binding == null) return
    // Late callbacks (artwork downloads, media changes) can land after onPause; they must not
    // restart the music tick in the background. onResume refreshes again.
    if (!homeVisible) return
    val media = if (prefs.homeContextLine || prefs.homeMusicPulse) nowPlaying?.current() else null
    updateMusicPulse(media)
    if (!prefs.homeContextLine) {
        contextItem = null
        binding.contextLine.isVisible = false
        return
    }
    val appContext = context?.applicationContext ?: return
    val labels = ContextLabels(
        inMinutes = getString(R.string.context_line_in_minutes),
        now = getString(R.string.context_line_now),
    )
    contextJob?.cancel()
    contextJob = viewLifecycleOwner.lifecycleScope.launch {
        val now = Instant.now()
        val event = if (media != null) null else {
            val events = try {
                CalendarRepository(appContext).next(now.toEpochMilli(), hours = 1, limit = 8)
            } catch (_: Exception) {
                emptyList()
            }
            ContextLine.relevantEvent(events, now)
        }
        if (_binding == null) return@launch
        val item = ContextLine.pick(media, event, now, labels)
        contextItem = item
        binding.contextLine.text = item?.text
        binding.contextLine.contentDescription = (item as? ContextItem.Media)?.let {
            getString(if (it.playing) R.string.context_line_pause_title else R.string.context_line_play_title, it.title)
        }
        binding.contextLine.isVisible = item != null
    }
}

internal fun HomeFragment.openContextItem() {
    val context = context ?: return
    try {
        when (val item = contextItem) {
            is ContextItem.Media -> nowPlaying?.toggle(item.packageName)
            is ContextItem.Event -> startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    data = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, item.eventId)
                }
            )
            null -> {}
        }
    } catch (_: Exception) {
        context.showToast(getString(R.string.unable_to_open_app))
    }
}

internal fun HomeFragment.openMediaApp(item: ContextItem.Media) {
    val context = context ?: return
    try {
        val intent = context.packageManager.getLaunchIntentForPackage(item.packageName)
        if (intent != null) startActivity(intent)
    } catch (_: Exception) {
    }
}

/** Shows the album-colour glow and progress line for playing or paused media; paused freezes them. Hides them otherwise. */
internal fun HomeFragment.updateMusicPulse(media: MediaInfo?) {
    val binding = _binding ?: return
    binding.root.removeCallbacks(musicTick)
    if (media == null || !prefs.homeMusicPulse) {
        stopMusicPulse()
        return
    }
    val strength = prefs.homeGlowStrength
    val colors = if (MusicPulse.isGlowVisible(strength)) nowPlaying?.glowColors(media).orEmpty() else emptyList()
    val glow = colors.firstOrNull()
    val strengthChanged = strength != glowShownStrength
    if (colors != glowShownColors || strengthChanged) {
        // New track or new strength: start again from the strongest colour.
        stopGlowRotation()
        glowShownColors = colors
        glowShownStrength = strength
        glowColorIndex = 0
        binding.musicGlow.background = glow?.let { glowDrawable(it, strength) }
    }
    binding.musicGlow.isVisible = glow != null
    if (glow == null) {
        glowAnimator?.cancel()
        glowAnimator = null
    } else if (media.playing) {
        if (strengthChanged || glowAnimator?.isRunning != true) startGlowPulse(strength)
    } else {
        // Paused: hold the glow where it is; if it was not pulsing, rest at its brightest.
        if (glowAnimator == null) binding.musicGlow.alpha = MusicPulse.glowPulseRange(strength).high
        glowAnimator?.cancel()
        glowAnimator = null
    }
    if (!media.playing) stopGlowRotation() else if (colors.size > 1 && !glowRotating) startGlowRotation()
    val fraction = MusicPulse.progress(
        media.positionMs, media.positionAtMs, SystemClock.elapsedRealtime(), media.speed, media.playing, media.durationMs,
    )
    binding.mediaProgress.isVisible = fraction != null
    if (fraction != null) {
        // One steady album colour picked for contrast on the wallpaper; the text colour without a glow.
        binding.mediaProgress.setLineColor(
            MusicPulse.progressLineColor(colors, binding.contextLine.currentTextColor)
        )
        binding.mediaProgress.setProgress(fraction)
    }
    binding.mediaProgress.setRunning(fraction != null && homeVisible && media.playing)
    // Re-check every second so the progress line keeps moving while the song plays; paused needs no tick.
    if (media.playing) binding.root.postDelayed(musicTick, 1_000L)
}

internal fun HomeFragment.stopMusicPulse() {
    val binding = _binding ?: return
    binding.root.removeCallbacks(musicTick)
    glowAnimator?.cancel()
    glowAnimator = null
    stopGlowRotation()
    binding.musicGlow.isVisible = false
    binding.mediaProgress.setRunning(false)
    binding.mediaProgress.isVisible = false
}

/** Fades the glow between the album colours: rests on one, then fades to the next, wrapping round. */
internal fun HomeFragment.startGlowRotation() {
    val binding = _binding ?: return
    glowRotating = true
    binding.root.removeCallbacks(glowRotationStep)
    binding.root.postDelayed(glowRotationStep, MusicPulse.GLOW_COLOR_HOLD_MS)
}

internal fun HomeFragment.stopGlowRotation() {
    glowRotating = false
    _binding?.root?.removeCallbacks(glowRotationStep)
    glowColorAnimator?.cancel()
    glowColorAnimator = null
}

internal fun HomeFragment.fadeToNextGlowColor() {
    val binding = _binding ?: return
    val colors = glowShownColors
    val strength = glowShownStrength
    val drawable = binding.musicGlow.background as? GradientDrawable
    if (!glowRotating || colors.size < 2 || strength == null || drawable == null) {
        stopGlowRotation()
        return
    }
    val from = colors[glowColorIndex.coerceIn(0, colors.lastIndex)]
    glowColorIndex = MusicPulse.nextGlowColorIndex(glowColorIndex, colors.size)
    val to = colors[glowColorIndex]
    glowColorAnimator?.cancel()
    glowColorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), from, to).apply {
        duration = MusicPulse.GLOW_COLOR_FADE_MS
        addUpdateListener {
            val color = it.animatedValue as Int
            drawable.setColors(glowGradient(color, strength))
        }
        start()
    }
    // The next fade starts once this one has run and the colour has rested.
    binding.root.postDelayed(glowRotationStep, MusicPulse.GLOW_COLOR_FADE_MS + MusicPulse.GLOW_COLOR_HOLD_MS)
}

/** Slow alpha breathing of the glow; about 6 seconds per half cycle. The range scales with [strength]. */
internal fun HomeFragment.startGlowPulse(strength: Int) {
    glowAnimator?.cancel()
    val range = MusicPulse.glowPulseRange(strength)
    glowAnimator = ObjectAnimator.ofFloat(binding.musicGlow, View.ALPHA, range.low, range.high).apply {
        duration = 6_000L
        repeatCount = ObjectAnimator.INFINITE
        repeatMode = ObjectAnimator.REVERSE
        start()
    }
}

/** Radial glow that is strongest at the centre and fades to fully transparent at its edge. */
internal fun HomeFragment.glowDrawable(argb: Int, strength: Int): GradientDrawable = GradientDrawable().apply {
    gradientType = GradientDrawable.RADIAL_GRADIENT
    gradientRadius = resources.getDimension(R.dimen.music_glow_height) / 2f
    setColors(glowGradient(argb, strength))
}

/** Centre and edge colours of the glow: the album colour at the strength's alpha, fading to transparent. */
internal fun HomeFragment.glowGradient(argb: Int, strength: Int): IntArray {
    val centreAlpha = MusicPulse.glowCentreAlpha(strength) shl 24
    return intArrayOf(centreAlpha or (argb and 0x00FFFFFF), argb and 0x00FFFFFF)
}
