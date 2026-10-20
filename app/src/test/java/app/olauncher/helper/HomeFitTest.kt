package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeFitTest {
    @Test
    fun allFitWhenThereIsRoom() {
        assertEquals(8, HomeFit.visibleCount(800f, 50f, 200f, 8))
    }

    @Test
    fun onlyFittingRowsShow() {
        // room = 700 - 230 = 470 -> 470 / 67 = 7.01 -> 7
        assertEquals(7, HomeFit.visibleCount(700f, 67f, 230f, 8))
        // room = 500 - 260 = 240 -> 240 / 70 = 3.4 -> 3
        assertEquals(3, HomeFit.visibleCount(500f, 70f, 260f, 8))
    }

    @Test
    fun neverMoreThanWanted() {
        assertEquals(2, HomeFit.visibleCount(2000f, 40f, 100f, 2))
    }

    @Test
    fun exactFitCounts() {
        assertEquals(4, HomeFit.visibleCount(400f, 60f, 160f, 8))
    }

    @Test
    fun nothingFitsWhenReservedTakesAll() {
        assertEquals(0, HomeFit.visibleCount(300f, 60f, 300f, 8))
        assertEquals(0, HomeFit.visibleCount(300f, 60f, 500f, 8))
        assertEquals(0, HomeFit.visibleCount(300f, 90f, 250f, 8))
    }

    @Test
    fun badInputGivesZero() {
        assertEquals(0, HomeFit.visibleCount(800f, 0f, 100f, 8))
        assertEquals(0, HomeFit.visibleCount(800f, -5f, 100f, 8))
        assertEquals(0, HomeFit.visibleCount(800f, 50f, 100f, 0))
    }
}
