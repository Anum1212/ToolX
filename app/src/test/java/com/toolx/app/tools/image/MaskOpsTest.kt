package com.toolx.app.tools.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class MaskOpsTest {

    /** Synthetic head-and-shoulders silhouette: circular head, neck, wide shoulders. */
    private fun silhouette(w: Int, h: Int, cx: Int, headTop: Int, headR: Int): IntArray {
        val a = IntArray(w * h)
        val headCy = headTop + headR
        val neckTop = headCy + headR - headR / 5
        val neckBottom = neckTop + headR / 2
        for (y in 0 until h) for (x in 0 until w) {
            val dx = x - cx
            val dy = y - headCy
            val inHead = dx * dx + dy * dy <= headR * headR
            val inNeck = y in neckTop until neckBottom && abs(dx) <= headR / 2
            val inBody = y >= neckBottom && abs(dx) <= headR * 3
            if (inHead || inNeck || inBody) a[y * w + x] = 255
        }
        return a
    }

    @Test fun alphaRampIsCentredOnThreshold() {
        val a = MaskOps.toAlpha(floatArrayOf(0f, 0.3f, 0.5f, 0.7f, 1f), threshold = 0.5f, softness = 0.2f)
        assertEquals(0, a[0])
        assertEquals(0, a[1])     // below the ramp
        assertEquals(127.5f, a[2].toFloat(), 1f) // smoothstep(0.5) = 0.5 → half of 255
        assertEquals(255, a[3])   // above the ramp
        assertEquals(255, a[4])
    }

    @Test fun hardEdgeWhenSoftnessIsTiny() {
        val a = MaskOps.toAlpha(floatArrayOf(0.48f, 0.52f), threshold = 0.5f, softness = 0f)
        assertEquals(0, a[0])
        assertEquals(255, a[1])
    }

    @Test fun findsHeadInSilhouette() {
        val w = 400; val h = 600
        val head = MaskOps.findHead(silhouette(w, h, cx = 220, headTop = 100, headR = 60), w, h)
        assertNotNull(head)
        head!!
        assertTrue("top ${head.top}", head.top in 100..103) // first row wider than a speck
        assertEquals(220f, head.centerX, 1.5f)
        assertEquals(121f, head.width.toFloat(), 2f)  // diameter
        // True chin is at top + 2r = 220; the estimate must be close.
        assertTrue("chin ${head.chin}", head.chin in 205..235)
    }

    @Test fun findsHeadWhenShouldersSlopeGradually() {
        // Real portraits: a short neck, then a collar and shoulders that widen a little each row.
        // (The first version kept growing its "head width" with the shoulders and framed the whole body.)
        val w = 600; val h = 800
        val cx = 300; val headTop = 100; val r = 80
        val a = IntArray(w * h)
        val headCy = headTop + r
        val chin = headCy + r            // 260
        for (y in 0 until h) for (x in 0 until w) {
            val dx = x - cx; val dy = y - headCy
            val inHead = dx * dx + dy * dy <= r * r
            val inNeck = y in (chin - 15) until (chin + 25) && kotlin.math.abs(dx) <= 45
            // Shoulders: from 50 px half-width at y=285, widening 1.5 px per row.
            val half = 50 + (y - (chin + 25)) * 1.5
            val inBody = y >= chin + 25 && kotlin.math.abs(dx) <= half
            if (inHead || inNeck || inBody) a[y * w + x] = 255
        }
        val head = MaskOps.findHead(a, w, h)!!
        assertTrue("top ${head.top}", head.top in 100..104)
        assertTrue("chin ${head.chin}", head.chin in 245..285)
        assertEquals(161f, head.width.toFloat(), 4f)
    }

    @Test fun strayBlobAboveHeadIsIgnored() {
        // Seen on a real photo: a 40×6 px speck at the top edge was taken as the crown, and the
        // empty rows below it as the neck, giving a head a few pixels tall.
        val w = 400; val h = 600
        val a = silhouette(w, h, cx = 220, headTop = 100, headR = 60)
        for (y in 2 until 8) for (x in 200 until 240) a[y * w + x] = 255
        val head = MaskOps.findHead(a, w, h)!!
        assertTrue("top ${head.top}", head.top in 100..103)
        assertTrue("chin ${head.chin}", head.chin in 205..235)
    }

    @Test fun emptyMaskHasNoHead() {
        assertNull(MaskOps.findHead(IntArray(100 * 100), 100, 100))
        assertEquals(0f, MaskOps.coverage(IntArray(10)))
    }

    @Test fun passportCropMatchesRules() {
        val head = MaskOps.Head(top = 300, chin = 600, centerX = 500f, width = 230)
        val ratio = 35f / 45f
        val c = MaskOps.passportCrop(head, 1000, 1400, ratio, headFraction = 0.75f, topMargin = 0.08f)
        val cropH = c.height * 1400
        val cropW = c.width * 1000
        assertEquals(400f, cropH, 0.5f)                     // 300 px head = 75%
        assertEquals(ratio, cropW / cropH, 0.001f)
        assertEquals(300f - 0.08f * 400f, c.top * 1400, 0.5f) // crown 8% below the top
        assertEquals(500f, (c.left + c.right) / 2 * 1000, 0.5f)
    }

    @Test fun passportCropStaysInsideImage() {
        // Head near the top-left corner of a small image: the crop must shift and shrink to fit.
        val head = MaskOps.Head(top = 5, chin = 200, centerX = 40f, width = 150)
        val c = MaskOps.passportCrop(head, 300, 300, 1f, 0.5f, 0.1f)
        assertTrue(c.left >= 0f && c.top >= 0f && c.right <= 1.0001f && c.bottom <= 1.0001f)
        assertEquals(c.width * 300, c.height * 300, 0.5f) // still square
    }

    @Test fun sheetGridAndSizes() {
        // 35×45 mm at 300 dpi.
        assertEquals(413, MaskOps.mmToPx(35f, 300))
        assertEquals(531, MaskOps.mmToPx(45f, 300))
        assertEquals(600, MaskOps.mmToPx(50.8f, 300)) // 2 in
        // On a 6×4 in sheet with 2 mm gaps: 4 across, 2 down.
        assertEquals(MaskOps.SheetGrid(4, 2, 24), MaskOps.sheetGrid(413, 531, 1800, 1200, 24))
        // 2×2 in: with gaps only 2 fit; touching, 3 × 2 = 6 fill the sheet exactly.
        assertEquals(MaskOps.SheetGrid(3, 2, 0), MaskOps.sheetGrid(600, 600, 1800, 1200, 24))
    }

    @Test fun boxMeanAveragesWindowsAndBorders() {
        val m = MaskOps.boxMean(floatArrayOf(0f, 3f, 6f, 9f), 4, 1, 1)
        assertEquals(1.5f, m[0], 1e-5f)  // (0+3)/2 at the border
        assertEquals(3f, m[1], 1e-5f)    // (0+3+6)/3
        assertEquals(7.5f, m[3], 1e-5f)
        val flat = MaskOps.boxMean(FloatArray(25) { 0.4f }, 5, 5, 2)
        flat.forEach { assertEquals(0.4f, it, 1e-5f) }
    }

    /** Person colour on the left 50 columns, wall colour on the right; mask edge 6 px too far right. */
    private fun refineEdge(person: Int, wall: Int): Pair<FloatArray, FloatArray> {
        val w = 100; val h = 20
        val argb = IntArray(w * h) { if (it % w < 50) person else wall }
        val coarse = FloatArray(w * h) { val x = it % w; ((56 - x) / 6f + 0.5f).coerceIn(0f, 1f) }
        // Window radius ~3× the overshoot (the engine uses side / 32).
        val c = MaskOps.guidedCoefficients(MaskOps.rgbOf(argb), coarse, w, h, 20, 1e-3f)
        return coarse to MaskOps.applyGuided(c, w, h, argb, w, h)
    }

    @Test fun guidedFilterSnapsMaskToGuideEdge() {
        val (coarse, out) = refineEdge(person = 0xFF1A1A1A.toInt(), wall = 0xFFE6E6E6.toInt())
        val row = 10 * 100
        assertTrue("inside ${out[row + 47]}", out[row + 47] > 0.8f)
        // Just past the true edge, where the coarse mask still said "person"...
        assertTrue("coarse ${coarse[row + 53]}", coarse[row + 53] > 0.9f)
        // ...the refined mask is below the default 0.5 cut-off, so the overshoot is removed.
        assertTrue("refined ${out[row + 53]}", out[row + 53] < 0.5f)
    }

    @Test fun colourGuideSeparatesEqualBrightness() {
        // Navy suit vs dark red curtain with (nearly) the same luma: a grey guide sees no edge here.
        val navy = 0xFF1E2A5A.toInt()   // luma ≈ 0.150
        val red = 0xFF5A1414.toInt()    // luma ≈ 0.149
        val (coarse, out) = refineEdge(navy, red)
        val row = 10 * 100
        assertTrue("coarse ${coarse[row + 53]}", coarse[row + 53] > 0.9f)
        assertTrue("refined ${out[row + 53]}", out[row + 53] < 0.5f)
        assertTrue("inside ${out[row + 47]}", out[row + 47] > 0.8f)
    }

    @Test fun resampleIsBilinear() {
        val up = MaskOps.resample(floatArrayOf(0f, 1f), 2, 1, 4, 1)
        assertEquals(0f, up[0], 1e-5f)
        assertEquals(0.25f, up[1], 1e-5f)
        assertEquals(0.75f, up[2], 1e-5f)
        assertEquals(1f, up[3], 1e-5f)
    }

    @Test fun hexColours() {
        assertEquals(0xFFFFFFFF.toInt(), parseHexColor("#ffffff"))
        assertEquals(0xFF1E5AA8.toInt(), parseHexColor("1E5AA8"))
        assertNull(parseHexColor("#FFF"))
        assertNull(parseHexColor("#GGGGGG"))
    }
}
