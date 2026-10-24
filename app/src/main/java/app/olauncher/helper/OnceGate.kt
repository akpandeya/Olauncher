package app.olauncher.helper

/**
 * Answers true the first time each key is asked and false after, so a one-shot motion (such as
 * a progress bar filling from 0) runs once per owner lifetime and not on every view rebuild.
 */
class OnceGate {
    private val seen = HashSet<String>()

    fun firstTime(key: String): Boolean = seen.add(key)
}
