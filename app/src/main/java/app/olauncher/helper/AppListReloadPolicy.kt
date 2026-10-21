package app.olauncher.helper

enum class AppListReloadTrigger {
    RESUME,
    PACKAGE_ADDED,
    PACKAGE_REMOVED,
    PACKAGE_CHANGED,
    PACKAGES_AVAILABILITY_CHANGED,
    SHORTCUTS_CHANGED,
    APP_HIDDEN_OR_UNHIDDEN,
    APP_RENAMED,
}

object AppListReloadPolicy {
    /** The first load always happens; after that only real changes reload, never a plain resume. */
    fun shouldReload(trigger: AppListReloadTrigger, isListLoaded: Boolean): Boolean {
        if (!isListLoaded) return true
        return trigger != AppListReloadTrigger.RESUME
    }
}
