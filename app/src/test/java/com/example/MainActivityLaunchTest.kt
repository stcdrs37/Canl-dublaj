package com.example

import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * End-to-end cold launch regression test verifying MainActivity + LiveDubApplication
 * opens visibly into SplashIntroScreen, transitions cleanly into SetupWizardScreen,
 * and NEVER sets FLAG_SECURE (which blacks out streaming emulators & virtual displays).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityLaunchTest {

    @get:Rule
    val activityRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainActivity_coldLaunch_rendersVisibleSplashAndTransitionsToSetupWizardWithoutBlackScreen() {
        // Control virtual clock so the finite 6-second SplashIntroScreen animation
        // doesn't auto-complete before we verify its rendered hierarchy.
        activityRule.mainClock.autoAdvance = false
        activityRule.mainClock.advanceTimeBy(300L)

        // 1. Splash screen must render visibly on cold launch
        activityRule.onNodeWithTag("splash_intro_root").assertIsDisplayed()
        activityRule.onNodeWithTag("splash_skip_button").assertIsDisplayed()

        // 2. Skip splash -> SetupWizard must appear immediately
        activityRule.onNodeWithTag("splash_skip_button").performClick()
        activityRule.mainClock.autoAdvance = true
        activityRule.waitForIdle()
        activityRule.onNodeWithTag("wizard_next_button").assertIsDisplayed()

        // 3. Window must NEVER carry FLAG_SECURE (which causes pitch-black screens on virtual displays)
        activityRule.activityRule.scenario.onActivity { activity ->
            val secureFlag = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE
            assertEquals("FLAG_SECURE must not black out activity window", 0, secureFlag)
        }
    }
}
