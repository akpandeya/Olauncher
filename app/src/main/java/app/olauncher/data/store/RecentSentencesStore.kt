package app.olauncher.data.store

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.TimeUnit

data class RecentSentence(
    val id: String,
    val text: String,
    val source: String,
    val url: String,
    val publishedAt: Long,
    val fetchedAt: Long,
    val consumed: Boolean = false,
    val wordIds: List<Long> = emptyList()
)

data class FetchedArticle(
    val url: String,
    val fetchedAt: Long
)

data class RecentSentencesData(
    val sentences: List<RecentSentence>,
    val articles: List<FetchedArticle>
)

object RecentSentencesCodec {

    const val CURRENT_VERSION = 1

    fun encode(data: RecentSentencesData): String {
        val envelope = JSONObject()
        envelope.put("version", CURRENT_VERSION)

        val sentencesArray = JSONArray()
        for (sentence in data.sentences) {
            val obj = JSONObject()
            obj.put("id", sentence.id)
            obj.put("text", sentence.text)
            obj.put("source", sentence.source)
            obj.put("url", sentence.url)
            obj.put("publishedAt", sentence.publishedAt)
            obj.put("fetchedAt", sentence.fetchedAt)
            obj.put("consumed", sentence.consumed)

            val wordIdsArray = JSONArray()
            for (wordId in sentence.wordIds) {
                wordIdsArray.put(wordId)
            }
            obj.put("wordIds", wordIdsArray)
            sentencesArray.put(obj)
        }
        envelope.put("sentences", sentencesArray)

        val articlesArray = JSONArray()
        for (article in data.articles) {
            val obj = JSONObject()
            obj.put("url", article.url)
            obj.put("fetchedAt", article.fetchedAt)
            articlesArray.put(obj)
        }
        envelope.put("articles", articlesArray)

        return envelope.toString(2)
    }

    fun decode(raw: String?): RecentSentencesData {
        if (raw.isNullOrBlank()) return RecentSentencesData(emptyList(), emptyList())
        val trimmed = raw.trim()
        if (!trimmed.startsWith("{")) return RecentSentencesData(emptyList(), emptyList())
        return try {
            val envelope = JSONObject(trimmed)
            val sentencesArray = envelope.optJSONArray("sentences")
            val sentences = if (sentencesArray != null) decodeSentences(sentencesArray) else emptyList()
            val articlesArray = envelope.optJSONArray("articles")
            val articles = if (articlesArray != null) decodeArticles(articlesArray) else emptyList()
            RecentSentencesData(sentences, articles)
        } catch (e: Exception) {
            RecentSentencesData(emptyList(), emptyList())
        }
    }

    private fun decodeSentences(array: JSONArray): List<RecentSentence> {
        val sentences = mutableListOf<RecentSentence>()
        val seenIds = mutableSetOf<String>()
        for (i in 0 until array.length()) {
            try {
                val obj = array.optJSONObject(i) ?: continue

                val id = obj.opt("id") as? String ?: continue
                if (id.isBlank()) continue

                val text = obj.opt("text") as? String ?: continue

                val url = obj.opt("url") as? String ?: continue

                val source = (obj.opt("source") as? String) ?: ""

                if (!obj.has("publishedAt") || obj.isNull("publishedAt")) continue
                val publishedAtNumber = obj.opt("publishedAt") as? Number ?: continue
                val publishedAt = publishedAtNumber.toLong()

                if (!obj.has("fetchedAt") || obj.isNull("fetchedAt")) continue
                val fetchedAtNumber = obj.opt("fetchedAt") as? Number ?: continue
                val fetchedAt = fetchedAtNumber.toLong()

                val consumed = if (obj.has("consumed") && !obj.isNull("consumed")) {
                    obj.optBoolean("consumed", false)
                } else {
                    false
                }

                val wordIds = mutableListOf<Long>()
                val wordIdsArray = obj.optJSONArray("wordIds")
                if (wordIdsArray != null) {
                    for (j in 0 until wordIdsArray.length()) {
                        val entry = wordIdsArray.opt(j)
                        if (entry is Number) {
                            wordIds.add(entry.toLong())
                        }
                    }
                }

                if (!seenIds.add(id)) continue

                sentences.add(
                    RecentSentence(
                        id = id,
                        text = text,
                        source = source,
                        url = url,
                        publishedAt = publishedAt,
                        fetchedAt = fetchedAt,
                        consumed = consumed,
                        wordIds = wordIds
                    )
                )
            } catch (e: Exception) {
                // Ignore individual corrupt entry
            }
        }
        return sentences
    }

