package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AiChatRouterTest {

    // --- fixtures with distinct, non-default values so a swapped shape fails ---

    private val today = 19_500L

    private fun savedWord(id: Long, text: String) =
        Word(id = id, text = text, box = 1, dueDay = today, addedAt = 100L)

    private val strasse = savedWord(10, "die Straße")
    private val haus = savedWord(20, "das Haus")
    private val durch = savedWord(30, "durch")
    private val anrufen = savedWord(40, "anrufen")
    private val grueßeNfd = savedWord(50, "Gru\u0308\u00dfe")

    private fun route(
        item: DrillItemId,
        ruleId: String? = null,
        words: List<Word> = listOf(strasse, haus, durch, anrufen),
        existing: Set<AiChatSubject> = emptySet(),
    ) = AiChatRouter.route(item, ruleId, words, existing)

    // --- routing step 1: word-linked by construction ---

    @Test
    fun `ai practice item routes to its saved word`() {
        val route = route(DrillItemId(DrillType.CLOZE, "ai:10:p-7"))
        assertEquals(AiChatRouter.Route.Word(AiChatSubject(AiChatSubject.Kind.WORD, "die straße")), route)
    }

    @Test
    fun `ai practice item for a deleted word falls through to the rule`() {
        val route = route(DrillItemId(DrillType.CONJUGATION, "ai:99:p-7"), ruleId = "praeteritum-strong")
        assertEquals(AiChatRouter.Route.Rule(AiChatSubject(AiChatSubject.Kind.RULE, "praeteritum-strong")), route)
    }

    @Test
    fun `ai practice item for a deleted word without a rule falls through to the topic`() {
        val route = route(DrillItemId(DrillType.CLOZE, "ai:99:p-7"))
        assertEquals(AiChatRouter.Route.Topic(AiChatSubject(AiChatSubject.Kind.TOPIC, "cloze")), route)
    }

    @Test
    fun `case endings item built from a saved word routes to the word`() {
        val route = route(DrillItemId(DrillType.CASE_ENDINGS, "10:akk-masc"))
        assertEquals(AiChatRouter.Route.Word(AiChatSubject(AiChatSubject.Kind.WORD, "die straße")), route)
    }

    @Test
    fun `adjective endings item built from a saved word routes to the word`() {
        val route = route(DrillItemId(DrillType.ADJECTIVE_ENDINGS, "10:nom-masc"))
        assertEquals(AiChatRouter.Route.Word(AiChatSubject(AiChatSubject.Kind.WORD, "die straße")), route)
    }

    @Test
    fun `built-in case endings item routes to its rule`() {
        val route = route(DrillItemId(DrillType.CASE_ENDINGS, "case:akk-masc"), ruleId = "case-accusative")
        assertEquals(AiChatRouter.Route.Rule(AiChatSubject(AiChatSubject.Kind.RULE, "case-accusative")), route)
    }

    @Test
    fun `bare word id routes to the word for the topics that build items from saved words`() {
        // Articles and plurals per the design; prepositions, pronouns, verb
        // prepositions and separable verbs build saved-word items the same way.
        val articles = route(DrillItemId(DrillType.ARTICLES, "10"), words = listOf(strasse))
        assertEquals(AiChatRouter.Route.Word(AiChatSubject(AiChatSubject.Kind.WORD, "die straße")), articles)

        val plurals = route(DrillItemId(DrillType.PLURALS, "20"), words = listOf(haus))
        assertEquals(AiChatRouter.Route.Word(AiChatSubject(AiChatSubject.Kind.WORD, "das haus")), plurals)

        val prepositions = route(DrillItemId(DrillType.PREPOSITIONS, "30"), words = listOf(durch))
        assertEquals(AiChatRouter.Route.Word(AiChatSubject(AiChatSubject.Kind.WORD, "durch")), prepositions)

        val separable = route(DrillItemId(DrillType.SEPARABLE_VERBS, "40"), words = listOf(anrufen))
        assertEquals(AiChatRouter.Route.Word(AiChatSubject(AiChatSubject.Kind.WORD, "anrufen")), separable)
    }

    @Test
    fun `word order synthetic numeric keys never route to a word`() {
        // Word-order keys "1".."5" are item numbers, not word ids, even when a saved
        // word with that id exists.
        val route = route(DrillItemId(DrillType.WORD_ORDER, "1"), words = listOf(savedWord(1, "der Hund")))
        assertEquals(AiChatRouter.Route.Topic(AiChatSubject(AiChatSubject.Kind.TOPIC, "word_order")), route)
    }

    @Test
    fun `step 1 word match wins even when the item carries a rule id`() {
        val route = route(DrillItemId(DrillType.ARTICLES, "10"), ruleId = "noun-e-ending")
        assertEquals(
            AiChatRouter.Route.Word(AiChatSubject(AiChatSubject.Kind.WORD, "die straße")),
            route
        )
    }

    // --- routing step 2: citation noun match ---

    @Test
    fun `articles citation noun match routes to the word`() {
        val route = route(DrillItemId(DrillType.ARTICLES, "art:straße"))
        assertEquals(AiChatRouter.Route.Word(AiChatSubject(AiChatSubject.Kind.WORD, "die straße")), route)
    }

    @Test
    fun `plurals citation noun match routes to the word`() {
        val route = route(DrillItemId(DrillType.PLURALS, "plur:haus"), words = listOf(haus))
        assertEquals(AiChatRouter.Route.Word(AiChatSubject(AiChatSubject.Kind.WORD, "das haus")), route)
    }

    @Test
    fun `citation match ignores case, surrounding whitespace and unicode form`() {
        // Saved in a decomposed form (u + combining diaeresis) with padding and
        // capitals; the item id carries the composed lowercase noun.
        val route = route(DrillItemId(DrillType.PLURALS, "plur:grüße"), words = listOf(grueßeNfd))
        assertEquals(AiChatRouter.Route.Word(AiChatSubject(AiChatSubject.Kind.WORD, "grüße")), route)
    }

    @Test
    fun `normalizeCitation trims lowercases and composes to NFC`() {
        assertEquals("grüße", AiChatRouter.normalizeCitation("  Grüße  "))
        assertEquals("grüße", AiChatRouter.normalizeCitation("Gru\u0308\u00dfe"))
        assertEquals("grüße", AiChatRouter.normalizeCitation("grüße"))
    }

    @Test
    fun `an inflected form never matches the citation noun`() {
        // "häuser" is the plural, not the citation noun "haus" — no stemming.
        val ruleRoute = route(DrillItemId(DrillType.PLURALS, "plur:häuser"), ruleId = "plural-umlaut")
        assertEquals(AiChatRouter.Route.Rule(AiChatSubject(AiChatSubject.Kind.RULE, "plural-umlaut")), ruleRoute)

        val topicRoute = route(DrillItemId(DrillType.PLURALS, "plur:häuser"))
        assertEquals(AiChatRouter.Route.Topic(AiChatSubject(AiChatSubject.Kind.TOPIC, "plurals")), topicRoute)
    }

    @Test
    fun `table noun with no saved match falls through to rule or topic`() {
        val ruleRoute = route(DrillItemId(DrillType.ARTICLES, "art:tisch"), ruleId = "noun-e-ending")
        assertEquals(AiChatRouter.Route.Rule(AiChatSubject(AiChatSubject.Kind.RULE, "noun-e-ending")), ruleRoute)

        val topicRoute = route(DrillItemId(DrillType.ARTICLES, "art:tisch"))
        assertEquals(AiChatRouter.Route.Topic(AiChatSubject(AiChatSubject.Kind.TOPIC, "articles")), topicRoute)
    }

    @Test
    fun `citation match with no rule id routes to the word, not provisional`() {
        val route = route(DrillItemId(DrillType.ARTICLES, "art:straße"), ruleId = null)
        assertEquals(AiChatRouter.Route.Word(AiChatSubject(AiChatSubject.Kind.WORD, "die straße")), route)
    }

    // --- both candidates on a fresh thread (step 2 matched + ruleId) ---

    @Test
    fun `both candidates when neither thread exists`() {
        val route = route(DrillItemId(DrillType.ARTICLES, "art:straße"), ruleId = "noun-e-ending")
        assertEquals(
            AiChatRouter.Route.ProvisionalBoth(
                wordSubject = AiChatSubject(AiChatSubject.Kind.WORD, "die straße"),
                ruleSubject = AiChatSubject(AiChatSubject.Kind.RULE, "noun-e-ending"),
            ),
            route
        )
    }

    @Test
    fun `both candidates - existing word thread opens`() {
        val existing = setOf(
            AiChatSubject(AiChatSubject.Kind.WORD, "die straße"),
            // A decoy rule subject with a different ref must not satisfy the rule side.
            AiChatSubject(AiChatSubject.Kind.RULE, "some-other-rule"),
        )
        val route = route(DrillItemId(DrillType.ARTICLES, "art:straße"), ruleId = "noun-e-ending", existing = existing)
        assertEquals(AiChatRouter.Route.Word(AiChatSubject(AiChatSubject.Kind.WORD, "die straße")), route)
    }

    @Test
    fun `both candidates - existing rule thread opens`() {
        val existing = setOf(
            AiChatSubject(AiChatSubject.Kind.RULE, "noun-e-ending"),
            // A decoy word subject with a different ref must not satisfy the word side.
            AiChatSubject(AiChatSubject.Kind.WORD, "der hund"),
        )
        val route = route(DrillItemId(DrillType.ARTICLES, "art:straße"), ruleId = "noun-e-ending", existing = existing)
        assertEquals(AiChatRouter.Route.Rule(AiChatSubject(AiChatSubject.Kind.RULE, "noun-e-ending")), route)
    }

    @Test
    fun `both candidates - when both exist the word thread opens`() {
        val existing = setOf(
            AiChatSubject(AiChatSubject.Kind.WORD, "die straße"),
            AiChatSubject(AiChatSubject.Kind.RULE, "noun-e-ending"),
        )
        val route = route(DrillItemId(DrillType.ARTICLES, "art:straße"), ruleId = "noun-e-ending", existing = existing)
        assertEquals(AiChatRouter.Route.Word(AiChatSubject(AiChatSubject.Kind.WORD, "die straße")), route)
    }

    @Test
    fun `both candidate word subject matches the subject a review miss routes to`() {
        // The word candidate built from a citation match must equal the subject a
        // review miss routes to, so existence checks see the same thread.
        val reviewRoute = AiChatRouter.routeForReviewMiss(strasse)
        val reviewSubject = (reviewRoute as AiChatRouter.Route.Word).subject
        val route = route(
            DrillItemId(DrillType.ARTICLES, "art:straße"),
            ruleId = "noun-e-ending",
            existing = setOf(reviewSubject),
        )
        assertEquals(AiChatRouter.Route.Word(reviewSubject), route)
    }

    // --- routing step 3: rule or topic ---

    @Test
    fun `item with a rule id and no word candidate routes to the rule`() {
        val route = route(DrillItemId(DrillType.VERB_PREPOSITIONS, "verb-prep:freuen-akk"), ruleId = "verb-prep-auf")
        assertEquals(AiChatRouter.Route.Rule(AiChatSubject(AiChatSubject.Kind.RULE, "verb-prep-auf")), route)
    }

    @Test
    fun `item without a rule id routes to the topic by drill type id`() {
        val cloze = route(DrillItemId(DrillType.CLOZE, "gap:weil"))
        assertEquals(AiChatRouter.Route.Topic(AiChatSubject(AiChatSubject.Kind.TOPIC, "cloze")), cloze)

        val gap = route(DrillItemId(DrillType.PASSIVE, "gap:werden-passiv"))
        assertEquals(AiChatRouter.Route.Topic(AiChatSubject(AiChatSubject.Kind.TOPIC, "passive")), gap)
    }

    @Test
    fun `art prefix on a non-articles item is not a citation match`() {
        // Only the builders' own types carry the citation-noun shapes.
        val route = route(DrillItemId(DrillType.CASE_ENDINGS, "art:straße"))
        assertEquals(AiChatRouter.Route.Topic(AiChatSubject(AiChatSubject.Kind.TOPIC, "case_endings")), route)
    }

    // --- review misses ---

    @Test
    fun `review miss routes to the word itself`() {
        val route = AiChatRouter.routeForReviewMiss(strasse)
        assertEquals(AiChatRouter.Route.Word(AiChatSubject(AiChatSubject.Kind.WORD, "die straße")), route)

        val other = AiChatRouter.routeForReviewMiss(haus)
        assertNotEquals(route, other)
        assertEquals(AiChatSubject(AiChatSubject.Kind.WORD, "das haus"), (other as AiChatRouter.Route.Word).subject)
    }
}
