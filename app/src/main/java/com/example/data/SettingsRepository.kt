package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import com.example.live.GeminiModelInfo
import com.example.live.LiveSessionClient
import com.example.security.ApiKeyVault
import com.example.security.CyberSecurityReport
import com.example.security.SecurityShield
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

data class AppSettings(
    // First-launch setup wizard & UI localization
    val onboardingCompleted: Boolean = false,
    val appLanguageCode: String = "tr",

    // Screen Dubbing Language preferences (Voice tone is 100% Automatic AI Voice Cloning)
    val sourceLanguageCode: String = LanguageItem.AUTO_CODE,
    val targetLanguageCode: String = "tr",
    val recentLanguageCodes: List<String> = listOf("tr", "en", "de", "fr", "es", "ja"),

    // Gemini Live API & Always-Latest Dubbing Model preferences (stored encrypted via AES-256-GCM Vault)
    val apiKey: String = "",
    val selectedModel: String = LiveSessionClient.DEFAULT_TRANSLATE_MODEL,
    val autoSelectLatestModel: Boolean = true,

    // Floating Premium Liquid Glass OSD preferences
    val osdEnabled: Boolean = true,
    val osdOpacityPercent: Int = 84,
    val osdFontSize: OsdFontSizePreset = OsdFontSizePreset.MEDIUM,
    val osdShowOriginalText: Boolean = true,
    val osdCompactMode: Boolean = false,

    // Audio, Bluetooth Dub-Only Routing, Haptics & Zero-Word-Drop streaming preferences
    val echoTargetLanguage: Boolean = false,
    // Dubbing voice starts at 60% by default
    val dubVolumePercent: Int = SettingsRepository.DEFAULT_DUB_VOLUME_PERCENT,
    // Automatically drops original media volume immediately to 10% when translation starts
    val duckOriginalAudio: Boolean = true,
    // When Bluetooth headphones/speakers are connected, routes ONLY dubbing voice to Bluetooth
    val bluetoothDubOnlyRouting: Boolean = true,
    // Default false = Lossless continuous stream so no word/syllable is ever swallowed
    val silenceDetectionEnabled: Boolean = false,
    val autoReconnect: Boolean = true,
    val hapticFeedbackEnabled: Boolean = true,

    // Automatic System Theme (SYSTEM by default, also supports LIGHT / DARK)
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM
)

