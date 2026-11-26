package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import com.example.LiveDubApplication
import com.example.audio.AudioEngine
import com.example.audio.AudioMetrics
import com.example.data.LanguageItem
import com.example.live.LiveSessionListener
import com.example.live.LiveSessionState
import com.example.util.ErrorEntry
import com.example.util.HapticFeedbackHelper
import com.example.util.LanguageDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class DubbingService : Service() {
    companion object {
        const val ACTION_START = "com.example.service.action.START"
        const val ACTION_PAUSE_TOGGLE = "com.example.service.action.PAUSE_TOGGLE"
        const val ACTION_STOP = "com.example.service.action.STOP"
        const val ACTION_UPDATE_RUNTIME_PREFS = "com.example.service.action.UPDATE_RUNTIME_PREFS"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var timerJob: Job? = null
    private var stateCollectorJob: Job? = null

    private var mediaProjection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var audioEngine: AudioEngine? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var osdOverlayManager: DubbingOsdOverlayManager? = null
    private var osdCollectorJob: Job? = null

    private val isStopping = AtomicBoolean(false)
    private val turnHapticTriggered = AtomicBoolean(false)
    private var sessionStartedAtMs: Long = 0L

    private val inputPhraseBuffer = StringBuilder()
    private val outputPhraseBuffer = StringBuilder()

    override fun onCreate() {
        super.onCreate()
        DubbingNotificationHelper.ensureChannel(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_STOP
        when (action) {
            ACTION_START -> handleStart()
            ACTION_PAUSE_TOGGLE -> handlePauseToggle()
            ACTION_UPDATE_RUNTIME_PREFS -> handleUpdateRuntimePrefs()
            ACTION_STOP -> stopDubbingOrdered()
        }
        return START_NOT_STICKY
    }

    private fun releasePreviousSessionResources() {
        timerJob?.cancel()
        timerJob = null
        stateCollectorJob?.cancel()
        stateCollectorJob = null
        osdCollectorJob?.cancel()
        osdCollectorJob = null
        try {
            osdOverlayManager?.dismiss()
        } catch (_: Throwable) {
        }
        try {
            audioEngine?.stop()
        } catch (_: Throwable) {
        }
        audioEngine = null
        val prevProj = mediaProjection
        val prevCb = projectionCallback
        mediaProjection = null
        projectionCallback = null
        if (prevProj != null) {
            if (prevCb != null) {
                try {
                    prevProj.unregisterCallback(prevCb)
                } catch (_: Throwable) {
                }
            }
            try {
                prevProj.stop()
            } catch (_: Throwable) {
            }
        }
    }

    private fun handleStart() {
        val app = application as LiveDubApplication
        val controller = app.container.dubbingController
        val settingsRepo = app.container.settingsRepository
        val liveClient = app.container.liveSessionClient

        isStopping.set(false)
        turnHapticTriggered.set(false)
        HapticFeedbackHelper.resetTurnCooldown()
        releasePreviousSessionResources()

        // 1. Enter Foreground Service IMMEDIATELY (API 29+ MEDIA_PROJECTION type, API 23-28 standard foreground)
        val initialNotif = DubbingNotificationHelper.buildNotification(
            context = this,
            statusText = "Başlatılıyor...",
            detectedLanguageText = "Otomatik algılanıyor",
            isPaused = false
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    DubbingNotificationHelper.NOTIFICATION_ID,
                    initialNotif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(DubbingNotificationHelper.NOTIFICATION_ID, initialNotif)
            }
        } catch (t: Throwable) {
            controller.updateState {
                it.copy(
                    isServiceRunning = false,
                    bannerMessage = "Ön plan servisi başlatılamadı: ${t.message}"
                )
            }
            stopSelf()
            return
        }

        val settings = settingsRepo.settings.value
        if (settings.apiKey.isBlank()) {
            controller.updateState {
                it.copy(bannerMessage = "Lütfen önce geçerli bir Gemini API anahtarı girin.")
            }
            stopDubbingOrdered()
            return
        }

        // 2. Obtain MediaProjection on Android 10+ (API 29+); on Android 6-9 use direct acoustic capture
        var projection: MediaProjection? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val consent = controller.consumeOneTimeProjectionConsent()
            if (consent == null) {
                stopDubbingOrdered()
                return
            }
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = try {
                mpManager.getMediaProjection(consent.first, consent.second)
            } catch (t: Throwable) {
                controller.updateState {
                    it.copy(bannerMessage = "MediaProjection alınamadı: ${t.message}")
                }
                stopDubbingOrdered()
                return
            }
            if (projection == null) {
                controller.updateState {
                    it.copy(bannerMessage = "Ekran/ses paylaşımı iptal edildi veya geçersiz.")
                }
                stopDubbingOrdered()
                return
            }
            mediaProjection = projection
            val cb = object : MediaProjection.Callback() {
                override fun onStop() {
                    if (!isStopping.get()) {
                        stopDubbingOrdered()
                    }
                }
            }
            projectionCallback = cb
            projection.registerCallback(cb, Handler(Looper.getMainLooper()))
        } else {
            controller.clearProjectionConsent()
        }

        acquireWakeLock()

        // 3. Reset live buffers and UI state
        synchronized(this) {
            inputPhraseBuffer.setLength(0)
            outputPhraseBuffer.setLength(0)
        }
        sessionStartedAtMs = System.currentTimeMillis()
        val initialSourceDisplay = if (settings.sourceLanguageCode == LanguageItem.AUTO_CODE) {
            "Otomatik algılanıyor"
        } else {
            val lang = LanguageItem.byCode(settings.sourceLanguageCode, allowAuto = false)
            "${lang.code.uppercase()} (${lang.nativeName})"
        }
        controller.updateState {
            it.copy(
                isServiceRunning = true,
                isPaused = false,
                liveState = LiveSessionState.Connecting,
                audioMetrics = AudioMetrics(),
                detectedSourceLanguage = initialSourceDisplay,
                currentInputLiveText = "",
                currentOutputLiveText = "",
                firstAudioLatencyMs = null,
                activeDurationSec = 0L,
                bannerMessage = null
            )
        }

        // 4. PARALLEL ZERO-DELAY STARTUP: Open Gemini Live WebSocket FIRST so TLS + setupComplete
        //    runs concurrently while AudioRecord/AudioTrack hardware initializes!
        liveClient.start(
            apiKey = settings.apiKey,
            modelName = settings.selectedModel,
            echoTargetLanguage = settings.echoTargetLanguage,
            autoReconnect = settings.autoReconnect,
            sourceLanguageCode = settings.sourceLanguageCode,
            targetLanguageCode = settings.targetLanguageCode,
            listener = object : LiveSessionListener {
                override fun onAudioOutputPcm24k(pcmBytes: ByteArray, latencyMs: Long?) {
                    audioEngine?.pushDubAudioPcm24k(pcmBytes)
                    if (turnHapticTriggered.compareAndSet(false, true)) {
                        HapticFeedbackHelper.vibrateAiVoiceTurnStart(
                            context = applicationContext,
                            enabled = settingsRepo.settings.value.hapticFeedbackEnabled
                        )
                    }
                    if (latencyMs != null) {
                        controller.updateState { it.copy(firstAudioLatencyMs = latencyMs) }
                    }
                }

                override fun onInputTranscript(text: String, languageCode: String?) {
                    var currentIn = ""
                    synchronized(this@DubbingService) {
                        if (text.isNotEmpty()) {
                            appendTranscriptChunk(inputPhraseBuffer, text)
                            currentIn = inputPhraseBuffer.toString().takeLast(240)
                        }
                    }
                    val resolvedLang = LanguageDetector.resolveSourceLanguageLabel(
                        explicitLanguageCode = languageCode,
                        transcriptSample = currentIn.ifBlank { text },
                        previousLabel = controller.uiState.value.detectedSourceLanguage
                    )
                    controller.updateState { state ->
                        state.copy(
                            detectedSourceLanguage = resolvedLang,
                            currentInputLiveText = if (currentIn.isNotEmpty()) currentIn else state.currentInputLiveText
                        )
                    }
                    refreshNotification()
                }

                override fun onOutputTranscript(text: String) {
                    var currentOut = ""
                    var currentIn = ""
                    synchronized(this@DubbingService) {
                        appendTranscriptChunk(outputPhraseBuffer, text)
                        currentOut = outputPhraseBuffer.toString().takeLast(240)
                        currentIn = inputPhraseBuffer.toString()
                    }
                    controller.updateState { state ->
                        val updatedLang = if (state.detectedSourceLanguage == "Otomatik algılanıyor" && currentIn.isNotBlank()) {
                            LanguageDetector.resolveSourceLanguageLabel(null, currentIn, state.detectedSourceLanguage)
                        } else {
                            state.detectedSourceLanguage
                        }
                        state.copy(
                            detectedSourceLanguage = updatedLang,
                            currentOutputLiveText = currentOut
                        )
                    }
                }

                override fun onTurnComplete() {
                    turnHapticTriggered.set(false)
                    audioEngine?.flushPendingDubAudio()
                    synchronized(this@DubbingService) {
                        val inText = inputPhraseBuffer.toString().trim()
                        if (inText.isNotEmpty()) {
                            val detected = LanguageDetector.resolveSourceLanguageLabel(
                                explicitLanguageCode = null,
                                transcriptSample = inText,
                                previousLabel = controller.uiState.value.detectedSourceLanguage
                            )
                            if (detected != controller.uiState.value.detectedSourceLanguage) {
                                controller.updateState { it.copy(detectedSourceLanguage = detected) }
                            }
                        }
                        inputPhraseBuffer.setLength(0)
                        outputPhraseBuffer.setLength(0)
                    }
                }

                override fun onFatalError(entry: ErrorEntry) {
                    controller.updateState {
                        it.copy(
                            bannerMessage = "${entry.title} — ${entry.solution}"
                        )
                    }
                    refreshNotification()
                }

                override fun onModelFallbackToDefault(fallbackModel: String) {
                    settingsRepo.updateSelectedModel(fallbackModel)
                    controller.updateState {
                        it.copy(
                            bannerMessage = "Seçilen model desteklenmediği için otomatik olarak $fallbackModel modeline geçildi."
                        )
                    }
                }
            }
        )

        // 5. Start AudioEngine concurrently (immediately drops original audio to 10%, starts dubbing at 60%,
        //    and routes ONLY dubbing audio to Bluetooth headphones/speakers if connected!)
        val engine = AudioEngine(
            context = applicationContext,
            settingsRepository = settingsRepo
        )
        audioEngine = engine

        val startedOk = engine.start(
            mediaProjection = projection,
            dubVolumePercent = settings.dubVolumePercent,
            silenceDetectionEnabled = settings.silenceDetectionEnabled,
            duckOriginalAudio = settings.duckOriginalAudio,
            bluetoothDubOnlyRouting = settings.bluetoothDubOnlyRouting,
            onChunk = { pcm16kBytes ->
                liveClient.sendPcm16kChunk(pcm16kBytes)
            },
            onMetrics = { metrics ->
                controller.updateState { state ->
                    val banner = metrics.duckAutoRevertedMessage ?: state.bannerMessage
                    state.copy(
                        audioMetrics = metrics,
                        bannerMessage = banner
                    )
                }
            },
            onError = { errMsg ->
                serviceScope.launch {
                    controller.updateState { it.copy(bannerMessage = errMsg) }
                    stopDubbingOrdered()
                }
            }
        )

        if (!startedOk) {
            stopDubbingOrdered()
            return
        }

        // Tactile double-pulse vibration feedback confirming AI Screen Dubbing has started
        HapticFeedbackHelper.vibrateDubbingStart(
            context = applicationContext,
            enabled = settings.hapticFeedbackEnabled
        )

        // 6. Collect LiveSessionState, drive Premium Floating Liquid Glass OSD & 1-second duration timer
        if (osdOverlayManager == null) {
            osdOverlayManager = DubbingOsdOverlayManager(
                context = applicationContext,
                onTogglePause = { handlePauseToggle() },
                onStopDubbing = { stopDubbingOrdered() }
            )
        }
        osdCollectorJob?.cancel()
        osdCollectorJob = serviceScope.launch {
            controller.uiState.collectLatest { uiSt ->
                if (!isStopping.get() && uiSt.isServiceRunning) {
                    osdOverlayManager?.showOrUpdate(uiSt, settingsRepo.settings.value)
                }
            }
        }

        stateCollectorJob?.cancel()
        stateCollectorJob = serviceScope.launch {
            liveClient.state.collectLatest { ls ->
                controller.updateState { it.copy(liveState = ls) }
                refreshNotification()
            }
        }

        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive && !isStopping.get()) {
                delay(1000L)
                val sec = ((System.currentTimeMillis() - sessionStartedAtMs) / 1000L).coerceAtLeast(0L)
                controller.updateState {
                    it.copy(activeDurationSec = sec)
                }
            }
        }
    }

    private fun handlePauseToggle() {
        val app = application as LiveDubApplication
        if (isStopping.get() || !app.container.dubbingController.uiState.value.isServiceRunning) {
            stopSelf()
            return
        }
        val engine = audioEngine ?: return
        val newPause = !engine.isPaused()
        engine.setPaused(newPause)
        val hapticsEnabled = app.container.settingsRepository.settings.value.hapticFeedbackEnabled
        if (!newPause) {
            HapticFeedbackHelper.vibrateDubbingResume(
                context = applicationContext,
                enabled = hapticsEnabled
            )
        } else {
            HapticFeedbackHelper.vibrateSoftTap(
                context = applicationContext,
                enabled = hapticsEnabled
            )
        }
        app.container.dubbingController.updateState {
            it.copy(isPaused = newPause)
        }
        refreshNotification()
    }

    private fun handleUpdateRuntimePrefs() {
        val app = application as LiveDubApplication
        if (isStopping.get() || !app.container.dubbingController.uiState.value.isServiceRunning) {
            stopSelf()
            return
        }
        val s = app.container.settingsRepository.settings.value
        audioEngine?.setDubVolumePercent(s.dubVolumePercent)
        audioEngine?.setSilenceDetectionEnabled(s.silenceDetectionEnabled)
        audioEngine?.setDuckOriginalAudio(s.duckOriginalAudio)
        audioEngine?.setBluetoothDubOnlyRouting(s.bluetoothDubOnlyRouting)
        app.container.liveSessionClient.updateRuntimeFlags(
            apiKey = s.apiKey,
            autoReconnect = s.autoReconnect
        )
        app.container.liveSessionClient.switchModelLive(
            newModel = s.selectedModel,
            echoTargetLanguage = s.echoTargetLanguage,
            newApiKey = s.apiKey,
            sourceLanguageCode = s.sourceLanguageCode,
            targetLanguageCode = s.targetLanguageCode
        )
        osdOverlayManager?.showOrUpdate(app.container.dubbingController.uiState.value, s)
        refreshNotification()
    }

    private fun appendTranscriptChunk(buffer: StringBuilder, incoming: String) {
        val trimmed = incoming.trim()
        if (trimmed.isEmpty()) return
        if (buffer.isEmpty()) {
            buffer.append(trimmed)
            return
        }
        val current = buffer.toString()
        if (trimmed.length > current.length && trimmed.startsWith(current, ignoreCase = true)) {
            buffer.setLength(0)
            buffer.append(trimmed)
        } else {
            if (!current.endsWith(" ") && !trimmed.startsWith(" ")) {
                buffer.append(' ')
            }
            buffer.append(trimmed)
        }
    }

    private fun refreshNotification() {
        if (isStopping.get()) return
        val app = application as LiveDubApplication
        val st = app.container.dubbingController.uiState.value
        val s = app.container.settingsRepository.settings.value
        val targetLang = LanguageItem.byCode(s.targetLanguageCode, allowAuto = false)
        val statusLabel = if (st.isPaused) "Duraklatıldı" else st.liveState.labelTr()
        DubbingNotificationHelper.updateNotification(
            context = this,
            statusText = statusLabel,
            detectedLanguageText = st.detectedSourceLanguage,
            isPaused = st.isPaused,
            targetLanguageLabel = targetLang.nativeName
        )
    }

    private fun acquireWakeLock() {
        try {
            releaseWakeLock()
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ScreenDubbing::DubbingWakeLock")
            wl.setReferenceCounted(false)
            wl.acquire(4 * 60 * 60 * 1000L)
            wakeLock = wl
        } catch (_: Throwable) {
        }
    }

    private fun releaseWakeLock() {
        try {
            val wl = wakeLock
            wakeLock = null
            if (wl != null && wl.isHeld) {
                wl.release()
            }
        } catch (_: Throwable) {
        }
    }

    private fun stopDubbingOrdered() {
        if (!isStopping.compareAndSet(false, true)) return
        val app = application as LiveDubApplication
        val controller = app.container.dubbingController
        val settingsRepo = app.container.settingsRepository
        val liveClient = app.container.liveSessionClient
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        try {
            HapticFeedbackHelper.vibrateDubbingStop(
                context = applicationContext,
                enabled = settingsRepo.settings.value.hapticFeedbackEnabled
            )
            timerJob?.cancel()
            timerJob = null
            stateCollectorJob?.cancel()
            stateCollectorJob = null
            osdCollectorJob?.cancel()
            osdCollectorJob = null
            try {
                osdOverlayManager?.dismiss()
            } catch (_: Throwable) {
            }
            osdOverlayManager = null

            try {
                audioEngine?.stop()
            } catch (_: Throwable) {
            }
            audioEngine = null

            try {
                liveClient.stop()
            } catch (_: Throwable) {
            }

            val proj = mediaProjection
            val cb = projectionCallback
            mediaProjection = null
            projectionCallback = null
            if (proj != null) {
                if (cb != null) {
                    try {
                        proj.unregisterCallback(cb)
                    } catch (_: Throwable) {
                    }
                }
                try {
                    proj.stop()
                } catch (_: Throwable) {
                }
            }

            controller.clearProjectionConsent()

            controller.updateState {
                it.copy(
                    isServiceRunning = false,
                    isPaused = false,
                    liveState = LiveSessionState.Stopped,
                    audioMetrics = AudioMetrics(),
                    firstAudioLatencyMs = null
                )
            }
        } finally {
            settingsRepo.restoreAllPendingVolumes(audioManager)
            releaseWakeLock()
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
            } catch (_: Throwable) {
            }
            stopSelf()
        }
    }

    override fun onDestroy() {
        stopDubbingOrdered()
        serviceScope.cancel()
        super.onDestroy()
    }
}
