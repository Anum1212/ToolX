package com.toolx.app.tools.pdf

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeoSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toolx.app.core.ProcessingViewModel
import com.toolx.app.core.formatBytes
import com.toolx.app.tools.Tool
import com.toolx.app.tools.image.CropRect
import com.toolx.app.ui.common.ExportButton
import com.toolx.app.ui.common.Note
import com.toolx.app.ui.common.NumberField
import com.toolx.app.ui.common.OptionChips
import com.toolx.app.ui.common.ProcessingStatus
import com.toolx.app.ui.common.SectionLabel
import com.toolx.app.ui.common.TextInput
import com.toolx.app.ui.common.ToggleChip
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ---------- shared ----------

/**
 * Renders page [index] with [op] applied (on a one-page copy), ~250 ms after the last change
 * of [key], so the preview is exactly what the export produces.
 */
@Composable
private fun StampPreview(file: File, index: Int, key: Any, op: (PDDocument, PDPage) -> Unit) {
    val context = LocalContext.current
    var bmp by remember(file, index) { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(file, index, key) {
        if (bmp != null) delay(250)
        busy = true
        try {
            bmp = PdfEngine.previewPage(context, file, index, 900, op)
        } catch (e: CancellationException) { throw e } catch (_: Throwable) {
        } finally { busy = false }
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b == null) Busy("Rendering preview…") else FittedImage(b, "Preview of page ${index + 1}")
        if (busy && b != null) CircularProgressIndicator(Modifier.align(Alignment.TopEnd).padding(8.dp).size(18.dp), strokeWidth = 2.dp)
    }
}

/** [bitmap] scaled to the full width, at most 420 dp tall, with a thin outline. */
@Composable
private fun FittedImage(bitmap: Bitmap, description: String) {
    val img = remember(bitmap) { bitmap.asImageBitmap() }
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val aspect = bitmap.width.toFloat() / bitmap.height
        var w = maxWidth
        var h = w / aspect
        if (h > 420.dp) { h = 420.dp; w = h * aspect }
        Image(img, description, Modifier.size(w, h).border(1.dp, MaterialTheme.colorScheme.outlineVariant), contentScale = ContentScale.Fit)
    }
}

/** Page sized like [FittedImage]; [content] draws on top in the same box (gestures included). */
@Composable
private fun PageCanvasBox(page: Bitmap, content: @Composable (w: androidx.compose.ui.unit.Dp, h: androidx.compose.ui.unit.Dp) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val aspect = page.width.toFloat() / page.height
        var w = maxWidth
        var h = w / aspect
        if (h > 460.dp) { h = 460.dp; w = h * aspect }
        content(w, h)
    }
}

// ---------- Page numbers ----------

@Composable
fun PdfPageNumbersScreen(tool: Tool, onBack: () -> Unit) {
    val vm: ProcessingViewModel = viewModel()
    val state by vm.state.collectAsState()
    var format by rememberSaveable { mutableStateOf(PdfOps.NumberFormat.PLAIN) }
    var spot by rememberSaveable { mutableStateOf(PdfOps.Spot.BOTTOM_CENTER) }
    var startText by rememberSaveable { mutableStateOf("1") }
    var size by rememberSaveable { mutableFloatStateOf(11f) }
    var skipFirst by rememberSaveable { mutableStateOf(false) }

    PdfToolFrame(tool, onBack, onNewFile = { vm.reset() }) { _, pdf ->
        val ready = pdf as PdfState.Ready
        val total = ready.pages.pageCount
        val style = PdfEngine.NumberStyle(format, spot, startText.toIntOrNull() ?: 1, size, skipFirst)
        val previewIndex = if (skipFirst && total > 1) 1 else 0
        StampPreview(ready.file, previewIndex, style) { doc, page -> PdfEngine.stampNumber(doc, page, previewIndex, total, style) }
        SectionLabel("Position")
        OptionChips(PdfOps.Spot.entries.filter { it != PdfOps.Spot.CENTER }, spot, label = { it.label }) { spot = it; vm.reset() }
        SectionLabel("Style")
        OptionChips(PdfOps.NumberFormat.entries, format, label = { it.label }) { format = it; vm.reset() }
        SectionLabel("Size: ${size.roundToInt()} pt")
        Slider(size, { size = it; vm.reset() }, valueRange = 7f..24f, modifier = Modifier.fillMaxWidth().systemGestureExclusion())
        NumberField(startText, { startText = it; vm.reset() }, "Start at", integerOnly = true, allowNegative = false)
        ToggleChip("Skip the first page (cover)", skipFirst) { skipFirst = it; vm.reset() }
        ExportButton("Add page numbers", state) {
            vm.run { ctx, _ -> PdfEngine.pageNumbers(ctx, ready.file, style) }
        }
        ProcessingStatus(state, onCancel = vm::cancel)
    }
}

