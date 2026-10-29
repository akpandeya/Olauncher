package app.olauncher.data.store

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * What a chat thread is about (docs/design/ask-ai-chat.md §Threads): a saved word,
 * a grammar rule, or a drill topic. [ref] is the normalized citation text (word),
 * the help-card rule id (rule, e.g. `prep-two-way`) or the `DrillType.id` (topic).
 * Subjects — not drill items — own threads, so a miss on *neben* today and on *auf*
 * next week join one rule conversation.
 */
data class AiChatSubject(
    val kind: Kind,
    val ref: String,
) {
    enum class Kind { WORD, RULE, TOPIC }
}

/**
 * One chat thread about one [AiChatSubject]. Created on first send, never by
 * opening an empty chat (design §Threads: thread existence starts at first send).
 *
 * The design doc's §Data schema has no thread id, but the thread sheet must open
 * one specific thread and "latest" must resolve by identity, so every thread
 * carries a stable random id (a UUID string) from creation — a deliberate
 * deviation from the doc.
 */
data class AiChatThread(
    val id: String,
    val subject: AiChatSubject,
    val createdAt: Long,
    val updatedAt: Long,
    val groundings: List<AiChatGrounding> = emptyList(),
    val summaries: List<AiChatSummary> = emptyList(),
    val messages: List<AiChatMessage> = emptyList(),
) {
    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}

/**
 * One piece of context shown to the learner and injected into the request. The
 * newest 3 are re-injected per request; the store keeps them all (design §Threads:
 * "Groundings are bounded too").
 */
data class AiChatGrounding(
    val id: String,
    val payload: AiChatGroundingPayload,
) {
    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}

/**
 * Typed grounding payload (docs/design/ask-ai-chat.md §Data). The JSON `kind` is
 * `word|miss|practice`; each kind carries its own fields so the grounding card
 * can never render a blank block.
 */
sealed class AiChatGroundingPayload {

    /** A saved word: citation text, meaning, saved context sentence. */
    data class Word(
        val text: String,
        val meaning: String,
        val context: String,
    ) : AiChatGroundingPayload()

    /** One wrong drill answer: prompt, wrong pick, correct answer, rule chips. */
    data class Miss(
        val prompt: String,
        val picked: String,
        val answer: String,
        val ruleId: String?,
        val chips: List<String>,
    ) : AiChatGroundingPayload()

    /** An AI practice prompt with its answer; [wordId] resolves to the thread subject. */
    data class Practice(
        val prompt: String,
        val answer: String,
        val wordId: Long,
    ) : AiChatGroundingPayload()

    companion object {
        const val KIND_WORD = "word"
        const val KIND_MISS = "miss"
        const val KIND_PRACTICE = "practice"
    }
}

/**
 * One compaction result (design §Threads: "Compaction is lossy for the model,
 * lossless for the user"). [coversUpTo] is the index in [AiChatThread.messages]
 * of the last message the summary covers, so the request split is reconstructable
 * and the expander knows which original turns to show under the summary. Only the
 * newest summary is active; older entries stay for the audit trail.
 */
data class AiChatSummary(
    val text: String,
    val coversUpTo: Int,
    val createdAt: Long,
)

/**
 * One transcript line. A failed user turn is persisted as failed *before* the
 * request fires (design §Reliability) and stays visible as "Not sent · Retry";
 * it is excluded from AI context and counts until a retry succeeds.
 * [usage] is the provider's token report for an assistant reply; [groundingId]
 * sits on the user turn that introduced the grounding.
 */
data class AiChatMessage(
    val role: Role,
    val text: String,
    val ts: Long,
    val usage: AiUsage? = null,
    val failed: Boolean = false,
    val groundingId: String? = null,
) {
    enum class Role { USER, ASSISTANT }
}

/** Pure helpers derived from thread contents; no I/O. */
object AiChatThreads {

    /**
     * Delivered turns: user messages that were sent successfully and answered by
     * an assistant reply. A failed user turn is not a turn (it never reached the
     * model), an unanswered user turn is not a turn yet, and a failed assistant
     * line does not answer anything. Summarised turns count — the summary replaces
     * them in the request only, never in the transcript (design §Threads: counts
     * never regress).
     */
    fun deliveredTurns(thread: AiChatThread): Int {
        var turns = 0
        for (i in thread.messages.indices) {
            val message = thread.messages[i]
            val reply = thread.messages.getOrNull(i + 1) ?: continue
            if (message.role == AiChatMessage.Role.USER &&
                !message.failed &&
                reply.role == AiChatMessage.Role.ASSISTANT &&
                !reply.failed
            ) {
                turns++
            }
        }
        return turns
    }

