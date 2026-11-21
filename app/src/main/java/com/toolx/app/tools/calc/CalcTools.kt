package com.toolx.app.tools.calc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Exposure
import androidx.compose.material.icons.rounded.Functions
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Iso
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.toolx.app.tools.Tool
import com.toolx.app.tools.ToolCategory
import com.toolx.app.tools.ToolModule
import com.toolx.app.tools.ToolScreenFn
import com.toolx.app.ui.common.BigResult
import com.toolx.app.ui.common.DateField
import com.toolx.app.ui.common.Note
import com.toolx.app.ui.common.NumberField
import com.toolx.app.ui.common.OptionChips
import com.toolx.app.ui.common.ResultCard
import com.toolx.app.ui.common.SectionLabel
import com.toolx.app.ui.common.ToolScaffold
import com.toolx.app.ui.common.fmt
import com.toolx.app.ui.common.formatDate
import com.toolx.app.ui.common.parseNum
import java.math.BigDecimal
import java.math.BigInteger
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sqrt

object CalcTools : ToolModule {

    private fun t(id: String, title: String, desc: String, icon: ImageVector, vararg kw: String) =
        Tool(id, title, desc, icon, ToolCategory.CALC, available = true, keywords = kw.toList() + listOf("calculator", "math"))

    override val tools = listOf(
        t("calc_percent", "Percentage", "X% of Y, % change, add or remove %", Icons.Rounded.Percent, "percent", "increase", "decrease"),
        t("calc_average", "Average", "Mean, median, mode and more", Icons.Rounded.Functions, "mean", "median", "mode", "statistics"),
        t("calc_proportion", "Proportion", "Rule of three: a is to b as c is to ?", Icons.AutoMirrored.Rounded.CompareArrows, "ratio", "rule of three", "proportional"),
        t("calc_aspect", "Aspect ratio", "Simplify ratios, resize keeping shape", Icons.Rounded.AspectRatio, "16:9", "resolution", "ratio"),
        t("calc_fraction", "Fraction simplifier", "Reduce fractions, mixed numbers", Icons.Rounded.Iso, "simplify", "reduce", "fraction"),
        t("calc_decimal_fraction", "Decimal to fraction", "0.75 → 3/4", Icons.Rounded.Exposure, "fraction", "convert"),
        t("calc_prime", "Prime number", "Prime check and factorization", Icons.Rounded.Pin, "prime", "factor", "divisors"),
        t("calc_exponent", "Exponent", "Powers and roots", Icons.Rounded.Calculate, "power", "root", "square", "cube"),
        t("calc_factorial", "Factorial", "n! with exact digits", Icons.Rounded.Tag, "factorial", "permutation"),
        t("calc_age", "Age calculator", "Exact age and next birthday", Icons.Rounded.Cake, "birthday", "age", "born"),
        t("calc_time", "Time calculator", "Add durations or time between clocks", Icons.Rounded.HourglassBottom, "hours", "minutes", "duration", "clock"),
    )

    override val screens: Map<String, ToolScreenFn> = mapOf(
        "calc_percent" to { tool, back -> PercentScreen(tool, back) },
        "calc_average" to { tool, back -> AverageScreen(tool, back) },
        "calc_proportion" to { tool, back -> ProportionScreen(tool, back) },
        "calc_aspect" to { tool, back -> AspectScreen(tool, back) },
        "calc_fraction" to { tool, back -> FractionScreen(tool, back) },
        "calc_decimal_fraction" to { tool, back -> DecimalFractionScreen(tool, back) },
        "calc_prime" to { tool, back -> PrimeScreen(tool, back) },
        "calc_exponent" to { tool, back -> ExponentScreen(tool, back) },
        "calc_factorial" to { tool, back -> FactorialScreen(tool, back) },
        "calc_age" to { tool, back -> AgeScreen(tool, back) },
        "calc_time" to { tool, back -> TimeCalcScreen(tool, back) },
    )
}

internal fun gcd(a: Long, b: Long): Long = if (b == 0L) abs(a) else gcd(b, a % b)

// ---------- Percentage ----------

private enum class PercentMode(val label: String) { OF("X% of Y"), WHAT("X is what % of Y"), CHANGE("% change X → Y"), ADD("Y + X%"), SUB("Y − X%") }

