package app.olauncher.wellbeing

/** One bar of the 7 days strip. */
data class DayBar(val epochDay: Long, val minutes: Int, val isToday: Boolean, val overAllowance: Boolean)

/** Everything the 7 days side of the Wellbeing card shows. All counts are zero, never missing. */
data class WeekBento(
    val bars: List<DayBar>,
    val totalMinutes: Int,
    /** Negative: less than the week before. Null when there is nothing to compare. */
    val changePercent: Int?,
    val topApps: List<AppMinutes>,
    val daysOverAllowance: Int,
    val allowanceMin: Int,
    val notNow: Int,
    val opened: Int,
    val topWhy: WhyChoice?,
    val water: Int,
    val walk: Int,
) {
    /** Height of the tallest bar, or the allowance line if that is higher; never zero. */
    val scaleMinutes: Int get() = maxOf(bars.maxOfOrNull { it.minutes } ?: 0, allowanceMin, 1)
}

/** Context-free numbers for the Wellbeing card's Today and 7 days sides. */
object WellbeingBento {

    const val DAYS = 7
    const val TOP_APPS = 3

    /**
     * The seven days ending [today]. Earlier days come from [snapshots]; [today] comes from [live]
     * (a snapshot built from the live counters), replacing any stored row for that day.
     */
    fun week(snapshots: List<DaySnapshot>, today: Long, live: DaySnapshot, allowanceMin: Int): WeekBento {
        val liveToday = live.copy(epochDay = today)
        val all = snapshots.filter { it.epochDay != today } + liveToday
        // The review window is the seven days before its "today", so ask it as of tomorrow.
        val review = WeeklyReview.summarise(all, today + 1, TOP_APPS)
        val bars = ((today - (DAYS - 1))..today).map { day ->
            val minutes = all.filter { it.epochDay == day }.mapNotNull { it.distractingMinutes }.maxOrNull() ?: 0
            DayBar(day, minutes, day == today, allowanceMin > 0 && minutes >= allowanceMin)
        }
        return WeekBento(
            bars = bars,
            totalMinutes = bars.sumOf { it.minutes },
            changePercent = review.weekChangePercent,
            topApps = review.topApps.map { (label, minutes) -> AppMinutes(label, minutes) },
            daysOverAllowance = bars.count { it.overAllowance },
            allowanceMin = allowanceMin.coerceAtLeast(0),
            notNow = review.pauseNotNow,
            opened = review.pauseOpened,
            topWhy = review.topWhy,
            water = review.water,
            walk = review.walk,
        )
    }

    /** The most chosen reason; ties go to the alphabetically first key. Null when none was chosen. */
    fun topWhy(why: Map<String, Int>): WhyChoice? =
        why.entries.filter { it.value > 0 }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .firstOrNull()?.let { WeeklyReview.resolveWhy(it.key) }

    /** Top apps by minutes for a day snapshot, at most [TOP_APPS]. */
    fun topAppsOf(millisByPackage: Map<String, Long>, label: (String) -> String): List<Pair<String, Int>> =
        millisByPackage.filter { it.value >= 60_000L }
            .entries.sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .take(TOP_APPS)
            .map { label(it.key) to (it.value / 60_000L).toInt() }

    fun meterFill(percent: Int): Float = AllowanceMeter.fill(percent)
    fun meterFill(usedMinutes: Int, allowanceMin: Int): Float = AllowanceMeter.fill(usedMinutes, allowanceMin)
    fun meterBand(percent: Int): AllowanceBand = AllowanceMeter.band(percent)
    fun meterBand(usedMinutes: Int, allowanceMin: Int): AllowanceBand = AllowanceMeter.band(usedMinutes, allowanceMin)
    fun allowanceMeter(percent: Int): AllowanceMeter = AllowanceMeter.calculate(percent)
    fun allowanceMeter(usedMinutes: Int, allowanceMin: Int): AllowanceMeter = AllowanceMeter.calculate(usedMinutes, allowanceMin)
}