class SettingsRepository(context: Context) {
    companion object {
        const val DEFAULT_DUB_VOLUME_PERCENT = 60
        const val ORIGINAL_DUCK_TARGET_PERCENT = 10

        private const val PREFS_NAME = "livedub_prefs_v4"
        private const val KEY_ONBOARDING_DONE = "onboarding_completed"
        private const val KEY_APP_LANG = "app_language_code"
        private const val KEY_SOURCE_LANG = "source_language_code"
        private const val KEY_TARGET_LANG = "target_language_code"
        private const val KEY_RECENT_LANGS = "recent_language_codes"

        private const val KEY_API_KEY_LEGACY = "api_key"
        private const val KEY_API_KEY_VAULT_V2 = "api_key_vault_v2"
        private const val KEY_MODEL = "selected_model"
        private const val KEY_AUTO_LATEST_MODEL = "auto_select_latest_model"
        private const val KEY_CACHED_MODELS_JSON = "cached_models_json"

        private const val KEY_OSD_ENABLED = "osd_enabled"
        private const val KEY_OSD_OPACITY = "osd_opacity_percent"
        private const val KEY_OSD_FONT_SIZE = "osd_font_size"
        private const val KEY_OSD_SHOW_ORIGINAL = "osd_show_original"
        private const val KEY_OSD_COMPACT = "osd_compact_mode"

        private const val KEY_ECHO_TARGET = "echo_target_language"
        private const val KEY_DUB_VOLUME = "dub_volume_percent"
        private const val KEY_DUCK_ORIGINAL = "duck_original_audio"
        private const val KEY_BT_DUB_ONLY = "bluetooth_dub_only_routing"
        private const val KEY_SILENCE_DETECTION = "silence_detection_enabled"
        private const val KEY_AUTO_RECONNECT = "auto_reconnect"
        private const val KEY_HAPTIC_FEEDBACK = "haptic_feedback_enabled"
        private const val KEY_THEME_MODE = "theme_mode"

        private const val KEY_PENDING_RESTORE_MUSIC_VOL = "pending_restore_music_vol"

        const val DEFAULT_MODEL = LiveSessionClient.DEFAULT_TRANSLATE_MODEL

        /**
         * Computes the target STREAM_MUSIC step when dropping original audio to 10%
         * (or capture-safe floor when isolating Bluetooth dub-only playback).
         * Keeps at least step 1 when `currentVol > 0` so Android AudioPlaybackCapture
         * continues receiving live PCM frames cleanly while original media drops immediately to ~10%.
         */
        fun computeDuckedMusicVolume(
            currentVol: Int,
            maxVol: Int,
            bluetoothDubOnlyActive: Boolean = false
        ): Int {
            if (currentVol <= 0 || maxVol <= 0) return 0
            if (bluetoothDubOnlyActive) {
                // Capture-safe minimal floor so original audio stays suppressed on BT while PCM capture remains alive
                return minOf(currentVol, 1)
            }
            val tenPercentStep = (maxVol * (ORIGINAL_DUCK_TARGET_PERCENT / 100f)).roundToInt().coerceAtLeast(1)
            return minOf(currentVol, tenPercentStep)
        }

        /**
         * Zero-clutter fallback catalog containing ONLY real, verified live audio dubbing models.
         */
        val DEFAULT_MODELS = listOf(
            GeminiModelInfo(
                id = "gemini-2.5-flash-native-audio-preview-12-2025",
                displayName = "Gemini 2.5 Flash Native Audio • Instant Zero-Shot Clone",
                supportsBidi = true,
                isRecommendedTranslate = true
            ),
            GeminiModelInfo(
                id = "gemini-2.5-flash-native-audio-preview-09-2025",
                displayName = "Gemini 2.5 Flash Native Audio (09-2025)",
                supportsBidi = true,
                isRecommendedTranslate = false
            ),
            GeminiModelInfo(
                id = "gemini-2.0-flash-live-001",
                displayName = "Gemini 2.0 Flash Live Audio",
                supportsBidi = true,
                isRecommendedTranslate = false
            )
        )
    }

    private val appContext: Context = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val apiKeyVault = ApiKeyVault(appContext)

    val isHardwareVaultActive: Boolean
        get() = apiKeyVault.isHardwareKeystoreActive

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _cachedModels = MutableStateFlow(loadCachedModels(_settings.value.selectedModel))
    val cachedModels: StateFlow<List<GeminiModelInfo>> = _cachedModels.asStateFlow()

    fun getCyberSecurityReport(): CyberSecurityReport =
        SecurityShield.inspectDeviceSecurity(appContext, apiKeyVault.isHardwareKeystoreActive)

    /**
     * Verifies that no plaintext API key exists in SharedPreferences and any stored key is encrypted.
     */
    fun isApiKeyEncryptedOnDisk(): Boolean {
        val hasPlaintext = prefs.contains(KEY_API_KEY_LEGACY)
        if (hasPlaintext) return false
        val rawVault = prefs.getString(KEY_API_KEY_VAULT_V2, null)
        return rawVault.isNullOrBlank() || apiKeyVault.isEncryptedBlob(rawVault)
    }

    private fun loadEncryptedApiKey(): String {
        val vaultBlob = prefs.getString(KEY_API_KEY_VAULT_V2, null)
        if (!vaultBlob.isNullOrBlank() && apiKeyVault.isEncryptedBlob(vaultBlob)) {
            if (prefs.contains(KEY_API_KEY_LEGACY)) {
                prefs.edit().remove(KEY_API_KEY_LEGACY).commit()
            }
            return apiKeyVault.decrypt(vaultBlob)
        }
        // Automatic zero-plaintext migration from legacy SharedPreferences
        val legacyPlain = prefs.getString(KEY_API_KEY_LEGACY, null)?.trim().orEmpty()
        if (legacyPlain.isNotEmpty()) {
            val encrypted = apiKeyVault.encrypt(legacyPlain)
            prefs.edit()
                .putString(KEY_API_KEY_VAULT_V2, encrypted)
                .remove(KEY_API_KEY_LEGACY)
                .commit()
            return legacyPlain
        }
        return ""
    }

