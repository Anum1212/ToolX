package com.toolx.app.tools.video

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Animated GIF writer (GIF89a), pure Kotlin. Each frame gets its own 256-colour palette from a
 * median cut over a 15-bit colour histogram, so colours stay faithful frame to frame. Loops forever.
 */
internal class GifEncoder(private val out: OutputStream, private val width: Int, private val height: Int) {

    init {
        out.write("GIF89a".toByteArray())
        short(width); short(height)
        out.write(0x70) // no global colour table, 8-bit colour resolution
        out.write(0); out.write(0)
        // NETSCAPE2.0 application extension: loop forever.
        out.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B)); out.write("NETSCAPE2.0".toByteArray())
        out.write(byteArrayOf(3, 1, 0, 0, 0))
    }

    /** Adds one frame of ARGB [pixels] ([width]×[height]) shown for [delayCs] hundredths of a second. */
    fun addFrame(pixels: IntArray, delayCs: Int) {
        require(pixels.size == width * height)
        val (palette, indices) = Quantizer.quantize(pixels, 256)
        val bits = paletteBits(palette.size)
        // Graphic control extension: delay; no transparency.
        out.write(byteArrayOf(0x21, 0xF9.toByte(), 4, 0)); short(delayCs); out.write(0); out.write(0)
        // Image descriptor with a local colour table of 2^bits entries.
        out.write(0x2C); short(0); short(0); short(width); short(height)
        out.write(0x80 or (bits - 1))
        for (i in 0 until (1 shl bits)) {
            val c = palette.getOrElse(i) { 0 }
            out.write((c shr 16) and 0xFF); out.write((c shr 8) and 0xFF); out.write(c and 0xFF)
        }
        val minCode = maxOf(2, bits)
        out.write(minCode)
        val data = Lzw.encode(indices, minCode)
        var p = 0
        while (p < data.size) {
            val n = minOf(255, data.size - p)
            out.write(n); out.write(data, p, n); p += n
        }
        out.write(0)
    }

    fun finish() { out.write(0x3B); out.flush() }

    private fun short(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }

    companion object {
        /** Smallest n with 2^n ≥ colours (1..8). */
        fun paletteBits(colours: Int): Int { var b = 1; while ((1 shl b) < colours) b++; return b }
    }
}

/** Median-cut colour quantizer over a 32×32×32 histogram (fast enough for video frames). */
internal object Quantizer {

    fun quantize(pixels: IntArray, maxColours: Int): Pair<IntArray, ByteArray> {
        val hist = IntArray(32768)
        for (c in pixels) hist[key(c)]++
        val used = (0 until 32768).filter { hist[it] > 0 }.toIntArray()
        val boxes = mutableListOf(used)
        while (boxes.size < maxColours) {
            val i = boxes.indices.filter { boxes[it].size > 1 }.maxByOrNull { spread(boxes[it]) } ?: break
            val box = boxes.removeAt(i)
            val ch = widest(box)
            val sorted = box.sortedBy { channel(it, ch) }
            // Split at the pixel-weighted median.
            val total = sorted.sumOf { hist[it].toLong() }
            var acc = 0L; var cut = 1
            for ((k, b) in sorted.withIndex()) { acc += hist[b]; if (acc * 2 >= total) { cut = (k + 1).coerceIn(1, sorted.size - 1); break } }
            boxes += sorted.subList(0, cut).toIntArray()
            boxes += sorted.subList(cut, sorted.size).toIntArray()
        }
        val palette = IntArray(boxes.size)
        val lookup = IntArray(32768) { -1 }
        boxes.forEachIndexed { i, box ->
            var r = 0L; var g = 0L; var b = 0L; var n = 0L
            for (k in box) { val w = hist[k].toLong(); r += ((k shr 10) and 31) * w; g += ((k shr 5) and 31) * w; b += (k and 31) * w; n += w }
            val rr = (r * 255 / (31 * n)).toInt(); val gg = (g * 255 / (31 * n)).toInt(); val bb = (b * 255 / (31 * n)).toInt()
            palette[i] = (rr shl 16) or (gg shl 8) or bb
            for (k in box) lookup[k] = i
        }
        val indices = ByteArray(pixels.size) { lookup[key(pixels[it])].toByte() }
        return palette to indices
    }

    private fun key(c: Int) = (((c shr 19) and 31) shl 10) or (((c shr 11) and 31) shl 5) or ((c shr 3) and 31)
    private fun channel(k: Int, ch: Int) = when (ch) { 0 -> (k shr 10) and 31; 1 -> (k shr 5) and 31; else -> k and 31 }
    private fun range(box: IntArray, ch: Int): Int { var lo = 31; var hi = 0; for (k in box) { val v = channel(k, ch); if (v < lo) lo = v; if (v > hi) hi = v }; return hi - lo }
    private fun widest(box: IntArray) = (0..2).maxBy { range(box, it) }
    private fun spread(box: IntArray) = (0..2).maxOf { range(box, it) }
}

/** GIF-flavoured LZW: variable code size from minCode+1 up to 12 bits, LSB-first packing. */
internal object Lzw {

    fun encode(indices: ByteArray, minCode: Int): ByteArray {
        val clear = 1 shl minCode
        val eoi = clear + 1
        val table = IntArray(1 shl 20) { -1 } // key = prefix shl 8 or symbol (prefix < 4096)
        val usedKeys = IntArray(4096); var usedCount = 0
        var next = eoi + 1
        var size = minCode + 1
        val out = ByteArrayOutputStream()
        var acc = 0L; var nbits = 0
        fun emit(code: Int) {
            acc = acc or (code.toLong() shl nbits); nbits += size
            while (nbits >= 8) { out.write((acc and 0xFF).toInt()); acc = acc ushr 8; nbits -= 8 }
        }
        fun reset() {
            for (i in 0 until usedCount) table[usedKeys[i]] = -1
            usedCount = 0; next = eoi + 1; size = minCode + 1
        }
        emit(clear)
        if (indices.isEmpty()) { emit(eoi); if (nbits > 0) out.write((acc and 0xFF).toInt()); return out.toByteArray() }
        var prefix = indices[0].toInt() and 0xFF
        for (i in 1 until indices.size) {
            val k = indices[i].toInt() and 0xFF
            val key = (prefix shl 8) or k
            val found = table[key]
            if (found >= 0) { prefix = found; continue }
            emit(prefix)
            if (next < 4096) {
                table[key] = next; usedKeys[usedCount++] = key
                // Grow the code size once the next code no longer fits.
                if (next == (1 shl size) && size < 12) size++
                next++
            } else {
                emit(clear); reset()
            }
            prefix = k
        }
        emit(prefix)
        emit(eoi)
        if (nbits > 0) out.write((acc and 0xFF).toInt())
        return out.toByteArray()
    }
}
