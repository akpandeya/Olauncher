package app.olauncher

import app.olauncher.helper.SwipeSettingMenu
import app.olauncher.helper.SwipeSettingMenu.Option
import app.olauncher.helper.SwipeSettingMenu.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeSettingMenuTest {
    @Test
    fun offIsFirstForBothSides() {
        assertEquals(Option.OFF, SwipeSettingMenu.options(Side.LEFT).first())
        assertEquals(Option.OFF, SwipeSettingMenu.options(Side.RIGHT).first())
    }

    @Test
    fun leftOffersNoTodayPage() {
        assertEquals(listOf(Option.OFF, Option.CHOOSE_APP), SwipeSettingMenu.options(Side.LEFT))
    }

    @Test
    fun rightOffersTodayAndApp() {
        assertEquals(listOf(Option.OFF, Option.TODAY_PAGE, Option.CHOOSE_APP), SwipeSettingMenu.options(Side.RIGHT))
    }

    @Test
    fun offDisablesWithoutPicker() {
        val o = SwipeSettingMenu.outcome(Side.RIGHT, Option.OFF)
        assertFalse(o.enabled)
        assertFalse(o.openAppPicker)
        assertNull(o.rightOpensToday)
    }

    @Test
    fun todayEnablesAndSelectsToday() {
        val o = SwipeSettingMenu.outcome(Side.RIGHT, Option.TODAY_PAGE)
        assertTrue(o.enabled)
        assertFalse(o.openAppPicker)
        assertEquals(true, o.rightOpensToday)
    }

    @Test
    fun chooseAppEnablesAndOpensPicker() {
        val o = SwipeSettingMenu.outcome(Side.LEFT, Option.CHOOSE_APP)
        assertTrue(o.enabled)
        assertTrue(o.openAppPicker)
        assertNull(o.rightOpensToday)
    }

    @Test(expected = IllegalArgumentException::class)
    fun leftRejectsTodayPage() {
        SwipeSettingMenu.outcome(Side.LEFT, Option.TODAY_PAGE)
    }
}
