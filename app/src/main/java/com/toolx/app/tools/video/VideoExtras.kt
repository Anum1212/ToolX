package com.toolx.app.tools.video

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.TextureOverlay
import androidx.media3.common.Effect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.EncoderUtil
import androidx.media3.transformer.Effects
import com.google.common.collect.ImmutableList
import com.toolx.app.core.MediaExport
import com.toolx.app.core.MediaKind
import com.toolx.app.core.MediaSaver
import com.toolx.app.core.SavedMedia
import com.toolx.app.tools.Tool
import com.toolx.app.tools.image.ImageEngine
import com.toolx.app.tools.image.ImageExtrasEngine
import com.toolx.app.tools.image.ImageOps
import com.toolx.app.ui.common.AudioPreview
import com.toolx.app.ui.common.Note
import com.toolx.app.ui.common.OptionChips
import com.toolx.app.ui.common.PickType
import com.toolx.app.ui.common.SectionLabel
import com.toolx.app.ui.common.TextInput
import com.toolx.app.ui.common.ToggleChip
import com.toolx.app.ui.common.rememberDisplayName
import com.toolx.app.ui.common.rememberMediaPicker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

// ---------- engine ----------

@OptIn(UnstableApi::class)
internal object VideoExtrasEngine {

    /** A transparent frame-sized bitmap with the watermark drawn on it (also used for the preview). */
    fun markLayer(width: Int, height: Int, style: ImageExtrasEngine.MarkStyle): Bitmap {
        val clear = Bitmap.createBitmap(width.coerceAtLeast(2), height.coerceAtLeast(2), Bitmap.Config.ARGB_8888)
        return ImageExtrasEngine.watermark(clear, style).also { clear.recycle() }
    }

