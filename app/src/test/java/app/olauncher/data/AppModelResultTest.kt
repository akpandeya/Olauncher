package app.olauncher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppModelResultTest {

    @Test
    fun `result row properties and copy action`() {
        val copyAction = AppModel.Result.Action.Copy("84")
        val result = AppModel.Result(text = "= 84", action = copyAction)

        assertEquals("= 84", result.text)
        assertEquals("= 84", result.appLabel)
        assertEquals("", result.appPackage)
        assertFalse(result.isNew)
        assertEquals(0L, result.firstInstallTime)
        assertNotNull(result.user)
        assertEquals("84", copyAction.value)
        assertEquals("84", copyAction.textToCopy)
        assertEquals(copyAction, result.action)
    }

    @Test
    fun `result row properties and dial action`() {
        val dialAction = AppModel.Result.Action.Dial("0176 1234567")
        val result = AppModel.Result(text = "Call 0176 1234567", action = dialAction)

        assertEquals("Call 0176 1234567", result.text)
        assertEquals("Call 0176 1234567", result.appLabel)
        assertEquals("0176 1234567", dialAction.number)
        assertEquals(dialAction, result.action)
    }

    @Test
    fun `result rows equality and comparison`() {
        val res1 = AppModel.Result("= 84", AppModel.Result.Action.Copy("84"))
        val res2 = AppModel.Result("= 84", AppModel.Result.Action.Copy("84"))
        val res3 = AppModel.Result("= 49", AppModel.Result.Action.Copy("49"))

        assertEquals(res1, res2)
        assertNotEquals(res1, res3)
        assertTrue(res3 < res1) // "= 49" < "= 84"
    }
}
