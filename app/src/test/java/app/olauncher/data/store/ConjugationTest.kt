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
}
