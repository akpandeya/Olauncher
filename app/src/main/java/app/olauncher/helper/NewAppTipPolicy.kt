package app.olauncher.helper

/** When the one-time "new app" tip may show: once, and never over the keyboard. */
object NewAppTipPolicy {
    fun shouldShow(
        alreadySeen: Boolean,
        isAppLaunchDrawer: Boolean,
        hasNewApp: Boolean,
        keyboardVisible: Boolean,
        searchFocused: Boolean,
        keyboardWasShown: Boolean,
    ): Boolean {
        if (alreadySeen || !isAppLaunchDrawer || !hasNewApp || keyboardVisible) return false
        // Search focus with no keyboard yet means the keyboard is about to open: wait for it to close.
        if (searchFocused && !keyboardWasShown) return false
        return true
    }
}