@Composable
private fun PercentScreen(tool: Tool, onBack: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf(PercentMode.OF) }
    var a by rememberSaveable { mutableStateOf("") }
    var b by rememberSaveable { mutableStateOf("") }
    val x = parseNum(a)
    val y = parseNum(b)
    ToolScaffold(tool.title, onBack) {
        OptionChips(PercentMode.entries, mode, label = { it.label }) { mode = it }
        val (la, lb) = when (mode) {
            PercentMode.OF -> "Percent (X)" to "Of value (Y)"
            PercentMode.WHAT -> "Value (X)" to "Total (Y)"
            PercentMode.CHANGE -> "From (X)" to "To (Y)"
            PercentMode.ADD, PercentMode.SUB -> "Percent (X)" to "Value (Y)"
        }
        NumberField(a, { a = it }, la, suffix = if (mode == PercentMode.OF || mode == PercentMode.ADD || mode == PercentMode.SUB) "%" else null)
        NumberField(b, { b = it }, lb)
        if (x != null && y != null) {
            when (mode) {
                PercentMode.OF -> BigResult(fmt(x / 100 * y), "${fmt(x)}% of ${fmt(y)} =")
                PercentMode.WHAT -> BigResult(if (y == 0.0) "—" else "${fmt(x / y * 100)}%", "${fmt(x)} is this much of ${fmt(y)}")
                PercentMode.CHANGE -> {
                    val pct = if (x == 0.0) Double.NaN else (y - x) / abs(x) * 100
                    BigResult(if (pct.isNaN()) "—" else (if (pct > 0) "+" else "") + fmt(pct) + "%", if (pct >= 0) "Increase" else "Decrease")
                    ResultCard(listOf("Difference" to fmt(y - x)))
                }
                PercentMode.ADD -> BigResult(fmt(y * (1 + x / 100)), "${fmt(y)} + ${fmt(x)}% =")
                PercentMode.SUB -> BigResult(fmt(y * (1 - x / 100)), "${fmt(y)} − ${fmt(x)}% =")
            }
        }
    }
}

// ---------- Average ----------

internal data class AvgStats(
    val count: Int, val sum: Double, val mean: Double, val median: Double, val modes: List<Double>,
    val min: Double, val max: Double, val populationSd: Double, val sampleSd: Double, val geometricMean: Double?,
)

/** Descriptive statistics. [modes] is empty when no value repeats. */
internal fun averageStats(nums: List<Double>): AvgStats {
    val sorted = nums.sorted()
    val n = nums.size
    val mean = nums.average()
    val median = if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2
    val counts = nums.groupingBy { it }.eachCount()
    val maxCount = counts.values.max()
    val modes = if (maxCount == 1) emptyList() else counts.filterValues { it == maxCount }.keys.sorted()
    val squares = nums.sumOf { (it - mean).pow(2) }
    return AvgStats(
        count = n, sum = nums.sum(), mean = mean, median = median, modes = modes,
        min = sorted.first(), max = sorted.last(),
        populationSd = sqrt(squares / n), sampleSd = if (n > 1) sqrt(squares / (n - 1)) else Double.NaN,
        geometricMean = if (nums.all { it > 0 }) Math.exp(nums.sumOf { Math.log(it) } / n) else null,
    )
}

@Composable
private fun AverageScreen(tool: Tool, onBack: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val nums = text.split(Regex("[\\s,;]+")).mapNotNull { it.toDoubleOrNull() }
    ToolScaffold(tool.title, onBack) {
        OutlinedTextField(
            text, { text = it }, label = { Text("Numbers (separated by spaces, commas or lines)") },
            minLines = 3, modifier = Modifier.heightIn(min = 100.dp).then(Modifier),
        )
        if (nums.isNotEmpty()) {
            val s = averageStats(nums)
            BigResult(fmt(s.mean), "Average (mean) of ${s.count} numbers")
            ResultCard(
                listOf(
                    "Count" to s.count.toString(), "Sum" to fmt(s.sum), "Median" to fmt(s.median),
                    "Mode" to if (s.modes.isEmpty()) "none" else s.modes.joinToString(", ") { fmt(it) },
                    "Minimum" to fmt(s.min), "Maximum" to fmt(s.max), "Range" to fmt(s.max - s.min),
                    "Std. deviation (population)" to fmt(s.populationSd), "Std. deviation (sample)" to fmt(s.sampleSd),
                    "Geometric mean" to (s.geometricMean?.let { fmt(it) } ?: "—"),
                ),
            )
        }
    }
}

