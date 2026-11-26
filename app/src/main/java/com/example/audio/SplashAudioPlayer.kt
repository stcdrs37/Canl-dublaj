package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Pure real-time 48 kHz 16-bit Stereo PCM synthesizer for LiveDub's 6.0-second cinematic splash intro.
 * - Uses `USAGE_MEDIA` + `CONTENT_TYPE_SONIFICATION` without requesting exclusive AudioFocus.
 * - Synthesizes a lush Liquid Glass Dmaj9 harmonic swell, crystalline arpeggio chimes, and
 *   a glowing 3.2s signature chord reveal with smooth cosine fade-in/fade-out (zero audio clicks).
 * - Runs on a lightweight background thread and releases cleanly on skip or screen disposal.
 */
class SplashAudioPlayer {

    companion object {
        const val DURATION_MS = 6000L
        const val SAMPLE_RATE = 48000
        private const val DURATION_SEC = DURATION_MS / 1000.0
        private const val TWO_PI = 2.0 * PI

        private data class ChimeNote(
            val startSec: Double,
            val freqHz: Double,
            val pan: Double, // -1.0 left .. +1.0 right
            val gain: Double,
            val decayRate: Double
        )

        // Dmaj9 Liquid Glass harmonic progression synced to the 6.0s splash visual choreography
        private val CHIME_NOTES = listOf(
            // Liquid Prism Ignition arpeggio (0.45s -> 2.4s)
            ChimeNote(0.45, 293.66, -0.45, 0.22, 1.35), // D4
            ChimeNote(0.85, 369.99, 0.38, 0.21, 1.35),  // F#4
            ChimeNote(1.25, 440.00, -0.30, 0.22, 1.30), // A4
            ChimeNote(1.65, 554.37, 0.42, 0.21, 1.30),  // C#5
            ChimeNote(2.05, 659.25, -0.22, 0.20, 1.25), // E5
            ChimeNote(2.45, 880.00, 0.26, 0.18, 1.20),  // A5
            // 3.15s Cyber Shield Lock & "LiveDub" handwritten signature shimmer chord
            ChimeNote(3.15, 587.33, -0.35, 0.22, 0.95), // D5
            ChimeNote(3.18, 739.99, 0.35, 0.18, 0.95),  // F#5
            ChimeNote(3.22, 880.00, 0.0, 0.16, 0.90),   // A5
            ChimeNote(3.28, 1108.73, 0.20, 0.12, 1.10)  // C#6 crystal sparkle
        )

        /**
         * Pure mathematical stereo DSP generator at time [t] (seconds in 0.0..6.0).
         * Returns normalized left/right samples in [-1.0, 1.0].
         */
        fun synthesizeStereoSample(t: Double): Pair<Float, Float> {
            if (t <= 0.0 || t >= DURATION_SEC) return 0f to 0f

            // Smooth global master envelope (0.35s fade-in, 0.95s silky cosine fade-out)
            val fadeIn = (t / 0.35).coerceIn(0.0, 1.0)
            val fadeOut = if (t > DURATION_SEC - 0.95) {
                val p = ((t - (DURATION_SEC - 0.95)) / 0.95).coerceIn(0.0, 1.0)
                0.5 * (1.0 + cos(PI * p))
            } else {
                1.0
            }
            val masterEnv = fadeIn * fadeOut

            // Warm velvet crystal sub-pad (D2 + D3 + A3 with gentle 0.5 Hz stereo breathing)
            val padPulse = 0.78 + 0.22 * sin(TWO_PI * 0.42 * t)
            val padEnv = (t / 1.1).coerceIn(0.0, 1.0) * padPulse
            val subD2 = sin(TWO_PI * 73.42 * t) * 0.17
            val warmD3 = sin(TWO_PI * 146.83 * t) * 0.14
            val fifthA3 = sin(TWO_PI * 220.00 * t + 0.35 * sin(TWO_PI * 0.3 * t)) * 0.09
            val padBase = (subD2 + warmD3 + fifthA3) * padEnv

            val stereoBreath = 0.08 * sin(TWO_PI * 0.35 * t)
            var left = padBase * (1.0 - stereoBreath)
            var right = padBase * (1.0 + stereoBreath)

            // Liquid Glass harmonic bell chimes
            for (note in CHIME_NOTES) {
                val dt = t - note.startSec
                if (dt > 0.0 && dt < 3.2) {
                    val attack = (dt / 0.025).coerceIn(0.0, 1.0)
                    val decay = exp(-dt * note.decayRate)
                    val env = attack * decay * note.gain
                    val fundamental = sin(TWO_PI * note.freqHz * dt)
                    val crystalOvertone = sin(TWO_PI * (note.freqHz * 2.0) * dt) * 0.24 * exp(-dt * 2.6)
                    val glassShimmer = sin(TWO_PI * (note.freqHz * 3.0) * dt) * 0.08 * exp(-dt * 4.5)
                    val voice = (fundamental + crystalOvertone + glassShimmer) * env
                    val panRight = ((note.pan + 1.0) * 0.5).coerceIn(0.0, 1.0)
                    val panLeft = 1.0 - panRight
                    left += voice * panLeft * 1.35
                    right += voice * panRight * 1.35
                }
            }

            // Soft analog saturation limiter for rich, distortion-free speaker & headphone playback
            val outL = (tanh(left * masterEnv * 1.35) * 0.78).toFloat()
            val outR = (tanh(right * masterEnv * 1.35) * 0.78).toFloat()
            return outL to outR
        }
    }

