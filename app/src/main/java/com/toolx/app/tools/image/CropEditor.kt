package com.toolx.app.tools.image

import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.util.Size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.toolx.app.core.formatBytes
import com.toolx.app.ui.common.rememberContentSize
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.ui.geometry.Rect as GeoRect
import androidx.compose.ui.geometry.Size as GeoSize

/** Crop rectangle in normalized image coordinates (0..1 on each axis). */
@Immutable
data class CropRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    /** Output size in pixels when applied to an image of [image] size. */
    fun pixelSize(image: Size) = Size(
        (width * image.width).roundToInt().coerceIn(1, image.width),
        (height * image.height).roundToInt().coerceIn(1, image.height),
    )

    /** Integer pixel region inside a bitmap of [w]×[h], always at least 1×1 and in bounds. */
    fun pixelRegion(w: Int, h: Int): Rect {
        val l = (left * w).roundToInt().coerceIn(0, w - 1)
        val t = (top * h).roundToInt().coerceIn(0, h - 1)
        val r = (right * w).roundToInt().coerceIn(l + 1, w)
        val b = (bottom * h).roundToInt().coerceIn(t + 1, h)
        return Rect(l, t, r, b)
    }

    fun movedBy(dx: Float, dy: Float): CropRect {
        val x = dx.coerceIn(-left, 1f - right)
        val y = dy.coerceIn(-top, 1f - bottom)
        return CropRect(left + x, top + y, right + x, bottom + y)
    }

    companion object {
        val Full = CropRect(0f, 0f, 1f, 1f)

        /** Largest centred rect with the given pixel aspect [ratio] (width / height); null = whole image. */
        fun centered(image: Size, ratio: Float?): CropRect {
            if (ratio == null) return Full
            val iw = image.width.toFloat()
            val ih = image.height.toFloat()
            var w = iw
            var h = iw / ratio
            if (h > ih) {
                h = ih
                w = ih * ratio
            }
            val wn = w / iw
            val hn = h / ih
            return CropRect((1f - wn) / 2f, (1f - hn) / 2f, (1f + wn) / 2f, (1f + hn) / 2f)
        }
    }
}

val CropRectSaver = listSaver<CropRect, Float>(
    save = { listOf(it.left, it.top, it.right, it.bottom) },
    restore = { CropRect(it[0], it[1], it[2], it[3]) },
)

enum class CropRatio(val label: String, private val w: Int, private val h: Int) {
    FREE("Free", 0, 0),
    ORIGINAL("Original", 0, 0),
    SQUARE("1:1", 1, 1),
    R4_5("4:5", 4, 5),
    R4_3("4:3", 4, 3),
    R3_4("3:4", 3, 4),
    R16_9("16:9", 16, 9),
    R9_16("9:16", 9, 16);

    /** Pixel aspect (width / height), or null for free-form. */
    fun value(image: Size): Float? = when (this) {
        FREE -> null
        ORIGINAL -> image.width.toFloat() / image.height
        else -> w.toFloat() / h
    }
}

private enum class CropHandle { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, MOVE }

/**
 * Moves one corner of [this] towards [point] (normalized) while the opposite corner
 * stays fixed. Works in pixel space so a locked [ratio] means real pixel proportions.
 */
