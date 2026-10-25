package app.olauncher.ui.today.cards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DayBarsGeometryTest {

    @Test
    fun barHeightScalesWithMinutes() {
        assertEquals(50f, DayBarsGeometry.barHeight(100f, 30, 60, 1f, 3f), 0.001f)
        assertEquals(25f, DayBarsGeometry.barHeight(100f, 30, 60, 0.5f, 3f), 0.001f)
    }

    @Test
    fun smallAndZeroDaysShareTheSameMinimumHeight() {
        assertEquals(3f, DayBarsGeometry.barHeight(100f, 0, 600, 1f, 3f), 0.001f)
        assertEquals(3f, DayBarsGeometry.barHeight(100f, 1, 600, 1f, 3f), 0.001f)
        assertEquals(3f, DayBarsGeometry.barHeight(100f, 5, 600, 0f, 3f), 0.001f)
    }

    @Test
    fun barNeverTallerThanTheChart() {
        assertEquals(100f, DayBarsGeometry.barHeight(100f, 900, 600, 1f, 3f), 0.001f)
        assertEquals(100f, DayBarsGeometry.barHeight(100f, 10, 0, 1f, 3f), 0.001f)
    }

    @Test
    fun shortBarsAreNotRoundedIntoPills() {
        assertEquals(1.5f, DayBarsGeometry.cornerRadius(3f, 8f), 0.001f)
        assertEquals(8f, DayBarsGeometry.cornerRadius(40f, 8f), 0.001f)
    }

    @Test
    fun dayBarsViewColoursBarsTheSameForEveryDay() {
        val source = listOf(
            File("src/main/java/app/olauncher/ui/today/cards/DayBarsView.kt"),
            File("app/src/main/java/app/olauncher/ui/today/cards/DayBarsView.kt"),
        ).first { it.isFile }.readText()
        assertFalse("bar colour must not depend on today", source.contains("barPaint.color = if"))
        assertTrue("today is marked by its label", source.contains("labelPaint.color = if (bar.isToday)"))
        assertTrue(source.contains("DayBarsGeometry.barHeight("))
    }
}
