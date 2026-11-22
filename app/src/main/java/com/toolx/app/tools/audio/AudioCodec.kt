package com.toolx.app.tools.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.EditedMediaItem
import com.toolx.app.core.MediaExport
import com.toolx.app.core.MediaKind
import com.toolx.app.core.MediaSaver
import com.toolx.app.core.SavedMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

/**
 * Decodes any audio the phone can play into 16-bit PCM, and encodes PCM to WAV, FLAC, Opus
 * (OGG) or AAC (M4A), all with the platform codecs. MP3 isn't possible: Android has no MP3 encoder.
 */
@OptIn(UnstableApi::class)
internal object AudioCodec {

    /** Longest input decoded into memory (stereo 44.1 kHz ≈ 10 MB per minute). */
    const val MAX_MS = 20 * 60 * 1000L
    private const val TIMEOUT_US = 10_000L

    enum class Format(val label: String, val ext: String, val mime: String, val codec: String?, val note: String) {
        M4A("M4A (AAC)", "m4a", "audio/mp4", null, "Small, plays everywhere"),
        WAV("WAV", "wav", "audio/x-wav", null, "Uncompressed, largest"),
        FLAC("FLAC", "flac", "audio/flac", MediaFormat.MIMETYPE_AUDIO_FLAC, "Lossless, about half of WAV"),
        OPUS("Opus (OGG)", "ogg", "audio/ogg", MediaFormat.MIMETYPE_AUDIO_OPUS, "Smallest at good quality"),
    }

