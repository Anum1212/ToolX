package com.toolx.app.tools.image

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Pure ARGB pixel maths for Blur & pixelate. No Android, so it's unit-tested. */
object BlurOps {

    /** A box in 0..1 image coordinates (left, top, right, bottom), so it fits any decode size. */
    data class Box(val l: Float, val t: Float, val r: Float, val b: Float) {
        fun normalized() = Box(min(l, r).coerceIn(0f, 1f), min(t, b).coerceIn(0f, 1f), max(l, r).coerceIn(0f, 1f), max(t, b).coerceIn(0f, 1f))
        val isUsable get() = r - l > 0.01f && b - t > 0.01f
    }

    enum class Effect { BLUR, PIXELATE, SOLID }

    /**
     * Effect size in pixels for an image whose longer side is [longSide]. It's relative to the
     * image, so the preview (working copy) and the full-size export look the same.
     */
    fun sizeFor(effect: Effect, strength: Float, longSide: Int): Int = when (effect) {
        Effect.BLUR -> max(1, (strength * 0.04f * longSide).roundToInt())
        Effect.PIXELATE -> max(2, (strength * 0.07f * longSide).roundToInt())
        Effect.SOLID -> 0
    }

    /**
     * Three box-blur passes in each direction approximate a Gaussian. Running sums make each
     * pass O(pixels) whatever the radius. Edges repeat the border pixel.
     */
    fun blur(src: IntArray, w: Int, h: Int, radius: Int): IntArray {
        if (radius <= 0) return src.copyOf()
        // One channel at a time keeps memory at three buffers (source, channel, result).
        val out = IntArray(src.size)
        val ch = IntArray(src.size)
        val tmp = IntArray(max(w, h))
        for (shift in intArrayOf(24, 16, 8, 0)) {
            for (i in src.indices) ch[i] = (src[i] ushr shift) and 255
            repeat(3) {
                for (y in 0 until h) pass(ch, y * w, 1, w, radius, tmp)
                for (x in 0 until w) pass(ch, x, w, h, radius, tmp)
            }
            for (i in out.indices) out[i] = out[i] or (ch[i] shl shift)
        }
        return out
    }

    /** One box pass over [n] values starting at [start], [step] apart. */
    private fun pass(ch: IntArray, start: Int, step: Int, n: Int, radius: Int, tmp: IntArray) {
        val win = 2 * radius + 1
        fun at(i: Int) = ch[start + i.coerceIn(0, n - 1) * step]
        var sum = 0
        for (i in -radius..radius) sum += at(i)
        for (i in 0 until n) {
            tmp[i] = (sum + win / 2) / win
            sum += at(i + radius + 1) - at(i - radius)
        }
        for (i in 0 until n) ch[start + i * step] = tmp[i]
    }

    /** Each [block]×[block] cell becomes its average colour. */
    fun pixelate(src: IntArray, w: Int, h: Int, block: Int): IntArray {
        val out = IntArray(src.size)
        val k = max(1, block)
        var by = 0
        while (by < h) {
            var bx = 0
            val ey = min(h, by + k)
            while (bx < w) {
                val ex = min(w, bx + k)
                var sa = 0L; var sr = 0L; var sg = 0L; var sb = 0L
                for (y in by until ey) for (x in bx until ex) {
                    val c = src[y * w + x]
                    sa += c ushr 24; sr += (c shr 16) and 255; sg += (c shr 8) and 255; sb += c and 255
                }
                val n = (ey - by) * (ex - bx)
                val avg = ((sa / n).toInt() shl 24) or ((sr / n).toInt() shl 16) or ((sg / n).toInt() shl 8) or (sb / n).toInt()
                for (y in by until ey) out.fill(avg, y * w + bx, y * w + ex)
                bx += k
            }
            by += k
        }
        return out
    }

    /** 1 inside any box, 0 outside, at [w]×[h]. */
    fun boxMask(boxes: List<Box>, w: Int, h: Int): FloatArray {
        val m = FloatArray(w * h)
        for (box in boxes) {
            val n = box.normalized()
            val x0 = (n.l * w).toInt(); val x1 = (n.r * w).roundToInt().coerceAtMost(w)
            val y0 = (n.t * h).toInt(); val y1 = (n.b * h).roundToInt().coerceAtMost(h)
            for (y in y0 until y1) m.fill(1f, y * w + x0, y * w + x1)
        }
        return m
    }

    /** Pixel-wise [orig] → [effect] by [mask] (0 keeps the original, 1 takes the effect). */
    fun blend(orig: IntArray, effect: IntArray, mask: FloatArray): IntArray = IntArray(orig.size) { i ->
        val t = mask[i]
        when {
            t <= 0f -> orig[i]
            t >= 1f -> effect[i]
            else -> {
                val o = orig[i]; val e = effect[i]
                fun mix(shift: Int) = ((((o shr shift) and 255) * (1 - t)) + (((e shr shift) and 255) * t)).roundToInt()
                (mix(24) shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
            }
        }
    }

    /** The whole operation on one pixel buffer. [mask] says where the effect goes. */
    fun apply(src: IntArray, w: Int, h: Int, effect: Effect, strength: Float, mask: FloatArray, solidColor: Int = 0xFF000000.toInt()): IntArray {
        val size = sizeFor(effect, strength, max(w, h))
        val fx = when (effect) {
            Effect.BLUR -> blur(src, w, h, size)
            Effect.PIXELATE -> pixelate(src, w, h, size)
            Effect.SOLID -> IntArray(src.size) { solidColor }
        }
        return blend(src, fx, mask)
    }
}
