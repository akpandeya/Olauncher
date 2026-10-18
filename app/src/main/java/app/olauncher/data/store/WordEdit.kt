package app.olauncher.data.store

import java.net.URLEncoder

/** Rules for editing a saved word by hand, kept free of Android so they can be unit tested. */
object WordEdit {

    data class Outcome(val word: Word, val needsLookup: Boolean)

    /**
     * Applies the user's edit to [word].
     *
     * - A blank [newText] keeps the old text.
     * - A changed meaning becomes the user's own and is never overwritten by Wiktionary.
     * - A cleared meaning, or a new spelling with an untouched Wiktionary meaning,
     *   drops the meaning so Wiktionary can look the word up again. When lookups are
     *   off ([lookupAllowed] false) the Wiktionary meaning is kept, since nothing would refill it.
     */
    fun apply(word: Word, newText: String, newMeaning: String, lookupAllowed: Boolean = true): Outcome {
        val text = newText.trim().ifBlank { word.text }
        val meaning = newMeaning.trim()
        val textChanged = text != word.text
        val meaningChanged = meaning != word.meaning

        return when {
            meaning.isBlank() -> Outcome(
                word.copy(text = text, meaning = "", meaningSource = ""),
                needsLookup = true
            )
            meaningChanged -> Outcome(
                word.copy(text = text, meaning = meaning, meaningSource = "user"),
                needsLookup = false
            )
            textChanged && word.meaningSource != "user" && lookupAllowed -> Outcome(
                word.copy(text = text, meaning = "", meaningSource = ""),
                needsLookup = true
            )
            else -> Outcome(word.copy(text = text), needsLookup = false)
        }
    }

    /** True when another saved word (not [editedId]) already has [newText], ignoring case like [WordsStore.add]. */
    fun clashesWithOtherWord(newText: String, editedId: Long, words: List<Word>): Boolean {
        val text = newText.trim()
        if (text.isBlank()) return false
        return words.any { it.id != editedId && it.text.equals(text, ignoreCase = true) }
    }

    /** Forgets the current meaning, so the next lookup fills it from Wiktionary again. */
    fun resetMeaning(word: Word): Word = word.copy(meaning = "", meaningSource = "")

    fun wiktionaryUrl(text: String): String {
        val encoded = URLEncoder.encode(text.trim(), "UTF-8").replace("+", "_")
        return "https://en.wiktionary.org/wiki/$encoded#German"
    }
}

object WordsListLogic {

    sealed interface Status {
        data object Due : Status
        data class DueIn(val days: Long) : Status
        data object Learned : Status
    }

    fun status(word: Word, today: Long): Status = when {
        word.learnedAt != null -> Status.Learned
        word.dueDay <= today -> Status.Due
        else -> Status.DueIn(word.dueDay - today)
    }

    /** Due words first, then the ones coming up soonest, learned words last. */
    fun sort(words: List<Word>): List<Word> {
        return words.sortedWith(
            compareBy<Word> { it.learnedAt != null }
                .thenBy { it.dueDay }
                .thenByDescending { it.addedAt }
        )
    }
}
