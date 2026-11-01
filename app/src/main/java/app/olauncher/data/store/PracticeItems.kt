package app.olauncher.data.store

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * One AI-generated practice item for a saved word (docs/design/ai-practice.md).
 *
 * The content (prompt, answer, choices, hint) is frozen at creation: it is written
 * once, after [PracticeContract.parse] validated it, and never edited afterwards.
 * [source] is [SOURCE_WORD] or [SOURCE_DIGEST]; [model] is the provider model that
 * generated the item, null for locally built cloze fallbacks.
 */
data class PracticeItem(
    val id: Long = 0,
    val wordId: Long,
    val type: DrillType,
    val prompt: String,
    val answer: String,
    val choices: List<String>,
    val hint: String? = null,
    val source: String? = null,
    val model: String? = null,
    val createdAt: Long = 0,
    /**
     * The model's full sentence (prompt with the gap filled), kept only in memory:
     * the quality filters consume it at parse time and the benchmark CSV records
     * it, but [PracticeItemsCodec] never persists it — the stored card stays
     * prompt + hint + answer + choices.
     */
    val full: String? = null,
) {
    object Source {
        const val WORD = "word"
        const val DIGEST = "digest"
    }
}

object PracticeItemsCodec {

    fun encode(items: List<PracticeItem>): String {
        val array = JSONArray()
        for (item in items) {
            val obj = JSONObject()
                .put("id", item.id)
                .put("wordId", item.wordId)
                .put("type", item.type.id)
                .put("prompt", item.prompt)
                .put("answer", item.answer)
            val choices = JSONArray()
            for (choice in item.choices) {
                choices.put(choice)
            }
            obj.put("choices", choices)
            if (item.hint != null) {
                obj.put("hint", item.hint)
            }
            if (item.source != null) {
                obj.put("source", item.source)
            }
            if (item.model != null) {
                obj.put("model", item.model)
            }
            obj.put("createdAt", item.createdAt)
            array.put(obj)
        }
        return array.toString(2)
    }

    /** Tolerant decode: drops malformed entries instead of failing the whole file. */
    fun decode(raw: String?): List<PracticeItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            decode(JSONArray(raw))
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun decode(array: JSONArray): List<PracticeItem> {
        val items = mutableListOf<PracticeItem>()
        for (i in 0 until array.length()) {
            try {
                val obj = array.getJSONObject(i)
                val id = obj.getLong("id")
                val wordId = obj.getLong("wordId")
                val type = DrillType.fromId(obj.getString("type")) ?: continue
                val prompt = obj.getString("prompt")
                val answer = obj.getString("answer")
                val choicesJson = obj.getJSONArray("choices")
                val choices = (0 until choicesJson.length()).map { choicesJson.getString(it) }
                val hint = optionalString(obj, "hint")
                val source = optionalString(obj, "source")
                val model = optionalString(obj, "model")
                val createdAt = obj.optLong("createdAt")
                items.add(PracticeItem(id, wordId, type, prompt, answer, choices, hint, source, model, createdAt))
            } catch (e: Exception) {
                // Ignore individual corrupt entry
            }
        }
        return items
    }

    private fun optionalString(obj: JSONObject, key: String): String? {
        return if (obj.has(key) && !obj.isNull(key)) obj.getString(key) else null
    }
}

/**
 * Store of validated AI-generated practice items, in its own file: like the review
 * event log it is written in batches, and the items are user data that costs money
 * to regenerate, so the file is exported (see [Backup]).
 */
class PracticeItemsStore(
    private val filesDir: File
) {
    constructor(context: Context) : this(context.filesDir)

    private val file = File(filesDir, "practice_items.json")

    fun all(): List<PracticeItem> = synchronized(LOCK) {
        if (!file.exists()) return emptyList()
        return try {
            PracticeItemsCodec.decode(file.readText())
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Stores new items (ids and createdAt assigned here) and returns the stored copies. */
    fun append(newItems: List<PracticeItem>): List<PracticeItem> = synchronized(LOCK) {
        if (newItems.isEmpty()) return emptyList()
        val existing = all()
        var nextId = generateId(existing)
        val createdAt = System.currentTimeMillis()
        val stored = newItems.map { item ->
            item.copy(id = nextId++, createdAt = createdAt)
        }
        save(existing + stored)
        return stored
    }

    private fun save(items: List<PracticeItem>) {
        if (!filesDir.exists()) {
            filesDir.mkdirs()
        }
        val tempFile = File(filesDir, "practice_items.json.tmp")
        val json = PracticeItemsCodec.encode(items)
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

    private fun generateId(items: List<PracticeItem>): Long {
        return (items.maxOfOrNull { it.id } ?: 0L) + 1
    }

    companion object {
        /**
         * One lock for every instance: every worker run builds its own store,
         * and the file's read-modify-write must never interleave —
         * a lost append loses paid-for items (review R1 #3).
         */
        private val LOCK = Any()
    }
}
