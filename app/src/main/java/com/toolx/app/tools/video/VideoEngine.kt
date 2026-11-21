package com.toolx.app.tools.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.toolx.app.core.MediaExport
import com.toolx.app.core.MediaKind
import com.toolx.app.core.MediaSaver
import com.toolx.app.core.SavedMedia
import com.toolx.app.tools.image.CropRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

data class VideoMeta(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val rotation: Int,
    /** Overall bitrate in bits/s, 0 if unknown. */
    val bitrate: Int = 0,
    /** Audio bitrate in bits/s, 0 if there's no audio track. */
    val audioBitrate: Int = 0,
) {
    /** Video-only bitrate, 0 if unknown. */
    val videoBitrate get() = if (bitrate > 0) (bitrate - audioBitrate).coerceAtLeast(0) else 0
    /** Resolution as displayed (accounts for portrait videos stored rotated). */
    val displayWidth get() = if (rotation % 180 == 0) width else height
    val displayHeight get() = if (rotation % 180 == 0) height else width
    val shortSide get() = min(width, height)
    val resolutionLabel get() = "${displayWidth}×${displayHeight}"
}

/** A short encoded sample used to preview compression. Delete [file] when done. */
data class CompressSample(val file: File, val preset: CompressPreset, val bytes: Long, val lengthMs: Long)

enum class CompressPreset(val label: String, val shortSide: Int, val bitrate: Int) {
    P1080("1080p", 1080, 6_000_000),
    P720("720p", 720, 3_000_000),
    P480("480p", 480, 1_200_000),
    P360("360p", 360, 700_000),
}

/**
 * Video operations built on Media3 Transformer, which uses the device's hardware
 * codecs. Every operation exports to a cache file, then copies it into MediaStore.
 */
@OptIn(UnstableApi::class)
object VideoEngine {

