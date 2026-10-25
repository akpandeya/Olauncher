package app.olauncher.data.store

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import app.olauncher.R
import org.json.JSONObject
import java.io.File
import java.text.Normalizer

private const val TAG = "OfflineGermanStore"

/** Call on Dispatchers.IO: the first lookup installs the bundled database without network. */
class OfflineGermanStore(context: Context) {
    private val appContext = context.applicationContext

    /** Reused read-only handle: per-keystroke lookups must not re-read metadata or reopen the file. */
    @Volatile
    private var database: SQLiteDatabase? = null

    private val genderCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun deleteInstalled() {
        File(appContext.noBackupFilesDir, "german-dictionary").listFiles()
            ?.filter { it.extension == "sqlite" }
            ?.forEach { it.delete() }
    }

    private fun open(): SQLiteDatabase {
        database?.let { return it }
        synchronized(this) {
            database?.let { return it }
            val metadata = appContext.assets.open("german-v2.sqlite.json").bufferedReader().use {
                JSONObject(it.readText())
            }
            val file = OfflineDictionaryInstaller.install(
                File(appContext.noBackupFilesDir, "german-dictionary"),
                metadata.getString("database_sha256")
            ) { appContext.assets.open("german-v2.sqlite.gzip") }
            // An install that fails to open is corrupt: remove it so the next attempt reinstalls.
            val opened = try {
                SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
            } catch (e: Exception) {
                deleteInstalled()
                throw e
            }
            try {
                check(opened.version in 2..3)
            } catch (e: Exception) {
                opened.close()
                deleteInstalled()
                throw e
            }
            database = opened
            return opened
        }
    }

    /** The installed database is unreadable: drop it so the next attempt reinstalls from assets. */
    private fun discard(opened: SQLiteDatabase) {
        synchronized(this) {
            if (database === opened) database = null
        }
        genderCache.clear()
        try {
            opened.close()
        } catch (_: Exception) {
        }
        deleteInstalled()
    }

