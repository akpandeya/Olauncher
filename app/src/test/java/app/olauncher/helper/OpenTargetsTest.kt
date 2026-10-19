package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenTargetsTest {

    @Test
    fun `blank package gives no target`() {
        assertNull(OpenTargets.target("Clock", "", "cls", "u"))
        assertNull(OpenTargets.target("Clock", "   ", null, "u"))
    }

    @Test
    fun `chosen package keeps all fields`() {
        val t = OpenTargets.target("Weather", "com.example.wx", "com.example.wx.Main", "user-7")
        assertEquals(AppTarget("Weather", "com.example.wx", "com.example.wx.Main", "user-7"), t)
    }

    @Test
    fun `event opens only with a positive id`() {
        assertTrue(OpenTargets.canOpenEvent(42L))
        assertFalse(OpenTargets.canOpenEvent(0L))
        assertFalse(OpenTargets.canOpenEvent(-1L))
    }
}
