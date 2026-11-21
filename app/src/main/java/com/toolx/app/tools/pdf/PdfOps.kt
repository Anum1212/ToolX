package com.toolx.app.tools.pdf

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure PDF maths and text logic (no Android or PdfBox types), so it is unit-tested.
 * Page indexes are 0-based in code and 1-based in anything the user types or sees.
 * PDF user space has its origin at the bottom-left and y pointing up, in points (1/72 in).
 */
internal object PdfOps {

    // ---------- Page ranges ----------

    /**
     * Parses "1-3, 5, 8-" (1-based, inclusive; "8-" = to the end, "-3" = from the start)
     * into 0-based ranges, in the order typed. Null if anything is invalid or out of range.
     */
    fun parsePageRanges(text: String, pageCount: Int): List<IntRange>? {
        if (pageCount <= 0) return null
        val parts = text.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        return parts.map { part ->
            val dash = part.indexOf('-')
            val (from, to) = if (dash < 0) {
                val n = part.toIntOrNull() ?: return null
                n to n
            } else {
                val a = part.substring(0, dash).trim()
                val b = part.substring(dash + 1).trim()
                val start = if (a.isEmpty()) 1 else a.toIntOrNull() ?: return null
                val end = if (b.isEmpty()) pageCount else b.toIntOrNull() ?: return null
                start to end
            }
            if (from < 1 || to > pageCount || from > to) return null
            (from - 1)..(to - 1)
        }
    }

    /** Compact 1-based text for a set of 0-based pages: {0,1,2,4} → "1-3, 5". */
    fun formatPages(pages: Collection<Int>): String {
        val sorted = pages.toSortedSet().toList()
        if (sorted.isEmpty()) return ""
        val out = mutableListOf<String>()
        var start = sorted[0]
        var prev = start
        for (p in sorted.drop(1) + Int.MIN_VALUE) {
            if (p == prev + 1) { prev = p; continue }
            out += if (start == prev) "${start + 1}" else "${start + 1}-${prev + 1}"
            start = p
            prev = p
        }
        return out.joinToString(", ")
    }

    // ---------- Split ----------

    enum class SplitMode { RANGES, EVERY_N, EXTRACT }

    /**
     * The 0-based pages of each output file.
     * RANGES: one file per typed range. EVERY_N: chunks of [n] pages. EXTRACT: one file with the typed pages.
     */
    fun splitGroups(mode: SplitMode, pageCount: Int, ranges: List<IntRange>?, n: Int): List<List<Int>>? = when (mode) {
        SplitMode.RANGES -> ranges?.map { it.toList() }
        SplitMode.EXTRACT -> ranges?.flatMap { it.toList() }?.distinct()?.takeIf { it.isNotEmpty() }?.let { listOf(it) }
        SplitMode.EVERY_N -> if (n < 1 || pageCount < 1) null else (0 until pageCount).chunked(n)
    }

    // ---------- Page numbers ----------

    enum class NumberFormat(val label: String) {
        PLAIN("1"),
        PAGE("Page 1"),
        OF_SLASH("1 / 10"),
        OF_WORDS("Page 1 of 10"),
    }

    fun pageLabel(format: NumberFormat, number: Int, total: Int): String = when (format) {
        NumberFormat.PLAIN -> "$number"
        NumberFormat.PAGE -> "Page $number"
        NumberFormat.OF_SLASH -> "$number / $total"
        NumberFormat.OF_WORDS -> "Page $number of $total"
    }

    enum class Spot(val label: String, val h: Int, val v: Int) {
        // h: 0 left, 1 centre, 2 right; v: 0 top, 1 middle, 2 bottom
        TOP_LEFT("Top left", 0, 0), TOP_CENTER("Top", 1, 0), TOP_RIGHT("Top right", 2, 0),
        CENTER("Centre", 1, 1),
        BOTTOM_LEFT("Bottom left", 0, 2), BOTTOM_CENTER("Bottom", 1, 2), BOTTOM_RIGHT("Bottom right", 2, 2),
    }

    /**
     * Where to start drawing a line of text [textW] wide (baseline origin), in *visual*
     * coordinates (origin top-left, y down, as the reader sees the page), [margin] from the edges.
     */
    fun anchor(spot: Spot, visualW: Float, visualH: Float, textW: Float, fontSize: Float, margin: Float): Pair<Float, Float> {
        val x = when (spot.h) {
            0 -> margin
            1 -> (visualW - textW) / 2f
            else -> visualW - margin - textW
        }
        // Baseline: cap height ≈ 0.7 of the font size.
        val y = when (spot.v) {
            0 -> margin + fontSize * 0.7f
            1 -> (visualH + fontSize * 0.7f) / 2f
            else -> visualH - margin
        }
        return x to y
    }

