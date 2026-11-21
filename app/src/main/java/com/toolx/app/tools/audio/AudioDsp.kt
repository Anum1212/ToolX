package com.toolx.app.tools.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Decoded audio: interleaved 16-bit samples ([channels] per frame). */
class Pcm(val sampleRate: Int, val channels: Int, val samples: ShortArray) {
    val frames get() = samples.size / channels
    val durationMs get() = frames * 1000L / sampleRate
}

/** Pure audio maths (no Android types; unit-tested). */
internal object AudioDsp {

    /** Plays backwards: frame order reversed, channels within a frame kept in place. */
    fun reverse(p: Pcm): Pcm {
        val c = p.channels
        val out = ShortArray(p.samples.size)
        val frames = p.frames
        for (f in 0 until frames) {
            val src = (frames - 1 - f) * c
            for (ch in 0 until c) out[f * c + ch] = p.samples[src + ch]
        }
        return Pcm(p.sampleRate, c, out)
    }

    /** Linear-interpolation resampling to [rate] (used for Opus, which needs 48 kHz). */
    fun resample(p: Pcm, rate: Int): Pcm {
        if (rate == p.sampleRate) return p
        val c = p.channels
        val inFrames = p.frames
        val outFrames = (inFrames.toLong() * rate / p.sampleRate).toInt()
        val out = ShortArray(outFrames * c)
        val step = p.sampleRate.toDouble() / rate
        for (f in 0 until outFrames) {
            val pos = f * step
            val i = pos.toInt()
            val t = pos - i
            val j = min(i + 1, inFrames - 1)
            for (ch in 0 until c) {
                val a = p.samples[i * c + ch]
                val b = p.samples[j * c + ch]
                out[f * c + ch] = (a + (b - a) * t).roundToInt().toShort()
            }
        }
        return Pcm(rate, c, out)
    }

    /** 44-byte canonical WAV header for 16-bit PCM. */
    fun wavHeader(sampleRate: Int, channels: Int, dataBytes: Int): ByteArray {
        val b = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()); b.putInt(36 + dataBytes); b.put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()); b.putInt(16); b.putShort(1); b.putShort(channels.toShort())
        b.putInt(sampleRate); b.putInt(sampleRate * channels * 2); b.putShort((channels * 2).toShort()); b.putShort(16)
        b.put("data".toByteArray()); b.putInt(dataBytes)
        return b.array()
    }

    // ---------- FFT ----------

    /** In-place radix-2 FFT; [re]/[im] length must be a power of two. [inverse] includes the 1/n scale. */
    fun fft(re: DoubleArray, im: DoubleArray, inverse: Boolean = false) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val ang = 2 * PI / len * if (inverse) 1 else -1
            val wr = cos(ang); val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0; var ci = 0.0
                for (k in 0 until len / 2) {
                    val ar = re[i + k + len / 2] * cr - im[i + k + len / 2] * ci
                    val ai = re[i + k + len / 2] * ci + im[i + k + len / 2] * cr
                    re[i + k + len / 2] = re[i + k] - ar; im[i + k + len / 2] = im[i + k] - ai
                    re[i + k] += ar; im[i + k] += ai
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr; cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
        if (inverse) for (i in 0 until n) { re[i] /= n; im[i] /= n }
    }

    // ---------- noise reduction ----------

    private const val N = 2048
    private const val HOP = N / 4

    /**
     * Spectral gating. The noise "fingerprint" is the average spectrum of the quietest 10% of
     * the recording (steady hiss, hum, fan noise); each bin is then turned down in proportion to
     * how much of it is noise. [strength] 0..1 sets how hard; a floor keeps it from sounding
     * hollow. Channels are processed separately. [onProgress] gets 0..1.
     */
    fun denoise(p: Pcm, strength: Float, onProgress: (Float) -> Unit = {}): Pcm {
        val c = p.channels
        val out = ShortArray(p.samples.size)
        val window = DoubleArray(N) { 0.5 - 0.5 * cos(2 * PI * it / N) } // Hann; 4× overlap sums to 1.5
        val frames = p.frames
        for (ch in 0 until c) {
            val x = DoubleArray(frames) { p.samples[it * c + ch] / 32768.0 }
            val count = max(1, (frames - N) / HOP + 1)
            // Pass 1: energy per frame (time domain, cheap) to find the quietest 10%.
            val energy = DoubleArray(count) { k ->
                var e = 0.0
                for (i in 0 until N) { val s = if (k * HOP + i < frames) x[k * HOP + i] * window[i] else 0.0; e += s * s }
                e
            }
            val quiet = energy.indices.sortedBy { energy[it] }.take(max(1, count / 10))
            // Pass 2: the noise profile = average spectrum of those quiet frames.
            // (Spectra aren't kept for every frame: 3 minutes would need ~130 MB.)
            val re = DoubleArray(N); val im = DoubleArray(N)
            val noise = DoubleArray(N / 2 + 1)
            for (k in quiet) {
                frameSpectrum(x, k * HOP, window, re, im)
                for (b in 0..N / 2) noise[b] += sqrt(re[b] * re[b] + im[b] * im[b]) / quiet.size
            }
            // Pass 3: gain per bin, smoothed over time, then overlap-add.
            val y = DoubleArray(frames + N)
            val over = 1.0 + 2.0 * strength      // over-subtraction factor
            val floor = 0.15 - 0.1 * strength    // never fully silence a bin
            val prevGain = DoubleArray(N / 2 + 1) { 1.0 }
            for (k in 0 until count) {
                frameSpectrum(x, k * HOP, window, re, im)
                for (b in 0..N / 2) {
                    val m = sqrt(re[b] * re[b] + im[b] * im[b])
                    var g = if (m > 0) 1.0 - over * noise[b] / m else 0.0
                    g = g.coerceIn(floor, 1.0)
                    g = 0.6 * g + 0.4 * prevGain[b] // smoothing avoids "musical noise" chirps
                    prevGain[b] = g
                    re[b] *= g; im[b] *= g
                    if (b in 1 until N / 2) { re[N - b] = re[b]; im[N - b] = -im[b] }
                }
                fft(re, im, inverse = true)
                for (i in 0 until N) y[k * HOP + i] += re[i] * window[i] / 1.5
                if (k % 64 == 0) onProgress((ch + k.toFloat() / count) / c)
            }
            for (f in 0 until frames) {
                out[f * c + ch] = (y[f] * 32767).roundToInt().coerceIn(-32768, 32767).toShort()
            }
        }
        onProgress(1f)
        return Pcm(p.sampleRate, c, out)
    }

    private fun frameSpectrum(x: DoubleArray, start: Int, window: DoubleArray, re: DoubleArray, im: DoubleArray) {
        for (i in 0 until N) {
            val s = if (start + i < x.size) x[start + i] else 0.0
            re[i] = s * window[i]; im[i] = 0.0
        }
        fft(re, im)
    }

    /** Root-mean-square level (0..1) of a sample range, for tests and meters. */
    fun rms(samples: ShortArray, from: Int = 0, to: Int = samples.size): Double {
        var s = 0.0
        for (i in from until to) { val v = samples[i] / 32768.0; s += v * v }
        return sqrt(s / max(1, to - from))
    }
}
