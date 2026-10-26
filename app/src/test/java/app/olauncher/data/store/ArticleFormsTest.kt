package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArticleFormsTest {

    // --- Definite articles (16 cells) ---

    @Test
    fun definiteNominativePinsAllGenders() {
        assertEquals("der", ArticleForms.form(ArticleForms.Kind.DEFINITE, ArticleForms.Case.NOMINATIVE, ArticleForms.Gender.MASCULINE))
        assertEquals("die", ArticleForms.form(ArticleForms.Kind.DEFINITE, ArticleForms.Case.NOMINATIVE, ArticleForms.Gender.FEMININE))
        assertEquals("das", ArticleForms.form(ArticleForms.Kind.DEFINITE, ArticleForms.Case.NOMINATIVE, ArticleForms.Gender.NEUTER))
        assertEquals("die", ArticleForms.form(ArticleForms.Kind.DEFINITE, ArticleForms.Case.NOMINATIVE, ArticleForms.Gender.PLURAL))
    }

    @Test
    fun definiteAccusativePinsAllGenders() {
        assertEquals("den", ArticleForms.form(ArticleForms.Kind.DEFINITE, ArticleForms.Case.ACCUSATIVE, ArticleForms.Gender.MASCULINE))
        assertEquals("die", ArticleForms.form(ArticleForms.Kind.DEFINITE, ArticleForms.Case.ACCUSATIVE, ArticleForms.Gender.FEMININE))
        assertEquals("das", ArticleForms.form(ArticleForms.Kind.DEFINITE, ArticleForms.Case.ACCUSATIVE, ArticleForms.Gender.NEUTER))
        assertEquals("die", ArticleForms.form(ArticleForms.Kind.DEFINITE, ArticleForms.Case.ACCUSATIVE, ArticleForms.Gender.PLURAL))
    }

    @Test
    fun definiteDativePinsAllGenders() {
        assertEquals("dem", ArticleForms.form(ArticleForms.Kind.DEFINITE, ArticleForms.Case.DATIVE, ArticleForms.Gender.MASCULINE))
        assertEquals("der", ArticleForms.form(ArticleForms.Kind.DEFINITE, ArticleForms.Case.DATIVE, ArticleForms.Gender.FEMININE))
        assertEquals("dem", ArticleForms.form(ArticleForms.Kind.DEFINITE, ArticleForms.Case.DATIVE, ArticleForms.Gender.NEUTER))
        assertEquals("den", ArticleForms.form(ArticleForms.Kind.DEFINITE, ArticleForms.Case.DATIVE, ArticleForms.Gender.PLURAL))
    }

    @Test
    fun definiteGenitivePinsAllGenders() {
        assertEquals("des", ArticleForms.form(ArticleForms.Kind.DEFINITE, ArticleForms.Case.GENITIVE, ArticleForms.Gender.MASCULINE))
        assertEquals("der", ArticleForms.form(ArticleForms.Kind.DEFINITE, ArticleForms.Case.GENITIVE, ArticleForms.Gender.FEMININE))
        assertEquals("des", ArticleForms.form(ArticleForms.Kind.DEFINITE, ArticleForms.Case.GENITIVE, ArticleForms.Gender.NEUTER))
        assertEquals("der", ArticleForms.form(ArticleForms.Kind.DEFINITE, ArticleForms.Case.GENITIVE, ArticleForms.Gender.PLURAL))
    }

    // --- Indefinite articles (12 cells; plural is null) ---

    @Test
    fun indefiniteNominativePinsAllGenders() {
        assertEquals("ein", ArticleForms.form(ArticleForms.Kind.INDEFINITE, ArticleForms.Case.NOMINATIVE, ArticleForms.Gender.MASCULINE))
        assertEquals("eine", ArticleForms.form(ArticleForms.Kind.INDEFINITE, ArticleForms.Case.NOMINATIVE, ArticleForms.Gender.FEMININE))
        assertEquals("ein", ArticleForms.form(ArticleForms.Kind.INDEFINITE, ArticleForms.Case.NOMINATIVE, ArticleForms.Gender.NEUTER))
        assertNull(ArticleForms.form(ArticleForms.Kind.INDEFINITE, ArticleForms.Case.NOMINATIVE, ArticleForms.Gender.PLURAL))
    }

    @Test
    fun indefiniteAccusativePinsAllGenders() {
        assertEquals("einen", ArticleForms.form(ArticleForms.Kind.INDEFINITE, ArticleForms.Case.ACCUSATIVE, ArticleForms.Gender.MASCULINE))
        assertEquals("eine", ArticleForms.form(ArticleForms.Kind.INDEFINITE, ArticleForms.Case.ACCUSATIVE, ArticleForms.Gender.FEMININE))
        assertEquals("ein", ArticleForms.form(ArticleForms.Kind.INDEFINITE, ArticleForms.Case.ACCUSATIVE, ArticleForms.Gender.NEUTER))
        assertNull(ArticleForms.form(ArticleForms.Kind.INDEFINITE, ArticleForms.Case.ACCUSATIVE, ArticleForms.Gender.PLURAL))
    }

    @Test
    fun indefiniteDativePinsAllGenders() {
        assertEquals("einem", ArticleForms.form(ArticleForms.Kind.INDEFINITE, ArticleForms.Case.DATIVE, ArticleForms.Gender.MASCULINE))
        assertEquals("einer", ArticleForms.form(ArticleForms.Kind.INDEFINITE, ArticleForms.Case.DATIVE, ArticleForms.Gender.FEMININE))
        assertEquals("einem", ArticleForms.form(ArticleForms.Kind.INDEFINITE, ArticleForms.Case.DATIVE, ArticleForms.Gender.NEUTER))
        assertNull(ArticleForms.form(ArticleForms.Kind.INDEFINITE, ArticleForms.Case.DATIVE, ArticleForms.Gender.PLURAL))
    }

    @Test
    fun indefiniteGenitivePinsAllGenders() {
        assertEquals("eines", ArticleForms.form(ArticleForms.Kind.INDEFINITE, ArticleForms.Case.GENITIVE, ArticleForms.Gender.MASCULINE))
        assertEquals("einer", ArticleForms.form(ArticleForms.Kind.INDEFINITE, ArticleForms.Case.GENITIVE, ArticleForms.Gender.FEMININE))
        assertEquals("eines", ArticleForms.form(ArticleForms.Kind.INDEFINITE, ArticleForms.Case.GENITIVE, ArticleForms.Gender.NEUTER))
        assertNull(ArticleForms.form(ArticleForms.Kind.INDEFINITE, ArticleForms.Case.GENITIVE, ArticleForms.Gender.PLURAL))
    }

    // --- Kein- articles (16 cells) ---

    @Test
    fun keinNominativePinsAllGenders() {
        assertEquals("kein", ArticleForms.form(ArticleForms.Kind.KEIN, ArticleForms.Case.NOMINATIVE, ArticleForms.Gender.MASCULINE))
        assertEquals("keine", ArticleForms.form(ArticleForms.Kind.KEIN, ArticleForms.Case.NOMINATIVE, ArticleForms.Gender.FEMININE))
        assertEquals("kein", ArticleForms.form(ArticleForms.Kind.KEIN, ArticleForms.Case.NOMINATIVE, ArticleForms.Gender.NEUTER))
        assertEquals("keine", ArticleForms.form(ArticleForms.Kind.KEIN, ArticleForms.Case.NOMINATIVE, ArticleForms.Gender.PLURAL))
    }

    @Test
    fun keinAccusativePinsAllGenders() {
        assertEquals("keinen", ArticleForms.form(ArticleForms.Kind.KEIN, ArticleForms.Case.ACCUSATIVE, ArticleForms.Gender.MASCULINE))
        assertEquals("keine", ArticleForms.form(ArticleForms.Kind.KEIN, ArticleForms.Case.ACCUSATIVE, ArticleForms.Gender.FEMININE))
        assertEquals("kein", ArticleForms.form(ArticleForms.Kind.KEIN, ArticleForms.Case.ACCUSATIVE, ArticleForms.Gender.NEUTER))
        assertEquals("keine", ArticleForms.form(ArticleForms.Kind.KEIN, ArticleForms.Case.ACCUSATIVE, ArticleForms.Gender.PLURAL))
    }

    @Test
    fun keinDativePinsAllGenders() {
        assertEquals("keinem", ArticleForms.form(ArticleForms.Kind.KEIN, ArticleForms.Case.DATIVE, ArticleForms.Gender.MASCULINE))
        assertEquals("keiner", ArticleForms.form(ArticleForms.Kind.KEIN, ArticleForms.Case.DATIVE, ArticleForms.Gender.FEMININE))
        assertEquals("keinem", ArticleForms.form(ArticleForms.Kind.KEIN, ArticleForms.Case.DATIVE, ArticleForms.Gender.NEUTER))
        assertEquals("keinen", ArticleForms.form(ArticleForms.Kind.KEIN, ArticleForms.Case.DATIVE, ArticleForms.Gender.PLURAL))
    }

    @Test
    fun keinGenitivePinsAllGenders() {
        assertEquals("keines", ArticleForms.form(ArticleForms.Kind.KEIN, ArticleForms.Case.GENITIVE, ArticleForms.Gender.MASCULINE))
        assertEquals("keiner", ArticleForms.form(ArticleForms.Kind.KEIN, ArticleForms.Case.GENITIVE, ArticleForms.Gender.FEMININE))
        assertEquals("keines", ArticleForms.form(ArticleForms.Kind.KEIN, ArticleForms.Case.GENITIVE, ArticleForms.Gender.NEUTER))
        assertEquals("keiner", ArticleForms.form(ArticleForms.Kind.KEIN, ArticleForms.Case.GENITIVE, ArticleForms.Gender.PLURAL))
    }

    // --- Exhaustive matrix verification ---

    @Test
    fun pinsEverySingleCellDefinite16Indefinite12Kein16() {
        var definiteCount = 0
        var indefiniteCount = 0
        var keinCount = 0

        for (case in ArticleForms.Case.values()) {
            for (gender in ArticleForms.Gender.values()) {
                val def = ArticleForms.form(ArticleForms.Kind.DEFINITE, case, gender)
                if (def != null) definiteCount++

                val indef = ArticleForms.form(ArticleForms.Kind.INDEFINITE, case, gender)
                if (indef != null) indefiniteCount++

                val kein = ArticleForms.form(ArticleForms.Kind.KEIN, case, gender)
                if (kein != null) keinCount++
            }
        }

        assertEquals("Definite must have exactly 16 cells", 16, definiteCount)
        assertEquals("Indefinite must have exactly 12 cells (no plural)", 12, indefiniteCount)
        assertEquals("Kein- must have exactly 16 cells", 16, keinCount)
    }

    // --- String-based lookup helper ---

    @Test
    fun stringOverloadParsesVariations() {
        assertEquals("den", ArticleForms.form("definite", "accusative", "masculine"))
        assertEquals("den", ArticleForms.form("def", "akk", "m"))
        assertEquals("den", ArticleForms.form("bestimmt", "Akkusativ", "der"))

        assertEquals("keiner", ArticleForms.form("kein", "dativ", "feminin"))
        assertEquals("keiner", ArticleForms.form("kein-", "dat", "die"))

        assertEquals("einem", ArticleForms.form("indefinite", "dat", "das"))
        assertNull(ArticleForms.form("indefinite", "dat", "plural"))

        assertNull(ArticleForms.form("unknown", "nom", "m"))
        assertNull(ArticleForms.form("def", "unknown", "m"))
        assertNull(ArticleForms.form("def", "nom", "unknown"))
    }
}
