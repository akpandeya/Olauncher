package app.olauncher.helper

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.palette.graphics.Palette
import app.olauncher.quiet.QuietListenerService
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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
    val artworkUri: String? = null,
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
        artworkUri = artworkUri,
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

    /** Artwork fetched from a URL, keyed by URL. Only successful fetches are kept. Guarded by this. */
    private val artworkCache = object : LinkedHashMap<String, Bitmap>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?) = size > ARTWORK_CACHE_SIZE
    }

    /** When a fetch for a URL last failed (elapsed realtime), so it is retried later, not forever skipped. Guarded by this. */
    private val artworkFailedAtMs = object : LinkedHashMap<String, Long>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > ARTWORK_CACHE_SIZE
    }
    private val artworkPending = HashSet<String>()

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
            val artworkUrl = MusicPulse.loadableArtworkUri(
                metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI),
                metadata?.getString(MediaMetadata.METADATA_KEY_ART_URI),
                metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI),
            )
            MediaSession(
                packageName = controller.packageName,
                transport = transport,
                title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE),
                artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                    ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
                artwork = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                    ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                    ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
                    ?: artworkFromUrl(artworkUrl),
                positionMs = state?.position ?: 0L,
                positionAtMs = state?.lastPositionUpdateTime ?: 0L,
                durationMs = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
                speed = state?.playbackSpeed ?: 1f,
                artworkUri = artworkUrl,
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
        // Each refresh reads a fresh bitmap copy over IPC, so the key uses the art link and size, not object identity.
        val artwork = media.artwork
        val key = MusicPulse.glowCacheKey(
            media.packageName, media.title, media.artist, media.artworkUri, artwork?.width ?: 0, artwork?.height ?: 0,
        )
        if (key == glowKey) return glowCached
        val bitmap = media.artwork
        val color = if (bitmap == null) null else {
            try {
                MusicPulse.glowColor(swatchesOf(bitmap.toSoftwareBitmap()))
            } catch (_: Exception) {
                // A recycled or unreadable bitmap just means no glow for this track.
                null
            }
        }
        glowKey = key
        glowCached = color
        return color
    }

    /** Palette cannot read hardware bitmaps, so they are copied to a normal one first. */
    private fun Bitmap.toSoftwareBitmap(): Bitmap =
        if (config == Bitmap.Config.HARDWARE) copy(Bitmap.Config.ARGB_8888, false) else this

    /**
     * Artwork bitmap for [url], fetched in the background. Returns null until the fetch
     * succeeds; then [onChange] runs so the glow appears without waiting for the next tick.
     * A failed fetch (say, no network) is retried after [MusicPulse.ARTWORK_RETRY_MS].
     */
    private fun artworkFromUrl(url: String?): Bitmap? {
        if (url == null) return null
        synchronized(this) {
            artworkCache[url]?.let { return it }
            if (!MusicPulse.shouldFetchArtwork(artworkFailedAtMs[url], SystemClock.elapsedRealtime())) return null
            if (!artworkPending.add(url)) return null
        }
        artworkExecutor.execute {
            val bitmap = fetchBitmap(url)
            synchronized(this) {
                artworkPending.remove(url)
                if (bitmap != null) {
                    artworkCache[url] = bitmap
                    artworkFailedAtMs.remove(url)
                } else {
                    artworkFailedAtMs[url] = SystemClock.elapsedRealtime()
                }
            }
            if (bitmap != null) onChange()
        }
        return null
    }

    private fun fetchBitmap(url: String): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inSampleSize = ARTWORK_SAMPLE_SIZE
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return if (MusicPulse.isWebArtwork(url)) {
            fetchWebBitmap(url, options)
        } else {
            try {
                context.contentResolver.openInputStream(Uri.parse(url))?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun fetchWebBitmap(url: String, options: BitmapFactory.Options): Bitmap? {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (_: Exception) {
            return null
        }
        return try {
            connection.connectTimeout = ARTWORK_TIMEOUT_MS
            connection.readTimeout = ARTWORK_TIMEOUT_MS
            connection.inputStream.use { BitmapFactory.decodeStream(it, null, options) }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
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

    companion object {
        private const val ARTWORK_CACHE_SIZE = 8
        private const val ARTWORK_TIMEOUT_MS = 5_000
        private const val ARTWORK_SAMPLE_SIZE = 2

        /** One background thread for artwork downloads, shared by every instance. */
        private val artworkExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "thumbline-artwork").apply { isDaemon = true }
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
