package app.olauncher.helper

import java.time.LocalDate
import java.time.ZonedDateTime

/** What the Add note sheet holds before Save. [showFrom] is the optional "Remind me on" date; [lists] are list ids. */
data class NoteDraft(
    val title: String = "",
    val body: String = "",
    val checklist: Boolean = false,
    val showFrom: LocalDate? = null,
    val pinned: Boolean = false,
    val lists: List<String> = emptyList(),
)

/** Outcome of [NoteDraftRules.add]. */
sealed class DraftResult {
    /** [notes] is the new full list; [note] is the one that was added. */
    data class Added(val notes: List<Note>, val note: Note) : DraftResult()
    /** Nothing to save: no title and no body. */
    object Empty : DraftResult()
    /** The title ended in a date-shaped word that is not a real date. */
    data class BadDate(val token: String) : DraftResult()
}

/** Context-free rules of the Add note sheet. */
object NoteDraftRules {
    const val MAX_BODY_CHARS = 1000
    private const val ITEM_SEPARATOR = ", "

    /** Body lines as checklist item texts: one per non-blank line, trimmed, cleaned, at most [Notes.MAX_ITEMS]. */
    fun itemTexts(body: String): List<String> =
        body.lines().map { Notes.clean(it) }.filter { it.isNotEmpty() }.take(Notes.MAX_ITEMS)

    /**
     * What the body field shows after the Checklist switch changes. On: one item per line (a single
     * line with commas is split at the commas). Off: the lines are joined back with ", ".
     */
    fun switchChecklist(body: String, on: Boolean): String {
        val lines = body.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return if (on) {
            val items = if (lines.size == 1) lines[0].split(',').map { it.trim() }.filter { it.isNotEmpty() } else lines
            items.joinToString("\n")
        } else {
            lines.joinToString(ITEM_SEPARATOR)
        }
    }

    /** Body text kept for a plain note: lines trimmed at the end, blank edges dropped, cut to [MAX_BODY_CHARS]. */
    fun cleanBody(body: String): String =
        body.lines().joinToString("\n") { it.trimEnd() }.trim().take(MAX_BODY_CHARS).trim()

    /** True when Cancel should ask first: something is typed in the title or the body. */
    fun hasTypedText(draft: NoteDraft): Boolean = draft.title.isNotBlank() || draft.body.isNotBlank()

    /**
     * Adds the note [draft] describes. A title alone is enough. The title keeps the typed shortcuts: a
     * trailing date word sets the date (unless the date row already did) and `[] Shopping: milk, bread`
     * makes a checklist (body lines are then added as more items). With the Checklist switch on, body lines
     * become the items and the title may be empty. Otherwise the body stays as the note's body, and when the
     * title is empty the first body line becomes the title.
     */
    fun add(notes: List<Note>, draft: NoteDraft, now: Long, zoned: ZonedDateTime): DraftResult {
        val parsed = Notes.parse(draft.title, zoned)
        val ok = when (parsed) {
            is Notes.Parsed.Error -> return DraftResult.BadDate(parsed.token)
            is Notes.Parsed.Ok -> parsed
        }
        val date = draft.showFrom ?: ok.showFrom
        val shortcut = Notes.parseChecklist(ok.text)
        val bodyItems = itemTexts(draft.body)
        val id = (notes.maxOfOrNull { it.id } ?: 0L) + 1L
        val base = when {
            shortcut != null ->
                Note(id, shortcut.first, now, items = (shortcut.second + bodyItems.map { ChecklistItem(it) }).take(Notes.MAX_ITEMS))
            draft.checklist && bodyItems.isNotEmpty() ->
                Note(id, ok.text, now, items = bodyItems.map { ChecklistItem(it) })
            else -> {
                val body = cleanBody(draft.body)
                if (ok.text.isNotEmpty()) {
                    Note(id, ok.text, now, body = body)
                } else {
                    val lines = body.lines()
                    val title = Notes.clean(lines.first())
                    if (title.isEmpty()) return DraftResult.Empty
                    Note(id, title, now, body = lines.drop(1).joinToString("\n").trim())
                }
            }
        }
        val note = base.copy(showFrom = date?.toEpochDay(), pinned = draft.pinned, lists = draft.lists.distinct())
        return DraftResult.Added(Notes.prune(notes + note), note)
    }
}
