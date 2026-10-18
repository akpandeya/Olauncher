package app.olauncher.glance

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import app.olauncher.R
import app.olauncher.data.Prefs
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.hypot

object WeatherLocation {

    const val STALE_DURATION_MS = 60 * 60 * 1000L // 1 hour
    const val MOVED_THRESHOLD_DEGREES = 0.05

    // Injected hooks for unit testing
    internal var nowSupplier: () -> Long = { System.currentTimeMillis() }
    internal var permissionChecker: ((Context) -> Boolean)? = null
    internal var locationFetcher: ((Context, (Location?) -> Unit) -> Unit)? = null
    internal var weatherNowTrigger: ((Context) -> Unit)? = null
    internal var refreshHook: ((Context) -> Unit)? = null
    internal var placeNameProvider: ((Context) -> String)? = null

    /**
     * Pure function to round a coordinate value to 2 decimal places.
     */
    fun round(value: Double): Double {
        return BigDecimal.valueOf(value)
            .setScale(2, RoundingMode.HALF_UP)
            .toDouble()
    }

    /**
     * Pure function to round latitude and longitude to 2 decimal places.
     */
    fun round(lat: Double, lon: Double): Pair<Double, Double> {
        return Pair(round(lat), round(lon))
    }

    /**
     * Pure function checking if the last location fix is older than 1 hour.
     */
    fun isStale(lastFixAt: Long, now: Long = nowSupplier()): Boolean {
        if (lastFixAt <= 0L) return true
        if (now < lastFixAt) return true
        return (now - lastFixAt) >= STALE_DURATION_MS
    }

    /**
     * Pure function checking if coordinates moved by more than 0.05 degrees.
     */
    fun hasMoved(
        oldLat: Double?,
        oldLon: Double?,
        newLat: Double,
        newLon: Double,
        threshold: Double = MOVED_THRESHOLD_DEGREES,
    ): Boolean {
        if (oldLat == null || oldLon == null) return true
        val dLat = newLat - oldLat
        val dLon = newLon - oldLon
        val distance = hypot(dLat, dLon)
        return distance > threshold
    }

    /**
     * Checks whether ACCESS_COARSE_LOCATION has been granted.
     */
    fun hasPermission(context: Context): Boolean {
        return permissionChecker?.invoke(context) ?: (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    /**
     * Pure function resolving the display place name based on location setting and permission.
     */
    fun resolvePlaceName(
        useLocation: Boolean,
        hasPermission: Boolean,
        nearYouLabel: String,
        savedPlaceName: String,
    ): String {
        return if (useLocation && hasPermission) {
            nearYouLabel
        } else {
            savedPlaceName
        }
    }

    /**
     * Returns the place name to display (either "Near you" or the last stored city).
     * Falls back to the last stored place when permission is revoked or disabled.
     */
    fun placeName(context: Context, prefs: Prefs = Prefs(context)): String {
        return placeNameProvider?.invoke(context) ?: resolvePlaceName(
            useLocation = prefs.weatherUseLocation,
            hasPermission = hasPermission(context),
            nearYouLabel = context.getString(R.string.weather_near_you),
            savedPlaceName = prefs.weatherPlaceName,
        )
    }

    /**
     * Refreshes the location fix if stale (> 1 h) and location weather is enabled.
     * Stores rounded coordinates (2 decimals) and triggers weather_now when moved > 0.05°.
     * Revoking permission in system settings falls back safely without crashing.
     */
    @SuppressLint("MissingPermission")
    fun refreshIfStale(context: Context, force: Boolean = false): Boolean {
        refreshHook?.invoke(context)
        val prefs = Prefs(context)
        if (!prefs.weatherEnabled || !prefs.weatherUseLocation) {
            return false
        }
        if (!hasPermission(context)) {
            return false
        }
        val now = nowSupplier()
        if (!force && !isStale(prefs.weatherLocationAt, now)) {
            return false
        }

        val customFetcher = locationFetcher
        if (customFetcher != null) {
            customFetcher(context) { location ->
                handleLocationResult(context, prefs, location)
            }
            return true
        }

        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return false
            val provider = when {
                locationManager.allProviders.contains(LocationManager.FUSED_PROVIDER) -> LocationManager.FUSED_PROVIDER
                locationManager.allProviders.contains(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> LocationManager.NETWORK_PROVIDER
            }

            locationManager.getCurrentLocation(
                provider,
                null,
                ContextCompat.getMainExecutor(context),
            ) { location ->
                handleLocationResult(context, prefs, location)
            }
            true
        } catch (e: SecurityException) {
            // Permission revoked: fall back safely without crashing
            false
        } catch (e: Exception) {
            false
        }
    }

    internal fun handleLocationResult(context: Context, prefs: Prefs, location: Location?) {
        if (location == null) return
        val (lat, lon) = round(location.latitude, location.longitude)
        val prevLat = prefs.weatherLat.toDoubleOrNull()
        val prevLon = prefs.weatherLon.toDoubleOrNull()
        val moved = hasMoved(prevLat, prevLon, lat, lon)

        prefs.weatherLat = String.format(Locale.US, "%.2f", lat)
        prefs.weatherLon = String.format(Locale.US, "%.2f", lon)
        prefs.weatherLocationAt = nowSupplier()

        if (moved) {
            val trigger = weatherNowTrigger ?: { ctx -> WeatherWorker.runOnce(ctx) }
            trigger(context)
        }
    }
}
