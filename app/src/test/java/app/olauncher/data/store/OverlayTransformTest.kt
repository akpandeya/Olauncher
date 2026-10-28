package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class OverlayTransformTest {

    // Distinguishing values on purpose: different x/y accessibility scales (1.01 vs
    // 0.99) and a view whose aspect does not match the frame's, so the letterbox offset
    // is non-zero and swapped axes or a dropped offset cannot pass.
    private val transform = OverlayTransform(
        viewWidth = 1000,
        viewHeight = 500,
        frameWidth = 500,
        frameHeight = 1000,
        scaleX = 1.01f,
        scaleY = 0.99f,
    )

    @Test
    fun `frame is letterboxed not cropped`() {
        // fitScale = min(1000/500, 500/1000) = 0.5: the 500x1000 frame draws 250x250,
        // centred — horizontal bars, so drawLeft is 375 and drawTop is 0.
        assertEquals(0.5f, transform.fitScale, 1e-6f)
        assertEquals(375f, transform.drawLeft, 1e-3f)
        assertEquals(0f, transform.drawTop, 1e-6f)
    }

    @Test
    fun `toView places a box through scale and letterbox offset`() {
        // Full-frame box: the left edge sits at the left letterbox edge, the right edge
        // is 375 + 1000 * (0.5/1.01) ≈ 870 (x scale), the bottom 2000 * (0.5/0.99) ≈ 1010
        // (y scale) — a swapped axis lands at 880/990 instead.
        val view = transform.toView(ScreenBox(0, 0, 1000, 2000))
        assertEquals(375, view.left)
        assertEquals(0, view.top)
        assertTrue(view.right in 869..871)
        assertTrue(view.bottom in 1009..1011)
    }

    @Test
    fun `toViewF is toView before rounding`() {
        // The drawing path must go through the same mapping as the placement path —
        // a swapped axis in toViewF would land at 880/990, not 870/1010.
        val box = ScreenBox(0, 0, 1000, 2000)
        val f = transform.toViewF(box)
        val view = transform.toView(box)
        assertEquals(view.left.toFloat(), f[0], 0.5f)
        assertEquals(view.top.toFloat(), f[1], 0.5f)
        assertEquals(view.right.toFloat(), f[2], 0.5f)
        assertEquals(view.bottom.toFloat(), f[3], 0.5f)
    }

    @Test
    fun `toScreen maps the letterbox edge outside the frame`() {
        // One pixel left of the frame's left edge (drawLeft = 375) is outside the
        // accessibility screen; a dropped letterbox offset would call it x ≈ 756.
        assertTrue(transform.toScreen(374f, 250f).x < 0)
        assertTrue(transform.toScreen(376f, 250f).x >= 0)
        // The frame centre maps back to the screen centre — x through the 1.01 scale,
        // y through the 0.99 scale, so the two coordinates take different routes.
        val center = transform.toScreen(500f, 250f)
        assertTrue("x=${center.x}", center.x in 251..255) // (500-375) * 1.01 / 0.5 ≈ 252.5
        assertTrue("y=${center.y}", center.y in 493..497) // (250-0) * 0.99 / 0.5 ≈ 495
    }

    @Test
    fun `box round-trips through the view unchanged`() {
        // A word box near the middle of the screen survives screen → view → screen with
        // at most 1 px of rounding slack per edge.
        val box = ScreenBox(200, 300, 420, 380)
        val view = transform.toView(box)
        val backStart = transform.toScreen(view.left.toFloat(), view.top.toFloat())
        val backEnd = transform.toScreen(view.right.toFloat(), view.bottom.toFloat())
        assertTrue(abs(backStart.x - box.left) <= 1)
        assertTrue(abs(backStart.y - box.top) <= 1)
        assertTrue(abs(backEnd.x - box.right) <= 1)
        assertTrue(abs(backEnd.y - box.bottom) <= 1)
    }

    @Test
    fun `slop scales through the fit`() {
        // 20 view px are ~40 screen px at fitScale 0.5 — a dropped scale returns 20.
        assertEquals(40, transform.slopToScreen(20))
        // A zero slop must not collapse to zero screen px — the max(1, …) clamp.
        assertEquals(1, transform.slopToScreen(0))
    }
}
