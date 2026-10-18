package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DialMatchTest {

    @Test
    fun `valid phone numbers match and return number`() {
        assertEquals("0176 1234567", DialMatch.number("0176 1234567"))
        assertEquals("+49 176 1234567", DialMatch.number("+49 176 1234567"))
        assertEquals("0176-1234567", DialMatch.number("0176-1234567"))
        assertEquals("+1 (555) 123-4567", DialMatch.number("+1 (555) 123-4567"))
        assertEquals("123456", DialMatch.number("123456"))
        assertEquals("+123456", DialMatch.number("+123456"))
        assertEquals("0176 1234567", DialMatch.number("  0176 1234567  "))
    }

    @Test
    fun `numbers that are too short return null`() {
        assertNull(DialMatch.number("12345"))
        assertNull(DialMatch.number("+12345"))
        assertNull(DialMatch.number("1"))
        assertNull(DialMatch.number("+1"))
    }

    @Test
    fun `invalid formats return null`() {
        assertNull(DialMatch.number(""))
        assertNull(DialMatch.number("   "))
        assertNull(DialMatch.number("hello"))
        assertNull(DialMatch.number("0176 abc"))
        assertNull(DialMatch.number("12*7"))
        assertNull(DialMatch.number("(0176) 1234567")) // Doesn't start with digit or +digit
        assertNull(DialMatch.number("+"))
        assertNull(DialMatch.number("+(555) 1234567")) // Doesn't have digit after +
    }
}
