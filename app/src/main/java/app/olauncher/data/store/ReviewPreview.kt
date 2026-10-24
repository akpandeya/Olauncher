package app.olauncher.data.store

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What a grade button shows under its name, before the user taps it. Android-free. */
object ReviewPreview {

    sealed interface Interval {
        /** "Again tomorrow": the word drops back to box 1. */
        data object AgainTomorrow : Interval

        /** "Same box": the box stays, the word returns sooner. */
        data object SameBox : Interval

        /** "Box 3 · in 4 days": the word moves up to [box] and is due in [days]. */
        data class Promoted(val box: Int, val days: Long) : Interval

        /** A knew-it on the last box: the word is finished. */
        data object Learned : Interval
    }

    /** The interval [result] would give [word] today, computed with the real grading rules. */
    fun interval(word: Word, result: String, today: Long): Interval {
        val updated = ReviewSessionLogic.grade(word, result, today)
        return when (result) {
            ReviewEvent.Result.AGAIN -> Interval.AgainTomorrow
            ReviewEvent.Result.ALMOST -> Interval.SameBox
            else -> if (word.box >= Leitner.learnedBox) {
                Interval.Learned
            } else {
                Interval.Promoted(updated.box, updated.dueDay - today)
            }
        }
    }
}

/** The "Box 2 · next review Mon 26 Oct · 6 reviews" line on the edit screen. Android-free. */
object WordProgress {

    data class Progress(
        val box: Int,
        val learned: Boolean,
        val nextReviewDay: Long,
        val reviewCount: Int,
    )

    fun of(word: Word, events: List<ReviewEvent>): Progress = Progress(
        box = word.box,
        learned = word.learnedAt != null,
        nextReviewDay = word.dueDay,
        reviewCount = events.count { it.wordId == word.id },
    )

    /** Short weekday and date for [epochDay], in [locale]: "Mon 26 Oct". */
    fun formatDay(epochDay: Long, locale: Locale): String =
        DateTimeFormatter.ofPattern("EEE d MMM", locale).format(LocalDate.ofEpochDay(epochDay))
}

/** Counts shown on the word lists screen. Android-free. */
object WordListsSummary {

    fun dueCount(words: List<Word>, today: Long): Int =
        words.count { it.learnedAt == null && it.dueDay <= today }

    fun membersOf(words: List<Word>, listId: String): List<Word> =
        words.filter { listId in it.listIds }
}
