package app.olauncher.data.store

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * One graded review, kept forever: the raw material for the study stats
 * (#453's dot grid and comeback counter) and any future sync.
 *
 * [result] is one of [Result.KNEW], [Result.ALMOST], [Result.AGAIN].
 */
data class ReviewEvent(
    val id: Long,
    val wordId: Long,
    val reviewedAt: Long,
    val result: String,
    val boxBefore: Int,
    val boxAfter: Int,
    val source: String? = null,
) {
    object Result {
        const val KNEW = "knew"
        const val ALMOST = "almost"
        const val AGAIN = "again"
    }
}

object ReviewEventsCodec {

    fun encode(events: List<ReviewEvent>): String {
        val array = JSONArray()
        for (event in events) {
            val obj = JSONObject()
                .put("id", event.id)
                .put("wordId", event.wordId)
                .put("reviewedAt", event.reviewedAt)
                .put("result", event.result)
                .put("boxBefore", event.boxBefore)
                .put("boxAfter", event.boxAfter)
            if (event.source != null) {
                obj.put("source", event.source)
            }
            array.put(obj)
        }
        return array.toString(2)
    }

    /** Tolerant decode: drops malformed entries instead of failing the whole log. */
    fun decode(raw: String?): List<ReviewEvent> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            decode(JSONArray(raw))
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun decode(array: JSONArray): List<ReviewEvent> {
        val events = mutableListOf<ReviewEvent>()
        for (i in 0 until array.length()) {
            try {
                val obj = array.getJSONObject(i)
                val id = obj.getLong("id")
                val wordId = obj.getLong("wordId")
                val reviewedAt = obj.getLong("reviewedAt")
                val result = obj.getString("result")
                val boxBefore = obj.getInt("boxBefore")
                val boxAfter = obj.getInt("boxAfter")
                val source = if (obj.has("source") && !obj.isNull("source")) {
                    obj.getString("source").takeIf { it.isNotBlank() }
                } else {
                    null
                }
                if (result in setOf(ReviewEvent.Result.KNEW, ReviewEvent.Result.ALMOST, ReviewEvent.Result.AGAIN)) {
                    events.add(ReviewEvent(id, wordId, reviewedAt, result, boxBefore, boxAfter, source))
                }
            } catch (e: Exception) {
                // Ignore individual corrupt entry
            }
        }
        return events
    }
}

/**
 * Append-only log of graded reviews, in its own file: unlike `words.json` it is
 * high-churn and lifetime-retained, so it must not rewrite the word list.
 */
class ReviewEventsStore(
    private val filesDir: File
) {
    constructor(context: Context) : this(context.filesDir)

    private val file = File(filesDir, "review_events.json")

    @Synchronized
    fun all(): List<ReviewEvent> {
        if (!file.exists()) return emptyList()
        return try {
            ReviewEventsCodec.decode(file.readText())
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Appends one event and returns the stored copy with its id assigned. */
    @Synchronized
    fun append(
        wordId: Long,
        result: String,
        boxBefore: Int,
        boxAfter: Int,
        reviewedAt: Long,
        source: String? = null,
    ): ReviewEvent {
        val events = all()
        val event = ReviewEvent(
            id = generateId(events),
            wordId = wordId,
            reviewedAt = reviewedAt,
            result = result,
            boxBefore = boxBefore,
            boxAfter = boxAfter,
            source = source,
        )
        save(events + event)
        return event
    }

    private fun save(events: List<ReviewEvent>) {
        if (!filesDir.exists()) {
            filesDir.mkdirs()
        }
        val tempFile = File(filesDir, "review_events.json.tmp")
        val json = ReviewEventsCodec.encode(events)
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

    private fun generateId(events: List<ReviewEvent>): Long {
        return (events.maxOfOrNull { it.id } ?: 0L) + 1
    }
}
