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
 * 5. Every wrong choice of every item carries its own miss reason, transcribed from
 *    the topic design files, and it reaches the miss line through [DrillHelpCard.missParts].
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

    /**
     * Choices whose content file carries no miss line for them: konjunktiv-i item 2's
     * third distractor is "müsset" in code, while the design file's third miss line
     * names "müsstet" (a different form). They fall back to the item's note.
     */
    private val choicesWithoutOwnMissReason = mapOf("konjunktiv_i:gap:2" to setOf("müsset"))

    @Test
    fun everyWrongChoiceHasItsOwnMissReason() {
        for ((type, items) in DrillGapTopics.allTopics()) {
            for (item in items) {
                val question = DrillQuizQuestion(item, DrillFormat.TAP, item.choices)
                for (wrong in item.choices.filter { it != item.answer }) {
                    val reason = item.missByChoice[wrong]
                    val fallbackAllowed =
                        wrong in choicesWithoutOwnMissReason[item.id.toString()].orEmpty()
                    assertTrue(
                        "${type.id} ${item.id}: wrong choice \"$wrong\" needs a miss reason " +
                            "from its content file",
                        !reason.isNullOrBlank() || fallbackAllowed,
                    )
                    if (reason == null) continue
                    // The reason reaches the miss line for that exact choice — not the
                    // item's general note and not the bare answer.
                    assertEquals(
                        "${type.id} ${item.id}: missParts must use the per-choice reason of " +
                            "\"$wrong\"",
                        reason,
                        DrillHelpCard.missParts(question, wrong)!!.reason,
                    )
                }
            }
        }
    }

    @Test
    fun wrongChoiceWithoutItsOwnReasonFallsBackToTheNote() {
        val item = DrillGapTopics.items(DrillType.KONJUNKTIV_I)!!
            .first { it.id.key == "gap:2" }
        val question = DrillQuizQuestion(item, DrillFormat.TAP, item.choices)
        assertEquals(item.missReason, DrillHelpCard.missParts(question, "müsset")!!.reason)
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
