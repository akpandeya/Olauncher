package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerDrillsTest {

    // Distinguishing values so a swapped or missing wiring cannot pass.
    private val suggestedText = "Vorschlag: Kasusendungen · Start 12"
    private val allText = "Alle Übungen…"

    @Test
    fun matchesTypoTolerantDrillQueries() {
        assertTrue(DrawerDrills.matches("drill"))
        assertTrue(DrawerDrills.matches("drills"))
        assertTrue(DrawerDrills.matches("  Drills  "))
        assertTrue(DrawerDrills.matches("dril"))
        assertTrue(DrawerDrills.matches("drils"))
    }

    @Test
    fun shortAndUnrelatedQueriesDoNotMatch() {
        assertFalse(DrawerDrills.matches("d"))
        assertFalse(DrawerDrills.matches("dri"))
        assertFalse(DrawerDrills.matches("verb"))
        assertFalse(DrawerDrills.matches(""))
        assertFalse(DrawerDrills.matches("     "))
    }

    @Test
    fun drillQueryShowsSuggestedAndAllRows() {
        val rows = DrawerDrills.rows(
            query = "drill",
            germanEnabled = true,
            suggestedTypeId = "case_endings",
            suggestedRowText = suggestedText,
            allDrillsText = allText,
        )
        assertEquals(
            listOf(
                DrawerDrillRow(suggestedText, "case_endings"),
                DrawerDrillRow(allText, null),
            ),
            rows,
        )
    }

    @Test
    fun nonDrillQueryShowsNoRows() {
        val rows = DrawerDrills.rows(
            query = "telegram",
            germanEnabled = true,
            suggestedTypeId = "case_endings",
            suggestedRowText = suggestedText,
            allDrillsText = allText,
        )
        assertEquals(emptyList<DrawerDrillRow>(), rows)
    }

    @Test
    fun germanOffShowsNoRows() {
        val rows = DrawerDrills.rows(
            query = "drill",
            germanEnabled = false,
            suggestedTypeId = "case_endings",
            suggestedRowText = suggestedText,
            allDrillsText = allText,
        )
        assertEquals(emptyList<DrawerDrillRow>(), rows)
    }

    @Test
    fun noSuggestedTypeShowsNoRows() {
        val rows = DrawerDrills.rows(
            query = "drill",
            germanEnabled = true,
            suggestedTypeId = null,
            suggestedRowText = suggestedText,
            allDrillsText = allText,
        )
        assertEquals(emptyList<DrawerDrillRow>(), rows)
    }
}
