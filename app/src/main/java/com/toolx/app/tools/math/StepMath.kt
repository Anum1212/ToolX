package com.toolx.app.tools.math

import java.math.BigInteger

/**
 * Pen-and-paper arithmetic with every step recorded, as a [Worksheet] (the written layout) plus
 * explained [Step]s. Pure Kotlin, so it is unit-tested. Whole numbers only, as taught in school
 * column methods; inputs are digit strings so they can be long.
 */

/** How a cell is drawn. */
enum class Mark {
    NORMAL,
    /** Small digit carried into this column. */
    CARRY,
    /** A digit crossed out because it was borrowed from. */
    STRUCK,
    /** Small new value above a struck digit after borrowing. */
    BORROWED,
    /** Placeholder zero in a partial product. */
    PLACEHOLDER,
    /** A digit brought down in long division. */
    BROUGHT_DOWN,
    /** The final answer. */
    ANSWER,
}

/** One cell; [group] picks a colour so related carries/rows match (e.g. "× 4" carries). */
data class Cell(val text: String, val mark: Mark = Mark.NORMAL, val group: Int = 0)

/**
 * One written line. [cells] are keyed by column (0 = leftmost). [prefix] sits left of the
 * digits (an operator, or the divisor in long division). [ruleAbove] draws a line over
 * columns [ruleFrom]..[ruleTo] (null = whole width). [note] is a margin comment.
 */
data class Line(
    val cells: Map<Int, Cell>,
    val prefix: String = "",
    val small: Boolean = false,
    val ruleAbove: Boolean = false,
    val ruleFrom: Int? = null,
    val ruleTo: Int? = null,
    val bracket: Boolean = false,
    val note: String? = null,
    val prefixGroup: Int = 0,
)

data class Worksheet(val width: Int, val lines: List<Line>)

/** An explained step; [details] are its sub-steps (e.g. one per column). */
data class Step(val title: String, val details: List<String> = emptyList())

data class Solution(val worksheet: Worksheet, val steps: List<Step>, val answer: String, val extra: List<Pair<String, String>> = emptyList())

object StepMath {

    private val placeNames = listOf("ones", "tens", "hundreds", "thousands", "ten-thousands", "hundred-thousands", "millions", "ten-millions", "hundred-millions", "billions")

    /** Column name counted from the right: 0 → "ones", 1 → "tens"… */
    fun place(i: Int): String = placeNames.getOrNull(i) ?: "column ${i + 1}"

    /** Digits only, leading zeros removed ("007" → "7"); null if not a non-negative whole number. */
    fun clean(s: String): String? {
        val t = s.trim().replace(",", "").replace(" ", "")
        if (t.isEmpty() || t.any { !it.isDigit() }) return null
        return t.trimStart('0').ifEmpty { "0" }
    }

    /** Right-aligned row: digit i from the right goes to column width-1-i. */
    private fun rightAligned(digits: String, width: Int, mark: Mark = Mark.NORMAL, group: Int = 0, shift: Int = 0): MutableMap<Int, Cell> {
        val m = mutableMapOf<Int, Cell>()
        digits.reversed().forEachIndexed { i, ch -> m[width - 1 - i - shift] = Cell(ch.toString(), mark, group) }
        return m
    }

    // ---------- Addition ----------

