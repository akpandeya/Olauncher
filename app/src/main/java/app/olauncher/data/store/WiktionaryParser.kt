package app.olauncher.data.store

import org.json.JSONObject
import java.util.Locale

object WiktionaryParser {

    private const val MAX_LENGTH = 160

    private val ARTICLE_REGEX = Regex("""(?i)(?:der|die|das|ein|eine)\s+(.+)""")
    private val ENTITY_REGEX = Regex("""&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);""")
    private val TAG_STRIP_REGEX = Regex("""<[^>]+>""")
    private val STYLE_SCRIPT_REGEX = Regex("""<(style|script)[^>]*>.*?</\1>""", RegexOption.DOT_MATCHES_ALL)

    private val HTML_ENTITIES = mapOf(
        "quot" to "\"",
        "amp" to "&",
        "apos" to "'",
        "lt" to "<",
        "gt" to ">",
        "nbsp" to " ",
        "ndash" to "–",
        "mdash" to "—",
        "lsquo" to "‘",
        "rsquo" to "’",
        "sbquo" to "‚",
        "ldquo" to "“",
        "rdquo" to "”",
        "bdquo" to "„",
        "hellip" to "…",
        "bull" to "•",
        "copy" to "©",
        "reg" to "®",
        "trade" to "™",
        "euro" to "€",
        "pound" to "£",
        "yen" to "¥",
        "cent" to "¢",
        "sect" to "§",
        "deg" to "°",
        "plusmn" to "±",
        "times" to "×",
        "divide" to "÷",
        "frac12" to "½",
        "frac14" to "¼",
        "frac34" to "¾",
        "Auml" to "Ä",
        "Ouml" to "Ö",
        "Uuml" to "Ü",
        "auml" to "ä",
        "ouml" to "ö",
        "uuml" to "ü",
        "szlig" to "ß",
        "Eacute" to "É",
        "eacute" to "é",
        "Egrave" to "È",
        "egrave" to "è",
        "Aacute" to "Á",
        "aacute" to "á",
        "Agrave" to "À",
        "agrave" to "à",
        "Ccedil" to "Ç",
        "ccedil" to "ç"
    )

    fun parse(json: String): String? {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            return null
        }

        if (!root.has("de") || root.isNull("de")) return null
        val deArray = root.optJSONArray("de") ?: return null

        val definitions = mutableListOf<String>()
        for (i in 0 until deArray.length()) {
            val entry = deArray.optJSONObject(i) ?: continue
            val defsArray = entry.optJSONArray("definitions") ?: continue
            for (j in 0 until defsArray.length()) {
                val defObj = defsArray.optJSONObject(j) ?: continue
                val rawDef = defObj.optString("definition", "")
                val cleaned = cleanDefinition(rawDef)
                if (cleaned.isNotEmpty()) {
                    definitions.add(cleaned)
                    if (definitions.size == 2) break
                }
            }
            if (definitions.size == 2) break
        }

        if (definitions.isEmpty()) return null
        val joined = definitions.joinToString("; ")
        return truncate(joined, MAX_LENGTH)
    }

    fun candidates(text: String): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return listOf(text)
        if (trimmed.contains(Regex("""\s"""))) {
            val stripped = withoutArticle(text) ?: return listOf(text)
            return listOf(text, stripped).distinct()
        }
        val capitalised = text.lowercase(Locale.GERMAN).replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.GERMAN) else it.toString()
        }
        val lowercased = text.lowercase(Locale.GERMAN)
        return listOf(text, capitalised, lowercased).distinct()
    }

    private fun withoutArticle(text: String): String? {
        val match = ARTICLE_REGEX.matchEntire(text.trim()) ?: return null
        return match.groupValues[1].trim().takeIf { it.isNotEmpty() }
    }

    private fun cleanDefinition(html: String): String {
        var text = html.replace(STYLE_SCRIPT_REGEX, "")
        text = text.replace(TAG_STRIP_REGEX, "")
        text = decodeHtmlEntities(text)
        return text.replace('\u00A0', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun decodeHtmlEntities(input: String): String {
        if (!input.contains('&')) return input
        return ENTITY_REGEX.replace(input) { match ->
            val content = match.groupValues[1]
            when {
                content.startsWith("#x", ignoreCase = true) -> {
                    try {
                        val code = content.substring(2).toInt(16)
                        String(Character.toChars(code))
                    } catch (e: Exception) {
                        match.value
                    }
                }
                content.startsWith("#") -> {
                    try {
                        val code = content.substring(1).toInt(10)
                        String(Character.toChars(code))
                    } catch (e: Exception) {
                        match.value
                    }
                }
                else -> HTML_ENTITIES[content] ?: match.value
            }
        }
    }

    internal fun truncate(text: String, maxChars: Int = MAX_LENGTH): String {
        if (text.length <= maxChars) return text
        val target = text.take(maxChars)
        val lastSpace = target.lastIndexOf(' ')
        return if (lastSpace > 0) {
            val trimmed = target.substring(0, lastSpace).trimEnd { it.isWhitespace() || it == ';' || it == ',' }
            if (trimmed.isNotEmpty()) "$trimmed…" else target.take(maxChars - 1) + "…"
        } else {
            target.take(maxChars - 1) + "…"
        }
    }
}
