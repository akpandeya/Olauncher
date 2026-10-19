package app.olauncher.helper

import android.view.Gravity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HandSideTest {

    @Test
    fun `swap with left alignment flips side and mirrors alignment to right`() {
        val fromRightIconsLeftText = HandSide.swap(shortcutSideStart = false, alignment = Gravity.START)
        assertTrue(fromRightIconsLeftText.shortcutSideStart)
        assertEquals(Gravity.END, fromRightIconsLeftText.alignment)

        val fromLeftIconsLeftText = HandSide.swap(shortcutSideStart = true, alignment = Gravity.START)
        assertFalse(fromLeftIconsLeftText.shortcutSideStart)
        assertEquals(Gravity.END, fromLeftIconsLeftText.alignment)

        // Gravity.LEFT fallback
        val fromGravityLeft = HandSide.swap(shortcutSideStart = false, alignment = Gravity.LEFT)
        assertTrue(fromGravityLeft.shortcutSideStart)
        assertEquals(Gravity.END, fromGravityLeft.alignment)
    }

    @Test
    fun `swap with right alignment flips side and mirrors alignment to left`() {
        val fromLeftIconsRightText = HandSide.swap(shortcutSideStart = true, alignment = Gravity.END)
        assertFalse(fromLeftIconsRightText.shortcutSideStart)
        assertEquals(Gravity.START, fromLeftIconsRightText.alignment)

        val fromRightIconsRightText = HandSide.swap(shortcutSideStart = false, alignment = Gravity.END)
        assertTrue(fromRightIconsRightText.shortcutSideStart)
        assertEquals(Gravity.START, fromRightIconsRightText.alignment)

        // Gravity.RIGHT fallback
        val fromGravityRight = HandSide.swap(shortcutSideStart = true, alignment = Gravity.RIGHT)
        assertFalse(fromGravityRight.shortcutSideStart)
        assertEquals(Gravity.START, fromGravityRight.alignment)
    }

    @Test
    fun `swap with centre alignment flips side and keeps centre alignment unchanged`() {
        val fromRightIconsCenterText = HandSide.swap(shortcutSideStart = false, alignment = Gravity.CENTER)
        assertTrue(fromRightIconsCenterText.shortcutSideStart)
        assertEquals(Gravity.CENTER, fromRightIconsCenterText.alignment)

        val fromLeftIconsCenterText = HandSide.swap(shortcutSideStart = true, alignment = Gravity.CENTER)
        assertFalse(fromLeftIconsCenterText.shortcutSideStart)
        assertEquals(Gravity.CENTER, fromLeftIconsCenterText.alignment)

        // Gravity.CENTER_HORIZONTAL
        val fromCenterHorizontal = HandSide.swap(shortcutSideStart = false, alignment = Gravity.CENTER_HORIZONTAL)
        assertTrue(fromCenterHorizontal.shortcutSideStart)
        assertEquals(Gravity.CENTER_HORIZONTAL, fromCenterHorizontal.alignment)
    }

    @Test
    fun `double swap returns to original layout for left right and centre`() {
        val originalConfigs = listOf(
            HandLayout(shortcutSideStart = false, alignment = Gravity.START),
            HandLayout(shortcutSideStart = true, alignment = Gravity.START),
            HandLayout(shortcutSideStart = false, alignment = Gravity.END),
            HandLayout(shortcutSideStart = true, alignment = Gravity.END),
            HandLayout(shortcutSideStart = false, alignment = Gravity.CENTER),
            HandLayout(shortcutSideStart = true, alignment = Gravity.CENTER),
        )

        for (original in originalConfigs) {
            val once = HandSide.swap(original.shortcutSideStart, original.alignment)
            val twice = HandSide.swap(once.shortcutSideStart, once.alignment)
            assertEquals("Expected round-trip return for $original", original, twice)
        }
    }

    @Test
    fun `HandLayout named properties and destructuring work as expected`() {
        val layout = HandSide.swap(shortcutSideStart = false, alignment = Gravity.START)
        assertEquals(true, layout.shortcutSideStart)
        assertEquals(Gravity.END, layout.alignment)

        val (side, alignment) = layout
        assertEquals(true, side)
        assertEquals(Gravity.END, alignment)
    }
}