// ---------- Watermark ----------

private val WatermarkColors = listOf("Grey" to 0xFF808080.toInt(), "Red" to 0xFFD32F2F.toInt(), "Blue" to 0xFF1565C0.toInt(), "Black" to 0xFF000000.toInt())

@Composable
fun PdfWatermarkScreen(tool: Tool, onBack: () -> Unit) {
    val vm: ProcessingViewModel = viewModel()
    val state by vm.state.collectAsState()
    var text by rememberSaveable { mutableStateOf("CONFIDENTIAL") }
    var size by rememberSaveable { mutableFloatStateOf(54f) }
    var opacity by rememberSaveable { mutableFloatStateOf(0.25f) }
    var angle by rememberSaveable { mutableIntStateOf(45) }
    var color by rememberSaveable { mutableIntStateOf(WatermarkColors[0].second) }
    var tiled by rememberSaveable { mutableStateOf(false) }

    PdfToolFrame(tool, onBack, onNewFile = { vm.reset() }) { _, pdf ->
        val ready = pdf as PdfState.Ready
        val style = PdfEngine.WatermarkStyle(text.trim(), size, opacity, angle, color, tiled)
        StampPreview(ready.file, 0, style) { doc, page -> PdfEngine.stampWatermark(doc, page, style) }
        TextInput(text, { text = it.take(80); vm.reset() }, "Watermark text")
        SectionLabel("Layout")
        OptionChips(listOf(false, true), tiled, label = { if (it) "Tiled" else "Centre" }) { tiled = it; vm.reset() }
        SectionLabel("Angle")
        OptionChips(listOf(0, 30, 45, 90), angle, label = { "$it°" }) { angle = it; vm.reset() }
        SectionLabel("Colour")
        OptionChips(WatermarkColors.map { it.second }, color, label = { c -> WatermarkColors.first { it.second == c }.first }) { color = it; vm.reset() }
        SectionLabel("Size: ${size.roundToInt()} pt")
        Slider(size, { size = it; vm.reset() }, valueRange = 16f..120f, modifier = Modifier.fillMaxWidth().systemGestureExclusion())
        SectionLabel("Opacity: ${(opacity * 100).roundToInt()}%")
        Slider(opacity, { opacity = it; vm.reset() }, valueRange = 0.05f..1f, modifier = Modifier.fillMaxWidth().systemGestureExclusion())
        ExportButton("Add watermark", state, enabled = text.isNotBlank()) {
            vm.run { ctx, _ -> PdfEngine.watermark(ctx, ready.file, style) }
        }
        ProcessingStatus(state, onCancel = vm::cancel)
    }
}

// ---------- Sign ----------

/** Finger signature pad; returns the strokes as a tightly cropped, transparent bitmap. */
@Composable
private fun SignaturePad(onDone: (Bitmap) -> Unit, onDismiss: () -> Unit) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var ink by remember { mutableIntStateOf(0xFF0D1B4C.toInt()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Sign with your finger", style = MaterialTheme.typography.titleMedium)
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color.White)
                        .border(1.dp, MaterialTheme.colorScheme.outline)
                        .systemGestureExclusion()
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { strokes.add(listOf(it)) },
                                onDrag = { change, _ ->
                                    change.consume()
                                    strokes[strokes.lastIndex] = strokes.last() + change.position
                                },
                            )
                        },
                ) {
                    canvasSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                    // Guide line.
                    drawLine(Color(0xFFBDBDBD), Offset(16f, size.height * 0.75f), Offset(size.width - 16f, size.height * 0.75f), 1.dp.toPx())
                    strokes.forEach { s ->
                        if (s.size == 1) drawCircle(Color(ink), 1.5.dp.toPx(), s[0])
                        for (i in 1 until s.size) drawLine(Color(ink), s[i - 1], s[i], 3.dp.toPx(), StrokeCap.Round)
                    }
                }
                OptionChips(listOf(0xFF0D1B4C.toInt(), 0xFF000000.toInt()), ink, label = { if (it == 0xFF000000.toInt()) "Black" else "Blue ink" }) { ink = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { strokes.clear() }) { Text("Clear") }
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = { signatureBitmap(strokes, ink)?.let(onDone) }, enabled = strokes.isNotEmpty()) { Text("Use") }
                }
            }
        }
    }
}

