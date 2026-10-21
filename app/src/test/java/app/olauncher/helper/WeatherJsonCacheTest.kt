package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherJsonCacheTest {

    @Test
    fun firstReadLoads() {
        var loads = 0
        val cache = WeatherJsonCache { loads++; "a" }
        assertNull(cache.peek())
        assertEquals("a", cache.get(100L))
        assertEquals(1, loads)
        assertEquals("a", cache.peek())
    }

    @Test
    fun sameFetchedAtReusesMemory() {
        var loads = 0
        val cache = WeatherJsonCache { loads++; "v$loads" }
        cache.get(100L)
        assertTrue(cache.isCurrent(100L))
        assertEquals("v1", cache.get(100L))
        assertEquals(1, loads)
    }

    @Test
    fun newFetchedAtReloads() {
        var loads = 0
        val cache = WeatherJsonCache { loads++; "v$loads" }
        cache.get(100L)
        assertFalse(cache.isCurrent(200L))
        assertEquals("v2", cache.get(200L))
        assertEquals(2, loads)
    }

    @Test
    fun missingCacheReturnsNothingAndRetries() {
        var loads = 0
        val cache = WeatherJsonCache<String> { loads++; null }
        assertNull(cache.get(0L))
        assertNull(cache.get(0L))
        assertNull(cache.peek())
        assertEquals(2, loads)
    }

    @Test
    fun failedReloadDropsStaleValue() {
        var data: String? = "a"
        val cache = WeatherJsonCache { data }
        assertEquals("a", cache.get(1L))
        data = null
        assertNull(cache.get(2L))
        assertNull(cache.peek())
    }
}
