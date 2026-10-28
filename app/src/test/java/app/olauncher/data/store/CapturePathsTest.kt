package app.olauncher.data.store

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CapturePathsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `exact capture file passes`() {
        val cache = tmp.newFolder("cache")
        assertTrue(
            CapturePaths.isCaptureFile(
                File(cache, "capture/frame.png").path,
                cache,
                "capture/frame.png",
            )
        )
    }

    @Test
    fun `traversal outside the capture file is rejected`() {
        val cache = tmp.newFolder("cache")
        // ../ traversal that resolves into the prefs directory instead.
        assertFalse(
            CapturePaths.isCaptureFile(
                File(cache, "capture/../shared_prefs/app.olauncher.xml").path,
                cache,
                "capture/frame.png",
            )
        )
        // A private file far outside the cache directory.
        assertFalse(
            CapturePaths.isCaptureFile(
                "/data/data/app.thumbline/shared_prefs/app.olauncher.xml",
                cache,
                "capture/frame.png",
            )
        )
    }

    @Test
    fun `sibling files are rejected`() {
        val cache = tmp.newFolder("cache")
        // Same directory, different name.
        assertFalse(
            CapturePaths.isCaptureFile(
                File(cache, "capture/nodes.txt").path,
                cache,
                "capture/frame.png",
            )
        )
        // Different file name inside the cache root.
        assertFalse(
            CapturePaths.isCaptureFile(
                File(cache, "frame.png").path,
                cache,
                "capture/frame.png",
            )
        )
    }

    @Test
    fun `null and empty candidates are rejected`() {
        val cache = tmp.newFolder("cache")
        assertFalse(CapturePaths.isCaptureFile(null, cache, "capture/frame.png"))
        assertFalse(CapturePaths.isCaptureFile("", cache, "capture/frame.png"))
    }

    @Test
    fun `unchanged guards the timestamp against a newer capture`() {
        // The split-screen race: a newer capture rewrites the fixed files while the older
        // instance is closing — only an unchanged file may be deleted.
        val file = tmp.newFile("capture.txt")
        val openedAt = file.lastModified()

        assertTrue(CapturePaths.unchanged(openedAt, file))
        // The service rewrote the file after this instance opened it.
        file.setLastModified(openedAt + 5_000)
        assertFalse(CapturePaths.unchanged(openedAt, file))
        // The file is already gone — nothing left to tidy up.
        assertFalse(CapturePaths.unchanged(openedAt, File(tmp.root, "never-written.txt")))
    }
}
