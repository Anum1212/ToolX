package com.toolx.app.tools.units

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Architecture
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Css
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Scale
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.toolx.app.tools.Tool
import com.toolx.app.tools.ToolCategory
import com.toolx.app.tools.ToolModule
import com.toolx.app.tools.ToolScreenFn
import com.toolx.app.ui.common.BigResult
import com.toolx.app.ui.common.Dropdown
import com.toolx.app.ui.common.Note
import com.toolx.app.ui.common.NumberField
import com.toolx.app.ui.common.ResultCard
import com.toolx.app.ui.common.SectionLabel
import com.toolx.app.ui.common.ToolScaffold
import com.toolx.app.ui.common.fmt
import com.toolx.app.ui.common.parseNum
import kotlin.math.PI

/** A unit as a pair of functions to and from its category's base unit. */
class MeasureUnit(val name: String, val symbol: String, val toBase: (Double) -> Double, val fromBase: (Double) -> Double) {
    val label get() = "$name ($symbol)"
}

private fun u(name: String, symbol: String, factor: Double) = MeasureUnit(name, symbol, { it * factor }, { it / factor })

/** Extra inputs some categories need (CSS relative units). */
data class CssParams(val basePx: Double = 16.0, val viewportW: Double = 1440.0, val viewportH: Double = 900.0)

class UnitCategory(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
    val keywords: List<String>,
    val note: String? = null,
    val defaultFrom: Int = 0,
    val defaultTo: Int = 1,
    val units: (CssParams) -> List<MeasureUnit>,
)

object UnitTools : ToolModule {

