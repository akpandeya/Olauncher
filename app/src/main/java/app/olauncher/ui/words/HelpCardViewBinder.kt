package app.olauncher.ui.words

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.TypedValue
import android.view.Gravity
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import app.olauncher.R
import app.olauncher.data.store.DrillHelpCard

/**
 * Binds a [DrillHelpCard.State] to the shared help-card views of
 * `view_help_card.xml` (#651 design §3): chips, rule text, first-exposure line,
 * forms table, example and miss line. The drill screen's help slot and the
 * rules-index detail (#792) render through this one path, so a rule looks the
 * same wherever it is opened.
 */
object HelpCardViewBinder {

    /** The shared card views of `view_help_card.xml`, in include order. */
    class CardViews(
        val chips: LinearLayout,
        val rule: TextView,
        /** The rule's first-exposure line (#808); hidden when the state has none. */
        val firstExposure: TextView,
        val table: GridLayout,
        val example: TextView,
        val miss: TextView,
    )

    /** Binds one help-card state: chips, rule, first-exposure line, table, example, miss. */
    fun bind(context: Context, views: CardViews, state: DrillHelpCard.State, answer: String?) {
        val table = state.rule.table

        // Chips: the table's column headers when they carry the pattern (Wohin?/Wo?,
        // Wer? → Nom). Bare gender labels (m·f·n·pl) only repeat the table's own
        // header row below, so the row hides for them (DrillHelpCard.patternChips,
        // #723). Never this item's answer.
        views.chips.removeAllViews()
        val chips = DrillHelpCard.patternChips(table)
        for (chip in chips) views.chips.addView(newHelpChip(context, chip))
        views.chips.isVisible = chips.isNotEmpty()

        views.rule.text = state.rule.rule

        // The first-exposure line (#808, design §2): the rule's one-case list, shown
        // only where the design allows it — the flashcard, a first-exposure tap's open
        // states and the rules-index detail. Null hides the line on later rungs,
        // recaps and word-order states.
        val firstExposure = state.firstExposureRule
        if (firstExposure != null) {
            views.firstExposure.text = firstExposure
            views.firstExposure.isVisible = true
        } else {
            views.firstExposure.isVisible = false
        }

        // The item's own cell: by position when the item carries it, else the unique
        // text match (DrillHelpCard.answerCellPosition). Null lights no cell.
        val answerPosition = DrillHelpCard.answerCellPosition(table, answer, state.answerCell)

        views.table.removeAllViews()
        views.table.columnCount = table.headers.size
        for (header in table.headers) {
            views.table.addView(
                helpCell(
                    context,
                    text = header,
                    emphasised = true,
                    color = genderColorFor(context, header),
                ),
            )
        }
        table.rows.forEachIndexed { rowIndex, row ->
            views.table.addView(helpCell(context, text = row.label, emphasised = true, color = null))
            row.cells.forEachIndexed { index, cell ->
                views.table.addView(
                    helpCell(
                        context,
                        text = cell,
                        emphasised = false,
                        color = genderColorFor(context, table.headers.getOrNull(index + 1)),
                        showAnswer = state.showAnswer && answerPosition == (rowIndex to index),
                    ),
                )
            }
        }

        // Example: shown in the flashcard and after-a-miss states, never before a tap
        // answer (design §3).
        if (state.example != null) {
            views.example.text = markedText(state.example)
            views.example.isVisible = true
        } else {
            views.example.isVisible = false
        }

        // Miss line: rose ✗ mark, then the wrong choice and why it is wrong, in ink.
        // The reason's `*…*` marked pairs render italic (DrillHelpCard.italicSegments);
        // the data keeps the marks, the screen strips them from the shown text.
        val miss = state.miss
        if (miss != null) {
            val line = context.getString(R.string.drill_help_miss_line, miss.wrong, miss.reason)
            val segments = DrillHelpCard.italicSegments(line)
            val span = SpannableString(segments.joinToString("") { it.text })
            if (span.isNotEmpty()) {
                span.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(context, R.color.accentRose)),
                    0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
            var index = 0
            for (segment in segments) {
                if (segment.italic) {
                    span.setSpan(
                        StyleSpan(Typeface.ITALIC),
                        index, index + segment.text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
                index += segment.text.length
            }
            views.miss.text = span
            views.miss.isVisible = true
        } else {
            views.miss.isVisible = false
        }
    }

    /** A rule chip (design §3 anatomy 1): TodayChip.Small on the chip background. */
    private fun newHelpChip(context: Context, label: String): TextView = TextView(context).apply {
        TextViewCompat.setTextAppearance(this, R.style.TodayChip_Small)
        text = label
        background = ContextCompat.getDrawable(context, R.drawable.today_chip)
        val horizontalPadding = context.resources.getDimensionPixelSize(R.dimen.today_chip_padding_horizontal)
        setPadding(horizontalPadding, 0, horizontalPadding, 0)
        gravity = Gravity.CENTER
    }

    /**
     * One forms-table cell. Gender columns take their fixed colour ([genderColorFor]);
     * the answer's cell, shown only in the flashcard and after-a-miss states, is bold
     * and underlined (design §4).
     */
    private fun helpCell(
        context: Context,
        text: String,
        emphasised: Boolean,
        color: Int?,
        showAnswer: Boolean = false,
    ): TextView {
        val tv = TextView(context)
        TextViewCompat.setTextAppearance(tv, R.style.TodayCardBody)
        tv.text = text
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(R.dimen.today_label_size))
        if (color != null) tv.setTextColor(color)
        tv.maxWidth = context.resources.getDimensionPixelSize(R.dimen.help_cell_max_width)
        val padding = context.resources.getDimensionPixelSize(R.dimen.help_cell_padding)
        tv.setPadding(padding, padding / 2, padding, padding / 2)
        if (showAnswer) {
            val span = SpannableString(text)
            span.setSpan(StyleSpan(Typeface.BOLD), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            span.setSpan(UnderlineSpan(), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            tv.text = span
        } else if (emphasised) {
            tv.paint.isFakeBoldText = true
        }
        return tv
    }

    /**
     * The fixed gender colour of a table column header (design §4): m blue, f red,
     * n green; plural and any other label stay ink (null). Case rows carry no colour.
     */
    private fun genderColorFor(context: Context, header: String?): Int? = when (header?.trim()) {
        "m" -> ContextCompat.getColor(context, R.color.genderMasc)
        "f" -> ContextCompat.getColor(context, R.color.genderFem)
        "n" -> ContextCompat.getColor(context, R.color.genderNeut)
        else -> null
    }

    /** Renders `**…**` marked help text: marked segments bold and underlined. */
    private fun markedText(text: String): CharSequence {
        val segments = DrillHelpCard.markedSegments(text)
        if (segments.none { it.bold }) return text
        val span = SpannableString(segments.joinToString("") { it.text })
        var index = 0
        for (segment in segments) {
            if (segment.bold) {
                span.setSpan(StyleSpan(Typeface.BOLD), index, index + segment.text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                span.setSpan(UnderlineSpan(), index, index + segment.text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            index += segment.text.length
        }
        return span
    }
}
