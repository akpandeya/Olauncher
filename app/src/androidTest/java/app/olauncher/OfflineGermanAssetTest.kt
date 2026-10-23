package app.olauncher

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.util.zip.GZIPInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses platform APIs only: the release-like debug build may inline/strip app implementation classes. */
@RunWith(AndroidJUnit4::class)
class OfflineGermanAssetTest {
    @Test
    fun bundledDictionaryWorksWithoutAnyOnlineFallback() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.filesDir, "dictionary-test-${UUID.randomUUID()}.sqlite")
        try {
            instrumentation.targetContext.assets.open("german-v1.sqlite.gzip").use { asset ->
                GZIPInputStream(asset).use { input -> file.outputStream().use { input.copyTo(it) } }
            }
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                fun lookup(form: String): LookupRow {
                    db.rawQuery(
                        """SELECT e.lemma, e.gender, e.plural, e.present, e.past, e.participle
                           FROM lookup l JOIN entries e ON e.id=l.entry_id
                           WHERE l.form=? ORDER BY e.id LIMIT 1""",
                        arrayOf(form)
                    ).use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        return LookupRow(
                            cursor.getString(0), cursor.getString(1), cursor.getString(2),
                            cursor.getString(3), cursor.getString(4), cursor.getString(5)
                        )
                    }
                }
                val gehen = lookup("gehen")
                assertEquals("gehen", gehen.lemma)
                assertEquals("geht", gehen.present)
                assertEquals("ging", gehen.past)
                assertEquals("gegangen", gehen.participle)
                assertEquals(gehen, lookup("ging"))
                assertEquals("das", lookup("Mädchen").gender)
                assertEquals("Häuser", lookup("Haus").plural)
            }
        } finally {
            file.delete()
        }
    }

    private data class LookupRow(
        val lemma: String,
        val gender: String,
        val plural: String,
        val present: String,
        val past: String,
        val participle: String
    )
}
