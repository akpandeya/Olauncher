package app.olauncher.ui

import android.util.Log
import android.content.ActivityNotFoundException
import android.Manifest
import android.content.Context
import android.text.format.DateFormat
import app.olauncher.glance.CalendarRepository
import app.olauncher.glance.CalendarSearch
import app.olauncher.glance.NextUpLabels
import java.time.ZoneId
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.text.Spannable
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.olauncher.R
import app.olauncher.data.AppModel
import app.olauncher.data.SearchEngine
import app.olauncher.helper.CurrencyConvert
import app.olauncher.helper.RatesCache
import app.olauncher.data.SearchTarget
import app.olauncher.helper.AndroidContactSource
import app.olauncher.helper.ContactSource
import app.olauncher.helper.EnterPolicy
import app.olauncher.helper.openSearch
import app.olauncher.helper.openUrl
import app.olauncher.helper.DrawerCommand
import app.olauncher.helper.DictionaryWebsite
import app.olauncher.helper.createChoiceDialog
import app.olauncher.data.store.WiktionaryApi
import app.olauncher.data.store.WiktionaryCache
import app.olauncher.data.store.WiktionarySuggestCache
import app.olauncher.data.store.GermanLookup
import app.olauncher.data.store.OfflineGermanStore
import app.olauncher.helper.WordSuggest
import app.olauncher.helper.WordLookupRows
import app.olauncher.helper.DictionaryApps
import app.olauncher.helper.DrawerDrills
import app.olauncher.data.store.DrillScores
import app.olauncher.data.store.DrillTables
import app.olauncher.ui.today.cards.DrillsCardLogic
import app.olauncher.data.store.WordLookup
import app.olauncher.data.store.WordsStore
import kotlinx.coroutines.delay
import app.olauncher.helper.showToast

private const val TAG = "AppDrawerFragment"

internal fun AppDrawerFragment.initSearch() {
    binding.search.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
        override fun onQueryTextSubmit(query: String?): Boolean {
            fun openSearchTarget() = openWebSearch(query.orEmpty())

            if (query.isNullOrBlank() && adapter.itemCount > 0) {
                // Blank query: keep existing behaviour (open first app in list).
                adapter.launchFirstInList()
                return true
            }

            scheduleLookup(query.orEmpty(), LOOKUP_DEBOUNCE_ON_ENTER_MS)
            val topItem = adapter.getTopItem()
            val topScore = adapter.getTopScore()
            when (val action = EnterPolicy.resolve(query, topItem, topScore, adapter.getTopApp())) {
                is EnterPolicy.Action.ExecuteResult -> {
                    adapter.executeItem(action.result)
                }
                is EnterPolicy.Action.LaunchApp -> {
                    adapter.executeItem(action.app)
                }
                is EnterPolicy.Action.WebSearch -> {
                    openSearchTarget()
                }
            }
            return true
        }

        override fun onQueryTextChange(newText: String): Boolean {
            try {
                val lookUpText = (parseCommand(newText) as? DrawerCommand.LookUp)?.text
                if (lookUpText == null || !lookUpText.equals(lookupState?.first, ignoreCase = true)) {
                    lookupJob?.cancel()
                    lookupState = null
                    paradigmState = null
                }
                scheduleLookup(newText, LOOKUP_DEBOUNCE_MS)
                scheduleSuggest(newText, LOOKUP_DEBOUNCE_MS)
                adapter.allowAutoLaunch = !isSearchComposing()
                adapter.filter.filter(newText)
                binding.appRename.visibility =
                    if (canRename && newText.isNotBlank()) View.VISIBLE else View.GONE
                updateLetterStripVisibility(newText)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "onQueryTextChange failed", e)
            }
            return false
        }
    })
}

internal fun AppDrawerFragment.parseCommand(query: String): DrawerCommand? =
    DrawerCommand.parse(query, prefs.germanEnabled)

