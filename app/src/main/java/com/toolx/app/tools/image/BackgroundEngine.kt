package com.toolx.app.tools.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.net.Uri
import android.util.Size
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.toolx.app.core.SavedMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A picked photo's working copy plus the model's person confidence for each of its pixels. */
class Segmented(val working: Bitmap, val confidence: FloatArray) {
    val width: Int get() = working.width
    val height: Int get() = working.height
}

/**
 * Background removal with ML Kit's selfie segmenter. The model is bundled in the APK and
 * runs on the device; it detects people only (not objects or pets).
 */
object BackgroundEngine {

    /** Full-size exports are capped (~12 MP) so the photo plus its cut-out fit in memory. */
    private const val MAX_EXPORT_PIXELS = 12_000_000L

    /** Decodes the working copy and runs the segmenter on it once. */
    suspend fun segment(context: Context, uri: Uri): Segmented {
        val working = ImageEngine.loadWorkingCopy(context, uri).let {
            if (it.config == Bitmap.Config.ARGB_8888) it else it.copy(Bitmap.Config.ARGB_8888, false)
        }
        return Segmented(working, refine(working, confidence(working)))
    }

    /**
     * The model's mask is 256×256 at heart, so scaled up it overshoots edges by several pixels
     * (picking up bits of background). A fast guided filter re-fits it to the photo's real edges:
     * coefficients at ≤640 px, applied to the full working copy.
     */
    private suspend fun refine(bitmap: Bitmap, conf: FloatArray): FloatArray = withContext(Dispatchers.Default) {
        val w = bitmap.width
        val h = bitmap.height
        val k = minOf(1f, GUIDE_SIDE.toFloat() / max(w, h))
        val sw = max(1, (w * k).roundToInt())
        val sh = max(1, (h * k).roundToInt())
        val small = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
        val guideSmall = MaskOps.rgbOf(pixels(small))
        if (small !== bitmap) small.recycle()
        val pSmall = MaskOps.resample(conf, w, h, sw, sh)
        // The window must be ~3× wider than the model's overshoot to average it away (20 px at 640).
        val r = max(2, max(sw, sh) / 32)
        val coefficients = MaskOps.guidedCoefficients(guideSmall, pSmall, sw, sh, r, GUIDE_EPS)
        val refined = MaskOps.applyGuided(coefficients, sw, sh, pixels(bitmap), w, h)
        // The colour model can tip far-away background over the threshold (stray specks);
        // refinement may only reshape the mask where the model saw some person.
        for (i in refined.indices) if (conf[i] < MODEL_FLOOR) refined[i] = 0f
        refined
    }

    private const val MODEL_FLOOR = 0.05f

    private const val GUIDE_SIDE = 640
    /** Lower follows colour edges harder but copies texture (white flecks in hair at 1e-4). */
    private const val GUIDE_EPS = 1e-3f

