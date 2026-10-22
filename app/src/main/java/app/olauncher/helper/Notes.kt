package app.olauncher.helper

import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * One note from `:n`. [created] is epoch millis; [id] is unique within the list.
 * [showFrom] is a local epoch day: the note shows on home from that day on; null means always.
 */
data class Note(val id: Long, val text: String, val created: Long, val done: Boolean = false, val showFrom: Long? = null)

/** Pure list logic for notes; every function returns a new list. */
object Notes {
    const val MAX_TEXT_CHARS = 200
    private const val MAX_OPEN = 100
    private const val MAX_DONE = 20
    private val WHITESPACE = Regex("""\s+""")
    private val DATE_LABEL = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

    sealed class Parsed {
        /** [text] is cleaned note text; [showFrom] is the date from a trailing date token, if any. */
        data class Ok(val text: String, val showFrom: LocalDate?) : Parsed()
        /** [token] is the unreadable date text. */
        data class Error(val token: String) : Parsed()
    }

    /**
     * Splits `:n` args into text and an optional trailing date token (same grammar as `:e`).
     * The first word is always text. A date-shaped token that is not a real date is an error.
     */
    fun parse(args: String, now: ZonedDateTime): Parsed {
        val words = args.trim().split(WHITESPACE).filter { it.isNotEmpty() }
        val last = words.lastOrNull()
        if (words.size < 2 || last == null || !EventCommand.looksLikeDate(last)) return Parsed.Ok(clean(args), null)
        val date = EventCommand.parseDate(last, now.toLocalDate()) ?: return Parsed.Error(last)
        return Parsed.Ok(clean(words.dropLast(1).joinToString(" ")), date)
    }

    /** `Fri 24 Oct` for a dated note, null for an undated one. */
    fun dateLabel(note: Note): String? = note.showFrom?.let { dateLabel(LocalDate.ofEpochDay(it)) }

    fun dateLabel(date: LocalDate): String = date.format(DATE_LABEL)

    fun encode(notes: List<Note>): String {
        val array = JSONArray()
        for (n in notes) {
            array.put(
                JSONObject().put("id", n.id).put("text", n.text).put("created", n.created).put("done", n.done).apply { n.showFrom?.let { put("showFrom", it) } }
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
                result.add(Note(id, text, o.optLong("created", 0L), o.optBoolean("done", false), if (o.has("showFrom")) o.optLong("showFrom") else null))
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** One-line text for a note: whitespace collapsed, cut to [MAX_TEXT_CHARS]. Empty if blank. */
    fun clean(text: String): String = text.trim().replace(WHITESPACE, " ").take(MAX_TEXT_CHARS).trim()

    /** Adds an open note. Blank text leaves the list as it is. Old done notes are dropped past a small cap. */
    fun add(notes: List<Note>, text: String, now: Long, showFrom: LocalDate? = null): List<Note> {
        val cleaned = clean(text)
        if (cleaned.isEmpty()) return notes
        val id = (notes.maxOfOrNull { it.id } ?: 0L) + 1L
        return prune(notes + Note(id, cleaned, now, showFrom = showFrom?.toEpochDay()))
    }

    fun markDone(notes: List<Note>, id: Long, done: Boolean = true): List<Note> =
        notes.map { if (it.id == id) it.copy(done = done) else it }

    fun delete(notes: List<Note>, id: Long): List<Note> = notes.filterNot { it.id == id }

    /** Open notes, oldest first (ties by id). */
    fun open(notes: List<Note>): List<Note> =
        notes.filterNot { it.done }.sortedWith(compareBy({ it.created }, { it.id }))

    /** Open notes whose date has come (or that have none), oldest first. */
    fun visible(notes: List<Note>, today: LocalDate): List<Note> =
        open(notes).filter { it.showFrom == null || it.showFrom <= today.toEpochDay() }

    /** The note shown on home: the oldest visible one. */
    fun top(notes: List<Note>, today: LocalDate): Note? = visible(notes, today).firstOrNull()

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
    fun top(today: LocalDate): Note? = Notes.top(all(), today)

    /** True if a note was saved; blank text saves nothing. */
    fun add(text: String, now: Long, showFrom: LocalDate? = null): Boolean {
        val before = all()
        val after = Notes.add(before, text, now, showFrom)
        if (after === before) return false
        write(Notes.encode(after))
        return true
    }

    fun markDone(id: Long, done: Boolean = true) = write(Notes.encode(Notes.markDone(all(), id, done)))
    fun delete(id: Long) = write(Notes.encode(Notes.delete(all(), id)))
}