    /**
     * The thread to resume for [subject]: the newest by [AiChatThread.updatedAt].
     * A thread whose only turn failed still resolves as latest (design §Reliability:
     * the "Not sent · Retry" tail reads correctly on arrival).
     */
    fun latestThread(threads: List<AiChatThread>, subject: AiChatSubject): AiChatThread? {
        return threads.filter { it.subject == subject }.maxByOrNull { it.updatedAt }
    }
}

/**
 * Strict codec for `ai_chats.json` (docs/design/ask-ai-chat.md §Data). Unlike
 * [WordsCodec] — whose decode swallows corrupt entries — a malformed chat file is
 * reported as [AiChatDecoded.Corrupt] with a reason, never decoded to a silently
 * empty list: losing a conversation the learner wrote looks like deleted history.
 */
object AiChatCodec {

    /** Envelope format written today. */
    const val CURRENT_VERSION = 1

    /** Outcome of a strict decode: either the threads, or why the file was rejected. */
    sealed class AiChatDecoded {
        data class Decoded(val threads: List<AiChatThread>) : AiChatDecoded()
        data class Corrupt(val reason: String) : AiChatDecoded()
    }

    private class CorruptFile(val reason: String) : Exception(reason)

    /** Encodes the versioned envelope `{ "version": 1, "threads": [...] }`. */
    fun encode(threads: List<AiChatThread>): String {
        val envelope = JSONObject()
        envelope.put("version", CURRENT_VERSION)
        envelope.put("threads", encodeThreads(threads))
        return envelope.toString(2)
    }

    /**
     * Decodes strictly. An absent or blank file decodes to zero threads — that is
     * an empty store, not corruption. Everything else that does not match the
     * schema exactly is rejected: not JSON, missing `threads`, a thread missing
     * `id`/`subject`/`createdAt`, an unknown subject kind, an unknown grounding
     * kind, a payload missing a required field for its kind, an unknown role,
     * a `coversUpTo` outside the message bounds, or an optional field
     * (`messages`, `groundings`, `summaries`, `failed`, `groundingId`, `ruleId`)
     * present with the wrong type. Optional fields that are absent or JSON null
     * decode to their defaults.
     */
    fun decode(raw: String?): AiChatDecoded {
        if (raw.isNullOrBlank()) return AiChatDecoded.Decoded(emptyList())
        return try {
            val envelope = JSONObject(raw.trim())
            val threadsArray = envelope.optJSONArray("threads")
                ?: throw CorruptFile("missing threads array")
            val threads = mutableListOf<AiChatThread>()
            for (i in 0 until threadsArray.length()) {
                val obj = threadsArray.optJSONObject(i)
                    ?: throw CorruptFile("thread $i is not an object")
                threads.add(decodeThread(obj, "thread $i"))
            }
            AiChatDecoded.Decoded(threads)
        } catch (e: CorruptFile) {
            AiChatDecoded.Corrupt(e.reason)
        } catch (e: Exception) {
            AiChatDecoded.Corrupt("malformed: ${e.message}")
        }
    }

    private fun encodeThreads(threads: List<AiChatThread>): JSONArray {
        val array = JSONArray()
        for (thread in threads) {
            val obj = JSONObject()
            obj.put("id", thread.id)
            obj.put("subject", subjectToJson(thread.subject))
            obj.put("createdAt", thread.createdAt)
            obj.put("updatedAt", thread.updatedAt)
            obj.put("groundings", encodeGroundings(thread.groundings))
            obj.put("summaries", encodeSummaries(thread.summaries))
            obj.put("messages", encodeMessages(thread.messages))
            array.put(obj)
        }
        return array
    }

    private fun subjectToJson(subject: AiChatSubject): JSONObject {
        // Kind serializes as the doc's `word|rule|topic`.
        return JSONObject()
            .put("kind", subject.kind.name.lowercase())
            .put("ref", subject.ref)
    }

