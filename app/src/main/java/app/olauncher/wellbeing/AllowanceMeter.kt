package app.olauncher.wellbeing

enum class AllowanceBand {
    NORMAL,
    WARNING,
    OVER;

    companion object {
        val SAFE: AllowanceBand = NORMAL
        val WARN: AllowanceBand = WARNING
        val DANGER: AllowanceBand = OVER
    }
}

typealias MeterBand = AllowanceBand
typealias AllowanceMeterBand = AllowanceBand

/**
 * Context-free policy for computing allowance meter fill fraction and colour band.
 * Thresholds:
 *   [0, 80)%   -> NORMAL (accent colour)
 *   [80, 100)% -> WARNING (accentSand)
 *   [100, ∞)%  -> OVER (dangerColor)
 */
data class AllowanceMeter(
    val fill: Float,
    val band: AllowanceBand,
    val percent: Int = 0,
    val ratio: Float = fill,
) {
    companion object {
        const val WARNING_THRESHOLD_PERCENT: Int = 80
        const val OVER_THRESHOLD_PERCENT: Int = 100

        fun fill(percent: Int): Float = (percent / 100f).coerceIn(0f, 1f)

        fun fill(usedMinutes: Int, allowanceMin: Int): Float =
            if (allowanceMin <= 0) 0f else (usedMinutes.toFloat() / allowanceMin).coerceIn(0f, 1f)

        fun fill(usedMillis: Long, allowanceMin: Int): Float =
            if (allowanceMin <= 0) 0f else (usedMillis.toFloat() / (allowanceMin * 60_000L)).coerceIn(0f, 1f)

        fun band(percent: Int): AllowanceBand = when {
            percent < WARNING_THRESHOLD_PERCENT -> AllowanceBand.NORMAL
            percent < OVER_THRESHOLD_PERCENT -> AllowanceBand.WARNING
            else -> AllowanceBand.OVER
        }

        fun band(usedMinutes: Int, allowanceMin: Int): AllowanceBand =
            if (allowanceMin <= 0) AllowanceBand.NORMAL
            else band(((usedMinutes * 100.0) / allowanceMin).toInt())

        fun band(usedMillis: Long, allowanceMin: Int): AllowanceBand =
            if (allowanceMin <= 0) AllowanceBand.NORMAL
            else band(((usedMillis * 100.0) / (allowanceMin * 60_000L)).toInt())

        fun meterFill(percent: Int): Float = fill(percent)
        fun meterFill(usedMinutes: Int, allowanceMin: Int): Float = fill(usedMinutes, allowanceMin)
        fun meterBand(percent: Int): AllowanceBand = band(percent)
        fun meterBand(usedMinutes: Int, allowanceMin: Int): AllowanceBand = band(usedMinutes, allowanceMin)

        fun calculate(percent: Int): AllowanceMeter {
            val safePercent = percent.coerceAtLeast(0)
            return AllowanceMeter(
                fill = fill(safePercent),
                band = band(safePercent),
                percent = safePercent,
                ratio = safePercent / 100f,
            )
        }

        fun calculate(usedMinutes: Int, allowanceMin: Int): AllowanceMeter {
            if (allowanceMin <= 0 || usedMinutes <= 0) {
                return AllowanceMeter(0f, AllowanceBand.NORMAL, 0, 0f)
            }
            val pct = ((usedMinutes * 100.0) / allowanceMin).toInt()
            val ratio = usedMinutes.toFloat() / allowanceMin
            return AllowanceMeter(
                fill = ratio.coerceIn(0f, 1f),
                band = band(pct),
                percent = pct,
                ratio = ratio,
            )
        }

        fun calculate(usedMillis: Long, allowanceMin: Int): AllowanceMeter {
            if (allowanceMin <= 0 || usedMillis <= 0L) {
                return AllowanceMeter(0f, AllowanceBand.NORMAL, 0, 0f)
            }
            val allowanceMillis = allowanceMin * 60_000L
            val pct = ((usedMillis * 100.0) / allowanceMillis).toInt()
            val ratio = usedMillis.toFloat() / allowanceMillis
            return AllowanceMeter(
                fill = ratio.coerceIn(0f, 1f),
                band = band(pct),
                percent = pct,
                ratio = ratio,
            )
        }
    }
}
