package com.toolx.app.tools.image

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toolx.app.core.ProcessingViewModel
import com.toolx.app.core.SavedMedia
import com.toolx.app.tools.Tool
import com.toolx.app.tools.image.BlurOps.Box
import com.toolx.app.tools.image.BlurOps.Effect
import com.toolx.app.ui.common.ExportButton
import com.toolx.app.ui.common.Note
import com.toolx.app.ui.common.OptionChips
import com.toolx.app.ui.common.ProcessingStatus
import com.toolx.app.ui.common.SectionLabel
import androidx.compose.foundation.systemGestureExclusion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Where the effect goes. */
enum class BlurArea(val label: String) { BOXES("Areas you draw"), BACKGROUND("Background"), PEOPLE("People") }

private val EffectLabels = mapOf(Effect.BLUR to "Blur", Effect.PIXELATE to "Pixelate", Effect.SOLID to "Black box")

object BlurEngine {
    /** Three full-size buffers plus the bitmap; 12 MP keeps that under ~200 MB. */
    private const val MAX_EXPORT_PIXELS = 12_000_000L

    /** Person confidence → soft 0..1 mask, inverted for "background". */
    fun areaMask(area: BlurArea, boxes: List<Box>, person: FloatArray?, pw: Int, ph: Int, w: Int, h: Int): FloatArray = when (area) {
        BlurArea.BOXES -> BlurOps.boxMask(boxes, w, h)
        else -> {
            val p = person ?: return FloatArray(w * h)
            val soft = MaskOps.toAlpha(p, 0.5f, 0.3f).let { a -> FloatArray(a.size) { a[it] / 255f } }
            val m = if (pw == w && ph == h) soft else MaskOps.resample(soft, pw, ph, w, h)
            if (area == BlurArea.BACKGROUND) for (i in m.indices) m[i] = 1f - m[i]
            m
        }
    }

    /**
     * Applies the effect only inside the bounding box of [mask] (plus enough margin for the blur
     * to see its neighbours), so a couple of small boxes on a big photo stay quick.
     */
    fun render(src: IntArray, w: Int, h: Int, effect: Effect, strength: Float, mask: FloatArray): IntArray {
        var x0 = w; var y0 = h; var x1 = -1; var y1 = -1
        for (y in 0 until h) for (x in 0 until w) if (mask[y * w + x] > 0f) {
            if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
        }
        if (x1 < 0) return src.copyOf()
        val size = BlurOps.sizeFor(effect, strength, max(w, h))
        val margin = if (effect == Effect.BLUR) size * 3 else 0
        // Pixelate cells stay aligned to the whole image, so preview and export match.
        val align = if (effect == Effect.PIXELATE) size else 1
        val cx0 = ((x0 - margin).coerceAtLeast(0) / align) * align
        val cy0 = ((y0 - margin).coerceAtLeast(0) / align) * align
        val cx1 = min(w, x1 + 1 + margin); val cy1 = min(h, y1 + 1 + margin)
        val cw = cx1 - cx0; val ch = cy1 - cy0
        val sub = IntArray(cw * ch) { src[(cy0 + it / cw) * w + cx0 + it % cw] }
        val subMask = FloatArray(cw * ch) { mask[(cy0 + it / cw) * w + cx0 + it % cw] }
        // Effect size is relative to the whole image, not the crop.
        val fx = when (effect) {
            Effect.BLUR -> BlurOps.blur(sub, cw, ch, size)
            Effect.PIXELATE -> BlurOps.pixelate(sub, cw, ch, size)
            Effect.SOLID -> IntArray(sub.size) { 0xFF000000.toInt() }
        }
        val done = BlurOps.blend(sub, fx, subMask)
        val out = src.copyOf()
        for (y in 0 until ch) System.arraycopy(done, y * cw, out, (cy0 + y) * w + cx0, cw)
        return out
    }

