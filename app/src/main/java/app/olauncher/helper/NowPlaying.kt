package app.olauncher.helper

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import app.olauncher.quiet.QuietListenerService

/**
 * Reads the active media session. Needs notification access; without it every call
 * quietly returns null / does nothing.
 */
class NowPlaying(private val context: Context, private val onChange: () -> Unit) {

    private val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager
    private val component = ComponentName(context, QuietListenerService::class.java)
    private val callbacks = HashMap<MediaController, MediaController.Callback>()
    private var started = false

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

    /** The first playing session, else null. */
    fun current(): MediaInfo? {
        val controllers = try {
            manager?.getActiveSessions(component).orEmpty()
        } catch (_: Exception) {
            return null
        }
        for (controller in controllers) {
            if (controller.playbackState?.state != PlaybackState.STATE_PLAYING) continue
            val metadata = controller.metadata ?: continue
            val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE) ?: continue
            val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            return MediaInfo(title, artist, controller.packageName, true)
        }
        return null
    }

    private fun watch(controllers: List<MediaController>) {
        unwatchAll()
        controllers.forEach { controller ->
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) = onChange()
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
