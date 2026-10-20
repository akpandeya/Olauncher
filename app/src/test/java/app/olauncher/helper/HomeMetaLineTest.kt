package app.olauncher.helper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeMetaLineTest {

    @Test
    fun shortDateAndWeatherStayOnOneRow() {
        assertFalse(HomeMetaLine.shouldStack(300f, 30f, 150f, weatherShown = true, availableWidthPx = 1000f))
    }

    @Test
    fun longGermanDateStacksWeatherUnder() {
        // "DIENSTAG, 20. OKTOBER" at large font scale.
        assertTrue(HomeMetaLine.shouldStack(820f, 30f, 200f, weatherShown = true, availableWidthPx = 1000f))
    }

    @Test
    fun exactFitStaysOnOneRow() {
        assertFalse(HomeMetaLine.shouldStack(700f, 50f, 250f, weatherShown = true, availableWidthPx = 1000f))
    }

    @Test
    fun noWeatherNeverStacks() {
        assertFalse(HomeMetaLine.shouldStack(5000f, 30f, 0f, weatherShown = false, availableWidthPx = 1000f))
    }

    @Test
    fun unknownWidthNeverStacks() {
        assertFalse(HomeMetaLine.shouldStack(820f, 30f, 200f, weatherShown = true, availableWidthPx = 0f))
    }

    @Test
    fun separatorOnlyWhenBothShownSideBySide() {
        assertTrue(HomeMetaLine.showSeparator(dateShown = true, weatherShown = true, stacked = false))
        assertFalse(HomeMetaLine.showSeparator(dateShown = true, weatherShown = true, stacked = true))
        assertFalse(HomeMetaLine.showSeparator(dateShown = true, weatherShown = false, stacked = false))
        assertFalse(HomeMetaLine.showSeparator(dateShown = false, weatherShown = true, stacked = false))
    }
}
