package app.olauncher.helper

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import android.os.Handler
import android.os.Looper
import androidx.core.view.isVisible
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.databinding.DialogBaseBinding
import app.olauncher.ui.AppAction

/**
 * Shows a popup menu hanging off the end edge of this view.
 * [configure] can add or tweak items before the menu is shown.
 */
fun View.showPopupMenu(
    @MenuRes menuRes: Int = 0,
    configure: (Menu) -> Unit = {},
    onItemClick: (MenuItem) -> Unit,
): PopupMenu {
    val popup = PopupMenu(context, this, Gravity.END)
    if (menuRes != 0) popup.menuInflater.inflate(menuRes, popup.menu)
    configure(popup.menu)
    popup.setOnMenuItemClickListener { item ->
        Haptics.perform(this, Haptics.Kind.MENU)
        onItemClick(item)
        true
    }
    popup.show()
    return popup
}

/**
 * Shows the long-press actions of an app as an anchored popup: the app name on
 * top (not clickable), then every action. The popup sits next to this view and
 * flips above it near the bottom of the screen or the keyboard.
 */
fun View.showAppActionPopup(
    appName: String,
    actions: List<AppAction>,
    onAction: (AppAction) -> Unit,
): PopupMenu {
    val flipAnchor = flipAnchorIfNeeded(actions.size + 1)
    val popup = PopupMenu(context, flipAnchor ?: this, Gravity.END)
    popup.menu.add(Menu.NONE, Menu.NONE, 0, appName).isEnabled = false
    actions.forEachIndexed { index, action ->
        popup.menu.add(Menu.NONE, index, index + 1, action.labelRes)
    }
    popup.setOnMenuItemClickListener { item ->
        val action = actions.getOrNull(item.itemId) ?: return@setOnMenuItemClickListener true
        Haptics.perform(this, if (action.isDestructive) Haptics.Kind.REJECT else Haptics.Kind.MENU)
        onAction(action)
        true
    }
    if (flipAnchor != null) popup.setOnDismissListener { (flipAnchor.parent as? ViewGroup)?.removeView(flipAnchor) }
    popup.show()
    return popup
}

/**
 * When a menu of [itemCount] rows does not fit right below this view (above the
 * keyboard, if it is up), returns a 1px invisible view placed so a popup anchored
 * to it opens at the computed top: above this view, or moved up to clear the keyboard.
 * Returns null when the normal below placement is fine. The caller removes it on dismiss.
 */
private fun View.flipAnchorIfNeeded(itemCount: Int): View? {
    val root = rootView as? ViewGroup ?: return null
    val density = resources.displayMetrics.density
    val rowHeight = (48 * density * resources.configuration.fontScale.coerceAtLeast(1f)).toInt()
    val menuHeight = itemCount * rowHeight + (16 * density).toInt()
    val loc = IntArray(2)
    getLocationInWindow(loc)
    // Read the keyboard height now: the caller may have just asked it to hide.
    val imeBottom = root.rootWindowInsets?.getInsets(WindowInsets.Type.ime())?.bottom ?: 0
    val visibleBottom = PopupPlacement.visibleBottom(root.height, imeBottom)
    val anchorBottom = loc[1] + height
    val menuTop = PopupPlacement.menuTop(loc[1], anchorBottom, visibleTop = 0, visibleBottom, menuHeight)
    if (menuTop == anchorBottom) return null
    val anchorHeight = 1
    val anchor = View(context)
    val top = menuTop - anchorHeight
    val params = android.widget.FrameLayout.LayoutParams(width, anchorHeight).apply {
        leftMargin = loc[0]
        topMargin = maxOf(0, top)
    }
    (root as? android.widget.FrameLayout)?.addView(anchor, params) ?: return null
    return anchor
}

/**
 * App dialog: shows without bringing back a hidden status bar, and blurs the
 * screen behind it on Android 12+, fading blur and dialog out together on dismiss.
 */
class OlDialog(context: Context) : AlertDialog(context) {

    private var blur: WindowBlur? = null

    fun showRespectingStatusBar() {
        val window = window
        if (window == null || Prefs(context).showStatusBar) {
            show()
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            show()
            window.hideStatusBar()
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }
        blur = window?.let { WindowBlur(it).apply { fadeIn() } }
    }

    override fun dismiss() {
        val blur = blur ?: return super.dismiss()
        this.blur = null
        blur.fadeOut { super.dismiss() }
    }
}

/**
 * Builds a dialog using the app's own layout: a title row with a close icon,
 * an optional [message] or custom [content], and a text [action] at the end.
 * [content] receives the container so the inflated view keeps its XML margins.
 */
