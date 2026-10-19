package app.olauncher.helper

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Offline unit conversion for queries like "5 km in mi". Pure Kotlin, no Context. */
object UnitConvert {

    private enum class Kind { LENGTH, MASS, VOLUME, SPEED, TEMPERATURE }

    private class Unit(val label: String, val kind: Kind, val factor: Double = 1.0)

    // Factors convert to the base unit of each kind: m, g, ml, km/h.
    private val UNITS: Map<String, Unit> = buildMap {
        fun add(kind: Kind, label: String, factor: Double, vararg aliases: String) {
            val unit = Unit(label, kind, factor)
            put(label.lowercase(), unit)
            aliases.forEach { put(it, unit) }
        }
        add(Kind.LENGTH, "mm", 0.001)
        add(Kind.LENGTH, "cm", 0.01)
        add(Kind.LENGTH, "m", 1.0)
        add(Kind.LENGTH, "km", 1000.0)
        add(Kind.LENGTH, "in", 0.0254)
        add(Kind.LENGTH, "ft", 0.3048)
        add(Kind.LENGTH, "yd", 0.9144)
        add(Kind.LENGTH, "mi", 1609.344)
        add(Kind.MASS, "g", 1.0)
        add(Kind.MASS, "kg", 1000.0)
        add(Kind.MASS, "oz", 28.349523125)
        add(Kind.MASS, "lb", 453.59237)
        add(Kind.VOLUME, "ml", 1.0)
        add(Kind.VOLUME, "l", 1000.0)
        add(Kind.VOLUME, "cup", 236.5882365)
        add(Kind.VOLUME, "floz", 29.5735295625)
        add(Kind.VOLUME, "gal", 3785.411784)
        add(Kind.SPEED, "km/h", 1.0)
        add(Kind.SPEED, "mph", 1.609344)
        add(Kind.TEMPERATURE, "°C", 1.0, "c")
        add(Kind.TEMPERATURE, "°F", 1.0, "f")
    }

    private val REGEX = Regex(
        """^\s*(-?\d+(?:[.,]\d+)?)\s*(\S+?)\s+(?:in|to)\s+(\S+)\s*$""",
        RegexOption.IGNORE_CASE
    )

    /** Returns e.g. "3.107 mi", or null unless the whole query parses and both units share a kind. */
    fun evaluate(query: String): String? {
        val match = REGEX.matchEntire(query) ?: return null
        val value = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val from = UNITS[match.groupValues[2].lowercase()] ?: return null
        val to = UNITS[match.groupValues[3].lowercase()] ?: return null
        if (from.kind != to.kind) return null
        val result = if (from.kind == Kind.TEMPERATURE) {
            val celsius = if (from.label == "°F") (value - 32) * 5 / 9 else value
            if (to.label == "°F") celsius * 9 / 5 + 32 else celsius
        } else {
            value * from.factor / to.factor
        }
        if (result.isNaN() || result.isInfinite()) return null
        return "${format(result)} ${to.label}"
    }

    private fun format(value: Double): String {
        val rounded = BigDecimal(value).round(MathContext(4, RoundingMode.HALF_UP)).stripTrailingZeros()
        val text = rounded.toPlainString()
        return if (text == "-0") "0" else text
    }
}
