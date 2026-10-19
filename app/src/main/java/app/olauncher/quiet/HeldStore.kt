package app.olauncher.quiet

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class HeldItem(
    val key: String,
    val pkg: String,
    val label: String,
    val title: String,
    val text: String,
    val postTime: Long,
)

object HeldStoreCodec {
    fun encode(items: List<HeldItem>): String {
        val array = JSONArray()
        for (item in items) {
            val obj = JSONObject()
            obj.put("key", item.key)
            obj.put("pkg", item.pkg)
            obj.put("label", item.label)
            obj.put("title", item.title)
            obj.put("text", item.text)
            obj.put("postTime", item.postTime)
            array.put(obj)
        }
        return array.toString(2)
    }

    fun decode(raw: String?): List<HeldItem> {
        return try {
            decodeStrict(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Throws on malformed input. Blank input is an empty list. */
    fun decodeStrict(raw: String?): List<HeldItem> {
        if (raw.isNullOrBlank()) return emptyList()
        run {
            val array = JSONArray(raw)
            val list = mutableListOf<HeldItem>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    HeldItem(
                        key = obj.getString("key"),
                        pkg = obj.getString("pkg"),
                        label = obj.optString("label", ""),
                        title = obj.optString("title", ""),
                        text = obj.optString("text", ""),
                        postTime = obj.getLong("postTime")
                    )
                )
            }
            return list
        }
    }
}

class HeldStore(
    private val filesDir: File
) {
    constructor(context: Context) : this(context.filesDir)

    companion object {
        const val FILE_NAME = "held.json"
        const val FORTY_EIGHT_HOURS_MS = 48L * 60 * 60 * 1000
        private val LOCK = Any()
    }

    private val file = File(filesDir, FILE_NAME)

    fun add(item: HeldItem) {
        synchronized(LOCK) {
            val all = loadAll().toMutableList()
            val sanitized = if (item.text.length > 120) item.copy(text = item.text.take(120)) else item
            val index = all.indexOfFirst { it.key == item.key }
            if (index != -1) {
                all[index] = sanitized
            } else {
                all.add(sanitized)
            }
            saveAll(all)
        }
    }

    fun all(): List<HeldItem> {
        synchronized(LOCK) {
            return loadAll()
        }
    }

    fun clear(pkg: String) {
        synchronized(LOCK) {
            val remaining = loadAll().filter { it.pkg != pkg }
            saveAll(remaining)
        }
    }

    fun clearAll() {
        synchronized(LOCK) {
            saveAll(emptyList())
        }
    }

    fun pruneOlderThan(cutoffMillis: Long = System.currentTimeMillis() - FORTY_EIGHT_HOURS_MS) {
        synchronized(LOCK) {
            val remaining = loadAll().filter { it.postTime >= cutoffMillis }
            saveAll(remaining)
        }
    }

    private fun loadAll(): List<HeldItem> {
        if (!file.exists() || !file.isFile) return emptyList()
        return try {
            HeldStoreCodec.decodeStrict(file.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            quarantineCorruptFile()
            emptyList()
        }
    }

    private fun quarantineCorruptFile() {
        val corrupt = File(filesDir, "$FILE_NAME.corrupt")
        try {
            Files.move(file.toPath(), corrupt.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (_: Exception) {
            // Keep the unreadable file in place if it cannot be moved.
        }
    }

    private fun saveAll(items: List<HeldItem>) {
        if (!filesDir.exists()) {
            filesDir.mkdirs()
        }
        val tempFile = File(filesDir, "$FILE_NAME.tmp")
        val json = HeldStoreCodec.encode(items)
        tempFile.writeText(json, Charsets.UTF_8)
        if (!tempFile.renameTo(file)) {
            try {
                Files.move(
                    tempFile.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (_: Exception) {
                if (file.exists()) file.delete()
                if (!tempFile.renameTo(file)) {
                    tempFile.copyTo(file, overwrite = true)
                    tempFile.delete()
                }
            }
        }
    }
}
