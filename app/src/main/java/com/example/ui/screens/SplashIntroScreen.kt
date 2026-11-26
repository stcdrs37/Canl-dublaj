package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.audio.SplashAudioPlayer
import com.example.ui.theme.EmeraldOk
import com.example.util.HapticFeedbackHelper
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * 6.0-Second Zero-Recomposition 60–120 FPS 4K-Ready Liquid Glass Cinematic Intro for Screen Dubbing.
 *
 * Performance Architecture (True 60–120 FPS, ZERO Composition Overhead):
 * - `progress.value` is NEVER read during Composition! Every animated transform, opacity,
 *   orbiting aurora, hex cyber-shield ring, and progress bar scale is read exclusively inside
 *   `Canvas` (Draw phase) and `graphicsLayer` (RenderNode phase) with pre-allocated `Path`.
 * - Showcases the flagship Screen Dubbing logo inside an animated luminous crystal medallion.
 */
@Composable
fun SplashIntroScreen(
    playAudio: Boolean = true,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }
    val audioPlayer = remember { SplashAudioPlayer() }
    val context = LocalContext.current
    val hexPath = remember { Path() }

    DisposableEffect(playAudio) {
        if (playAudio) {
            audioPlayer.start()
        }
        onDispose {
            audioPlayer.stop()
        }
    }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = SplashAudioPlayer.DURATION_MS.toInt(),
                easing = LinearEasing
            )
        )
        audioPlayer.stop()
        onFinished()
    }

    val isDark = MaterialTheme.colorScheme.background.red < 0.25f

    val bgBrush = remember(isDark) {
        if (isDark) {
            Brush.verticalGradient(
                listOf(Color(0xFF040814), Color(0xFF0A142A), Color(0xFF060C1C))
            )
        } else {
            Brush.verticalGradient(
                listOf(Color(0xFFE6F0FF), Color(0xFFF3F8FF), Color(0xFFDCEBFF))
            )
        }
    }

    val primaryGlow = if (isDark) Color(0xFF5FA2FF) else Color(0xFF2858F5)
    val secondaryGlow = if (isDark) Color(0xFF3EEAD6) else Color(0xFF00B4A2)
    val tertiaryGlow = if (isDark) Color(0xFFB394FF) else Color(0xFF7B4BFF)
    val inkPrimary = if (isDark) Color(0xFFF4F8FF) else Color(0xFF0A1226)
    val inkSecondary = if (isDark) Color(0xFFB3C5E8) else Color(0xFF3F527A)
    val railBrush = remember(primaryGlow, secondaryGlow) {
        Brush.horizontalGradient(listOf(primaryGlow, secondaryGlow, EmeraldOk))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgBrush)
            .testTag("splash_intro_root")
    ) {
        // 1. Zero-Recomposition 60–120 FPS GPU Vector Refraction & Acoustic Wave Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 1f || h <= 1f) return@Canvas
            val p = progress.value // Read strictly in Draw phase (zero recomposition!)
            val cx = w * 0.5f
            val cy = h * 0.40f
            val minDim = minOf(w, h)
            val theta = p * 2.0 * PI

            // Volumetric Liquid Aurora Orbs
            val orb1 = Offset(
                x = cx + (w * 0.34f) * cos(theta * 1.1).toFloat(),
                y = cy - (h * 0.22f) * sin(theta * 0.9).toFloat()
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(primaryGlow.copy(alpha = if (isDark) 0.34f else 0.26f), Color.Transparent),
                    center = orb1,
                    radius = minDim * 0.72f
                ),
                radius = minDim * 0.72f,
                center = orb1
            )

            val orb2 = Offset(
                x = cx - (w * 0.32f) * sin(theta * 1.3).toFloat(),
                y = cy + (h * 0.26f) * cos(theta * 1.1).toFloat()
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(secondaryGlow.copy(alpha = if (isDark) 0.30f else 0.24f), Color.Transparent),
                    center = orb2,
                    radius = minDim * 0.68f
                ),
                radius = minDim * 0.68f,
                center = orb2
            )

            val orb3 = Offset(
                x = cx + (w * 0.26f) * sin(theta * 0.85 + 1.2).toFloat(),
                y = cy + (h * 0.14f) * sin(theta * 1.4).toFloat()
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(tertiaryGlow.copy(alpha = if (isDark) 0.26f else 0.20f), Color.Transparent),
                    center = orb3,
                    radius = minDim * 0.56f
                ),
                radius = minDim * 0.56f,
                center = orb3
            )

            // Expanding 60–120fps Harmonic Acoustic Ripples
            for (ring in 0 until 4) {
                val ringPhase = ((p * 2.6f) + ring * 0.25f) % 1f
                val ringRadius = minDim * (0.16f + ringPhase * 0.44f)
                val ringAlpha = (1f - ringPhase) * (1f - ringPhase) * if (isDark) 0.42f else 0.32f
                drawCircle(
                    brush = Brush.sweepGradient(
                        listOf(primaryGlow, secondaryGlow, tertiaryGlow, primaryGlow),
                        center = Offset(cx, cy)
                    ),
                    radius = ringRadius,
                    center = Offset(cx, cy),
                    alpha = ringAlpha,
                    style = Stroke(width = (2.8f - ringPhase * 1.6f).dp.toPx())
                )
            }

            // 32 Shimmering Crystal Light Particles
            for (i in 0 until 32) {
                val angle = (i * 0.618034 * 2.0 * PI) + theta * (0.35 + (i % 5) * 0.08)
                val distNorm = 0.18f + ((i * 37) % 100) / 220f + 0.04f * sin(theta * 2.0 + i).toFloat()
                val px = cx + (minDim * distNorm) * cos(angle).toFloat()
                val py = cy + (minDim * distNorm * 1.12f) * sin(angle).toFloat()
                val twinkle = (0.35f + 0.65f * ((sin(theta * 4.0 + i * 1.7) + 1.0) * 0.5)).toFloat()
                val dotR = (1.5f + (i % 3) * 1.05f).dp.toPx()
                val dotColor = when (i % 3) {
                    0 -> primaryGlow
                    1 -> secondaryGlow
                    else -> Color.White
                }
                drawCircle(
                    color = dotColor.copy(alpha = twinkle * if (isDark) 0.72f else 0.55f),
                    radius = dotR,
                    center = Offset(px, py)
                )
            }

            // Luminous Outer Prism Halo & Rotating Specular Ring around center logo medallion
            val orbScale = when {
                p < 0.22f -> 0.62f + (p / 0.22f) * 0.40f
                p < 0.86f -> 1.02f + 0.022f * sin(p * 18f)
                else -> 1.02f + ((p - 0.86f) / 0.14f) * 0.14f
            }
            val haloR = minDim * 0.24f * orbScale
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        secondaryGlow.copy(alpha = 0.42f),
                        primaryGlow.copy(alpha = 0.18f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = haloR
                ),
                radius = haloR,
                center = Offset(cx, cy)
            )

            // Crystalline Hex Cyber Shield Ring locking in around the logo (1.5s -> 3.0s)
            val shieldReveal = ((p - 0.26f) / 0.22f).coerceIn(0f, 1f)
            if (shieldReveal > 0.01f) {
                val hexR = minDim * 0.215f * orbScale * (1.28f - 0.12f * shieldReveal)
                val rot = (1f - shieldReveal) * (PI / 3.0) + p * 0.28
                hexPath.reset()
                for (v in 0 until 6) {
                    val a = rot + v * (PI / 3.0) - PI / 2.0
                    val vx = cx + hexR * cos(a).toFloat()
                    val vy = cy + hexR * sin(a).toFloat()
                    if (v == 0) hexPath.moveTo(vx, vy) else hexPath.lineTo(vx, vy)
                }
                hexPath.close()
                drawPath(
                    path = hexPath,
                    brush = Brush.linearGradient(
                        listOf(EmeraldOk.copy(alpha = 0.9f * shieldReveal), secondaryGlow.copy(alpha = 0.85f * shieldReveal)),
                        start = Offset(cx - hexR, cy - hexR),
                        end = Offset(cx + hexR, cy + hexR)
                    ),
                    style = Stroke(
                        width = 2.2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }

        // 2. Center Flagship Screen Dubbing Logo Medallion (animated purely in graphicsLayer!)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    val p = progress.value
                    val s = when {
                        p < 0.22f -> 0.58f + (p / 0.22f) * 0.44f
                        p < 0.86f -> 1.02f + 0.022f * sin(p * 18f)
                        else -> 1.02f + ((p - 0.86f) / 0.14f) * 0.10f
                    }
                    scaleX = s
                    scaleY = s
                    translationY = -size.height * 0.62f
                    alpha = (p / 0.12f).coerceIn(0.35f, 1f)
                }
                .size(134.dp)
                .clip(CircleShape)
                .border(
                    width = 2.5.dp,
                    brush = Brush.sweepGradient(
                        listOf(
                            Color.White.copy(alpha = 0.92f),
                            secondaryGlow,
                            primaryGlow,
                            tertiaryGlow,
                            Color.White.copy(alpha = 0.92f)
                        )
                    ),
                    shape = CircleShape
                )
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_screen_dubbing_logo),
                contentDescription = "Screen Dubbing Logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 3. Top-right Skip Liquid Glass Pill
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = 12.dp, end = 16.dp)
        ) {
            val skipShape = RoundedCornerShape(999.dp)
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(skipShape)
                    .background(if (isDark) Color(0x66162444) else Color(0xAAFFFFFF))
                    .border(
                        width = 1.dp,
                        color = if (isDark) Color.White.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.92f),
                        shape = skipShape
                    )
                    .clickable {
                        HapticFeedbackHelper.vibrateSoftTap(context, enabled = true)
                        audioPlayer.stop()
                        onFinished()
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("splash_skip_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Atla ›",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = inkPrimary
                )
            }
        }

        // 4. Bottom Signature Reveal + AES-256 Cyber Shield Pill + Zero-Recomposition Progress Bar
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 28.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Screen Dubbing",
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 44.sp),
                fontWeight = FontWeight.Bold,
                color = inkPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .graphicsLayer {
                        val a = ((progress.value - 0.36f) / 0.16f).coerceIn(0f, 1f)
                        alpha = a
                        translationY = (1f - a) * 26f
                    }
                    .testTag("splash_brand_title")
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Google AI API • 60 FPS Liquid Glass • BT Dubbing",
                style = MaterialTheme.typography.bodyMedium,
                color = inkSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .graphicsLayer {
                        val a = ((progress.value - 0.48f) / 0.16f).coerceIn(0f, 1f)
                        alpha = a
                        translationY = (1f - a) * 18f
                    }
            )

            Spacer(modifier = Modifier.height(14.dp))

            val shieldPillShape = RoundedCornerShape(999.dp)
            Row(
                modifier = Modifier
                    .graphicsLayer {
                        alpha = ((progress.value - 0.52f) / 0.16f).coerceIn(0f, 1f)
                    }
                    .clip(shieldPillShape)
                    .background(EmeraldOk.copy(alpha = if (isDark) 0.20f else 0.14f))
                    .border(1.dp, EmeraldOk.copy(alpha = 0.55f), shieldPillShape)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(EmeraldOk)
                )
                Text(
                    text = "AES-256 Cyber Shield • TLS 1.3",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldOk
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Smooth 6.0-second translucent glass loading rail (GPU scaleX, zero recomposition!)
            val trackShape = RoundedCornerShape(999.dp)
            Box(
                modifier = Modifier
                    .width(216.dp)
                    .height(5.dp)
                    .clip(trackShape)
                    .background(if (isDark) Color.White.copy(alpha = 0.14f) else Color(0x33163060))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            scaleX = progress.value.coerceIn(0.02f, 1f)
                        }
                        .clip(trackShape)
                        .background(railBrush)
                )
            }
        }
    }
}
