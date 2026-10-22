package app.olauncher.ui

import app.olauncher.helper.createChoiceDialog
import android.content.Context
import android.util.TypedValue
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.setPadding
import androidx.navigation.fragment.findNavController
import app.olauncher.R
import app.olauncher.data.Constants
import app.olauncher.data.ShortcutAction
import app.olauncher.data.ShortcutOffset
import app.olauncher.data.ShortcutSize
import app.olauncher.data.SlotCount
import app.olauncher.helper.Haptics
import app.olauncher.helper.TouchTarget
import app.olauncher.helper.dpToPx
import app.olauncher.helper.openCameraApp
import app.olauncher.helper.openDialerApp
import app.olauncher.helper.openGalleryApp
import app.olauncher.helper.openMessagesApp
import app.olauncher.listener.ViewSwipeTouchListener

internal fun HomeFragment.getFittingShortcutCount(): Int {
    val displayMetrics = resources.displayMetrics
    val density = displayMetrics.density.takeIf { it > 0f } ?: 1f
    val columnHeightDp = (displayMetrics.heightPixels / density).toInt()
    val viewSizeDp = ShortcutSize.slotSizeDp(prefs.shortcutSizeStep)
    val bottomMarginDp = ShortcutOffset.bottomMarginDp(prefs.shortcutOffsetStep)
    return SlotCount.fitting(prefs.shortcutCount, columnHeightDp, viewSizeDp, bottomMarginDp)
}

internal fun HomeFragment.setupShortcutViews() {
    val context = context ?: return
    val count = getFittingShortcutCount()
    val viewSizeDp = ShortcutSize.viewSizeDp(prefs.shortcutSizeStep)
    val viewSizePx = viewSizeDp.dpToPx()
    val targetSizePx = TouchTarget.targetSizePx(viewSizePx, TouchTarget.MIN_TARGET_DP.dpToPx())
    binding.shortcutsLayout.removeAllViews()
    for (slot in 1..count) {
        val imageView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                targetSizePx,
                targetSizePx,
            )
            val typedValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, typedValue, true)
            setBackgroundResource(typedValue.resourceId)
            tag = slot
            isClickable = true
            isFocusable = true
            bindShortcutIcon(context, this, slot)
            setOnTouchListener(getShortcutSwipeTouchListener(context, this, slot))
            // These fire only on d-pad/keyboard/accessibility events; touch is consumed by ViewSwipeTouchListener
            setOnClickListener {
                Haptics.tick(this)
                shortcutClicked(slot)
            }
            setOnLongClickListener {
                Haptics.longPress(this)
                showShortcutPopup(this, slot)
                true
            }
        }
        binding.shortcutsLayout.addView(imageView)
    }
}

internal fun HomeFragment.populateShortcuts() {
    val context = context ?: return
    val count = getFittingShortcutCount()
    val viewSizePx = ShortcutSize.viewSizeDp(prefs.shortcutSizeStep).dpToPx()
    val expectedViewSizePx = TouchTarget.targetSizePx(viewSizePx, TouchTarget.MIN_TARGET_DP.dpToPx())
    val firstChild = binding.shortcutsLayout.getChildAt(0)
    val currentViewSizePx = firstChild?.layoutParams?.width ?: 0
    if (binding.shortcutsLayout.childCount != count || (count > 0 && currentViewSizePx != expectedViewSizePx)) {
        setupShortcutViews()
        return
    }
    for (i in 0 until binding.shortcutsLayout.childCount) {
        val view = binding.shortcutsLayout.getChildAt(i) as? ImageView ?: continue
        val slot = (view.tag as? Int) ?: (i + 1)
        bindShortcutIcon(context, view, slot)
    }
}

internal fun HomeFragment.bindShortcutIcon(context: Context, view: ImageView, slot: Int) {
    val viewSizeDp = ShortcutSize.viewSizeDp(prefs.shortcutSizeStep)
    val (drawable, paddingPx, isAppIcon) = ShortcutIconRenderer.render(context, slot, prefs, viewSizeDp)
    val extraPaddingPx = TouchTarget.extraPaddingPx(viewSizeDp.dpToPx(), TouchTarget.MIN_TARGET_DP.dpToPx())
    val totalPaddingPx = paddingPx + extraPaddingPx
    view.setPadding(totalPaddingPx, totalPaddingPx, totalPaddingPx, totalPaddingPx)
    view.setImageDrawable(drawable)
    view.contentDescription = ShortcutIconRenderer.getLabel(context, slot, prefs, isAppIcon)
    // Extra slots (7..MAX_SHORTCUT_SLOTS) show a faint placeholder until an app icon resolves
    val isPlaceholder = !isAppIcon && prefs.getShortcutAction(slot) == null
    view.alpha = if (isPlaceholder) 0.4f else 0.85f
}

internal fun HomeFragment.shortcutClicked(slot: Int) {
    val context = context ?: return
    if (prefs.getShortcutMode(slot) == Constants.SHORTCUT_MODE_APP && prefs.getShortcutPackage(slot).isNotEmpty()) {
        launchApp(
            appName = prefs.getShortcutLabel(slot),
            packageName = prefs.getShortcutPackage(slot),
            activityClassName = prefs.getShortcutClass(slot),
            userString = prefs.getShortcutUser(slot)
        )
        return
    }
    when (prefs.getShortcutAction(slot)) {
        ShortcutAction.SEARCH -> showAppList(Constants.FLAG_LAUNCH_APP)
        ShortcutAction.GALLERY -> openGalleryApp(context)
        ShortcutAction.CAMERA -> openCameraApp(context)
        ShortcutAction.MESSAGES -> openMessagesApp(context)
        ShortcutAction.PHONE -> openDialerApp(context)
        ShortcutAction.SETTINGS -> findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
        null -> showLongPressToast()
    }
}

internal fun HomeFragment.showBuiltinShortcutMenu(slot: Int) {
    val context = context ?: return
    val actions = ShortcutAction.values()
    val labels = actions.map { getString(it.labelRes) }.toTypedArray()
    context.createChoiceDialog(
        title = R.string.shortcut_choose_builtin,
        items = labels.toList(),
    ) { which ->
        prefs.setShortcutBuiltin(slot, actions[which].id)
        populateShortcuts()
    }.showRespectingStatusBar()
}
