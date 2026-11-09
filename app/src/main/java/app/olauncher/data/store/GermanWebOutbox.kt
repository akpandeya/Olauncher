package app.olauncher.data.store

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * One queued write for the German web app (epic #835, task 03), replayed
 * byte-identically on every retry: the [idempotencyKey] is minted once here at
 * enqueue time and the [bodyJson] is stored exactly as captured — `today`
 * included, never recomputed at replay time.
 */
data class GermanOutboxEntry(
    /** `ola-<UUID>`, matching the server's `[A-Za-z0-9._:-]{1,128}`. Never regenerated on retry. */
    val idempotencyKey: String,
    val method: String,
    val path: String,
    /** The request body exactly as captured; null for a body-less request. */
    val bodyJson: String?,
    val createdAt: Long,
    /** Send attempts that ended in a retry decision so far. */
    val attempts: Int,
    /** Epoch millis before which the entry must not be sent again. */
    val nextAttemptAt: Long,
)

/**
 * An entry that will never succeed (a 4xx the server rejects, or an undecodable
 * line in the file) and was therefore dropped instead of silently lost. The
 * list is capped by [GermanWebOutbox.MAX_FAILED] entries, newest first kept.
 */
data class GermanOutboxFailedEntry(
    /** What the entry was about: the captured word's text, or the raw JSON of a corrupt line. */
    val text: String,
    /** The HTTP status it failed with, or "corrupt" for an undecodable entry. */
    val status: String,
    val time: Long,
)

/**
 * Encodes and decodes the outbox file, Context-free and unit-tested. The
 * envelope is a JSON object `{version, entries, failed}`; the decoder drops the
 * whole file only when it is not a JSON object and skips individually corrupt
 * entries, reporting their raw text as [Decoded.corruptRaw] so the store can
 * move them to the failed list — one bad entry never blocks the queue.
 */
object GermanOutboxCodec {

    const val CURRENT_VERSION = 1

    /** The server's idempotency keys are 1..128 chars of `[A-Za-z0-9._:-]`. */
    const val MAX_KEY_LENGTH = 128

    /** Raw text kept for a corrupt or failed line, so the failed list stays readable. */
    const val MAX_FAILED_TEXT_LENGTH = 200

    /** A decode result: live entries, the failed list and the raw texts of dropped lines. */
    data class Decoded(
        val entries: List<GermanOutboxEntry>,
        val failed: List<GermanOutboxFailedEntry>,
        val corruptRaw: List<String>,
    )

    fun encode(entries: List<GermanOutboxEntry>, failed: List<GermanOutboxFailedEntry>): String {
        val envelope = JSONObject()
        envelope.put("version", CURRENT_VERSION)
        envelope.put("entries", entriesToJson(entries))
        envelope.put("failed", failedToJson(failed))
        return envelope.toString(2)
    }

    fun decode(raw: String?): Decoded {
        if (raw.isNullOrBlank()) return Decoded(emptyList(), emptyList(), emptyList())
        val root = try {
            JSONObject(raw.trim())
        } catch (e: Exception) {
            // Not a JSON object at all: the whole file is unusable. The raw text
            // goes to corruptRaw so it is recorded, not silently lost.
            return Decoded(emptyList(), emptyList(), listOf(truncate(raw)))
        }
        val entries = mutableListOf<GermanOutboxEntry>()
        val corrupt = mutableListOf<String>()
        val entriesArray = root.optJSONArray("entries")
        if (entriesArray == null && root.has("entries")) {
            corrupt += truncate((root.opt("entries") ?: JSONObject.NULL).toString())
        }
        if (entriesArray != null) {
            for (i in 0 until entriesArray.length()) {
                val decoded = (entriesArray.opt(i) as? JSONObject)?.let(::decodeEntry)
                if (decoded == null) {
                    corrupt += truncate(entriesArray.opt(i)?.toString() ?: "null")
                } else {
                    entries += decoded
                }
            }
        }
        val failed = mutableListOf<GermanOutboxFailedEntry>()
        root.optJSONArray("failed")?.let { array ->
            for (i in 0 until array.length()) {
                val decoded = (array.opt(i) as? JSONObject)?.let(::decodeFailed)
                if (decoded != null) failed += decoded
            }
        }
        return Decoded(entries, failed, corrupt)
    }

    /** Complete-or-nothing: every field must be present with the right type, or the entry is null. */
    fun decodeEntry(obj: JSONObject): GermanOutboxEntry? {
        val key = obj.opt("idempotencyKey") as? String ?: return null
        val method = obj.opt("method") as? String ?: return null
        val path = obj.opt("path") as? String ?: return null
        if (!isValidKey(key) || method.isBlank() || path.isBlank()) return null
        val createdAt = obj.optEpochMillis("createdAt") ?: return null
        val attempts = obj.optEpochMillis("attempts")?.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt() ?: return null
        val nextAttemptAt = obj.optEpochMillis("nextAttemptAt") ?: return null
        return GermanOutboxEntry(
            idempotencyKey = key,
            method = method,
            path = path,
            bodyJson = obj.opt("bodyJson") as? String,
            createdAt = createdAt,
            attempts = attempts,
            nextAttemptAt = nextAttemptAt,
        )
    }

    private fun decodeFailed(obj: JSONObject): GermanOutboxFailedEntry? {
        val text = obj.opt("text") as? String ?: return null
        val status = obj.opt("status") as? String ?: return null
        if (text.isBlank() || status.isBlank()) return null
        val time = obj.optEpochMillis("time") ?: return null
        return GermanOutboxFailedEntry(text = truncate(text), status = status, time = time)
    }

    private fun entriesToJson(entries: List<GermanOutboxEntry>): JSONArray {
        val array = JSONArray()
        for (entry in entries) {
            val obj = JSONObject()
            obj.put("idempotencyKey", entry.idempotencyKey)
            obj.put("method", entry.method)
            obj.put("path", entry.path)
            if (entry.bodyJson == null) obj.put("bodyJson", JSONObject.NULL) else obj.put("bodyJson", entry.bodyJson)
            obj.put("createdAt", entry.createdAt)
            obj.put("attempts", entry.attempts)
            obj.put("nextAttemptAt", entry.nextAttemptAt)
            array.put(obj)
        }
        return array
    }

    private fun failedToJson(failed: List<GermanOutboxFailedEntry>): JSONArray {
        val array = JSONArray()
        for (entry in failed) {
            array.put(
                JSONObject()
                    .put("text", entry.text)
                    .put("status", entry.status)
                    .put("time", entry.time)
            )
        }
        return array
    }

    /** The server's idempotency-key alphabet: 1..128 chars of `[A-Za-z0-9._:-]`. */
    private fun isValidKey(key: String): Boolean =
        key.length in 1..MAX_KEY_LENGTH &&
            key.all { c -> c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' || c in "._:-" }

    /** Raw text kept for a corrupt line, so the failed list stays readable. */
    private fun truncate(raw: String): String = raw.take(MAX_FAILED_TEXT_LENGTH)
}

/**
 * The offline outbox for the German web app (epic #835, task 03): every write
 * the phone owes the server, as JSON at `<dir>/german_web_outbox.json`
 * (`noBackupFilesDir` in the app; a temporary folder in tests). Entries keep
 * FIFO order, writes are atomic (temp file + rename, the [WordsStore.saveData]
 * pattern) and every operation runs under one process-wide lock, the
 * `WordsStore.LOCK` pattern, because the app builds a new outbox per call site.
 *
 * The queue is generic (method/path/body): task 04 queues
 * `POST /api/german/review/:wordId` with it the same way.
 */
class GermanWebOutbox(private val dir: File) {

    /** Injected time source, so tests control enqueue and retry timing. */
    internal var clockSupplier: () -> Long = { System.currentTimeMillis() }

    /** Appends one write to the queue, due immediately. The key is minted here, once. */
    fun enqueue(method: String, path: String, bodyJson: String?): GermanOutboxEntry = synchronized(LOCK) {
        val now = clockSupplier()
        val entry = GermanOutboxEntry(
            idempotencyKey = KEY_PREFIX + UUID.randomUUID(),
            method = method,
            path = path,
            bodyJson = bodyJson,
            createdAt = now,
            attempts = 0,
            nextAttemptAt = now,
        )
        val state = load()
        save(state.copy(entries = state.entries + entry, failed = state.failed))
        entry
    }

    /** The queue in FIFO order. */
    fun entries(): List<GermanOutboxEntry> = synchronized(LOCK) { load().entries }

    /** How many writes are still owed to the server (waiting or deferred). */
    fun pendingCount(): Int = synchronized(LOCK) { load().entries.size }

    /** The dropped entries that will never succeed, newest last, capped at [MAX_FAILED]. */
    fun failed(): List<GermanOutboxFailedEntry> = synchronized(LOCK) { load().failed }

    fun failedCount(): Int = synchronized(LOCK) { load().failed.size }

    /** The entries whose backoff has elapsed, in FIFO order. */
    fun due(now: Long = clockSupplier()): List<GermanOutboxEntry> = synchronized(LOCK) {
        load().entries.filter { it.nextAttemptAt <= now }
    }

    /** Removes a synced entry. False when the key is unknown (already removed). */
    fun remove(idempotencyKey: String): Boolean = synchronized(LOCK) {
        mutate { state ->
            if (state.entries.none { it.idempotencyKey == idempotencyKey }) {
                null
            } else {
                state.copy(entries = state.entries.filterNot { it.idempotencyKey == idempotencyKey })
            }
        }
    }

    /**
     * Drops an entry that can never succeed to the failed list, so it is not
     * silently lost. Returns the recorded failure, or null for an unknown key.
     */
    fun dropToFailed(
        idempotencyKey: String,
        status: String,
        text: String,
        now: Long = clockSupplier(),
    ): GermanOutboxFailedEntry? = synchronized(LOCK) {
        val state = load()
        if (state.entries.none { it.idempotencyKey == idempotencyKey }) return null
        val failedEntry = GermanOutboxFailedEntry(
            text = text.take(GermanOutboxCodec.MAX_FAILED_TEXT_LENGTH),
            status = status,
            time = now,
        )
        save(
            Contents(
                entries = state.entries.filterNot { it.idempotencyKey == idempotencyKey },
                failed = capFailed(state.failed + failedEntry),
            )
        )
        failedEntry
    }

    /**
     * Keeps a failed entry for the next run, one attempt later and not before
     * [delaySeconds] from [now]. The key and the stored body are left untouched.
     */
    fun retryIn(idempotencyKey: String, delaySeconds: Long, now: Long = clockSupplier()): Boolean = synchronized(LOCK) {
        mutate { state ->
            val index = state.entries.indexOfFirst { it.idempotencyKey == idempotencyKey }
            if (index == -1) {
                null
            } else {
                val deferred = state.entries[index].let {
                    it.copy(attempts = it.attempts + 1, nextAttemptAt = now + delaySeconds * MILLIS_PER_SECOND)
                }
                state.copy(entries = state.entries.toMutableList().also { it[index] = deferred })
            }
        }
    }

    private data class Contents(
        val entries: List<GermanOutboxEntry>,
        val failed: List<GermanOutboxFailedEntry>,
    )

    /**
     * Reads the file (and, when the decoder had to skip corrupt lines, writes
     * the cleaned state back so they are recorded as failures, not re-skipped
     * on every read). A missing file is an empty queue, never an error.
     */
    private fun load(): Contents {
        if (!file().exists()) return Contents(emptyList(), emptyList())
        val decoded = try {
            GermanOutboxCodec.decode(file().readText())
        } catch (e: Exception) {
            return Contents(emptyList(), emptyList())
        }
        if (decoded.corruptRaw.isEmpty()) return Contents(decoded.entries, decoded.failed)
        val failed = capFailed(
            decoded.failed + decoded.corruptRaw.map {
                GermanOutboxFailedEntry(text = it, status = STATUS_CORRUPT, time = clockSupplier())
            }
        )
        val contents = Contents(decoded.entries, failed)
        try {
            save(contents)
        } catch (e: Exception) {
            // The in-memory state is still the cleaned one; the next mutation retries the write-back.
        }
        return contents
    }

    /** Keeps the newest [MAX_FAILED] failures (stable for equal times, so appended order wins). */
    private fun capFailed(failed: List<GermanOutboxFailedEntry>): List<GermanOutboxFailedEntry> =
        failed.sortedBy { it.time }.takeLast(MAX_FAILED)

    private inline fun mutate(transform: (Contents) -> Contents?): Boolean {
        val state = load()
        val next = transform(state) ?: return false
        save(next)
        return true
    }

    private fun save(contents: Contents) {
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val tempFile = File(dir, "$FILE_NAME.tmp")
        tempFile.writeText(GermanOutboxCodec.encode(contents.entries, contents.failed))
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
        const val FILE_NAME = "german_web_outbox.json"
        const val KEY_PREFIX = "ola-"

        /** The failed list is a bounded record, not a second queue. */
        const val MAX_FAILED = 50
        const val STATUS_CORRUPT = "corrupt"
        private const val MILLIS_PER_SECOND = 1_000L

        /** One lock for every instance, the `WordsStore.LOCK` pattern (new store per call site). */
        private val LOCK = Any()
    }
}

/** org.json parses small numbers as Integer and large ones as Long; both are accepted here. */
private fun JSONObject.optEpochMillis(name: String): Long? = when (val value = opt(name)) {
    is Long -> value
    is Int -> value.toLong()
    else -> null
}
