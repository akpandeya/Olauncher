package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    /** The live (words-driven) topics whose items carry help rules since #699. */
    private fun liveItems(): List<Pair<DrillType, List<DrillItem>>> = listOf(
        DrillType.PREPOSITIONS to DrillPrepositions.items(),
        DrillType.CASE_ENDINGS to DrillCaseEndings.items(),
        DrillType.VERB_PREPOSITIONS to DrillVerbPrepositions.items(),
        DrillType.ADJECTIVE_ENDINGS to DrillAdjectiveEndings.items(),
        DrillType.ARTICLES to DrillArticles.items(),
        DrillType.PLURALS to DrillPlurals.items(),
    )

    private fun itemRules(): List<ItemRule> =
        (DrillGapTopics.allTopics() + liveItems()).flatMap { (_, items) ->
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
            assertTrue("$id: rule <= 170 chars", rule.rule.length <= 170)
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
    fun everyRuleCarriesShortTitleNamingThePattern() {
        for (id in DrillHelpContent.ids()) {
            val rule = DrillHelpContent.rule(id)!!
            assertTrue("$id: title must not be blank", rule.title.isNotBlank())
            assertTrue(
                "$id: title must stay short (found ${rule.title.length} chars)",
                rule.title.length <= 40,
            )
            assertTrue(
                "$id: title must be sentence case",
                rule.title[0].isUpperCase(),
            )
        }
    }

    @Test
    fun ruleIdsAreUnique() {
        // The id → rule map would silently collapse duplicates; the authored list must not.
        val all = DrillHelpContent.allRules()
        assertEquals(all.size, all.map { it.id }.distinct().size)
        assertEquals(all.size, DrillHelpContent.ids().size)
    }

    @Test
    fun everyBuiltInLiveItemCarriesItsHelpRule() {
        for ((type, items) in liveItems()) {
            for (item in items) {
                assertTrue(
                    "$type item ${item.id} must carry a help rule",
                    item.ruleId != null,
                )
                assertNotNull(
                    "$type item ${item.id}: rule id ${item.ruleId} is not in the registry",
                    DrillHelpContent.rule(item.ruleId),
                )
                assertEquals(
                    "$type item ${item.id}: miss lines must cover exactly the wrong choices",
                    (item.choices - item.answer).toSet(),
                    item.missByChoice.keys,
                )
                assertTrue(
                    "$type item ${item.id}: every miss line must be filled in to explain its wrong choice",
                    item.missByChoice.values.all { it.isNotBlank() },
                )
            }
        }
    }

    @Test
    fun authoredRuleContentCoversExactlyTheBuiltInItems() {
        // Authored assignments from docs/design/drill-content/: prepositions and case
        // endings are keyed by item key, verb prepositions by pair → 2 items each,
        // adjective endings by template key, articles and plurals by table noun.
        val authored: Map<String, String> =
            PREPOSITION_RULE_CONTENT.mapValues { it.value.ruleId } +
                CASE_ENDING_RULE_CONTENT.entries.associate { "case:${it.key}" to it.value.ruleId } +
                VERB_PREP_RULE_CONTENT.entries.flatMap { (pairKey, content) ->
                    val pairs = DrillVerbPrepositions.PAIRS.filter {
                        "${it.verb} ${it.preposition}" == pairKey
                    }
                    assertEquals("Pair $pairKey must exist once", 1, pairs.size)
                    val pair = pairs.single()
                    listOf(
                        "verbprep:${pair.verb}:${pair.preposition}:0" to content.gapRuleId,
                        "verbprep:${pair.verb}:${pair.preposition}:1" to content.caseRuleId,
                    )
                }.toMap() +
                ADJECTIVE_ENDING_RULE_ITEMS.entries.associate { (key, ruleId) ->
                    "adj:$key" to ruleId
                } +
                ARTICLE_GENDER_RULE_CONTENT_BY_NOUN.entries.associate { (noun, content) ->
                    "art:${noun.lowercase()}" to content.ruleId
                } +
                PLURAL_RULE_CONTENT_BY_NOUN.entries.associate { (noun, content) ->
                    "plur:${noun.lowercase()}" to content.ruleId
                }
        val built: Map<String, String> = liveItems().flatMap { (_, items) ->
            items.map { it.id.key to it.ruleId!! }
        }.toMap()
        // Both directions: an authored item that matches no built-in item or a built-in
        // item with no authored content must fail — a count match alone proves nothing.
        assertEquals(
            "Authored items must match the built-in items exactly",
            built.keys,
            authored.keys,
        )
        for (key in built.keys) {
            assertEquals(
                "Item $key: built-in rule id must be the authored one",
                authored[key],
                built[key],
            )
        }
    }

    @Test
    fun savedWordItemsKeepNoRuleId() {
        // Items rebuilt from saved words replace the stable key; they match no authored
        // help item and must stay without a rule id.
        val savedPrepositions = DrillPrepositions.items(
            listOf(Word(id = 101L, text = "durch", dueDay = 0L, addedAt = 0L)),
        )
        val wordKeyedPrep = savedPrepositions.filter { it.id.key == "101" }
        assertTrue("Expected saved-word items for durch", wordKeyedPrep.isNotEmpty())
        for (item in wordKeyedPrep) {
            assertNull("Saved-word item ${item.id} must carry no rule id", item.ruleId)
        }

        val savedCaseEndings = DrillCaseEndings.items(
            listOf(Word(id = 202L, text = "sehen", dueDay = 0L, addedAt = 0L)),
        )
        val wordKeyedCase = savedCaseEndings.filter { it.id.key == "202:akk_m_sehen_hund" }
        assertTrue("Expected saved-word item for sehen", wordKeyedCase.isNotEmpty())
        for (item in wordKeyedCase) {
            assertNull("Saved-word item ${item.id} must carry no rule id", item.ruleId)
        }

        val savedVerbPreps = DrillVerbPrepositions.items(
            listOf(Word(id = 303L, text = "warten", dueDay = 0L, addedAt = 0L)),
        )
        val wordKeyedVerbPrep = savedVerbPreps.filter { it.id.key == "303" }
        assertTrue("Expected saved-word items for warten", wordKeyedVerbPrep.isNotEmpty())
        for (item in wordKeyedVerbPrep) {
            assertNull("Saved-word item ${item.id} must carry no rule id", item.ruleId)
        }

        val savedAdjectiveEndings = DrillAdjectiveEndings.items(
            listOf(Word(id = 404L, text = "mit", dueDay = 0L, addedAt = 0L)),
        )
        val wordKeyedAdjective = savedAdjectiveEndings.filter { it.id.key.startsWith("404:") }
        assertTrue("Expected saved-word items for mit", wordKeyedAdjective.isNotEmpty())
        for (item in wordKeyedAdjective) {
            assertNull("Saved-word item ${item.id} must carry no rule id", item.ruleId)
        }

        val savedArticles = DrillArticles.items(
            listOf(Word(id = 505L, text = "der Tisch", dueDay = 0L, addedAt = 0L)),
        )
        val wordKeyedArticles = savedArticles.filter { it.id.key == "505" }
        assertTrue("Expected saved-word item for der Tisch", wordKeyedArticles.isNotEmpty())
        for (item in wordKeyedArticles) {
            assertNull("Saved-word item ${item.id} must carry no rule id", item.ruleId)
        }

        val savedPlurals = DrillPlurals.items(
            listOf(Word(id = 606L, text = "das Haus", dueDay = 0L, addedAt = 0L)),
            pluralLookup = { if (it == "Haus") "Häuser" else null },
        )
        val wordKeyedPlurals = savedPlurals.filter { it.id.key == "606" }
        assertTrue("Expected saved-word item for das Haus", wordKeyedPlurals.isNotEmpty())
        for (item in wordKeyedPlurals) {
            assertNull("Saved-word item ${item.id} must carry no rule id", item.ruleId)
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
                    "title" to rule.title,
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
        // Design §2: a first-exposure rule exists only where the design author allows a
        // case name before an answer: one-case prepositions, the two-way prepositions
        // (their items answer with the article, not the case) and the Sie imperative
        // pattern.
        val expected = setOf(
            "prep-fixed-acc", "prep-fixed-dat", "prep-fixed-gen", "prep-two-way",
            "rel-after-prep", "imp-sie-infinitive",
        )
        assertEquals(expected, DrillHelpContent.ids().filter {
            DrillHelpContent.rule(it)?.firstExposureRule != null
        }.toSet())
    }

    @Test
    fun everyRuleIdInContentIsUsedByAnItem() {
        val usedIds = (DrillGapTopics.allTopics() + liveItems())
            .flatMap { (_, items) -> items.mapNotNull { it.ruleId } }
            .toSet()
        for (id in DrillHelpContent.ids()) {
            assertTrue(
                "$id is defined in DrillHelpContent but no item points at it",
                id in usedIds,
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

    @Test
    fun missPartsLookUpTheReasonForTheWrongChoice() {
        val item = DrillItem(
            id = DrillItemId(DrillType.PASSIVE, "gap:test"),
            prompt = "Das Auto ___ gerade repariert.",
            answer = "wird",
            choices = listOf("wird", "werde", "wurde", "wurden"),
            ruleId = "passiv-aux-form",
            missReason = "general note",
            missByChoice = mapOf(
                "werde" to "the ich form. Das Auto is a third-person subject, so wird.",
                "wurden" to "the past plural form. Das Auto is one thing, so wird.",
            ),
        )
        val question = DrillQuizQuestion(item, DrillFormat.TAP, item.choices)
        assertEquals(
            "the ich form. Das Auto is a third-person subject, so wird.",
            DrillHelpCard.missParts(question, "werde")!!.reason,
        )
        assertEquals(
            "the past plural form. Das Auto is one thing, so wird.",
            DrillHelpCard.missParts(question, "wurden")!!.reason,
        )
        // A wrong choice without its own reason falls back to the item's note.
        assertEquals("general note", DrillHelpCard.missParts(question, "werden")!!.reason)
    }
}
