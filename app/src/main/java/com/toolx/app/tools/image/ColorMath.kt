package com.toolx.app.tools.image

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Colour conversions for Colour codes. Pure (no android.graphics.Color), so it's unit-tested. */
object ColorMath {

    fun r(c: Int) = (c shr 16) and 255
    fun g(c: Int) = (c shr 8) and 255
    fun b(c: Int) = c and 255
    fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    fun hex(c: Int) = "#%02X%02X%02X".format(r(c), g(c), b(c))

    /** Accepts "#RGB", "RGB", "#RRGGBB" or "RRGGBB" (any case, spaces ignored). */
    fun parseHex(s: String): Int? {
        val t = s.trim().removePrefix("#").replace(" ", "")
        val full = when (t.length) {
            3 -> t.map { "$it$it" }.joinToString("")
            6 -> t
            else -> return null
        }
        if (!full.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) return null
        return (0xFF shl 24) or full.toInt(16)
    }

    /** Hue 0..360, saturation and value 0..1. */
    fun toHsv(c: Int): FloatArray {
        val rf = r(c) / 255f; val gf = g(c) / 255f; val bf = b(c) / 255f
        val mx = max(rf, max(gf, bf)); val mn = min(rf, min(gf, bf)); val d = mx - mn
        val h = when {
            d == 0f -> 0f
            mx == rf -> 60f * (((gf - bf) / d) % 6f)
            mx == gf -> 60f * ((bf - rf) / d + 2f)
            else -> 60f * ((rf - gf) / d + 4f)
        }.let { if (it < 0) it + 360f else it }
        return floatArrayOf(h, if (mx == 0f) 0f else d / mx, mx)
    }

    fun fromHsv(h: Float, s: Float, v: Float): Int {
        val hh = ((h % 360f) + 360f) % 360f
        val c = v * s
        val x = c * (1 - abs((hh / 60f) % 2f - 1))
        val m = v - c
        val (r1, g1, b1) = when ((hh / 60f).toInt()) {
            0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
        }
        return rgb(((r1 + m) * 255).roundToInt(), ((g1 + m) * 255).roundToInt(), ((b1 + m) * 255).roundToInt())
    }

    /** WCAG relative luminance. */
    fun luminance(c: Int): Double {
        fun lin(v: Int) = (v / 255.0).let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * lin(r(c)) + 0.7152 * lin(g(c)) + 0.0722 * lin(b(c))
    }

    /** WCAG contrast ratio, 1..21. */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a); val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** Mix toward [target] by [t] (0..1). */
    fun mix(c: Int, target: Int, t: Float): Int = rgb(
        (r(c) + (r(target) - r(c)) * t).roundToInt(),
        (g(c) + (g(target) - g(c)) * t).roundToInt(),
        (b(c) + (b(target) - b(c)) * t).roundToInt(),
    )

    /** Four tints (toward white), the colour, then four shades (toward black): nine swatches, lightest first. */
    fun scale(c: Int): List<Int> =
        listOf(0.8f, 0.6f, 0.4f, 0.2f).map { mix(c, 0xFFFFFFFF.toInt(), it) } + c + listOf(0.2f, 0.4f, 0.6f, 0.8f).map { mix(c, 0xFF000000.toInt(), it) }
}
