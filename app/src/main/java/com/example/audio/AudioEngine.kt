package com.example.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Process
import androidx.core.content.ContextCompat
import com.example.data.SettingsRepository
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

data class AudioMetrics(
    val inputSampleRate: Int = 48000,
    val captureRmsDb: Float = -90f,
    val captureLevelNorm: Float = 0f,
    val playbackLevelNorm: Float = 0f,
    val isAudioDetected: Boolean = false,
    val captureWaveform: List<Float> = List(32) { 0f },
    val playbackWaveform: List<Float> = List(32) { 0f },
    val isSilencePaused: Boolean = false,
    val drmSilenceWarning: Boolean = false,
    val duckAutoRevertedMessage: String? = null,
    val isOriginalDuckedTo10Percent: Boolean = false,
    val isBluetoothDubOnlyActive: Boolean = false,
    val bluetoothDeviceName: String = ""
)

class AudioEngine(
    private val context: Context,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        private const val OUT_SAMPLE_RATE = 24000
        // Ultra-low-latency 40 ms outgoing chunks (saves 60 ms per packet vs 100 ms chunks!)
        internal const val CHUNK_SAMPLES_16K = 640 // 40 ms at 16 kHz
        internal const val CHUNK_BYTES_16K = 1280 // 640 * 2 bytes
        private const val PRE_ROLL_CHUNKS = 18 // ~720 ms — catches quiet sentence onsets
        // Lowered threshold + higher AGC headroom so original audio ducked to 10% is still 100% intelligible to AI!
        private const val RMS_SILENCE_THRESHOLD = 35.0
        private const val SILENCE_THRESHOLD_MS = 4500L // 4.5s — never clips natural pauses between clauses
        private const val DRM_SILENCE_WARN_MS = 8000L
        private const val RAMP_DURATION_MS = 120L

        /**
         * Media stream dubbing attributes: mixes AI dubbing right over playing media
         * (YouTube, Netflix, Twitch, etc.) using the Media volume channel WITHOUT pausing
         * or stopping the active video/music! Self-capture is excluded via `excludeUid(myUid())`
         * and `android:allowAudioPlaybackCapture="false"`.
         */
        fun buildDubAudioAttributes(): AudioAttributes =
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

        /**
         * Isolated non-alarm dubbing attributes (API 26+) used when original media volume is
         * immediately dropped to 10% or routed in Bluetooth Dub-Only mode: plays on an independent
         * voice/accessibility bus routed cleanly to Bluetooth A2DP/LE headsets & speakers at full
         * clarity (default 60% gain) while original media remains smoothly at 10% without pausing!
         */
        fun buildIsolatedDubAudioAttributes(): AudioAttributes {
            val usage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY
            } else {
                AudioAttributes.USAGE_MEDIA
            }
            return AudioAttributes.Builder()
                .setUsage(usage)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        }
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val bluetoothRouter = BluetoothAudioRouter(audioManager)
    val jitterBuffer = DubJitterBuffer(OUT_SAMPLE_RATE)

    private val running = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private val stopping = AtomicBoolean(false)

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var captureThread: Thread? = null
    private var playbackThread: Thread? = null
    private var duckFocusRequest: AudioFocusRequest? = null
    private var legacyFocusListener: AudioManager.OnAudioFocusChangeListener? = null

    @Volatile
    private var userVolumeGain: Float = SettingsRepository.DEFAULT_DUB_VOLUME_PERCENT / 100f

    @Volatile
    private var silenceDetectionEnabled: Boolean = false

    @Volatile
    private var duckEnabled: Boolean = true

    @Volatile
    private var bluetoothDubOnlyEnabled: Boolean = true

    @Volatile
    private var activeBluetoothRoute: BluetoothAudioRouteInfo = BluetoothAudioRouteInfo()

    @Volatile
    private var originalDuckedActive: Boolean = false

    private var onPcm16kChunkReady: ((ByteArray) -> Unit)? = null
    private var onMetricsUpdated: ((AudioMetrics) -> Unit)? = null
    private var onFatalAudioError: ((String) -> Unit)? = null

    /**
     * Starts real-time capture + 24 kHz low-latency playback.
     * - Immediately drops original audio to 10% when [duckOriginalAudio] is true (restored cleanly on pause/stop).
     * - Starts dubbing volume at [dubVolumePercent] (default 60%).
     * - Automatically routes ONLY dubbing audio to connected Bluetooth headphones/speakers via
     *   `AudioTrack.setPreferredDevice()` + dynamic `AudioDeviceCallback` hot-plug monitoring.
     */
    fun start(
        mediaProjection: MediaProjection?,
        dubVolumePercent: Int,
        silenceDetectionEnabled: Boolean,
        duckOriginalAudio: Boolean,
        bluetoothDubOnlyRouting: Boolean = true,
        onChunk: (ByteArray) -> Unit,
        onMetrics: (AudioMetrics) -> Unit,
        onError: (String) -> Unit
    ): Boolean {
        if (running.get()) {
            stop()
        }
        stopping.set(false)
        paused.set(false)
        this.userVolumeGain = (dubVolumePercent.coerceIn(0, 200)) / 100f
        this.silenceDetectionEnabled = silenceDetectionEnabled
        this.duckEnabled = duckOriginalAudio
        this.bluetoothDubOnlyEnabled = bluetoothDubOnlyRouting
        this.onPcm16kChunkReady = onChunk
        this.onMetricsUpdated = onMetrics
        this.onFatalAudioError = onError
        jitterBuffer.clear()
        jitterBuffer.resetCounters()

        // 1. Check RECORD_AUDIO permission before touching AudioRecord
        val hasRecordPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasRecordPerm) {
            val msg = "RECORD_AUDIO (Mikrofon/Ses Yakalama) izni verilmeden ses yakalanamaz."
            onError(msg)
            return false
        }

        // 2. Build AudioRecord (API 29+ Internal Playback Capture OR API 23-28 Acoustic Fallback)
        var chosenRate = 48000
        var chosenChannels = 2
        var record: AudioRecord? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaProjection != null) {
            val captureConfig = try {
                AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .excludeUid(Process.myUid())
                    .build()
            } catch (t: Throwable) {
                val msg = "AudioPlaybackCaptureConfiguration oluşturulamadı: ${t.message}"
                onError(msg)
                return false
            }

            record = tryCreatePlaybackCaptureRecord(captureConfig, 48000)
            if (record == null) {
                chosenRate = 44100
                record = tryCreatePlaybackCaptureRecord(captureConfig, 44100)
            }
        } else {
            // Android 6.0 - 9.0 (API 23-28) compatibility path: hardware-AEC acoustic capture
            chosenChannels = 1
            chosenRate = 16000
            record = tryCreateLegacyAcousticRecord(16000)
            if (record == null) {
                chosenRate = 44100
                record = tryCreateLegacyAcousticRecord(44100)
            }
            if (record != null) {
                attachAcousticEffects(record.audioSessionId)
            }
        }

        if (record == null) {
            val msg = "Ses yakalayıcı başlatılamadı (desteklenen PCM_16BIT akışı açılamadı)."
            onError(msg)
            return false
        }
        audioRecord = record

        // 3. Detect Bluetooth audio output & create 24 kHz MONO PCM_16BIT low-latency AudioTrack
        //    When ducking original audio to 10% or isolating Bluetooth dubbing, use isolated non-alarm
        //    voice attributes so dubbing stays crystal-clear at 60% while original media drops to 10%!
        val initialBtRoute = bluetoothRouter.detectConnectedBluetoothOutput()
        activeBluetoothRoute = initialBtRoute
        val needsIsolatedBus = duckEnabled || (bluetoothDubOnlyEnabled && initialBtRoute.isConnected)
        val track = tryCreateDubAudioTrack(isolatedBus = needsIsolatedBus)
            ?: tryCreateDubAudioTrack(isolatedBus = false)
        if (track == null) {
            releaseAcousticEffects()
            try {
                record.release()
            } catch (_: Throwable) {
            }
            audioRecord = null
            val msg = "Medya ses çıkışı (AudioTrack 24 kHz) başlatılamadı."
            onError(msg)
            return false
        }
        audioTrack = track

        // 4. Pin dubbing AudioTrack exclusively to Bluetooth device when connected & monitor hot-plug!
        bluetoothRouter.startMonitoring { route ->
            activeBluetoothRoute = route
            bluetoothRouter.applyRoutingToTrack(audioTrack, bluetoothDubOnlyEnabled)
            if (running.get() && !paused.get()) {
                syncOriginalAudioAttenuation()
            }
        }
        bluetoothRouter.applyRoutingToTrack(track, bluetoothDubOnlyEnabled)

        // 5. Immediately drop original background audio to 10% (or BT-only floor) without pausing media
        running.set(true)
        syncOriginalAudioAttenuation()

        val captureRateFinal = chosenRate
        val captureChannelsFinal = chosenChannels
        captureThread = Thread(
            { runCaptureLoop(record, captureRateFinal, captureChannelsFinal) },
            "ScreenDubbing-Capture"
        ).apply { start() }

        playbackThread = Thread(
            { runPlaybackLoop(track) },
            "ScreenDubbing-Playback"
        ).apply { start() }

        return true
    }

    /**
     * Immediately applies or restores the 10% original audio drop + Bluetooth dub-only isolation.
     */
    @Synchronized
    private fun syncOriginalAudioAttenuation() {
        val btDubOnlyNow = bluetoothDubOnlyEnabled && activeBluetoothRoute.isConnected
        if (!running.get() || paused.get()) {
            originalDuckedActive = false
            abandonDuckFocus()
            settingsRepository.restorePendingMusicVolumeIfNeeded(audioManager)
            return
        }
        if (duckEnabled || btDubOnlyNow) {
            requestNonPausingDuckFocus()
            originalDuckedActive = settingsRepository.applyOriginalAudioDuck10Percent(
                audioManager = audioManager,
                bluetoothDubOnlyActive = btDubOnlyNow
            )
        } else {
            originalDuckedActive = false
            abandonDuckFocus()
            settingsRepository.restorePendingMusicVolumeIfNeeded(audioManager)
        }
    }

    @SuppressLint("MissingPermission", "NewApi")
    private fun tryCreatePlaybackCaptureRecord(
        config: AudioPlaybackCaptureConfiguration,
        sampleRate: Int
    ): AudioRecord? {
        return try {
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) return null
            val bufferSize = maxOf(minBuf * 4, sampleRate * 2 * 2 / 5)
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build()
            val ar = AudioRecord.Builder()
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setAudioPlaybackCaptureConfig(config)
                .build()
            if (ar.state == AudioRecord.STATE_INITIALIZED) {
                ar
            } else {
                try {
                    ar.release()
                } catch (_: Throwable) {
                }
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    private fun tryCreateLegacyAcousticRecord(sampleRate: Int): AudioRecord? {
        val sources = intArrayOf(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.MIC
        )
        for (source in sources) {
            try {
                val minBuf = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (minBuf <= 0) continue
                val bufferSize = maxOf(minBuf * 3, sampleRate * 2 / 5)
                val ar = AudioRecord(
                    source,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
                if (ar.state == AudioRecord.STATE_INITIALIZED) {
                    return ar
                } else {
                    try {
                        ar.release()
                    } catch (_: Throwable) {
                    }
                }
            } catch (_: Throwable) {
            }
        }
        return null
    }

    private fun attachAcousticEffects(audioSessionId: Int) {
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply {
                    enabled = true
                }
            }
        } catch (_: Throwable) {
        }
        try {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply {
                    enabled = true
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun releaseAcousticEffects() {
        try {
            echoCanceler?.release()
        } catch (_: Throwable) {
        }
        echoCanceler = null
        try {
            noiseSuppressor?.release()
        } catch (_: Throwable) {
        }
        noiseSuppressor = null
    }

    private fun tryCreateDubAudioTrack(isolatedBus: Boolean): AudioTrack? {
        return try {
            val minBuf = AudioTrack.getMinBufferSize(
                OUT_SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) return null
            // Compact 2x minBuf reduces HAL playback queue latency while staying glitch-free
            val bufferSize = maxOf(minBuf * 2, OUT_SAMPLE_RATE * 2 / 5)
            val attrs = if (isolatedBus) buildIsolatedDubAudioAttributes() else buildDubAudioAttributes()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(OUT_SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val builder = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }
            val at = builder.build()
            if (at.state == AudioTrack.STATE_INITIALIZED) {
                at
            } else {
                try {
                    at.release()
                } catch (_: Throwable) {
                }
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Requests transient MAY_DUCK focus so background media apps (YouTube/Netflix/Spotify)
     * keep playing smoothly without pausing while original volume drops to 10%.
     */
    @Synchronized
    private fun requestNonPausingDuckFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (duckFocusRequest != null) return
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(buildIsolatedDubAudioAttributes())
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(false)
                    .setOnAudioFocusChangeListener { /* Never pause our dubbing overlay on focus hints */ }
                    .build()
                audioManager.requestAudioFocus(req)
                duckFocusRequest = req
            } else {
                if (legacyFocusListener != null) return
                val listener = AudioManager.OnAudioFocusChangeListener { /* keep playing */ }
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    listener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
                legacyFocusListener = listener
            }
        } catch (_: Throwable) {
        }
    }

    @Synchronized
    private fun abandonDuckFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = duckFocusRequest ?: return
            duckFocusRequest = null
            try {
                audioManager.abandonAudioFocusRequest(req)
            } catch (_: Throwable) {
            }
        } else {
            val listener = legacyFocusListener ?: return
            legacyFocusListener = null
            try {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(listener)
            } catch (_: Throwable) {
            }
        }
    }

    fun setPaused(pause: Boolean) {
        val prev = paused.getAndSet(pause)
        if (prev != pause) {
            jitterBuffer.clear()
            syncOriginalAudioAttenuation()
        }
    }

    fun isPaused(): Boolean = paused.get()

    fun setDubVolumePercent(percent: Int) {
        userVolumeGain = (percent.coerceIn(0, 200)) / 100f
    }

    fun setSilenceDetectionEnabled(enabled: Boolean) {
        silenceDetectionEnabled = enabled
    }

    fun setDuckOriginalAudio(enabled: Boolean) {
        if (duckEnabled == enabled) return
        duckEnabled = enabled
        syncOriginalAudioAttenuation()
    }

    fun setBluetoothDubOnlyRouting(enabled: Boolean) {
        if (bluetoothDubOnlyEnabled == enabled) return
        bluetoothDubOnlyEnabled = enabled
        bluetoothRouter.applyRoutingToTrack(audioTrack, bluetoothDubOnlyEnabled)
        syncOriginalAudioAttenuation()
    }

    fun pushDubAudioPcm24k(pcmBytes: ByteArray) {
        if (!running.get()) return
        jitterBuffer.pushPcm16Le(pcmBytes)
    }

    /**
     * Releases any pending sub-80ms audio samples on turn completion so short words are never swallowed.
     */
    fun flushPendingDubAudio() {
        if (!running.get()) return
        jitterBuffer.flushShortUtterance()
    }

    private fun runCaptureLoop(record: AudioRecord, inputSampleRate: Int, inputChannels: Int) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val resampler = Resampler(inputSampleRate, inputChannels = inputChannels, outputSampleRate = 16000)

        // Read ~20 ms frames at a time
        val framesPerRead = inputSampleRate / 50
        val readBuffer = ShortArray(framesPerRead * inputChannels)

        val pcm16kStaging = ShortArray(CHUNK_SAMPLES_16K * 12)
        var stagedSamples = 0

        val preRollRing = ArrayDeque<ByteArray>(PRE_ROLL_CHUNKS)
        var silenceDurationMs = 0L
        var totalContinuousLowRmsMs = 0L
        var isSilencePaused = false

        try {
            record.startRecording()
        } catch (t: Throwable) {
            onFatalAudioError?.invoke("Ses yakalama başlatılamadı: ${t.message}")
            return
        }

        var lastMetricEmitMs = 0L
        var lastNonZeroReadMs = System.currentTimeMillis()
        var pendingLeftSample: Short? = null
        var consecutiveReadErrors = 0

        try {
            while (running.get()) {
                val nowBeforeRead = System.currentTimeMillis()
                val rawRead = record.read(readBuffer, 0, readBuffer.size, AudioRecord.READ_NON_BLOCKING)
                val readCount: Int
                if (rawRead > 0) {
                    consecutiveReadErrors = 0
                    lastNonZeroReadMs = nowBeforeRead
                    var effectiveCount = rawRead
                    if (inputChannels == 2) {
                        val carried = pendingLeftSample
                        if (carried != null) {
                            if (effectiveCount < readBuffer.size) {
                                System.arraycopy(readBuffer, 0, readBuffer, 1, effectiveCount)
                                readBuffer[0] = carried
                                effectiveCount += 1
                                pendingLeftSample = null
                            } else {
                                val newPending = readBuffer[effectiveCount - 1]
                                System.arraycopy(readBuffer, 0, readBuffer, 1, effectiveCount - 1)
                                readBuffer[0] = carried
                                pendingLeftSample = newPending
                            }
                        }
                        if (effectiveCount % 2 != 0) {
                            pendingLeftSample = readBuffer[effectiveCount - 1]
                            effectiveCount -= 1
                        }
                    }
                    if (effectiveCount <= 0) {
                        Thread.sleep(4)
                        continue
                    }
                    readCount = effectiveCount
                } else if (rawRead == 0) {
                    consecutiveReadErrors = 0
                    if (!running.get()) break
                    val idleMs = nowBeforeRead - lastNonZeroReadMs
                    if (idleMs >= 50L) {
                        Thread.sleep(16)
                        readBuffer.fill(0)
                        readCount = readBuffer.size
                    } else {
                        Thread.sleep(5)
                        continue
                    }
                } else {
                    if (!running.get()) break
                    consecutiveReadErrors++
                    if (consecutiveReadErrors >= 50) {
                        onFatalAudioError?.invoke("Ses yakalama akışı kesildi (hata kodu: $rawRead)")
                        break
                    }
                    Thread.sleep(10)
                    continue
                }

                val chunkDurationMs = ((readCount / inputChannels) * 1000L) / inputSampleRate

                // Calculate captured RMS from real PCM samples
                var sumSq = 0.0
                for (i in 0 until readCount) {
                    val s = readBuffer[i].toDouble()
                    sumSq += s * s
                }
                val rms = sqrt(sumSq / readCount)

                if (!paused.get()) {
                    if (rms < RMS_SILENCE_THRESHOLD * 0.5) {
                        totalContinuousLowRmsMs += chunkDurationMs
                    } else {
                        totalContinuousLowRmsMs = 0L
                    }
                }

                // Resample to 16 kHz mono (or pass through directly if already 16 kHz mono)
                val resampled = if (inputSampleRate == 16000 && inputChannels == 1) {
                    readBuffer.copyOf(readCount)
                } else {
                    resampler.process(readBuffer, readCount)
                }
                val spaceLeft = pcm16kStaging.size - stagedSamples
                val copyLen = minOf(resampled.size, spaceLeft)
                if (copyLen > 0) {
                    System.arraycopy(resampled, 0, pcm16kStaging, stagedSamples, copyLen)
                    stagedSamples += copyLen
                }

                // Slice into ultra-low-latency 40 ms (640 samples = 1280 bytes) chunks with Adaptive Speech AGC
                // Up to 12x clean boost ensures that even when original media is dropped to 10%, AI hears every syllable!
                while (stagedSamples >= CHUNK_SAMPLES_16K) {
                    var chunkSumSq = 0.0
                    for (i in 0 until CHUNK_SAMPLES_16K) {
                        val s = pcm16kStaging[i].toDouble()
                        chunkSumSq += s * s
                    }
                    val chunkRms = sqrt(chunkSumSq / CHUNK_SAMPLES_16K)

                    val agcGain = if (chunkRms >= RMS_SILENCE_THRESHOLD && chunkRms < 6200.0) {
                        (6200.0 / chunkRms).coerceIn(1.0, 12.0)
                    } else {
                        1.0
                    }

                    val chunkBytes = ByteArray(CHUNK_BYTES_16K)
                    var bi = 0
                    for (i in 0 until CHUNK_SAMPLES_16K) {
                        val rawSample = pcm16kStaging[i].toInt()
                        val boosted = if (agcGain > 1.02) {
                            softLimitPcm16((rawSample * agcGain).toInt()).toInt()
                        } else {
                            rawSample
                        }
                        chunkBytes[bi++] = (boosted and 0xFF).toByte()
                        chunkBytes[bi++] = ((boosted shr 8) and 0xFF).toByte()
                    }
                    val remaining = stagedSamples - CHUNK_SAMPLES_16K
                    if (remaining > 0) {
                        System.arraycopy(pcm16kStaging, CHUNK_SAMPLES_16K, pcm16kStaging, 0, remaining)
                    }
                    stagedSamples = remaining

                    if (paused.get()) {
                        continue
                    }

                    if (silenceDetectionEnabled) {
                        if (chunkRms < RMS_SILENCE_THRESHOLD) {
                            silenceDurationMs += 40L
                            if (silenceDurationMs >= SILENCE_THRESHOLD_MS) {
                                isSilencePaused = true
                                if (preRollRing.size >= PRE_ROLL_CHUNKS) {
                                    preRollRing.removeFirst()
                                }
                                preRollRing.addLast(chunkBytes)
                            } else {
                                onPcm16kChunkReady?.invoke(chunkBytes)
                            }
                        } else {
                            silenceDurationMs = 0L
                            if (isSilencePaused) {
                                isSilencePaused = false
                                while (preRollRing.isNotEmpty()) {
                                    onPcm16kChunkReady?.invoke(preRollRing.removeFirst())
                                }
                            }
                            onPcm16kChunkReady?.invoke(chunkBytes)
                        }
                    } else {
                        isSilencePaused = false
                        silenceDurationMs = 0L
                        onPcm16kChunkReady?.invoke(chunkBytes)
                    }
                }

                // Update 32-bar rolling capture waveform directly from real PCM samples
                updateRollingWaveform(captureWaveRing, readBuffer, readCount, paused.get(), normDivisor = 5200f)

                val now = System.currentTimeMillis()
                if (now - lastMetricEmitMs >= 65L) {
                    lastMetricEmitMs = now
                    val isPauseActive = paused.get()
                    val effectiveRms = if (isPauseActive) 0.0 else rms
                    val norm = (effectiveRms / 4800.0).coerceIn(0.0, 1.0).toFloat()
                    val db = if (effectiveRms > 1.0) (20.0 * log10(effectiveRms / 32768.0)).toFloat() else -90f
                    val audioDetected = !isPauseActive && effectiveRms >= RMS_SILENCE_THRESHOLD
                    val capWaveList = synchronized(captureWaveRing) { captureWaveRing.toList() }
                    val playWaveList = synchronized(playbackWaveRing) { playbackWaveRing.toList() }
                    val btRoute = activeBluetoothRoute
                    onMetricsUpdated?.invoke(
                        AudioMetrics(
                            inputSampleRate = inputSampleRate,
                            captureRmsDb = db,
                            captureLevelNorm = norm,
                            playbackLevelNorm = currentPlaybackLevelNorm,
                            isAudioDetected = audioDetected,
                            captureWaveform = capWaveList,
                            playbackWaveform = playWaveList,
                            isSilencePaused = isSilencePaused,
                            drmSilenceWarning = totalContinuousLowRmsMs >= DRM_SILENCE_WARN_MS,
                            isOriginalDuckedTo10Percent = originalDuckedActive && !isPauseActive,
                            isBluetoothDubOnlyActive = bluetoothDubOnlyEnabled && btRoute.isConnected,
                            bluetoothDeviceName = btRoute.deviceName
                        )
                    )
                }
            }
        } catch (_: Throwable) {
        } finally {
            abandonDuckFocus()
        }
    }

    private val captureWaveRing = FloatArray(32)
    private val playbackWaveRing = FloatArray(32)

    private fun updateRollingWaveform(
        ring: FloatArray,
        samples: ShortArray,
        sampleCount: Int,
        isZeroed: Boolean,
        normDivisor: Float
    ) {
        val slices = 4
        synchronized(ring) {
            System.arraycopy(ring, slices, ring, 0, ring.size - slices)
            if (isZeroed || sampleCount <= 0) {
                for (s in 0 until slices) {
                    ring[ring.size - slices + s] = 0f
                }
                return
            }
            val sliceLen = (sampleCount / slices).coerceAtLeast(1)
            for (s in 0 until slices) {
                val start = s * sliceLen
                val end = if (s == slices - 1) sampleCount else minOf(sampleCount, start + sliceLen)
                var sumSq = 0.0
                var peak = 0
                for (i in start until end) {
                    val v = abs(samples[i].toInt())
                    if (v > peak) peak = v
                    sumSq += v.toDouble() * v.toDouble()
                }
                val count = (end - start).coerceAtLeast(1)
                val rmsVal = sqrt(sumSq / count).toFloat()
                val blended = (rmsVal * 0.65f) + (peak * 0.35f)
                ring[ring.size - slices + s] = (blended / normDivisor).coerceIn(0f, 1f)
            }
        }
    }

    private fun softLimitPcm16(sample: Int): Short {
        return when {
            sample > 27000 -> {
                val excess = (sample - 27000).toFloat() / 18000f
                (27000 + (5767f * (excess / (1f + excess))).toInt()).coerceAtMost(32767).toShort()
            }
            sample < -27000 -> {
                val excess = (-27000 - sample).toFloat() / 18000f
                (-27000 - (5767f * (excess / (1f + excess))).toInt()).coerceAtLeast(-32768).toShort()
            }
            else -> sample.toShort()
        }
    }

    @Volatile
    private var currentPlaybackLevelNorm: Float = 0f

    private fun runPlaybackLoop(track: AudioTrack) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val frameSamples = (OUT_SAMPLE_RATE * 20) / 1000
        val outShorts = ShortArray(frameSamples)

        var currentRampGain = 1.0f
        val rampStepPerFrame = 20f / RAMP_DURATION_MS.toFloat()

        try {
            track.setVolume(1.0f)
            track.play()
        } catch (_: Throwable) {
            return
        }

        try {
            while (running.get()) {
                val targetRamp = if (paused.get()) 0.0f else 1.0f
                if (currentRampGain < targetRamp) {
                    currentRampGain = (currentRampGain + rampStepPerFrame).coerceAtMost(1.0f)
                } else if (currentRampGain > targetRamp) {
                    currentRampGain = (currentRampGain - rampStepPerFrame).coerceAtLeast(0.0f)
                }

                if (currentRampGain <= 0.001f && paused.get()) {
                    outShorts.fill(0)
                    currentPlaybackLevelNorm = 0f
                    updateRollingWaveform(playbackWaveRing, outShorts, outShorts.size, true, 9000f)
                    val w = track.write(outShorts, 0, outShorts.size)
                    if (w <= 0) {
                        Thread.sleep(20)
                    }
                    continue
                }

                val hasAudio = jitterBuffer.popSamples(outShorts)
                val linearGain = (userVolumeGain * currentRampGain * 1.35f).coerceIn(0f, 2.7f)

                if (hasAudio) {
                    var sumSq = 0.0
                    for (i in outShorts.indices) {
                        val boosted = softLimitPcm16((outShorts[i].toInt() * linearGain).toInt())
                        outShorts[i] = boosted
                        val s = boosted.toDouble()
                        sumSq += s * s
                    }
                    val rms = sqrt(sumSq / outShorts.size)
                    currentPlaybackLevelNorm = (rms / 8000.0).coerceIn(0.0, 1.0).toFloat()
                    updateRollingWaveform(playbackWaveRing, outShorts, outShorts.size, false, 9000f)
                } else {
                    currentPlaybackLevelNorm = 0f
                    updateRollingWaveform(playbackWaveRing, outShorts, outShorts.size, true, 9000f)
                }

                val written = track.write(outShorts, 0, outShorts.size)
                if (written <= 0) {
                    Thread.sleep(20)
                }
            }
        } catch (_: Throwable) {
        } finally {
            currentPlaybackLevelNorm = 0f
            abandonDuckFocus()
        }
    }

    /**
     * Strict shutdown order:
     * 1) Set running flags to false & stop Bluetooth AudioDeviceCallback monitoring
     * 2) Join capture & playback threads
     * 3) Stop & release AudioRecord, AcousticEffects & AudioTrack
     * 4) Abandon any transient duck focus & restore saved original STREAM_MUSIC volume in finally
     */
    fun stop() {
        if (!stopping.compareAndSet(false, true) && !running.get()) {
            bluetoothRouter.stopMonitoring()
            originalDuckedActive = false
            abandonDuckFocus()
            settingsRepository.restoreAllPendingVolumes(audioManager)
            return
        }
        try {
            running.set(false)
            paused.set(false)
            originalDuckedActive = false
            bluetoothRouter.stopMonitoring()
            abandonDuckFocus()

            val curThread = Thread.currentThread()
            val cap = captureThread
            captureThread = null
            if (cap != null && cap !== curThread) {
                try {
                    cap.interrupt()
                    cap.join(1200L)
                } catch (_: Throwable) {
                }
            }

            val play = playbackThread
            playbackThread = null
            if (play != null && play !== curThread) {
                try {
                    play.interrupt()
                    play.join(1200L)
                } catch (_: Throwable) {
                }
            }

            releaseAcousticEffects()

            val ar = audioRecord
            audioRecord = null
            if (ar != null) {
                try {
                    if (ar.state == AudioRecord.STATE_INITIALIZED) {
                        ar.stop()
                    }
                } catch (_: Throwable) {
                }
                try {
                    ar.release()
                } catch (_: Throwable) {
                }
            }

            val at = audioTrack
            audioTrack = null
            if (at != null) {
                try {
                    at.setPreferredDevice(null)
                } catch (_: Throwable) {
                }
                try {
                    if (at.state == AudioTrack.STATE_INITIALIZED) {
                        at.stop()
                    }
                } catch (_: Throwable) {
                }
                try {
                    at.release()
                } catch (_: Throwable) {
                }
            }

            synchronized(captureWaveRing) { captureWaveRing.fill(0f) }
            synchronized(playbackWaveRing) { playbackWaveRing.fill(0f) }
            currentPlaybackLevelNorm = 0f
            jitterBuffer.clear()
            onMetricsUpdated?.invoke(AudioMetrics())
        } finally {
            abandonDuckFocus()
            settingsRepository.restoreAllPendingVolumes(audioManager)
            stopping.set(false)
        }
    }
}
