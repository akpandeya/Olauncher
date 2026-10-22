package app.olauncher.ui

import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import app.olauncher.helper.HomeAccessibilityAction
import app.olauncher.helper.HomeAccessibilityActions
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import app.olauncher.R
import app.olauncher.data.Constants
import app.olauncher.data.Prefs
import app.olauncher.helper.Gesture
import app.olauncher.helper.GestureHints
import app.olauncher.helper.Haptics
import app.olauncher.helper.expandNotificationDrawer
import app.olauncher.helper.openCameraApp
import app.olauncher.helper.openDialerApp
import app.olauncher.helper.PhotoWallpaper
import app.olauncher.helper.WallpaperHomeMenu
import app.olauncher.helper.showPopupMenu
import app.olauncher.listener.OnSwipeTouchListener
import app.olauncher.listener.ViewSwipeTouchListener
import kotlinx.coroutines.launch
import android.util.Log

private const val TAG = "HomeFragmentGestures"

internal fun HomeFragment.showGestureHint() {
    val enabled = buildSet {
        if (prefs.swipeRightEnabled && prefs.swipeRightAction == Prefs.SWIPE_RIGHT_ACTION_TODAY) add(Gesture.SWIPE_RIGHT)
        if (prefs.swipeLeftEnabled) add(Gesture.SWIPE_LEFT)
        if (prefs.lockModeOn) add(Gesture.DOUBLE_TAP)
        add(Gesture.LONG_PRESS)
    }
    val today = prefs.epochDaySupplier()
    val hint = GestureHints.next(prefs.gestureHintIndex, prefs.gestureHintDay, today, enabled) ?: return
    prefs.gestureHintIndex = hint.position + 1
    prefs.gestureHintDay = today
    binding.firstRunTips.text = when (hint.gesture) {
        Gesture.SWIPE_RIGHT -> getString(R.string.hint_swipe_right_today)
        Gesture.SWIPE_LEFT -> getString(R.string.hint_swipe_left_app, prefs.appNameSwipeLeft)
        Gesture.DOUBLE_TAP -> getString(R.string.hint_double_tap_lock)
        Gesture.LONG_PRESS -> getString(R.string.hint_long_press_settings)
    }
    // The index already moved past this hint, so a tap only hides it; the next one comes tomorrow.
    binding.firstRunTips.setOnClickListener {
        binding.firstRunTips.visibility = View.GONE
    }
    binding.firstRunTips.visibility = View.VISIBLE
}

internal fun HomeFragment.initSwipeTouchListener() {
    val context = requireContext()
    binding.mainLayout.setOnTouchListener(getSwipeGestureListener(context))
    binding.homeApp1.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp1))
    binding.homeApp2.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp2))
    binding.homeApp3.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp3))
    binding.homeApp4.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp4))
    binding.homeApp5.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp5))
    binding.homeApp6.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp6))
    binding.homeApp7.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp7))
    binding.homeApp8.setOnTouchListener(getViewSwipeTouchListener(context, binding.homeApp8))
}

/** Gives every gesture a TalkBack custom action. Read from prefs on each query, so it never goes stale. */
internal fun HomeFragment.initAccessibilityActions() {
    ViewCompat.setAccessibilityDelegate(binding.mainLayout, object : AccessibilityDelegateCompat() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            if (_binding == null) return
            HomeAccessibilityActions.enabled(
                lockModeOn = prefs.lockModeOn,
                swipeLeftEnabled = prefs.swipeLeftEnabled,
                // Swipe-right set to Today is already covered by the Open Today action.
                swipeRightEnabled = prefs.swipeRightEnabled && prefs.swipeRightAction != Prefs.SWIPE_RIGHT_ACTION_TODAY,
            ).forEach { action ->
                val label = when (action) {
                    HomeAccessibilityAction.OPEN_APPS -> getString(R.string.a11y_action_open_apps)
                    HomeAccessibilityAction.OPEN_TODAY -> getString(R.string.a11y_action_open_today)
                    HomeAccessibilityAction.SETTINGS -> getString(R.string.a11y_action_settings)
                    HomeAccessibilityAction.LOCK_SCREEN -> getString(R.string.a11y_action_lock_screen)
                    HomeAccessibilityAction.SWIPE_LEFT_APP ->
                        getString(R.string.a11y_action_swipe_left_app, prefs.appNameSwipeLeft)
                    HomeAccessibilityAction.SWIPE_RIGHT_APP ->
                        getString(R.string.a11y_action_swipe_right_app, prefs.appNameSwipeRight)
                }
                info.addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat(accessibilityActionId(action), label))
            }
        }

        override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
            val match = HomeAccessibilityAction.values().firstOrNull { accessibilityActionId(it) == action }
                ?: return super.performAccessibilityAction(host, action, args)
            if (_binding == null) return false
            when (match) {
                HomeAccessibilityAction.OPEN_APPS -> onHomeSwipeUp()
                HomeAccessibilityAction.OPEN_TODAY -> openToday()
                HomeAccessibilityAction.SETTINGS -> openSettings()
                HomeAccessibilityAction.LOCK_SCREEN -> if (prefs.lockModeOn) binding.lock.performClick()
                HomeAccessibilityAction.SWIPE_LEFT_APP -> openSwipeLeftApp()
                HomeAccessibilityAction.SWIPE_RIGHT_APP -> openSwipeRightApp()
            }
            return true
        }
    })
}

