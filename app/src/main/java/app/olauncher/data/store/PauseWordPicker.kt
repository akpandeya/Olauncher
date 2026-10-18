package app.olauncher.data.store

object PauseWordPicker {
    fun pick(due: List<Word>, lastShownId: Long?): Word? {
        if (due.isEmpty()) return null
        return due.firstOrNull { it.id != lastShownId } ?: due.first()
    }
}
