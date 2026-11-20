package com.toolx.app.tools.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.toolx.app.core.MediaKind
import com.toolx.app.core.MediaSaver
import com.toolx.app.core.SavedMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A rendered preview of what an export will look like, plus its expected file size. */
data class ImagePreviewResult(
    val image: ImageBitmap,
    val bytes: Long,
    /** True when [bytes] was measured at the real output size; false when extrapolated. */
    val exact: Boolean,
    val width: Int,
    val height: Int,
)

enum class OutputFormat(val label: String, val extension: String, val mimeType: String, val supportsQuality: Boolean) {
    JPEG("JPG", "jpg", "image/jpeg", true),
    PNG("PNG", "png", "image/png", false),
    WEBP("WebP", "webp", "image/webp", true);

    fun compressFormat(): Bitmap.CompressFormat = when (this) {
        JPEG -> Bitmap.CompressFormat.JPEG
        PNG -> Bitmap.CompressFormat.PNG
        WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
    }

    companion object {
        fun fromMime(mime: String?): OutputFormat = when (mime) {
            "image/png" -> PNG
            "image/webp" -> WEBP
            else -> JPEG
        }
    }
}

/** Image operations using the platform ImageDecoder (handles EXIF rotation and HEIC). */
object ImageEngine {

    /** Cap decoded size (~24 MP ≈ 96 MB ARGB) so huge photos don't run out of memory. */
    private const val MAX_PIXELS = 24_000_000L

    /** Longest side of the in-memory copy used for live previews. */
    private const val PREVIEW_MAX_SIDE = 2048

    /** Upper bound on pixels encoded per preview (~4 MP keeps each render well under a second). */
    private const val MAX_PREVIEW_PIXELS = 4_000_000L

    suspend fun readSize(context: Context, uri: Uri): Size? = withContext(Dispatchers.IO) {
        runCatching {
            var size: Size? = null
            // Decode at 1/32 scale just to read the header cheaply.
            ImageDecoder.decodeBitmap(source(context, uri)) { decoder, info, _ ->
                size = info.size
                decoder.setTargetSampleSize(32)
            }
            size
        }.getOrNull()
    }

    fun mimeOf(context: Context, uri: Uri): String? = context.contentResolver.getType(uri)

    suspend fun resize(context: Context, uri: Uri, width: Int, height: Int, format: OutputFormat, quality: Int): SavedMedia =
        withContext(Dispatchers.IO) {
            val bitmap = decode(context, uri, targetSize = Size(width, height))
            save(context, bitmap, "resized", format, quality)
        }

    suspend fun convert(context: Context, uri: Uri, format: OutputFormat, quality: Int): SavedMedia =
        withContext(Dispatchers.IO) {
            save(context, decode(context, uri), "converted", format, quality)
        }

    suspend fun rotate(context: Context, uri: Uri, degrees: Int, flipH: Boolean, flipV: Boolean, format: OutputFormat): SavedMedia =
        withContext(Dispatchers.IO) {
            val src = decode(context, uri)
            val matrix = Matrix().apply {
                // Flip first (in image space), then rotate — matches the on-screen preview.
                postScale(if (flipH) -1f else 1f, if (flipV) -1f else 1f)
                postRotate(degrees.toFloat())
            }
            val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
            if (out !== src) src.recycle()
            save(context, out, "rotated", format, 95)
        }

    // ---------- live preview ----------

    /**
     * Decodes a bounded "working copy" once per picked image. Previews are rendered
     * from this instead of the full-resolution file so each change stays cheap.
     */
    suspend fun loadWorkingCopy(context: Context, uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        ImageDecoder.decodeBitmap(source(context, uri)) { decoder, info, _ ->
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
            val w = info.size.width
            val h = info.size.height
            val longSide = max(w, h)
            if (longSide > PREVIEW_MAX_SIDE) {
                val s = PREVIEW_MAX_SIDE.toFloat() / longSide
                decoder.setTargetSize((w * s).roundToInt().coerceAtLeast(1), (h * s).roundToInt().coerceAtLeast(1))
            }
        }
    }

