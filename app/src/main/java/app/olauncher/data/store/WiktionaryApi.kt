package app.olauncher.data.store

import app.olauncher.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object WiktionaryApi {

    const val TIMEOUT_MS = 10_000
    const val SUGGEST_TIMEOUT_MS = 3_000
    const val SUGGEST_LIMIT = 3

    /** Result of a spelling-suggestion query; [Failed] covers 429, timeout and no network. */
    sealed interface SuggestResult {
        data class Suggestions(val words: List<String>) : SuggestResult
        data object Failed : SuggestResult
    }

    /** Test hook: replaces the network call in [suggest]. */
    internal var suggestFetcherOverride: ((String) -> SuggestResult)? = null

    /** Parses an opensearch response: `[query, [titles], [descriptions], [urls]]`. */
    fun parseSuggestions(body: String): List<String> {
        return try {
            val titles = org.json.JSONArray(body).getJSONArray(1)
            (0 until titles.length()).map { titles.getString(it) }.filter { it.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun suggest(query: String): SuggestResult {
        suggestFetcherOverride?.let { return it(query) }
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val url = URL(
            "https://en.wiktionary.org/w/api.php?action=opensearch&profile=fuzzy" +
                "&limit=$SUGGEST_LIMIT&namespace=0&format=json&search=$encoded"
        )
        var connection: HttpURLConnection? = null
        return try {
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = SUGGEST_TIMEOUT_MS
                readTimeout = SUGGEST_TIMEOUT_MS
                setRequestProperty(
                    "User-Agent",
                    "Thumbline/${BuildConfig.VERSION_NAME} (https://github.com/akpandeya/Olauncher)"
                )
            }
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                SuggestResult.Suggestions(parseSuggestions(body).take(SUGGEST_LIMIT))
            } else {
                SuggestResult.Failed
            }
        } catch (e: Exception) {
            SuggestResult.Failed
        } finally {
            connection?.disconnect()
        }
    }

    sealed interface FetchOutcome {
        data class Success(val definition: String) : FetchOutcome
        data object NotFound : FetchOutcome
        data class IoError(val error: IOException) : FetchOutcome
    }

    sealed interface LookupResult {
        /** [baseForm] is set when the typed word is an inflection and [meaning] is its base word's. */
        data class Found(val meaning: String, val candidate: String, val baseForm: String? = null) : LookupResult
        data object NotFound : LookupResult
        data object Failed : LookupResult
    }

    /** Test hook: replaces the network call in [fetch]. */
    internal var fetcherOverride: ((String) -> FetchOutcome)? = null

    fun lookup(text: String, fetcher: (String) -> FetchOutcome = ::fetch): LookupResult {
        for (candidate in WiktionaryParser.candidates(text)) {
            when (val outcome = fetcher(candidate)) {
                is FetchOutcome.Success -> return followBase(outcome.definition, candidate, fetcher)
                FetchOutcome.NotFound -> Unit
                is FetchOutcome.IoError -> return LookupResult.Failed
            }
        }
        return LookupResult.NotFound
    }

    /** Replaces an inflection's gloss with its base word's meaning; keeps the typed form on any miss. */
    private fun followBase(definition: String, candidate: String, fetcher: (String) -> FetchOutcome): LookupResult.Found {
        val resolved = LemmaLink.resolve(definition) { fetchBaseMeaning(it, fetcher) }
            ?: return LookupResult.Found(definition, candidate)
        return LookupResult.Found(resolved.meaning, resolved.baseForm, baseForm = resolved.baseForm)
    }

    /** First candidate spelling of [base] with a meaning; network errors count as a miss. */
    internal fun fetchBaseMeaning(base: String, fetcher: (String) -> FetchOutcome): String? {
        for (candidate in WiktionaryParser.candidates(base)) {
            val outcome = fetcher(candidate)
            if (outcome is FetchOutcome.Success) return outcome.definition
        }
        return null
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
