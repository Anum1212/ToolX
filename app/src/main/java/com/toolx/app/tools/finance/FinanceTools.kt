package com.toolx.app.tools.finance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Discount
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RequestQuote
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.toolx.app.ui.common.Note
import com.toolx.app.ui.common.NumberField
import com.toolx.app.ui.common.OptionChips
import com.toolx.app.ui.common.ResultCard
import com.toolx.app.ui.common.SectionLabel
import com.toolx.app.ui.common.ToggleChip
import com.toolx.app.ui.common.ToolScaffold
import com.toolx.app.ui.common.fmt
import com.toolx.app.ui.common.money
import com.toolx.app.ui.common.parseNum
import kotlin.math.ceil
import kotlin.math.pow

object FinanceTools : ToolModule {

    private fun t(id: String, title: String, desc: String, icon: ImageVector, vararg kw: String) =
        Tool(id, title, desc, icon, ToolCategory.FINANCE, available = true, keywords = kw.toList() + "money")

    override val tools = listOf(
        t("fin_loan", "Loan EMI", "Monthly installment, interest and schedule", Icons.Rounded.RequestQuote, "emi", "mortgage", "installment", "car loan", "interest"),
        t("fin_invest", "Investment", "Lump sum or monthly (SIP) growth", Icons.AutoMirrored.Rounded.TrendingUp, "sip", "compound", "savings", "returns", "interest"),
        t("fin_bill", "Bill splitter", "Split a bill with tip and tax", Icons.Rounded.Receipt, "tip", "split", "restaurant", "share"),
        t("fin_discount", "Discount", "Sale price after % off and tax", Icons.Rounded.Discount, "sale", "price", "off", "percent"),
    )

    override val screens: Map<String, ToolScreenFn> = mapOf(
        "fin_loan" to { tool, back -> LoanScreen(tool, back) },
        "fin_invest" to { tool, back -> InvestScreen(tool, back) },
        "fin_bill" to { tool, back -> BillScreen(tool, back) },
        "fin_discount" to { tool, back -> DiscountScreen(tool, back) },
    )
}

// ---------- Formulas (internal so they can be unit-tested) ----------

/** Monthly installment for [principal] at [annualRatePct]% over [months] (reducing balance). */
internal fun emi(principal: Double, annualRatePct: Double, months: Int): Double {
    val r = annualRatePct / 12 / 100
    return if (r == 0.0) principal / months else principal * r * (1 + r).pow(months) / ((1 + r).pow(months) - 1)
}

internal data class YearRow(val year: Int, val principal: Double, val interest: Double, val balance: Double)

/** Year-by-year split of payments into principal and interest. */
internal fun loanSchedule(principal: Double, annualRatePct: Double, months: Int): List<YearRow> {
    val r = annualRatePct / 12 / 100
    val payment = emi(principal, annualRatePct, months)
    var balance = principal
    return (1..ceil(months / 12.0).toInt()).map { year ->
        var p = 0.0; var i = 0.0
        repeat(minOf(12, months - (year - 1) * 12)) {
            val interest = balance * r
            i += interest; p += payment - interest; balance -= payment - interest
        }
        YearRow(year, p, i, balance.coerceAtLeast(0.0))
    }
}

/** Value after [months] of a lump sum plus monthly deposits made at the start of each month, compounded monthly. */
internal fun futureValue(lump: Double, monthly: Double, annualRatePct: Double, months: Int): Double {
    val i = annualRatePct / 12 / 100
    val lumpFv = lump * (1 + i).pow(months)
    val sipFv = if (i == 0.0) monthly * months else monthly * ((1 + i).pow(months) - 1) / i * (1 + i)
    return lumpFv + sipFv
}

// ---------- Loan EMI ----------

