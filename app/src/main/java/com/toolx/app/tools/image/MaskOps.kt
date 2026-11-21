package com.toolx.app.tools.image

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure mask maths for background removal and passport framing (no Android types, so it is unit-tested).
 * A mask is row-major, [w]×[h]; confidences are 0..1 and alphas 0..255.
 */
internal object MaskOps {

    /**
     * Turns model confidence into alpha with a smooth ramp centred on [threshold].
     * [softness] is the ramp width: small = hard cut-out edge, large = feathered edge.
     */
    fun toAlpha(confidence: FloatArray, threshold: Float, softness: Float): IntArray {
        val half = (softness / 2f).coerceAtLeast(0.01f)
        val lo = threshold - half
        val span = 2f * half
        return IntArray(confidence.size) { i ->
            val t = ((confidence[i] - lo) / span).coerceIn(0f, 1f)
            (t * t * (3f - 2f * t) * 255f).roundToInt()
        }
    }

    /** Share of pixels that are mostly foreground; ~0 means no person was found. */
    fun coverage(alpha: IntArray): Float = if (alpha.isEmpty()) 0f else alpha.count { it >= 128 }.toFloat() / alpha.size

    /** Head position in mask pixels: crown (top of hair), estimated chin, horizontal centre and widest point. */
    data class Head(val top: Int, val chin: Int, val centerX: Float, val width: Int) {
        val height: Int get() = chin - top
    }

    /**
     * Finds the head from the silhouette alone: rows widen over the head, narrow at the neck,
     * then widen again at the shoulders. The first narrowest row (where the jaw meets the neck) is taken as the chin.
     */
    fun findHead(alpha: IntArray, w: Int, h: Int): Head? {
        if (w <= 0 || h <= 0 || alpha.size != w * h) return null
        val widths = IntArray(h)
        val centers = FloatArray(h)
        for (y in 0 until h) {
            var count = 0
            var sumX = 0L
            val row = y * w
            for (x in 0 until w) if (alpha[row + x] >= 128) { count++; sumX += x }
            widths[y] = count
            centers[y] = if (count > 0) sumX.toFloat() / count else 0f
        }
        val minRow = max(2, w / 50) // ignore specks
        // Top of the head = first row of a solid run (≥3% of the height), so stray blobs
        // above the hair are skipped.
        val run = max(3, h / 33)
        val top = (0 until h).firstOrNull { y -> (y until min(h, y + run)).all { widths[it] >= minRow } } ?: return null
        // Light smoothing so single ragged rows don't count as a neck.
        val smooth = FloatArray(h) { y ->
            var s = 0f; var n = 0
            for (k in max(0, y - 2)..min(h - 1, y + 2)) { s += widths[k]; n++ }
            s / n
        }

        // Walk down: the width grows over the head, then must drop clearly (jaw → neck) and
        // rise again (collar, shoulders). The narrowest row in that dip is the neck. Shoulders
        // usually widen gradually, so "much wider than the head" alone can't find them.
        var headMax = 0f
        var narrowing = false
        var dipMin = Float.MAX_VALUE
        var dipY = -1
        var neck = -1
        var y = top
        while (y < h) {
            val wy = smooth[y]
            if (!narrowing) {
                if (wy > headMax) headMax = wy
                // Only a dip below a plausible head (≥ ~0.8× its width tall) counts as the neck.
                else if (wy < headMax * 0.88f && y - top >= headMax * 0.8f) { narrowing = true; dipMin = wy; dipY = y }
            } else {
                if (wy < dipMin) { dipMin = wy; dipY = y }
                if (wy > dipMin * 1.12f) { neck = dipY; break }
            }
            // A head is never more than ~1.6× as tall as wide: stop looking past that.
            if (headMax > 0 && y - top > headMax * 1.8f) break
            y++
        }
        // No clear neck (e.g. hair down to the shoulders): assume a typical head shape.
        val chin = (if (neck > 0) neck else top + (headMax * 1.3f).roundToInt()).coerceIn(top + 1, h)
        val headWidth = headMax.roundToInt()
        var cx = 0f
        var n = 0
        for (yy in top until chin) if (widths[yy] > 0) { cx += centers[yy]; n++ }
        return Head(top, chin, if (n > 0) cx / n else w / 2f, headWidth)
    }

    /**
     * Crop (normalized) that frames [head] the way passport rules ask: the crown→chin height
     * fills [headFraction] of the photo and the crown sits [topMargin] below the top edge.
     * [ratio] is the output's width / height. Clamped inside the image; shrinks if it can't fit.
     */
    fun passportCrop(head: Head, w: Int, h: Int, ratio: Float, headFraction: Float, topMargin: Float): CropRect {
        var cropH = head.height / headFraction
        var cropW = cropH * ratio
        // Too big for the image: shrink, keeping the ratio.
        val fit = min(1f, min(w / cropW, h / cropH))
        cropH *= fit
        cropW *= fit
        var left = head.centerX - cropW / 2f
        var top = head.top - cropH * topMargin
        left = left.coerceIn(0f, w - cropW)
        top = top.coerceIn(0f, h - cropH)
        return CropRect(left / w, top / h, (left + cropW) / w, (top + cropH) / h)
    }

