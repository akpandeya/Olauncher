package app.olauncher.data.store

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale

data class WiktionaryCacheEntry(
    val key: String,
    val result: WiktionaryApi.LookupResult,
    val storedAt: Long
)

object WiktionaryCacheCodec {

    fun encode(entries: List<WiktionaryCacheEntry>): String {
        val array = JSONArray()
        for (entry in entries) {
            val obj = JSONObject()
            obj.put("key", entry.key)
            obj.put("storedAt", entry.storedAt)
            when (val result = entry.result) {
                is WiktionaryApi.LookupResult.Found -> {
                    obj.put("found", true)
                    obj.put("meaning", result.meaning)
                    obj.put("candidate", result.candidate)
                }
                WiktionaryApi.LookupResult.NotFound -> obj.put("found", false)
                WiktionaryApi.LookupResult.Failed -> continue
            }
            array.put(obj)
        }
        return array.toString(2)
    }

    fun decode(raw: String): List<WiktionaryCacheEntry> {
        if (raw.isBlank()) return emptyList()
        val array = try {
            JSONArray(raw)
        } catch (e: Exception) {
            return emptyList()
        }
        val entries = mutableListOf<WiktionaryCacheEntry>()
        for (i in 0 until array.length()) {
            try {
                val obj = array.getJSONObject(i)
                val key = obj.getString("key")
                val storedAt = obj.getLong("storedAt")
                val result = if (obj.getBoolean("found")) {
                    WiktionaryApi.LookupResult.Found(obj.getString("meaning"), obj.getString("candidate"))
                } else {
                    WiktionaryApi.LookupResult.NotFound
                }
                entries.add(WiktionaryCacheEntry(key, result, storedAt))
            } catch (e: Exception) {
                // Skip corrupt entry
            }
        }
        return entries
    }
}

class WiktionaryCache(private val file: File) {
    constructor(context: Context) : this(File(context.cacheDir, FILE_NAME))

    companion object {
        const val FILE_NAME = "wiktionary_cache.json"
        const val MAX_ENTRIES = 200
        const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
    }

    internal var clockSupplier: () -> Long = { System.currentTimeMillis() }

    /** Returns a cached [WiktionaryApi.LookupResult.Found] or NotFound, or null when unknown or expired. */
    @Synchronized
    fun get(text: String): WiktionaryApi.LookupResult? {
        val key = keyOf(text)
        val now = clockSupplier()
        return load().lastOrNull { it.key == key && now - it.storedAt <= MAX_AGE_MS }?.result
    }

    /** Stores Found and NotFound; Failed is never cached. */
    @Synchronized
    fun put(text: String, result: WiktionaryApi.LookupResult) {
        if (result == WiktionaryApi.LookupResult.Failed) return
        val key = keyOf(text)
        val kept = load().filter { it.key != key }.toMutableList()
        kept.add(WiktionaryCacheEntry(key, result, clockSupplier()))
        val trimmed = if (kept.size > MAX_ENTRIES) kept.takeLast(MAX_ENTRIES) else kept
        save(trimmed)
    }

    private fun keyOf(text: String): String = text.trim().lowercase(Locale.GERMAN)

    private fun load(): List<WiktionaryCacheEntry> {
        if (!file.exists()) return emptyList()
        return try {
            WiktionaryCacheCodec.decode(file.readText())
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(entries: List<WiktionaryCacheEntry>) {
        try {
            file.parentFile?.mkdirs()
            val tempFile = File(file.parentFile, "$FILE_NAME.tmp")
            tempFile.writeText(WiktionaryCacheCodec.encode(entries))
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
        } catch (e: Exception) {
            // Cache is best-effort
        }
    }
}
