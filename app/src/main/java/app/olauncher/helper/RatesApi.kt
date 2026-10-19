package app.olauncher.helper

import app.olauncher.glance.WeatherApi
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Fetches daily ECB rates from Frankfurter (free, no key). */
object RatesApi {
    private const val URL_LATEST = "https://api.frankfurter.app/latest?from=EUR"

    @Throws(IOException::class)
    fun fetchLatest(): String {
        val connection = URL(URL_LATEST).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.setRequestProperty("User-Agent", WeatherApi.USER_AGENT)
        connection.instanceFollowRedirects = true
        val code = connection.responseCode
        if (code !in 200..299) throw IOException("HTTP $code from rates API")
        return connection.inputStream.bufferedReader().use { it.readText() }
    }
}
