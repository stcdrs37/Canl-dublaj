package com.example.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Pure Kotlin Resampler (no Android dependencies):
 * 1) Converts interleaved Stereo (or Mono) 16-bit PCM to Mono.
 * 2) Applies a 33-tap Hamming-windowed sinc low-pass FIR filter with a 7 kHz cutoff
 *    computed dynamically for [inputSampleRate].
 * 3) Downsamples to [outputSampleRate] (16000 Hz) using fractional-step linear interpolation.
 */
class Resampler(
    val inputSampleRate: Int,
    val inputChannels: Int = 2,
    val outputSampleRate: Int = 16000
) {
    companion object {
        private const val FIR_TAPS = 33
        private const val CUTOFF_HZ = 7000.0
    }

    private val firCoeffs: DoubleArray = buildLowPassFir(inputSampleRate, CUTOFF_HZ, FIR_TAPS)
    private val firDelayLine = DoubleArray(FIR_TAPS)
    private var firWriteIndex = 0

    private val step: Double = inputSampleRate.toDouble() / outputSampleRate.toDouble()
    private var phase: Double = 0.0
    private var prevFilteredSample: Double = 0.0
    private var hasPrevSample: Boolean = false

    private fun buildLowPassFir(sampleRate: Int, cutoffHz: Double, taps: Int): DoubleArray {
        val coeffs = DoubleArray(taps)
        val fc = (cutoffHz / sampleRate.toDouble()).coerceIn(0.01, 0.49)
        val mid = (taps - 1) / 2.0
        var sum = 0.0
        for (n in 0 until taps) {
            val k = n - mid
            val sinc = if (k == 0.0) {
                2.0 * fc
            } else {
                sin(2.0 * PI * fc * k) / (PI * k)
            }
            val hamming = 0.54 - 0.46 * cos(2.0 * PI * n / (taps - 1))
            val c = sinc * hamming
            coeffs[n] = c
            sum += c
        }
        if (sum != 0.0) {
            for (n in 0 until taps) {
                coeffs[n] /= sum
            }
        }
        return coeffs
    }

    private fun pushFir(monoSample: Double): Double {
        firDelayLine[firWriteIndex] = monoSample
        var acc = 0.0
        var idx = firWriteIndex
        for (i in 0 until FIR_TAPS) {
            acc += firCoeffs[i] * firDelayLine[idx]
            idx--
            if (idx < 0) idx = FIR_TAPS - 1
        }
        firWriteIndex = (firWriteIndex + 1) % FIR_TAPS
        return acc
    }

    /**
     * Processes interleaved 16-bit PCM samples ([input], length [inputFrames] * [inputChannels])
     * and returns newly resampled 16 kHz mono 16-bit samples.
     */
    fun process(input: ShortArray, inputSamplesCount: Int = input.size): ShortArray {
        val frames = inputSamplesCount / inputChannels
        if (frames <= 0) return ShortArray(0)

        val maxOut = ((frames / step) + 4).toInt()
        val outBuffer = ShortArray(maxOut)
        var outCount = 0

        for (f in 0 until frames) {
            val mono: Double = if (inputChannels >= 2) {
                val base = f * inputChannels
                (input[base].toInt() + input[base + 1].toInt()) * 0.5
            } else {
                input[f].toDouble()
            }

            val currFiltered = pushFir(mono)
            if (!hasPrevSample) {
                prevFilteredSample = currFiltered
                hasPrevSample = true
                phase = 0.0
                outBuffer[outCount++] = currFiltered.roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                phase += step
                continue
            }

            // Advance fractional phase across [prevFilteredSample, currFiltered]
            while (phase <= 1.0) {
                val interpolated = prevFilteredSample + phase * (currFiltered - prevFilteredSample)
                val clamped = interpolated.roundToInt()
                    .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()
                if (outCount < outBuffer.size) {
                    outBuffer[outCount++] = clamped
                }
                phase += step
            }
            phase -= 1.0
            prevFilteredSample = currFiltered
        }

        return if (outCount == outBuffer.size) outBuffer else outBuffer.copyOf(outCount)
    }
}
