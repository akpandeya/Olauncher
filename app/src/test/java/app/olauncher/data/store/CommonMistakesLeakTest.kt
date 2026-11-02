package app.olauncher.data.store

import org.junit.Test

/**
 * Guards the common-mistakes lines of every help rule against leaking the
 * answers of the rule's own drill items (#822): a mistakes line is shown on
 * the same card as the rule's items, so it must never hand over an item's
 * answer.
 *
 * For every rule with common-mistakes lines and every drill item of that same
 * rule, a line fails when it, case-insensitively:
 *  - contains the item's full prompt, or
 *  - contains the item's answer sentence (the prompt with the gap filled in,
 *    for word order every accepted sentence), or
 *  - shows the item's answer word-form together with a distinctive word from
 *    that item's own prompt — with both on the line the reader can copy them
 *    straight onto the item's gap (for example "… needs werden: das Haus wird
 *    gebaut" next to the item "Das Haus ___ im Moment renoviert.").
 *
 * Naming a bare form on its own ("wurde for one subject, wurden for several")
 * stays allowed: the rule's forms table already shows those forms, and the
 * prescribed correction lines teach them. Missing item data — a rule without
 * items, an item without prompt or answer, a word-order item without accepted
 * sentences — is a test error, never a pass.
 */
class CommonMistakesLeakTest {

    // German function words: they open every sentence frame and never identify
    // an item, so they cannot turn an answer mention into a leak. Distinctive
    // content words (nouns, verbs, time markers like "gestern") are not on
    // this list — they are exactly what pairs an answer with an item.
    private val stopWords = setOf(
        "der", "die", "das", "den", "dem", "des", "ein", "eine", "einen", "einem", "einer", "eines",
        "ich", "du", "er", "sie", "es", "wir", "ihr", "mich", "mir", "dich", "dir", "ihn", "ihm",
        "uns", "euch", "sich", "man",
        "und", "oder", "aber", "denn", "weil", "dass", "ob", "nicht", "kein", "keine", "keinen",
        "auch", "noch", "schon", "nur", "sehr", "hier", "da", "dort", "dann", "mal", "bitte",
        "in", "an", "auf", "mit", "von", "zu", "bei", "nach", "aus", "für", "um", "durch", "gegen",
        "ohne", "über", "unter", "hinter", "neben", "zwischen", "seit", "während", "trotz", "wegen",
        "als", "wie", "wer", "wen", "wem", "wo", "wohin", "was", "warum",
        // "worden" is the passive-perfect frame word: the rule's own table and
        // cue teach "sein + Partizip II + worden", so it names the rule, not an
        // item. Every perfect-passive example contains it.
        "worden",
        "ist", "bin", "bist", "sind", "seid", "war", "warst", "waren", "wart",
        "hat", "habe", "hast", "haben", "hatte", "hatten", "sein",
        "werde", "wirst", "wird", "werden", "werdet", "würde", "würden", "wurde", "wurden",
    )

    /** One drill item reduced to what a mistakes line must not reveal. */
    private data class LeakItem(val label: String, val prompt: String, val answerSentences: List<String>, val answerWord: String?)

    /** Every rule's mistakes lines together with the items built from the real registries. */
    private data class RuleCheck(val ruleId: String, val lines: List<String>, val items: List<LeakItem>)

    private fun normalize(text: String): String = text
        .lowercase()
        .replace("„", " ")
        .replace("“", " ")
        .replace("”", " ")
        .replace("’", " ")
        .replace("'", " ")
        .replace(DrillItem.GAP_MARKER, " ")
        .split(Regex("[^\\p{L}\\p{Nd}]+"))
        .filter { it.isNotBlank() }
        .joinToString(" ")

    private fun tokens(text: String): List<String> = normalize(text).split(" ")

