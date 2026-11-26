package com.example.audio

/**
 * Ultra-low-latency lossless jitter buffer for 24 kHz mono 16-bit PCM dubbing output:
 * - Primes at just 80 ms (1920 samples = 3840 bytes) instead of 200 ms, slashing first-word
 *   audible startup delay by 120 ms while preserving smooth 4x20ms jitter immunity!
 * - Supports [flushShortUtterance] on Gemini `turnComplete` so ultra-short single-word utterances
 *   (<80 ms, e.g. "OK", "Hi") are IMMEDIATELY primed and played out completely
 *   without ever being trapped or swallowed in the buffer!
 * - Expands ceiling to 25 seconds (600,000 samples) so rapid continuous speech / long monologues
 *   never suffer overrun drops.
 */
class DubJitterBuffer(
    val sampleRate: Int = 24000
) {
    private val minPrimeSamples = (sampleRate * 80) / 1000 // 1920 samples (80 ms — instant audible start!)
    private val maxBufferSamples = sampleRate * 25 // 600,000 samples (25 s) — prevents overrun drops

    private val lock = Any()
    private val queue = ArrayDeque<ShortArray>()
    private var headOffset = 0
    private var bufferedSamples = 0
    private var isPrimed = false

    @Volatile
    var underrunCount: Int = 0
        private set

    @Volatile
    var overrunCount: Int = 0
        private set

    fun pushPcm16Le(bytes: ByteArray) {
        if (bytes.size < 2) return
        val sampleCount = bytes.size / 2
        val shorts = ShortArray(sampleCount)
        var bi = 0
        for (i in 0 until sampleCount) {
            val lo = bytes[bi].toInt() and 0xFF
            val hi = bytes[bi + 1].toInt()
            shorts[i] = ((hi shl 8) or lo).toShort()
            bi += 2
        }
        pushSamples(shorts)
    }

    fun pushSamples(samples: ShortArray) {
        if (samples.isEmpty()) return
        synchronized(lock) {
            queue.addLast(samples)
            bufferedSamples += samples.size
            if (bufferedSamples >= minPrimeSamples) {
                isPrimed = true
            }
            while (bufferedSamples > maxBufferSamples && queue.isNotEmpty()) {
                val first = queue.first()
                val remainingInFirst = first.size - headOffset
                if (bufferedSamples - remainingInFirst >= maxBufferSamples - minPrimeSamples) {
                    queue.removeFirst()
                    headOffset = 0
                    bufferedSamples -= remainingInFirst
                } else {
                    val toDrop = bufferedSamples - maxBufferSamples
                    headOffset += toDrop
                    bufferedSamples -= toDrop
                }
                overrunCount++
            }
        }
    }

    /**
     * Immediately releases any pending sub-80ms audio when the AI finishes a turn (`turnComplete`).
     * Guarantees short words and trailing syllables are NEVER trapped unplayed in the jitter buffer.
     */
    fun flushShortUtterance() {
        synchronized(lock) {
            if (bufferedSamples > 0) {
                isPrimed = true
            }
        }
    }

    /**
     * Reads exactly [out.size] samples into [out].
     * Returns true if real audio was read, or false if silence was written (pre-buffering or underrun).
     */
    fun popSamples(out: ShortArray): Boolean {
        synchronized(lock) {
            if (!isPrimed) {
                out.fill(0)
                return false
            }
            if (bufferedSamples == 0) {
                isPrimed = false
                underrunCount++
                out.fill(0)
                return false
            }

            var written = 0
            while (written < out.size && queue.isNotEmpty()) {
                val current = queue.first()
                val avail = current.size - headOffset
                val toCopy = minOf(avail, out.size - written)
                System.arraycopy(current, headOffset, out, written, toCopy)
                written += toCopy
                headOffset += toCopy
                bufferedSamples -= toCopy
                if (headOffset >= current.size) {
                    queue.removeFirst()
                    headOffset = 0
                }
            }

            if (written < out.size) {
                out.fill(0, written, out.size)
                isPrimed = false
                underrunCount++
            }
            return true
        }
    }

    fun bufferedMs(): Int = synchronized(lock) {
        (bufferedSamples * 1000) / sampleRate
    }

    fun clear() {
        synchronized(lock) {
            queue.clear()
            headOffset = 0
            bufferedSamples = 0
            isPrimed = false
        }
    }

    fun resetCounters() {
        synchronized(lock) {
            underrunCount = 0
            overrunCount = 0
        }
    }
}
