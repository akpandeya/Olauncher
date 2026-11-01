package app.olauncher.helper

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean

interface NewsHarvestScheduler {
    fun schedulePeriodic()
    fun runOnce()
    fun cancel()
}

class NewsHarvestWorkerScheduler(private val context: Context) : NewsHarvestScheduler {
    override fun schedulePeriodic() = NewsHarvestWorker.schedulePeriodic(context)
    override fun runOnce() = NewsHarvestWorker.runOnce(context)
    override fun cancel() = NewsHarvestWorker.cancel(context)
}

object NewsHarvestToggle {
    fun apply(on: Boolean, scheduler: NewsHarvestScheduler) {
        if (on) {
            scheduler.schedulePeriodic()
            scheduler.runOnce()
        } else {
            scheduler.cancel()
        }
    }

    fun onGermanToggled(
        germanEnabled: Boolean,
        newsSentencesEnabled: Boolean,
        scheduler: NewsHarvestScheduler
    ) {
        if (!germanEnabled) {
            scheduler.cancel()
        } else if (newsSentencesEnabled) {
            scheduler.schedulePeriodic()
        }
    }

    /** Brings the periodic work in line with both flags, e.g. after a backup restore. */
    fun sync(germanEnabled: Boolean, newsSentencesEnabled: Boolean, scheduler: NewsHarvestScheduler) {
        if (NewsHarvestPolicy.mayRun(newsSentencesEnabled, germanEnabled)) {
            scheduler.schedulePeriodic()
        } else {
            scheduler.cancel()
        }
    }
}

object NewsHarvestPolicy {
    fun mayRun(enabled: Boolean, germanEnabled: Boolean): Boolean = enabled && germanEnabled

    fun articlesLeftToday(cap: Int, fetchedToday: Int): Int = (cap - fetchedToday).coerceAtLeast(0)
}

/**
 * Lets one harvest run at a time. Switching the feature on enqueues the periodic work and a
 * one-off run together, and WorkManager starts both at once; without this guard the two runs
 * would fetch the same feeds twice, each spend the daily cap read before the other counted,
 * and race on `recent_sentences.json` from two store instances.
 */
class NewsHarvestRunGuard {
    private val running = AtomicBoolean(false)

    /** Runs [block] unless another run is in progress; returns null when it was skipped. */
    suspend fun <T> runExclusive(block: suspend () -> T): T? {
        if (!running.compareAndSet(false, true)) return null
        try {
            return block()
        } finally {
            running.set(false)
        }
    }
}