/** Draws [strokes] into a transparent bitmap cropped to their bounds, at 3× for print sharpness. */
private fun signatureBitmap(strokes: List<List<Offset>>, ink: Int): Bitmap? {
    val pts = strokes.flatten()
    if (pts.isEmpty()) return null
    val scale = 3f
    val pad = 12f
    val minX = pts.minOf { it.x } - pad; val minY = pts.minOf { it.y } - pad
    val w = ((pts.maxOf { it.x } + pad - minX) * scale).roundToInt().coerceIn(1, 4000)
    val h = ((pts.maxOf { it.y } + pad - minY) * scale).roundToInt().coerceIn(1, 2000)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink; style = Paint.Style.STROKE; strokeWidth = 7f * scale / 2f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    val canvas = android.graphics.Canvas(bmp)
    strokes.forEach { s ->
        val path = Path()
        s.forEachIndexed { i, p ->
            val x = (p.x - minX) * scale; val y = (p.y - minY) * scale
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        if (s.size == 1) canvas.drawPoint((s[0].x - minX) * scale, (s[0].y - minY) * scale, paint) else canvas.drawPath(path, paint)
    }
    return bmp
}

/** Page with a movable, resizable [overlay] box. Drag inside to move; drag the corner dot to resize. */
@Composable
private fun PlacementCanvas(page: Bitmap, overlay: Bitmap, rect: CropRect, onRect: (CropRect) -> Unit) {
    val pageImg = remember(page) { page.asImageBitmap() }
    val overlayImg = remember(overlay) { overlay.asImageBitmap() }
    val current by rememberUpdatedState(rect)
    val update by rememberUpdatedState(onRect)
    PageCanvasBox(page) { w, h ->
        Canvas(
            Modifier.size(w, h).pointerInput(Unit) {
                var resizing = false
                var moving = false
                detectDragGestures(
                    onDragStart = { pos ->
                        val r = current
                        val corner = Offset(r.right * size.width, r.bottom * size.height)
                        resizing = (corner - pos).getDistance() < 36.dp.toPx()
                        moving = !resizing && pos.x / size.width in r.left..r.right && pos.y / size.height in r.top..r.bottom
                    },
                    onDrag = { change, drag ->
                        val r = current
                        if (moving) { change.consume(); update(r.movedBy(drag.x / size.width, drag.y / size.height)) }
                        if (resizing) {
                            change.consume()
                            // Keep the signature's proportions (in screen pixels).
                            val ratio = r.height * size.height / (r.width * size.width)
                            val maxW = min(1f - r.left, (1f - r.top) * size.height / (ratio * size.width))
                            val nw = (change.position.x / size.width - r.left).coerceIn(0.05f, maxW)
                            val nh = nw * size.width * ratio / size.height
                            update(CropRect(r.left, r.top, r.left + nw, r.top + nh))
                        }
                    },
                )
            },
        ) {
            drawImage(pageImg, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
            val l = rect.left * size.width; val t = rect.top * size.height
            val rw = rect.width * size.width; val rh = rect.height * size.height
            drawImage(overlayImg, dstOffset = IntOffset(l.roundToInt(), t.roundToInt()), dstSize = IntSize(rw.roundToInt().coerceAtLeast(1), rh.roundToInt().coerceAtLeast(1)))
            drawRect(Color(0xFF1E88E5), Offset(l, t), GeoSize(rw, rh), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 5.dp.toPx()))))
            drawCircle(Color(0xFF1E88E5), 8.dp.toPx(), Offset(l + rw, t + rh))
        }
    }
}