internal fun AppDrawerFragment.lookupAllowed(): Boolean =
    WordLookup.isLookupAllowed(prefs.germanEnabled, prefs.germanLookup)

internal fun AppDrawerFragment.savedWord(ctx: Context, text: String) =
    WordsStore(ctx).all().firstOrNull { it.text.equals(text.trim(), ignoreCase = true) }

/** Looks offline first; only a miss may use the opt-in Wiktionary network/cache fallback. */
internal fun AppDrawerFragment.scheduleLookup(query: String, delayMs: Long) {
    val text = (parseCommand(query) as? DrawerCommand.LookUp)?.text ?: return
    if (text.equals(lookupState?.first, ignoreCase = true)) return
    val ctx = context?.applicationContext ?: return
    lookupJob?.cancel()
    lookupJob = viewLifecycleOwner.lifecycleScope.launch {
        if (delayMs > 0) delay(delayMs)
        lookupState = text to null
        paradigmState = null
        val offlineOn = prefs.germanOfflineDictionary
        val (result, paradigm) = withContext(Dispatchers.IO) {
            val store = OfflineGermanStore(ctx)
            // Cheap indexed query next to the meaning fetch; it must also run for
            // saved words, whose meaning fetch below returns without looking up.
            val paradigm = if (offlineOn) store.paradigm(text) else null
            val saved = savedWord(ctx, text)
            val cache = WiktionaryCache(ctx)
            val result = when {
                // A stored meaning is already on screen; nothing to fetch.
                saved != null && saved.meaning.isNotBlank() -> null
                // Saved words never touch the network; only the offline dictionary may fill a blank meaning.
                saved != null -> GermanLookup.lookup(
                    text, false, { if (offlineOn) store.lookup(it) else null },
                    cache::get, { WiktionaryApi.lookup(it) }, cache::put
                )
                else -> GermanLookup.lookup(
                    text, lookupAllowed(), { if (offlineOn) store.lookup(it) else null },
                    cache::get, { WiktionaryApi.lookup(it) }, cache::put
                )
            }
            result to paradigm
        }
        paradigmState = paradigm?.let { text to it }
        if (!isAdded || _binding == null) return@launch
        if (result == null) {
            // The stored meaning needed no fetch, but the drill row may still be new.
            if (paradigm != null) adapter.filter.filter(binding.search.query)
            return@launch
        }
        lookupState = text to result
        scheduleSuggest(query, 0)
        adapter.filter.filter(binding.search.query)
    }
}

internal fun AppDrawerFragment.refreshSavedWords() {
    if (!prefs.germanEnabled) return
    val ctx = context?.applicationContext ?: return
    viewLifecycleOwner.lifecycleScope.launch {
        savedWordTexts = withContext(Dispatchers.IO) { WordsStore(ctx).all().map { it.text } }
    }
}

internal fun AppDrawerFragment.suggestItems(query: String): List<String> =
    when (val command = parseCommand(query)) {
        is DrawerCommand.SaveWords -> command.items
        is DrawerCommand.LookUp -> listOf(command.text)
        is DrawerCommand.Timer, is DrawerCommand.Alarm, is DrawerCommand.Event,
        is DrawerCommand.Note, is DrawerCommand.ShopItems, is DrawerCommand.Profile, is DrawerCommand.Help, null -> emptyList()
    }

internal fun AppDrawerFragment.suggestOnlineAllowed(): Boolean = prefs.germanEnabled && prefs.germanSpellSuggest

/**
 * Drill rows for the drawer search: typing "drill" offers the suggested round and the
 * hub; nothing shows when German is off or the query is not a drill query.
 */
