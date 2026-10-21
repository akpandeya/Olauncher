package app.olauncher.wellbeing

import kotlin.math.max
import kotlin.math.roundToLong

/** A Health Connect step record as epoch milliseconds. [origin] is the writing app's package. */
data class StepSpan(val startMs: Long, val endMs: Long, val steps: Long, val origin: String)

/** A walking exercise session as epoch milliseconds. */
data class WalkSpan(val startMs: Long, val endMs: Long)

/** Walks found in one day. [totalWalks] = [sessionWalks] + [boutWalks]. */
data class WalkDetection(val totalWalks: Int, val sessionWalks: Int, val boutWalks: Int)

/** The one rule for the walk count shown to the user: automatic walks plus manual taps. */
object WalkCount {
    fun shown(auto: Int?, manualTaps: Int): Int = (auto ?: 0) + manualTaps
}

object WalkDetector {
    const val MIN_BOUT_STEPS = 200L
    const val MAX_GAP_MINUTES = 5L

    /** Average steps per active minute a bout needs; slower spreads are not walks. */
    const val MIN_STEPS_PER_MINUTE = 60.0

    /** Records longer than this are daily totals (e.g. one record per day) and are ignored. */
    const val MAX_STEP_RECORD_MS = 12L * 60 * 60 * 1000

    private const val MINUTE_MS = 60_000L

    fun detect(steps: List<StepSpan>, sessions: List<WalkSpan>, dayStartMs: Long, dayEndMs: Long): WalkDetection {
        val daySessions = sessions.filter { minOf(it.endMs, dayEndMs) > max(it.startMs, dayStartMs) }
        val bouts = findBouts(steps, dayStartMs, dayEndMs)
            .filter { bout -> daySessions.none { it.startMs < bout.endMs && it.endMs > bout.startMs } }
        return WalkDetection(daySessions.size + bouts.size, daySessions.size, bouts.size)
    }

    private class Bout(val startMs: Long, val endMs: Long)

    private fun findBouts(steps: List<StepSpan>, dayStartMs: Long, dayEndMs: Long): List<Bout> {
        // minute index -> origin -> steps in that minute
        val perMinute = HashMap<Long, HashMap<String, Double>>()
        for (s in steps) {
            val length = s.endMs - s.startMs
            if (length <= 0 || length > MAX_STEP_RECORD_MS || s.steps <= 0) continue
            val from = max(s.startMs, dayStartMs)
            val to = minOf(s.endMs, dayEndMs)
            if (to <= from) continue
            var minute = Math.floorDiv(from, MINUTE_MS)
            while (minute * MINUTE_MS < to) {
                val overlap = minOf(to, (minute + 1) * MINUTE_MS) - max(from, minute * MINUTE_MS)
                if (overlap > 0) {
                    val share = s.steps.toDouble() * overlap / length
                    val byOrigin = perMinute.getOrPut(minute) { HashMap() }
                    byOrigin[s.origin] = (byOrigin[s.origin] ?: 0.0) + share
                }
                minute++
            }
        }
        val active = perMinute.keys.sorted()
        val bouts = ArrayList<Bout>()
        var i = 0
        while (i < active.size) {
            var last = active[i]
            var total = perMinute.getValue(last).values.max()
            var activeMinutes = 1
            var j = i + 1
            while (j < active.size && active[j] - last - 1 <= MAX_GAP_MINUTES) {
                last = active[j]
                total += perMinute.getValue(last).values.max()
                activeMinutes++
                j++
            }
            if (total.roundToLong() >= MIN_BOUT_STEPS && total / activeMinutes >= MIN_STEPS_PER_MINUTE) {
                bouts.add(Bout(active[i] * MINUTE_MS, (last + 1) * MINUTE_MS))
            }
            i = j
        }
        return bouts
    }
}

/** Reads every page of a paged source: [readPage] takes a page token (null for the first) and returns items plus the next token. */
suspend fun <T> readAllPages(readPage: suspend (String?) -> Pair<List<T>, String?>): List<T> {
    val all = ArrayList<T>()
    var token: String? = null
    do {
        val (items, next) = readPage(token)
        all.addAll(items)
        token = next
    } while (token != null)
    return all
}
