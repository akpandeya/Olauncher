package app.olauncher.helper

import java.text.Normalizer
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * One note from `:n`. [created] is epoch millis; [id] is unique within the list.
 * [showFrom] is a local epoch day: the note shows on home from that day on; null means always.
 * [pinned] notes come first on the card and the home line.
 */
data class Note(val id: Long, val text: String, val created: Long, val done: Boolean = false, val showFrom: Long? = null, val pinned: Boolean = false)

/** Pure list logic for notes; every function returns a new list. */
object Notes {
    const val MAX_TEXT_CHARS = 200
    const val SEARCH_MIN_NOTES = 5
    private const val MAX_OPEN = 100
    private const val MAX_DONE = 20
    private val WHITESPACE = Regex("""\s+""")
    private val MARKS = Regex("""\p{Mn}+""")
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
                JSONObject().put("id", n.id).put("text", n.text).put("created", n.created).put("done", n.done).apply {
                    n.showFrom?.let { put("showFrom", it) }
                    if (n.pinned) put("pinned", true)
                }
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
                result.add(Note(id, text, o.optLong("created", 0L), o.optBoolean("done", false), if (o.has("showFrom")) o.optLong("showFrom") else null, o.optBoolean("pinned", false)))
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

    /** Pins or unpins a note. A missing id leaves the list as it is. */
    fun setPinned(notes: List<Note>, id: Long, pinned: Boolean): List<Note> =
        notes.map { if (it.id == id) it.copy(pinned = pinned) else it }

    /** [notes] with pinned ones first; each group keeps its order. */
    fun pinnedFirst(notes: List<Note>): List<Note> = notes.filter { it.pinned } + notes.filterNot { it.pinned }

    /** The one note a single-note view shows: the first pinned one, else the newest. Null for an empty list. */
    fun featured(notes: List<Note>): Note? = notes.firstOrNull { it.pinned } ?: notes.lastOrNull()

    fun delete(notes: List<Note>, id: Long): List<Note> = notes.filterNot { it.id == id }

    /** Open notes, oldest first (ties by id). */
    fun open(notes: List<Note>): List<Note> =
        notes.filterNot { it.done }.sortedWith(compareBy({ it.created }, { it.id }))

    /** Open notes whose date has come (or that have none): pinned first, then the rest, each oldest first. */
    fun visible(notes: List<Note>, today: LocalDate): List<Note> =
        pinnedFirst(open(notes)).filter { it.showFrom == null || it.showFrom <= today.toEpochDay() }

    /** The note shown on home: the first pinned visible one, else the oldest visible one. */
    fun top(notes: List<Note>, today: LocalDate): Note? = visible(notes, today).firstOrNull()

    /** How many visible notes the home line does not show: all but the top one. */
    fun moreCount(notes: List<Note>, today: LocalDate): Int = (visible(notes, today).size - 1).coerceAtLeast(0)

    /** "+2 more" for the home line, built from [format] (has one %d); null when nothing is hidden. */
    fun moreText(count: Int, format: String): String? = if (count > 0) String.format(Locale.ROOT, format, count) else null

    /**
     * All open notes for the Today card: those showing now first (pinned ones, then the rest, each oldest first), then dated
     * ones for later by date (ties oldest first).
     */
    fun cardList(notes: List<Note>, today: LocalDate): List<Note> {
        val open = open(notes)
        val now = pinnedFirst(open).filter { it.showFrom == null || it.showFrom <= today.toEpochDay() }
        val later = open.filter { it.showFrom != null && it.showFrom > today.toEpochDay() }
            .sortedWith(compareBy({ it.showFrom }, { it.created }, { it.id }))
        return now + later
    }

    /** The Today card shows its search field only once there are enough open notes to need it. */
    fun showSearch(openCount: Int): Boolean = openCount >= SEARCH_MIN_NOTES

    /**
     * Notes whose text contains every word of [query], keeping the list order. Case, accents and
     * `ß` are ignored ("cafe" finds "Café", "strasse" finds "Straße"). A blank query keeps all.
     */
    fun search(notes: List<Note>, query: String): List<Note> {
        val words = fold(query).split(WHITESPACE).filter { it.isNotEmpty() }
        if (words.isEmpty()) return notes
        return notes.filter { note ->
            val haystack = fold(note.text)
            words.all { it in haystack }
        }
    }

    private fun fold(text: String): String =
        Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(MARKS, "").replace("ß", "ss")

    private fun prune(notes: List<Note>): List<Note> {
        val keepDone = notes.filter { it.done }.sortedByDescending { it.created }.take(MAX_DONE).map { it.id }.toSet()
        val openNotes = open(notes)
        // Pinned notes are never dropped by the cap.
        val keepOpen = (openNotes.filter { it.pinned } + openNotes.filterNot { it.pinned }.takeLast(MAX_OPEN)).map { it.id }.toSet()
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
    fun setPinned(id: Long, pinned: Boolean) = write(Notes.encode(Notes.setPinned(all(), id, pinned)))
    fun delete(id: Long) = write(Notes.encode(Notes.delete(all(), id)))
}