    fun canEncode(mime: String): Boolean = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
        info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
    }

    fun available(): List<Format> = Format.entries.filter { it.codec == null || canEncode(it.codec) }

    /** Decodes the first audio track (up to [maxMs]). */
    suspend fun decode(context: Context, uri: Uri, maxMs: Long = MAX_MS, onProgress: (Float) -> Unit = {}): Pcm = withContext(Dispatchers.IO) {
        val ex = MediaExtractor()
        ex.setDataSource(context, uri, null)
        try {
            val track = (0 until ex.trackCount).firstOrNull { ex.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: error("No audio track found")
            ex.selectTrack(track)
            val format = ex.getTrackFormat(track)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, 0)
            codec.start()
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var float = false
            val out = ShortBuilder()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            try {
                while (!outputDone) {
                    coroutineContext.ensureActive()
                    if (!inputDone) {
                        val i = codec.dequeueInputBuffer(TIMEOUT_US)
                        if (i >= 0) {
                            val buf = codec.getInputBuffer(i)!!
                            val n = ex.readSampleData(buf, 0)
                            if (n < 0 || ex.sampleTime > maxMs * 1000) {
                                codec.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(i, 0, n, ex.sampleTime, 0)
                                if (durationUs > 0) onProgress((ex.sampleTime.toFloat() / minOf(durationUs, maxMs * 1000)).coerceIn(0f, 1f))
                                ex.advance()
                            }
                        }
                    }
                    when (val o = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val f = codec.outputFormat
                            rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            float = f.containsKey(MediaFormat.KEY_PCM_ENCODING) && f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                        }
                        else -> if (o >= 0) {
                            val buf = codec.getOutputBuffer(o)!!.order(ByteOrder.nativeOrder())
                            buf.position(info.offset); buf.limit(info.offset + info.size)
                            if (float) {
                                val fb = buf.asFloatBuffer()
                                while (fb.hasRemaining()) out.add((fb.get().coerceIn(-1f, 1f) * 32767).toInt().toShort())
                            } else {
                                val sb = buf.asShortBuffer()
                                while (sb.hasRemaining()) out.add(sb.get())
                            }
                            codec.releaseOutputBuffer(o, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                        }
                    }
                }
            } finally {
                codec.stop(); codec.release()
            }
            Pcm(rate, channels, out.toArray())
        } finally {
            ex.release()
        }
    }

    fun writeWav(file: File, p: Pcm) {
        file.outputStream().buffered().use { os ->
            os.write(AudioDsp.wavHeader(p.sampleRate, p.channels, p.samples.size * 2))
            val bb = ByteBuffer.allocate(p.samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            bb.asShortBuffer().put(p.samples)
            os.write(bb.array())
        }
    }

    fun tempFile(context: Context, ext: String) = File(context.cacheDir, "audio_${System.nanoTime()}.$ext")

    /** Saves [p] as [format] into Download/ToolX/Audio. */
    suspend fun save(context: Context, p: Pcm, format: Format, tag: String, onProgress: (Float) -> Unit): SavedMedia {
        val name = MediaSaver.fileName(tag, format.ext)
        return when (format) {
            Format.WAV -> withContext(Dispatchers.IO) {
                val f = tempFile(context, "wav"); writeWav(f, p)
                MediaExport.saveAndCleanup(context, f, name, format.mime, MediaKind.AUDIO)
            }
            Format.M4A -> {
                // Transformer reads WAV, so go through a temp WAV to get Media3's AAC encoder and MP4 muxer.
                val wav = withContext(Dispatchers.IO) { tempFile(context, "wav").also { writeWav(it, p) } }
                try {
                    val edited = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(wav))).build()
                    val out = MediaExport.run(context, MediaExport.single(edited), "m4a", onProgress = onProgress)
                    MediaExport.saveAndCleanup(context, out, name, format.mime, MediaKind.AUDIO)
                } finally { wav.delete() }
            }
            Format.FLAC -> withContext(Dispatchers.IO) {
                val f = tempFile(context, "flac"); encodeFlac(p, f, onProgress)
                MediaExport.saveAndCleanup(context, f, name, format.mime, MediaKind.AUDIO)
            }
            Format.OPUS -> withContext(Dispatchers.IO) {
                val f = tempFile(context, "ogg"); encodeOpus(p, f, onProgress)
                MediaExport.saveAndCleanup(context, f, name, format.mime, MediaKind.AUDIO)
            }
        }
    }

    /** FLAC: the encoder's config buffer is the "fLaC" header + STREAMINFO; frames follow. */
    private suspend fun encodeFlac(p: Pcm, out: File, onProgress: (Float) -> Unit) {
        out.outputStream().buffered().use { os ->
            var wroteHeader = false
            encode(p, MediaFormat.MIMETYPE_AUDIO_FLAC, p.sampleRate, onProgress,
                configure = { it.setInteger(MediaFormat.KEY_FLAC_COMPRESSION_LEVEL, 5) },
                onConfig = { csd ->
                    val bytes = ByteArray(csd.remaining()).also { csd.get(it) }
                    if (!(bytes.size >= 4 && String(bytes, 0, 4) == "fLaC")) os.write("fLaC".toByteArray())
                    os.write(bytes); wroteHeader = true
                },
                onData = { buf, _ ->
                    check(wroteHeader) { "FLAC encoder gave no header" }
                    val b = ByteArray(buf.remaining()); buf.get(b); os.write(b)
                },
            )
        }
    }

    /** Opus needs 48 kHz (resampled) and at most 2 channels; muxed into OGG. */
    private suspend fun encodeOpus(src: Pcm, out: File, onProgress: (Float) -> Unit) {
        val p = AudioDsp.resample(downmix(src), 48_000)
        val muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)
        var track = -1
        try {
            encode(p, MediaFormat.MIMETYPE_AUDIO_OPUS, 48_000, onProgress,
                configure = { it.setInteger(MediaFormat.KEY_BIT_RATE, 128_000) },
                onFormat = { f -> track = muxer.addTrack(f); muxer.start() },
                onConfig = { },
                onData = { buf, info -> muxer.writeSampleData(track, buf, info) },
            )
        } finally {
            runCatching { if (track >= 0) muxer.stop() }
            muxer.release()
        }
    }

    private fun downmix(p: Pcm): Pcm {
        if (p.channels <= 2) return p
        val out = ShortArray(p.frames * 2)
        for (f in 0 until p.frames) {
            var l = 0; var r = 0
            for (c in 0 until p.channels) { if (c % 2 == 0) l += p.samples[f * p.channels + c] else r += p.samples[f * p.channels + c] }
            out[f * 2] = (l / ((p.channels + 1) / 2)).toShort(); out[f * 2 + 1] = (r / (p.channels / 2)).toShort()
        }
        return Pcm(p.sampleRate, 2, out)
    }

    /** Generic PCM → MediaCodec encoder loop. */
    private suspend fun encode(
        p: Pcm, mime: String, rate: Int, onProgress: (Float) -> Unit,
        configure: (MediaFormat) -> Unit,
        onFormat: (MediaFormat) -> Unit = {},
        onConfig: (ByteBuffer) -> Unit,
        onData: (ByteBuffer, MediaCodec.BufferInfo) -> Unit,
    ) {
        val format = MediaFormat.createAudioFormat(mime, rate, p.channels).apply {
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            configure(this)
        }
        val codec = MediaCodec.createEncoderByType(mime)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val bytes = ByteBuffer.allocate(p.samples.size * 2).order(ByteOrder.nativeOrder()).also { it.asShortBuffer().put(p.samples) }
        val total = bytes.capacity()
        val frameBytes = p.channels * 2
        var pos = 0
        var inputDone = false
        var outputDone = false
        val info = MediaCodec.BufferInfo()
        try {
            while (!outputDone) {
                coroutineContext.ensureActive()
                if (!inputDone) {
                    val i = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (i >= 0) {
                        val buf = codec.getInputBuffer(i)!!
                        buf.clear()
                        val n = minOf(buf.capacity() / frameBytes * frameBytes, total - pos)
                        val ptsUs = (pos / frameBytes).toLong() * 1_000_000 / rate
                        if (n <= 0) {
                            codec.queueInputBuffer(i, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val slice = bytes.duplicate(); slice.position(pos); slice.limit(pos + n)
                            buf.put(slice)
                            codec.queueInputBuffer(i, 0, n, ptsUs, 0)
                            pos += n
                            onProgress(pos.toFloat() / total)
                        }
                    }
                }
                when (val o = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> onFormat(codec.outputFormat)
                    else -> if (o >= 0) {
                        val buf = codec.getOutputBuffer(o)!!
                        buf.position(info.offset); buf.limit(info.offset + info.size)
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) onConfig(buf)
                        else if (info.size > 0) onData(buf, info)
                        codec.releaseOutputBuffer(o, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
        } finally {
            codec.stop(); codec.release()
        }
    }

    /** Growable short array (decoded audio can be tens of millions of samples). */
    private class ShortBuilder {
        private var data = ShortArray(1 shl 16)
        private var size = 0
        fun add(v: Short) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = v
        }
        fun toArray(): ShortArray = data.copyOf(size)
    }
}