    /** Burns a text or logo watermark into every frame, sized and placed as in the preview. */
    suspend fun watermark(context: Context, uri: Uri, style: ImageExtrasEngine.MarkStyle, onProgress: (Float) -> Unit): SavedMedia {
        val meta = VideoEngine.readMeta(context, uri) ?: error("Couldn't read this video")
        // Overlays are drawn at their pixel size; a layer the size of the output frame covers it exactly.
        val scale = minOf(1f, 1920f / max(meta.displayWidth, meta.displayHeight))
        val w = (meta.displayWidth * scale).roundToInt() / 2 * 2
        val h = (meta.displayHeight * scale).roundToInt() / 2 * 2
        val layer = withContext(Dispatchers.Default) { markLayer(w, h, style) }
        val effects = buildList {
            if (scale < 1f) add(Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT))
            add(OverlayEffect(ImmutableList.of<TextureOverlay>(BitmapOverlay.createStaticBitmapOverlay(layer))))
        }
        val edited = EditedMediaItem.Builder(MediaItem.fromUri(uri)).setEffects(Effects(listOf(), effects)).build()
        val file = MediaExport.run(context, MediaExport.single(edited), "mp4", onProgress = onProgress)
        return MediaExport.saveAndCleanup(context, file, MediaSaver.fileName("watermarked", "mp4"), MimeTypes.VIDEO_MP4, MediaKind.VIDEO)
    }

    /**
     * Mixes [music] under the video. [originalGain] 0 removes the original sound. With [loop],
     * a short song repeats to fill the video; a long one is always cut at the video's end.
     */
    suspend fun addMusic(
        context: Context, video: Uri, music: Uri, durationMs: Long,
        musicGain: Float, originalGain: Float, loop: Boolean, onProgress: (Float) -> Unit,
    ): SavedMedia {
        val videoItem = EditedMediaItem.Builder(MediaItem.fromUri(video))
            .setRemoveAudio(originalGain <= 0f)
            .setEffects(Effects(if (originalGain > 0f && originalGain != 1f) listOf(MediaExport.gainProcessor(originalGain)) else listOf(), listOf()))
            .build()
        val musicMedia = MediaItem.Builder().setUri(music)
            .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setEndPositionMs(durationMs).build())
            .build()
        val musicItem = EditedMediaItem.Builder(musicMedia)
            .setRemoveVideo(true)
            .setEffects(Effects(listOf(MediaExport.gainProcessor(musicGain)), listOf()))
            .build()
        val composition = Composition.Builder(
            EditedMediaItemSequence.Builder(listOf(videoItem)).build(),
            EditedMediaItemSequence.Builder(listOf(musicItem)).setIsLooping(loop).build(),
        ).build()
        val file = MediaExport.run(context, composition, "mp4", onProgress = onProgress)
        return MediaExport.saveAndCleanup(context, file, MediaSaver.fileName("music", "mp4"), MimeTypes.VIDEO_MP4, MediaKind.VIDEO)
    }

    enum class Codec(val label: String, val mime: String, val note: String) {
        H264("H.264", MimeTypes.VIDEO_H264, "Plays everywhere"),
        H265("H.265 / HEVC", MimeTypes.VIDEO_H265, "About half the size at the same quality; newer devices"),
    }

    fun canEncode(codec: Codec): Boolean = EncoderUtil.getSupportedEncoders(codec.mime).isNotEmpty()

    /**
     * Whether some encoder for [codec] accepts [w]×[h] (either orientation). If none does, Media3
     * silently shrinks the video to fit; the emulator's software H.265 encoder, for example,
     * turned 720×1600 into 180×400.
     */
    fun supportsSize(codec: Codec, w: Int, h: Int): Boolean = EncoderUtil.getSupportedEncoders(codec.mime).any { info ->
        val caps = runCatching { info.getCapabilitiesForType(codec.mime).videoCapabilities }.getOrNull() ?: return@any false
        caps.isSizeSupported(w, h) || caps.isSizeSupported(h, w)
    }

    /** Output size for a source of [w]×[h] with an optional short-side cap. */
    fun targetSize(w: Int, h: Int, maxShortSide: Int?): Pair<Int, Int> {
        if (maxShortSide == null || minOf(w, h) <= maxShortSide) return w to h
        val k = maxShortSide.toFloat() / minOf(w, h)
        return ((w * k).roundToInt() / 2 * 2) to ((h * k).roundToInt() / 2 * 2)
    }

    /**
     * Re-encodes any video the phone can play (MKV, WebM, MOV, 3GP, MP4…) into MP4 with
     * [codec], optionally scaled so its shorter side is at most [maxShortSide].
     */
    suspend fun convert(context: Context, uri: Uri, codec: Codec, maxShortSide: Int?, onProgress: (Float) -> Unit): SavedMedia {
        val meta = VideoEngine.readMeta(context, uri)
        val effects = buildList<Effect> {
            if (maxShortSide != null && meta != null && minOf(meta.displayWidth, meta.displayHeight) > maxShortSide) {
                val k = maxShortSide.toFloat() / minOf(meta.displayWidth, meta.displayHeight)
                val w = (meta.displayWidth * k).roundToInt() / 2 * 2
                val h = (meta.displayHeight * k).roundToInt() / 2 * 2
                add(Presentation.createForWidthAndHeight(w, h, Presentation.LAYOUT_SCALE_TO_FIT))
            }
        }
        val edited = EditedMediaItem.Builder(MediaItem.fromUri(uri)).setEffects(Effects(listOf(), effects)).build()
        val file = MediaExport.run(context, MediaExport.single(edited), "mp4", configure = {
            setVideoMimeType(codec.mime)
            setAudioMimeType(MimeTypes.AUDIO_AAC)
        }, onProgress = onProgress)
        return MediaExport.saveAndCleanup(context, file, MediaSaver.fileName("converted", "mp4"), MimeTypes.VIDEO_MP4, MediaKind.VIDEO)
    }
}

// ---------- screens ----------

private val MarkColors = listOf("White" to 0xFFFFFFFF.toInt(), "Black" to 0xFF000000.toInt(), "Grey" to 0xFF9E9E9E.toInt(), "Red" to 0xFFE53935.toInt())

