package app.olauncher.data.store

import org.json.JSONObject

/**
 * Parses the German web app's review and summary responses (epic #835,
 * task 04), Context-free and unit-tested. Complete-or-nothing: [decodeDueWords]
 * returns null unless every element of `words` decodes — a partial due set is
 * never cached, the old cache is kept.
 */
object GermanDueApi {

    /** The due set's hard server cap (`germanReview.js` MAX_DUE_LIMIT). */
    const val DUE_LIMIT = 200

    /** `GET /api/german/review/due?today=<day>&limit=200` — oldest due first. */
    fun duePath(today: Long): String = "/api/german/review/due?today=$today&limit=$DUE_LIMIT"

    /** `GET /api/german/summary?today=<day>` — only `dueCount` is read here. */
    fun summaryPath(today: Long): String = "/api/german/summary?today=$today"

    /**
     * `{ "words": [word…] }`. A word needs a non-blank string `id`, a
     * non-blank `text`, an integer `box` in 1..7 and an integer `dueDay`
     * (the same rules as [GermanDueCacheCodec.decodeWord]). Any element
     * that fails — or a `words` that is not an array — rejects the whole
     * response as null.
     */
    fun decodeDueWords(body: String?): List<GermanDueWord>? {
        if (body.isNullOrBlank()) return null
        val root = try {
            JSONObject(body.trim())
        } catch (e: Exception) {
            return null
        }
        val wordsArray = root.optJSONArray("words") ?: return null
        val words = mutableListOf<GermanDueWord>()
        for (i in 0 until wordsArray.length()) {
            val word = (wordsArray.opt(i) as? JSONObject)?.let(GermanDueCacheCodec::decodeWord)
                ?: return null
            words += word
        }
        return words
    }

    /** `{ dueCount, … }` — only a non-negative integer `dueCount` is read; anything else is null. */
    fun decodeDueCount(body: String?): Int? {
        if (body.isNullOrBlank()) return null
        val root = try {
            JSONObject(body.trim())
        } catch (e: Exception) {
            return null
        }
        val dueCount = when (val value = root.opt("dueCount")) {
            is Int -> value
            is Long -> value.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()
            else -> null
        }
        return dueCount?.takeIf { it >= 0 }
    }
}

/** What one due-set refresh did to the cache. */
sealed class GermanDueRefreshOutcome {
    /** Both reads made it and the cache was replaced. */
    object Refreshed : GermanDueRefreshOutcome()

    /** A 401: the session is lapsed ([onUnauthorized] fired) and the cache is cleared. */
    object Unauthorized : GermanDueRefreshOutcome()

    /** A network error, a 5xx or a response that does not parse: the old cache is kept. */
    object KeptOldCache : GermanDueRefreshOutcome()
}

/**
 * One due-set refresh (epic #835, task 04), Context-free and unit-tested;
 * [GermanDueRefreshWorker] is the WorkManager wrapper around it.
 *
 * The outbox is replayed first (task 03), so a rating made offline reaches
 * the server before the due set is re-read. Then `GET /review/due` and
 * `GET /summary` are read through [GermanWebApi.send] with the stored
 * token. A 401 anywhere lapses the session and clears the cache; a network
 * error, a 5xx or a body that does not parse completely keeps the old
 * cache. The summary is auxiliary: when only it fails, the fresh due set is
 * still cached with its own size as the count.
 */
object GermanDueRefresh {

    suspend fun refresh(
        cache: GermanDueCache,
        outbox: GermanWebOutbox,
        api: GermanWebApi,
        token: String,
        username: String,
        today: Long,
        nowMillis: Long,
        onUnauthorized: () -> Unit,
    ): GermanDueRefreshOutcome {
        // A rating made offline is applied before the due set is re-read.
        val replay = GermanOutboxReplay.replayAll(outbox, api, token, onUnauthorized)
        if (replay.stopReason == GermanReplayStopReason.UNAUTHORIZED) {
            cache.clear()
            return GermanDueRefreshOutcome.Unauthorized
        }

        val dueWords = when (val due = api.send(GermanWebRequest("GET", GermanDueApi.duePath(today)), token)) {
            is GermanWebResponse.Http -> when (due.status) {
                401 -> {
                    onUnauthorized()
                    cache.clear()
                    return GermanDueRefreshOutcome.Unauthorized
                }
                in 200..299 -> GermanDueApi.decodeDueWords(due.body)
                else -> null // 5xx, a rate limit or a bad request: keep the old cache
            }
            is GermanWebResponse.NetworkFailure -> null
        } ?: return GermanDueRefreshOutcome.KeptOldCache

        // The summary only refines the stored due count (task 05 reads it);
        // a failed summary never throws away a good due set.
        var dueCount = dueWords.size
        when (val summary = api.send(GermanWebRequest("GET", GermanDueApi.summaryPath(today)), token)) {
            is GermanWebResponse.Http -> when {
                summary.status == 401 -> {
                    onUnauthorized()
                    cache.clear()
                    return GermanDueRefreshOutcome.Unauthorized
                }
                summary.status in 200..299 -> GermanDueApi.decodeDueCount(summary.body)?.let { dueCount = it }
            }
            is GermanWebResponse.NetworkFailure -> {}
        }

        cache.save(username = username, fetchedAt = nowMillis, fetchedDay = today, dueCount = dueCount, words = dueWords)
        return GermanDueRefreshOutcome.Refreshed
    }
}

/**
 * One pause-screen rating of a server word (epic #835, task 04), mapped to
 * its outbox entry: `POST /api/german/review/<id>` with `{ result, today }`.
 * The pause screen has two buttons; the API's third result ("almost") is
 * not reachable from it, and "again" is the API's "no" (Leitner: box 1, due
 * tomorrow). The idempotency key is minted by the outbox at enqueue time —
 * one per tap, never regenerated on replay, and `today` is captured here so
 * a retry days later still grades the right day.
 */
object GermanWebReview {

    const val REVIEW_PATH_PREFIX = "/api/german/review/"

    /** The API result code for a pause button: "knew" stays "knew", "again" becomes "no". */
    fun result(knewIt: Boolean): String = if (knewIt) "knew" else "no"

    /** The rating path for one server word id. */
    fun reviewPath(wordId: String): String = REVIEW_PATH_PREFIX + wordId

    /** The replay-exact body: `result` and `today`, nothing else. */
    fun ratingBody(knewIt: Boolean, today: Long): String =
        JSONObject().put("result", result(knewIt)).put("today", today).toString()

    /** Queues one rating write; each tap enqueues its own entry. */
    fun enqueueRating(
        outbox: GermanWebOutbox,
        wordId: String,
        knewIt: Boolean,
        today: Long,
    ): GermanOutboxEntry = outbox.enqueue("POST", reviewPath(wordId), ratingBody(knewIt, today))
}
