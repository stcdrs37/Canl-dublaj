package com.example.service

import android.content.Intent
import com.example.audio.AudioMetrics
import com.example.live.LiveSessionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DubbingSessionUiState(
    val isServiceRunning: Boolean = false,
    val isPaused: Boolean = false,
    val liveState: LiveSessionState = LiveSessionState.Idle,
    val audioMetrics: AudioMetrics = AudioMetrics(),
    val detectedSourceLanguage: String = "Otomatik algılanıyor",
    val currentInputLiveText: String = "",
    val currentOutputLiveText: String = "",
    val firstAudioLatencyMs: Long? = null,
    val activeDurationSec: Long = 0L,
    val bannerMessage: String? = null
)

/**
 * Central state bridge between DubbingService and Compose UI.
 * Also holds the one-time MediaProjection consent result across the start boundary
 * and clears it immediately once consumed so it is never reused across sessions.
 */
class DubbingController {
    private val _uiState = MutableStateFlow(DubbingSessionUiState())
    val uiState: StateFlow<DubbingSessionUiState> = _uiState.asStateFlow()

    @Volatile
    private var pendingProjectionResultCode: Int? = null

    @Volatile
    private var pendingProjectionDataIntent: Intent? = null

    @Synchronized
    fun storeOneTimeProjectionConsent(resultCode: Int, data: Intent) {
        pendingProjectionResultCode = resultCode
        pendingProjectionDataIntent = data
    }

    /**
     * Consumes and immediately clears the stored MediaProjection consent so a new
     * system consent dialog is always requested on subsequent sessions.
     */
    @Synchronized
    fun consumeOneTimeProjectionConsent(): Pair<Int, Intent>? {
        val code = pendingProjectionResultCode
        val data = pendingProjectionDataIntent
        pendingProjectionResultCode = null
        pendingProjectionDataIntent = null
        return if (code != null && data != null) {
            code to data
        } else {
            null
        }
    }

    @Synchronized
    fun clearProjectionConsent() {
        pendingProjectionResultCode = null
        pendingProjectionDataIntent = null
    }

    fun updateState(transform: (DubbingSessionUiState) -> DubbingSessionUiState) {
        _uiState.value = transform(_uiState.value)
    }

    fun dismissBanner() {
        updateState { it.copy(bannerMessage = null) }
    }
}