/** A still frame with the watermark layer drawn over it. */
@Composable
private fun FramePreview(frame: Bitmap, layer: Bitmap?) {
    val img = remember(frame) { frame.asImageBitmap() }
    val over = remember(layer) { layer?.asImageBitmap() }
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val aspect = frame.width.toFloat() / frame.height
        var w = maxWidth
        var h = w / aspect
        if (h > 360.dp) { h = 360.dp; w = h * aspect }
        androidx.compose.foundation.layout.Box(Modifier.size(w, h)) {
            Image(img, "Video frame", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            if (over != null) Image(over, "Watermark", Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        }
    }
}

@Composable
fun VideoWatermarkScreen(tool: Tool, onBack: () -> Unit) {
    val context = LocalContext.current
    var useLogo by rememberSaveable { mutableStateOf(false) }
    var text by rememberSaveable { mutableStateOf("© My name") }
    var spot by rememberSaveable { mutableStateOf(ImageOps.Spot.BOTTOM_RIGHT) }
    var size by rememberSaveable { mutableFloatStateOf(0.25f) }
    var opacity by rememberSaveable { mutableFloatStateOf(0.7f) }
    var color by rememberSaveable { mutableIntStateOf(MarkColors[0].second) }
    var logoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var logo by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(logoUri) { logo = logoUri?.let { u -> runCatching { ImageEngine.loadWorkingCopy(context, u) }.getOrNull() } }
    val pickLogo = rememberMediaPicker(PickType.IMAGE) { logoUri = it }
    val style = ImageExtrasEngine.MarkStyle(if (useLogo) "" else text, if (useLogo) logo else null, spot, size, opacity, color)

    VideoToolFrame(
        tool, onBack, exportLabel = "Add watermark",
        canExport = { if (useLogo) logo != null else text.isNotBlank() },
        preview = { uri, meta, _ ->
            var frame by remember(uri, meta) { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(uri, meta) { meta?.let { frame = VideoEngine.frameAt(context, uri, it, it.durationMs / 2) } }
            var layer by remember(frame) { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(frame, style) {
                val f = frame ?: return@LaunchedEffect
                if (layer != null) delay(100)
                layer = withContext(Dispatchers.Default) { VideoExtrasEngine.markLayer(f.width, f.height, style) }
            }
            frame?.let { FramePreview(it, layer) }
        },
        options = { _, _ ->
            OptionChips(listOf(false, true), useLogo, label = { if (it) "Logo" else "Text" }) { useLogo = it }
            if (useLogo) {
                OutlinedButton(onClick = pickLogo) { Text(if (logoUri == null) "Choose a logo image" else "Change logo") }
            } else {
                TextInput(text, { text = it.take(60) }, "Watermark text")
                SectionLabel("Colour")
                OptionChips(MarkColors.map { it.second }, color, label = { c -> MarkColors.first { it.second == c }.first }) { color = it }
            }
            SectionLabel("Position")
            OptionChips(ImageOps.Spot.entries, spot, label = { it.label }) { spot = it }
            SectionLabel("Size: ${(size * 100).roundToInt()}% of the width")
            Slider(size, { size = it }, valueRange = 0.08f..0.8f, modifier = Modifier.fillMaxWidth().systemGestureExclusion())
            SectionLabel("Opacity: ${(opacity * 100).roundToInt()}%")
            Slider(opacity, { opacity = it }, valueRange = 0.1f..1f, modifier = Modifier.fillMaxWidth().systemGestureExclusion())
        },
        task = { ctx, uri, _, progress ->
            val fullLogo = if (useLogo) logoUri?.let { ImageEngine.loadWorkingCopy(ctx, it) } else null
            listOf(VideoExtrasEngine.watermark(ctx, uri, style.copy(logo = fullLogo), progress))
        },
    )
}

@Composable
fun VideoAddMusicScreen(tool: Tool, onBack: () -> Unit) {
    var music by rememberSaveable { mutableStateOf<Uri?>(null) }
    var musicVol by rememberSaveable { mutableFloatStateOf(1f) }
    var originalVol by rememberSaveable { mutableFloatStateOf(0.3f) }
    var loop by rememberSaveable { mutableStateOf(true) }
    val pickMusic = rememberMediaPicker(PickType.AUDIO) { music = it }

    VideoToolFrame(
        tool, onBack, exportLabel = "Add music",
        canExport = { music != null && (it?.durationMs ?: 0) > 0 },
        options = { _, _ ->
            SectionLabel("Music")
            val m = music
            if (m == null) {
                OutlinedButton(onClick = pickMusic) { Text("Choose a song") }
            } else {
                Text(rememberDisplayName(m) ?: "Song", maxLines = 2)
                AudioPreview(m)
                OutlinedButton(onClick = pickMusic) { Text("Change song") }
            }
            SectionLabel("Music volume: ${(musicVol * 100).roundToInt()}%")
            Slider(musicVol, { musicVol = it }, valueRange = 0f..2f, modifier = Modifier.fillMaxWidth().systemGestureExclusion())
            SectionLabel("Original sound: " + if (originalVol <= 0.01f) "off" else "${(originalVol * 100).roundToInt()}%")
            Slider(originalVol, { originalVol = it }, valueRange = 0f..1.5f, modifier = Modifier.fillMaxWidth().systemGestureExclusion())
            ToggleChip("Repeat the song if it's shorter than the video", loop) { loop = it }
            Note("A longer song is cut where the video ends.")
        },
        task = { ctx, uri, meta, progress ->
            val m = music ?: error("Choose a song first")
            listOf(VideoExtrasEngine.addMusic(ctx, uri, m, meta?.durationMs ?: 0L, musicVol, if (originalVol <= 0.01f) 0f else originalVol, loop, progress))
        },
    )
}

@Composable
fun VideoReverseScreen(tool: Tool, onBack: () -> Unit) {
    VideoToolFrame(
        tool, onBack, exportLabel = "Reverse",
        canExport = { (it?.durationMs ?: 0) in 1..VideoFrames.MAX_REVERSE_MS },
        options = { _, meta ->
            if (meta != null && meta.durationMs > VideoFrames.MAX_REVERSE_MS) {
                Note("Videos up to ${VideoFrames.MAX_REVERSE_MS / 1000} seconds can be reversed (every frame is processed). Trim it first.", error = true)
            } else {
                Note("Picture and sound both play backwards. Output is MP4 up to 1280 px on the long side.")
            }
        },
        task = { ctx, uri, _, progress -> listOf(VideoFrames.reverse(ctx, uri, progress)) },
    )
}

@Composable
fun VideoToGifScreen(tool: Tool, onBack: () -> Unit) {
    var range by rememberSaveable(stateSaver = com.toolx.app.ui.common.FloatRangeSaver) { mutableStateOf(0f..1f) }
    var fps by rememberSaveable { mutableIntStateOf(10) }
    var width by rememberSaveable { mutableIntStateOf(320) }
    var seek by remember { mutableStateOf<Long?>(null) }
    var initialised by rememberSaveable { mutableStateOf(false) }
    VideoToolFrame(
        tool, onBack, exportLabel = "Make GIF",
        seekToMs = seek,
        onNewVideo = { initialised = false },
        canExport = { m -> m != null && (range.endInclusive - range.start) * m.durationMs >= 200 },
        options = { _, meta ->
            val d = meta?.durationMs ?: 0L
            // Start with the first 5 seconds: GIFs get big fast.
            LaunchedEffect(d) { if (d > 0 && !initialised) { range = 0f..minOf(1f, 5000f / d); initialised = true } }
            if (d > 0) {
                val lenMs = ((range.endInclusive - range.start) * d).toLong()
                com.toolx.app.ui.common.ClipRangePicker("Part to use", range, d, { r, s -> range = r; seek = s },
                    summary = "%.1f s".format(lenMs / 1000f))
                SectionLabel("Frames per second")
                OptionChips(listOf(5, 10, 15), fps, label = { "$it fps" }) { fps = it }
                SectionLabel("Width")
                OptionChips(listOf(240, 320, 480), width, label = { "$it px" }) { width = it }
                val frames = (lenMs / 1000f * fps).roundToInt()
                Note("$frames frames. GIFs are large: keep clips short (2–6 s) for sharing." + if (frames > 150) " This one will be big." else "")
            }
        },
        task = { ctx, uri, meta, progress ->
            val d = meta?.durationMs ?: error("Couldn't read this video")
            listOf(VideoFrames.toGif(ctx, uri, (range.start * d).toLong(), (range.endInclusive * d).toLong(), fps, width, progress))
        },
    )
}

@Composable
fun VideoConvertScreen(tool: Tool, onBack: () -> Unit) {
    var codec by rememberSaveable { mutableStateOf(VideoExtrasEngine.Codec.H264) }
    var maxSide by rememberSaveable { mutableIntStateOf(0) }
    val hevc = remember { VideoExtrasEngine.canEncode(VideoExtrasEngine.Codec.H265) }
    VideoToolFrame(
        tool, onBack, exportLabel = "Convert to MP4",
        options = { _, meta ->
            SectionLabel("Video codec")
            OptionChips(VideoExtrasEngine.Codec.entries.filter { it != VideoExtrasEngine.Codec.H265 || hevc }, codec, label = { it.label }) { codec = it }
            Note(codec.note + if (!hevc) ". This phone can't encode H.265." else "")
            SectionLabel("Resolution")
            OptionChips(listOf(0, 1080, 720, 480), maxSide, label = { if (it == 0) "Keep" else "${it}p" }) { maxSide = it }
            if (meta != null) {
                val (tw, th) = VideoExtrasEngine.targetSize(meta.displayWidth, meta.displayHeight, maxSide.takeIf { it > 0 })
                val ok = remember(codec, tw, th) { VideoExtrasEngine.supportsSize(codec, tw, th) }
                if (!ok) Note(
                    "This phone's ${codec.label} encoder can't make ${tw}×$th, so the video would be shrunk to fit. " +
                        "Pick a lower resolution or H.264 to control the size.",
                    error = true,
                )
            }
            Note(
                "Opens anything this phone can play (MKV, WebM, MOV, 3GP, MP4) and saves MP4 with AAC audio" +
                    (meta?.let { ". Source: ${it.resolutionLabel}" } ?: "") + ". MKV/AVI output isn't available.",
            )
        },
        task = { ctx, uri, _, progress -> listOf(VideoExtrasEngine.convert(ctx, uri, codec, maxSide.takeIf { it > 0 }, progress)) },
    )
}
