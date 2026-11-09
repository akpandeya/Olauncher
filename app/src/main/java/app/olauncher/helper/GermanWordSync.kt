package app.olauncher.helper

import android.content.Context
import app.olauncher.data.Prefs
import app.olauncher.data.store.GermanOutboxEntry
import app.olauncher.data.store.GermanWebOutbox
import app.olauncher.data.store.GermanWebSession
import app.olauncher.data.store.GermanWebSessionState
import app.olauncher.data.store.Word
import org.json.JSONObject

/**
 * The bridge from a native capture to the German web app (epic #835, task 03):
 * the [WordsStore.onWordsCreated] hook feeds it every word the local store just
 * created, and it queues one `POST /api/german/words` per word in the outbox.
 *
 * Only words new on the phone are queued — the server does not dedupe by text,
 * so a re-capture or a context edit of an existing word stays local (a known
 * limitation). Captures made while [GermanWebSessionState.Lapsed] queue too,
 * so they sync after re-pairing; signed out means local only — words captured
 * before pairing reach the web through the backup import, not the outbox.
 */
object GermanWordSync {

    const val WORDS_PATH = "/api/german/words"

    /** Queues are only for real captures: German off or signed out means local only. */
    fun shouldQueue(germanEnabled: Boolean, state: GermanWebSessionState): Boolean =
        germanEnabled && state != GermanWebSessionState.SignedOut

    /**
     * The `POST /words` body for one newly captured word. `today` is the
     * capture day ([Word.dueDay] of a fresh word — box 1 is due the day it was
     * captured), so a retry days later still enters Leitner on the right day;
     * the outbox replays this body as-is and never recomputes it. `meaning`/
     * `meaningSource` go in only when non-blank, and `listIds` never: local
     * list ids are not server ids.
     */
    fun captureBody(word: Word): String {
        val body = JSONObject()
            .put("text", word.text)
            .put("context", word.context)
            .put("contextSource", word.contextSource)
            .put("today", word.dueDay)
        if (word.meaning.isNotBlank()) {
            body.put("meaning", word.meaning)
            body.put("meaningSource", word.meaningSource)
        }
        return body.toString()
    }

    /**
     * Context-free core: queue [words] unless the session does not queue.
     * Returns the entries that were queued, in [words] order.
     */
    fun queueCaptures(
        outbox: GermanWebOutbox,
        germanEnabled: Boolean,
        state: GermanWebSessionState,
        words: List<Word>,
    ): List<GermanOutboxEntry> {
        if (!shouldQueue(germanEnabled, state)) return emptyList()
        return words.map { word -> outbox.enqueue("POST", WORDS_PATH, captureBody(word)) }
    }

    /** The [WordsStore.onWordsCreated] hook: gate, queue, and schedule the replay worker. */
    fun enqueue(context: Context, words: List<Word>) {
        if (words.isEmpty()) return
        val session = GermanWebSession(context)
        val queued = queueCaptures(
            outbox = GermanWebOutbox(context.noBackupFilesDir),
            germanEnabled = Prefs(context).germanEnabled,
            state = session.state(),
            words = words,
        )
        if (queued.isNotEmpty()) GermanSyncWorker.schedule(context)
    }
}
