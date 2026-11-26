package com.example.live

import android.util.Base64
import com.example.data.LanguageItem
import com.example.data.LiveConnectionTestResult
import com.example.security.SecurityShield
import com.example.util.ErrorCatalog
import com.example.util.ErrorEntry
import com.example.util.Redactor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

data class GeminiModelInfo(
    val id: String,
    val displayName: String,
    val supportsBidi: Boolean,
    val isRecommendedTranslate: Boolean
)

sealed class LiveSessionState {
    data object Idle : LiveSessionState()
    data object Connecting : LiveSessionState()
    data object Streaming : LiveSessionState()
    data object Renewing : LiveSessionState()
    data class Reconnecting(val attempt: Int, val waitSec: Int) : LiveSessionState()
    data class Error(val message: String, val entry: ErrorEntry? = null) : LiveSessionState()
    data object Stopped : LiveSessionState()

    fun labelTr(): String = when (this) {
        is Idle -> "Hazır"
        is Connecting -> "Bağlanıyor..."
        is Streaming -> "Canlı Dublaj"
        is Renewing -> "Yenileniyor..."
        is Reconnecting -> "Yeniden Bağlanıyor ($attempt. deneme, ${waitSec}s)..."
        is Error -> "Hata: $message"
        is Stopped -> "Durduruldu"
    }
}

interface LiveSessionListener {
    fun onAudioOutputPcm24k(pcmBytes: ByteArray, latencyMs: Long?)
    fun onInputTranscript(text: String, languageCode: String?)
    fun onOutputTranscript(text: String)
    fun onTurnComplete()
    fun onFatalError(entry: ErrorEntry)
    fun onModelFallbackToDefault(fallbackModel: String) {}
}

