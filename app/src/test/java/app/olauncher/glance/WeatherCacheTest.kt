package app.olauncher.glance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class WeatherCacheTest {

    private fun loadResource(name: String): String {
        val stream = javaClass.classLoader?.getResourceAsStream(name)
            ?: javaClass.getResourceAsStream("/$name")
            ?: File("app/src/test/resources/$name").takeIf { it.exists() }?.inputStream()
            ?: File("src/test/resources/$name").takeIf { it.exists() }?.inputStream()
            ?: error("Resource $name not found")
        return stream.bufferedReader().use { it.readText() }
    }

    @Test
    fun `write and read round trip saves forecast and fetchedAt`() {
        val tempDir = Files.createTempDirectory("weather_cache_test").toFile()
        try {
            val cache = WeatherCache(tempDir)
            val json = loadResource("openmeteo_forecast.json")
            val timestamp = 1716040800000L

            assertNull(cache.read())

            cache.write(json, timestamp)

            val weatherFile = File(tempDir, "weather.json")
            val tempFile = File(tempDir, "weather.json.tmp")
            assertTrue(weatherFile.exists())
            assertFalse(tempFile.exists())

            val result = cache.read()
            assertNotNull(result)
            val (forecast, fetchedAt) = result!!

            assertEquals(timestamp, fetchedAt)
            assertEquals(18.2, forecast.nowTemp, 0.001)
            assertEquals(1, forecast.nowCode)
            assertTrue(forecast.isDay)
            assertEquals(21.4, forecast.todayMax, 0.001)
            assertEquals(10.1, forecast.todayMin, 0.001)
            assertEquals(12, forecast.hours.size)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `read returns null on invalid or corrupted file`() {
        val tempDir = Files.createTempDirectory("weather_cache_corrupt_test").toFile()
        try {
            val cache = WeatherCache(tempDir)
            val weatherFile = File(tempDir, "weather.json")
            weatherFile.writeText("corrupted-not-json")

            assertNull(cache.read())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `clear removes cache file`() {
        val tempDir = Files.createTempDirectory("weather_cache_clear_test").toFile()
        try {
            val cache = WeatherCache(tempDir)
            val json = loadResource("openmeteo_forecast.json")
            cache.write(json)
            assertTrue(File(tempDir, "weather.json").exists())

            cache.clear()
            assertFalse(File(tempDir, "weather.json").exists())
            assertNull(cache.read())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
