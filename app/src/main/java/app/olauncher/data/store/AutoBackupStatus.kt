package app.olauncher.data.store

enum class AutoBackupStatus {
    HIDDEN,
    FAILED,
    LAST_OK,
    WAITING;

    companion object {
        fun of(enabled: Boolean, lastOkMillis: Long, lastFailed: Boolean): AutoBackupStatus = when {
            !enabled -> HIDDEN
            lastFailed -> FAILED
            lastOkMillis > 0L -> LAST_OK
            else -> WAITING
        }
    }
}
