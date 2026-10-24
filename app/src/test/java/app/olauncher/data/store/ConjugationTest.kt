package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConjugationTest {

    private val machenRows = listOf(
        "mache" to "first-person indicative present singular",
        "machst" to "indicative present second-person singular",
        "macht" to "indicative present singular third-person",
        "machen" to "first-person indicative plural present",
        "macht" to "indicative plural present second-person",
        "machen" to "indicative plural present third-person",
    )

    @Test
    fun `full paradigm drills the six cells in person and number order`() {
        val paradigm = Conjugation.parsePresent("machen", machenRows)!!
        assertEquals("machen", paradigm.lemma)
        assertEquals(
            listOf(
                DrillPrompt("ich", "mache"),
                DrillPrompt("du", "machst"),
                DrillPrompt("er/sie/es", "macht"),
                DrillPrompt("wir", "machen"),
                DrillPrompt("ihr", "macht"),
                DrillPrompt("sie/Sie", "machen"),
            ),
            Conjugation.drillPrompts(paradigm),
        )
    }

    @Test
    fun `present rows without an indicative tag still parse`() {
        // Kaikki headword summaries drop "indicative": gehen's "geht" is only
        // "present singular third-person".
        val paradigm = Conjugation.parsePresent("gehen", listOf("geht" to "present singular third-person"))
        assertEquals(listOf(PresentForm(3, false, "geht")), paradigm!!.present)
    }

    @Test
    fun `other moods and tenses never produce cells`() {
        val rows = listOf(
            "gemacht" to "past participle",
            "machte" to "first-person indicative preterite singular",
            "machte" to "first-person singular subjunctive-ii",
            "mache" to "first-person present singular subjunctive-i",
            "mache" to "first-person present singular subjunctive",
            "mach" to "imperative second-person singular",
            "machen" to "infinitive",
        )
        assertNull(Conjugation.parsePresent("machen", rows))
    }

    @Test
    fun `defective verb with one present cell gives a one-prompt drill`() {
        val paradigm = Conjugation.parsePresent(
            "geschehen",
            listOf("geschieht" to "indicative present singular third-person"),
        )!!
        assertEquals(listOf(PresentForm(3, false, "geschieht")), paradigm.present)
        assertEquals(listOf(DrillPrompt("er/sie/es", "geschieht")), Conjugation.drillPrompts(paradigm))
    }

    @Test
    fun `no present rows means no paradigm`() {
        assertNull(Conjugation.parsePresent("gehen", emptyList()))
        assertNull(
            Conjugation.parsePresent(
                "gehen",
                listOf("ging" to "first-person indicative preterite singular"),
            ),
        )
    }

    @Test
    fun `homograph forms land in their distinct cells`() {
        val paradigm = Conjugation.parsePresent(
            "machen",
            listOf(
                "macht" to "indicative present singular third-person",
                "macht" to "indicative plural present second-person",
            ),
        )!!
        assertEquals(
            listOf(PresentForm(3, false, "macht"), PresentForm(2, true, "macht")),
            paradigm.present,
        )
    }

    @Test
    fun `cell conflict prefers the single-word form`() {
        val paradigm = Conjugation.parsePresent(
            "sich freuen",
            listOf(
                "freue mich" to "first-person indicative present reflexive singular",
                "freue" to "first-person indicative present singular",
            ),
        )!!
        assertEquals(listOf(PresentForm(1, false, "freue")), paradigm.present)
    }

    @Test
    fun `cell conflict prefers the separable split over the joined form`() {
        val paradigm = Conjugation.parsePresent(
            "übersetzen",
            listOf(
                "übersetze" to "first-person indicative present singular",
                "setze über" to "first-person indicative present singular",
                "übersetzt" to "indicative present second-person singular",
                "setzt über" to "indicative present second-person singular",
                "übersetzt" to "indicative present singular third-person",
                "setzt über" to "indicative present singular third-person",
            ),
        )!!
        assertEquals(
            listOf(
                PresentForm(1, false, "setze über"),
                PresentForm(2, false, "setzt über"),
                PresentForm(3, false, "setzt über"),
            ),
            paradigm.present,
        )
    }

    @Test
    fun `cell conflict between same-shape forms breaks the tie deterministically`() {
        val singleWord = Conjugation.parsePresent(
            "x",
            listOf(
                "b" to "first-person present singular",
                "a" to "first-person present singular",
            ),
        )!!
        assertEquals("a", singleWord.present.single().form)
        val multiWord = Conjugation.parsePresent(
            "x",
            listOf(
                "y z" to "first-person present singular",
                "a z" to "first-person present singular",
            ),
        )!!
        assertEquals("a z", multiWord.present.single().form)
    }

    @Test
    fun `pronoun labels are exact`() {
        assertEquals("ich", Conjugation.pronoun(1, false))
        assertEquals("du", Conjugation.pronoun(2, false))
        assertEquals("er/sie/es", Conjugation.pronoun(3, false))
        assertEquals("wir", Conjugation.pronoun(1, true))
        assertEquals("ihr", Conjugation.pronoun(2, true))
        assertEquals("sie/Sie", Conjugation.pronoun(3, true))
    }

    @Test
    fun `malformed tag strings are skipped without crashing`() {
        val paradigm = Conjugation.parsePresent(
            "gehen",
            listOf(
                "geht" to "",
                "geht" to "frobnicated nonsense",
                "geht" to "first-person present",
                "geht" to "present singular",
                "geht" to "  present   singular   third-person  ",
            ),
        )!!
        assertEquals(listOf(PresentForm(3, false, "geht")), paradigm.present)
    }

    private val machenPreteriteRows = listOf(
        "machte" to "first-person indicative preterite singular",
        "machtest" to "indicative preterite second-person singular",
        "machte" to "indicative preterite singular third-person",
        "machten" to "first-person indicative plural preterite",
        "machtet" to "indicative plural preterite second-person",
        "machten" to "indicative plural preterite third-person",
    )

    private val machenSubjunctiveIiRows = listOf(
        "machte" to "first-person singular subjunctive subjunctive-ii",
        "machtest" to "second-person singular subjunctive subjunctive-ii",
        "machte" to "singular subjunctive subjunctive-ii third-person",
        "machten" to "first-person plural subjunctive subjunctive-ii",
        "machtet" to "plural second-person subjunctive subjunctive-ii",
        "machten" to "plural subjunctive subjunctive-ii third-person",
    )

    @Test
    fun `machen preterite parses all six cells in person and number order`() {
        val paradigm = Conjugation.parsePreterite("machen", machenPreteriteRows)!!
        assertEquals("machen", paradigm.lemma)
        assertEquals(Tense.PRETERITE, paradigm.tense)
        assertEquals(
            listOf(
                DrillPrompt("ich", "machte"),
                DrillPrompt("du", "machtest"),
                DrillPrompt("er/sie/es", "machte"),
                DrillPrompt("wir", "machten"),
                DrillPrompt("ihr", "machtet"),
                DrillPrompt("sie/Sie", "machten"),
            ),
            Conjugation.drillPrompts(paradigm),
        )
    }

    @Test
    fun `machen subjunctive-ii parses all six cells in person and number order`() {
        val paradigm = Conjugation.parseSubjunctiveIi("machen", machenSubjunctiveIiRows)!!
        assertEquals("machen", paradigm.lemma)
        assertEquals(Tense.SUBJUNCTIVE_II, paradigm.tense)
        assertEquals(
            listOf(
                DrillPrompt("ich", "machte"),
                DrillPrompt("du", "machtest"),
                DrillPrompt("er/sie/es", "machte"),
                DrillPrompt("wir", "machten"),
                DrillPrompt("ihr", "machtet"),
                DrillPrompt("sie/Sie", "machten"),
            ),
            Conjugation.drillPrompts(paradigm),
        )
    }

    private val gehenPreteriteRows = listOf(
        "ging" to "first-person indicative preterite singular",
        "gingst" to "indicative preterite second-person singular",
        "ging" to "indicative preterite singular third-person",
        "gingen" to "first-person indicative plural preterite",
        "gingt" to "indicative plural preterite second-person",
        "gingen" to "indicative plural preterite third-person",
    )

    private val gehenSubjunctiveIiRows = listOf(
        "ginge" to "first-person singular subjunctive subjunctive-ii",
        "gingest" to "second-person singular subjunctive subjunctive-ii",
        "gingst" to "second-person singular subjunctive subjunctive-ii",
        "ginge" to "singular subjunctive subjunctive-ii third-person",
        "gingen" to "first-person plural subjunctive subjunctive-ii",
        "ginget" to "plural second-person subjunctive subjunctive-ii",
        "gingt" to "plural second-person subjunctive subjunctive-ii",
        "gingen" to "plural subjunctive subjunctive-ii third-person",
    )

    @Test
    fun `gehen parses correctly in both preterite and subjunctive-ii`() {
        val preterite = Conjugation.parsePreterite("gehen", gehenPreteriteRows)!!
        assertEquals(
            listOf(
                ParadigmCell(1, false, "ging"),
                ParadigmCell(2, false, "gingst"),
                ParadigmCell(3, false, "ging"),
                ParadigmCell(1, true, "gingen"),
                ParadigmCell(2, true, "gingt"),
                ParadigmCell(3, true, "gingen"),
            ),
            preterite.cells,
        )

        val subjunctiveIi = Conjugation.parseSubjunctiveIi("gehen", gehenSubjunctiveIiRows)!!
        assertEquals(
            listOf(
                ParadigmCell(1, false, "ginge"),
                ParadigmCell(2, false, "gingest"),
                ParadigmCell(3, false, "ginge"),
                ParadigmCell(1, true, "gingen"),
                ParadigmCell(2, true, "ginget"),
                ParadigmCell(3, true, "gingen"),
            ),
            subjunctiveIi.cells,
        )
    }

    private val seinPreteriteRows = listOf(
        "war" to "first-person indicative preterite singular",
        "warst" to "indicative preterite second-person singular",
        "war" to "indicative preterite singular third-person",
        "waren" to "first-person indicative plural preterite",
        "wart" to "indicative plural preterite second-person",
        "waren" to "indicative plural preterite third-person",
    )

    private val seinSubjunctiveIiRows = listOf(
        "wäre" to "first-person singular subjunctive subjunctive-ii",
        "wärest" to "second-person singular subjunctive subjunctive-ii",
        "wärst" to "second-person singular subjunctive subjunctive-ii",
        "wäre" to "singular subjunctive subjunctive-ii third-person",
        "wären" to "first-person plural subjunctive subjunctive-ii",
        "wäret" to "plural second-person subjunctive subjunctive-ii",
        "wärt" to "plural second-person subjunctive subjunctive-ii",
        "wären" to "plural subjunctive subjunctive-ii third-person",
    )

    @Test
    fun `sein parses correctly in both preterite and subjunctive-ii`() {
        val preterite = Conjugation.parsePreterite("sein", seinPreteriteRows)!!
        assertEquals(
            listOf(
                ParadigmCell(1, false, "war"),
                ParadigmCell(2, false, "warst"),
                ParadigmCell(3, false, "war"),
                ParadigmCell(1, true, "waren"),
                ParadigmCell(2, true, "wart"),
                ParadigmCell(3, true, "waren"),
            ),
            preterite.cells,
        )

        val subjunctiveIi = Conjugation.parseSubjunctiveIi("sein", seinSubjunctiveIiRows)!!
        assertEquals(
            listOf(
                ParadigmCell(1, false, "wäre"),
                ParadigmCell(2, false, "wärest"),
                ParadigmCell(3, false, "wäre"),
                ParadigmCell(1, true, "wären"),
                ParadigmCell(2, true, "wäret"),
                ParadigmCell(3, true, "wären"),
            ),
            subjunctiveIi.cells,
        )
    }

    private val aufstehenPreteriteRows = listOf(
        "aufstand" to "first-person indicative preterite singular",
        "stand auf" to "first-person indicative preterite singular",
        "aufstandest" to "indicative preterite second-person singular",
        "standest auf" to "indicative preterite second-person singular",
        "aufstand" to "indicative preterite singular third-person",
        "stand auf" to "indicative preterite singular third-person",
        "aufstanden" to "first-person indicative plural preterite",
        "standen auf" to "first-person indicative plural preterite",
        "aufstandet" to "indicative plural preterite second-person",
        "standet auf" to "indicative plural preterite second-person",
        "aufstanden" to "indicative plural preterite third-person",
        "standen auf" to "indicative plural preterite third-person",
    )

    private val aufstehenSubjunctiveIiRows = listOf(
        "aufstände" to "first-person singular subjunctive subjunctive-ii",
        "stände auf" to "first-person singular subjunctive subjunctive-ii",
        "aufständest" to "second-person singular subjunctive subjunctive-ii",
        "ständest auf" to "second-person singular subjunctive subjunctive-ii",
        "aufstände" to "singular subjunctive subjunctive-ii third-person",
        "stände auf" to "singular subjunctive subjunctive-ii third-person",
        "aufständen" to "first-person plural subjunctive subjunctive-ii",
        "ständen auf" to "first-person plural subjunctive subjunctive-ii",
        "aufständet" to "plural second-person subjunctive subjunctive-ii",
        "ständet auf" to "plural second-person subjunctive subjunctive-ii",
        "aufständen" to "plural subjunctive subjunctive-ii third-person",
        "ständen auf" to "plural subjunctive subjunctive-ii third-person",
    )

    @Test
    fun `separable verb prefers split form in both preterite and subjunctive-ii`() {
        val preterite = Conjugation.parsePreterite("aufstehen", aufstehenPreteriteRows)!!
        assertEquals(
            listOf(
                ParadigmCell(1, false, "stand auf"),
                ParadigmCell(2, false, "standest auf"),
                ParadigmCell(3, false, "stand auf"),
                ParadigmCell(1, true, "standen auf"),
                ParadigmCell(2, true, "standet auf"),
                ParadigmCell(3, true, "standen auf"),
            ),
            preterite.cells,
        )

        val subjunctiveIi = Conjugation.parseSubjunctiveIi("aufstehen", aufstehenSubjunctiveIiRows)!!
        assertEquals(
            listOf(
                ParadigmCell(1, false, "stände auf"),
                ParadigmCell(2, false, "ständest auf"),
                ParadigmCell(3, false, "stände auf"),
                ParadigmCell(1, true, "ständen auf"),
                ParadigmCell(2, true, "ständet auf"),
                ParadigmCell(3, true, "ständen auf"),
            ),
            subjunctiveIi.cells,
        )
    }

    @Test
    fun `parseAll only offers tenses that have cells`() {
        val combinedRows = machenRows + machenPreteriteRows
        val paradigms = Conjugation.parseAll("machen", combinedRows)
        assertEquals(setOf(Tense.PRESENT, Tense.PRETERITE), paradigms.keys)
        assertNull(Conjugation.parseSubjunctiveIi("machen", combinedRows))
    }

    @Test
    fun `tense parser skips rows of conflicting tenses`() {
        assertNull(Conjugation.parsePreterite("gehen", machenRows))
        assertNull(Conjugation.parseSubjunctiveIi("gehen", machenPreteriteRows))
        assertNull(Conjugation.parseImperative("gehen", machenRows))
        assertNull(Conjugation.parseImperative("gehen", machenPreteriteRows))
    }

    private val machenImperativeRows = listOf(
        "mach" to "imperative second-person singular",
        "mache" to "imperative second-person singular",
        "macht" to "imperative plural second-person",
    )

    @Test
    fun `regular verb machen parses imperative cells with Sie built from lemma`() {
        val imperative = Conjugation.parseImperative("machen", machenImperativeRows)!!
        assertEquals("machen", imperative.lemma)
        assertEquals(Tense.IMPERATIVE, imperative.tense)
        assertEquals(
            listOf(
                ParadigmCell(2, false, "mach"),
                ParadigmCell(2, true, "macht"),
                ParadigmCell(3, true, "machen Sie"),
            ),
            imperative.cells,
        )
        assertEquals(
            listOf(
                DrillPrompt("du", "mach"),
                DrillPrompt("ihr", "macht"),
                DrillPrompt("Sie", "machen Sie"),
            ),
            Conjugation.drillPrompts(imperative),
        )
    }

    @Test
    fun `regular verb machen parses stammformen from past and participle fields`() {
        val stammformen = Conjugation.parseStammformen("machen", "machte", "gemacht")!!
        assertEquals("machen", stammformen.lemma)
        assertEquals(Tense.STAMMFORMEN, stammformen.tense)
        assertEquals(
            listOf(
                ParadigmCell(1, false, "machte"),
                ParadigmCell(2, false, "gemacht"),
            ),
            stammformen.cells,
        )
        assertEquals(
            listOf(
                DrillPrompt("Präteritum", "machte"),
                DrillPrompt("Partizip II", "gemacht"),
            ),
            Conjugation.drillPrompts(stammformen),
        )
    }

    private val gehenImperativeRows = listOf(
        "geh" to "imperative second-person singular",
        "gehe" to "imperative second-person singular",
        "geht" to "imperative plural second-person",
    )

    @Test
    fun `strong verb gehen parses imperative and stammformen correctly`() {
        val imperative = Conjugation.parseImperative("gehen", gehenImperativeRows)!!
        assertEquals("gehen", imperative.lemma)
        assertEquals(
            listOf(
                ParadigmCell(2, false, "geh"),
                ParadigmCell(2, true, "geht"),
                ParadigmCell(3, true, "gehen Sie"),
            ),
            imperative.cells,
        )
        assertEquals(
            listOf(
                DrillPrompt("du", "geh"),
                DrillPrompt("ihr", "geht"),
                DrillPrompt("Sie", "gehen Sie"),
            ),
            Conjugation.drillPrompts(imperative),
        )

        val stammformen = Conjugation.parseStammformen("gehen", "ging", "gegangen")!!
        assertEquals(
            listOf(
                ParadigmCell(1, false, "ging"),
                ParadigmCell(2, false, "gegangen"),
            ),
            stammformen.cells,
        )
        assertEquals(
            listOf(
                DrillPrompt("Präteritum", "ging"),
                DrillPrompt("Partizip II", "gegangen"),
            ),
            Conjugation.drillPrompts(stammformen),
        )
    }

    private val seinImperativeRows = listOf(
        "sei" to "imperative second-person singular",
        "seid" to "imperative plural second-person",
    )

    @Test
    fun `irregular verb sein parses imperative and stammformen correctly`() {
        val imperative = Conjugation.parseImperative("sein", seinImperativeRows)!!
        assertEquals(
            listOf(
                ParadigmCell(2, false, "sei"),
                ParadigmCell(2, true, "seid"),
                ParadigmCell(3, true, "sein Sie"),
            ),
            imperative.cells,
        )

        val stammformen = Conjugation.parseStammformen("sein", "war", "gewesen")!!
        assertEquals(
            listOf(
                ParadigmCell(1, false, "war"),
                ParadigmCell(2, false, "gewesen"),
            ),
            stammformen.cells,
        )
    }

    private val aufstehenImperativeRows = listOf(
        "steh auf" to "imperative second-person singular",
        "stehe auf" to "imperative second-person singular",
        "steht auf" to "imperative plural second-person",
    )

    @Test
    fun `separable verb aufstehen prefers split imperative and builds Sie with infinitive`() {
        val imperative = Conjugation.parseImperative("aufstehen", aufstehenImperativeRows)!!
        assertEquals(
            listOf(
                ParadigmCell(2, false, "steh auf"),
                ParadigmCell(2, true, "steht auf"),
                ParadigmCell(3, true, "aufstehen Sie"),
            ),
            imperative.cells,
        )
        assertEquals(
            listOf(
                DrillPrompt("du", "steh auf"),
                DrillPrompt("ihr", "steht auf"),
                DrillPrompt("Sie", "aufstehen Sie"),
            ),
            Conjugation.drillPrompts(imperative),
        )

        val stammformen = Conjugation.parseStammformen("aufstehen", "stand auf", "aufgestanden")!!
        assertEquals(
            listOf(
                ParadigmCell(1, false, "stand auf"),
                ParadigmCell(2, false, "aufgestanden"),
            ),
            stammformen.cells,
        )
        assertEquals(
            listOf(
                DrillPrompt("Präteritum", "stand auf"),
                DrillPrompt("Partizip II", "aufgestanden"),
            ),
            Conjugation.drillPrompts(stammformen),
        )
    }

    @Test
    fun `imperative parser builds Sie using explicit infinitive row if available`() {
        val rowsWithInfinitive = aufstehenImperativeRows + listOf("aufstehen" to "infinitive")
        val imperative = Conjugation.parseImperative("aufstehen", rowsWithInfinitive)!!
        assertEquals("aufstehen Sie", imperative.cells.first { it.person == 3 && it.plural }.form)
    }

    @Test
    fun `parseAll offers all five tenses when imperative and stammformen data exist`() {
        val combinedRows = machenRows + machenPreteriteRows + machenSubjunctiveIiRows + machenImperativeRows
        val paradigms = Conjugation.parseAll("machen", combinedRows, "machte", "gemacht")
        assertEquals(
            setOf(Tense.PRESENT, Tense.PRETERITE, Tense.SUBJUNCTIVE_II, Tense.IMPERATIVE, Tense.STAMMFORMEN),
            paradigms.keys,
        )
    }

    @Test
    fun `parseAll omits imperative when no imperative rows exist`() {
        val combinedRows = machenRows + machenPreteriteRows
        val paradigms = Conjugation.parseAll("machen", combinedRows, "machte", "gemacht")
        assertEquals(
            setOf(Tense.PRESENT, Tense.PRETERITE, Tense.STAMMFORMEN),
            paradigms.keys,
        )
        assertNull(Conjugation.parseImperative("machen", combinedRows))
    }

    @Test
    fun `parseAll omits stammformen when past or participle is empty`() {
        val combinedRows = machenRows + machenImperativeRows
        val noPast = Conjugation.parseAll("machen", combinedRows, "", "gemacht")
        assertEquals(setOf(Tense.PRESENT, Tense.IMPERATIVE), noPast.keys)

        val noParticiple = Conjugation.parseAll("machen", combinedRows, "machte", "")
        assertEquals(setOf(Tense.PRESENT, Tense.IMPERATIVE), noParticiple.keys)

        val neither = Conjugation.parseAll("machen", combinedRows, "", "")
        assertEquals(setOf(Tense.PRESENT, Tense.IMPERATIVE), neither.keys)
    }

    @Test
    fun `parseStammformen returns null when either past or participle is blank`() {
        assertNull(Conjugation.parseStammformen("machen", "", "gemacht"))
        assertNull(Conjugation.parseStammformen("machen", "   ", "gemacht"))
        assertNull(Conjugation.parseStammformen("machen", "machte", ""))
        assertNull(Conjugation.parseStammformen("machen", "machte", "   "))
        assertNull(Conjugation.parseStammformen("machen", "", ""))
    }
}