// ---------- Proportion ----------

@Composable
private fun ProportionScreen(tool: Tool, onBack: () -> Unit) {
    var a by rememberSaveable { mutableStateOf("") }
    var b by rememberSaveable { mutableStateOf("") }
    var c by rememberSaveable { mutableStateOf("") }
    var inverse by rememberSaveable { mutableStateOf(false) }
    ToolScaffold(tool.title, onBack) {
        OptionChips(listOf(false, true), inverse, label = { if (it) "Inverse (more → less)" else "Direct (more → more)" }) { inverse = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(a, { a = it }, "A", Modifier.weight(1f))
            NumberField(b, { b = it }, "is to B", Modifier.weight(1f))
        }
        NumberField(c, { c = it }, "as C is to…")
        val x = parseNum(a); val y = parseNum(b); val z = parseNum(c)
        if (x != null && y != null && z != null && x != 0.0 && z != 0.0) {
            val result = if (inverse) x * y / z else y * z / x
            BigResult(fmt(result), "X =")
            Note(if (inverse) "Inverse: A × B = C × X (e.g. more workers, fewer days)." else "Direct: A / B = C / X (e.g. more items, higher price).")
        }
    }
}

// ---------- Aspect ratio ----------

@Composable
private fun AspectScreen(tool: Tool, onBack: () -> Unit) {
    var w by rememberSaveable { mutableStateOf("1920") }
    var h by rememberSaveable { mutableStateOf("1080") }
    var newW by rememberSaveable { mutableStateOf("") }
    var newH by rememberSaveable { mutableStateOf("") }
    val width = parseNum(w); val height = parseNum(h)
    ToolScaffold(tool.title, onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(w, { w = it }, "Width", Modifier.weight(1f), allowNegative = false)
            NumberField(h, { h = it }, "Height", Modifier.weight(1f), allowNegative = false)
        }
        OptionChips(listOf("16:9", "4:3", "1:1", "21:9", "9:16", "3:2"), "", label = { it }) { r ->
            val (rw, rh) = r.split(":").map { it.toDouble() }
            val base = width ?: 1920.0
            w = fmt(base); h = fmt((base * rh / rw).roundToLong().toDouble())
        }
        if (width != null && height != null && width > 0 && height > 0) {
            val wi = width.roundToLong(); val hi = height.roundToLong()
            val g = gcd(wi, hi).coerceAtLeast(1)
            BigResult("${wi / g}:${hi / g}", "Aspect ratio")
            ResultCard(listOf("Decimal" to "${fmt(width / height)}:1", "Megapixels" to fmt(width * height / 1e6)))
            SectionLabel("Resize keeping the ratio")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(newW, { newW = it; newH = parseNum(it)?.let { v -> fmt(v * height / width) } ?: "" }, "New width", Modifier.weight(1f), allowNegative = false)
                NumberField(newH, { newH = it; newW = parseNum(it)?.let { v -> fmt(v * width / height) } ?: "" }, "New height", Modifier.weight(1f), allowNegative = false)
            }
        }
    }
}

// ---------- Fractions ----------

internal fun fractionRows(num: BigInteger, den: BigInteger): List<Pair<String, String>> {
    val g = num.gcd(den).takeIf { it.signum() != 0 } ?: BigInteger.ONE
    var n = num / g; var d = den / g
    if (d.signum() < 0) { n = n.negate(); d = d.negate() }
    val whole = n / d
    val rem = (n % d).abs()
    val mixed = if (rem.signum() == 0) whole.toString() else if (whole.signum() == 0) "$n/$d" else "$whole $rem/$d"
    val dec = BigDecimal(n).divide(BigDecimal(d), java.math.MathContext(15)).stripTrailingZeros().toPlainString()
    return listOf("Simplified" to "$n/$d", "Mixed number" to mixed, "Decimal" to dec, "Percent" to fmt(dec.toDouble() * 100) + "%")
}

