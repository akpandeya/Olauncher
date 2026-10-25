package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherSimClockTest {

    private val delta = 1e-4f

    @Test
    fun fixedStepExecutionAndBacklogPreservation() {
        val clock = WeatherSimClock(autoWarmUp = false)
        val stepDts = mutableListOf<Float>()

        // Step is 1/30 s = 0.033333 s
        // Tick with 0.07 s -> should execute 2 steps (0.066667 s) and retain remaining backlog (~0.003333 s)
        val steps1 = clock.tick(0.07f) { dt -> stepDts.add(dt) }
        assertEquals(2, steps1)
        assertEquals(2, stepDts.size)
        assertEquals(WeatherSimClock.STEP, stepDts[0], delta)
        assertEquals(WeatherSimClock.STEP, stepDts[1], delta)
        assertEquals(0.07f - 2 * WeatherSimClock.STEP, clock.backlog, delta)

        // Tick with 0.031 s -> backlog becomes ~0.034333 s >= 1/30 s -> executes 1 step
        val steps2 = clock.tick(0.031f) { dt -> stepDts.add(dt) }
        assertEquals(1, steps2)
        assertEquals(3, stepDts.size)
    }

    @Test
    fun fiveStepCapPerFrame() {
        val clock = WeatherSimClock(autoWarmUp = false)
        var stepsRun = 0

        // 0.3 s corresponds to 9 steps of 1/30 s, but max per frame is 5
        val steps = clock.tick(0.3f) { stepsRun++ }
        assertEquals(5, steps)
        assertEquals(5, stepsRun)
    }

    @Test
    fun remainingBacklogDroppedWhenBehind() {
        val clock = WeatherSimClock(autoWarmUp = false)

        // Tick with 0.5 s (15 steps). After 5 steps, backlog would still be 10 steps.
        // It must drop the remaining backlog when behind.
        clock.tick(0.5f) { /* no-op */ }
        assertEquals("Remaining backlog must be dropped to 0 when behind", 0f, clock.backlog, delta)

        // Next tick with 0.01 s (< 1/30 s) should execute 0 steps because old backlog was dropped
        var nextSteps = 0
        clock.tick(0.01f) { nextSteps++ }
        assertEquals(0, nextSteps)
        assertEquals(0.01f, clock.backlog, delta)
    }

    @Test
    fun warmUpRuns180StepsAndPopulatesDropsOnGlass() {
        val sim = WeatherSim(
            look = WeatherLook.LIGHT_RAIN,
            width = 390f,
            height = 844f,
            seed = 123L,
        )

        // Before warm-up, drops list is empty
        assertEquals(0, sim.drops.size)

        var warmUpStepCount = 0
        val clock = WeatherSimClock(
            warmUpSeconds = 6f,
            onStep = { dt ->
                warmUpStepCount++
                sim.updateHome(dt)
            }
        )

        // 6 s / (1/30 s) = 180 steps
        assertEquals(WeatherSimClock.WARM_UP_STEPS, warmUpStepCount)
        assertEquals(180, warmUpStepCount)

        // Drops must already be on the glass
        assertTrue("Drops must be present on the glass after 6 s warm-up", sim.drops.isNotEmpty())
    }

    @Test
    fun secondaryConstructorWithSimAutoWarmsUp() {
        val sim = WeatherSim(
            look = WeatherLook.HEAVY_RAIN,
            width = 390f,
            height = 844f,
            seed = 456L,
        )

        assertEquals(0, sim.drops.size)
        val clock = WeatherSimClock(sim = sim, autoWarmUp = true)

        assertTrue("Sim must have drops after auto warm-up", sim.drops.isNotEmpty())

        // Tick through clock directly
        val executed = clock.tick(WeatherSimClock.STEP)
        assertEquals(1, executed)
    }
}
