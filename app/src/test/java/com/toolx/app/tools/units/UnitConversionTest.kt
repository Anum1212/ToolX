package com.toolx.app.tools.units

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

/** Reference values from NIST / SI definitions and US customary definitions. */
class UnitConversionTest {

    private fun convert(categoryId: String, value: Double, from: String, to: String, params: CssParams = CssParams()): Double {
        val units = UnitTools.categories.first { it.id == categoryId }.units(params)
        val f = units.first { it.name == from }
        val t = units.first { it.name == to }
        return t.fromBase(f.toBase(value))
    }

    private fun near(expected: Double, actual: Double, relTol: Double = 1e-9) {
        val tol = maxOf(abs(expected) * relTol, 1e-12)
        assertTrue("expected $expected but was $actual", abs(expected - actual) <= tol)
    }

    @Test fun length() {
        near(1.609344, convert("unit_length", 1.0, "Mile", "Kilometre"))
        near(2.54, convert("unit_length", 1.0, "Inch", "Centimetre"))
        near(0.3048, convert("unit_length", 1.0, "Foot", "Metre"))
        near(5280.0, convert("unit_length", 1.0, "Mile", "Foot"))
        near(109.36132983377, convert("unit_length", 100.0, "Metre", "Yard"))
        near(1.852, convert("unit_length", 1.0, "Nautical mile", "Kilometre"))
    }

    @Test fun temperature() {
        near(212.0, convert("unit_temperature", 100.0, "Celsius", "Fahrenheit"))
        near(0.0, convert("unit_temperature", 32.0, "Fahrenheit", "Celsius"))
        near(273.15, convert("unit_temperature", 0.0, "Celsius", "Kelvin"))
        near(-40.0, convert("unit_temperature", -40.0, "Celsius", "Fahrenheit"))
        near(37.0, convert("unit_temperature", 98.6, "Fahrenheit", "Celsius"))
        near(-459.67, convert("unit_temperature", 0.0, "Kelvin", "Fahrenheit"))
        near(0.0, convert("unit_temperature", 491.67, "Rankine", "Celsius"))
        near(671.67, convert("unit_temperature", 212.0, "Fahrenheit", "Rankine"))
    }

    @Test fun mass() {
        near(2.2046226218, convert("unit_mass", 1.0, "Kilogram", "Pound"), 1e-9)
        near(16.0, convert("unit_mass", 1.0, "Pound", "Ounce"))
        near(14.0, convert("unit_mass", 1.0, "Stone", "Pound"))
        near(2000.0, convert("unit_mass", 1.0, "US ton", "Pound"))
        near(2240.0, convert("unit_mass", 1.0, "UK ton", "Pound"))
        near(11.6638038, convert("unit_mass", 1.0, "Tola", "Gram"))
        near(5.0, convert("unit_mass", 1.0, "Gram", "Carat"))
    }

    @Test fun area() {
        near(2.4710538147, convert("unit_area", 1.0, "Hectare", "Acre"), 1e-9)
        near(43560.0, convert("unit_area", 1.0, "Acre", "Square foot"))
        near(640.0, convert("unit_area", 1.0, "Square mile", "Acre"))
        near(144.0, convert("unit_area", 1.0, "Square foot", "Square inch"))
        near(100.0, convert("unit_area", 1.0, "Square kilometre", "Hectare"))
    }

    @Test fun cooking() {
        near(16.0, convert("unit_cooking", 1.0, "Cup", "Tablespoon"))
        near(3.0, convert("unit_cooking", 1.0, "Tablespoon", "Teaspoon"))
        near(4.0, convert("unit_cooking", 1.0, "Gallon", "Quart"))
        near(128.0, convert("unit_cooking", 1.0, "Gallon", "Fluid ounce"))
        near(236.5882365, convert("unit_cooking", 1.0, "Cup", "Millilitre"))
        near(2.0, convert("unit_cooking", 1.0, "Pint", "Cup"))
        near(8.0, convert("unit_cooking", 1.0, "Gallon (UK)", "Pint (UK)"))
    }