@Composable
private fun FractionScreen(tool: Tool, onBack: () -> Unit) {
    var n by rememberSaveable { mutableStateOf("") }
    var d by rememberSaveable { mutableStateOf("") }
    ToolScaffold(tool.title, onBack) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(n, { n = it }, "Numerator", Modifier.weight(1f), integerOnly = true)
            NumberField(d, { d = it }, "Denominator", Modifier.weight(1f), integerOnly = true)
        }
        val num = n.toBigIntegerOrNull(); val den = d.toBigIntegerOrNull()
        when {
            num == null || den == null -> Unit
            den.signum() == 0 -> Note("The denominator can't be 0.", error = true)
            else -> {
                val rows = fractionRows(num, den)
                BigResult(rows[0].second, "$num/$den =")
                ResultCard(rows.drop(1))
            }
        }
    }
}

/** Best rational approximation with denominator ≤ [maxDen], via continued fractions. */
internal fun approximate(x: Double, maxDen: Long): Pair<Long, Long> {
    var h0 = 0L; var h1 = 1L; var k0 = 1L; var k1 = 0L
    var v = x
    repeat(64) {
        val a = floor(v).toLong()
        val h2 = a * h1 + h0; val k2 = a * k1 + k0
        if (k2 > maxDen) return h1 to k1
        h0 = h1; h1 = h2; k0 = k1; k1 = k2
        val frac = v - a
        if (abs(frac) < 1e-12 || abs(x - h1.toDouble() / k1) < 1e-12) return h1 to k1
        v = 1 / frac
    }
    return h1 to k1
}

@Composable
private fun DecimalFractionScreen(tool: Tool, onBack: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("0.75") }
    ToolScaffold(tool.title, onBack) {
        NumberField(text, { text = it }, "Decimal")
        val bd = text.trim().toBigDecimalOrNull()
        if (bd != null) {
            val scale = bd.scale().coerceAtLeast(0)
            val num = bd.movePointRight(scale).toBigInteger()
            val den = BigInteger.TEN.pow(scale)
            val rows = fractionRows(num, den)
            BigResult(rows[0].second, "Exact fraction")
            val (an, ad) = approximate(bd.toDouble(), 1000)
            ResultCard(listOf(rows[1], "Closest simple fraction (≤ 1000)" to "$an/$ad", rows[3]))
            Note("For repeating decimals like 0.333…, use the closest simple fraction.")
        }
    }
}

// ---------- Prime ----------

internal fun factorize(n0: Long): List<Pair<Long, Int>> {
    var n = n0
    val out = mutableListOf<Pair<Long, Int>>()
    var p = 2L
    while (p * p <= n) {
        var e = 0
        while (n % p == 0L) { n /= p; e++ }
        if (e > 0) out += p to e
        p += if (p == 2L) 1 else 2
    }
    if (n > 1) out += n to 1
    return out
}

internal fun isPrime(n: Long) = n >= 2 && BigInteger.valueOf(n).isProbablePrime(40)

@Composable
private fun PrimeScreen(tool: Tool, onBack: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("97") }
    ToolScaffold(tool.title, onBack) {
        NumberField(text, { text = it }, "Whole number", integerOnly = true, allowNegative = false)
        val n = text.toLongOrNull()
        when {
            n == null -> if (text.isNotBlank()) Note("Up to 9,223,372,036,854,775,807.", error = true)
            n < 2 -> BigResult("Not prime", "$n")
            else -> {
                val prime = isPrime(n)
                BigResult(if (prime) "Prime" else "Not prime", "$n is")
                val factors = if (n < 1_000_000_000_000_000L) remember(n) { factorize(n) } else null
                val next = generateSequence(n + 1) { it + 1 }.first { isPrime(it) }
                val prev = generateSequence(n - 1) { it - 1 }.takeWhile { it >= 2 }.firstOrNull { isPrime(it) }
                ResultCard(
                    listOfNotNull(
                        factors?.let { f -> "Prime factors" to f.joinToString(" × ") { (p, e) -> if (e == 1) "$p" else "$p^$e" } },
                        factors?.let { f -> "Number of divisors" to f.fold(1L) { acc, (_, e) -> acc * (e + 1) }.toString() },
                        "Next prime" to next.toString(),
                        prev?.let { "Previous prime" to it.toString() },
                    ),
                )
            }
        }
    }
}