    data class SheetGrid(val cols: Int, val rows: Int, val gap: Int) {
        val count: Int get() = cols * rows
    }

    /**
     * Grid of [photoW]×[photoH] copies on a sheet, [gap] apart. If dropping the gap fits more copies
     * (e.g. three 2-inch photos across 6 inches), the photos touch and share cut lines instead.
     */
    fun sheetGrid(photoW: Int, photoH: Int, sheetW: Int, sheetH: Int, gap: Int): SheetGrid {
        if (photoW <= 0 || photoH <= 0) return SheetGrid(0, 0, gap)
        fun grid(g: Int) = SheetGrid((sheetW + g) / (photoW + g), (sheetH + g) / (photoH + g), g)
        val spaced = grid(gap)
        val tight = grid(0)
        return if (tight.count > spaced.count) tight else spaced
    }

    // ---------- Edge refinement (fast guided filter, He et al.) ----------

    /** Mean over a (2r+1)² window, clamped at the borders, in O(n) via running sums. */
    fun boxMean(src: FloatArray, w: Int, h: Int, r: Int): FloatArray {
        val tmp = FloatArray(w * h)
        val out = FloatArray(w * h)
        // Horizontal pass.
        for (y in 0 until h) {
            val row = y * w
            var sum = 0f
            var lo = 0
            var hi = -1
            for (x in 0 until w) {
                val nl = max(0, x - r)
                val nh = min(w - 1, x + r)
                while (hi < nh) { hi++; sum += src[row + hi] }
                while (lo < nl) { sum -= src[row + lo]; lo++ }
                tmp[row + x] = sum / (hi - lo + 1)
            }
        }
        // Vertical pass.
        for (x in 0 until w) {
            var sum = 0f
            var lo = 0
            var hi = -1
            for (y in 0 until h) {
                val nl = max(0, y - r)
                val nh = min(h - 1, y + r)
                while (hi < nh) { hi++; sum += tmp[hi * w + x] }
                while (lo < nl) { sum -= tmp[lo * w + x]; lo++ }
                out[y * w + x] = sum / (hi - lo + 1)
            }
        }
        return out
    }

    /** An RGB image as three 0..1 planes, row-major. */
    class Rgb(val r: FloatArray, val g: FloatArray, val b: FloatArray)

    /** Per-pixel linear model q = ar·R + ag·G + ab·B + b, already window-averaged. */
    class GuidedCoefficients(val ar: FloatArray, val ag: FloatArray, val ab: FloatArray, val b: FloatArray)

    /**
     * Colour guided filter. Within each window the mask is fitted as a linear function of the
     * guide's colour, so it follows edges between regions of different colour, even when they
     * have the same brightness (navy suit vs dark red curtain).
     */
    fun guidedCoefficients(guide: Rgb, p: FloatArray, w: Int, h: Int, rad: Int, eps: Float): GuidedCoefficients {
        val n = w * h
        fun box(f: (Int) -> Float) = boxMean(FloatArray(n) { f(it) }, w, h, rad)
        val (R, G, B) = Triple(guide.r, guide.g, guide.b)
        val mR = boxMean(R, w, h, rad); val mG = boxMean(G, w, h, rad); val mB = boxMean(B, w, h, rad)
        val mP = boxMean(p, w, h, rad)
        val rr = box { R[it] * R[it] }; val rg = box { R[it] * G[it] }; val rb = box { R[it] * B[it] }
        val gg = box { G[it] * G[it] }; val gb = box { G[it] * B[it] }; val bb = box { B[it] * B[it] }
        val rp = box { R[it] * p[it] }; val gp = box { G[it] * p[it] }; val bp = box { B[it] * p[it] }

        val ar = FloatArray(n); val ag = FloatArray(n); val ab = FloatArray(n); val b0 = FloatArray(n)
        for (i in 0 until n) {
            // Covariance of the guide colour (+ eps on the diagonal) and its covariance with p.
            val sRR = rr[i] - mR[i] * mR[i] + eps
            val sRG = rg[i] - mR[i] * mG[i]
            val sRB = rb[i] - mR[i] * mB[i]
            val sGG = gg[i] - mG[i] * mG[i] + eps
            val sGB = gb[i] - mG[i] * mB[i]
            val sBB = bb[i] - mB[i] * mB[i] + eps
            val cR = rp[i] - mR[i] * mP[i]
            val cG = gp[i] - mG[i] * mP[i]
            val cB = bp[i] - mB[i] * mP[i]
            // Symmetric 3×3 inverse by cofactors.
            val i00 = sGG * sBB - sGB * sGB
            val i01 = sRB * sGB - sRG * sBB
            val i02 = sRG * sGB - sRB * sGG
            val i11 = sRR * sBB - sRB * sRB
            val i12 = sRB * sRG - sRR * sGB
            val i22 = sRR * sGG - sRG * sRG
            val det = sRR * i00 + sRG * i01 + sRB * i02
            val inv = if (det != 0f) 1f / det else 0f
            ar[i] = (i00 * cR + i01 * cG + i02 * cB) * inv
            ag[i] = (i01 * cR + i11 * cG + i12 * cB) * inv
            ab[i] = (i02 * cR + i12 * cG + i22 * cB) * inv
            b0[i] = mP[i] - ar[i] * mR[i] - ag[i] * mG[i] - ab[i] * mB[i]
        }
        return GuidedCoefficients(boxMean(ar, w, h, rad), boxMean(ag, w, h, rad), boxMean(ab, w, h, rad), boxMean(b0, w, h, rad))
    }

