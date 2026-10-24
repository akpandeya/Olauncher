package app.olauncher.wellbeing

import app.olauncher.ui.today.cards.WellbeingCardLogic
import org.junit.Assert.assertEquals
import org.junit.Test

class AllowanceMeterTest {

    @Test
    fun `meter fill and colour band at 0 percent`() {
        val meter = AllowanceMeter.calculate(0)
        assertEquals(0f, meter.fill, 0.001f)
        assertEquals(AllowanceBand.NORMAL, meter.band)
        assertEquals(0, meter.percent)
        assertEquals(0f, meter.ratio, 0.001f)

        assertEquals(0f, AllowanceMeter.meterFill(0), 0.001f)
        assertEquals(AllowanceBand.NORMAL, AllowanceMeter.meterBand(0))
    }

    @Test
    fun `meter fill and colour band at 79 percent`() {
        val meter = AllowanceMeter.calculate(79)
        assertEquals(0.79f, meter.fill, 0.001f)
        assertEquals(AllowanceBand.NORMAL, meter.band)
        assertEquals(79, meter.percent)
        assertEquals(0.79f, meter.ratio, 0.001f)

        assertEquals(0.79f, AllowanceMeter.meterFill(79), 0.001f)
        assertEquals(AllowanceBand.NORMAL, AllowanceMeter.meterBand(79))
    }

    @Test
    fun `meter fill and colour band at 80 percent threshold`() {
        val meter = AllowanceMeter.calculate(80)
        assertEquals(0.80f, meter.fill, 0.001f)
        assertEquals(AllowanceBand.WARNING, meter.band)
        assertEquals(80, meter.percent)
        assertEquals(0.80f, meter.ratio, 0.001f)

        assertEquals(0.80f, AllowanceMeter.meterFill(80), 0.001f)
        assertEquals(AllowanceBand.WARNING, AllowanceMeter.meterBand(80))
    }

    @Test
    fun `meter fill and colour band at 100 percent threshold`() {
        val meter = AllowanceMeter.calculate(100)
        assertEquals(1.0f, meter.fill, 0.001f)
        assertEquals(AllowanceBand.OVER, meter.band)
        assertEquals(100, meter.percent)
        assertEquals(1.0f, meter.ratio, 0.001f)

        assertEquals(1.0f, AllowanceMeter.meterFill(100), 0.001f)
        assertEquals(AllowanceBand.OVER, AllowanceMeter.meterBand(100))
    }

    @Test
    fun `meter fill and colour band at 137 percent over allowance`() {
        val meter = AllowanceMeter.calculate(137)
        // Meter fill caps at 1.0 (100% full bar)
        assertEquals(1.0f, meter.fill, 0.001f)
        assertEquals(AllowanceBand.OVER, meter.band)
        assertEquals(137, meter.percent)
        // Uncapped ratio preserves 1.37
        assertEquals(1.37f, meter.ratio, 0.001f)

        assertEquals(1.0f, AllowanceMeter.meterFill(137), 0.001f)
        assertEquals(AllowanceBand.OVER, AllowanceMeter.meterBand(137))
    }

    @Test
    fun `calculate with used minutes and allowance min for 0, 79, 80, 100, 137 percent`() {
        val allowance = 100

        val m0 = AllowanceMeter.calculate(usedMinutes = 0, allowanceMin = allowance)
        assertEquals(0f, m0.fill, 0.001f)
        assertEquals(AllowanceBand.NORMAL, m0.band)

        val m79 = AllowanceMeter.calculate(usedMinutes = 79, allowanceMin = allowance)
        assertEquals(0.79f, m79.fill, 0.001f)
        assertEquals(AllowanceBand.NORMAL, m79.band)

        val m80 = AllowanceMeter.calculate(usedMinutes = 80, allowanceMin = allowance)
        assertEquals(0.80f, m80.fill, 0.001f)
        assertEquals(AllowanceBand.WARNING, m80.band)

        val m100 = AllowanceMeter.calculate(usedMinutes = 100, allowanceMin = allowance)
        assertEquals(1.0f, m100.fill, 0.001f)
        assertEquals(AllowanceBand.OVER, m100.band)

        val m137 = AllowanceMeter.calculate(usedMinutes = 137, allowanceMin = allowance)
        assertEquals(1.0f, m137.fill, 0.001f)
        assertEquals(AllowanceBand.OVER, m137.band)
        assertEquals(1.37f, m137.ratio, 0.001f)
    }

    @Test
    fun `WellbeingBento delegations match AllowanceMeter`() {
        assertEquals(0f, WellbeingBento.meterFill(0), 0.001f)
        assertEquals(0.79f, WellbeingBento.meterFill(79), 0.001f)
        assertEquals(0.80f, WellbeingBento.meterFill(80), 0.001f)
        assertEquals(1.0f, WellbeingBento.meterFill(100), 0.001f)
        assertEquals(1.0f, WellbeingBento.meterFill(137), 0.001f)

        assertEquals(AllowanceBand.NORMAL, WellbeingBento.meterBand(0))
        assertEquals(AllowanceBand.NORMAL, WellbeingBento.meterBand(79))
        assertEquals(AllowanceBand.WARNING, WellbeingBento.meterBand(80))
        assertEquals(AllowanceBand.OVER, WellbeingBento.meterBand(100))
        assertEquals(AllowanceBand.OVER, WellbeingBento.meterBand(137))
    }

    @Test
    fun `PausePolicy delegations match AllowanceMeter`() {
        assertEquals(0.79f, PausePolicy.meterFill(79), 0.001f)
        assertEquals(0.80f, PausePolicy.meterFill(80), 0.001f)
        assertEquals(AllowanceBand.NORMAL, PausePolicy.meterBand(79))
        assertEquals(AllowanceBand.WARNING, PausePolicy.meterBand(80))
        assertEquals(AllowanceBand.OVER, PausePolicy.meterBand(100))
    }

    @Test
    fun `WellbeingCardLogic delegations match AllowanceMeter`() {
        assertEquals(0.79f, WellbeingCardLogic.meterFill(79), 0.001f)
        assertEquals(0.80f, WellbeingCardLogic.meterFill(80), 0.001f)
        assertEquals(AllowanceBand.NORMAL, WellbeingCardLogic.meterBand(79))
        assertEquals(AllowanceBand.WARNING, WellbeingCardLogic.meterBand(80))
        assertEquals(AllowanceBand.OVER, WellbeingCardLogic.meterBand(100))
    }

    @Test
    fun `edge cases with zero or negative allowance or used time`() {
        val zeroAllowance = AllowanceMeter.calculate(usedMinutes = 50, allowanceMin = 0)
        assertEquals(0f, zeroAllowance.fill, 0.001f)
        assertEquals(AllowanceBand.NORMAL, zeroAllowance.band)

        val negAllowance = AllowanceMeter.calculate(usedMinutes = 50, allowanceMin = -10)
        assertEquals(0f, negAllowance.fill, 0.001f)
        assertEquals(AllowanceBand.NORMAL, negAllowance.band)

        val negUsed = AllowanceMeter.calculate(usedMinutes = -5, allowanceMin = 60)
        assertEquals(0f, negUsed.fill, 0.001f)
        assertEquals(AllowanceBand.NORMAL, negUsed.band)
    }

    @Test
    fun `aliases for band enum`() {
        assertEquals(AllowanceBand.NORMAL, AllowanceBand.SAFE)
        assertEquals(AllowanceBand.WARNING, AllowanceBand.WARN)
        assertEquals(AllowanceBand.OVER, AllowanceBand.DANGER)
    }
}