internal fun AppDrawerFragment.drillRows(query: String): List<AppModel.Result> {
    if (!prefs.germanEnabled || !DrawerDrills.matches(query)) return emptyList()
    val ctx = context ?: return emptyList()
    val suggested = DrillsCardLogic.suggestedType(
        DrillTables.available(),
        DrillScores.decode(prefs.drillScores),
        prefs.drillLastType,
    ) ?: return emptyList()
    val suggestedText = ctx.getString(
        R.string.drills_card_suggested,
        ctx.getString(DrillTables.labelRes(suggested)),
        DrillTables.ROUND_SIZE,
    )
    return DrawerDrills.rows(
        query = query,
        germanEnabled = prefs.germanEnabled,
        suggestedTypeId = suggested.id,
        suggestedRowText = suggestedText,
        allDrillsText = ctx.getString(R.string.drills_drawer_all),
    ).map { row ->
        if (row.typeId == null) AppModel.Result(row.text, AppModel.Result.Action.OpenDrills)
        else AppModel.Result(row.text, AppModel.Result.Action.StartDrill(row.typeId))
    }
}

/** Cancels any pending online suggestion and, when allowed, starts one for the unmatched `:sw` items. */
internal fun AppDrawerFragment.scheduleSuggest(query: String, delayMs: Long) {
    // A :w hit needs no spelling request; wait for its offline-first lookup to finish.
    if (parseCommand(query) is DrawerCommand.LookUp) {
        val result = lookupState?.second
        if (result == null || result is WiktionaryApi.LookupResult.Found) {
            suggestJob?.cancel()
            suggestState = null
            return
        }
    }
    val items = suggestItems(query)
    if (items == suggestState?.first) return
    suggestJob?.cancel()
    suggestState = null
    if (!suggestOnlineAllowed() || items.isEmpty()) return
    val unmatched = items.filter { !WordSuggest.hasExact(savedWordTexts, it) }
        .take(SUGGEST_MAX_ONLINE_ITEMS)
    if (unmatched.isEmpty()) return
    suggestJob = viewLifecycleOwner.lifecycleScope.launch {
        delay(delayMs)
        val found = withContext(Dispatchers.IO) {
            val map = LinkedHashMap<String, List<String>>()
            for (item in unmatched) {
                val words = WiktionarySuggestCache.get(item) ?: when (val r = WiktionaryApi.suggest(item)) {
                    is WiktionaryApi.SuggestResult.Suggestions -> r.words.also { WiktionarySuggestCache.put(item, it) }
                    WiktionaryApi.SuggestResult.Failed -> emptyList()
                }
                map[item] = words
            }
            map
        }
        if (!isAdded || _binding == null) return@launch
        // Never show an old answer: the text must still produce the same items.
        if (suggestItems(binding.search.query.toString()) != items) return@launch
        suggestState = items to found
        adapter.filter.filter(binding.search.query)
    }
}

/** "Did you mean" rows per unmatched item: offline from saved words first, then online ones. */
internal fun AppDrawerFragment.spellRows(items: List<String>): List<AppModel.Result> {
    val ctx = context ?: return emptyList()
    val offline = WordSuggest.suggestForItems(savedWordTexts, items)
    val online = suggestState?.takeIf { it.first == items && suggestOnlineAllowed() }?.second.orEmpty()
    val rows = ArrayList<AppModel.Result>()
    for ((item, local) in offline) {
        for (option in WordSuggest.mergeOptions(item, local, online[item].orEmpty())) {
            val text = if (items.size == 1) ctx.getString(R.string.did_you_mean_row, option)
            else ctx.getString(R.string.did_you_mean_for_row, option, item)
            rows.add(AppModel.Result(text, AppModel.Result.Action.SaveWords(listOf(option))))
        }
    }
    return rows
}

/** "Did you mean" rows for a `:w` query without an exact saved match; tapping one looks that spelling up. */
internal fun AppDrawerFragment.lookUpSuggestionRows(ctx: Context, text: String): List<AppModel.Result> {
    val local = WordSuggest.suggest(savedWordTexts, text)
    val online = suggestState?.takeIf { it.first == listOf(text) && suggestOnlineAllowed() }
        ?.second?.get(text).orEmpty()
    return WordSuggest.mergeOptions(text, local, online).map { option ->
        AppModel.Result(
            ctx.getString(R.string.did_you_mean_row, option),
            AppModel.Result.Action.LookUpWord(option)
        )
    }
}

