package com.toolx.app.tools.image

import android.content.Context
import android.net.Uri
import android.util.Size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toolx.app.core.ProcessingViewModel
import com.toolx.app.core.SavedMedia
import com.toolx.app.tools.Tool
import com.toolx.app.ui.common.ExportButton
import com.toolx.app.ui.common.ImagePreview
import com.toolx.app.ui.common.OptionChips
import com.toolx.app.ui.common.PickPrompt
import com.toolx.app.ui.common.PickType
import com.toolx.app.ui.common.ProcessingStatus
import com.toolx.app.ui.common.SectionLabel
import com.toolx.app.ui.common.SelectedInfo
import com.toolx.app.ui.common.ToolScaffold
import com.toolx.app.ui.common.rememberMediaPicker
import kotlin.math.roundToInt

/** Shared layout for image tools: pick → preview → options → export → result. */
@Composable
private fun ImageToolFrame(
    tool: Tool,
    onBack: () -> Unit,
    exportLabel: String,
    onNewImage: (uri: Uri, size: Size?, mime: String?) -> Unit = { _, _, _ -> },
    canExport: Boolean = true,
    preview: @Composable (uri: Uri, size: Size?) -> Unit = { uri, _ -> ImagePreview(uri) },
    options: @Composable (uri: Uri, size: Size?) -> Unit = { _, _ -> },
    task: suspend (context: Context, uri: Uri) -> SavedMedia,
) {
    val vm: ProcessingViewModel = viewModel()
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    var uri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var size by remember(uri) { mutableStateOf<Size?>(null) }
    var loadedFor by rememberSaveable { mutableStateOf<Uri?>(null) }
    LaunchedEffect(uri) {
        val u = uri ?: return@LaunchedEffect
        size = ImageEngine.readSize(context, u)
        // Only push defaults for a genuinely new pick, not after rotation.
        if (loadedFor != u) {
            onNewImage(u, size, ImageEngine.mimeOf(context, u))
            loadedFor = u
        }
    }
    val pick = rememberMediaPicker(PickType.IMAGE) {
        uri = it
        vm.reset()
    }

    ToolScaffold(title = tool.title, onBack = onBack) {
        val current = uri
        if (current == null) {
            PickPrompt("Choose an image", Icons.Rounded.Image, onClick = pick)
            return@ToolScaffold
        }
        preview(current, size)
        SelectedInfo(current, size?.let { "${it.width}×${it.height}" }, onChange = pick)
        options(current, size)
        ExportButton(exportLabel, state, enabled = canExport) {
            vm.run { ctx, _ -> task(ctx, current) }
        }
        ProcessingStatus(state, onCancel = vm::cancel)
    }
}

