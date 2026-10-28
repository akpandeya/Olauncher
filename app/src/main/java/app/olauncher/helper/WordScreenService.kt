package app.olauncher.helper

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.hardware.HardwareBuffer
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.data.store.CapturePaths
import app.olauncher.data.store.ScreenBox
import app.olauncher.data.store.ScreenNode
import app.olauncher.data.store.ScreenNodesCodec
import android.os.Bundle
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Reads the screen text only when the accessibility button is pressed and hands it to the
 * word picker. Keeps nothing and sends nothing.
 */
class WordScreenService : AccessibilityService() {

    companion object {
        const val EXTRA_SCREEN_LINES = "app.olauncher.SCREEN_LINES"

        /** Path of the screenshot frame (cacheDir/capture/frame.png) for the Circle-to-Search overlay. */
        const val EXTRA_FRAME_PATH = "app.olauncher.FRAME_PATH"

        /** Path of the node snapshot (cacheDir/capture/nodes.txt, [ScreenNodesCodec] format). */
        const val EXTRA_NODES_PATH = "app.olauncher.NODES_PATH"

        private const val MAX_LINES = 600
        private const val MAX_NODES = 2000
        private val NEWLINE_REGEX = Regex("""[\n\r]+""")

        /** Upper bound for one screenshot + PNG write; past it the legacy picker takes over. */
        private const val CAPTURE_TIMEOUT_MS = 5_000L


        fun setEnabled(context: Context, enabled: Boolean) {
            val state = if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            context.packageManager.setComponentEnabledSetting(
                ComponentName(context, WordScreenService::class.java), state, PackageManager.DONT_KILL_APP
            )
        }
    }

    private val buttonCallback = object : AccessibilityButtonController.AccessibilityButtonCallback() {
        override fun onClicked(controller: AccessibilityButtonController) {
            pickFromScreen()
        }
    }

    /**
     * One background worker: the screenshot and the PNG write must not run on the main
     * thread ([AccessibilityService.takeScreenshot] requires a background executor).
     */
    private val captureExecutor: Executor by lazy { Executors.newSingleThreadExecutor() }

    /** Runs callback tasks inline on the thread the system delivers them on. */
    private val directExecutor = Executor { it.run() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        accessibilityButtonController.registerAccessibilityButtonCallback(buttonCallback)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        try {
            accessibilityButtonController.unregisterAccessibilityButtonCallback(buttonCallback)
        } catch (_: Exception) {
        }
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    private fun pickFromScreen() {
        val prefs = Prefs(applicationContext)
        if (!prefs.germanEnabled || !prefs.germanScreenPick) return
        val paragraphs = ArrayList<String>()
        val screenNodes = ArrayList<ScreenNode>()
        try {
            val root = rootInActiveWindow
            if (root != null && root.packageName?.toString() != packageName) {
                collect(root, paragraphs, screenNodes)
            }
        } catch (_: Exception) {
        }
        if (paragraphs.isEmpty()) {
            applicationContext.showToast(getString(R.string.nothing_to_pick_here))
            return
        }
        // One screenshot attempt; any failure (secure window, rate limit, missing
        // capability) falls back to the legacy string picker. 10.5 replaces the
        // fallback branch with the Reader view path — keep it a single named branch.
        val framePath = captureFrame(screenNodes)
        if (framePath != null) {
            launchOverlayCapture(framePath)
        } else {
            launchLegacyPicker(paragraphs)
        }
    }

    /**
     * Takes one screenshot of the default display and, on success, writes the frame to
     * cacheDir/capture/frame.png (overwritten on every press) and the node snapshot to
     * cacheDir/capture/nodes.txt. Returns the frame path, or null when anything fails.
     * The whole pipeline runs on [captureExecutor]; the button click waits for it.
     */
    private fun captureFrame(nodes: List<ScreenNode>): String? {
        // The capture files live under capture/ in the cache; the exported capture
        // activity accepts only these exact paths ([CapturePaths.isCaptureFile]).
        val frameFile = CapturePaths.frameFile(cacheDir)
        val nodesFile = CapturePaths.nodesFile(cacheDir)
        val done = CountDownLatch(1)
        var framePath: String? = null
        captureExecutor.execute {
            try {
                framePath = captureFrameFile(frameFile)
                if (framePath != null) nodesFile.writeText(ScreenNodesCodec.encode(nodes))
            } catch (_: Throwable) {
                framePath = null
            } finally {
                done.countDown()
            }
        }
        return try {
            if (done.await(CAPTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) framePath else null
        } catch (_: InterruptedException) {
            null
        }
    }

    /** Calls [takeScreenshot] and returns the written frame path; null on any failure. */
    private fun captureFrameFile(frameFile: File): String? {
        val done = CountDownLatch(1)
        var framePath: String? = null
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            directExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    framePath = try {
                        writeFrame(screenshot, frameFile)
                    } catch (_: Throwable) {
                        null
                    }
                    done.countDown()
                }

                override fun onFailure(errorCode: Int) {
                    // Secure window, rate limit, or a missing capability.
                    done.countDown()
                }
            },
        )
        return if (done.await(CAPTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) framePath else null
    }

