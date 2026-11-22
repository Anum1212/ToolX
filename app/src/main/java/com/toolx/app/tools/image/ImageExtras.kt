package com.toolx.app.tools.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image as ImageIcon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toolx.app.core.ProcessingViewModel
import com.toolx.app.core.SavedMedia
import com.toolx.app.tools.Tool
import com.toolx.app.ui.common.ExportButton
import com.toolx.app.ui.common.Note
import com.toolx.app.ui.common.OptionChips
import com.toolx.app.ui.common.PickPrompt
import com.toolx.app.ui.common.PickType
import com.toolx.app.ui.common.ProcessingStatus
import com.toolx.app.ui.common.ResultCard
import com.toolx.app.ui.common.SectionLabel
import com.toolx.app.ui.common.SelectedInfo
import com.toolx.app.ui.common.TextInput
import com.toolx.app.ui.common.ToolScaffold
import com.toolx.app.ui.common.copyToClipboard
import com.toolx.app.ui.common.rememberMediaPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.roundToInt

// ---------- engine ----------

/** Round corners, watermark and colour sampling on bitmaps. */
internal object ImageExtrasEngine {

    /**
     * Rounds the corners by [radiusFraction] of the shorter side (0.5 = pill). With [circle],
     * crops a centred square first and makes it a circle. Corners are transparent.
     */
    fun round(src: Bitmap, radiusFraction: Float, circle: Boolean): Bitmap {
        val base = if (circle) {
            val s = min(src.width, src.height)
            Bitmap.createBitmap(src, (src.width - s) / 2, (src.height - s) / 2, s, s)
        } else src
        val out = Bitmap.createBitmap(base.width, base.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val r = if (circle) base.width / 2f else min(base.width, base.height) * radiusFraction
        val path = Path().apply { addRoundRect(RectF(0f, 0f, base.width.toFloat(), base.height.toFloat()), r, r, Path.Direction.CW) }
        canvas.drawPath(path, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(base, 0f, 0f, paint)
        if (base !== src) base.recycle()
        return out
    }

    data class MarkStyle(
        val text: String,
        val logo: Bitmap?,
        val spot: ImageOps.Spot,
        /** Mark width as a share of the image width. */
        val size: Float,
        val opacity: Float,
        val color: Int,
    )

    /** Draws a text or logo watermark onto a copy of [src]. */
    fun watermark(src: Bitmap, s: MarkStyle): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val W = out.width.toFloat()
        val H = out.height.toFloat()
        val margin = min(W, H) * 0.03f
        val alpha = (s.opacity * 255).roundToInt().coerceIn(0, 255)
        val logo = s.logo
        if (logo != null) {
            val w = W * s.size
            val h = w * logo.height / logo.width
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.alpha = alpha }
            val positions = if (s.spot == ImageOps.Spot.TILED) ImageOps.tiles(W, H, w, h).map { (cx, cy) -> cx - w / 2 to cy - h / 2 }
            else listOf(ImageOps.place(s.spot, W, H, w, h, margin))
            positions.forEach { (x, y) -> canvas.drawBitmap(logo, null, RectF(x, y, x + w, y + h), paint) }
        } else if (s.text.isNotBlank()) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.DEFAULT_BOLD
                color = s.color
                this.alpha = alpha
                textSize = 100f
            }
            // Fit the text to the requested width.
            paint.textSize = 100f * (W * s.size) / paint.measureText(s.text).coerceAtLeast(1f)
            val fm = paint.fontMetrics
            val w = paint.measureText(s.text)
            val h = fm.descent - fm.ascent
            // A soft shadow keeps light text readable on light photos and vice versa.
            paint.setShadowLayer(h * 0.06f, 0f, h * 0.03f, if (isLight(s.color)) 0x66000000 else 0x66FFFFFF)
            val positions = if (s.spot == ImageOps.Spot.TILED) ImageOps.tiles(W, H, w, h).map { (cx, cy) -> cx - w / 2 to cy - h / 2 }
            else listOf(ImageOps.place(s.spot, W, H, w, h, margin))
            positions.forEach { (x, y) ->
                if (s.spot == ImageOps.Spot.TILED) {
                    canvas.save(); canvas.rotate(-30f, x + w / 2, y + h / 2)
                    canvas.drawText(s.text, x, y - fm.ascent, paint)
                    canvas.restore()
                } else canvas.drawText(s.text, x, y - fm.ascent, paint)
            }
        }
        return out
    }

    private fun isLight(c: Int) = (ImageOps.red(c) * 299 + ImageOps.green(c) * 587 + ImageOps.blue(c) * 114) / 1000 > 140

    /** Palette from a small copy (fast). */
    fun palette(src: Bitmap): List<Int> {
        val small = Bitmap.createScaledBitmap(src, 96, (96f * src.height / src.width).roundToInt().coerceAtLeast(1), true)
        val px = IntArray(small.width * small.height).also { small.getPixels(it, 0, small.width, 0, 0, small.width, small.height) }
        if (small !== src) small.recycle()
        return ImageOps.dominantColors(px, 6)
    }
}