    /**
     * Applies low-res coefficients ([sw]×[sh]) to the full-res guide ([dw]×[dh]) with bilinear
     * upsampling, so edges keep full-resolution detail. Output clamped to 0..1.
     */
    fun applyGuided(c: GuidedCoefficients, sw: Int, sh: Int, argb: IntArray, dw: Int, dh: Int): FloatArray {
        val out = FloatArray(dw * dh)
        val sx = sw.toFloat() / dw
        val sy = sh.toFloat() / dh
        for (y in 0 until dh) {
            val fy = ((y + 0.5f) * sy - 0.5f).coerceIn(0f, (sh - 1).toFloat())
            val y0 = fy.toInt()
            val y1 = min(y0 + 1, sh - 1)
            val ty = fy - y0
            for (x in 0 until dw) {
                val fx = ((x + 0.5f) * sx - 0.5f).coerceIn(0f, (sw - 1).toFloat())
                val x0 = fx.toInt()
                val x1 = min(x0 + 1, sw - 1)
                val tx = fx - x0
                val i00 = y0 * sw + x0; val i01 = y0 * sw + x1; val i10 = y1 * sw + x0; val i11 = y1 * sw + x1
                val w00 = (1 - tx) * (1 - ty); val w01 = tx * (1 - ty); val w10 = (1 - tx) * ty; val w11 = tx * ty
                fun lerp(a: FloatArray) = a[i00] * w00 + a[i01] * w01 + a[i10] * w10 + a[i11] * w11
                val i = y * dw + x
                val px = argb[i]
                val r = ((px shr 16) and 0xFF) / 255f
                val g = ((px shr 8) and 0xFF) / 255f
                val b = (px and 0xFF) / 255f
                out[i] = (lerp(c.ar) * r + lerp(c.ag) * g + lerp(c.ab) * b + lerp(c.b)).coerceIn(0f, 1f)
            }
        }
        return out
    }

    /** Splits packed ARGB pixels into 0..1 colour planes. */
    fun rgbOf(argb: IntArray) = Rgb(
        FloatArray(argb.size) { ((argb[it] shr 16) and 0xFF) / 255f },
        FloatArray(argb.size) { ((argb[it] shr 8) and 0xFF) / 255f },
        FloatArray(argb.size) { (argb[it] and 0xFF) / 255f },
    )

    /** Bilinear resample of a w×h map of 0..1 values to dw×dh. */
    fun resample(src: FloatArray, w: Int, h: Int, dw: Int, dh: Int): FloatArray =
        resampleRaw(src, w, h, dw, dh).also { for (i in it.indices) it[i] = it[i].coerceIn(0f, 1f) }

    private fun resampleRaw(src: FloatArray, w: Int, h: Int, dw: Int, dh: Int): FloatArray {
        val out = FloatArray(dw * dh)
        val sx = w.toFloat() / dw
        val sy = h.toFloat() / dh
        for (y in 0 until dh) {
            val fy = ((y + 0.5f) * sy - 0.5f).coerceIn(0f, (h - 1).toFloat())
            val y0 = fy.toInt()
            val y1 = min(y0 + 1, h - 1)
            val ty = fy - y0
            for (x in 0 until dw) {
                val fx = ((x + 0.5f) * sx - 0.5f).coerceIn(0f, (w - 1).toFloat())
                val x0 = fx.toInt()
                val x1 = min(x0 + 1, w - 1)
                val tx = fx - x0
                val top = src[y0 * w + x0] * (1 - tx) + src[y0 * w + x1] * tx
                val bottom = src[y1 * w + x0] * (1 - tx) + src[y1 * w + x1] * tx
                out[y * dw + x] = top * (1 - ty) + bottom * ty
            }
        }
        return out
    }

    /** Millimetres → pixels at [dpi]. */
    fun mmToPx(mm: Float, dpi: Int): Int = (mm / 25.4f * dpi).roundToInt()
}
