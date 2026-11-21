package com.toolx.app.tools.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.util.Size
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toolx.app.core.ProcessingState
import com.toolx.app.core.ProcessingViewModel
import com.toolx.app.core.SavedMedia
import com.toolx.app.core.formatBytes
import com.toolx.app.core.formatDuration
import com.toolx.app.tools.Tool
import com.toolx.app.tools.image.CropCanvas
import com.toolx.app.tools.image.CropRatio
import com.toolx.app.tools.image.CropRect
import com.toolx.app.tools.image.CropRectSaver
import com.toolx.app.ui.common.ClipRangePicker
import com.toolx.app.ui.common.ExportButton
import com.toolx.app.ui.common.FloatRangeSaver
import com.toolx.app.ui.common.MergeFrame
import com.toolx.app.ui.common.OptionChips
import com.toolx.app.ui.common.PickPrompt
import com.toolx.app.ui.common.PickType
import com.toolx.app.ui.common.ProcessingStatus
import com.toolx.app.ui.common.SectionLabel
import com.toolx.app.ui.common.SelectedInfo
import com.toolx.app.ui.common.SpeedPresets
import com.toolx.app.ui.common.ToolScaffold
import com.toolx.app.ui.common.rememberMediaPicker
import com.toolx.app.ui.common.speedLabel
import kotlin.math.roundToInt

/** Shared layout for video tools: pick → preview → options → export → result. */
@Composable
private fun VideoToolFrame(
    tool: Tool,
    onBack: () -> Unit,
    exportLabel: String,
    canExport: (VideoMeta?) -> Boolean = { true },
    seekToMs: Long? = null,
    /** Replaces the default player. `busy` is true while an export is running. */
    preview: (@Composable (uri: Uri, meta: VideoMeta?, busy: Boolean) -> Unit)? = null,
    options: @Composable (uri: Uri, meta: VideoMeta?) -> Unit = { _, _ -> },
    onNewVideo: () -> Unit = {},
    task: suspend (context: Context, uri: Uri, meta: VideoMeta?, onProgress: (Float) -> Unit) -> List<SavedMedia>,
) {
    val vm: ProcessingViewModel = viewModel()
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    var uri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var meta by remember(uri) { mutableStateOf<VideoMeta?>(null) }
    LaunchedEffect(uri) { uri?.let { meta = VideoEngine.readMeta(context, it) } }

    val pick = rememberMediaPicker(PickType.VIDEO) {
        uri = it
        vm.reset()
        onNewVideo()
    }

    ToolScaffold(title = tool.title, onBack = onBack) {
        val current = uri
        if (current == null) {
            PickPrompt("Choose a video", Icons.Rounded.VideoLibrary, onClick = pick)
            return@ToolScaffold
        }
        if (preview != null) {
            preview(current, meta, state is ProcessingState.Running)
        } else {
            VideoPreview(current, seekToMs = seekToMs)
        }
        SelectedInfo(
            uri = current,
            details = meta?.let { "${it.resolutionLabel} · ${formatDuration(it.durationMs)}" },
            onChange = pick,
        )
        options(current, meta)
        ExportButton(exportLabel, state, enabled = canExport(meta)) {
            val m = meta
            vm.runAll { ctx, progress -> task(ctx, current, m, progress) }
        }
        ProcessingStatus(state, onCancel = vm::cancel)
    }
}