// ---------- shared frame ----------

/** Pick a photo, load a working copy, then show [content]. */
@Composable
internal fun PhotoFrame(tool: Tool, onBack: () -> Unit, onNew: () -> Unit = {}, content: @Composable (uri: Uri, working: Bitmap) -> Unit) {
    val context = LocalContext.current
    var uri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val pick = rememberMediaPicker(PickType.IMAGE) { uri = it; onNew() }
    ToolScaffold(tool.title, onBack) {
        val u = uri
        if (u == null) {
            PickPrompt("Choose a photo", Icons.Rounded.ImageIcon, onClick = pick)
            return@ToolScaffold
        }
        var working by remember(u) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(u) { working = runCatching { ImageEngine.loadWorkingCopy(context, u) }.getOrNull() }
        val w = working
        if (w == null) Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else content(u, w)
        SelectedInfo(u, w?.let { "${it.width}×${it.height} preview" }, onChange = pick)
    }
}

/** Bitmap fitted to the width (max 380 dp tall), on a checkerboard so transparency shows. */
@Composable
private fun Preview(bitmap: Bitmap, checker: Boolean = false) {
    val img = remember(bitmap) { bitmap.asImageBitmap() }
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val aspect = bitmap.width.toFloat() / bitmap.height
        var w = maxWidth
        var h = w / aspect
        if (h > 380.dp) { h = 380.dp; w = h * aspect }
        Box(
            Modifier.size(w, h).then(if (checker) Modifier.drawBehind {
                val cell = 10.dp.toPx()
                drawRect(Color.White)
                var y = 0f; var row = 0
                while (y < size.height) {
                    var x = if (row % 2 == 0) 0f else cell
                    while (x < size.width) { drawRect(Color(0xFFD6D6D6), Offset(x, y), androidx.compose.ui.geometry.Size(cell, cell)); x += cell * 2 }
                    y += cell; row++
                }
            } else Modifier),
        ) { Image(img, "Preview", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
    }
}

// ---------- Round corners ----------

@Composable
fun RoundCornersScreen(tool: Tool, onBack: () -> Unit) {
    val vm: ProcessingViewModel = viewModel()
    val state by vm.state.collectAsState()
    var radius by rememberSaveable { mutableFloatStateOf(0.12f) }
    var circle by rememberSaveable { mutableStateOf(false) }
    PhotoFrame(tool, onBack, onNew = { vm.reset() }) { uri, working ->
        var shown by remember(working) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(working, radius, circle) {
            if (shown != null) delay(80)
            shown = withContext(Dispatchers.Default) { ImageExtrasEngine.round(working, radius, circle) }
        }
        shown?.let { Preview(it, checker = true) }
        OptionChips(listOf(false, true), circle, label = { if (it) "Circle" else "Rounded" }) { circle = it; vm.reset() }
        if (!circle) {
            SectionLabel("Corner radius: ${(radius * 200).roundToInt()}%")
            Slider(radius, { radius = it; vm.reset() }, valueRange = 0f..0.5f, modifier = Modifier.fillMaxWidth().systemGestureExclusion())
        } else Note("Crops a centred square and makes it a circle, ready for a profile picture.")
        Note("Saves as PNG so the corners stay transparent.")
        ExportButton("Save", state) {
            val r = radius; val c = circle
            vm.run { ctx, _ -> saveFull(ctx, uri, "rounded", OutputFormat.PNG) { ImageExtrasEngine.round(it, r, c) } }
        }
        ProcessingStatus(state, onCancel = vm::cancel)
    }
}

/** Decodes the full image, applies [op] and saves. */
private suspend fun saveFull(context: Context, uri: Uri, tag: String, format: OutputFormat, op: (Bitmap) -> Bitmap): SavedMedia =
    withContext(Dispatchers.IO) {
        val src = ImageEngine.decode(context, uri)
        val out = op(src)
        if (out !== src) src.recycle()
        ImageEngine.save(context, out, tag, format, 95)
    }

// ---------- Color picker ----------

@Composable
fun ColorPickerScreen(tool: Tool, onBack: () -> Unit) {
    val context = LocalContext.current
    PhotoFrame(tool, onBack) { _, working ->
        var point by remember(working) { mutableStateOf(Offset(0.5f, 0.5f)) }
        val color = remember(working, point) {
            val x = (point.x * (working.width - 1)).roundToInt().coerceIn(0, working.width - 1)
            val y = (point.y * (working.height - 1)).roundToInt().coerceIn(0, working.height - 1)
            working.getPixel(x, y) or 0xFF000000.toInt()
        }
        val img = remember(working) { working.asImageBitmap() }
        val update by rememberUpdatedState<(Offset) -> Unit> { point = it }
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val aspect = working.width.toFloat() / working.height
            var w = maxWidth
            var h = w / aspect
            if (h > 380.dp) { h = 380.dp; w = h * aspect }
            ComposeCanvas(
                Modifier.size(w, h)
                    .pointerInput(Unit) {
                        detectTapGestures { update(Offset((it.x / size.width).coerceIn(0f, 1f), (it.y / size.height).coerceIn(0f, 1f))) }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            update(Offset((change.position.x / size.width).coerceIn(0f, 1f), (change.position.y / size.height).coerceIn(0f, 1f)))
                        }
                    },
            ) {
                drawImage(img, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
                val c = Offset(point.x * size.width, point.y * size.height)
                val r = 22.dp.toPx()
                drawCircle(Color(color), r, c)
                drawCircle(Color.White, r, c, style = Stroke(3.dp.toPx()))
                drawCircle(Color.Black.copy(alpha = 0.5f), r + 2.dp.toPx(), c, style = Stroke(1.dp.toPx()))
            }
        }
        Note("Tap or drag on the photo to pick a colour. Tap a row to copy it.")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(Color(color)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp)))
            Text(ImageOps.hex(color), style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        }
        ResultCard(listOf("HEX" to ImageOps.hex(color), "RGB" to ImageOps.rgb(color), "HSL" to ImageOps.hslText(color), "CMYK" to ImageOps.cmyk(color)))
        val palette = remember(working) { ImageExtrasEngine.palette(working) }
        if (palette.isNotEmpty()) {
            SectionLabel("Main colours in this photo")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                palette.forEach { c ->
                    Column(Modifier.weight(1f).clickable { copyToClipboard(context, ImageOps.hex(c)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(10.dp)).background(Color(c)))
                        Text(ImageOps.hex(c), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            Note("Tap a swatch to copy its HEX code.")
        }
    }
}

// ---------- Watermark image ----------

private val MarkColors = listOf("White" to 0xFFFFFFFF.toInt(), "Black" to 0xFF000000.toInt(), "Grey" to 0xFF9E9E9E.toInt(), "Red" to 0xFFE53935.toInt())

@Composable
fun WatermarkImageScreen(tool: Tool, onBack: () -> Unit) {
    val vm: ProcessingViewModel = viewModel()
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var useLogo by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("© My name") }
    var spot by rememberSaveable { mutableStateOf(ImageOps.Spot.BOTTOM_RIGHT) }
    var size by rememberSaveable { mutableFloatStateOf(0.3f) }
    var opacity by rememberSaveable { mutableFloatStateOf(0.7f) }
    var color by rememberSaveable { mutableIntStateOf(MarkColors[0].second) }
    var logoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var logo by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(logoUri) { logo = logoUri?.let { u -> runCatching { ImageEngine.loadWorkingCopy(context, u) }.getOrNull() } }
    val pickLogo = rememberMediaPicker(PickType.IMAGE) { logoUri = it; vm.reset() }

    PhotoFrame(tool, onBack, onNew = { vm.reset() }) { uri, working ->
        val style = ImageExtrasEngine.MarkStyle(if (useLogo) "" else text, if (useLogo) logo else null, spot, size, opacity, color)
        var shown by remember(working) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(working, style) {
            if (shown != null) delay(100)
            shown = withContext(Dispatchers.Default) { ImageExtrasEngine.watermark(working, style) }
        }
        shown?.let { Preview(it) }
        OptionChips(listOf(false, true), useLogo, label = { if (it) "Logo" else "Text" }) { useLogo = it; vm.reset() }
        if (useLogo) {
            OutlinedButton(onClick = pickLogo) { Text(if (logoUri == null) "Choose a logo image" else "Change logo") }
            Note("A PNG with a transparent background looks best.")
        } else {
            TextInput(text, { text = it.take(60); vm.reset() }, "Watermark text")
            SectionLabel("Colour")
            OptionChips(MarkColors.map { it.second }, color, label = { c -> MarkColors.first { it.second == c }.first }) { color = it; vm.reset() }
        }
        SectionLabel("Position")
        OptionChips(ImageOps.Spot.entries, spot, label = { it.label }) { spot = it; vm.reset() }
        SectionLabel("Size: ${(size * 100).roundToInt()}% of the width")
        Slider(size, { size = it; vm.reset() }, valueRange = 0.08f..0.8f, modifier = Modifier.fillMaxWidth().systemGestureExclusion())
        SectionLabel("Opacity: ${(opacity * 100).roundToInt()}%")
        Slider(opacity, { opacity = it; vm.reset() }, valueRange = 0.1f..1f, modifier = Modifier.fillMaxWidth().systemGestureExclusion())
        val ready = if (useLogo) logo != null else text.isNotBlank()
        ExportButton("Save", state, enabled = ready) {
            val format = OutputFormat.fromMime(ImageEngine.mimeOf(context, uri))
            val logoFull = logoUri
            vm.run { ctx, _ ->
                val full = logoFull?.let { if (useLogo) ImageEngine.loadWorkingCopy(ctx, it) else null }
                saveFull(ctx, uri, "watermarked", format) { ImageExtrasEngine.watermark(it, style.copy(logo = full)) }
            }
        }
        ProcessingStatus(state, onCancel = vm::cancel)
    }
}
