package com.toolx.app.tools.math

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Iso
import androidx.compose.material.icons.rounded.Add as PlusIcon
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toolx.app.tools.Tool
import com.toolx.app.tools.ToolCategory
import com.toolx.app.tools.ToolModule
import com.toolx.app.tools.ToolScreenFn
import com.toolx.app.ui.common.BigResult
import com.toolx.app.ui.common.Note
import com.toolx.app.ui.common.NumberField
import com.toolx.app.ui.common.OptionChips
import com.toolx.app.ui.common.ResultCard
import com.toolx.app.ui.common.SectionLabel
import com.toolx.app.ui.common.TextInput
import com.toolx.app.ui.common.ToolScaffold
import com.toolx.app.ui.theme.LocalAppTheme
import com.toolx.app.ui.theme.readable

/** Step-by-step school arithmetic, listed under Calculate. */
object MathTools : ToolModule {
    private fun t(id: String, title: String, desc: String, icon: androidx.compose.ui.graphics.vector.ImageVector, vararg kw: String) =
        Tool(id, title, desc, icon, ToolCategory.CALC, available = true, keywords = kw.toList() + listOf("steps", "math", "solver", "working", "homework"))

    override val tools = listOf(
        t("math_add", "Addition with steps", "Column addition with carries", Icons.Rounded.PlusIcon, "add", "sum", "plus", "carry", "column"),
        t("math_subtract", "Subtraction with steps", "Column subtraction with borrowing", Icons.Rounded.Remove, "subtract", "minus", "borrow", "difference", "column"),
        t("math_multiply", "Long multiplication", "Partial products and carries", Icons.Rounded.Clear, "multiply", "times", "product", "carry"),
        t("math_divide", "Long division", "Quotient, remainder and decimals", Icons.Rounded.Percent, "divide", "division", "quotient", "remainder", "bus stop"),
        t("math_fractions", "Fractions with steps", "Add, subtract, multiply, divide fractions", Icons.Rounded.Iso, "fraction", "mixed number", "lcd", "reciprocal", "whole"),
    )

    override val screens: Map<String, ToolScreenFn> = mapOf(
        "math_add" to { tool, back -> AddScreen(tool, back) },
        "math_subtract" to { tool, back -> SubtractScreen(tool, back) },
        "math_multiply" to { tool, back -> MultiplyScreen(tool, back) },
        "math_divide" to { tool, back -> DivideScreen(tool, back) },
        "math_fractions" to { tool, back -> FractionStepsScreen(tool, back) },
    )
}

private const val MAX_DIGITS = 30

// ---------- worksheet drawing ----------

private val CELL_W: Dp = 30.dp
private val BIG: TextUnit = 28.sp
private val SMALL: TextUnit = 14.sp

/** Colours for carry groups etc., taken from the theme so they stay readable. */
@Composable
private fun groupColor(group: Int): Color {
    val t = LocalAppTheme.current
    val bg = MaterialTheme.colorScheme.surfaceContainerLow
    val c = when (group % 4) {
        1 -> t.accent(ToolCategory.VIDEO)
        2 -> t.accent(ToolCategory.TEXT)
        3 -> t.accent(ToolCategory.IMAGE)
        else -> t.accent(ToolCategory.AUDIO)
    }
    return readable(c, bg, 3f)
}

