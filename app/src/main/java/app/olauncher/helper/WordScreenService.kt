package app.olauncher.helper

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import app.olauncher.R
import app.olauncher.data.Prefs

/**
 * Reads the screen text only when the accessibility button is pressed and hands it to the
 * word picker. Keeps nothing and sends nothing.
 */
class WordScreenService : AccessibilityService() {

    companion object {
        const val EXTRA_SCREEN_LINES = "app.olauncher.SCREEN_LINES"
        private const val MAX_LINES = 600

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
        val lines = ArrayList<String>()
        try {
            val root = rootInActiveWindow
            if (root != null && root.packageName?.toString() != packageName) {
                collect(root, lines)
            }
        } catch (_: Exception) {
        }
        if (lines.isEmpty()) {
            applicationContext.showToast(getString(R.string.nothing_to_pick_here))
            return
        }
        val intent = Intent(applicationContext, WordCaptureActivity::class.java)
            .putStringArrayListExtra(EXTRA_SCREEN_LINES, lines)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun collect(node: AccessibilityNodeInfo, out: MutableList<String>) {
        if (out.size >= MAX_LINES) return
        if (node.isPassword || !node.isVisibleToUser) return
        val text = node.text?.toString()?.takeIf { it.isNotBlank() }
            ?: node.contentDescription?.toString()?.takeIf { it.isNotBlank() }
        if (text != null) out.add(text)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collect(child, out)
        }
    }
}
