package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.AppSettings
import com.example.data.LanguageItem
import com.example.live.LiveSessionState
import com.example.util.HapticFeedbackHelper
import kotlin.math.abs

/**
 * Premium Floating Liquid Glass OSD (On-Screen Display) Overlay for Screen Dubbing.
 * Floats cleanly above YouTube, Netflix, Twitch, or any video app without stealing focus:
 * - Translucent Liquid Glass capsule with luminous specular rim & pen handwriting typography
 * - Smooth drag-to-reposition anywhere on screen
 * - Dedicated HIDE (`—`) button collapses the OSD into an unobtrusive draggable mini-pill
 * - Real-time status dot, language bridge pill (e.g. EN → TR), Bluetooth Dub-Only badge (`BT`),
 *   latency badge, dual live subtitles, and crisp haptic feedback on every button
 */
class DubbingOsdOverlayManager(
    private val context: Context,
    private val onTogglePause: () -> Unit,
    private val onStopDubbing: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var rootContainer: LinearLayout? = null
    private var expandedContent: LinearLayout? = null
    private var collapsedPill: LinearLayout? = null
    private var collapsedDotView: View? = null
    private var statusDotView: View? = null
    private var headerBadgeView: TextView? = null
    private var latencyBadgeView: TextView? = null
    private var inputCaptionView: TextView? = null
    private var outputCaptionView: TextView? = null
    private var hideActionView: TextView? = null
    private var pauseActionView: TextView? = null
    private var stopActionView: TextView? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var isShowing = false
    private var collapsed = false
    private var lastState: DubbingSessionUiState? = null
    private var lastSettings: AppSettings? = null

    private val handwritingTypeface: Typeface by lazy {
        try {
            ResourcesCompat.getFont(context, R.font.patrick_hand)
                ?: Typeface.create("sans-serif-medium", Typeface.NORMAL)
        } catch (_: Throwable) {
            Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
    }

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    fun isCollapsed(): Boolean = collapsed

    fun setCollapsed(hide: Boolean) {
        if (collapsed == hide) return
        collapsed = hide
        applyCollapsedMode(lastSettings)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun showOrUpdate(state: DubbingSessionUiState, settings: AppSettings) {
        lastState = state
        lastSettings = settings
        if (!settings.osdEnabled || !canDrawOverlays()) {
            dismiss()
            return
        }

        if (!isShowing || rootContainer == null) {
            createAndAttachOverlay(settings)
        }
        bindState(state, settings)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createAndAttachOverlay(settings: AppSettings) {
        val density = context.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            elevation = dp(14).toFloat()
        }

        // --- Expanded Full Liquid Glass OSD Card ---
        val expanded = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
        }

        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val dot = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply {
                marginEnd = dp(7)
            }
        }
        statusDotView = dot

        val headerBadge = TextView(context).apply {
            setTextColor(Color.parseColor("#EEF5FF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = handwritingTypeface
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerBadgeView = headerBadge

        val latencyPill = TextView(context).apply {
            setTextColor(Color.parseColor("#6CF2DE"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = handwritingTypeface
            setPadding(dp(7), dp(2), dp(7), dp(2))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(Color.parseColor("#2E00B4D8"))
                setStroke(dp(1), Color.parseColor("#666CF2DE"))
            }
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(5) }
        }
        latencyBadgeView = latencyPill

        // Hide / Collapse OSD button (`—`) — hides overlay into a tiny restore pill without stopping dubbing!
        val hideBtn = TextView(context).apply {
            text = "—"
            contentDescription = "Gizle"
            setTextColor(Color.parseColor("#CBD5E1"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            minWidth = dp(32)
            minHeight = dp(28)
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#29FFFFFF"))
                setStroke(dp(1), Color.parseColor("#55FFFFFF"))
            }
            setOnClickListener { view ->
                HapticFeedbackHelper.vibrateSoftTap(context, lastSettings?.hapticFeedbackEnabled ?: true, view)
                setCollapsed(true)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(5) }
        }
        hideActionView = hideBtn

        val pauseBtn = TextView(context).apply {
            text = "❚❚"
            contentDescription = "Duraklat"
            setTextColor(Color.parseColor("#FBBF24"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            minWidth = dp(32)
            minHeight = dp(28)
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#2CF59E0B"))
                setStroke(dp(1), Color.parseColor("#77F59E0B"))
            }
            setOnClickListener { view ->
                HapticFeedbackHelper.vibrateSoftTap(context, lastSettings?.hapticFeedbackEnabled ?: true, view)
                onTogglePause()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(5) }
        }
        pauseActionView = pauseBtn

        val stopBtn = TextView(context).apply {
            text = "■"
            contentDescription = "Durdur"
            setTextColor(Color.parseColor("#FF7A8A"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            gravity = Gravity.CENTER
            minWidth = dp(32)
            minHeight = dp(28)
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#33E11D48"))
                setStroke(dp(1), Color.parseColor("#77FF7A8A"))
            }
            setOnClickListener { view ->
                HapticFeedbackHelper.vibrateSoftTap(context, lastSettings?.hapticFeedbackEnabled ?: true, view)
                onStopDubbing()
            }
        }
        stopActionView = stopBtn

        headerRow.addView(dot)
        headerRow.addView(headerBadge)
        headerRow.addView(latencyPill)
        headerRow.addView(hideBtn)
        headerRow.addView(pauseBtn)
        headerRow.addView(stopBtn)
        expanded.addView(headerRow)

        val inputTv = TextView(context).apply {
            setTextColor(Color.parseColor("#C2D4F2"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, settings.osdFontSize.inputSp)
            typeface = handwritingTypeface
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.START
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
        }
        inputCaptionView = inputTv
        expanded.addView(inputTv)

        val outputTv = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, settings.osdFontSize.outputSp)
            typeface = handwritingTypeface
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.START
            setShadowLayer(4f, 0f, 1.5f, Color.parseColor("#99000000"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(2) }
        }
        outputCaptionView = outputTv
        expanded.addView(outputTv)

        // --- Collapsed Ultra-Compact Restore Glass Pill (tap to show full OSD again!) ---
        val miniPill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(7), dp(12), dp(7))
            visibility = View.GONE
        }
        val miniDot = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply {
                marginEnd = dp(6)
            }
        }
        collapsedDotView = miniDot
        val miniLabel = TextView(context).apply {
            text = "Screen Dubbing ▾"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            typeface = handwritingTypeface
        }
        miniPill.addView(miniDot)
        miniPill.addView(miniLabel)

        root.addView(expanded)
        root.addView(miniPill)
        expandedContent = expanded
        collapsedPill = miniPill

        val screenWidth = context.resources.displayMetrics.widthPixels
        val overlayWidth = (screenWidth * 0.90f).toInt().coerceAtMost(dp(420))

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            if (collapsed) WindowManager.LayoutParams.WRAP_CONTENT else overlayWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = dp(76)
        }

        // Smooth drag-to-reposition + tap-to-restore when collapsed
        var initX = 0
        var initY = 0
        var touchX = 0f
        var touchY = 0f
        var dragging = false
        root.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initX = params.x
                    initY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > 6 || abs(dy) > 6) dragging = true
                    if (dragging) {
                        params.x = initX + dx
                        params.y = (initY + dy).coerceAtLeast(dp(24))
                        try {
                            windowManager.updateViewLayout(root, params)
                        } catch (_: Throwable) {
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging && collapsed) {
                        HapticFeedbackHelper.vibrateSoftTap(context, lastSettings?.hapticFeedbackEnabled ?: true, v)
                        setCollapsed(false)
                    }
                    dragging
                }
                else -> dragging
            }
        }

        try {
            windowManager.addView(root, params)
            rootContainer = root
            layoutParams = params
            isShowing = true
            applyCollapsedMode(settings)
        } catch (_: Throwable) {
            rootContainer = null
            expandedContent = null
            collapsedPill = null
            layoutParams = null
            isShowing = false
        }
    }

    private fun applyCollapsedMode(settings: AppSettings?) {
        val root = rootContainer ?: return
        val params = layoutParams ?: return
        val density = context.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        expandedContent?.visibility = if (collapsed) View.GONE else View.VISIBLE
        collapsedPill?.visibility = if (collapsed) View.VISIBLE else View.GONE

        val screenWidth = context.resources.displayMetrics.widthPixels
        val expandedWidth = (screenWidth * 0.90f).toInt().coerceAtMost(dp(420))
        params.width = if (collapsed) WindowManager.LayoutParams.WRAP_CONTENT else expandedWidth
        try {
            windowManager.updateViewLayout(root, params)
        } catch (_: Throwable) {
        }
        val st = lastState
        val s = settings ?: lastSettings
        if (st != null && s != null) {
            bindState(st, s)
        }
    }

    private fun bindState(state: DubbingSessionUiState, settings: AppSettings) {
        val root = rootContainer ?: return
        val density = context.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val bgAlpha = ((settings.osdOpacityPercent.coerceIn(50, 100) / 100f) * 214).toInt().coerceIn(96, 220)
        val topGlass = Color.argb(bgAlpha, 18, 28, 52)
        val midGlass = Color.argb((bgAlpha * 0.88f).toInt(), 11, 18, 36)
        val bottomGlass = Color.argb((bgAlpha * 0.95f).toInt(), 9, 14, 28)
        val rimColor = when {
            state.liveState is LiveSessionState.Error -> Color.parseColor("#CCFF5C77")
            state.isPaused -> Color.parseColor("#CCF59E0B")
            state.audioMetrics.isBluetoothDubOnlyActive -> Color.parseColor("#CC60A5FA")
            state.audioMetrics.isAudioDetected -> Color.parseColor("#BB3EEAD6")
            else -> Color.parseColor("#9988BBFF")
        }

        root.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(topGlass, midGlass, bottomGlass)
        ).apply {
            cornerRadius = dp(if (collapsed) 24 else if (settings.osdCompactMode) 18 else 22).toFloat()
            setStroke((1.35f * density).toInt().coerceAtLeast(2), rimColor)
        }

        val dotColor = when {
            state.liveState is LiveSessionState.Error -> Color.parseColor("#FF5C77")
            state.isPaused -> Color.parseColor("#F59E0B")
            state.liveState is LiveSessionState.Streaming -> Color.parseColor("#10B981")
            else -> Color.parseColor("#6AA8FF")
        }
        val dotDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(dotColor)
        }
        statusDotView?.background = dotDrawable
        collapsedDotView?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(dotColor)
        }

        val targetLang = LanguageItem.byCode(settings.targetLanguageCode, allowAuto = false)
        val sourceTag = state.detectedSourceLanguage.substringBefore(" ").uppercase()
            .takeIf { it.length in 2..4 } ?: "AUTO"
        val routeTag = when {
            state.isPaused -> "PAUSED"
            state.audioMetrics.isBluetoothDubOnlyActive -> "BT DUB"
            else -> "AI DUB"
        }
        headerBadgeView?.text = "Screen Dubbing • $sourceTag → ${targetLang.code.uppercase()} • $routeTag"

        val lat = state.firstAudioLatencyMs
        latencyBadgeView?.apply {
            if (lat != null) {
                text = "${lat}ms"
                visibility = View.VISIBLE
            } else {
                visibility = View.GONE
            }
        }

        pauseActionView?.apply {
            text = if (state.isPaused) "▶" else "❚❚"
            setTextColor(if (state.isPaused) Color.parseColor("#10B981") else Color.parseColor("#FBBF24"))
        }

        val showOrig = settings.osdShowOriginalText && !settings.osdCompactMode && state.currentInputLiveText.isNotBlank()
        inputCaptionView?.apply {
            visibility = if (showOrig) View.VISIBLE else View.GONE
            if (showOrig) {
                text = state.currentInputLiveText
                setTextSize(TypedValue.COMPLEX_UNIT_SP, settings.osdFontSize.inputSp)
            }
        }

        outputCaptionView?.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, settings.osdFontSize.outputSp)
            maxLines = if (settings.osdCompactMode) 1 else 3
            text = state.currentOutputLiveText.ifBlank {
                if (state.isPaused) "⏸" else "● Screen Dubbing AI..."
            }
        }
    }

    fun dismiss() {
        val view = rootContainer
        rootContainer = null
        expandedContent = null
        collapsedPill = null
        collapsedDotView = null
        layoutParams = null
        statusDotView = null
        headerBadgeView = null
        latencyBadgeView = null
        inputCaptionView = null
        outputCaptionView = null
        hideActionView = null
        pauseActionView = null
        stopActionView = null
        isShowing = false
        collapsed = false
        if (view != null) {
            try {
                windowManager.removeViewImmediate(view)
            } catch (_: Throwable) {
                try {
                    windowManager.removeView(view)
                } catch (_: Throwable) {
                }
            }
        }
    }
}