    /** Builds the check input from the real item registries, erroring on gaps. */
    private fun ruleChecks(): List<RuleCheck> {
        val itemsByRule = mutableMapOf<String, MutableList<LeakItem>>()

        fun add(ruleId: String, item: LeakItem) {
            itemsByRule.getOrPut(ruleId) { mutableListOf() }.add(item)
        }

        for (type in DrillTables.available()) {
            for (item in DrillTables.items(type)) {
                val ruleId = item.ruleId ?: continue
                check(item.prompt.isNotBlank()) { "Missing prompt for item ${item.id}" }
                check(item.answer.isNotBlank()) { "Missing answer for item ${item.id}" }
                val filled = item.prompt.replace(DrillItem.GAP_MARKER, item.answer)
                add(
                    ruleId,
                    LeakItem(
                        label = "${type.id}:${item.id.key}",
                        prompt = item.prompt,
                        answerSentences = listOf(filled),
                        answerWord = item.answer.trim().takeIf { !it.contains(' ') },
                    ),
                )
            }
        }
        for (entry in DrillWordOrder.ENTRIES) {
            val item = entry.item
            val ruleId = item.ruleId ?: continue
            check(item.accepted.isNotEmpty() && item.accepted.all { it.isNotEmpty() }) {
                "Missing accepted sentences for word-order item ${item.id}"
            }
            val sentences = item.accepted.map { it.joinToString(" ") }
            add(
                ruleId,
                LeakItem(
                    label = "word_order:${item.id.key}",
                    prompt = item.tiles.joinToString(" "),
                    answerSentences = sentences,
                    // The answer is a whole sentence; the prompt/answer-sentence
                    // checks above cover it.
                    answerWord = null,
                ),
            )
        }

        return DrillHelpContent.allRules()
            .filter { it.commonMistakes.isNotEmpty() }
            .map { rule ->
                val items = itemsByRule[rule.id].orEmpty()
                check(items.isNotEmpty()) {
                    "No drill items found for help rule ${rule.id}, so its " +
                        "common-mistakes lines cannot be leak-checked (missing item data)"
                }
                RuleCheck(rule.id, rule.commonMistakes, items)
            }
    }

    @Test
    fun commonMistakesLinesNeverRevealSameRuleDrillItems() {
        val failures = mutableListOf<String>()
        for (rule in ruleChecks()) {
            for ((lineIndex, line) in rule.lines.withIndex()) {
                val lineTokens = tokens(line)
                val normalizedLine = lineTokens.joinToString(" ")
                for (item in rule.items) {
                    val normalizedPrompt = normalize(item.prompt)
                    if (normalizedPrompt.isNotEmpty() && normalizedLine.contains(normalizedPrompt)) {
                        failures += leakMessage(rule.ruleId, lineIndex, line, item, "quotes the item prompt")
                        continue
                    }
                    for (sentence in item.answerSentences) {
                        val normalizedSentence = normalize(sentence)
                        if (normalizedSentence.isNotEmpty() && normalizedLine.contains(normalizedSentence)) {
                            failures += leakMessage(rule.ruleId, lineIndex, line, item, "quotes the item answer sentence \"$sentence\"")
                            continue
                        }
                    }
                    val answer = item.answerWord ?: continue
                    val answerToken = normalize(answer)
                    if (answerToken.length < 3 || answerToken !in lineTokens) continue
                    val promptContent = tokens(item.prompt)
                        .filter { it.length >= 4 && it !in stopWords && it != answerToken }
                        .toSet()
                    val shared = promptContent.intersect(lineTokens.toSet())
                    if (shared.isNotEmpty()) {
                        failures += leakMessage(
                            rule.ruleId,
                            lineIndex,
                            line,
                            item,
                            "shows the item's answer word-form \"$answer\" together with the item's own word(s) $shared",
                        )
                    }
                }
            }
        }
        check(failures.isEmpty()) {
            "Common-mistakes lines leak drill answers:\n${failures.joinToString("\n")}"
        }
    }

    private fun leakMessage(ruleId: String, lineIndex: Int, line: String, item: LeakItem, reason: String): String =
        "rule $ruleId, mistakes line ${lineIndex + 1} \"$line\" $reason " +
            "(item ${item.label}: prompt \"${item.prompt}\")"
}
