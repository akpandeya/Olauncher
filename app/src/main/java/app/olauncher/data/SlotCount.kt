package app.olauncher.data

object SlotCount {
    const val MIN = 3
    const val MAX = 8
    const val DEFAULT = 6

    fun clamp(v: Int): Int = v.coerceIn(MIN, MAX)
}
