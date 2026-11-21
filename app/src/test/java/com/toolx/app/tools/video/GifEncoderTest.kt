package com.toolx.app.tools.video

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import kotlin.random.Random

class GifEncoderTest {

    /**
     * Independent GIF LZW decoder following giflib's rules (code size grows when the next free
     * code reaches 2^size; clear resets), used to check the encoder round-trips.
     */
    private fun decode(data: ByteArray, minCode: Int): ByteArray {
        val clear = 1 shl minCode; val eoi = clear + 1
        var size = minCode + 1
        val dict = ArrayList<ByteArray>()
        fun resetDict() { dict.clear(); for (i in 0 until clear) dict += byteArrayOf(i.toByte()); dict += ByteArray(0); dict += ByteArray(0); size = minCode + 1 }
        resetDict()
        val out = ByteArrayOutputStream()
        var bitPos = 0
        fun read(): Int {
            var v = 0
            for (b in 0 until size) {
                val byte = data[(bitPos + b) / 8].toInt() and 0xFF
                if ((byte shr ((bitPos + b) % 8)) and 1 == 1) v = v or (1 shl b)
            }
            bitPos += size; return v
        }
        var prev: ByteArray? = null
        while (true) {
            val code = read()
            if (code == clear) { resetDict(); prev = null; continue }
            if (code == eoi) break
            val entry = when {
                code < dict.size -> dict[code]
                code == dict.size && prev != null -> prev + prev[0]
                else -> error("bad code $code")
            }
            out.write(entry)
            if (prev != null && dict.size < 4096) dict += prev + entry[0]
            if (dict.size == (1 shl size) && size < 12) size++
            prev = entry
        }
        return out.toByteArray()
    }

    @Test fun lzwRoundTrips() {
        val rnd = Random(7)
        // Repetitive data (compresses) and noisy data (forces table resets at 4096 codes).
        val repetitive = ByteArray(20_000) { ((it / 37) % 5).toByte() }
        val noisy = ByteArray(50_000) { rnd.nextInt(256).toByte() }
        assertArrayEquals(repetitive, decode(Lzw.encode(repetitive, 3), 3))
        assertArrayEquals(noisy, decode(Lzw.encode(noisy, 8), 8))
        val one = byteArrayOf(2)
        assertArrayEquals(one, decode(Lzw.encode(one, 2), 2))
    }

    @Test fun quantizerKeepsFewColoursExact() {
        val colours = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFFFFFFFF.toInt())
        val px = IntArray(400) { colours[it % 4] }
        val (palette, idx) = Quantizer.quantize(px, 256)
        assertEquals(4, palette.size)
        // Every pixel maps back to (nearly) its own colour.
        px.forEachIndexed { i, c ->
            val p = palette[idx[i].toInt() and 0xFF]
            assertEquals((c shr 16) and 0xFF, (p shr 16) and 0xFF, 8)
            assertEquals(c and 0xFF, p and 0xFF, 8)
        }
    }

    private fun assertEquals(expected: Int, actual: Int, tol: Int) = org.junit.Assert.assertTrue("$expected vs $actual", kotlin.math.abs(expected - actual) <= tol)

    @Test fun fileStructure() {
        val out = ByteArrayOutputStream()
        val gif = GifEncoder(out, 4, 2)
        gif.addFrame(IntArray(8) { if (it % 2 == 0) 0xFFFF0000.toInt() else 0xFF0000FF.toInt() }, 10)
        gif.addFrame(IntArray(8) { 0xFF00FF00.toInt() }, 10)
        gif.finish()
        val b = out.toByteArray()
        assertEquals("GIF89a", String(b, 0, 6))
        assertEquals(4, (b[6].toInt() and 0xFF) or ((b[7].toInt() and 0xFF) shl 8))
        assertEquals(2, (b[8].toInt() and 0xFF) or ((b[9].toInt() and 0xFF) shl 8))
        assertEquals(0x3B, b.last().toInt() and 0xFF)
        // Two image descriptors (0x2C after a graphic control block).
        val s = String(b, Charsets.ISO_8859_1)
        assertEquals(1, Regex("NETSCAPE2\\.0").findAll(s).count())
        assertEquals(2, Regex("!ù\u0004").findAll(s).count())
        assertEquals(3, GifEncoder.paletteBits(5))
        assertEquals(1, GifEncoder.paletteBits(1))
        assertEquals(8, GifEncoder.paletteBits(256))
    }
}