    private val playing = AtomicBoolean(false)
    private var playThread: Thread? = null
    @Volatile
    private var audioTrack: AudioTrack? = null

    fun start() {
        if (!playing.compareAndSet(false, true)) return
        playThread = Thread({ runSynthLoop() }, "LiveDub-SplashAudio").apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        playing.set(false)
        val track = audioTrack
        audioTrack = null
        if (track != null) {
            try {
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.pause()
                    track.flush()
                    track.stop()
                }
            } catch (_: Throwable) {
            }
            try {
                track.release()
            } catch (_: Throwable) {
            }
        }
        try {
            playThread?.interrupt()
        } catch (_: Throwable) {
        }
        playThread = null
    }

    private fun runSynthLoop() {
        var track: AudioTrack? = null
        try {
            val minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) return

            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build()

            val builder = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(minBuf * 2, SAMPLE_RATE * 4 / 10))
                .setTransferMode(AudioTrack.MODE_STREAM)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }
            track = builder.build()
            if (track.state != AudioTrack.STATE_INITIALIZED) {
                track.release()
                return
            }
            audioTrack = track
            track.setVolume(0.82f)
            track.play()

            val framesPerBlock = SAMPLE_RATE / 50 // 20 ms stereo blocks (960 frames)
            val totalFrames = (SAMPLE_RATE * DURATION_MS / 1000L).toInt()
            val pcmBlock = ShortArray(framesPerBlock * 2)
            var frameIndex = 0

            while (playing.get() && frameIndex < totalFrames) {
                val count = minOf(framesPerBlock, totalFrames - frameIndex)
                var si = 0
                for (i in 0 until count) {
                    val t = (frameIndex + i).toDouble() / SAMPLE_RATE.toDouble()
                    val (l, r) = synthesizeStereoSample(t)
                    pcmBlock[si++] = (l * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                    pcmBlock[si++] = (r * 32767f).toInt().coerceIn(-32768, 32767).toShort()
                }
                val written = track.write(pcmBlock, 0, count * 2)
                if (written <= 0) break
                frameIndex += count
            }
        } catch (_: Throwable) {
        } finally {
            playing.set(false)
            val t = audioTrack
            audioTrack = null
            val toRelease = t ?: track
            if (toRelease != null) {
                try {
                    if (toRelease.state == AudioTrack.STATE_INITIALIZED) {
                        toRelease.stop()
                    }
                } catch (_: Throwable) {
                }
                try {
                    toRelease.release()
                } catch (_: Throwable) {
                }
            }
        }
    }
}
