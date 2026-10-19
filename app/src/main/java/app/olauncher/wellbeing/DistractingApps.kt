package app.olauncher.wellbeing

/**
 * Wellbeing utilities and candidate app lists for distracting / social media apps.
 */
object DistractingApps {

    /**
     * Default candidate packages pre-selected on first configuration if installed.
     */
    val DEFAULT_PACKAGES: Set<String> = setOf(
        "com.instagram.android",
        "com.zhiliaoapp.musically",
        "com.google.android.youtube",
        "com.reddit.frontpage",
        "com.twitter.android",
        "com.facebook.katana",
        "com.snapchat.android",
    )

    /**
     * Pre-selects installed candidate apps when configuring distracting apps for the first time.
     * If the user has already saved their choices (even an empty set), returns the intersection
     * of saved apps and installed apps.
     */
    fun initialSelection(
        installedPackages: Set<String>,
        saved: Set<String>,
        hasSaved: Boolean = false,
    ): Set<String> {
        return if (!hasSaved && saved.isEmpty()) {
            DEFAULT_PACKAGES.filter { it in installedPackages }.toSet()
        } else {
            saved.filter { it in installedPackages }.toSet()
        }
    }

    /**
     * Checks if [pkg] is considered distracting according to [distractingApps].
     */
    fun isDistracting(pkg: String, distractingApps: Set<String>): Boolean = pkg in distractingApps

    /**
     * Checks whether an app launch should be intercepted by the pause screen.
     */
    fun shouldPauseLaunch(
        pkg: String?,
        pauseEnabled: Boolean,
        distractingApps: Set<String>,
        bypassPackage: String? = null,
        snoozeUntil: Long = 0L,
        appSnoozes: Map<String, Long> = emptyMap(),
        now: Long = 0L,
    ): Boolean {
        if (PauseSnooze.isActive(snoozeUntil, now)) return false
        if (pkg != null && PauseSnooze.isAppSnoozed(pkg, appSnoozes, now)) return false
        return pauseEnabled && pkg != null && pkg in distractingApps && pkg != bypassPackage
    }
}
