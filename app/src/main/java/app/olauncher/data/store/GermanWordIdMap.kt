package app.olauncher.data.store

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONArray
import org.json.JSONObject

/**
 * One local-id → server-id row: the phone's [Word.id] is local-only and the
 * German web server mints its own UUID when the outbox replays the word's
 * `POST /words`, so a later meaning update ([app.olauncher.helper.GermanMeaningSync])
 * needs this bridge to know which server row to PATCH.
 */
data class GermanWordIdEntry(
    val localId: Long,
    val serverId: String,
)

/**
 * Encodes and decodes the id-map file, Context-free and unit-tested. The
 * envelope is a JSON object `{version, entries}`; the decoder skips individually
 * corrupt or malformed rows (a bad row never blocks the mapping) and ignores
 * rows whose server id is not UUID-shaped — the server's `isId` contract
 * (apps/backend/src/lib/germanHttp.js), so nothing unparsable is ever stored.
 */
object GermanWordIdCodec {

    const val CURRENT_VERSION = 1

    private val UUID_REGEX = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", RegexOption.IGNORE_CASE)

    fun isServerWordId(raw: String?): Boolean = raw != null && UUID_REGEX.matches(raw)

    fun encode(entries: List<GermanWordIdEntry>): String {
        val envelope = JSONObject()
        envelope.put("version", CURRENT_VERSION)
        val array = JSONArray()
        for (entry in entries) {
            array.put(
                JSONObject()
                    .put("localId", entry.localId)
                    .put("serverId", entry.serverId)
            )
        }
        envelope.put("entries", array)
        return envelope.toString(2)
    }

    fun decode(raw: String?): List<GermanWordIdEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        val root = try {
            JSONObject(raw.trim())
        } catch (e: Exception) {
            return emptyList()
        }
        val array = root.optJSONArray("entries") ?: return emptyList()
        val entries = mutableListOf<GermanWordIdEntry>()
        for (i in 0 until array.length()) {
            val obj = array.opt(i) as? JSONObject ?: continue
            // org.json parses small numbers as Integer and large ones as Long; both are accepted.
            val localId = when (val value = obj.opt("localId")) {
                is Long -> value
                is Int -> value.toLong()
                else -> continue
            }
            val serverId = obj.opt("serverId") as? String
            if (!isServerWordId(serverId)) continue
            entries.add(GermanWordIdEntry(localId, serverId ?: continue))
        }
        return entries
    }
}

/**
 * The offline store for the local-id → server-id mapping, as JSON at
 * `<dir>/german_word_id_map.json` (`noBackupFilesDir` in the app; a temporary
 * folder in tests). Writes are atomic (temp file + rename, the
 * [GermanWebOutbox] pattern) and every operation runs under one process-wide
 * lock, the `WordsStore.LOCK` pattern, because the app builds a new map per
 * call site. The newest put for a local id wins.
 */
class GermanWordIdMap(private val dir: File) {

    /** Records the server id a replay created for [localId]. Overwrites any previous row. */
    fun put(localId: Long, serverId: String) = synchronized(LOCK) {
        if (!GermanWordIdCodec.isServerWordId(serverId)) return
        val others = load().filterNot { it.localId == localId }
        save(others + GermanWordIdEntry(localId, serverId))
    }

    /** The server id recorded for [localId], or null when its POST has not succeeded (yet). */
    fun serverIdFor(localId: Long): String? = synchronized(LOCK) {
        load().firstOrNull { it.localId == localId }?.serverId
    }

    private fun load(): List<GermanWordIdEntry> {
        if (!file().exists()) return emptyList()
        return try {
            GermanWordIdCodec.decode(file().readText())
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun save(entries: List<GermanWordIdEntry>) {
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val tempFile = File(dir, "$FILE_NAME.tmp")
        tempFile.writeText(GermanWordIdCodec.encode(entries))
        if (!tempFile.renameTo(file())) {
            try {
                Files.move(
                    tempFile.toPath(),
                    file().toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (e: Exception) {
                if (file().exists()) file().delete()
                if (!tempFile.renameTo(file())) {
                    tempFile.copyTo(file(), overwrite = true)
                    tempFile.delete()
                }
            }
        }
    }

    private fun file(): File = File(dir, FILE_NAME)

    companion object {
        const val FILE_NAME = "german_word_id_map.json"

        /** One lock for every instance, the `WordsStore.LOCK` pattern (new map per call site). */
        private val LOCK = Any()
    }
}
