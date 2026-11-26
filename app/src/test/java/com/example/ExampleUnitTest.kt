package com.example

import android.media.AudioDeviceInfo
import com.example.audio.AudioMetrics
import com.example.audio.BluetoothAudioRouter
import com.example.audio.DubJitterBuffer
import com.example.audio.Resampler
import com.example.audio.SplashAudioPlayer
import com.example.data.AppThemeMode
import com.example.data.LanguageItem
import com.example.data.OsdFontSizePreset
import com.example.data.SettingsRepository
import com.example.i18n.AppStrings
import com.example.live.LiveSessionClient
import com.example.security.SecurityShield
import com.example.util.ErrorCatalog
import com.example.util.HapticFeedbackHelper
import com.example.util.LanguageDetector
import com.example.util.Redactor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ExampleUnitTest {

    @Test
    fun resampler_48kStereoTo16kMono_producesExactSampleCountAndSteadyStateDC() {
        val resampler = Resampler(inputSampleRate = 48000, inputChannels = 2, outputSampleRate = 16000)
        val stereoInput = ShortArray(1920) { i ->
            if (i % 2 == 0) 4000.toShort() else 6000.toShort()
        }
        val mono16k = resampler.process(stereoInput, stereoInput.size)
        assertEquals(320, mono16k.size)
        for (i in 15 until mono16k.size) {
            assertTrue(mono16k[i] in 4990..5010)
        }
    }

    @Test
    fun resampler_44100StereoTo16kMono_fractionalInterpolationWorks() {
        val resampler = Resampler(inputSampleRate = 44100, inputChannels = 2, outputSampleRate = 16000)
        val stereoInput = ShortArray(8820) { 3000.toShort() }
        val mono16k = resampler.process(stereoInput, stereoInput.size)
        assertTrue(mono16k.size in 1598..1602)
    }

    @Test
    fun jitterBuffer_primesFastAt80msAndFlushesShortWordsOnTurnComplete() {
        val buffer = DubJitterBuffer(sampleRate = 24000)
        val outFrame = ShortArray(480) // 20 ms at 24 kHz

        // 1. Before 80ms prime threshold, popSamples returns false
        assertFalse(buffer.popSamples(outFrame))

        // 2. Push 1920 samples (80 ms = 3840 bytes) of value 1024 to prime buffer immediately
        val pcmBytes = ByteArray(3840)
        var bi = 0
        repeat(1920) {
            pcmBytes[bi++] = 0x00
            pcmBytes[bi++] = 0x04
        }
        buffer.pushPcm16Le(pcmBytes)
        assertEquals(80, buffer.bufferedMs())

        // 3. Pop all 4 frames (4 x 480 = 1920 samples)
        repeat(4) {
            assertTrue(buffer.popSamples(outFrame))
            assertEquals(1024.toShort(), outFrame[0])
            assertEquals(1024.toShort(), outFrame[479])
        }

        // 4. Next pop triggers underrun
        assertFalse(buffer.popSamples(outFrame))
        assertEquals(1, buffer.underrunCount)

        // 5. ZERO WORD SWALLOWING TEST:
        // Push an ultra-short 40ms word (960 samples < 1920 prime threshold)
        val shortWordSamples = ShortArray(960) { 2048.toShort() }
        buffer.pushSamples(shortWordSamples)
        assertFalse(buffer.popSamples(outFrame))
        buffer.flushShortUtterance()
        repeat(2) {
            assertTrue(buffer.popSamples(outFrame))
            assertEquals(2048.toShort(), outFrame[0])
        }
    }

    @Test
    fun securityShield_enforcesEndpointAllowlistHeaderOnlyAuthAndDeepRedaction() {
        // Allowed official Google Gemini endpoints (NO key query parameter!)
        assertTrue(SecurityShield.isAllowedGeminiEndpoint(LiveSessionClient.WS_ENDPOINT))
        assertTrue(SecurityShield.isAllowedGeminiEndpoint(LiveSessionClient.REST_MODELS_URL + "?pageSize=1000"))

        // Blocked endpoints: cleartext HTTP, hostile host, SSRF loopback, or API key leaked in URL!
        assertFalse(SecurityShield.isAllowedGeminiEndpoint("http://generativelanguage.googleapis.com/v1beta/models"))
        assertFalse(SecurityShield.isAllowedGeminiEndpoint("https://evil-hacker.example.com/v1beta/models"))
        assertFalse(SecurityShield.isAllowedGeminiEndpoint("https://127.0.0.1/v1beta/models"))
        assertFalse(SecurityShield.isAllowedGeminiEndpoint("${LiveSessionClient.WS_ENDPOINT}?key=AIzaSySecret12345678901234567890"))

        // Secured request uses x-goog-api-key header exclusively, never URL query string
        val req = LiveSessionClient.buildSecuredRequest(LiveSessionClient.REST_MODELS_URL, "AIzaSyTestHeaderOnlyKey1234567890")
        assertEquals("AIzaSyTestHeaderOnlyKey1234567890", req.header(SecurityShield.HEADER_API_KEY))
        assertFalse(req.url.toString().contains("AIzaSy"))

        // Masking & deep redaction
        assertEquals("AIza••••••••7890", SecurityShield.maskApiKeyForDisplay("AIzaSyTestHeaderOnlyKey1234567890"))
        val leak = "Header x-goog-api-key: AIzaSyAbCdEfGhIjKlMnOpQrStUvWxYz123456 Bearer tok_secret v2:ks:AAAA:BBBB"
        val safe = Redactor.redact(leak)
        assertFalse(safe.contains("AIzaSyAbCdEf"))
        assertFalse(safe.contains("tok_secret"))
        assertFalse(safe.contains("v2:ks:AAAA:BBBB"))
    }

    @Test
    fun splashAudioPlayer_synthesizes6SecondCinematicStereoAudioWithCleanFades() {
        assertEquals(6000L, SplashAudioPlayer.DURATION_MS)
        assertEquals(0f to 0f, SplashAudioPlayer.synthesizeStereoSample(0.0))
        assertEquals(0f to 0f, SplashAudioPlayer.synthesizeStereoSample(6.0))

        // Mid-arpeggio & 3.25s signature chord reveal must produce rich stereo signal inside [-1, 1]
        var maxEnergy = 0f
        var stereoDiffSeen = false
        for (ms in 500..4500 step 25) {
            val (l, r) = SplashAudioPlayer.synthesizeStereoSample(ms / 1000.0)
            assertTrue(l in -1f..1f)
            assertTrue(r in -1f..1f)
            maxEnergy = maxOf(maxEnergy, abs(l), abs(r))
            if (abs(l - r) > 0.005f) stereoDiffSeen = true
        }
        assertTrue("Splash audio must produce audible energy", maxEnergy > 0.15f)
        assertTrue("Splash audio must have stereo separation", stereoDiffSeen)

        // Smooth fade-out at 5.98s approaches zero cleanly (zero click)
        val (endL, endR) = SplashAudioPlayer.synthesizeStereoSample(5.98)
        assertTrue(abs(endL) < 0.03f && abs(endR) < 0.03f)
    }

    @Test
    fun liveSessionClient_strictDubbingModelFilter_and_recencySorting_eliminateAllClutter() {
        // Must REJECT non-dubbing clutter models
        assertFalse(LiveSessionClient.isLiveDubbingCapableModel("gemini-2.5-pro", supportsBidiMethod = false))
        assertFalse(LiveSessionClient.isLiveDubbingCapableModel("gemini-2.5-flash", supportsBidiMethod = false))
        assertFalse(LiveSessionClient.isLiveDubbingCapableModel("text-embedding-004", supportsBidiMethod = false))
        assertFalse(LiveSessionClient.isLiveDubbingCapableModel("imagen-3.0-generate-002", supportsBidiMethod = false))
        assertFalse(LiveSessionClient.isLiveDubbingCapableModel("veo-2.0-generate-001", supportsBidiMethod = false))
        assertFalse(LiveSessionClient.isLiveDubbingCapableModel("gemma-3-27b-it", supportsBidiMethod = false))
        assertFalse(LiveSessionClient.isLiveDubbingCapableModel("gemini-2.5-flash-preview-tts", supportsBidiMethod = false))
        assertFalse(LiveSessionClient.isLiveDubbingCapableModel("gemini-2.5-pro-preview-tts", supportsBidiMethod = true))

        // Must ACCEPT dedicated live translation & native audio dubbing models
        assertTrue(LiveSessionClient.isLiveDubbingCapableModel("gemini-3.5-live-translate-preview", supportsBidiMethod = true))
        assertTrue(LiveSessionClient.isLiveDubbingCapableModel("gemini-2.5-flash-native-audio-preview-12-2025", supportsBidiMethod = true))
        assertTrue(LiveSessionClient.isLiveDubbingCapableModel("gemini-2.0-flash-live-001", supportsBidiMethod = true))
        assertTrue(LiveSessionClient.isLiveDubbingCapableModel("gemini-3.0-flash-native-audio-preview-06-2026", supportsBidiMethod = false))

        // Recency ranking: newer Gemini versions & dates always score higher
        val score35 = LiveSessionClient.modelRecencyScore("gemini-3.5-live-translate-preview")
        val score25Dec = LiveSessionClient.modelRecencyScore("gemini-2.5-flash-native-audio-preview-12-2025")
        val score25Sep = LiveSessionClient.modelRecencyScore("gemini-2.5-flash-native-audio-preview-09-2025")
        val score20 = LiveSessionClient.modelRecencyScore("gemini-2.0-flash-live-001")
        assertTrue(score35 > score25Dec)
        assertTrue(score25Dec > score25Sep)
        assertTrue(score25Sep > score20)
    }

    @Test
    fun liveSessionClient_buildSetupJson_enforcesUltraLowLatencyVadVoiceCloningAndZeroThinkingDelay() {
        // 1. Translate model with explicit source -> target (en -> de)
        val jsonTranslate = LiveSessionClient.buildSetupJson(
            modelName = "models/gemini-3.5-live-translate-preview",
            echoTargetLanguage = false,
            setupTier = 0,
            sourceLanguageCode = "en",
            targetLanguageCode = "de"
        )
        assertFalse(jsonTranslate.contains("models/models/"))
        assertTrue(jsonTranslate.contains("\"model\":\"models/gemini-3.5-live-translate-preview\""))
        assertTrue(jsonTranslate.contains("\"sourceLanguageCode\":\"en\""))
        assertTrue(jsonTranslate.contains("\"targetLanguageCode\":\"de\""))
        assertTrue(jsonTranslate.contains("\"activityHandling\":\"NO_INTERRUPTION\""))

        // 2. Native audio model uses FAST 320ms VAD + Automatic Zero-Shot Speaker Voice Cloning
        val jsonAutoVoiceClone = LiveSessionClient.buildSetupJson(
            modelName = "gemini-2.5-flash-native-audio-preview-12-2025",
            echoTargetLanguage = false,
            setupTier = 0,
            sourceLanguageCode = "auto",
            targetLanguageCode = "tr"
        )
        assertTrue(jsonAutoVoiceClone.contains("\"languageCode\":\"tr-TR\""))
        assertFalse(jsonAutoVoiceClone.contains("prebuiltVoiceConfig"))
        assertTrue(jsonAutoVoiceClone.contains("AUTOMATIC ZERO-SHOT SPEAKER VOICE CLONING"))
        assertTrue(jsonAutoVoiceClone.contains("ZERO-WORD-SWALLOWING"))
        assertTrue(jsonAutoVoiceClone.contains("\"activityHandling\":\"NO_INTERRUPTION\""))
        assertTrue(jsonAutoVoiceClone.contains("\"startOfSpeechSensitivity\":\"START_SENSITIVITY_HIGH\""))
        assertTrue(jsonAutoVoiceClone.contains("\"endOfSpeechSensitivity\":\"END_SENSITIVITY_HIGH\""))
        assertTrue(jsonAutoVoiceClone.contains("\"prefixPaddingMs\":220"))
        assertTrue(jsonAutoVoiceClone.contains("\"silenceDurationMs\":320"))

        // 3. Thinking-capable models disable thinking budget so AI never stalls before speaking
        val jsonThinking = LiveSessionClient.buildSetupJson(
            modelName = "gemini-2.5-flash-native-audio-thinking-preview",
            echoTargetLanguage = false,
            setupTier = 0
        )
        assertTrue(jsonThinking.contains("\"thinkingBudget\":0"))

        // 4. Tier 1 and Tier 2 fallbacks strip advanced fields cleanly
        val tier1 = LiveSessionClient.buildSetupJson(
            modelName = "gemini-2.5-flash-native-audio-preview-12-2025",
            echoTargetLanguage = false,
            setupTier = 1
        )
        assertFalse(tier1.contains("automaticActivityDetection"))
        assertTrue(tier1.contains("\"activityHandling\":\"NO_INTERRUPTION\""))

        val tier2 = LiveSessionClient.buildSetupJson(
            modelName = "gemini-2.5-flash-native-audio-preview-12-2025",
            echoTargetLanguage = false,
            setupTier = 2
        )
        assertFalse(tier2.contains("realtimeInputConfig"))
        assertTrue(tier2.contains("\"responseModalities\":[\"AUDIO\"]"))
    }

    @Test
    fun appStrings_supports24CompleteUiLanguages_includingAllSettingsTabsAndThemeStrings() {
        assertTrue("Must support at least 20 app UI languages", AppStrings.SUPPORTED_APP_LANGUAGES.size >= 20)
        assertEquals(24, AppStrings.SUPPORTED_APP_LANGUAGES.size)

        AppStrings.SUPPORTED_APP_LANGUAGES.forEach { option ->
            val pack = AppStrings.forCode(option.code)
            assertEquals(option.code, pack.code)
            assertTrue(pack.startDubbing.isNotBlank())
            assertTrue(pack.wizardTitle.isNotBlank())
            assertTrue(pack.aiVoiceCloneTitle.isNotBlank())
            assertTrue(pack.settingsTabOsd.isNotBlank())
            assertTrue(pack.settingsTabModel.isNotBlank())
            assertTrue(pack.settingsTabAudio.isNotBlank())
            assertTrue(pack.settingsTabGeneral.isNotBlank())
            assertTrue(pack.themeModeSystem.isNotBlank())
            assertTrue(pack.hapticFeedbackTitle.isNotBlank())
            assertTrue(pack.mediaStreamBadge.isNotBlank())
            assertTrue(pack.osdHideHint.isNotBlank())
        }
    }

    @Test
    fun dubbingCatalog_themeModes_haptics_errorCatalog_and_languageDetector_workCorrectly() {
        assertTrue(LanguageItem.ALL_LANGUAGES.size >= 30)
        assertTrue(LanguageItem.byCode("auto", allowAuto = true).isAuto)
        assertEquals("tr", LanguageItem.byCode("auto", allowAuto = false).code)
        assertEquals(OsdFontSizePreset.MEDIUM, OsdFontSizePreset.fromIdOrDefault("unknown"))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromIdOrDefault(null))
        assertEquals(AppThemeMode.LIGHT, AppThemeMode.SYSTEM.next())
        assertEquals(AppThemeMode.DARK, AppThemeMode.LIGHT.next())
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.DARK.next())

        assertEquals(HapticFeedbackHelper.DUB_START_TIMINGS.size, HapticFeedbackHelper.DUB_START_AMPLITUDES.size)
        assertTrue(HapticFeedbackHelper.DUB_START_AMPLITUDES.all { it in 0..255 })

        assertFalse(ErrorCatalog.fromCode(1008, "API_KEY_INVALID").shouldRetry)
        assertTrue(ErrorCatalog.fromCode(1006, "EOF").shouldRetry)

        val langEn = LanguageDetector.resolveSourceLanguageLabel(
            explicitLanguageCode = null,
            transcriptSample = "Welcome to the live broadcast and thank you for watching today",
            previousLabel = "Otomatik algılanıyor"
        )
        assertTrue(langEn.contains("İngilizce"))

        val defaultMetrics = AudioMetrics()
        assertEquals(32, defaultMetrics.captureWaveform.size)
        assertFalse(defaultMetrics.isAudioDetected)
    }

    @Test
    fun liveSessionClient_switchModelLiveWhenStopped_updatesModelCleanly() {
        val client = LiveSessionClient(okhttp3.OkHttpClient())
        assertEquals(LiveSessionClient.DEFAULT_TRANSLATE_MODEL, client.currentActiveModel())

        client.updateRuntimeFlags(apiKey = "AIzaTestKey123", autoReconnect = true)
        client.switchModelLive(
            newModel = "  models/gemini-2.0-flash-live-001  ",
            echoTargetLanguage = true,
            newApiKey = "AIzaUpdatedKey456",
            sourceLanguageCode = "en",
            targetLanguageCode = "tr"
        )
        assertEquals("gemini-2.0-flash-live-001", client.currentActiveModel())
    }

    @Test
    fun audioDuck10Percent_bluetoothDubOnlyRouter_andAllHapticPatterns_areDeterministicAndValid() {
        // 1. Default dubbing volume is 60% and original ducking target is 10%
        assertEquals(60, SettingsRepository.DEFAULT_DUB_VOLUME_PERCENT)
        assertEquals(10, SettingsRepository.ORIGINAL_DUCK_TARGET_PERCENT)

        // 10% of 15-step Android media bar rounds to 2; 10% of 100 rounds to 10; never raises already-quiet audio
        assertEquals(2, SettingsRepository.computeDuckedMusicVolume(currentVol = 12, maxVol = 15, bluetoothDubOnlyActive = false))
        assertEquals(10, SettingsRepository.computeDuckedMusicVolume(currentVol = 80, maxVol = 100, bluetoothDubOnlyActive = false))
        assertEquals(1, SettingsRepository.computeDuckedMusicVolume(currentVol = 1, maxVol = 15, bluetoothDubOnlyActive = false))
        assertEquals(0, SettingsRepository.computeDuckedMusicVolume(currentVol = 0, maxVol = 15, bluetoothDubOnlyActive = false))

        // Bluetooth Dub-Only mode suppresses original media to capture-safe step 1 so only dubbing flows to BT
        assertEquals(1, SettingsRepository.computeDuckedMusicVolume(currentVol = 15, maxVol = 15, bluetoothDubOnlyActive = true))
        assertEquals(0, SettingsRepository.computeDuckedMusicVolume(currentVol = 0, maxVol = 15, bluetoothDubOnlyActive = true))

        // 2. Bluetooth Audio Router prioritizes A2DP > BLE Headset > BLE Speaker > SCO and ignores built-in speaker
        assertFalse(BluetoothAudioRouter.isBluetoothOutputType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        assertFalse(BluetoothAudioRouter.isBluetoothOutputType(AudioDeviceInfo.TYPE_WIRED_HEADPHONES))
        assertTrue(BluetoothAudioRouter.isBluetoothOutputType(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
        assertTrue(BluetoothAudioRouter.isBluetoothOutputType(BluetoothAudioRouter.TYPE_BLE_HEADSET_SAFE))
        assertTrue(BluetoothAudioRouter.isBluetoothOutputType(BluetoothAudioRouter.TYPE_BLE_SPEAKER_SAFE))
        assertTrue(BluetoothAudioRouter.isBluetoothOutputType(AudioDeviceInfo.TYPE_BLUETOOTH_SCO))

        val bestA2dp = BluetoothAudioRouter.pickBestCandidate(
            listOf(
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER to "Phone Speaker",
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO to "Handsfree SCO",
                BluetoothAudioRouter.TYPE_BLE_HEADSET_SAFE to "Galaxy Buds LE",
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP to "Sony WH-1000XM5"
            )
        )
        assertEquals("Sony WH-1000XM5", bestA2dp?.second)

        val bestBle = BluetoothAudioRouter.pickBestCandidate(
            listOf(
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER to "Phone Speaker",
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO to "Handsfree SCO",
                BluetoothAudioRouter.TYPE_BLE_SPEAKER_SAFE to "JBL Flip LE"
            )
        )
        assertEquals("JBL Flip LE", bestBle?.second)

        val noneWhenOnlySpeaker = BluetoothAudioRouter.pickBestCandidate(
            listOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER to "Phone Speaker")
        )
        assertEquals(null, noneWhenOnlySpeaker)

        // 3. All haptic waveform patterns have matching lengths and valid 0..255 amplitudes
        val patterns = listOf(
            HapticFeedbackHelper.DUB_START_TIMINGS to HapticFeedbackHelper.DUB_START_AMPLITUDES,
            HapticFeedbackHelper.DUB_RESUME_TIMINGS to HapticFeedbackHelper.DUB_RESUME_AMPLITUDES,
            HapticFeedbackHelper.DUB_STOP_TIMINGS to HapticFeedbackHelper.DUB_STOP_AMPLITUDES,
            HapticFeedbackHelper.AI_VOICE_TURN_TIMINGS to HapticFeedbackHelper.AI_VOICE_TURN_AMPLITUDES,
            HapticFeedbackHelper.SOFT_TAP_TIMINGS to HapticFeedbackHelper.SOFT_TAP_AMPLITUDES
        )
        patterns.forEach { (timings, amps) ->
            assertEquals(timings.size, amps.size)
            assertTrue(timings.all { it >= 0L })
            assertTrue(amps.all { it in 0..255 })
        }
    }
}
