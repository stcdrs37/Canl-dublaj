package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppThemeMode
import com.example.data.LiveConnectionTestResult
import com.example.i18n.AppStrings
import com.example.i18n.LocalAppStrings
import com.example.live.LiveSessionState
import com.example.service.DubbingService
import com.example.ui.components.LiquidGlassBackground
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SetupWizardScreen
import com.example.ui.screens.SplashIntroScreen
import com.example.ui.theme.LiveDubTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as LiveDubApplication
        setContent {
            val settings by app.container.settingsRepository.settings.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val effectiveDarkTheme = when (settings.themeMode) {
                AppThemeMode.SYSTEM -> systemDark
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
            }
            LaunchedEffect(effectiveDarkTheme) {
                applySystemBars(darkTheme = effectiveDarkTheme)
            }
            val appStrings = remember(settings.appLanguageCode) {
                AppStrings.forCode(settings.appLanguageCode)
            }
            var showSplash by rememberSaveable { mutableStateOf(true) }

            CompositionLocalProvider(LocalAppStrings provides appStrings) {
                LiveDubTheme(darkTheme = effectiveDarkTheme) {
                    val emphasizedEase = remember { CubicBezierEasing(0.2f, 0f, 0f, 1f) }
                    AnimatedContent(
                        targetState = showSplash,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(420, easing = emphasizedEase)) +
                                scaleIn(initialScale = 0.96f, animationSpec = tween(420, easing = emphasizedEase)))
                                .togetherWith(
                                    fadeOut(animationSpec = tween(300, easing = emphasizedEase)) +
                                        scaleOut(targetScale = 1.04f, animationSpec = tween(300, easing = emphasizedEase))
                                )
                        },
                        label = "splash_to_app_transition"
                    ) { splashActive ->
                        if (splashActive) {
                            SplashIntroScreen(
                                playAudio = true,
                                onFinished = { showSplash = false }
                            )
                        } else {
                            LiveDubApp(app = app)
                        }
                    }
                }
            }
        }
    }

    private fun applySystemBars(darkTheme: Boolean) {
        val barStyle = if (darkTheme) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        }
        enableEdgeToEdge(
            statusBarStyle = barStyle,
            navigationBarStyle = barStyle
        )
    }
}

private enum class ActiveAppScreen {
    SETUP_WIZARD,
    HOME,
    SETTINGS
}

