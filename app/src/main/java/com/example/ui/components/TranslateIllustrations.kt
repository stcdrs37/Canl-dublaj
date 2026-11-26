package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.AppThemeMode

// =========================================================================
// Lightweight Custom Vector Icons for AeroGlass 60 Liquid Glass UI
// Zero dependency on material-icons-extended (keeps APK small & crisp).
// =========================================================================

@Composable
fun SwapArrowsIcon(
    tint: Color,
    size: Dp = 22.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = w * 0.09f
        drawLine(tint, Offset(w * 0.18f, h * 0.34f), Offset(w * 0.80f, h * 0.34f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.64f, h * 0.20f), Offset(w * 0.82f, h * 0.34f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.64f, h * 0.48f), Offset(w * 0.82f, h * 0.34f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.20f, h * 0.66f), Offset(w * 0.82f, h * 0.66f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.36f, h * 0.52f), Offset(w * 0.18f, h * 0.66f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.36f, h * 0.80f), Offset(w * 0.18f, h * 0.66f), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

/**
 * 3-State Theme Mode Icon:
 * - SYSTEM: Half-lit crystal contrast circle (Auto-System)
 * - LIGHT: Radiant sun
 * - DARK: Crescent moon
 */
@Composable
fun ThemeModeIcon(
    themeMode: AppThemeMode,
    tint: Color,
    size: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = w * 0.09f
        when (themeMode) {
            AppThemeMode.SYSTEM -> {
                drawCircle(
                    color = tint,
                    radius = w * 0.36f,
                    center = Offset(w * 0.5f, h * 0.5f),
                    style = Stroke(width = sw)
                )
                drawArc(
                    color = tint,
                    startAngle = -90f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = Offset(w * 0.14f, h * 0.14f),
                    size = Size(w * 0.72f, h * 0.72f)
                )
            }
            AppThemeMode.LIGHT -> {
                drawCircle(color = tint, radius = w * 0.20f, center = Offset(w * 0.5f, h * 0.5f))
                val rayDist1 = w * 0.30f
                val rayDist2 = w * 0.42f
                for (angleDeg in 0 until 360 step 45) {
                    val rad = Math.toRadians(angleDeg.toDouble())
                    val cosA = kotlin.math.cos(rad).toFloat()
                    val sinA = kotlin.math.sin(rad).toFloat()
                    drawLine(
                        color = tint,
                        start = Offset(w * 0.5f + cosA * rayDist1, h * 0.5f + sinA * rayDist1),
                        end = Offset(w * 0.5f + cosA * rayDist2, h * 0.5f + sinA * rayDist2),
                        strokeWidth = sw,
                        cap = StrokeCap.Round
                    )
                }
            }
            AppThemeMode.DARK -> {
                val moon = Path().apply {
                    moveTo(w * 0.58f, h * 0.16f)
                    cubicTo(w * 0.30f, h * 0.20f, w * 0.18f, h * 0.52f, w * 0.36f, h * 0.74f)
                    cubicTo(w * 0.52f, h * 0.92f, w * 0.82f, h * 0.84f, w * 0.86f, h * 0.62f)
                    cubicTo(w * 0.66f, h * 0.68f, w * 0.46f, h * 0.50f, w * 0.58f, h * 0.16f)
                    close()
                }
                drawPath(moon, color = tint, style = Stroke(width = sw, join = StrokeJoin.Round))
            }
        }
    }
}

@Composable
fun EyeVisibilityIcon(
    visible: Boolean,
    tint: Color,
    size: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = w * 0.085f
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val eye = Path().apply {
            moveTo(w * 0.10f, h * 0.50f)
            quadraticTo(w * 0.50f, h * 0.14f, w * 0.90f, h * 0.50f)
            quadraticTo(w * 0.50f, h * 0.86f, w * 0.10f, h * 0.50f)
            close()
        }
        drawPath(eye, color = tint, style = stroke)
        drawCircle(color = tint, radius = w * 0.13f, center = Offset(w * 0.50f, h * 0.50f), style = stroke)
        if (!visible) {
            drawLine(tint, Offset(w * 0.18f, h * 0.82f), Offset(w * 0.82f, h * 0.18f), strokeWidth = sw, cap = StrokeCap.Round)
        }
    }
}

@Composable
fun GlobeLanguageIcon(
    tint: Color,
    size: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = w * 0.08f
        val stroke = Stroke(width = sw)
        drawCircle(color = tint, radius = w * 0.40f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
        drawLine(tint, Offset(w * 0.10f, h * 0.50f), Offset(w * 0.90f, h * 0.50f), strokeWidth = sw)
        drawOval(
            color = tint,
            topLeft = Offset(w * 0.30f, h * 0.10f),
            size = Size(w * 0.40f, h * 0.80f),
            style = stroke
        )
    }
}

/**
 * Custom AI Voice Cloning Acoustic Sparkle Icon — visualizes automatic speaker voice matching.
 */
@Composable
fun VoiceWaveSparkIcon(
    tint: Color,
    size: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = w * 0.09f
        drawLine(tint, Offset(w * 0.16f, h * 0.40f), Offset(w * 0.16f, h * 0.60f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.34f, h * 0.24f), Offset(w * 0.34f, h * 0.76f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.52f, h * 0.14f), Offset(w * 0.52f, h * 0.86f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(tint, Offset(w * 0.70f, h * 0.30f), Offset(w * 0.70f, h * 0.70f), strokeWidth = sw, cap = StrokeCap.Round)
        val spark = Path().apply {
            moveTo(w * 0.86f, h * 0.10f)
            quadraticTo(w * 0.87f, h * 0.23f, w * 0.98f, h * 0.24f)
            quadraticTo(w * 0.87f, h * 0.25f, w * 0.86f, h * 0.38f)
            quadraticTo(w * 0.85f, h * 0.25f, w * 0.74f, h * 0.24f)
            quadraticTo(w * 0.85f, h * 0.23f, w * 0.86f, h * 0.10f)
            close()
        }
        drawPath(spark, color = tint)
    }
}

/**
 * Custom Floating Liquid Glass OSD (On-Screen Display) Window Icon.
 */
@Composable
fun OsdFloatingIcon(
    tint: Color,
    activeDotColor: Color? = null,
    size: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sw = w * 0.085f
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.10f, h * 0.16f),
            size = Size(w * 0.80f, h * 0.68f),
            cornerRadius = CornerRadius(w * 0.16f, w * 0.16f),
            style = stroke
        )
        drawRoundRect(
            color = tint.copy(alpha = 0.22f),
            topLeft = Offset(w * 0.20f, h * 0.48f),
            size = Size(w * 0.60f, h * 0.24f),
            cornerRadius = CornerRadius(w * 0.08f, w * 0.08f)
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.28f, h * 0.60f),
            end = Offset(w * 0.72f, h * 0.60f),
            strokeWidth = sw * 0.95f,
            cap = StrokeCap.Round
        )
        if (activeDotColor != null) {
            drawCircle(
                color = activeDotColor,
                radius = w * 0.11f,
                center = Offset(w * 0.78f, h * 0.28f)
            )
        }
    }
}
