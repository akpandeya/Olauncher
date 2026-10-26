package app.olauncher.data.store

/**
 * Context-free lookup for German adjective declension endings.
 *
 * Supports weak, mixed, and strong declensions across all four cases
 * (Nominative, Accusative, Dative, Genitive) and genders (Masculine, Feminine, Neuter, Plural).
 */
object AdjectiveEndings {

    enum class Declension(val displayName: String) {
        WEAK("Schwache Deklination"),
        MIXED("Gemischte Deklination"),
        STRONG("Starke Deklination");

        companion object {
            fun from(raw: String?): Declension? {
                val clean = raw?.trim()?.lowercase() ?: return null
                return when (clean) {
                    "weak", "schwach", "schwache", "definite" -> WEAK
                    "mixed", "gemischt", "gemischte", "indefinite" -> MIXED
                    "strong", "stark", "starke", "zero" -> STRONG
                    else -> null
                }
            }
        }
    }

    /**
     * Returns the adjective ending (without leading hyphen, e.g. "e", "en", "er", "es", "em")
     * for the given [declension], [case], and [gender].
     */
    fun ending(
        declension: Declension,
        case: ArticleForms.Case,
        gender: ArticleForms.Gender,
    ): String = when (declension) {
        Declension.WEAK -> when (case) {
            ArticleForms.Case.NOMINATIVE -> when (gender) {
                ArticleForms.Gender.MASCULINE -> "e"
                ArticleForms.Gender.FEMININE -> "e"
                ArticleForms.Gender.NEUTER -> "e"
                ArticleForms.Gender.PLURAL -> "en"
            }
            ArticleForms.Case.ACCUSATIVE -> when (gender) {
                ArticleForms.Gender.MASCULINE -> "en"
                ArticleForms.Gender.FEMININE -> "e"
                ArticleForms.Gender.NEUTER -> "e"
                ArticleForms.Gender.PLURAL -> "en"
            }
            ArticleForms.Case.DATIVE -> "en"
            ArticleForms.Case.GENITIVE -> "en"
        }
        Declension.MIXED -> when (case) {
            ArticleForms.Case.NOMINATIVE -> when (gender) {
                ArticleForms.Gender.MASCULINE -> "er"
                ArticleForms.Gender.FEMININE -> "e"
                ArticleForms.Gender.NEUTER -> "es"
                ArticleForms.Gender.PLURAL -> "en"
            }
            ArticleForms.Case.ACCUSATIVE -> when (gender) {
                ArticleForms.Gender.MASCULINE -> "en"
                ArticleForms.Gender.FEMININE -> "e"
                ArticleForms.Gender.NEUTER -> "es"
                ArticleForms.Gender.PLURAL -> "en"
            }
            ArticleForms.Case.DATIVE -> "en"
            ArticleForms.Case.GENITIVE -> "en"
        }
        Declension.STRONG -> when (case) {
            ArticleForms.Case.NOMINATIVE -> when (gender) {
                ArticleForms.Gender.MASCULINE -> "er"
                ArticleForms.Gender.FEMININE -> "e"
                ArticleForms.Gender.NEUTER -> "es"
                ArticleForms.Gender.PLURAL -> "e"
            }
            ArticleForms.Case.ACCUSATIVE -> when (gender) {
                ArticleForms.Gender.MASCULINE -> "en"
                ArticleForms.Gender.FEMININE -> "e"
                ArticleForms.Gender.NEUTER -> "es"
                ArticleForms.Gender.PLURAL -> "e"
            }
            ArticleForms.Case.DATIVE -> when (gender) {
                ArticleForms.Gender.MASCULINE -> "em"
                ArticleForms.Gender.FEMININE -> "er"
                ArticleForms.Gender.NEUTER -> "em"
                ArticleForms.Gender.PLURAL -> "en"
            }
            ArticleForms.Case.GENITIVE -> when (gender) {
                ArticleForms.Gender.MASCULINE -> "en"
                ArticleForms.Gender.FEMININE -> "er"
                ArticleForms.Gender.NEUTER -> "en"
                ArticleForms.Gender.PLURAL -> "er"
            }
        }
    }

    /**
     * String-based convenience overload. Returns null if any parameter cannot be parsed.
     */
    fun ending(declension: String, case: String, gender: String): String? {
        val d = Declension.from(declension) ?: return null
        val c = ArticleForms.Case.from(case) ?: return null
        val g = ArticleForms.Gender.from(gender) ?: return null
        return ending(d, c, g)
    }
}
