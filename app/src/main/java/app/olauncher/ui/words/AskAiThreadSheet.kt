package app.olauncher.ui.words

import android.content.Context
import android.text.format.DateUtils
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import app.olauncher.R
import app.olauncher.data.store.AiChatGrounding
import app.olauncher.data.store.AiChatStore
import app.olauncher.data.store.AiChatSubject
import app.olauncher.databinding.SheetAskAiThreadsBinding
import app.olauncher.helper.Haptics
import app.olauncher.helper.OlDialog
import app.olauncher.ui.askai.AskAiEntries

/**
 * The Ask-AI thread sheet (docs/design/ask-ai-chat.md §Entry points, mock
 * screen 7): long-press on any entry opens the subject's conversations, newest
 * first — first-question snippet, turn count, date — one tap resumes that
 * thread, and "New conversation" starts a fresh one carrying the entry's
 * groundings. Shown over the surface that opened it, which keeps its exact
 * state behind.
 */
object AskAiThreadSheet {

    /**
     * Shows the sheet for [subject]. [onOpen] receives the tapped thread's id,
     * or null for "New conversation"; the caller navigates, so the dialog is
     * already dismissed when the navigation lands.
     */
    fun show(
        fragment: Fragment,
        subject: AiChatSubject,
        title: String,
        groundings: List<AiChatGrounding>,
        onOpen: (threadId: String?) -> Unit,
    ) {
        val context = fragment.requireContext()
        val dialog = OlDialog(context)
        val binding = SheetAskAiThreadsBinding.inflate(LayoutInflater.from(context))
        val rows = AskAiEntries.sheetRows(AiChatStore(context).threadsFor(subject))

        binding.tvSheetTitle.text = if (AskAiEntries.sheetTitleShowsCount(rows.size)) {
            context.getString(R.string.ask_ai_sheet_title_count, title, rows.size)
        } else {
            title
        }
        binding.tvSheetEmpty.isVisible = rows.isEmpty()
        for (row in rows) {
            binding.llSheetRows.addView(threadRow(context, row) {
                Haptics.tick(it)
                dialog.dismiss()
                onOpen(row.threadId)
            })
        }
        binding.btnNewConversation.setOnClickListener {
            Haptics.tick(it)
            dialog.dismiss()
            onOpen(null)
        }

        dialog.setView(binding.root)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.apply {
            setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.showRespectingStatusBar()
    }

    /** One conversation row: the first question, then "n turns · date". */
    private fun threadRow(context: Context, row: AskAiEntries.SheetRow, onOpen: (View) -> Unit): View {
        val line = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = AppCompatResources.getDrawable(context, R.drawable.today_tile)
            val pad = (context.resources.displayMetrics.density * 10).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
            minimumHeight = (context.resources.displayMetrics.density * 48).toInt()
            isClickable = true
            isFocusable = true
            // The row announces its content ("Chat, 4 turns"), design §Accessibility.
            contentDescription = context.getString(R.string.ask_ai_row_a11y, row.turns)
        }
        val snippet = TextView(context).apply {
            TextViewCompat.setTextAppearance(this, R.style.TodayCardBody)
            text = row.snippet
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val meta = TextView(context).apply {
            TextViewCompat.setTextAppearance(this, R.style.TodayCardBodyLight)
            text = context.getString(R.string.ask_ai_sheet_row_meta, row.turns, dateLabel(context, row.updatedAt))
        }
        line.addView(snippet)
        line.addView(meta)
        line.setOnClickListener(onOpen)
        return line
    }

    /** "today" for today's threads, otherwise the short date ("12 Oct"). */
    private fun dateLabel(context: Context, at: Long): String {
        if (DateUtils.isToday(at)) return context.getString(R.string.ask_ai_sheet_today)
        return DateUtils.formatDateTime(context, at, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
    }
}
