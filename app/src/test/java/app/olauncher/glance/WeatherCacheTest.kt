package app.olauncher.glance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

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

    @Test
    fun `concurrent writes from separate instances never lose or corrupt the cache`() {
        val tempDir = Files.createTempDirectory("weather_cache_concurrent_test").toFile()
        val pool = Executors.newFixedThreadPool(3)
        try {
            val json = loadResource("openmeteo_forecast.json")
            WeatherCache(tempDir).write(json, 1L)
            val rounds = 300
            val start = CountDownLatch(1)
            val badReads = AtomicInteger(0)
            val writeErrors = AtomicInteger(0)
            repeat(2) { writer ->
                pool.execute {
                    // Each worker builds its own cache instance, as WeatherWorker does.
                    val cache = WeatherCache(tempDir)
                    start.await()
                    repeat(rounds) { i ->
                        try {
                            cache.write(json, 1000L * writer + i)
                        } catch (e: Exception) {
                            writeErrors.incrementAndGet()
                        }
                    }
                }
            }
            pool.execute {
                val reader = WeatherCache(tempDir)
                start.await()
                repeat(rounds) { if (reader.read() == null) badReads.incrementAndGet() }
            }
            start.countDown()
            pool.shutdown()
            assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS))

            assertEquals(0, writeErrors.get())
            assertEquals(0, badReads.get())
            assertNotNull(WeatherCache(tempDir).read())
            assertEquals(listOf("weather.json"), tempDir.list()!!.sorted())
        } finally {
            pool.shutdownNow()
            tempDir.deleteRecursively()
        }
    }
}
