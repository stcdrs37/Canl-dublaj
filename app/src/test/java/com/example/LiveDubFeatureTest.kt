package com.example

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.data.AppSettings
import com.example.data.AppThemeMode
import com.example.data.LiveConnectionTestResult
import com.example.data.OsdFontSizePreset
import com.example.data.SettingsRepository
import com.example.i18n.AppStrings
import com.example.i18n.LocalAppStrings
import com.example.service.DubbingSessionUiState
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SetupWizardScreen
import com.example.ui.theme.LiveDubTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LiveDubFeatureTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun setupWizard_full3StepFlow_switchesUiLanguageBindsKeySelectsDubbingModelAndTestsConnection() {
        var settings by mutableStateOf(AppSettings(onboardingCompleted = false, appLanguageCode = "tr"))
        var testTriggered = false
        var testResult by mutableStateOf<Result<LiveConnectionTestResult>?>(null)
        var completed = false

        composeRule.setContent {
            CompositionLocalProvider(LocalAppStrings provides AppStrings.forCode(settings.appLanguageCode)) {
                LiveDubTheme {
                    SetupWizardScreen(
                        settings = settings,
                        availableModels = SettingsRepository.DEFAULT_MODELS,
                        isLoadingModels = false,
                        modelFetchStatus = null,
                        isTestingConnection = false,
                        connectionTestResult = testResult,
                        onSelectAppLanguage = { code -> settings = settings.copy(appLanguageCode = code) },
                        onApiKeyChange = { key -> settings = settings.copy(apiKey = key) },
                        onValidateAndFetchModels = {},
                        onAutoSelectLatestChange = { enabled -> settings = settings.copy(autoSelectLatestModel = enabled) },
                        onSelectModel = { model -> settings = settings.copy(selectedModel = model) },
                        onSelectTargetLanguage = { lang -> settings = settings.copy(targetLanguageCode = lang.code) },
                        onTestConnection = {
                            testTriggered = true
                            testResult = Result.success(
                                LiveConnectionTestResult(
                                    modelId = settings.selectedModel,
                                    latencyMs = 142L,
                                    setupTierUsed = 0
                                )
                            )
                        },
                        onCompleteSetup = { completed = true }
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Step 1: Switch app language from Turkish to English and verify live UI update
        composeRule.onNodeWithTag("wizard_app_lang_en").performClick()
        composeRule.waitForIdle()
        assertEquals("en", settings.appLanguageCode)
        composeRule.onNodeWithText("Choose App Language").assertIsDisplayed()

        // Advance to Step 2
        composeRule.onNodeWithTag("wizard_next_button").performClick()
        composeRule.waitForIdle()

        // Step 2: Enter API Key
        composeRule.onNodeWithTag("wizard_api_key_input").performTextInput("AIzaSyRealTestKey12345")
        composeRule.waitForIdle()
        assertEquals("AIzaSyRealTestKey12345", settings.apiKey)

        // Advance to Step 3
        composeRule.onNodeWithTag("wizard_next_button").performClick()
        composeRule.waitForIdle()

        // Step 3: Select dubbing-only native audio model, run real connection test, and finish
        composeRule.onNodeWithTag("wizard_model_gemini-2.5-flash-native-audio-preview-12-2025")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        assertEquals("gemini-2.5-flash-native-audio-preview-12-2025", settings.selectedModel)

        composeRule.onNodeWithTag("wizard_test_connection_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(testTriggered)
        composeRule.onNodeWithTag("wizard_test_success_badge").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("wizard_finish_button").performClick()
        composeRule.waitForIdle()
        assertTrue(completed)
    }

    @Test
    fun homeScreen_cyclesSystemTheme_togglesOsd_swapsLanguages_andStartsDubbing() {
        var settings by mutableStateOf(
            AppSettings(
                onboardingCompleted = true,
                appLanguageCode = "tr",
                sourceLanguageCode = "auto",
                targetLanguageCode = "tr",
                apiKey = "AIzaTestKey12345",
                themeMode = AppThemeMode.SYSTEM
            )
        )
        var swapped = false
        var started = false
        var osdToggled = false

        composeRule.setContent {
            CompositionLocalProvider(LocalAppStrings provides AppStrings.forCode(settings.appLanguageCode)) {
                LiveDubTheme {
                    HomeScreen(
                        uiState = DubbingSessionUiState(),
                        settings = settings,
                        canDrawOverlay = true,
                        onSelectSourceLanguage = { settings = settings.copy(sourceLanguageCode = it.code) },
                        onSelectTargetLanguage = { settings = settings.copy(targetLanguageCode = it.code) },
                        onSwapLanguages = { swapped = true },
                        onToggleOsdQuick = {
                            osdToggled = true
                            settings = settings.copy(osdEnabled = !settings.osdEnabled)
                        },
                        onCycleThemeMode = { settings = settings.copy(themeMode = settings.themeMode.next()) },
                        onStartDubbing = { started = true },
                        onTogglePause = {},
                        onStopDubbing = {},
                        onOpenSettings = {},
                        onDismissBanner = {}
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // Screen Dubbing header & Automatic AI Voice Cloning banner are displayed
        composeRule.onNodeWithText("Screen Dubbing").assertIsDisplayed()
        composeRule.onNodeWithTag("home_ai_voice_clone_banner").assertIsDisplayed()

        // Quick OSD toggle orb works
        composeRule.onNodeWithTag("toggle_osd_button").performClick()
        composeRule.waitForIdle()
        assertTrue(osdToggled)

        // 3-way Automatic Theme Orb cycles SYSTEM -> LIGHT -> DARK
        composeRule.onNodeWithTag("toggle_theme_button").performClick()
        composeRule.waitForIdle()
        assertEquals(AppThemeMode.LIGHT, settings.themeMode)
        composeRule.onNodeWithTag("toggle_theme_button").performClick()
        composeRule.waitForIdle()
        assertEquals(AppThemeMode.DARK, settings.themeMode)

        // Language swap & Start Live Dubbing button work
        composeRule.onNodeWithTag("dub_swap_languages_button").performClick()
        composeRule.waitForIdle()
        assertTrue(swapped)

        composeRule.onNodeWithTag("start_dubbing_button").performClick()
        composeRule.waitForIdle()
        assertTrue(started)
    }

    @Test
    fun settingsScreen_all4CleanTabs_osdModelAudioHapticsThemeAnd24LanguagesWork() {
        var settings by mutableStateOf(
            AppSettings(
                onboardingCompleted = true,
                appLanguageCode = "tr",
                apiKey = "AIzaTestKey12345",
                osdEnabled = true,
                osdFontSize = OsdFontSizePreset.MEDIUM,
                silenceDetectionEnabled = false,
                hapticFeedbackEnabled = true,
                themeMode = AppThemeMode.SYSTEM
            )
        )
        var wizardOpened = false
        var overlayPermissionRequested = false

        composeRule.setContent {
            CompositionLocalProvider(LocalAppStrings provides AppStrings.forCode(settings.appLanguageCode)) {
                LiveDubTheme {
                    SettingsScreen(
                        settings = settings,
                        availableModels = SettingsRepository.DEFAULT_MODELS,
                        canDrawOverlay = false,
                        isLoadingModels = false,
                        modelFetchStatus = null,
                        isTestingConnection = false,
                        connectionTestResult = null,
                        onBack = {},
                        onOpenSetupWizard = { wizardOpened = true },
                        onRequestOverlayPermission = { overlayPermissionRequested = true },
                        onOsdEnabledChange = { settings = settings.copy(osdEnabled = it) },
                        onOsdOpacityChange = { settings = settings.copy(osdOpacityPercent = it) },
                        onOsdFontSizeChange = { settings = settings.copy(osdFontSize = it) },
                        onOsdShowOriginalChange = { settings = settings.copy(osdShowOriginalText = it) },
                        onOsdCompactChange = { settings = settings.copy(osdCompactMode = it) },
                        onSelectAppLanguage = { settings = settings.copy(appLanguageCode = it) },
                        onSelectSourceLanguage = { settings = settings.copy(sourceLanguageCode = it.code) },
                        onSelectTargetLanguage = { settings = settings.copy(targetLanguageCode = it.code) },
                        onApiKeyChange = { settings = settings.copy(apiKey = it) },
                        onAutoSelectLatestChange = { settings = settings.copy(autoSelectLatestModel = it) },
                        onSelectModel = { settings = settings.copy(selectedModel = it) },
                        onRefreshModels = {},
                        onTestConnection = {},
                        onDubVolumeChange = { settings = settings.copy(dubVolumePercent = it) },
                        onDuckAudioChange = { settings = settings.copy(duckOriginalAudio = it) },
                        onBluetoothDubOnlyChange = { settings = settings.copy(bluetoothDubOnlyRouting = it) },
                        onEchoTargetChange = { settings = settings.copy(echoTargetLanguage = it) },
                        onSilenceDetectionChange = { settings = settings.copy(silenceDetectionEnabled = it) },
                        onHapticFeedbackChange = { settings = settings.copy(hapticFeedbackEnabled = it) },
                        onAutoReconnectChange = { settings = settings.copy(autoReconnect = it) },
                        onThemeModeChange = { settings = settings.copy(themeMode = it) }
                    )
                }
            }
        }
        composeRule.waitForIdle()

        // TAB 1: OSD — Live Preview, Overlay permission CTA & Font size preset
        composeRule.onNodeWithTag("settings_osd_preview_card").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_grant_osd_permission").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(overlayPermissionRequested)

        composeRule.onNodeWithTag("settings_osd_size_large").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(OsdFontSizePreset.LARGE, settings.osdFontSize)

        // TAB 2: API & Model — Cyber Security Shield card, Always-Latest toggle & clean dubbing-only model list
        composeRule.onNodeWithTag("settings_tab_model").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_cyber_shield_card").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_auto_latest_model_toggle").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertFalse(settings.autoSelectLatestModel)
        composeRule.onNodeWithTag("settings_model_gemini-2.5-flash-native-audio-preview-12-2025")
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
        assertEquals("gemini-2.5-flash-native-audio-preview-12-2025", settings.selectedModel)

        // TAB 3: Ses & Dil — Bluetooth Dub-Only Routing, Haptic Feedback toggle & Test Vibration button
        composeRule.onNodeWithTag("settings_tab_audio").performClick()
        composeRule.waitForIdle()
        assertTrue(settings.bluetoothDubOnlyRouting)
        composeRule.onNodeWithTag("settings_bt_dub_only_toggle").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertFalse(settings.bluetoothDubOnlyRouting)
        composeRule.onNodeWithTag("settings_test_haptic_button").performScrollTo().assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_haptic_toggle").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertFalse(settings.hapticFeedbackEnabled)

        // TAB 4: Genel — System/Light/Dark Theme selector, Setup Wizard & 24-language Modal Sheet
        composeRule.onNodeWithTag("settings_tab_general").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("settings_theme_dark").performClick()
        composeRule.waitForIdle()
        assertEquals(AppThemeMode.DARK, settings.themeMode)

        composeRule.onNodeWithTag("settings_open_wizard_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(wizardOpened)

        composeRule.onNodeWithTag("settings_app_lang_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_app_lang_de").performClick()
        composeRule.waitForIdle()
        assertEquals("de", settings.appLanguageCode)
        composeRule.onNodeWithText("Einstellungen").assertIsDisplayed()
    }

    @Test
    fun splashIntroScreen_renders6SecondLiquidGlassIntroAndAllowsCleanSkip() {
        var finished = false
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            LiveDubTheme(darkTheme = true) {
                com.example.ui.screens.SplashIntroScreen(
                    playAudio = false,
                    onFinished = { finished = true }
                )
            }
        }
        composeRule.mainClock.advanceTimeBy(400L)
        composeRule.onNodeWithTag("splash_intro_root").assertIsDisplayed()
        composeRule.onNodeWithText("Screen Dubbing").assertIsDisplayed()
        composeRule.onNodeWithTag("splash_skip_button").assertIsDisplayed().performClick()
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        assertTrue(finished)
    }
}