    private fun pixels(bitmap: Bitmap): IntArray =
        IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }

    private suspend fun confidence(bitmap: Bitmap): FloatArray = withContext(Dispatchers.Default) {
        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        val segmenter = Segmentation.getClient(options)
        try {
            val mask = Tasks.await(segmenter.process(InputImage.fromBitmap(bitmap, 0)))
            val floats = mask.buffer.apply { rewind() }.asFloatBuffer()
            val mw = mask.width
            val mh = mask.height
            val raw = FloatArray(mw * mh).also { floats.get(it) }
            if (mw == bitmap.width && mh == bitmap.height) {
                raw
            } else {
                // Nearest-neighbour resample onto the bitmap grid (only if ML Kit returned another size).
                val w = bitmap.width
                val h = bitmap.height
                FloatArray(w * h) { i ->
                    val x = (i % w) * mw / w
                    val y = (i / w) * mh / h
                    raw[y * mw + x]
                }
            }
        } finally {
            segmenter.close()
        }
    }

    fun alphaOf(seg: Segmented, threshold: Float, softness: Float): IntArray =
        MaskOps.toAlpha(seg.confidence, threshold, softness)

    private fun maskBitmap(alpha: IntArray, w: Int, h: Int): Bitmap =
        Bitmap.createBitmap(IntArray(alpha.size) { alpha[it] shl 24 }, w, h, Bitmap.Config.ARGB_8888)

    /**
     * Draws [src] (already at output size) through the [maskRect] part of [mask], then fills
     * what's left with [background]; null keeps it transparent.
     */
    private fun composite(src: Bitmap, mask: Bitmap, maskRect: Rect, background: Int?): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val dst = Rect(0, 0, src.width, src.height)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        Canvas(out).apply {
            drawBitmap(src, null, dst, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            drawBitmap(mask, maskRect, dst, paint)
            if (background != null) drawColor(background, PorterDuff.Mode.DST_OVER)
        }
        return out
    }

    /** Live preview: the working copy with its background replaced. */
    suspend fun preview(seg: Segmented, threshold: Float, softness: Float, background: Int?): Bitmap =
        withContext(Dispatchers.Default) {
            val mask = maskBitmap(alphaOf(seg, threshold, softness), seg.width, seg.height)
            try {
                composite(seg.working, mask, Rect(0, 0, seg.width, seg.height), background)
            } finally {
                mask.recycle()
            }
        }

    /** Full-resolution export (capped at ~12 MP). Transparent output is always PNG. */
    suspend fun removeBackground(
        context: Context,
        uri: Uri,
        seg: Segmented,
        threshold: Float,
        softness: Float,
        background: Int?,
        format: OutputFormat,
    ): SavedMedia = withContext(Dispatchers.IO) {
        val size = ImageEngine.readSize(context, uri)
        val target = size?.let { cap(it) }
        val src = ImageEngine.decode(context, uri, target)
        val mask = maskBitmap(alphaOf(seg, threshold, softness), seg.width, seg.height)
        val out = try {
            composite(src, mask, Rect(0, 0, seg.width, seg.height), background)
        } finally {
            src.recycle()
            mask.recycle()
        }
        ImageEngine.save(context, out, "nobg", if (background == null) OutputFormat.PNG else format, 95)
    }

    /**
     * Passport export: crops the full photo to [crop], scales it to [outW]×[outH], replaces the
     * background, and optionally also saves a 4×6 in print sheet of copies.
     */
    suspend fun passport(
        context: Context,
        uri: Uri,
        seg: Segmented,
        threshold: Float,
        softness: Float,
        background: Int,
        crop: CropRect,
        outW: Int,
        outH: Int,
        printSheet: Boolean,
    ): List<SavedMedia> = withContext(Dispatchers.IO) {
        val size = ImageEngine.readSize(context, uri)
        val src = ImageEngine.decode(context, uri, size?.let { cap(it) })
        val region = crop.pixelRegion(src.width, src.height)
        val piece = Bitmap.createBitmap(src, region.left, region.top, region.width(), region.height())
        if (piece !== src) src.recycle()
        val scaled = downscale(piece, outW, outH)
        val mask = maskBitmap(alphaOf(seg, threshold, softness), seg.width, seg.height)
        val photo = try {
            composite(scaled, mask, crop.pixelRegion(seg.width, seg.height), background)
        } finally {
            scaled.recycle()
            mask.recycle()
        }
        val saved = mutableListOf<SavedMedia>()
        if (printSheet) saved += ImageEngine.save(context, printSheet(photo), "passport_sheet", OutputFormat.JPEG, 95)
        saved.add(0, ImageEngine.save(context, photo, "passport", OutputFormat.JPEG, 95))
        saved
    }

    /** 4×6 in landscape at 300 dpi, white, with as many copies as fit and light cut lines. */
    const val SHEET_W = 1800
    const val SHEET_H = 1200
    const val SHEET_GAP = 24 // 2 mm

    private fun printSheet(photo: Bitmap): Bitmap {
        val grid = MaskOps.sheetGrid(photo.width, photo.height, SHEET_W, SHEET_H, SHEET_GAP)
        val gap = grid.gap
        val sheet = Bitmap.createBitmap(SHEET_W, SHEET_H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.WHITE)
        val gridW = grid.cols * photo.width + (grid.cols - 1) * gap
        val gridH = grid.rows * photo.height + (grid.rows - 1) * gap
        val x0 = (SHEET_W - gridW) / 2
        val y0 = (SHEET_H - gridH) / 2
        val line = Paint().apply { color = Color.rgb(200, 200, 200); style = Paint.Style.STROKE; strokeWidth = 1f }
        for (r in 0 until grid.rows) for (c in 0 until grid.cols) {
            val x = x0 + c * (photo.width + gap)
            val y = y0 + r * (photo.height + gap)
            canvas.drawBitmap(photo, x.toFloat(), y.toFloat(), null)
            canvas.drawRect(x - 0.5f, y - 0.5f, x + photo.width + 0.5f, y + photo.height + 0.5f, line)
        }
        return sheet
    }

    /** Halves repeatedly before the last step, so big reductions don't alias. Recycles [src]. */
    private fun downscale(src: Bitmap, w: Int, h: Int): Bitmap {
        var cur = src
        while (cur.width >= w * 2 && cur.height >= h * 2) {
            val next = Bitmap.createScaledBitmap(cur, cur.width / 2, cur.height / 2, true)
            if (next !== cur) cur.recycle()
            cur = next
        }
        if (cur.width == w && cur.height == h) return cur
        val out = Bitmap.createScaledBitmap(cur, w, h, true)
        if (out !== cur) cur.recycle()
        return out
    }

    private fun cap(size: Size): Size? {
        val px = size.width.toLong() * size.height
        if (px <= MAX_EXPORT_PIXELS) return null
        val k = sqrt(MAX_EXPORT_PIXELS.toDouble() / px)
        return Size(max(1, (size.width * k).roundToInt()), max(1, (size.height * k).roundToInt()))
    }
}
