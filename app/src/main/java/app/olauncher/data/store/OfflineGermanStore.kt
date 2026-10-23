package app.olauncher.data.store

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import app.olauncher.R
import org.json.JSONObject
import java.io.File

/** Call on Dispatchers.IO: the first lookup installs the bundled database without network. */
class OfflineGermanStore(context: Context) {
    private val appContext = context.applicationContext

    fun lookup(text: String): WiktionaryApi.LookupResult.Found? = try {
        val metadata = appContext.assets.open("german-v1.sqlite.json").bufferedReader().use {
            JSONObject(it.readText())
        }
        val file = OfflineDictionaryInstaller.install(
            File(appContext.noBackupFilesDir, "german-dictionary"),
            metadata.getString("database_sha256")
        ) { appContext.assets.open("german-v1.sqlite.gzip") }
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            check(db.version == 1)
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
            }.lookup(text, appContext.getString(R.string.offline_dictionary_plural))
        }
    } catch (e: Exception) {
        Log.w("OfflineGermanStore", "Offline dictionary unavailable; using existing fallback", e)
        null
    }
}