@Composable
private fun LoanScreen(tool: Tool, onBack: () -> Unit) {
    var amount by rememberSaveable { mutableStateOf("1000000") }
    var rate by rememberSaveable { mutableStateOf("12") }
    var term by rememberSaveable { mutableStateOf("5") }
    var inYears by rememberSaveable { mutableStateOf(true) }
    val p = parseNum(amount); val annual = parseNum(rate); val t = parseNum(term)

    ToolScaffold(tool.title, onBack) {
        NumberField(amount, { amount = it }, "Loan amount", allowNegative = false)
        NumberField(rate, { rate = it }, "Interest rate (yearly)", suffix = "%", allowNegative = false)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(term, { term = it }, "Term", Modifier.weight(1f), allowNegative = false)
            OptionChips(listOf(true, false), inYears, label = { if (it) "Years" else "Months" }) { inYears = it }
        }
        if (p != null && annual != null && t != null && p > 0 && t > 0) {
            val n = (if (inYears) t * 12 else t).toInt().coerceAtLeast(1)
            val payment = emi(p, annual, n)
            val total = payment * n
            BigResult(money(payment), "Monthly payment (EMI)")
            ResultCard(
                listOf(
                    "Total interest" to money(total - p),
                    "Total payment" to money(total),
                    "Interest share" to "${fmt((total - p) / total * 100, 1)}%",
                    "Number of payments" to n.toString(),
                ),
            )
            SectionLabel("Schedule by year")
            ResultCard(
                loanSchedule(p, annual, n).take(40).map { row ->
                    "Year ${row.year}" to "P ${money(row.principal)} · I ${money(row.interest)} · left ${money(row.balance)}"
                },
            )
            Note("P = principal repaid, I = interest paid in that year.")
        }
    }
}

// ---------- Investment ----------

@Composable
private fun InvestScreen(tool: Tool, onBack: () -> Unit) {
    var initial by rememberSaveable { mutableStateOf("100000") }
    var monthly by rememberSaveable { mutableStateOf("10000") }
    var rate by rememberSaveable { mutableStateOf("10") }
    var years by rememberSaveable { mutableStateOf("10") }
    var inflation by rememberSaveable { mutableStateOf("") }
    val p = parseNum(initial) ?: 0.0; val m = parseNum(monthly) ?: 0.0; val annual = parseNum(rate); val y = parseNum(years)

    ToolScaffold(tool.title, onBack) {
        NumberField(initial, { initial = it }, "Starting amount (lump sum)", allowNegative = false)
        NumberField(monthly, { monthly = it }, "Added every month", allowNegative = false)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(rate, { rate = it }, "Return per year", Modifier.weight(1f), suffix = "%")
            NumberField(years, { years = it }, "Years", Modifier.weight(1f), allowNegative = false)
        }
        NumberField(inflation, { inflation = it }, "Inflation (optional)", suffix = "%", allowNegative = false)
        if (annual != null && y != null && y > 0) {
            val n = (y * 12).toInt()
            fun fv(months: Int) = futureValue(p, m, annual, months)
            val value = fv(n)
            val invested = p + m * n
            BigResult(money(value), "Value after ${fmt(y)} years")
            val rows = mutableListOf(
                "Total invested" to money(invested),
                "Growth" to money(value - invested),
                "Growth multiple" to if (invested > 0) "${fmt(value / invested, 2)}×" else "—",
            )
            parseNum(inflation)?.let { inf -> rows += "In today's money" to money(value / (1 + inf / 100).pow(y)) }
            ResultCard(rows)
            SectionLabel("Growth by year")
            ResultCard((1..y.toInt().coerceAtMost(50)).map { yr -> "Year $yr" to money(fv(yr * 12)) })
            Note("Assumes monthly compounding and deposits at the start of each month. Real returns vary.")
        }
    }
}

// ---------- Bill splitter ----------

