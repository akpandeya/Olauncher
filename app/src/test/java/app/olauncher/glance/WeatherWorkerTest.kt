package app.olauncher.glance

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherWorkerTest {

    @Test
    fun `worker unique work names match constants`() {
        assertEquals("weather", WeatherWorker.WORK_NAME_PERIODIC)
        assertEquals("weather_now", WeatherWorker.WORK_NAME_NOW)
    }
}
