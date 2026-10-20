package app.olauncher.data.store

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate

data class Word(
    val id: Long,
    val text: String,
    val context: String = "",
    val meaning: String = "",
    val meaningSource: String = "", /* "" | "wiktionary" | "user" */
    val box: Int = 1,
    val dueDay: Long,
    val addedAt: Long,
    val learnedAt: Long? = null
)

data class AddAllResult(
    val added: List<Word>,
    val alreadySaved: List<Word>
)

data class WordStats(
    val total: Int,
    val due: Int,
    val learned: Int
)

object WordsCodec {

    fun encode(words: List<Word>): String {
        return encodeToJson(words).toString(2)
    }

    fun encodeToJson(words: List<Word>): JSONArray {
        val array = JSONArray()
        for (word in words) {
            val obj = JSONObject()
            obj.put("id", word.id)
            obj.put("text", word.text)
            obj.put("context", word.context)
            obj.put("meaning", word.meaning)
            obj.put("meaningSource", word.meaningSource)
            obj.put("box", word.box)
            obj.put("dueDay", word.dueDay)
            obj.put("addedAt", word.addedAt)
            if (word.learnedAt != null) {
                obj.put("learnedAt", word.learnedAt)
            } else {
                obj.put("learnedAt", JSONObject.NULL)
            }
            array.put(obj)
        }
        return array
    }

    fun decode(raw: String?): List<Word> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            decode(JSONArray(raw))
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun decode(array: JSONArray): List<Word> {
        val items = mutableListOf<Word>()
        for (i in 0 until array.length()) {
            try {
                val obj = array.getJSONObject(i)
                val id = obj.getLong("id")
                val text = obj.getString("text")
                val context = if (obj.has("context") && !obj.isNull("context")) obj.getString("context") else ""
                val meaning = if (obj.has("meaning") && !obj.isNull("meaning")) obj.getString("meaning") else ""
                val meaningSource = if (obj.has("meaningSource") && !obj.isNull("meaningSource")) obj.getString("meaningSource") else ""
                val box = if (obj.has("box") && !obj.isNull("box")) obj.getInt("box") else 1
                val dueDay = obj.getLong("dueDay")
                val addedAt = obj.getLong("addedAt")
                val learnedAt = if (obj.has("learnedAt") && !obj.isNull("learnedAt")) {
                    obj.getLong("learnedAt")
                } else {
                    null
                }
                items.add(
                    Word(
                        id = id,
                        text = text,
                        context = context,
                        meaning = meaning,
                        meaningSource = meaningSource,
                        box = box,
                        dueDay = dueDay,
                        addedAt = addedAt,
                        learnedAt = learnedAt
                    )
                )
            } catch (e: Exception) {
                // Ignore individual corrupt entry
            }
        }
        return items
    }
}

class WordsStore(
    private val filesDir: File
) {
    constructor(context: Context) : this(context.filesDir)

    private val file = File(filesDir, "words.json")

    internal var epochDaySupplier: () -> Long = { LocalDate.now().toEpochDay() }
    internal var clockSupplier: () -> Long = { System.currentTimeMillis() }

    @Synchronized
    fun add(text: String, context: String = ""): Word {
        val cleanText = text.trim()
        val cleanContext = context.trim()
        val all = loadAll().toMutableList()
        val existingIndex = all.indexOfFirst { it.text.equals(cleanText, ignoreCase = true) }
        val word = if (existingIndex != -1) {
            val existing = all[existingIndex]
            if (cleanContext.isNotBlank() && cleanContext != existing.context) {
                val updated = existing.copy(context = cleanContext)
                all[existingIndex] = updated
                saveAll(all)
                updated
            } else {
                existing
            }
        } else {
            val now = clockSupplier()
            val today = epochDaySupplier()
            val id = generateId(all, now)
            val newWord = Word(
                id = id,
                text = cleanText,
                context = cleanContext,
                box = 1,
                dueDay = Leitner.newWordDueDay(today),
                addedAt = now
            )
            all.add(0, newWord)
            saveAll(all)
            newWord
        }
        return word
    }

    /** Saves each text not already stored (case-insensitive). Existing words are left untouched. */
    @Synchronized
    fun addAll(texts: List<String>, context: String = ""): AddAllResult {
        val cleanContext = context.trim()
        val all = loadAll().toMutableList()
        val added = mutableListOf<Word>()
        val alreadySaved = mutableListOf<Word>()
        val now = clockSupplier()
        val today = epochDaySupplier()
        for (raw in texts) {
            val cleanText = raw.trim()
            if (cleanText.isEmpty()) continue
            val existing = all.firstOrNull { it.text.equals(cleanText, ignoreCase = true) }
            if (existing != null) {
                if (added.none { it.id == existing.id } && alreadySaved.none { it.id == existing.id }) {
                    alreadySaved.add(existing)
                }
                continue
            }
            val newWord = Word(
                id = generateId(all, now),
                text = cleanText,
                context = cleanContext,
                box = 1,
                dueDay = Leitner.newWordDueDay(today),
                addedAt = now
            )
            all.add(0, newWord)
            added.add(newWord)
        }
        if (added.isNotEmpty()) saveAll(all)
        return AddAllResult(added, alreadySaved)
    }

    @Synchronized
    fun update(word: Word) {
        val all = loadAll().toMutableList()
        val index = all.indexOfFirst { it.id == word.id }
        if (index != -1) {
            all[index] = word
            saveAll(all)
        }
    }

    @Synchronized
    fun delete(id: Long) {
        val all = loadAll().toMutableList()
        val index = all.indexOfFirst { it.id == id }
        if (index != -1) {
            all.removeAt(index)
            saveAll(all)
        }
    }

    @Synchronized
    fun get(id: Long): Word? {
        return loadAll().firstOrNull { it.id == id }
    }

    @Synchronized
    fun all(): List<Word> {
        return loadAll()
    }

    @Synchronized
    fun due(today: Long, limit: Int = Int.MAX_VALUE): List<Word> {
        return Leitner.due(loadAll(), today, limit)
    }

    @Synchronized
    fun stats(today: Long): WordStats {
        val words = loadAll()
        val dueCount = words.count { it.learnedAt == null && it.dueDay <= today }
        val learnedCount = words.count { it.learnedAt != null }
        return WordStats(
            total = words.size,
            due = dueCount,
            learned = learnedCount
        )
    }

    private fun loadAll(): List<Word> {
        if (!file.exists()) return emptyList()
        return try {
            WordsCodec.decode(file.readText())
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveAll(items: List<Word>) {
        if (!filesDir.exists()) {
            filesDir.mkdirs()
        }
        val tempFile = File(filesDir, "words.json.tmp")
        val json = WordsCodec.encode(items)
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

    private fun generateId(items: List<Word>, seed: Long): Long {
        val maxId = items.maxOfOrNull { it.id } ?: 0L
        return maxOf(seed, maxId + 1)
    }
}
