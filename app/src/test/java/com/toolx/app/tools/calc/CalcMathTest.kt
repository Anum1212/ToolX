package com.toolx.app.tools.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class CalcMathTest {

    private fun bi(x: Long) = BigInteger.valueOf(x)

    @Test fun gcdAspect() {
        assertEquals(120L, gcd(1920, 1080)) // 16:9
        assertEquals(256L, gcd(1024, 768))  // 4:3
        assertEquals(40L, gcd(2560, 1080))  // 64:27 (marketed as 21:9)
        assertEquals(7L, gcd(0, 7))
    }

    @Test fun fractions() {
        assertEquals(listOf("Simplified" to "3/4", "Mixed number" to "3/4", "Decimal" to "0.75", "Percent" to "75%"), fractionRows(bi(6), bi(8)))
        val improper = fractionRows(bi(10), bi(4)).toMap()
        assertEquals("5/2", improper["Simplified"]); assertEquals("2 1/2", improper["Mixed number"]); assertEquals("2.5", improper["Decimal"])
        assertEquals("-3/4", fractionRows(bi(-6), bi(8)).toMap()["Simplified"])
        assertEquals("-3/4", fractionRows(bi(3), bi(-4)).toMap()["Simplified"])
        assertEquals("-2 1/2", fractionRows(bi(-10), bi(4)).toMap()["Mixed number"])
        assertEquals("4", fractionRows(bi(12), bi(3)).toMap()["Mixed number"])
        assertEquals("0.3333333333", fractionRows(bi(1), bi(3)).toMap()["Decimal"]!!.take(12))
    }

    @Test fun decimalApproximation() {
        assertEquals(1L to 3L, approximate(0.333333333, 1000))
        assertEquals(355L to 113L, approximate(Math.PI, 1000))
        assertEquals(22L to 7L, approximate(Math.PI, 10))
        assertEquals(3L to 4L, approximate(0.75, 1000))
        assertEquals(5L to 2L, approximate(2.5, 1000))
        assertEquals(2L to 3L, approximate(0.6666667, 1000))
    }

    @Test fun primes() {
        assertFalse(isPrime(1)); assertTrue(isPrime(2)); assertFalse(isPrime(9)); assertTrue(isPrime(7919))
        assertTrue(isPrime(1_000_000_007)); assertFalse(isPrime(1_000_000_008)); assertTrue(isPrime(2_147_483_647))
        assertFalse(isPrime(561)) // Carmichael number
    }

    @Test fun factorization() {
        assertEquals(listOf(2L to 3, 3L to 2, 5L to 1), factorize(360))
        assertEquals(listOf(97L to 1), factorize(97))
        assertEquals(listOf(71L to 1, 839L to 1, 1471L to 1, 6857L to 1), factorize(600_851_475_143))
        assertEquals(listOf(2L to 10), factorize(1024))
    }

    @Test fun durations() {
        assertEquals("1:01:01", hms(3661))
        assertEquals("−0:01:30", hms(-90))
        assertEquals("25:01:01", hms(90061))
        assertEquals("0:00:00", hms(0))
    }

    @Test fun averages() {
        val s = averageStats(listOf(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0))
        assertEquals(8, s.count); assertEquals(40.0, s.sum, 0.0); assertEquals(5.0, s.mean, 0.0)
        assertEquals(4.5, s.median, 0.0); assertEquals(listOf(4.0), s.modes)
        assertEquals(2.0, s.populationSd, 1e-12); assertEquals(2.138089935, s.sampleSd, 1e-9)
        assertEquals(2.0, s.min, 0.0); assertEquals(9.0, s.max, 0.0)
        assertEquals(2.0, averageStats(listOf(1.0, 2.0, 4.0)).geometricMean!!, 1e-12)
        assertTrue(averageStats(listOf(1.0, 2.0, 3.0)).modes.isEmpty())
        assertEquals(listOf(1.0, 3.0), averageStats(listOf(1.0, 1.0, 3.0, 3.0, 2.0)).modes)
        assertEquals(null, averageStats(listOf(-1.0, 2.0)).geometricMean)
        assertEquals(7.0, averageStats(listOf(7.0)).median, 0.0)
    }
}
