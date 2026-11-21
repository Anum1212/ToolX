package com.toolx.app.tools.audio

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toolx.app.core.ProcessingViewModel
import com.toolx.app.core.SavedMedia
import com.toolx.app.core.formatDuration
import com.toolx.app.tools.Tool
import com.toolx.app.tools.video.SpeedOptions
import com.toolx.app.tools.video.VolumeOptions
import com.toolx.app.ui.common.AudioPreview
import com.toolx.app.ui.common.ClipRangePicker
import com.toolx.app.ui.common.ExportButton
import com.toolx.app.ui.common.FloatRangeSaver
import com.toolx.app.ui.common.MergeFrame
import com.toolx.app.ui.common.PickPrompt
import com.toolx.app.ui.common.PickType
import com.toolx.app.ui.common.ProcessingStatus
import com.toolx.app.ui.common.SelectedInfo
import com.toolx.app.ui.common.ToolScaffold
import com.toolx.app.ui.common.rememberDisplayName
import com.toolx.app.ui.common.rememberMediaPicker

/** How the preview player should sound, so it matches the export. */
private data class PreviewTuning(val seekToMs: Long? = null, val speed: Float = 1f, val pitch: Float = 1f, val volume: Float = 1f)

/** Shared layout for single-file audio tools: pick → player → options → export → result. */
@Composable
private fun AudioToolFrame(
    tool: Tool,
    onBack: () -> Unit,
    exportLabel: String,
    canExport: (durationMs: Long?) -> Boolean = { true },
    tuning: PreviewTuning = PreviewTuning(),
    onNewFile: () -> Unit = {},
    options: @Composable (durationMs: Long?) -> Unit = {},
    task: suspend (context: Context, uri: Uri, durationMs: Long?, onProgress: (Float) -> Unit) -> SavedMedia,
) {
    val vm: ProcessingViewModel = viewModel()
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    var uri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var duration by remember(uri) { mutableStateOf<Long?>(null) }
    LaunchedEffect(uri) { uri?.let { duration = AudioEngine.duration(context, it) } }

    val pick = rememberMediaPicker(PickType.AUDIO) {
        uri = it
        vm.reset()
        onNewFile()
    }

    ToolScaffold(title = tool.title, onBack = onBack) {
        val current = uri
        if (current == null) {
            PickPrompt("Choose an audio file", Icons.Rounded.LibraryMusic, onClick = pick)
            return@ToolScaffold
        }
        AudioPreview(current, tuning.seekToMs, tuning.speed, tuning.pitch, tuning.volume)
        val name = rememberDisplayName(current)
        SelectedInfo(
            uri = current,
            details = listOfNotNull(name, duration?.let(::formatDuration)).joinToString(" · ").ifEmpty { null },
            onChange = pick,
        )
        options(duration)
        ExportButton(exportLabel, state, enabled = canExport(duration)) {
            val d = duration
            vm.run { ctx, progress -> task(ctx, current, d, progress) }
        }
        Text(
            "Saved as M4A (AAC) in Music/ToolX.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ProcessingStatus(state, onCancel = vm::cancel)
    }
}

// ---------- Trim ----------

@Composable
fun AudioTrimScreen(tool: Tool, onBack: () -> Unit) {
    var range by rememberSaveable(stateSaver = FloatRangeSaver) { mutableStateOf(0f..1f) }
    var seekTo by remember { mutableStateOf<Long?>(null) }
    AudioToolFrame(
        tool = tool,
        onBack = onBack,
        exportLabel = "Save trimmed audio",
        tuning = PreviewTuning(seekToMs = seekTo),
        onNewFile = { range = 0f..1f },
        canExport = { d -> d != null && (range.endInclusive - range.start) * d >= MIN_CLIP_MS && range != 0f..1f },
        options = { d ->
            val duration = d ?: 0L
            ClipRangePicker(
                label = "Keep",
                range = range,
                durationMs = duration,
                onChange = { r, seek -> range = r; seekTo = seek },
                summary = "Length ${formatDuration(((range.endInclusive - range.start) * duration).toLong())}",
            )
        },
        task = { ctx, uri, d, progress ->
            val total = d ?: error("Couldn't read the audio length")
            AudioEngine.trim(ctx, uri, (range.start * total).toLong(), (range.endInclusive * total).toLong(), progress)
        },
    )
}

// ---------- Cut a section ----------

@Composable
fun AudioCutScreen(tool: Tool, onBack: () -> Unit) {
    var range by rememberSaveable(stateSaver = FloatRangeSaver) { mutableStateOf(0.4f..0.6f) }
    var seekTo by remember { mutableStateOf<Long?>(null) }
    AudioToolFrame(
        tool = tool,
        onBack = onBack,
        exportLabel = "Remove section",
        tuning = PreviewTuning(seekToMs = seekTo),
        onNewFile = { range = 0.4f..0.6f },
        canExport = { d ->
            d != null && (range.endInclusive - range.start) * d >= MIN_CLIP_MS &&
                (1f - (range.endInclusive - range.start)) * d >= MIN_CLIP_MS
        },
        options = { d ->
            val duration = d ?: 0L
            val removed = ((range.endInclusive - range.start) * duration).toLong()
            ClipRangePicker(
                label = "Remove",
                range = range,
                durationMs = duration,
                onChange = { r, seek -> range = r; seekTo = seek },
                summary = "Removes ${formatDuration(removed)}",
            )
            Text(
                "Result: ${formatDuration(duration - removed)}. The parts before and after are joined.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        task = { ctx, uri, d, progress ->
            val total = d ?: error("Couldn't read the audio length")
            AudioEngine.cut(ctx, uri, (range.start * total).toLong(), (range.endInclusive * total).toLong(), total, progress)
        },
    )
}

// ---------- Speed ----------

@Composable
fun AudioSpeedScreen(tool: Tool, onBack: () -> Unit) {
    var speed by rememberSaveable { mutableFloatStateOf(1.25f) }
    var keepPitch by rememberSaveable { mutableStateOf(true) }
    AudioToolFrame(
        tool = tool,
        onBack = onBack,
        exportLabel = "Save",
        tuning = PreviewTuning(speed = speed, pitch = if (keepPitch) 1f else speed),
        canExport = { it != null && speed != 1f },
        options = { d ->
            SpeedOptions(speed, d) { speed = it }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (keepPitch) "Keep pitch: voices sound normal" else "Pitch follows speed (chipmunk / slowed)",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = keepPitch, onCheckedChange = { keepPitch = it })
            }
        },
        task = { ctx, uri, _, progress -> AudioEngine.speed(ctx, uri, speed, keepPitch, progress) },
    )
}

// ---------- Volume ----------

@Composable
fun AudioVolumeScreen(tool: Tool, onBack: () -> Unit) {
    var percent by rememberSaveable { mutableIntStateOf(150) }
    AudioToolFrame(
        tool = tool,
        onBack = onBack,
        exportLabel = "Save",
        tuning = PreviewTuning(volume = percent / 100f),
        canExport = { it != null && percent != 100 },
        options = { VolumeOptions(percent) { percent = it } },
        task = { ctx, uri, _, progress -> AudioEngine.volume(ctx, uri, percent / 100f, progress) },
    )
}

// ---------- Merge ----------

@Composable
fun AudioMergeScreen(tool: Tool, onBack: () -> Unit) {
    MergeFrame(
        tool = tool,
        onBack = onBack,
        type = PickType.AUDIO,
        exportLabel = "Merge",
        note = "Tracks play one after another and are saved as one M4A (AAC) file.",
        task = { ctx, uris, progress -> AudioEngine.merge(ctx, uris, progress) },
    )
}

private const val MIN_CLIP_MS = 500L
