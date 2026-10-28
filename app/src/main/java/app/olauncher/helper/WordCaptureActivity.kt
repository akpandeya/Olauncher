package app.olauncher.helper

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.graphics.Color
import android.graphics.Paint
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.CharacterStyle
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.UnderlineSpan
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.data.store.Capture
import app.olauncher.data.store.ContextSentence
import app.olauncher.data.store.ReaderText
import app.olauncher.data.store.WiktionaryApi
import app.olauncher.data.store.WiktionaryCache
import app.olauncher.data.store.ScreenNode
import app.olauncher.data.store.ScreenNodesCodec
import app.olauncher.data.store.ScreenWord
import app.olauncher.data.store.ScreenWords
import app.olauncher.data.store.WordCapture
import app.olauncher.data.store.WordLookup
import app.olauncher.data.store.Word
import app.olauncher.data.store.WordsStore
import app.olauncher.databinding.ActivityWordCaptureBinding
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WordCaptureActivity : AppCompatActivity() {

    companion object {
        fun setEnabled(context: Context, enabled: Boolean, lookupEnabled: Boolean) {
            val state = if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            val lookupState = if (lookupEnabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            listOf(
                ComponentName(context, WordCaptureActivity::class.java),
                ComponentName(context, "app.olauncher.helper.WordShareAlias"),
            ).forEach {
                context.packageManager.setComponentEnabledSetting(it, state, PackageManager.DONT_KILL_APP)
            }
            // The look-up share entry is useless while look-ups are switched off.
            listOf(
                ComponentName(context, WordLookupActivity::class.java),
                ComponentName(context, "app.olauncher.helper.WordLookupShareAlias")
            ).forEach {
                context.packageManager.setComponentEnabledSetting(it, lookupState, PackageManager.DONT_KILL_APP)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = Prefs(this)
        AppCompatDelegate.setDefaultNightMode(prefs.appTheme)

        // Reader or translucent sheet is decided from the intent before super.onCreate —
        // setTheme must happen there. Theme.WordCapture is built for the bottom sheet
        // floating over the app; the full-screen reader needs an opaque window (#591).
        // Shared text is classified early too: a sentence opens the reader as well.
        val nodesPath = intent?.getStringExtra(WordScreenService.EXTRA_NODES_PATH)
        val screenLines = intent?.getStringArrayListExtra(WordScreenService.EXTRA_SCREEN_LINES)
        val capture = WordCapture.classify(readIncomingText())
        if (nodesPath != null || screenLines != null || capture is Capture.Sentence) {
            setTheme(R.style.Theme_WordCapture_Reader)
        }
        prefs.applyFontOverlay(theme)
        super.onCreate(savedInstanceState)

        // Screen pick with a successful screenshot: the service hands over the node snapshot
        // (the frame path arrives too but is only used by the Circle-to-Search overlay, #592).
        if (nodesPath != null) {
            openReaderFromSnapshot(nodesPath, prefs)
            return
        }

        // Screen pick whose screenshot failed: the service falls back to the paragraph
        // strings. Synthetic nodes (no geometry) open the same reader — it needs none.
        if (screenLines != null) {
            openReader(
                screenLines.mapIndexed { index, text ->
                    ScreenNode(index, -1, text, null, null, null, null)
                },
                prefs,
            )
            return
        }

        when (capture) {
            is Capture.Word -> {
                saveWord(capture.text, context = "")
                finish()
            }
            // A shared sentence is one paragraph of prose — the reader handles it like a screen pick.
            is Capture.Sentence -> openReader(
                listOf(ScreenNode(0, -1, capture.text, null, null, null, null)),
                prefs,
            )
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

    /**
     * Reads the node snapshot the service wrote next to the frame. A malformed or missing
     * snapshot is never shown partially — [ScreenNodesCodec.decode] fails the whole file
     * and the reader shows the nothing-to-pick toast instead.
     */
    private fun openReaderFromSnapshot(path: String, prefs: Prefs) {
        val encoded = try {
            File(path).readText()
        } catch (_: Exception) {
            ""
        }
        openReader(ScreenNodesCodec.decode(encoded).orEmpty(), prefs)
    }

    /**
     * The full-screen Reader (#591): the capture's blocks in reading order with tappable
     * words. Tapping a word highlights it and shows its meaning line; Save stores it with
     * the sentence context cut from its node's paragraph. [nodes] may be raw capture
     * nodes — [ReaderText.blocks] filters the #588 noise and orders the kept ones.
     */
    private fun openReader(nodes: List<ScreenNode>, prefs: Prefs) {
        val blocks = ReaderText.blocks(nodes)
        if (blocks.isEmpty()) {
            showToast(R.string.nothing_to_pick_here)
            finish()
            return
        }
        val screenWords = ScreenWords.build(nodes)
        val wordsByNode = screenWords.words.groupBy { it.nodeId }

        val binding = ActivityWordCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.captureSheet.visibility = View.GONE
        binding.readerRoot.visibility = View.VISIBLE

        // Keep the reader clear of the system bars, top and bottom.
        val rootTopPad = binding.readerRoot.paddingTop
        val rootBottomPad = binding.readerRoot.paddingBottom
        binding.readerRoot.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            v.setPadding(
                v.paddingLeft,
                rootTopPad + insets.systemWindowInsetTop,
                v.paddingRight,
                rootBottomPad + insets.systemWindowInsetBottom
            )
            insets
        }

        val typeface = mainTypeface()
        val primaryColor = getColorFromAttr(R.attr.primaryColor)
        val accentColor = getColorFromAttr(R.attr.accentColor)
        val hasAccent = accentColor != primaryColor
        val mutedColor = getColorFromAttr(R.attr.inkMuted)
        val lineSpacing = 2 * resources.displayMetrics.density

        val store = WordsStore(this)
        val savedByText = store.all().associateBy { it.text.lowercase() }.toMutableMap()
        val lookupAllowed = WordLookup.isLookupAllowed(prefs.germanEnabled, prefs.germanLookup)
        val savedThisSession = HashSet<ScreenWord>()

        var highlighted: ScreenWord? = null
        var highlightedView: TextView? = null
        var highlightSpan: CharacterStyle? = null
        var meaningToken: String? = null

        /** The pickable token of a screen word, stripped like the old picker's choices. */
        fun tokenOf(word: ScreenWord): String =
            WordCapture.tokenize(word.text).firstOrNull() ?: word.text

        fun clearHighlight() {
            val view = highlightedView ?: return
            val span = highlightSpan ?: return
            (view.text as Spannable).removeSpan(span)
            highlighted = null
            highlightedView = null
            highlightSpan = null
        }

        fun setHighlight(word: ScreenWord, view: TextView) {
            clearHighlight()
            highlighted = word
            highlightedView = view
            val span = if (hasAccent) ForegroundColorSpan(accentColor) else UnderlineSpan()
            (view.text as Spannable).setSpan(
                span, word.charRange.first, word.charRange.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            highlightSpan = span
        }

        /** The persistent saved mark: the word keeps its underline after saving. */
        fun markSaved(word: ScreenWord, view: TextView) {
            (view.text as Spannable).setSpan(
                UnderlineSpan(), word.charRange.first, word.charRange.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        fun refreshSave() {
            // Always visible: the count is what saves this session, plus the highlighted
            // word still unsaved — so the button never reads "Save 0" without reason.
            val pending = highlighted?.takeIf { it !in savedThisSession }
            val n = savedThisSession.size + if (pending != null) 1 else 0
            binding.readerSaveButton.text = getString(R.string.word_picker_save_n, n)
        }

        fun showMeaning(token: String) {
            meaningToken = token
            val line = binding.readerMeaningLine
            val show = { text: String ->
                line.text = text
                line.visibility = View.VISIBLE
            }
            line.visibility = View.GONE
            resolveMeaning(
                token,
                savedByText[token.lowercase()],
                lookupAllowed,
                isCurrent = { meaningToken == token },
                show = show,
            )
        }

        binding.readerClose.setOnClickListener { finish() }
        binding.readerSaveButton.setOnClickListener {
            val word = highlighted?.takeIf { it !in savedThisSession } ?: return@setOnClickListener
            val view = highlightedView ?: return@setOnClickListener
            val token = tokenOf(word)
            val sentence = ContextSentence.around(
                screenWords.paragraphOf(word),
                word.charRange.first,
                word.charRange.last + 1,
            )
            val saved = WordsStore(this).add(token, sentence)
            WordLookup.enqueue(this, saved.id)
            savedByText[token.lowercase()] = saved
            savedThisSession += word
            markSaved(word, view)
            showToast(getString(R.string.saved_word_de, token))
            refreshSave()
        }
        refreshSave()

        fun wordTapSpan(word: ScreenWord, view: TextView) = object : ClickableSpan() {
            override fun onClick(widget: View) {
                setHighlight(word, view)
                refreshSave()
                showMeaning(tokenOf(word))
            }

            // Keep the prose look; the highlight on tap is added by setHighlight.
            override fun updateDrawState(ds: TextPaint) = Unit
        }

        for (block in blocks) {
            val textView = TextView(this).apply {
                setText(SpannableStringBuilder(block.text), TextView.BufferType.SPANNABLE)
                when (block.kind) {
                    ReaderText.Kind.KICKER -> {
                        setTextAppearance(R.style.TextSmall)
                        setTextColor(mutedColor)
                    }
                    ReaderText.Kind.HEADLINE -> {
                        setTextAppearance(R.style.TextLarge)
                        setTextColor(primaryColor)
                    }
                    ReaderText.Kind.BODY -> {
                        setTextAppearance(R.style.TextMedium)
                        setTextColor(primaryColor)
                    }
                }
                this.typeface = typeface
                setLineSpacing(lineSpacing, 1f)
                movementMethod = LinkMovementMethod.getInstance()
                highlightColor = Color.TRANSPARENT
            }
            val spannable = textView.text as Spannable
            for (word in wordsByNode[block.nodeId].orEmpty()) {
                val range = word.charRange
                // Words already in the store carry the saved underline from the start.
                if (savedByText.containsKey(tokenOf(word).lowercase())) {
                    spannable.setSpan(UnderlineSpan(), range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                spannable.setSpan(wordTapSpan(word, textView), range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            binding.readerContainer.addView(textView)
        }
    }

    /**
     * Meaning for one tapped word: a saved word shows its stored meaning, anything else
     * goes through the Wiktionary cache and, when look-ups are allowed, a live lookup.
     * [isCurrent] drops results for a word that is no longer tapped; [show] renders the line.
     */
    private fun resolveMeaning(
        choice: String,
        saved: Word?,
        lookupAllowed: Boolean,
        isCurrent: () -> Boolean,
        show: (String) -> Unit,
    ) {
        if (saved != null) {
            val meaning = saved.meaning.ifBlank { getString(R.string.word_row_no_meaning_yet) }
            show(getString(R.string.word_row_meaning, saved.text, meaning))
            return
        }
        lifecycleScope.launch {
            val appContext = applicationContext
            val cached = withContext(Dispatchers.IO) { WiktionaryCache(appContext).get(choice) }
            if (!isCurrent()) return@launch
            fun render(result: WiktionaryApi.LookupResult) {
                show(
                    when (result) {
                        is WiktionaryApi.LookupResult.Found ->
                            getString(R.string.word_row_wiktionary_meaning, result.baseForm ?: choice, result.meaning)
                        WiktionaryApi.LookupResult.NotFound -> getString(R.string.word_row_no_meaning_found)
                        WiktionaryApi.LookupResult.Failed -> getString(R.string.word_row_unreachable)
                    }
                )
            }
            if (cached != null) {
                render(cached)
                return@launch
            }
            if (!lookupAllowed) return@launch
            show(getString(R.string.word_row_looking_up))
            val result = withContext(Dispatchers.IO) {
                val cache = WiktionaryCache(appContext)
                WiktionaryApi.lookup(choice).also { cache.put(choice, it) }
            }
            if (isCurrent()) render(result)
        }
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

    /** The bottom sheet now only serves the shared-text list picker; a list is not prose. */
    private fun showPicker(
        choices: List<String>,
        context: String,
        showSelectAll: Boolean,
        prefs: Prefs,
    ) {
        val binding = ActivityWordCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.readerRoot.visibility = View.GONE
        binding.captureSheet.visibility = View.VISIBLE

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

        val store = WordsStore(this)
        val savedByText = store.all().associateBy { it.text.lowercase() }
        val isSaved = { choice: String -> savedByText.containsKey(choice.trim().lowercase()) }
        val lookupAllowed = WordLookup.isLookupAllowed(prefs.germanEnabled, prefs.germanLookup)

        val typeface = mainTypeface()
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
            val show = { text: String ->
                line.text = text
                line.visibility = View.VISIBLE
            }
            line.visibility = View.GONE
            resolveMeaning(
                choice,
                savedByText[choice.trim().lowercase()],
                lookupAllowed,
                isCurrent = { lastTapped == choice },
                show = show,
            )
        }

        for (choice in choices) {
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
            saveSelected(selected.toList(), context)
            finish()
        }
    }
}