// ---------- Exponent ----------

@Composable
private fun ExponentScreen(tool: Tool, onBack: () -> Unit) {
    var root by rememberSaveable { mutableStateOf(false) }
    var a by rememberSaveable { mutableStateOf("2") }
    var b by rememberSaveable { mutableStateOf("10") }
    ToolScaffold(tool.title, onBack) {
        OptionChips(listOf(false, true), root, label = { if (it) "Root  ⁿ√x" else "Power  xⁿ" }) { root = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(a, { a = it }, if (root) "Number (x)" else "Base (x)", Modifier.weight(1f))
            NumberField(b, { b = it }, if (root) "Root (n)" else "Exponent (n)", Modifier.weight(1f))
        }
        val x = parseNum(a); val n = parseNum(b)
        if (x != null && n != null) {
            if (root) {
                val r = if (x < 0 && n % 2 == 1.0) -abs(x).pow(1 / n) else x.pow(1 / n)
                BigResult(fmt(r), "${fmt(n)}√${fmt(x)} =")
            } else {
                BigResult(fmt(x.pow(n)), "${fmt(x)}^${fmt(n)} =")
                // Exact integer power when it's small enough to show.
                val bx = a.trim().toBigIntegerOrNull(); val bn = b.trim().toIntOrNull()
                if (bx != null && bn != null && bn in 0..5000 && bx.abs().bitLength().toLong() * bn < 200_000) {
                    val exact = bx.pow(bn).toString()
                    ResultCard(listOf("Digits" to exact.trimStart('-').length.toString()))
                    LongNumber(exact)
                }
            }
        }
    }
}

@Composable
private fun LongNumber(value: String) {
    SelectionContainer(Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState())) {
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
    }
}

// ---------- Factorial ----------

@Composable
private fun FactorialScreen(tool: Tool, onBack: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("20") }
    ToolScaffold(tool.title, onBack) {
        NumberField(text, { text = it }, "n (0 – 5000)", integerOnly = true, allowNegative = false)
        val n = text.toIntOrNull()
        if (n != null && n in 0..5000) {
            val value = remember(n) { (2..n).fold(BigInteger.ONE) { acc, i -> acc * BigInteger.valueOf(i.toLong()) }.toString() }
            BigResult(if (value.length <= 18) value else "${value[0]}.${value.substring(1, 7)}e${value.length - 1}", "$n! =")
            ResultCard(listOf("Digits" to value.length.toString(), "Trailing zeros" to (value.length - value.trimEnd('0').length).toString()))
            LongNumber(value)
        } else if (n != null) {
            Note("Pick a number up to 5000.", error = true)
        }
    }
}

// ---------- Age ----------

private val LocalDateSaver = Saver<LocalDate, String>({ it.toString() }, { LocalDate.parse(it) })

private fun dayName(d: DayOfWeek) = d.getDisplayName(TextStyle.FULL, Locale.getDefault())

@Composable
private fun AgeScreen(tool: Tool, onBack: () -> Unit) {
    var birth by rememberSaveable(stateSaver = LocalDateSaver) { mutableStateOf(LocalDate.now().minusYears(25)) }
    var on by rememberSaveable(stateSaver = LocalDateSaver) { mutableStateOf(LocalDate.now()) }
    ToolScaffold(tool.title, onBack) {
        DateField("Date of birth", birth, { birth = it })
        DateField("Age on", on, { on = it })
        if (!birth.isAfter(on)) {
            val p = Period.between(birth, on)
            BigResult("${p.years} years, ${p.months} months, ${p.days} days", "Age")
            var next = birth.withYear(on.year)
            if (next.isBefore(on)) next = next.plusYears(1)
            val daysToNext = ChronoUnit.DAYS.between(on, next)
            val totalDays = ChronoUnit.DAYS.between(birth, on)
            ResultCard(
                listOf(
                    "Total months" to ChronoUnit.MONTHS.between(birth, on).toString(),
                    "Total weeks" to (totalDays / 7).toString(),
                    "Total days" to "%,d".format(totalDays),
                    "Total hours (approx.)" to "%,d".format(totalDays * 24),
                    "Born on a" to dayName(birth.dayOfWeek),
                    "Next birthday" to if (daysToNext == 0L) "Today! 🎉" else "${formatDate(next)} (${dayName(next.dayOfWeek)}), in $daysToNext days",
                ),
            )
        } else {
            Note("The birth date is after the second date.", error = true)
        }
    }
}

