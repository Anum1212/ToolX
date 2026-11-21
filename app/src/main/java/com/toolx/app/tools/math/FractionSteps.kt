package com.toolx.app.tools.math

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

/** One piece of a written equation chain. */
sealed interface Term {
    /** A stacked fraction; numerator/denominator may be expressions like "4 × 3". */
    data class Frac(val num: String, val den: String) : Term
    data class Mixed(val whole: String, val num: String, val den: String) : Term
    /** Plain text: a whole number, an operator or "=". */
    data class Text(val text: String) : Term
}

typealias Chain = List<Term>

data class FracStep(val title: String, val chains: List<Chain> = emptyList())

data class FracSolution(val steps: List<FracStep>, val answer: Chain, val extra: List<Pair<String, String>>)

enum class FracOp(val symbol: String, val label: String) {
    ADD("+", "Add"), SUBTRACT("−", "Subtract"), MULTIPLY("×", "Multiply"), DIVIDE("÷", "Divide"),
}

/** An exact fraction num/den with den > 0. */
data class Rational(val num: BigInteger, val den: BigInteger) {
    init { require(den.signum() > 0) }
    val isWhole get() = den == BigInteger.ONE
}

/** A typed number as the user wrote it: whole ("5"), fraction ("3/4") or mixed ("2 1/3"). */
data class FracInput(val negative: Boolean, val whole: BigInteger?, val num: BigInteger?, val den: BigInteger?) {
    val isMixed get() = whole != null && num != null
    val isWhole get() = num == null
    fun value(): Rational {
        val d = den ?: BigInteger.ONE
        val n = (whole ?: BigInteger.ZERO) * d + (num ?: BigInteger.ZERO)
        return Rational(if (negative) -n else n, d)
    }
    /** As written, for the first line of the working. */
    fun term(): Term = when {
        isMixed -> Term.Mixed(sign + whole, num.toString(), den.toString())
        isWhole -> Term.Text(sign + whole)
        else -> Term.Frac(sign + num, den.toString())
    }
    private val sign get() = if (negative) "-" else ""
}

object FractionSteps {

    private val mixedRe = Regex("""^\s*(-)?\s*(\d+)\s+(\d+)\s*/\s*(\d+)\s*$""")
    private val fracRe = Regex("""^\s*(-)?\s*(\d+)\s*/\s*(\d+)\s*$""")
    private val wholeRe = Regex("""^\s*(-)?\s*(\d+)\s*$""")

    /** "2 1/3", "-3/4", "5" → input; null if not understood or the denominator is 0. */
    fun parse(s: String): FracInput? {
        mixedRe.matchEntire(s)?.let { m ->
            val d = m.groupValues[4].toBigInteger()
            if (d.signum() == 0) return null
            return FracInput(m.groupValues[1].isNotEmpty(), m.groupValues[2].toBigInteger(), m.groupValues[3].toBigInteger(), d)
        }
        fracRe.matchEntire(s)?.let { m ->
            val d = m.groupValues[3].toBigInteger()
            if (d.signum() == 0) return null
            return FracInput(m.groupValues[1].isNotEmpty(), null, m.groupValues[2].toBigInteger(), d)
        }
        wholeRe.matchEntire(s)?.let { m ->
            return FracInput(m.groupValues[1].isNotEmpty(), m.groupValues[2].toBigInteger(), null, null)
        }
        return null
    }

    private fun lcm(a: BigInteger, b: BigInteger): BigInteger = a / a.gcd(b) * b
    private fun f(r: Rational) = Term.Frac(r.num.toString(), r.den.toString())
    private fun t(s: String) = Term.Text(s)
    private val eq = t("=")

