package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        DrillType.PRONOUNS to DrillPronouns.items(),
        DrillType.SEPARABLE_VERBS to DrillSeparableVerbs.items(),
    )

    private fun wordOrderItems(): List<WordOrderItem> = DrillWordOrder.items()

    /** The words of a sentence, without the `**` marks and the sentence punctuation. */
    private fun sentenceWords(sentence: String): List<String> =
        sentence.replace("**", "").replace(Regex("[.,?!]"), "").split(' ').filter { it.isNotEmpty() }

    private fun itemRules(): List<ItemRule> =
        (DrillGapTopics.allTopics() + liveItems()).flatMap { (_, items) ->
            items.mapNotNull { item ->
                item.ruleId?.let { id ->
                    DrillHelpContent.rule(id)?.let { ItemRule(item, it) }
                }
            }
        }

    /** Word-order items are [WordOrderItem]s; adapt them so the shared prose checks apply. */
    private fun wordOrderItemRules(): List<ItemRule> = wordOrderItems().mapNotNull { w ->
        w.ruleId?.let { id ->
            DrillHelpContent.rule(id)?.let {
                ItemRule(
                    DrillItem(
                        id = w.id,
                        prompt = w.tiles.joinToString(" "),
                        answer = w.canonicalAnswer,
                        choices = emptyList(),
                        ruleId = id,
                    ),
                    it,
                )
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
                PRONOUN_RULE_CONTENT.mapValues { it.value.ruleId } +
                SEPARABLE_VERB_RULE_CONTENT.mapValues { it.value.ruleId } +
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
    fun everyBuiltInWordOrderItemCarriesItsAuthoredHelpRule() {
        val built = wordOrderItems()
        assertEquals(
            "Authored word order items must match the built-in items exactly",
            built.map { it.id.key }.toSet(),
            WORD_ORDER_RULE_ITEMS.keys,
        )
        assertEquals(built.size, built.map { it.id.key }.distinct().size)
        for (item in built) {
            assertNotNull("Word order item ${item.id}: no rule id", item.ruleId)
            assertNotNull(
                "Word order item ${item.id}: rule ${item.ruleId} is not in the registry",
                DrillHelpContent.rule(item.ruleId),
            )
            assertEquals(
                "Word order item ${item.id}: built-in rule id must be the authored one",
                WORD_ORDER_RULE_ITEMS[item.id.key],
                item.ruleId,
            )
        }
        assertEquals(
            DrillWordOrder.Category.entries.map { it.ruleId }.toSet(),
            built.mapNotNull { it.ruleId }.toSet(),
        )
    }

    @Test
    fun savedWordPronounSeparableAndWordOrderItemsKeepNoRuleId() {
        val pronounWords = listOf(Word(id = 707L, text = "ich", dueDay = 0L, addedAt = 0L))
        val savedPronouns = DrillPronouns.items(pronounWords).filter { it.id.key == "707" }
        assertTrue("Expected saved-word items for ich", savedPronouns.isNotEmpty())
        for (item in savedPronouns) assertNull("${item.id} must carry no rule id", item.ruleId)

        val sepWords = listOf(Word(id = 808L, text = "anrufen", dueDay = 0L, addedAt = 0L))
        val savedSeparable = DrillSeparableVerbs.items(sepWords).filter { it.id.key.startsWith("808") }
        assertTrue("Expected saved-word items for anrufen", savedSeparable.isNotEmpty())
        for (item in savedSeparable) assertNull("${item.id} must carry no rule id", item.ruleId)

        val builtIn = wordOrderItems().first()
        val tile = builtIn.tiles.first()
        val saved = DrillWordOrder.items(
            listOf(Word(id = 909L, text = tile, dueDay = 0L, addedAt = 0L)),
        ).filter { it.id.key.startsWith("909:") }
        assertTrue("Expected a saved-word word order item", saved.isNotEmpty())
        for (item in saved) assertNull("${item.id} must carry no rule id", item.ruleId)
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
        for ((item, rule) in itemRules() + wordOrderItemRules()) {
            // Words the rule already shows in its forms table are part of the shown
            // contrast set, not a named answer.
            val contrastWords = DrillHints.words(rule.table.allText()).toSet()
            for (answerWord in DrillHints.words(item.answer)) {
                if (answerWord in contrastWords) continue
                // Word-order answers are whole sentences (synthetic items have no choices),
                // so their short function words ("in", "an", "um") collide with ordinary
                // English in the authored prose and carry no answer.
                if (item.choices.isEmpty() && answerWord.length <= 3) continue
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
            .toSet() + wordOrderItems().mapNotNull { it.ruleId }
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
    fun italicSegmentsSplitItalicAndPlain() {
        // The verb + preposition miss lines mark their pairs with *…*; the split
        // must yield the marked span, so the screen can render it italic instead
        // of showing the literal asterisks.
        val segments = DrillHelpCard.italicSegments("an belongs to pairs like *denken an*. Waiting.")
        assertEquals(3, segments.size)
        assertEquals("an belongs to pairs like ", segments[0].text)
        assertEquals(false, segments[0].italic)
        assertEquals("denken an", segments[1].text)
        assertEquals(true, segments[1].italic)
        assertEquals(". Waiting.", segments[2].text)
        assertEquals(false, segments[2].italic)
    }

    @Test
    fun italicSegmentsDropTheMarksAndKeepUnmatchedOnesLiteral() {
        val marked = "über belongs to *sprechen über*. *warten* pairs with *auf*."
        val rendered = DrillHelpCard.italicSegments(marked).joinToString("") { it.text }
        assertEquals("über belongs to sprechen über. warten pairs with auf.", rendered)
        assertEquals(
            listOf("sprechen über", "warten", "auf"),
            DrillHelpCard.italicSegments(marked).filter { it.italic }.map { it.text },
        )
        // A single asterisk with no partner stays in the text as it is.
        val literal = DrillHelpCard.italicSegments("a * b")
        assertEquals("a * b", literal.joinToString("") { it.text })
        assertTrue(literal.none { it.italic })
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

    @Test
    fun liveRuleTitlesAreUniquePerSubject() {
        // A repeated title names no pattern, so every live rule's title must be unique
        // within its subject (adjective endings, articles and plurals repeated theirs).
        val bySubject = DrillHelpContentLive.rules.groupBy { rule ->
            when {
                rule.id.startsWith("prep-") -> "prepositions"
                rule.id.startsWith("case-") -> "case endings"
                rule.id.startsWith("verb-prep") -> "verb prepositions"
                rule.id.startsWith("adj-") -> "adjective endings"
                rule.id.startsWith("art-") -> "articles"
                rule.id.startsWith("plural") -> "plurals"
                rule.id.startsWith("pron-") -> "pronouns"
                rule.id.startsWith("sep-") -> "separable verbs"
                rule.id.startsWith("wo-") -> "word order"
                else -> throw AssertionError("Unclassified live rule id: ${rule.id}")
            }
        }
        assertEquals(DrillHelpContentLive.rules.size, bySubject.values.sumOf { it.size })
        for ((subject, rules) in bySubject) {
            assertEquals(
                "$subject: rule titles must be unique per subject",
                rules.size,
                rules.map { it.title }.distinct().size,
            )
        }
    }

    @Test
    fun liveRuleTitleNamesNoItemAnswer() {
        for ((type, items) in liveItems()) {
            for (item in items) {
                val rule = DrillHelpContent.rule(item.ruleId)!!
                assertFalse(
                    "$type item ${item.id}: title \"${rule.title}\" reveals the answer " +
                        "\"${item.answer}\"",
                    DrillHints.revealsAnswer(rule.title, item.answer),
                )
            }
        }
    }

    @Test
    fun perItemExamplesCoverExactlyTheItemsWithoutAGapInTheirPrompt() {
        // Items whose prompt carries the gap render their own filled prompt as the
        // example; the which-case items and the word-order items (no gap) need an
        // authored per-item example, and only they may have one.
        val keysWithoutGap = liveItems().flatMap { (_, items) -> items }
            .filter { !it.prompt.contains(DrillItem.GAP_MARKER) }
            .map { it.id.key }
            .toSet() + wordOrderItems().filter { it.ruleId != null }.map { it.id.key }.toSet()
        assertEquals(keysWithoutGap, ITEM_HELP_EXAMPLES.keys)
    }

    @Test
    fun perItemExamplesDifferAcrossItemsOfOneRule() {
        // A shared rule must never show a different item's example sentence: the
        // example the card would show in the flashcard state differs per item.
        // Two items of one rule can carry the very same sentence and answer (the
        // prepositions file and the case-endings file both author "Das Geschenk ist
        // für den Vater."); that is one example for one sentence, not a shared one.
        val examplesByRule = liveItems().flatMap { (_, items) ->
            items.mapNotNull { item ->
                item.ruleId?.let { ruleId ->
                    val question = DrillQuizQuestion(item, DrillFormat.FLASHCARD)
                    ruleId to Pair("${question.prompt}|${question.answer}", DrillHelpCard.filledExample(question))
                }
            }
        }.groupBy({ it.first }, { it.second })
        for ((ruleId, pairs) in examplesByRule) {
            assertEquals(
                "$ruleId: every distinct item of the rule must show its own example",
                pairs.map { it.first }.distinct().size,
                pairs.map { it.second }.distinct().size,
            )
        }
    }

    @Test
    fun everyWordOrderItemWithARuleHasItsOwnExample() {
        // Word-order prompts have no gap, so the rule's example would repeat across its
        // items. Each ruled item needs its own entry; a missing entry fails here.
        for (item in wordOrderItems().filter { it.ruleId != null }) {
            val example = DrillHelpCard.wordOrderExample(item)
            assertNotNull("${item.id}: no per-item word-order example", example)
            assertEquals(
                "${item.id}: the example must spell the item's first accepted order",
                item.canonicalAnswer.split(' '),
                sentenceWords(example!!),
            )
        }
    }

    @Test
    fun wordOrderExamplesDifferAcrossItemsOfOneRule() {
        val ruledItems = wordOrderItems().filter { it.ruleId != null }
        for ((ruleId, items) in ruledItems.groupBy { it.ruleId!! }) {
            assertEquals(
                "$ruleId: every distinct word order of the rule must show its own example",
                items.map { it.canonicalAnswer }.distinct().size,
                items.mapNotNull { DrillHelpCard.wordOrderExample(it) }.distinct().size,
            )
        }
    }

    @Test
    fun wordOrderHelpCardOpensWithRuleBeforeAnswerAndExampleAfterAMiss() {
        val item = wordOrderItems().single { it.id.key == "v2_kino" }
        val whyOpen = DrillHelpCard.wordOrderState(item, whyOpen = true, answeredWrong = false)
        assertNotNull(whyOpen)
        assertEquals("wo-verb-second", whyOpen!!.rule.id)
        assertNull("before an answer the card names no example", whyOpen.example)
        assertNull(DrillHelpCard.wordOrderState(item, whyOpen = false, answeredWrong = false))
        val afterMiss = DrillHelpCard.wordOrderState(item, whyOpen = false, answeredWrong = true)
        assertNotNull(afterMiss)
        assertTrue(afterMiss!!.showAnswer)
        assertEquals("Ich **gehe** morgen ins Kino.", afterMiss.example)
    }

    @Test
    fun whichCaseItemsShowTheirOwnExampleSentence() {
        // Distinguishing check for prep-which-fixed: the durch item must no longer
        // show the rule's mit-Bus example, but its own durch sentence.
        val durch = DrillPrepositions.items().single { it.id.key == "prep:durch:0" }
        assertEquals(
            "Ich gehe durch **den** Park.",
            DrillHelpCard.filledExample(DrillQuizQuestion(durch, DrillFormat.FLASHCARD)),
        )
        val mit = DrillPrepositions.items().single { it.id.key == "prep:mit:0" }
        assertEquals(
            "Ich fahre mit **dem** Bus.",
            DrillHelpCard.filledExample(DrillQuizQuestion(mit, DrillFormat.FLASHCARD)),
        )
        // The verb + preposition case question renders its pair's own sentence with
        // the preposition bold.
        val bittenUm = DrillVerbPrepositions.items().single { it.id.key == "verbprep:bitten:um:1" }
        assertEquals(
            "Darf ich dich **um** Hilfe bitten?",
            DrillHelpCard.filledExample(DrillQuizQuestion(bittenUm, DrillFormat.FLASHCARD)),
        )
    }

    @Test
    fun authoredPronounAndSeparableContentCoversExactlyTheBuiltInItems() {
        for ((type, content) in listOf(
            DrillType.PRONOUNS to PRONOUN_RULE_CONTENT.mapValues { it.value.ruleId },
            DrillType.SEPARABLE_VERBS to SEPARABLE_VERB_RULE_CONTENT.mapValues { it.value.ruleId },
        )) {
            val built = DrillTables.items(type)
            assertTrue("$type must have built-in items", built.isNotEmpty())
            // Both directions: authored content that matches no item, or an item with
            // no authored content, fails; a count match alone proves nothing.
            assertEquals(
                "$type: authored keys must match the built-in item keys exactly",
                built.map { it.id.key }.toSet(),
                content.keys,
            )
            assertEquals(built.size, built.map { it.id.key }.distinct().size)
            for (item in built) {
                assertEquals(
                    "$type item ${item.id}: built-in rule id must be the authored one",
                    content[item.id.key],
                    item.ruleId,
                )
            }
        }
    }

    @Test
    fun pronounAndSeparableMissLinesAreWiredPerWrongChoice() {
        val pronouns = DrillPronouns.items().single { it.id.key == "pron:ich:2" }
        assertEquals("pron-first-second", pronouns.ruleId)
        assertEquals(
            "the dative form of du — the help comes to me, so mir.",
            pronouns.missByChoice["dir"],
        )
        val separable = DrillSeparableVerbs.items().single { it.id.key == "sep:anrufen:1" }
        assertEquals("sep-participle-ge", separable.ruleId)
        assertEquals(
            "ge- belongs between the prefix and the stem, never in front, so angerufen.",
            separable.missByChoice["geanrufen"],
        )
    }

    @Test
    fun onlyConjugationAndClozeStillHaveNoBuiltInHelpRule() {
        // A type lands in this set when a built-in item of it carries no rule id, or
        // when it has no built-in items at all (such a type must not count as wired).
        // Word-order items are [WordOrderItem]s, so their rule ids are read apart.
        val withoutRule = DrillType.entries.filter { type ->
            val ruleIds = if (type == DrillType.WORD_ORDER) {
                wordOrderItems().map { it.ruleId }
            } else {
                DrillTables.items(type).map { it.ruleId }
            }
            ruleIds.isEmpty() || ruleIds.any { it == null }
        }.toSet()
        assertEquals(setOf(DrillType.CONJUGATION, DrillType.CLOZE), withoutRule)
    }
}
