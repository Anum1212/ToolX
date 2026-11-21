package com.toolx.app.tools.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FractionStepsTest {

    private fun solve(a: String, op: FracOp, b: String) = FractionSteps.solve(FractionSteps.parse(a)!!, op, FractionSteps.parse(b)!!)!!
    private fun FracSolution.fraction() = extra.toMap()["As a fraction"]
    private fun FracSolution.titles() = steps.joinToString("\n") { it.title }

    @Test fun multiplyLikeTheTextbook() {
        val s = solve("4/5", FracOp.MULTIPLY, "3/7")
        assertEquals("12/35", s.fraction())
        // The working shows 4 × 3 over 5 × 7, then 12/35.
        val chain = s.steps.first { it.title.startsWith("Multiply the numerators") }.chains.single()
        assertTrue(chain.contains(Term.Frac("4 × 3", "5 × 7")))
        assertTrue(chain.contains(Term.Frac("12", "35")))
        assertTrue(s.titles(), "already in its simplest form" in s.titles())
    }

    @Test fun addWithLowestCommonDenominator() {
        val s = solve("1/4", FracOp.ADD, "1/6")
        assertEquals("5/12", s.fraction())
        assertTrue(s.titles(), "lowest common denominator of 4 and 6 is 12" in s.titles())
        val scale = s.steps.first { it.title.contains("lowest common") }.chains
        assertTrue(scale[0].contains(Term.Frac("1 × 3", "4 × 3")))
        assertTrue(scale[1].contains(Term.Frac("1 × 2", "6 × 2")))
    }

    @Test fun subtractMixedNumbers() {
        val s = solve("2 1/3", FracOp.SUBTRACT, "3/4")
        assertEquals("19/12", s.fraction())
        assertEquals(listOf(Term.Mixed("1", "7", "12")), s.answer)
        val rewrite = s.steps.first().chains.single()
        assertTrue(rewrite.contains(Term.Frac("2 × 3 + 1", "3")))
        assertTrue(rewrite.contains(Term.Frac("7", "3")))
    }

    @Test fun divideByFlipping() {
        val s = solve("3/4", FracOp.DIVIDE, "2/5")
        assertEquals("15/8", s.fraction())
        assertEquals(listOf(Term.Mixed("1", "7", "8")), s.answer)
        assertTrue(s.titles(), "reciprocal" in s.titles())
        assertNull(FractionSteps.solve(FractionSteps.parse("1/2")!!, FracOp.DIVIDE, FractionSteps.parse("0")!!))
    }

    @Test fun wholeTimesFraction() {
        val s = solve("5", FracOp.MULTIPLY, "2/3")
        assertEquals("10/3", s.fraction())
        assertEquals(listOf(Term.Mixed("3", "1", "3")), s.answer)
        assertTrue(s.steps.first().chains.single().contains(Term.Frac("5", "1")))
    }

    @Test fun simplifyAndWholeResults() {
        val s = solve("1/2", FracOp.ADD, "1/2")
        assertEquals(listOf(Term.Text("1")), s.answer)
        val half = solve("2/4", FracOp.MULTIPLY, "1")
        assertEquals("1/2", half.fraction())
        assertTrue(half.titles(), "divide the top and bottom by 2" in half.titles())
        val neg = solve("1/3", FracOp.SUBTRACT, "1/2")
        assertEquals("-1/6", neg.fraction())
    }

    @Test fun parsing() {
        assertEquals(Rational(7.toBigInteger(), 3.toBigInteger()), FractionSteps.parse("2 1/3")!!.value())
        assertEquals(Rational((-3).toBigInteger(), 4.toBigInteger()), FractionSteps.parse("-3/4")!!.value())
        assertNull(FractionSteps.parse("3/0"))
        assertNull(FractionSteps.parse("abc"))
        assertNull(FractionSteps.parse("1.5"))
    }
}