    fun add(numbers: List<String>): Solution? {
        val nums = numbers.map { clean(it) ?: return null }
        if (nums.size < 2) return null
        val maxLen = nums.maxOf { it.length }
        val width = maxLen + 1
        val carries = mutableMapOf<Int, Cell>()
        val result = StringBuilder()
        val details = mutableListOf<String>()
        var carry = 0
        for (i in 0 until maxLen) {
            val digits = nums.map { n -> if (i < n.length) n[n.length - 1 - i] - '0' else null }
            val present = digits.filterNotNull()
            val sum = present.sum() + carry
            val expr = present.joinToString(" + ") + if (carry > 0) " + $carry (carried)" else ""
            val digit = sum % 10
            val newCarry = sum / 10
            details += if (newCarry > 0) {
                "${place(i).cap()}: $expr = $sum → write $digit, carry $newCarry to the ${place(i + 1)}"
            } else {
                "${place(i).cap()}: $expr = $sum → write $digit"
            }
            result.insert(0, digit)
            carry = newCarry
            if (carry > 0) carries[width - 2 - i] = Cell(carry.toString(), Mark.CARRY)
        }
        if (carry > 0) {
            result.insert(0, carry)
            details += "Nothing left to add, so the carried $carry goes at the front"
        }
        val answer = result.toString().trimStart('0').ifEmpty { "0" }
        val lines = buildList {
            if (carries.isNotEmpty()) add(Line(carries, small = true))
            nums.forEachIndexed { k, n -> add(Line(rightAligned(n, width), prefix = if (k == nums.lastIndex) "+" else "")) }
            add(Line(rightAligned(answer, width, Mark.ANSWER), ruleAbove = true))
        }
        val steps = listOf(
            Step("Line the numbers up by place value, ones under ones."),
            Step("Add each column from right to left, carrying when a column reaches 10 or more.", details),
            Step("Answer: ${nums.joinToString(" + ")} = ${group(answer)}"),
        )
        return Solution(Worksheet(width, lines), steps, answer)
    }

    // ---------- Subtraction ----------

    fun subtract(aIn: String, bIn: String): Solution? {
        var a = clean(aIn) ?: return null
        var b = clean(bIn) ?: return null
        val negative = BigInteger(a) < BigInteger(b)
        val intro = mutableListOf<Step>()
        if (negative) {
            intro += Step("$a is smaller than $b, so the answer is negative. Work out $b − $a and put a minus sign in front.")
            val t = a; a = b; b = t
        }
        val width = a.length
        val top = a.map { it - '0' }.toMutableList()  // left to right
        val original = top.toList()
        val bottom = List(width - b.length) { 0 } + b.map { it - '0' }
        val details = mutableListOf<String>()
        val resultDigits = IntArray(width)
        for (col in width - 1 downTo 0) {
            val i = width - 1 - col // place from right
            if (top[col] < bottom[col]) {
                // Borrow from the nearest non-zero digit to the left; zeros in between become 9.
                var j = col - 1
                while (top[j] == 0) j--
                val lender = top[j]
                top[j] -= 1
                for (k in j + 1 until col) top[k] = 9
                top[col] += 10
                val zeros = col - 1 - j
                val via = when {
                    zeros == 1 -> ", and the zero in between becomes 9"
                    zeros > 1 -> ", and the zeros in between become 9s"
                    else -> ""
                }
                details += "${place(i).cap()}: ${top[col] - 10} is less than ${bottom[col]}, so borrow 1 from the ${place(width - 1 - j)} " +
                    "($lender → ${lender - 1}$via). Now ${top[col]} − ${bottom[col]} = ${top[col] - bottom[col]}"
            } else {
                details += "${place(i).cap()}: ${top[col]} − ${bottom[col]} = ${top[col] - bottom[col]}"
            }
            resultDigits[col] = top[col] - bottom[col]
        }
        val answerDigits = resultDigits.joinToString("").trimStart('0').ifEmpty { "0" }
        val answer = (if (negative && answerDigits != "0") "-" else "") + answerDigits
        // Borrow marks: changed columns get the original struck and the new value above.
        val borrowRow = mutableMapOf<Int, Cell>()
        val topRow = mutableMapOf<Int, Cell>()
        for (c in 0 until width) {
            if (top[c] != original[c]) {
                borrowRow[c] = Cell(top[c].toString(), Mark.BORROWED)
                topRow[c] = Cell(original[c].toString(), Mark.STRUCK)
            } else {
                topRow[c] = Cell(original[c].toString())
            }
        }
        val lines = buildList {
            if (borrowRow.isNotEmpty()) add(Line(borrowRow, small = true))
            add(Line(topRow))
            add(Line(rightAligned(b, width), prefix = "−"))
            add(Line(rightAligned(answerDigits, width, Mark.ANSWER), ruleAbove = true, prefix = if (negative && answerDigits != "0") "−" else ""))
        }
        val steps = intro + listOf(
            Step("Write the bigger number on top and line up the place values."),
            Step("Subtract each column from right to left. When the top digit is smaller, borrow 1 from the next column (it's worth 10 here).", details),
            Step("Answer: ${if (negative) "$b − $a" else "$a − $b"} = ${group(answer)}"),
        )
        return Solution(Worksheet(width, lines), steps, answer)
    }

