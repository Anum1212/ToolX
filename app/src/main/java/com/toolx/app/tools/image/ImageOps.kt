package com.toolx.app.tools.image

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Pure colour and layout maths for the image tools (no Android types; unit-tested). */
internal object ImageOps {

    fun red(c: Int) = (c shr 16) and 0xFF
    fun green(c: Int) = (c shr 8) and 0xFF
    fun blue(c: Int) = c and 0xFF

    fun hex(c: Int): String = "#%02X%02X%02X".format(red(c), green(c), blue(c))
    fun rgb(c: Int): String = "rgb(${red(c)}, ${green(c)}, ${blue(c)})"

    /** Hue 0..360, saturation and lightness 0..100 (rounded). */
    fun hsl(c: Int): Triple<Int, Int, Int> {
        val r = red(c) / 255.0
        val g = green(c) / 255.0
        val b = blue(c) / 255.0
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val l = (mx + mn) / 2
        val d = mx - mn
        if (d == 0.0) return Triple(0, 0, (l * 100).roundToInt())
        val s = d / (1 - abs(2 * l - 1))
        val h = when (mx) {
            r -> 60 * (((g - b) / d) % 6)
            g -> 60 * ((b - r) / d + 2)
            else -> 60 * ((r - g) / d + 4)
        }.let { if (it < 0) it + 360 else it }
        return Triple(h.roundToInt() % 360, (s * 100).roundToInt(), (l * 100).roundToInt())
    }

    fun hslText(c: Int): String = hsl(c).let { (h, s, l) -> "hsl($h, $s%, $l%)" }

    /** CMYK percentages, as printers use. */
    fun cmyk(c: Int): String {
        val r = red(c) / 255.0; val g = green(c) / 255.0; val b = blue(c) / 255.0
        val k = 1 - max(r, max(g, b))
        if (k >= 1.0) return "cmyk(0%, 0%, 0%, 100%)"
        fun p(x: Double) = ((x - k) / (1 - k) * 100).roundToInt()
        return "cmyk(${p(1 - r)}%, ${p(1 - g)}%, ${p(1 - b)}%, ${(k * 100).roundToInt()}%)"
    }

    /**
     * Dominant colours by median cut: split the colour box along its widest channel until there
     * are [count] boxes, then average each. Sorted by how many pixels each represents.
     */
    fun dominantColors(pixels: IntArray, count: Int = 6): List<Int> {
        val opaque = pixels.filter { (it ushr 24) > 128 }
        if (opaque.isEmpty()) return emptyList()
        var boxes = mutableListOf(opaque)
        while (boxes.size < count) {
            val (idx, channel) = boxes.withIndex()
                .filter { it.value.size > 1 }
                .map { (i, box) -> Triple(i, widest(box), range(box, widest(box))) }
                .maxByOrNull { it.third }
                ?.let { it.first to it.second } ?: break
            val box = boxes.removeAt(idx).sortedBy { channelOf(it, channel) }
            val mid = box.size / 2
            boxes.add(box.subList(0, mid))
            boxes.add(box.subList(mid, box.size))
        }
        boxes = boxes.filter { it.isNotEmpty() }.toMutableList()
        return boxes.sortedByDescending { it.size }.map { box ->
            val r = box.sumOf { red(it).toLong() } / box.size
            val g = box.sumOf { green(it).toLong() } / box.size
            val b = box.sumOf { blue(it).toLong() } / box.size
            (0xFF shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
        }
    }

    private fun channelOf(c: Int, ch: Int) = when (ch) { 0 -> red(c); 1 -> green(c); else -> blue(c) }
    private fun range(box: List<Int>, ch: Int): Int = box.maxOf { channelOf(it, ch) } - box.minOf { channelOf(it, ch) }
    private fun widest(box: List<Int>): Int = (0..2).maxBy { range(box, it) }

    // ---------- watermark layout ----------

    enum class Spot(val label: String, val h: Int, val v: Int) {
        TOP_LEFT("Top left", 0, 0), TOP_RIGHT("Top right", 2, 0), CENTER("Centre", 1, 1),
        BOTTOM_LEFT("Bottom left", 0, 2), BOTTOM_RIGHT("Bottom right", 2, 2), TILED("Tiled", -1, -1),
    }

    /** Top-left corner for a [w]×[h] mark inside a [W]×[H] image, [margin] from the edges. */
    fun place(spot: Spot, W: Float, H: Float, w: Float, h: Float, margin: Float): Pair<Float, Float> {
        val x = when (spot.h) { 0 -> margin; 1 -> (W - w) / 2; else -> W - margin - w }
        val y = when (spot.v) { 0 -> margin; 1 -> (H - h) / 2; else -> H - margin - h }
        return x to y
    }

    /** Centres of a staggered grid of marks covering the image (tiled watermark). */
    fun tiles(W: Float, H: Float, w: Float, h: Float): List<Pair<Float, Float>> {
        val stepX = w * 1.6f
        val stepY = h * 3f
        val out = mutableListOf<Pair<Float, Float>>()
        var y = stepY / 2
        var row = 0
        while (y < H + stepY) {
            var x = if (row % 2 == 0) stepX / 2 else 0f
            while (x < W + stepX) { out += x to y; x += stepX }
            y += stepY; row++
        }
        return out
    }
}
