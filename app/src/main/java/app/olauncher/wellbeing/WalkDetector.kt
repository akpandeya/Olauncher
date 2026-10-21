package app.olauncher.wellbeing

import kotlin.math.max
import kotlin.math.roundToLong

/** A Health Connect step record as epoch milliseconds. [origin] is the writing app's package. */
data class StepSpan(val startMs: Long, val endMs: Long, val steps: Long, val origin: String)

/** A walking exercise session as epoch milliseconds. */
data class WalkSpan(val startMs: Long, val endMs: Long)

/** Walks found in one day. [totalWalks] = [sessionWalks] + [boutWalks]. */
data class WalkDetection(val totalWalks: Int, val sessionWalks: Int, val boutWalks: Int)

object WalkDetector {
    const val MIN_BOUT_STEPS = 200L
    const val MAX_GAP_MINUTES = 5L

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
            var j = i + 1
            while (j < active.size && active[j] - last - 1 <= MAX_GAP_MINUTES) {
                last = active[j]
                total += perMinute.getValue(last).values.max()
                j++
            }
            if (total.roundToLong() >= MIN_BOUT_STEPS) {
                bouts.add(Bout(active[i] * MINUTE_MS, (last + 1) * MINUTE_MS))
            }
            i = j
        }
        return bouts
    }
}