    fun pixels(b: Bitmap) = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }

    suspend fun export(
        context: Context, uri: Uri, effect: Effect, strength: Float, area: BlurArea, boxes: List<Box>, seg: Segmented?,
    ): SavedMedia = withContext(Dispatchers.Default) {
        val size = ImageEngine.readSize(context, uri)
        val target = size?.let {
            val px = it.width.toLong() * it.height
            if (px <= MAX_EXPORT_PIXELS) null else sqrt(MAX_EXPORT_PIXELS.toDouble() / px).let { k -> Size(max(1, (it.width * k).toInt()), max(1, (it.height * k).toInt())) }
        }
        val decoded = ImageEngine.decode(context, uri, target, mutable = true)
        val bmp = if (decoded.config == Bitmap.Config.ARGB_8888 && decoded.isMutable) decoded
        else decoded.copy(Bitmap.Config.ARGB_8888, true).also { decoded.recycle() }
        val w = bmp.width; val h = bmp.height
        val mask = areaMask(area, boxes, seg?.confidence, seg?.width ?: 0, seg?.height ?: 0, w, h)
        val out = render(pixels(bmp), w, h, effect, strength, mask)
        bmp.setPixels(out, 0, w, 0, 0, w, h)
        val png = context.contentResolver.getType(uri) == "image/png"
        ImageEngine.save(context, bmp, "blurred", if (png) OutputFormat.PNG else OutputFormat.JPEG, 95)
    }
}

private val BoxListSaver = Saver<List<Box>, FloatArray>(
    save = { list -> FloatArray(list.size * 4).also { a -> list.forEachIndexed { i, b -> a[i * 4] = b.l; a[i * 4 + 1] = b.t; a[i * 4 + 2] = b.r; a[i * 4 + 3] = b.b } } },
    restore = { a -> List(a.size / 4) { Box(a[it * 4], a[it * 4 + 1], a[it * 4 + 2], a[it * 4 + 3]) } },
)

@Composable
fun BlurScreen(tool: Tool, onBack: () -> Unit) {
    val vm: ProcessingViewModel = viewModel()
    val state by vm.state.collectAsState()
    var effect by rememberSaveable { mutableStateOf(Effect.PIXELATE) }
    var area by rememberSaveable { mutableStateOf(BlurArea.BOXES) }
    var strength by rememberSaveable { mutableFloatStateOf(0.5f) }
    var boxes by rememberSaveable(stateSaver = BoxListSaver) { mutableStateOf(emptyList()) }
    val context = androidx.compose.ui.platform.LocalContext.current

    PhotoFrame(tool, onBack, onNew = { vm.reset(); boxes = emptyList() }) { uri, working ->
        val src = remember(working) { BlurEngine.pixels(working) }
        // People are found once per photo, and only when an option needs them.
        var seg by remember(uri) { mutableStateOf<Segmented?>(null) }
        var segFailed by remember(uri) { mutableStateOf(false) }
        LaunchedEffect(uri, area) {
            if (area != BlurArea.BOXES && seg == null && !segFailed) {
                seg = runCatching { BackgroundEngine.segment(context, uri) }.getOrNull()
                segFailed = seg == null
            }
        }
        val needsPeople = area != BlurArea.BOXES
        val personShare = remember(seg) { seg?.let { s -> s.confidence.count { it > 0.5f }.toFloat() / s.confidence.size } }

        var shown by remember(working) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(working, effect, strength, area, boxes, seg) {
            if (shown != null) delay(120)
            shown = withContext(Dispatchers.Default) {
                val w = working.width; val h = working.height
                val s = seg
                val mask = BlurEngine.areaMask(area, boxes, s?.confidence, s?.width ?: 0, s?.height ?: 0, w, h)
                Bitmap.createBitmap(BlurEngine.render(src, w, h, effect, strength, mask), w, h, Bitmap.Config.ARGB_8888)
            }
        }

        BoxCanvas(shown ?: working, boxes.takeIf { area == BlurArea.BOXES }) { boxes = boxes + it; vm.reset() }

        SectionLabel("Hide")
        OptionChips(BlurArea.entries, area, label = { it.label }) { area = it; vm.reset() }
        when {
            area == BlurArea.BOXES && boxes.isEmpty() -> Note("Drag on the photo to draw a box over what you want to hide. Draw as many as you need.")
            area == BlurArea.BOXES -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { boxes = boxes.dropLast(1); vm.reset() }) { Text("Undo box") }
                OutlinedButton(onClick = { boxes = emptyList(); vm.reset() }) { Text("Clear all") }
            }
            segFailed -> Note("Couldn't look for people in this photo.", error = true)
            seg == null -> Note("Finding people…")
            (personShare ?: 0f) < 0.005f -> Note("No person found in this photo. Use \"Areas you draw\" instead.", error = true)
            area == BlurArea.BACKGROUND -> Note("Blurs everything except people, like portrait mode. People only: it doesn't find pets or objects.")
            else -> Note("Hides every person in the photo.")
        }

        SectionLabel("Effect")
        OptionChips(Effect.entries, effect, label = { EffectLabels.getValue(it) }) { effect = it; vm.reset() }
        if (effect != Effect.SOLID) {
            SectionLabel("Strength ${(strength * 100).roundToInt()}%")
            Slider(strength, { strength = it; vm.reset() }, valueRange = 0.05f..1f, modifier = Modifier.fillMaxWidth().systemGestureExclusion())
        }
        if (area != BlurArea.BACKGROUND) Note("To hide private details for good, use Pixelate at high strength or Black box. A light blur can sometimes be partly undone.")

        val ready = when (area) {
            BlurArea.BOXES -> boxes.isNotEmpty()
            else -> seg != null && (personShare ?: 0f) >= 0.005f
        }
        ExportButton("Save", state, enabled = ready && !(needsPeople && seg == null)) {
            val e = effect; val s = strength; val a = area; val b = boxes; val sg = seg
            vm.run { ctx, _ -> BlurEngine.export(ctx, uri, e, s, a, b, sg) }
        }
        ProcessingStatus(state, onCancel = vm::cancel)
    }
}

