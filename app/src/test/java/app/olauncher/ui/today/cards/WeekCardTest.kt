package app.olauncher.ui.today.cards

import app.olauncher.R
import app.olauncher.ui.today.TodayCards
import app.olauncher.wellbeing.DaySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.time.LocalDate
import javax.xml.parsers.DocumentBuilderFactory

class WeekCardTest {

    @Test
    fun `week card is registered in TodayCards all`() {
        val card = TodayCards.all.firstOrNull { it.key == WeekCard.KEY }
        assertNotNull(card)
        assertEquals("week", card?.key)
        assertEquals(R.string.last_7_days, card?.titleRes)
        assertEquals(true, card?.defaultEnabled)
    }

    @Test
    fun `isAvailable returns false when fewer than 2 snapshots in last 7 days`() {
        val today = 100L

        // Empty
        assertFalse(WeekCard.isAvailable(emptyList(), today))

        // Only 1 snapshot in last 7 days (93..99)
        val oneSnapshot = listOf(DaySnapshot(epochDay = 95L))
        assertFalse(WeekCard.isAvailable(oneSnapshot, today))

        // Snapshots outside last 7 days (e.g. 90L and 91L, or today 100L)
        val outsideSnapshots = listOf(
            DaySnapshot(epochDay = 90L),
            DaySnapshot(epochDay = 91L),
            DaySnapshot(epochDay = 100L),
        )
        assertFalse(WeekCard.isAvailable(outsideSnapshots, today))

        // 1 inside (95L) and 1 outside (90L) = 1 in last 7 days
        val mixedOne = listOf(
            DaySnapshot(epochDay = 90L),
            DaySnapshot(epochDay = 95L),
        )
        assertFalse(WeekCard.isAvailable(mixedOne, today))
    }

    @Test
    fun `isAvailable returns true when at least 2 snapshots in last 7 days`() {
        val today = 100L

        // Exactly 2 snapshots in last 7 days (93..99)
        val twoSnapshots = listOf(
            DaySnapshot(epochDay = 93L),
            DaySnapshot(epochDay = 99L),
        )
        assertTrue(WeekCard.isAvailable(twoSnapshots, today))

        // 3 snapshots in last 7 days + 1 outside
        val multipleSnapshots = listOf(
            DaySnapshot(epochDay = 80L),
            DaySnapshot(epochDay = 94L),
            DaySnapshot(epochDay = 95L),
            DaySnapshot(epochDay = 96L),
        )
        assertTrue(WeekCard.isAvailable(multipleSnapshots, today))
    }

    @Test
    fun `isAvailable LocalDate overload delegates correctly`() {
        val date = LocalDate.of(2026, 10, 18)
        val epochDay = date.toEpochDay()
        val snapshots = listOf(
            DaySnapshot(epochDay = epochDay - 1),
            DaySnapshot(epochDay = epochDay - 2),
        )
        assertTrue(WeekCard.isAvailable(snapshots, date))
    }

    private fun stringsXml(): org.w3c.dom.Document {
        val file = listOf(
            File("src/main/res/values/strings.xml"),
            File("app/src/main/res/values/strings.xml"),
        ).firstOrNull { it.isFile } ?: error("strings.xml not found from ${File(".").absolutePath}")
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
    }

    private fun namesOf(tag: String): Map<String, String> {
        val nodes = stringsXml().getElementsByTagName(tag)
        return (0 until nodes.length).associate {
            val element = nodes.item(it) as Element
            element.getAttribute("name") to element.textContent
        }
    }

    private fun resourceNamesById(innerClass: String): Map<Int, String> =
        Class.forName("app.olauncher.R\$$innerClass").fields
            .filter { it.type == Int::class.javaPrimitiveType }
            .associate { it.getInt(null) to it.name }

    @Test
    fun `every string and plural used by WeekCardText exists in strings xml`() {
        val strings = namesOf("string")
        val stringNamesById = resourceNamesById("string")
        for (id in WeekCardText.usedStringResIds) {
            val name = stringNamesById[id] ?: error("no R.string name for id $id")
            assertTrue("missing string $name", name in strings)
        }
        val plurals = namesOf("plurals")
        val pluralNamesById = resourceNamesById("plurals")
        for (id in WeekCardText.usedPluralsResIds) {
            val name = pluralNamesById[id] ?: error("no R.plurals name for id $id")
            assertTrue("missing plurals $name", name in plurals)
        }
    }

    @Test
    fun `every week_review string in strings xml is used by WeekCardText`() {
        val usedNames = WeekCardText.usedStringResIds.map { resourceNamesById("string").getValue(it) } +
            WeekCardText.usedPluralsResIds.map { resourceNamesById("plurals").getValue(it) }
        val declared = (namesOf("string").keys + namesOf("plurals").keys).filter { it.startsWith("week_review_") }
        assertEquals(declared.toSet(), usedNames.filter { it.startsWith("week_review_") }.toSet())
    }

    @Test
    fun `english templates produce the same text as before`() {
        val strings = namesOf("string")
        fun t(name: String, vararg args: Any) = String.format(strings.getValue(name), *args)

        assertEquals("4 h 12 min", t("week_review_duration_hours_minutes", 4, 12))
        assertEquals("2 h", t("week_review_duration_hours", 2))
        assertEquals("37 min", t("week_review_duration_minutes", 37))
        assertEquals("Distracting apps 3 min · 1 open", t("week_review_distracting", "3 min", "1 open"))
        assertEquals("17% less than the week before", t("week_review_less_than_week_before", 17))
        assertEquals("23% more than the week before", t("week_review_more_than_week_before", 23))
        assertEquals("Most: Mail 1 h", t("week_review_top_apps", t("week_review_top_app_item", "Mail", "1 h")))
        assertEquals("Over allowance on 3 of 7 days", t("week_review_over_allowance", 3, 7))
        assertEquals("Pause: 6 × not now · 4 × opened", t("week_review_pause", 6, 4))
        assertEquals("Pause: 6 × not now · 4 × opened (mostly: Just bored)", t("week_review_pause_mostly", "Pause: 6 × not now · 4 × opened", "Just bored"))
        assertEquals("Water 9 · Walk 8", t("week_review_nudges_water_walk", 9, 8))
        assertEquals("Water 9", t("week_review_nudges_water", 9))
        assertEquals("Walk 8", t("week_review_nudges_walk", 8))

        val pluralsNode = stringsXml().getElementsByTagName("plurals").let { list ->
            (0 until list.length).map { list.item(it) as Element }.first { it.getAttribute("name") == "week_review_opens" }
        }
        val items = pluralsNode.getElementsByTagName("item").let { list ->
            (0 until list.length).map { list.item(it) as Element }.associate { it.getAttribute("quantity") to it.textContent }
        }
        assertEquals("1 open", String.format(items.getValue("one"), 1))
        assertEquals("46 opens", String.format(items.getValue("other"), 46))
    }
}
