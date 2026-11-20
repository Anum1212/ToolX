package com.toolx.app.tools.video

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toolx.app.core.ProcessingState
import com.toolx.app.core.ProcessingViewModel
import com.toolx.app.core.formatBytes
import com.toolx.app.core.SavedMedia
import com.toolx.app.core.formatDuration
import com.toolx.app.tools.Tool
import com.toolx.app.ui.common.ExportButton
import com.toolx.app.ui.common.OptionChips
import com.toolx.app.ui.common.PickPrompt
import com.toolx.app.ui.common.PickType
import com.toolx.app.ui.common.ProcessingStatus
import com.toolx.app.ui.common.SectionLabel
import com.toolx.app.ui.common.SelectedInfo
import com.toolx.app.ui.common.ToolScaffold
import com.toolx.app.ui.common.rememberMediaPicker

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
    task: suspend (context: Context, uri: Uri, meta: VideoMeta?, onProgress: (Float) -> Unit) -> SavedMedia,
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
            vm.run { ctx, progress -> task(ctx, current, m, progress) }
        }
        ProcessingStatus(state, onCancel = vm::cancel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
        canExport = { meta -> meta != null && (range.endInclusive - range.start) * meta.durationMs >= MIN_CLIP_MS },
        options = { _, meta ->
            val duration = meta?.durationMs ?: 0L
            SectionLabel("Keep")
            RangeSlider(
                value = range,
                onValueChange = { new ->
                    // Preview whichever handle moved.
                    seekTo = if (new.start != range.start) {
                        (new.start * duration).toLong()
                    } else {
                        (new.endInclusive * duration).toLong()
                    }
                    range = new
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth()) {
                val startMs = (range.start * duration).toLong()
                val endMs = (range.endInclusive * duration).toLong()
                Text(formatDuration(startMs), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Length ${formatDuration(endMs - startMs)}",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(formatDuration(endMs), style = MaterialTheme.typography.bodyMedium)
            }
        },
        task = { ctx, uri, meta, progress ->
            val total = meta?.durationMs ?: error("Couldn't read the video length")
            VideoEngine.trim(ctx, uri, (range.start * total).toLong(), (range.endInclusive * total).toLong(), progress)
        },
    )
}

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
                Text(
                    "The video is already ${meta.shortSide}p or smaller, so only the bitrate will be lowered.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        task = { ctx, uri, _, progress -> VideoEngine.compress(ctx, uri, preset, progress) },
    )
}

@Composable
fun VideoMuteScreen(tool: Tool, onBack: () -> Unit) {
    VideoToolFrame(
        tool = tool,
        onBack = onBack,
        exportLabel = "Save without audio",
        task = { ctx, uri, _, progress -> VideoEngine.mute(ctx, uri, progress) },
    )
}

@Composable
fun VideoExtractAudioScreen(tool: Tool, onBack: () -> Unit) {
    VideoToolFrame(
        tool = tool,
        onBack = onBack,
        exportLabel = "Extract audio",
        task = { ctx, uri, _, progress -> VideoEngine.extractAudio(ctx, uri, progress) },
    )
}

private const val MIN_CLIP_MS = 500L

private val FloatRangeSaver = Saver<ClosedFloatingPointRange<Float>, List<Float>>(
    save = { listOf(it.start, it.endInclusive) },
    restore = { it[0]..it[1] },
)
