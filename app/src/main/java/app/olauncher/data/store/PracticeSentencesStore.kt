package app.olauncher.data.store

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * An accepted practice sentence for a word: either an AI rewrite of a harvested
 * sentence ([model] set) or the source sentence kept as-is ([model] empty).
 * [originalContext] keeps the word's sentence before replacement so nothing the
 * user had is lost, and [sourceSentenceId] points back to the harvested sentence.
 */
data class PracticeSentence(
    val wordId: Long,
    val sourceSentenceId: String,
    val text: String,
    val originalContext: String,
    val model: String,
    val createdAt: Long
)

object PracticeSentencesCodec {

    const val CURRENT_VERSION = 1

    fun encode(sentences: List<PracticeSentence>): String {
        val envelope = JSONObject()
        envelope.put("version", CURRENT_VERSION)

        val sentencesArray = JSONArray()
        for (sentence in sentences) {
            val obj = JSONObject()
            obj.put("wordId", sentence.wordId)
            obj.put("sourceSentenceId", sentence.sourceSentenceId)
            obj.put("text", sentence.text)
            obj.put("originalContext", sentence.originalContext)
            obj.put("model", sentence.model)
            obj.put("createdAt", sentence.createdAt)
            sentencesArray.put(obj)
        }
        envelope.put("sentences", sentencesArray)

        return envelope.toString(2)
    }

    fun decode(raw: String?): List<PracticeSentence> {
        if (raw.isNullOrBlank()) return emptyList()
        val trimmed = raw.trim()
        if (!trimmed.startsWith("{")) return emptyList()
        return try {
            val envelope = JSONObject(trimmed)
            val sentencesArray = envelope.optJSONArray("sentences") ?: return emptyList()
            decodeSentences(sentencesArray)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun decodeSentences(array: JSONArray): List<PracticeSentence> {
        val sentences = mutableListOf<PracticeSentence>()
        for (i in 0 until array.length()) {
            try {
                val obj = array.optJSONObject(i) ?: continue

                // Never use getString/optString here: they coerce numbers to strings.
                if (!obj.has("wordId") || obj.isNull("wordId")) continue
                val wordIdNumber = obj.opt("wordId") as? Number ?: continue
                val wordId = wordIdNumber.toLong()

                val sourceSentenceId = obj.opt("sourceSentenceId") as? String ?: continue

                val text = obj.opt("text") as? String ?: continue
                if (text.isBlank()) continue

                val originalContext = (obj.opt("originalContext") as? String) ?: ""

                val model = (obj.opt("model") as? String) ?: ""

                if (!obj.has("createdAt") || obj.isNull("createdAt")) continue
                val createdAtNumber = obj.opt("createdAt") as? Number ?: continue
                val createdAt = createdAtNumber.toLong()

                sentences.add(
                    PracticeSentence(
                        wordId = wordId,
                        sourceSentenceId = sourceSentenceId,
                        text = text,
                        originalContext = originalContext,
                        model = model,
                        createdAt = createdAt
                    )
                )
            } catch (e: Exception) {
                // Ignore individual corrupt entry
            }
        }
        return sentences
    }
}

/**
 * Stores accepted practice sentences in filesDir, following the
 * [RecentSentencesStore] pattern: versioned envelope, tolerant decode and an
 * atomic save through a `.tmp` file plus rename.
 */
class PracticeSentencesStore(
    private val filesDir: File,
    /** Cap on stored entries; oldest by [PracticeSentence.createdAt] are dropped. */
    private val maxEntries: Int = MAX_ENTRIES
) {
    constructor(context: Context) : this(context.filesDir)

    companion object {
        const val FILE_NAME = "practice_sentences.json"
        const val MAX_ENTRIES = 2000
    }

    private val file = File(filesDir, FILE_NAME)

    @Synchronized
    fun all(): List<PracticeSentence> {
        return loadData()
    }

    @Synchronized
    fun forWord(wordId: Long): List<PracticeSentence> {
        return loadData()
            .filter { it.wordId == wordId }
            .sortedByDescending { it.createdAt }
    }

    /**
     * Adds a sentence unless an entry for the same word with the same normalized
     * text already exists. Returns false and writes nothing when deduplicated.
     */
    @Synchronized
    fun add(sentence: PracticeSentence): Boolean {
        val existing = loadData()
        val normalizedText = RecentSentencesCodec.normalizeText(sentence.text)
        if (normalizedText.isBlank()) return false
        val isDuplicate = existing.any {
            it.wordId == sentence.wordId &&
                RecentSentencesCodec.normalizeText(it.text) == normalizedText
        }
        if (isDuplicate) return false

        val combined = existing + sentence.copy(text = normalizedText)
        val capped = if (combined.size > maxEntries) {
            // Drop the oldest entries by createdAt when over the cap (stable
            // sort keeps equal timestamps distinct by position).
            combined
                .withIndex()
                .sortedBy { it.value.createdAt }
                .drop(combined.size - maxEntries)
                .map { it.value }
        } else {
            combined
        }
        saveData(capped)
        return true
    }

    private fun loadData(): List<PracticeSentence> {
        if (!file.exists()) return emptyList()
        return try {
            PracticeSentencesCodec.decode(file.readText())
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveData(sentences: List<PracticeSentence>) {
        if (!filesDir.exists()) {
            filesDir.mkdirs()
        }
        val tempFile = File(filesDir, "$FILE_NAME.tmp")
        val json = PracticeSentencesCodec.encode(sentences)
        tempFile.writeText(json)
        if (!tempFile.renameTo(file)) {
            try {
                Files.move(
                    tempFile.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (e: Exception) {
                if (file.exists()) file.delete()
                if (!tempFile.renameTo(file)) {
                    tempFile.copyTo(file, overwrite = true)
                    tempFile.delete()
                }
            }
        }
    }
}
