package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt
import kotlin.math.sqrt

class RainDropSimTest {

    private val delta = 1e-4f

    @Test
    fun noTrailStateOnDrops() {
        val fields = RainDrop::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse("RainDrop must not have any trail fields", fields.any { it.contains("trail") })
    }

    @Test
    fun sheenSpotsOnlyForMovingDropsAndOnePer6Px() {
        val sim = WeatherSim(
            look = WeatherLook.LIGHT_RAIN,
            width = 390f,
            height = 844f,
            seed = 42L,
        )

        val drop = sim.makeDrop(100f, 100f).copy(stall = 2f, vy = 0f, gx = 100f, gy = 100f)
        sim.drops.add(drop)

        // Stationary drop should not produce glints/sheen spots
        sim.leaveGlint(drop)
        assertEquals(0, sim.sheenSpots.size)

        // Moving less than 6 px travel: no sheen spot
        drop.vy = 20f
        drop.y = 105f
        sim.leaveGlint(drop)
        assertEquals(0, sim.sheenSpots.size)

        // Moving to 106 px (6 px total travel from gx, gy): exactly 1 sheen spot
        drop.y = 106f
        sim.leaveGlint(drop)
        assertEquals(1, sim.sheenSpots.size)
        assertEquals(100f, sim.sheenSpots[0].x, delta)
        assertEquals(100f, sim.sheenSpots[0].y, delta)
        assertEquals(100f, drop.gx, delta)
        assertEquals(106f, drop.gy, delta)

        // Another 6 px: another sheen spot
        drop.y = 112f
        sim.leaveGlint(drop)
        assertEquals(2, sim.sheenSpots.size)
    }

    @Test
    fun sheenSpotsAllGoneWithin02SecondsAfterDropStops() {
        val sim = WeatherSim(
            look = WeatherLook.LIGHT_RAIN,
            width = 390f,
            height = 844f,
            seed = 42L,
        )

        val drop = sim.makeDrop(100f, 100f).copy(stall = 0f, vy = 20f, gx = 100f, gy = 100f)
        drop.y = 110f
        sim.leaveGlint(drop)
        assertEquals(1, sim.sheenSpots.size)

        // Drop stops
        drop.vy = 0f
        drop.stall = 1f

        // Advance by 0.1s: sheen spot still present
        sim.fadeGlints(0.1f)
        assertEquals(1, sim.sheenSpots.size)

        // Advance by another 0.11s (total 0.21s > 0.2s): sheen spot is gone
        sim.fadeGlints(0.11f)
        assertEquals(0, sim.sheenSpots.size)
    }

    @Test
    fun sheenSpotsNeverExceed240() {
        val sim = WeatherSim(
            look = WeatherLook.STORM,
            width = 390f,
            height = 844f,
            seed = 12345L,
        )

        // Run for 30 seconds (900 frames at 1/30 s)
        for (step in 0 until 900) {
            sim.update(WeatherSim.STEP)
            assertTrue(
                "Sheen spots (${sim.sheenSpots.size}) must never exceed ${WeatherSim.MAX_GLINTS}",
                sim.sheenSpots.size <= WeatherSim.MAX_GLINTS
            )
        }
    }

    @Test
    fun mergeKeepsAreaAndCapsRadiusAt15() {
        val sim = WeatherSim(
            look = WeatherLook.LIGHT_RAIN,
            width = 390f,
            height = 844f,
            seed = 999L,
        )

        val d1 = sim.makeDrop(50f, 50f).copy(r = 3f, stall = 2f)
        val d2 = sim.makeDrop(50f, 50f).copy(r = 4f, stall = 3f)

        val merged = sim.mergeDrops(d1, d2)
        // Area conserved: sqrt(3^2 + 4^2) = 5
        assertEquals(5f, merged.r, delta)
        assertEquals(0f, merged.stall, delta)

        // Radii that would exceed 15
        val d3 = sim.makeDrop(100f, 100f).copy(r = 12f)
        val d4 = sim.makeDrop(100f, 100f).copy(r = 12f)
        val mergedBig = sim.mergeDrops(d3, d4)
        // sqrt(144 + 144) = 16.97 > 15 -> capped at 15
        assertEquals(WeatherSim.MAX_RADIUS, mergedBig.r, delta)
        assertEquals(0f, mergedBig.stall, delta)
    }

    @Test
    fun dropCountNeverExceedsRoundGlassDropsTimes2Point5() {
        listOf(WeatherLook.LIGHT_RAIN, WeatherLook.HEAVY_RAIN, WeatherLook.STORM).forEach { look ->
            val maxAllowed = (look.params.glassDrops * WeatherSim.DROPS_PER_GLASS_UNIT).roundToInt()
            val sim = WeatherSim(
                look = look,
                width = 390f,
                height = 844f,
                seed = 777L,
            )

            // Run for 60 seconds of simulation with home impacts
            for (step in 0 until 1800) {
                sim.updateHome(WeatherSim.STEP)
                assertTrue(
                    "Drop count ${sim.drops.size} on $look must not exceed max $maxAllowed",
                    sim.drops.size <= maxAllowed
                )
            }
        }
    }
}
