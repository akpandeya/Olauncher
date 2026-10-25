package app.olauncher.helper

import android.text.SpannableStringBuilder
import android.text.Spanned

object InfoLine {

    const val SEPARATOR = " · "

    /** Joins the non-blank parts with a middle dot; missing parts leave no extra separators. */
    fun compose(
        date: CharSequence,
        weather: CharSequence? = null,
        battery: CharSequence? = null,
        screenTime: CharSequence? = null
    ): CharSequence {
        val parts = listOfNotNull(date, weather, battery, screenTime)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (parts.none { it is Spanned }) {
            return parts.joinToString(SEPARATOR)
        }
        val builder = SpannableStringBuilder()
        parts.forEachIndexed { index, part ->
            if (index > 0) builder.append(SEPARATOR)
            builder.append(part)
        }
        return builder
    }
}
