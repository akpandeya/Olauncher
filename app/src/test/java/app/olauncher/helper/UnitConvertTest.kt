package app.olauncher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitConvertTest {
    @Test fun kmToMiles() = assertEquals("3.107 mi", UnitConvert.evaluate("5 km in mi"))
    @Test fun celsiusToFahrenheit() = assertEquals("68 °F", UnitConvert.evaluate("20c to f"))
    @Test fun fahrenheitToCelsius() = assertEquals("0 °C", UnitConvert.evaluate("32 °F to °C"))
    @Test fun commaDecimal() = assertEquals("6.34 cup", UnitConvert.evaluate("1,5 l in cup"))
    @Test fun cmToFeet() = assertEquals("5.906 ft", UnitConvert.evaluate("180 cm in ft"))
    @Test fun speed() = assertEquals("62.14 mph", UnitConvert.evaluate("100 km/h to mph"))
    @Test fun mass() = assertEquals("2.205 lb", UnitConvert.evaluate("1 kg in lb"))
    @Test fun mismatchedKinds() = assertNull(UnitConvert.evaluate("5 km in kg"))
    @Test fun missingTarget() = assertNull(UnitConvert.evaluate("5 km"))
    @Test fun missingNumber() = assertNull(UnitConvert.evaluate("km in mi"))
    @Test fun trailingGarbage() = assertNull(UnitConvert.evaluate("5 km in mi please"))
}
