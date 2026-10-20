package app.olauncher.helper

object SheetHeight {
    fun maxListHeight(screenHeightPx: Int, fraction: Double): Int =
        (screenHeightPx * fraction).toInt().coerceAtLeast(0)
}