    val categories = listOf(
        UnitCategory("unit_length", "Length", "m, km, ft, in, miles…", Icons.Rounded.Straighten, listOf("distance", "meter", "feet", "inch", "mile"), defaultFrom = 4, defaultTo = 7) {
            listOf(
                u("Nanometre", "nm", 1e-9), u("Micrometre", "µm", 1e-6), u("Millimetre", "mm", 1e-3), u("Centimetre", "cm", 1e-2),
                u("Metre", "m", 1.0), u("Kilometre", "km", 1e3), u("Inch", "in", 0.0254), u("Foot", "ft", 0.3048),
                u("Yard", "yd", 0.9144), u("Mile", "mi", 1609.344), u("Nautical mile", "nmi", 1852.0), u("Light-year", "ly", 9.4607304725808e15),
            )
        },
        UnitCategory("unit_area", "Area", "m², acres, hectares, ft²…", Icons.Rounded.CropSquare, listOf("square", "acre", "hectare", "land"), defaultFrom = 2, defaultTo = 6) {
            listOf(
                u("Square millimetre", "mm²", 1e-6), u("Square centimetre", "cm²", 1e-4), u("Square metre", "m²", 1.0), u("Square kilometre", "km²", 1e6),
                u("Hectare", "ha", 1e4), u("Acre", "ac", 4046.8564224), u("Square foot", "ft²", 0.09290304), u("Square inch", "in²", 0.00064516),
                u("Square yard", "yd²", 0.83612736), u("Square mile", "mi²", 2589988.110336),
            )
        },
        UnitCategory("unit_mass", "Mass", "kg, lb, oz, stone, tola…", Icons.Rounded.Scale, listOf("weight", "kilogram", "pound", "ounce", "gram"), defaultFrom = 2, defaultTo = 5) {
            listOf(
                u("Milligram", "mg", 1e-6), u("Gram", "g", 1e-3), u("Kilogram", "kg", 1.0), u("Tonne", "t", 1e3),
                u("Ounce", "oz", 0.028349523125), u("Pound", "lb", 0.45359237), u("Stone", "st", 6.35029318),
                u("US ton", "ton", 907.18474), u("UK ton", "long ton", 1016.0469088), u("Carat", "ct", 0.0002), u("Tola", "tola", 0.0116638038),
            )
        },
        UnitCategory("unit_temperature", "Temperature", "°C, °F, K", Icons.Rounded.Thermostat, listOf("celsius", "fahrenheit", "kelvin", "weather"), defaultFrom = 0, defaultTo = 1) {
            listOf(
                MeasureUnit("Celsius", "°C", { it }, { it }),
                MeasureUnit("Fahrenheit", "°F", { (it - 32) * 5 / 9 }, { it * 9 / 5 + 32 }),
                MeasureUnit("Kelvin", "K", { it - 273.15 }, { it + 273.15 }),
                MeasureUnit("Rankine", "°R", { (it - 491.67) * 5 / 9 }, { (it + 273.15) * 9 / 5 }),
            )
        },
        UnitCategory("unit_cooking", "Cooking", "Cups, spoons, ml, fl oz…", Icons.Rounded.Restaurant, listOf("cup", "tablespoon", "teaspoon", "volume", "recipe", "liquid"), note = "Cups and spoons are US customary unless marked metric or UK.", defaultFrom = 7, defaultTo = 0) {
            listOf(
                u("Millilitre", "ml", 1.0), u("Litre", "l", 1000.0), u("Teaspoon", "tsp", 4.92892159375), u("Tablespoon", "tbsp", 14.78676478125),
                u("Teaspoon (metric)", "tsp", 5.0), u("Tablespoon (metric)", "tbsp", 15.0), u("Fluid ounce", "fl oz", 29.5735295625),
                u("Cup", "cup", 236.5882365), u("Cup (metric)", "cup", 250.0), u("Pint", "pt", 473.176473), u("Pint (UK)", "pt", 568.26125),
                u("Quart", "qt", 946.352946), u("Gallon", "gal", 3785.411784), u("Gallon (UK)", "gal", 4546.09),
            )
        },
        UnitCategory("unit_time", "Time", "Seconds to years", Icons.Rounded.Schedule, listOf("hours", "minutes", "days", "duration"), note = "A month is an average Gregorian month (30.44 days), a year 365.2425 days.", defaultFrom = 5, defaultTo = 3) {
            val day = 86400.0
            listOf(
                u("Nanosecond", "ns", 1e-9), u("Microsecond", "µs", 1e-6), u("Millisecond", "ms", 1e-3), u("Second", "s", 1.0),
                u("Minute", "min", 60.0), u("Hour", "h", 3600.0), u("Day", "d", day), u("Week", "wk", 7 * day),
                u("Month", "mo", 30.436875 * day), u("Year", "yr", 365.2425 * day), u("Decade", "dec", 3652.425 * day), u("Century", "c", 36524.25 * day),
            )
        },
        UnitCategory("unit_energy", "Energy", "J, cal, kWh, BTU…", Icons.Rounded.Bolt, listOf("joule", "calorie", "kwh", "electricity", "food"), defaultFrom = 3, defaultTo = 1) {
            listOf(
                u("Joule", "J", 1.0), u("Kilojoule", "kJ", 1e3), u("Calorie", "cal", 4.184), u("Kilocalorie", "kcal", 4184.0),
                u("Watt-hour", "Wh", 3600.0), u("Kilowatt-hour", "kWh", 3.6e6), u("Electronvolt", "eV", 1.602176634e-19),
                u("British thermal unit", "BTU", 1055.05585262), u("Foot-pound", "ft·lbf", 1.3558179483),
            )
        },
        UnitCategory("unit_angle", "Angle", "Degrees, radians, gradians…", Icons.Rounded.Architecture, listOf("degree", "radian", "rotation"), defaultFrom = 0, defaultTo = 1) {
            listOf(
                u("Degree", "°", 1.0), u("Radian", "rad", 180 / PI), u("Gradian", "gon", 0.9), u("Milliradian", "mrad", 0.18 / PI),
                u("Arcminute", "′", 1.0 / 60), u("Arcsecond", "″", 1.0 / 3600), u("Turn", "tr", 360.0),
            )
        },
        UnitCategory("unit_speed", "Speed", "km/h, mph, knots, m/s…", Icons.Rounded.Speed, listOf("velocity", "kmh", "mph", "knot"), note = "Mach uses the speed of sound in air at 20 °C (343 m/s).", defaultFrom = 1, defaultTo = 2) {
            listOf(
                u("Metre per second", "m/s", 1.0), u("Kilometre per hour", "km/h", 1 / 3.6), u("Mile per hour", "mph", 0.44704),
                u("Knot", "kn", 1852.0 / 3600), u("Foot per second", "ft/s", 0.3048), u("Mach", "M", 343.0), u("Speed of light", "c", 299792458.0),
            )
        },
        UnitCategory("unit_data", "Data", "Bytes, KB, MB, GB, GiB…", Icons.Rounded.Storage, listOf("storage", "byte", "megabyte", "gigabyte", "file size"), note = "KB/MB/GB are decimal (1000); KiB/MiB/GiB are binary (1024).", defaultFrom = 4, defaultTo = 3) {
            listOf(
                u("Bit", "b", 1.0), u("Byte", "B", 8.0), u("Kilobyte", "KB", 8e3), u("Megabyte", "MB", 8e6), u("Gigabyte", "GB", 8e9),
                u("Terabyte", "TB", 8e12), u("Petabyte", "PB", 8e15), u("Kibibyte", "KiB", 8.0 * 1024), u("Mebibyte", "MiB", 8.0 * 1024 * 1024),
                u("Gibibyte", "GiB", 8.0 * 1024 * 1024 * 1024), u("Tebibyte", "TiB", 8.0 * 1024 * 1024 * 1024 * 1024),
                u("Kilobit", "Kb", 1e3), u("Megabit", "Mb", 1e6), u("Gigabit", "Gb", 1e9),
            )
        },
        UnitCategory("unit_data_rate", "Data transfer", "Mbps, MB/s, Gbps…", Icons.Rounded.NetworkCheck, listOf("bandwidth", "internet", "speed", "mbps", "download"), note = "Internet speeds are usually quoted in megabits (Mbps); downloads show megabytes (MB/s).", defaultFrom = 2, defaultTo = 6) {
            listOf(
                u("Bit per second", "bps", 1.0), u("Kilobit per second", "Kbps", 1e3), u("Megabit per second", "Mbps", 1e6), u("Gigabit per second", "Gbps", 1e9),
                u("Byte per second", "B/s", 8.0), u("Kilobyte per second", "KB/s", 8e3), u("Megabyte per second", "MB/s", 8e6), u("Gigabyte per second", "GB/s", 8e9),
                u("Kibibyte per second", "KiB/s", 8.0 * 1024), u("Mebibyte per second", "MiB/s", 8.0 * 1024 * 1024),
            )
        },
        UnitCategory("unit_css", "CSS units", "px, rem, em, pt, vw, vh…", Icons.Rounded.Css, listOf("web", "pixel", "rem", "em", "font"), note = "em/rem/% use the base font size; vw/vh use the viewport size below. 1in = 96px, as in CSS.", defaultFrom = 0, defaultTo = 1) { p ->
            listOf(
                u("Pixel", "px", 1.0), u("Root em", "rem", p.basePx), u("Em", "em", p.basePx), u("Percent of font", "%", p.basePx / 100),
                u("Point", "pt", 96.0 / 72), u("Pica", "pc", 16.0), u("Inch", "in", 96.0), u("Centimetre", "cm", 96 / 2.54),
                u("Millimetre", "mm", 96 / 25.4), u("Viewport width", "vw", p.viewportW / 100), u("Viewport height", "vh", p.viewportH / 100),
            )
        },
    )

