package com.toolx.app.tools.video

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.toolx.app.core.formatBytes
import com.toolx.app.ui.common.rememberContentSize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** Wait after the last preset change before encoding a sample. */
private const val SAMPLE_DEBOUNCE_MS = 400L

/**
 * Live preview for video compression.
 *
 * - Shows an instant bitrate-based size estimate.
 * - Encodes a 3-second sample with the real export settings, lets the user switch
 *   between it and the original, and refines the size estimate from its measured size.
 *
 * [paused] stops sampling (e.g. while the real export is running, so the two
 * don't compete for the hardware encoder).
 */
@Composable
fun CompressLivePreview(uri: Uri, meta: VideoMeta?, preset: CompressPreset, paused: Boolean) {
    val context = LocalContext.current
    var sample by remember(uri) { mutableStateOf<CompressSample?>(null) }
    var rendering by remember { mutableStateOf(false) }
    var sampleFailed by remember(uri) { mutableStateOf(false) }
    var showSample by remember(uri) { mutableStateOf(true) }

    // Delete sample files: the old one when replaced, the last one when leaving.
    val latestSample by rememberUpdatedState(sample)
    DisposableEffect(uri) { onDispose { latestSample?.file?.delete() } }

    LaunchedEffect(uri, meta, preset, paused) {
        if (paused || meta == null || meta.durationMs <= 0) return@LaunchedEffect
        if (sample?.preset == preset) return@LaunchedEffect
        delay(SAMPLE_DEBOUNCE_MS)
        rendering = true
        sampleFailed = false
        try {
            val fresh = VideoEngine.renderCompressSample(context, uri, meta, preset)
            val old = sample
            sample = fresh
            old?.file?.delete()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            sampleFailed = true
        } finally {
            rendering = false
        }
    }

    val current = sample?.takeIf { it.preset == preset }
    val playing = current?.takeIf { showSample }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (playing != null) {
            VideoPreview(Uri.fromFile(playing.file), loop = true)
        } else {
            VideoPreview(uri)
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = playing == null, onClick = { showSample = false }, label = { Text("Original") })
            FilterChip(
                selected = playing != null,
                onClick = { showSample = true },
                enabled = current != null,
                label = { Text("${preset.label} sample") },
            )
            if (rendering) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(
                    "Encoding sample…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (meta != null) EstimateLine(uri, meta, preset, current)
        if (sampleFailed) {
            Text(
                "Couldn't encode a preview sample on this device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun EstimateLine(uri: Uri, meta: VideoMeta, preset: CompressPreset, sample: CompressSample?) {
    val originalBytes = rememberContentSize(uri)
    val (w, h) = VideoEngine.outputSize(meta, preset)
    // Prefer the measured sample; fall back to bitrate × duration.
    val estimate = if (sample != null && sample.lengthMs > 0) {
        sample.bytes * meta.durationMs / sample.lengthMs
    } else {
        VideoEngine.estimateBytes(meta, preset)
    }
    val change = originalBytes?.takeIf { it > 0 }?.let { orig ->
        val pct = ((estimate - orig) * 100.0 / orig).roundToInt()
        when {
            pct < 0 -> "${-pct}% smaller"
            pct > 0 -> "$pct% larger"
            else -> "same size"
        }
    }
    Text(
        buildString {
            append("Output: ≈ ${formatBytes(estimate)} · ${w}×${h}")
            change?.let { append(" · $it") }
        },
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
    )
    Text(
        if (sample != null) "Estimated from a ${sample.lengthMs / 1000}-second encoded sample." else "Estimated from bitrate; refining with a sample…",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