/** Small grey explanatory line used under options. */
@Composable
private fun Hint(text: String, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ---------- Trim ----------

@Composable
fun VideoTrimScreen(tool: Tool, onBack: () -> Unit) {
    // Selection as fractions of the duration, so it doesn't depend on meta being loaded.
    var range by rememberSaveable(stateSaver = FloatRangeSaver) { mutableStateOf(0f..1f) }
    var seekTo by remember { mutableStateOf<Long?>(null) }

    VideoToolFrame(
        tool = tool,
        onBack = onBack,
        exportLabel = "Save trimmed video",
        seekToMs = seekTo,
        onNewVideo = { range = 0f..1f },
        canExport = { meta -> meta != null && (range.endInclusive - range.start) * meta.durationMs >= MIN_CLIP_MS },
        options = { _, meta ->
            val duration = meta?.durationMs ?: 0L
            ClipRangePicker(
                label = "Keep",
                range = range,
                durationMs = duration,
                onChange = { r, seek -> range = r; seekTo = seek },
                summary = "Length ${formatDuration(((range.endInclusive - range.start) * duration).toLong())}",
            )
        },
        task = { ctx, uri, meta, progress ->
            val total = meta?.durationMs ?: error("Couldn't read the video length")
            listOf(VideoEngine.trim(ctx, uri, (range.start * total).toLong(), (range.endInclusive * total).toLong(), progress))
        },
    )
}

// ---------- Compress ----------

@Composable
fun VideoCompressScreen(tool: Tool, onBack: () -> Unit) {
    var preset by rememberSaveable { mutableStateOf(CompressPreset.P720) }
    VideoToolFrame(
        tool = tool,
        onBack = onBack,
        exportLabel = "Compress",
        preview = { uri, meta, busy -> CompressLivePreview(uri, meta, preset, paused = busy) },
        options = { _, meta ->
            SectionLabel("Target quality")
            OptionChips(
                CompressPreset.entries,
                preset,
                label = { p -> meta?.let { "${p.label} · ≈ ${formatBytes(VideoEngine.estimateBytes(it, p))}" } ?: p.label },
            ) { preset = it }
            if (meta != null && meta.shortSide in 1..preset.shortSide) {
                Hint("The video is already ${meta.shortSide}p or smaller, so only the bitrate will be lowered.")
            }
        },
        task = { ctx, uri, _, progress -> listOf(VideoEngine.compress(ctx, uri, preset, progress)) },
    )
}

// ---------- Mute / Extract audio ----------

@Composable
fun VideoMuteScreen(tool: Tool, onBack: () -> Unit) {
    VideoToolFrame(
        tool = tool,
        onBack = onBack,
        exportLabel = "Save without audio",
        task = { ctx, uri, _, progress -> listOf(VideoEngine.mute(ctx, uri, progress)) },
    )
}

@Composable
fun VideoExtractAudioScreen(tool: Tool, onBack: () -> Unit) {
    VideoToolFrame(
        tool = tool,
        onBack = onBack,
        exportLabel = "Extract audio",
        task = { ctx, uri, _, progress -> listOf(VideoEngine.extractAudio(ctx, uri, progress)) },
    )
}

// ---------- Volume ----------

/** Volume slider (0–300%) with quick presets. Shared with the audio volume tool. */
@Composable
fun VolumeOptions(percent: Int, onPercent: (Int) -> Unit) {
    SectionLabel("Volume: $percent%")
    Slider(
        value = percent.toFloat(),
        onValueChange = { onPercent((it / 5).roundToInt() * 5) },
        valueRange = 0f..300f,
        modifier = Modifier.fillMaxWidth().systemGestureExclusion(),
    )
    OptionChips(listOf(0, 50, 100, 150, 200, 300), percent, label = { "$it%" }, onSelect = onPercent)
    when {
        percent == 0 -> Hint("0% makes the track silent.")
        percent > 150 -> Hint("Large boosts can distort loud parts. Play the preview to check.")
        else -> Hint("Play the preview to hear the new volume.")
    }
}

@Composable
fun VideoVolumeScreen(tool: Tool, onBack: () -> Unit) {
    var percent by rememberSaveable { mutableIntStateOf(150) }
    VideoToolFrame(
        tool = tool,
        onBack = onBack,
        exportLabel = "Save",
        canExport = { meta -> meta != null && meta.audioBitrate > 0 && percent != 100 },
        preview = { uri, _, _ -> VideoPreview(uri, volume = percent / 100f) },
        options = { _, meta ->
            if (meta != null && meta.audioBitrate == 0) {
                Hint("This video has no audio track.", error = true)
            } else {
                VolumeOptions(percent) { percent = it }
            }
        },
        task = { ctx, uri, _, progress -> listOf(VideoEngine.volume(ctx, uri, percent / 100f, progress)) },
    )
}

// ---------- Speed ----------

/** Speed slider (0.25×–4×) with presets. Shared with the audio speed tool. */
@Composable
fun SpeedOptions(speed: Float, durationMs: Long?, onSpeed: (Float) -> Unit) {
    SectionLabel("Speed: ${speedLabel(speed)}")
    Slider(
        value = speed,
        onValueChange = { onSpeed((it * 20).roundToInt() / 20f) },
        valueRange = 0.25f..4f,
        modifier = Modifier.fillMaxWidth().systemGestureExclusion(),
    )
    OptionChips(SpeedPresets, speed, label = ::speedLabel, onSelect = onSpeed)
    durationMs?.takeIf { it > 0 }?.let {
        Hint("New length: ${formatDuration((it / speed).toLong())} (was ${formatDuration(it)})")
    }
}

@Composable
fun VideoSpeedScreen(tool: Tool, onBack: () -> Unit) {
    var speed by rememberSaveable { mutableFloatStateOf(2f) }
    VideoToolFrame(
        tool = tool,
        onBack = onBack,
        exportLabel = "Save",
        canExport = { it != null && speed != 1f },
        preview = { uri, _, _ -> VideoPreview(uri, speed = speed) },
        options = { _, meta -> SpeedOptions(speed, meta?.durationMs) { speed = it } },
        task = { ctx, uri, _, progress -> listOf(VideoEngine.speed(ctx, uri, speed, progress)) },
    )
}

// ---------- Crop & rotate ----------

@Composable
fun VideoCropScreen(tool: Tool, onBack: () -> Unit) {
    var crop by rememberSaveable(stateSaver = CropRectSaver) { mutableStateOf(CropRect.Full) }
    var ratio by rememberSaveable { mutableStateOf(CropRatio.FREE) }
    var rotation by rememberSaveable { mutableIntStateOf(0) }

    /** Display size after the user's rotation; crop coordinates live in this space. */
    fun rotatedSize(meta: VideoMeta): Size =
        if (rotation % 180 == 0) Size(meta.displayWidth, meta.displayHeight) else Size(meta.displayHeight, meta.displayWidth)

    VideoToolFrame(
        tool = tool,
        onBack = onBack,
        exportLabel = "Save",
        canExport = { it != null && (rotation != 0 || crop != CropRect.Full) },
        onNewVideo = { crop = CropRect.Full; ratio = CropRatio.FREE; rotation = 0 },
        preview = { uri, meta, _ ->
            if (meta == null || meta.displayWidth <= 0 || meta.displayHeight <= 0) {
                Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                VideoCropPreview(uri, meta, rotation, rotatedSize(meta), crop, ratio.value(rotatedSize(meta))) { crop = it }
            }
        },
        options = { _, meta ->
            fun applyRatio(r: CropRatio, rot: Int) {
                if (meta != null) {
                    val size = if (rot % 180 == 0) Size(meta.displayWidth, meta.displayHeight) else Size(meta.displayHeight, meta.displayWidth)
                    crop = CropRect.centered(size, r.value(size))
                }
            }
            SectionLabel("Rotate")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { rotation = (rotation + 270) % 360; applyRatio(ratio, rotation) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Rounded.RotateLeft, contentDescription = "Rotate left")
                }
                FilledTonalButton(onClick = { rotation = (rotation + 90) % 360; applyRatio(ratio, rotation) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Rounded.RotateRight, contentDescription = "Rotate right")
                }
            }
            SectionLabel("Aspect ratio")
            OptionChips(CropRatio.entries, ratio, label = { it.label }) { r ->
                ratio = r
                applyRatio(r, rotation)
            }
        },
        task = { ctx, uri, _, progress -> listOf(VideoEngine.cropRotate(ctx, uri, rotation, crop, progress)) },
    )
}