    // ---------- Multiplication ----------

    fun multiply(aIn: String, bIn: String): Solution? {
        val a = clean(aIn) ?: return null
        val b = clean(bIn) ?: return null
        val answer = (BigInteger(a) * BigInteger(b)).toString()
        val width = maxOf(a.length, b.length, answer.length) + 0
        val carryLines = mutableListOf<Line>()
        val partials = mutableListOf<Pair<String, Int>>() // digits incl. placeholder zeros, multiplier digit index
        val steps = mutableListOf(Step("Write the bigger number on top. Multiply it by each digit of the bottom number, starting from the ones."))
        val digitsB = b.reversed()
        digitsB.forEachIndexed { k, dc ->
            val d = dc - '0'
            val group = k + 1
            val details = mutableListOf<String>()
            if (k > 0) details += "Write ${"0".repeat(k)} first as a placeholder, because this digit is worth $d${"0".repeat(k)}"
            if (d == 0) {
                details += "Multiplying by 0 gives 0, so this row is all zeros"
                partials += "0".repeat(a.length + k) to k
                steps += Step("Multiply by the ${place(k)} digit, 0:", details)
                return@forEachIndexed
            }
            var carry = 0
            val row = StringBuilder()
            val carryRow = mutableMapOf<Int, Cell>()
            a.reversed().forEachIndexed { i, ac ->
                val x = ac - '0'
                val prod = d * x + carry
                val expr = "$d × $x" + if (carry > 0) " + $carry (carried) = $prod" else " = $prod"
                val newCarry = prod / 10
                details += if (i == a.length - 1) {
                    "$expr → write $prod"
                } else if (newCarry > 0) {
                    "$expr → write ${prod % 10}, carry $newCarry"
                } else {
                    "$expr → write ${prod % 10}"
                }
                if (i == a.length - 1) row.insert(0, prod) else row.insert(0, prod % 10)
                carry = if (i == a.length - 1) 0 else newCarry
                // Carry sits above the next digit of the top number.
                if (i < a.length - 1 && newCarry > 0) carryRow[width - 2 - i] = Cell(newCarry.toString(), Mark.CARRY, group)
            }
            if (carryRow.isNotEmpty()) carryLines += Line(carryRow, small = true, prefix = "×$d", prefixGroup = group)
            val partial = row.toString() + "0".repeat(k)
            partials += partial to k
            steps += Step("Multiply by the ${place(k)} digit, $d:", details + "This row: ${group(partial.trimStart('0').ifEmpty { "0" })}")
        }
        val lines = mutableListOf<Line>()
        lines += carryLines.reversed() // latest multiplier's carries nearest the number, like on paper
        lines += Line(rightAligned(a, width))
        lines += Line(rightAligned(b, width), prefix = "×")
        if (partials.size == 1) {
            lines += Line(rightAligned(answer, width, Mark.ANSWER), ruleAbove = true)
        } else {
            // Carries of the final addition, then the partial rows with placeholders marked.
            val add = add(partials.map { it.first })!!
            val addCarries = add.worksheet.lines.firstOrNull { it.small }?.cells.orEmpty()
                .mapKeys { (c, _) -> c - (add.worksheet.width - width) }.filterKeys { it >= 0 }
                .mapValues { (_, cell) -> cell.copy(group = 0) }
            var first = true
            if (addCarries.isNotEmpty()) {
                lines += Line(addCarries, small = true, ruleAbove = true)
                first = false
            }
            partials.forEachIndexed { idx, (p, k) ->
                val cells = rightAligned(p, width, group = k + 1)
                for (z in 0 until k) cells[width - 1 - z] = Cell("0", Mark.PLACEHOLDER, k + 1)
                lines += Line(cells, prefix = if (idx == partials.lastIndex) "+" else "", ruleAbove = first && idx == 0)
            }
            lines += Line(rightAligned(answer, width, Mark.ANSWER), ruleAbove = true)
            steps += Step(
                "Add the rows: ${partials.joinToString(" + ") { group(it.first.trimStart('0').ifEmpty { "0" }) }}",
                add.steps[1].details,
            )
        }
        steps += Step("Answer: ${group(a)} × ${group(b)} = ${group(answer)}")
        return Solution(Worksheet(width, lines), steps, answer)
    }