@Composable
private fun FormatAndQuality(
    format: OutputFormat,
    quality: Int,
    onFormat: (OutputFormat) -> Unit,
    onQuality: (Int) -> Unit,
) {
    SectionLabel("Format")
    OptionChips(OutputFormat.entries, format, label = { it.label }, onSelect = onFormat)
    if (format.supportsQuality) {
        SectionLabel("Quality: $quality")
        Slider(
            value = quality.toFloat(),
            onValueChange = { onQuality(it.roundToInt()) },
            valueRange = 10f..100f,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Text(
            "PNG is lossless — quality doesn't apply.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---------- Resize ----------

@Composable
fun ImageResizeScreen(tool: Tool, onBack: () -> Unit) {
    var widthText by rememberSaveable { mutableStateOf("") }
    var heightText by rememberSaveable { mutableStateOf("") }
    var lockAspect by rememberSaveable { mutableStateOf(true) }
    var format by rememberSaveable { mutableStateOf(OutputFormat.JPEG) }
    var quality by rememberSaveable { mutableIntStateOf(90) }

    val w = widthText.toIntOrNull()
    val h = heightText.toIntOrNull()
    val valid = w != null && h != null && w in 1..MAX_SIDE && h in 1..MAX_SIDE

    ImageToolFrame(
        tool = tool,
        onBack = onBack,
        exportLabel = "Resize",
        canExport = valid,
        onNewImage = { _, size, mime ->
            size?.let { widthText = it.width.toString(); heightText = it.height.toString() }
            format = OutputFormat.fromMime(mime)
        },
        preview = { uri, size ->
            LiveImagePreview(uri, size, w?.takeIf { valid }, h?.takeIf { valid }, format, quality)
        },
        options = { _, size ->
            val aspect = size?.let { it.width.toFloat() / it.height } ?: 1f
            SectionLabel("Quick size")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(25, 50, 75).forEach { pct ->
                    FilledTonalButton(
                        onClick = {
                            size?.let {
                                widthText = (it.width * pct / 100).coerceAtLeast(1).toString()
                                heightText = (it.height * pct / 100).coerceAtLeast(1).toString()
                            }
                        },
                        enabled = size != null,
                    ) { Text("$pct%") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = widthText,
                    onValueChange = { v ->
                        widthText = v.filter(Char::isDigit).take(5)
                        if (lockAspect) widthText.toIntOrNull()?.let { heightText = (it / aspect).roundToInt().coerceAtLeast(1).toString() }
                    },
                    label = { Text("Width") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                IconToggleButton(checked = lockAspect, onCheckedChange = { lockAspect = it }) {
                    Icon(
                        if (lockAspect) Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                        contentDescription = if (lockAspect) "Aspect ratio locked" else "Aspect ratio unlocked",
                    )
                }
                OutlinedTextField(
                    value = heightText,
                    onValueChange = { v ->
                        heightText = v.filter(Char::isDigit).take(5)
                        if (lockAspect) heightText.toIntOrNull()?.let { widthText = (it * aspect).roundToInt().coerceAtLeast(1).toString() }
                    },
                    label = { Text("Height") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            if (!valid && (widthText.isNotEmpty() || heightText.isNotEmpty())) {
                Text(
                    "Use sizes between 1 and $MAX_SIDE px.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            FormatAndQuality(format, quality, { format = it }, { quality = it })
        },
        task = { ctx, uri -> ImageEngine.resize(ctx, uri, w!!, h!!, format, quality) },
    )
}

// ---------- Compress & convert ----------

@Composable
fun ImageConvertScreen(tool: Tool, onBack: () -> Unit) {
    var format by rememberSaveable { mutableStateOf(OutputFormat.JPEG) }
    var quality by rememberSaveable { mutableIntStateOf(75) }

    ImageToolFrame(
        tool = tool,
        onBack = onBack,
        exportLabel = "Save",
        preview = { uri, size ->
            LiveImagePreview(uri, size, size?.width, size?.height, format, quality)
        },
        options = { _, _ -> FormatAndQuality(format, quality, { format = it }, { quality = it }) },
        task = { ctx, uri -> ImageEngine.convert(ctx, uri, format, quality) },
    )
}

// ---------- Crop ----------

@Composable
fun ImageCropScreen(tool: Tool, onBack: () -> Unit) {
    var crop by rememberSaveable(stateSaver = CropRectSaver) { mutableStateOf(CropRect.Full) }
    var ratio by rememberSaveable { mutableStateOf(CropRatio.FREE) }
    var format by rememberSaveable { mutableStateOf(OutputFormat.JPEG) }
    var quality by rememberSaveable { mutableIntStateOf(92) }

    ImageToolFrame(
        tool = tool,
        onBack = onBack,
        exportLabel = "Crop",
        canExport = crop != CropRect.Full,
        onNewImage = { _, _, mime ->
            crop = CropRect.Full
            ratio = CropRatio.FREE
            format = OutputFormat.fromMime(mime)
        },
        preview = { uri, size ->
            if (size == null) {
                ImagePreview(uri)
            } else {
                CropPreview(uri, size, crop, ratio.value(size), onCropChange = { crop = it }, format = format, quality = quality)
            }
        },
        options = { _, size ->
            SectionLabel("Aspect ratio")
            OptionChips(CropRatio.entries, ratio, label = { it.label }) { r ->
                ratio = r
                // Picking a ratio (or Free, which acts as reset) snaps to the largest centred box.
                size?.let { crop = CropRect.centered(it, r.value(it)) }
            }
            FormatAndQuality(format, quality, { format = it }, { quality = it })
        },
        task = { ctx, uri -> ImageEngine.crop(ctx, uri, crop, format, quality) },
    )
}

// ---------- Rotate & flip ----------

@Composable
fun ImageRotateScreen(tool: Tool, onBack: () -> Unit) {
    var degrees by rememberSaveable { mutableIntStateOf(0) }
    var flipH by rememberSaveable { mutableStateOf(false) }
    var flipV by rememberSaveable { mutableStateOf(false) }
    var format by rememberSaveable { mutableStateOf(OutputFormat.JPEG) }

    // Flips are stored in image space; when the image is sideways on screen,
    // a "horizontal" flip as the user sees it is a vertical flip of the image.
    val sideways = degrees % 180 != 0
    val flipScreenH: () -> Unit = { if (sideways) { flipV = !flipV } else { flipH = !flipH } }
    val flipScreenV: () -> Unit = { if (sideways) { flipH = !flipH } else { flipV = !flipV } }

    ImageToolFrame(
        tool = tool,
        onBack = onBack,
        exportLabel = "Save",
        canExport = degrees != 0 || flipH || flipV,
        onNewImage = { _, _, mime ->
            degrees = 0; flipH = false; flipV = false
            format = if (OutputFormat.fromMime(mime) == OutputFormat.PNG) OutputFormat.PNG else OutputFormat.JPEG
        },
        preview = { uri, _ ->
            ImagePreview(
                uri,
                Modifier.graphicsLayer {
                    rotationZ = degrees.toFloat()
                    scaleX = if (flipH) -1f else 1f
                    scaleY = if (flipV) -1f else 1f
                    // Shrink when sideways so the rotated image stays inside its box.
                    if (sideways) {
                        scaleX *= 0.7f
                        scaleY *= 0.7f
                    }
                },
            )
        },
        options = { _, _ ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { degrees = (degrees + 270) % 360 }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Rounded.RotateLeft, contentDescription = "Rotate left")
                }
                FilledTonalButton(onClick = { degrees = (degrees + 90) % 360 }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Rounded.RotateRight, contentDescription = "Rotate right")
                }
                FilledTonalButton(onClick = flipScreenH, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Flip, contentDescription = "Flip horizontally")
                }
                FilledTonalButton(onClick = flipScreenV, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Flip, contentDescription = "Flip vertically", modifier = Modifier.graphicsLayer { rotationZ = 90f })
                }
            }
            SectionLabel("Format")
            OptionChips(listOf(OutputFormat.JPEG, OutputFormat.PNG), format, label = { it.label }) { format = it }
        },
        task = { ctx, uri -> ImageEngine.rotate(ctx, uri, degrees, flipH, flipV, format) },
    )
}

private const val MAX_SIDE = 12_000
