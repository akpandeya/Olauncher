package app.olauncher.glance

import app.olauncher.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

object WeatherApi {

    val USER_AGENT = "Thumbline/${BuildConfig.VERSION_NAME} (https://github.com/akpandeya/Olauncher)"

    fun searchPlaces(name: String): List<Place> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return emptyList()
        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        val urlString = "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=5&language=en&format=json"
        return try {
            val json = get(urlString)
            WeatherParser.parsePlaces(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Throws(IOException::class)
    fun fetchForecast(lat: Double, lon: Double, unit: String): String {
        val latStr = String.format(Locale.US, "%.2f", lat)
        val lonStr = String.format(Locale.US, "%.2f", lon)
        return fetchForecast(latStr, lonStr, unit)
    }

    @Throws(IOException::class)
    fun fetchForecast(lat: String, lon: String, unit: String): String {
        val unitParam = if (unit.equals("F", ignoreCase = true)) "&temperature_unit=fahrenheit" else ""
        val urlString = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,is_day&hourly=temperature_2m,precipitation_probability,weather_code&daily=temperature_2m_max,temperature_2m_min&forecast_hours=12&forecast_days=1&timezone=auto$unitParam"
        return get(urlString)
    }

    @Throws(IOException::class)
    private fun get(urlString: String): String {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.instanceFollowRedirects = true

        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
            throw IOException("HTTP $responseCode from $urlString: $error")
        }

        return connection.inputStream.bufferedReader().use { it.readText() }
    }
}
