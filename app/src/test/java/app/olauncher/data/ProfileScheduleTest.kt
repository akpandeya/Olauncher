package app.olauncher.data

import app.olauncher.helper.HomeSet
import app.olauncher.helper.HomeSetStarts
import app.olauncher.helper.HomeSets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ProfileScheduleTest {

    private val mon = 1
    private val tue = 2
    private val wed = 3
    private val fri = 5
    private val sat = 6
    private val sun = 7
    private val weekdays = setOf(1, 2, 3, 4, 5)

    private fun hm(h: Int, m: Int = 0) = h * 60 + m

    private val focus = ProfileRule("focus", hm(9), hm(17), weekdays)
    private val schedule = ProfileSchedule("rich", listOf(focus))

    @Test
    fun ruleWindowStartIncludedEndExcluded() {
        assertEquals("rich", schedule.resolve(hm(8, 59), mon))
        assertEquals("focus", schedule.resolve(hm(9), mon))
        assertEquals("focus", schedule.resolve(hm(16, 59), mon))
        assertEquals("rich", schedule.resolve(hm(17), mon))
    }

    @Test
    fun weekdayBoundaries() {
        assertEquals("focus", schedule.resolve(hm(10), mon))
        assertEquals("focus", schedule.resolve(hm(10), fri))
        assertEquals("rich", schedule.resolve(hm(10), sat))
        assertEquals("rich", schedule.resolve(hm(10), sun))
    }

    @Test
    fun uncoveredTimeUsesTheDefault() {
        assertEquals("rich", ProfileSchedule("rich", emptyList()).resolve(hm(12), tue))
        assertEquals("rich", ProfileSchedule("rich", listOf(focus.copy(weekdays = emptySet()))).resolve(hm(12), tue))
    }

    @Test
    fun laterRuleWinsWhereRulesOverlap() {
        val lunch = ProfileRule("bare", hm(12), hm(13), weekdays)
        val s = ProfileSchedule("rich", listOf(focus, lunch))
        assertEquals("focus", s.resolve(hm(11, 59), tue))
        assertEquals("bare", s.resolve(hm(12), tue))
        assertEquals("focus", s.resolve(hm(13), tue))
        // Same two rules, other order: the wider rule now wins everywhere.
        assertEquals("focus", ProfileSchedule("rich", listOf(lunch, focus)).resolve(hm(12, 30), tue))
    }

    @Test
    fun midnightCrossingRangeBelongsToTheStartDay() {
        val night = ProfileRule("bare", hm(22), hm(6), setOf(fri))
        val s = ProfileSchedule("rich", listOf(night))
        assertEquals("rich", s.resolve(hm(21, 59), fri))
        assertEquals("bare", s.resolve(hm(22), fri))
        assertEquals("bare", s.resolve(hm(23, 59), fri))
        assertEquals("bare", s.resolve(hm(0), sat))
        assertEquals("bare", s.resolve(hm(5, 59), sat))
        assertEquals("rich", s.resolve(hm(6), sat))
        // Not on Friday morning (the window started Thursday) nor Saturday night.
        assertEquals("rich", s.resolve(hm(3), fri))
        assertEquals("rich", s.resolve(hm(23), sat))
    }

    @Test
    fun midnightCrossingWrapsSundayToMonday() {
        val night = ProfileRule("bare", hm(22), hm(6), setOf(sun))
        val s = ProfileSchedule("rich", listOf(night))
        assertEquals("bare", s.resolve(hm(23), sun))
        assertEquals("bare", s.resolve(hm(2), mon))
        assertEquals("rich", s.resolve(hm(2), tue))
    }

    @Test
    fun endEqualToStartIsAFullDay() {
        val all = ProfileRule("bare", hm(8), hm(8), setOf(sat))
        val s = ProfileSchedule("rich", listOf(all))
        assertEquals("rich", s.resolve(hm(7, 59), sat))
        assertEquals("bare", s.resolve(hm(8), sat))
        assertEquals("bare", s.resolve(hm(23, 59), sat))
        assertEquals("bare", s.resolve(hm(7, 59), sun))
        assertEquals("rich", s.resolve(hm(8), sun))
    }

    @Test
    fun invalidRuleValuesAreRejected() {
        listOf(
            { ProfileRule("x", -1, 10, weekdays) },
            { ProfileRule("x", 0, 1440, weekdays) },
            { ProfileRule("x", 0, 10, setOf(0)) },
            { ProfileRule("x", 0, 10, setOf(8)) },
        ).forEach {
            try {
                it()
                fail("expected rejection")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    // --- Migration from the old Home sets ---

    private val startSamples = listOf(
        HomeSetStarts(6 * 60, 9 * 60, 18 * 60),
        HomeSetStarts(0, 12 * 60, 23 * 60 + 59),
        HomeSetStarts(20 * 60, 6 * 60, 22 * 60),
        HomeSetStarts(9 * 60, 9 * 60, 18 * 60),
        HomeSetStarts(6 * 60, 18 * 60, 18 * 60),
        HomeSetStarts(6 * 60, 6 * 60, 6 * 60),
        HomeSetStarts(18 * 60, 9 * 60, 9 * 60),
        HomeSetStarts(1439, 0, 720),
    )

    private val appCases: List<(HomeSet) -> Boolean> = listOf(
        { true },
        { it == HomeSet.DAY },
        { it != HomeSet.EVENING },
        { it != HomeSet.MORNING },
    )

    @Test
    fun migratedScheduleGivesTheSameSetAtEveryMinuteAndWeekday() {
        for (starts in startSamples) {
            val migrated = HomeSetProfiles.scheduleFrom(starts)
            for (hasApps in appCases) {
                for (minute in 0 until HomeSets.MINUTES_PER_DAY) {
                    val expected = HomeSets.active(minute, starts, true, hasApps)
                    for (day in 1..7) {
                        val id = ProfileScheduleResolver.activeProfileId(
                            migrated, minute, day, windDownActive = false, windDownProfileId = null,
                            hasApps = { hasApps(HomeSetProfiles.setOf(it)) },
                        )
                        assertEquals("starts=$starts minute=$minute day=$day", expected, HomeSetProfiles.setOf(id))
                    }
                }
            }
        }
    }

    @Test
    fun migrationKeepsTheSwitchTimes() {
        val migrated = HomeSetProfiles.scheduleFrom(HomeSetStarts(6 * 60, 9 * 60, 18 * 60))
        assertEquals(HomeSetProfiles.DAY_ID, migrated.defaultProfileId)
        assertEquals(
            listOf(
                ProfileRule(HomeSetProfiles.DAY_ID, hm(9), hm(18), ProfileSchedule.EVERY_DAY),
                ProfileRule(HomeSetProfiles.MORNING_ID, hm(6), hm(9), ProfileSchedule.EVERY_DAY),
                ProfileRule(HomeSetProfiles.EVENING_ID, hm(18), hm(6), ProfileSchedule.EVERY_DAY),
            ),
            migrated.rules,
        )
    }

    @Test
    fun homeSetsOffGivesTheDayProfile() {
        val off = ProfileSchedule(HomeSetProfiles.DAY_ID, emptyList())
        for (minute in 0 until HomeSets.MINUTES_PER_DAY step 7) {
            assertEquals(HomeSetProfiles.DAY_ID, off.resolve(minute, mon))
        }
    }

    @Test
    fun profileIdsMapToTheirHomeSets() {
        assertEquals(HomeSet.MORNING, HomeSetProfiles.setOf(HomeSetProfiles.MORNING_ID))
        assertEquals(HomeSet.DAY, HomeSetProfiles.setOf(HomeSetProfiles.DAY_ID))
        assertEquals(HomeSet.EVENING, HomeSetProfiles.setOf(HomeSetProfiles.EVENING_ID))
        assertEquals(HomeSet.DAY, HomeSetProfiles.setOf("focus"))
        HomeSet.values().forEach { assertEquals(it, HomeSetProfiles.setOf(HomeSetProfiles.idOf(it))) }
    }

    // --- Wind-down ---

    private val sets = HomeSetProfiles.scheduleFrom(HomeSetStarts(6 * 60, 9 * 60, 18 * 60))

    @Test
    fun windDownPicksTheEveningProfileAtAnyTime() {
        val id = ProfileScheduleResolver.activeProfileId(sets, hm(10), tue, true, HomeSetProfiles.EVENING_ID)
        assertEquals(HomeSetProfiles.EVENING_ID, id)
    }

    @Test
    fun windDownNeedsApps() {
        val id = ProfileScheduleResolver.activeProfileId(
            sets, hm(10), tue, true, HomeSetProfiles.EVENING_ID, hasApps = { it != HomeSetProfiles.EVENING_ID },
        )
        assertEquals(HomeSetProfiles.DAY_ID, id)
    }

    @Test
    fun windDownDoesNotForceWhenHomeSetsAreOff() {
        val id = ProfileScheduleResolver.activeProfileId(sets, hm(10), tue, true, null)
        assertEquals(HomeSetProfiles.DAY_ID, id)
    }

    @Test
    fun noWindDownFollowsTheSchedule() {
        val id = ProfileScheduleResolver.activeProfileId(sets, hm(7), tue, false, HomeSetProfiles.EVENING_ID)
        assertEquals(HomeSetProfiles.MORNING_ID, id)
    }

    @Test
    fun windDownMatchesTheOldRuleAtEveryMinute() {
        val starts = HomeSetStarts(6 * 60, 9 * 60, 18 * 60)
        for (hasApps in appCases) {
            for (minute in 0 until HomeSets.MINUTES_PER_DAY) {
                for (windDown in listOf(false, true)) {
                    // The rule in Prefs.activeHomeSet before this change.
                    val old = if (windDown && hasApps(HomeSet.EVENING)) HomeSet.EVENING
                    else HomeSets.active(minute, starts, true, hasApps)
                    val id = ProfileScheduleResolver.activeProfileId(
                        sets, minute, wed, windDown, HomeSetProfiles.EVENING_ID,
                        hasApps = { hasApps(HomeSetProfiles.setOf(it)) },
                    )
                    assertEquals("minute=$minute windDown=$windDown", old, HomeSetProfiles.setOf(id))
                }
            }
        }
    }

    @Test
    fun nonDefaultProfileWithoutAppsFallsBackToTheDefault() {
        val id = ProfileScheduleResolver.activeProfileId(
            schedule, hm(10), mon, false, null, hasApps = { it != "focus" },
        )
        assertEquals("rich", id)
    }

    // --- Settings bundle on change ---

    @Test
    fun bundleIsAppliedOnlyWhenTheActiveProfileChanges() {
        val profiles = BuiltInProfiles.all(emptyList())
        val focusProfile = profiles.first { it.id == BuiltInProfiles.FOCUS_ID }
        assertSame(focusProfile, ProfileScheduleResolver.profileToApply("focus", "rich", profiles))
        assertNull(ProfileScheduleResolver.profileToApply("focus", "focus", profiles))
        assertNull(ProfileScheduleResolver.profileToApply(HomeSetProfiles.DAY_ID, "focus", profiles))
    }

    // --- Storage ---

    @Test
    fun jsonRoundTrips() {
        val s = ProfileSchedule("rich", listOf(focus, ProfileRule("bare", hm(22), hm(6), setOf(fri, sat))))
        assertEquals(s, ProfileSchedule.fromJson(s.toJson()))
    }

    @Test
    fun badJsonIsRejected() {
        listOf(
            "not json",
            """{"default":"x"}""",
            """{"default":"x","rules":[],"extra":1}""",
            """{"default":"x","rules":[{"profile":"a","start":0,"end":5}]}""",
            """{"default":"x","rules":[{"profile":"a","start":0,"end":5,"weekdays":[8]}]}""",
            """{"default":"x","rules":[{"profile":"a","start":0,"end":5,"weekdays":[1],"x":1}]}""",
            """{"default":"x","rules":[{"profile":"a","start":-3,"end":5,"weekdays":[1]}]}""",
        ).forEach {
            try {
                ProfileSchedule.fromJson(it)
                fail("expected rejection of $it")
            } catch (_: ProfileFormatException) {
            }
        }
        assertTrue(runCatching { ProfileSchedule.fromJson("""{"default":"x","rules":[]}""") }.isSuccess)
        assertFalse(runCatching { ProfileSchedule.fromJson("") }.isSuccess)
    }
}
