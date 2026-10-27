package app.olauncher.data.store

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Enforces that no drill hint ever reveals its answer: every hint in every drill
 * table must pass [DrillHints.revealsAnswer], word-order hints must not reveal any
 * accepted sentence, and case questions must not name the case they ask for.
 */
class DrillHintLeaksTest {

    private fun assertNoHintRevealsAnswer(items: List<DrillItem>, table: String) {
        for (item in items) {
            val hint = item.hint ?: continue
            assertFalse(
                "$table item ${item.id}: hint \"$hint\" reveals the answer \"${item.answer}\"",
                DrillHints.revealsAnswer(hint, item.answer),
            )
        }
    }

    @Test
    fun noHintInAnyDrillTableRevealsItsAnswer() {
        for (type in DrillTables.available()) {
            assertNoHintRevealsAnswer(DrillTables.items(type), type.id)
        }
    }

    @Test
    fun noHintInSeparableVerbsExtraSavedItemsRevealsItsAnswer() {
        val savedWords = listOf(
            Word(id = 1L, text = "anrufen", dueDay = 0L, addedAt = 0L),
            Word(id = 2L, text = "einkaufen", dueDay = 0L, addedAt = 0L),
        )
        val presentLookup: (String) -> String? = { text ->
            if (text.contains("anrufen")) "ruft an" else "kauft ein"
        }
        val items = DrillTables.items(DrillType.SEPARABLE_VERBS, savedWords, presentLookup = presentLookup)
        assertTrue(
            "Expected extra saved-verb items keyed by word id",
            items.any { it.id.key == "1" || it.id.key == "2" },
        )
        assertNoHintRevealsAnswer(items, "separable_verbs")
    }

    @Test
    fun noWordOrderHintRevealsTheSentence() {
        val allItems = DrillWordOrder.ENTRIES.map { it.item } + WordOrderQuiz.SAMPLE_ITEMS
        for (item in allItems) {
            val hint = item.hint ?: continue
            for (order in item.accepted) {
                assertFalse(
                    "Word-order item ${item.id}: hint \"$hint\" reveals the answer \"$order\"",
                    DrillHints.revealsAnswer(hint, order.joinToString(" ")),
                )
            }
        }
    }

    @Test
    fun caseQuestionHintsDoNotNameTheCase() {
        val caseNames = listOf("Akkusativ", "Dativ", "Genitiv")
        // Abbreviated ("mit + Dat") and prefixed ("Dativ-Endung") forms count too.
        val caseAbbreviations = listOf("akk", "dat", "gen")
        val caseNamePrefixes = listOf("akkusativ", "dativ", "genitiv")
        for (type in DrillTables.available()) {
            for (item in DrillTables.items(type)) {
                if (item.answer !in caseNames) continue
                val hint = item.hint ?: continue
                for (caseName in caseNames) {
                    assertFalse(
                        "${type.id} item ${item.id}: case-question hint \"$hint\" names the case \"$caseName\"",
                        DrillHints.revealsAnswer(hint, caseName),
                    )
                }
                val namesCase = DrillHints.words(hint).any { word ->
                    word in caseAbbreviations || caseNamePrefixes.any { word.startsWith(it) }
                }
                assertFalse(
                    "${type.id} item ${item.id}: case-question hint \"$hint\" names the case via abbreviation or prefix",
                    namesCase,
                )
            }
        }
    }

    @Test
    fun prefixAtSentenceEndHintsNeverStartWithThePrefix() {
        fun assertHintIsSafe(item: DrillItem, prefix: String, table: String) {
            val hintWords = item.hint?.let { DrillHints.words(it) } ?: return
            for (word in hintWords) {
                assertFalse(
                    "$table item ${item.id}: hint \"${item.hint}\" gives away the prefix \"$prefix\" via \"$word\"",
                    word.startsWith(prefix.lowercase()),
                )
            }
        }

        val tableItems = DrillTables.items(DrillType.SEPARABLE_VERBS)
        for (entry in DrillSeparableVerbs.TABLE) {
            if (!entry.isSeparable || entry.sentence == null) continue
            val item = tableItems.first { it.id.key == "sep:${entry.keyStem}:2" }
            assertHintIsSafe(item, entry.prefix, "separable_verbs")
        }

        val savedWords = listOf(
            Word(id = 1L, text = "anrufen", dueDay = 0L, addedAt = 0L),
            Word(id = 2L, text = "einkaufen", dueDay = 0L, addedAt = 0L),
        )
        val presentLookup: (String) -> String? = { text ->
            if (text.contains("anrufen")) "ruft an" else "kauft ein"
        }
        val savedItems = DrillTables.items(DrillType.SEPARABLE_VERBS, savedWords, presentLookup = presentLookup)
        assertHintIsSafe(savedItems.first { it.id.key == "1" && it.answer == "an" }, "an", "separable_verbs_saved")
        assertHintIsSafe(savedItems.first { it.id.key == "2" && it.answer == "ein" }, "ein", "separable_verbs_saved")
    }

    @Test
    fun revealsAnswerDetectsLeaksAndCleanHints() {
        // Leak shapes that existed before the fix.
        assertTrue(DrillHints.revealsAnswer("Maskulinum Akkusativ: meinen", "meinen"))
        assertTrue(DrillHints.revealsAnswer("Akkusativ", "Akkusativ"))
        assertTrue(DrillHints.revealsAnswer("sich freuen auf + Akk (1. Person: mich)", "mich"))
        assertTrue(DrillHints.revealsAnswer("sich erinnern an + Akk (3. Person)", "sich"))
        assertTrue(DrillHints.revealsAnswer("-ung: die", "die"))
        assertTrue(DrillHints.revealsAnswer("anrufen (ruft an)", "an"))

        // Clean hints must not be flagged.
        assertFalse(DrillHints.revealsAnswer("warten + Akkusativ", "auf"))
        assertFalse(DrillHints.revealsAnswer("besuchen + Akkusativ", "mich"))
        assertFalse(DrillHints.revealsAnswer("Wo? → location", "dem"))
        assertFalse(DrillHints.revealsAnswer("-ung → feminin", "die"))
        assertFalse(DrillHints.revealsAnswer("Dativ nach mit", "Sie fahren immer mit dem Zug"))

        // Compact cues without spaces must still be checked word by word.
        assertTrue(DrillHints.revealsAnswer("-ung→die", "die"))
        assertTrue(DrillHints.revealsAnswer("Wo?→Dativ", "Dativ"))
        // The whole-word check cannot catch "ankommen" revealing the prefix "an";
        // those drills are guarded by prefixAtSentenceEndHintsNeverStartWithThePrefix.
    }
}
