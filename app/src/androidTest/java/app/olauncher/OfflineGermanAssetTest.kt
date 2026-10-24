package app.olauncher

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.util.zip.GZIPInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Uses platform APIs only: the release-like debug build may inline/strip app implementation classes. */
@RunWith(AndroidJUnit4::class)
class OfflineGermanAssetTest {
    @Test
    fun bundledDictionaryWorksWithoutAnyOnlineFallback() = withInstalledDatabase { db ->
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

    @Test
    fun formsTableStoresSingleWordParadigmCells() = withInstalledDatabase { db ->
        assertEquals(3, db.version)
        db.rawQuery(
            "SELECT sql FROM sqlite_master WHERE type='table' AND name='forms'", null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            val schema = cursor.getString(0)
            assertTrue(schema.contains("entry_id"))
            assertTrue(schema.contains("form"))
            assertTrue(schema.contains("tags"))
        }
        // Periphrastic tenses are compositional and excluded: no row is a multiword phrase.
        db.rawQuery("SELECT count(*) FROM forms WHERE tags LIKE '%multiword-construction%'", null)
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getLong(0))
            }
    }

    @Test
    fun machenYieldsSixDistinctPresentPersonNumberCells() = withInstalledDatabase { db ->
        val cells = mutableSetOf<Pair<String, String>>()
        db.rawQuery(
            """SELECT f.form, f.tags FROM forms f JOIN entries e ON e.id=f.entry_id
               WHERE e.lemma='machen' AND e.pos='verb' AND f.tags LIKE '%indicative%present%'""",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                cells.add(cursor.getString(0) to cursor.getString(1))
            }
        }
        assertEquals(
            setOf(
                "mache" to "first-person indicative present singular",
                "machst" to "indicative present second-person singular",
                "macht" to "indicative present singular third-person",
                "machen" to "first-person indicative plural present",
                "macht" to "indicative plural present second-person",
                "machen" to "indicative plural present third-person",
            ),
            cells
        )
    }

    @Test
    fun seinBinIsTaggedFirstPersonSingularPresent() = withInstalledDatabase { db ->
        db.rawQuery(
            """SELECT f.tags FROM forms f JOIN entries e ON e.id=f.entry_id
               WHERE e.lemma='sein' AND e.pos='verb' AND f.form='bin'""",
            null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("first-person indicative present singular", cursor.getString(0))
            assertFalse(cursor.moveToNext())
        }
    }

    private fun withInstalledDatabase(block: (SQLiteDatabase) -> Unit) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.filesDir, "dictionary-test-${UUID.randomUUID()}.sqlite")
        try {
            instrumentation.targetContext.assets.open("german-v2.sqlite.gzip").use { asset ->
                GZIPInputStream(asset).use { input -> file.outputStream().use { input.copyTo(it) } }
            }
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use(block)
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
