package app.olauncher.wellbeing

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.olauncher.data.store.LemmaLink
import app.olauncher.data.store.WiktionaryApi
import app.olauncher.data.store.WiktionaryApi.FetchOutcome
import app.olauncher.data.store.WiktionaryParser
import app.olauncher.data.store.WordsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WordLookupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_WORD_ID = "word_id"
        const val MAX_ATTEMPTS = 3

        internal var fetcherOverride: ((String) -> FetchOutcome)?
            get() = WiktionaryApi.fetcherOverride
            set(value) { WiktionaryApi.fetcherOverride = value }

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
                    // An inflection's gloss has no meaning: swap in the base word, keep the typed form as context.
                    val resolved = LemmaLink.resolve(meaningFound) {
                        WiktionaryApi.fetchBaseMeaning(it, fetchCandidate)
                    }
                    val baseTaken = resolved != null && store.all().any {
                        it.id != current.id && it.text.equals(resolved.baseForm, ignoreCase = true)
                    }
                    store.update(
                        if (resolved != null && !baseTaken) {
                            current.copy(
                                text = resolved.baseForm,
                                context = current.context.ifBlank { current.text },
                                meaning = resolved.meaning,
                                meaningSource = "wiktionary"
                            )
                        } else {
                            current.copy(meaning = resolved?.meaning ?: meaningFound, meaningSource = "wiktionary")
                        }
                    )
                }
            }

            return Result.success()
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val wordId = inputData.getLong(KEY_WORD_ID, -1L)
        val store = WordsStore(applicationContext)
        lookupWord(
            wordId = wordId,
            store = store,
            runAttemptCount = runAttemptCount,
            fetchCandidate = WiktionaryApi::fetch
        )
    }
}