@Composable
fun LiveDubApp(app: LiveDubApplication) {
    val container = app.container
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
    val availableModels by container.settingsRepository.cachedModels.collectAsStateWithLifecycle()
    val dubbingUiState by container.dubbingController.uiState.collectAsStateWithLifecycle()
    val strings = LocalAppStrings.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showSettings by rememberSaveable { mutableStateOf(false) }
    var forceSetupWizard by rememberSaveable { mutableStateOf(false) }
    var isLoadingModels by remember { mutableStateOf(false) }
    var modelFetchStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var lastAutoSyncedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var isTestingConnection by remember { mutableStateOf(false) }
    var connectionTestResult by remember { mutableStateOf<Result<LiveConnectionTestResult>?>(null) }
    var canDrawOverlay by remember { mutableStateOf(Settings.canDrawOverlays(app)) }
    val securityReport = remember { container.settingsRepository.getCyberSecurityReport() }

    val sendServiceCommandSafely: (String) -> Unit = { actionName ->
        try {
            val intent = Intent(app, DubbingService::class.java).apply {
                action = actionName
            }
            app.startService(intent)
        } catch (_: Throwable) {
        }
    }

    // Refresh overlay permission reactively when user returns from Android System Overlay Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = Settings.canDrawOverlays(app)
                if (granted != canDrawOverlay) {
                    canDrawOverlay = granted
                    if (granted && dubbingUiState.isServiceRunning) {
                        sendServiceCommandSafely(DubbingService.ACTION_UPDATE_RUNTIME_PREFS)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val openOverlayPermissionSettings: () -> Unit = {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${app.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            app.startActivity(intent)
        } catch (_: Throwable) {
        }
    }

    val notifyRuntimeSettingsChanged: () -> Unit = {
        if (dubbingUiState.isServiceRunning) {
            sendServiceCommandSafely(DubbingService.ACTION_UPDATE_RUNTIME_PREFS)
        }
    }

    val syncLatestDubbingModels: (String, Boolean) -> Unit = sync@{ keyToSync, manual ->
        val cleanKey = keyToSync.trim()
        if (cleanKey.isBlank()) {
            if (manual) modelFetchStatus = strings.needApiKeyForModels
            return@sync
        }
        if (isLoadingModels) return@sync
        isLoadingModels = true
        lastAutoSyncedKey = cleanKey
        modelFetchStatus = strings.fetchingModelsStatus
        scope.launch {
            val res = container.liveSessionClient.fetchBidiModels(cleanKey)
            isLoadingModels = false
            res.fold(
                onSuccess = { list ->
                    container.settingsRepository.saveFetchedModels(list)
                    modelFetchStatus = String.format(strings.syncedDubbingModelsStatus, list.size)
                    notifyRuntimeSettingsChanged()
                },
                onFailure = { err ->
                    modelFetchStatus = "${strings.statusErrorPrefix}: ${err.message}"
                }
            )
        }
    }

    // Automatically sync the latest live dubbing models on launch & whenever user enters a valid API key
    LaunchedEffect(settings.apiKey) {
        val trimmed = settings.apiKey.trim()
        if (trimmed.length >= 30 && trimmed != lastAutoSyncedKey) {
            delay(650L)
            syncLatestDubbingModels(trimmed, false)
        }
    }

    val startDubbingForegroundService = {
        val serviceIntent = Intent(app, DubbingService::class.java).apply {
            action = DubbingService.ACTION_START
        }
        ContextCompat.startForegroundService(app, serviceIntent)
    }

    val projectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            container.dubbingController.storeOneTimeProjectionConsent(result.resultCode, data)
            startDubbingForegroundService()
        } else {
            container.dubbingController.clearProjectionConsent()
            scope.launch {
                snackbarHostState.showSnackbar(strings.projectionDeniedMsg)
            }
        }
    }

    val requestCaptureOrStartFlow = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val mpManager = app.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(mpManager.createScreenCaptureIntent())
        } else {
            // Android 6.0 - 9.0 (API 23-28): start directly using hardware-AEC acoustic capture mode
            startDubbingForegroundService()
        }
    }

    val dubbingPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grantMap ->
        val recordGranted = grantMap[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (recordGranted) {
            requestCaptureOrStartFlow()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar(strings.recordAudioDeniedMsg)
            }
        }
    }

    val triggerStartDubbing: () -> Unit = {
        if (settings.apiKey.isBlank()) {
            showSettings = true
            scope.launch {
                snackbarHostState.showSnackbar(strings.enterApiKeyFirstMsg)
            }
        } else {
            val needed = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(app, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                needed.add(Manifest.permission.RECORD_AUDIO)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (needed.isNotEmpty()) {
                dubbingPermissionsLauncher.launch(needed.toTypedArray())
            } else {
                requestCaptureOrStartFlow()
            }
        }
    }

    val testConnectionAction: () -> Unit = test@{
        if (settings.apiKey.isBlank() || isTestingConnection) return@test
        isTestingConnection = true
        connectionTestResult = null
        scope.launch {
            val result = container.liveSessionClient.testLiveModelConnection(
                apiKey = settings.apiKey,
                modelName = settings.selectedModel,
                sourceLanguageCode = settings.sourceLanguageCode,
                targetLanguageCode = settings.targetLanguageCode
            )
            isTestingConnection = false
            connectionTestResult = result
        }
    }

    val activeScreen = when {
        !settings.onboardingCompleted || forceSetupWizard -> ActiveAppScreen.SETUP_WIZARD
        showSettings -> ActiveAppScreen.SETTINGS
        else -> ActiveAppScreen.HOME
    }

    val isDubbingStreaming = dubbingUiState.isServiceRunning && dubbingUiState.liveState is LiveSessionState.Streaming
    val combinedEnergy = maxOf(
        dubbingUiState.audioMetrics.captureLevelNorm,
        dubbingUiState.audioMetrics.playbackLevelNorm
    )
    val cs = MaterialTheme.colorScheme

    LiquidGlassBackground(
        isStreaming = isDubbingStreaming,
        audioEnergy = combinedEnergy
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = {
                SnackbarHost(snackbarHostState) { data ->
                    Snackbar(
                        snackbarData = data,
                        shape = RoundedCornerShape(18.dp),
                        containerColor = cs.surface.copy(alpha = 0.94f),
                        contentColor = cs.onSurface,
                        actionColor = cs.primary
                    )
                }
            }
        ) { innerPadding ->
            val emphasizedEase = remember { CubicBezierEasing(0.2f, 0f, 0f, 1f) }
            AnimatedContent(
                targetState = activeScreen,
                transitionSpec = {
                    val forward = targetState.ordinal >= initialState.ordinal
                    val slideIn = slideInHorizontally(
                        animationSpec = tween(300, easing = emphasizedEase),
                        initialOffsetX = { fullW -> if (forward) fullW / 5 else -fullW / 5 }
                    )
                    val slideOut = slideOutHorizontally(
                        animationSpec = tween(240, easing = emphasizedEase),
                        targetOffsetX = { fullW -> if (forward) -fullW / 6 else fullW / 6 }
                    )
                    (slideIn + fadeIn(tween(280, easing = emphasizedEase)) +
                        scaleIn(initialScale = 0.975f, animationSpec = tween(280, easing = emphasizedEase)))
                        .togetherWith(slideOut + fadeOut(tween(200, easing = emphasizedEase)))
                },
                label = "app_screen_transition",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) { screen ->
                when (screen) {
                    ActiveAppScreen.SETUP_WIZARD -> {
                        SetupWizardScreen(
                            settings = settings,
                            availableModels = availableModels,
                            isLoadingModels = isLoadingModels,
                            modelFetchStatus = modelFetchStatus,
                            isTestingConnection = isTestingConnection,
                            connectionTestResult = connectionTestResult,
                            onSelectAppLanguage = { code ->
                                container.settingsRepository.updateAppLanguageCode(code)
                            },
                            onApiKeyChange = { newKey ->
                                container.settingsRepository.updateApiKey(newKey)
                                connectionTestResult = null
                            },
                            onValidateAndFetchModels = { syncLatestDubbingModels(settings.apiKey, true) },
                            onAutoSelectLatestChange = { enabled ->
                                container.settingsRepository.updateAutoSelectLatestModel(enabled)
                            },
                            onSelectModel = { modelId ->
                                container.settingsRepository.updateSelectedModel(modelId, userManualSelection = true)
                                connectionTestResult = null
                            },
                            onSelectTargetLanguage = { lang ->
                                container.settingsRepository.updateTargetLanguageCode(lang.code)
                            },
                            onTestConnection = testConnectionAction,
                            onCompleteSetup = {
                                container.settingsRepository.updateOnboardingCompleted(true)
                                forceSetupWizard = false
                                showSettings = false
                            }
                        )
                    }

                    ActiveAppScreen.HOME -> {
                        HomeScreen(
                            uiState = dubbingUiState,
                            settings = settings,
                            canDrawOverlay = canDrawOverlay,
                            onSelectSourceLanguage = { lang ->
                                container.settingsRepository.updateSourceLanguageCode(lang.code)
                                notifyRuntimeSettingsChanged()
                            },
                            onSelectTargetLanguage = { lang ->
                                container.settingsRepository.updateTargetLanguageCode(lang.code)
                                notifyRuntimeSettingsChanged()
                            },
                            onSwapLanguages = {
                                container.settingsRepository.swapDubbingLanguages()
                                notifyRuntimeSettingsChanged()
                            },
                            onToggleOsdQuick = {
                                if (!canDrawOverlay) {
                                    container.settingsRepository.updateOsdEnabled(true)
                                    openOverlayPermissionSettings()
                                } else {
                                    container.settingsRepository.updateOsdEnabled(!settings.osdEnabled)
                                    notifyRuntimeSettingsChanged()
                                }
                            },
                            onCycleThemeMode = {
                                container.settingsRepository.cycleThemeMode()
                            },
                            onStartDubbing = triggerStartDubbing,
                            onTogglePause = {
                                sendServiceCommandSafely(DubbingService.ACTION_PAUSE_TOGGLE)
                            },
                            onStopDubbing = {
                                sendServiceCommandSafely(DubbingService.ACTION_STOP)
                            },
                            onOpenSettings = { showSettings = true },
                            onDismissBanner = { container.dubbingController.dismissBanner() }
                        )
                    }

                    ActiveAppScreen.SETTINGS -> {
                        SettingsScreen(
                            settings = settings,
                            availableModels = availableModels,
                            canDrawOverlay = canDrawOverlay,
                            isLoadingModels = isLoadingModels,
                            modelFetchStatus = modelFetchStatus,
                            isTestingConnection = isTestingConnection,
                            connectionTestResult = connectionTestResult,
                            cyberSecurityReport = securityReport,
                            onBack = { showSettings = false },
                            onOpenSetupWizard = {
                                showSettings = false
                                forceSetupWizard = true
                            },
                            onRequestOverlayPermission = openOverlayPermissionSettings,
                            onOsdEnabledChange = { enabled ->
                                container.settingsRepository.updateOsdEnabled(enabled)
                                notifyRuntimeSettingsChanged()
                            },
                            onOsdOpacityChange = { opacity ->
                                container.settingsRepository.updateOsdOpacityPercent(opacity)
                                notifyRuntimeSettingsChanged()
                            },
                            onOsdFontSizeChange = { preset ->
                                container.settingsRepository.updateOsdFontSize(preset)
                                notifyRuntimeSettingsChanged()
                            },
                            onOsdShowOriginalChange = { show ->
                                container.settingsRepository.updateOsdShowOriginalText(show)
                                notifyRuntimeSettingsChanged()
                            },
                            onOsdCompactChange = { compact ->
                                container.settingsRepository.updateOsdCompactMode(compact)
                                notifyRuntimeSettingsChanged()
                            },
                            onSelectAppLanguage = { code ->
                                container.settingsRepository.updateAppLanguageCode(code)
                            },
                            onSelectSourceLanguage = { lang ->
                                container.settingsRepository.updateSourceLanguageCode(lang.code)
                                notifyRuntimeSettingsChanged()
                            },
                            onSelectTargetLanguage = { lang ->
                                container.settingsRepository.updateTargetLanguageCode(lang.code)
                                notifyRuntimeSettingsChanged()
                            },
                            onApiKeyChange = { newKey ->
                                container.settingsRepository.updateApiKey(newKey)
                                connectionTestResult = null
                            },
                            onAutoSelectLatestChange = { enabled ->
                                container.settingsRepository.updateAutoSelectLatestModel(enabled)
                                notifyRuntimeSettingsChanged()
                            },
                            onSelectModel = { newModel ->
                                container.settingsRepository.updateSelectedModel(newModel, userManualSelection = true)
                                connectionTestResult = null
                                notifyRuntimeSettingsChanged()
                            },
                            onRefreshModels = { syncLatestDubbingModels(settings.apiKey, true) },
                            onTestConnection = testConnectionAction,
                            onDubVolumeChange = { vol ->
                                container.settingsRepository.updateDubVolumePercent(vol)
                                notifyRuntimeSettingsChanged()
                            },
                            onDuckAudioChange = { duck ->
                                container.settingsRepository.updateDuckOriginalAudio(duck)
                                notifyRuntimeSettingsChanged()
                            },
                            onBluetoothDubOnlyChange = { btOnly ->
                                container.settingsRepository.updateBluetoothDubOnlyRouting(btOnly)
                                notifyRuntimeSettingsChanged()
                            },
                            onEchoTargetChange = { echo ->
                                container.settingsRepository.updateEchoTargetLanguage(echo)
                                notifyRuntimeSettingsChanged()
                            },
                            onSilenceDetectionChange = { enabled ->
                                container.settingsRepository.updateSilenceDetection(enabled)
                                notifyRuntimeSettingsChanged()
                            },
                            onHapticFeedbackChange = { enabled ->
                                container.settingsRepository.updateHapticFeedbackEnabled(enabled)
                            },
                            onAutoReconnectChange = { enabled ->
                                container.settingsRepository.updateAutoReconnect(enabled)
                                notifyRuntimeSettingsChanged()
                            },
                            onThemeModeChange = { mode ->
                                container.settingsRepository.updateThemeMode(mode)
                            }
                        )
                    }
                }
            }
        }
    }
}
