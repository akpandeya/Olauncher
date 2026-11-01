package app.olauncher.helper

import android.content.Context

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
}

object NewsHarvestPolicy {
    fun mayRun(enabled: Boolean, germanEnabled: Boolean): Boolean = enabled && germanEnabled

    fun articlesLeftToday(cap: Int, fetchedToday: Int): Int = (cap - fetchedToday).coerceAtLeast(0)
}
