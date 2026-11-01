package app.olauncher.data.store

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate

data class Word(
    val id: Long,
    val text: String,
    val context: String = "",
    /** [ContextSource.CAPTURED] | [ContextSource.USER] | [ContextSource.AUTO] */
    val contextSource: String = ContextSource.CAPTURED,
    val meaning: String = "",
    val meaningSource: String = "", /* "" | "wiktionary" | "user" */
    val box: Int = 1,
    val dueDay: Long,
    val addedAt: Long,
    val learnedAt: Long? = null,
    val listIds: List<String> = emptyList()
)

data class WordList(
    val id: String,
    val name: String
)

data class WordsData(
    val words: List<Word>,
    val lists: List<WordList>
)

data class AddAllResult(
    val added: List<Word>,
    val alreadySaved: List<Word>
)

/** A planned context rewrite: replace [expectedContext] (as last read) with [newContext]. */
data class ContextReplacement(
    val expectedContext: String,
    val newContext: String
)

data class WordStats(
    val total: Int,
    val due: Int,
    val learned: Int,
    val boxCounts: List<Int> = emptyList()
)

object WordsCodec {

    /** Envelope format written today. v1 files were a bare JSON array of words. */
    const val CURRENT_VERSION = 2

    fun encode(words: List<Word>, lists: List<WordList>): String {
        val envelope = JSONObject()
        envelope.put("version", CURRENT_VERSION)
        envelope.put("lists", listsToJson(lists))
        envelope.put("words", encodeToJson(words))
        return envelope.toString(2)
    }

    fun encodeToJson(words: List<Word>): JSONArray {
        val array = JSONArray()
        for (word in words) {
            val obj = JSONObject()
            obj.put("id", word.id)
            obj.put("text", word.text)
            obj.put("context", word.context)
            obj.put("contextSource", word.contextSource)
            obj.put("meaning", word.meaning)
            obj.put("meaningSource", word.meaningSource)
            obj.put("box", word.box)
            obj.put("dueDay", word.dueDay)
            obj.put("addedAt", word.addedAt)
            if (word.learnedAt != null) {
                obj.put("learnedAt", word.learnedAt)
            } else {
                obj.put("learnedAt", JSONObject.NULL)
            }
            val listIds = JSONArray()
            for (listId in word.listIds) {
                listIds.put(listId)
            }
            obj.put("listIds", listIds)
            array.put(obj)
        }
        return array
    }

    private fun listsToJson(lists: List<WordList>): JSONArray {
        val array = JSONArray()
        for (list in lists) {
            val obj = JSONObject()
            obj.put("id", list.id)
            obj.put("name", list.name)
            array.put(obj)
        }
        return array
    }

    /**
     * Sniffs the top-level shape: a bare array is a legacy v1 words-only file, an object is
     * a versioned envelope. The version is informational only — missing or newer versions
     * still decode, since unknown fields are ignored either way.
     */
    fun decode(raw: String?): WordsData {
        if (raw.isNullOrBlank()) return WordsData(emptyList(), emptyList())
        val trimmed = raw.trim()
        return try {
            when (trimmed.first()) {
                '[' -> WordsData(decode(JSONArray(trimmed)), emptyList())
                '{' -> decodeEnvelope(JSONObject(trimmed))
                else -> WordsData(emptyList(), emptyList())
            }
        } catch (e: Exception) {
            WordsData(emptyList(), emptyList())
        }
    }

    private fun decodeEnvelope(envelope: JSONObject): WordsData {
        val wordsArray = envelope.optJSONArray("words")
        val words = if (wordsArray != null) decode(wordsArray) else emptyList()
        val listsArray = envelope.optJSONArray("lists")
        val lists = if (listsArray != null) decodeLists(listsArray) else emptyList()
        return WordsData(words, lists)
    }

    private fun decodeLists(array: JSONArray): List<WordList> {
        val lists = mutableListOf<WordList>()
        for (i in 0 until array.length()) {
            try {
                val obj = array.getJSONObject(i)
                // Require real strings: getString would coerce numbers on Android.
                val id = obj.opt("id") as? String ?: continue
                val name = obj.opt("name") as? String ?: continue
                if (id.isBlank()) continue
                if (lists.any { it.id == id }) continue // duplicate ids: first wins
                lists.add(WordList(id, name))
            } catch (e: Exception) {
                // Ignore individual corrupt entry
            }
        }
        return lists
    }

