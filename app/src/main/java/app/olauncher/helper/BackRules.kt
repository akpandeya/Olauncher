package app.olauncher.helper

/**
 * Decides whether the activity should register an app-level back callback.
 *
 * Home never needs one: the system handles back there (and keeps the predictive-back preview).
 * Any other screen pops the nav stack, and an open search query is closed before leaving.
 */
object BackRules {
    fun shouldConsumeBack(isHome: Boolean, searchOpen: Boolean): Boolean =
        !isHome || searchOpen
}
