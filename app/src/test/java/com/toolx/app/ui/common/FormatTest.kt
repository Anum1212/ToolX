package com.toolx.app.ui.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class FormatTest {

    @Test fun fmtCleansFloatNoise() {
        assertEquals("0.3", fmt(0.1 + 0.2))
        assertEquals("1", fmt(1.0))
        assertEquals("-2.5", fmt(-2.5))
        assertEquals("0.6666666667", fmt(2.0 / 3))
        assertEquals("1234567.891", fmt(1234567.891))
        assertEquals("0", fmt(0.0))
        assertEquals("3.28", fmt(3.2808398950, 2))
    }

    @Test fun fmtExtremes() {
        assertEquals("1e+20", fmt(1e20))
        assertEquals("1.5e-12", fmt(1.5e-12))
        assertEquals("∞", fmt(Double.POSITIVE_INFINITY))
        assertEquals("—", fmt(Double.NaN))
    }

    @Test fun money() {
        val old = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            assertEquals("1,234.50", money(1234.5))
            assertEquals("22,244.45", money(22244.448))
            assertEquals("0.00", money(0.0))
        } finally {
            Locale.setDefault(old)
        }
    }

    @Test fun parse() {
        assertEquals(1234.5, parseNum("1,234.5")!!, 0.0)
        assertEquals(-3.0, parseNum(" -3 ")!!, 0.0)
        assertNull(parseNum(""))
        assertNull(parseNum("abc"))
        assertNull(parseNum("1.2.3"))
    }
}
