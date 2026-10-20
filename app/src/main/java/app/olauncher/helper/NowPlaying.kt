package app.olauncher.helper

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import app.olauncher.quiet.QuietListenerService

/** Transport state of a media session, reduced to what the picker needs. */
enum class Transport { PLAYING, PAUSED, OTHER }

data class MediaSession(
    val packageName: String,
    val transport: Transport,
    val title: String?,
    val artist: String?,
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

    private fun MediaSession.toInfo(playing: Boolean) = MediaInfo(title.orEmpty(), artist, packageName, playing)
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
            val transport = transportOf(controller.playbackState?.state)
            noteTransport(controller.packageName, transport, now)
            val metadata = controller.metadata
            MediaSession(
                controller.packageName,
                transport,
                metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
                metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
            )
        }
        return synchronized(this) { MediaPicker.pick(sessions, lastPlayingMs, now) }
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