    /**
     * Encodes the working copy the way the real export would and decodes it back,
     * so the preview shows actual scaling and compression artifacts.
     *
     * When the target fits inside the working copy the output is rendered at the
     * exact target size and the byte count is accurate. Larger targets are rendered
     * smaller and the byte count is scaled by pixel count (an estimate).
     */
    suspend fun renderPreview(
        working: Bitmap,
        original: Size,
        targetWidth: Int,
        targetHeight: Int,
        format: OutputFormat,
        quality: Int,
    ): ImagePreviewResult = withContext(Dispatchers.Default) {
        val exact = targetWidth <= working.width && targetHeight <= working.height
        var rw = targetWidth
        var rh = targetHeight
        if (!exact) {
            rw = (targetWidth.toDouble() * working.width / original.width).roundToInt()
            rh = (targetHeight.toDouble() * working.height / original.height).roundToInt()
            val px = rw.toLong() * rh
            if (px > MAX_PREVIEW_PIXELS) {
                val k = sqrt(MAX_PREVIEW_PIXELS.toDouble() / px)
                rw = (rw * k).toInt()
                rh = (rh * k).toInt()
            }
            rw = rw.coerceAtLeast(1)
            rh = rh.coerceAtLeast(1)
        }

        val scaled = if (rw == working.width && rh == working.height) working else Bitmap.createScaledBitmap(working, rw, rh, true)
        val flat = if (format == OutputFormat.JPEG && scaled.hasAlpha()) flattenOnWhite(scaled) else scaled
        val bytes = ByteArrayOutputStream().use { out ->
            flat.compress(format.compressFormat(), quality.coerceIn(1, 100), out)
            out.toByteArray()
        }
        if (flat !== scaled) flat.recycle()
        if (scaled !== working) scaled.recycle()

        val shown = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("Couldn't render preview")
        val estimate = if (exact) {
            bytes.size.toLong()
        } else {
            (bytes.size * (targetWidth.toDouble() * targetHeight) / (rw.toDouble() * rh)).toLong()
        }
        ImagePreviewResult(shown.asImageBitmap(), estimate, exact, targetWidth, targetHeight)
    }

    // ---------- plumbing ----------

    private fun source(context: Context, uri: Uri) = ImageDecoder.createSource(context.contentResolver, uri)

    private fun decode(context: Context, uri: Uri, targetSize: Size? = null): Bitmap =
        ImageDecoder.decodeBitmap(source(context, uri)) { decoder, info, _ ->
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
            if (targetSize != null) {
                decoder.setTargetSize(targetSize.width, targetSize.height)
            } else {
                val pixels = info.size.width.toLong() * info.size.height
                var sample = 1
                while (pixels / (sample.toLong() * sample) > MAX_PIXELS) sample *= 2
                if (sample > 1) decoder.setTargetSampleSize(sample)
            }
        }

    private fun save(context: Context, bitmap: Bitmap, tag: String, format: OutputFormat, quality: Int): SavedMedia {
        val toWrite = if (format == OutputFormat.JPEG && bitmap.hasAlpha()) flattenOnWhite(bitmap) else bitmap
        return try {
            MediaSaver.write(context, MediaSaver.fileName(tag, format.extension), format.mimeType, MediaKind.IMAGE) { out ->
                check(toWrite.compress(format.compressFormat(), quality.coerceIn(1, 100), out)) { "Couldn't encode the image" }
            }
        } finally {
            if (toWrite !== bitmap) toWrite.recycle()
            bitmap.recycle()
        }
    }

    /** JPEG has no transparency; without this, transparent areas turn black. */
    private fun flattenOnWhite(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(src, 0f, 0f, null)
        }
        return out
    }
}
