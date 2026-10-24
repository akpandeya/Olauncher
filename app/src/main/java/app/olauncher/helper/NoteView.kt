package app.olauncher.helper

import java.time.LocalDate

/**
 * Context-free rules of the note view and the Notes screen. Every function returns a new [Note]
 * (the same instance when nothing changed, so the "edited" time only moves on a real change).
 */
object NoteViewRules {

    /** The Notes screen shows a "Due" chip once the note's date is today or past; before that only the plain date. */
    fun isDue(note: Note, today: LocalDate): Boolean = note.showFrom?.let { it <= today.toEpochDay() } == true

    /** A plain note is finished from the note view; a checklist is finished by ticking its items. */
    fun canMarkDone(note: Note): Boolean = note.items.isEmpty()

    /** The title cannot be emptied on a plain note, so a blank title keeps the old one. A checklist title may be empty. */
    fun withTitle(note: Note, text: String, now: Long): Note {
        val title = Notes.clean(text)
        if (title == note.text || (title.isEmpty() && note.items.isEmpty())) return note
        return note.copy(text = title, edited = now)
    }

    fun withBody(note: Note, body: String, now: Long): Note {
        val cleaned = NoteDraftRules.cleanBody(body)
        return if (cleaned == note.body) note else note.copy(body = cleaned, edited = now)
    }

    /**
     * Sets the text of item [index]. A blank text removes the item, except the last one of an untitled
     * checklist (the note would be empty). An unknown index changes nothing.
     */
    fun withItemText(note: Note, index: Int, text: String, now: Long): Note {
        if (index !in note.items.indices) return note
        val cleaned = Notes.clean(text)
        if (cleaned.isEmpty()) {
            if (note.items.size == 1 && note.text.isEmpty()) return note
            return note.copy(items = note.items.filterIndexed { i, _ -> i != index }, edited = now)
        }
        if (cleaned == note.items[index].text) return note
        return note.copy(items = note.items.mapIndexed { i, item -> if (i == index) item.copy(text = cleaned) else item }, edited = now)
    }

    /** Adds an unticked item at the end. Blank text, or a list already at [Notes.MAX_ITEMS], changes nothing. */
    fun withNewItem(note: Note, text: String, now: Long): Note {
        val cleaned = Notes.clean(text)
        if (cleaned.isEmpty() || note.items.size >= Notes.MAX_ITEMS) return note
        return note.copy(items = note.items + ChecklistItem(cleaned), edited = now)
    }

    fun withShowFrom(note: Note, date: LocalDate?, now: Long): Note {
        val day = date?.toEpochDay()
        return if (day == note.showFrom) note else note.copy(showFrom = day, edited = now)
    }

    /** Ticks or unticks list [listId] on the note (a note can sit in several lists). */
    fun withListToggled(note: Note, listId: String, now: Long): Note =
        note.copy(lists = if (listId in note.lists) note.lists - listId else note.lists + listId, edited = now)

    /** "Move to list": the note ends up in [listId] only, or in no list when null. */
    fun withMovedTo(note: Note, listId: String?, now: Long): Note {
        val lists = listOfNotNull(listId)
        return if (lists == note.lists) note else note.copy(lists = lists, edited = now)
    }

    /** The note's list ids that still exist in [knownIds], in the note's own order. */
    fun liveLists(note: Note, knownIds: Set<String>): List<String> = note.lists.filter { it in knownIds }

    /** The first line of a plain note's body for a Notes screen row; null when there is no body. */
    fun bodyPreview(note: Note): String? = note.body.lines().firstOrNull { it.isNotBlank() }?.trim()
}
