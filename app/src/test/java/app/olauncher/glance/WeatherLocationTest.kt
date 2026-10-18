package app.olauncher.glance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherLocationTest {

    @Test
    fun `round rounds positive coordinates to 2 decimal places`() {
        val (lat, lon) = WeatherLocation.round(52.524, 13.416)
        assertEquals(52.52, lat, 0.0001)
        assertEquals(13.42, lon, 0.0001)
    }

    @Test
    fun `round rounds negative coordinates correctly`() {
        val (lat, lon) = WeatherLocation.round(-33.8688, -151.2093)
        assertEquals(-33.87, lat, 0.0001)
        assertEquals(-151.21, lon, 0.0001)
    }

    @Test
    fun `round uses HALF_UP rounding convention`() {
        assertEquals(10.12, WeatherLocation.round(10.124), 0.0001)
        assertEquals(10.13, WeatherLocation.round(10.125), 0.0001)
        assertEquals(10.13, WeatherLocation.round(10.126), 0.0001)
        assertEquals(-10.12, WeatherLocation.round(-10.124), 0.0001)
        assertEquals(-10.13, WeatherLocation.round(-10.125), 0.0001)
    }

    @Test
    fun `round handles zero and already rounded numbers`() {
        val (lat, lon) = WeatherLocation.round(0.0, 0.0)
        assertEquals(0.0, lat, 0.0001)
        assertEquals(0.0, lon, 0.0001)

        val (lat2, lon2) = WeatherLocation.round(48.85, 2.35)
        assertEquals(48.85, lat2, 0.0001)
        assertEquals(2.35, lon2, 0.0001)
    }

    @Test
    fun `isStale returns true when last fix timestamp is 0 or negative`() {
        val now = 1_000_000_000L
        assertTrue(WeatherLocation.isStale(lastFixAt = 0L, now = now))
        assertTrue(WeatherLocation.isStale(lastFixAt = -100L, now = now))
    }

    @Test
    fun `isStale returns false when last fix is younger than 1 hour`() {
        val now = 10_000_000L
        val fiveMinutesAgo = now - (5 * 60 * 1000L)
        val fiftyNineMinutesAgo = now - (59 * 60 * 1000L)

        assertFalse(WeatherLocation.isStale(lastFixAt = fiveMinutesAgo, now = now))
        assertFalse(WeatherLocation.isStale(lastFixAt = fiftyNineMinutesAgo, now = now))
    }

    @Test
    fun `isStale returns true when last fix is 1 hour old or older`() {
        val now = 10_000_000L
        val exactlyOneHourAgo = now - (60 * 60 * 1000L)
        val sixtyOneMinutesAgo = now - (61 * 60 * 1000L)
        val oneDayAgo = now - (24 * 60 * 60 * 1000L)

        assertTrue(WeatherLocation.isStale(lastFixAt = exactlyOneHourAgo, now = now))
        assertTrue(WeatherLocation.isStale(lastFixAt = sixtyOneMinutesAgo, now = now))
        assertTrue(WeatherLocation.isStale(lastFixAt = oneDayAgo, now = now))
    }

    @Test
    fun `isStale returns true when system clock moved backward`() {
        val now = 10_000_000L
        val futureFix = now + 60_000L
        assertTrue(WeatherLocation.isStale(lastFixAt = futureFix, now = now))
    }

    @Test
    fun `hasMoved returns true when previous coordinates are null`() {
        assertTrue(WeatherLocation.hasMoved(null, null, 52.52, 13.41))
        assertTrue(WeatherLocation.hasMoved(52.52, null, 52.52, 13.41))
        assertTrue(WeatherLocation.hasMoved(null, 13.41, 52.52, 13.41))
    }

    @Test
    fun `hasMoved returns false when coordinates did not move or moved less than threshold`() {
        assertFalse(WeatherLocation.hasMoved(52.52, 13.41, 52.52, 13.41))
        assertFalse(WeatherLocation.hasMoved(52.52, 13.41, 52.53, 13.41))
        assertFalse(WeatherLocation.hasMoved(52.52, 13.41, 52.52, 13.43))
        assertFalse(WeatherLocation.hasMoved(52.52, 13.41, 52.57, 13.41)) // exactly 0.05
    }

    @Test
    fun `hasMoved returns true when moved more than 0_05 degrees`() {
        assertTrue(WeatherLocation.hasMoved(52.52, 13.41, 52.58, 13.41)) // 0.06 lat
        assertTrue(WeatherLocation.hasMoved(52.52, 13.41, 52.52, 13.47)) // 0.06 lon
        assertTrue(WeatherLocation.hasMoved(52.52, 13.41, 52.46, 13.41)) // -0.06 lat
        assertTrue(WeatherLocation.hasMoved(52.52, 13.41, 52.56, 13.45)) // diagonal (0.04, 0.04) -> hypot = 0.0566 > 0.05
    }

    @Test
    fun `resolvePlaceName returns Near you when location on and permission granted`() {
        val resolved = WeatherLocation.resolvePlaceName(
            useLocation = true,
            hasPermission = true,
            nearYouLabel = "Near you",
            savedPlaceName = "Berlin",
        )
        assertEquals("Near you", resolved)
    }

    @Test
    fun `resolvePlaceName falls back to saved place when permission is revoked`() {
        val resolved = WeatherLocation.resolvePlaceName(
            useLocation = true,
            hasPermission = false,
            nearYouLabel = "Near you",
            savedPlaceName = "Berlin",
        )
        assertEquals("Berlin", resolved)
    }

    @Test
    fun `resolvePlaceName returns saved place when location feature is off`() {
        val resolvedWithPerm = WeatherLocation.resolvePlaceName(
            useLocation = false,
            hasPermission = true,
            nearYouLabel = "Near you",
            savedPlaceName = "Munich",
        )
        assertEquals("Munich", resolvedWithPerm)

        val resolvedWithoutPerm = WeatherLocation.resolvePlaceName(
            useLocation = false,
            hasPermission = false,
            nearYouLabel = "Near you",
            savedPlaceName = "Munich",
        )
        assertEquals("Munich", resolvedWithoutPerm)
    }

    @Test
    fun `resolvePlaceName returns blank string without crashing when no saved place exists`() {
        val resolved = WeatherLocation.resolvePlaceName(
            useLocation = true,
            hasPermission = false,
            nearYouLabel = "Near you",
            savedPlaceName = "",
        )
        assertEquals("", resolved)
    }
}