// ---------- Time calculator ----------

private enum class TimeMode(val label: String) { ADD("Add / subtract durations"), BETWEEN("Time between two clocks") }

internal fun hms(totalSeconds: Long): String {
    val sign = if (totalSeconds < 0) "−" else ""
    val s = abs(totalSeconds)
    return "%s%d:%02d:%02d".format(sign, s / 3600, s / 60 % 60, s % 60)
}

@Composable
private fun DurationRow(label: String, h: String, m: String, s: String, onH: (String) -> Unit, onM: (String) -> Unit, onS: (String) -> Unit) {
    SectionLabel(label)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(h, onH, "Hours", Modifier.weight(1f), integerOnly = true, allowNegative = false)
        NumberField(m, onM, "Minutes", Modifier.weight(1f), integerOnly = true, allowNegative = false)
        NumberField(s, onS, "Seconds", Modifier.weight(1f), integerOnly = true, allowNegative = false)
    }
}

@Composable
private fun TimeCalcScreen(tool: Tool, onBack: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf(TimeMode.ADD) }
    var h1 by rememberSaveable { mutableStateOf("1") }; var m1 by rememberSaveable { mutableStateOf("30") }; var s1 by rememberSaveable { mutableStateOf("0") }
    var h2 by rememberSaveable { mutableStateOf("0") }; var m2 by rememberSaveable { mutableStateOf("45") }; var s2 by rememberSaveable { mutableStateOf("0") }
    var subtract by rememberSaveable { mutableStateOf(false) }
    var start by rememberSaveable { mutableStateOf("09:00") }
    var end by rememberSaveable { mutableStateOf("17:30") }
    fun secs(h: String, m: String, s: String) = (h.toLongOrNull() ?: 0) * 3600 + (m.toLongOrNull() ?: 0) * 60 + (s.toLongOrNull() ?: 0)

    ToolScaffold(tool.title, onBack) {
        OptionChips(TimeMode.entries, mode, label = { it.label }) { mode = it }
        when (mode) {
            TimeMode.ADD -> {
                DurationRow("First", h1, m1, s1, { h1 = it }, { m1 = it }, { s1 = it })
                OptionChips(listOf(false, true), subtract, label = { if (it) "− Subtract" else "+ Add" }) { subtract = it }
                DurationRow("Second", h2, m2, s2, { h2 = it }, { m2 = it }, { s2 = it })
                val total = secs(h1, m1, s1) + (if (subtract) -1 else 1) * secs(h2, m2, s2)
                BigResult(hms(total), "Result (h:mm:ss)")
                ResultCard(listOf("Total minutes" to fmt(total / 60.0), "Total hours" to fmt(total / 3600.0), "Total seconds" to total.toString()))
            }
            TimeMode.BETWEEN -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.toolx.app.ui.common.TextInput(start, { start = it }, "Start (HH:MM)", Modifier.weight(1f))
                    com.toolx.app.ui.common.TextInput(end, { end = it }, "End (HH:MM)", Modifier.weight(1f))
                }
                val a = runCatching { LocalTime.parse(start.trim().padStart(5, '0')) }.getOrNull()
                val b = runCatching { LocalTime.parse(end.trim().padStart(5, '0')) }.getOrNull()
                if (a != null && b != null) {
                    var diff = ChronoUnit.SECONDS.between(a, b)
                    val overnight = diff < 0
                    if (overnight) diff += 86400
                    BigResult(hms(diff), if (overnight) "Duration (crosses midnight)" else "Duration")
                    ResultCard(listOf("Total minutes" to fmt(diff / 60.0), "Total hours" to fmt(diff / 3600.0)))
                } else {
                    Note("Use 24-hour times like 09:00 and 17:30.", error = true)
                }
            }
        }
    }
}