    fun decode(array: JSONArray): List<Word> {
        val items = mutableListOf<Word>()
        for (i in 0 until array.length()) {
            try {
                val obj = array.getJSONObject(i)
                val id = obj.getLong("id")
                val text = obj.getString("text")
                val context = if (obj.has("context") && !obj.isNull("context")) obj.getString("context") else ""
                // Additive field: files written before it existed decode every context as
                // captured, so no rewrite of old words.json files is needed.
                val contextSource = when (val raw = obj.opt("contextSource")) {
                    is String -> raw.trim().ifBlank { ContextSource.CAPTURED }
                    else -> ContextSource.CAPTURED
                }
                val meaning = if (obj.has("meaning") && !obj.isNull("meaning")) obj.getString("meaning") else ""
                val meaningSource = if (obj.has("meaningSource") && !obj.isNull("meaningSource")) obj.getString("meaningSource") else ""
                val box = if (obj.has("box") && !obj.isNull("box")) obj.getInt("box") else 1
                // A box outside the interval table (corrupt file, or written by a future
                // version with more boxes) would crash interval lookups; clamp instead.
                val clampedBox = box.coerceIn(1, Leitner.INTERVALS.size)
                val dueDay = obj.getLong("dueDay")
                val addedAt = obj.getLong("addedAt")
                val learnedAt = if (obj.has("learnedAt") && !obj.isNull("learnedAt")) {
                    obj.getLong("learnedAt")
                } else {
                    null
                }
                items.add(
                    Word(
                        id = id,
                        text = text,
                        context = context,
                        contextSource = contextSource,
                        meaning = meaning,
                        meaningSource = meaningSource,
                        box = clampedBox,
                        dueDay = dueDay,
                        addedAt = addedAt,
                        learnedAt = learnedAt,
                        listIds = decodeListIds(obj)
                    )
                )
            } catch (e: Exception) {
                // Ignore individual corrupt entry
            }
        }
        return items
    }

    /** Tolerant per-word listIds read: non-string and blank entries are dropped. */
    private fun decodeListIds(word: JSONObject): List<String> {
        val array = word.optJSONArray("listIds") ?: return emptyList()
        val ids = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val entry = array.opt(i)
            if (entry is String && entry.isNotBlank()) {
                ids.add(entry)
            }
        }
        return ids
    }
}

