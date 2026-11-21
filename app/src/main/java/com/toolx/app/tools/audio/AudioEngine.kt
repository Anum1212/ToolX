package com.toolx.app.tools.audio

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import com.toolx.app.core.MediaExport
import com.toolx.app.core.MediaKind
import com.toolx.app.core.MediaSaver
import com.toolx.app.core.SavedMedia
import com.toolx.app.ui.common.readDurationMs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Audio operations on Media3 Transformer. Any input the device can decode
 * (MP3, AAC, WAV, FLAC, OGG, Opus…) goes in; output is always AAC in an .m4a file.
 */
@OptIn(UnstableApi::class)
object AudioEngine {

    suspend fun duration(context: Context, uri: Uri): Long? =
        withContext(Dispatchers.IO) { readDurationMs(context, uri) }

    suspend fun trim(context: Context, uri: Uri, startMs: Long, endMs: Long, onProgress: (Float) -> Unit): SavedMedia =
        export(context, MediaExport.single(clip(uri, startMs, endMs)), "trim", onProgress)

    /** Removes [cutStartMs]..[cutEndMs] and joins what's left on either side. */
    suspend fun cut(context: Context, uri: Uri, cutStartMs: Long, cutEndMs: Long, durationMs: Long, onProgress: (Float) -> Unit): SavedMedia {
        val pieces = buildList {
            if (cutStartMs > 0) add(clip(uri, 0, cutStartMs))
            if (cutEndMs < durationMs) add(clip(uri, cutEndMs, durationMs))
        }
        require(pieces.isNotEmpty()) { "Nothing would be left after the cut" }
        return export(context, MediaExport.sequence(pieces), "cut", onProgress)
    }

    suspend fun merge(context: Context, uris: List<Uri>, onProgress: (Float) -> Unit): SavedMedia =
        export(context, MediaExport.sequence(uris.map { item(MediaItem.fromUri(it)) }), "merged", onProgress)

    /**
     * Changes tempo by [speed]. With [keepPitch] voices sound normal; without it,
     * pitch follows speed like a sped-up tape.
     */
    suspend fun speed(context: Context, uri: Uri, speed: Float, keepPitch: Boolean, onProgress: (Float) -> Unit): SavedMedia {
        val processors = MediaExport.speedEffects(speed, keepPitch).first
        return export(context, MediaExport.single(item(MediaItem.fromUri(uri), processors)), "speed", onProgress)
    }

    suspend fun volume(context: Context, uri: Uri, gain: Float, onProgress: (Float) -> Unit): SavedMedia =
        export(context, MediaExport.single(item(MediaItem.fromUri(uri), listOf(MediaExport.gainProcessor(gain)))), "volume", onProgress)

    // ---------- plumbing ----------

    private fun clip(uri: Uri, startMs: Long, endMs: Long): EditedMediaItem = item(
        MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build(),
            )
            .build(),
    )

    /** Audio-only item; video is dropped so embedded cover art can't be treated as a video track. */
    private fun item(mediaItem: MediaItem, processors: List<AudioProcessor> = listOf()): EditedMediaItem =
        EditedMediaItem.Builder(mediaItem)
            .setRemoveVideo(true)
            .setEffects(Effects(processors, listOf()))
            .build()

    private suspend fun export(context: Context, composition: Composition, tag: String, onProgress: (Float) -> Unit): SavedMedia {
        val file = MediaExport.run(
            context,
            composition,
            "m4a",
            configure = { setAudioMimeType(MimeTypes.AUDIO_AAC) },
            onProgress = onProgress,
        )
        return MediaExport.saveAndCleanup(context, file, MediaSaver.fileName(tag, "m4a"), "audio/mp4", MediaKind.AUDIO)
    }
}
