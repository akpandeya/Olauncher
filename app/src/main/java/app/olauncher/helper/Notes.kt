package app.olauncher.helper

import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * One note from `:n`. [created] is epoch millis; [id] is unique within the list.
 * [showFrom] is a local epoch day: the note shows on home from that day on; null means always.
 * [pinned] notes come first on the card and the home line.
 * [edited] is epoch millis of the last tick or pin change; null when never changed.
 * A checklist note has [items]; its [text] is then the title and may be empty.
 */
data class Note(
    val id: Long,
    val text: String,
    val created: Long,
    val done: Boolean = false,
    val showFrom: Long? = null,
    val pinned: Boolean = false,
    val items: List<ChecklistItem> = emptyList(),
    val edited: Long? = null,
)

/** One tickable line of a checklist note. */
data class ChecklistItem(val text: String, val checked: Boolean = false)

/** Pure list logic for notes; every function returns a new list. */
object Notes {
    const val MAX_TEXT_CHARS = 200
    const val SEARCH_MIN_NOTES = 5
    const val MAX_ITEMS = 20
    private const val CHECKLIST_PREFIX = "[]"
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

    /** What the card shows for [Note.edited]: [today], [yesterday] or the date; null when never edited. */
    fun editedLabel(note: Note, today: LocalDate, zone: ZoneId, format: EditedFormat): String? {
        val millis = note.edited ?: return null
        val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        return when (day) {
            today -> format.today
            today.minusDays(1) -> format.yesterday
            else -> String.format(Locale.ROOT, format.on, dateLabel(day))
        }
    }

    /** Wording for [editedLabel]; [on] has one %1$s for the date. */
    data class EditedFormat(val today: String, val yesterday: String, val on: String)

