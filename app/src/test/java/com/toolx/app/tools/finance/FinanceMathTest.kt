package com.toolx.app.tools.finance

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.pow

/** Reference values from standard amortization tables / calculators. */
class FinanceMathTest {

    @Test fun emiKnownValues() {
        assertEquals(22244.45, emi(1_000_000.0, 12.0, 60), 0.005)   // 10 lakh, 12%, 5 years
        assertEquals(1398.43, emi(200_000.0, 7.5, 360), 0.005)      // classic 30-year mortgage
        assertEquals(1073.64, emi(200_000.0, 5.0, 360), 0.005)
        assertEquals(10_000.0, emi(100_000.0, 0.0, 10), 1e-9)        // zero interest
        assertEquals(8884.88, emi(100_000.0, 12.0, 12), 0.005)
    }

    @Test fun scheduleAddsUp() {
        val p = 1_000_000.0
        val rows = loanSchedule(p, 12.0, 60)
        assertEquals(5, rows.size)
        assertEquals(0.0, rows.last().balance, 0.01)
        assertEquals(p, rows.sumOf { it.principal }, 0.01)
        assertEquals(emi(p, 12.0, 60) * 60 - p, rows.sumOf { it.interest }, 0.01)
        assertEquals(111_643.13, rows.first().interest, 0.01)
    }

    @Test fun scheduleHandlesPartialYears() {
        val rows = loanSchedule(50_000.0, 10.0, 18)
        assertEquals(2, rows.size)
        assertEquals(0.0, rows.last().balance, 0.01)
    }

    @Test fun futureValueKnown() {
        assertEquals(1126.825030, futureValue(1000.0, 0.0, 12.0, 12), 1e-6)      // 1% a month for a year
        assertEquals(1280.932804, futureValue(0.0, 100.0, 12.0, 12), 1e-6)       // annuity due
        assertEquals(1200.0, futureValue(0.0, 100.0, 0.0, 12), 1e-9)
        // 10,000/month at 12% for 10 years (annuity due): standard SIP value ≈ 23.23 lakh.
        assertEquals(2_323_390.76, futureValue(0.0, 10_000.0, 12.0, 120), 0.5)
        assertEquals(100_000 * 1.01.pow(120), futureValue(100_000.0, 0.0, 12.0, 120), 1e-6)
    }
}
