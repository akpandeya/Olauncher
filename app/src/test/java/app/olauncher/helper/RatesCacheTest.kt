package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class RatesCacheTest {
    private val good = """{"amount":1.0,"base":"EUR","date":"2026-10-18","rates":{"USD":1.084,"INR":90.5}}"""

    @Test fun roundTrip() {
        val dir = Files.createTempDirectory("rates_cache_test").toFile()
        try {
            val cache = RatesCache(dir)
            assertNull(cache.read())
            assertTrue(cache.write(good, 1234L))
            val r = cache.read()!!
            assertEquals("2026-10-18", r.date)
            assertEquals(1234L, r.fetchedAt)
            assertEquals(90.5, r.rates["INR"]!!, 0.0)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun malformedJsonNeverReplacesGoodRates() {
        val dir = Files.createTempDirectory("rates_cache_test").toFile()
        try {
            val cache = RatesCache(dir)
            assertTrue(cache.write(good, 1L))
            assertFalse(cache.write("""{"base":"EUR","date":"2026-10-19","rates":{}}""", 2L))
            assertFalse(cache.write("garbage", 3L))
            val r = cache.read()!!
            assertEquals("2026-10-18", r.date)
            assertEquals(1L, r.fetchedAt)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun malformedFirstWriteLeavesNoFile() {
        val dir = Files.createTempDirectory("rates_cache_test").toFile()
        try {
            val cache = RatesCache(dir)
            assertFalse(cache.write("{}", 1L))
            assertNull(cache.read())
        } finally {
            dir.deleteRecursively()
        }
    }
}
