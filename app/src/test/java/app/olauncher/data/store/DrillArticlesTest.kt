package app.olauncher.data.store

import app.olauncher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillArticlesTest {

    @Test
    fun allItemsPassValidation() {
        val items = DrillArticles.items()
        assertTrue("Expected at least 60 items, found ${items.size}", items.size >= 60)
        for (item in items) {
            val check = item.validate()
            assertTrue(
                "Item ${item.id} failed validation: ${check.problems.joinToString()}",
                check.isValid,
            )
        }
    }

    @Test
    fun bundledTableHasAtLeast60CommonNouns() {
        assertTrue(
            "Bundled table must contain at least 60 common nouns, found ${DrillArticles.TABLE_ENTRIES.size}",
            DrillArticles.TABLE_ENTRIES.size >= 60,
        )
    }

    @Test
    fun choicesAreExactlyDerDieDas() {
        val expectedChoices = listOf("der", "die", "das")
        assertEquals(expectedChoices, DrillArticles.CHOICES)

        val items = DrillArticles.items()
        for (item in items) {
            assertEquals("Choices must be exactly der, die, das", expectedChoices, item.choices)
            assertTrue("Answer must be one of der, die, das", item.answer in expectedChoices)
        }
    }

    @Test
    fun hintsApplyGenderRulesWhereApplicable() {
        val items = DrillArticles.items().associateBy { it.prompt }

        // -ung: die
        val wohnung = items["___ Wohnung"]
        assertNotNull(wohnung)
        assertEquals("-ung → feminin", wohnung?.hint)

        // -heit: die
        val freiheit = items["___ Freiheit"]
        assertNotNull(freiheit)
        assertEquals("-heit → feminin", freiheit?.hint)

        // -keit: die
        val moeglichkeit = items["___ Möglichkeit"]
        assertNotNull(moeglichkeit)
        assertEquals("-keit → feminin", moeglichkeit?.hint)

        // -schaft: die
        val freundschaft = items["___ Freundschaft"]
        assertNotNull(freundschaft)
        assertEquals("-schaft → feminin", freundschaft?.hint)

        // -ion: die
        val station = items["___ Station"]
        assertNotNull(station)
        assertEquals("-ion → feminin", station?.hint)

        // -chen: das
        val maedchen = items["___ Mädchen"]
        assertNotNull(maedchen)
        assertEquals("-chen → neutral", maedchen?.hint)

        // -lein: das
        val fraeulein = items["___ Fräulein"]
        assertNotNull(fraeulein)
        assertEquals("-lein → neutral", fraeulein?.hint)

        // -ment: das
        val dokument = items["___ Dokument"]
        assertNotNull(dokument)
        assertEquals("-ment → neutral", dokument?.hint)

        // -um: das
        val museum = items["___ Museum"]
        assertNotNull(museum)
        assertEquals("-um → neutral", museum?.hint)

        // -tät: die
        val uni = items["___ Universität"]
        assertNotNull(uni)
        assertEquals("-tät → feminin", uni?.hint)

        // -ei: die
        val baeckerei = items["___ Bäckerei"]
        assertNotNull(baeckerei)
        assertEquals("-ei → feminin", baeckerei?.hint)

        // -ismus: der
        val optimismus = items["___ Optimismus"]
        assertNotNull(optimismus)
        assertEquals("-ismus → maskulin", optimismus?.hint)

        // -ling: der
        val fruehling = items["___ Frühling"]
        assertNotNull(fruehling)
        assertEquals("-ling → maskulin", fruehling?.hint)
    }

    @Test
    fun tableNounBreakingRuleNeverCarriesRuleHint() {
        val items = DrillArticles.items().associateBy { it.prompt }

        // "Sprung" ends in "ung" but is masculine ("der Sprung").
        // It breaks the -ung -> die rule and must NEVER carry the -ung hint.
        val sprung = items["___ Sprung"]
        assertNotNull("Sprung should be in the table", sprung)
        assertEquals("der", sprung?.answer)
        assertNull("Sprung breaks -ung rule so hint must be null", sprung?.hint)

        // "Moment" ends in "ment" but is masculine ("der Moment").
        // It breaks the -ment -> das rule and must NEVER carry the -ment hint.
        val moment = items["___ Moment"]
        assertNotNull("Moment should be in the table", moment)
        assertEquals("der", moment?.answer)
        assertNull("Moment breaks -ment rule so hint must be null", moment?.hint)

        // "Baum" ends in "um" but is masculine ("der Baum").
        // It breaks the -um -> das rule and must NEVER carry the -um hint.
        val baum = items["___ Baum"]
        assertNotNull("Baum should be in the table", baum)
        assertEquals("der", baum?.answer)
        assertNull("Baum breaks -um rule so hint must be null", baum?.hint)
    }

    @Test
    fun savedNounsWithLeadingArticleGiveItemsKeyedByWordId() {
        val savedWords = listOf(
            Word(id = 42L, text = "der Tisch", dueDay = 1L, addedAt = 1000L),
            Word(id = 43L, text = "Die Katze", dueDay = 1L, addedAt = 1000L),
            Word(id = 44L, text = "das Haus", dueDay = 1L, addedAt = 1000L),
        )

        val items = DrillArticles.items(savedWords)
        val byId = items.associateBy { it.id.toString() }

        val tisch = byId["articles:42"]
        assertNotNull(tisch)
        assertEquals("___ Tisch", tisch?.prompt)
        assertEquals("der", tisch?.answer)
        assertEquals(listOf("der", "die", "das"), tisch?.choices)

        val katze = byId["articles:43"]
        assertNotNull(katze)
        assertEquals("___ Katze", katze?.prompt)
        assertEquals("die", katze?.answer)

        val haus = byId["articles:44"]
        assertNotNull(haus)
        assertEquals("___ Haus", haus?.prompt)
        assertEquals("das", haus?.answer)
    }

    @Test
    fun savedNounsWithDictionaryGenderGiveItemsKeyedByWordId() {
        val savedWords = listOf(
            Word(id = 101L, text = "Rückkehr", dueDay = 1L, addedAt = 1000L),
        )
        val lookup: (String) -> String? = { if (it == "Rückkehr") "die" else null }

        val items = DrillArticles.items(savedWords, genderLookup = lookup)
        val rueckkehr = items.firstOrNull { it.id.toString() == "articles:101" }

        assertNotNull(rueckkehr)
        assertEquals("___ Rückkehr", rueckkehr?.prompt)
        assertEquals("die", rueckkehr?.answer)
        assertEquals(listOf("der", "die", "das"), rueckkehr?.choices)
    }

    @Test
    fun savedWordsWithoutKnownGenderAreExcluded() {
        val savedWords = listOf(
            Word(id = 201L, text = "schnell", dueDay = 1L, addedAt = 1000L),
            Word(id = 202L, text = "gehen", dueDay = 1L, addedAt = 1000L),
        )
        val lookup: (String) -> String? = { null }

        val items = DrillArticles.items(savedWords, genderLookup = lookup)
        val savedItemIds = items.map { it.id.key }
        assertFalse(savedItemIds.contains("201"))
        assertFalse(savedItemIds.contains("202"))
    }

    @Test
    fun savedNounsDeduplicateMatchingTableNouns() {
        val savedWords = listOf(
            Word(id = 500L, text = "der Hund", dueDay = 1L, addedAt = 1000L),
        )

        val items = DrillArticles.items(savedWords)
        val hundItems = items.filter { it.prompt.equals("___ Hund", ignoreCase = true) }

        assertEquals("Should only have one item for Hund", 1, hundItems.size)
        assertEquals("Item for Hund should use saved word ID", "500", hundItems[0].id.key)
        assertFalse("Table item art:hund should not be present", items.any { it.id.key == "art:hund" })
    }

    @Test
    fun drillTablesAvailableIncludesArticles() {
        val available = DrillTables.available()
        assertTrue(DrillType.ARTICLES in available)
    }

    @Test
    fun drillTablesLabelResForArticles() {
        assertEquals(R.string.drill_type_articles, DrillTables.labelRes(DrillType.ARTICLES))
    }

    @Test
    fun drillTablesItemsDelegatesToArticles() {
        val directItems = DrillArticles.items()
        val tableItems = DrillTables.items(DrillType.ARTICLES)
        assertEquals(directItems.size, tableItems.size)
        for (i in directItems.indices) {
            assertEquals(directItems[i].id, tableItems[i].id)
            assertEquals(directItems[i].prompt, tableItems[i].prompt)
            assertEquals(directItems[i].answer, tableItems[i].answer)
            assertEquals(directItems[i].choices, tableItems[i].choices)
            assertEquals(directItems[i].hint, tableItems[i].hint)
        }
    }

    @Test
    fun builtInItemsMatchTheAuthoredRuleAssignment() {
        val built = DrillArticles.items().associate { it.id.key to it.ruleId!! }
        val authored = ARTICLE_GENDER_RULE_CONTENT_BY_NOUN.entries.associate { (noun, content) ->
            "art:${noun.lowercase()}" to content.ruleId
        }
        // Both directions: every table noun carries the rule its content file assigns,
        // and every authored noun is a built-in one (no partial mapping).
        assertEquals(authored, built)
    }

    @Test
    fun authoredArticleNounsNeverCollideAcrossRules() {
        // The by-noun map is built with toMap(), which silently drops duplicate
        // keys, so a repeated noun would lose its rule instead of failing loudly.
        assertEquals(
            "Every authored article noun must land in ARTICLE_GENDER_RULE_CONTENT_BY_NOUN;" +
                " a duplicate noun would be silently dropped",
            ARTICLE_GENDER_RULE_CONTENT.sumOf { it.nouns.size },
            ARTICLE_GENDER_RULE_CONTENT_BY_NOUN.size,
        )
    }

    @Test
    fun missLinesExplainEveryWrongChoice() {
        for (item in DrillArticles.items()) {
            assertEquals(
                "Item ${item.id}: miss lines must cover exactly the wrong choices",
                (item.choices - item.answer).toSet(),
                item.missByChoice.keys,
            )
            assertTrue(
                "Item ${item.id}: every miss line must be filled in",
                item.missByChoice.values.all { it.isNotBlank() },
            )
        }
    }

    @Test
    fun missLinesFollowTheAuthoredPerRuleWording() {
        val items = DrillArticles.items().associateBy { it.id.key }

        // A suffix rule: the wrong article's gender, the suffix's gender, the right one.
        val zeitung = items.getValue("art:zeitung")
        assertEquals("art-gender-suffix-ung", zeitung.ruleId)
        assertEquals(
            "masculine. Nouns ending in -ung are feminine, so die.",
            zeitung.missByChoice.getValue("der"),
        )

        // A fixed-gender noun: its wording names the noun itself.
        val tisch = items.getValue("art:tisch")
        assertEquals("art-gender-fixed", tisch.ruleId)
        assertEquals("feminine. Tisch is masculine, so der.", tisch.missByChoice.getValue("die"))
        assertEquals("neuter. Tisch is masculine, so der.", tisch.missByChoice.getValue("das"))

        // People nouns: one pattern for male, one for female nouns.
        val mann = items.getValue("art:mann")
        assertEquals("art-gender-natural", mann.ruleId)
        assertEquals(
            "feminine. A male person usually takes the masculine article, so der.",
            mann.missByChoice.getValue("die"),
        )
        val mutter = items.getValue("art:mutter")
        assertEquals(
            "masculine. A female person usually takes the feminine article, so die.",
            mutter.missByChoice.getValue("der"),
        )

        // The misleading-endings rule: the misleading line names the item's own ending.
        val baum = items.getValue("art:baum")
        assertEquals("art-gender-suffix-exception", baum.ruleId)
        assertEquals(
            "neuter. Nouns ending in -um usually are, but Baum is masculine, so der.",
            baum.missByChoice.getValue("das"),
        )
    }

    @Test
    fun savedWordItemsCarryNoRuleId() {
        val savedWords = listOf(
            Word(id = 42L, text = "der Tisch", dueDay = 1L, addedAt = 1000L),
        )
        val items = DrillArticles.items(savedWords)
        val tisch = items.first { it.id.key == "42" }
        assertNull("Saved-word item must carry no rule id", tisch.ruleId)
        assertTrue("Saved-word item must carry no miss lines", tisch.missByChoice.isEmpty())
    }
}
