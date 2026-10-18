package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DialCalcRowsTest {
    private fun calc(q: String) = if (DialMatch.showCalcRow(q)) Calc.evaluate(q) else null
    private fun dial(q: String) = if (DialMatch.showDialRow(q)) DialMatch.number(q) else null

    @Test
    fun `hyphenated phone number has dial row and no calc row`() {
        assertNull(calc("030-1234567"))
        assertNotNull(dial("030-1234567"))
    }

    @Test
    fun `spaced subtraction has calc row and no dial row`() {
        assertNotNull(calc("1000 - 200"))
        assertNull(dial("1000 - 200"))
    }

    @Test
    fun `international number has no calc row`() {
        assertNull(calc("+49 30 1234567"))
        assertNotNull(dial("+49 30 1234567"))
    }

    @Test
    fun `plain sum still has calc row`() {
        assertNotNull(calc("2+3"))
        assertTrue(DialMatch.showCalcRow("2+3"))
        assertFalse(DialMatch.hasSpacedOperator("2+3"))
    }

    @Test
    fun `short unspaced arithmetic keeps calc row`() {
        assertEquals("5", calc("10-5"))
        assertEquals("0", calc("5-5"))
        assertEquals("5", calc("2+3"))
        assertEquals("48", calc("12*4"))
        assertEquals("5", calc("10/2"))
    }
}
