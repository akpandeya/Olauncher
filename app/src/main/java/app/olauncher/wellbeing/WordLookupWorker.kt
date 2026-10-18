package app.olauncher.wellbeing

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.olauncher.BuildConfig
import app.olauncher.data.store.WiktionaryParser
import app.olauncher.data.store.WordsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class WordLookupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_WORD_ID = "word_id"
        const val TIMEOUT_MS = 10_000
        const val MAX_ATTEMPTS = 3

        internal var fetcherOverride: ((String) -> FetchOutcome)? = null

        internal fun lookupWord(
            wordId: Long,
            store: WordsStore,
            runAttemptCount: Int,
            fetchCandidate: (String) -> FetchOutcome
        ): Result {
            if (wordId == -1L) return Result.success()
            val word = store.get(wordId) ?: return Result.success()

            if (word.meaningSource == "user") {
                return Result.success()
            }

            val candidates = WiktionaryParser.candidates(word.text)
            var meaningFound: String? = null

            for (candidate in candidates) {
                when (val outcome = fetchCandidate(candidate)) {
                    is FetchOutcome.Success -> {
                        meaningFound = outcome.definition
                        break
                    }
                    FetchOutcome.NotFound -> {
                        // Try next candidate
                    }
                    is FetchOutcome.IoError -> {
                        return if (runAttemptCount < MAX_ATTEMPTS - 1) {
                            Result.retry()
                        } else {
                            Result.failure()
                        }
                    }
                }
            }

            if (meaningFound != null) {
                val current = store.get(wordId)
                if (current != null && current.meaningSource != "user") {
                    store.update(current.copy(meaning = meaningFound, meaningSource = "wiktionary"))
                }
            }

            return Result.success()
        }
    }

    sealed interface FetchOutcome {
        data class Success(val definition: String) : FetchOutcome
        data object NotFound : FetchOutcome
        data class IoError(val error: IOException) : FetchOutcome
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val wordId = inputData.getLong(KEY_WORD_ID, -1L)
        val store = WordsStore(applicationContext)
        lookupWord(
            wordId = wordId,
            store = store,
            runAttemptCount = runAttemptCount,
            fetchCandidate = ::fetchCandidate
        )
    }

    private fun fetchCandidate(candidate: String): FetchOutcome {
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
