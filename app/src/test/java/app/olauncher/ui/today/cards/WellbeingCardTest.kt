package app.olauncher.ui.today.cards

import app.olauncher.R
import app.olauncher.ui.today.TodayCards
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WellbeingCardTest {

    @Test
    fun `wellbeing card is registered in TodayCards all`() {
        val card = TodayCards.all.firstOrNull { it.key == WellbeingCard.KEY }
        assertNotNull(card)
        assertEquals("wellbeing", card?.key)
        assertEquals(R.string.wellbeing, card?.titleRes)
    }

    @Test
    fun `isAvailable returns true when water nudge is on`() {
        assertTrue(
            WellbeingCardLogic.isAvailable(
                waterIntervalMin = 60,
                walkIntervalMin = 0,
                socialThresholdMin = 0,
                distractingApps = emptySet(),
            )
        )
    }

    @Test
    fun `isAvailable returns true when walk nudge is on`() {
        assertTrue(
            WellbeingCardLogic.isAvailable(
                waterIntervalMin = 0,
                walkIntervalMin = 120,
                socialThresholdMin = 0,
                distractingApps = emptySet(),
            )
        )
    }

    @Test
    fun `isAvailable returns true when social nudge is on`() {
        assertTrue(
            WellbeingCardLogic.isAvailable(
                waterIntervalMin = 0,
                walkIntervalMin = 0,
                socialThresholdMin = 30,
                distractingApps = emptySet(),
            )
        )
    }

    @Test
    fun `isAvailable returns true when distracting apps is non-empty`() {
        assertTrue(
            WellbeingCardLogic.isAvailable(
                waterIntervalMin = 0,
                walkIntervalMin = 0,
                socialThresholdMin = 0,
                distractingApps = setOf("com.instagram.android"),
            )
        )
    }

    @Test
    fun `isAvailable returns false when all nudges are off and distracting apps is empty`() {
        assertFalse(
            WellbeingCardLogic.isAvailable(
                waterIntervalMin = 0,
                walkIntervalMin = 0,
                socialThresholdMin = 0,
                distractingApps = emptySet(),
            )
        )
    }

    @Test
    fun `formatWater and formatWalk format labels with counts`() {
        assertEquals("Water 3", WellbeingCardLogic.formatWater(3))
        assertEquals("Water 0", WellbeingCardLogic.formatWater(0))
        assertEquals("Walk 1", WellbeingCardLogic.formatWalk(1))
        assertEquals("Walk 0", WellbeingCardLogic.formatWalk(0))
    }

    @Test
    fun `formatLine1 joins parts with middle dot separator only when present`() {
        assertEquals("Water 3 · Walk 1", WellbeingCardLogic.formatLine1("Water 3", "Walk 1"))
        assertEquals("Water 3", WellbeingCardLogic.formatLine1("Water 3", null))
        assertEquals("Walk 1", WellbeingCardLogic.formatLine1(null, "Walk 1"))
        assertEquals("", WellbeingCardLogic.formatLine1(null, null))
    }

    @Test
    fun `formatDuration formats duration using PauseFormatting`() {
        assertEquals("38 min", WellbeingCardLogic.formatDuration(38 * 60 * 1000L))
        assertEquals("0 min", WellbeingCardLogic.formatDuration(0L))
        assertEquals("1h 15m", WellbeingCardLogic.formatDuration(75 * 60 * 1000L))
        assertEquals("<1 min", WellbeingCardLogic.formatDuration(30 * 1000L))
    }

    @Test
    fun `formatOpens handles singular and plural`() {
        assertEquals("1 open", WellbeingCardLogic.formatOpens(1))
        assertEquals("6 opens", WellbeingCardLogic.formatOpens(6))
        assertEquals("0 opens", WellbeingCardLogic.formatOpens(0))
        assertEquals("2 opens", WellbeingCardLogic.formatOpens(2))
    }

    @Test
    fun `formatLine2 formats distracting apps label, duration, and opens`() {
        val line = WellbeingCardLogic.formatLine2(
            label = "Distracting apps",
            durationText = "38 min",
            opens = 6,
        )
        assertEquals("Distracting apps 38 min · 6 opens", line)

        val singularLine = WellbeingCardLogic.formatLine2(
            label = "Distracting apps",
            durationText = "1h 15m",
            opens = 1,
        )
        assertEquals("Distracting apps 1h 15m · 1 open", singularLine)
    }

    @Test
    fun `hasContent is false when nothing would render`() {
        assertFalse(WellbeingCardLogic.hasContent(0, 0, emptySet()))
    }

    @Test
    fun `hasContent is true for a tile or for distracting apps even without usage access`() {
        assertTrue(WellbeingCardLogic.hasContent(60, 0, emptySet()))
        assertTrue(WellbeingCardLogic.hasContent(0, 120, emptySet()))
        // The card then asks for usage access instead of vanishing.
        assertTrue(WellbeingCardLogic.hasContent(0, 0, setOf("a")))
    }

    @Test
    fun `formatDistractingLine formats distracting apps line with time opens and top app`() {
        val fullLine = WellbeingCardLogic.formatDistractingLine(
            label = "Distracting",
            durationText = "4 h 7 min",
            opensText = "107 opens",
            topAppText = "top: YouTube 4 h 7 min",
        )
        assertEquals("Distracting · 4 h 7 min · 107 opens · top: YouTube 4 h 7 min", fullLine)

        val withoutTopApp = WellbeingCardLogic.formatDistractingLine(
            label = "Distracting",
            durationText = "15 min",
            opensText = "3 opens",
            topAppText = null,
        )
        assertEquals("Distracting · 15 min · 3 opens", withoutTopApp)
    }

    @Test
    fun `card_wellbeing xml contains pbAllowance meter with horizontal style`() {
        val layout = resource("layout/card_wellbeing.xml")
        assertTrue(layout.contains("android:id=\"@+id/pbAllowance\""))
        assertTrue(layout.contains("?android:attr/progressBarStyleHorizontal"))
    }

    private fun resource(path: String): String =
        listOf("app/src/main/res/$path", "src/main/res/$path").map(::File).first { it.exists() }.readText()

    @Test
    fun `water and walk tiles are labelled with the nudge strings, not a bare number`() {
        val layout = resource("layout/card_wellbeing.xml")
        assertTrue(layout.contains("android:id=\"@+id/tvWaterLabel\""))
        assertTrue(layout.contains("android:text=\"@string/nudge_water\""))
        assertTrue(layout.contains("android:id=\"@+id/tvWalkLabel\""))
        assertTrue(layout.contains("android:text=\"@string/nudge_walk\""))
        // The value views carry the count only.
        assertEquals("%d", Regex("name=\"wellbeing_water_chip\"[^>]*>([^<]*)<").find(resource("values/strings.xml"))?.groupValues?.get(1))
        assertEquals("%d", Regex("name=\"wellbeing_walk_chip\"[^>]*>([^<]*)<").find(resource("values/strings.xml"))?.groupValues?.get(1))
    }

    @Test
    fun `isAvailable and hasContent return true when healthConnectEnabled is true`() {
        assertTrue(
            WellbeingCardLogic.isAvailable(
                waterIntervalMin = 0,
                walkIntervalMin = 0,
                socialThresholdMin = 0,
                distractingApps = emptySet(),
                healthConnectEnabled = true,
            )
        )
        assertTrue(
            WellbeingCardLogic.hasContent(
                waterIntervalMin = 0,
                walkIntervalMin = 0,
                distractingApps = emptySet(),
                healthConnectEnabled = true,
            )
        )
    }

    @Test
    fun `card_wellbeing xml contains health tiles for exercise resting hr and sleep`() {
        val layout = resource("layout/card_wellbeing.xml")
        assertTrue(layout.contains("android:id=\"@+id/llHealth\""))
        assertTrue(layout.contains("android:id=\"@+id/tileExercise\""))
        assertTrue(layout.contains("android:id=\"@+id/tvExercise\""))
        assertTrue(layout.contains("android:id=\"@+id/tvExerciseLabel\""))
        assertTrue(layout.contains("android:id=\"@+id/tileRestingHr\""))
        assertTrue(layout.contains("android:id=\"@+id/tvRestingHr\""))
        assertTrue(layout.contains("android:id=\"@+id/tvRestingHrLabel\""))
        assertTrue(layout.contains("android:id=\"@+id/tileSleep\""))
        assertTrue(layout.contains("android:id=\"@+id/tvSleep\""))
        assertTrue(layout.contains("android:id=\"@+id/tvSleepLabel\""))
    }
}
