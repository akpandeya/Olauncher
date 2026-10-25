package app.olauncher.ui.words

import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import androidx.core.content.ContextCompat
import app.olauncher.R

/**
 * Result of formatting a word for noun cards (#515).
 * [displayText] is the text to show (including written article for nouns).
 * [article] is the article portion if known.
 * [articleRange] is the character range of the article within [displayText].
 * [gender] is canonical "der", "die", or "das", or null if unknown.
 * [colorRes] is the accent colour resource for the article, or null if uncoloured.
 */
data class FormattedWord(
    val displayText: String,
    val article: String? = null,
    val articleRange: IntRange? = null,
    val gender: String? = null,
    val colorRes: Int? = null,
)

/**
 * Context-free helper for gender detection and article colours on noun cards (#515):
 * - Der is blue (accentSky).
 * - Die is rose (accentRose).
 * - Das is sage (accentSage).
 * The article is written, so colour is never the only cue.
 * Unknown genders stay uncoloured and do not gain an article.
 */
object NounArticle {

    private val LEADING_ARTICLE_REGEX = Regex("""^(?i)(der|die|das)\s+(\S.*)$""")

    /**
     * Normalizes a gender string (e.g. "der", "die", "das", "masculine", "m")
     * to canonical "der", "die", or "das". Dual or unknown genders return null.
     */
    fun canonicalGender(gender: String?): String? {
        val trimmed = gender?.trim()?.lowercase() ?: return null
        return when (trimmed) {
            "der", "m", "masculine", "maskulin" -> "der"
            "die", "f", "feminine", "feminin" -> "die"
            "das", "n", "neuter", "neutral", "sächlich" -> "das"
            else -> null
        }
    }

    /**
     * Extracts a leading article from [text] if present (e.g. "der Hund" -> "der", "Die Katze" -> "Die").
     * Returns null if no leading der/die/das is present.
     */
    fun extractLeadingArticle(text: String): String? {
        val match = LEADING_ARTICLE_REGEX.matchEntire(text.trim()) ?: return null
        return match.groupValues[1]
    }

    /**
     * Resolves the gender for [text]: from a leading der/die/das in [text],
     * or from [dictionaryGender]. Returns "der", "die", "das", or null if unknown.
     */
    fun resolveGender(text: String, dictionaryGender: String? = null): String? {
        val leading = extractLeadingArticle(text)
        if (leading != null) {
            return canonicalGender(leading)
        }
        return canonicalGender(dictionaryGender)
    }

    /** Returns the accent colour resource for [gender], or null if unknown. */
    fun colorResForGender(gender: String?): Int? = when (canonicalGender(gender)) {
        "der" -> R.color.accentSky
        "die" -> R.color.accentRose
        "das" -> R.color.accentSage
        else -> null
    }

    /**
     * Formats [text] with its article and colour for noun cards (#515):
     * - If [text] has a leading der/die/das, that article is coloured.
     * - If [text] has no leading article but [dictionaryGender] resolves to der/die/das,
     *   the article is prepended and coloured.
     * - If gender is unknown, the word stays uncoloured without an added article.
     */
    fun format(text: String, dictionaryGender: String? = null): FormattedWord {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return FormattedWord(displayText = "", article = null, articleRange = null, gender = null, colorRes = null)
        }

        val leadingMatch = LEADING_ARTICLE_REGEX.matchEntire(trimmed)
        if (leadingMatch != null) {
            val article = leadingMatch.groupValues[1]
            val noun = leadingMatch.groupValues[2]
            val gender = canonicalGender(article)
            val colorRes = colorResForGender(gender)
            val displayText = "$article $noun"
            return FormattedWord(
                displayText = displayText,
                article = article,
                articleRange = 0 until article.length,
                gender = gender,
                colorRes = colorRes,
            )
        }

        val gender = canonicalGender(dictionaryGender)
        val colorRes = colorResForGender(gender)
        if (gender != null && colorRes != null) {
            val article = gender
            val displayText = "$article $trimmed"
            return FormattedWord(
                displayText = displayText,
                article = article,
                articleRange = 0 until article.length,
                gender = gender,
                colorRes = colorRes,
            )
        }

        return FormattedWord(
            displayText = trimmed,
            article = null,
            articleRange = null,
            gender = null,
            colorRes = null,
        )
    }

    /** Applies [formatted] to [textView], setting a ForegroundColorSpan on the article when coloured. */
    fun applyToTextView(textView: TextView, formatted: FormattedWord) {
        val colorRes = formatted.colorRes
        val range = formatted.articleRange
        if (colorRes != null && range != null) {
            val color = ContextCompat.getColor(textView.context, colorRes)
            val spannable = SpannableString(formatted.displayText)
            spannable.setSpan(
                ForegroundColorSpan(color),
                range.first,
                range.last + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            textView.text = spannable
        } else {
            textView.text = formatted.displayText
        }
    }
}
