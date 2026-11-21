package com.toolx.app.tools.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import com.toolx.app.core.MediaExport
import com.toolx.app.core.MediaKind
import com.toolx.app.core.MediaSaver
import com.toolx.app.core.SavedMedia
import com.toolx.app.tools.audio.AudioCodec
import com.toolx.app.tools.audio.AudioDsp
import com.toolx.app.tools.audio.Pcm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.roundToInt

/** Frame-by-frame video work the Media3 pipeline can't do: GIFs and reversing. */
internal object VideoFrames {

    data class FrameInfo(val frameCount: Int, val durationMs: Long, val rotation: Int, val width: Int, val height: Int) {
        val fps get() = if (durationMs > 0) frameCount * 1000f / durationMs else 30f
        val displayW get() = if (rotation % 180 == 0) width else height
        val displayH get() = if (rotation % 180 == 0) height else width
    }

    fun info(context: Context, uri: Uri): FrameInfo {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            fun k(key: Int) = r.extractMetadata(key)?.toIntOrNull() ?: 0
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val count = k(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT).takeIf { it > 0 } ?: (dur * 30 / 1000).toInt()
            return FrameInfo(count, dur, k(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION),
                k(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH), k(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
        } finally { r.release() }
    }

    /** Even output size with the display aspect, long side at most [maxLong]. */
    fun outSize(info: FrameInfo, maxLong: Int): Pair<Int, Int> {
        val k = minOf(1f, maxLong.toFloat() / max(info.displayW, info.displayH))
        return ((info.displayW * k).roundToInt() / 2 * 2).coerceAtLeast(2) to ((info.displayH * k).roundToInt() / 2 * 2).coerceAtLeast(2)
    }

    /**
     * Decodes frames [from]..[to] in order, in small batches (forward decoding is fast; seeking
     * per frame is very slow), and hands every [step]-th one, upright and scaled to [w]×[h], to [onFrame].
     */
    suspend fun forEachFrame(
        context: Context, uri: Uri, info: FrameInfo, from: Int, to: Int, step: Int, w: Int, h: Int,
        onFrame: suspend (index: Int, frame: Bitmap) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val params = MediaMetadataRetriever.BitmapParams().apply { preferredConfig = Bitmap.Config.ARGB_8888 }
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            var start = from
            while (start <= to) {
                coroutineContext.ensureActive()
                val n = minOf(BATCH, to - start + 1)
                val batch = runCatching { r.getFramesAtIndex(start, n, params) }.getOrElse { emptyList() }
                batch.forEachIndexed { k, raw ->
                    val idx = start + k
                    if ((idx - from) % step == 0) onFrame(idx, upright(raw, info, w, h, paint))
                    raw.recycle()
                }
                if (batch.isEmpty()) break
                start += n
            }
        } finally { r.release() }
    }

    private const val BATCH = 8

