package com.toolx.app.tools.image

import android.graphics.Bitmap
import android.net.Uri
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Face
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toolx.app.core.ProcessingViewModel
import com.toolx.app.tools.Tool
import com.toolx.app.ui.common.ExportButton
import com.toolx.app.ui.common.Note
import com.toolx.app.ui.common.OptionChips
import com.toolx.app.ui.common.PickPrompt
import com.toolx.app.ui.common.PickType
import com.toolx.app.ui.common.ProcessingStatus
import com.toolx.app.ui.common.SectionLabel
import com.toolx.app.ui.common.SelectedInfo
import com.toolx.app.ui.common.TextInput
import com.toolx.app.ui.common.ToggleChip
import com.toolx.app.ui.common.rememberMediaPicker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import android.graphics.Color as AColor

// ---------- Shared pieces ----------

enum class BgColor(val label: String, val argb: Int?) {
    TRANSPARENT("Transparent", null),
    WHITE("White", 0xFFFFFFFF.toInt()),
    OFF_WHITE("Off-white", 0xFFF2F1EC.toInt()),
    LIGHT_GREY("Light grey", 0xFFD8D9DB.toInt()),
    LIGHT_BLUE("Light blue", 0xFFB9D5EF.toInt()),
    BLUE("Blue", 0xFF2D6BB4.toInt()),
    RED("Red", 0xFFD0312D.toInt()),
    CUSTOM("Custom", null),
}

/** "#RRGGBB" or "RRGGBB" → opaque ARGB, or null. */
internal fun parseHexColor(text: String): Int? {
    val hex = text.trim().removePrefix("#")
    if (hex.length != 6 || hex.any { it.digitToIntOrNull(16) == null }) return null
    return (0xFF000000 or hex.toLong(16)).toInt()
}

private sealed interface SegState {
    data object Loading : SegState
    data class Ready(val seg: Segmented) : SegState
    data class Failed(val message: String) : SegState
}

/** Runs the person segmenter once per picked image. */
@Composable
private fun rememberSegmentation(uri: Uri): SegState {
    val context = LocalContext.current
    var state by remember(uri) { mutableStateOf<SegState>(SegState.Loading) }
    LaunchedEffect(uri) {
        state = try {
            SegState.Ready(BackgroundEngine.segment(context, uri))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            SegState.Failed(e.message ?: "Couldn't analyse this photo")
        }
    }
    return state
}

/** Background replaced on the working copy, re-rendered ~150 ms after the last change. */
@Composable
private fun rememberComposite(seg: Segmented?, threshold: Float, softness: Float, background: Int?): Bitmap? {
    var out by remember(seg) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(seg, threshold, softness, background) {
        val s = seg ?: return@LaunchedEffect
        if (out != null) delay(150)
        out = runCatching { BackgroundEngine.preview(s, threshold, softness, background) }.getOrNull()
    }
    return out
}

@Composable
private fun ColorSwatch(argb: Int?) {
    val shape = CircleShape
    Box(
        Modifier
            .size(18.dp)
            .clip(shape)
            .then(if (argb == null) Modifier.drawBehind { drawChecker(4.dp.toPx()) } else Modifier.drawBehind { drawRect(Color(argb)) })
            .border(1.dp, MaterialTheme.colorScheme.outline, shape),
    )
}

@Composable
private fun BackgroundOptions(
    colors: List<BgColor>,
    selected: BgColor,
    onSelect: (BgColor) -> Unit,
    customHex: String,
    onCustomHex: (String) -> Unit,
    threshold: Float,
    onThreshold: (Float) -> Unit,
    softness: Float,
    onSoftness: (Float) -> Unit,
) {
    SectionLabel("Background")
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        colors.forEach { c ->
            val swatch = if (c == BgColor.CUSTOM) parseHexColor(customHex) ?: AColor.GRAY else c.argb
            FilterChip(
                selected = c == selected,
                onClick = { onSelect(c) },
                label = { Text(c.label) },
                leadingIcon = { ColorSwatch(swatch) },
            )
        }
    }
    if (selected == BgColor.CUSTOM) {
        TextInput(customHex, { onCustomHex(it.take(7)) }, "Colour (hex, e.g. #E6F0FA)")
        if (parseHexColor(customHex) == null) Note("Type 6 hex digits, like #FFFFFF for white.", error = true)
    }
    SectionLabel("Edge: " + when {
        threshold < 0.4f -> "keep more"
        threshold > 0.6f -> "cut tighter"
        else -> "balanced"
    })
    Slider(threshold, onThreshold, valueRange = 0.2f..0.8f, modifier = Modifier.fillMaxWidth().systemGestureExclusion())
    SectionLabel("Softness: ${(softness * 100).roundToInt()}")
    Slider(softness, onSoftness, valueRange = 0.02f..0.5f, modifier = Modifier.fillMaxWidth().systemGestureExclusion())
}

