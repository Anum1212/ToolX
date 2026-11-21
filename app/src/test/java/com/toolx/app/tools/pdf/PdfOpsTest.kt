package com.toolx.app.tools.pdf

import com.toolx.app.tools.pdf.PdfOps.Box
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfOpsTest {

    // US Letter in points.
    private val w = 612f
    private val h = 792f

    @Test fun pageRanges() {
        assertEquals(listOf(0..2, 4..4, 7..9), PdfOps.parsePageRanges("1-3, 5, 8-", 10))
        assertEquals(listOf(0..2), PdfOps.parsePageRanges("-3", 10))
        assertEquals(listOf(9..9, 0..0), PdfOps.parsePageRanges(" 10 ; 1 ", 10)) // order kept, ; allowed
        listOf("0", "3-1", "11", "a", "", "1-", "1-2-3").forEach { bad ->
            if (bad == "1-") return@forEach // "1-" = 1 to the end, valid
            assertNull("'$bad'", PdfOps.parsePageRanges(bad, 10))
        }
        assertEquals(listOf(0..9), PdfOps.parsePageRanges("1-", 10))
    }

    @Test fun formatPages() {
        assertEquals("1-3, 5, 7-8", PdfOps.formatPages(setOf(6, 0, 1, 2, 4, 7)))
        assertEquals("", PdfOps.formatPages(emptySet()))
    }

    @Test fun splitGroups() {
        assertEquals(listOf(listOf(0, 1), listOf(2, 3), listOf(4)), PdfOps.splitGroups(PdfOps.SplitMode.EVERY_N, 5, null, 2))
        val r = PdfOps.parsePageRanges("1-2, 4", 5)
        assertEquals(listOf(listOf(0, 1), listOf(3)), PdfOps.splitGroups(PdfOps.SplitMode.RANGES, 5, r, 0))
        // Extract: one file, duplicates dropped.
        assertEquals(listOf(listOf(0, 1, 3)), PdfOps.splitGroups(PdfOps.SplitMode.EXTRACT, 5, PdfOps.parsePageRanges("1-2, 2, 4", 5), 0))
        assertNull(PdfOps.splitGroups(PdfOps.SplitMode.EVERY_N, 5, null, 0))
    }

    @Test fun labels() {
        assertEquals("7", PdfOps.pageLabel(PdfOps.NumberFormat.PLAIN, 7, 10))
        assertEquals("3 / 10", PdfOps.pageLabel(PdfOps.NumberFormat.OF_SLASH, 3, 10))
        assertEquals("Page 3 of 10", PdfOps.pageLabel(PdfOps.NumberFormat.OF_WORDS, 3, 10))
    }

    @Test fun visualCornersOnRotatedPages() {
        // The reader's top-left corner, for each /Rotate (clockwise):
        //   0°: the page's top-left (0, h). 90°: its bottom-left (0, 0), because the left edge is now on top.
        // 180°: its bottom-right (w, 0). 270°: its top-right (w, h).
        assertEquals(0f to h, PdfOps.visualToUser(0f, 0f, w, h, 0))
        assertEquals(0f to 0f, PdfOps.visualToUser(0f, 0f, w, h, 90))
        assertEquals(w to 0f, PdfOps.visualToUser(0f, 0f, w, h, 180))
        assertEquals(w to h, PdfOps.visualToUser(0f, 0f, w, h, 270))
        // At 90° the reader's top-right is the page's top-left.
        assertEquals(0f to h, PdfOps.visualToUser(h, 0f, w, h, 90))
        // Negative and >360 rotations normalise.
        assertEquals(PdfOps.visualToUser(5f, 7f, w, h, 270), PdfOps.visualToUser(5f, 7f, w, h, -90))
        // Box offset is added.
        assertEquals(10f to (h + 20f), PdfOps.visualToUser(0f, 0f, w, h, 0, 10f, 20f))
    }

    @Test fun cropMapping() {
        // Top-left quarter of an unrotated page.
        assertEquals(Box(0f, 396f, 306f, 396f), PdfOps.cropToUser(0f, 0f, 0.5f, 0.5f, 0f, 0f, w, h, 0))
        // Top half as the reader sees a 90° page = the left part of the page (x 0..306), full height.
        assertEquals(Box(0f, 0f, 306f, 792f), PdfOps.cropToUser(0f, 0f, 1f, 0.5f, 0f, 0f, w, h, 90))
        // Whole page with an offset box stays the box.
        assertEquals(Box(36f, 36f, w, h), PdfOps.cropToUser(0f, 0f, 1f, 1f, 36f, 36f, w, h, 180))
    }

    @Test fun contentBoundsFindsInk() {
        // 100×200 white page with a dark block at x 20..39, y 50..149.
        val w = 100; val h = 200
        val px = IntArray(w * h) { 0xFFFFFFFF.toInt() }
        for (y in 50 until 150) for (x in 20 until 40) px[y * w + x] = 0xFF202020.toInt()
        px[5] = 0xFFF0F0F0.toInt() // faint paper texture: ignored (luma 240 ≥ 235)
        val b = PdfOps.contentBounds(px, w, h, pad = 0f)!!
        assertEquals(0.20f, b[0], 1e-6f)
        assertEquals(0.25f, b[1], 1e-6f)
        assertEquals(0.40f, b[2], 1e-6f)
        assertEquals(0.75f, b[3], 1e-6f)
        // Padding is clamped to the page.
        assertEquals(0f, PdfOps.contentBounds(IntArray(4) { 0xFF000000.toInt() }, 2, 2, pad = 0.1f)!![0], 0f)
        assertNull(PdfOps.contentBounds(IntArray(w * h) { 0xFFFFFFFF.toInt() }, w, h))
    }

    @Test fun anchors() {
        val (x, y) = PdfOps.anchor(PdfOps.Spot.BOTTOM_CENTER, w, h, textW = 20f, fontSize = 10f, margin = 24f)
        assertEquals(296f, x, 0.001f)
        assertEquals(768f, y, 0.001f)
        val (rx, ty) = PdfOps.anchor(PdfOps.Spot.TOP_RIGHT, w, h, 20f, 10f, 24f)
        assertEquals(568f, rx, 0.001f)
        assertEquals(31f, ty, 0.001f) // margin + cap height (0.7 × 10)
    }

    @Test fun imagePages() {
        // A4 landscape for a landscape photo in Auto.
        assertEquals(841.89f to 595.28f, PdfOps.pageFor(PdfOps.PaperSize.A4, PdfOps.Orientation.AUTO, 4000, 3000, 18f))
        assertEquals(612f to 792f, PdfOps.pageFor(PdfOps.PaperSize.LETTER, PdfOps.Orientation.PORTRAIT, 4000, 3000, 18f))
        val fit = PdfOps.fitImage(4000, 3000, 841.89f, 595.28f, 18f)
        // Height-limited: 595.28 − 36 = 559.28 tall, 4:3 → 745.71 wide, centred.
        assertEquals(559.28f, fit.h, 0.01f)
        assertEquals(745.71f, fit.w, 0.01f)
        assertEquals(18f, fit.y, 0.01f)
        assertEquals((841.89f - 745.71f) / 2f, fit.x, 0.01f)
        // Fit image: page is the photo's shape plus margins.
        val (fw, fh) = PdfOps.pageFor(PdfOps.PaperSize.FIT, PdfOps.Orientation.AUTO, 1000, 2000, 0f)
        assertEquals(0.5f, fw / fh, 0.001f)
    }

    @Test fun downscale() {
        // A4 long side 842 pt at 144 dpi → at most 1684 px.
        assertEquals(1684 to 1263, PdfOps.downscaleTarget(4000, 3000, 842f, 144))
        assertNull(PdfOps.downscaleTarget(1000, 800, 842f, 144))
    }

    @Test fun markdown() {
        val lines = listOf(
            PdfOps.TextLine("Title", 24f, true),
            PdfOps.TextLine("Intro para line one", 11f, true),
            PdfOps.TextLine("continues here.", 11f, false),
            PdfOps.TextLine("Section", 15f, true),
            PdfOps.TextLine("• first", 11f, false),
            PdfOps.TextLine("• second", 11f, false),
            PdfOps.TextLine("Body", 11f, true),
            PdfOps.TextLine("infor-", 11f, true),
            PdfOps.TextLine("mation here", 11f, false),
        )
        assertEquals(
            "# Title\n\nIntro para line one continues here.\n\n## Section\n- first\n- second\n\nBody\n\ninformation here",
            PdfOps.toMarkdown(lines),
        )
        assertEquals("", PdfOps.toMarkdown(emptyList()))
    }
}
