package com.toolx.app.tools.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageOpsTest {

    @Test fun colourFormats() {
        // Reference values from the CSS colour spec.
        assertEquals("#FF0000", ImageOps.hex(0xFFFF0000.toInt()))
        assertEquals("rgb(255, 0, 0)", ImageOps.rgb(0xFFFF0000.toInt()))
        assertEquals(Triple(0, 100, 50), ImageOps.hsl(0xFFFF0000.toInt()))
        assertEquals(Triple(120, 100, 25), ImageOps.hsl(0xFF008000.toInt())) // CSS "green"
        assertEquals(Triple(240, 100, 50), ImageOps.hsl(0xFF0000FF.toInt()))
        assertEquals(Triple(0, 0, 50), ImageOps.hsl(0xFF808080.toInt()).let { Triple(it.first, it.second, it.third) }) // grey ≈ 50%
        assertEquals("cmyk(0%, 100%, 100%, 0%)", ImageOps.cmyk(0xFFFF0000.toInt()))
        assertEquals("cmyk(0%, 0%, 0%, 100%)", ImageOps.cmyk(0xFF000000.toInt()))
    }

    @Test fun dominantColoursFindTheMainOnes() {
        // 70% red, 30% blue, a few greens: red first, blue second.
        val px = IntArray(1000) { i -> when { i < 700 -> 0xFFE00000.toInt(); i < 990 -> 0xFF0000E0.toInt(); else -> 0xFF00E000.toInt() } }
        val pal = ImageOps.dominantColors(px, 3)
        assertEquals("#E00000", ImageOps.hex(pal[0]))
        assertEquals("#0000E0", ImageOps.hex(pal[1]))
        assertTrue(ImageOps.dominantColors(IntArray(10), 3).isEmpty()) // fully transparent
    }

    @Test fun watermarkPlacement() {
        assertEquals(10f to 10f, ImageOps.place(ImageOps.Spot.TOP_LEFT, 1000f, 500f, 200f, 50f, 10f))
        assertEquals(790f to 440f, ImageOps.place(ImageOps.Spot.BOTTOM_RIGHT, 1000f, 500f, 200f, 50f, 10f))
        assertEquals(400f to 225f, ImageOps.place(ImageOps.Spot.CENTER, 1000f, 500f, 200f, 50f, 10f))
        val tiles = ImageOps.tiles(1000f, 500f, 200f, 50f)
        // Tiles cover the whole image (some start beyond each edge).
        assertTrue(tiles.any { it.first <= 100f } && tiles.any { it.first >= 900f })
        assertTrue(tiles.any { it.second >= 450f })
    }
}
