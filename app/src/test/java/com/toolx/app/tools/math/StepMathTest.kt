package com.toolx.app.tools.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StepMathTest {

    private fun Solution.text() = steps.flatMap { listOf(it.title) + it.details }.joinToString("\n")

    /** Digits of a worksheet line read left to right, e.g. "748". */
    private fun Line.digits() = cells.toSortedMap().values.joinToString("") { it.text }

    @Test fun additionWithCarries() {
        val s = StepMath.add(listOf("748", "9350"))!!
        assertEquals("10098", s.answer)
        val text = s.text()
        assertTrue(text, "Hundreds: 7 + 3 = 10 → write 0, carry 1 to the thousands" in text)
        assertTrue(text, "Thousands: 9 + 1 (carried) = 10 → write 0, carry 1 to the ten-thousands" in text)
        // Carry row sits above the thousands and ten-thousands columns.
        val carries = s.worksheet.lines.first { it.small }.cells
        assertEquals(setOf(0, 1), carries.keys) // width 5: col 0 = ten-thousands, col 1 = thousands
        assertTrue(carries.values.all { it.text == "1" && it.mark == Mark.CARRY })
    }

    @Test fun additionManyAndLarge() {
        assertEquals("1111111110", StepMath.add(listOf("123456789", "987654321"))!!.answer)
        assertEquals("60", StepMath.add(listOf("10", "20", "30"))!!.answer)
        assertNull(StepMath.add(listOf("12", "x")))
    }

    @Test fun subtractionBorrowsAcrossZeros() {
        val s = StepMath.subtract("1000", "1")!!
        assertEquals("999", s.answer)
        // 1000: the 1 becomes 0, both zeros become 9, the ones become 10.
        val borrow = s.worksheet.lines.first { it.small }
        assertEquals("09910", borrow.digits())
        val top = s.worksheet.lines[1]
        assertTrue(top.cells.values.all { it.mark == Mark.STRUCK })
        assertTrue(s.text(), "the zeros in between become 9s" in s.text())
    }

    @Test fun subtractionSimpleAndNegative() {
        assertEquals("14", StepMath.subtract("52", "38")!!.answer)
        val neg = StepMath.subtract("5", "8")!!
        assertEquals("-3", neg.answer)
        assertTrue("answer is negative" in neg.text())
        assertEquals("0", StepMath.subtract("42", "42")!!.answer)
    }

    @Test fun longMultiplicationLikeTheTextbook() {
        val s = StepMath.multiply("187", "54")!!
        assertEquals("10098", s.answer)
        val text = s.text()
        // Ones digit 4: 4×7=28 carry 2, 4×8+2=34 carry 3, 4×1+3=7 → 748.
        assertTrue(text, "4 × 7 = 28 → write 8, carry 2" in text)
        assertTrue(text, "4 × 8 + 2 (carried) = 34 → write 4, carry 3" in text)
        assertTrue(text, "This row: 748" in text)
        // Tens digit 5: placeholder zero, 5×7=35, 5×8+3=43, 5×1+4=9 → 9350.
        assertTrue(text, "Write 0 first as a placeholder" in text)
        assertTrue(text, "5 × 8 + 3 (carried) = 43 → write 3, carry 4" in text)
        assertTrue(text, "This row: 9,350" in text)
        // Carry rows: one per multiplier digit, above the top number.
        val carryRows = s.worksheet.lines.filter { it.small && it.prefix.startsWith("×") }
        assertEquals(setOf("×4", "×5"), carryRows.map { it.prefix }.toSet())
        // Partial rows 748 and 9350 (placeholder zero marked).
        val partial2 = s.worksheet.lines.first { l -> l.cells.values.any { it.mark == Mark.PLACEHOLDER } }
        assertEquals("9350", partial2.digits())
    }

    @Test fun multiplicationEdgeCases() {
        assertEquals("0", StepMath.multiply("123", "0")!!.answer)
        assertEquals("12193263111263526900", StepMath.multiply("123456789", "98765432100")!!.answer)
        assertEquals("6", StepMath.multiply("2", "3")!!.answer)
        assertEquals("1230", StepMath.multiply("123", "10")!!.answer) // zero digit row
    }

    @Test fun longDivisionLikeTheTextbook() {
        val s = StepMath.divide("432", "15")!!
        assertEquals("28 r 12", s.answer)
        val extra = s.extra.toMap()
        assertEquals("28 4/5", extra["As a mixed number"])
        assertEquals("28.8", extra["As a decimal"])
        val text = s.text()
        assertTrue(text, "15 doesn't go into 4" in text)
        assertTrue(text, "How many 15s in 43? 2 (2 × 15 = 30)" in text)
        assertTrue(text, "43 − 30 = 13, bring down the next digit → 132" in text)
        assertTrue(text, "How many 15s in 132? 8 (8 × 15 = 120)" in text)
        // The simplified mixed number keeps its whole part (it once read "28 12/15 = 4/5").
        assertTrue(text, "28 12/15 = 28 4/5 (dividing the top and bottom by 3)" in text)
        // Quotient digits sit above the 3 and the 2.
        assertEquals("28", s.worksheet.lines[0].digits())
        assertEquals(mapOf(1 to "2", 2 to "8"), s.worksheet.lines[0].cells.mapValues { it.value.text })
    }

    @Test fun divisionToDecimals() {
        val s = StepMath.divide("1", "4", decimals = 3)!!
        assertEquals("0.25", s.answer) // stops early: exact
        val third = StepMath.divide("10", "3", decimals = 3)!!
        assertTrue(third.answer, third.answer.startsWith("3.333"))
        assertEquals("3", StepMath.divide("12", "4")!!.answer)
        assertNull(StepMath.divide("5", "0"))
    }

    @Test fun cleaning() {
        assertEquals("7", StepMath.clean("007"))
        assertEquals("1234", StepMath.clean("1,234"))
        assertNull(StepMath.clean("-5"))
        assertEquals("1,234,567", StepMath.group("1234567"))
    }
}