/** The preview, with box drawing by drag when [boxes] isn't null. */
@Composable
private fun BoxCanvas(bitmap: Bitmap, boxes: List<Box>?, onBox: (Box) -> Unit) {
    val img = remember(bitmap) { bitmap.asImageBitmap() }
    var drawing by remember { mutableStateOf<Box?>(null) }
    val add by rememberUpdatedState(onBox)
    val accent = MaterialTheme.colorScheme.primary
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val aspect = bitmap.width.toFloat() / bitmap.height
        var w = maxWidth
        var h = w / aspect
        if (h > 420.dp) { h = 420.dp; w = h * aspect }
        Canvas(
            Modifier.size(w, h).then(
                if (boxes == null) Modifier else Modifier.pointerInput(Unit) {
                    fun n(o: Offset) = Offset((o.x / size.width).coerceIn(0f, 1f), (o.y / size.height).coerceIn(0f, 1f))
                    var start = Offset.Zero
                    detectDragGestures(
                        onDragStart = { start = n(it); drawing = Box(start.x, start.y, start.x, start.y) },
                        onDrag = { change, _ -> change.consume(); val p = n(change.position); drawing = Box(start.x, start.y, p.x, p.y) },
                        onDragEnd = { drawing?.normalized()?.takeIf { it.isUsable }?.let(add); drawing = null },
                        onDragCancel = { drawing = null },
                    )
                },
            ),
        ) {
            drawImage(img, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
            fun rect(b: Box, color: Color, dashed: Boolean) {
                val n = b.normalized()
                val tl = Offset(n.l * size.width, n.t * size.height)
                val sz = GSize((n.r - n.l) * size.width, (n.b - n.t) * size.height)
                drawRect(Color.Black.copy(alpha = 0.6f), tl, sz, style = Stroke(4.dp.toPx()))
                drawRect(color, tl, sz, style = Stroke(2.dp.toPx(), pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(12f, 8f)) else null))
            }
            boxes?.forEach { rect(it, Color.White, false) }
            drawing?.let { rect(it, accent, true) }
        }
    }
}
