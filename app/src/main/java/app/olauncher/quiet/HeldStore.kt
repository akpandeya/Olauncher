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
        if (raw.isNullOrBlank()) return emptyList()
        return try {
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
            list
        } catch (_: Exception) {
            emptyList()
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
    }

    private val file = File(filesDir, FILE_NAME)

    @Synchronized
    fun add(item: HeldItem) {
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

    @Synchronized
    fun all(): List<HeldItem> {
        return loadAll()
    }

    @Synchronized
    fun clear(pkg: String) {
        val remaining = loadAll().filter { it.pkg != pkg }
        saveAll(remaining)
    }

    @Synchronized
    fun clearAll() {
        saveAll(emptyList())
    }

    @Synchronized
    fun pruneOlderThan(cutoffMillis: Long = System.currentTimeMillis() - FORTY_EIGHT_HOURS_MS) {
        val remaining = loadAll().filter { it.postTime >= cutoffMillis }
        saveAll(remaining)
    }

    private fun loadAll(): List<HeldItem> {
        if (!file.exists() || !file.isFile) return emptyList()
        return try {
            HeldStoreCodec.decode(file.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            emptyList()
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