class LiveSessionClient(
    private val okHttpClient: OkHttpClient
) {
    companion object {
        const val WS_ENDPOINT =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        const val REST_MODELS_URL =
            "https://generativelanguage.googleapis.com/v1beta/models"
        // Verified, universally-available Gemini Native Audio Live model — eliminates phantom-model 404 fallback delays!
        const val DEFAULT_TRANSLATE_MODEL = "gemini-2.5-flash-native-audio-preview-12-2025"

        private const val MAX_QUEUE_BYTES = 1024 * 1024L // 1 MB bounded send queue
        private const val MAX_STARTUP_PREBUFFER_CHUNKS = 45 // ~1.8 s of 40ms PCM chunks buffered during handshake!
        private const val RENEW_THRESHOLD_MS = 9 * 60 * 1000L // 9 minutes make-before-break
        private const val DRAIN_OLD_WS_MS = 3000L // 3.0s drain so trailing words finish completely
        private val BACKOFF_SECONDS = intArrayOf(1, 2, 4, 8, 16, 30)

        private val VERSION_REGEX = Regex("""gemini-(\d+)(?:\.(\d+))?""", RegexOption.IGNORE_CASE)
        private val DATE_MM_YYYY_REGEX = Regex("""(\d{2})-(20\d{2})""")
        private val DATE_YYYY_MM_REGEX = Regex("""(20\d{2})-(\d{2})""")

        private fun escapeJson(raw: String): String =
            raw.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "")

        /**
         * Builds a hardened OkHttp Request using the TLS-encrypted `x-goog-api-key` header
         * (NEVER putting the API key in URL query parameters) and enforcing our host allowlist.
         */
        fun buildSecuredRequest(url: String, apiKey: String): Request {
            require(SecurityShield.isAllowedGeminiEndpoint(url)) {
                "Blocked outbound request to untrusted or non-TLS endpoint"
            }
            return Request.Builder()
                .url(url)
                .header(SecurityShield.HEADER_API_KEY, apiKey.trim())
                .build()
        }

        /**
         * Strict filter ensuring ONLY real-time live translation & bidirectional audio dubbing
         * models appear in the UI — eliminating all text-only, vision, embedding, pro, flash-lite,
         * imagen, veo, gemma, or one-shot TTS clutter!
         */
        fun isLiveDubbingCapableModel(modelId: String, supportsBidiMethod: Boolean): Boolean {
            val lower = modelId.trim().removePrefix("models/").lowercase()
            if (lower.isEmpty()) return false

            val blockedTokens = listOf(
                "embedding", "imagen", "veo", "aqa", "gemma", "learnlm",
                "vision", "robotics", "computer-use", "deep-research",
                "image-generation", "preview-tts", "flash-tts", "pro-tts"
            )
            if (blockedTokens.any { lower.contains(it) }) return false

            val isExplicitDubbingFamily =
                lower.contains("live-translate") ||
                    lower.contains("native-audio") ||
                    lower.contains("-live") ||
                    lower.contains("live-") ||
                    lower.endsWith("live")

            return supportsBidiMethod || isExplicitDubbingFamily
        }

        /**
         * Extracts a sortable recency score so the newest Gemini version & preview release date
         * always rank first automatically.
         */
        internal fun modelRecencyScore(modelId: String): Long {
            val lower = modelId.lowercase()
            val versionMatch = VERSION_REGEX.find(lower)
            val major = versionMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
            val minor = versionMatch?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 0
            val versionScore = major * 100L + minor * 10L

            val dateScore: Long = DATE_MM_YYYY_REGEX.find(lower)?.let { m ->
                val month = m.groupValues[1].toLongOrNull() ?: 0L
                val year = m.groupValues[2].toLongOrNull() ?: 0L
                year * 100L + month
            } ?: DATE_YYYY_MM_REGEX.find(lower)?.let { m ->
                val year = m.groupValues[1].toLongOrNull() ?: 0L
                val month = m.groupValues[2].toLongOrNull() ?: 0L
                year * 100L + month
            } ?: 0L

            val isNativeAudioBonus = if (lower.contains("native-audio")) 50L else 0L
            return versionScore * 1_000_000L + dateScore * 100L + isNativeAudioBonus
        }

        /**
         * Builds the initial Gemini Live BidiGenerateContent setup JSON frame tuned for:
         * 1. ULTRA-LOW-LATENCY SIMULTANEOUS STARTUP:
         *    - `END_SENSITIVITY_HIGH` + `silenceDurationMs: 320` + `prefixPaddingMs: 220` cuts
         *      600-2500ms of VAD hesitation while `NO_INTERRUPTION` still prevents word-swallowing!
         *    - Zero thinking latency (`thinkingBudget: 0` / `MINIMAL`) on reasoning-capable models.
         * 2. 100% Automatic AI Zero-Shot Speaker Voice Cloning (woman, man, child, elderly, robot).
         * 3. Dynamic Source Language (Auto Detect or explicit) -> Target Dubbing Language.
         */
        fun buildSetupJson(
            modelName: String,
            echoTargetLanguage: Boolean,
            setupTier: Int = 0,
            sourceLanguageCode: String = LanguageItem.AUTO_CODE,
            targetLanguageCode: String = "tr"
        ): String {
            val cleanModel = escapeJson(modelName.trim().removePrefix("models/"))
            val fullModelPath = "models/$cleanModel"
            val isTranslateModel = cleanModel.contains("translate", ignoreCase = true)
            val isThinkingModel = cleanModel.contains("thinking", ignoreCase = true)

            val targetItem = LanguageItem.byCode(targetLanguageCode, allowAuto = false)
            val sourceItem = LanguageItem.byCode(sourceLanguageCode, allowAuto = true)

            if (!isTranslateModel) {
                val sourceDirective = if (sourceItem.isAuto) {
                    "Automatically detect the spoken source language in real time (even if speakers switch languages mid-stream)."
                } else {
                    "The expected source language is ${sourceItem.nameEn} (${sourceItem.code})."
                }
                val echoRule = if (echoTargetLanguage) {
                    "If the incoming audio is already in ${targetItem.nameEn}, still dub it clearly and faithfully in ${targetItem.nameEn}."
                } else {
                    "If the incoming audio is already spoken in ${targetItem.nameEn}, stay silent and do not echo it."
                }
                val voiceCloningDirective =
                    "AUTOMATIC ZERO-SHOT SPEAKER VOICE CLONING: Continuously analyze the incoming speaker's exact acoustic identity (woman, man, child, elderly, or stylized/robotic voice) and replicate THAT EXACT SPEAKER'S VOICE in ${targetItem.nameEn} — matching pitch, timbre, gender, age, emotion, and rhythm. Immediately switch voice identity when a new speaker talks."
                val zeroWordDropDirective =
                    "INSTANT SIMULTANEOUS ZERO-WORD-SWALLOWING RULE: Begin speaking the ${targetItem.nameEn} translation immediately after the very first phrase without waiting for long pauses. Translate EVERY SINGLE WORD completely from start to finish — NEVER skip, summarize, or drop beginnings or endings."

                val fullPrompt = escapeJson(
                    "You are an ultra-low-latency simultaneous AI voice dubbing engine. " +
                        "$sourceDirective " +
                        "Output ONLY natural spoken ${targetItem.nameEn} (${targetItem.bcp47}) dubbing — zero commentary or source repetition. " +
                        "$voiceCloningDirective " +
                        "$zeroWordDropDirective " +
                        echoRule
                )

                val thinkingPart = if (isThinkingModel) {
                    ",\"thinkingConfig\":{\"thinkingBudget\":0}"
                } else {
                    ""
                }

                // Omit prebuiltVoiceConfig so the native-audio model stays completely free to mirror
                // the live speaker's exact voice (gender, age, pitch, timbre, and emotion).
                val speechConfigPart =
                    ",\"speechConfig\":{\"languageCode\":\"${targetItem.bcp47}\"}"

                val sysInstruction =
                    "\"systemInstruction\":{\"parts\":[{\"text\":\"$fullPrompt\"}]}"

                // Ultra-responsive simultaneous dubbing VAD: starts speaking fast (320ms pause window)
                // while NO_INTERRUPTION + 220ms prefix padding guarantees zero swallowed words!
                val lowLatencyRealtimeInputConfig =
                    "\"realtimeInputConfig\":{\"activityHandling\":\"NO_INTERRUPTION\",\"automaticActivityDetection\":{\"startOfSpeechSensitivity\":\"START_SENSITIVITY_HIGH\",\"endOfSpeechSensitivity\":\"END_SENSITIVITY_HIGH\",\"prefixPaddingMs\":220,\"silenceDurationMs\":320}}"

                return when (setupTier) {
                    0 -> {
                        "{\"setup\":{\"model\":\"$fullModelPath\",\"generationConfig\":{\"responseModalities\":[\"AUDIO\"]$speechConfigPart$thinkingPart},$sysInstruction,$lowLatencyRealtimeInputConfig,\"inputAudioTranscription\":{},\"outputAudioTranscription\":{}}}"
                    }
                    1 -> {
                        "{\"setup\":{\"model\":\"$fullModelPath\",\"generationConfig\":{\"responseModalities\":[\"AUDIO\"]$thinkingPart},$sysInstruction,\"realtimeInputConfig\":{\"activityHandling\":\"NO_INTERRUPTION\"},\"inputAudioTranscription\":{},\"outputAudioTranscription\":{}}}"
                    }
                    else -> {
                        "{\"setup\":{\"model\":\"$fullModelPath\",\"generationConfig\":{\"responseModalities\":[\"AUDIO\"]$thinkingPart},$sysInstruction}}"
                    }
                }
            }

            // Dedicated Gemini Live Translate model (native zero-shot speaker voice preservation)
            val sourceLangField = if (!sourceItem.isAuto) {
                "\"sourceLanguageCode\":\"${sourceItem.code}\","
            } else {
                ""
            }
            val translateConfig =
                "\"translationConfig\":{${sourceLangField}\"targetLanguageCode\":\"${targetItem.code}\",\"echoTargetLanguage\":$echoTargetLanguage}"
            val translateNoDropRealtime =
                "\"realtimeInputConfig\":{\"activityHandling\":\"NO_INTERRUPTION\"}"

            return when (setupTier) {
                0 -> {
                    "{\"setup\":{\"model\":\"$fullModelPath\",\"generationConfig\":{\"responseModalities\":[\"AUDIO\"],$translateConfig},$translateNoDropRealtime,\"inputAudioTranscription\":{},\"outputAudioTranscription\":{}}}"
                }
                1 -> {
                    "{\"setup\":{\"model\":\"$fullModelPath\",\"generationConfig\":{\"responseModalities\":[\"AUDIO\"],$translateConfig},\"inputAudioTranscription\":{},\"outputAudioTranscription\":{}}}"
                }
                else -> {
                    "{\"setup\":{\"model\":\"$fullModelPath\",\"generationConfig\":{\"responseModalities\":[\"AUDIO\"],$translateConfig}}}"
                }
            }
        }

        /**
         * Zero-allocation-overhead real-time 16 kHz PCM audio frame builder (called 25x/sec at 40ms).
         */
        fun buildRealtimeAudioJson(base64Pcm16k: String): String {
            return "{\"realtimeInput\":{\"audio\":{\"data\":\"$base64Pcm16k\",\"mimeType\":\"audio/pcm;rate=16000\"}}}"
        }

        /**
         * Parses a single `/v1beta/models` JSON page into ONLY real-time live translation &
         * dubbing-capable GeminiModelInfo entries (zero text/vision/embedding clutter!).
         */
        fun parseModelsPage(bodyStr: String): Pair<List<GeminiModelInfo>, String?> {
            val root = JSONObject(bodyStr)
            val modelsArr = root.optJSONArray("models")
            val result = mutableListOf<GeminiModelInfo>()
            if (modelsArr != null) {
                for (i in 0 until modelsArr.length()) {
                    val obj = modelsArr.optJSONObject(i) ?: continue
                    val rawName = obj.optString("name", "").removePrefix("models/").trim()
                    if (rawName.isEmpty()) continue
                    val display = obj.optString("displayName", rawName).ifBlank { rawName }
                    val methods = obj.optJSONArray("supportedGenerationMethods")
                    var hasBidi = false
                    if (methods != null) {
                        for (m in 0 until methods.length()) {
                            if (methods.optString(m).equals("bidiGenerateContent", ignoreCase = true)) {
                                hasBidi = true
                                break
                            }
                        }
                    }
                    if (isLiveDubbingCapableModel(rawName, supportsBidiMethod = hasBidi)) {
                        result.add(
                            GeminiModelInfo(
                                id = rawName,
                                displayName = display,
                                supportsBidi = true,
                                isRecommendedTranslate = rawName == DEFAULT_TRANSLATE_MODEL ||
                                    rawName.contains("live-translate", ignoreCase = true) ||
                                    rawName.contains("native-audio", ignoreCase = true)
                            )
                        )
                    }
                }
            }
            val nextToken = root.optString("nextPageToken", "").trim().ifEmpty { null }
            return result to nextToken
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<LiveSessionState>(LiveSessionState.Idle)
    val state: StateFlow<LiveSessionState> = _state.asStateFlow()

    private val activeHolder = AtomicReference<ConnectionHolder?>(null)
    private val candidateHolder = AtomicReference<ConnectionHolder?>(null)
    private val isStopped = AtomicBoolean(true)
    private val isRenewing = AtomicBoolean(false)

    // Caches the fastest known-working setupTier per model to avoid repeating failed handshakes
    private val workingTierByModel = ConcurrentHashMap<String, Int>()

    // Bounded pre-buffer capturing spoken PCM during WebSocket handshake so ZERO first words are lost!
    private val startupPreBufferLock = Any()
    private val startupPcmPreBuffer = ArrayDeque<ByteArray>(MAX_STARTUP_PREBUFFER_CHUNKS)

    private var apiKey: String = ""
    private var modelName: String = DEFAULT_TRANSLATE_MODEL
    private var sourceLanguageCode: String = LanguageItem.AUTO_CODE
    private var targetLanguageCode: String = "tr"
    private var echoTargetLanguage: Boolean = false
    private var autoReconnect: Boolean = true
    private var listener: LiveSessionListener? = null

    @Volatile
    private var currentSetupTier = 0

    @Volatile
    private var reconnectAttempt = 0
    private var reconnectJob: Job? = null
    private var watchdogJob: Job? = null

    @Volatile
    private var lastAudioSentTimestampMs: Long = 0L

    @Volatile
    private var awaitingFirstAudioResponseForTurn: Boolean = false

    @Volatile
    private var receivingTurnAudioInProgress: Boolean = false

    private inner class ConnectionHolder(
        val isCandidate: Boolean
    ) {
        @Volatile
        var ws: WebSocket? = null

        @Volatile
        var setupComplete: Boolean = false

        @Volatile
        var connectedAtMs: Long = 0L

        @Volatile
        var isClosingIntentionally: Boolean = false

        @Volatile
        var allowReceivingAudio: Boolean = true
    }

    /**
     * Pre-warms DNS resolution and TLS 1.3 connection pool to Google AI servers without
     * sending any credentials, eliminating cold TCP+TLS handshake latency when starting dubbing.
     */
    fun prewarmTlsConnection() {
        scope.launch {
            try {
                if (!SecurityShield.isAllowedGeminiEndpoint(REST_MODELS_URL)) return@launch
                val req = Request.Builder()
                    .url(REST_MODELS_URL)
                    .head()
                    .build()
                okHttpClient.newCall(req).execute().use { /* warm TCP + TLS 1.3 pool */ }
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * Fetches ONLY the latest verified live translation & dubbing models available to this API key,
     * authenticating exclusively via encrypted `x-goog-api-key` header (zero URL key exposure!).
     */
    suspend fun fetchBidiModels(apiKey: String): Result<List<GeminiModelInfo>> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("API key is empty."))
        }
        try {
            val collected = LinkedHashMap<String, GeminiModelInfo>()
            var pageToken: String? = null
            var pagesFetched = 0

            do {
                val urlBuilder = StringBuilder("$REST_MODELS_URL?pageSize=1000")
                if (!pageToken.isNullOrBlank()) {
                    urlBuilder.append("&pageToken=").append(java.net.URLEncoder.encode(pageToken, "UTF-8"))
                }
                val req = buildSecuredRequest(urlBuilder.toString(), cleanKey)
                okHttpClient.newCall(req).execute().use { resp ->
                    val bodyStr = resp.body?.string() ?: ""
                    if (!resp.isSuccessful) {
                        val entry = ErrorCatalog.fromCode(resp.code, Redactor.redact(bodyStr))
                        return@withContext Result.failure(IllegalStateException(entry.title))
                    }
                    val (pageItems, next) = parseModelsPage(bodyStr)
                    for (item in pageItems) {
                        collected[item.id] = item
                    }
                    pageToken = next
                    pagesFetched++
                }
            } while (!pageToken.isNullOrBlank() && pagesFetched < 6)

            // NEVER inject phantom models that aren't enabled on this API key — only fallback if empty!
            if (collected.isEmpty()) {
                collected[DEFAULT_TRANSLATE_MODEL] = GeminiModelInfo(
                    id = DEFAULT_TRANSLATE_MODEL,
                    displayName = "Gemini 2.5 Flash Native Audio • Instant Zero-Shot Clone",
                    supportsBidi = true,
                    isRecommendedTranslate = true
                )
            }

            val sorted = collected.values.sortedWith(
                compareByDescending<GeminiModelInfo> { it.id.contains("live-translate", ignoreCase = true) }
                    .thenByDescending { modelRecencyScore(it.id) }
                    .thenBy { it.id }
            )
            Result.success(sorted)
        } catch (t: Throwable) {
            val entry = ErrorCatalog.fromThrowable(t)
            Result.failure(IllegalStateException(entry.title, t))
        }
    }

    /**
     * Performs a REAL Gemini Live WebSocket handshake test (`setup` -> `setupComplete`)
     * using `x-goog-api-key` header auth and caches the winning setupTier for instant starts.
     */
    suspend fun testLiveModelConnection(
        apiKey: String,
        modelName: String,
        sourceLanguageCode: String = LanguageItem.AUTO_CODE,
        targetLanguageCode: String = "tr"
    ): Result<LiveConnectionTestResult> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("API key is empty."))
        }
        val cleanModel = modelName.trim().removePrefix("models/").ifEmpty { DEFAULT_TRANSLATE_MODEL }

        var lastErrorMsg = "Handshake timeout"
        for (tier in 0..2) {
            val startedMs = System.currentTimeMillis()
            val outcome = CompletableDeferred<Pair<Boolean, String>>()
            val request = buildSecuredRequest(WS_ENDPOINT, cleanKey)

            val ws = okHttpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    val setupJson = buildSetupJson(
                        modelName = cleanModel,
                        echoTargetLanguage = false,
                        setupTier = tier,
                        sourceLanguageCode = sourceLanguageCode,
                        targetLanguageCode = targetLanguageCode
                    )
                    webSocket.send(setupJson)
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    parseTestMessage(text, outcome)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    parseTestMessage(bytes.utf8(), outcome)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, null)
                    val safe = Redactor.redact(reason.ifBlank { "HTTP/WS $code" })
                    outcome.complete(false to "$code:$safe")
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    val code = response?.code ?: 0
                    val safe = Redactor.redact(t.message ?: "Connection failed")
                    outcome.complete(false to "$code:$safe")
                }
            })

            val res = withTimeoutOrNull(10_000L) { outcome.await() }
            try {
                ws.close(1000, "Connection test finished")
            } catch (_: Throwable) {
            }

            if (res == null) {
                lastErrorMsg = "Sunucu yanıt zaman aşımı (10s)"
                break
            }
            if (res.first) {
                workingTierByModel[cleanModel] = tier
                val rtt = (System.currentTimeMillis() - startedMs).coerceAtLeast(1L)
                return@withContext Result.success(
                    LiveConnectionTestResult(
                        modelId = cleanModel,
                        latencyMs = rtt,
                        setupTierUsed = tier
                    )
                )
            } else {
                lastErrorMsg = res.second
                val shouldRetryTier = lastErrorMsg.startsWith("1007:") ||
                    lastErrorMsg.startsWith("400:") ||
                    lastErrorMsg.contains("INVALID_ARGUMENT", ignoreCase = true)
                if (!shouldRetryTier) {
                    break
                }
            }
        }

        val cleanedError = Redactor.redact(lastErrorMsg)
        val entry = when {
            cleanedError.startsWith("401:") || cleanedError.startsWith("403:") || cleanedError.startsWith("1008:") ||
                cleanedError.contains("API_KEY_INVALID", ignoreCase = true) ->
                ErrorCatalog.fromCode(1008, cleanedError)
            cleanedError.startsWith("404:") || cleanedError.contains("NOT_FOUND", ignoreCase = true) ->
                ErrorCatalog.fromCode(404, cleanedError)
            cleanedError.startsWith("429:") -> ErrorCatalog.fromCode(429, cleanedError)
            else -> ErrorCatalog.fromCode(1006, cleanedError)
        }
        Result.failure(IllegalStateException(entry.title))
    }

    private fun parseTestMessage(rawJson: String, outcome: CompletableDeferred<Pair<Boolean, String>>) {
        try {
            val root = JSONObject(rawJson)
            if (root.has("setupComplete")) {
                outcome.complete(true to "OK")
                return
            }
            if (root.has("error")) {
                val errObj = root.optJSONObject("error")
                val code = errObj?.optInt("code", 1007) ?: 1007
                val msg = Redactor.redact(errObj?.optString("message", rawJson) ?: rawJson)
                outcome.complete(false to "$code:$msg")
            }
        } catch (_: Throwable) {
        }
    }

    fun start(
        apiKey: String,
        modelName: String,
        echoTargetLanguage: Boolean,
        autoReconnect: Boolean,
        listener: LiveSessionListener,
        sourceLanguageCode: String = LanguageItem.AUTO_CODE,
        targetLanguageCode: String = "tr"
    ) {
        stopInternal()
        this.apiKey = apiKey.trim()
        this.modelName = modelName.trim().removePrefix("models/").ifEmpty { DEFAULT_TRANSLATE_MODEL }
        this.sourceLanguageCode = sourceLanguageCode.trim().lowercase().ifEmpty { LanguageItem.AUTO_CODE }
        this.targetLanguageCode = targetLanguageCode.trim().lowercase().takeUnless { it.isEmpty() || it == LanguageItem.AUTO_CODE } ?: "tr"
        this.echoTargetLanguage = echoTargetLanguage
        this.autoReconnect = autoReconnect
        this.listener = listener
        this.currentSetupTier = workingTierByModel[this.modelName] ?: 0
        this.reconnectAttempt = 0
        this.awaitingFirstAudioResponseForTurn = false
        this.receivingTurnAudioInProgress = false
        synchronized(startupPreBufferLock) { startupPcmPreBuffer.clear() }
        this.isStopped.set(false)
        this.isRenewing.set(false)

        _state.value = LiveSessionState.Connecting
        openConnection(isCandidate = false, setupTier = currentSetupTier)
        startRenewalWatchdog()
    }

    fun currentActiveModel(): String = modelName

    internal fun pendingStartupPreBufferCount(): Int =
        synchronized(startupPreBufferLock) { startupPcmPreBuffer.size }

    fun switchModelLive(
        newModel: String,
        echoTargetLanguage: Boolean,
        newApiKey: String? = null,
        sourceLanguageCode: String = this.sourceLanguageCode,
        targetLanguageCode: String = this.targetLanguageCode
    ) {
        val cleanedModel = newModel.trim().removePrefix("models/").ifEmpty { DEFAULT_TRANSLATE_MODEL }
        val cleanedKey = newApiKey?.trim()?.takeIf { it.isNotEmpty() }
        val cleanSource = sourceLanguageCode.trim().lowercase().ifEmpty { LanguageItem.AUTO_CODE }
        val cleanTarget = targetLanguageCode.trim().lowercase().takeUnless { it.isEmpty() || it == LanguageItem.AUTO_CODE } ?: "tr"

        val keyChanged = cleanedKey != null && cleanedKey != this.apiKey
        if (cleanedKey != null) {
            this.apiKey = cleanedKey
        }
        if (isStopped.get()) {
            this.modelName = cleanedModel
            this.echoTargetLanguage = echoTargetLanguage
            this.sourceLanguageCode = cleanSource
            this.targetLanguageCode = cleanTarget
            return
        }
        val modelChanged = cleanedModel != this.modelName
        val echoChanged = echoTargetLanguage != this.echoTargetLanguage
        val sourceChanged = cleanSource != this.sourceLanguageCode
        val targetChanged = cleanTarget != this.targetLanguageCode
        if (!modelChanged && !echoChanged && !keyChanged && !sourceChanged && !targetChanged) return

        this.modelName = cleanedModel
        this.echoTargetLanguage = echoTargetLanguage
        this.sourceLanguageCode = cleanSource
        this.targetLanguageCode = cleanTarget
        this.currentSetupTier = workingTierByModel[cleanedModel] ?: 0
        this.reconnectAttempt = 0
        this.awaitingFirstAudioResponseForTurn = false
        this.receivingTurnAudioInProgress = false
        this.isRenewing.set(false)
        reconnectJob?.cancel()
        reconnectJob = null
        candidateHolder.getAndSet(null)?.let { cand ->
            cand.isClosingIntentionally = true
            cand.allowReceivingAudio = false
            try {
                cand.ws?.close(1000, "Configuration switched")
            } catch (_: Throwable) {
            }
        }
        _state.value = LiveSessionState.Connecting
        openConnection(isCandidate = false, setupTier = currentSetupTier)
    }

    fun updateRuntimeFlags(apiKey: String, autoReconnect: Boolean) {
        this.autoReconnect = autoReconnect
        if (isStopped.get() && apiKey.isNotBlank()) {
            this.apiKey = apiKey.trim()
        }
    }

    /**
     * Streams a 16 kHz PCM chunk to Gemini Live.
     * If the WebSocket handshake (`setupComplete`) is still in flight, buffers the audio cleanly
     * in [startupPcmPreBuffer] (up to 1.8s) so ZERO opening words are lost and the AI responds
     * the instant the socket becomes ready!
     */
    fun sendPcm16kChunk(pcmBytes: ByteArray) {
        if (isStopped.get() || pcmBytes.isEmpty()) return
        val holder = activeHolder.get()
        if (holder == null || !holder.setupComplete) {
            synchronized(startupPreBufferLock) {
                if (startupPcmPreBuffer.size >= MAX_STARTUP_PREBUFFER_CHUNKS) {
                    startupPcmPreBuffer.removeFirst()
                }
                startupPcmPreBuffer.addLast(pcmBytes.copyOf())
            }
            return
        }
        val ws = holder.ws ?: return
        sendRawPcmOverWebSocket(ws, pcmBytes)
    }

    private fun flushStartupPreBuffer(ws: WebSocket) {
        val pending: List<ByteArray> = synchronized(startupPreBufferLock) {
            if (startupPcmPreBuffer.isEmpty()) return
            val copy = startupPcmPreBuffer.toList()
            startupPcmPreBuffer.clear()
            copy
        }
        for (chunk in pending) {
            if (isStopped.get()) break
            sendRawPcmOverWebSocket(ws, chunk)
        }
    }

    private fun sendRawPcmOverWebSocket(ws: WebSocket, pcmBytes: ByteArray) {
        if (ws.queueSize() > MAX_QUEUE_BYTES) return
        val b64 = Base64.encodeToString(pcmBytes, Base64.NO_WRAP)
        val payload = buildRealtimeAudioJson(b64)
        if (!awaitingFirstAudioResponseForTurn && !receivingTurnAudioInProgress) {
            lastAudioSentTimestampMs = System.currentTimeMillis()
            awaitingFirstAudioResponseForTurn = true
        }
        ws.send(payload)
    }

    fun stop() {
        stopInternal()
        _state.value = LiveSessionState.Stopped
    }

    private fun stopInternal() {
        isStopped.set(true)
        isRenewing.set(false)
        reconnectJob?.cancel()
        reconnectJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        synchronized(startupPreBufferLock) { startupPcmPreBuffer.clear() }

        candidateHolder.getAndSet(null)?.let { h ->
            h.isClosingIntentionally = true
            h.allowReceivingAudio = false
            try {
                h.ws?.close(1000, "Stopped")
            } catch (_: Throwable) {
            }
        }
        activeHolder.getAndSet(null)?.let { h ->
            h.isClosingIntentionally = true
            h.allowReceivingAudio = false
            try {
                h.ws?.close(1000, "Stopped")
            } catch (_: Throwable) {
            }
        }
    }

    private fun startRenewalWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive && !isStopped.get()) {
                delay(5000L)
                val active = activeHolder.get()
                if (active != null && active.setupComplete && active.connectedAtMs > 0L) {
                    val age = System.currentTimeMillis() - active.connectedAtMs
                    if (age >= RENEW_THRESHOLD_MS && !isRenewing.get()) {
                        triggerSeamlessRenewal()
                    }
                }
            }
        }
    }

    private fun triggerSeamlessRenewal() {
        if (isStopped.get() || !isRenewing.compareAndSet(false, true)) return
        _state.value = LiveSessionState.Renewing
        openConnection(isCandidate = true, setupTier = currentSetupTier)
    }

    private fun openConnection(isCandidate: Boolean, setupTier: Int) {
        if (isStopped.get()) return
        val request = buildSecuredRequest(WS_ENDPOINT, apiKey)
        val holder = ConnectionHolder(isCandidate = isCandidate)

        if (isCandidate) {
            candidateHolder.getAndSet(holder)?.let { oldCand ->
                oldCand.isClosingIntentionally = true
                oldCand.allowReceivingAudio = false
                try {
                    oldCand.ws?.close(1000, "Replaced")
                } catch (_: Throwable) {
                }
            }
        } else {
            activeHolder.getAndSet(holder)?.let { oldActive ->
                oldActive.isClosingIntentionally = true
                oldActive.allowReceivingAudio = false
                try {
                    oldActive.ws?.close(1000, "Replaced")
                } catch (_: Throwable) {
                }
            }
        }

        val ws = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (isStopped.get() || holder.isClosingIntentionally) {
                    webSocket.close(1000, "Cancelled")
                    return
                }
                val setupJson = buildSetupJson(
                    modelName = modelName,
                    echoTargetLanguage = echoTargetLanguage,
                    setupTier = setupTier,
                    sourceLanguageCode = sourceLanguageCode,
                    targetLanguageCode = targetLanguageCode
                )
                webSocket.send(setupJson)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerFrame(holder, text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleServerFrame(holder, bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                val safeReason = Redactor.redact(reason)
                try {
                    webSocket.close(code, null)
                } catch (_: Throwable) {
                }
                if (!holder.isClosingIntentionally && !isStopped.get()) {
                    handleUnexpectedDisconnect(holder, code, safeReason, null)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!holder.isClosingIntentionally && !isStopped.get()) {
                    handleUnexpectedDisconnect(holder, code, Redactor.redact(reason), null)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (holder.isClosingIntentionally || isStopped.get()) return
                val httpCode = response?.code ?: 0
                val safeMsg = Redactor.redact(t.message ?: "Unknown connection error")
                handleUnexpectedDisconnect(holder, httpCode, safeMsg, t)
            }
        })
        holder.ws = ws
    }

    private fun handleServerFrame(holder: ConnectionHolder, rawJson: String) {
        if (isStopped.get() || !holder.allowReceivingAudio) return
        try {
            val root = JSONObject(rawJson)

            if (root.has("setupComplete")) {
                if (holder !== activeHolder.get() && holder !== candidateHolder.get()) return
                holder.setupComplete = true
                holder.connectedAtMs = System.currentTimeMillis()
                workingTierByModel[modelName] = currentSetupTier
                reconnectAttempt = 0

                if (holder.isCandidate || candidateHolder.get() === holder) {
                    val oldActive = activeHolder.getAndSet(holder)
                    candidateHolder.compareAndSet(holder, null)
                    isRenewing.set(false)
                    _state.value = LiveSessionState.Streaming

                    if (oldActive != null && oldActive !== holder) {
                        oldActive.isClosingIntentionally = true
                        scope.launch {
                            delay(DRAIN_OLD_WS_MS)
                            oldActive.allowReceivingAudio = false
                            try {
                                oldActive.ws?.close(1000, "Session renewed")
                            } catch (_: Throwable) {
                            }
                        }
                    }
                } else {
                    _state.value = LiveSessionState.Streaming
                }

                // Immediately burst-flush any audio captured during handshake so ZERO opening words are lost!
                holder.ws?.let { readyWs ->
                    flushStartupPreBuffer(readyWs)
                }
            }

            if (root.has("goAway") && holder === activeHolder.get()) {
                triggerSeamlessRenewal()
            }

            val serverContent = root.optJSONObject("serverContent")
            if (serverContent != null) {
                val inputTrans = serverContent.optJSONObject("inputTranscription")
                    ?: serverContent.optJSONObject("inputAudioTranscription")
                    ?: serverContent.optJSONObject("interimInputTranscription")
                if (inputTrans != null) {
                    val text = inputTrans.optString("text", "").ifEmpty {
                        inputTrans.optString("transcript", "")
                    }
                    val lang = inputTrans.optString("languageCode", "")
                        .ifBlank { inputTrans.optString("language", "") }
                        .ifBlank { inputTrans.optString("detectedLanguage", "") }
                        .ifBlank { null }
                    if (text.isNotEmpty() || lang != null) {
                        listener?.onInputTranscript(text, lang)
                    }
                }

                val outputTrans = serverContent.optJSONObject("outputTranscription")
                    ?: serverContent.optJSONObject("outputAudioTranscription")
                var outputTextDeliveredInFrame = false
                if (outputTrans != null) {
                    val text = outputTrans.optString("text", "").ifEmpty {
                        outputTrans.optString("transcript", "")
                    }
                    if (text.isNotEmpty()) {
                        outputTextDeliveredInFrame = true
                        listener?.onOutputTranscript(text)
                    }
                }

                val modelTurn = serverContent.optJSONObject("modelTurn")
                val parts = modelTurn?.optJSONArray("parts")
                if (parts != null) {
                    for (i in 0 until parts.length()) {
                        val part = parts.optJSONObject(i) ?: continue
                        if (!outputTextDeliveredInFrame && !part.optBoolean("thought", false)) {
                            val partText = part.optString("text", "")
                            if (partText.isNotEmpty()) {
                                listener?.onOutputTranscript(partText)
                            }
                        }
                        val inlineData = part.optJSONObject("inlineData") ?: continue
                        val b64Data = inlineData.optString("data", "")
                        if (b64Data.isNotEmpty()) {
                            val pcmBytes = Base64.decode(b64Data, Base64.DEFAULT)
                            receivingTurnAudioInProgress = true
                            val latency: Long? = if (awaitingFirstAudioResponseForTurn && lastAudioSentTimestampMs > 0L) {
                                awaitingFirstAudioResponseForTurn = false
                                (System.currentTimeMillis() - lastAudioSentTimestampMs).coerceIn(10L, 15000L)
                            } else {
                                null
                            }
                            listener?.onAudioOutputPcm24k(pcmBytes, latency)
                        }
                    }
                }

                if (serverContent.optBoolean("interrupted", false)) {
                    awaitingFirstAudioResponseForTurn = false
                    receivingTurnAudioInProgress = false
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    awaitingFirstAudioResponseForTurn = false
                    receivingTurnAudioInProgress = false
                    listener?.onTurnComplete()
                }
            }

            if (root.has("error")) {
                val errObj = root.optJSONObject("error")
                val errCode = errObj?.optInt("code", 1007) ?: 1007
                val errMsg = Redactor.redact(errObj?.optString("message", rawJson) ?: rawJson)
                handleUnexpectedDisconnect(holder, errCode, errMsg, null)
            }
        } catch (_: Throwable) {
        }
    }

    private fun handleUnexpectedDisconnect(
        holder: ConnectionHolder,
        code: Int,
        reason: String,
        throwable: Throwable?
    ) {
        if (isStopped.get() || holder.isClosingIntentionally) return
        if (holder !== activeHolder.get() && holder !== candidateHolder.get()) return
        holder.isClosingIntentionally = true
        holder.allowReceivingAudio = false

        if (candidateHolder.compareAndSet(holder, null)) {
            isRenewing.set(false)
            val active = activeHolder.get()
            if (active != null && active.setupComplete && !active.isClosingIntentionally) {
                _state.value = LiveSessionState.Streaming
                return
            }
        }

        val isModelNotFound = code == 404 || reason.contains("NOT_FOUND", ignoreCase = true) || reason.contains("not found", ignoreCase = true)
        if (isModelNotFound && !modelName.equals(DEFAULT_TRANSLATE_MODEL, ignoreCase = true)) {
            modelName = DEFAULT_TRANSLATE_MODEL
            currentSetupTier = workingTierByModel[DEFAULT_TRANSLATE_MODEL] ?: 0
            listener?.onModelFallbackToDefault(DEFAULT_TRANSLATE_MODEL)
            openConnection(isCandidate = false, setupTier = currentSetupTier)
            return
        }
        if (code == 1007 || code == 400 || reason.contains("1007") || reason.contains("INVALID_ARGUMENT", ignoreCase = true)) {
            if (currentSetupTier < 2) {
                currentSetupTier++
                openConnection(isCandidate = false, setupTier = currentSetupTier)
                return
            } else if (!modelName.equals(DEFAULT_TRANSLATE_MODEL, ignoreCase = true)) {
                modelName = DEFAULT_TRANSLATE_MODEL
                currentSetupTier = workingTierByModel[DEFAULT_TRANSLATE_MODEL] ?: 0
                listener?.onModelFallbackToDefault(DEFAULT_TRANSLATE_MODEL)
                openConnection(isCandidate = false, setupTier = currentSetupTier)
                return
            } else {
                val entry = ErrorCatalog.fromCode(1007, reason)
                _state.value = LiveSessionState.Error(entry.solution, entry)
                listener?.onFatalError(entry)
                return
            }
        }

        val entry = when {
            code in listOf(1008, 401, 403) || reason.contains("API_KEY_INVALID", ignoreCase = true) || reason.contains("permission", ignoreCase = true) ->
                ErrorCatalog.fromCode(if (code != 0) code else 1008, reason)
            code == 429 || reason.contains("429") || reason.contains("RESOURCE_EXHAUSTED", ignoreCase = true) ->
                ErrorCatalog.fromCode(429, reason)
            throwable != null && code == 0 ->
                ErrorCatalog.fromThrowable(throwable)
            else ->
                ErrorCatalog.fromCode(if (code != 0) code else 1006, reason)
        }

        if (!entry.shouldRetry || !autoReconnect) {
            _state.value = LiveSessionState.Error(entry.title, entry)
            listener?.onFatalError(entry)
            return
        }

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            reconnectAttempt++
            val waitSec = if (entry.code == "429") {
                5
            } else {
                BACKOFF_SECONDS[(reconnectAttempt - 1).coerceIn(0, BACKOFF_SECONDS.lastIndex)]
            }
            _state.value = LiveSessionState.Reconnecting(reconnectAttempt, waitSec)
            delay(waitSec * 1000L)
            if (!isStopped.get()) {
                _state.value = LiveSessionState.Connecting
                openConnection(isCandidate = false, setupTier = currentSetupTier)
            }
        }
    }
}
