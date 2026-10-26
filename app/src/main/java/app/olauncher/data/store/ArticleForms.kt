package app.olauncher.data.store

/**
 * Context-free lookup for German article declensions.
 *
 * Supports definite, indefinite, and kein- articles across all four cases
 * (Nominative, Accusative, Dative, Genitive) and genders (Masculine, Feminine, Neuter, Plural).
 *
 * Note: Indefinite articles have no plural form, so [form] returns null for indefinite plural.
 */
object ArticleForms {

    enum class Kind {
        DEFINITE,
        INDEFINITE,
        KEIN;

        companion object {
            fun from(raw: String?): Kind? {
                val clean = raw?.trim()?.lowercase() ?: return null
                return when (clean) {
                    "definite", "bestimmt", "def" -> DEFINITE
                    "indefinite", "unbestimmt", "indef", "ein" -> INDEFINITE
                    "kein", "kein-", "negative", "negativ" -> KEIN
                    else -> null
                }
            }
        }
    }

    enum class Case(val displayName: String) {
        NOMINATIVE("Nominativ"),
        ACCUSATIVE("Akkusativ"),
        DATIVE("Dativ"),
        GENITIVE("Genitiv");

        companion object {
            fun from(raw: String?): Case? {
                val clean = raw?.trim()?.lowercase() ?: return null
                return when (clean) {
                    "nominative", "nominativ", "nom" -> NOMINATIVE
                    "accusative", "akkusativ", "akk", "acc" -> ACCUSATIVE
                    "dative", "dativ", "dat" -> DATIVE
                    "genitive", "genitiv", "gen" -> GENITIVE
                    else -> null
                }
            }
        }
    }

    enum class Gender(val displayName: String) {
        MASCULINE("Maskulinum"),
        FEMININE("Femininum"),
        NEUTER("Neutrum"),
        PLURAL("Plural");

        companion object {
            fun from(raw: String?): Gender? {
                val clean = raw?.trim()?.lowercase() ?: return null
                return when (clean) {
                    "masculine", "maskulin", "maskulinum", "m", "der" -> MASCULINE
                    "feminine", "feminin", "femininum", "f", "die" -> FEMININE
                    "neuter", "neutral", "neutrum", "sächlich", "n", "das" -> NEUTER
                    "plural", "pl", "die (pl)" -> PLURAL
                    else -> null
                }
            }
        }
    }

    /**
     * Returns the declined article form for the given [kind], [case], and [gender].
     * Returns null when no form exists (e.g. indefinite article plural).
     */
    fun form(kind: Kind, case: Case, gender: Gender): String? = when (kind) {
        Kind.DEFINITE -> when (case) {
            Case.NOMINATIVE -> when (gender) {
                Gender.MASCULINE -> "der"
                Gender.FEMININE -> "die"
                Gender.NEUTER -> "das"
                Gender.PLURAL -> "die"
            }
            Case.ACCUSATIVE -> when (gender) {
                Gender.MASCULINE -> "den"
                Gender.FEMININE -> "die"
                Gender.NEUTER -> "das"
                Gender.PLURAL -> "die"
            }
            Case.DATIVE -> when (gender) {
                Gender.MASCULINE -> "dem"
                Gender.FEMININE -> "der"
                Gender.NEUTER -> "dem"
                Gender.PLURAL -> "den"
            }
            Case.GENITIVE -> when (gender) {
                Gender.MASCULINE -> "des"
                Gender.FEMININE -> "der"
                Gender.NEUTER -> "des"
                Gender.PLURAL -> "der"
            }
        }
        Kind.INDEFINITE -> when (case) {
            Case.NOMINATIVE -> when (gender) {
                Gender.MASCULINE -> "ein"
                Gender.FEMININE -> "eine"
                Gender.NEUTER -> "ein"
                Gender.PLURAL -> null
            }
            Case.ACCUSATIVE -> when (gender) {
                Gender.MASCULINE -> "einen"
                Gender.FEMININE -> "eine"
                Gender.NEUTER -> "ein"
                Gender.PLURAL -> null
            }
            Case.DATIVE -> when (gender) {
                Gender.MASCULINE -> "einem"
                Gender.FEMININE -> "einer"
                Gender.NEUTER -> "einem"
                Gender.PLURAL -> null
            }
            Case.GENITIVE -> when (gender) {
                Gender.MASCULINE -> "eines"
                Gender.FEMININE -> "einer"
                Gender.NEUTER -> "eines"
                Gender.PLURAL -> null
            }
        }
        Kind.KEIN -> when (case) {
            Case.NOMINATIVE -> when (gender) {
                Gender.MASCULINE -> "kein"
                Gender.FEMININE -> "keine"
                Gender.NEUTER -> "kein"
                Gender.PLURAL -> "keine"
            }
            Case.ACCUSATIVE -> when (gender) {
                Gender.MASCULINE -> "keinen"
                Gender.FEMININE -> "keine"
                Gender.NEUTER -> "kein"
                Gender.PLURAL -> "keine"
            }
            Case.DATIVE -> when (gender) {
                Gender.MASCULINE -> "keinem"
                Gender.FEMININE -> "keiner"
                Gender.NEUTER -> "keinem"
                Gender.PLURAL -> "keinen"
            }
            Case.GENITIVE -> when (gender) {
                Gender.MASCULINE -> "keines"
                Gender.FEMININE -> "keiner"
                Gender.NEUTER -> "keines"
                Gender.PLURAL -> "keiner"
            }
        }
    }

    /**
     * String-based convenience overload. Returns null if any parameter cannot be parsed
     * or if no form exists.
     */
    fun form(kind: String, case: String, gender: String): String? {
        val k = Kind.from(kind) ?: return null
        val c = Case.from(case) ?: return null
        val g = Gender.from(gender) ?: return null
        return form(k, c, g)
    }
}
