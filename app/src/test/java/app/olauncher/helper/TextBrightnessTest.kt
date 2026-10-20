package app.olauncher.helper

import app.olauncher.data.Prefs
import org.junit.Assert.assertEquals
import org.junit.Test

class TextBrightnessTest {
    @Test
    fun opacityStepsDownFromFullToDim() {
        assertEquals(1f, TextBrightness.opacity(Prefs.BRIGHTNESS_FULL), 0f)
        assertEquals(0.9f, TextBrightness.opacity(Prefs.BRIGHTNESS_SOFT), 0f)
        assertEquals(0.75f, TextBrightness.opacity(Prefs.BRIGHTNESS_DIM), 0f)
    }

    @Test
    fun unknownLevelFallsBackToFull() {
        assertEquals(1f, TextBrightness.opacity(99), 0f)
    }
}
