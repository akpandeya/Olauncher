package app.olauncher.data.store

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONArray
import org.json.JSONObject

/**
 * One word of the server's due set (epic #835, task 04), as cached on the
 * phone for the pause screen. The [id] is the server's text id (a UUID
 * string), never a local `Word` Long id — the two id spaces never mix.
 */
data class GermanDueWord(
    /** The server's text id (`gen_random_uuid()::text`); never blank. */
    val id: String,
    val text: String,
    val context: String = "",
    val meaning: String = "",
    /** The server's Leitner box (1..7). */
    val box: Int,
    /** Days since the Unix epoch in the learner's local date, like `Word.dueDay`. */
    val dueDay: Long,
)

/**
 * The whole cached snapshot: what the last successful refresh read from
 * `GET /review/due` and `GET /summary`, plus every word rated locally since
 * ([ratedIds]) — a rated word must not come back on the pause screen before
 * the next refresh replaces the whole snapshot.
 */
data class GermanDueCacheState(
    /** The user the fetch was made for; a cache for another user reads as empty. */
    val username: String,
    /** Epoch millis of the fetch; a cache older than an hour is refreshed. */
    val fetchedAt: Long,
    /** The `today` day number the fetch was made with. */
    val fetchedDay: Long,
    /** The summary's own due count for [fetchedDay] (task 05 reads it). */
    val dueCount: Int,
    val words: List<GermanDueWord>,
    /** Server ids rated on the phone since the fetch, oldest first. */
    val ratedIds: List<String> = emptyList(),
)

/**
 * Encodes and decodes the cache file, Context-free and unit-tested.
 * Complete-or-nothing: a cache missing any field, or with any word the
 * [GermanDueCacheCodec.decodeWord] rules reject, decodes as null — the
 * caller treats that as an empty cache, never a half-parsed one.
 */
object GermanDueCacheCodec {

    const val CURRENT_VERSION = 1

    /** The same strict word rules the API response parser applies. */
    fun decodeWord(obj: JSONObject): GermanDueWord? {
        val id = obj.opt("id") as? String ?: return null
        val text = obj.opt("text") as? String ?: return null
        if (id.isBlank() || text.isBlank()) return null
        val context = obj.optionalString("context") ?: return null
        val meaning = obj.optionalString("meaning") ?: return null
        val box = obj.optSmallInt("box") ?: return null
        if (box !in 1..7) return null
        val dueDay = obj.optDayNumber("dueDay") ?: return null
        return GermanDueWord(id = id, text = text, context = context, meaning = meaning, box = box, dueDay = dueDay)
    }

    fun decode(raw: String?): GermanDueCacheState? {
        if (raw.isNullOrBlank()) return null
        val root = try {
            JSONObject(raw.trim())
        } catch (e: Exception) {
            return null
        }
        if (root.optSmallInt("version") != CURRENT_VERSION) return null
        val username = root.opt("username") as? String ?: return null
        if (username.isBlank()) return null
        val fetchedAt = root.optDayNumber("fetchedAt") ?: return null
        val fetchedDay = root.optDayNumber("fetchedDay") ?: return null
        val dueCount = root.optSmallInt("dueCount") ?: return null
        if (dueCount < 0) return null
        val wordsArray = root.optJSONArray("words") ?: return null
        val words = mutableListOf<GermanDueWord>()
        for (i in 0 until wordsArray.length()) {
            val word = (wordsArray.opt(i) as? JSONObject)?.let(::decodeWord) ?: return null
            words += word
        }
        val ratedIds = mutableListOf<String>()
        root.optJSONArray("ratedIds")?.let { array ->
            for (i in 0 until array.length()) {
                ratedIds += array.opt(i) as? String ?: return null
            }
        }
        return GermanDueCacheState(
            username = username,
            fetchedAt = fetchedAt,
            fetchedDay = fetchedDay,
            dueCount = dueCount,
            words = words,
            ratedIds = ratedIds,
        )
    }

    fun encode(state: GermanDueCacheState): String {
        val words = JSONArray()
        for (word in state.words) {
            words.put(
                JSONObject()
                    .put("id", word.id)
                    .put("text", word.text)
                    .put("context", word.context)
                    .put("meaning", word.meaning)
                    .put("box", word.box)
                    .put("dueDay", word.dueDay)
            )
        }
        return JSONObject()
            .put("version", CURRENT_VERSION)
            .put("username", state.username)
            .put("fetchedAt", state.fetchedAt)
            .put("fetchedDay", state.fetchedDay)
            .put("dueCount", state.dueCount)
            .put("words", words)
            .put("ratedIds", JSONArray(state.ratedIds))
            .toString(2)
    }
}