private fun CropRect.dragCorner(handle: CropHandle, point: Offset, image: Size, ratio: Float?, minSidePx: Float): CropRect {
    val iw = image.width.toFloat()
    val ih = image.height.toFloat()
    val dirX = if (handle == CropHandle.TOP_LEFT || handle == CropHandle.BOTTOM_LEFT) -1f else 1f
    val dirY = if (handle == CropHandle.TOP_LEFT || handle == CropHandle.TOP_RIGHT) -1f else 1f
    // Anchor = the corner opposite the one being dragged.
    val ax = (if (dirX > 0) left else right) * iw
    val ay = (if (dirY > 0) top else bottom) * ih
    val dx = (point.x.coerceIn(0f, 1f) * iw - ax) * dirX
    val dy = (point.y.coerceIn(0f, 1f) * ih - ay) * dirY
    val maxW = if (dirX > 0) iw - ax else ax
    val maxH = if (dirY > 0) ih - ay else ay
    val minSide = min(minSidePx, min(iw, ih))

    val w: Float
    val h: Float
    if (ratio == null) {
        w = dx.coerceIn(minSide.coerceAtMost(maxW), maxW)
        h = dy.coerceIn(minSide.coerceAtMost(maxH), maxH)
    } else {
        val upper = min(maxW, maxH * ratio)
        val lower = max(minSide, minSide * ratio).coerceAtMost(upper)
        w = max(dx, dy * ratio).coerceIn(lower, upper)
        h = w / ratio
    }
    val l = if (dirX > 0) ax else ax - w
    val t = if (dirY > 0) ay else ay - h
    return CropRect(l / iw, t / ih, (l + w) / iw, (t + h) / ih)
}

/**
 * Interactive crop box over the image, plus a live output estimate underneath.
 * Drag a corner to resize, drag inside to move.
 */
