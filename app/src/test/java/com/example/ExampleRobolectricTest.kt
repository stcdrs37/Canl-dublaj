package com.example

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import com.example.audio.AudioEngine
import com.example.data.AppThemeMode
import com.example.data.LanguageItem
import com.example.data.OsdFontSizePreset
import com.example.data.SettingsRepository
import com.example.live.GeminiModelInfo
import com.example.live.LiveSessionClient
import com.example.security.ApiKeyVault
import com.example.service.DubbingController
import com.example.service.DubbingNotificationHelper
import com.example.service.DubbingOsdOverlayManager
import com.example.util.HapticFeedbackHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun apiKeyVault_encryptsWithRandomIvAes256Gcm_andRejectsTamperedCiphertext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vault = ApiKeyVault(context)
        val secret = "AIzaSyUltraSecretCyberKey1234567890"

        val blob1 = vault.encrypt(secret)
        val blob2 = vault.encrypt(secret)

        assertTrue(vault.isEncryptedBlob(blob1))
        assertFalse("Plaintext API key must NEVER appear in encrypted blob", blob1.contains("AIzaSy"))
        assertNotEquals("Each encryption must use a fresh random GCM IV", blob1, blob2)
        assertEquals(secret, vault.decrypt(blob1))
        assertEquals(secret, vault.decrypt(blob2))

        // Tampering with ciphertext must fail GCM authentication cleanly and return empty string
        val tampered = blob1.dropLast(4) + "ZZZZ"
        assertEquals("", vault.decrypt(tampered))
    }

    @Test
    fun settingsRepository_encryptsApiKeyOnDiskAndAutoMigratesLegacyPlaintext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rawPrefs = context.getSharedPreferences("livedub_prefs_v4", Context.MODE_PRIVATE)
        rawPrefs.edit().clear().putString("api_key", "AIzaSyLegacyPlainKey1234567890").commit()

        // Loading SettingsRepository MUST migrate plaintext to AES-256-GCM vault & wipe legacy plaintext key!
        val repo = SettingsRepository(context)
        assertEquals("AIzaSyLegacyPlainKey1234567890", repo.settings.value.apiKey)
        assertFalse("Legacy plaintext api_key MUST be scrubbed from disk", rawPrefs.contains("api_key"))
        assertTrue(repo.isApiKeyEncryptedOnDisk())

        // Updating API key writes encrypted vault blob only
        repo.updateApiKey("AIzaSyNewCyberKey9876543210")
        assertEquals("AIzaSyNewCyberKey9876543210", repo.settings.value.apiKey)
        assertFalse(rawPrefs.contains("api_key"))
        val storedBlob = rawPrefs.getString("api_key_vault_v2", "") ?: ""
        assertFalse(storedBlob.contains("AIzaSyNewCyberKey"))
        assertTrue(repo.isApiKeyEncryptedOnDisk())

        val report = repo.getCyberSecurityReport()
        assertTrue(report.encryptionActive)
        assertTrue(report.headerOnlyAuthEnforced)
        assertTrue(report.tls13Hardened)
    }

    @Test
    fun audioEngine_usesMediaOrIsolatedNonAlarmBus_and40msLowLatencyChunks() {
        val attrs = AudioEngine.buildDubAudioAttributes()
        assertEquals(AudioAttributes.USAGE_MEDIA, attrs.usage)
        assertEquals(AudioAttributes.CONTENT_TYPE_SPEECH, attrs.contentType)
        assertNotEquals(AudioAttributes.USAGE_ALARM, attrs.usage)

        val isolated = AudioEngine.buildIsolatedDubAudioAttributes()
        assertEquals(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY, isolated.usage)
        assertEquals(AudioAttributes.CONTENT_TYPE_SPEECH, isolated.contentType)
        assertNotEquals(AudioAttributes.USAGE_ALARM, isolated.usage)

        assertEquals(640, AudioEngine.CHUNK_SAMPLES_16K) // 40ms low-latency PCM chunk
        assertEquals(1280, AudioEngine.CHUNK_BYTES_16K)
    }

    @Test
    fun appBranding_andInstant10PercentDuckWithExactVolumeRestore_workCleanly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Screen Dubbing", context.getString(R.string.app_name))

        context.getSharedPreferences("livedub_prefs_v4", Context.MODE_PRIVATE).edit().clear().commit()
        val repo = SettingsRepository(context)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(10)
        val startVol = (maxVol * 0.8f).toInt().coerceAtLeast(3)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, startVol, 0)

        // Immediately drop original media audio to 10% when dubbing begins
        assertTrue(repo.applyOriginalAudioDuck10Percent(audioManager, bluetoothDubOnlyActive = false))
        val expectedDucked = SettingsRepository.computeDuckedMusicVolume(startVol, maxVol, bluetoothDubOnlyActive = false)
        assertEquals(expectedDucked, audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
        assertTrue("Ducked volume must be <= original volume", expectedDucked <= startVol)

        // Switching to Bluetooth Dub-Only drops original media to capture-safe step 1
        assertTrue(repo.applyOriginalAudioDuck10Percent(audioManager, bluetoothDubOnlyActive = true))
        assertEquals(1, audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))

        // Stopping/pausing restores exact original media volume
        repo.restorePendingMusicVolumeIfNeeded(audioManager)
        assertEquals(startVol, audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
    }

    @Test
    @Config(sdk = [23])
    fun android6Api23_coreVaultHapticsNotificationsAndOsd_workCleanlyWithoutCrashing() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vault = ApiKeyVault(context)
        val encrypted = vault.encrypt("AIzaSyAndroid6CompatibleKey123456")
        assertEquals("AIzaSyAndroid6CompatibleKey123456", vault.decrypt(encrypted))

        // API 23 isolated dubbing attributes fall back cleanly to USAGE_MEDIA
        assertEquals(AudioAttributes.USAGE_MEDIA, AudioEngine.buildIsolatedDubAudioAttributes().usage)

        // API 23 Haptics legacy path must not crash
        HapticFeedbackHelper.resetTurnCooldown()
        HapticFeedbackHelper.vibrateDubbingStart(context, enabled = true)
        HapticFeedbackHelper.vibrateAiVoiceTurnStart(context, enabled = true, nowMs = 5000L)
        HapticFeedbackHelper.vibrateDubbingStop(context, enabled = true)
        HapticFeedbackHelper.vibrateSoftTap(context, enabled = true)

        // API 23 Notification builder & OSD manager must work cleanly
        val notif = DubbingNotificationHelper.buildNotification(
            context = context,
            statusText = "Canlı Dublaj",
            detectedLanguageText = "EN",
            isPaused = false,
            targetLanguageLabel = "Türkçe"
        )
        assertNotNull(notif)
        val osd = DubbingOsdOverlayManager(context, {}, {})
        osd.setCollapsed(true)
        assertTrue(osd.isCollapsed())
        osd.dismiss()
    }

    @Test
    fun settingsRepository_persistsSystemThemeHapticsOnboardingLanguagesAndOsdDefaults() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("livedub_prefs_v4", Context.MODE_PRIVATE).edit().clear().commit()
        val repo = SettingsRepository(context)

        assertEquals(AppThemeMode.SYSTEM, repo.settings.value.themeMode)
        assertTrue(repo.settings.value.hapticFeedbackEnabled)
        assertEquals(60, repo.settings.value.dubVolumePercent)
        assertTrue(repo.settings.value.duckOriginalAudio)
        assertTrue(repo.settings.value.bluetoothDubOnlyRouting)
        assertFalse(repo.settings.value.silenceDetectionEnabled)
        assertTrue(repo.settings.value.autoSelectLatestModel)
        assertTrue(repo.settings.value.osdEnabled)
        assertEquals(84, repo.settings.value.osdOpacityPercent)
        assertEquals(OsdFontSizePreset.MEDIUM, repo.settings.value.osdFontSize)
        assertTrue(repo.settings.value.osdShowOriginalText)
        assertFalse(repo.settings.value.osdCompactMode)
        assertEquals(LanguageItem.AUTO_CODE, repo.settings.value.sourceLanguageCode)
        assertEquals("tr", repo.settings.value.targetLanguageCode)

        repo.cycleThemeMode()
        assertEquals(AppThemeMode.LIGHT, repo.settings.value.themeMode)
        repo.cycleThemeMode()
        assertEquals(AppThemeMode.DARK, repo.settings.value.themeMode)
        repo.cycleThemeMode()
        assertEquals(AppThemeMode.SYSTEM, repo.settings.value.themeMode)

        repo.updateThemeMode(AppThemeMode.DARK)
        repo.updateHapticFeedbackEnabled(false)
        repo.updateOnboardingCompleted(true)
        repo.updateAppLanguageCode("de")
        repo.updateApiKey("  AIzaTestKey999  ")
        repo.updateSourceLanguageCode("en")
        repo.updateTargetLanguageCode("ja")
        repo.updateOsdEnabled(false)
        repo.updateOsdOpacityPercent(72)
        repo.updateOsdFontSize(OsdFontSizePreset.LARGE)
        repo.updateOsdShowOriginalText(false)
        repo.updateOsdCompactMode(true)
        repo.updateDubVolumePercent(145)
        repo.updateEchoTargetLanguage(true)
        repo.updateSilenceDetection(true)
        repo.updateDuckOriginalAudio(true)
        repo.updateBluetoothDubOnlyRouting(false)

        val current = repo.settings.value
        assertEquals(AppThemeMode.DARK, current.themeMode)
        assertFalse(current.hapticFeedbackEnabled)
        assertTrue(current.onboardingCompleted)
        assertEquals("de", current.appLanguageCode)
        assertEquals("AIzaTestKey999", current.apiKey)
        assertEquals("en", current.sourceLanguageCode)
        assertEquals("ja", current.targetLanguageCode)
        assertFalse(current.osdEnabled)
        assertEquals(72, current.osdOpacityPercent)
        assertEquals(OsdFontSizePreset.LARGE, current.osdFontSize)
        assertFalse(current.osdShowOriginalText)
        assertTrue(current.osdCompactMode)
        assertEquals(145, current.dubVolumePercent)
        assertTrue(current.echoTargetLanguage)
        assertTrue(current.silenceDetectionEnabled)
        assertTrue(current.duckOriginalAudio)
        assertFalse(current.bluetoothDubOnlyRouting)

        repo.swapDubbingLanguages()
        assertEquals("ja", repo.settings.value.sourceLanguageCode)
        assertEquals("en", repo.settings.value.targetLanguageCode)
    }

    @Test
    fun osdOverlayManager_supportsHideAndRestoreCollapseStateCleanly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = DubbingOsdOverlayManager(
            context = context,
            onTogglePause = {},
            onStopDubbing = {}
        )
        assertFalse(manager.isCollapsed())
        manager.setCollapsed(true)
        assertTrue(manager.isCollapsed())
        manager.setCollapsed(false)
        assertFalse(manager.isCollapsed())
        manager.dismiss()
        assertFalse(manager.isCollapsed())
    }

    @Test
    fun hapticFeedbackHelper_respectsEnabledFlagCooldownAndNeverThrows() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        HapticFeedbackHelper.resetTurnCooldown()
        assertFalse(HapticFeedbackHelper.vibrateDubbingStart(context, enabled = false))
        assertFalse(HapticFeedbackHelper.vibrateDubbingResume(context, enabled = false))
        assertFalse(HapticFeedbackHelper.vibrateDubbingStop(context, enabled = false))
        assertFalse(HapticFeedbackHelper.vibrateAiVoiceTurnStart(context, enabled = false, nowMs = 2000L))
        assertFalse(HapticFeedbackHelper.vibrateSoftTap(context, enabled = false))

        HapticFeedbackHelper.vibrateDubbingStart(context, enabled = true)
        HapticFeedbackHelper.vibrateDubbingResume(context, enabled = true)
        HapticFeedbackHelper.vibrateDubbingStop(context, enabled = true)
        HapticFeedbackHelper.vibrateSoftTap(context, enabled = true)

        // Cooldown prevents rapid buzz bursts within 1200ms
        HapticFeedbackHelper.resetTurnCooldown()
        HapticFeedbackHelper.vibrateAiVoiceTurnStart(context, enabled = true, nowMs = 5000L)
        assertFalse(HapticFeedbackHelper.vibrateAiVoiceTurnStart(context, enabled = true, nowMs = 5500L))
    }

    @Test
    fun dubbingController_consumesMediaProjectionConsentStrictlyOnce() {
        val controller = DubbingController()
        val fakeIntent = Intent("com.example.FAKE_PROJECTION")

        controller.storeOneTimeProjectionConsent(-1, fakeIntent)
        val firstConsume = controller.consumeOneTimeProjectionConsent()
        assertNotNull(firstConsume)
        assertEquals(-1, firstConsume?.first)

        val secondConsume = controller.consumeOneTimeProjectionConsent()
        assertNull(secondConsume)
    }

    @Test
    fun dubbingNotificationHelper_buildsValidNotificationWithDynamicTargetLanguage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notif = DubbingNotificationHelper.buildNotification(
            context = context,
            statusText = "Canlı Dublaj Akıyor",
            detectedLanguageText = "EN (İngilizce)",
            targetLanguageLabel = "Deutsch",
            isPaused = false
        )
        assertNotNull(notif)
        assertEquals(2, notif.actions.size)
        val contentText = notif.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""
        assertTrue(contentText.contains("Deutsch"))
    }

    @Test
    fun parseModelsPage_and_settingsRepository_filterClutterAndAutoUpgradeToLatestDubbingModel() {
        val sampleJson = """
            {
              "models": [
                {
                  "name": "models/gemini-2.5-flash-native-audio-preview-12-2025",
                  "displayName": "Gemini 2.5 Flash Native Audio",
                  "supportedGenerationMethods": ["bidiGenerateContent"]
                },
                {
                  "name": "models/gemini-2.5-pro",
                  "displayName": "Gemini 2.5 Pro",
                  "supportedGenerationMethods": ["generateContent"]
                },
                {
                  "name": "models/text-embedding-004",
                  "displayName": "Text Embedding",
                  "supportedGenerationMethods": ["embedContent"]
                },
                {
                  "name": "models/gemini-2.5-flash-preview-tts",
                  "displayName": "Gemini 2.5 Flash TTS",
                  "supportedGenerationMethods": ["generateContent"]
                }
              ],
              "nextPageToken": "page_2_token"
            }
        """.trimIndent()

        val (models, nextToken) = LiveSessionClient.parseModelsPage(sampleJson)
        assertEquals(1, models.size)
        assertEquals("page_2_token", nextToken)
        assertEquals("gemini-2.5-flash-native-audio-preview-12-2025", models[0].id)
        assertTrue(models[0].supportsBidi)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = SettingsRepository(context)
        repo.updateAutoSelectLatestModel(true)

        val fetchedWithClutter = listOf(
            GeminiModelInfo(
                id = "gemini-4.0-live-translate-preview",
                displayName = "Gemini 4.0 Live Translate",
                supportsBidi = true,
                isRecommendedTranslate = true
            ),
            GeminiModelInfo(
                id = "gemini-2.5-pro",
                displayName = "Gemini 2.5 Pro",
                supportsBidi = false,
                isRecommendedTranslate = false
            )
        )
        repo.saveFetchedModels(fetchedWithClutter)

        assertFalse(repo.cachedModels.value.any { it.id == "gemini-2.5-pro" })
        assertEquals("gemini-4.0-live-translate-preview", repo.settings.value.selectedModel)
    }
}
