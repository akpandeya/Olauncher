package app.olauncher.data.store

import java.text.Normalizer

/**
 * Deterministic subject router for Ask-AI chat threads (docs/design/ask-ai-chat.md
 * §Threads "Routing" + "When a fresh thread could be word or rule"). Subjects — not
 * drill items — own threads ([AiChatSubject]), so the router maps one drill miss to
 * the thread subject the chat opens, resumes or provisionally creates:
 *
 * 1. Word-linked by construction, read from the item id (`DrillItem` has no `wordId`
 *    field): AI practice items `ai:<wordId>:<itemId>`; case- and adjective-ending
 *    items built from a saved word `<wordId>:<key>`; articles, plurals, prepositions,
 *    pronouns, verb prepositions and separable verbs built from a saved word use the
 *    bare word id; review misses are the word itself ([routeForReviewMiss]). A word id
 *    that no longer resolves (the word was deleted) falls through — it cannot build
 *    the word subject.
 * 2. Built-in items that carry the noun's citation form in their id — articles
 *    `art:<noun>` and plurals `plur:<noun>` — whose noun exactly matches a saved
 *    word's citation noun (leading article stripped, then trim + lowercase + NFC).
 *    Exact match only, never the inflected prompt token. Other types carry only
 *    inflected forms and skip this step.
 * 3. Otherwise: the rule thread, or the topic thread (`DrillType.id`) when there is
 *    no rule.
 *
 * Both candidates ([Route.ProvisionalBoth]) arise only when routing step 2 matched AND
 * the item has a `ruleId` AND neither thread exists yet; if exactly one exists it
 * opens, and if both exist the word thread opens.
 *
 * Shape parsing is type-gated because [DrillType.WORD_ORDER] uses synthetic numeric
 * keys (`1`–`5`) that are not word ids; an unrecognised shape under-matches gracefully
 * to a rule or topic thread.
 */
object AiChatRouter {

    /** Where one miss lands. Each route carries the subject (or candidates) to open. */
    sealed class Route {
        /** Open or resume the word thread. */
        data class Word(val subject: AiChatSubject) : Route()

        /** Open or resume the rule thread. */
        data class Rule(val subject: AiChatSubject) : Route()

        /** Open or resume the topic thread. */
        data class Topic(val subject: AiChatSubject) : Route()

        /**
         * A fresh thread where both the word and the rule are plausible subjects:
         * the chat opens unpersisted with the header "New conversation", the grounding
         * card shows both blocks, and the AI's first reply names the subject
         * (`SUBJECT: word|rule`, rule fallback). The router never sees it again —
         * once both threads exist, the deterministic routes decide.
         */
        data class ProvisionalBoth(
            val wordSubject: AiChatSubject,
            val ruleSubject: AiChatSubject,
        ) : Route()
    }

    /**
     * Routes one drill miss ([item] + its [ruleId]) to a thread route. [savedWords]
     * are the learner's saved words; [existingSubjects] are the subjects that already
     * have a thread in [AiChatStore].
     */
    fun route(
        item: DrillItemId,
        ruleId: String?,
        savedWords: Collection<Word>,
        existingSubjects: Set<AiChatSubject>,
    ): Route {
        // Routing step 1: word-linked by construction, read from the item id.
        wordById(item, savedWords.associateBy { it.id })?.let { word ->
            return Route.Word(wordSubject(word))
        }

        // Routing step 2: built-in items carrying the citation noun in their id.
        citationMatch(item, savedWords)?.let { word ->
            val wordSubject = wordSubject(word)
            if (ruleId == null) return Route.Word(wordSubject)
            val ruleSubject = AiChatSubject(AiChatSubject.Kind.RULE, ruleId)
            val wordExists = wordSubject in existingSubjects
            val ruleExists = ruleSubject in existingSubjects
            return when {
                // Fresh: neither thread exists — both candidates are plausible.
                !wordExists && !ruleExists -> Route.ProvisionalBoth(wordSubject, ruleSubject)
                // Exactly one exists opens; if both exist the word thread opens.
                wordExists -> Route.Word(wordSubject)
                else -> Route.Rule(ruleSubject)
            }
        }

        // Routing step 3: the rule thread, or the topic thread without a rule.
        return if (ruleId != null) {
            Route.Rule(AiChatSubject(AiChatSubject.Kind.RULE, ruleId))
        } else {
            Route.Topic(AiChatSubject(AiChatSubject.Kind.TOPIC, item.type.id))
        }
    }