internal fun HomeFragment.accessibilityActionId(action: HomeAccessibilityAction): Int = when (action) {
    HomeAccessibilityAction.OPEN_APPS -> R.id.a11y_action_open_apps
    HomeAccessibilityAction.OPEN_TODAY -> R.id.a11y_action_open_today
    HomeAccessibilityAction.SETTINGS -> R.id.a11y_action_settings
    HomeAccessibilityAction.LOCK_SCREEN -> R.id.a11y_action_lock_screen
    HomeAccessibilityAction.SWIPE_LEFT_APP -> R.id.a11y_action_swipe_left_app
    HomeAccessibilityAction.SWIPE_RIGHT_APP -> R.id.a11y_action_swipe_right_app
}

internal fun HomeFragment.getShortcutSwipeTouchListener(context: Context, view: View, slot: Int): View.OnTouchListener {
    return object : ViewSwipeTouchListener(context, view) {
        override fun onSwipeLeft() {
            super.onSwipeLeft()
            openSwipeLeftApp()
        }

        override fun onSwipeRight() {
            super.onSwipeRight()
            openSwipeRightApp()
        }

        override fun onSwipeUp() {
            super.onSwipeUp()
            onHomeSwipeUp()
        }

        override fun onSwipeDown() {
            super.onSwipeDown()
            onHomeSwipeDown(context)
        }

        override fun onLongClick(view: View) {
            super.onLongClick(view)
            Haptics.longPress(view)
            showShortcutPopup(view, slot)
        }

        override fun onClick(view: View) {
            super.onClick(view)
            Haptics.tick(view)
            shortcutClicked(slot)
        }
    }
}

internal fun HomeFragment.onHomeSwipeUp() {
    Haptics.perform(binding.root, Haptics.Kind.GESTURE)
    showAppList(Constants.FLAG_LAUNCH_APP)
}

internal fun HomeFragment.onHomeSwipeDown(context: Context = requireContext()) {
    Haptics.perform(binding.root, Haptics.Kind.GESTURE)
    expandNotificationDrawer(context)
}

internal fun HomeFragment.openSwipeRightApp() {
    if (!prefs.swipeRightEnabled) return
    Haptics.perform(binding.root, Haptics.Kind.GESTURE)
    if (prefs.swipeRightAction == Prefs.SWIPE_RIGHT_ACTION_TODAY) {
        try {
            findNavController().navigate(R.id.action_mainFragment_to_todayFragment)
        } catch (e: Exception) {
            Log.w(TAG, "openSwipeRightApp failed", e)
        }
        return
    }
    launchAppOrShortcut(
        appName = prefs.appNameSwipeRight,
        packageName = prefs.appPackageSwipeRight,
        activityClassName = prefs.appActivityClassNameRight,
        shortcutId = prefs.shortcutIdSwipeRight,
        isShortcut = prefs.isShortcutSwipeRight,
        userString = prefs.appUserSwipeRight,
        fallback = { openDialerApp(requireContext()) }
    )
}

