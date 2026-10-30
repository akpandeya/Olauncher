package app.olauncher.helper

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.graphics.Color
import android.graphics.Paint
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.CharacterStyle
import android.text.style.ClickableSpan
import android.text.style.RelativeSizeSpan
import android.text.style.UnderlineSpan
import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import app.olauncher.R
import app.olauncher.data.Prefs
import app.olauncher.data.store.AiJoinBudget
import app.olauncher.data.store.AiPracticeGate
import app.olauncher.data.store.AiProvider
import app.olauncher.data.store.AiProviderConfig
import app.olauncher.data.store.AiProviders
import app.olauncher.data.store.AiRequest
import app.olauncher.data.store.AiResult
import app.olauncher.data.store.Capture
import app.olauncher.data.store.CaptureMarks
import app.olauncher.data.store.CapturePaths
import app.olauncher.data.store.CapturePick
import app.olauncher.data.store.CaptureSave
import app.olauncher.data.store.KeySource
import app.olauncher.data.store.KeyVault
import app.olauncher.data.store.MeaningCardPlacement
import app.olauncher.data.store.PracticeSchemaModes
import app.olauncher.data.store.ReaderSelection
import app.olauncher.data.store.ReaderText
import app.olauncher.data.store.SavedMarks
import app.olauncher.data.store.ScreenMapping
import app.olauncher.data.store.SharedOfflineGermanStore
import app.olauncher.data.store.SplitVerbAi
import app.olauncher.data.store.SplitVerbs
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
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
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

        /** Saved in [onSaveInstanceState]: the Reader is the visible mode (rotation keeps it). */
        private const val STATE_READER_MODE = "app.olauncher.READER_MODE"

        /** Saved in [onSaveInstanceState]: AI split-verb suggestions issued this capture. */
        private const val STATE_AI_JOIN_USED = "app.olauncher.AI_JOIN_USED"

        /** Saved in [onSaveInstanceState]: the capture session the AI budget counter belongs to. */
        private const val STATE_AI_JOIN_SESSION = "app.olauncher.AI_JOIN_SESSION"

        /**
         * Tier-3 AI split-verb suggestions per capture (#523): a capture is minutes long,
         * so the cap bounds the cost of the fallback while still covering a few unknowns.
         */
        private const val AI_JOIN_SESSION_CAP = 3

        /** The AI suggestion budgets of the capture sessions alive in this process (#698). */
        private val aiJoinBudget = AiJoinBudget(AI_JOIN_SESSION_CAP)
    }

    /** Capture files this intent referenced (path → lastModified at open); deleted on close. */
    private val referencedFiles = LinkedHashMap<String, Long>()

    /**
     * AI split-verb suggestions issued this capture session (#523 tier 3). Counted when a
     * request goes out, not when it succeeds, so retries cannot exceed [AI_JOIN_SESSION_CAP].
     * The counter lives in the process-scoped [aiJoinBudget], keyed by [aiJoinSessionId]:
     * rotation rebinds to the same counter, so an in-flight request from the previous
     * instance cannot under-count the restored session (#698).
     */
    private lateinit var aiJoinCounter: AtomicInteger

    /** The capture session's id, carried through rotation via [STATE_AI_JOIN_SESSION] (#698). */
    private lateinit var aiJoinSessionId: String

    /** The Reader is the visible UI — remembered across rotation so it is not lost. */
    private var readerMode = false

    /** The pending picks shared by the overlay and the reader, so a switch keeps them (#523). */
    private var captureSelection: ReaderSelection<CapturePick>? = null

    /** Saved words by lowercased token, shared so the pending selection's isSaved stays fresh. */
    private val captureSavedByText = HashMap<String, Word>()

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = Prefs(this)
        AppCompatDelegate.setDefaultNightMode(prefs.appTheme)

        // Reader or translucent sheet is decided from the intent before super.onCreate —
        // setTheme must happen there. Theme.WordCapture is built for the bottom sheet
        // floating over the app; the full-screen reader needs an opaque window (#591).
        // Shared text is classified early too: a sentence opens the reader as well.
        // The activity is exported: path extras are honored only when they name exactly
        // the capture files the service writes — any other path could make us read or
        // delete a private file, so it falls back to the Reader path like a missing one.
        val nodesPath = intent?.getStringExtra(WordScreenService.EXTRA_NODES_PATH)
            ?.takeIf { CapturePaths.isCaptureFile(it, cacheDir, CapturePaths.NODES_FILE) }
        val framePath = intent?.getStringExtra(WordScreenService.EXTRA_FRAME_PATH)
            ?.takeIf { CapturePaths.isCaptureFile(it, cacheDir, CapturePaths.FRAME_FILE) }
        val screenLines = intent?.getStringArrayListExtra(WordScreenService.EXTRA_SCREEN_LINES)
        val capture = WordCapture.classify(readIncomingText())
        val restoredReader = savedInstanceState?.getBoolean(STATE_READER_MODE, false) == true
        // Rotation mid-capture rebinds to the same session counter (#698), so the per-capture
        // AI budget is not refilled by turning the device — and an in-flight request from the
        // previous instance increments a counter this instance still reads. After process
        // death the counter is gone; the count saved in the bundle seeds a fresh one.
        aiJoinSessionId = savedInstanceState?.getString(STATE_AI_JOIN_SESSION)
            ?: UUID.randomUUID().toString()
        aiJoinCounter = aiJoinBudget.counter(
            aiJoinSessionId,
            savedInstanceState?.getInt(STATE_AI_JOIN_USED, 0) ?: 0,
        )
        if (nodesPath != null || screenLines != null || capture is Capture.Sentence || restoredReader) {
            setTheme(R.style.Theme_WordCapture_Reader)
        }
        prefs.applyFontOverlay(theme)
        prefs.applyAccentOverlay(theme)
        super.onCreate(savedInstanceState)

        // Remember what this intent referenced: only those capture files are deleted on
        // close, and only while their timestamp is unchanged — the names are fixed, so
        // in multi-window mode a newer capture may have rewritten them already (N1).
        referencedFiles.clear()
        for (path in listOfNotNull(framePath, nodesPath)) {
            referencedFiles[path] = File(path).lastModified()
        }

        // Screen pick with a successful screenshot: the frame + node snapshot drive the
        // Circle-to-Search overlay (#592); every failure on the way falls back to the reader.
        // After a rotation with the Reader open, the reader is restored instead — the user
        // did not ask for the overlay again.
        if (nodesPath != null) {
            if (framePath != null && !restoredReader) {
                openOverlayCapture(framePath, nodesPath, prefs)
            } else {
                openReaderFromSnapshot(nodesPath, prefs)
            }
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
     * The Circle-to-Search overlay (#592): decodes the frozen frame and the node snapshot
     * off the main thread, then verifies that both describe the same picture. A missing
     * frame, a failed decode, a node set without any boxed words, or a
     * [ScreenMapping.Mismatch] falls back to the reader — it needs no geometry.
     */
    private fun openOverlayCapture(framePath: String, nodesPath: String, prefs: Prefs) {
        lifecycleScope.launch {
            val decoded = withContext(Dispatchers.IO) {
                val encoded = try {
                    File(nodesPath).readText()
                } catch (_: Exception) {
                    ""
                }
                val nodes = ScreenNodesCodec.decode(encoded).orEmpty()
                val bitmap = try {
                    BitmapFactory.decodeFile(framePath)
                } catch (_: Exception) {
                    null
                }
                nodes to bitmap
            }
            val (nodes, bitmap) = decoded
            if (isFinishing || isDestroyed) {
                bitmap?.recycle()
                return@launch
            }
            if (bitmap == null) {
                openReader(nodes, prefs)
                return@launch
            }
            if (nodes.isEmpty()) {
                // The reader shows the nothing-to-pick toast for an empty snapshot.
                bitmap.recycle()
                openReader(nodes, prefs)
                return@launch
            }
            val screenWords = ScreenWords.build(nodes)
            // Zero-area boxes (hidden nodes parked at 0,0,0,0) are not real geometry;
            // ScreenWords already drops them, and only boxed words can drive the overlay.
            val boxed = screenWords.words.filter { it.box != null }
            if (boxed.isEmpty()) {
                // No geometry at all: hit-testing is impossible, the reader still works.
                bitmap.recycle()
                openReader(nodes, prefs)
                return@launch
            }
            // The root node's bounds describe the screen the nodes were read from; the
            // union of every node is the fallback (reduceOrNull — an empty list must not
            // crash the coroutine) and no bounds at all leaves nothing to verify against.
            val rootBounds = nodes.firstOrNull { node ->
                node.parentId == -1 && node.bounds?.let { it.width > 0 && it.height > 0 } == true
            }?.bounds
                ?: nodes.mapNotNull { it.bounds }.reduceOrNull { a, b -> a.union(b) }
            if (rootBounds == null) {
                bitmap.recycle()
                openReader(nodes, prefs)
                return@launch
            }
            val anchors = listOfNotNull(boxed.first().box, boxed.last().box)
            val verified = ScreenMapping.verify(
                ScreenMapping.Frame(bitmap.width, bitmap.height),
                rootBounds,
                anchors,
            ) as? ScreenMapping.Verified
            if (verified == null) {
                // The frame and the nodes drifted apart — boxes would land in the wrong place.
                bitmap.recycle()
                openReader(nodes, prefs)
                return@launch
            }
            showOverlay(bitmap, nodes, screenWords, verified, prefs)
        }
    }

    /**
     * The overlay UI: dimmed frame, tap/circle picking, meaning card. Saves go through the
     * same store as the reader, the live count and saved dots update with every save.
     */
    private fun showOverlay(
        frame: Bitmap,
        nodes: List<ScreenNode>,
        screenWords: ScreenWords,
        mapping: ScreenMapping.Verified,
        prefs: Prefs,
    ) {
        val binding = ActivityWordCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.captureSheet.visibility = View.GONE
        binding.readerRoot.visibility = View.GONE
        binding.overlayRoot.visibility = View.VISIBLE
        readerMode = false
        // Reading the meaning card without touching the screen can outlast the screen
        // timeout, whose onStop cleanup would close the capture. Screen-off while the
        // user is actively reading is that side effect, not a leave — keep the screen on
        // while this window is visible (no permission; the F5 cleanup stays as it is).
        binding.root.keepScreenOn = true

        // Keep the top bar clear of the status bar; the frame fills the rest. The overlay's
        // content view is set from a coroutine (after the first insets dispatch), so a
        // fresh dispatch is requested below, after the listener is installed — the
        // listener alone would never fire.
        var bottomInset = ViewCompat.getRootWindowInsets(binding.overlayRoot)
            ?.getInsets(WindowInsetsCompat.Type.systemBars())
            ?.bottom ?: 0
        val overlayTopPad = binding.overlayRoot.paddingTop

        val overlay = binding.captureOverlay
        overlay.accentColor = getColorFromAttr(R.attr.accentColor)
        overlay.setFrame(frame, screenWords.words, mapping.scaleX, mapping.scaleY)

        val store = WordsStore(this)
        captureSavedByText.clear()
        captureSavedByText.putAll(store.all().associateBy { it.text.lowercase() })
        overlay.setSavedTexts(captureSavedByText.keys)
        val lookupAllowed = WordLookup.isLookupAllowed(prefs.germanEnabled, prefs.germanLookup)
        val savedThisSession = HashSet<ScreenWord>()

        // One pending set, shared with the reader opened from here: a tap toggles exactly
        // like the reader, a stroke/circle or a long-press drag adds every touched word.
        // Keys come from SavedMarks.keyOf(pick), so a joined split verb keys by its lemma.
        val selection = ReaderSelection<CapturePick>(
            isSaved = { captureSavedByText.containsKey(SavedMarks.keyOf(it)) },
            keyOf = { SavedMarks.keyOf(it) },
        )
        captureSelection = selection

        binding.overlayClose.typeface = mainTypeface()
        binding.overlayHint.typeface = mainTypeface()
        binding.overlayMenu.typeface = mainTypeface()
        binding.overlaySaveButton.typeface = mainTypeface()
        binding.overlaySaveCount.typeface = mainTypeface()
        binding.overlayCardWord.typeface = mainTypeface()
        binding.overlayCardMeaning.typeface = mainTypeface()
        binding.overlayCardJoin.typeface = mainTypeface()
        binding.overlayCardReader.typeface = mainTypeface()
        // Hint seen: keep the space (INVISIBLE, not GONE) so the ⋮ stays at the trailing edge.
        if (prefs.captureHintSeen) binding.overlayHint.visibility = View.INVISIBLE

        /** The word the meaning card is showing — drawn with the thicker outline. */
        var focusedWord: ScreenWord? = null
        var meaningToken: String? = null

        /** The split-verb join the card is offering, if any; the id drops stale detections. */
        var overlayJoinMatch: SplitVerbs.Match? = null
        var overlayJoinRequestId = 0

        fun refreshPending() {
            overlay.pendingWords = selection.pending.flatMap { it.words }.toSet()
            overlay.focusedWord = focusedWord
        }

        fun refreshSaveCount() {
            binding.overlaySaveCount.visibility = if (savedThisSession.isEmpty()) View.GONE else View.VISIBLE
            binding.overlaySaveCount.text = getString(R.string.capture_saved_count, savedThisSession.size)
            binding.overlaySaveButton.text = getString(R.string.word_picker_save_n, selection.count)
        }

        fun hideCard() {
            binding.overlayCard.visibility = View.GONE
            focusedWord = null
            refreshPending()
        }

        fun positionCard(word: ScreenWord) {
            // Measured height first, then the placement: the card is full-width, only the
            // top edge comes from [MeaningCardPlacement].
            binding.overlayCard.post {
                val box = overlay.viewBoxOf(word) ?: return@post
                if (binding.overlayContainer.height <= 0) return@post
                val width = if (binding.overlayCard.width > 0) {
                    binding.overlayCard.width
                } else {
                    val marginPx = (24 * resources.displayMetrics.density).toInt()
                    (binding.overlayContainer.width - marginPx).coerceAtLeast(0)
                }
                if (width > 0) {
                    val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
                    val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    binding.overlayCard.measure(widthSpec, heightSpec)
                }
                // After the explicit measure, measuredHeight is fresh; height can still
                // hold the previous layout's larger value when the card shrank (a new
                // pick hides the meaning line) and placement would sit it too high.
                val cardHeight = if (width > 0) {
                    binding.overlayCard.measuredHeight
                } else {
                    binding.overlayCard.height
                }
                val placement = MeaningCardPlacement.place(
                    box,
                    binding.overlayContainer.height,
                    cardHeight,
                    bottomInset,
                )
                (binding.overlayCard.layoutParams as FrameLayout.LayoutParams).topMargin = placement.top
                binding.overlayCard.requestLayout()
            }
        }

        fun hideJoinChip() {
            overlayJoinMatch = null
            binding.overlayCardJoin.visibility = View.GONE
        }

        fun showJoinChip(match: SplitVerbs.Match) {
            overlayJoinMatch = match
            binding.overlayCardJoin.text = getString(
                R.string.capture_join_split,
                SavedMarks.tokenOf(match.verb),
                SavedMarks.tokenOf(match.particle),
                match.lemma,
            )
            // The static contentDescription is only the XML fallback; announce the
            // actual suggestion so screen readers read the real verbs and lemma.
            binding.overlayCardJoin.contentDescription = binding.overlayCardJoin.text
            binding.overlayCardJoin.visibility = View.VISIBLE
            focusedWord?.let { positionCard(it) }
        }

        /** Runs the split-verb cascade for [word] off the main thread; only the newest tap wins. */
        fun detectSplitVerb(word: ScreenWord) {
            val requestId = ++overlayJoinRequestId
            hideJoinChip()
            val nodeWords = screenWords.words.filter { it.nodeId == word.nodeId }
            val paragraph = screenWords.paragraphOf(word)
            lifecycleScope.launch {
                val match = resolveSplitVerb(nodeWords, word, paragraph, prefs.germanOfflineDictionary, lookupAllowed)
                if (overlayJoinRequestId != requestId) return@launch
                if (match != null) showJoinChip(match)
            }
        }

        /** Shows the meaning card for the card's word — the gesture's head or the last tap. */
        fun showCard(word: ScreenWord) {
            val token = SavedMarks.tokenOf(word)
            // A slow tap resolves the same word twice (long-press UP, then the tap), and a
            // stroke re-reports a head already up. The first call's look-up is still in
            // flight (its result is not cached yet), so restarting it would spend another
            // request — only the title and placement need refreshing in that case.
            val restart = token != meaningToken || binding.overlayCard.visibility != View.VISIBLE
            meaningToken = token
            focusedWord = word
            binding.overlayCardWord.text = word.text
            if (restart) {
                val line = binding.overlayCardMeaning
                val show: (String) -> Unit = { text ->
                    line.text = text
                    line.visibility = View.VISIBLE
                    focusedWord?.let { positionCard(it) }
                }
                line.visibility = View.GONE
                resolveMeaning(
                    token,
                    captureSavedByText[token.lowercase()],
                    lookupAllowed,
                    isCurrent = { meaningToken == token },
                    show = show,
                )
            }
            binding.overlayCard.visibility = View.VISIBLE
            positionCard(word)
            refreshPending()
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.overlayRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            bottomInset = bars.bottom
            v.setPadding(v.paddingLeft, overlayTopPad + bars.top, v.paddingRight, v.paddingBottom)
            focusedWord?.let { positionCard(it) }
            insets
        }
        ViewCompat.requestApplyInsets(binding.overlayRoot)

        fun openReaderView() {
            // The overlay is done with the frame, but its pending picks are not: hand the
            // shared selection to the reader so they carry over as pending marks.
            overlay.releaseFrame()
            frame.recycle()
            openReader(nodes, prefs, captureSelection)
        }

        /** The first successful pick retires the hint, once — later picks must not rewrite the pref. */
        fun markHintSeen() {
            if (!prefs.captureHintSeen) prefs.captureHintSeen = true
            binding.overlayHint.visibility = View.INVISIBLE
        }

        // Tap toggles one word, exactly like the reader; empty space hides the card and
        // keeps the pending picks.
        overlay.onTap = { word ->
            if (word == null) {
                meaningToken = null
                overlayJoinRequestId++
                hideJoinChip()
                hideCard()
            } else {
                markHintSeen()
                selection.tap(CapturePick.Word(word))
                val focused = selection.lastTapped?.words?.firstOrNull() ?: word
                showCard(focused)
                detectSplitVerb(word)
            }
            refreshSaveCount()
        }

        // A stroke, circle or committed long-press drag adds every touched word (add-only).
        overlay.onAddSelection = { picked ->
            markHintSeen()
            overlayJoinRequestId++
            hideJoinChip()
            selection.addAll(picked.words.map { CapturePick.Word(it) })
            showCard(picked.head)
            refreshSaveCount()
        }
        overlay.onCloseSwipe = { finish() }

        binding.overlayClose.setOnClickListener { finish() }
        binding.overlayMenu.setOnClickListener {
            binding.overlayMenu.showPopupMenu(
                configure = { menu -> menu.add(Menu.NONE, 1, 0, R.string.capture_reader_view) }
            ) { item ->
                if (item.itemId == 1) openReaderView()
            }
        }
        binding.overlayCardReader.setOnClickListener { openReaderView() }

        binding.overlayCardJoin.setOnClickListener {
            val match = overlayJoinMatch ?: return@setOnClickListener
            selection.join(
                CapturePick.SplitVerb(match.verb, match.particle, match.lemma),
                listOf(CapturePick.Word(match.verb), CapturePick.Word(match.particle)),
            )
            hideJoinChip()
            refreshPending()
            refreshSaveCount()
            // The card now explains the joined verb, not the tapped component word —
            // the reader's chip handler shows the lemma's meaning the same way.
            meaningToken = match.lemma
            val line = binding.overlayCardMeaning
            val show: (String) -> Unit = { text ->
                line.text = text
                line.visibility = View.VISIBLE
                focusedWord?.let { positionCard(it) }
            }
            line.visibility = View.GONE
            binding.overlayCardWord.text = match.lemma
            resolveMeaning(
                match.lemma,
                captureSavedByText[match.lemma.lowercase()],
                lookupAllowed,
                isCurrent = { meaningToken == match.lemma },
                show = show,
            )
        }

        binding.overlaySaveButton.setOnClickListener {
            if (selection.isEmpty) return@setOnClickListener
            val picks = selection.pending.toList()
            val plan = CaptureSave.plan(picks, screenWords::paragraphOf)
            for ((pick, item) in picks.zip(plan)) {
                val saved = store.add(item.text, item.context)
                WordLookup.enqueue(this, saved.id)
                captureSavedByText[item.text.lowercase()] = saved
                // A joined split verb must not lock its component tokens as saved: "auf"
                // would otherwise stop being pickable for the rest of the session.
                if (pick is CapturePick.Word) savedThisSession += item.words
            }
            overlay.setSavedTexts(captureSavedByText.keys)
            selection.clear()
            val message = if (plan.size == 1) {
                getString(R.string.saved_word_de, plan[0].text)
            } else {
                getString(R.string.words_saved_de, plan.size)
            }
            showToast(message)
            meaningToken = null
            overlayJoinRequestId++
            hideJoinChip()
            hideCard()
            refreshSaveCount()
        }
        refreshSaveCount()
        refreshPending()
    }

    /**
     * Leaving the capture (Home, another app) closes it like ✕: the activity is
     * excludeFromRecents, so the user could never return, and the full-resolution frame
     * in memory plus its screenshot file on disk must not outlive the session. A
     * rotation keeps the activity (and the Reader mode, see [onSaveInstanceState]).
     */
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !isFinishing) finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // A rotation re-runs onCreate with the same intent; remember a Reader user's
        // mode so they are not dropped back into the overlay.
        if (readerMode) outState.putBoolean(STATE_READER_MODE, true)
        // The AI suggestion cap counts a whole capture session: a rotation mid-capture
        // must not grant a fresh budget (the activity is recreated, not finished). The
        // session id lets the recreated instance rebind to the same counter (#698); the
        // count covers process death, where the counter no longer exists.
        outState.putString(STATE_AI_JOIN_SESSION, aiJoinSessionId)
        outState.putInt(STATE_AI_JOIN_USED, aiJoinCounter.get())
    }

    /** Closes the capture: ✕, back and the close swipe all land here via [finish]. */
    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            // A real close ends the capture session; a rotation (isFinishing false)
            // keeps the budget alive for the recreated instance (#698).
            aiJoinBudget.release(aiJoinSessionId)
            // Only the files this intent referenced — the reader path never touches them.
            // The service overwrites both on the next press, so this is tidy-up; a file
            // whose timestamp moved on belongs to a newer capture and stays.
            for ((path, openedAt) in referencedFiles) {
                val file = File(path)
                if (CapturePaths.unchanged(openedAt, file)) file.delete()
            }
        }
    }

    /**
     * The full-screen Reader (#591): the capture's blocks in reading order with tappable
     * words. Tapping a word highlights it and shows its meaning line; Save stores every
     * pending pick with the sentence context cut from its node's paragraph. [nodes] may be
     * raw capture nodes — [ReaderText.blocks] filters the #588 noise and orders the kept
     * ones. [sharedSelection], when the reader is opened from the overlay, is the one
     * pending set both surfaces share: its picks are painted as pending marks here.
     */
    private fun openReader(
        nodes: List<ScreenNode>,
        prefs: Prefs,
        sharedSelection: ReaderSelection<CapturePick>? = null,
    ) {
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
        readerMode = true
        // Same screen-timeout reasoning as the overlay: a reader left open on the table
        // must not lose the capture to the screen turning off.
        binding.root.keepScreenOn = true

        // Keep the reader clear of the system bars, top and bottom. The reader can be
        // opened from a coroutine (after the first insets dispatch), so a fresh dispatch
        // is requested here — the listener alone would never fire.
        val rootTopPad = binding.readerRoot.paddingTop
        val rootBottomPad = binding.readerRoot.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.readerRoot) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                v.paddingLeft,
                rootTopPad + bars.top,
                v.paddingRight,
                rootBottomPad + bars.bottom,
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.readerRoot)

        val typeface = mainTypeface()
        val primaryColor = getColorFromAttr(R.attr.primaryColor)
        val accentColor = getColorFromAttr(R.attr.accentColor)
        val hasAccent = accentColor != primaryColor
        val mutedColor = getColorFromAttr(R.attr.inkMuted)
        val lineSpacing = 2 * resources.displayMetrics.density

        val store = WordsStore(this)
        captureSavedByText.clear()
        captureSavedByText.putAll(store.all().associateBy { it.text.lowercase() })
        val lookupAllowed = WordLookup.isLookupAllowed(prefs.germanEnabled, prefs.germanLookup)
        val savedThisSession = HashSet<ScreenWord>()

        fun isWordSaved(word: ScreenWord): Boolean =
            word in savedThisSession || captureSavedByText.containsKey(SavedMarks.keyOf(word))

        // Pending picks are keyed by SavedMarks.keyOf(pick): a word by its token, a joined
        // split verb by its lemma. The store dedupes case-insensitively, so two occurrences
        // of the same word ("Regierung" / "Regierung.") are one selection, one save and one
        // toast. A selection handed in from the overlay is reused, so picks made there stay
        // pending here.
        val selection = sharedSelection ?: ReaderSelection<CapturePick>(
            isSaved = { captureSavedByText.containsKey(SavedMarks.keyOf(it)) },
            keyOf = { SavedMarks.keyOf(it) },
        )
        // One key can hold several pending marks: a joined split verb fills both its ranges.
        val pendingSpans = HashMap<String, MutableList<Pair<TextView, CharacterStyle>>>()

        val viewsByNode = HashMap<Int, TextView>()
        var meaningToken: String? = null

        /** The split-verb join the chip is offering, if any; the id drops stale detections. */
        var joinMatch: SplitVerbs.Match? = null
        var joinRequestId = 0

        /** Drops every pending fill under [key] (a word's one mark, or a split verb's two). */
        fun clearPending(key: String) {
            pendingSpans.remove(key)?.forEach { (view, span) ->
                (view.text as Spannable).removeSpan(span)
            }
        }

        /** Adds one pending fill for [word] in [view], under the pick's [key]. */
        fun addPending(key: String, view: TextView, word: ScreenWord) {
            val span = BackgroundColorSpan(CaptureMarks.pendingFill(if (hasAccent) accentColor else primaryColor))
            (view.text as Spannable).setSpan(
                span, word.charRange.first, word.charRange.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            pendingSpans.getOrPut(key) { ArrayList() }.add(view to span)
        }

        fun hideJoinChip() {
            joinMatch = null
            binding.readerJoinChip.visibility = View.GONE
        }

        fun showJoinChip(match: SplitVerbs.Match) {
            joinMatch = match
            binding.readerJoinChip.text = getString(
                R.string.capture_join_split,
                SavedMarks.tokenOf(match.verb),
                SavedMarks.tokenOf(match.particle),
                match.lemma,
            )
            // The static contentDescription is only the XML fallback; announce the
            // actual suggestion so screen readers read the real verbs and lemma.
            binding.readerJoinChip.contentDescription = binding.readerJoinChip.text
            binding.readerJoinChip.visibility = View.VISIBLE
        }

        /** Runs the split-verb cascade for [word] off the main thread; only the newest tap wins. */
        fun detectSplitVerb(word: ScreenWord) {
            val requestId = ++joinRequestId
            hideJoinChip()
            val nodeWords = wordsByNode[word.nodeId].orEmpty()
            val paragraph = screenWords.paragraphOf(word)
            lifecycleScope.launch {
                val match = resolveSplitVerb(nodeWords, word, paragraph, prefs.germanOfflineDictionary, lookupAllowed)
                if (joinRequestId != requestId) return@launch
                if (match != null) showJoinChip(match)
            }
        }

        /** The persistent saved mark: the word keeps its underline after saving. */
        fun markSaved(word: ScreenWord, view: TextView) {
            (view.text as Spannable).setSpan(
                UnderlineSpan(), word.charRange.first, word.charRange.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        fun refreshSave() {
            binding.readerSaveButton.text = getString(R.string.word_picker_save_n, selection.count)
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
                captureSavedByText[token.lowercase()],
                lookupAllowed,
                isCurrent = { meaningToken == token },
                show = show,
            )
        }

        binding.readerJoinChip.typeface = typeface
        binding.readerJoinChip.setOnClickListener {
            val match = joinMatch ?: return@setOnClickListener
            val pick = CapturePick.SplitVerb(match.verb, match.particle, match.lemma)
            val joined = selection.join(
                pick,
                listOf(CapturePick.Word(match.verb), CapturePick.Word(match.particle)),
            )
            if (!joined) {
                // The lemma is already saved (earlier session): stop offering the join,
                // like the overlay's handler, instead of leaving a dead chip on screen.
                hideJoinChip()
                return@setOnClickListener
            }
            // The component picks are gone; the joined lemma carries both ranges as one mark.
            clearPending(SavedMarks.keyOf(pick))
            clearPending(SavedMarks.keyOf(CapturePick.Word(match.verb)))
            clearPending(SavedMarks.keyOf(CapturePick.Word(match.particle)))
            val key = SavedMarks.keyOf(pick)
            viewsByNode[match.verb.nodeId]?.let { addPending(key, it, match.verb) }
            viewsByNode[match.particle.nodeId]?.let { addPending(key, it, match.particle) }
            hideJoinChip()
            refreshSave()
            // The meaning line follows the joined lemma, so "aufnehmen" shows its gloss.
            showMeaning(match.lemma)
        }

        binding.readerClose.setOnClickListener { finish() }
        binding.readerSaveButton.setOnClickListener {
            if (selection.isEmpty) return@setOnClickListener
            val picks = selection.pending.toList()
            val plan = CaptureSave.plan(picks, screenWords::paragraphOf)
            for ((pick, item) in picks.zip(plan)) {
                val saved = store.add(item.text, item.context)
                WordLookup.enqueue(this, saved.id)
                captureSavedByText[item.text.lowercase()] = saved
                // A joined split verb must not lock its component tokens as saved: "auf"
                // would otherwise stop being pickable for the rest of the session.
                if (pick is CapturePick.Word) savedThisSession += item.words

                // Drop the pending fill(s) and underline every saved range from its own node.
                clearPending(item.text.lowercase())
                for (word in item.words) {
                    viewsByNode[word.nodeId]?.let { markSaved(word, it) }
                }
            }
            for ((nodeId, nodeWords) in wordsByNode) {
                val view = viewsByNode[nodeId] ?: continue
                for (nw in nodeWords) {
                    if (captureSavedByText.containsKey(SavedMarks.keyOf(nw))) {
                        markSaved(nw, view)
                    }
                }
            }
            selection.clear()
            joinRequestId++
            hideJoinChip()
            val message = if (plan.size == 1) {
                getString(R.string.saved_word_de, plan[0].text)
            } else {
                getString(R.string.words_saved_de, plan.size)
            }
            showToast(message)
            refreshSave()
        }
        refreshSave()

        fun wordTapSpan(word: ScreenWord, view: TextView) = object : ClickableSpan() {
            override fun onClick(widget: View) {
                val key = SavedMarks.keyOf(word)
                val added = selection.tap(CapturePick.Word(word))
                if (added) {
                    // The pending entry may come from another occurrence of the same
                    // token: move the highlight off that copy before marking this one,
                    // so exactly one occurrence of the token stays highlighted.
                    clearPending(key)
                    // Pending means "selected, not saved yet": a translucent background
                    // fill, which stays visible whatever the font setting resolves to.
                    // Bold would be invisible under the bold-font setting (#P3) and the
                    // saved underline is a different mark, so neither replaces the fill.
                    addPending(key, view, word)
                } else if (!isWordSaved(word)) {
                    // The tapped occurrence is the highlighted copy for its key — this
                    // view loses the highlight and the pending entry is gone.
                    clearPending(key)
                }
                refreshSave()
                // The meaning line follows the most recently tapped word — the
                // selection's view of "last tap", not this span's word.
                selection.lastTapped?.words?.firstOrNull()?.let { showMeaning(SavedMarks.tokenOf(it)) }
                detectSplitVerb(word)
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
            viewsByNode[block.nodeId] = textView
            for (word in wordsByNode[block.nodeId].orEmpty()) {
                val range = word.charRange
                // Words already in the store carry the saved underline from the start.
                if (captureSavedByText.containsKey(SavedMarks.keyOf(word))) {
                    spannable.setSpan(UnderlineSpan(), range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                // Picks carried in from the overlay are pending here too: a translucent fill
                // on the very occurrence that was picked, next to the saved underline. A
                // joined split verb fills both of its ranges under its one lemma key.
                val pick = selection.pending.firstOrNull { it.words.contains(word) }
                if (pick != null) {
                    addPending(SavedMarks.keyOf(pick), textView, word)
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
            // Offline-first, like the drawer search: the bundled dictionary answers a joined
            // "aufnehmen" with its gloss without any network (#523).
            val offline = withContext(Dispatchers.IO) {
                if (Prefs(appContext).germanOfflineDictionary) {
                    SharedOfflineGermanStore.get(appContext).lookup(choice)
                } else {
                    null
                }
            }
            if (!isCurrent()) return@launch
            val direct = offline ?: withContext(Dispatchers.IO) { WiktionaryCache(appContext).get(choice) }
            if (!isCurrent()) return@launch
            if (direct != null) {
                render(direct)
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

    /**
     * Resolves the split verb behind [tapped] (#523). Tier 1 queries the bundled dictionary
     * ([SplitVerbs.find], only when the offline dictionary is on); on a miss, and only when
     * online look-ups are allowed, tier 2 composes "<particle><verb lemma>" online and
     * verifies it exists before offering it. When both find nothing, tier 3 asks the AI for
     * a lemma from [paragraph] and verifies it before it may show ([aiSplitVerb]). Runs on
     * Dispatchers.IO, never on the tap thread.
     */
    private suspend fun resolveSplitVerb(
        nodeWords: List<ScreenWord>,
        tapped: ScreenWord,
        paragraph: String,
        offlineAllowed: Boolean,
        onlineAllowed: Boolean,
    ): SplitVerbs.Match? = withContext(Dispatchers.IO) {
        val pair = SplitVerbs.pairs(nodeWords, tapped).firstOrNull()
        if (offlineAllowed) {
            val store = SharedOfflineGermanStore.get(applicationContext)
            SplitVerbs.find(nodeWords, tapped) { store.splitVerbLemmas(it) }?.let { return@withContext it }
        }
        if (onlineAllowed && pair != null) {
            onlineSplitVerb(pair.first, pair.second)?.let { return@withContext it }
        }
        // Tier 3: only when both dictionary tiers found nothing. The AI proposes, the
        // dictionary verifies — a proposal alone never becomes a chip.
        if (pair != null) aiSplitVerb(pair, tapped, paragraph, offlineAllowed)?.let { return@withContext it }
        null
    }

    /**
     * Tier 3 of the join cascade (#523): both dictionary tiers found nothing, so the AI is
     * asked about [tapped] and its [paragraph]. The answer is only a proposal — the lemma is
     * verified against the dictionary ([SharedOfflineGermanStore.isVerbLemma], or a Wiktionary
     * existence check when the bundled dictionary is off) before the chip may show, and the
     * chip then joins and saves through the same path as tiers 1-2 (never a direct save).
     *
     * Gated by the AI-practice switch, key and model ([AiPracticeGate]), like the practice
     * paths; at most [AI_JOIN_SESSION_CAP] requests per capture, counted when a request is
     * issued. Any error, timeout or invalid answer yields null, silently. The caller runs this
     * on Dispatchers.IO; the tap path never waits on it.
     */
    private suspend fun aiSplitVerb(
        pair: Pair<ScreenWord, ScreenWord>,
        tapped: ScreenWord,
        paragraph: String,
        offlineAllowed: Boolean,
    ): SplitVerbs.Match? {
        if (aiJoinCounter.get() >= AI_JOIN_SESSION_CAP) return null
        val aiPrefs = Prefs(applicationContext)
        // Same key source as the practice feature: KeyVault through a KeySource, never logged.
        val vault = KeyVault(applicationContext)
        val keySource = KeySource { vault.read() }
        if (!AiPracticeGate.canRun(aiPrefs.aiPracticeEnabled, !keySource.get().isNullOrBlank(), aiPrefs.aiModel)) {
            return null
        }
        val provider = AiProviders.create(
            AiProviderConfig(aiPrefs.aiProvider, aiPrefs.aiBaseUrl, aiPrefs.aiModel),
            keySource,
        ) ?: return null
        // One suggestion is counted as soon as its first request goes out: retries and
        // failed verifications still count, so the cap can never be exceeded. tryIssue
        // makes the check-then-increment atomic: overlapping tap coroutines on IO can
        // otherwise both read below the cap and issue together.
        if (!aiJoinBudget.tryIssue(aiJoinCounter)) return null
        val result = requestSplitVerb(
            provider,
            paragraph,
            SavedMarks.tokenOf(tapped),
            PracticeSchemaModes.initialMode(aiPrefs.aiModel),
        )
        val answer = (result as? AiResult.Ok)?.let { SplitVerbAi.parseResponse(it.text) } ?: return null
        if (!answer.isSplitVerb) return null
        val lemma = answer.lemma ?: return null
        return if (verifyVerbLemma(lemma, offlineAllowed)) {
            SplitVerbs.Match(pair.first, pair.second, lemma)
        } else {
            null
        }
    }

    /**
     * One AI request for the split-verb answer. The first mode comes from
     * [PracticeSchemaModes.initialMode] — the same starting point the practice generation uses,
     * because some models (e.g. glm-5.3-flash) reject strict `json_schema` and would otherwise
     * burn every attempt on a mode they cannot answer. Retried once with `json_object` on the
     * provider's capability code ([PracticeSchemaModes]). Never throws; failures come back as
     * [AiResult.Error].
     */
    private suspend fun requestSplitVerb(
        provider: AiProvider,
        paragraph: String,
        tapped: String,
        initialMode: String,
    ): AiResult {
        val prompt = SplitVerbAi.buildPrompt(paragraph, tapped)
        val strict = provider.complete(
            AiRequest(
                prompt = prompt,
                jsonMode = true,
                jsonSchema = if (initialMode == PracticeSchemaModes.JSON_SCHEMA) SplitVerbAi.schema() else null,
                maxTokens = SplitVerbAi.MAX_TOKENS,
                readTimeoutMs = SplitVerbAi.READ_TIMEOUT_MS,
            )
        )
        val error = strict as? AiResult.Error ?: return strict
        if (PracticeSchemaModes.fallbackMode(initialMode, error) == null) return strict
        return provider.complete(
            AiRequest(
                prompt = prompt,
                jsonMode = true,
                jsonSchema = null,
                maxTokens = SplitVerbAi.MAX_TOKENS,
                readTimeoutMs = SplitVerbAi.READ_TIMEOUT_MS,
            )
        )
    }

    /**
     * The mandatory verification before an AI lemma may be offered (#523 tier 3): a known verb
     * lemma in the bundled dictionary, or a Wiktionary existence check when that dictionary is
     * off. A failed verification shows nothing. Runs on Dispatchers.IO (the caller).
     */
    private suspend fun verifyVerbLemma(lemma: String, offlineAllowed: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (offlineAllowed) {
            SharedOfflineGermanStore.get(applicationContext).isVerbLemma(lemma)
        } else {
            WiktionaryApi.lookup(lemma) is WiktionaryApi.LookupResult.Found
        }
    }

    /**
     * The online fallback for one split pair: the verb's Wiktionary lemma ("nehme" →
     * "nehmen"), composed with the particle ("aufnehmen") and verified with one more
     * lookup. Two look-ups (up to three requests) and a failed verification yields null —
     * nothing is offered or saved without a confirmed word.
     */
    private fun onlineSplitVerb(verb: ScreenWord, particle: ScreenWord): SplitVerbs.Match? {
        val verbResult = WiktionaryApi.lookup(SavedMarks.tokenOf(verb))
        if (verbResult !is WiktionaryApi.LookupResult.Found) return null
        val lemma = verbResult.baseForm ?: verbResult.candidate
        if (lemma.isBlank()) return null
        val composed = (SavedMarks.tokenOf(particle) + lemma).lowercase(Locale.GERMAN)
        if (WiktionaryApi.lookup(composed) !is WiktionaryApi.LookupResult.Found) return null
        return SplitVerbs.Match(verb, particle, composed)
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
        readerMode = false

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
