package app.olauncher.data.store

/**
 * Round shape for a drill: whether it has a typed round after the choice round,
 * or is choice-only.
 */
enum class DrillRoundShape {
    CHOICE_THEN_TYPED,
    CHOICE_ONLY,
}

/** Default number of items in a drill round. */
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
    CLOZE("cloze");

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