private val OptionalCropSaver = listSaver<CropRect?, Float>(
    save = { r -> r?.let { listOf(it.left, it.top, it.right, it.bottom) } ?: emptyList() },
    restore = { if (it.size == 4) CropRect(it[0], it[1], it[2], it[3]) else null },
)

private fun DrawScope.drawChecker(cell: Float) {
    val light = Color(0xFFFFFFFF)
    val dark = Color(0xFFD6D6D6)
    drawRect(light)
    var y = 0f
    var row = 0
    while (y < size.height) {
        var x = if (row % 2 == 0) 0f else cell
        while (x < size.width) {
            drawRect(dark, Offset(x, y), androidx.compose.ui.geometry.Size(cell, cell))
            x += cell * 2
        }
        y += cell
        row++
    }
}

/** Shows [bitmap] fitted to the width (max 360 dp tall), over a checkerboard so transparency is visible. */
@Composable
private fun CheckerImage(bitmap: Bitmap, description: String, original: Bitmap? = null) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val originalImage = remember(original) { original?.asImageBitmap() }
    var showOriginal by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val aspect = bitmap.width.toFloat() / bitmap.height
        var w = maxWidth
        var h = w / aspect
        if (h > 360.dp) { h = 360.dp; w = h * aspect }
        Box(
            Modifier
                .size(w, h)
                .clip(RoundedCornerShape(12.dp))
                .drawBehind { drawChecker(10.dp.toPx()) }
                .pointerInput(original) {
                    if (original != null) detectTapGestures(onPress = {
                        showOriginal = true
                        tryAwaitRelease()
                        showOriginal = false
                    })
                },
        ) {
            Image(
                if (showOriginal && originalImage != null) originalImage else image,
                contentDescription = if (showOriginal) "Original photo" else description,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            if (original != null) {
                Text(
                    if (showOriginal) "Original" else "Preview · hold to compare",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .drawBehind { drawRect(Color.Black.copy(alpha = 0.6f)) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun Analysing() {
    Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text("Finding the person…", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Shared frame: pick a photo, segment it, then show [content] once the mask is ready. */
@Composable
private fun SegmentToolFrame(
    tool: Tool,
    onBack: () -> Unit,
    onNewImage: () -> Unit,
    content: @Composable (uri: Uri, seg: Segmented, original: Size?) -> Unit,
) {
    val vm: ProcessingViewModel = viewModel()
    val context = LocalContext.current
    var uri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var original by remember(uri) { mutableStateOf<Size?>(null) }
    LaunchedEffect(uri) { uri?.let { original = ImageEngine.readSize(context, it) } }
    val pick = rememberMediaPicker(PickType.IMAGE) {
        uri = it
        vm.reset()
        onNewImage()
    }
    com.toolx.app.ui.common.ToolScaffold(title = tool.title, onBack = onBack) {
        val current = uri
        if (current == null) {
            PickPrompt("Choose a photo of a person", Icons.Rounded.Face, onClick = pick)
            Note("Works on people only, like selfies, portraits and ID photos. Everything runs on your phone.")
            return@ToolScaffold
        }
        when (val s = rememberSegmentation(current)) {
            SegState.Loading -> Analysing()
            is SegState.Failed -> Note(s.message, error = true)
            is SegState.Ready -> {
                val coverage = remember(s.seg) { MaskOps.coverage(BackgroundEngine.alphaOf(s.seg, 0.5f, 0.1f)) }
                if (coverage < 0.01f) {
                    Note("No person found in this photo. This tool only recognises people.", error = true)
                }
                content(current, s.seg, original)
            }
        }
        SelectedInfo(current, original?.let { "${it.width}×${it.height}" }, onChange = pick)
    }
}

// ---------- Remove background ----------

@Composable
fun RemoveBackgroundScreen(tool: Tool, onBack: () -> Unit) {
    val vm: ProcessingViewModel = viewModel()
    val state by vm.state.collectAsState()
    var color by rememberSaveable { mutableStateOf(BgColor.TRANSPARENT) }
    var customHex by rememberSaveable { mutableStateOf("#E6F0FA") }
    var threshold by rememberSaveable { mutableFloatStateOf(0.5f) }
    var softness by rememberSaveable { mutableFloatStateOf(0.15f) }
    var format by rememberSaveable { mutableStateOf(OutputFormat.PNG) }

    val background = if (color == BgColor.CUSTOM) parseHexColor(customHex) else color.argb
    val validColor = color != BgColor.CUSTOM || background != null

    SegmentToolFrame(tool, onBack, onNewImage = {}) { uri, seg, _ ->
        val shown = rememberComposite(seg, threshold, softness, background)
        if (shown == null) Analysing() else CheckerImage(shown, "Photo with background removed", original = seg.working)
        BackgroundOptions(
            BgColor.entries, color, { color = it }, customHex, { customHex = it },
            threshold, { threshold = it }, softness, { softness = it },
        )
        if (background != null) {
            SectionLabel("Format")
            OptionChips(listOf(OutputFormat.JPEG, OutputFormat.PNG), format, label = { it.label }) { format = it }
        } else {
            Note("Transparent backgrounds are saved as PNG.")
        }
        ExportButton("Save", state, enabled = validColor) {
            vm.run { ctx, _ -> BackgroundEngine.removeBackground(ctx, uri, seg, threshold, softness, background, format) }
        }
        ProcessingStatus(state, onCancel = vm::cancel)
    }
}

// ---------- Passport photo ----------

/**
 * Common photo sizes. [headFraction] is the crown-to-chin height as a share of the photo height
 * and [topMargin] the gap above the crown, both at the middle of each standard's allowed range.
 */
enum class PassportSize(val label: String, val widthMm: Float, val heightMm: Float, val headFraction: Float, val topMargin: Float, val usedFor: String) {
    P35X45("35×45 mm", 35f, 45f, 0.75f, 0.08f, "UK, EU, Pakistan, India and most other countries"),
    US2X2("2×2 in", 50.8f, 50.8f, 0.60f, 0.11f, "USA passport and visa"),
    P33X48("33×48 mm", 33f, 48f, 0.64f, 0.08f, "China visa"),
    P30X40("30×40 mm", 30f, 40f, 0.72f, 0.08f, "Some ID cards and forms");

    val ratio: Float get() = widthMm / heightMm
    val outW: Int get() = MaskOps.mmToPx(widthMm, DPI)
    val outH: Int get() = MaskOps.mmToPx(heightMm, DPI)

    companion object { const val DPI = 300 }
}

/** Oval where the head should sit, plus a line at the target chin height. */
private fun DrawScope.drawHeadGuide(box: Rect, size: PassportSize) {
    val top = box.top + box.height * size.topMargin
    val bottom = top + box.height * size.headFraction
    val ovalH = bottom - top
    val ovalW = ovalH * 0.74f
    val cx = box.center.x
    val dash = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx()))
    val topLeft = Offset(cx - ovalW / 2f, top)
    val ovalSize = androidx.compose.ui.geometry.Size(ovalW, ovalH)
    drawOval(Color.Black.copy(alpha = 0.5f), topLeft, ovalSize, style = Stroke(3.dp.toPx(), pathEffect = dash))
    drawOval(Color(0xFFFFEB3B), topLeft, ovalSize, style = Stroke(1.5.dp.toPx(), pathEffect = dash))
}

@Composable
fun PassportPhotoScreen(tool: Tool, onBack: () -> Unit) {
    val vm: ProcessingViewModel = viewModel()
    val state by vm.state.collectAsState()
    var photoSize by rememberSaveable { mutableStateOf(PassportSize.P35X45) }
    var color by rememberSaveable { mutableStateOf(BgColor.WHITE) }
    var customHex by rememberSaveable { mutableStateOf("#E6F0FA") }
    var threshold by rememberSaveable { mutableFloatStateOf(0.5f) }
    var softness by rememberSaveable { mutableFloatStateOf(0.12f) }
    var sheet by rememberSaveable { mutableStateOf(true) }
    var crop by rememberSaveable(stateSaver = OptionalCropSaver) { mutableStateOf<CropRect?>(null) }

    val background = if (color == BgColor.CUSTOM) parseHexColor(customHex) else color.argb
    val colors = BgColor.entries.filter { it != BgColor.TRANSPARENT }

    SegmentToolFrame(tool, onBack, onNewImage = { crop = null }) { uri, seg, original ->
        val autoFrame: () -> Unit = {
            val alpha = BackgroundEngine.alphaOf(seg, threshold, softness)
            val head = MaskOps.findHead(alpha, seg.width, seg.height)
            crop = head?.let { MaskOps.passportCrop(it, seg.width, seg.height, photoSize.ratio, photoSize.headFraction, photoSize.topMargin) }
                ?: CropRect.centered(Size(seg.width, seg.height), photoSize.ratio)
        }
        LaunchedEffect(seg) { if (crop == null) autoFrame() }

        val shown = rememberComposite(seg, threshold, softness, background ?: AColor.WHITE)
        val box = crop
        if (shown == null || box == null) {
            Analysing()
        } else {
            CropCanvas(
                working = shown,
                imageSize = Size(seg.width, seg.height),
                crop = box,
                ratio = photoSize.ratio,
                onCropChange = { crop = it },
                guide = { r -> drawHeadGuide(r, photoSize) },
            )
            Note("Drag the box so the head fills the dashed oval: crown at its top, chin at its bottom.")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = autoFrame) {
                Icon(Icons.Rounded.AutoFixHigh, contentDescription = null, modifier = Modifier.padding(end = 6.dp).size(18.dp))
                Text("Auto-frame")
            }
        }

        SectionLabel("Size")
        OptionChips(PassportSize.entries, photoSize, label = { it.label }) {
            photoSize = it
            // Re-frame for the new proportions.
            val alpha = BackgroundEngine.alphaOf(seg, threshold, softness)
            crop = MaskOps.findHead(alpha, seg.width, seg.height)
                ?.let { h -> MaskOps.passportCrop(h, seg.width, seg.height, it.ratio, it.headFraction, it.topMargin) }
                ?: CropRect.centered(Size(seg.width, seg.height), it.ratio)
        }
        Note("${photoSize.usedFor}. Output ${photoSize.outW}×${photoSize.outH} px (${photoSize.label} at ${PassportSize.DPI} dpi). Rules differ by country, so check the official requirements.")
        val c = crop
        if (c != null && original != null && c.width * original.width < photoSize.outW) {
            Note("This photo is low resolution for a ${photoSize.label} print, so it may look soft.", error = true)
        }

        BackgroundOptions(
            colors, color, { color = it }, customHex, { customHex = it },
            threshold, { threshold = it }, softness, { softness = it },
        )

        SectionLabel("Printing")
        val grid = MaskOps.sheetGrid(photoSize.outW, photoSize.outH, BackgroundEngine.SHEET_W, BackgroundEngine.SHEET_H, BackgroundEngine.SHEET_GAP)
        ToggleChip("Also save a 4×6 in print sheet (${grid.count} copies)", sheet) { sheet = it }
        if (sheet) Note("Print the sheet on 4×6 in (10×15 cm) photo paper at 100% / actual size, then cut along the grey lines.")

        ExportButton(if (sheet) "Save photo + sheet" else "Save photo", state, enabled = background != null && c != null) {
            val bg = background ?: return@ExportButton
            val cr = c ?: return@ExportButton
            vm.runAll { ctx, _ ->
                BackgroundEngine.passport(ctx, uri, seg, threshold, softness, bg, cr, photoSize.outW, photoSize.outH, sheet)
            }
        }
        ProcessingStatus(state, onCancel = vm::cancel)
    }
}
