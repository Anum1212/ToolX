package com.toolx.app.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.transformer.Effects
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Shared Media3 Transformer plumbing for video and audio tools: run an export into
 * the cache dir, then copy the result into MediaStore.
 */
@OptIn(UnstableApi::class)
object MediaExport {

    fun single(edited: EditedMediaItem): Composition =
        Composition.Builder(EditedMediaItemSequence.Builder(listOf(edited)).build()).build()

    /** Plays [items] back to back. A silent track is added for items without audio. */
    fun sequence(items: List<EditedMediaItem>): Composition =
        Composition.Builder(EditedMediaItemSequence.Builder(items).build())
            .experimentalSetForceAudioTrack(true)
            .build()

    /** Multiplies every channel by [gain] (1 = unchanged). */
    fun gainProcessor(gain: Float): AudioProcessor = ChannelMixingAudioProcessor().apply {
        for (channels in 1..8) {
            runCatching { putChannelMixingMatrix(ChannelMixingMatrix.create(channels, channels).scaleBy(gain)) }
        }
    }

    /**
     * Speed change as an (audio processors, video effect) pair that stays in sync.
     *
     * Media3's speed-changing processor shifts pitch with speed, like a tape. With
     * [keepPitch] a Sonic pitch correction of 1/speed is appended, so voices sound
     * normal (matching ExoPlayer's preview, which keeps pitch).
     */
    fun speedEffects(speed: Float, keepPitch: Boolean): Pair<List<AudioProcessor>, androidx.media3.common.Effect> {
        val pair = Effects.createExperimentalSpeedChangingEffect(ConstantSpeed(speed))
        val audio = buildList {
            add(pair.first)
            if (keepPitch && speed != 1f) add(SonicAudioProcessor().apply { setPitch(1f / speed) })
        }
        return audio to pair.second
    }

    class ConstantSpeed(private val speed: Float) : SpeedProvider {
        override fun getSpeed(timeUs: Long): Float = speed
        override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
    }

    suspend fun saveAndCleanup(context: Context, file: File, name: String, mime: String, kind: MediaKind): SavedMedia =
        withContext(Dispatchers.IO) {
            try {
                MediaSaver.saveFile(context, file, name, mime, kind)
            } finally {
                file.delete()
            }
        }

    /** Runs a Transformer export on the main looper, reporting progress and honoring cancellation. */
    suspend fun run(
        context: Context,
        composition: Composition,
        extension: String,
        configure: Transformer.Builder.() -> Unit = {},
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.Main) {
        val output = File(context.cacheDir, "export_${System.nanoTime()}.$extension")
        val handler = Handler(Looper.getMainLooper())

        suspendCancellableCoroutine { cont ->
            val transformer = Transformer.Builder(context)
                .apply(configure)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        handler.removeCallbacksAndMessages(null)
                        if (cont.isActive) cont.resume(output)
                    }

                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        handler.removeCallbacksAndMessages(null)
                        output.delete()
                        if (cont.isActive) cont.resumeWithException(exportException)
                    }
                })
                .build()

            transformer.start(composition, output.absolutePath)

            val holder = ProgressHolder()
            val poll = object : Runnable {
                override fun run() {
                    if (transformer.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(holder.progress / 100f)
                    }
                    if (cont.isActive) handler.postDelayed(this, 250)
                }
            }
            handler.post(poll)

            cont.invokeOnCancellation {
                handler.post {
                    handler.removeCallbacksAndMessages(null)
                    transformer.cancel()
                    output.delete()
                }
            }
        }
    }
}
