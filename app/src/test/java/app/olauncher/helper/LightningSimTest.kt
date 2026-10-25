package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LightningSimTest {

    @Test
    fun noStrikesOnAnyLookExceptStorm() {
        val nonStormLooks = WeatherLook.values().filter { it != WeatherLook.STORM }
        for (look in nonStormLooks) {
            val sim = WeatherSim(
                look = look,
                width = 390f,
                height = 844f,
                seed = 1234L,
            )
            for (step in 0 until 1800) { // 60 seconds
                sim.update(WeatherSim.STEP)
                assertEquals("Flash must stay 0 on $look", 0f, sim.flash, 0f)
                assertEquals("flashAge must stay -1 on $look", -1f, sim.flashAge, 0f)
                assertNull("boltPath must be null on $look", sim.boltPath)
            }
        }
    }

    @Test
    fun flashEndsBy06Seconds() {
        val sim = WeatherSim(
            look = WeatherLook.STORM,
            width = 390f,
            height = 844f,
            seed = 42L,
        )

        var flashStarted = false
        var flashDuration = 0f
        var strikeCount = 0

        // Run for 120 seconds to capture several flashes
        val dt = WeatherSim.STEP
        for (step in 0 until 3600) {
            sim.update(dt)
            if (sim.flashAge >= 0f) {
                if (!flashStarted) {
                    flashStarted = true
                    flashDuration = 0f
                    strikeCount++
                }
                flashDuration += dt
                assertTrue(
                    "Flash duration ($flashDuration s) must not exceed 0.6 s + 1 frame tolerance",
                    flashDuration <= WeatherSim.FLASH_SECONDS + dt + 1e-4f
                )
            } else {
                flashStarted = false
            }
        }
        assertTrue("Must observe multiple strikes in 120 s", strikeCount >= 5)
    }

    @Test
    fun strikeGapsWithinBoundsOverLongRun() {
        val sim = WeatherSim(
            look = WeatherLook.STORM,
            width = 390f,
            height = 844f,
            seed = 98765L,
        )

        val dt = WeatherSim.STEP
        var firstStrikeTime: Float? = null
        val gapsFromPreviousFlashEnd = mutableListOf<Float>()

        var flashActive = false
        var timeSinceLastFlashEnd = 0f

        // Run for 300 s
        val totalSteps = (300f / dt).toInt()
        for (step in 0 until totalSteps) {
            val prevFlashAge = sim.flashAge
            sim.update(dt)

            if (prevFlashAge < 0f && sim.flashAge >= 0f) {
                // Strike just started
                if (firstStrikeTime == null) {
                    firstStrikeTime = sim.time
                } else {
                    gapsFromPreviousFlashEnd.add(timeSinceLastFlashEnd)
                }
                flashActive = true
                timeSinceLastFlashEnd = 0f
            } else if (prevFlashAge >= 0f && sim.flashAge < 0f) {
                // Flash just ended
                flashActive = false
                timeSinceLastFlashEnd = 0f
            } else if (!flashActive) {
                timeSinceLastFlashEnd += dt
            }
        }

        // First strike gap: initial boltTimer is 1.5..5 s
        assertTrue(
            "First strike time ($firstStrikeTime s) must be between 1.5 s and 5.0 s (plus 1 frame)",
            firstStrikeTime != null && firstStrikeTime >= 1.5f - dt && firstStrikeTime <= 5.0f + dt
        )

        assertTrue("Must record multiple strike gaps", gapsFromPreviousFlashEnd.size >= 10)
        for ((idx, gap) in gapsFromPreviousFlashEnd.withIndex()) {
            assertTrue(
                "Strike gap #$idx ($gap s) must be within [4.0, 12.0] s (plus 1 frame)",
                gap >= 4.0f - dt && gap <= 12.0f + dt
            )
        }
    }
}