@Composable
fun PdfSignScreen(tool: Tool, onBack: () -> Unit) {
    val vm: ProcessingViewModel = viewModel()
    val state by vm.state.collectAsState()
    var signature by remember { mutableStateOf<Bitmap?>(null) }
    var padOpen by remember { mutableStateOf(false) }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var rect by remember { mutableStateOf<CropRect?>(null) }

    PdfToolFrame(tool, onBack, onNewFile = { page = 0; vm.reset() }) { _, pdf ->
        val ready = pdf as PdfState.Ready
        var pageBmp by remember(ready, page) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(ready, page) { pageBmp = ready.pages.render(page, 1000) }
        val sig = signature
        // The box is stored as page fractions; on a page of another shape (e.g. landscape),
        // re-derive its height so the signature keeps its proportions.
        LaunchedEffect(pageBmp, sig) {
            val pb = pageBmp ?: return@LaunchedEffect
            val s = sig ?: return@LaunchedEffect
            val r = rect ?: return@LaunchedEffect
            val h = r.width * pb.width / pb.height * s.height / s.width
            val w = if (h > 1f) r.width / h else r.width
            val hh = min(h, 1f)
            val top = r.top.coerceIn(0f, 1f - hh)
            val left = r.left.coerceIn(0f, 1f - w)
            rect = CropRect(left, top, left + w, top + hh)
        }
        if (padOpen) SignaturePad(onDone = { bmp ->
            signature = bmp; padOpen = false; vm.reset()
            // Default spot: lower right, 35% of the page width, keeping the signature's shape.
            val pb = pageBmp
            val wNorm = 0.35f
            val hNorm = if (pb != null) wNorm * pb.width / pb.height * bmp.height / bmp.width else 0.08f
            rect = CropRect(0.58f, (0.86f - hNorm).coerceAtLeast(0f), 0.58f + wNorm, 0.86f.coerceAtLeast(hNorm))
        }, onDismiss = { padOpen = false })

        if (ready.pages.pageCount > 1) PageStepper(page, ready.pages.pageCount) { page = it; vm.reset() }
        val pb = pageBmp
        val r = rect
        when {
            pb == null -> Busy("Rendering page…")
            sig == null || r == null -> FittedImage(pb, "Page ${page + 1}")
            else -> {
                PlacementCanvas(pb, sig, r) { rect = it; vm.reset() }
                Note("Drag the signature to move it; drag the blue dot to resize.")
            }
        }
        FilledTonalButton(onClick = { padOpen = true }) {
            Icon(Icons.Rounded.Draw, null, Modifier.size(18.dp)); Text(if (sig == null) "  Draw your signature" else "  Draw again")
        }
        Note("This adds a visible signature image, like signing on paper. It isn't a certificate-based digital signature.")
        ExportButton("Sign page ${page + 1}", state, enabled = sig != null && r != null) {
            val s = sig ?: return@ExportButton
            val box = r ?: return@ExportButton
            val p = page
            vm.run { ctx, _ -> PdfEngine.sign(ctx, ready.file, s, p, box) }
        }
        ProcessingStatus(state, onCancel = vm::cancel)
    }
}

// ---------- Redact ----------

@Composable
private fun RedactCanvas(page: Bitmap, boxes: List<CropRect>, onAdd: (CropRect) -> Unit, onRemove: (Int) -> Unit) {
    val img = remember(page) { page.asImageBitmap() }
    val current by rememberUpdatedState(boxes)
    var drag by remember { mutableStateOf<Pair<Offset, Offset>?>(null) }
    PageCanvasBox(page) { w, h ->
        Canvas(
            Modifier
                .size(w, h)
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        val x = pos.x / size.width; val y = pos.y / size.height
                        val hit = current.indexOfLast { x in it.left..it.right && y in it.top..it.bottom }
                        if (hit >= 0) onRemove(hit)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { drag = it to it },
                        onDrag = { change, _ -> change.consume(); drag = drag?.copy(second = change.position) },
                        onDragEnd = {
                            drag?.let { (a, b) ->
                                if (abs(a.x - b.x) > 6.dp.toPx() && abs(a.y - b.y) > 6.dp.toPx()) {
                                    fun nx(v: Float) = (v / size.width).coerceIn(0f, 1f)
                                    fun ny(v: Float) = (v / size.height).coerceIn(0f, 1f)
                                    onAdd(CropRect(nx(min(a.x, b.x)), ny(min(a.y, b.y)), nx(max(a.x, b.x)), ny(max(a.y, b.y))))
                                }
                            }
                            drag = null
                        },
                        onDragCancel = { drag = null },
                    )
                },
        ) {
            drawImage(img, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
            boxes.forEach { b ->
                drawRect(Color.Black, Offset(b.left * size.width, b.top * size.height), GeoSize(b.width * size.width, b.height * size.height))
            }
            drag?.let { (a, b) ->
                drawRect(
                    Color(0xFFD32F2F), Offset(min(a.x, b.x), min(a.y, b.y)), GeoSize(abs(a.x - b.x), abs(a.y - b.y)),
                    style = Stroke(2.dp.toPx(), join = StrokeJoin.Miter),
                )
            }
        }
    }
}

