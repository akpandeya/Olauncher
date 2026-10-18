package app.olauncher.helper

/**
 * Decides whether MainActivity should pop back to the home screen when it leaves the
 * foreground (onUserLeaveHint / onStop).
 *
 * Starting another activity for a result (photo picker, document picker) also fires
 * onUserLeaveHint and usually onStop. Popping the calling fragment then destroys it, and its
 * activity-result callback is dropped, so the picked result never arrives.
 */
object HomeReturnPolicy {
    fun shouldPopToHome(
        isOnHome: Boolean,
        privateSpaceToggling: Boolean,
        awaitingActivityResult: Boolean,
    ): Boolean = !isOnHome && !privateSpaceToggling && !awaitingActivityResult
}
