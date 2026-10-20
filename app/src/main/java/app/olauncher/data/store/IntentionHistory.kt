package app.olauncher.data.store

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class IntentionEntry(
    val day: Long,
    val text: String,
)

object IntentionHistory {

    const val MAX_ENTRIES = 365

    fun encode(entries: List<IntentionEntry>): String {
        val array = JSONArray()
        for (entry in entries) {
            val obj = JSONObject()
            obj.put("day", entry.day)
            obj.put("text", entry.text)
            array.put(obj)
        }
        return array.toString(2)
    }

    /** Skips corrupt entries. Blank or invalid input is an empty list. */
    fun decode(raw: String?): List<IntentionEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        val array = try {
            JSONArray(raw)
        } catch (_: Exception) {
            return emptyList()
        }
        val list = mutableListOf<IntentionEntry>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (!obj.has("day") || !obj.has("text")) continue
            val day = obj.optLong("day", Long.MIN_VALUE)
            val text = obj.optString("text", "")
            if (day == Long.MIN_VALUE || text.isBlank()) continue
            list.add(IntentionEntry(day, text))
        }
        return normalize(list)
    }

    /** Sets [text] for [day], replacing that day's entry. Blank text removes it. */
    fun put(entries: List<IntentionEntry>, day: Long, text: String): List<IntentionEntry> {
        val others = entries.filter { it.day != day }
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return normalize(others)
        return normalize(others + IntentionEntry(day, trimmed))
    }

    fun remove(entries: List<IntentionEntry>, day: Long): List<IntentionEntry> =
        normalize(entries.filter { it.day != day })

    /** One entry per day (last wins), sorted oldest first, newest [MAX_ENTRIES] kept. */
    fun normalize(entries: List<IntentionEntry>): List<IntentionEntry> =
        entries.associateBy { it.day }.values.sortedBy { it.day }.takeLast(MAX_ENTRIES)
}

class IntentionHistoryStore(
    private val filesDir: File
) {
    companion object {
        const val FILE_NAME = "intention_history.json"
        private val LOCK = Any()
    }

    private val file = File(filesDir, FILE_NAME)

    /** Entries oldest first. Seeds from the old single intention when the file does not exist yet. */
    fun load(seedText: String = "", seedDay: Long = 0L): List<IntentionEntry> {
        synchronized(LOCK) {
            if (!file.exists()) {
                val seeded = IntentionHistory.put(emptyList(), seedDay, seedText)
                if (seedText.isNotBlank() && seedDay > 0L) {
                    save(seeded)
                    return seeded
                }
                return emptyList()
            }
            return read()
        }
    }

    fun put(day: Long, text: String) {
        synchronized(LOCK) {
            save(IntentionHistory.put(read(), day, text))
        }
    }

    private fun read(): List<IntentionEntry> {
        if (!file.isFile) return emptyList()
        return try {
            IntentionHistory.decode(file.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun save(entries: List<IntentionEntry>) {
        if (!filesDir.exists()) filesDir.mkdirs()
        val tempFile = File(filesDir, "$FILE_NAME.tmp")
        tempFile.writeText(IntentionHistory.encode(entries), Charsets.UTF_8)
        try {
            Files.move(
                tempFile.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE
            )
        } catch (_: Exception) {
            tempFile.copyTo(file, overwrite = true)
            tempFile.delete()
        }
    }
}
