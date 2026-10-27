package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Help-card content checks (#651 design, docs/design/drill-help-card.md §2–3):
 *
 * 1. Every rule carries the content the card needs: a rule sentence, a forms table of
 *    at most 3 rows × 4 data columns, an example, and a collapsed cue line.
 * 2. The pre-answer prose — rule, first-exposure rule and cue — names no item-specific
 *    answer. An answer word may appear there only when the same word is part of the
 *    rule's forms table, i.e. it is shown inside the whole contrast set with equal
 *    weight (design §3: "the whole contrast set … Nothing is hidden"), never as a
 *    callout. The ✗ miss line, the example and the table cells themselves are
 *    post-answer or equal-weight content and are exempt.
 */
class DrillHelpContentTest {

    private data class ItemRule(val item: DrillItem, val rule: DrillHelpRule)

    private fun itemRules(): List<ItemRule> =
        DrillGapTopics.allTopics().flatMap { (_, items) ->
            items.mapNotNull { item ->
                item.ruleId?.let { id ->
                    DrillHelpContent.rule(id)?.let { ItemRule(item, it) }
                }
            }
        }

    @Test
    fun everyRuleCarriesCompleteCardContent() {
        for (id in DrillHelpContent.ids()) {
            val rule = DrillHelpContent.rule(id)!!
            assertTrue("$id: rule text must not be blank", rule.rule.isNotBlank())
            assertTrue("$id: cue must not be blank", rule.cue.isNotBlank())
            assertTrue("$id: example must not be blank", rule.example.isNotBlank())
            assertTrue("$id: table needs a header column for the row labels", rule.table.headers.isNotEmpty())
            assertTrue(
                "$id: table rows must stay within the 3-row cap (design §3)",
                rule.table.rows.size <= 3,
            )
            for (row in rule.table.rows) {
                assertEquals(
                    "$id: each row needs one cell per data column",
                    rule.table.headers.size - 1,
                    row.cells.size,
                )
            }
            assertTrue(
                "$id: table must stay within the 4-data-column cap (design §3)",
                rule.table.headers.size - 1 <= 4,
            )
        }
    }

    @Test
    fun preAnswerProseNamesNoItemSpecificAnswer() {
        for ((item, rule) in itemRules()) {
            // Words the rule already shows in its forms table are part of the shown
            // contrast set, not a named answer.
            val contrastWords = DrillHints.words(rule.table.allText()).toSet()
            for (answerWord in DrillHints.words(item.answer)) {
                if (answerWord in contrastWords) continue
                for ((label, prose) in listOf(
                    "rule" to rule.rule,
                    "first exposure" to rule.firstExposureRule,
                    "cue" to rule.cue,
                )) {
                    val proseWords = prose?.let(DrillHints::words).orEmpty().toSet()
                    assertTrue(
                        "${rule.id}: pre-answer $label names the answer word " +
                            "\"$answerWord\" of ${item.id} outside the contrast set",
                        answerWord !in proseWords,
                    )
                }
            }
        }
    }

    @Test
    fun firstExposureRuleIsOnlyWhereTheDesignAllowsIt() {
        // Design §2: a first-exposure rule exists only where a one-case preposition (or
        // the Sie imperative pattern) may name its case before an answer.
        val expected = setOf("prep-fixed-dat", "rel-after-prep", "imp-sie-infinitive")
        assertEquals(expected, DrillHelpContent.ids().filter {
            DrillHelpContent.rule(it)?.firstExposureRule != null
        }.toSet())
    }

    @Test
    fun everyRuleIdInContentIsUsedByAnItem() {
        val usedIds = DrillGapTopics.allTopics()
            .flatMap { (_, items) -> items.mapNotNull { it.ruleId } }
            .toSet()
        for (id in DrillHelpContent.ids()) {
            assertNotNull(
                "$id is defined in DrillHelpContent but no item points at it",
                usedIds.contains(id),
            )
        }
    }

    @Test
    fun markedSegmentsSplitBoldAndPlain() {
        val segments = DrillHelpCard.markedSegments("Ich **werde** gern mitkommen.")
        assertEquals(3, segments.size)
        assertEquals("Ich ", segments[0].text)
        assertEquals(false, segments[0].bold)
        assertEquals("werde", segments[1].text)
        assertEquals(true, segments[1].bold)
        assertEquals(" gern mitkommen.", segments[2].text)
        assertEquals(false, segments[2].bold)
    }

    @Test
    fun filledExampleFillsTheGapWithTheMarkedAnswer() {
        val item = DrillItem(
            id = DrillItemId(DrillType.PASSIVE, "gap:test"),
            prompt = "Das Auto ___ gerade repariert.",
            answer = "wird",
            choices = listOf("wird", "werde", "wurde", "wurden"),
            ruleId = "passiv-aux-form",
        )
        val question = DrillQuizQuestion(item, DrillFormat.FLASHCARD)
        assertEquals(
            "Das Auto **wird** gerade repariert.",
            DrillHelpCard.filledExample(question),
        )
    }

    @Test
    fun missPartsNameTheWrongChoiceAndTheReason() {
        val item = DrillItem(
            id = DrillItemId(DrillType.PASSIVE, "gap:test"),
            prompt = "Das Auto ___ gerade repariert.",
            answer = "wird",
            choices = listOf("wird", "werde", "wurde", "wurden"),
            ruleId = "passiv-aux-form",
            missReason = "Das Auto is one thing, and gerade shows the repair is going on now.",
        )
        val question = DrillQuizQuestion(item, DrillFormat.TAP, item.choices)
        val miss = DrillHelpCard.missParts(question, "wurden")
        assertNotNull(miss)
        assertEquals("wurden", miss!!.wrong)
        assertEquals(
            "Das Auto is one thing, and gerade shows the repair is going on now.",
            miss.reason,
        )
        // Without a reason the line falls back to naming the right form.
        val bareItem = item.copy(missReason = null)
        val plain = DrillHelpCard.missParts(DrillQuizQuestion(bareItem, DrillFormat.TAP, bareItem.choices), "werde")
        assertEquals("wird", plain!!.reason)
    }
}