@Composable
private fun BillScreen(tool: Tool, onBack: () -> Unit) {
    var bill by rememberSaveable { mutableStateOf("") }
    var tip by rememberSaveable { mutableStateOf("10") }
    var tax by rememberSaveable { mutableStateOf("") }
    var people by rememberSaveable { mutableIntStateOf(2) }
    var roundUp by rememberSaveable { mutableStateOf(false) }
    val b = parseNum(bill)

    ToolScaffold(tool.title, onBack) {
        NumberField(bill, { bill = it }, "Bill amount", allowNegative = false)
        SectionLabel("Tip")
        OptionChips(listOf("0", "5", "10", "15", "18", "20"), tip, label = { "$it%" }) { tip = it }
        NumberField(tip, { tip = it }, "Tip", suffix = "%", allowNegative = false)
        NumberField(tax, { tax = it }, "Tax (optional, if not included)", suffix = "%", allowNegative = false)
        SectionLabel("People")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FilledTonalIconButton(onClick = { if (people > 1) people-- }) { Icon(Icons.Rounded.Remove, contentDescription = "Fewer people") }
            Text("$people", style = MaterialTheme.typography.headlineSmall)
            FilledTonalIconButton(onClick = { if (people < 100) people++ }) { Icon(Icons.Rounded.Add, contentDescription = "More people") }
        }
        ToggleChip("Round each share up", roundUp) { roundUp = it }
        if (b != null && b > 0) {
            val taxAmt = b * (parseNum(tax) ?: 0.0) / 100
            val tipAmt = b * (parseNum(tip) ?: 0.0) / 100
            val total = b + taxAmt + tipAmt
            val share = total / people
            val shown = if (roundUp) ceil(share) else share
            BigResult(money(shown), "Each person pays")
            ResultCard(
                listOfNotNull(
                    "Tip" to money(tipAmt),
                    taxAmt.takeIf { it > 0 }?.let { "Tax" to money(it) },
                    "Total" to money(total),
                    roundUp.takeIf { it }?.let { "Collected (rounded)" to money(shown * people) },
                ),
            )
        }
    }
}

// ---------- Discount ----------

@Composable
private fun DiscountScreen(tool: Tool, onBack: () -> Unit) {
    var price by rememberSaveable { mutableStateOf("") }
    var off by rememberSaveable { mutableStateOf("20") }
    var extra by rememberSaveable { mutableStateOf("") }
    var tax by rememberSaveable { mutableStateOf("") }
    val p = parseNum(price)

    ToolScaffold(tool.title, onBack) {
        NumberField(price, { price = it }, "Original price", allowNegative = false)
        OptionChips(listOf("10", "15", "20", "25", "30", "40", "50", "70"), off, label = { "$it%" }) { off = it }
        NumberField(off, { off = it }, "Discount", suffix = "%", allowNegative = false)
        NumberField(extra, { extra = it }, "Extra discount on top (optional)", suffix = "%", allowNegative = false)
        NumberField(tax, { tax = it }, "Sales tax (optional)", suffix = "%", allowNegative = false)
        if (p != null && p > 0) {
            val d1 = (parseNum(off) ?: 0.0).coerceIn(0.0, 100.0) / 100
            val d2 = (parseNum(extra) ?: 0.0).coerceIn(0.0, 100.0) / 100
            val afterDiscount = p * (1 - d1) * (1 - d2)
            val taxAmt = afterDiscount * (parseNum(tax) ?: 0.0) / 100
            BigResult(money(afterDiscount + taxAmt), "You pay")
            ResultCard(
                listOfNotNull(
                    "You save" to money(p - afterDiscount),
                    "Effective discount" to "${fmt((1 - (1 - d1) * (1 - d2)) * 100, 2)}%",
                    taxAmt.takeIf { it > 0 }?.let { "Tax" to money(it) },
                ),
            )
            if (d2 > 0) Note("Stacked discounts multiply: ${fmt(d1 * 100)}% then ${fmt(d2 * 100)}% is not ${fmt((d1 + d2) * 100)}%.")
        }
    }
}