    suspend fun readMeta(context: Context, uri: Uri): VideoMeta? = withContext(Dispatchers.IO) {
        runCatching {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(context, uri)
                fun key(k: Int) = r.extractMetadata(k)?.toIntOrNull() ?: 0
                val hasAudio = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
                VideoMeta(
                    durationMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                    width = key(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),
                    height = key(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT),
                    rotation = key(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION),
                    bitrate = key(MediaMetadataRetriever.METADATA_KEY_BITRATE),
                    audioBitrate = if (hasAudio) readAudioBitrate(context, uri) ?: DEFAULT_AUDIO_BITRATE else 0,
                )
            } finally {
                r.release()
            }
        }.getOrNull()
    }

    suspend fun trim(context: Context, uri: Uri, startMs: Long, endMs: Long, onProgress: (Float) -> Unit): SavedMedia =
        exportVideo(context, clipItem(uri, startMs, endMs), "trim", onProgress = onProgress)

    suspend fun compress(context: Context, uri: Uri, preset: CompressPreset, onProgress: (Float) -> Unit): SavedMedia {
        val meta = readMeta(context, uri)
        return exportVideo(
            context,
            compressItem(uri, meta, preset),
            "compressed",
            onProgress = onProgress,
            configure = compressConfig(context, meta, preset),
        )
    }

    // ---------- compress estimates & preview ----------

    /** Encoder bitrate for [preset], never above 90% of the source so "compress" can't grow the file. */
    fun targetVideoBitrate(meta: VideoMeta?, preset: CompressPreset): Int {
        val src = meta?.videoBitrate ?: 0
        return if (src > 0) min(preset.bitrate, (src * 0.9).toInt()) else preset.bitrate
    }

    /** Displayed output resolution for [preset] (never upscales; even dimensions for the encoder). */
    fun outputSize(meta: VideoMeta, preset: CompressPreset): Pair<Int, Int> {
        val scale = if (meta.shortSide > preset.shortSide) preset.shortSide.toFloat() / meta.shortSide else 1f
        fun even(x: Float) = ((x / 2).roundToInt() * 2).coerceAtLeast(2)
        return even(meta.displayWidth * scale) to even(meta.displayHeight * scale)
    }

    /** Instant size estimate from bitrate × duration. */
    fun estimateBytes(meta: VideoMeta, preset: CompressPreset): Long =
        (targetVideoBitrate(meta, preset).toLong() + meta.audioBitrate) * meta.durationMs / 8000

    /**
     * Encodes a short clip from the middle of the video with the exact export
     * settings. The caller owns the returned file and must delete it.
     */
    suspend fun renderCompressSample(context: Context, uri: Uri, meta: VideoMeta, preset: CompressPreset): CompressSample {
        val length = min(SAMPLE_LENGTH_MS, meta.durationMs)
        val start = ((meta.durationMs - length) / 2).coerceAtLeast(0)
        val file = runTransformer(
            context,
            compressItem(uri, meta, preset, clipStartMs = start, clipEndMs = start + length),
            "mp4",
            compressConfig(context, meta, preset),
            onProgress = {},
        )
        return CompressSample(file, preset, file.length(), length)
    }

    private fun compressItem(
        uri: Uri,
        meta: VideoMeta?,
        preset: CompressPreset,
        clipStartMs: Long? = null,
        clipEndMs: Long? = null,
    ): EditedMediaItem {
        val builder = MediaItem.Builder().setUri(uri)
        if (clipStartMs != null && clipEndMs != null) {
            builder.setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clipStartMs)
                    .setEndPositionMs(clipEndMs)
                    .build(),
            )
        }
        val effects = buildList<Effect> {
            if (meta != null && meta.shortSide > preset.shortSide) {
                val scale = preset.shortSide.toFloat() / meta.shortSide
                add(ScaleAndRotateTransformation.Builder().setScale(scale, scale).build())
            }
        }
        return EditedMediaItem.Builder(builder.build())
            .setEffects(Effects(listOf<AudioProcessor>(), effects))
            .build()
    }

    private fun compressConfig(context: Context, meta: VideoMeta?, preset: CompressPreset): Transformer.Builder.() -> Unit = {
        setVideoMimeType(MimeTypes.VIDEO_H264)
        setEncoderFactory(
            DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(
                    VideoEncoderSettings.Builder().setBitrate(targetVideoBitrate(meta, preset)).build(),
                )
                .build(),
        )
    }

    private fun readAudioBitrate(context: Context, uri: Uri): Int? = runCatching {
        val ex = MediaExtractor()
        try {
            ex.setDataSource(context, uri, null)
            (0 until ex.trackCount)
                .map { ex.getTrackFormat(it) }
                .firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?.takeIf { it.containsKey(MediaFormat.KEY_BIT_RATE) }
                ?.getInteger(MediaFormat.KEY_BIT_RATE)
        } finally {
            ex.release()
        }
    }.getOrNull()

    private const val SAMPLE_LENGTH_MS = 3_000L
    private const val DEFAULT_AUDIO_BITRATE = 128_000

    suspend fun mute(context: Context, uri: Uri, onProgress: (Float) -> Unit): SavedMedia {
        val edited = EditedMediaItem.Builder(MediaItem.fromUri(uri)).setRemoveAudio(true).build()
        return exportVideo(context, edited, "muted", onProgress = onProgress)
    }

    suspend fun extractAudio(context: Context, uri: Uri, onProgress: (Float) -> Unit): SavedMedia {
        val edited = EditedMediaItem.Builder(MediaItem.fromUri(uri)).setRemoveVideo(true).build()
        val file = runTransformer(context, edited, "m4a", onProgress = onProgress)
        return saveAndCleanup(context, file, MediaSaver.fileName("audio", "m4a"), "audio/mp4", MediaKind.AUDIO)
    }

    suspend fun volume(context: Context, uri: Uri, gain: Float, onProgress: (Float) -> Unit): SavedMedia {
        val edited = EditedMediaItem.Builder(MediaItem.fromUri(uri))
            .setEffects(Effects(listOf(MediaExport.gainProcessor(gain)), listOf()))
            .build()
        return exportVideo(context, edited, "volume", onProgress = onProgress)
    }

    /** Constant speed change; audio keeps its pitch, like the preview player. */
    suspend fun speed(context: Context, uri: Uri, speed: Float, onProgress: (Float) -> Unit): SavedMedia {
        val (audio, video) = MediaExport.speedEffects(speed, keepPitch = true)
        val edited = EditedMediaItem.Builder(MediaItem.fromUri(uri))
            .setEffects(Effects(audio, listOf(video)))
            .build()
        return exportVideo(context, edited, "speed", onProgress = onProgress)
    }

    /**
     * Rotates clockwise by [rotationCw] degrees, then crops to [crop], which is
     * given in the coordinates of the rotated frame (what the user sees).
     */
    suspend fun cropRotate(context: Context, uri: Uri, rotationCw: Int, crop: CropRect, onProgress: (Float) -> Unit): SavedMedia {
        val effects = buildList<Effect> {
            if (rotationCw % 360 != 0) {
                // Media3 rotates counter-clockwise.
                add(ScaleAndRotateTransformation.Builder().setRotationDegrees((360 - rotationCw % 360).toFloat()).build())
            }
            if (crop != CropRect.Full) {
                // Crop takes normalized device coordinates: -1..1 with y pointing up.
                add(Crop(crop.left * 2 - 1, crop.right * 2 - 1, 1 - crop.bottom * 2, 1 - crop.top * 2))
            }
        }
        val edited = EditedMediaItem.Builder(MediaItem.fromUri(uri))
            .setEffects(Effects(listOf(), effects))
            .build()
        return exportVideo(context, edited, "cropped", onProgress = onProgress)
    }

    /** Splits at [pointsMs] (sorted, strictly inside the video) into consecutive parts. */
    suspend fun split(context: Context, uri: Uri, pointsMs: List<Long>, durationMs: Long, onProgress: (Float) -> Unit): List<SavedMedia> {
        val bounds = listOf(0L) + pointsMs + durationMs
        val parts = bounds.size - 1
        return (0 until parts).map { i ->
            exportVideo(context, clipItem(uri, bounds[i], bounds[i + 1]), "part${i + 1}", onProgress = { p ->
                onProgress((i + p) / parts)
            })
        }
    }

    /**
     * Joins videos in order. Every clip is fitted (letterboxed) into the first
     * clip's resolution so mixed sizes and orientations line up.
     */
    suspend fun merge(context: Context, uris: List<Uri>, onProgress: (Float) -> Unit): SavedMedia {
        val first = readMeta(context, uris.first())
        val fit = first?.let {
            fun even(x: Int) = (x / 2 * 2).coerceAtLeast(2)
            Presentation.createForWidthAndHeight(even(it.displayWidth), even(it.displayHeight), Presentation.LAYOUT_SCALE_TO_FIT)
        }
        val items = uris.map { u ->
            EditedMediaItem.Builder(MediaItem.fromUri(u))
                .setEffects(Effects(listOf(), listOfNotNull(fit)))
                .build()
        }
        val file = MediaExport.run(context, MediaExport.sequence(items), "mp4", onProgress = onProgress)
        return MediaExport.saveAndCleanup(context, file, MediaSaver.fileName("merged", "mp4"), MimeTypes.VIDEO_MP4, MediaKind.VIDEO)
    }

    /**
     * A display-oriented frame (at most 1280 px) for editors such as crop.
     * Some devices return frames unrotated, so the result is rotated to match [meta].
     */
    suspend fun frameAt(context: Context, uri: Uri, meta: VideoMeta, timeMs: Long): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(context, uri)
                val frame = r.getScaledFrameAtTime(timeMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 1280, 1280)
                    ?: return@runCatching null
                val wantLandscape = meta.displayWidth > meta.displayHeight
                val isLandscape = frame.width > frame.height
                if (meta.rotation % 180 != 0 && wantLandscape != isLandscape) {
                    val m = Matrix().apply { postRotate(meta.rotation.toFloat()) }
                    Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height, m, true).also { if (it !== frame) frame.recycle() }
                } else {
                    frame
                }
            } finally {
                r.release()
            }
        }.getOrNull()
    }

    // ---------- plumbing ----------

    private fun clipItem(uri: Uri, startMs: Long, endMs: Long): EditedMediaItem {
        val item = MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build(),
            )
            .build()
        return EditedMediaItem.Builder(item).build()
    }

    private suspend fun exportVideo(
        context: Context,
        edited: EditedMediaItem,
        tag: String,
        onProgress: (Float) -> Unit,
        configure: Transformer.Builder.() -> Unit = {},
    ): SavedMedia {
        val file = runTransformer(context, edited, "mp4", configure, onProgress)
        return MediaExport.saveAndCleanup(context, file, MediaSaver.fileName(tag, "mp4"), MimeTypes.VIDEO_MP4, MediaKind.VIDEO)
    }

    private suspend fun saveAndCleanup(context: Context, file: File, name: String, mime: String, kind: MediaKind): SavedMedia =
        MediaExport.saveAndCleanup(context, file, name, mime, kind)

    private suspend fun runTransformer(
        context: Context,
        edited: EditedMediaItem,
        extension: String,
        configure: Transformer.Builder.() -> Unit = {},
        onProgress: (Float) -> Unit,
    ): File = MediaExport.run(context, MediaExport.single(edited), extension, configure, onProgress)
}