    // ---------- Long division ----------

    /**
     * Long division, digit by digit. [decimals] > 0 continues past the point with zeros
     * (stopping early if it divides exactly). Remainder, fraction and decimal forms in [Solution.extra].
     */
    fun divide(dividendIn: String, divisorIn: String, decimals: Int = 0): Solution? {
        val dividend = clean(dividendIn) ?: return null
        val divisorS = clean(divisorIn) ?: return null
        val divisor = BigInteger(divisorS)
        if (divisor.signum() == 0) return null
        val digits = dividend.map { it - '0' } + List(decimals) { 0 }
        val n = dividend.length
        val pointCol = if (decimals > 0) n else -1
        fun col(i: Int) = if (pointCol >= 0 && i >= n) i + 1 else i // column of digit i (decimal point takes one column)
        val width = col(digits.size - 1) + 1

        val quotient = mutableMapOf<Int, Cell>()
        val lines = mutableListOf<Line>()
        val details = mutableListOf<String>()
        var current = BigInteger.ZERO
        var started = false
        var work: MutableMap<Int, Cell>? = null // the current "working number" row (difference + brought-down digits)
        var lastDigit = digits.size - 1
        for (i in digits.indices) {
            current = current * BigInteger.TEN + BigInteger.valueOf(digits[i].toLong())
            if (i >= n && current.signum() == 0 && started) { lastDigit = i - 1; break } // exact: stop decimals
            val q = current / divisor
            if (!started && q.signum() == 0 && i < n - 1) {
                details += "$divisorS doesn't go into $current, so look at one more digit"
                continue
            }
            if (i == n && decimals > 0) details += "No more digits: write a decimal point and bring down 0s"
            started = true
            val qi = q.toInt()
            quotient[col(i)] = Cell(qi.toString(), Mark.ANSWER)
            val product = q * divisor
            val diff = current - product
            if (qi == 0) {
                details += "$divisorS goes into $current 0 times. Write 0 above, keep $current" + if (i < digits.size - 1) " and bring down the next digit" else ""
            } else {
                details += "How many ${divisorS}s in $current? $qi ($qi × $divisorS = $product). Write $qi above. " +
                    "$current − $product = $diff" + if (i < digits.size - 1) ", bring down the next digit → ${diff * BigInteger.TEN + BigInteger.valueOf(digits[i + 1].toLong())}" else ""
                val ps = product.toString()
                val cells = mutableMapOf<Int, Cell>()
                ps.reversed().forEachIndexed { k, ch -> cells[colBack(i, k, pointCol, n)] = Cell(ch.toString()) }
                val left = cells.keys.min()
                lines += Line(cells, prefix = "", note = "$qi × $divisorS")
                val ds = diff.toString()
                val dcells = mutableMapOf<Int, Cell>()
                ds.reversed().forEachIndexed { k, ch -> dcells[colBack(i, k, pointCol, n)] = Cell(ch.toString()) }
                lines += Line(dcells, ruleAbove = true, ruleFrom = left, ruleTo = col(i))
                work = dcells
            }
            if (i < digits.size - 1) work?.set(col(i + 1), Cell(digits[i + 1].toString(), Mark.BROUGHT_DOWN))
            current = diff
        }
        val remainder = current
        val qWhole = BigInteger(dividend) / divisor
        val rWhole = BigInteger(dividend) % divisor

        val dividendCells = mutableMapOf<Int, Cell>()
        digits.forEachIndexed { i, d -> if (i <= lastDigit) dividendCells[col(i)] = Cell(d.toString()) }
        if (pointCol >= 0 && lastDigit >= n) { dividendCells[pointCol] = Cell("."); quotient[pointCol] = Cell(".", Mark.ANSWER) }
        // Trim columns that were never used (exact decimals stop early).
        val usedWidth = col(lastDigit) + 1
        val sheet = mutableListOf<Line>()
        sheet += Line(quotient.filterKeys { it < usedWidth })
        sheet += Line(dividendCells, prefix = divisorS, bracket = true, ruleAbove = true, ruleFrom = 0, ruleTo = usedWidth - 1)
        sheet += lines.map { l -> l.copy(cells = l.cells.filterKeys { it < usedWidth }) }

        val g = rWhole.gcd(divisor)
        val q = group(qWhole.toString())
        // "28 12/15 = 28 4/5 (simplified by dividing by 3)"
        val fracText = if (rWhole.signum() == 0) "" else "$q ${rWhole}/${divisorS}" +
            if (g > BigInteger.ONE) " = $q ${rWhole / g}/${divisor / g} (dividing the top and bottom by $g)" else ""
        val extra = buildList {
            add("Quotient" to group(qWhole.toString()))
            add("Remainder" to rWhole.toString())
            if (rWhole.signum() != 0) add("As a mixed number" to "${group(qWhole.toString())} ${rWhole / g}/${divisor / g}")
            add("As a decimal" to java.math.BigDecimal(dividend).divide(java.math.BigDecimal(divisor), 10, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString())
        }
        val answer = if (decimals > 0 && lastDigit >= n) {
            buildString {
                append(qWhole)
                append('.')
                for (i in n..lastDigit) append(quotient[col(i)]?.text ?: "0")
            } + if (remainder.signum() != 0) " (rounded down, remainder continues)" else ""
        } else if (rWhole.signum() == 0) group(qWhole.toString()) else "${group(qWhole.toString())} r $rWhole"
        val steps = listOf(
            Step("Write $divisorS outside the bracket and $dividend inside. Work from the left, one digit at a time."),
            Step("Divide, multiply, subtract, bring down, and repeat:", details),
            Step(
                if (rWhole.signum() == 0) "It divides exactly: $dividend ÷ $divisorS = ${group(qWhole.toString())}"
                else "Answer: $dividend ÷ $divisorS = $q remainder $rWhole. The remainder over the divisor gives $fracText",
            ),
        )
        return Solution(Worksheet(usedWidth, sheet), steps, answer, extra)
    }

    /** Column of the k-th digit from the right of a number whose last digit is at dividend index [i]. */
    private fun colBack(i: Int, k: Int, pointCol: Int, n: Int): Int {
        var c = if (pointCol >= 0 && i >= n) i + 1 else i
        repeat(k) {
            c -= 1
            if (c == pointCol) c -= 1
        }
        return c
    }

    /** 1234567 → "1,234,567" for readability in steps. */
    fun group(s: String): String {
        val neg = s.startsWith("-")
        val d = s.removePrefix("-")
        val out = d.reversed().chunked(3).joinToString(",").reversed()
        return if (neg) "-$out" else out
    }

    private fun String.cap() = replaceFirstChar { it.uppercase() }
}