    private fun decodeArticles(array: JSONArray): List<FetchedArticle> {
        val articles = mutableListOf<FetchedArticle>()
        val seenUrls = mutableSetOf<String>()
        for (i in 0 until array.length()) {
            try {
                val obj = array.optJSONObject(i) ?: continue
                val url = obj.opt("url") as? String ?: continue
                if (url.isBlank()) continue

                if (!obj.has("fetchedAt") || obj.isNull("fetchedAt")) continue
                val fetchedAtNumber = obj.opt("fetchedAt") as? Number ?: continue
                val fetchedAt = fetchedAtNumber.toLong()

                if (!seenUrls.add(url)) continue

                articles.add(FetchedArticle(url = url, fetchedAt = fetchedAt))
            } catch (e: Exception) {
                // Ignore individual corrupt entry
            }
        }
        return articles
    }

    fun normalizeText(text: String): String {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFC)
        val noNbs = normalized.replace('\u00A0', ' ')
        return noNbs.replace(Regex("\\s+"), " ").trim()
    }

    fun textId(text: String): String {
        val normalized = normalizeText(text).lowercase(Locale.GERMAN)
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(16)
        for (i in 0 until 8) {
            val b = digest[i].toInt() and 0xFF
            if (b < 0x10) sb.append('0')
            sb.append(Integer.toHexString(b))
        }
        return sb.toString()
    }
}