    /**
     * Copies the screenshot's hardware buffer into a software bitmap (the hardware
     * bitmap cannot be read later), compresses it to PNG over [frameFile], and marks
     * the first success in prefs — the settings hint goes away from then on.
     */
    private fun writeFrame(screenshot: ScreenshotResult, frameFile: File): String? {
        val buffer: HardwareBuffer = screenshot.hardwareBuffer
        val hardware = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace) ?: return null
        val bitmap = hardware.copy(Bitmap.Config.ARGB_8888, false)
        buffer.close()
        hardware.recycle()
        bitmap ?: return null
        try {
            frameFile.parentFile?.mkdirs()
            frameFile.outputStream().use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) return null
            }
        } finally {
            bitmap.recycle()
        }
        Prefs(applicationContext).captureScreenshotOk = true
        return frameFile.absolutePath
    }

    /**
     * Hands the frame and the node snapshot to the capture activity (10.5 reads them and
     * builds the Circle-to-Search overlay). No serialization beyond the simple line
     * codec in [ScreenNodesCodec]: the frame travels as a path, the nodes as a file.
     */
    private fun launchOverlayCapture(framePath: String) {
        startActivity(
            Intent(applicationContext, WordCaptureActivity::class.java)
                .putExtra(EXTRA_FRAME_PATH, framePath)
                .putExtra(EXTRA_NODES_PATH, CapturePaths.nodesFile(cacheDir).absolutePath)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** The legacy string picker; 10.5 replaces this fallback with the Reader view path. */
    private fun launchLegacyPicker(paragraphs: ArrayList<String>) {
        startActivity(
            Intent(applicationContext, WordCaptureActivity::class.java)
                .putStringArrayListExtra(EXTRA_SCREEN_LINES, paragraphs)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /**
     * Walks the tree once and fills both outputs: the legacy paragraph strings for
     * EXTRA_SCREEN_LINES and the geometric [ScreenNode]s for the overlay. Each node's full
     * text stays one paragraph; the picker splits it into sentences.
     */
    private fun collect(
        node: AccessibilityNodeInfo,
        out: MutableList<String>,
        nodesOut: MutableList<ScreenNode>,
        parentId: Int = -1,
    ) {
        if (out.size >= MAX_LINES || nodesOut.size >= MAX_NODES) return
        if (node.isPassword || !node.isVisibleToUser) return
        val text = node.text?.toString()
        val description = node.contentDescription?.toString()
        val visibleText = text?.takeIf { it.isNotBlank() }
            ?: description?.takeIf { it.isNotBlank() }
        if (visibleText != null) out.add(visibleText.replace(NEWLINE_REGEX, " "))
        val id = nodesOut.size
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        nodesOut += ScreenNode(
            id = id,
            parentId = parentId,
            text = text,
            bounds = ScreenBox(bounds.left, bounds.top, bounds.right, bounds.bottom),
            charBoxes = text?.takeIf { it.isNotBlank() }?.let { requestCharLocations(node, it) },
            className = node.className?.toString(),
            contentDescription = description,
        )
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, out, nodesOut, id)
        }
    }

    /**
     * Asks the node for per-character boxes and maps the returned RectFs. Returns null when
     * the request or the decode fails — no geometry is guessed. The view fills the extras
     * bundle of the refreshed node synchronously where supported.
     */
    private fun requestCharLocations(node: AccessibilityNodeInfo, text: String): List<ScreenBox>? {
        return try {
            val args = Bundle().apply {
                putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX, 0)
                putInt(
                    AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH,
                    minOf(text.length, AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_MAX_LENGTH),
                )
            }
            if (!node.refreshWithExtraData(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY, args)) return null
            val rects = node.extras.getParcelableArray(
                AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY, RectF::class.java
            ) ?: return null
            if (rects.size != text.length) return null
            val boxes = ArrayList<ScreenBox>(rects.size)
            for (rect in rects) {
                val r = rect ?: return null // missing character: skip the whole node's boxes
                boxes += ScreenBox(r.left.roundToInt(), r.top.roundToInt(), r.right.roundToInt(), r.bottom.roundToInt())
            }
            boxes
        } catch (_: Exception) {
            null
        }
    }
}
