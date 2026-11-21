package com.toolx.app.tools.units

import com.toolx.app.tools.units.ShoeSizes.System
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShoeSizesTest {

    @Test fun roundTripEverySystem() {
        System.entries.forEach { s ->
            val foot = 25.3
            assertEquals(s.name, foot, ShoeSizes.toFoot(s, ShoeSizes.fromFoot(s, foot)), 1e-9)
        }
    }

    @Test fun standardRelationships() {
        // By definition of the barleycorn systems: US men's = UK + 1, US women's = UK + 2.5,
        // and one size step = 1/3 inch of foot.
        val foot = ShoeSizes.toFoot(System.UK, 7.0)
        assertEquals(8.0, ShoeSizes.fromFoot(System.US_MEN, foot), 1e-9)
        assertEquals(9.5, ShoeSizes.fromFoot(System.US_WOMEN, foot), 1e-9)
        assertEquals(2.54 / 3, ShoeSizes.toFoot(System.UK, 8.0) - foot, 1e-9)
        // EU: one size = one Paris point = 2/3 cm.
        assertEquals(2.0 / 3, ShoeSizes.toFoot(System.EU, 42.0) - ShoeSizes.toFoot(System.EU, 41.0), 1e-9)
    }

    @Test fun matchesCommonRetailChart() {
        // Typical chart row: 26 cm foot ≈ UK 7 ≈ US M 8 ≈ US W 9.5 ≈ EU 41.
        assertEquals("7", ShoeSizes.display(System.UK, 26.0))
        assertEquals("8", ShoeSizes.display(System.US_MEN, 26.0))
        assertEquals("9.5", ShoeSizes.display(System.US_WOMEN, 26.0))
        assertEquals("41", ShoeSizes.display(System.EU, 26.0))
        assertEquals("260 mm", ShoeSizes.display(System.MONDOPOINT, 26.0))
        assertEquals("26 cm", ShoeSizes.display(System.JAPAN, 26.0))
    }

    @Test fun kidsSizesOnlyForSmallFeet() {
        assertNull(ShoeSizes.display(System.UK_KIDS, 26.0)) // adult foot
        // Published kids' charts: UK 7 ≈ 14.8 cm, UK 8 ≈ 15.6 cm, so a 15 cm foot ≈ UK 7–7.5.
        val k = ShoeSizes.display(System.UK_KIDS, 15.0)!!.toDouble()
        assertEquals(7.25, k, 0.5)
    }
}
