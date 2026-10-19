package app.olauncher.helper

/** Context-free list of the TalkBack custom actions the home screen offers for the current prefs. */
enum class HomeAccessibilityAction { OPEN_APPS, OPEN_TODAY, SETTINGS, LOCK_SCREEN, SWIPE_LEFT_APP, SWIPE_RIGHT_APP }

object HomeAccessibilityActions {
    fun enabled(
        lockModeOn: Boolean,
        swipeLeftEnabled: Boolean,
        swipeRightEnabled: Boolean,
    ): List<HomeAccessibilityAction> = buildList {
        add(HomeAccessibilityAction.OPEN_APPS)
        add(HomeAccessibilityAction.OPEN_TODAY)
        add(HomeAccessibilityAction.SETTINGS)
        if (lockModeOn) add(HomeAccessibilityAction.LOCK_SCREEN)
        if (swipeLeftEnabled) add(HomeAccessibilityAction.SWIPE_LEFT_APP)
        if (swipeRightEnabled) add(HomeAccessibilityAction.SWIPE_RIGHT_APP)
    }

    /** The pause countdown is announced at its first step and at 0, never in between. */
    fun shouldAnnounceCountdown(remaining: Int, startSeconds: Int): Boolean =
        remaining <= 0 || remaining == startSeconds
}
