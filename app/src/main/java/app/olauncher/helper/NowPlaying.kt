package app.olauncher.helper

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.palette.graphics.Palette
import app.olauncher.quiet.QuietListenerService

/** Transport state of a media session, reduced to what the picker needs. */
enum class Transport { PLAYING, PAUSED, OTHER }

data class MediaSession(
    val packageName: String,
    val transport: Transport,
    val title: String?,
    val artist: String?,
    val artwork: Bitmap? = null,
    val positionMs: Long = 0L,
    val positionAtMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
)

/** Context-free choice of which media session to show. */
object MediaPicker {
    const val PAUSED_WINDOW_MS = 30L * 60_000L

    /** A playing session wins; else a paused one that was playing within 30 minutes. */
    fun pick(sessions: List<MediaSession>, lastPlayingMs: Map<String, Long>, nowMs: Long): MediaInfo? {
        val usable = sessions.filter { !it.title.isNullOrBlank() }
        usable.firstOrNull { it.transport == Transport.PLAYING }?.let { return it.toInfo(true) }
        val paused = usable.firstOrNull {
            if (it.transport != Transport.PAUSED) return@firstOrNull false
            val last = lastPlayingMs[it.packageName] ?: return@firstOrNull false
            nowMs - last <= PAUSED_WINDOW_MS
        }
        return paused?.toInfo(false)
    }

    private fun MediaSession.toInfo(playing: Boolean) = MediaInfo(
        title = title.orEmpty(),
        artist = artist,
        packageName = packageName,
        playing = playing,
        artwork = artwork,
        positionMs = positionMs,
        positionAtMs = positionAtMs,
        durationMs = durationMs,
        speed = speed,
    )
}

/**
 * Reads the active media session. Needs notification access; without it every call
 * quietly returns null / does nothing.
 */
class NowPlaying(private val context: Context, private val onChange: () -> Unit) {

    private val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
    private val component = ComponentName(context, QuietListenerService::class.java)
    private val callbacks = HashMap<MediaController, MediaController.Callback>()
    private var started = false
    private val lastPlayingMs = HashMap<String, Long>()
    private val playingPackages = HashSet<String>()

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        watch(controllers.orEmpty())
        onChange()
    }

    fun start() {
        if (started) return
        try {
            manager?.addOnActiveSessionsChangedListener(sessionsListener, component)
            started = true
            watch(manager?.getActiveSessions(component).orEmpty())
        } catch (_: SecurityException) {
            // No notification access: the line just has no media part.
        } catch (_: Exception) {
        }
    }

    fun stop() {
        if (!started) return
        started = false
        try {
            manager?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (_: Exception) {
        }
        unwatchAll()
    }

    /** The playing session, else a recently playing paused one, else null. */
    fun current(): MediaInfo? {
        val controllers = try {
            manager?.getActiveSessions(component).orEmpty()
        } catch (_: Exception) {
            return null
        }
        val now = System.currentTimeMillis()
        val sessions = controllers.map { controller ->
            val state = controller.playbackState
            val transport = transportOf(state?.state)
            noteTransport(controller.packageName, transport, now)
            val metadata = controller.metadata
            MediaSession(
                packageName = controller.packageName,
                transport = transport,
                title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
                artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
                artwork = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART),
                positionMs = state?.position ?: 0L,
                positionAtMs = state?.lastPositionUpdateTime ?: 0L,
                durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
                speed = state?.playbackSpeed ?: 1f,
            )
        }
        return synchronized(this) { MediaPicker.pick(sessions, lastPlayingMs, now) }
    }

    private var glowKey: String? = null
    private var glowCached: Int? = null

    /**
     * Dominant album colour for the glow, or null when there is no artwork or no fitting colour.
     * Cached per track, so the palette runs once per song rather than on every refresh.
     */
    fun glowColor(media: MediaInfo): Int? {
        // The artwork identity is part of the key, so art that arrives after the title still gets a colour.
        val key = "${media.packageName}|${media.title}|${media.artist.orEmpty()}|${media.artwork?.let { System.identityHashCode(it) }}"
        if (key == glowKey) return glowCached
        val bitmap = media.artwork
        val color = if (bitmap == null) null else {
            try {
                MusicPulse.glowColor(swatchesOf(bitmap))
            } catch (_: Exception) {
                // A recycled or unreadable bitmap just means no glow for this track.
                null
            }
        }
        glowKey = key
        glowCached = color
        return color
    }

    private fun swatchesOf(bitmap: Bitmap): List<SwatchColor> =
        Palette.from(bitmap).maximumColorCount(16).generate().swatches.map { swatch ->
            val hsl = swatch.hsl
            SwatchColor(
                argb = 0xFF000000.toInt() or swatch.rgb,
                population = swatch.population,
                saturation = hsl[1],
                lightness = hsl[2],
            )
        }

    /** Plays or pauses the session of [packageName]. Does nothing if it is gone. */
    fun toggle(packageName: String) {
        try {
            val controller = manager?.getActiveSessions(component).orEmpty()
                .firstOrNull { it.packageName == packageName } ?: return
            if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
                controller.transportControls.pause()
            } else {
                controller.transportControls.play()
            }
        } catch (_: Exception) {
        }
    }

    private fun transportOf(state: Int?) = when (state) {
        PlaybackState.STATE_PLAYING -> Transport.PLAYING
        PlaybackState.STATE_PAUSED -> Transport.PAUSED
        else -> Transport.OTHER
    }

    /** Remembers when a session was last playing: now while playing, and at the moment it stops. */
    @Synchronized
    private fun noteTransport(packageName: String, transport: Transport, now: Long) {
        if (transport == Transport.PLAYING) {
            playingPackages.add(packageName)
            lastPlayingMs[packageName] = now
        } else if (playingPackages.remove(packageName)) {
            lastPlayingMs[packageName] = now
        }
    }

    private fun watch(controllers: List<MediaController>) {
        unwatchAll()
        controllers.forEach { controller ->
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    noteTransport(controller.packageName, transportOf(state?.state), System.currentTimeMillis())
                    onChange()
                }
                override fun onMetadataChanged(metadata: MediaMetadata?) = onChange()
                override fun onSessionDestroyed() = onChange()
            }
            try {
                controller.registerCallback(callback)
                callbacks[controller] = callback
            } catch (_: Exception) {
            }
        }
    }

    private fun unwatchAll() {
        callbacks.forEach { (controller, callback) ->
            try {
                controller.unregisterCallback(callback)
            } catch (_: Exception) {
            }
        }
        callbacks.clear()
    }
}
