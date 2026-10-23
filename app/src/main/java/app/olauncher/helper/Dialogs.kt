package app.olauncher.helper

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Outline
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.graphics.Typeface
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.core.widget.TextViewCompat
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.PopupMenu
import android.os.Handler
import android.os.Looper
import androidx.core.view.isVisible
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.databinding.DialogBaseBinding
import app.olauncher.databinding.ItemPopupActionBinding
import app.olauncher.databinding.PopupAppActionsBinding
import app.olauncher.ui.AppAction
import app.olauncher.ui.AppActionList

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
 * Shows the long-press actions of an app as a Thumbline card: the app name on
 * top (a heading, not clickable), a hairline, then every action; destructive
 * actions in the danger colour. Actions are split into groups by thin dividers (see
 * [AppActionList.groupStarts]). The card is 68% of the screen wide, sits on the same side as this view and
 * flips above it near the bottom of the screen or the keyboard (see [PopupPlacement]).
 */
fun View.showAppActionPopup(
    appName: String,
    actions: List<AppAction>,
    onAction: (AppAction) -> Unit,
): PopupWindow {
    val inflater = LayoutInflater.from(context)
    val card = PopupAppActionsBinding.inflate(inflater)
    card.tvPopupTitle.text = appName
    val popup = PopupWindow(card.root, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true)
    val dangerColor = context.getColorFromAttr(R.attr.dangerColor)
    val dividerBefore = AppActionList.groupStarts(actions)
    actions.forEachIndexed { index, action ->
        if (index in dividerBefore) {
            inflater.inflate(R.layout.item_popup_divider, card.popupItems, true)
        }
        val row = ItemPopupActionBinding.inflate(inflater, card.popupItems, false).root
        row.setText(action.labelRes)
        if (action.isDestructive) row.setTextColor(dangerColor)
        row.setOnClickListener {
            Haptics.perform(this, if (action.isDestructive) Haptics.Kind.REJECT else Haptics.Kind.MENU)
            // Dismiss first so an action that opens the keyboard (rename) gets the window focus back.
            popup.dismiss()
            onAction(action)
        }
        card.popupItems.addView(row)
    }

    val root = rootView
    val margin = resources.getDimensionPixelSize(R.dimen.popup_card_screen_margin)
    val loc = IntArray(2)
    getLocationInWindow(loc)
    // Read the keyboard height now: the caller may have just asked it to hide.
    val imeBottom = root.rootWindowInsets?.getInsets(WindowInsets.Type.ime())?.bottom ?: 0
    val visibleBottom = PopupPlacement.visibleBottom(root.height, imeBottom)
    val menuWidth = PopupPlacement.menuWidth(root.width, margin)
    card.root.measure(
        View.MeasureSpec.makeMeasureSpec(menuWidth, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
    )
    val menuHeight = PopupPlacement.fitHeight(card.root.measuredHeight, visibleTop = 0, visibleBottom)
    val top = PopupPlacement.menuTop(loc[1], loc[1] + height, visibleTop = 0, visibleBottom, menuHeight)
    val left = PopupPlacement.menuLeftBySide(
        anchorLeft = loc[0],
        anchorRight = loc[0] + width,
        menuWidth = menuWidth,
        screenWidth = root.width,
        margin = margin,
    )

    val animation = TypedValue()
    popup.apply {
        width = menuWidth
        height = menuHeight
        setBackgroundDrawable(AppCompatResources.getDrawable(context, R.drawable.bg_popup_card))
        elevation = resources.getDimension(R.dimen.popup_card_elevation)
        isOutsideTouchable = true
        // Leave the keyboard as it is; the placement above already keeps the card clear of it.
        inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
        animationStyle = if (context.theme.resolveAttribute(R.attr.popupCardAnimationStyle, animation, true)) {
            animation.resourceId
        } else {
            0
        }
    }
    // Clip the row ripples to the card's rounded corners.
    card.root.clipToOutline = true
    card.root.outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, resources.getDimension(R.dimen.radiusMedium))
        }
    }
    // Absolute LEFT: [left] is already resolved for right-to-left by PopupPlacement.menuLeftBySide.
    @SuppressLint("RtlHardcoded")
    val gravity = Gravity.TOP or Gravity.LEFT
    popup.showAtLocation(this, gravity, left, top)
    // The popup window frame holds the card background and shadow, so animate it rather than the contents.
    card.root.rootView.animateFromTap(
        PopupOrigin.pivotX(loc[0] + width / 2, left, menuWidth),
        PopupOrigin.pivotY(loc[1] + height / 2, top, menuHeight),
    )
    return popup
}

