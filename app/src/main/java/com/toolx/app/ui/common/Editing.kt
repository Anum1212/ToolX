package com.toolx.app.ui.common

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.toolx.app.core.ProcessingViewModel
import com.toolx.app.core.SavedMedia
import com.toolx.app.core.formatDuration
import com.toolx.app.tools.Tool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val FloatRangeSaver = Saver<ClosedFloatingPointRange<Float>, List<Float>>(
    save = { listOf(it.start, it.endInclusive) },
    restore = { it[0]..it[1] },
)

private val UriListSaver = listSaver<List<Uri>, String>(
    save = { list -> list.map { it.toString() } },
    restore = { list -> list.map { Uri.parse(it) } },
)

/** Duration of any audio or video Uri in ms, or null if unreadable. */
fun readDurationMs(context: Context, uri: Uri): Long? = runCatching {
    val r = MediaMetadataRetriever()
    try {
        r.setDataSource(context, uri)
        r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
    } finally {
        r.release()
    }
}.getOrNull()

/**
 * Range slider over a clip, as fractions of [durationMs]. Reports which handle
 * moved as a seek position so the preview can jump to it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipRangePicker(
    label: String,
    range: ClosedFloatingPointRange<Float>,
    durationMs: Long,
    onChange: (range: ClosedFloatingPointRange<Float>, seekMs: Long) -> Unit,
    summary: String,
) {
    SectionLabel(label)
    RangeSlider(
        value = range,
        onValueChange = { new ->
            val moved = if (new.start != range.start) new.start else new.endInclusive
            onChange(new, (moved * durationMs).toLong())
        },
        // Handles sit at the screen edges; without this, dragging them triggers the system Back gesture.
        modifier = Modifier.fillMaxWidth().systemGestureExclusion(),
    )
    Row(Modifier.fillMaxWidth()) {
        Text(formatDuration((range.start * durationMs).toLong()), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(summary, Modifier.weight(2f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Text(formatDuration((range.endInclusive * durationMs).toLong()), style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Frame for tools that join several files: pick, reorder, remove, then export.
 * [type] decides which picker is used.
 */
@Composable
fun MergeFrame(
    tool: Tool,
    onBack: () -> Unit,
    type: PickType,
    exportLabel: String,
    note: String? = null,
    task: suspend (context: Context, uris: List<Uri>, onProgress: (Float) -> Unit) -> SavedMedia,
) {
    val vm: ProcessingViewModel = viewModel()
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    var uris by rememberSaveable(stateSaver = UriListSaver) { mutableStateOf<List<Uri>>(emptyList()) }
    val durations = remember { mutableStateMapOf<Uri, Long>() }
    LaunchedEffect(uris) {
        uris.filter { it !in durations }.forEach { u ->
            withContext(Dispatchers.IO) { readDurationMs(context, u) }?.let { durations[u] = it }
        }
    }

    val add = rememberMultiMediaPicker(type) { picked ->
        uris = uris + picked.filter { it !in uris }
        vm.reset()
    }

    ToolScaffold(title = tool.title, onBack = onBack) {
        if (uris.isEmpty()) {
            PickPrompt(
                if (type == PickType.AUDIO) "Choose audio files" else "Choose videos",
                Icons.Rounded.LibraryAdd,
                onClick = add,
            )
            return@ToolScaffold
        }

        val total = uris.sumOf { durations[it] ?: 0L }
        Text(
            "${uris.size} files · total ${formatDuration(total)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            uris.forEachIndexed { index, uri ->
                MergeRow(
                    index = index,
                    uri = uri,
                    durationMs = durations[uri],
                    canMoveUp = index > 0,
                    canMoveDown = index < uris.lastIndex,
                    onMove = { delta ->
                        uris = uris.toMutableList().apply { add(index + delta, removeAt(index)) }
                        vm.reset()
                    },
                    onRemove = {
                        uris = uris - uri
                        vm.reset()
                    },
                )
            }
        }
        OutlinedButton(onClick = add, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Text("Add more", Modifier.padding(start = 8.dp))
        }
        note?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ExportButton(exportLabel, state, enabled = uris.size >= 2) {
            val list = uris
            vm.run { ctx, progress -> task(ctx, list, progress) }
        }
        if (uris.size < 2) {
            Text("Add at least two files.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ProcessingStatus(state, onCancel = vm::cancel)
    }
}

@Composable
private fun MergeRow(
    index: Int,
    uri: Uri,
    durationMs: Long?,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    val name = rememberDisplayName(uri)
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${index + 1}.", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(end = 8.dp))
            Column(Modifier.weight(1f)) {
                Text(name ?: "File ${index + 1}", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                durationMs?.let {
                    Text(formatDuration(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
                Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move up")
            }
            IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move down")
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Close, contentDescription = "Remove")
            }
        }
    }
}

/** Speed choices shared by video and audio speed tools. */
val SpeedPresets = listOf(0.25f, 0.5f, 0.75f, 1.25f, 1.5f, 2f, 3f, 4f)

fun speedLabel(speed: Float): String =
    (if (speed % 1f == 0f) speed.toInt().toString() else "%.2f".format(speed).trimEnd('0').trimEnd('.')) + "×"
