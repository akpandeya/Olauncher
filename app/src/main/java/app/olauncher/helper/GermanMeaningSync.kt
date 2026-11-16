package app.olauncher.helper

import android.content.Context
import app.olauncher.data.store.GermanWordIdMap
import app.olauncher.data.store.GermanWebOutbox
import app.olauncher.data.store.WordsStore
import app.olauncher.data.store.Word
import org.json.JSONObject

/**
 * The second half of the capture sync: the word reaches the server the moment
 * it is captured ([GermanWordSync]), but its Wiktionary meaning is only
 * resolved seconds later by the WordLookupWorker — a local `WordsStore.update`
 * the first sync never sees. This object closes that gap: once the outbox
 * replay has recorded which server row a capture created ([GermanWordIdMap]),
 * a newly resolved meaning is queued as one `PATCH /api/german/words/<id>`.
 *
 * Both orderings are covered: a meaning that resolves before the POST replays
 * queues nothing (no server id yet) and is pushed by the replay's
 * `onWordCreated` catch-up; a meaning that resolves after queues the PATCH
 * directly ([push]). A word whose POST never succeeded (offline for good,
 * dropped to the failed list, signed out) stays local-only — the backup
 * import carries its meaning instead.
 *
 * Context-free core ([patchBody], [patchPath], [queuePatch], the store-taking
 * [push]/[onWordCreated]) is unit-tested; the Context glue only wires the
 * app's directories and the sync worker, the GermanWordSync pattern.
 */
object GermanMeaningSync {

    /** The server's field caps (apps/backend/src/routes/germanWords.js): never queue a 400. */
    const val MEANING_MAX = 1_000
    const val SOURCE_MAX = 200

    /**
     * The `PATCH /words/<id>` body for one resolved meaning: `meaning` always
     * (the caller only queues with a non-blank one), `meaningSource` only when
     * non-blank, both truncated to the server's field caps so the write can
     * never be rejected for size.
     */
    fun patchBody(word: Word): String {
        val body = JSONObject().put("meaning", word.meaning.take(MEANING_MAX))
        if (word.meaningSource.isNotBlank()) {
            body.put("meaningSource", word.meaningSource.take(SOURCE_MAX))
        }
        return body.toString()
    }

    /** The PATCH path for one server word id: the capture POST path plus the id. */
    fun patchPath(serverId: String): String = "${GermanWordSync.WORDS_PATH}/$serverId"

    /**
     * Appends the meaning write for [word] to [outbox]. The caller checks the
     * gate (non-blank meaning, known server id); returns the queued entry.
     */
    fun queuePatch(outbox: GermanWebOutbox, serverId: String, word: Word) =
        outbox.enqueue("PATCH", patchPath(serverId), patchBody(word))

    /**
     * Queues the meaning write for [word] when it has one and its server row is
     * known; otherwise does nothing (the catch-up covers it). True when a PATCH
     * was queued. Call after any local meaning update outside the replay — the
     * WordLookupWorker and the lookup activity's save-with-meaning.
     */
    fun push(idMap: GermanWordIdMap, outbox: GermanWebOutbox, word: Word): Boolean {
        if (word.meaning.isBlank()) return false
        val serverId = idMap.serverIdFor(word.id) ?: return false
        queuePatch(outbox, serverId, word)
        return true
    }

    /**
     * The replay's `onWordCreated` hook: record the mapping, then catch up when
     * the meaning resolved while the capture POST was still unsent — the race
     * the other way round. True when a PATCH was queued.
     */
    fun onWordCreated(
        idMap: GermanWordIdMap,
        outbox: GermanWebOutbox,
        store: WordsStore,
        localId: Long,
        serverId: String,
    ): Boolean {
        idMap.put(localId, serverId)
        val word = store.get(localId) ?: return false
        if (word.meaning.isBlank()) return false
        queuePatch(outbox, serverId, word)
        return true
    }

    // --- Context glue -----------------------------------------------------------------

    /** The [push] core over the app's directories, plus the sync worker schedule. */
    fun push(context: Context, word: Word) {
        if (push(GermanWordIdMap(context.noBackupFilesDir), GermanWebOutbox(context.noBackupFilesDir), word)) {
            GermanSyncWorker.schedule(context)
        }
    }

    /** The [onWordCreated] core over the app's directories, plus the sync worker schedule. */
    fun onWordCreated(context: Context, localId: Long, serverId: String) {
        val queued = onWordCreated(
            idMap = GermanWordIdMap(context.noBackupFilesDir),
            outbox = GermanWebOutbox(context.noBackupFilesDir),
            store = WordsStore(context),
            localId = localId,
            serverId = serverId,
        )
        if (queued) {
            GermanSyncWorker.schedule(context)
        }
    }
}
