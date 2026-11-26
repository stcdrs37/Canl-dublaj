package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AppSettings
import com.example.data.AppThemeMode
import com.example.data.LanguageItem
import com.example.data.LiveConnectionTestResult
import com.example.data.OsdFontSizePreset
import com.example.i18n.AppStrings
import com.example.i18n.LocalAppStrings
import com.example.live.GeminiModelInfo
import com.example.security.CyberSecurityReport
import com.example.ui.components.AiVoiceCloneBannerStrip
import com.example.ui.components.EyeVisibilityIcon
import com.example.ui.components.GlobeLanguageIcon
import com.example.ui.components.LanguagePickerBottomSheet
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGlassIconOrb
import com.example.ui.components.LiquidGlassPillBadge
import com.example.ui.components.OsdFloatingIcon
import com.example.ui.components.PremiumOsdPreviewCard
import com.example.ui.components.ThemeModeIcon
import com.example.ui.components.liquidPressBounce
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.EmeraldOk
import com.example.ui.theme.SolarGold
import com.example.util.HapticFeedbackHelper

private enum class SettingsTab(val id: String) {
    OSD("osd"),
    MODEL("model"),
    AUDIO("audio"),
    GENERAL("general")
}

/**
 * AeroGlass 60 — Ultra-Clean, Organized 4-Tab Liquid Glass Settings Screen.
 * Eliminates long scrolling clutter by grouping settings into 4 crisp frosted tabs:
 * 1) OSD: Live Interactive Glass Preview (with Hide `—` button), opacity, font size, compact mode
 * 2) API & Model: Gemini API key, Always-Latest toggle, dubbing-only models, real WebSocket test
 * 3) Ses & Dil: Dubbing languages, Media-channel mixer (never pauses video), Lossless & Haptics
 * 4) Genel: Automatic System/Light/Dark Liquid Glass theme, 24-language searchable sheet, Wizard
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    availableModels: List<GeminiModelInfo>,
    canDrawOverlay: Boolean,
    isLoadingModels: Boolean,
    modelFetchStatus: String?,
    isTestingConnection: Boolean,
    connectionTestResult: Result<LiveConnectionTestResult>?,
    cyberSecurityReport: CyberSecurityReport = CyberSecurityReport(
        encryptionActive = true,
        hardwareBackedKeystore = true,
        headerOnlyAuthEnforced = true,
        tls13Hardened = true,
        proxyDetected = false,
        rootIndicatorDetected = false,
        debuggerAttached = false
    ),
    onBack: () -> Unit,
    onOpenSetupWizard: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onOsdEnabledChange: (Boolean) -> Unit,
    onOsdOpacityChange: (Int) -> Unit,
    onOsdFontSizeChange: (OsdFontSizePreset) -> Unit,
    onOsdShowOriginalChange: (Boolean) -> Unit,
    onOsdCompactChange: (Boolean) -> Unit,
    onSelectAppLanguage: (String) -> Unit,
    onSelectSourceLanguage: (LanguageItem) -> Unit,
    onSelectTargetLanguage: (LanguageItem) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onAutoSelectLatestChange: (Boolean) -> Unit,
    onSelectModel: (String) -> Unit,
    onRefreshModels: () -> Unit,
    onTestConnection: () -> Unit,
    onDubVolumeChange: (Int) -> Unit,
    onDuckAudioChange: (Boolean) -> Unit,
    onBluetoothDubOnlyChange: (Boolean) -> Unit = {},
    onEchoTargetChange: (Boolean) -> Unit,
    onSilenceDetectionChange: (Boolean) -> Unit,
    onHapticFeedbackChange: (Boolean) -> Unit,
    onAutoReconnectChange: (Boolean) -> Unit,
    onThemeModeChange: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val strings = LocalAppStrings.current
    val cs = MaterialTheme.colorScheme
    val isDark = cs.background.red < 0.25f
    val context = LocalContext.current
    val isTurkishUi = strings.code == "tr"

    var selectedTab by rememberSaveable { mutableStateOf(SettingsTab.OSD) }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var modelQuery by rememberSaveable { mutableStateOf("") }
    var pickerForSource by remember { mutableStateOf<Boolean?>(null) }
    var showAppLangSheet by remember { mutableStateOf(false) }

    val filteredModels = remember(availableModels, modelQuery) {
        val q = modelQuery.trim()
        if (q.isEmpty()) availableModels
        else availableModels.filter {
            it.id.contains(q, ignoreCase = true) || it.displayName.contains(q, ignoreCase = true)
        }
    }

    val sourceItem = LanguageItem.byCode(settings.sourceLanguageCode, allowAuto = true)
    val targetItem = LanguageItem.byCode(settings.targetLanguageCode, allowAuto = false)
    val currentAppLang = remember(settings.appLanguageCode) {
        AppStrings.SUPPORTED_APP_LANGUAGES.firstOrNull {
            it.code.equals(settings.appLanguageCode, ignoreCase = true)
        } ?: AppStrings.SUPPORTED_APP_LANGUAGES.first()
    }
    val previewBridge = "${if (sourceItem.isAuto) "AUTO" else sourceItem.code.uppercase()} → ${targetItem.code.uppercase()}"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 580.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // 1. Clean Liquid Glass Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                LiquidGlassIconOrb(
                    onClick = onBack,
                    modifier = Modifier.testTag("settings_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = strings.backDesc,
                        tint = cs.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.settingsTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = cs.onBackground
                    )
                    Text(
                        text = strings.settingsSubtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 2. Clean 4-Tab Liquid Glass Segmented Control (zero scroll wall clutter!)
            val tabBarShape = RoundedCornerShape(18.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(tabBarShape)
                    .background(if (isDark) Color(0x80131D36) else Color(0xA6FFFFFF))
                    .border(
                        width = 1.dp,
                        color = if (isDark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.92f),
                        shape = tabBarShape
                    )
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SettingsTab.entries.forEach { tab ->
                    val active = selectedTab == tab
                    val label = when (tab) {
                        SettingsTab.OSD -> strings.settingsTabOsd
                        SettingsTab.MODEL -> strings.settingsTabModel
                        SettingsTab.AUDIO -> strings.settingsTabAudio
                        SettingsTab.GENERAL -> strings.settingsTabGeneral
                    }
                    val pillShape = RoundedCornerShape(14.dp)
                    val interactionSource = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .height(40.dp)
                            .liquidPressBounce(interactionSource, pressedScale = 0.95f)
                            .clip(pillShape)
                            .background(
                                if (active) cs.primary.copy(alpha = if (isDark) 0.30f else 0.16f)
                                else Color.Transparent
                            )
                            .border(
                                width = if (active) 1.1.dp else 0.dp,
                                color = if (active) cs.primary.copy(alpha = 0.60f) else Color.Transparent,
                                shape = pillShape
                            )
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { selectedTab = tab }
                            )
                            .testTag("settings_tab_${tab.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) cs.primary else cs.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Smooth 60-120fps Directional Shared-Axis Tabbed Content Viewport
            val tabEase = remember { CubicBezierEasing(0.2f, 0f, 0f, 1f) }
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val forward = targetState.ordinal >= initialState.ordinal
                    (slideInHorizontally(
                        animationSpec = tween(250, easing = tabEase),
                        initialOffsetX = { w -> if (forward) w / 7 else -w / 7 }
                    ) + fadeIn(tween(220, easing = tabEase)))
                        .togetherWith(
                            slideOutHorizontally(
                                animationSpec = tween(180, easing = tabEase),
                                targetOffsetX = { w -> if (forward) -w / 8 else w / 8 }
                            ) + fadeOut(tween(150, easing = tabEase))
                        )
                },
                label = "settings_tab_content",
                modifier = Modifier.weight(1f)
            ) { tab ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (tab) {
                        SettingsTab.OSD -> {
                            LiquidGlassCard(
                                accentColor = cs.primary,
                                cornerRadius = 24.dp,
                                contentPadding = 14.dp
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OsdFloatingIcon(
                                        tint = cs.primary,
                                        activeDotColor = if (settings.osdEnabled && canDrawOverlay) EmeraldOk else null,
                                        size = 20.dp
                                    )
                                    Text(
                                        text = strings.osdSectionTitle,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = cs.primary,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                PremiumOsdPreviewCard(
                                    osdEnabled = settings.osdEnabled,
                                    opacityPercent = settings.osdOpacityPercent,
                                    fontSizePreset = settings.osdFontSize,
                                    showOriginalText = settings.osdShowOriginalText,
                                    compactMode = settings.osdCompactMode,
                                    languageBridgeLabel = previewBridge,
                                    modifier = Modifier.testTag("settings_osd_preview_card")
                                )

                                AnimatedVisibility(visible = settings.osdEnabled && !canDrawOverlay) {
                                    Column {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(SolarGold.copy(alpha = 0.14f))
                                                .border(1.dp, SolarGold.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = strings.warningDesc,
                                                tint = SolarGold,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = strings.osdPermissionNeeded,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = cs.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Button(
                                                onClick = onRequestOverlayPermission,
                                                shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = SolarGold,
                                                    contentColor = Color(0xFF0E1320)
                                                ),
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .testTag("settings_grant_osd_permission")
                                            ) {
                                                Text(
                                                    text = strings.osdGrantPermissionBtn,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                SettingsToggleRow(
                                    title = strings.osdEnabledTitle,
                                    subtitle = strings.osdEnabledSubtitle,
                                    checked = settings.osdEnabled,
                                    onCheckedChange = onOsdEnabledChange,
                                    testTag = "settings_osd_enabled_toggle"
                                )

                                AnimatedVisibility(visible = settings.osdEnabled) {
                                    Column {
                                        HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f))
                                        Spacer(modifier = Modifier.height(10.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = strings.osdOpacityLabel,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = cs.onSurface
                                            )
                                            Text(
                                                text = "%${settings.osdOpacityPercent}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = cs.primary
                                            )
                                        }
                                        Slider(
                                            value = settings.osdOpacityPercent.toFloat(),
                                            onValueChange = { onOsdOpacityChange(it.toInt()) },
                                            valueRange = 50f..100f,
                                            modifier = Modifier.testTag("settings_osd_opacity_slider")
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Text(
                                            text = strings.osdFontSizeLabel,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = cs.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OsdFontSizePreset.entries.forEach { preset ->
                                                val selected = settings.osdFontSize == preset
                                                val shape = RoundedCornerShape(13.dp)
                                                val interactionSource = remember { MutableInteractionSource() }
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .minimumInteractiveComponentSize()
                                                        .height(42.dp)
                                                        .liquidPressBounce(interactionSource, pressedScale = 0.95f)
                                                        .clip(shape)
                                                        .background(
                                                            if (selected) cs.primary.copy(alpha = 0.20f)
                                                            else cs.surfaceVariant.copy(alpha = 0.42f)
                                                        )
                                                        .border(
                                                            width = if (selected) 1.3.dp else 1.dp,
                                                            color = if (selected) cs.primary else cs.outlineVariant.copy(alpha = 0.45f),
                                                            shape = shape
                                                        )
                                                        .clickable(
                                                            interactionSource = interactionSource,
                                                            indication = null,
                                                            onClick = { onOsdFontSizeChange(preset) }
                                                        )
                                                        .testTag("settings_osd_size_${preset.id}"),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = strings.osdSizeLabel(preset),
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                        color = if (selected) cs.primary else cs.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f))

                                        SettingsToggleRow(
                                            title = strings.osdShowOriginalTitle,
                                            subtitle = strings.osdShowOriginalSubtitle,
                                            checked = settings.osdShowOriginalText,
                                            onCheckedChange = onOsdShowOriginalChange,
                                            testTag = "settings_osd_original_toggle"
                                        )

                                        HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f))

                                        SettingsToggleRow(
                                            title = strings.osdCompactTitle,
                                            subtitle = strings.osdCompactSubtitle,
                                            checked = settings.osdCompactMode,
                                            onCheckedChange = onOsdCompactChange,
                                            testTag = "settings_osd_compact_toggle"
                                        )
                                    }
                                }
                            }
                        }

                        SettingsTab.MODEL -> {
                            LiquidGlassCard(
                                accentColor = cs.primary,
                                cornerRadius = 24.dp,
                                contentPadding = 14.dp
                            ) {
                                Text(
                                    text = strings.apiAndModelSection,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = cs.primary
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = strings.dubbingModelsOnlyNote,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                // Live Cyber Security Shield Status Strip
                                val shieldAccent = if (cyberSecurityReport.isFullyProtected) EmeraldOk else SolarGold
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(shieldAccent.copy(alpha = if (isDark) 0.16f else 0.11f))
                                        .border(1.dp, shieldAccent.copy(alpha = 0.50f), RoundedCornerShape(14.dp))
                                        .padding(horizontal = 12.dp, vertical = 9.dp)
                                        .testTag("settings_cyber_shield_card"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Cyber Shield",
                                        tint = shieldAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isTurkishUi) {
                                                if (cyberSecurityReport.isFullyProtected) "Siber Güvenlik Kalkanı Aktif"
                                                else "Siber Kalkan Uyarısı (Ağ/Cihaz Kontrolü)"
                                            } else {
                                                if (cyberSecurityReport.isFullyProtected) "Cyber Security Shield Active"
                                                else "Cyber Shield Advisory"
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = shieldAccent
                                        )
                                        Text(
                                            text = if (isTurkishUi) {
                                                "AES-256-GCM Kasa • Header Auth • TLS 1.3 • MITM Koruması"
                                            } else {
                                                "AES-256-GCM Vault • Header Auth • TLS 1.3 • Anti-MITM"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = cs.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = settings.apiKey,
                                    onValueChange = onApiKeyChange,
                                    label = { Text(strings.wizardApiKeyLabel) },
                                    placeholder = { Text("AIza...") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    trailingIcon = {
                                        IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                                            EyeVisibilityIcon(
                                                visible = apiKeyVisible,
                                                tint = cs.onSurfaceVariant,
                                                size = 20.dp
                                            )
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("settings_api_key_input")
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                context.startActivity(
                                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))
                                                )
                                            } catch (_: Throwable) {
                                            }
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                    ) {
                                        Text(strings.wizardGetApiKey, maxLines = 1)
                                    }

                                    OutlinedButton(
                                        onClick = onRefreshModels,
                                        enabled = !isLoadingModels,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                            .testTag("settings_fetch_models_button")
                                    ) {
                                        if (isLoadingModels) {
                                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                        } else {
                                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(strings.fetchModelsBtn, maxLines = 1)
                                    }
                                }

                                if (!modelFetchStatus.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val isError = modelFetchStatus.contains("hata", ignoreCase = true) ||
                                        modelFetchStatus.contains("error", ignoreCase = true) ||
                                        modelFetchStatus.contains("failed", ignoreCase = true)
                                    LiquidGlassPillBadge(
                                        text = modelFetchStatus,
                                        accentColor = if (isError) CrimsonRed else EmeraldOk,
                                        leadingDot = true
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f))

                                SettingsToggleRow(
                                    title = strings.autoLatestModelTitle,
                                    subtitle = strings.autoLatestModelSubtitle,
                                    checked = settings.autoSelectLatestModel,
                                    onCheckedChange = onAutoSelectLatestChange,
                                    testTag = "settings_auto_latest_model_toggle"
                                )

                                if (availableModels.size > 4 || modelQuery.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = modelQuery,
                                        onValueChange = { modelQuery = it },
                                        placeholder = { Text(strings.searchModelHint) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = strings.searchModelHint) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("settings_model_search")
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    filteredModels.take(12).forEach { model ->
                                        val isSelected = settings.selectedModel == model.id
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .minimumInteractiveComponentSize()
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(
                                                    if (isSelected) cs.primaryContainer.copy(alpha = 0.85f)
                                                    else cs.surfaceVariant.copy(alpha = 0.36f)
                                                )
                                                .border(
                                                    width = if (isSelected) 1.3.dp else 1.dp,
                                                    color = if (isSelected) cs.primary else cs.outlineVariant.copy(alpha = 0.45f),
                                                    shape = RoundedCornerShape(14.dp)
                                                )
                                                .clickable { onSelectModel(model.id) }
                                                .padding(horizontal = 12.dp, vertical = 9.dp)
                                                .testTag("settings_model_${model.id}"),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = model.id,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = cs.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = model.displayName,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = cs.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (isSelected) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = strings.selectedDesc,
                                                    tint = cs.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            } else if (model.isRecommendedTranslate) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                LiquidGlassPillBadge(text = strings.recommendedBadge, accentColor = EmeraldOk)
                                            } else {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                LiquidGlassPillBadge(text = strings.liveAudioBadge, accentColor = cs.primary)
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedButton(
                                    onClick = onTestConnection,
                                    enabled = !isTestingConnection && settings.apiKey.isNotBlank(),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.2.dp, cs.primary),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("settings_test_connection_button")
                                ) {
                                    if (isTestingConnection) {
                                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(strings.wizardTestingConnection, fontWeight = FontWeight.Bold)
                                    } else {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(strings.wizardTestConnectionBtn, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (connectionTestResult != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    connectionTestResult.fold(
                                        onSuccess = { res ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.testTag("settings_test_success_badge")
                                            ) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = EmeraldOk, modifier = Modifier.size(18.dp))
                                                Text(
                                                    text = String.format(strings.wizardTestSuccess, res.latencyMs, res.modelId),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = EmeraldOk
                                                )
                                            }
                                        },
                                        onFailure = { err ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.testTag("settings_test_error_badge")
                                            ) {
                                                Icon(Icons.Default.Warning, contentDescription = null, tint = CrimsonRed, modifier = Modifier.size(18.dp))
                                                Text(
                                                    text = String.format(strings.wizardTestFailed, err.message ?: "Error"),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = CrimsonRed
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        SettingsTab.AUDIO -> {
                            LiquidGlassCard(
                                accentColor = cs.secondary,
                                cornerRadius = 24.dp,
                                contentPadding = 14.dp
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = strings.audioAndQualitySection,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = cs.primary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    LiquidGlassPillBadge(
                                        text = strings.mediaStreamBadge,
                                        accentColor = EmeraldOk,
                                        leadingDot = true
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { pickerForSource = true },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("settings_source_lang_button")
                                    ) {
                                        Text(
                                            text = if (sourceItem.isAuto) strings.autoDetectTitle else if (isTurkishUi) sourceItem.nameTr else sourceItem.nativeName,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { pickerForSource = false },
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("settings_target_lang_button")
                                    ) {
                                        Text(
                                            text = if (isTurkishUi) targetItem.nameTr else targetItem.nativeName,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                AiVoiceCloneBannerStrip(isLosslessActive = !settings.silenceDetectionEnabled)

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = strings.dubVolumeLabel,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = cs.onSurface
                                    )
                                    Text(
                                        text = "%${settings.dubVolumePercent}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = cs.primary
                                    )
                                }
                                Slider(
                                    value = settings.dubVolumePercent.toFloat(),
                                    onValueChange = { onDubVolumeChange(it.toInt()) },
                                    valueRange = 0f..200f,
                                    modifier = Modifier.testTag("settings_volume_slider")
                                )

                                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f))

                                SettingsToggleRow(
                                    title = strings.losslessStreamTitle,
                                    subtitle = strings.losslessStreamSubtitle,
                                    checked = !settings.silenceDetectionEnabled,
                                    onCheckedChange = { losslessOn -> onSilenceDetectionChange(!losslessOn) },
                                    testTag = "settings_lossless_toggle"
                                )

                                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f))

                                SettingsToggleRow(
                                    title = strings.hapticFeedbackTitle,
                                    subtitle = strings.hapticFeedbackSubtitle,
                                    checked = settings.hapticFeedbackEnabled,
                                    onCheckedChange = { enabled ->
                                        onHapticFeedbackChange(enabled)
                                        if (enabled) {
                                            HapticFeedbackHelper.vibrateDubbingStart(context, enabled = true)
                                        }
                                    },
                                    testTag = "settings_haptic_toggle"
                                )

                                if (settings.hapticFeedbackEnabled) {
                                    OutlinedButton(
                                        onClick = {
                                            HapticFeedbackHelper.vibrateDubbingStart(context, enabled = true)
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp)
                                            .height(44.dp)
                                            .testTag("settings_test_haptic_button")
                                    ) {
                                        Text(
                                            text = strings.testVibrationBtn,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f))

                                SettingsToggleRow(
                                    title = strings.duckOriginalTitle,
                                    subtitle = strings.duckOriginalSubtitle,
                                    checked = settings.duckOriginalAudio,
                                    onCheckedChange = onDuckAudioChange,
                                    testTag = "settings_duck_toggle"
                                )

                                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f))

                                SettingsToggleRow(
                                    title = strings.bluetoothDubOnlyTitle,
                                    subtitle = strings.bluetoothDubOnlySubtitle,
                                    checked = settings.bluetoothDubOnlyRouting,
                                    onCheckedChange = onBluetoothDubOnlyChange,
                                    testTag = "settings_bt_dub_only_toggle"
                                )

                                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f))

                                SettingsToggleRow(
                                    title = strings.echoTargetTitle,
                                    subtitle = strings.echoTargetSubtitle,
                                    checked = settings.echoTargetLanguage,
                                    onCheckedChange = onEchoTargetChange,
                                    testTag = "settings_echo_toggle"
                                )

                                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.45f))

                                SettingsToggleRow(
                                    title = strings.autoReconnectTitle,
                                    subtitle = strings.autoReconnectSubtitle,
                                    checked = settings.autoReconnect,
                                    onCheckedChange = onAutoReconnectChange,
                                    testTag = "settings_reconnect_toggle"
                                )
                            }
                        }

                        SettingsTab.GENERAL -> {
                            // 1. Automatic System / Light / Dark Liquid Glass Theme Card
                            LiquidGlassCard(
                                accentColor = cs.tertiary,
                                cornerRadius = 24.dp,
                                contentPadding = 14.dp
                            ) {
                                Text(
                                    text = strings.appearanceSection,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = cs.primary
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = strings.themeModeSubtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = cs.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    AppThemeMode.entries.forEach { mode ->
                                        val selected = settings.themeMode == mode
                                        val shape = RoundedCornerShape(14.dp)
                                        val interactionSource = remember { MutableInteractionSource() }
                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .minimumInteractiveComponentSize()
                                                .height(44.dp)
                                                .liquidPressBounce(interactionSource, pressedScale = 0.95f)
                                                .clip(shape)
                                                .background(
                                                    if (selected) cs.primary.copy(alpha = 0.20f)
                                                    else cs.surfaceVariant.copy(alpha = 0.40f)
                                                )
                                                .border(
                                                    width = if (selected) 1.35.dp else 1.dp,
                                                    color = if (selected) cs.primary else cs.outlineVariant.copy(alpha = 0.45f),
                                                    shape = shape
                                                )
                                                .clickable(
                                                    interactionSource = interactionSource,
                                                    indication = null,
                                                    onClick = { onThemeModeChange(mode) }
                                                )
                                                .padding(horizontal = 8.dp)
                                                .testTag("settings_theme_${mode.id}"),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            ThemeModeIcon(
                                                themeMode = mode,
                                                tint = if (selected) cs.primary else cs.onSurfaceVariant,
                                                size = 16.dp
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = strings.themeModeLabel(mode),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (selected) cs.primary else cs.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }

                            // 2. App UI Language (24-Language Searchable Modal Sheet) & Setup Wizard Card
                            LiquidGlassCard(
                                accentColor = cs.primary,
                                cornerRadius = 24.dp,
                                contentPadding = 14.dp
                            ) {
                                Text(
                                    text = strings.appLanguageSection,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = cs.primary
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .clip(RoundedCornerShape(15.dp))
                                        .background(cs.surfaceVariant.copy(alpha = 0.40f))
                                        .border(1.dp, cs.outlineVariant.copy(alpha = 0.45f), RoundedCornerShape(15.dp))
                                        .clickable { showAppLangSheet = true }
                                        .padding(horizontal = 12.dp, vertical = 11.dp)
                                        .testTag("settings_app_lang_button"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    GlobeLanguageIcon(tint = cs.primary, size = 20.dp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = strings.selectAppLanguageTitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = cs.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${currentAppLang.nativeName} (${currentAppLang.englishName})",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = cs.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = cs.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedButton(
                                    onClick = onOpenSetupWizard,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("settings_open_wizard_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(strings.openSetupWizardBtn, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
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

    if (showAppLangSheet) {
        AppLanguagePickerBottomSheet(
            selectedCode = settings.appLanguageCode,
            onSelect = { code ->
                onSelectAppLanguage(code)
                showAppLangSheet = false
            },
            onDismiss = { showAppLangSheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppLanguagePickerBottomSheet(
    selectedCode: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalAppStrings.current
    val cs = MaterialTheme.colorScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }

    val filtered = remember(query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) AppStrings.SUPPORTED_APP_LANGUAGES
        else AppStrings.SUPPORTED_APP_LANGUAGES.filter {
            it.nativeName.contains(trimmed, ignoreCase = true) ||
                it.englishName.contains(trimmed, ignoreCase = true) ||
                it.code.contains(trimmed, ignoreCase = true)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = cs.surface,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GlobeLanguageIcon(tint = cs.primary, size = 20.dp)
                Text(
                    text = strings.selectAppLanguageTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(strings.wizardSearchAppLangHint) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = strings.wizardSearchAppLangHint) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, contentDescription = strings.clearDesc)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_app_lang_search")
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filtered, key = { it.code }) { option ->
                    val isSelected = selectedCode.equals(option.code, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSelected) cs.primaryContainer.copy(alpha = 0.85f)
                                else cs.surfaceVariant.copy(alpha = 0.38f)
                            )
                            .border(
                                width = if (isSelected) 1.3.dp else 1.dp,
                                color = if (isSelected) cs.primary else cs.outlineVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { onSelect(option.code) }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("settings_app_lang_${option.code}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = option.nativeName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                color = cs.onSurface
                            )
                            Text(
                                text = "${option.englishName} • ${option.code.uppercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = strings.selectedDesc,
                                tint = cs.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
