package app.olauncher.data.store

import app.olauncher.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrillPronounsTest {

    @Test
    fun allItemsPassValidation() {
        val items = DrillPronouns.items()
        assertTrue("Expected at least 60 items, found ${items.size}", items.size >= 60)
        for (item in items) {
            val check = item.validate()
            assertTrue(
                "Item ${item.id} failed validation: ${check.problems.joinToString()}",
                check.isValid,
            )
            assertTrue(
                "Item ${item.id} must contain the gap marker '${DrillItem.GAP_MARKER}'",
                DrillItem.GAP_MARKER in item.prompt,
            )
        }
    }

    @Test
    fun personalPronounsCoverAllPersonsAndThreeCases() {
        val personalEntries = DrillPronouns.ENTRIES.filter { it.category == DrillPronouns.Category.PERSONAL }
        val allTemplates = personalEntries.flatMap { it.templates }

        val expectedPersons = listOf("1sg", "2sg", "3sg_m", "3sg_f", "3sg_n", "1pl", "2pl", "3pl", "formal")
        val expectedCases = listOf("Nom", "Akk", "Dat")

        for (person in expectedPersons) {
            for (c in expectedCases) {
                val matches = allTemplates.filter { it.person == person && it.grammaticalCase == c }
                assertTrue(
                    "Missing personal pronoun item for person $person in case $c",
                    matches.isNotEmpty(),
                )
            }
        }

        // Spot-check expected answers for every person in Nom, Akk, Dat
        val expectedAnswers = mapOf(
            ("1sg" to "Nom") to "ich",
            ("1sg" to "Akk") to "mich",
            ("1sg" to "Dat") to "mir",
            ("2sg" to "Nom") to "du",
            ("2sg" to "Akk") to "dich",
            ("2sg" to "Dat") to "dir",
            ("3sg_m" to "Nom") to "er",
            ("3sg_m" to "Akk") to "ihn",
            ("3sg_m" to "Dat") to "ihm",
            ("3sg_f" to "Nom") to "sie",
            ("3sg_f" to "Akk") to "sie",
            ("3sg_f" to "Dat") to "ihr",
            ("3sg_n" to "Nom") to "Es",
            ("3sg_n" to "Akk") to "es",
            ("3sg_n" to "Dat") to "ihm",
            ("1pl" to "Nom") to "wir",
            ("1pl" to "Akk") to "uns",
            ("1pl" to "Dat") to "uns",
            ("2pl" to "Nom") to "ihr",
            ("2pl" to "Akk") to "euch",
            ("2pl" to "Dat") to "euch",
            ("3pl" to "Nom") to "sie",
            ("3pl" to "Akk") to "sie",
            ("3pl" to "Dat") to "ihnen",
            ("formal" to "Nom") to "Sie",
            ("formal" to "Akk") to "Sie",
            ("formal" to "Dat") to "Ihnen",
        )

        for ((key, expectedAnswer) in expectedAnswers) {
            val (person, c) = key
            val template = allTemplates.first { it.person == person && it.grammaticalCase == c }
            assertEquals(
                "Expected $expectedAnswer for person $person and case $c",
                expectedAnswer,
                template.answer,
            )
        }
    }

    @Test
    fun reflexivePronounsCoverAccusativeVsDativeAndCommonVerbs() {
        val reflexiveEntries = DrillPronouns.ENTRIES.filter { it.category == DrillPronouns.Category.REFLEXIVE }
        val allTemplates = reflexiveEntries.flatMap { it.templates }

        // Accusative vs Dative reflexive contrast:
        // Check waschen contrast (ich: mich vs mir; du: dich vs dir)
        val waschenAkk1sg = allTemplates.find { it.verb == "sich waschen" && it.person == "1sg" && it.grammaticalCase == "Akk" }
        val waschenDat1sg = allTemplates.find { it.verb == "sich waschen" && it.person == "1sg" && it.grammaticalCase == "Dat" }
        assertNotNull("Must have accusative reflexive item for waschen (1sg)", waschenAkk1sg)
        assertNotNull("Must have dative reflexive item for waschen (1sg)", waschenDat1sg)
        assertEquals("mich", waschenAkk1sg?.answer)
        assertEquals("mir", waschenDat1sg?.answer)

        val waschenAkk2sg = allTemplates.find { it.verb == "sich waschen" && it.person == "2sg" && it.grammaticalCase == "Akk" }
        val waschenDat2sg = allTemplates.find { it.verb == "sich waschen" && it.person == "2sg" && it.grammaticalCase == "Dat" }
        assertNotNull("Must have accusative reflexive item for waschen (2sg)", waschenAkk2sg)
        assertNotNull("Must have dative reflexive item for waschen (2sg)", waschenDat2sg)
        assertEquals("dich", waschenAkk2sg?.answer)
        assertEquals("dir", waschenDat2sg?.answer)

        // At least 15 common reflexive verbs
        val distinctVerbs = allTemplates.mapNotNull { it.verb }.distinct()
        assertTrue(
            "Expected at least 15 common reflexive verbs, found ${distinctVerbs.size}: $distinctVerbs",
            distinctVerbs.size >= 15,
        )

        // Check required named verbs from issue description
        val requiredNamedVerbs = listOf(
            "sich freuen",
            "sich erinnern an",
            "sich vorstellen",
            "sich beeilen",
            "sich Sorgen machen",
        )
        for (verb in requiredNamedVerbs) {
            assertTrue("Expected reflexive verb '$verb' to be present", verb in distinctVerbs)
        }

        // Check forms per person are represented in reflexive items
        val answers = allTemplates.map { it.answer }.toSet()
        val expectedReflexiveForms = setOf("mich", "mir", "dich", "dir", "sich", "uns", "euch")
        for (form in expectedReflexiveForms) {
            assertTrue("Expected reflexive form '$form' to be tested", form in answers)
        }
    }

    @Test
    fun possessivesCoverDeclensionsAndEDrop() {
        val possessiveEntries = DrillPronouns.ENTRIES.filter { it.category == DrillPronouns.Category.POSSESSIVE }
        val allTemplates = possessiveEntries.flatMap { it.templates }

        assertTrue(
            "Expected at least 24 possessive items, found ${allTemplates.size}",
            allTemplates.size >= 24,
        )

        // Check mein declension covers mein, meinen, meinem, meiner, meines
        val meinAnswers = allTemplates.filter { it.answer.startsWith("mein") }.map { it.answer }.toSet()
        val expectedMeinForms = setOf("mein", "meine", "meinen", "meinem", "meiner", "meines")
        for (form in expectedMeinForms) {
            assertTrue("Missing possessive form '$form'", form in meinAnswers)
        }

        // Check unser- items present
        val unserAnswers = allTemplates.filter { it.answer.startsWith("unser") }.map { it.answer }.toSet()
        assertTrue("Expected unser- forms, found $unserAnswers", unserAnswers.isNotEmpty())
        assertTrue("unser" in unserAnswers)
        assertTrue("unsere" in unserAnswers)
        assertTrue("unseren" in unserAnswers)
        assertTrue("unserem" in unserAnswers)

        // Check euer- with e-drop "eure"
        val euerAnswers = allTemplates.filter { it.answer.startsWith("eu") }.map { it.answer }.toSet()
        assertTrue("Expected euer- forms, found $euerAnswers", euerAnswers.isNotEmpty())
        assertTrue("euer" in euerAnswers)
        assertTrue("eure" in euerAnswers)
        assertTrue("euren" in euerAnswers)
        assertTrue("eurem" in euerAnswers)
        assertTrue("eurer" in euerAnswers)

        // Specifically test e-drop items
        val eDropItems = allTemplates.filter { it.answer == "eure" }
        assertTrue("Expected items testing e-drop 'eure'", eDropItems.isNotEmpty())
    }

    @Test
    fun distractorsComeFromSamePronounFamily() {
        for (entry in DrillPronouns.ENTRIES) {
            val allowedPool = when (entry.category) {
                DrillPronouns.Category.PERSONAL -> DrillPronouns.PERSONAL_PRONOUNS
                DrillPronouns.Category.REFLEXIVE -> DrillPronouns.REFLEXIVE_PRONOUNS
                DrillPronouns.Category.POSSESSIVE -> DrillPronouns.POSSESSIVE_PRONOUNS
            }

            for (template in entry.templates) {
                for (choice in template.choices) {
                    assertTrue(
                        "Choice '$choice' in template '${template.prompt}' (category ${entry.category}) not in allowed pool: $allowedPool",
                        choice in allowedPool,
                    )
                }
            }
        }
    }

    @Test
    fun keysUseTableFormatWhenNoMatchingSavedWord() {
        val items = DrillPronouns.items(emptyList())
        for (item in items) {
            val key = item.id.key
            assertTrue(
                "Key '$key' should match 'pron:<tag>:<n>'",
                key.matches(Regex("pron:[a-zA-Z0-9äöüÄÖÜß_ ]+:\\d+")),
            )
        }

        // Spot-check specific entries
        val ichItems = items.filter { it.id.key.startsWith("pron:ich:") }
        assertEquals(3, ichItems.size)
        assertEquals("pron:ich:0", ichItems[0].id.key)
        assertEquals("pron:ich:1", ichItems[1].id.key)
        assertEquals("pron:ich:2", ichItems[2].id.key)

        val freuenItems = items.filter { it.id.key.startsWith("pron:sich freuen:") }
        assertEquals(1, freuenItems.size)
        assertEquals("pron:sich freuen:0", freuenItems[0].id.key)
    }

    @Test
    fun keysUseWordIdWhenMatchingSavedWordExists() {
        val savedWords = listOf(
            Word(id = 501L, text = "ich", dueDay = 0L, addedAt = 0L),
            Word(id = 502L, text = "sich freuen", dueDay = 0L, addedAt = 0L),
            Word(id = 503L, text = "euer", dueDay = 0L, addedAt = 0L),
        )

        val items = DrillPronouns.items(savedWords)

        // "ich" items should use word ID "501"
        val ichItems = items.filter { it.prompt.contains("ich") || it.answer == "ich" || it.answer == "mich" || it.answer == "mir" }
            .filter { it.choices == listOf("ich", "mich", "mir", "du") || it.choices == listOf("mich", "mir", "ich", "dich") || it.choices == listOf("mir", "mich", "ich", "dir") }
        assertEquals(3, ichItems.size)
        for (item in ichItems) {
            assertEquals("501", item.id.key)
        }

        // "sich freuen" item should use word ID "502"
        val freuenItem = items.first { it.prompt.contains("Wochenende") }
        assertEquals("502", freuenItem.id.key)

        // Other items without saved word match should retain "pron:<tag>:<n>"
        val duItems = items.filter { it.id.key.startsWith("pron:du:") }
        assertEquals(3, duItems.size)
        assertEquals("pron:du:0", duItems[0].id.key)
    }

    @Test
    fun drillTablesAvailableIncludesPronouns() {
        val available = DrillTables.available()
        assertTrue(DrillType.PRONOUNS in available)
    }

    @Test
    fun drillTablesItemsDelegatesToPronouns() {
        val directItems = DrillPronouns.items()
        val tableItems = DrillTables.items(DrillType.PRONOUNS)
        assertEquals(directItems.size, tableItems.size)
        for (i in directItems.indices) {
            assertEquals(directItems[i].id, tableItems[i].id)
            assertEquals(directItems[i].prompt, tableItems[i].prompt)
            assertEquals(directItems[i].answer, tableItems[i].answer)
            assertEquals(directItems[i].choices, tableItems[i].choices)
        }
    }

    @Test
    fun drillTablesLabelResReturnsPronounsString() {
        assertEquals(R.string.drill_type_pronouns, DrillTables.labelRes(DrillType.PRONOUNS))
    }
}
