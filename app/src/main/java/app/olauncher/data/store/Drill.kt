package app.olauncher.data.store

/**
 * Round shape for a drill: whether it has a typed round after the choice round,
 * or is choice-only.
 */
enum class DrillRoundShape {
    CHOICE_THEN_TYPED,
    CHOICE_ONLY,
}

/**
 * Legacy round size from before #639: old stored rounds held 10 items, and the
 * decayed-counts seeding in [DrillScores] still derives round counts from it.
 * A round now draws 5-8 items (see [DrillRoundPlanner]); [DrillTables.ROUND_SIZE]
 * remains the compatibility cap.
 */
const val DRILL_ROUND_SIZE = 10

/**
 * Drill types supported by the app.
 *
 * Note: These IDs are stored in persistent data (preferences, review events, practice items),
 * so they must remain stable.
 */
enum class DrillType(val id: String, val isChoiceOnly: Boolean = false) {
    PREPOSITIONS("prepositions"),
    PRONOUNS("pronouns"),
    ARTICLES("articles"),
    PLURALS("plurals"),
    CASE_ENDINGS("case_endings", isChoiceOnly = true),
    VERB_PREPOSITIONS("verb_prepositions"),
    SEPARABLE_VERBS("separable_verbs"),
    ADJECTIVE_ENDINGS("adjective_endings", isChoiceOnly = true),
    WORD_ORDER("word_order", isChoiceOnly = true),
    CONJUGATION("conjugation"),
    CLOZE("cloze"),

    // Gap topics (#651): item data from docs/design/drill-content/items/. The ids are
    // stored in persistent data (scores, item history), so they must remain stable.
    PASSIVE("passive"),
    KONJUNKTIV_II("konjunktiv_ii"),
    RELATIVE_PRONOUNS("relative_pronouns"),
    GENITIVE("genitive"),
    N_DECLENSION("n_declension"),
    IMPERATIVE("imperative"),
    WERDEN("werden"),
    PRAETERITUM("praeteritum"),
    INFINITIV_ZU("infinitiv_zu"),
    MODAL_VERBS("modal_verbs"),
    KONJUNKTIV_I("konjunktiv_i");

    val hasTypedRound: Boolean get() = !isChoiceOnly

    val roundShape: DrillRoundShape
        get() = if (isChoiceOnly) DrillRoundShape.CHOICE_ONLY else DrillRoundShape.CHOICE_THEN_TYPED

    companion object {
        fun fromId(id: String): DrillType? = values().firstOrNull { it.id == id }
    }
}

/**
 * Identifier for a drill item: combination of the drill type and a stable key.
 *
 * Encodes as "<type.id>:<key>".
 */
data class DrillItemId(val type: DrillType, val key: String) {
    override fun toString(): String = "${type.id}:$key"
    fun encode(): String = "${type.id}:$key"

    companion object {
        fun parse(raw: String): DrillItemId? {
            val colonIndex = raw.indexOf(':')
            if (colonIndex < 0) return null
            val typeStr = raw.substring(0, colonIndex)
            val keyStr = raw.substring(colonIndex + 1)
            val type = DrillType.fromId(typeStr) ?: return null
            if (keyStr.isBlank()) return null
            return DrillItemId(type, keyStr)
        }
    }
}

/** Validation outcome for a [DrillItem]. */
data class DrillItemCheck(val isValid: Boolean, val problems: List<String>)

/**
 * A single drill item card.
 *
 * The gap marker in prompts is [GAP_MARKER] ("___").
 */
data class DrillItem(
    val id: DrillItemId,
    val prompt: String,
    val answer: String,
    val choices: List<String>,
    val hint: String? = null,
    /**
     * The help-card rule this item belongs to (#651), as authored in
     * docs/design/drill-content/ — looked up in [DrillHelpContent]. Null for
     * items without help-card content (the live topics).
     */
    val ruleId: String? = null,
    /**
     * Item-specific reason the answer is right, shown in the miss line after a
     * wrong tap (design §3: the ✗ line names the wrong choice and why it is wrong).
     */
    val missReason: String? = null,
    /**
     * One miss reason per wrong choice, transcribed from docs/design/drill-content/
     * (design §3: the ✗ line says why this wrong choice is wrong). Looked up in
     * [DrillHelpCard.missParts] by the tapped choice; items without an entry for a
     * choice fall back to [missReason].
     */
    val missByChoice: Map<String, String> = emptyMap(),
) {
    fun validate(): DrillItemCheck {
        val problems = mutableListOf<String>()
        if (prompt.isBlank()) {
            problems += "Prompt cannot be blank"
        }
        if (answer.isBlank()) {
            problems += "Answer cannot be blank"
        }
        if (choices.size !in 2..4) {
            problems += "Choices count must be between 2 and 4 (found ${choices.size})"
        }
        if (choices.distinct().size != choices.size) {
            problems += "Choices cannot contain duplicates"
        }
        if (answer !in choices) {
            problems += "Answer must be one of the choices"
        }
        return DrillItemCheck(isValid = problems.isEmpty(), problems = problems)
    }

    companion object {
        const val GAP_MARKER = "___"
    }
}

const val DRILL_GAP_MARKER = DrillItem.GAP_MARKER
