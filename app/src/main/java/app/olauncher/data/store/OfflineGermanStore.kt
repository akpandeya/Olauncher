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
                check(opened.version == 2)
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
     * The present-tense paradigm of the first verb entry [text] resolves to,
     * or null when no candidate maps to a verb (or that verb has no present
     * cells), which drives the drill's graceful state. Call on Dispatchers.IO.
     */
    fun paradigm(text: String): VerbParadigm? {
        val db = try {
            open()
        } catch (e: Exception) {
            Log.w(TAG, "Offline dictionary unavailable; using existing fallback", e)
            return null
        }
        return try {
            val normalized = Normalizer.normalize(text.trim(), Normalizer.Form.NFC)
            for (candidate in WiktionaryParser.candidates(normalized)) {
                val entry = db.rawQuery(
                    """SELECT e.id, e.lemma
                       FROM lookup l JOIN entries e ON e.id = l.entry_id
                       WHERE l.form = ? AND e.pos = 'verb'
                       ORDER BY CASE WHEN e.lemma = ? THEN 0 ELSE 1 END, e.id LIMIT 1""",
                    arrayOf(candidate, candidate)
                ).use { cursor ->
                    if (cursor.moveToNext()) cursor.getLong(0) to cursor.getString(1) else null
                } ?: continue
                val rows = db.rawQuery(
                    "SELECT form, tags FROM forms WHERE entry_id = ?",
                    arrayOf(entry.first.toString())
                ).use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            add(cursor.getString(0) to cursor.getString(1))
                        }
                    }
                }
                return Conjugation.parsePresent(entry.second, rows)
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Offline dictionary unreadable; deleting it so the next lookup reinstalls", e)
            discard(db)
            null
        }
    }
}
