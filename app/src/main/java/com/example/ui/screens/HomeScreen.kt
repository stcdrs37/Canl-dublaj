package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AppSettings
import com.example.data.LanguageItem
import com.example.i18n.LocalAppStrings
import com.example.live.LiveSessionState
import com.example.service.DubbingSessionUiState
import com.example.ui.components.AiVoiceCloneBannerStrip
import com.example.ui.components.DualAudioWaveVisualizer
import com.example.ui.components.LanguagePickerBottomSheet
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGlassIconOrb
import com.example.ui.components.LiquidGlassPillBadge
import com.example.ui.components.LiquidGlassPrimaryButton
import com.example.ui.components.OsdFloatingIcon
import com.example.ui.components.SwapArrowsIcon
import com.example.ui.components.ThemeModeIcon
import com.example.ui.components.liquidPressBounce
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.EmeraldOk
import com.example.ui.theme.SolarGold
import com.example.util.HapticFeedbackHelper

/**
 * AeroGlass 60 — Compact Dubbing-Only Studio Home Screen.
 * - Source Language (Auto Detect or any of 34 languages) -> Target Dubbing Language
 * - 100% Automatic AI Zero-Shot Speaker Voice Cloning
 * - Quick Floating Liquid Glass OSD toggle + Automatic System/Light/Dark theme cycle orb
 * - Media-stream non-pausing dubbing + tactile vibration feedback on start
 */