    /** Works out a [op] b with every step shown. Null if b is zero for division. */
    fun solve(a: FracInput, op: FracOp, b: FracInput): FracSolution? {
        val x = a.value()
        val y = b.value()
        if (op == FracOp.DIVIDE && y.num.signum() == 0) return null
        val steps = mutableListOf<FracStep>()

        // 1. Rewrite mixed and whole numbers as fractions.
        val rewrites = mutableListOf<Chain>()
        listOf(a, b).forEach { inp ->
            val v = inp.value()
            when {
                inp.isMixed -> rewrites += listOf(
                    inp.term(), eq,
                    Term.Frac("${inp.whole} × ${inp.den} + ${inp.num}", inp.den.toString()), eq,
                    Term.Frac((if (inp.negative) "-" else "") + (inp.whole!! * inp.den!! + inp.num!!), inp.den.toString()),
                )
                inp.isWhole -> rewrites += listOf(inp.term(), eq, f(v))
            }
        }
        if (rewrites.isNotEmpty()) {
            steps += FracStep("Write whole and mixed numbers as fractions (whole × denominator + numerator, over the denominator).", rewrites)
        }

        val raw: Rational = when (op) {
            FracOp.ADD, FracOp.SUBTRACT -> addSub(x, y, op, steps)
            FracOp.MULTIPLY -> mul(x, y, steps, op)
            FracOp.DIVIDE -> {
                val flipped = Rational(if (y.num.signum() < 0) -y.den else y.den, y.num.abs())
                steps += FracStep(
                    "Dividing by a fraction is the same as multiplying by its reciprocal: flip the second fraction and multiply.",
                    listOf(listOf(f(x), t("÷"), f(y), eq, f(x), t("×"), f(flipped))),
                )
                mul(x, flipped, steps, FracOp.DIVIDE)
            }
        }

        // 2. Simplify.
        val g = raw.num.abs().gcd(raw.den)
        val simple = if (g > BigInteger.ONE) Rational(raw.num / g, raw.den / g) else raw
        if (g > BigInteger.ONE) {
            steps += FracStep(
                "Simplify: the biggest number that divides both ${raw.num.abs()} and ${raw.den} is $g, so divide the top and bottom by $g.",
                listOf(listOf(f(raw), eq, Term.Frac("${raw.num} ÷ $g", "${raw.den} ÷ $g"), eq, f(simple))),
            )
        } else if (!raw.isWhole && raw.num.signum() != 0) {
            steps += FracStep("${raw.num.abs()} and ${raw.den} share no common factor, so the fraction is already in its simplest form.")
        }

        // 3. Mixed number / whole.
        val answer: Chain = when {
            simple.num.signum() == 0 -> listOf(t("0"))
            simple.isWhole -> listOf(t(simple.num.toString()))
            simple.num.abs() > simple.den -> {
                val w = simple.num.abs() / simple.den
                val r = simple.num.abs() % simple.den
                val sign = if (simple.num.signum() < 0) "-" else ""
                val mixed = Term.Mixed(sign + w, r.toString(), simple.den.toString())
                steps += FracStep(
                    "Turn the improper fraction into a mixed number: ${simple.num.abs()} ÷ ${simple.den} = $w remainder $r.",
                    listOf(listOf(f(simple), eq, mixed)),
                )
                listOf(mixed)
            }
            else -> listOf(f(simple))
        }
        if (simple.isWhole && raw.den != BigInteger.ONE && simple.num.signum() != 0) {
            steps += FracStep("The denominator is 1, so the answer is the whole number ${simple.num}.")
        }

        val decimal = BigDecimal(simple.num).divide(BigDecimal(simple.den), 10, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        val extra = buildList {
            add("As a fraction" to "${simple.num}/${simple.den}")
            add("As a decimal" to decimal)
        }
        steps += FracStep("Answer", listOf(listOf(a.term(), t(op.symbol), b.term(), eq) + answer))
        return FracSolution(steps, answer, extra)
    }

    private fun addSub(x: Rational, y: Rational, op: FracOp, steps: MutableList<FracStep>): Rational {
        val sym = op.symbol
        val combine: (BigInteger, BigInteger) -> BigInteger = if (op == FracOp.ADD) { p, q -> p + q } else { p, q -> p - q }
        if (x.den == y.den) {
            val n = combine(x.num, y.num)
            steps += FracStep(
                "The denominators are the same, so ${if (op == FracOp.ADD) "add" else "subtract"} the numerators and keep the denominator.",
                listOf(listOf(f(x), t(sym), f(y), eq, Term.Frac("${x.num} $sym ${paren(y.num)}", x.den.toString()), eq, Term.Frac(n.toString(), x.den.toString()))),
            )
            return Rational(n, x.den)
        }
        val l = lcm(x.den, y.den)
        val kx = l / x.den
        val ky = l / y.den
        val ax = Rational(x.num * kx, l)
        val ay = Rational(y.num * ky, l)
        steps += FracStep(
            "The denominators differ. The lowest common denominator of ${x.den} and ${y.den} is $l. Multiply the top and bottom of each fraction to get $l at the bottom.",
            listOfNotNull(
                if (kx != BigInteger.ONE) listOf(f(x), eq, Term.Frac("${x.num} × $kx", "${x.den} × $kx"), eq, f(ax)) else null,
                if (ky != BigInteger.ONE) listOf(f(y), eq, Term.Frac("${y.num} × $ky", "${y.den} × $ky"), eq, f(ay)) else null,
            ),
        )
        val n = combine(ax.num, ay.num)
        steps += FracStep(
            "Now ${if (op == FracOp.ADD) "add" else "subtract"} the numerators.",
            listOf(listOf(f(ax), t(sym), f(ay), eq, Term.Frac("${ax.num} $sym ${paren(ay.num)}", l.toString()), eq, Term.Frac(n.toString(), l.toString()))),
        )
        return Rational(n, l)
    }

    private fun mul(x: Rational, y: Rational, steps: MutableList<FracStep>, op: FracOp): Rational {
        val n = x.num * y.num
        val d = x.den * y.den
        steps += FracStep(
            "Multiply the numerators together and the denominators together.",
            listOf(listOf(f(x), t("×"), f(y), eq, Term.Frac("${paren(x.num)} × ${paren(y.num)}", "${x.den} × ${y.den}"), eq, Term.Frac(n.toString(), d.toString()))),
        )
        return Rational(n, d)
    }

    private fun paren(v: BigInteger) = if (v.signum() < 0) "($v)" else v.toString()
}