internal fun AppDrawerFragment.lookUpRows(text: String): List<AppModel.Result> {
    val ctx = context ?: return emptyList()
    val main = lookUpMainRows(ctx, text)
    // The drill row sits before the dictionary row, which lookUpMainRows always trails with.
    val rows = conjugationRow(ctx, text)?.let { main.dropLast(1) + it + main.last() } ?: main
    if (savedWord(ctx, text) != null) return rows
    return rows + lookUpSuggestionRows(ctx, text)
}

/** Drill row for the current lookup; null when the offline store has no verb paradigm for it. */
private fun AppDrawerFragment.conjugationRow(ctx: Context, text: String): AppModel.Result? {
    val lemma = paradigmState?.takeIf { it.first.equals(text, ignoreCase = true) }?.second?.lemma
        ?: return null
    return AppModel.Result(
        text = ctx.getString(R.string.word_row_conjugation, lemma),
        action = AppModel.Result.Action.ConjugateWord(lemma),
    )
}

internal fun AppDrawerFragment.lookUpMainRows(ctx: Context, text: String): List<AppModel.Result> {
    val state = lookupState?.takeIf { it.first.equals(text, ignoreCase = true) }?.second
    val saved = savedWord(ctx, text)
    if (saved != null) {
        // Saved words always offer the dictionary row; a blank stored meaning is filled
        // from the offline dictionary when it has the word (never from the network).
        val meaning = WordLookupRows.savedMeaning(
            saved.meaning, state, ctx.getString(R.string.word_row_no_meaning_yet)
        )
        return listOf(
            WordLookupRows.saved(saved.text, meaning) { word, definition ->
                ctx.getString(R.string.word_row_meaning, word, definition)
            },
            dictionaryRow(ctx, saved.text),
        )
    }
    if (!lookupAllowed()) {
        // Online lookup is off, but an offline hit is still worth showing.
        val offline = WordLookupRows.offlineOnlyMeaning(state)
            ?: return listOf(dictionaryRow(ctx, text))
        return listOf(
            WordLookupRows.found(text, offline, (state as? WiktionaryApi.LookupResult.Found)?.baseForm) { word, definition ->
                ctx.getString(R.string.word_row_wiktionary_meaning, word, definition)
            },
            dictionaryRow(ctx, text),
        )
    }
    val silent = AppModel.Result.Action.OpenDictionary(text, openOnTap = false)
    val row = when (val result = state) {
        null -> AppModel.Result(ctx.getString(R.string.word_row_looking_up), silent)
        is WiktionaryApi.LookupResult.Found -> WordLookupRows.found(text, result.meaning, result.baseForm) { word, definition ->
            ctx.getString(R.string.word_row_wiktionary_meaning, word, definition)
        }
        WiktionaryApi.LookupResult.NotFound ->
            AppModel.Result(ctx.getString(R.string.word_row_no_meaning_found), silent)
        WiktionaryApi.LookupResult.Failed ->
            AppModel.Result(ctx.getString(R.string.word_row_unreachable), silent)
    }
    return listOf(row, dictionaryRow(ctx, text))
}

internal fun AppDrawerFragment.openWiktionary(text: String) {
    openDictionaryWebsite(DictionaryWebsite.WIKTIONARY, text)
}