@Composable
private fun VideoCropPreview(
    uri: Uri,
    meta: VideoMeta,
    rotation: Int,
    rotatedSize: Size,
    crop: CropRect,
    ratio: Float?,
    onCropChange: (CropRect) -> Unit,
) {
    val context = LocalContext.current
    var frame by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri, meta) { frame = VideoEngine.frameAt(context, uri, meta, meta.durationMs / 2) }
    val shown = remember(frame, rotation) {
        frame?.let { f ->
            if (rotation == 0) f else Bitmap.createBitmap(f, 0, 0, f.width, f.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
        }
    }
    if (shown == null) {
        Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else {
        CropCanvas(shown, rotatedSize, crop, ratio, onCropChange)
    }
    val out = crop.pixelSize(rotatedSize)
    Text(
        "Output: ${out.width}×${out.height}" + if (rotation != 0) " · rotated $rotation°" else "",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Hint("Frame from the middle of the video. Drag a corner to resize, or drag inside to move.")
}

// ---------- Split ----------

private enum class SplitMode(val label: String) { AT_POINT("At a point"), EQUAL("Equal parts") }

@Composable
fun VideoSplitScreen(tool: Tool, onBack: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf(SplitMode.AT_POINT) }
    var point by rememberSaveable { mutableFloatStateOf(0.5f) }
    var parts by rememberSaveable { mutableIntStateOf(2) }
    var seekTo by remember { mutableStateOf<Long?>(null) }

    fun points(durationMs: Long): List<Long> = when (mode) {
        SplitMode.AT_POINT -> listOf((point * durationMs).toLong())
        SplitMode.EQUAL -> (1 until parts).map { durationMs * it / parts }
    }

    VideoToolFrame(
        tool = tool,
        onBack = onBack,
        exportLabel = "Split",
        seekToMs = seekTo,
        canExport = { meta ->
            meta != null && (listOf(0L) + points(meta.durationMs) + meta.durationMs).zipWithNext().all { (a, b) -> b - a >= MIN_CLIP_MS }
        },
        options = { _, meta ->
            val duration = meta?.durationMs ?: 0L
            OptionChips(SplitMode.entries, mode, label = { it.label }) { mode = it }
            when (mode) {
                SplitMode.AT_POINT -> {
                    SectionLabel("Split at ${formatDuration((point * duration).toLong())}")
                    Slider(
                        value = point,
                        onValueChange = { point = it; seekTo = (it * duration).toLong() },
                        modifier = Modifier.fillMaxWidth().systemGestureExclusion(),
                    )
                    Hint(
                        "Part 1: ${formatDuration((point * duration).toLong())} · " +
                            "Part 2: ${formatDuration(((1 - point) * duration).toLong())}",
                    )
                }
                SplitMode.EQUAL -> {
                    SectionLabel("Number of parts")
                    OptionChips((2..6).toList(), parts, label = { "$it" }) { parts = it }
                    if (duration > 0) Hint("Each part ≈ ${formatDuration(duration / parts)}")
                }
            }
        },
        task = { ctx, uri, meta, progress ->
            val total = meta?.durationMs ?: error("Couldn't read the video length")
            VideoEngine.split(ctx, uri, points(total), total, progress)
        },
    )
}

// ---------- Merge ----------

@Composable
fun VideoMergeScreen(tool: Tool, onBack: () -> Unit) {
    MergeFrame(
        tool = tool,
        onBack = onBack,
        type = PickType.VIDEO,
        exportLabel = "Merge",
        note = "The result uses the first video's size. Others are fitted inside it with black bars if their shape differs.",
        task = { ctx, uris, progress -> VideoEngine.merge(ctx, uris, progress) },
    )
}

private const val MIN_CLIP_MS = 500L
