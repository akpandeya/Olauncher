package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Item data checks for the 11 gap topics (#651):
 *
 * 1. Every topic carries exactly the 10 items authored in
 *    docs/design/drill-content/items/.
 * 2. Every item passes [DrillItem.validate] and has exactly one correct choice; the
 *    three wrong choices are distinct, so all distractors are plausible and no item
 *    can be answered right by tapping an equal choice twice.
 * 3. Every item's rule id exists in [DrillHelpContent] — no rule text is invented in
 *    code; the help card resolves its content through the same id.
 * 4. The collapsed cue (the item's hint) never reveals the answer, and the prompt
 *    never contains it (same whole-word check as [DrillContentAuditTest]).
 */
class DrillGapTopicsTest {

    @Test
    fun everyGapTopicCarriesExactlyTenItems() {
        val topics = DrillGapTopics.allTopics()
        assertEquals(11, topics.size)
        for ((type, items) in topics) {
            assertEquals("${type.id} item count", 10, items.size)
        }
    }

    @Test
    fun everyGapItemHasExactlyOneCorrectChoiceAndPlausibleDistractors() {
        for ((type, items) in DrillGapTopics.allTopics()) {
            for (item in items) {
                val check = item.validate()
                assertTrue("${type.id} ${item.id}: ${check.problems}", check.isValid)
                assertEquals(
                    "${type.id} ${item.id}: the answer must appear exactly once",
                    1,
                    item.choices.count { it == item.answer },
                )
                val distractors = item.choices.filter { it != item.answer }
                assertEquals(
                    "${type.id} ${item.id}: three plausible wrong choices expected",
                    3,
                    distractors.size,
                )
                assertEquals(
                    "${type.id} ${item.id}: distractors must be distinct",
                    distractors.size,
                    distractors.distinct().size,
                )
            }
        }
    }

    @Test
    fun everyGapItemPointsAtAnExistingRule() {
        for ((type, items) in DrillGapTopics.allTopics()) {
            for (item in items) {
                val ruleId = item.ruleId
                assertNotNull("${type.id} ${item.id}: ruleId required", ruleId)
                assertNotNull(
                    "${type.id} ${item.id}: rule \"$ruleId\" must exist in DrillHelpContent",
                    DrillHelpContent.rule(ruleId),
                )
            }
        }
    }

    @Test
    fun everyGapItemHintComesFromItsRuleCue() {
        for ((type, items) in DrillGapTopics.allTopics()) {
            for (item in items) {
                val rule = DrillHelpContent.rule(item.ruleId)!!
                if (item.hint != rule.cue) {
                    // The only allowed deviation: a documented override for an item
                    // whose rule cue would name the answer (werden future items).
                    assertEquals(
                        "${type.id} ${item.id}: hint must be the rule cue or a leak override",
                        "futur-helper-form",
                        item.ruleId,
                    )
                    assertTrue(
                        "${type.id} ${item.id}: override hint must not reveal the answer",
                        !DrillHints.revealsAnswer(item.hint.orEmpty(), item.answer),
                    )
                }
            }
        }
    }

    @Test
    fun noGapPromptOrHintContainsTheAnswer() {
        for ((type, items) in DrillGapTopics.allTopics()) {
            for (item in items) {
                val hint = item.hint.orEmpty()
                assertFalse(
                    "${type.id} ${item.id}: hint \"$hint\" reveals \"${item.answer}\"",
                    DrillHints.revealsAnswer(hint, item.answer),
                )
                val promptWords = DrillHints.words(item.prompt).toSet()
                // A relative-pronoun prompt names its referent ("Der Mann, ___"),
                // whose article is itself the answer in nominative items. Gender and
                // number are the given; the case is the question (see the content
                // files), so the prompt's first word is not treated as a leak.
                val firstWord = item.prompt.split(Regex("\\s+")).firstOrNull()?.lowercase()
                    ?.trim { !it.isLetterOrDigit() }
                val checkedWords = if (firstWord != null) promptWords - firstWord else promptWords
                for (answerWord in DrillHints.words(item.answer)) {
                    assertFalse(
                        "${type.id} ${item.id}: prompt contains the answer word \"$answerWord\"",
                        answerWord in checkedWords,
                    )
                }
            }
        }
    }

    @Test
    fun gapTopicsAreServedThroughDrillTables() {
        for ((type, items) in DrillGapTopics.allTopics()) {
            assertTrue("${type.id} must be available", DrillTables.available().contains(type))
            assertEquals(
                "${type.id} items through DrillTables",
                items,
                DrillTables.items(type),
            )
        }
    }
}
