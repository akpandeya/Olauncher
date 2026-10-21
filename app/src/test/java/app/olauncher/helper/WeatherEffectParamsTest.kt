package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherEffectParamsTest {
    private fun rain(i: Intensity, freezing: Boolean = false) =
        EffectParams.of(EffectSpec(WeatherEffect.RAIN, i, freezing = freezing))

    @Test
    fun heavyBeatsModerateBeatsLight() {
        val l = rain(Intensity.LIGHT)
        val m = rain(Intensity.MODERATE)
        val h = rain(Intensity.HEAVY)
        assertTrue(h.density > m.density && m.density > l.density)
        assertTrue(h.glassDrops > m.glassDrops && m.glassDrops > l.glassDrops)
        assertTrue(h.slideSpeed > m.slideSpeed && m.slideSpeed > l.slideSpeed)
        assertTrue(h.fallSpeed > m.fallSpeed && m.fallSpeed > l.fallSpeed)
    }

    @Test
    fun valuesStayInClampRanges() {
        for (effect in WeatherEffect.values()) for (i in Intensity.values()) for (f in listOf(false, true)) for (hail in listOf(false, true)) {
            val p = EffectParams.of(EffectSpec(effect, i, f, hail))
            val tag = "$effect $i $f $hail"
            assertTrue(tag, p.density in 0f..1f)
            assertTrue(tag, p.fallSpeed in EffectParams.MIN_FALL_SPEED..EffectParams.MAX_FALL_SPEED)
            assertTrue(tag, p.glassDrops in 0f..EffectParams.MAX_GLASS_DROPS)
            assertTrue(tag, p.slideSpeed in 0f..EffectParams.MAX_SLIDE_SPEED)
            assertTrue(tag, p.frost in 0f..1f)
            assertTrue(tag, p.hail in 0f..1f)
            assertTrue(tag, p.bolt in 0f..1f)
        }
    }

    @Test
    fun nonRainEffectsHaveNoGlassDrops() {
        for (effect in WeatherEffect.values().filter { it != WeatherEffect.RAIN }) for (i in Intensity.values()) {
            val p = EffectParams.of(EffectSpec(effect, i))
            assertEquals("$effect $i", 0f, p.glassDrops, 0f)
            assertEquals("$effect $i", 0f, p.slideSpeed, 0f)
        }
    }

    @Test
    fun freezingRainAddsFrostAndSlowsSlide() {
        val plain = rain(Intensity.MODERATE)
        val ice = rain(Intensity.MODERATE, freezing = true)
        assertEquals(0f, plain.frost, 0f)
        assertTrue(ice.frost > 0.5f)
        assertTrue(ice.slideSpeed < plain.slideSpeed)
        assertEquals(plain.glassDrops, ice.glassDrops, 0f)
    }

    @Test
    fun hailAndBoltOnlyForStorms() {
        val hailStorm = EffectParams.of(WeatherEffects.specFor(99))
        val storm = EffectParams.of(WeatherEffects.specFor(95))
        assertEquals(1f, hailStorm.hail, 0f)
        assertEquals(0f, storm.hail, 0f)
        assertEquals(1f, storm.bolt, 0f)
        assertEquals(0f, EffectParams.of(WeatherEffects.specFor(65)).bolt, 0f)
        assertEquals(0f, EffectParams.of(WeatherEffects.specFor(65)).hail, 0f)
    }

    @Test
    fun codesWireThroughToParams() {
        val light = EffectParams.of(WeatherEffects.specFor(61))
        val heavy = EffectParams.of(WeatherEffects.specFor(82))
        assertEquals(6f, light.glassDrops, 0f)
        assertEquals(26f, heavy.glassDrops, 0f)
        assertEquals(0.4f * 0.3f, EffectParams.of(WeatherEffects.specFor(56)).slideSpeed, 1e-6f)
    }
}