fun Context.createDialog(
    @StringRes title: Int,
    @StringRes action: Int,
    @StringRes message: Int = 0,
    messageText: CharSequence? = null,
    @StringRes neutral: Int = 0,
    onNeutral: () -> Unit = {},
    onAction: () -> Unit = {},
    content: ((ViewGroup) -> View)? = null,
): OlDialog {
    val dialog = OlDialog(this)
    val binding = DialogBaseBinding.inflate(LayoutInflater.from(dialog.context))
    binding.tvTitle.setText(title)
    binding.tvAction.setText(action)
    if (message != 0) {
        binding.tvMessage.setText(message)
        binding.tvMessage.isVisible = true
    } else if (messageText != null) {
        binding.tvMessage.text = messageText
        binding.tvMessage.isVisible = true
    }
    if (neutral != 0) {
        binding.tvNeutral.setText(neutral)
        binding.tvNeutral.isVisible = true
    }
    content?.let {
        binding.contentContainer.addView(it(binding.contentContainer))
        binding.contentContainer.isVisible = true
    }
    dialog.setView(binding.root)
    binding.ivClose.setOnClickListener {
        Haptics.perform(it, Haptics.Kind.MENU)
        dialog.dismiss()
    }
    binding.tvNeutral.setOnClickListener {
        Haptics.perform(it, Haptics.Kind.MENU)
        onNeutral()
        dialog.dismiss()
    }
    binding.tvAction.setOnClickListener {
        Haptics.perform(it, Haptics.Kind.MENU)
        onAction()
        dialog.dismiss()
    }
    return dialog
}

/** Title with a close icon, a message and a single action. */
fun Context.showMessageDialog(
    @StringRes title: Int,
    @StringRes message: Int,
    @StringRes action: Int,
    onAction: () -> Unit,
): OlDialog {
    val dialog = createDialog(title, action, message = message, onAction = onAction)
    dialog.showRespectingStatusBar()
    return dialog
}

/** Title with a close icon, a dynamic text message and a single action. */
fun Context.showMessageDialog(
    @StringRes title: Int,
    message: CharSequence,
    @StringRes action: Int,
    onAction: () -> Unit,
): OlDialog {
    val dialog = createDialog(title, action, messageText = message, onAction = onAction)
    dialog.showRespectingStatusBar()
    return dialog
}

/**
 * Shows a dialog that requires waiting [seconds] before confirmation.
 * The confirm button counts down "Confirm (seconds)" and remains disabled
 * until 0. Leaving or dismissing the dialog cancels it.
 */
fun Context.showWaitDialog(
    seconds: Int = 30,
    message: CharSequence,
    onConfirm: () -> Unit,
): OlDialog {
    val dialog = OlDialog(this)
    val binding = DialogBaseBinding.inflate(LayoutInflater.from(dialog.context))
    binding.tvTitle.setText(R.string.commit_lock)
    binding.tvMessage.text = message
    binding.tvMessage.isVisible = true

    val handler = Handler(Looper.getMainLooper())
    var remaining = seconds
    var timerRunnable: Runnable? = null
    var isCancelled = false

    fun updateConfirmButton(rem: Int) {
        if (rem > 0) {
            binding.tvAction.isEnabled = false
            binding.tvAction.alpha = 0.5f
            binding.tvAction.text = getString(R.string.confirm_countdown, rem)
        } else {
            binding.tvAction.isEnabled = true
            binding.tvAction.alpha = 1.0f
            binding.tvAction.text = getString(R.string.confirm)
        }
    }

    updateConfirmButton(remaining)

    if (remaining > 0) {
        timerRunnable = object : Runnable {
            override fun run() {
                if (isCancelled) return
                remaining--
                updateConfirmButton(remaining)
                if (remaining > 0) {
                    handler.postDelayed(this, 1000L)
                }
            }
        }
        handler.postDelayed(timerRunnable, 1000L)
    }

    dialog.setView(binding.root)
    binding.ivClose.setOnClickListener {
        Haptics.perform(it, Haptics.Kind.MENU)
        isCancelled = true
        timerRunnable?.let { handler.removeCallbacks(it) }
        dialog.dismiss()
    }
    binding.tvAction.setOnClickListener {
        if (remaining <= 0 && !isCancelled) {
            Haptics.perform(it, Haptics.Kind.MENU)
            onConfirm()
            dialog.dismiss()
        }
    }
    dialog.setOnDismissListener {
        isCancelled = true
        timerRunnable?.let { handler.removeCallbacks(it) }
    }

    dialog.showRespectingStatusBar()
    return dialog
}

fun Context.showWaitDialog(
    seconds: Int = 30,
    @StringRes message: Int,
    onConfirm: () -> Unit,
): OlDialog = showWaitDialog(seconds = seconds, message = getString(message), onConfirm = onConfirm)

