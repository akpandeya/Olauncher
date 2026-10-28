package app.olauncher.helper

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.graphics.RectF
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.data.store.ScreenBox
import app.olauncher.data.store.ScreenNode
import android.os.Bundle
import kotlin.math.roundToInt

/**
 * Reads the screen text only when the accessibility button is pressed and hands it to the
 * word picker. Keeps nothing and sends nothing.
 */
class WordScreenService : AccessibilityService() {

    companion object {
        const val EXTRA_SCREEN_LINES = "app.olauncher.SCREEN_LINES"
        private const val MAX_LINES = 600
        private const val MAX_NODES = 2000
        private val NEWLINE_REGEX = Regex("""[\n\r]+""")

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
        // screenNodes (words with boxes for the Circle-to-Search overlay) are not handed on
        // yet; the word picker still runs on the legacy EXTRA_SCREEN_LINES strings.
        if (paragraphs.isEmpty()) {
            applicationContext.showToast(getString(R.string.nothing_to_pick_here))
            return
        }
        val intent = Intent(applicationContext, WordCaptureActivity::class.java)
            .putStringArrayListExtra(EXTRA_SCREEN_LINES, paragraphs)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
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
