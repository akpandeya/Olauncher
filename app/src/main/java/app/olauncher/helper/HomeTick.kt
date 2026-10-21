package app.olauncher.helper

/** Context-free decisions for the single every-minute tick on home. */
object HomeTick {
    fun needsTick(homeSets: Boolean, wordsClock: Boolean, contextLine: Boolean, musicPulse: Boolean): Boolean =
        homeSets || wordsClock || contextLine || musicPulse

    fun dayChanged(lastRenderedEpochDay: Long?, nowEpochDay: Long): Boolean =
        lastRenderedEpochDay != null && lastRenderedEpochDay != nowEpochDay
}