@Composable
fun CropPreview(
    uri: Uri,
    imageSize: Size,
    crop: CropRect,
    ratio: Float?,
    onCropChange: (CropRect) -> Unit,
    format: OutputFormat,
    quality: Int,
) {
    val context = LocalContext.current
    var working by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        working = runCatching { ImageEngine.loadWorkingCopy(context, uri) }.getOrNull()
    }

    var result by remember(uri) { mutableStateOf<ImagePreviewResult?>(null) }
    var rendering by remember { mutableStateOf(false) }
    LaunchedEffect(working, imageSize, crop, format, quality) {
        val w = working ?: return@LaunchedEffect
        delay(150) // debounce: restarts on every drag step, so this runs once the finger stops
        rendering = true
        try {
            result = runCatching { ImageEngine.renderCropPreview(w, imageSize, crop, format, quality) }.getOrNull()
        } finally {
            rendering = false
        }
    }
    val originalBytes = rememberContentSize(uri)

    Column {
        val bmp = working
        if (bmp == null) {
            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            CropCanvas(bmp, imageSize, crop, ratio, onCropChange)
        }
        val r = result
        if (r != null) {
            Box {
                EstimateLine(r, originalBytes)
                if (rendering) {
                    CircularProgressIndicator(
                        Modifier.align(Alignment.CenterEnd).padding(top = 8.dp).size(14.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        } else {
            val px = crop.pixelSize(imageSize)
            Text(
                "Output: ${px.width}×${px.height}" + (originalBytes?.let { " · original ${formatBytes(it)}" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Text(
            "Drag a corner to resize, or drag inside the box to move it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The interactive crop box drawn over [working]. Also used by video Crop & rotate. */
@Composable
internal fun CropCanvas(
    working: Bitmap,
    imageSize: Size,
    crop: CropRect,
    ratio: Float?,
    onCropChange: (CropRect) -> Unit,
    /** Extra drawing inside the crop box (e.g. the passport head guide), given the box in canvas pixels. */
    guide: (DrawScope.(GeoRect) -> Unit)? = null,
) {
    val image = remember(working) { working.asImageBitmap() }
    val currentCrop by rememberUpdatedState(crop)
    val currentRatio by rememberUpdatedState(ratio)
    val currentSize by rememberUpdatedState(imageSize)
    val currentOnChange by rememberUpdatedState(onCropChange)

    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val aspect = imageSize.width.toFloat() / imageSize.height
        val maxH = 360.dp
        var dw = maxWidth
        var dh = dw / aspect
        if (dh > maxH) {
            dh = maxH
            dw = dh * aspect
        }
        Canvas(
            Modifier
                .size(dw, dh)
                .pointerInput(Unit) {
                    var handle: CropHandle? = null
                    var grab = Offset.Zero
                    var startRect = CropRect.Full
                    var startPos = Offset.Zero
                    detectDragGestures(
                        onDragStart = { pos ->
                            val r = currentCrop
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            val corners = listOf(
                                CropHandle.TOP_LEFT to Offset(r.left * w, r.top * h),
                                CropHandle.TOP_RIGHT to Offset(r.right * w, r.top * h),
                                CropHandle.BOTTOM_LEFT to Offset(r.left * w, r.bottom * h),
                                CropHandle.BOTTOM_RIGHT to Offset(r.right * w, r.bottom * h),
                            )
                            val nearest = corners.minBy { (it.second - pos).getDistance() }
                            val inside = pos.x in (r.left * w)..(r.right * w) && pos.y in (r.top * h)..(r.bottom * h)
                            handle = when {
                                (nearest.second - pos).getDistance() <= 32.dp.toPx() -> nearest.first
                                inside -> CropHandle.MOVE
                                else -> null
                            }
                            grab = if (handle == CropHandle.MOVE || handle == null) Offset.Zero else nearest.second - pos
                            startRect = r
                            startPos = pos
                        },
                        onDrag = { change, _ ->
                            val hd = handle
                            if (hd != null) {
                                change.consume()
                                val w = size.width.toFloat()
                                val h = size.height.toFloat()
                                val next = if (hd == CropHandle.MOVE) {
                                    startRect.movedBy((change.position.x - startPos.x) / w, (change.position.y - startPos.y) / h)
                                } else {
                                    val p = change.position + grab
                                    val img = currentSize
                                    // Smallest box: ~48dp on screen, converted to image pixels.
                                    val minSidePx = 48.dp.toPx() * img.width / w
                                    startRect.dragCorner(hd, Offset(p.x / w, p.y / h), img, currentRatio, minSidePx)
                                }
                                currentOnChange(next)
                            }
                        },
                        onDragEnd = { handle = null },
                        onDragCancel = { handle = null },
                    )
                },
        ) {
            drawImage(
                image,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = FilterQuality.Medium,
            )
            drawCropOverlay(crop)
            guide?.invoke(this, GeoRect(crop.left * size.width, crop.top * size.height, crop.right * size.width, crop.bottom * size.height))
        }
    }
}

private fun DrawScope.drawCropOverlay(crop: CropRect) {
    val l = crop.left * size.width
    val t = crop.top * size.height
    val r = crop.right * size.width
    val b = crop.bottom * size.height
    val dim = Color.Black.copy(alpha = 0.55f)

    // Darken everything outside the box.
    drawRect(dim, Offset.Zero, GeoSize(size.width, t))
    drawRect(dim, Offset(0f, b), GeoSize(size.width, size.height - b))
    drawRect(dim, Offset(0f, t), GeoSize(l, b - t))
    drawRect(dim, Offset(r, t), GeoSize(size.width - r, b - t))

    // Rule-of-thirds grid.
    val grid = Color.White.copy(alpha = 0.45f)
    val thin = 1.dp.toPx()
    for (i in 1..2) {
        val x = l + (r - l) * i / 3f
        val y = t + (b - t) * i / 3f
        drawLine(grid, Offset(x, t), Offset(x, b), thin)
        drawLine(grid, Offset(l, y), Offset(r, y), thin)
    }

    // Border and corner handles.
    drawRect(Color.White, Offset(l, t), GeoSize(r - l, b - t), style = Stroke(1.5.dp.toPx()))
    val len = min(18.dp.toPx(), min(r - l, b - t) / 2f)
    val thick = 4.dp.toPx()
    listOf(
        Triple(Offset(l, t), 1f, 1f),
        Triple(Offset(r, t), -1f, 1f),
        Triple(Offset(l, b), 1f, -1f),
        Triple(Offset(r, b), -1f, -1f),
    ).forEach { (c, sx, sy) ->
        drawLine(Color.White, c, Offset(c.x + len * sx, c.y), thick)
        drawLine(Color.White, c, Offset(c.x, c.y + len * sy), thick)
    }
}
