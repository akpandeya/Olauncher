package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class GermanTimeTest {

    @Test
    fun `every 5-minute mark for hour 3`() {
        assertEquals("drei Uhr", GermanTime.words(3, 0))
        assertEquals("fünf nach drei", GermanTime.words(3, 5))
        assertEquals("zehn nach drei", GermanTime.words(3, 10))
        assertEquals("Viertel nach drei", GermanTime.words(3, 15))
        assertEquals("zwanzig nach drei", GermanTime.words(3, 20))
        assertEquals("fünf vor halb vier", GermanTime.words(3, 25))
        assertEquals("halb vier", GermanTime.words(3, 30))
        assertEquals("fünf nach halb vier", GermanTime.words(3, 35))
        assertEquals("zwanzig vor vier", GermanTime.words(3, 40))
        assertEquals("Viertel vor vier", GermanTime.words(3, 45))
        assertEquals("zehn vor vier", GermanTime.words(3, 50))
        assertEquals("fünf vor vier", GermanTime.words(3, 55))
    }

    @Test
    fun `both sides of 00 and 30`() {
        // Around 3:00
        assertEquals("kurz vor drei", GermanTime.words(2, 58))
        assertEquals("kurz vor drei", GermanTime.words(2, 59))
        assertEquals("drei Uhr", GermanTime.words(3, 0))
        assertEquals("kurz nach drei", GermanTime.words(3, 1))
        assertEquals("kurz nach drei", GermanTime.words(3, 2))

        // Around 3:30 (halb vier)
        assertEquals("kurz vor halb vier", GermanTime.words(3, 28))
        assertEquals("kurz vor halb vier", GermanTime.words(3, 29))
        assertEquals("halb vier", GermanTime.words(3, 30))
        assertEquals("kurz nach halb vier", GermanTime.words(3, 31))
        assertEquals("kurz nach halb vier", GermanTime.words(3, 32))
    }

    @Test
    fun `gleich cases`() {
        assertEquals("gleich fünf nach drei", GermanTime.words(3, 3))
        assertEquals("gleich fünf nach drei", GermanTime.words(3, 4))
        assertEquals("gleich zehn nach drei", GermanTime.words(3, 8))
        assertEquals("gleich zehn nach drei", GermanTime.words(3, 9))
        assertEquals("gleich Viertel nach drei", GermanTime.words(3, 13))
        assertEquals("gleich Viertel nach drei", GermanTime.words(3, 14))
        assertEquals("gleich zwanzig nach drei", GermanTime.words(3, 18))
        assertEquals("gleich zwanzig nach drei", GermanTime.words(3, 19))
        assertEquals("gleich fünf vor halb vier", GermanTime.words(3, 23))
        assertEquals("gleich fünf vor halb vier", GermanTime.words(3, 24))
        assertEquals("gleich fünf nach halb vier", GermanTime.words(3, 33))
        assertEquals("gleich fünf nach halb vier", GermanTime.words(3, 34))
        assertEquals("gleich zwanzig vor vier", GermanTime.words(3, 38))
        assertEquals("gleich zwanzig vor vier", GermanTime.words(3, 39))
        assertEquals("gleich Viertel vor vier", GermanTime.words(3, 43))
        assertEquals("gleich Viertel vor vier", GermanTime.words(3, 44))
        assertEquals("gleich zehn vor vier", GermanTime.words(3, 48))
        assertEquals("gleich zehn vor vier", GermanTime.words(3, 49))
        assertEquals("gleich fünf vor vier", GermanTime.words(3, 53))
        assertEquals("gleich fünf vor vier", GermanTime.words(3, 54))
    }

    @Test
    fun `after-mark cases between marks retain the mark`() {
        assertEquals("fünf nach drei", GermanTime.words(3, 6))
        assertEquals("fünf nach drei", GermanTime.words(3, 7))
        assertEquals("zehn nach drei", GermanTime.words(3, 11))
        assertEquals("zehn nach drei", GermanTime.words(3, 12))
        assertEquals("Viertel nach drei", GermanTime.words(3, 16))
        assertEquals("Viertel nach drei", GermanTime.words(3, 17))
        assertEquals("zwanzig nach drei", GermanTime.words(3, 21))
        assertEquals("zwanzig nach drei", GermanTime.words(3, 22))
        assertEquals("fünf vor halb vier", GermanTime.words(3, 26))
        assertEquals("fünf vor halb vier", GermanTime.words(3, 27))
        assertEquals("fünf nach halb vier", GermanTime.words(3, 36))
        assertEquals("fünf nach halb vier", GermanTime.words(3, 37))
        assertEquals("zwanzig vor vier", GermanTime.words(3, 41))
        assertEquals("zwanzig vor vier", GermanTime.words(3, 42))
        assertEquals("Viertel vor vier", GermanTime.words(3, 46))
        assertEquals("Viertel vor vier", GermanTime.words(3, 47))
        assertEquals("zehn vor vier", GermanTime.words(3, 51))
        assertEquals("zehn vor vier", GermanTime.words(3, 52))
        assertEquals("fünf vor vier", GermanTime.words(3, 56))
        assertEquals("fünf vor vier", GermanTime.words(3, 57))
    }

    @Test
    fun `1 00 vs 1 15 ein Uhr vs eins in phrases`() {
        assertEquals("ein Uhr", GermanTime.words(1, 0))
        assertEquals("ein Uhr", GermanTime.words(13, 0))
        assertEquals("Viertel nach eins", GermanTime.words(1, 15))
        assertEquals("Viertel nach eins", GermanTime.words(13, 15))
        assertEquals("fünf nach eins", GermanTime.words(1, 5))
        assertEquals("halb zwei", GermanTime.words(1, 30))
        assertEquals("fünf vor halb zwei", GermanTime.words(1, 25))
    }

    @Test
    fun `approaching 1 00 gives kurz vor eins`() {
        assertEquals("kurz vor eins", GermanTime.words(12, 58))
        assertEquals("kurz vor eins", GermanTime.words(12, 59))
        assertEquals("kurz vor eins", GermanTime.words(0, 58))
        assertEquals("kurz vor eins", GermanTime.words(0, 59))
        assertEquals("kurz nach eins", GermanTime.words(1, 1))
        assertEquals("kurz nach eins", GermanTime.words(1, 2))
    }

    @Test
    fun `midnight and approaching midnight`() {
        assertEquals("Mitternacht", GermanTime.words(0, 0))
        assertEquals("kurz nach Mitternacht", GermanTime.words(0, 1))
        assertEquals("kurz nach Mitternacht", GermanTime.words(0, 2))
        assertEquals("kurz vor Mitternacht", GermanTime.words(23, 58))
        assertEquals("kurz vor Mitternacht", GermanTime.words(23, 59))
    }

    @Test
    fun `noon and approaching noon`() {
        assertEquals("zwölf Uhr mittags", GermanTime.words(12, 0))
        assertEquals("kurz nach zwölf", GermanTime.words(12, 1))
        assertEquals("kurz nach zwölf", GermanTime.words(12, 2))
        assertEquals("kurz vor zwölf", GermanTime.words(11, 58))
        assertEquals("kurz vor zwölf", GermanTime.words(11, 59))
        assertEquals("halb eins", GermanTime.words(12, 30))
        assertEquals("Viertel nach zwölf", GermanTime.words(12, 15))
    }

    @Test
    fun `all 12 hours on the hour`() {
        assertEquals("Mitternacht", GermanTime.words(0, 0))
        assertEquals("ein Uhr", GermanTime.words(1, 0))
        assertEquals("zwei Uhr", GermanTime.words(2, 0))
        assertEquals("drei Uhr", GermanTime.words(3, 0))
        assertEquals("vier Uhr", GermanTime.words(4, 0))
        assertEquals("fünf Uhr", GermanTime.words(5, 0))
        assertEquals("sechs Uhr", GermanTime.words(6, 0))
        assertEquals("sieben Uhr", GermanTime.words(7, 0))
        assertEquals("acht Uhr", GermanTime.words(8, 0))
        assertEquals("neun Uhr", GermanTime.words(9, 0))
        assertEquals("zehn Uhr", GermanTime.words(10, 0))
        assertEquals("elf Uhr", GermanTime.words(11, 0))
        assertEquals("zwölf Uhr mittags", GermanTime.words(12, 0))
        assertEquals("ein Uhr", GermanTime.words(13, 0))
        assertEquals("elf Uhr", GermanTime.words(23, 0))
    }
}