    private fun loadSettings(): AppSettings {
        val rawModel = prefs.getString(KEY_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        val cleanModel = rawModel.trim().removePrefix("models/").ifEmpty { DEFAULT_MODEL }
        val recentRaw = prefs.getString(KEY_RECENT_LANGS, "tr,en,de,fr,es,ja") ?: "tr,en,de,fr,es,ja"
        val recents = recentRaw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(6)

        return AppSettings(
            onboardingCompleted = prefs.getBoolean(KEY_ONBOARDING_DONE, false),
            appLanguageCode = (prefs.getString(KEY_APP_LANG, "tr") ?: "tr").trim().lowercase().ifEmpty { "tr" },
            sourceLanguageCode = (prefs.getString(KEY_SOURCE_LANG, LanguageItem.AUTO_CODE) ?: LanguageItem.AUTO_CODE)
                .trim().lowercase().ifEmpty { LanguageItem.AUTO_CODE },
            targetLanguageCode = (prefs.getString(KEY_TARGET_LANG, "tr") ?: "tr")
                .trim().lowercase().takeUnless { it.isEmpty() || it == LanguageItem.AUTO_CODE } ?: "tr",
            recentLanguageCodes = recents.ifEmpty { listOf("tr", "en", "de", "fr", "es", "ja") },
            apiKey = loadEncryptedApiKey(),
            selectedModel = cleanModel,
            autoSelectLatestModel = prefs.getBoolean(KEY_AUTO_LATEST_MODEL, true),
            osdEnabled = prefs.getBoolean(KEY_OSD_ENABLED, true),
            osdOpacityPercent = prefs.getInt(KEY_OSD_OPACITY, 84).coerceIn(50, 100),
            osdFontSize = OsdFontSizePreset.fromIdOrDefault(prefs.getString(KEY_OSD_FONT_SIZE, null)),
            osdShowOriginalText = prefs.getBoolean(KEY_OSD_SHOW_ORIGINAL, true),
            osdCompactMode = prefs.getBoolean(KEY_OSD_COMPACT, false),
            echoTargetLanguage = prefs.getBoolean(KEY_ECHO_TARGET, false),
            dubVolumePercent = prefs.getInt(KEY_DUB_VOLUME, DEFAULT_DUB_VOLUME_PERCENT).coerceIn(0, 200),
            duckOriginalAudio = prefs.getBoolean(KEY_DUCK_ORIGINAL, true),
            bluetoothDubOnlyRouting = prefs.getBoolean(KEY_BT_DUB_ONLY, true),
            silenceDetectionEnabled = prefs.getBoolean(KEY_SILENCE_DETECTION, false),
            autoReconnect = prefs.getBoolean(KEY_AUTO_RECONNECT, true),
            hapticFeedbackEnabled = prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true),
            themeMode = AppThemeMode.fromIdOrDefault(prefs.getString(KEY_THEME_MODE, null))
        )
    }