    /** Some devices return frames unrotated: rotate when the orientation doesn't match the display. */
    private fun upright(raw: Bitmap, info: FrameInfo, w: Int, h: Int, paint: Paint): Bitmap {
        val needsRotate = info.rotation % 180 != 0 && (raw.width > raw.height) != (info.displayW > info.displayH)
        val m = Matrix()
        if (needsRotate) {
            m.postRotate(info.rotation.toFloat())
        }
        val rotated = if (needsRotate) Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true) else raw
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(out).drawBitmap(rotated, null, Rect(0, 0, w, h), paint)
        if (rotated !== raw) rotated.recycle()
        return out
    }

    // ---------- GIF ----------

    /** Animated GIF of [startMs]..[endMs] at [fps], [width] px wide. */
    suspend fun toGif(context: Context, uri: Uri, startMs: Long, endMs: Long, fps: Int, width: Int, onProgress: (Float) -> Unit): SavedMedia {
        val info = withContext(Dispatchers.IO) { info(context, uri) }
        val k = width.toFloat() / info.displayW
        val w = (width / 2 * 2).coerceAtLeast(2)
        val h = ((info.displayH * k).roundToInt() / 2 * 2).coerceAtLeast(2)
        val from = (startMs * info.fps / 1000).toInt().coerceIn(0, info.frameCount - 1)
        val to = (endMs * info.fps / 1000).toInt().coerceIn(from, info.frameCount - 1)
        val step = max(1, (info.fps / fps).roundToInt())
        val delayCs = (100f * step / info.fps).roundToInt().coerceAtLeast(2)
        val file = File(context.cacheDir, "gif_${System.nanoTime()}.gif")
        withContext(Dispatchers.IO) {
            file.outputStream().buffered().use { os ->
                val gif = GifEncoder(os, w, h)
                val px = IntArray(w * h)
                forEachFrame(context, uri, info, from, to, step, w, h) { idx, frame ->
                    frame.getPixels(px, 0, w, 0, 0, w, h)
                    frame.recycle()
                    withContext(Dispatchers.Default) { gif.addFrame(px, delayCs) }
                    onProgress((idx - from + 1f) / (to - from + 1))
                }
                gif.finish()
            }
        }
        return MediaExport.saveAndCleanup(context, file, MediaSaver.fileName("animation", "gif"), "image/gif", MediaKind.IMAGE)
    }

    // ---------- Reverse ----------

    /** Longest video reversed: every frame is cached as a JPEG first. */
    const val MAX_REVERSE_MS = 60_000L

    /**
     * Plays the video backwards (sound too). Frames are decoded forward into JPEGs in the cache,
     * then re-encoded last-to-first with the H.264 encoder at the original frame rate.
     */
    suspend fun reverse(context: Context, uri: Uri, onProgress: (Float) -> Unit): SavedMedia {
        val info = withContext(Dispatchers.IO) { info(context, uri) }
        require(info.durationMs <= MAX_REVERSE_MS) { "Videos up to ${MAX_REVERSE_MS / 1000} seconds can be reversed. Trim it first." }
        val (w, h) = outSize(info, 1280)
        val dir = File(context.cacheDir, "reverse_${System.nanoTime()}").apply { mkdirs() }
        val out = File(context.cacheDir, "reversed_${System.nanoTime()}.mp4")
        try {
            // 1. Frames → JPEG files (forward, fast).
            var count = 0
            forEachFrame(context, uri, info, 0, info.frameCount - 1, 1, w, h) { idx, frame ->
                File(dir, "$idx.jpg").outputStream().use { frame.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                frame.recycle()
                count = idx + 1
                onProgress(0.5f * count / info.frameCount)
            }
            require(count > 0) { "Couldn't read the video's frames" }
            // 2. Sound backwards (if any), encoded to AAC up front.
            val audio = runCatching { AudioCodec.decode(context, uri) }.getOrNull()?.let { encodeAac(AudioDsp.reverse(it)) }
            // 3. Encode frames last-to-first.
            withContext(Dispatchers.IO) {
                encodeFrames(dir, count, w, h, info.fps, audio, out, info) { onProgress(0.5f + 0.5f * it) }
            }
            return MediaExport.saveAndCleanup(context, out, MediaSaver.fileName("reversed", "mp4"), "video/mp4", MediaKind.VIDEO)
        } finally {
            dir.deleteRecursively()
            out.delete()
        }
    }

    private class EncodedAudio(val format: MediaFormat, val samples: List<Pair<ByteArray, MediaCodec.BufferInfo>>)

    private suspend fun encodeFrames(dir: File, count: Int, w: Int, h: Int, fps: Float, audio: EncodedAudio?, out: File, info: FrameInfo, onProgress: (Float) -> Unit) {
        val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, (w * h * fps * 0.12f).toInt().coerceIn(1_000_000, 12_000_000))
            setInteger(MediaFormat.KEY_FRAME_RATE, fps.roundToInt().coerceAtLeast(1))
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = encoder.createInputSurface()
        encoder.start()
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var videoTrack = -1
        var started = false
        val frameUs = (1_000_000 / fps).toLong()
        var written = 0
        val bufInfo = MediaCodec.BufferInfo()
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        fun drain(endOfStream: Boolean) {
            while (true) {
                val o = encoder.dequeueOutputBuffer(bufInfo, if (endOfStream) 10_000 else 0)
                when {
                    o == MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) return
                    o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        videoTrack = muxer.addTrack(encoder.outputFormat)
                        val audioTrack = audio?.let { muxer.addTrack(it.format) }
                        muxer.start(); started = true
                        if (audio != null && audioTrack != null) audio.samples.forEach { (bytes, i) -> muxer.writeSampleData(audioTrack, ByteBuffer.wrap(bytes), i) }
                    }
                    o >= 0 -> {
                        val buf = encoder.getOutputBuffer(o)!!
                        if (bufInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && bufInfo.size > 0 && started) {
                            // Surface timestamps are wall-clock; frames come out in order, so restamp them.
                            bufInfo.presentationTimeUs = written * frameUs
                            buf.position(bufInfo.offset); buf.limit(bufInfo.offset + bufInfo.size)
                            muxer.writeSampleData(videoTrack, buf, bufInfo)
                            written++
                        }
                        encoder.releaseOutputBuffer(o, false)
                        if (bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                    }
                }
            }
        }

        try {
            for ((posted, i) in (count - 1 downTo 0).withIndex()) {
                coroutineContext.ensureActive()
                val f = File(dir, "$i.jpg")
                val bmp = BitmapFactory.decodeFile(f.absolutePath) ?: continue
                val canvas = surface.lockHardwareCanvas()
                canvas.drawBitmap(bmp, null, Rect(0, 0, w, h), paint)
                surface.unlockCanvasAndPost(canvas)
                bmp.recycle()
                // Don't run far ahead of the encoder, or frames could be dropped.
                var waits = 0
                drain(false)
                while (posted + 1 - written > 4 && waits++ < 200) { Thread.sleep(5); drain(false) }
                onProgress((posted + 1f) / count)
            }
            encoder.signalEndOfInputStream()
            drain(true)
        } finally {
            runCatching { encoder.stop() }; encoder.release(); surface.release()
            runCatching { if (started) muxer.stop() }; muxer.release()
        }
    }

    /** AAC-LC encode of [p] into memory (small: ~16 KB per second), for muxing next to the video. */
    private fun encodeAac(p: Pcm): EncodedAudio? = runCatching {
        val ch = minOf(p.channels, 2)
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, p.sampleRate, ch).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val src = if (p.channels == ch) p.samples else ShortArray(p.frames * ch) { i -> p.samples[(i / ch) * p.channels + i % ch] }
        val bytes = ByteBuffer.allocate(src.size * 2).order(ByteOrder.nativeOrder()).also { it.asShortBuffer().put(src) }
        val frameBytes = ch * 2
        var pos = 0
        var inDone = false
        var outFormat: MediaFormat? = null
        val samples = mutableListOf<Pair<ByteArray, MediaCodec.BufferInfo>>()
        val info = MediaCodec.BufferInfo()
        try {
            while (true) {
                if (!inDone) {
                    val i = codec.dequeueInputBuffer(10_000)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i)!!; buf.clear()
                        val n = minOf(buf.capacity() / frameBytes * frameBytes, bytes.capacity() - pos)
                        val pts = (pos / frameBytes).toLong() * 1_000_000 / p.sampleRate
                        if (n <= 0) { codec.queueInputBuffer(i, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inDone = true }
                        else { val s = bytes.duplicate(); s.position(pos); s.limit(pos + n); buf.put(s); codec.queueInputBuffer(i, 0, n, pts, 0); pos += n }
                    }
                }
                val o = codec.dequeueOutputBuffer(info, 10_000)
                if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) outFormat = codec.outputFormat
                else if (o >= 0) {
                    val buf = codec.getOutputBuffer(o)!!
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                        val b = ByteArray(info.size); buf.position(info.offset); buf.get(b)
                        samples += b to MediaCodec.BufferInfo().apply { set(0, info.size, info.presentationTimeUs, info.flags) }
                    }
                    codec.releaseOutputBuffer(o, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
        } finally { codec.stop(); codec.release() }
        EncodedAudio(outFormat ?: error("no audio format"), samples)
    }.getOrNull()
}