internal fun HomeFragment.openSwipeLeftApp() {
    if (!prefs.swipeLeftEnabled) return
    Haptics.perform(binding.root, Haptics.Kind.GESTURE)
    launchAppOrShortcut(
        appName = prefs.appNameSwipeLeft,
        packageName = prefs.appPackageSwipeLeft,
        activityClassName = prefs.appActivityClassNameSwipeLeft,
        shortcutId = prefs.shortcutIdSwipeLeft,
        isShortcut = prefs.isShortcutSwipeLeft,
        userString = prefs.appUserSwipeLeft,
        fallback = { openCameraApp(requireContext()) }
    )
}

internal fun HomeFragment.textOnClick(view: View) = onClick(view)

internal fun HomeFragment.textOnLongClick(view: View) = onLongClick(view)

internal fun HomeFragment.getSwipeGestureListener(context: Context): View.OnTouchListener {
    return object : OnSwipeTouchListener(context) {
        override fun onSwipeLeft() {
            super.onSwipeLeft()
            openSwipeLeftApp()
        }

        override fun onSwipeRight() {
            super.onSwipeRight()
            openSwipeRightApp()
        }

        override fun onSwipeUp() {
            super.onSwipeUp()
            onHomeSwipeUp()
        }

        override fun onSwipeDown() {
            super.onSwipeDown()
            onHomeSwipeDown(requireContext())
        }

        override fun onLongClick() {
            super.onLongClick()
            if (WallpaperHomeMenu.isShown(prefs.photoWallpaperHomeMenu, prefs.photoWallpaperCount)) {
                Haptics.longPress(binding.mainLayout)
                showWallpaperHomeMenu(downY)
            } else {
                openSettings()
            }
        }

        override fun onDoubleClick() {
            super.onDoubleClick()
            if (!prefs.lockModeOn) return
            binding.lock.performClick()
        }

        override fun onClick() {
            super.onClick()
            viewModel.checkForMessages.call()
        }
    }
}

// Anchored to a 1px-high strip at the press height: anchoring to the
// full-screen layout drops the menu below the bottom edge, off screen.
internal fun HomeFragment.showWallpaperHomeMenu(y: Float) {
    val appContext = requireContext().applicationContext
    val anchor = View(requireContext()).apply {
        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply {
            topMargin = y.toInt()
        }
    }
    binding.mainLayout.addView(anchor)
    // Wait one layout pass so the anchor has its position.
    anchor.post {
        anchor.showPopupMenu(
            configure = { menu ->
                menu.add(Menu.NONE, R.string.next_wallpaper, 0, R.string.next_wallpaper)
                menu.add(Menu.NONE, R.string.previous_wallpaper, 1, R.string.previous_wallpaper)
                menu.add(Menu.NONE, R.string.settings, 2, R.string.settings)
            }
        ) { item ->
            when (item.itemId) {
                R.string.next_wallpaper -> {
                    viewLifecycleOwner.lifecycleScope.launch {
                        PhotoWallpaper.stepManually(appContext, prefs, forward = true)
                    }
                }
                R.string.previous_wallpaper -> {
                    viewLifecycleOwner.lifecycleScope.launch {
                        PhotoWallpaper.stepManually(appContext, prefs, forward = false)
                    }
                }
                R.string.settings -> openSettings()
            }
        }.setOnDismissListener { (anchor.parent as? ViewGroup)?.removeView(anchor) }
    }
}

internal fun HomeFragment.openSettings() {
    try {
        findNavController().navigate(R.id.action_mainFragment_to_settingsFragment)
        viewModel.firstOpen(false)
    } catch (e: Exception) {
        Log.w(TAG, "openSettings failed", e)
    }
}

internal fun HomeFragment.getViewSwipeTouchListener(context: Context, view: View): View.OnTouchListener {
    return object : ViewSwipeTouchListener(context, view) {
        override fun onSwipeLeft() {
            super.onSwipeLeft()
            openSwipeLeftApp()
        }

        override fun onSwipeRight() {
            super.onSwipeRight()
            openSwipeRightApp()
        }

        override fun onSwipeUp() {
            super.onSwipeUp()
            onHomeSwipeUp()
        }

        override fun onSwipeDown() {
            super.onSwipeDown()
            onHomeSwipeDown(requireContext())
        }

        override fun onLongClick(view: View) {
            super.onLongClick(view)
            textOnLongClick(view)
        }

        override fun onClick(view: View) {
            super.onClick(view)
            textOnClick(view)
        }
    }
}