    fun encode(notes: List<Note>): String {
        val array = JSONArray()
        for (n in notes) {
            array.put(
                JSONObject().put("id", n.id).put("text", n.text).put("created", n.created).put("done", n.done).apply {
                    n.showFrom?.let { put("showFrom", it) }
                    if (n.pinned) put("pinned", true)
                    n.edited?.let { put("edited", it) }
                    if (n.items.isNotEmpty()) {
                        put("items", JSONArray().also { arr ->
                            n.items.forEach { arr.put(JSONObject().put("text", it.text).put("checked", it.checked)) }
                        })
                    }
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
                val items = decodeItems(o.optJSONArray("items"))
                if ((text.isEmpty() && items.isEmpty()) || !o.has("id")) continue
                val id = o.optLong("id", -1L)
                if (!seen.add(id)) continue
                result.add(Note(id, text, o.optLong("created", 0L), o.optBoolean("done", false), if (o.has("showFrom")) o.optLong("showFrom") else null, o.optBoolean("pinned", false), items, if (o.has("edited")) o.optLong("edited") else null))
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun decodeItems(array: JSONArray?): List<ChecklistItem> {
        if (array == null) return emptyList()
        val result = ArrayList<ChecklistItem>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: continue
            val text = o.optString("text", "").trim()
            if (text.isNotEmpty()) result.add(ChecklistItem(text, o.optBoolean("checked", false)))
        }
        return result
    }

    /**
     * Reads `[] Shopping: milk, eggs` as a title and items (`[] milk, eggs` has no title). The title
     * ends at the first `:` that is followed by a space or the end (so `10:30` is not one), only if that comes before the first `,`. Null when there is no item.
     */
    private fun parseChecklist(cleaned: String): Pair<String, List<ChecklistItem>>? {
        if (!cleaned.startsWith(CHECKLIST_PREFIX)) return null
        var rest = cleaned.removePrefix(CHECKLIST_PREFIX).trim()
        var title = ""
        val colon = Regex(":(\\s|$)").find(rest)?.range?.first ?: -1
        val comma = rest.indexOf(',')
        if (colon >= 0 && (comma < 0 || colon < comma)) {
            title = rest.substring(0, colon).trim()
            rest = rest.substring(colon + 1)
        }
        val items = rest.split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(MAX_ITEMS).map { ChecklistItem(it) }
        if (items.isEmpty()) return null
        return title to items
    }

    /** Ticks or unticks one item. A missing note or out-of-range index leaves the list as it is. */
    fun toggleItem(notes: List<Note>, noteId: Long, index: Int, now: Long): List<Note> =
        notes.map { note ->
            if (note.id != noteId || index !in note.items.indices) note
            else note.copy(items = note.items.mapIndexed { i, item -> if (i == index) item.copy(checked = !item.checked) else item }, edited = now)
        }

    /** Index of the first unticked item, or null for a plain note or a finished checklist. */
    fun nextUnchecked(note: Note): Int? = note.items.indexOfFirst { !it.checked }.takeIf { it >= 0 }

    /** A plain note's text; a checklist as `Shopping · 1/3` (or `1/3` without a title). */
    fun displayText(note: Note): String {
        if (note.items.isEmpty()) return note.text
        val progress = "${note.items.count { it.checked }}/${note.items.size}"
        return if (note.text.isEmpty()) progress else "${note.text} · $progress"
    }

    /** One-line text for a note: whitespace collapsed, cut to [MAX_TEXT_CHARS]. Empty if blank. */
    fun clean(text: String): String = text.trim().replace(WHITESPACE, " ").take(MAX_TEXT_CHARS).trim()

    /** Adds an open note. Blank text leaves the list as it is. Old done notes are dropped past a small cap. */
    fun add(notes: List<Note>, text: String, now: Long, showFrom: LocalDate? = null): List<Note> {
        val cleaned = clean(text)
        if (cleaned.isEmpty()) return notes
        val id = (notes.maxOfOrNull { it.id } ?: 0L) + 1L
        val checklist = parseChecklist(cleaned)
        val note = if (checklist != null) Note(id, checklist.first, now, showFrom = showFrom?.toEpochDay(), items = checklist.second)
        else Note(id, cleaned, now, showFrom = showFrom?.toEpochDay())
        return prune(notes + note)
    }

    fun markDone(notes: List<Note>, id: Long, done: Boolean = true): List<Note> =
        notes.map { if (it.id == id) it.copy(done = done) else it }

    /** Pins or unpins a note. A missing id leaves the list as it is. */
    fun setPinned(notes: List<Note>, id: Long, pinned: Boolean, now: Long): List<Note> =
        notes.map { if (it.id == id) it.copy(pinned = pinned, edited = now) else it }

    /** [notes] with pinned ones first; each group keeps its order. */
    fun pinnedFirst(notes: List<Note>): List<Note> = notes.filter { it.pinned } + notes.filterNot { it.pinned }

    /** The one note a single-note view shows: the first pinned one, else the newest. Null for an empty list. */
    fun featured(notes: List<Note>): Note? = notes.firstOrNull { it.pinned } ?: notes.lastOrNull()

    /**
     * The note a single-note view shows for search [matches] (a [cardList] order): the one
     * showing now ([featured] among those whose date has come), else the soonest later match.
     */
    fun featuredMatch(matches: List<Note>, today: LocalDate): Note? {
        val showingNow = matches.filter { it.showFrom == null || it.showFrom <= today.toEpochDay() }
        return featured(showingNow) ?: matches.firstOrNull()
    }

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

    /** The open-notes screen: [pinned] first, then the [rest]; both already filtered by the search. */
    data class Sections(val pinned: List<Note>, val rest: List<Note>) {
        val isEmpty: Boolean get() = pinned.isEmpty() && rest.isEmpty()
    }

    /** All open notes for the Notes screen, narrowed by [query] with [search], split into pinned and the rest. */
    fun sections(notes: List<Note>, query: String, today: LocalDate): Sections {
        val matches = search(cardList(notes, today), query)
        return Sections(matches.filter { it.pinned }, matches.filterNot { it.pinned })
    }

    /** Items ticked on a checklist note; 0 for a plain note. */
    fun doneCount(note: Note): Int = note.items.count { it.checked }

    /** Whether the Notes screen shows a "Due" chip: the note has a date. */
    fun isDated(note: Note): Boolean = note.showFrom != null

    /** "4 open · 1 pinned" from [format] (two %d: open, pinned) over the open notes. */
    fun countLine(notes: List<Note>, format: String): String {
        val open = open(notes)
        return String.format(Locale.ROOT, format, open.size, open.count { it.pinned })
    }

    /** Plain extra note lines the full-width Today card shows under the main note. */
    const val CARD_MORE_LINES = 2

    /**
     * What the full-width Today card shows, from [list] (a [cardList] order, already narrowed by any search).
     * [main] is the featured note showing now; [more] are up to [CARD_MORE_LINES] other notes showing now (list order,
     * never [main]); [dated] is the soonest note whose date is still ahead (never [main]), null when there is none.
     */
    data class CardLines(val main: Note?, val more: List<Note>, val dated: Note?)

    fun cardLines(list: List<Note>, today: LocalDate): CardLines {
        val day = today.toEpochDay()
        val main = featuredMatch(list, today)?.takeIf { it.showFrom == null || it.showFrom <= day }
        val more = list.filter { (it.showFrom == null || it.showFrom <= day) && it.id != main?.id }.take(CARD_MORE_LINES)
        val dated = list.filter { it.showFrom != null && it.showFrom > day && it.id != main?.id }
            .minWithOrNull(compareBy({ it.showFrom }, { it.created }, { it.id }))
        return CardLines(main, more, dated)
    }

    /** "1 of 3 done" for a checklist note from [format] (two %d: done, total); null for a plain note. */
    fun progressText(note: Note, format: String): String? =
        if (note.items.isEmpty()) null else String.format(Locale.ROOT, format, doneCount(note), note.items.size)

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
            val haystack = fold((listOf(note.text) + note.items.map { it.text }).joinToString(" "))
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
    fun setPinned(id: Long, pinned: Boolean) = write(Notes.encode(Notes.setPinned(all(), id, pinned, System.currentTimeMillis())))
    fun toggleItem(noteId: Long, index: Int) = write(Notes.encode(Notes.toggleItem(all(), noteId, index, System.currentTimeMillis())))
    fun delete(id: Long) = write(Notes.encode(Notes.delete(all(), id)))
}