/** Fades and scales the card in from the tap point; skipped (full size at once) when animations are off. */
private fun View.animateFromTap(pivotXPx: Float, pivotYPx: Float) {
    val durationMs = MotionPolicy.durationMs(MotionPolicy.DEFAULT_DURATION_MS, ValueAnimator.areAnimatorsEnabled() && !context.isEinkDisplay())
    if (durationMs <= 0L) return
    pivotX = pivotXPx
    pivotY = pivotYPx
    scaleX = PopupOrigin.START_SCALE
    scaleY = PopupOrigin.START_SCALE
    alpha = 0f
    animate().scaleX(1f).scaleY(1f).alpha(1f)
        .setDuration(durationMs)
        .setInterpolator(DecelerateInterpolator())
        .start()
}

/**
 * App dialog: shows without bringing back a hidden status bar, and blurs the
 * screen behind it on Android 12+, fading blur and dialog out together on dismiss.
 */
class OlDialog(context: Context) : AlertDialog(context) {

    private var blur: WindowBlur? = null

    /** Set by [createMultiChoiceDialog]: ticks or unticks a row from outside, e.g. after a permission result. */
    var setItemChecked: (index: Int, checked: Boolean) -> Unit = { _, _ -> }

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
    titleText: CharSequence? = null,
    content: ((ViewGroup) -> View)? = null,
): OlDialog {
    val dialog = OlDialog(this)
    val binding = DialogBaseBinding.inflate(LayoutInflater.from(dialog.context))
    if (title != 0) binding.tvTitle.setText(title) else binding.tvTitle.text = titleText
    // action == 0: no action button, the close icon is the only way out (pick-one lists).
    if (action != 0) binding.tvAction.setText(action) else binding.tvAction.isVisible = false
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

private fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

private fun TextView.styleAsDialogRow(context: Context) {
    TextViewCompat.setTextAppearance(this, R.style.TextSmall)
    val ripple = TypedValue()
    context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)
    setBackgroundResource(ripple.resourceId)
    minHeight = context.dp(44)
    gravity = Gravity.CENTER_VERTICAL
}

private fun Context.scrollingRows(rows: List<View>): View {
    val column = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        rows.forEach { addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) }
    }
    return ScrollView(this).apply {
        addView(column)
        layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(8) }
    }
}

/**
 * Pick-one list in the app's dialog: tapping a row runs [onPick] and closes the
 * dialog; the close icon cancels. The row at [checkedIndex] is shown in bold.
 */
fun Context.createChoiceDialog(
    @StringRes title: Int = 0,
    titleText: CharSequence? = null,
    @StringRes message: Int = 0,
    items: List<CharSequence>,
    checkedIndex: Int = -1,
    onPick: (index: Int) -> Unit,
): OlDialog {
    val holder = arrayOfNulls<OlDialog>(1)
    val dialog = createDialog(title, action = 0, message = message, titleText = titleText) { parent ->
        val context = parent.context
        context.scrollingRows(
            items.mapIndexed { index, label ->
                TextView(context).apply {
                    styleAsDialogRow(context)
                    text = label
                    if (index == checkedIndex) setTypeface(typeface, Typeface.BOLD)
                    setOnClickListener {
                        Haptics.perform(it, Haptics.Kind.MENU)
                        onPick(index)
                        holder[0]?.dismiss()
                    }
                }
            }
        )
    }
    holder[0] = dialog
    return dialog
}

/**
 * Tick-many list in the app's dialog with an [action] button; the close icon cancels.
 * [checked] is updated as the user ticks. [onToggle] runs on a user tick and may return
 * false to reject it, which un-does the tick. [OlDialog.setItemChecked] ticks a row from outside.
 */
fun Context.createMultiChoiceDialog(
    @StringRes title: Int,
    items: List<CharSequence>,
    checked: BooleanArray,
    @StringRes action: Int,
    onToggle: (index: Int, isChecked: Boolean) -> Boolean = { _, _ -> true },
    onAction: () -> Unit,
): OlDialog {
    val boxes = mutableListOf<CompoundButton>()
    var silent = false
    val dialog = createDialog(title, action, onAction = onAction) { parent ->
        val context = parent.context
        context.scrollingRows(
            items.mapIndexed { index, label ->
                AppCompatCheckBox(context).also { box ->
                    box.styleAsDialogRow(context)
                    box.text = label
                    box.isChecked = checked[index]
                    box.setOnCheckedChangeListener { _, isChecked ->
                        if (silent) return@setOnCheckedChangeListener
                        Haptics.perform(box, Haptics.Kind.MENU)
                        checked[index] = isChecked
                        if (!onToggle(index, isChecked)) {
                            checked[index] = !isChecked
                            silent = true
                            box.isChecked = !isChecked
                            silent = false
                        }
                    }
                    boxes.add(box)
                }
            }
        )
    }
    dialog.setItemChecked = { index, isChecked ->
        checked[index] = isChecked
        silent = true
        boxes.getOrNull(index)?.isChecked = isChecked
        silent = false
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