    override val tools = categories.map {
        Tool(it.id, it.title, it.description, it.icon, ToolCategory.UNITS, available = true, keywords = it.keywords + listOf("convert", "unit", "converter"))
    }

    override val screens: Map<String, ToolScreenFn> = categories.associate { cat ->
        val screen: ToolScreenFn = { tool, back -> UnitConverterScreen(tool, back, cat) }
        cat.id to screen
    }
}

@Composable
private fun UnitConverterScreen(tool: Tool, onBack: () -> Unit, cat: UnitCategory) {
    var value by rememberSaveable { mutableStateOf("1") }
    var from by rememberSaveable { mutableIntStateOf(cat.defaultFrom) }
    var to by rememberSaveable { mutableIntStateOf(cat.defaultTo) }
    var base by rememberSaveable { mutableStateOf("16") }
    var vw by rememberSaveable { mutableStateOf("1440") }
    var vh by rememberSaveable { mutableStateOf("900") }
    val isCss = cat.id == "unit_css"
    val params = CssParams(parseNum(base) ?: 16.0, parseNum(vw) ?: 1440.0, parseNum(vh) ?: 900.0)
    val units = cat.units(params)
    val x = parseNum(value)
    val inBase = x?.let { units[from].toBase(it) }

    ToolScaffold(title = tool.title, onBack = onBack) {
        NumberField(value, { value = it }, "Value", suffix = units[from].symbol)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dropdown("From", units.indices.toList(), from, { units[it].label }, { from = it }, Modifier.weight(1f))
            FilledTonalIconButton(onClick = { val t = from; from = to; to = t }) {
                Icon(Icons.Rounded.SwapVert, contentDescription = "Swap units")
            }
            Dropdown("To", units.indices.toList(), to, { units[it].label }, { to = it }, Modifier.weight(1f))
        }
        if (inBase != null) {
            BigResult("${fmt(units[to].fromBase(inBase))} ${units[to].symbol}", caption = "${fmt(x)} ${units[from].symbol} =")
            SectionLabel("All units")
            ResultCard(units.map { it.label to fmt(it.fromBase(inBase)) })
            Note("Tap a row to copy its value.")
        } else if (value.isNotBlank()) {
            Note("Enter a number.", error = true)
        }
        if (isCss) {
            SectionLabel("Context")
            NumberField(base, { base = it }, "Base font size", suffix = "px", allowNegative = false)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(vw, { vw = it }, "Viewport width", Modifier.weight(1f), suffix = "px", allowNegative = false)
                NumberField(vh, { vh = it }, "Viewport height", Modifier.weight(1f), suffix = "px", allowNegative = false)
            }
        }
        cat.note?.let { Note(it) }
    }
}
