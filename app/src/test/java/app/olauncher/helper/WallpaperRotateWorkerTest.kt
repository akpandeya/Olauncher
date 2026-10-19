package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WallpaperRotateWorkerTest {

    @Test
    fun `worker unique work name matches constant`() {
        assertEquals("photo_wallpaper", WallpaperRotateWorker.WORK_NAME)
    }

    @Test
    fun `schedule periodic intervals match expected minutes or null`() {
        assertNull(RotationInterval.ON_OPEN.periodicRepeatMinutes)
        assertNull(RotationInterval.NEVER.periodicRepeatMinutes)

        assertEquals(15L, RotationInterval.MINUTES_15.periodicRepeatMinutes)
        assertEquals(30L, RotationInterval.MINUTES_30.periodicRepeatMinutes)
        assertEquals(60L, RotationInterval.HOURS_1.periodicRepeatMinutes)
        assertEquals(180L, RotationInterval.HOURS_3.periodicRepeatMinutes)
        assertEquals(360L, RotationInterval.HOURS_6.periodicRepeatMinutes)
        assertEquals(720L, RotationInterval.HOURS_12.periodicRepeatMinutes)
        assertEquals(1440L, RotationInterval.DAILY.periodicRepeatMinutes)
        assertEquals(10080L, RotationInterval.WEEKLY.periodicRepeatMinutes)
    }

    @Test
    fun `default interval provides 24 hour period`() {
        assertEquals(RotationInterval.DAILY, RotationInterval.DEFAULT)
        assertEquals(1440L, RotationInterval.DEFAULT.periodicRepeatMinutes)
    }
}
