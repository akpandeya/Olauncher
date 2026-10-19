package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CurrencyConvertTest {
    private val now = 1_000_000_000_000L
    private val rates = Rates("2026-10-18", mapOf("USD" to 1.084, "INR" to 90.0, "JPY" to 160.0), now)

    @Test fun parsesCodes() =
        assertEquals(CurrencyQuery(20.0, "EUR", "USD"), CurrencyConvert.parse("20 eur in usd"))

    @Test fun parsesSymbols() =
        assertEquals(CurrencyQuery(20.0, "EUR", "USD"), CurrencyConvert.parse("20€ to $"))

    @Test fun parsesDecimalComma() =
        assertEquals(CurrencyQuery(1.5, "GBP", "INR"), CurrencyConvert.parse("1,5 £ to inr"))

    @Test fun rejectsSuffixAmount() = assertNull(CurrencyConvert.parse("1,5k inr in eur"))
    @Test fun rejectsUnknownCode() = assertNull(CurrencyConvert.parse("20 xyz in usd"))
    @Test fun rejectsTrailingText() = assertNull(CurrencyConvert.parse("20 eur in usd please"))
    @Test fun rejectsUnitQuery() = assertNull(CurrencyConvert.parse("5 km in mi"))

    @Test fun eurToUsd() {
        val m = CurrencyConvert.convert(CurrencyQuery(20.0, "EUR", "USD"), rates, now)!!
        assertEquals("21.68", m.formatted)
        assertEquals("USD", m.code)
    }

    @Test fun crossRateViaEur() {
        val m = CurrencyConvert.convert(CurrencyQuery(10.0, "USD", "INR"), rates, now)!!
        assertEquals(10.0 / 1.084 * 90.0, m.amount, 1e-9)
    }

    @Test fun jpyHasNoDecimals() {
        val m = CurrencyConvert.convert(CurrencyQuery(1.0, "EUR", "JPY"), rates, now)!!
        assertEquals("160", m.formatted)
    }

    @Test fun staleRatesGiveNull() {
        val later = now + CurrencyConvert.MAX_AGE_MS + 1
        assertNull(CurrencyConvert.convert(CurrencyQuery(1.0, "EUR", "USD"), rates, later))
    }

    @Test fun missingRatesGiveNull() =
        assertNull(CurrencyConvert.convert(CurrencyQuery(1.0, "EUR", "USD"), null, now))

    @Test fun missingCurrencyGivesNull() =
        assertNull(CurrencyConvert.convert(CurrencyQuery(1.0, "EUR", "CHF"), rates, now))

    @Test fun sourceLabel() = assertEquals("ECB 18 Oct", CurrencyConvert.sourceLabel("2026-10-18"))

    @Test fun parserAcceptsGoodJson() {
        val r = RatesParser.parse("""{"base":"EUR","date":"2026-10-18","rates":{"USD":1.08}}""", 5L)
        assertNotNull(r)
        assertEquals(1.08, r!!.rates["USD"]!!, 0.0)
    }

    @Test fun parserRejectsMissingParts() {
        assertNull(RatesParser.parse("""{"date":"2026-10-18","rates":{"USD":1.08}}""", 5L))
        assertNull(RatesParser.parse("""{"base":"EUR","rates":{"USD":1.08}}""", 5L))
        assertNull(RatesParser.parse("""{"base":"EUR","date":"2026-10-18","rates":{}}""", 5L))
        assertNull(RatesParser.parse("not json", 5L))
    }
}
