package com.toolx.app.tools.audio

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.toolx.app.core.formatBytes
import com.toolx.app.tools.Tool
import com.toolx.app.ui.common.AudioPreview
import com.toolx.app.ui.common.Note
import com.toolx.app.ui.common.OptionChips
import com.toolx.app.ui.common.SectionLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

// ---------- Reverse ----------

@Composable
fun AudioReverseScreen(tool: Tool, onBack: () -> Unit) {
    AudioToolFrame(
        tool, onBack, exportLabel = "Reverse",
        options = { Note("The whole track plays backwards.") },
        task = { ctx, uri, _, progress ->
            val pcm = AudioCodec.decode(ctx, uri) { progress(it * 0.4f) }
            val rev = withContext(Dispatchers.Default) { AudioDsp.reverse(pcm) }
            AudioCodec.save(ctx, rev, AudioCodec.Format.M4A, "reversed") { progress(0.4f + it * 0.6f) }
        },
    )
}

// ---------- Noise reduction ----------

private const val PREVIEW_MS = 20_000L

@Composable
fun NoiseReductionScreen(tool: Tool, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var strength by rememberSaveable { mutableFloatStateOf(0.6f) }
    var before by remember { mutableStateOf<File?>(null) }
    var after by remember { mutableStateOf<File?>(null) }
    var previewing by remember { mutableStateOf(false) }
    var previewError by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { before?.delete(); after?.delete() } }

    AudioToolFrame(
        tool, onBack, exportLabel = "Reduce noise",
        onNewFile = { before?.delete(); after?.delete(); before = null; after = null },
        fileOptions = { u, _ ->
            SectionLabel("Strength: ${(strength * 100).roundToInt()}%")
            Slider(strength, { strength = it; after?.delete(); after = null }, valueRange = 0.1f..1f, modifier = Modifier.fillMaxWidth().systemGestureExclusion())
            Note("Works best on steady noise: hiss, hum, fans, traffic rumble. It learns the noise from the quietest moments, so a recording that starts or pauses in silence works best.")
            FilledTonalButton(
                enabled = !previewing,
                onClick = {
                    previewing = true; previewError = null
                    scope.launch {
                        try {
                            val pcm = AudioCodec.decode(context, u, PREVIEW_MS)
                            val clean = withContext(Dispatchers.Default) { AudioDsp.denoise(pcm, strength) }
                            withContext(Dispatchers.IO) {
                                before?.delete(); after?.delete()
                                before = AudioCodec.tempFile(context, "wav").also { AudioCodec.writeWav(it, pcm) }
                                after = AudioCodec.tempFile(context, "wav").also { AudioCodec.writeWav(it, clean) }
                            }
                        } catch (e: CancellationException) { throw e } catch (e: Throwable) {
                            previewError = e.message ?: "Couldn't preview this file"
                        } finally { previewing = false }
                    }
                },
            ) { Text(if (previewing) "Cleaning the first 20 s…" else "Preview the first 20 seconds") }
            previewError?.let { Note(it, error = true) }
            val b = before; val a = after
            if (b != null && a != null) {
                SectionLabel("After")
                AudioPreview(Uri.fromFile(a))
                SectionLabel("Before")
                AudioPreview(Uri.fromFile(b))
            }
        },
        task = { ctx, uri, _, progress ->
            val pcm = AudioCodec.decode(ctx, uri) { progress(it * 0.2f) }
            val clean = withContext(Dispatchers.Default) { AudioDsp.denoise(pcm, strength) { progress(0.2f + it * 0.5f) } }
            AudioCodec.save(ctx, clean, AudioCodec.Format.M4A, "denoised") { progress(0.7f + it * 0.3f) }
        },
    )
}

// ---------- Convert ----------

@Composable
fun AudioConvertScreen(tool: Tool, onBack: () -> Unit) {
    val formats = remember { AudioCodec.available() }
    var format by rememberSaveable { mutableStateOf(AudioCodec.Format.M4A) }
    AudioToolFrame(
        tool, onBack, exportLabel = "Convert",
        savedNote = "Saves as ${format.label} to Download/ToolX/Audio.",
        options = { durationMs ->
            SectionLabel("Save as")
            OptionChips(formats, format, label = { it.label }) { format = it }
            val est = durationMs?.let { ms ->
                val s = ms / 1000.0
                when (format) {
                    AudioCodec.Format.WAV -> (s * 44_100 * 2 * 2).toLong()
                    AudioCodec.Format.FLAC -> (s * 44_100 * 2 * 2 * 0.55).toLong()
                    AudioCodec.Format.M4A -> (s * 128_000 / 8).toLong()
                    AudioCodec.Format.OPUS -> (s * 128_000 / 8).toLong()
                }
            }
            Note(format.note + (est?.let { " · about ${formatBytes(it)} for stereo" } ?: ""))
            Note("MP3 isn't offered: Android has no MP3 encoder. M4A plays anywhere MP3 does and sounds better at the same size.")
        },
        task = { ctx, uri, _, progress ->
            val pcm = AudioCodec.decode(ctx, uri) { progress(it * 0.4f) }
            AudioCodec.save(ctx, pcm, format, "converted") { progress(0.4f + it * 0.6f) }
        },
    )
}
