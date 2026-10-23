package app.olauncher.helper

/** Ordered handoff attempts. A failed or no-longer-available handler falls through. */
object DictionaryHandoff {
    enum class Action { PROCESS_TEXT, SEND, COPY_AND_LAUNCH }

    fun open(
        supportsProcessText: Boolean,
        supportsSend: Boolean,
        attempt: (Action) -> Boolean,
    ): Boolean {
        if (supportsProcessText && attempt(Action.PROCESS_TEXT)) return true
        if (supportsSend && attempt(Action.SEND)) return true
        return attempt(Action.COPY_AND_LAUNCH)
    }
}
