package app.olauncher.helper

/**
 * The worker wiring behind the settings switch (#602, 9.9): on means the unique
 * periodic `ai_practice` work is scheduled plus one immediate run for the first
 * top-up; off means everything is idle — both work names are cancelled and nothing
 * is generated. The logic is context-free and unit-tested with a fake; the real
 * scheduler only forwards to [PracticeWorker].
 */
interface AiPracticeScheduler {
    fun schedulePeriodic()
    fun runOnce()
    fun cancel()
}

class PracticeWorkerScheduler(private val context: android.content.Context) : AiPracticeScheduler {
    override fun schedulePeriodic() = PracticeWorker.schedulePeriodic(context)
    override fun runOnce() = PracticeWorker.runOnce(context)
    override fun cancel() = PracticeWorker.cancel(context)
}

object AiPracticeToggle {

    /** Applies the switch state to the worker: on = schedule + first top-up, off = cancel. */
    fun apply(on: Boolean, scheduler: AiPracticeScheduler) {
        if (on) {
            scheduler.schedulePeriodic()
            scheduler.runOnce()
        } else {
            scheduler.cancel()
        }
    }
}
