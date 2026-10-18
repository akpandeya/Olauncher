package app.olauncher.data.store

import android.content.Context

import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import app.olauncher.data.Prefs
import app.olauncher.wellbeing.WordLookupWorker

sealed interface Capture {
    data class Word(val text: String) : Capture
    data class Sentence(val text: String, val tokens: List<String>) : Capture
    data object Invalid : Capture
}

object WordLookup {
    internal var workEnqueuer: ((Context, Long) -> Unit)? = null

    /** Lookups run only when both the master German switch and the lookup switch are on. */
    fun isLookupAllowed(germanEnabled: Boolean, germanLookup: Boolean): Boolean =
        germanEnabled && germanLookup

    fun enqueue(context: Context, wordId: Long) {
        val prefs = Prefs(context)
        if (!isLookupAllowed(prefs.germanEnabled, prefs.germanLookup)) return

        val word = WordsStore(context).get(wordId) ?: return
        if (word.meaning.isNotBlank()) return

        workEnqueuer?.let {
            it(context, wordId)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val inputData = Data.Builder()
            .putLong(WordLookupWorker.KEY_WORD_ID, wordId)
            .build()

        val request = OneTimeWorkRequestBuilder<WordLookupWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "word_lookup_$wordId",
            ExistingWorkPolicy.KEEP,
            request
        )
    }
}

object WordCapture {
    private const val MAX_WORD_CHARS = 40
    private const val MAX_SENTENCE_CHARS = 400
    private const val PUNCTUATION = ".,;:!?\"'„“”‚‘()[]«»"
    private val PUNCTUATION_SET = PUNCTUATION.toSet()
    private val URL_REGEX = Regex("""(?i)\b(https?://|ftp://|www\.)\S+""")

    fun classify(raw: String): Capture {
        if (raw.isBlank()) return Capture.Invalid
        if (URL_REGEX.containsMatchIn(raw)) return Capture.Invalid

        val trimmed = raw.trim()
        if (trimmed.length > MAX_SENTENCE_CHARS) return Capture.Invalid

        // Check Word candidate: 1-4 words after trimming whitespace and surrounding punctuation, <= 40 chars, no URL
        val wordCandidate = trimmed.trim { it.isWhitespace() || it in PUNCTUATION_SET }
        if (wordCandidate.isNotEmpty() && wordCandidate.length <= MAX_WORD_CHARS) {
            val words = wordCandidate.split(Regex("""\s+""")).filter { it.isNotBlank() }
            if (words.size in 1..4) {
                return Capture.Word(wordCandidate)
            }
        }

        // Check Sentence candidate: longer selections up to 400 chars, tokens = the words without punctuation
        val tokens = trimmed.split(Regex("""\s+"""))
            .map { it.filter { c -> c !in PUNCTUATION_SET }.trim() }
            .filter { it.isNotBlank() && it.any { c -> c.isLetterOrDigit() } }

        if (tokens.size >= 2) {
            return Capture.Sentence(text = trimmed, tokens = tokens)
        }

        return Capture.Invalid
    }
}