class WordsStore(
    private val filesDir: File
) {
    constructor(context: Context) : this(context.filesDir)

    private val file = File(filesDir, "words.json")

    internal var epochDaySupplier: () -> Long = { LocalDate.now().toEpochDay() }
    internal var clockSupplier: () -> Long = { System.currentTimeMillis() }

    fun add(text: String, context: String = ""): Word = synchronized(LOCK) {
        val cleanText = text.trim()
        val cleanContext = context.trim()
        val data = loadData()
        val all = data.words.toMutableList()
        val existingIndex = all.indexOfFirst { it.text.equals(cleanText, ignoreCase = true) }
        val word = if (existingIndex != -1) {
            val existing = all[existingIndex]
            if (cleanContext.isNotBlank() && cleanContext != existing.context) {
                // A new captured context supersedes the old one, so the source resets too:
                // what is stored after this is again a fresh capture, not the user's text.
                val updated = existing.copy(context = cleanContext, contextSource = ContextSource.CAPTURED)
                all[existingIndex] = updated
                saveData(data.copy(words = all))
                updated
            } else {
                existing
            }
        } else {
            val now = clockSupplier()
            val today = epochDaySupplier()
            val id = generateId(all, now)
            val newWord = Word(
                id = id,
                text = cleanText,
                context = cleanContext,
                box = 1,
                dueDay = Leitner.newWordDueDay(today),
                addedAt = now
            )
            all.add(0, newWord)
            saveData(data.copy(words = all))
            newWord
        }
        word
    }

    /** Saves each text not already stored (case-insensitive). Existing words are left untouched. */
    fun addAll(texts: List<String>, context: String = ""): AddAllResult = synchronized(LOCK) {
        val cleanContext = context.trim()
        val data = loadData()
        val all = data.words.toMutableList()
        val added = mutableListOf<Word>()
        val alreadySaved = mutableListOf<Word>()
        val now = clockSupplier()
        val today = epochDaySupplier()
        for (raw in texts) {
            val cleanText = raw.trim()
            if (cleanText.isEmpty()) continue
            val existing = all.firstOrNull { it.text.equals(cleanText, ignoreCase = true) }
            if (existing != null) {
                if (added.none { it.id == existing.id } && alreadySaved.none { it.id == existing.id }) {
                    alreadySaved.add(existing)
                }
                continue
            }
            val newWord = Word(
                id = generateId(all, now),
                text = cleanText,
                context = cleanContext,
                box = 1,
                dueDay = Leitner.newWordDueDay(today),
                addedAt = now
            )
            all.add(0, newWord)
            added.add(newWord)
        }
        if (added.isNotEmpty()) saveData(data.copy(words = all))
        AddAllResult(added, alreadySaved)
    }

    fun update(word: Word) = synchronized(LOCK) {
        val data = loadData()
        val all = data.words.toMutableList()
        val index = all.indexOfFirst { it.id == word.id }
        if (index != -1) {
            all[index] = word
            saveData(data.copy(words = all))
        }
    }

    /**
     * Sets each word's context from [contexts] (word id to text) only where it is still blank,
     * in one read and one write, so it never overwrites a context the user captured and never
     * reverts other fields (box, due day, meaning) changed since the caller read the word.
     * Returns the ids that were filled.
     */
    fun fillBlankContexts(contexts: Map<Long, String>): Set<Long> = synchronized(LOCK) {
        if (contexts.isEmpty()) return@synchronized emptySet()
        val data = loadData()
        val filled = mutableSetOf<Long>()
        val updated = data.words.map { word ->
            val text = contexts[word.id]?.trim()
            if (text.isNullOrEmpty() || word.context.isNotBlank()) {
                word
            } else {
                filled += word.id
                word.copy(context = text)
            }
        }
        if (filled.isNotEmpty()) saveData(data.copy(words = updated))
        filled
    }

    /**
     * Applies planned context rewrites ([replacements]: word id to the context expected
     * on disk and the new sentence) in one read and one write. A word is replaced only
     * when all of these still hold:
     *
     * - its current context equals [ContextReplacement.expectedContext], so anything the
     *   user or another flow changed after planning is left alone;
     * - [ContextQuality.isReplaceable] still holds — the user's own context is never
     *   touched, and a context that has become non-weak meanwhile is kept;
     * - the new context is non-blank and itself not weak, so a bad rewrite cannot plant
     *   a new weak context that the next run would just pick again.
     *
     * Every other field (box, due day, meaning, learnedAt, lists) is kept from the
     * freshly loaded word, so a review that moved the word between planning and apply
     * is not reverted. Returns the ids that were replaced.
     */
    fun replaceWeakContexts(replacements: Map<Long, ContextReplacement>): Set<Long> = synchronized(LOCK) {
        if (replacements.isEmpty()) return@synchronized emptySet()
        val data = loadData()
        val replaced = mutableSetOf<Long>()
        val updated = data.words.map { word ->
            val replacement = replacements[word.id] ?: return@map word
            val expected = replacement.expectedContext.trim()
            val newContext = replacement.newContext.trim()
            val replaceable = word.context.trim() == expected &&
                ContextQuality.isReplaceable(word) &&
                newContext.isNotEmpty() &&
                !ContextQuality.isWeak(newContext, word.text)
            if (!replaceable) {
                word
            } else {
                replaced += word.id
                word.copy(context = newContext, contextSource = ContextSource.AUTO)
            }
        }
        if (replaced.isNotEmpty()) saveData(data.copy(words = updated))
        replaced
    }

    fun delete(id: Long) = synchronized(LOCK) {
        val data = loadData()
        val all = data.words.toMutableList()
        val index = all.indexOfFirst { it.id == id }
        if (index != -1) {
            all.removeAt(index)
            saveData(data.copy(words = all))
        }
    }

    @Synchronized
    fun get(id: Long): Word? {
        return loadData().words.firstOrNull { it.id == id }
    }

    @Synchronized
    fun all(): List<Word> {
        return loadData().words
    }

    @Synchronized
    fun due(today: Long, limit: Int = Int.MAX_VALUE): List<Word> {
        return Leitner.due(loadData().words, today, limit)
    }

    @Synchronized
    fun stats(today: Long): WordStats {
        val words = loadData().words
        val dueCount = words.count { it.learnedAt == null && it.dueDay <= today }
        val learnedCount = words.count { it.learnedAt != null }
        return WordStats(
            total = words.size,
            due = dueCount,
            learned = learnedCount,
            boxCounts = Leitner.boxCounts(words)
        )
    }

    @Synchronized
    fun boxCounts(): List<Int> {
        return Leitner.boxCounts(loadData().words)
    }

    @Synchronized
    fun lists(): List<WordList> {
        return loadData().lists.sortedBy { it.name.lowercase() }
    }

    /** Null when the name is blank or already taken (compared ignoring case and punctuation). */
    fun addList(name: String): WordList? = synchronized(LOCK) {
        val data = loadData()
        val normalized = WordListsLogic.normalizeName(name)
        if (WordListsLogic.validateName(normalized, data.lists) != null) return null
        val list = WordList(
            id = WordListsLogic.uniqueListId(normalized, data.lists.mapTo(HashSet()) { it.id }),
            name = normalized
        )
        saveData(data.copy(lists = data.lists + list))
        list
    }

    /** False on unknown id or a name clash; the id never changes. */
    fun renameList(id: String, newName: String): Boolean = synchronized(LOCK) {
        val data = loadData()
        if (data.lists.none { it.id == id }) return false
        val normalized = WordListsLogic.normalizeName(newName)
        if (WordListsLogic.validateName(normalized, data.lists, exceptId = id) != null) return false
        val updated = data.lists.map { if (it.id == id) it.copy(name = normalized) else it }
        saveData(data.copy(lists = updated))
        true
    }

    /** Removes the list and scrubs its id from member words in the same save. */
    fun deleteList(id: String) = synchronized(LOCK) {
        val data = loadData()
        if (data.lists.none { it.id == id }) return
        saveData(
            WordsData(
                words = WordListsLogic.pruneListId(data.words, id),
                lists = data.lists.filterNot { it.id == id }
            )
        )
    }

    /** Replaces a word's memberships; unknown list ids are dropped. No-op when the word is missing. */
    fun setLists(wordId: Long, listIds: List<String>) = synchronized(LOCK) {
        val data = loadData()
        if (data.words.none { it.id == wordId }) return
        val known = data.lists.mapTo(HashSet()) { it.id }
        val clean = listIds.filter { it in known }.distinct()
        val updated = data.words.map { if (it.id == wordId) it.copy(listIds = clean) else it }
        saveData(data.copy(words = updated))
    }

    /** Idempotent bulk membership change; no-op when the list id is unknown. */
    fun assignAll(wordIds: Collection<Long>, listId: String) {
        mutateMembership(wordIds, listId, assign = true)
    }

    /** Idempotent bulk membership change; no-op when the list id is unknown. */
    fun unassignAll(wordIds: Collection<Long>, listId: String) {
        mutateMembership(wordIds, listId, assign = false)
    }

    private fun mutateMembership(wordIds: Collection<Long>, listId: String, assign: Boolean) = synchronized(LOCK) {
        val data = loadData()
        if (data.lists.none { it.id == listId }) return
        val updated = WordListsLogic.withAssignment(data.words, wordIds.toSet(), listId, assign)
        saveData(data.copy(words = updated))
    }

    /** Words carrying [listId], including a dangling id no list defines anymore. */
    @Synchronized
    fun wordsInList(listId: String): List<Word> {
        return loadData().words.filter { listId in it.listIds }
    }

    private fun loadData(): WordsData {
        if (!file.exists()) return WordsData(emptyList(), emptyList())
        return try {
            WordsCodec.decode(file.readText())
        } catch (e: Exception) {
            WordsData(emptyList(), emptyList())
        }
    }

    private fun saveData(data: WordsData) {
        if (!filesDir.exists()) {
            filesDir.mkdirs()
        }
        val tempFile = File(filesDir, "words.json.tmp")
        val json = WordsCodec.encode(data.words, data.lists)
        tempFile.writeText(json)
        if (!tempFile.renameTo(file)) {
            try {
                Files.move(
                    tempFile.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
            } catch (e: Exception) {
                if (file.exists()) file.delete()
                if (!tempFile.renameTo(file)) {
                    tempFile.copyTo(file, overwrite = true)
                    tempFile.delete()
                }
            }
        }
    }

    private fun generateId(items: List<Word>, seed: Long): Long {
        val maxId = items.maxOfOrNull { it.id } ?: 0L
        return maxOf(seed, maxId + 1)
    }

    companion object {
        /**
         * One lock for every instance: the app builds a new WordsStore per call site
         * (WordsCard.getStore, the workers, the dialogs), so an instance-level monitor
         * would not stop a review's load-modify-save from interleaving with a rewrite's
         * and losing the box/dueDay change (same idea as PracticeItemsStore).
         */
        private val LOCK = Any()
    }
}
