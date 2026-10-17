package app.olauncher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ConstantsTest {
    @Test
    fun `wallpaper worker name is preserved for legacy cancellation`() {
        assertEquals("WALLPAPER_WORKER_NAME", Constants.WALLPAPER_WORKER_NAME)
    }
}
