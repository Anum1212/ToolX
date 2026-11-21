package com.toolx.app.tools.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class AudioDspTest {

    @Test fun reverseKeepsChannelPairs() {
        // Stereo frames (L,R): (1,2) (3,4) (5,6) → (5,6) (3,4) (1,2)
        val p = Pcm(44100, 2, shortArrayOf(1, 2, 3, 4, 5, 6))
        assertArrayEquals(shortArrayOf(5, 6, 3, 4, 1, 2), AudioDsp.reverse(p).samples)
        val twice = AudioDsp.reverse(AudioDsp.reverse(p))
        assertArrayEquals(p.samples, twice.samples)
    }

    @Test fun resampleKeepsDurationAndShape() {
        val src = Pcm(44100, 1, ShortArray(44100) { (sin(2 * PI * 440 * it / 44100) * 10000).toInt().toShort() })
        val out = AudioDsp.resample(src, 48000)
        assertEquals(48000, out.frames)
        assertEquals(src.durationMs, out.durationMs)
        assertEquals(AudioDsp.rms(src.samples), AudioDsp.rms(out.samples), 0.01)
    }

    @Test fun wavHeaderFields() {
        val h = ByteBuffer.wrap(AudioDsp.wavHeader(44100, 2, 1000)).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(h.array(), 0, 4))
        assertEquals(1036, h.getInt(4))
        assertEquals("WAVE", String(h.array(), 8, 4))
        assertEquals(2, h.getShort(22).toInt())        // channels
        assertEquals(44100, h.getInt(24))             // sample rate
        assertEquals(176400, h.getInt(28))            // byte rate = 44100 × 2 × 2
        assertEquals(16, h.getShort(34).toInt())       // bits
        assertEquals(1000, h.getInt(40))              // data size
    }

    @Test fun fftFindsTheToneAndInverts() {
        val n = 1024
        val re = DoubleArray(n) { sin(2 * PI * 50 * it / n) }
        val orig = re.copyOf()
        val im = DoubleArray(n)
        AudioDsp.fft(re, im)
        // A sine of 50 cycles per window peaks in bin 50 with magnitude n/2.
        val mags = DoubleArray(n / 2) { hypot(re[it], im[it]) }
        assertEquals(50, mags.indices.maxBy { mags[it] })
        assertEquals(n / 2.0, mags[50], 1e-6)
        AudioDsp.fft(re, im, inverse = true)
        for (i in 0 until n) assertEquals(orig[i], re[i], 1e-9)
    }

    @Test fun denoiseRemovesHissButKeepsTheTone() {
        val rate = 16000
        val rnd = Random(1)
        // 1 s of hiss alone (so the noise can be learned), then 2 s of tone + the same hiss.
        val frames = rate * 3
        val tone = DoubleArray(frames) { if (it >= rate) sin(2 * PI * 440 * it / rate) * 0.3 else 0.0 }
        val noise = DoubleArray(frames) { (rnd.nextDouble() * 2 - 1) * 0.05 }
        val p = Pcm(rate, 1, ShortArray(frames) { ((tone[it] + noise[it]) * 32767).toInt().toShort() })
        val out = AudioDsp.denoise(p, 0.8f)
        // Silence part: hiss strongly reduced.
        val before = AudioDsp.rms(p.samples, 2048, rate - 2048)
        val after = AudioDsp.rms(out.samples, 2048, rate - 2048)
        assertTrue("hiss $before → $after", after < before * 0.35)
        // Tone part: the tone survives (its RMS is 0.3/√2 ≈ 0.212).
        val toneRms = AudioDsp.rms(out.samples, rate + 4096, frames - 4096)
        assertTrue("tone $toneRms", abs(toneRms - 0.212) < 0.04)
    }
}