class RecentSentencesStore(
    private val filesDir: File
) {
    constructor(context: Context) : this(context.filesDir)

    companion object {
        const val FILE_NAME = "recent_sentences.json"
        const val RETENTION_DAYS = 40
        const val MAX_SENTENCES = 5000
        const val MAX_ARTICLES = 3000
    }

    private val file = File(filesDir, FILE_NAME)

    @Synchronized
    fun all(): List<RecentSentence> {
        return loadData().sentences
    }

    @Synchronized
    fun unconsumed(): List<RecentSentence> {
        return loadData().sentences.filter { !it.consumed }
    }

    @Synchronized
    fun addAll(candidates: List<RecentSentence>): List<RecentSentence> {
        val data = loadData()
        val existingIds = data.sentences.mapTo(HashSet()) { it.id }
        val toAdd = mutableListOf<RecentSentence>()

        for (candidate in candidates) {
            val cleanText = RecentSentencesCodec.normalizeText(candidate.text)
            if (cleanText.isBlank()) continue
            val computedId = RecentSentencesCodec.textId(cleanText)
            if (existingIds.add(computedId)) {
                toAdd.add(
                    candidate.copy(
                        id = computedId,
                        text = cleanText
                    )
                )
            }
        }

        if (toAdd.isEmpty()) {
            return emptyList()
        }

        val combined = data.sentences + toAdd
        val capResult = enforceSentenceCap(combined)
        saveData(data.copy(sentences = capResult.sentences))
        return toAdd.filter { it.id !in capResult.droppedIds }
    }

    @Synchronized
    fun markConsumed(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val data = loadData()
        val targetIds = ids.toSet()
        var changed = false
        val updated = data.sentences.map { sentence ->
            if (sentence.id in targetIds && !sentence.consumed) {
                changed = true
                sentence.copy(consumed = true)
            } else {
                sentence
            }
        }
        if (changed) {
            saveData(data.copy(sentences = updated))
        }
    }

    @Synchronized
    fun linkWords(links: Map<String, List<Long>>) {
        if (links.isEmpty()) return
        val data = loadData()
        var changed = false
        val updated = data.sentences.map { sentence ->
            val newWordIds = links[sentence.id]
            if (newWordIds != null && newWordIds.isNotEmpty()) {
                val merged = (sentence.wordIds + newWordIds).distinct()
                if (merged != sentence.wordIds) {
                    changed = true
                    sentence.copy(wordIds = merged)
                } else {
                    sentence
                }
            } else {
                sentence
            }
        }
        if (changed) {
            saveData(data.copy(sentences = updated))
        }
    }

    @Synchronized
    fun forWord(wordId: Long): List<RecentSentence> {
        return loadData().sentences
            .filter { wordId in it.wordIds }
            .sortedByDescending { it.publishedAt }
    }

    @Synchronized
    fun hasArticle(url: String): Boolean {
        return loadData().articles.any { it.url == url }
    }

    /** Every recorded article url, read once (cheaper than [hasArticle] per feed item). */
    @Synchronized
    fun articleUrls(): Set<String> {
        return loadData().articles.mapTo(HashSet()) { it.url }
    }

    @Synchronized
    fun recordArticles(urls: Collection<String>, fetchedAt: Long) {
        if (urls.isEmpty()) return
        val data = loadData()
        val seenUrls = data.articles.mapTo(HashSet()) { it.url }
        val toAdd = mutableListOf<FetchedArticle>()
        for (url in urls) {
            if (seenUrls.add(url)) {
                toAdd.add(FetchedArticle(url = url, fetchedAt = fetchedAt))
            }
        }
        if (toAdd.isEmpty()) return

        val combined = data.articles + toAdd
        val capped = if (combined.size > MAX_ARTICLES) {
            val excess = combined.size - MAX_ARTICLES
            val toDropUrls = combined.sortedBy { it.fetchedAt }.take(excess).mapTo(HashSet()) { it.url }
            combined.filter { it.url !in toDropUrls }
        } else {
            combined
        }
        saveData(data.copy(articles = capped))
    }

    @Synchronized
    fun prune(nowMs: Long) {
        val data = loadData()
        val retentionMs = TimeUnit.DAYS.toMillis(RETENTION_DAYS.toLong())
        val cutoff = nowMs - retentionMs

        val remainingSentences = data.sentences.filter { it.publishedAt >= cutoff }
        val remainingArticles = data.articles.filter { it.fetchedAt >= cutoff }

        if (remainingSentences.size != data.sentences.size || remainingArticles.size != data.articles.size) {
            saveData(RecentSentencesData(sentences = remainingSentences, articles = remainingArticles))
        }
    }

    private data class CapResult(
        val sentences: List<RecentSentence>,
        val droppedIds: Set<String>
    )

    private fun enforceSentenceCap(sentences: List<RecentSentence>): CapResult {
        val excess = sentences.size - MAX_SENTENCES
        if (excess <= 0) {
            return CapResult(sentences, emptySet())
        }

        val consumed = sentences.filter { it.consumed }.sortedBy { it.publishedAt }
        val unconsumed = sentences.filter { !it.consumed }.sortedBy { it.publishedAt }

        val toDropConsumed = consumed.take(excess)
        val remainingExcess = excess - toDropConsumed.size
        val toDropUnconsumed = if (remainingExcess > 0) unconsumed.take(remainingExcess) else emptyList()

        val dropIds = (toDropConsumed + toDropUnconsumed).mapTo(HashSet()) { it.id }
        val remaining = sentences.filter { it.id !in dropIds }
        return CapResult(remaining, dropIds)
    }

    private fun loadData(): RecentSentencesData {
        if (!file.exists()) return RecentSentencesData(emptyList(), emptyList())
        return try {
            RecentSentencesCodec.decode(file.readText())
        } catch (e: Exception) {
            RecentSentencesData(emptyList(), emptyList())
        }
    }

    private fun saveData(data: RecentSentencesData) {
        if (!filesDir.exists()) {
            filesDir.mkdirs()
        }
        val tempFile = File(filesDir, "$FILE_NAME.tmp")
        val json = RecentSentencesCodec.encode(data)
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