internal fun AppDrawerFragment.showDictionaryWebsites(text: String) {
    val ctx = context ?: return
    val choices = listOf(
        DictionaryWebsite.VERBFORMEN to R.string.dictionary_website_verbformen,
        DictionaryWebsite.DWDS to R.string.dictionary_website_dwds,
        DictionaryWebsite.DUDEN to R.string.dictionary_website_duden,
        DictionaryWebsite.WIKTIONARY to R.string.dictionary_website_wiktionary,
    )
    ctx.createChoiceDialog(
        titleText = text,
        items = choices.map { ctx.getString(it.second) },
    ) { index ->
        openDictionaryWebsite(choices[index].first, text)
    }.show()
}

private fun AppDrawerFragment.openDictionaryWebsite(website: DictionaryWebsite, text: String) {
    try {
        requireContext().openUrl(website.buildUrl(text))
    } catch (e: Exception) {
        Log.w(TAG, "openDictionaryWebsite failed", e)
    }
}

private fun AppDrawerFragment.dictionaryRow(ctx: Context, text: String) = AppModel.Result(
    text = if (prefs.germanDictionaryApp.isBlank()) ctx.getString(R.string.word_row_open_wiktionary, text)
        else DictionaryApps.actionLabel(ctx, prefs.germanDictionaryApp),
    action = AppModel.Result.Action.OpenDictionary(text),
)

internal fun AppDrawerFragment.openDictionary(text: String) {
    DictionaryApps.open(requireContext(), text)
}

internal fun AppDrawerFragment.saveWords(action: AppModel.Result.Action.SaveWords) {
    val ctx = context?.applicationContext ?: return
    viewLifecycleOwner.lifecycleScope.launch {
        val result = withContext(Dispatchers.IO) {
            val store = WordsStore(ctx)
            val res = store.addAll(action.items, action.context)
            if (action.meaning.isNotBlank() && action.items.size == 1) {
                res.added.firstOrNull()?.let {
                    store.update(it.copy(meaning = action.meaning, meaningSource = "wiktionary"))
                }
            }
            res.added.forEach { WordLookup.enqueue(ctx, it.id) }
            res
        }
        val base = when {
            result.added.size == 1 -> ctx.getString(R.string.word_saved_de, result.added[0].text)
            result.added.isEmpty() && result.alreadySaved.size == 1 ->
                ctx.getString(R.string.word_saved_de, result.alreadySaved[0].text)
            else -> ctx.getString(R.string.words_saved_de, result.added.size)
        }
        val suffix = if (result.added.isNotEmpty() && result.alreadySaved.isNotEmpty()) {
            ctx.getString(R.string.words_already_saved_suffix_de, result.alreadySaved.size)
        } else ""
        ctx.showToast(base + suffix)
        savedWordTexts = withContext(Dispatchers.IO) { WordsStore(ctx).all().map { it.text } }
        if (_binding != null) binding.search.setQuery("", false)
    }
}

internal fun AppDrawerFragment.isSearchComposing(): Boolean {
    val text = searchTextView?.text
    if (text !is Spannable) return false
    val start = BaseInputConnection.getComposingSpanStart(text)
    val end = BaseInputConnection.getComposingSpanEnd(text)
    if (start !in 0 until end) return false
    return isCjkKeyboard()
}

internal fun AppDrawerFragment.isCjkKeyboard(): Boolean {
    cachedIsCjkKeyboard?.let { return it }
    val result = try {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val subtype = imm.currentInputMethodSubtype
        val language = when {
            subtype == null -> ""
            subtype.languageTag.isNotEmpty() -> subtype.languageTag // e.g. "zh-CN", "ja-JP", "en-US"
            else -> subtype.locale // deprecated fallback, e.g. "zh_CN"
        }
        language.startsWith("zh") || language.startsWith("ja") || language.startsWith("ko")
    } catch (e: Exception) {
        Log.w("AppDrawerFragment", "Failed to detect CJK keyboard", e)
        false
    }
    cachedIsCjkKeyboard = result
    return result
}

internal fun AppDrawerFragment.contactSourceOrNull(): ContactSource? {
    val ctx = context ?: return null
    if (!prefs.searchContacts) return null
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
        prefs.searchContacts = false
        return null
    }
    return AndroidContactSource(ctx)
}