    private fun encodeGroundings(groundings: List<AiChatGrounding>): JSONArray {
        val array = JSONArray()
        for (grounding in groundings) {
            val obj = JSONObject()
            obj.put("id", grounding.id)
            val payload = JSONObject()
            when (val p = grounding.payload) {
                is AiChatGroundingPayload.Word -> {
                    obj.put("kind", AiChatGroundingPayload.KIND_WORD)
                    payload.put("text", p.text)
                    payload.put("meaning", p.meaning)
                    payload.put("context", p.context)
                }
                is AiChatGroundingPayload.Miss -> {
                    obj.put("kind", AiChatGroundingPayload.KIND_MISS)
                    payload.put("prompt", p.prompt)
                    payload.put("picked", p.picked)
                    payload.put("answer", p.answer)
                    if (p.ruleId != null) payload.put("ruleId", p.ruleId)
                    val chips = JSONArray()
                    for (chip in p.chips) chips.put(chip)
                    payload.put("chips", chips)
                }
                is AiChatGroundingPayload.Practice -> {
                    obj.put("kind", AiChatGroundingPayload.KIND_PRACTICE)
                    payload.put("prompt", p.prompt)
                    payload.put("answer", p.answer)
                    payload.put("wordId", p.wordId)
                }
            }
            obj.put("payload", payload)
            array.put(obj)
        }
        return array
    }

    private fun encodeSummaries(summaries: List<AiChatSummary>): JSONArray {
        val array = JSONArray()
        for (summary in summaries) {
            array.put(
                JSONObject()
                    .put("text", summary.text)
                    .put("coversUpTo", summary.coversUpTo)
                    .put("createdAt", summary.createdAt)
            )
        }
        return array
    }

    private fun encodeMessages(messages: List<AiChatMessage>): JSONArray {
        val array = JSONArray()
        for (message in messages) {
            val obj = JSONObject()
                // Role serializes as the doc's `user|assistant`.
                .put("role", message.role.name.lowercase())
                .put("text", message.text)
                .put("ts", message.ts)
            if (message.usage != null) {
                obj.put(
                    "usage",
                    JSONObject()
                        .put("inputTokens", message.usage.inputTokens)
                        .put("outputTokens", message.usage.outputTokens)
                )
            }
            if (message.failed) obj.put("failed", true)
            if (message.groundingId != null) obj.put("groundingId", message.groundingId)
            array.put(obj)
        }
        return array
    }

    private fun decodeThread(obj: JSONObject, label: String): AiChatThread {
        // Require real strings: getString would coerce numbers on Android.
        val id = obj.opt("id") as? String
        if (id.isNullOrBlank()) throw CorruptFile("$label: missing id")
        val subjectObj = obj.optJSONObject("subject")
            ?: throw CorruptFile("$label: missing subject")
        val kindRaw = subjectObj.opt("kind") as? String
            ?: throw CorruptFile("$label: subject missing kind")
        val kind = AiChatSubject.Kind.entries.firstOrNull { it.name.lowercase() == kindRaw }
            ?: throw CorruptFile("$label: unknown subject kind '$kindRaw'")
        val ref = subjectObj.opt("ref") as? String
        if (ref.isNullOrBlank()) throw CorruptFile("$label: subject missing ref")
        if (!obj.has("createdAt")) throw CorruptFile("$label: missing createdAt")
        val createdAt = obj.getLong("createdAt")
        val updatedAt = if (obj.has("updatedAt")) obj.getLong("updatedAt") else createdAt
        val messages = decodeMessages(obj, label)
        val groundings = decodeGroundings(obj, label)
        val summaries = decodeSummaries(obj, label, messages)
        return AiChatThread(
            id = id,
            subject = AiChatSubject(kind, ref),
            createdAt = createdAt,
            updatedAt = updatedAt,
            groundings = groundings,
            summaries = summaries,
            messages = messages
        )
    }

    private fun decodeMessages(obj: JSONObject, label: String): List<AiChatMessage> {
        val array = obj.optArrayOrEmpty("messages", label)
        val messages = mutableListOf<AiChatMessage>()
        for (i in 0 until array.length()) {
            val m = array.optJSONObject(i)
                ?: throw CorruptFile("$label message $i is not an object")
            val roleRaw = m.opt("role") as? String
                ?: throw CorruptFile("$label message $i: missing role")
            val role = AiChatMessage.Role.entries.firstOrNull { it.name.lowercase() == roleRaw }
                ?: throw CorruptFile("$label message $i: unknown role '$roleRaw'")
            val text = m.opt("text") as? String
                ?: throw CorruptFile("$label message $i: missing text")
            val ts = m.getLong("ts")
            val usage = m.optJSONObject("usage")?.let { u ->
                AiUsage(inputTokens = u.getInt("inputTokens"), outputTokens = u.getInt("outputTokens"))
            }
            messages.add(
                AiChatMessage(
                    role = role,
                    text = text,
                    ts = ts,
                    usage = usage,
                    failed = m.optStrictBoolean("failed", "$label message $i"),
                    groundingId = m.optStrictString("groundingId", "$label message $i")
                )
            )
        }
        return messages
    }

