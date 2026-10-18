package app.olauncher.quiet

object HoldPolicy {
    const val CATEGORY_CALL = "call"
    const val CATEGORY_ALARM = "alarm"

    fun shouldHold(
        pkg: String,
        distracting: Set<String>,
        ownPkg: String,
        category: String?,
        isOngoing: Boolean,
        isForegroundService: Boolean,
        isGroupSummary: Boolean
    ): Boolean {
        if (pkg == ownPkg) return false
        if (pkg !in distracting) return false
        if (isOngoing) return false
        if (isForegroundService) return false
        if (category == CATEGORY_CALL || category == CATEGORY_ALARM) return false
        if (isGroupSummary) return false
        return true
    }
}