    fun lookup(text: String): WiktionaryApi.LookupResult.Found? {
        val pluralLabel = appContext.getString(R.string.offline_dictionary_plural)
        val db = try {
            open()
        } catch (e: Exception) {
            Log.w(TAG, "Offline dictionary unavailable; using existing fallback", e)
            return null
        }
        return try {
            OfflineGermanDictionary { candidate ->
                db.rawQuery(
                    """SELECT e.lemma, e.pos, e.gender, e.plural, e.gloss, e.present, e.past, e.participle
                       FROM lookup l JOIN entries e ON e.id = l.entry_id WHERE l.form = ?
                       ORDER BY CASE WHEN e.lemma = ? THEN 0 ELSE 1 END, e.id LIMIT 2""",
                    arrayOf(candidate, candidate)
                ).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            add(OfflineGermanEntry(
                                cursor.getString(0), cursor.getString(1), cursor.getString(2),
                                cursor.getString(3), cursor.getString(4), cursor.getString(5),
                                cursor.getString(6), cursor.getString(7)
                            ))
                        }
                    }
                }
            }.lookup(text, pluralLabel)
        } catch (e: Exception) {
            Log.w(TAG, "Offline dictionary unreadable; deleting it so the next lookup reinstalls", e)
            discard(db)
            null
        }
    }

    /**
     * Looks up noun gender for [text] (e.g. "der", "die", "das"), or null if unknown (#515).
     * First query hits the database; subsequent calls for the same word are served from cache.
     */
    fun gender(text: String): String? {
        val clean = text.trim()
        if (clean.isEmpty()) return null
        val cached = genderCache[clean]
        if (cached != null) return cached.takeIf { it.isNotEmpty() }
        val db = try {
            open()
        } catch (e: Exception) {
            Log.w(TAG, "Offline dictionary unavailable; using existing fallback", e)
            return null
        }
        return try {
            val normalized = Normalizer.normalize(clean, Normalizer.Form.NFC)
            var result: String? = null
            for (candidate in WiktionaryParser.candidates(normalized)) {
                val found = db.rawQuery(
                    """SELECT e.gender FROM lookup l JOIN entries e ON e.id = l.entry_id WHERE l.form = ?
                       ORDER BY CASE WHEN e.lemma = ? THEN 0 ELSE 1 END, e.id LIMIT 1""",
                    arrayOf(candidate, candidate)
                ).use { cursor ->
                    if (cursor.moveToNext()) {
                        cursor.getString(0)
                    } else null
                }
                if (!found.isNullOrBlank()) {
                    result = found
                    break
                }
            }
            genderCache[clean] = result.orEmpty()
            result
        } catch (e: Exception) {
            Log.w(TAG, "Offline dictionary unreadable; deleting it so the next lookup reinstalls", e)
            discard(db)
            null
        }
    }

    /**
     * The present-tense paradigm the verb entries for [text] merge into, or
     * null when no candidate maps to a verb (or none has present cells), which
     * drives the drill's graceful state. Only verb entries with the same lemma
     * merge (separable/inseparable homographs like übersetzen); distinct lemmas
     * sharing a form are dropped by [ParadigmEntries.sameLemmaAsFirst]. The
     * parser's cell-conflict rule then picks each drilled form; per the
     * homograph-merging note in tools/README the separable split wins. Call on
     * Dispatchers.IO.
     */
    fun paradigm(text: String): VerbParadigm? {
        val all = paradigms(text)
        return all[Tense.PRESENT] ?: all.values.firstOrNull()
    }

    /**
     * Looks up verb paradigms by tense for [text] (lemma or any inflected form).
     * Call on Dispatchers.IO.
     */
    fun paradigms(text: String): Map<Tense, VerbParadigm> {
        val db = try {
            open()
        } catch (e: Exception) {
            Log.w(TAG, "Offline dictionary unavailable; using existing fallback", e)
            return emptyMap()
        }
        return try {
            val normalized = Normalizer.normalize(text.trim(), Normalizer.Form.NFC)
            for (candidate in WiktionaryParser.candidates(normalized)) {
                val hasAuxiliary = db.version >= 3
                val candidates = db.rawQuery(
                    if (hasAuxiliary) {
                        """SELECT e.id, e.lemma, e.past, e.participle, e.auxiliary
                           FROM lookup l JOIN entries e ON e.id = l.entry_id
                           WHERE l.form = ? AND e.pos = 'verb'
                           ORDER BY CASE WHEN e.lemma = ? THEN 0 ELSE 1 END, e.id"""
                    } else {
                        """SELECT e.id, e.lemma, e.past, e.participle, ''
                           FROM lookup l JOIN entries e ON e.id = l.entry_id
                           WHERE l.form = ? AND e.pos = 'verb'
                           ORDER BY CASE WHEN e.lemma = ? THEN 0 ELSE 1 END, e.id"""
                    },
                    arrayOf(candidate, candidate)
                ).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            add(VerbEntryRef(cursor.getLong(0), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4)))
                        }
                    }
                }
                val entries = ParadigmEntries.sameLemmaAsFirst(candidates)
                if (entries.isEmpty()) continue
                val rows = db.rawQuery(
                    "SELECT form, tags FROM forms WHERE entry_id IN (${entries.joinToString(",") { "?" }})",
                    entries.map { it.id.toString() }.toTypedArray()
                ).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            add(cursor.getString(0) to cursor.getString(1))
                        }
                    }
                }
                val past = entries.map { it.past }.firstOrNull { it.isNotBlank() }.orEmpty()
                val participle = entries.map { it.participle }.firstOrNull { it.isNotBlank() }.orEmpty()
                val auxiliary = entries.map { it.auxiliary }.firstOrNull { it.isNotBlank() }.orEmpty()
                val parsed = Conjugation.parseAll(entries.first().lemma, rows, past, participle, auxiliary)
                if (parsed.isNotEmpty()) return parsed
            }
            emptyMap()
        } catch (e: Exception) {
            Log.w(TAG, "Offline dictionary unreadable; deleting it so the next lookup reinstalls", e)
            discard(db)
            emptyMap()
        }
    }
}