    // ---------- Rotated pages ----------

    /** Size of a [w]×[h] box as the reader sees it when the page has /Rotate [rotation]. */
    fun visualSize(w: Float, h: Float, rotation: Int): Pair<Float, Float> =
        if (normalizeRotation(rotation) % 180 == 0) w to h else h to w

    fun normalizeRotation(r: Int): Int = ((r % 360) + 360) % 360

    /**
     * Maps a point the reader sees (origin top-left of the visible box, y down) to PDF user
     * space inside a box [w]×[h] at ([x0], [y0]) whose page has /Rotate [rotation] (clockwise).
     */
    fun visualToUser(vx: Float, vy: Float, w: Float, h: Float, rotation: Int, x0: Float = 0f, y0: Float = 0f): Pair<Float, Float> {
        val (ux, uy) = when (normalizeRotation(rotation)) {
            90 -> vy to vx
            180 -> (w - vx) to vy
            270 -> (w - vy) to (h - vx)
            else -> vx to (h - vy)
        }
        return (ux + x0) to (uy + y0)
    }

    /** A rectangle in PDF user space: lower-left corner plus size. */
    data class Box(val x: Float, val y: Float, val w: Float, val h: Float)

    /**
     * A crop the reader drew on the visible page (normalized 0..1, top-left origin) as a
     * user-space box, given the page's current visible box ([x0],[y0],[w]×[h]) and rotation.
     */
    fun cropToUser(left: Float, top: Float, right: Float, bottom: Float, x0: Float, y0: Float, w: Float, h: Float, rotation: Int): Box {
        val (vw, vh) = visualSize(w, h, rotation)
        val a = visualToUser(left * vw, top * vh, w, h, rotation, x0, y0)
        val b = visualToUser(right * vw, bottom * vh, w, h, rotation, x0, y0)
        return Box(min(a.first, b.first), min(a.second, b.second), kotlin.math.abs(a.first - b.first), kotlin.math.abs(a.second - b.second))
    }

    // ---------- Auto-trim ----------

