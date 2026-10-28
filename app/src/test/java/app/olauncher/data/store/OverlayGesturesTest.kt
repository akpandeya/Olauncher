package app.olauncher.data.store

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayGesturesTest {

    @Test
    fun `fast vertical downward drag closes`() {
        // A quarter of the 1000-px view is 250 px: this drag covers 500 in 200 ms,
        // straight down (dy 500 vs dx 30).
        assertTrue(
            OverlayGestures.isCloseSwipe(
                viewHeight = 1000,
                downX = 500f, downY = 100f,
                upX = 530f, upY = 600f,
                elapsedMillis = 200,
            )
        )
    }

    @Test
    fun `circling stroke does not close`() {
        // Short, slow, direction meandering: the picking gesture, not a close flick.
        assertFalse(
            OverlayGestures.isCloseSwipe(
                viewHeight = 1000,
                downX = 500f, downY = 100f,
                upX = 540f, upY = 220f,
                elapsedMillis = 900,
            )
        )
    }

    @Test
    fun `fast sideways drag does not close`() {
        // 600 px across but only 110 down: dy is far below a quarter of the height.
        assertFalse(
            OverlayGestures.isCloseSwipe(
                viewHeight = 1000,
                downX = 100f, downY = 500f,
                upX = 700f, upY = 610f,
                elapsedMillis = 150,
            )
        )
    }

    @Test
    fun `slow downward drag does not close`() {
        // Long enough to pass the quarter height, but past the flick duration.
        assertFalse(
            OverlayGestures.isCloseSwipe(
                viewHeight = 1000,
                downX = 500f, downY = 100f,
                upX = 520f, upY = 700f,
                elapsedMillis = 401,
            )
        )
    }
}