@Composable
fun HomeScreen(
    uiState: DubbingSessionUiState,
    settings: AppSettings,
    canDrawOverlay: Boolean,
    onSelectSourceLanguage: (LanguageItem) -> Unit,
    onSelectTargetLanguage: (LanguageItem) -> Unit,
    onSwapLanguages: () -> Unit,
    onToggleOsdQuick: () -> Unit,
    onCycleThemeMode: () -> Unit,
    onStartDubbing: () -> Unit,
    onTogglePause: () -> Unit,
    onStopDubbing: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissBanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    val hostView = LocalView.current
    val isRunning = uiState.isServiceRunning
    val isStreaming = isRunning && uiState.liveState is LiveSessionState.Streaming
    val isTurkishUi = strings.code == "tr"
    val isOsdReadyAndActive = settings.osdEnabled && canDrawOverlay

    var pickerForSource by remember { mutableStateOf<Boolean?>(null) }

    val sourceItem = LanguageItem.byCode(settings.sourceLanguageCode, allowAuto = true)
    val targetItem = LanguageItem.byCode(settings.targetLanguageCode, allowAuto = false)

    val sourceDisplayLabel = if (sourceItem.isAuto) {
        strings.autoDetectTitle
    } else if (isTurkishUi) {
        sourceItem.nameTr
    } else {
        sourceItem.nativeName
    }
    val targetDisplayLabel = if (isTurkishUi) targetItem.nameTr else targetItem.nativeName

    val statusColor = when {
        uiState.liveState is LiveSessionState.Error -> CrimsonRed
        uiState.isPaused -> SolarGold
        isStreaming -> EmeraldOk
        isRunning -> cs.primary
        else -> cs.onSurfaceVariant
    }

    val statusLabel = when {
        uiState.isPaused -> strings.statusPaused
        !isRunning -> strings.statusReady
        uiState.liveState is LiveSessionState.Connecting -> strings.statusConnecting
        uiState.liveState is LiveSessionState.Streaming -> strings.statusStreaming
        uiState.liveState is LiveSessionState.Renewing -> strings.statusRenewing
        uiState.liveState is LiveSessionState.Stopped -> strings.statusStopped
        uiState.liveState is LiveSessionState.Error -> "${strings.statusErrorPrefix}: ${uiState.liveState.message}"
        else -> uiState.liveState.labelTr()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // 1. Compact Liquid Glass Header with New Screen Dubbing Logo, OSD, Theme & Settings Orbs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_screen_dubbing_logo),
                            contentDescription = "Screen Dubbing",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .border(1.dp, cs.primary.copy(alpha = 0.45f), RoundedCornerShape(11.dp))
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Screen Dubbing",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = cs.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                LiquidGlassPillBadge(
                                    text = statusLabel,
                                    accentColor = statusColor,
                                    leadingDot = true
                                )
                            }
                            Text(
                                text = strings.appSubtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LiquidGlassIconOrb(
                            onClick = {
                                HapticFeedbackHelper.vibrateSoftTap(context, settings.hapticFeedbackEnabled, hostView)
                                onToggleOsdQuick()
                            },
                            accentColor = if (isOsdReadyAndActive) EmeraldOk else cs.primary,
                            highlighted = isOsdReadyAndActive,
                            modifier = Modifier
                                .semantics { contentDescription = strings.osdQuickToggleDesc }
                                .testTag("toggle_osd_button")
                        ) {
                            OsdFloatingIcon(
                                tint = if (isOsdReadyAndActive) EmeraldOk else cs.onSurface,
                                activeDotColor = if (isOsdReadyAndActive) EmeraldOk else null,
                                size = 19.dp
                            )
                        }

                        LiquidGlassIconOrb(
                            onClick = {
                                HapticFeedbackHelper.vibrateSoftTap(context, settings.hapticFeedbackEnabled, hostView)
                                onCycleThemeMode()
                            },
                            modifier = Modifier
                                .semantics { contentDescription = "${strings.darkModeToggleDesc}: ${strings.themeModeLabel(settings.themeMode)}" }
                                .testTag("toggle_theme_button")
                        ) {
                            ThemeModeIcon(
                                themeMode = settings.themeMode,
                                tint = cs.onSurface,
                                size = 19.dp
                            )
                        }

                        LiquidGlassIconOrb(
                            onClick = {
                                HapticFeedbackHelper.vibrateSoftTap(context, settings.hapticFeedbackEnabled, hostView)
                                onOpenSettings()
                            },
                            modifier = Modifier.testTag("open_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = strings.settingsLabel,
                                tint = cs.onSurface,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                // 2. Status / Warning Banner
                val missingKey = settings.apiKey.isBlank()
                AnimatedVisibility(visible = uiState.bannerMessage != null || missingKey || uiState.audioMetrics.drmSilenceWarning) {
                    val bannerText = when {
                        uiState.bannerMessage != null -> uiState.bannerMessage
                        uiState.audioMetrics.drmSilenceWarning -> strings.drmWarningBanner
                        else -> strings.missingApiKeyBanner
                    }
                    val isError = uiState.bannerMessage != null && !uiState.audioMetrics.drmSilenceWarning
                    val accent = if (isError) CrimsonRed else SolarGold

                    LiquidGlassCard(
                        accentColor = accent,
                        cornerRadius = 20.dp,
                        contentPadding = 12.dp,
                        highlighted = true
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = strings.warningDesc,
                                tint = accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = bannerText,
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurface,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            if (uiState.bannerMessage != null) {
                                IconButton(
                                    onClick = onDismissBanner,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = strings.closeDesc,
                                        tint = cs.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else if (missingKey) {
                                Text(
                                    text = "${strings.settingsLabel} ›",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = cs.primary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(onClick = onOpenSettings)
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                // 3. Compact Source (Auto/Any) -> Target Dubbing Language Selector Capsule
                LiquidGlassCard(
                    accentColor = cs.primary,
                    cornerRadius = 24.dp,
                    contentPadding = 12.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LanguageCapsuleButton(
                            label = strings.sourceLangLabel,
                            value = sourceDisplayLabel,
                            accentColor = cs.primary,
                            onClick = { pickerForSource = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dub_source_language_button")
                        )

                        LiquidGlassIconOrb(
                            onClick = {
                                HapticFeedbackHelper.vibrateSoftTap(context, settings.hapticFeedbackEnabled, hostView)
                                onSwapLanguages()
                            },
                            accentColor = cs.secondary,
                            modifier = Modifier
                                .semantics { contentDescription = strings.swapLanguagesDesc }
                                .testTag("dub_swap_languages_button")
                        ) {
                            SwapArrowsIcon(
                                tint = cs.primary,
                                size = 18.dp
                            )
                        }

                        LanguageCapsuleButton(
                            label = strings.targetLangLabel,
                            value = targetDisplayLabel,
                            accentColor = cs.secondary,
                            onClick = { pickerForSource = false },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("dub_target_language_button")
                        )
                    }
                }

                // 4. 100% Automatic AI Zero-Shot Speaker Voice Cloning Strip
                AiVoiceCloneBannerStrip(
                    isLosslessActive = !settings.silenceDetectionEnabled,
                    modifier = Modifier.testTag("home_ai_voice_clone_banner")
                )

                // 5. Compact 60fps Dual PCM Waveform & Telemetry Card
                LiquidGlassCard(
                    accentColor = cs.primary,
                    cornerRadius = 24.dp,
                    contentPadding = 13.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "$sourceDisplayLabel → $targetDisplayLabel",
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant
                            )
                            Text(
                                text = if (isRunning && sourceItem.isAuto) uiState.detectedSourceLanguage else "$sourceDisplayLabel • ${strings.aiVoiceCloneBadge}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = cs.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isOsdReadyAndActive) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = EmeraldOk.copy(alpha = 0.15f),
                                    border = BorderStroke(0.8.dp, EmeraldOk.copy(alpha = 0.45f))
                                ) {
                                    Text(
                                        text = "OSD",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = EmeraldOk,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (uiState.firstAudioLatencyMs != null) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = cs.secondaryContainer
                                ) {
                                    Text(
                                        text = "${uiState.firstAudioLatencyMs} ms",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = cs.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            if (isRunning) {
                                val min = uiState.activeDurationSec / 60
                                val sec = uiState.activeDurationSec % 60
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = cs.surfaceVariant
                                ) {
                                    Text(
                                        text = "%02d:%02d".format(min, sec),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = cs.onSurface,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    DualAudioWaveVisualizer(
                        captureWaveform = uiState.audioMetrics.captureWaveform,
                        playbackWaveform = uiState.audioMetrics.playbackWaveform,
                        isAudioDetected = uiState.audioMetrics.isAudioDetected,
                        isActive = isRunning,
                        captureLevel = uiState.audioMetrics.captureLevelNorm,
                        playbackLevel = uiState.audioMetrics.playbackLevelNorm,
                        isPaused = uiState.isPaused,
                        isSilencePaused = uiState.audioMetrics.isSilencePaused,
                        captureDb = uiState.audioMetrics.captureRmsDb
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${strings.modelPrefix}: ${settings.selectedModel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        if (uiState.audioMetrics.isBluetoothDubOnlyActive) {
                            LiquidGlassPillBadge(
                                text = "BT • ${uiState.audioMetrics.bluetoothDeviceName.ifBlank { strings.bluetoothDubOnlyActiveBadge }}",
                                accentColor = cs.primary,
                                leadingDot = true,
                                modifier = Modifier.testTag("home_bt_dub_badge")
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        } else if (uiState.audioMetrics.isOriginalDuckedTo10Percent) {
                            LiquidGlassPillBadge(
                                text = "%10 Orj",
                                accentColor = EmeraldOk
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = "${strings.volumePrefix}: %${settings.dubVolumePercent}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = cs.primary
                        )
                    }
                }

                // 6. Compact Live Dubbing Subtitles Card
                LiquidGlassCard(
                    accentColor = cs.secondary,
                    cornerRadius = 24.dp,
                    contentPadding = 14.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 90.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = strings.liveSubtitlesTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = cs.primary
                            )
                            if (uiState.audioMetrics.isSilencePaused) {
                                Text(
                                    text = strings.silenceWaiting,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SolarGold,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (uiState.currentInputLiveText.isNotBlank()) {
                            Text(
                                text = uiState.currentInputLiveText,
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )
                            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.55f))
                        }

                        Text(
                            text = uiState.currentOutputLiveText.ifBlank {
                                if (isRunning) strings.subtitlesActiveHint else strings.subtitlesIdleHint
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (uiState.currentOutputLiveText.isNotBlank()) FontWeight.Bold else FontWeight.Normal,
                            color = if (uiState.currentOutputLiveText.isNotBlank()) cs.onSurface else cs.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
            }

            // 7. Floating Liquid Glass Primary Action Dock
            if (!isRunning) {
                LiquidGlassPrimaryButton(
                    onClick = {
                        HapticFeedbackHelper.vibrateDubbingStart(context, settings.hapticFeedbackEnabled)
                        onStartDubbing()
                    },
                    accentStart = cs.primary,
                    accentEnd = cs.secondary,
                    height = 54.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 10.dp)
                        .testTag("start_dubbing_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.startDubbing,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            HapticFeedbackHelper.vibrateSoftTap(context, settings.hapticFeedbackEnabled, hostView)
                            onTogglePause()
                        },
                        shape = RoundedCornerShape(26.dp),
                        border = BorderStroke(1.5.dp, if (uiState.isPaused) EmeraldOk else SolarGold),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("pause_dubbing_button")
                    ) {
                        Text(
                            text = if (uiState.isPaused) strings.resumeDubbing else strings.pauseDubbing,
                            fontWeight = FontWeight.Bold,
                            color = if (uiState.isPaused) EmeraldOk else SolarGold
                        )
                    }
                    Button(
                        onClick = {
                            HapticFeedbackHelper.vibrateDubbingStop(context, settings.hapticFeedbackEnabled)
                            onStopDubbing()
                        },
                        shape = RoundedCornerShape(26.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CrimsonRed,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("stop_dubbing_button")
                    ) {
                        Text(
                            text = strings.stopDubbing,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    pickerForSource?.let { isSource ->
        LanguagePickerBottomSheet(
            title = if (isSource) strings.sourceLangLabel else strings.targetLangLabel,
            selectedCode = if (isSource) settings.sourceLanguageCode else settings.targetLanguageCode,
            recentCodes = settings.recentLanguageCodes,
            allowAutoDetect = isSource,
            onSelect = { lang ->
                if (isSource) onSelectSourceLanguage(lang) else onSelectTargetLanguage(lang)
            },
            onDismiss = { pickerForSource = null }
        )
    }
}

@Composable
private fun LanguageCapsuleButton(
    label: String,
    value: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    val isDark = cs.background.red < 0.25f
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .liquidPressBounce(interactionSource, pressedScale = 0.96f)
            .clip(shape)
            .background(if (isDark) Color(0x6E152038) else Color(0x9EFFFFFF))
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.36f),
                shape = shape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            color = cs.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = cs.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