    /**
     * Bounds of the non-white content of a rendered page (ARGB pixels, [w]×[h]), normalized
     * 0..1 as (left, top, right, bottom) with [pad] added on each side; null for a blank page.
     * A pixel counts as content when its luma is below [threshold] (0..255).
     */
    fun contentBounds(argb: IntArray, w: Int, h: Int, pad: Float = 0.02f, threshold: Int = 235): FloatArray? {
        var l = w; var t = h; var r = -1; var b = -1
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                val c = argb[row + x]
                val luma = (299 * ((c shr 16) and 0xFF) + 587 * ((c shr 8) and 0xFF) + 114 * (c and 0xFF)) / 1000
                if (luma < threshold) {
                    if (x < l) l = x
                    if (x > r) r = x
                    if (y < t) t = y
                    if (y > b) b = y
                }
            }
        }
        if (r < 0) return null
        return floatArrayOf(
            (l.toFloat() / w - pad).coerceAtLeast(0f),
            (t.toFloat() / h - pad).coerceAtLeast(0f),
            ((r + 1f) / w + pad).coerceAtMost(1f),
            ((b + 1f) / h + pad).coerceAtMost(1f),
        )
    }

    // ---------- Images to PDF ----------

    enum class PaperSize(val label: String, val w: Float, val h: Float) {
        A4("A4", 595.28f, 841.89f),
        LETTER("Letter", 612f, 792f),
        FIT("Fit image", 0f, 0f),
    }

    enum class Orientation(val label: String) { AUTO("Auto"), PORTRAIT("Portrait"), LANDSCAPE("Landscape") }

    enum class Margin(val label: String, val pt: Float) { NONE("None", 0f), SMALL("Small", 18f), BIG("Big", 48f) }

    /**
     * Page size for an image of [imgW]×[imgH] px. FIT makes the page the image's shape
     * (long side 842 pt, about A4) so every page has the same scale feel.
     */
    fun pageFor(paper: PaperSize, orientation: Orientation, imgW: Int, imgH: Int, margin: Float): Pair<Float, Float> {
        if (paper == PaperSize.FIT) {
            val k = FIT_LONG_SIDE / max(imgW, imgH)
            return (imgW * k + 2 * margin) to (imgH * k + 2 * margin)
        }
        val landscape = when (orientation) {
            Orientation.PORTRAIT -> false
            Orientation.LANDSCAPE -> true
            Orientation.AUTO -> imgW > imgH
        }
        return if (landscape) paper.h to paper.w else paper.w to paper.h
    }

    /** Largest rect with the image's aspect that fits inside the margins, centred. */
    fun fitImage(imgW: Int, imgH: Int, pageW: Float, pageH: Float, margin: Float): Box {
        val aw = pageW - 2 * margin
        val ah = pageH - 2 * margin
        val k = min(aw / imgW, ah / imgH)
        val w = imgW * k
        val h = imgH * k
        return Box((pageW - w) / 2f, (pageH - h) / 2f, w, h)
    }

    private const val FIT_LONG_SIDE = 841.89f

    // ---------- Compression ----------

    enum class Compression(val label: String, val dpi: Int, val quality: Float, val note: String) {
        LIGHT("Light", 200, 0.85f, "Best quality, smaller file"),
        MEDIUM("Medium", 144, 0.7f, "Good for sharing"),
        STRONG("Strong", 96, 0.5f, "Smallest file, images get soft"),
    }

    /**
     * New pixel size for an image so its long side is at most [dpi] dots per inch across
     * a page whose long side is [pageLongPt] points; null if it is already small enough.
     */
    fun downscaleTarget(w: Int, h: Int, pageLongPt: Float, dpi: Int): Pair<Int, Int>? {
        val maxSide = (pageLongPt / 72f * dpi).roundToInt()
        val long = max(w, h)
        if (long <= maxSide) return null
        val k = maxSide.toFloat() / long
        return max(1, (w * k).roundToInt()) to max(1, (h * k).roundToInt())
    }

    // ---------- Text → Markdown ----------

    /** One extracted line: its text, font size, and whether a clear gap comes before it. */
    data class TextLine(val text: String, val fontSize: Float, val gapBefore: Boolean)

    private val bullet = Regex("^[•●▪◦‣∙·\\-*]\\s+")
    private val numbered = Regex("^\\d{1,3}[.)]\\s+")

    /**
     * Rebuilds simple Markdown: headings from font size relative to the body text, bullets,
     * numbered items and paragraphs. Lines of one paragraph are joined (hyphenated words re-joined).
     */
    fun toMarkdown(lines: List<TextLine>): String {
        val body = median(lines.filter { it.text.isNotBlank() }.map { it.fontSize }) ?: return ""
        val out = StringBuilder()
        var paragraph = StringBuilder()
        fun flush() {
            if (paragraph.isNotEmpty()) {
                if (out.isNotEmpty()) out.append("\n\n")
                out.append(paragraph.toString().trim())
                paragraph = StringBuilder()
            }
        }
        for (line in lines) {
            val t = line.text.trim()
            if (t.isEmpty()) { flush(); continue }
            val heading = when {
                line.fontSize >= body * 1.6f -> "# "
                line.fontSize >= body * 1.25f -> "## "
                line.fontSize >= body * 1.1f && t.length <= 80 && !t.endsWith('.') -> "### "
                else -> null
            }
            when {
                heading != null -> { flush(); out.append(if (out.isEmpty()) "" else "\n\n").append(heading).append(t) }
                bullet.containsMatchIn(t) -> { flush(); out.append(if (out.isEmpty()) "" else "\n").append("- ").append(t.replaceFirst(bullet, "")) }
                numbered.containsMatchIn(t) -> { flush(); out.append(if (out.isEmpty()) "" else "\n").append(t) }
                else -> {
                    if (line.gapBefore) flush()
                    when {
                        paragraph.isEmpty() -> paragraph.append(t)
                        // "infor-" + "mation" → "information"
                        paragraph.endsWith("-") && t.first().isLowerCase() -> {
                            paragraph.setLength(paragraph.length - 1)
                            paragraph.append(t)
                        }
                        else -> paragraph.append(' ').append(t)
                    }
                }
            }
        }
        flush()
        return out.toString()
    }

    private fun median(xs: List<Float>): Float? {
        if (xs.isEmpty()) return null
        val s = xs.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2f
    }
}
