package com.toolx.app.tools.general

import com.toolx.app.tools.general.GeneralOps.Pulse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalAndQiblaTest {

    // ---------- Morse timing ----------

    @Test fun sosTimeline() {
        val p = GeneralOps.timeline(GeneralOps.SOS)
        val dot = Pulse(true, 1); val dash = Pulse(true, 3); val g1 = Pulse(false, 1); val g3 = Pulse(false, 3)
        assertEquals(listOf(dot, g1, dot, g1, dot, g3, dash, g1, dash, g1, dash, g3, dot, g1, dot, g1, dot), p)
        assertEquals(27, p.sumOf { it.units })
    }

    @Test fun parisIsFiftyUnits() {
        // The standard word "PARIS " is 50 units long, including the 7-unit word gap after it.
        val p = GeneralOps.timeline(GeneralOps.toMorse("PARIS"))
        assertEquals(43, p.sumOf { it.units })
        assertEquals(50, GeneralOps.timeline(GeneralOps.toMorse("PARIS PARIS")).sumOf { it.units } - 43)
    }

    @Test fun wordGapIsSevenAndNothingTrails() {
        val p = GeneralOps.timeline(". / .")
        assertEquals(listOf(Pulse(true, 1), Pulse(false, 7), Pulse(true, 1)), p)
        assertEquals(listOf(Pulse(true, 3)), GeneralOps.timeline("  -  "))
        assertTrue(GeneralOps.timeline("").isEmpty())
    }

    @Test fun unitFromWpm() {
        assertEquals(100L, GeneralOps.unitMillis(12))
        assertEquals(60L, GeneralOps.unitMillis(20))
    }

    // ---------- Qibla ----------

    @Test fun bearingDueNorthAndSouth() {
        // Directly north of the Kaaba you face south, and vice versa.
        assertEquals(180.0, Qibla.bearing(50.0, Qibla.KAABA_LON), 1e-9)
        assertEquals(0.0, Qibla.bearing(-10.0, Qibla.KAABA_LON), 1e-9)
    }

    @Test fun bearingKnownCities() {
        assertEquals(118.99, Qibla.bearing(51.5074, -0.1278), 0.3) // London
        assertEquals(58.48, Qibla.bearing(40.7128, -74.0060), 0.3) // New York
    }

    @Test fun distanceAlongAMeridian() {
        // 10° of latitude on the mean-radius sphere is 2π·6371.0088/36 km.
        val expected = 2 * Math.PI * 6371.0088 / 36
        assertEquals(expected, Qibla.distanceKm(Qibla.KAABA_LAT + 10, Qibla.KAABA_LON), 1e-6)
    }

    @Test fun turnTakesTheShortWay() {
        assertEquals(20.0, Qibla.turn(350.0, 10.0), 1e-9)
        assertEquals(-20.0, Qibla.turn(10.0, 350.0), 1e-9)
        assertEquals(0.0, Qibla.turn(119.0, 119.0), 1e-9)
    }
}
