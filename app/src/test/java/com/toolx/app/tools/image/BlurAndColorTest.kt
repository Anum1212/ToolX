package com.toolx.app.tools.image

import com.toolx.app.tools.image.BlurOps.Box
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlurAndColorTest {

    private fun grey(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    private fun lum(c: Int) = c and 255

    // ---------- Blur ----------

    @Test fun blurKeepsAFlatImageFlat() {
        val src = IntArray(20 * 10) { grey(77) }
        assertArrayEquals(src, BlurOps.blur(src, 20, 10, 4))
    }

    @Test fun blurSpreadsADotSymmetricallyAndKeepsItsEnergy() {
        // A single 255 dot in the middle of a black 41×41 image.
        val w = 41; val src = IntArray(w * w) { grey(0) }; src[20 * w + 20] = grey(255)
        val out = BlurOps.blur(src, w, w, 2)
        assertTrue(lum(out[20 * w + 20]) < 255)
        assertEquals(lum(out[20 * w + 17]), lum(out[20 * w + 23]))
        assertEquals(lum(out[17 * w + 20]), lum(out[23 * w + 20]))
        // Three box passes of width 5 give a support of 13 px (±6): nothing beyond it.
        assertEquals(0, lum(out[20 * w + 27]))
        // Rounding at each pass loses a little, but most of the 255 survives.
        val total = out.sumOf { lum(it) }
        assertTrue("total $total", total in 200..300)
    }

    @Test fun pixelateAveragesEachCell() {
        // 4×2 image, cells of 2: left cell 0,100,0,100 → 50; right cell all 200.
        val src = intArrayOf(grey(0), grey(100), grey(200), grey(200), grey(100), grey(0), grey(200), grey(200))
        val out = BlurOps.pixelate(src, 4, 2, 2)
        assertArrayEquals(intArrayOf(grey(50), grey(50), grey(200), grey(200), grey(50), grey(50), grey(200), grey(200)), out)
    }

    @Test fun boxMaskAndBlendOnlyTouchTheBox() {
        val w = 10; val h = 10
        val mask = BlurOps.boxMask(listOf(Box(0.6f, 0.6f, 0.2f, 0.2f)), w, h) // drawn backwards
        assertEquals(16, mask.count { it == 1f }) // x and y 2..5
        assertEquals(1f, mask[2 * w + 2]); assertEquals(0f, mask[6 * w + 6])
        val src = IntArray(w * h) { grey(10) }
        val out = BlurOps.apply(src, w, h, BlurOps.Effect.SOLID, 1f, mask)
        assertEquals(0xFF000000.toInt(), out[3 * w + 3]); assertEquals(grey(10), out[0])
    }

    @Test fun renderOnACropMatchesTheWholeImage() {
        // BlurEngine.render only processes the box's neighbourhood; it must equal blurring everything.
        val w = 60; val h = 40
        val src = IntArray(w * h) { i -> grey((i * 37 + i / w * 11) % 256) }
        val mask = BlurOps.boxMask(listOf(Box(0.4f, 0.4f, 0.6f, 0.6f)), w, h)
        for (effect in listOf(BlurOps.Effect.BLUR, BlurOps.Effect.PIXELATE)) {
            assertArrayEquals(effect.name, BlurOps.apply(src, w, h, effect, 0.3f, mask), BlurEngine.render(src, w, h, effect, 0.3f, mask))
        }
    }

    @Test fun effectSizeScalesWithTheImage() {
        assertEquals(2 * BlurOps.sizeFor(BlurOps.Effect.PIXELATE, 0.5f, 1000), BlurOps.sizeFor(BlurOps.Effect.PIXELATE, 0.5f, 2000))
    }

    // ---------- Colour ----------

    @Test fun hexParsing() {
        assertEquals(0xFF1E88E5.toInt(), ColorMath.parseHex("#1e88e5"))
        assertEquals(0xFFFFAA00.toInt(), ColorMath.parseHex("fa0"))
        assertEquals(0xFF1E88E5.toInt(), ColorMath.parseHex(" 1E 88 E5 "))
        assertNull(ColorMath.parseHex("#12345"))
        assertNull(ColorMath.parseHex("#GGGGGG"))
        assertEquals("#1E88E5", ColorMath.hex(0xFF1E88E5.toInt()))
    }

    @Test fun hsvKnownValues() {
        assertArrayEquals(floatArrayOf(0f, 1f, 1f), ColorMath.toHsv(0xFFFF0000.toInt()), 1e-4f)
        assertArrayEquals(floatArrayOf(120f, 1f, 0.5019608f), ColorMath.toHsv(0xFF008000.toInt()), 1e-4f)
        assertEquals(0xFF00FFFF.toInt(), ColorMath.fromHsv(180f, 1f, 1f))
        assertEquals(0xFF808080.toInt(), ColorMath.fromHsv(42f, 0f, 128 / 255f))
        // Round trip over a spread of colours.
        for (c in listOf(0xFF1E88E5, 0xFFC0FFEE, 0xFF123456, 0xFFFFFFFF, 0xFF000000).map { it.toInt() }) {
            val (h, s, v) = ColorMath.toHsv(c).toList()
            assertEquals(ColorMath.hex(c), ColorMath.hex(ColorMath.fromHsv(h, s, v)))
        }
    }

    @Test fun wcagContrast() {
        assertEquals(21.0, ColorMath.contrast(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 1e-9)
        assertEquals(1.0, ColorMath.contrast(0xFF777777.toInt(), 0xFF777777.toInt()), 1e-9)
        // #767676 is the classic "just passes 4.5:1 on white" grey (4.54).
        assertEquals(4.54, ColorMath.contrast(0xFF767676.toInt(), 0xFFFFFFFF.toInt()), 0.01)
    }

    @Test fun scaleRunsLightToDark() {
        val s = ColorMath.scale(0xFF808080.toInt())
        assertEquals(9, s.size)
        assertEquals(0xFF808080.toInt(), s[4])
        assertTrue(s.zipWithNext().all { (a, b) -> lum(a) > lum(b) })
    }
}
