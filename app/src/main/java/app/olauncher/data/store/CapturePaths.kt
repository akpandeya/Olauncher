package app.olauncher.data.store

import java.io.File

/**
 * The capture files the screen-pick service writes under the app's cache directory and
 * passes to the capture activity as intent extras. The activity is exported, so any
 * installed app can start it with arbitrary extras — a path is only honored (read or
 * deleted on close) when it names exactly one of these files.
 */
object CapturePaths {
    const val FRAME_FILE = "capture/frame.png"
    const val NODES_FILE = "capture/nodes.txt"

    fun frameFile(cacheDir: File): File = File(cacheDir, FRAME_FILE)

    fun nodesFile(cacheDir: File): File = File(cacheDir, NODES_FILE)

    /**
     * True when [candidate] names exactly [expectedDir]/[name]. Canonical paths are
     * compared, so `../` traversal and symlinks cannot smuggle in a different file.
     * Anything unreadable, null or empty fails closed (not a capture file).
     */
    fun isCaptureFile(candidate: String?, expectedDir: File, name: String): Boolean {
        if (candidate.isNullOrEmpty()) return false
        return try {
            File(candidate).canonicalPath == File(expectedDir, name).canonicalPath
        } catch (_: Exception) {
            false
        }
    }

    /**
     * True when [file] is still the file [openedAt] was recorded from (its lastModified
     * at open). The names are fixed, so in multi-window mode an older capture's cleanup
     * could delete the files a newer capture is still reading — a timestamp that moved
     * on means the service rewrote them, and they must be left alone.
     */
    fun unchanged(openedAt: Long, file: File): Boolean =
        file.exists() && file.lastModified() == openedAt
}
