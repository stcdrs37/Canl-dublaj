package com.example.util

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View
import java.util.concurrent.atomic.AtomicLong

/**
 * Reliable Cross-OEM Tactile Haptic Vibration Engine (Android 6.0 / API 23 -> Android 16 / API 36).
 *
 * Fixes OEM/Android 13+ silent haptic suppression by passing explicit `VibrationAttributes`
 * (`USAGE_MEDIA` on API 33+) and `AudioAttributes` (`USAGE_ASSISTANCE_SONIFICATION` on API 26-32),
 * plus automatic fallback to `createOneShot` when a motor lacks waveform/amplitude support.
 */
object HapticFeedbackHelper {

    internal val DUB_START_TIMINGS = longArrayOf(0L, 65L, 55L, 95L)
    internal val DUB_START_AMPLITUDES = intArrayOf(0, 220, 0, 255)

    internal val DUB_RESUME_TIMINGS = longArrayOf(0L, 55L)
    internal val DUB_RESUME_AMPLITUDES = intArrayOf(0, 215)

    internal val DUB_STOP_TIMINGS = longArrayOf(0L, 38L)
    internal val DUB_STOP_AMPLITUDES = intArrayOf(0, 175)

    internal val AI_VOICE_TURN_TIMINGS = longArrayOf(0L, 42L)
    internal val AI_VOICE_TURN_AMPLITUDES = intArrayOf(0, 195)

    internal val SOFT_TAP_TIMINGS = longArrayOf(0L, 28L)
    internal val SOFT_TAP_AMPLITUDES = intArrayOf(0, 170)

    private const val AI_TURN_COOLDOWN_MS = 1200L
    private val lastAiTurnVibrateMs = AtomicLong(0L)

    /**
     * Crisp, unmistakable double-pulse signature haptic when AI Screen Dubbing starts.
     */
    fun vibrateDubbingStart(context: Context, enabled: Boolean = true): Boolean {
        if (!enabled) return false
        return triggerWaveform(context, DUB_START_TIMINGS, DUB_START_AMPLITUDES, fallbackDurationMs = 95L)
    }

    /**
     * Refined tactile tick whenever AI dubbing voice starts speaking a new translated turn.
     * Rate-limited by a 1.2s cooldown so continuous chunks feel smooth and never buzz repeatedly.
     */
    fun vibrateAiVoiceTurnStart(
        context: Context,
        enabled: Boolean = true,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (!enabled) return false
        val prev = lastAiTurnVibrateMs.get()
        if (nowMs - prev < AI_TURN_COOLDOWN_MS) return false
        if (!lastAiTurnVibrateMs.compareAndSet(prev, nowMs)) return false
        return triggerOneShot(context, durationMs = 42L, amplitude = 195)
    }

    fun resetTurnCooldown() {
        lastAiTurnVibrateMs.set(0L)
    }

    /**
     * Single firm tactile pulse when resuming live dubbing from pause.
     */
    fun vibrateDubbingResume(context: Context, enabled: Boolean = true): Boolean {
        if (!enabled) return false
        return triggerOneShot(context, durationMs = 55L, amplitude = 215)
    }

    /**
     * Clean closing tactile pulse when stopping live dubbing.
     */
    fun vibrateDubbingStop(context: Context, enabled: Boolean = true): Boolean {
        if (!enabled) return false
        return triggerOneShot(context, durationMs = 38L, amplitude = 175)
    }

    /**
     * Subtle crisp tap pulse for OSD hide/restore, language swaps, and primary glass controls.
     */
    fun vibrateSoftTap(context: Context, enabled: Boolean = true, view: View? = null): Boolean {
        if (!enabled) return false
        if (view != null) {
            try {
                @Suppress("DEPRECATION")
                view.performHapticFeedback(
                    HapticFeedbackConstants.CLOCK_TICK,
                    HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING
                )
            } catch (_: Throwable) {
            }
        }
        return triggerOneShot(context, durationMs = 28L, amplitude = 170)
    }

    private fun triggerOneShot(
        context: Context,
        durationMs: Long,
        amplitude: Int
    ): Boolean {
        return try {
            val vibrator = resolveVibrator(context) ?: return false
            if (!vibrator.hasVibrator()) return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val safeAmp = if (vibrator.hasAmplitudeControl()) {
                    amplitude.coerceIn(1, 255)
                } else {
                    VibrationEffect.DEFAULT_AMPLITUDE
                }
                val effect = VibrationEffect.createOneShot(durationMs.coerceAtLeast(10L), safeAmp)
                vibrateWithAttributes(vibrator, effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(durationMs.coerceAtLeast(10L))
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun triggerWaveform(
        context: Context,
        timings: LongArray,
        amplitudes: IntArray,
        fallbackDurationMs: Long
    ): Boolean {
        return try {
            val vibrator = resolveVibrator(context) ?: return false
            if (!vibrator.hasVibrator()) return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = try {
                    if (vibrator.hasAmplitudeControl()) {
                        VibrationEffect.createWaveform(timings, amplitudes, -1)
                    } else {
                        VibrationEffect.createWaveform(timings, -1)
                    }
                } catch (_: Throwable) {
                    VibrationEffect.createOneShot(fallbackDurationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                }
                vibrateWithAttributes(vibrator, effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(timings, -1)
            }
            true
        } catch (_: Throwable) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrateWithAttributes(vibrator: Vibrator, effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val attrs = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_MEDIA)
                .build()
            vibrator.vibrate(effect, attrs)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            vibrator.vibrate(effect, audioAttrs)
        }
    }

    private fun resolveVibrator(context: Context): Vibrator? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val defaultVib = vm?.defaultVibrator
                if (defaultVib != null && defaultVib.hasVibrator()) {
                    defaultVib
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: defaultVib
                }
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Throwable) {
            null
        }
    }
}
