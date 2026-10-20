package app.olauncher.helper

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.graphics.Paint
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.data.store.Capture
import app.olauncher.data.store.WiktionaryApi
import app.olauncher.data.store.WiktionaryCache
import app.olauncher.data.store.ScreenText
import app.olauncher.data.store.WordCapture
import app.olauncher.data.store.WordLookup
import app.olauncher.data.store.Word
import app.olauncher.data.store.WordsStore
import app.olauncher.databinding.ActivityWordCaptureBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WordCaptureActivity : AppCompatActivity() {

    companion object {
        fun setEnabled(context: Context, enabled: Boolean) {
            val state = if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            val components = listOf(
                ComponentName(context, WordCaptureActivity::class.java),
                ComponentName(context, "app.olauncher.helper.WordShareAlias"),
                ComponentName(context, WordLookupActivity::class.java),
                ComponentName(context, "app.olauncher.helper.WordLookupShareAlias")
            )
            components.forEach {
                context.packageManager.setComponentEnabledSetting(it, state, PackageManager.DONT_KILL_APP)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = Prefs(this)
        AppCompatDelegate.setDefaultNightMode(prefs.appTheme)
        if (prefs.boldFont) theme.applyStyle(R.style.BoldFontOverlay, true)
        super.onCreate(savedInstanceState)

        val screenLines = intent?.getStringArrayListExtra(WordScreenService.EXTRA_SCREEN_LINES)
        if (screenLines != null) {
            val screenChoices = ScreenText.choices(screenLines)
            if (screenChoices.isEmpty()) {
                showToast(R.string.nothing_to_pick_here)
                finish()
            } else {
                showPicker(
                    screenChoices.map { it.text }, context = "", showSelectAll = false, prefs = prefs,
                    contexts = screenChoices.associate { it.text.lowercase() to it.context }
                )
            }
            return
        }

        val rawText = readIncomingText()

        when (val capture = WordCapture.classify(rawText)) {
            is Capture.Word -> {
                saveWord(capture.text, context = "")
                finish()
            }
            is Capture.Sentence -> {
                val choices = capture.tokens.distinctBy { it.lowercase() }
                showPicker(choices, context = capture.text, showSelectAll = false, prefs = prefs)
            }
            is Capture.List -> {
                showPicker(capture.items, context = "", showSelectAll = true, prefs = prefs)
            }
            Capture.Invalid -> {
                showToast(R.string.select_word_or_sentence)
                finish()
            }
        }
    }

    private fun readIncomingText(): String {
        val i = intent ?: return ""
        if (i.action == Intent.ACTION_SEND) {
            val clipText = i.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
            return i.getStringExtra(Intent.EXTRA_TEXT)
                ?: i.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                ?: clipText
                ?: ""
        }
        return i.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            ?: i.getStringExtra(Intent.EXTRA_PROCESS_TEXT)
            ?: ""
    }

    private fun saveWord(text: String, context: String) {
        val word = WordsStore(this).add(text, context)
        WordLookup.enqueue(this, word.id)
        showToast(getString(R.string.saved_word_de, text))
    }

    private fun saveSelected(texts: List<String>, context: String) {
        val result = WordsStore(this).addAll(texts, context)
        result.added.forEach { WordLookup.enqueue(this, it.id) }
        val base = when {
            result.added.size == 1 -> getString(R.string.word_saved_de, result.added[0].text)
            result.added.isEmpty() && result.alreadySaved.size == 1 ->
                getString(R.string.word_saved_de, result.alreadySaved[0].text)
            else -> getString(R.string.words_saved_de, result.added.size)
        }
        val suffix = if (result.added.isNotEmpty() && result.alreadySaved.isNotEmpty()) {
            getString(R.string.words_already_saved_suffix_de, result.alreadySaved.size)
        } else ""
        showToast(base + suffix)
    }

    private fun saveSelectedByLine(texts: List<String>, contexts: Map<String, String>) {
        val store = WordsStore(this)
        val added = mutableListOf<Word>()
        val already = mutableListOf<Word>()
        texts.groupBy { contexts[it.lowercase()].orEmpty() }.forEach { (line, group) ->
            val result = store.addAll(group, line)
            added.addAll(result.added)
            already.addAll(result.alreadySaved)
        }
        added.forEach { WordLookup.enqueue(this, it.id) }
        val base = when {
            added.size == 1 -> getString(R.string.word_saved_de, added[0].text)
            added.isEmpty() && already.size == 1 -> getString(R.string.word_saved_de, already[0].text)
            else -> getString(R.string.words_saved_de, added.size)
        }
        val suffix = if (added.isNotEmpty() && already.isNotEmpty()) {
            getString(R.string.words_already_saved_suffix_de, already.size)
        } else ""
        showToast(base + suffix)
    }

    /**
     * [contexts] (screen pick only) maps a lowercase choice to its own line; choices are grouped by
     * that line and each saved word keeps its line as context. Otherwise [context] is used for all.
     */
    private fun showPicker(
        choices: List<String>,
        context: String,
        showSelectAll: Boolean,
        prefs: Prefs,
        contexts: Map<String, String>? = null,
    ) {
        val binding = ActivityWordCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Cap the word list so the meaning line and Save button stay on screen.
        val maxListPx = SheetHeight.maxListHeight(resources.displayMetrics.heightPixels, 0.5)
        binding.wordsScroll.viewTreeObserver.addOnPreDrawListener(object : android.view.ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                val scroll = binding.wordsScroll
                if (scroll.layoutParams.height == ViewGroup.LayoutParams.WRAP_CONTENT && scroll.measuredHeight > maxListPx) {
                    scroll.layoutParams.height = maxListPx
                    scroll.requestLayout()
                    return false
                }
                return true
            }
        })
        // Keep the sheet clear of the navigation bar.
        val sheetBottomPad = binding.captureSheet.paddingBottom
        binding.captureSheet.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, sheetBottomPad + insets.systemWindowInsetBottom)
            insets
        }

        binding.captureScrim.setOnClickListener { finish() }
        // Prevent clicks inside the sheet from dismissing via scrim
        binding.captureSheet.setOnClickListener { }
        onBackPressedDispatcher.addCallback(this) { finish() }

        val store = WordsStore(this)
        val savedByText = store.all().associateBy { it.text.lowercase() }
        val isSaved = { choice: String -> savedByText.containsKey(choice.trim().lowercase()) }
        val lookupAllowed = WordLookup.isLookupAllowed(prefs.germanEnabled, prefs.germanLookup)

        val typeface = Typeface.create(if (prefs.boldFont) "sans-serif" else "sans-serif-light", Typeface.NORMAL)
        val primaryColor = getColorFromAttr(R.attr.primaryColor)
        val accentColor = getColorFromAttr(R.attr.accentColor)
        val hasAccent = accentColor != primaryColor
        val paddingHorizontal = (10 * resources.displayMetrics.density).toInt()
        val paddingVertical = (8 * resources.displayMetrics.density).toInt()

        val outValue = TypedValue()
        val selectableBackground = if (theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)) {
            outValue.resourceId
        } else 0

        val selected = LinkedHashSet<String>()
        val views = LinkedHashMap<String, TextView>()
        var lastTapped: String? = null

        fun refreshSave() {
            val n = selected.size
            binding.saveButton.visibility = if (n > 0) View.VISIBLE else View.GONE
            binding.saveButton.text = getString(R.string.word_picker_save_n, n)
        }

        fun styleChoice(choice: String) {
            val view = views[choice] ?: return
            val on = choice in selected
            view.setTextColor(if (on && hasAccent) accentColor else primaryColor)
            view.paintFlags = if (on && !hasAccent) {
                view.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            } else {
                view.paintFlags and Paint.UNDERLINE_TEXT_FLAG.inv()
            }
            view.isSelected = on
        }

        fun showMeaning(choice: String) {
            lastTapped = choice
            val line = binding.meaningLine
            val saved = savedByText[choice.trim().lowercase()]
            if (saved != null) {
                val meaning = saved.meaning.ifBlank { getString(R.string.word_row_no_meaning_yet) }
                line.text = getString(R.string.word_row_meaning, saved.text, meaning)
                line.visibility = View.VISIBLE
                return
            }
            fun render(result: WiktionaryApi.LookupResult) {
                line.text = when (result) {
                    is WiktionaryApi.LookupResult.Found ->
                        getString(R.string.word_row_wiktionary_meaning, choice, result.meaning)
                    WiktionaryApi.LookupResult.NotFound -> getString(R.string.word_row_no_meaning_found)
                    WiktionaryApi.LookupResult.Failed -> getString(R.string.word_row_unreachable)
                }
                line.visibility = View.VISIBLE
            }
            line.visibility = View.GONE
            lifecycleScope.launch {
                val appContext = applicationContext
                val cached = withContext(Dispatchers.IO) { WiktionaryCache(appContext).get(choice) }
                if (lastTapped != choice) return@launch
                if (cached != null) {
                    render(cached)
                    return@launch
                }
                if (!lookupAllowed) return@launch
                line.text = getString(R.string.word_row_looking_up)
                line.visibility = View.VISIBLE
                val result = withContext(Dispatchers.IO) {
                    val cache = WiktionaryCache(appContext)
                    WiktionaryApi.lookup(choice).also { cache.put(choice, it) }
                }
                if (lastTapped == choice) render(result)
            }
        }

        var lastLine: String? = null
        for (choice in choices) {
            val line = contexts?.get(choice.lowercase())
            if (line != null && lastLine != null && line != lastLine) {
                // A full-width spacer forces the next line's words onto a new row
                binding.wordsContainer.addView(
                    View(this),
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, paddingVertical)
                )
            }
            lastLine = line ?: lastLine
            val saved = isSaved(choice)
            val textView = TextView(this).apply {
                text = if (saved) {
                    SpannableStringBuilder(choice).append("  ").also {
                        val start = it.length
                        it.append(getString(R.string.word_picker_saved_mark))
                        it.setSpan(RelativeSizeSpan(0.6f), start, it.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                } else choice
                setTextAppearance(R.style.TextLarge)
                setTextColor(primaryColor)
                this.typeface = typeface
                setPadding(paddingHorizontal, paddingVertical, paddingHorizontal, paddingVertical)
                if (selectableBackground != 0) setBackgroundResource(selectableBackground)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (!saved) {
                        if (!selected.remove(choice)) selected.add(choice)
                        styleChoice(choice)
                        refreshSave()
                    }
                    showMeaning(choice)
                }
            }
            views[choice] = textView
            binding.wordsContainer.addView(textView)
        }

        val selectable = choices.filter { !isSaved(it) }
        if (showSelectAll && selectable.isNotEmpty()) {
            binding.selectAll.visibility = View.VISIBLE
            binding.selectAll.typeface = typeface
            binding.selectAll.setOnClickListener {
                selected.addAll(selectable)
                selectable.forEach { styleChoice(it) }
                refreshSave()
            }
        }

        binding.saveButton.typeface = typeface
        binding.meaningLine.typeface = typeface
        binding.saveButton.setOnClickListener {
            if (selected.isEmpty()) return@setOnClickListener
            if (contexts == null) {
                saveSelected(selected.toList(), context)
            } else {
                saveSelectedByLine(selected.toList(), contexts)
            }
            finish()
        }
    }
}
