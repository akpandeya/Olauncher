package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentSuggestionsTest {
    private val red = 0xFFB00020.toInt()
    private val blue = 0xFF1A3FA0.toInt()
    private val green = 0xFF2E7D32.toInt()
    private val grey = 0xFF808080.toInt()
    private val black = 0xFF000000.toInt()
    private val white = 0xFFFFFFFF.toInt()

    @Test fun darkThemeSuggestionsReadOnBlack() {
        val out = AccentSuggestions.suggest(listOf(red, blue, green), themeIsDark = true)
        assertEquals(3, out.size)
        out.forEach { assertTrue(ReadableScrim.contrast(it, black) >= ReadableScrim.MIN_CONTRAST) }
    }

    @Test fun lightThemeSuggestionsReadOnWhite() {
        val out = AccentSuggestions.suggest(listOf(0xFFFFE082.toInt(), 0xFF90CAF9.toInt()), themeIsDark = false)
        assertEquals(2, out.size)
        out.forEach { assertTrue(ReadableScrim.contrast(it, white) >= ReadableScrim.MIN_CONTRAST) }
    }

    @Test fun keepsHueWhenToning() {
        val toned = AccentSuggestions.tone(blue, black, lighten = true)
        val before = AccentSuggestions.hsl(blue)[0]
        val after = AccentSuggestions.hsl(toned)[0]
        assertTrue(kotlin.math.abs(before - after) < 4f)
    }

    @Test fun dropsNearDuplicateHuesAndExactRepeats() {
        val almostRed = 0xFFB4101F.toInt()
        val out = AccentSuggestions.suggest(listOf(red, red, almostRed, blue), themeIsDark = true)
        assertEquals(2, out.size)
    }

    @Test fun greyWallpaperStillGivesGreys() {
        val out = AccentSuggestions.suggest(listOf(grey), themeIsDark = true)
        assertEquals(1, out.size)
    }

    @Test fun prefersColourfulOverGrey() {
        val out = AccentSuggestions.suggest(listOf(grey, red), themeIsDark = true)
        assertEquals(1, out.size)
        assertTrue(AccentSuggestions.hsl(out[0])[1] > 0.5f)
    }

    @Test fun capsAtMax() {
        val many = (0 until 12).map { AccentSuggestions.fromHsl(it * 30f, 0.8f, 0.4f) }
        assertEquals(AccentSuggestions.MAX_SUGGESTIONS, AccentSuggestions.suggest(many, true).size)
    }

    @Test fun emptyInputGivesEmpty() {
        assertTrue(AccentSuggestions.suggest(emptyList(), true).isEmpty())
    }

    @Test fun hslRoundTrip() {
        val hsl = AccentSuggestions.hsl(green)
        assertEquals(green, AccentSuggestions.fromHsl(hsl[0], hsl[1], hsl[2]))
    }
}