internal fun AppDrawerFragment.currencyRows(query: String): List<AppModel.Result> {
    val ctx = context ?: return emptyList()
    val q = CurrencyConvert.parse(query) ?: return emptyList()
    val rows = mutableListOf<AppModel.Result>()
    if (prefs.currencyRates) {
        val rates = RatesCache(ctx).read()
        val money = CurrencyConvert.convert(q, rates)
        if (money != null && rates != null) {
            rows.add(
                AppModel.Result(
                    text = "= ${money.formatted} ${money.code} · ${CurrencyConvert.sourceLabel(rates.date)}",
                    action = AppModel.Result.Action.Copy(money.formatted)
                )
            )
        }
    }
    val engine = ctx.getString(
        when (prefs.searchEngine) {
            SearchEngine.GOOGLE -> R.string.search_engine_google
            SearchEngine.DUCKDUCKGO -> R.string.search_engine_duckduckgo
            SearchEngine.BING -> R.string.search_engine_bing
            SearchEngine.CUSTOM -> R.string.search_engine_custom
            else -> R.string.search_engine_phone_default
        }
    )
    rows.add(
        AppModel.Result(
            text = ctx.getString(R.string.currency_web_row, q.amountText, q.from, q.to, engine),
            action = AppModel.Result.Action.Web(query.trim())
        )
    )
    return rows
}

internal fun AppDrawerFragment.openWebSearch(query: String) {
    when (val target = SearchEngine.resolve(prefs.searchEngine, prefs.searchEngineCustomUrl, query)) {
        is SearchTarget.PhoneDefault -> {
            try {
                requireContext().openSearch(target.query)
            } catch (e: Exception) {
                Log.w(TAG, "openWebSearch failed", e)
            }
        }
        is SearchTarget.Url -> {
            try {
                requireContext().openUrl(target.url)
            } catch (e: ActivityNotFoundException) {
                try {
                    requireContext().openSearch(query)
                } catch (e2: Exception) {
                    Log.w(TAG, "openWebSearch failed", e2)
                }
            } catch (e: Exception) {
                Log.w(TAG, "openWebSearch failed", e)
            }
        }
    }
}

internal fun AppDrawerFragment.calendarRows(query: String): List<AppModel.Result> {
    val ctx = context ?: return emptyList()
    if (query.trim().length < CalendarSearch.MIN_QUERY_LENGTH) return emptyList()
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
        return emptyList()
    }
    val now = System.currentTimeMillis()
    val zone = ZoneId.systemDefault()
    val is24Hour = DateFormat.is24HourFormat(ctx)
    val labels = NextUpLabels(
        today = ctx.getString(R.string.today_section),
        tomorrow = ctx.getString(R.string.next_up_tomorrow),
        nowTemplate = ctx.getString(R.string.next_up_now),
        tomorrowEventTemplate = ctx.getString(R.string.next_up_tomorrow_event),
        allDay = ctx.getString(R.string.next_up_all_day),
    )
    val to = now + CalendarSearch.WINDOW_DAYS * 24 * 3600 * 1000L
    return CalendarRepository(ctx).search(query, now, to)
        .map { CalendarSearch.toResult(it, now, zone, is24Hour, labels) }
}

internal fun AppDrawerFragment.dialContact(lookupUri: String) {
    val source = contactSourceOrNull()
    viewLifecycleOwner.lifecycleScope.launch {
        // Provider lookup stays off the main thread.
        val number = withContext(Dispatchers.IO) {
            try { source?.primaryNumber(lookupUri) } catch (_: Exception) { null }
        }
        if (!isAdded) return@launch
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                if (!number.isNullOrBlank()) data = Uri.fromParts("tel", number, null)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "dialContact failed", e)
        }
        findNavController().popBackStack(R.id.mainFragment, false)
    }
}
