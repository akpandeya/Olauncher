package app.olauncher.helper

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusBarFadeTest {
    @Test
    fun heightIsStatusBarPlusSixteenDp() {
        assertEquals(63 + 40, StatusBarFade.heightPx(63, 2.5f))
    }

    @Test
    fun negativeInsetIsTreatedAsZero() {
        assertEquals(32, StatusBarFade.heightPx(-5, 2f))
    }

    @Test
    fun fadeUsesTheReadableCardColour() {
        val xml = File("src/main/res/drawable/status_bar_fade.xml").readText()
        assertTrue(xml.contains("@color/readableCard"))
    }

    @Test
    fun fadeIsOnDrawerTodayAndSettingsOnly() {
        for (name in listOf("fragment_app_drawer", "fragment_today", "fragment_settings")) {
            assertTrue(name, File("src/main/res/layout/$name.xml").readText().contains("@drawable/status_bar_fade"))
        }
        assertTrue(File("src/main/res/layout-land/fragment_app_drawer.xml").readText().contains("@drawable/status_bar_fade"))
        assertTrue(!File("src/main/res/layout/fragment_home.xml").readText().contains("status_bar_fade"))
    }
}
