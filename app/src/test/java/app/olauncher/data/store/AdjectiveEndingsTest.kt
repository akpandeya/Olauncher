package app.olauncher.data.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdjectiveEndingsTest {

    // --- Weak declension (16 cells) ---

    @Test
    fun weakNominativePinsAllGenders() {
        assertEquals("e", AdjectiveEndings.ending(AdjectiveEndings.Declension.WEAK, ArticleForms.Case.NOMINATIVE, ArticleForms.Gender.MASCULINE))
        assertEquals("e", AdjectiveEndings.ending(AdjectiveEndings.Declension.WEAK, ArticleForms.Case.NOMINATIVE, ArticleForms.Gender.FEMININE))
        assertEquals("e", AdjectiveEndings.ending(AdjectiveEndings.Declension.WEAK, ArticleForms.Case.NOMINATIVE, ArticleForms.Gender.NEUTER))
        assertEquals("en", AdjectiveEndings.ending(AdjectiveEndings.Declension.WEAK, ArticleForms.Case.NOMINATIVE, ArticleForms.Gender.PLURAL))
    }

    @Test
    fun weakAccusativePinsAllGenders() {
        assertEquals("en", AdjectiveEndings.ending(AdjectiveEndings.Declension.WEAK, ArticleForms.Case.ACCUSATIVE, ArticleForms.Gender.MASCULINE))
        assertEquals("e", AdjectiveEndings.ending(AdjectiveEndings.Declension.WEAK, ArticleForms.Case.ACCUSATIVE, ArticleForms.Gender.FEMININE))
        assertEquals("e", AdjectiveEndings.ending(AdjectiveEndings.Declension.WEAK, ArticleForms.Case.ACCUSATIVE, ArticleForms.Gender.NEUTER))
        assertEquals("en", AdjectiveEndings.ending(AdjectiveEndings.Declension.WEAK, ArticleForms.Case.ACCUSATIVE, ArticleForms.Gender.PLURAL))
    }

    @Test
    fun weakDativePinsAllGenders() {
        assertEquals("en", AdjectiveEndings.ending(AdjectiveEndings.Declension.WEAK, ArticleForms.Case.DATIVE, ArticleForms.Gender.MASCULINE))
        assertEquals("en", AdjectiveEndings.ending(AdjectiveEndings.Declension.WEAK, ArticleForms.Case.DATIVE, ArticleForms.Gender.FEMININE))
        assertEquals("en", AdjectiveEndings.ending(AdjectiveEndings.Declension.WEAK, ArticleForms.Case.DATIVE, ArticleForms.Gender.NEUTER))
        assertEquals("en", AdjectiveEndings.ending(AdjectiveEndings.Declension.WEAK, ArticleForms.Case.DATIVE, ArticleForms.Gender.PLURAL))
    }

    @Test
    fun weakGenitivePinsAllGenders() {
        assertEquals("en", AdjectiveEndings.ending(AdjectiveEndings.Declension.WEAK, ArticleForms.Case.GENITIVE, ArticleForms.Gender.MASCULINE))
        assertEquals("en", AdjectiveEndings.ending(AdjectiveEndings.Declension.WEAK, ArticleForms.Case.GENITIVE, ArticleForms.Gender.FEMININE))
        assertEquals("en", AdjectiveEndings.ending(AdjectiveEndings.Declension.WEAK, ArticleForms.Case.GENITIVE, ArticleForms.Gender.NEUTER))
        assertEquals("en", AdjectiveEndings.ending(AdjectiveEndings.Declension.WEAK, ArticleForms.Case.GENITIVE, ArticleForms.Gender.PLURAL))
    }

    // --- Mixed declension (16 cells) ---

    @Test
    fun mixedNominativePinsAllGenders() {
        assertEquals("er", AdjectiveEndings.ending(AdjectiveEndings.Declension.MIXED, ArticleForms.Case.NOMINATIVE, ArticleForms.Gender.MASCULINE))
        assertEquals("e", AdjectiveEndings.ending(AdjectiveEndings.Declension.MIXED, ArticleForms.Case.NOMINATIVE, ArticleForms.Gender.FEMININE))
        assertEquals("es", AdjectiveEndings.ending(AdjectiveEndings.Declension.MIXED, ArticleForms.Case.NOMINATIVE, ArticleForms.Gender.NEUTER))
        assertEquals("en", AdjectiveEndings.ending(AdjectiveEndings.Declension.MIXED, ArticleForms.Case.NOMINATIVE, ArticleForms.Gender.PLURAL))
    }

    @Test
    fun mixedAccusativePinsAllGenders() {
        assertEquals("en", AdjectiveEndings.ending(AdjectiveEndings.Declension.MIXED, ArticleForms.Case.ACCUSATIVE, ArticleForms.Gender.MASCULINE))
        assertEquals("e", AdjectiveEndings.ending(AdjectiveEndings.Declension.MIXED, ArticleForms.Case.ACCUSATIVE, ArticleForms.Gender.FEMININE))
        assertEquals("es", AdjectiveEndings.ending(AdjectiveEndings.Declension.MIXED, ArticleForms.Case.ACCUSATIVE, ArticleForms.Gender.NEUTER))
        assertEquals("en", AdjectiveEndings.ending(AdjectiveEndings.Declension.MIXED, ArticleForms.Case.ACCUSATIVE, ArticleForms.Gender.PLURAL))
    }

    @Test
    fun mixedDativePinsAllGenders() {
        assertEquals("en", AdjectiveEndings.ending(AdjectiveEndings.Declension.MIXED, ArticleForms.Case.DATIVE, ArticleForms.Gender.MASCULINE))
        assertEquals("en", AdjectiveEndings.ending(AdjectiveEndings.Declension.MIXED, ArticleForms.Case.DATIVE, ArticleForms.Gender.FEMININE))
        assertEquals("en", AdjectiveEndings.ending(AdjectiveEndings.Declension.MIXED, ArticleForms.Case.DATIVE, ArticleForms.Gender.NEUTER))
        assertEquals("en", AdjectiveEndings.ending(AdjectiveEndings.Declension.MIXED, ArticleForms.Case.DATIVE, ArticleForms.Gender.PLURAL))
    }

    @Test
    fun mixedGenitivePinsAllGenders() {
        assertEquals("en", AdjectiveEndings.ending(AdjectiveEndings.Declension.MIXED, ArticleForms.Case.GENITIVE, ArticleForms.Gender.MASCULINE))
        assertEquals("en", AdjectiveEndings.ending(AdjectiveEndings.Declension.MIXED, ArticleForms.Case.GENITIVE, ArticleForms.Gender.FEMININE))
        assertEquals("en", AdjectiveEndings.ending(AdjectiveEndings.Declension.MIXED, ArticleForms.Case.GENITIVE, ArticleForms.Gender.NEUTER))
        assertEquals("en", AdjectiveEndings.ending(AdjectiveEndings.Declension.MIXED, ArticleForms.Case.GENITIVE, ArticleForms.Gender.PLURAL))
    }

    // --- Strong declension (16 cells) ---

    @Test
    fun strongNominativePinsAllGenders() {
        assertEquals("er", AdjectiveEndings.ending(AdjectiveEndings.Declension.STRONG, ArticleForms.Case.NOMINATIVE, ArticleForms.Gender.MASCULINE))
        assertEquals("e", AdjectiveEndings.ending(AdjectiveEndings.Declension.STRONG, ArticleForms.Case.NOMINATIVE, ArticleForms.Gender.FEMININE))
        assertEquals("es", AdjectiveEndings.ending(AdjectiveEndings.Declension.STRONG, ArticleForms.Case.NOMINATIVE, ArticleForms.Gender.NEUTER))
        assertEquals("e", AdjectiveEndings.ending(AdjectiveEndings.Declension.STRONG, ArticleForms.Case.NOMINATIVE, ArticleForms.Gender.PLURAL))
    }

    @Test
    fun strongAccusativePinsAllGenders() {
        assertEquals("en", AdjectiveEndings.ending(AdjectiveEndings.Declension.STRONG, ArticleForms.Case.ACCUSATIVE, ArticleForms.Gender.MASCULINE))
        assertEquals("e", AdjectiveEndings.ending(AdjectiveEndings.Declension.STRONG, ArticleForms.Case.ACCUSATIVE, ArticleForms.Gender.FEMININE))
        assertEquals("es", AdjectiveEndings.ending(AdjectiveEndings.Declension.STRONG, ArticleForms.Case.ACCUSATIVE, ArticleForms.Gender.NEUTER))
        assertEquals("e", AdjectiveEndings.ending(AdjectiveEndings.Declension.STRONG, ArticleForms.Case.ACCUSATIVE, ArticleForms.Gender.PLURAL))
    }

    @Test
    fun strongDativePinsAllGenders() {
        assertEquals("em", AdjectiveEndings.ending(AdjectiveEndings.Declension.STRONG, ArticleForms.Case.DATIVE, ArticleForms.Gender.MASCULINE))
        assertEquals("er", AdjectiveEndings.ending(AdjectiveEndings.Declension.STRONG, ArticleForms.Case.DATIVE, ArticleForms.Gender.FEMININE))
        assertEquals("em", AdjectiveEndings.ending(AdjectiveEndings.Declension.STRONG, ArticleForms.Case.DATIVE, ArticleForms.Gender.NEUTER))
        assertEquals("en", AdjectiveEndings.ending(AdjectiveEndings.Declension.STRONG, ArticleForms.Case.DATIVE, ArticleForms.Gender.PLURAL))
    }

    @Test
    fun strongGenitivePinsAllGenders() {
        assertEquals("en", AdjectiveEndings.ending(AdjectiveEndings.Declension.STRONG, ArticleForms.Case.GENITIVE, ArticleForms.Gender.MASCULINE))
        assertEquals("er", AdjectiveEndings.ending(AdjectiveEndings.Declension.STRONG, ArticleForms.Case.GENITIVE, ArticleForms.Gender.FEMININE))
        assertEquals("en", AdjectiveEndings.ending(AdjectiveEndings.Declension.STRONG, ArticleForms.Case.GENITIVE, ArticleForms.Gender.NEUTER))
        assertEquals("er", AdjectiveEndings.ending(AdjectiveEndings.Declension.STRONG, ArticleForms.Case.GENITIVE, ArticleForms.Gender.PLURAL))
    }

    // --- String overloads and parsing ---

    @Test
    fun stringOverloadPinsRepresentativeEndings() {
        assertEquals("en", AdjectiveEndings.ending("mixed", "dative", "masculine"))
        assertEquals("e", AdjectiveEndings.ending("weak", "nominative", "masculine"))
        assertEquals("er", AdjectiveEndings.ending("strong", "nominative", "masculine"))
        assertEquals("em", AdjectiveEndings.ending("strong", "dative", "neuter"))
    }

    @Test
    fun stringOverloadReturnsNullForInvalidInput() {
        assertNull(AdjectiveEndings.ending("unknown", "dative", "masculine"))
        assertNull(AdjectiveEndings.ending("mixed", "unknown", "masculine"))
        assertNull(AdjectiveEndings.ending("mixed", "dative", "unknown"))
    }
}
