package app.olauncher.helper

import app.olauncher.data.store.AiPracticeGate
import app.olauncher.data.store.AiResult

/**
 * The worker wiring behind the settings switch (#602, 9.9): on means the unique
 * periodic `ai_practice` work is scheduled (starting 6 h later — the first
 * top-up is the one-shot `ai_practice_now` run, so two workers never race on the
 * same queue, review R1 #3); off means everything is idle — both work names are
 * cancelled and nothing is generated. The logic is context-free and unit-tested
 * with a fake; the real scheduler only forwards to [PracticeWorker].
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

/**
 * Context-free decisions for one worker run (review R1 #6): a standing key
 * rejection pauses every call until a new key is saved (the save clears the
 * flag), and a provider refusal that retrying cannot fix — AUTH, BILLING — ends
 * the run before the digest calls the provider again in the same run.
 */
object PracticeWorkerPolicy {
    fun mayCall(enabled: Boolean, hasKey: Boolean, model: String, keyRejected: Boolean): Boolean =
        AiPracticeGate.canRun(enabled, hasKey, model) && !keyRejected

    fun stopsRun(kind: AiResult.ErrorKind): Boolean =
        kind == AiResult.ErrorKind.AUTH || kind == AiResult.ErrorKind.BILLING
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
