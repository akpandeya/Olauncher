package app.olauncher.helper

import org.json.JSONObject
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

data class CurrencyQuery(val amount: Double, val from: String, val to: String) {
    /** The amount as typed-style text, e.g. "20" or "1.5". */
    val amountText: String
        get() = BigDecimal.valueOf(amount).stripTrailingZeros().toPlainString()
}

data class Money(val amount: Double, val code: String) {
    /** 2 decimals, JPY 0. */
    val formatted: String
        get() = String.format(Locale.US, if (code == "JPY") "%.0f" else "%.2f", amount)
}

/** ECB reference rates against EUR, as published by Frankfurter. */
data class Rates(val date: String, val rates: Map<String, Double>, val fetchedAt: Long)

object RatesParser {
    /** Returns null unless the JSON has `base`, `date` and a non-empty `rates` object of positive numbers. */
    fun parse(json: String, fetchedAt: Long): Rates? {
        return try {
            val root = JSONObject(json)
            val base = root.optString("base", "")
            val date = root.optString("date", "")
            val ratesObj = root.optJSONObject("rates")
            if (base != "EUR" || date.isBlank() || ratesObj == null || ratesObj.length() == 0) return null
            val map = mutableMapOf<String, Double>()
            val keys = ratesObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = ratesObj.optDouble(key, Double.NaN)
                if (value.isNaN() || value.isInfinite() || value <= 0.0) return null
                map[key.uppercase(Locale.ROOT)] = value
            }
            Rates(date, map, fetchedAt)
        } catch (e: Exception) {
            null
        }
    }
}

/** Offline-friendly currency conversion via EUR cross-rates. Pure Kotlin, no Context. */
object CurrencyConvert {

    const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    /** ISO 4217 codes published by the ECB feed (EUR is the base, rate 1). */
    val CODES: Set<String> = setOf(
        "EUR", "USD", "JPY", "BGN", "CZK", "DKK", "GBP", "HUF", "PLN", "RON", "SEK", "CHF", "ISK", "NOK",
        "TRY", "AUD", "BRL", "CAD", "CNY", "HKD", "IDR", "ILS", "INR", "KRW", "MXN", "MYR", "NZD", "PHP",
        "SGD", "THB", "ZAR",
    )

    private val SYMBOLS = mapOf('€' to "EUR", '$' to "USD", '£' to "GBP", '¥' to "JPY", '₹' to "INR")

    private val REGEX = Regex(
        """^\s*(\d+(?:[.,]\d+)?)\s*([A-Za-z]{3}|[€$£¥₹])\s+(?:in|to)\s+([A-Za-z]{3}|[€$£¥₹])\s*$""",
        RegexOption.IGNORE_CASE
    )

    private fun code(token: String): String? {
        if (token.length == 1 && token[0] in SYMBOLS) return SYMBOLS[token[0]]
        val upper = token.uppercase(Locale.ROOT)
        return if (upper in CODES) upper else null
    }

    /** "20 eur in usd", "20€ to $". The whole query must parse, else null. */
    fun parse(query: String): CurrencyQuery? {
        val match = REGEX.matchEntire(query) ?: return null
        val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val from = code(match.groupValues[2]) ?: return null
        val to = code(match.groupValues[3]) ?: return null
        return CurrencyQuery(amount, from, to)
    }

    fun isFresh(rates: Rates, now: Long): Boolean = now - rates.fetchedAt in 0..MAX_AGE_MS

    /** Null when rates are missing, stale (over 7 days) or lack either currency. */
    fun convert(q: CurrencyQuery, rates: Rates?, now: Long = System.currentTimeMillis()): Money? {
        if (rates == null || !isFresh(rates, now)) return null
        val fromRate = if (q.from == "EUR") 1.0 else rates.rates[q.from] ?: return null
        val toRate = if (q.to == "EUR") 1.0 else rates.rates[q.to] ?: return null
        val result = q.amount / fromRate * toRate
        if (result.isNaN() || result.isInfinite()) return null
        return Money(result, q.to)
    }

    /** "ECB 18 Oct" from a yyyy-MM-dd rates date. */
    fun sourceLabel(date: String): String {
        val short = try {
            LocalDate.parse(date).format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH))
        } catch (e: Exception) {
            date
        }
        return "ECB $short"
    }
}
