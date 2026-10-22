package app.olauncher.helper

/** Home gestures whose action the user can choose. */
enum class GestureSlot(val key: String) {
    DOUBLE_TAP("DOUBLE_TAP"),
    CLOCK_TAP("CLOCK_TAP"),
    DATE_TAP("DATE_TAP"),
}

/** What a gesture can do. DEFAULT is today's built-in behaviour for that gesture. */
enum class GestureAction(val key: String) {
    DEFAULT("default"),
    LOCK("lock"),
    TORCH("torch"),
    NEXT_TRACK("next_track"),
    PREVIOUS_TRACK("previous_track"),
    OPEN_APP("open_app"),
    NOTHING("nothing"),
}

/** A saved target app of an "open app" gesture. */
data class GestureApp(
    val label: String,
    val packageName: String,
    val className: String?,
    val user: String,
)

/** Context-free rules for the custom gestures. */
object GestureActions {
    /** The choices offered in the settings menu: Default first. */
    fun options(slot: GestureSlot): List<GestureAction> = GestureAction.values().toList()

    /** Stored value to action. Unknown or missing values fall back to DEFAULT, so old installs keep today's behaviour. */
    fun fromKey(key: String?): GestureAction = GestureAction.values().firstOrNull { it.key == key } ?: GestureAction.DEFAULT

    /**
     * What a gesture does right now.
     * Double-tap DEFAULT is today's behaviour: lock when lock mode is on, otherwise nothing.
     * A saved double-tap LOCK follows the same lock mode switch, so switching it off stops the lock.
     * Clock and date DEFAULT stay DEFAULT: the caller opens its own app. OPEN_APP without a saved app does nothing.
     */
    fun resolve(slot: GestureSlot, stored: GestureAction, lockModeOn: Boolean, hasApp: Boolean): GestureAction = when {
        stored == GestureAction.OPEN_APP && !hasApp -> GestureAction.NOTHING
        // "Double tap to lock" off stops double-tap locking, whether the choice is Default or Lock.
        slot == GestureSlot.DOUBLE_TAP && (stored == GestureAction.DEFAULT || stored == GestureAction.LOCK) ->
            if (lockModeOn) GestureAction.LOCK else GestureAction.NOTHING
        else -> stored
    }

    /** Only locking needs the accessibility service, and only when it is chosen. */
    fun needsAccessibilityService(action: GestureAction): Boolean = action == GestureAction.LOCK
}