@Composable
fun PdfRedactScreen(tool: Tool, onBack: () -> Unit) {
    val vm: ProcessingViewModel = viewModel()
    val state by vm.state.collectAsState()
    var page by rememberSaveable { mutableIntStateOf(0) }
    val boxes = remember { mutableStateMapOf<Int, List<CropRect>>() }

    PdfToolFrame(tool, onBack, onNewFile = { page = 0; boxes.clear(); vm.reset() }) { _, pdf ->
        val ready = pdf as PdfState.Ready
        var pageBmp by remember(ready, page) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(ready, page) { pageBmp = ready.pages.render(page, 1000) }
        if (ready.pages.pageCount > 1) PageStepper(page, ready.pages.pageCount) { page = it }
        val pb = pageBmp
        val here = boxes[page].orEmpty()
        if (pb == null) Busy("Rendering page…") else RedactCanvas(
            pb, here,
            onAdd = { boxes[page] = here + it; vm.reset() },
            onRemove = { i -> boxes[page] = here.filterIndexed { k, _ -> k != i }; vm.reset() },
        )
        val total = boxes.values.sumOf { it.size }
        val pagesWith = boxes.filterValues { it.isNotEmpty() }.keys
        Note("Drag to black out an area; tap a box to remove it." + if (total > 0) " $total box(es) on page(s) ${PdfOps.formatPages(pagesWith)}." else "")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { boxes[page] = here.dropLast(1); vm.reset() }, enabled = here.isNotEmpty()) { Text("Undo") }
            OutlinedButton(onClick = { boxes.clear(); vm.reset() }, enabled = total > 0) { Text("Clear all") }
        }
        Note("Pages with boxes are turned into images, so the hidden text is truly gone (it can't be copied or searched). Other pages stay as they are.")
        ExportButton("Redact", state, enabled = total > 0) {
            val b = boxes.toMap()
            vm.run { ctx, _ -> PdfEngine.redact(ctx, ready.file, b) }
        }
        ProcessingStatus(state, onCancel = vm::cancel)
    }
}

// ---------- Protect / Unlock ----------

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun PdfProtectScreen(tool: Tool, onBack: () -> Unit) {
    val vm: ProcessingViewModel = viewModel()
    val state by vm.state.collectAsState()
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var allowPrint by rememberSaveable { mutableStateOf(true) }
    var allowCopy by rememberSaveable { mutableStateOf(false) }

    PdfToolFrame(tool, onBack, onNewFile = { vm.reset() }) { _, pdf ->
        val ready = pdf as PdfState.Ready
        PasswordField(password, { password = it; vm.reset() }, "Password")
        PasswordField(confirm, { confirm = it; vm.reset() }, "Repeat password")
        val mismatch = confirm.isNotEmpty() && confirm != password
        if (mismatch) Note("The passwords don't match.", error = true)
        if (password.isNotEmpty() && password.length < 6) Note("Short passwords are easy to guess; 8+ characters is better.")
        ToggleChip("Allow printing", allowPrint) { allowPrint = it; vm.reset() }
        ToggleChip("Allow copying text", allowCopy) { allowCopy = it; vm.reset() }
        Note("Encrypted with AES-256. There's no way to recover a forgotten password, so keep the original file or the password safe.")
        ExportButton("Protect", state, enabled = password.isNotEmpty() && password == confirm) {
            val p = password; val pr = allowPrint; val cp = allowCopy
            vm.run { ctx, _ -> PdfEngine.protect(ctx, ready.file, p, pr, cp) }
        }
        ProcessingStatus(state, onCancel = vm::cancel)
    }
}

@Composable
fun PdfUnlockScreen(tool: Tool, onBack: () -> Unit) {
    val vm: ProcessingViewModel = viewModel()
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var password by remember { mutableStateOf("") }

    PdfToolFrame(tool, onBack, onNewFile = { password = ""; vm.reset() }, acceptLocked = true) { _, pdf ->
        when (pdf) {
            is PdfState.Locked -> {
                Note("Enter the password to open this PDF. The saved copy won't need it.")
                PasswordField(password, { password = it; vm.reset() }, "Password")
                ExportButton("Unlock", state, enabled = password.isNotEmpty()) {
                    val p = password
                    vm.run { ctx, _ -> PdfEngine.unlock(ctx, pdf.file, p) }
                }
            }
            is PdfState.Ready -> {
                var encrypted by remember(pdf) { mutableStateOf<Boolean?>(null) }
                LaunchedEffect(pdf) { encrypted = PdfEngine.isEncrypted(context, pdf.file) }
                when (encrypted) {
                    null -> Busy("Checking…")
                    false -> Note("This PDF isn't protected; there's nothing to unlock.")
                    true -> {
                        Note("This PDF opens without a password but has restrictions (such as no printing or copying). Save a copy without them?")
                        ExportButton("Remove restrictions", state) {
                            vm.run { ctx, _ -> PdfEngine.unlock(ctx, pdf.file, "") }
                        }
                    }
                }
            }
            else -> Unit
        }
        ProcessingStatus(state, onCancel = vm::cancel)
    }
}

