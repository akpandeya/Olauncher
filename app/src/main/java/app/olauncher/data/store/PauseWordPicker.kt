package app.olauncher.data.store

object PauseWordPicker {
    fun pick(due: List<Word>, lastShownId: Long?): Word? {
        if (due.isEmpty()) return null
        return due.firstOrNull { it.id != lastShownId } ?: due.first()
    }

    /**
     * The same rotation for any due type (the web due words of epic #835,
     * task 04, whose ids are server text ids): the first due entry that is
     * not the last one shown, else the first.
     */
    fun <T> pick(due: List<T>, lastShownId: Any?, idOf: (T) -> Any?): T? {
        if (due.isEmpty()) return null
        return due.firstOrNull { idOf(it) != lastShownId } ?: due.first()
    }
}