    private fun decodeGroundings(obj: JSONObject, label: String): List<AiChatGrounding> {
        val array = obj.optArrayOrEmpty("groundings", label)
        val groundings = mutableListOf<AiChatGrounding>()
        for (i in 0 until array.length()) {
            val g = array.optJSONObject(i)
                ?: throw CorruptFile("$label grounding $i is not an object")
            val id = g.opt("id") as? String
            if (id.isNullOrBlank()) throw CorruptFile("$label grounding $i: missing id")
            val kind = g.opt("kind") as? String
                ?: throw CorruptFile("$label grounding $i: missing kind")
            val payloadObj = g.optJSONObject("payload")
                ?: throw CorruptFile("$label grounding $i: missing payload")
            val payload = decodePayload(kind, payloadObj, "$label grounding $i")
            groundings.add(AiChatGrounding(id, payload))
        }
        return groundings
    }

    private fun decodePayload(kind: String, payload: JSONObject, label: String): AiChatGroundingPayload {
        return when (kind) {
            AiChatGroundingPayload.KIND_WORD -> AiChatGroundingPayload.Word(
                text = payload.requireString("text", label),
                meaning = payload.requireString("meaning", label),
                context = payload.requireString("context", label)
            )
            AiChatGroundingPayload.KIND_MISS -> {
                val ruleId = payload.optStrictString("ruleId", label)
                AiChatGroundingPayload.Miss(
                    prompt = payload.requireString("prompt", label),
                    picked = payload.requireString("picked", label),
                    answer = payload.requireString("answer", label),
                    ruleId = ruleId,
                    chips = payload.requireStringArray("chips", label)
                )
            }
            AiChatGroundingPayload.KIND_PRACTICE -> AiChatGroundingPayload.Practice(
                prompt = payload.requireString("prompt", label),
                answer = payload.requireString("answer", label),
                wordId = payload.getLong("wordId")
            )
            else -> throw CorruptFile("$label: unknown grounding kind '$kind'")
        }
    }

    private fun decodeSummaries(
        obj: JSONObject,
        label: String,
        messages: List<AiChatMessage>
    ): List<AiChatSummary> {
        val array = obj.optArrayOrEmpty("summaries", label)
        val summaries = mutableListOf<AiChatSummary>()
        for (i in 0 until array.length()) {
            val s = array.optJSONObject(i)
                ?: throw CorruptFile("$label summary $i is not an object")
            val text = s.opt("text") as? String
                ?: throw CorruptFile("$label summary $i: missing text")
            val coversUpTo = s.getInt("coversUpTo")
            if (coversUpTo !in messages.indices) {
                throw CorruptFile(
                    "$label summary $i: coversUpTo $coversUpTo outside messages bounds (0..<${messages.size})"
                )
            }
            summaries.add(
                AiChatSummary(
                    text = text,
                    coversUpTo = coversUpTo,
                    createdAt = s.getLong("createdAt")
                )
            )
        }
        return summaries
    }

    /** Required string field; a missing or wrong-typed field rejects the file. */
    private fun JSONObject.requireString(key: String, label: String): String {
        val value = opt(key) as? String
            ?: throw CorruptFile("$label: payload missing '$key'")
        return value
    }

    private fun JSONObject.requireStringArray(key: String, label: String): List<String> {
        val array = optJSONArray(key)
            ?: throw CorruptFile("$label: payload missing '$key'")
        return (0 until array.length()).map { entry ->
            array.opt(entry) as? String
                ?: throw CorruptFile("$label: payload '$key' entry ${entry} is not a string")
        }
    }

    /**
     * Optional array field: absent or JSON null is an empty list; a present
     * value of any other type rejects the file instead of decoding to a
     * silently empty list.
     */
    private fun JSONObject.optArrayOrEmpty(key: String, label: String): JSONArray {
        val value = opt(key) ?: return JSONArray()
        if (value === JSONObject.NULL) return JSONArray()
        return value as? JSONArray ?: throw CorruptFile("$label: '$key' is not an array")
    }

    /** Optional string: absent or JSON null is null; a present non-string rejects the file. */
    private fun JSONObject.optStrictString(key: String, label: String): String? {
        val value = opt(key) ?: return null
        if (value === JSONObject.NULL) return null
        return value as? String ?: throw CorruptFile("$label: '$key' is not a string")
    }

    /** Optional boolean: absent or JSON null is false; a present non-boolean rejects the file. */
    private fun JSONObject.optStrictBoolean(key: String, label: String): Boolean {
        val value = opt(key) ?: return false
        if (value === JSONObject.NULL) return false
        return value as? Boolean ?: throw CorruptFile("$label: '$key' is not a boolean")
    }
}