// ---------- Compress ----------

@Composable
fun PdfCompressScreen(tool: Tool, onBack: () -> Unit) {
    val vm: ProcessingViewModel = viewModel()
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var level by rememberSaveable { mutableStateOf(PdfOps.Compression.MEDIUM) }

    PdfToolFrame(tool, onBack, onNewFile = { vm.reset() }) { _, pdf ->
        val ready = pdf as PdfState.Ready
        val results = remember(ready) { mutableStateMapOf<PdfOps.Compression, PdfEngine.Compressed>() }
        var failed by remember(ready) { mutableStateOf<String?>(null) }
        // Compress at every level in the background, so each choice shows its real size.
        LaunchedEffect(ready) {
            val order = listOf(level) + PdfOps.Compression.entries.filter { it != level }
            for (l in order) {
                try { results[l] = PdfEngine.compress(context, ready.file, l) }
                catch (e: CancellationException) { throw e }
                catch (e: Throwable) { failed = e.message ?: "Couldn't compress this PDF"; break }
            }
        }
        DisposableEffect(ready) { onDispose { results.values.forEach { it.file.delete() } } }

        OptionChips(PdfOps.Compression.entries, level, label = { l ->
            l.label + (results[l]?.let { " · ${formatBytes(it.after)}" } ?: "")
        }) { level = it; vm.reset() }
        Note(level.note)
        val r = results[level]
        failed?.let { Note(it, error = true) }
        if (r == null && failed == null) Busy("Compressing to measure the size…")
        if (r != null) {
            val smaller = r.after < r.before
            Text(
                if (smaller) "${formatBytes(r.before)} → ${formatBytes(r.after)} (${((1 - r.after.toDouble() / r.before) * 100).roundToInt()}% smaller)"
                else "Can't make this PDF smaller at this level (it's ${formatBytes(r.before)}).",
                style = MaterialTheme.typography.titleSmall,
                color = if (smaller) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Note(if (r.images > 0) "${r.images} image(s) re-encoded. Text and drawings are untouched." else "No images could be shrunk; most of this file is text or drawings.")
            // The first page with a re-encoded image, from the compressed result, to judge quality.
            val pages = remember(r) { runCatching { PdfPages(r.file) }.getOrNull() }
            DisposableEffect(pages) { onDispose { pages?.close() } }
            if (pages != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PageImage(pages, r.firstImagePage, 300.dp) }
                Note("Page ${r.firstImagePage + 1} after compression.")
            }
            ExportButton("Save", state, enabled = smaller) {
                vm.run { ctx, _ -> PdfEngine.saveCompressed(ctx, r) }
            }
        }
        ProcessingStatus(state, onCancel = vm::cancel)
    }
}

// ---------- Repair ----------

@Composable
fun PdfRepairScreen(tool: Tool, onBack: () -> Unit) {
    val vm: ProcessingViewModel = viewModel()
    val state by vm.state.collectAsState()
    var recovered by remember { mutableStateOf<Int?>(null) }

    PdfToolFrame(tool, onBack, onNewFile = { recovered = null; vm.reset() }, acceptBroken = true, acceptLocked = false) { _, pdf ->
        val file = when (pdf) {
            is PdfState.Ready -> pdf.file
            is PdfState.Failed -> pdf.file
            else -> null
        } ?: return@PdfToolFrame
        Note(
            if (pdf is PdfState.Failed) "This PDF doesn't open normally. Repair rebuilds its structure and saves whatever pages can be recovered."
            else "This PDF opens fine. Repairing writes a clean copy, which can help other apps that struggle with it.",
        )
        ExportButton("Repair", state) {
            vm.run { ctx, _ -> PdfEngine.repair(ctx, file).also { recovered = it.second }.first }
        }
        recovered?.let { Note("Recovered $it page(s).") }
        ProcessingStatus(state, onCancel = vm::cancel)
    }
}