    @Test fun time() {
        near(86400.0, convert("unit_time", 1.0, "Day", "Second"))
        near(168.0, convert("unit_time", 1.0, "Week", "Hour"))
        near(365.2425, convert("unit_time", 1.0, "Year", "Day"))
        near(12.0, convert("unit_time", 1.0, "Year", "Month"))
        near(1000.0, convert("unit_time", 1.0, "Second", "Millisecond"))
    }

    @Test fun energy() {
        near(4.184, convert("unit_energy", 1.0, "Kilocalorie", "Kilojoule"))
        near(3600.0, convert("unit_energy", 1.0, "Kilowatt-hour", "Kilojoule"))
        near(1055.05585262, convert("unit_energy", 1.0, "British thermal unit", "Joule"))
        near(860.4206501, convert("unit_energy", 1.0, "Kilowatt-hour", "Kilocalorie"), 1e-9)
    }

    @Test fun angle() {
        near(180.0, convert("unit_angle", PI, "Radian", "Degree"))
        near(400.0, convert("unit_angle", 1.0, "Turn", "Gradian"))
        near(60.0, convert("unit_angle", 1.0, "Degree", "Arcminute"))
        near(3600.0, convert("unit_angle", 1.0, "Degree", "Arcsecond"))
        near(1000.0, convert("unit_angle", 1.0, "Radian", "Milliradian"))
    }

    @Test fun speed() {
        near(62.13711922, convert("unit_speed", 100.0, "Kilometre per hour", "Mile per hour"), 1e-9)
        near(1.852, convert("unit_speed", 1.0, "Knot", "Kilometre per hour"))
        near(3.6, convert("unit_speed", 1.0, "Metre per second", "Kilometre per hour"))
        near(88.0, convert("unit_speed", 60.0, "Mile per hour", "Foot per second"))
    }

    @Test fun data() {
        near(1000.0, convert("unit_data", 1.0, "Gigabyte", "Megabyte"))
        near(1024.0, convert("unit_data", 1.0, "Gibibyte", "Mebibyte"))
        near(1.073741824, convert("unit_data", 1.0, "Gibibyte", "Gigabyte"))
        near(8.0, convert("unit_data", 1.0, "Byte", "Bit"))
        near(125.0, convert("unit_data", 1.0, "Gigabit", "Megabyte"))
    }

    @Test fun dataRate() {
        near(12.5, convert("unit_data_rate", 100.0, "Megabit per second", "Megabyte per second"))
        near(1000.0, convert("unit_data_rate", 1.0, "Gigabit per second", "Megabit per second"))
    }

    @Test fun css() {
        near(1.0, convert("unit_css", 16.0, "Pixel", "Root em"))
        near(96.0, convert("unit_css", 1.0, "Inch", "Pixel"))
        near(16.0, convert("unit_css", 12.0, "Point", "Pixel"))
        near(14.4, convert("unit_css", 1.0, "Viewport width", "Pixel"))
        near(9.0, convert("unit_css", 1.0, "Viewport height", "Pixel"))
        near(20.0, convert("unit_css", 1.0, "Root em", "Pixel", CssParams(basePx = 20.0)))
        near(37.795275591, convert("unit_css", 1.0, "Centimetre", "Pixel"), 1e-9)
    }

    @Test fun everyUnitRoundTrips() {
        UnitTools.categories.forEach { cat ->
            cat.units(CssParams()).forEach { u ->
                val back = u.fromBase(u.toBase(123.456))
                near(123.456, back, 1e-9)
            }
        }
    }

    @Test fun defaultsAreValidIndexes() {
        UnitTools.categories.forEach { cat ->
            val n = cat.units(CssParams()).size
            assertTrue("${cat.id} defaultFrom", cat.defaultFrom in 0 until n)
            assertTrue("${cat.id} defaultTo", cat.defaultTo in 0 until n)
            assertTrue("${cat.id} from != to", cat.defaultFrom != cat.defaultTo)
        }
    }

    @Test fun twelveCategories() = assertEquals(12, UnitTools.categories.size)
}
