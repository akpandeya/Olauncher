package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalcTest {

    @Test
    fun `acceptance criteria tests`() {
        assertEquals("84", Calc.evaluate("12*7"))
        assertEquals("3.5", Calc.evaluate("2,5+1"))
        assertEquals("49", Calc.evaluate("(3+4)^2"))
        assertNull(Calc.evaluate("50%"))
        assertEquals("20", Calc.evaluate("10%*200"))
        assertEquals("0.3333333333", Calc.evaluate("1/3"))
        assertNull(Calc.evaluate("7"))
        assertNull(Calc.evaluate("hello"))
    }

    @Test
    fun `phone check calculation`() {
        assertEquals("59.7", Calc.evaluate("19,90*3"))
        assertEquals("59.7", Calc.evaluate("19.90*3"))
    }

    @Test
    fun `unary operators without binary operator return null`() {
        assertNull(Calc.evaluate("-7"))
        assertNull(Calc.evaluate("+7"))
        assertNull(Calc.evaluate("--7"))
        assertNull(Calc.evaluate("(-7)"))
        assertNull(Calc.evaluate("-(7)"))
        assertNull(Calc.evaluate("-50%"))
    }

    @Test
    fun `unary minus combined with binary operators`() {
        assertEquals("-7", Calc.evaluate("-(3+4)"))
        assertEquals("-6", Calc.evaluate("3*-2"))
        assertEquals("-6", Calc.evaluate("-3*2"))
        assertEquals("-1", Calc.evaluate("-3--2"))
    }

    @Test
    fun `power operations`() {
        assertEquals("0.25", Calc.evaluate("2^-2"))
        assertEquals("9", Calc.evaluate("(-3)^2"))
        assertEquals("-9", Calc.evaluate("-3^2"))
        assertEquals("512", Calc.evaluate("2^3^2"))
        assertEquals("64", Calc.evaluate("(2^3)^2"))
    }

    @Test
    fun `unicode multiplication and division`() {
        assertEquals("42", Calc.evaluate("6×7"))
        assertEquals("7", Calc.evaluate("14÷2"))
    }

    @Test
    fun `division by zero returns null`() {
        assertNull(Calc.evaluate("1/0"))
        assertNull(Calc.evaluate("1÷0"))
        assertNull(Calc.evaluate("0/0"))
        assertNull(Calc.evaluate("1/(5-5)"))
        assertNull(Calc.evaluate("0^-1"))
    }

    @Test
    fun `formatting max 10 significant digits without trailing zeros`() {
        assertEquals("0.1666666667", Calc.evaluate("1/6"))
        assertEquals("33.33333333", Calc.evaluate("100/3"))
        assertEquals("0.0003333333333", Calc.evaluate("1/3000"))
        assertEquals("1000", Calc.evaluate("500*2"))
        assertEquals("0", Calc.evaluate("5-5"))
    }

    @Test
    fun `invalid or incomplete expressions return null`() {
        assertNull(Calc.evaluate(""))
        assertNull(Calc.evaluate("   "))
        assertNull(Calc.evaluate("12*"))
        assertNull(Calc.evaluate("+"))
        assertNull(Calc.evaluate("*"))
        assertNull(Calc.evaluate("1+*2"))
        assertNull(Calc.evaluate("(1+2"))
        assertNull(Calc.evaluate("1+2)"))
        assertNull(Calc.evaluate("()"))
        assertNull(Calc.evaluate("1 2 + 3"))
        assertNull(Calc.evaluate("12*7abc"))
        assertNull(Calc.evaluate("."))
        assertNull(Calc.evaluate(","))
    }

    @Test
    fun `deeply nested input does not crash`() {
        assertNull(Calc.evaluate("(".repeat(100000) + "1+1" + ")".repeat(100000)))
        assertNull(Calc.evaluate("-".repeat(100000) + "1+1"))
        assertNull(Calc.evaluate("9".repeat(400) + "*9".repeat(5)))
    }
}
