package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.data.LanguageItem
import com.example.data.LiveConnectionTestResult
import com.example.i18n.AppStrings
import com.example.i18n.LocalAppStrings
import com.example.live.GeminiModelInfo
import com.example.ui.components.AiVoiceCloneBannerStrip
import com.example.ui.components.EyeVisibilityIcon
import com.example.ui.components.GlobeLanguageIcon
import com.example.ui.components.LanguagePickerBottomSheet
import com.example.ui.components.LiquidGlassCard
import com.example.ui.components.LiquidGlassPillBadge
import com.example.ui.components.LiquidGlassPrimaryButton
import com.example.ui.components.liquidPressBounce
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.EmeraldOk
import com.example.ui.theme.SolarGold
import com.example.util.HapticFeedbackHelper

/**
 * AeroGlass 60 — First-Launch 3-Step Setup Wizard:
 * 1) App UI Language selection (24 languages — updates live instantly!)
 * 2) Gemini API Key binding & automatic sync of latest live dubbing-only models
 * 3) Target dubbing language, Auto-Latest Dubbing Model picker (zero clutter!) & REAL live WebSocket test
 */
@Composable
fun SetupWizardScreen(
    settings: AppSettings,
    availableModels: List<GeminiModelInfo>,
    isLoadingModels: Boolean,
    modelFetchStatus: String?,
    isTestingConnection: Boolean,
    connectionTestResult: Result<LiveConnectionTestResult>?,
    onSelectAppLanguage: (String) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onValidateAndFetchModels: () -> Unit,
    onAutoSelectLatestChange: (Boolean) -> Unit,
    onSelectModel: (String) -> Unit,
    onSelectTargetLanguage: (LanguageItem) -> Unit,
    onTestConnection: () -> Unit,
    onCompleteSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current

    var currentStep by rememberSaveable { mutableIntStateOf(1) }
    var appLangQuery by rememberSaveable { mutableStateOf("") }
    var modelQuery by rememberSaveable { mutableStateOf("") }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var showTargetLangSheet by remember { mutableStateOf(false) }

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
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Top Header & 3-Step Liquid Progress Dots
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.wizardTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = cs.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = String.format(strings.wizardStepOf, currentStep),
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // 3-step spring-animated indicator pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (step in 1..3) {
                        val active = step == currentStep
                        val done = step < currentStep
                        val dotWidth by animateDpAsState(
                            targetValue = if (active) 28.dp else 10.dp,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "wizard_dot_width_$step"
                        )
                        Box(
                            modifier = Modifier
                                .height(8.dp)
                                .width(dotWidth)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        active -> cs.primary
                                        done -> EmeraldOk
                                        else -> cs.outlineVariant
                                    }
                                )
                        )
                    }
                }
            }

            // Directional M3 Shared-Axis Step Content Area
            val stepEase = remember { CubicBezierEasing(0.2f, 0f, 0f, 1f) }
            AnimatedContent(
                targetState = currentStep,
                transitionSpec = {
                    val forward = targetState >= initialState
                    (slideInHorizontally(
                        animationSpec = tween(280, easing = stepEase),
                        initialOffsetX = { w -> if (forward) w / 4 else -w / 4 }
                    ) + fadeIn(tween(240, easing = stepEase)))
                        .togetherWith(
                            slideOutHorizontally(
                                animationSpec = tween(200, easing = stepEase),
                                targetOffsetX = { w -> if (forward) -w / 5 else w / 5 }
                            ) + fadeOut(tween(160, easing = stepEase))
                        )
                },
                label = "wizard_step_content",
                modifier = Modifier.weight(1f)
            ) { step ->
                when (step) {
                    1 -> WizardStep1AppLanguage(
                        selectedAppLangCode = settings.appLanguageCode,
                        query = appLangQuery,
                        onQueryChange = { appLangQuery = it },
                        onSelectAppLanguage = onSelectAppLanguage
                    )

                    2 -> WizardStep2ApiKey(
                        apiKey = settings.apiKey,
                        apiKeyVisible = apiKeyVisible,
                        onToggleApiKeyVisible = { apiKeyVisible = !apiKeyVisible },
                        onApiKeyChange = onApiKeyChange,
                        isLoadingModels = isLoadingModels,
                        modelFetchStatus = modelFetchStatus,
                        onValidateAndFetchModels = onValidateAndFetchModels,
                        onOpenGoogleAiStudio = {
                            try {
                                val intent = Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://aistudio.google.com/apikey")
                                )
                                context.startActivity(intent)
                            } catch (_: Throwable) {
                            }
                        }
                    )

                    else -> WizardStep3ModelAndTest(
                        settings = settings,
                        availableModels = availableModels,
                        modelQuery = modelQuery,
                        onModelQueryChange = { modelQuery = it },
                        onAutoSelectLatestChange = onAutoSelectLatestChange,
                        onSelectModel = onSelectModel,
                        onOpenTargetLangPicker = { showTargetLangSheet = true },
                        isTestingConnection = isTestingConnection,
                        connectionTestResult = connectionTestResult,
                        onTestConnection = onTestConnection
                    )
                }
            }

            // Bottom Wizard Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep > 1) {
                    OutlinedButton(
                        onClick = { currentStep -= 1 },
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.2.dp, cs.outlineVariant),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("wizard_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = strings.wizardBack,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(strings.wizardBack, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    TextButton(
                        onClick = onCompleteSetup,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("wizard_skip_button")
                    ) {
                        Text(
                            text = strings.wizardSkipForNow,
                            color = cs.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                LiquidGlassPrimaryButton(
                    onClick = {
                        HapticFeedbackHelper.vibrateSoftTap(context, settings.hapticFeedbackEnabled)
                        if (currentStep < 3) {
                            currentStep += 1
                        } else {
                            onCompleteSetup()
                        }
                    },
                    height = 52.dp,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(if (currentStep < 3) "wizard_next_button" else "wizard_finish_button")
                ) {
                    Text(
                        text = if (currentStep < 3) strings.wizardNext else strings.wizardFinish,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (currentStep < 3) Icons.AutoMirrored.Filled.ArrowForward else Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    if (showTargetLangSheet) {
        LanguagePickerBottomSheet(
            title = strings.targetLangLabel,
            selectedCode = settings.targetLanguageCode,
            recentCodes = settings.recentLanguageCodes,
            allowAutoDetect = false,
            onSelect = onSelectTargetLanguage,
            onDismiss = { showTargetLangSheet = false }
        )
    }
}

@Composable
private fun WizardStep1AppLanguage(
    selectedAppLangCode: String,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelectAppLanguage: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    val cs = MaterialTheme.colorScheme
    val trimmed = query.trim()

    val filtered = remember(trimmed) {
        if (trimmed.isEmpty()) {
            AppStrings.SUPPORTED_APP_LANGUAGES
        } else {
            AppStrings.SUPPORTED_APP_LANGUAGES.filter {
                it.nativeName.contains(trimmed, ignoreCase = true) ||
                    it.englishName.contains(trimmed, ignoreCase = true) ||
                    it.code.contains(trimmed, ignoreCase = true)
            }
        }
    }

    LiquidGlassCard(
        accentColor = cs.primary,
        cornerRadius = 22.dp,
        contentPadding = 14.dp,
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GlobeLanguageIcon(
                tint = cs.primary,
                size = 22.dp
            )
            Text(
                text = strings.wizardStep1Title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = strings.wizardStep1Subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(strings.wizardSearchAppLangHint) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = strings.wizardSearchAppLangHint)
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = strings.clearDesc)
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("wizard_app_lang_search")
        )

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filtered, key = { it.code }) { option ->
                val isSelected = selectedAppLangCode.equals(option.code, ignoreCase = true)
                val interactionSource = remember { MutableInteractionSource() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .liquidPressBounce(interactionSource, pressedScale = 0.98f)
                        .clip(RoundedCornerShape(15.dp))
                        .background(
                            if (isSelected) cs.primaryContainer.copy(alpha = 0.85f)
                            else cs.surfaceVariant.copy(alpha = 0.40f)
                        )
                        .border(
                            width = if (isSelected) 1.4.dp else 1.dp,
                            color = if (isSelected) cs.primary.copy(alpha = 0.7f) else cs.outlineVariant.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(15.dp)
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onSelectAppLanguage(option.code) }
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("wizard_app_lang_${option.code}"),
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

@Composable
private fun WizardStep2ApiKey(
    apiKey: String,
    apiKeyVisible: Boolean,
    onToggleApiKeyVisible: () -> Unit,
    onApiKeyChange: (String) -> Unit,
    isLoadingModels: Boolean,
    modelFetchStatus: String?,
    onValidateAndFetchModels: () -> Unit,
    onOpenGoogleAiStudio: () -> Unit
) {
    val strings = LocalAppStrings.current
    val cs = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        LiquidGlassCard(
            accentColor = cs.primary,
            cornerRadius = 22.dp,
            contentPadding = 16.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = strings.wizardStep2Title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = strings.wizardStep2Subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = apiKey,
                onValueChange = onApiKeyChange,
                label = { Text(strings.wizardApiKeyLabel) },
                placeholder = { Text("AIza...") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(
                        onClick = onToggleApiKeyVisible,
                        modifier = Modifier.testTag("wizard_toggle_key_visibility")
                    ) {
                        EyeVisibilityIcon(
                            visible = apiKeyVisible,
                            tint = cs.onSurfaceVariant,
                            size = 20.dp
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wizard_api_key_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onOpenGoogleAiStudio,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("wizard_get_key_button")
                ) {
                    Text(strings.wizardGetApiKey, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                LiquidGlassPrimaryButton(
                    onClick = onValidateAndFetchModels,
                    enabled = !isLoadingModels && apiKey.isNotBlank(),
                    height = 48.dp,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("wizard_validate_key_button")
                ) {
                    if (isLoadingModels) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = strings.wizardValidatingKey,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = strings.wizardValidateKeyBtn,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (!modelFetchStatus.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                val isError = modelFetchStatus.contains("güncellenemedi", ignoreCase = true) ||
                    modelFetchStatus.contains("hata", ignoreCase = true) ||
                    modelFetchStatus.contains("failed", ignoreCase = true) ||
                    modelFetchStatus.contains("error", ignoreCase = true)
                LiquidGlassPillBadge(
                    text = modelFetchStatus,
                    accentColor = if (isError) CrimsonRed else EmeraldOk,
                    leadingDot = true
                )
            }
        }

        AiVoiceCloneBannerStrip(isLosslessActive = true)
    }
}

@Composable
private fun WizardStep3ModelAndTest(
    settings: AppSettings,
    availableModels: List<GeminiModelInfo>,
    modelQuery: String,
    onModelQueryChange: (String) -> Unit,
    onAutoSelectLatestChange: (Boolean) -> Unit,
    onSelectModel: (String) -> Unit,
    onOpenTargetLangPicker: () -> Unit,
    isTestingConnection: Boolean,
    connectionTestResult: Result<LiveConnectionTestResult>?,
    onTestConnection: () -> Unit
) {
    val strings = LocalAppStrings.current
    val cs = MaterialTheme.colorScheme
    val targetLang = LanguageItem.byCode(settings.targetLanguageCode, allowAuto = false)
    val isTurkishUi = strings.code == "tr"
    val trimmed = modelQuery.trim()

    val filteredModels = remember(availableModels, trimmed) {
        if (trimmed.isEmpty()) availableModels
        else availableModels.filter {
            it.id.contains(trimmed, ignoreCase = true) || it.displayName.contains(trimmed, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Target language Card
        LiquidGlassCard(
            accentColor = cs.secondary,
            cornerRadius = 20.dp,
            contentPadding = 14.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = strings.targetLangLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant
                    )
                    Text(
                        text = if (isTurkishUi) targetLang.nameTr else targetLang.nativeName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurface
                    )
                }
                OutlinedButton(
                    onClick = onOpenTargetLangPicker,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("wizard_target_lang_button")
                ) {
                    Text(targetLang.code.uppercase(), fontWeight = FontWeight.Bold)
                }
            }
        }

        // Built-in Automatic AI Voice Cloning strip
        AiVoiceCloneBannerStrip(isLosslessActive = !settings.silenceDetectionEnabled)

        // Strict Dubbing-Only Model Selection + Auto-Latest Switch + Real WebSocket Test
        LiquidGlassCard(
            accentColor = cs.primary,
            cornerRadius = 20.dp,
            contentPadding = 14.dp
        ) {
            Text(
                text = strings.wizardStep3Title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = strings.dubbingModelsOnlyNote,
                style = MaterialTheme.typography.labelSmall,
                color = cs.primary,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                    Text(
                        text = strings.autoLatestModelTitle,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = cs.onSurface
                    )
                    Text(
                        text = strings.autoLatestModelSubtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant
                    )
                }
                Switch(
                    checked = settings.autoSelectLatestModel,
                    onCheckedChange = onAutoSelectLatestChange,
                    modifier = Modifier.testTag("wizard_auto_latest_toggle")
                )
            }

            if (availableModels.size > 4 || modelQuery.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = modelQuery,
                    onValueChange = onModelQueryChange,
                    placeholder = { Text(strings.searchModelHint) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = strings.searchModelHint) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("wizard_model_search")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                filteredModels.take(8).forEach { model ->
                    val isSelected = settings.selectedModel == model.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSelected) cs.primaryContainer.copy(alpha = 0.85f)
                                else cs.surfaceVariant.copy(alpha = 0.40f)
                            )
                            .border(
                                width = if (isSelected) 1.3.dp else 1.dp,
                                color = if (isSelected) cs.primary else cs.outlineVariant.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { onSelectModel(model.id) }
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                            .testTag("wizard_model_${model.id}"),
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

            Spacer(modifier = Modifier.height(12.dp))

            // REAL Gemini Live WebSocket Test Button
            OutlinedButton(
                onClick = onTestConnection,
                enabled = !isTestingConnection && settings.apiKey.isNotBlank(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.3.dp, cs.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("wizard_test_connection_button")
            ) {
                if (isTestingConnection) {
                    CircularProgressIndicator(
                        color = cs.primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(strings.wizardTestingConnection, fontWeight = FontWeight.Bold)
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = strings.wizardTestConnectionBtn,
                        fontWeight = FontWeight.Bold,
                        color = cs.primary
                    )
                }
            }

            if (connectionTestResult != null) {
                Spacer(modifier = Modifier.height(8.dp))
                connectionTestResult.fold(
                    onSuccess = { res ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.testTag("wizard_test_success_badge")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldOk,
                                modifier = Modifier.size(18.dp)
                            )
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
                            modifier = Modifier.testTag("wizard_test_error_badge")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = CrimsonRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = String.format(strings.wizardTestFailed, err.message ?: "Error"),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = CrimsonRed
                            )
                        }
                    }
                )
            } else if (settings.apiKey.isBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = strings.missingApiKeyBanner,
                    style = MaterialTheme.typography.labelSmall,
                    color = SolarGold
                )
            }
        }
    }
}