    /**
     * Routes a review miss, which has no drill item: the reviewed word itself is the
     * subject (design §Threads: "review misses are the word itself").
     */
    fun routeForReviewMiss(word: Word): Route = Route.Word(wordSubject(word))

    /** The citation text of a thread word subject: trim + lowercase + NFC. */
    internal fun normalizeCitation(text: String): String {
        return Normalizer.normalize(text.trim().lowercase(), Normalizer.Form.NFC)
    }

    /**
     * The saved word a step-1 item id is linked to by construction, or null. The word
     * must still be saved: a deleted word leaves no citation text to build the
     * subject ref from, so the miss falls through to a rule or topic thread.
     */
    private fun wordById(item: DrillItemId, wordsById: Map<Long, Word>): Word? {
        val wordId = when {
            // AI practice items: "ai:<wordId>:<itemId>" (any practice type).
            item.key.startsWith(AI_KEY_PREFIX) ->
                item.key.removePrefix(AI_KEY_PREFIX).substringBefore(':').toLongOrNull()
            // Case- and adjective-ending items built from a saved word: "<wordId>:<key>".
            item.type in WORD_ID_KEY_TYPES && item.key.contains(':') ->
                item.key.substringBefore(':').toLongOrNull()
            // Topics whose items built from a saved word use the bare word id.
            item.type in BARE_WORD_ID_TYPES -> item.key.toLongOrNull()
            else -> null
        }
        return wordId?.let(wordsById::get)
    }

    /**
     * The saved word whose citation noun exactly matches an `art:`/`plur:` item's
     * noun, or null. Exact match only — never the inflected prompt token — so an
     * unmatched noun under-matches gracefully to a rule or topic thread.
     */
    private fun citationMatch(item: DrillItemId, savedWords: Collection<Word>): Word? {
        val prefix = when (item.type) {
            DrillType.ARTICLES -> ARTICLES_NOUN_PREFIX
            DrillType.PLURALS -> PLURALS_NOUN_PREFIX
            else -> return null
        }
        if (!item.key.startsWith(prefix)) return null
        val noun = item.key.removePrefix(prefix)
        if (noun.isBlank()) return null
        val wanted = normalizeCitation(noun)
        return savedWords.firstOrNull { normalizeCitation(stripLeadingArticle(it.text)) == wanted }
    }

    private fun wordSubject(word: Word): AiChatSubject {
        return AiChatSubject(AiChatSubject.Kind.WORD, normalizeCitation(word.text))
    }

    /**
     * Strips a leading der/die/das from a saved word's text ("die Straße" → "Straße")
     * so the citation noun compares against `art:`/`plur:` item ids, which carry the
     * bare noun. Mirrors `NounArticle.stripLeadingArticle`, which stays in `ui.words`
     * (Android imports) — this pure copy lives with the router that needs it.
     */
    private fun stripLeadingArticle(text: String): String {
        val trimmed = text.trim()
        val match = LEADING_ARTICLE_REGEX.matchEntire(trimmed) ?: return trimmed
        return match.groupValues[2].trim()
    }

    private val LEADING_ARTICLE_REGEX = Regex("""^(?i)(der|die|das)\s+(\S.*)$""")

    /** Prefix of every AI practice item's key ([PracticeDrillItems]). */
    private val AI_KEY_PREFIX = PracticeDrillItems.KEY_PREFIX

    /** Item-id prefixes carrying the citation noun, per builder ([DrillArticles], [DrillPlurals]). */
    private const val ARTICLES_NOUN_PREFIX = "art:"
    private const val PLURALS_NOUN_PREFIX = "plur:"

    /** Types whose items built from a saved word key it as "<wordId>:<key>". */
    private val WORD_ID_KEY_TYPES = setOf(DrillType.CASE_ENDINGS, DrillType.ADJECTIVE_ENDINGS)

    /**
     * Types whose items built from a saved word key it as the bare word id
     * (word order's `1`–`5` are synthetic, not word ids).
     */
    private val BARE_WORD_ID_TYPES = setOf(
        DrillType.ARTICLES,
        DrillType.PLURALS,
        DrillType.PREPOSITIONS,
        DrillType.PRONOUNS,
        DrillType.VERB_PREPOSITIONS,
        DrillType.SEPARABLE_VERBS,
    )
}
