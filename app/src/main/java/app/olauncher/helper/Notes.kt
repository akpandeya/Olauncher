package app.olauncher.helper

import org.json.JSONArray
import org.json.JSONObject

/** One note from `:n`. [created] is epoch millis; [id] is unique within the list. */
data class Note(val id: Long, val text: String, val created: Long, val done: Boolean = false)

/** Pure list logic for notes; every function returns a new list. */
object Notes {
    const val MAX_TEXT_CHARS = 200
    private const val MAX_OPEN = 100
    private const val MAX_DONE = 20
    private val WHITESPACE = Regex("""\s+""")

    fun encode(notes: List<Note>): String {
        val array = JSONArray()
        for (n in notes) {
            array.put(
                JSONObject().put("id", n.id).put("text", n.text).put("created", n.created).put("done", n.done)
            )
        }
        return array.toString()
    }

    /** Corrupt or empty input gives an empty list; single bad entries are skipped. */
    fun decode(raw: String?): List<Note> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            val result = ArrayList<Note>()
            val seen = HashSet<Long>()
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                val text = o.optString("text", "").trim()
                if (text.isEmpty() || !o.has("id")) continue
                val id = o.optLong("id", -1L)
                if (!seen.add(id)) continue
                result.add(Note(id, text, o.optLong("created", 0L), o.optBoolean("done", false)))
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** One-line text for a note: whitespace collapsed, cut to [MAX_TEXT_CHARS]. Empty if blank. */
    fun clean(text: String): String = text.trim().replace(WHITESPACE, " ").take(MAX_TEXT_CHARS).trim()

    /** Adds an open note. Blank text leaves the list as it is. Old done notes are dropped past a small cap. */
    fun add(notes: List<Note>, text: String, now: Long): List<Note> {
        val cleaned = clean(text)
        if (cleaned.isEmpty()) return notes
        val id = (notes.maxOfOrNull { it.id } ?: 0L) + 1L
        return prune(notes + Note(id, cleaned, now))
    }

    fun markDone(notes: List<Note>, id: Long, done: Boolean = true): List<Note> =
        notes.map { if (it.id == id) it.copy(done = done) else it }

    fun delete(notes: List<Note>, id: Long): List<Note> = notes.filterNot { it.id == id }

    /** Open notes, oldest first (ties by id). */
    fun open(notes: List<Note>): List<Note> =
        notes.filterNot { it.done }.sortedWith(compareBy({ it.created }, { it.id }))

    /** The note shown on home: the oldest open one. */
    fun top(notes: List<Note>): Note? = open(notes).firstOrNull()

    private fun prune(notes: List<Note>): List<Note> {
        val keepDone = notes.filter { it.done }.sortedByDescending { it.created }.take(MAX_DONE).map { it.id }.toSet()
        val keepOpen = open(notes).takeLast(MAX_OPEN).map { it.id }.toSet()
        return notes.filter { if (it.done) it.id in keepDone else it.id in keepOpen }
    }
}

/** Reads and writes the notes JSON through [read] and [write] (a Prefs string in the app). */
class NotesStore(private val read: () -> String?, private val write: (String) -> Unit) {
    fun all(): List<Note> = Notes.decode(read())
    fun open(): List<Note> = Notes.open(all())
    fun top(): Note? = Notes.top(all())

    /** True if a note was saved; blank text saves nothing. */
    fun add(text: String, now: Long): Boolean {
        val before = all()
        val after = Notes.add(before, text, now)
        if (after === before) return false
        write(Notes.encode(after))
        return true
    }

    fun markDone(id: Long, done: Boolean = true) = write(Notes.encode(Notes.markDone(all(), id, done)))
    fun delete(id: Long) = write(Notes.encode(Notes.delete(all(), id)))
}
