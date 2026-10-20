package app.olauncher.data.store

import app.olauncher.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object WiktionaryApi {

    const val TIMEOUT_MS = 10_000

    sealed interface FetchOutcome {
        data class Success(val definition: String) : FetchOutcome
        data object NotFound : FetchOutcome
        data class IoError(val error: IOException) : FetchOutcome
    }

    sealed interface LookupResult {
        data class Found(val meaning: String, val candidate: String) : LookupResult
        data object NotFound : LookupResult
        data object Failed : LookupResult
    }

    /** Test hook: replaces the network call in [fetch]. */
    internal var fetcherOverride: ((String) -> FetchOutcome)? = null

    fun lookup(text: String, fetcher: (String) -> FetchOutcome = ::fetch): LookupResult {
        for (candidate in WiktionaryParser.candidates(text)) {
            when (val outcome = fetcher(candidate)) {
                is FetchOutcome.Success -> return LookupResult.Found(outcome.definition, candidate)
                FetchOutcome.NotFound -> Unit
                is FetchOutcome.IoError -> return LookupResult.Failed
            }
        }
        return LookupResult.NotFound
    }

    fun fetch(candidate: String): FetchOutcome {
        fetcherOverride?.let { return it(candidate) }

        val encoded = URLEncoder.encode(candidate, "UTF-8").replace("+", "%20")
        val url = URL("https://en.wiktionary.org/api/rest_v1/page/definition/$encoded")
        var connection: HttpURLConnection? = null
        return try {
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty(
                    "User-Agent",
                    "Thumbline/${BuildConfig.VERSION_NAME} (https://github.com/akpandeya/Olauncher)"
                )
            }
            val code = connection.responseCode
            when {
                code == HttpURLConnection.HTTP_OK -> {
                    val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val parsed = WiktionaryParser.parse(body)
                    if (parsed != null) {
                        FetchOutcome.Success(parsed)
                    } else {
                        FetchOutcome.NotFound
                    }
                }
                code == HttpURLConnection.HTTP_NOT_FOUND -> {
                    FetchOutcome.NotFound
                }
                code in 500..599 -> {
                    FetchOutcome.IoError(IOException("HTTP error: $code"))
                }
                else -> {
                    FetchOutcome.NotFound
                }
            }
        } catch (e: IOException) {
            FetchOutcome.IoError(e)
        } catch (e: Exception) {
            FetchOutcome.IoError(IOException(e))
        } finally {
            connection?.disconnect()
        }
    }
}