@Composable
private fun WorksheetView(ws: Worksheet) {
    val prefixW = (ws.lines.maxOfOrNull { it.prefix.length } ?: 0).let { if (it == 0) 0.dp else (it * 15 + 14).dp }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(16.dp), contentAlignment = Alignment.Center) {
            Column {
                ws.lines.forEach { line ->
                    val h = if (line.small) 20.dp else 40.dp
                    if (line.ruleAbove) {
                        val from = line.ruleFrom ?: 0
                        val to = line.ruleTo ?: (ws.width - 1)
                        Row {
                            Spacer(Modifier.width(prefixW + CELL_W * from))
                            Box(Modifier.width(CELL_W * (to - from + 1)).height(2.dp).background(onSurface))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(prefixW).height(h), contentAlignment = Alignment.CenterEnd) {
                            if (line.prefix.isNotEmpty()) {
                                Text(
                                    line.prefix,
                                    fontSize = if (line.small) SMALL else BIG,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (line.small) groupColor(line.prefixGroup) else onSurface,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                            }
                        }
                        if (line.bracket) Box(Modifier.width(2.dp).height(h).background(onSurface))
                        for (c in 0 until ws.width) {
                            val cell = line.cells[c]
                            Box(Modifier.width(CELL_W).height(h), contentAlignment = Alignment.Center) {
                                if (cell != null) {
                                    val color = when (cell.mark) {
                                        Mark.CARRY -> groupColor(cell.group)
                                        Mark.BORROWED -> groupColor(1)
                                        Mark.STRUCK, Mark.PLACEHOLDER -> muted
                                        Mark.BROUGHT_DOWN -> groupColor(2)
                                        Mark.ANSWER -> MaterialTheme.colorScheme.primary
                                        Mark.NORMAL -> if (cell.group > 0) groupColor(cell.group) else onSurface
                                    }
                                    Text(
                                        cell.text,
                                        fontSize = if (line.small) SMALL else BIG,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (cell.mark == Mark.ANSWER || line.small) FontWeight.Bold else FontWeight.Medium,
                                        textDecoration = if (cell.mark == Mark.STRUCK) TextDecoration.LineThrough else null,
                                        color = color,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                        line.note?.let {
                            Text(it, style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend(vararg items: Pair<Color, String>) {
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { (c, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(c))
                Text(" $label", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun StepsList(steps: List<Step>) {
    SectionLabel("Steps")
    steps.forEachIndexed { i, s ->
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp)) {
                Box(
                    Modifier.size(26.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Text("${i + 1}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
                Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(s.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    s.details.forEach { d ->
                        Text("• $d", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SolutionView(solution: Solution?, error: String?, legend: @Composable () -> Unit = {}) {
    when {
        error != null -> Note(error, error = true)
        solution != null -> {
            BigResult(solution.answer, "Answer")
            WorksheetView(solution.worksheet)
            legend()
            if (solution.extra.isNotEmpty()) ResultCard(solution.extra)
            StepsList(solution.steps)
        }
    }
}

/** Validation shared by the whole-number solvers. */
private fun checkWhole(vararg values: String): String? = when {
    values.any { it.isBlank() } -> ""
    values.any { StepMath.clean(it) == null } -> "Use whole numbers (0, 1, 2…). For fractions use Fractions with steps."
    values.any { StepMath.clean(it)!!.length > MAX_DIGITS } -> "Up to $MAX_DIGITS digits, so the working stays readable."
    else -> null
}

private fun digitsOnly(s: String) = s.filter { it.isDigit() }.take(MAX_DIGITS)

// ---------- screens ----------

@Composable
private fun AddScreen(tool: Tool, onBack: () -> Unit) {
    var count by rememberSaveable { mutableIntStateOf(2) }
    val values = remember { mutableStateMapOf0() }
    ToolScaffold(tool.title, onBack) {
        for (i in 0 until count) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumberField(values[i] ?: "", { values[i] = digitsOnly(it) }, "Number ${i + 1}", Modifier.weight(1f), integerOnly = true, allowNegative = false)
                if (count > 2) IconButton(onClick = { for (k in i until count - 1) values[k] = values[k + 1] ?: ""; values.remove(count - 1); count-- }) {
                    Icon(Icons.Rounded.Close, "Remove number ${i + 1}")
                }
            }
        }
        if (count < 6) OutlinedButton(onClick = { count++ }) { Icon(Icons.Rounded.Add, null); Text(" Add another number") }
        val list = (0 until count).map { values[it] ?: "" }
        val err = checkWhole(*list.toTypedArray())
        SolutionView(if (err == null) StepMath.add(list) else null, err?.ifEmpty { null }) {
            Legend(groupColor(0) to "carried digit", MaterialTheme.colorScheme.primary to "answer")
        }
    }
}

@Composable
private fun SubtractScreen(tool: Tool, onBack: () -> Unit) {
    var a by rememberSaveable { mutableStateOf("") }
    var b by rememberSaveable { mutableStateOf("") }
    ToolScaffold(tool.title, onBack) {
        NumberField(a, { a = digitsOnly(it) }, "Top number", integerOnly = true, allowNegative = false)
        NumberField(b, { b = digitsOnly(it) }, "Take away", integerOnly = true, allowNegative = false)
        val err = checkWhole(a, b)
        SolutionView(if (err == null) StepMath.subtract(a, b) else null, err?.ifEmpty { null }) {
            Legend(groupColor(1) to "new value after borrowing", MaterialTheme.colorScheme.onSurfaceVariant to "crossed-out digit", MaterialTheme.colorScheme.primary to "answer")
        }
    }
}

@Composable
private fun MultiplyScreen(tool: Tool, onBack: () -> Unit) {
    var a by rememberSaveable { mutableStateOf("") }
    var b by rememberSaveable { mutableStateOf("") }
    ToolScaffold(tool.title, onBack) {
        NumberField(a, { a = digitsOnly(it) }, "Number", integerOnly = true, allowNegative = false)
        NumberField(b, { b = digitsOnly(it) }, "Multiply by", integerOnly = true, allowNegative = false)
        val err = checkWhole(a, b)
        // Put the longer number on top, as on paper.
        val (top, bottom) = if (b.length > a.length) b to a else a to b
        val s = if (err == null) StepMath.multiply(top, bottom) else null
        SolutionView(s, err?.ifEmpty { null }) {
            Note("Small numbers above are carries; each multiplier digit's carries and row share a colour. Grey zeros are placeholders.")
        }
    }
}

@Composable
private fun DivideScreen(tool: Tool, onBack: () -> Unit) {
    var a by rememberSaveable { mutableStateOf("") }
    var b by rememberSaveable { mutableStateOf("") }
    var decimals by rememberSaveable { mutableIntStateOf(0) }
    ToolScaffold(tool.title, onBack) {
        NumberField(a, { a = digitsOnly(it).take(18) }, "Number to divide (dividend)", integerOnly = true, allowNegative = false)
        NumberField(b, { b = digitsOnly(it).take(9) }, "Divide by (divisor)", integerOnly = true, allowNegative = false)
        SectionLabel("Decimal places")
        OptionChips(listOf(0, 1, 2, 3, 4), decimals, label = { if (it == 0) "Remainder" else "$it" }) { decimals = it }
        val err = checkWhole(a, b) ?: if (StepMath.clean(b) == "0") "You can't divide by zero." else null
        SolutionView(if (err == null) StepMath.divide(a, b, decimals) else null, err?.ifEmpty { null }) {
            Legend(groupColor(2) to "digit brought down", MaterialTheme.colorScheme.primary to "quotient")
        }
    }
}

// ---------- fractions ----------

@Composable
private fun FractionTerm(term: Term, big: Boolean = true) {
    val numColor = groupColor(2)
    val denColor = groupColor(1)
    val size = if (big) 24.sp else 18.sp
    when (term) {
        is Term.Text -> Text(term.text, fontSize = size, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 6.dp))
        is Term.Frac -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 4.dp).width(androidx.compose.foundation.layout.IntrinsicSize.Max)) {
            Text(term.num, fontSize = size, color = numColor, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth())
            Text(term.den, fontSize = size, color = denColor, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        }
        is Term.Mixed -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text(term.whole, fontSize = size * 1.2f, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
            FractionTerm(Term.Frac(term.num, term.den), big)
        }
    }
}

/** One equation line, vertically centred so "=" and "×" sit level with the fraction bars. */
@Composable
private fun ChainView(chain: Chain) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        chain.forEach { FractionTerm(it) }
    }
}

@Composable
private fun FractionStepsScreen(tool: Tool, onBack: () -> Unit) {
    var a by rememberSaveable { mutableStateOf("") }
    var b by rememberSaveable { mutableStateOf("") }
    var op by rememberSaveable { mutableStateOf(FracOp.ADD) }
    ToolScaffold(tool.title, onBack) {
        TextInput(a, { a = it.take(30) }, "First number, e.g. 3/4, 2 1/3 or 5")
        OptionChips(FracOp.entries, op, label = { "${it.symbol}  ${it.label}" }) { op = it }
        TextInput(b, { b = it.take(30) }, "Second number")
        Note("Write a fraction as 3/4, a mixed number as 2 1/3 (space between), or a whole number like 5.")
        val pa = FractionSteps.parse(a)
        val pb = FractionSteps.parse(b)
        when {
            a.isBlank() || b.isBlank() -> Unit
            pa == null -> Note("First number: use forms like 3/4, 2 1/3 or 5 (the bottom can't be 0).", error = true)
            pb == null -> Note("Second number: use forms like 3/4, 2 1/3 or 5 (the bottom can't be 0).", error = true)
            else -> {
                val s = FractionSteps.solve(pa, op, pb)
                if (s == null) {
                    Note("You can't divide by zero.", error = true)
                } else {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Answer", style = MaterialTheme.typography.labelMedium)
                            ChainView(s.answer)
                        }
                    }
                    ResultCard(s.extra)
                    SectionLabel("Steps")
                    s.steps.forEachIndexed { i, step ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("${i + 1}. ${step.title}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                step.chains.forEach { chain ->
                                    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) { ChainView(chain) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Map for the dynamic list of addends (index → text). */
private fun mutableStateMapOf0() = androidx.compose.runtime.mutableStateMapOf<Int, String>()
