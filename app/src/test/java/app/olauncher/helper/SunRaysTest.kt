package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SunRaysTest {

    private val delta = 1e-5f

    @Test
    fun returnsExactly18Rays() {
        assertEquals(18, SunRays.rays.size)
        assertEquals(18, SunRays.size)
        assertEquals(18, SunRays.COUNT)
        assertEquals(18, SunRays.RAY_COUNT)

        val instance1 = SunRays()
        assertEquals(18, instance1.size)
        assertEquals(18, instance1.rays.size)

        val instance2 = SunRays(sunPhase = 2.5f)
        assertEquals(18, instance2.size)
    }

    @Test
    fun rayGeometryIdenticalAcrossFramesAndCalls() {
        val phases = floatArrayOf(0f, 0.5f, 1.25f, 5.0f, 100.0f)

        for (i in 0 until 18) {
            val rayA = SunRays.ray(i)
            val rayB = SunRays[i]
            assertEquals(rayA.angle, rayB.angle, delta)
            assertEquals(rayA.lengthFactor, rayB.lengthFactor, delta)
            assertEquals(rayA.length, rayB.length, delta)
            assertEquals(rayA.brightness, rayB.brightness, delta)

            // Test across different sunPhase frames
            for (phase in phases) {
                val frameRay = SunRays.ray(i, phase)
                assertEquals("Angle must be identical across frames for ray $i", rayA.angle, frameRay.angle, delta)
                assertEquals("Length factor must be identical across frames for ray $i", rayA.lengthFactor, frameRay.lengthFactor, delta)
                assertEquals("Length must be identical across frames for ray $i", rayA.length, frameRay.length, delta)
                assertEquals("Brightness must be identical across frames for ray $i", rayA.brightness, frameRay.brightness, delta)

                val instance = SunRays(phase)
                val instRay = instance[i]
                assertEquals(rayA.angle, instRay.angle, delta)
                assertEquals(rayA.lengthFactor, instRay.lengthFactor, delta)
                assertEquals(rayA.brightness, instRay.brightness, delta)
            }
        }
    }

    @Test
    fun rayFormulasMatchMock() {
        for (i in 0 until 18) {
            val r1 = SunRays.rayRandom(i, 1)
            val r2 = SunRays.rayRandom(i, 2)
            val r3 = SunRays.rayRandom(i, 3)

            assertTrue("r1 in [0, 1)", r1 in 0f..1f)
            assertTrue("r2 in [0, 1)", r2 in 0f..1f)
            assertTrue("r3 in [0, 1)", r3 in 0f..1f)

            val expectedAngle = (i.toFloat() / 18f) * WeatherSim.TAU + (r1 - 0.5f) * 0.12f
            val expectedLen = 0.5f + 0.5f * r2
            val expectedBright = 0.55f + 0.45f * r3

            val ray = SunRays.rays[i]
            assertEquals(i, ray.index)
            assertEquals("Ray $i angle formula", expectedAngle, ray.angle, delta)
            assertEquals("Ray $i lengthFactor formula", expectedLen, ray.lengthFactor, delta)
            assertEquals("Ray $i brightness formula", expectedBright, ray.brightness, delta)
        }
    }

    @Test
    fun passesAndTurnRateMatchMock() {
        assertEquals("Turn rate must be 0.08 rad/s", 0.08f, SunRays.TURN_RATE, delta)
        assertEquals("Turn rate matches WeatherSim.SUN_TURN", WeatherSim.SUN_TURN, SunRays.TURN_RATE, delta)

        val passes = SunRays.PASSES
        assertEquals(2, passes.size)

        // Pass 0: halo half-angle 0.11 rad alpha 0.35
        assertEquals(0.11f, passes[0].halfAngle, delta)
        assertEquals(0.35f, passes[0].alpha, delta)

        // Pass 1: core 0.04 rad alpha 0.8
        assertEquals(0.04f, passes[1].halfAngle, delta)
        assertEquals(0.8f, passes[1].alpha, delta)
    }
}