/**
 * The launcher's local copy of the server's due set (epic #835, task 04),
 * as JSON at `<dir>/german_due_words.json` (`noBackupFilesDir` in the app, a
 * temporary folder in tests). Context-free, atomic writes (temp file +
 * rename, the [GermanWebOutbox] pattern), every operation under one
 * process-wide lock because the app builds a new cache per call site.
 *
 * A cache whose [username] differs from the signed-in user reads as empty:
 * `read` only answers for the user it was fetched for. Reading never
 * touches the network, so the pause screen works in airplane mode.
 */
class GermanDueCache(private val dir: File) {

    /** The cached snapshot for [username], or null when missing, corrupt or another user's. */
    fun read(username: String): GermanDueCacheState? = synchronized(LOCK) {
        load()?.takeIf { it.username == username }
    }

    /**
     * Replaces the whole snapshot with a successful fetch. Local ratings reset
     * with it, except [stillRatedIds]: ratings still queued in the outbox, which
     * the server has not applied yet, so the fetch still lists them as due.
     */
    fun save(
        username: String,
        fetchedAt: Long,
        fetchedDay: Long,
        dueCount: Int,
        words: List<GermanDueWord>,
        stillRatedIds: Collection<String> = emptyList(),
    ) {
        if (username.isBlank()) return
        val fetchedIds = words.mapTo(HashSet()) { it.id }
        synchronized(LOCK) {
            store(
                GermanDueCacheState(
                    username = username,
                    fetchedAt = fetchedAt,
                    fetchedDay = fetchedDay,
                    dueCount = dueCount,
                    words = words,
                    ratedIds = stillRatedIds.filter { it in fetchedIds }.distinct(),
                )
            )
        }
    }

    /** The cached words with `dueDay <= today`, minus the ones rated locally since the fetch. */
    fun dueWords(username: String, today: Long): List<GermanDueWord> {
        val state = read(username) ?: return emptyList()
        val rated = state.ratedIds.toHashSet()
        return state.words.filter { it.dueDay <= today && it.id !in rated }
    }

    /** Records one rating so the word does not come back before the next refresh. */
    fun markRated(username: String, wordId: String): Boolean {
        if (wordId.isBlank()) return false
        return synchronized(LOCK) {
            val state = load()?.takeIf { it.username == username } ?: return false
            if (wordId in state.ratedIds) return true
            store(state.copy(ratedIds = state.ratedIds + wordId))
            true
        }
    }

    /**
     * The due count for [today]: the summary's own count while the snapshot
     * is from today (minus the words rated locally since — each rating
     * removes exactly one due word), and the cached words themselves once
     * the fetch is from an earlier day (then only they are known due).
     */
    fun dueCount(username: String, today: Long): Int {
        val state = read(username) ?: return 0
        return if (state.fetchedDay == today) {
            (state.dueCount - state.ratedIds.size).coerceAtLeast(0)
        } else {
            dueWords(username, today).size
        }
    }

    /** When the current user's snapshot was fetched; null when there is none to reuse. */
    fun fetchedAt(username: String): Long? = read(username)?.fetchedAt

    fun clear() = synchronized(LOCK) {
        file().delete()
    }

    private fun load(): GermanDueCacheState? {
        val file = file()
        if (!file.exists()) return null
        return try {
            GermanDueCacheCodec.decode(file.readText())
        } catch (e: Exception) {
            null
        }
    }

    private fun store(state: GermanDueCacheState) {
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val tempFile = File(dir, "$FILE_NAME.tmp")
        tempFile.writeText(GermanDueCacheCodec.encode(state))
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
        const val FILE_NAME = "german_due_words.json"

        /** One lock for every instance, the `WordsStore.LOCK` pattern (new cache per call site). */
        private val LOCK = Any()
    }
}

/** A string field: "" when absent, null when present with the wrong type. */
private fun JSONObject.optionalString(name: String): String? =
    if (has(name)) opt(name) as? String else ""

/** An integer field that fits an Int (org.json parses small numbers as Int, large as Long). */
private fun JSONObject.optSmallInt(name: String): Int? = when (val value = opt(name)) {
    is Int -> value
    is Long -> value.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()
    else -> null
}

/** A day number or epoch-millis field: Int or Long, both accepted, as a Long. */
private fun JSONObject.optDayNumber(name: String): Long? = when (val value = opt(name)) {
    is Int -> value.toLong()
    is Long -> value
    else -> null
}
