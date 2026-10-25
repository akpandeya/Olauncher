package app.olauncher.helper

/**
 * Fixed-step simulation clock. Context-free.
 * Runs at a fixed step (1/30 s), at most 5 steps per drawn frame,
 * dropping remaining backlog if behind. Supports a 6 s warm-up
 * (run 6 / (1/30) = 180 steps) so drops are already on the glass.
 */
class WeatherSimClock(
    val step: Float = STEP,
    val maxStepsPerFrame: Int = MAX_STEPS_PER_FRAME,
    val warmUpSeconds: Float = WARM_UP_SECONDS,
    val autoWarmUp: Boolean = true,
    onStep: ((Float) -> Unit)? = null,
) {
    companion object {
        const val STEP: Float = 1f / 30f
        const val MAX_STEPS_PER_FRAME: Int = 5
        const val WARM_UP_SECONDS: Float = 6f
        const val WARM_UP_STEPS: Int = 180
    }

    private var simRef: WeatherSim? = null
    var backlog: Float = 0f
        private set

    constructor(sim: WeatherSim, autoWarmUp: Boolean = true) : this(
        autoWarmUp = autoWarmUp,
        onStep = if (autoWarmUp) { dt -> sim.updateHome(dt) } else null,
    ) {
        this.simRef = sim
    }

    init {
        if (autoWarmUp && onStep != null && warmUpSeconds > 0f) {
            warmUp(onStep)
        }
    }

    fun warmUp(stepAction: (Float) -> Unit) {
        val steps = Math.round(warmUpSeconds / step)
        for (i in 0 until steps) {
            stepAction(step)
        }
    }

    fun warmUp(sim: WeatherSim) {
        warmUp { dt -> sim.updateHome(dt) }
    }

    /**
     * Ticks the clock with [dtSeconds] elapsed since the last frame.
     * Executes up to [maxStepsPerFrame] steps of [step] seconds each via [stepAction].
     * If the clock is still behind after [maxStepsPerFrame] steps, any remaining backlog is dropped.
     * Returns the number of steps executed.
     */
    fun tick(dtSeconds: Float, stepAction: (Float) -> Unit): Int {
        if (dtSeconds <= 0f) return 0
        backlog += dtSeconds
        var stepsExecuted = 0
        while (backlog >= step && stepsExecuted < maxStepsPerFrame) {
            stepAction(step)
            backlog -= step
            stepsExecuted++
        }
        if (backlog >= step) {
            backlog = 0f
        }
        return stepsExecuted
    }

    fun tick(dtSeconds: Float): Int {
        val sim = simRef ?: error("WeatherSimClock has no attached WeatherSim; provide a stepAction lambda")
        return tick(dtSeconds) { dt -> sim.updateHome(dt) }
    }
}