    private fun loadCachedModels(selectedModel: String): List<GeminiModelInfo> {
        val raw = prefs.getString(KEY_CACHED_MODELS_JSON, null)
        val parsed = mutableListOf<GeminiModelInfo>()
        if (!raw.isNullOrBlank()) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val id = obj.optString("id", "").removePrefix("models/").trim()
                    if (id.isEmpty()) continue
                    val supportsBidi = obj.optBoolean("supportsBidi", true)
                    if (!LiveSessionClient.isLiveDubbingCapableModel(id, supportsBidi)) continue
                    parsed.add(
                        GeminiModelInfo(
                            id = id,
                            displayName = obj.optString("displayName", id).ifBlank { id },
                            supportsBidi = true,
                            isRecommendedTranslate = obj.optBoolean("isRecommendedTranslate", false)
                        )
                    )
                }
            } catch (_: Throwable) {
            }
        }
        val baseList = if (parsed.isNotEmpty()) parsed else DEFAULT_MODELS
        return ensureModelIncluded(baseList, selectedModel)
    }

    private fun ensureModelIncluded(list: List<GeminiModelInfo>, modelId: String): List<GeminiModelInfo> {
        val clean = modelId.trim().removePrefix("models/")
        if (clean.isEmpty() || list.any { it.id == clean }) return list
        if (!LiveSessionClient.isLiveDubbingCapableModel(clean, supportsBidiMethod = false)) return list
        return list + GeminiModelInfo(
            id = clean,
            displayName = clean,
            supportsBidi = true,
            isRecommendedTranslate = clean.contains("translate", ignoreCase = true)
        )
    }

    fun saveFetchedModels(models: List<GeminiModelInfo>) {
        val dubbingOnly = models.filter {
            LiveSessionClient.isLiveDubbingCapableModel(it.id, it.supportsBidi)
        }
        val effective = if (dubbingOnly.isNotEmpty()) dubbingOnly else DEFAULT_MODELS

        var nextSelected = _settings.value.selectedModel
        if (_settings.value.autoSelectLatestModel && effective.isNotEmpty()) {
            val newest = effective.first().id
            if (newest != nextSelected) {
                nextSelected = newest
                prefs.edit().putString(KEY_MODEL, newest).apply()
                _settings.value = _settings.value.copy(selectedModel = newest)
            }
        } else if (effective.none { it.id == nextSelected }) {
            // Avoid keeping an unavailable model that causes slow 404 fallback delays
            val fallback = effective.first().id
            nextSelected = fallback
            prefs.edit().putString(KEY_MODEL, fallback).apply()
            _settings.value = _settings.value.copy(selectedModel = fallback)
        }

        val withSelected = ensureModelIncluded(effective, nextSelected)
        val arr = JSONArray()
        for (m in withSelected) {
            arr.put(
                JSONObject().apply {
                    put("id", m.id)
                    put("displayName", m.displayName)
                    put("supportsBidi", m.supportsBidi)
                    put("isRecommendedTranslate", m.isRecommendedTranslate)
                }
            )
        }
        prefs.edit().putString(KEY_CACHED_MODELS_JSON, arr.toString()).apply()
        _cachedModels.value = withSelected
    }

    fun updateOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, completed).apply()
        _settings.value = _settings.value.copy(onboardingCompleted = completed)
    }

    fun updateAppLanguageCode(code: String) {
        val clean = code.trim().lowercase().ifEmpty { "tr" }
        prefs.edit().putString(KEY_APP_LANG, clean).apply()
        _settings.value = _settings.value.copy(appLanguageCode = clean)
    }

    fun updateSourceLanguageCode(code: String) {
        val clean = code.trim().lowercase().ifEmpty { LanguageItem.AUTO_CODE }
        val cur = _settings.value
        val nextTarget = if (clean != LanguageItem.AUTO_CODE && clean == cur.targetLanguageCode) {
            if (cur.sourceLanguageCode != LanguageItem.AUTO_CODE && cur.sourceLanguageCode != clean) {
                cur.sourceLanguageCode
            } else {
                if (clean == "tr") "en" else "tr"
            }
        } else {
            cur.targetLanguageCode
        }
        prefs.edit()
            .putString(KEY_SOURCE_LANG, clean)
            .putString(KEY_TARGET_LANG, nextTarget)
            .apply()
        val updatedRecents = pushRecentLanguage(clean)
        _settings.value = cur.copy(
            sourceLanguageCode = clean,
            targetLanguageCode = nextTarget,
            recentLanguageCodes = updatedRecents
        )
    }

    fun updateTargetLanguageCode(code: String) {
        val clean = code.trim().lowercase().takeUnless { it.isEmpty() || it == LanguageItem.AUTO_CODE } ?: "tr"
        val cur = _settings.value
        val nextSource = if (clean == cur.sourceLanguageCode) {
            if (cur.targetLanguageCode != clean) {
                cur.targetLanguageCode
            } else {
                LanguageItem.AUTO_CODE
            }
        } else {
            cur.sourceLanguageCode
        }
        prefs.edit()
            .putString(KEY_SOURCE_LANG, nextSource)
            .putString(KEY_TARGET_LANG, clean)
            .apply()
        val updatedRecents = pushRecentLanguage(clean)
        _settings.value = cur.copy(
            sourceLanguageCode = nextSource,
            targetLanguageCode = clean,
            recentLanguageCodes = updatedRecents
        )
    }

    fun swapDubbingLanguages() {
        val cur = _settings.value
        val nextSource = cur.targetLanguageCode
        val nextTarget = if (cur.sourceLanguageCode == LanguageItem.AUTO_CODE) {
            if (cur.targetLanguageCode == "en") "tr" else "en"
        } else {
            cur.sourceLanguageCode
        }
        prefs.edit()
            .putString(KEY_SOURCE_LANG, nextSource)
            .putString(KEY_TARGET_LANG, nextTarget)
            .apply()
        _settings.value = cur.copy(
            sourceLanguageCode = nextSource,
            targetLanguageCode = nextTarget
        )
    }

    /**
     * Encrypts and stores the API key inside the AES-256-GCM Cyber Vault.
     * Plaintext is NEVER saved to SharedPreferences.
     */
    fun updateApiKey(apiKey: String) {
        val trimmed = apiKey.trim()
        val editor = prefs.edit().remove(KEY_API_KEY_LEGACY)
        if (trimmed.isEmpty()) {
            editor.remove(KEY_API_KEY_VAULT_V2)
        } else {
            editor.putString(KEY_API_KEY_VAULT_V2, apiKeyVault.encrypt(trimmed))
        }
        editor.apply()
        _settings.value = _settings.value.copy(apiKey = trimmed)
    }

    fun updateSelectedModel(model: String, userManualSelection: Boolean = false) {
        val clean = model.trim().removePrefix("models/").ifEmpty { DEFAULT_MODEL }
        val editor = prefs.edit().putString(KEY_MODEL, clean)
        var nextAuto = _settings.value.autoSelectLatestModel
        if (userManualSelection && _cachedModels.value.firstOrNull()?.id != clean) {
            nextAuto = false
            editor.putBoolean(KEY_AUTO_LATEST_MODEL, false)
        }
        editor.apply()
        _settings.value = _settings.value.copy(
            selectedModel = clean,
            autoSelectLatestModel = nextAuto
        )
        val updatedList = ensureModelIncluded(_cachedModels.value, clean)
        if (updatedList.size != _cachedModels.value.size) {
            saveFetchedModels(updatedList)
        }
    }

    fun updateAutoSelectLatestModel(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_LATEST_MODEL, enabled).apply()
        val newest = if (enabled) _cachedModels.value.firstOrNull()?.id else null
        if (newest != null && newest != _settings.value.selectedModel) {
            prefs.edit().putString(KEY_MODEL, newest).apply()
            _settings.value = _settings.value.copy(
                autoSelectLatestModel = enabled,
                selectedModel = newest
            )
        } else {
            _settings.value = _settings.value.copy(autoSelectLatestModel = enabled)
        }
    }

    // --- Premium Floating OSD Preferences ---

    fun updateOsdEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_OSD_ENABLED, enabled).apply()
        _settings.value = _settings.value.copy(osdEnabled = enabled)
    }

    fun updateOsdOpacityPercent(percent: Int) {
        val clamped = percent.coerceIn(50, 100)
        prefs.edit().putInt(KEY_OSD_OPACITY, clamped).apply()
        _settings.value = _settings.value.copy(osdOpacityPercent = clamped)
    }

    fun updateOsdFontSize(preset: OsdFontSizePreset) {
        prefs.edit().putString(KEY_OSD_FONT_SIZE, preset.id).apply()
        _settings.value = _settings.value.copy(osdFontSize = preset)
    }

    fun updateOsdShowOriginalText(show: Boolean) {
        prefs.edit().putBoolean(KEY_OSD_SHOW_ORIGINAL, show).apply()
        _settings.value = _settings.value.copy(osdShowOriginalText = show)
    }

    fun updateOsdCompactMode(compact: Boolean) {
        prefs.edit().putBoolean(KEY_OSD_COMPACT, compact).apply()
        _settings.value = _settings.value.copy(osdCompactMode = compact)
    }

    // --- Audio, Bluetooth Routing, Haptics & Streaming Engine Preferences ---

    fun updateEchoTargetLanguage(echo: Boolean) {
        prefs.edit().putBoolean(KEY_ECHO_TARGET, echo).apply()
        _settings.value = _settings.value.copy(echoTargetLanguage = echo)
    }

    fun updateDubVolumePercent(percent: Int) {
        val clamped = percent.coerceIn(0, 200)
        prefs.edit().putInt(KEY_DUB_VOLUME, clamped).apply()
        _settings.value = _settings.value.copy(dubVolumePercent = clamped)
    }

    fun updateDuckOriginalAudio(duck: Boolean) {
        prefs.edit().putBoolean(KEY_DUCK_ORIGINAL, duck).apply()
        _settings.value = _settings.value.copy(duckOriginalAudio = duck)
    }

    fun updateBluetoothDubOnlyRouting(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BT_DUB_ONLY, enabled).apply()
        _settings.value = _settings.value.copy(bluetoothDubOnlyRouting = enabled)
    }

    fun updateSilenceDetection(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SILENCE_DETECTION, enabled).apply()
        _settings.value = _settings.value.copy(silenceDetectionEnabled = enabled)
    }

    fun updateAutoReconnect(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_RECONNECT, enabled).apply()
        _settings.value = _settings.value.copy(autoReconnect = enabled)
    }

    fun updateHapticFeedbackEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTIC_FEEDBACK, enabled).apply()
        _settings.value = _settings.value.copy(hapticFeedbackEnabled = enabled)
    }

    fun updateThemeMode(mode: AppThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.id).apply()
        _settings.value = _settings.value.copy(themeMode = mode)
    }

    fun cycleThemeMode() {
        updateThemeMode(_settings.value.themeMode.next())
    }

    private fun pushRecentLanguage(code: String): List<String> {
        if (code == LanguageItem.AUTO_CODE || code.isBlank()) return _settings.value.recentLanguageCodes
        val updated = (listOf(code) + _settings.value.recentLanguageCodes.filter { it != code }).take(6)
        prefs.edit().putString(KEY_RECENT_LANGS, updated.joinToString(",")).apply()
        return updated
    }

    // --- Instant 10% Original Audio Ducking & Crash-Safe Hardware Volume Restore ---

    @Synchronized
    fun applyOriginalAudioDuck10Percent(
        audioManager: AudioManager,
        bluetoothDubOnlyActive: Boolean = false
    ): Boolean {
        return try {
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (max <= 0) return false
            // Save original volume once before attenuating so stop/pause/crash always restores it exactly
            if (!prefs.contains(KEY_PENDING_RESTORE_MUSIC_VOL)) {
                prefs.edit().putInt(KEY_PENDING_RESTORE_MUSIC_VOL, current).commit()
            }
            val savedOriginal = prefs.getInt(KEY_PENDING_RESTORE_MUSIC_VOL, current)
            val target = computeDuckedMusicVolume(savedOriginal, max, bluetoothDubOnlyActive)
            if (current != target) {
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    @Synchronized
    fun restorePendingMusicVolumeIfNeeded(audioManager: AudioManager) {
        if (prefs.contains(KEY_PENDING_RESTORE_MUSIC_VOL)) {
            val orig = prefs.getInt(KEY_PENDING_RESTORE_MUSIC_VOL, -1)
            if (orig >= 0) {
                try {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, orig, 0)
                } catch (_: Throwable) {
                }
            }
            prefs.edit().remove(KEY_PENDING_RESTORE_MUSIC_VOL).commit()
        }
    }

    @Synchronized
    fun restoreAllPendingVolumes(audioManager: AudioManager) {
        restorePendingMusicVolumeIfNeeded(audioManager)
    }
}
