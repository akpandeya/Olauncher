package app.olauncher.helper

/**
 * Context-free rules for "is Thumbline the default home app?".
 *
 * The HOME role (RoleManager) is the source of truth. The resolved HOME
 * activity is only a fallback, and the platform chooser ("android") or a
 * missing result tells us nothing, so it is not counted as "not default".
 */
object DefaultLauncherCheck {

    /** Package name the platform returns when it would show the HOME chooser. */
    const val CHOOSER_PACKAGE = "android"

    /**
     * @param roleHeld result of RoleManager.isRoleHeld(ROLE_HOME), or null when
     *   the role is unavailable or the call failed.
     * @param resolvedPackage package of the resolved HOME activity, or null.
     * @return true or false when the answer is known, null when it is inconclusive.
     */
    fun isDefault(roleHeld: Boolean?, resolvedPackage: String?, ownPackage: String): Boolean? {
        if (roleHeld != null) return roleHeld
        if (resolvedPackage.isNullOrBlank() || resolvedPackage == CHOOSER_PACKAGE) return null
        return resolvedPackage == ownPackage
    }

    /**
     * Bottom alignment only applies while Thumbline is the default launcher, but the
     * saved preference is never cleared here: it comes back once the role is held.
     */
    fun useBottomAlignment(bottomPref: Boolean, isDefault: Boolean?): Boolean =
        bottomPref && isDefault != false
}
