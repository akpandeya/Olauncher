package app.olauncher.data.store

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class SavedItem(
    val id: Long,
    val url: String,
    val title: String,
    val savedAt: Long,
    val archivedAt: Long? = null
)

object SavedItemsCodec {

    fun encode(items: List<SavedItem>): String {
        return encodeToJson(items).toString(2)
    }

    fun encodeToJson(items: List<SavedItem>): JSONArray {
        val array = JSONArray()
        for (item in items) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("url", item.url)
            obj.put("title", item.title)
            obj.put("savedAt", item.savedAt)
            if (item.archivedAt != null) {
                obj.put("archivedAt", item.archivedAt)
            } else {
                obj.put("archivedAt", JSONObject.NULL)
            }
            array.put(obj)
        }
        return array
    }

    fun decode(raw: String?): List<SavedItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            decode(JSONArray(raw))
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun decode(array: JSONArray): List<SavedItem> {
        val items = mutableListOf<SavedItem>()
        for (i in 0 until array.length()) {
            try {
                val obj = array.getJSONObject(i)
                val id = obj.getLong("id")
                val url = obj.getString("url")
                val title = obj.getString("title")
                val savedAt = obj.getLong("savedAt")
                val archivedAt = if (obj.has("archivedAt") && !obj.isNull("archivedAt")) {
                    obj.getLong("archivedAt")
                } else {
                    null
                }
                items.add(
                    SavedItem(
                        id = id,
                        url = url,
                        title = title,
                        savedAt = savedAt,
                        archivedAt = archivedAt
                    )
                )
            } catch (e: Exception) {
                // Ignore individual corrupt entry
            }
        }
        return items
    }
}

class SavedItemsStore(
    private val filesDir: File
) {
    constructor(context: Context) : this(context.filesDir)

    private val file = File(filesDir, "saved_items.json")

    @Synchronized
    fun add(url: String, title: String): SavedItem {
        val cleanUrl = url.trim()
        val cleanTitle = title.trim()
        val now = System.currentTimeMillis()
        val all = loadAll().toMutableList()
        val existingIndex = all.indexOfFirst { it.url == cleanUrl && it.archivedAt == null }
        val item = if (existingIndex != -1) {
            val existing = all.removeAt(existingIndex)
            existing.copy(
                title = if (cleanTitle.isNotBlank()) cleanTitle else existing.title,
                savedAt = now
            )
        } else {
            val id = generateId(all, now)
            SavedItem(
                id = id,
                url = cleanUrl,
                title = cleanTitle,
                savedAt = now,
                archivedAt = null
            )
        }
        all.add(0, item)
        saveAll(all)
        return item
    }

    @Synchronized
    fun add(item: SavedItem): SavedItem {
        val all = loadAll().toMutableList()
        val existingIndex = all.indexOfFirst { it.url == item.url && it.archivedAt == null }
        val toSave = if (existingIndex != -1) {
            all.removeAt(existingIndex)
            item.copy(id = if (item.id != 0L) item.id else generateId(all, item.savedAt))
        } else {
            if (item.id != 0L) item else item.copy(id = generateId(all, item.savedAt))
        }
        all.add(0, toSave)
        saveAll(all)
        return toSave
    }

    @Synchronized
    fun archive(id: Long) {
        val all = loadAll().toMutableList()
        val index = all.indexOfFirst { it.id == id }
        if (index != -1) {
            val item = all[index]
            if (item.archivedAt == null) {
                all[index] = item.copy(archivedAt = System.currentTimeMillis())
                saveAll(all)
            }
        }
    }

    /** Puts an item's archive stamp back to [previousArchivedAt], in place (used by Undo). */
    @Synchronized
    fun restoreArchivedAt(id: Long, previousArchivedAt: Long?) {
        val all = loadAll()
        if (all.none { it.id == id }) return
        saveAll(app.olauncher.helper.ArchiveChange(id, previousArchivedAt).undo(all))
    }

    @Synchronized
    fun unarchive(id: Long) {
        val all = loadAll().toMutableList()
        val index = all.indexOfFirst { it.id == id }
        if (index != -1) {
            val item = all.removeAt(index)
            all.removeAll { it.url == item.url && it.archivedAt == null }
            all.add(0, item.copy(archivedAt = null))
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
    fun active(): List<SavedItem> {
        return loadAll().filter { it.archivedAt == null }
    }

    @Synchronized
    fun archived(): List<SavedItem> {
        return loadAll().filter { it.archivedAt != null }
    }

    private fun loadAll(): List<SavedItem> {
        if (!file.exists()) return emptyList()
        return try {
            SavedItemsCodec.decode(file.readText())
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveAll(items: List<SavedItem>) {
        if (!filesDir.exists()) {
            filesDir.mkdirs()
        }
        val tempFile = File(filesDir, "saved_items.json.tmp")
        val json = SavedItemsCodec.encode(items)
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

    private fun generateId(items: List<SavedItem>, seed: Long): Long {
        val maxId = items.maxOfOrNull { it.id } ?: 0L
        return maxOf(seed, maxId + 1)
    }
}
