package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.OsdFontSizePreset
import com.example.i18n.LocalAppStrings
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.EmeraldOk
import com.example.ui.theme.GlassSpecularWhite
import com.example.ui.theme.SolarGold
import com.example.ui.theme.StudioDarkBg
import com.example.ui.theme.StudioDarkBgBottom
import com.example.ui.theme.StudioDarkBgMid
import com.example.ui.theme.StudioLightBg
import com.example.ui.theme.StudioLightBgBottom
import com.example.ui.theme.StudioLightBgMid
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tactile 60fps Liquid Glass press bounce modifier.
 * Animates scale smoothly inside graphicsLayer (zero layout pass / zero recomposition jitter).
 */
fun Modifier.liquidPressBounce(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.965f
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "liquid_press_scale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * 60fps GPU-friendly True Liquid Glass Refractive Backdrop.
 * Rich translucent Aurora / Crystal Mist color orbs softly illuminate through frosted glass cards
 * in both Light (Crystal Daylight) and Dark (Obsidian Prism) themes.
 */
@Composable
fun LiquidGlassBackground(
    isStreaming: Boolean,
    audioEnergy: Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val isDark = cs.background.red < 0.25f

    val infiniteTransition = rememberInfiniteTransition(label = "aeroglass_mesh")
    val phaseState = infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.2831855f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isStreaming) 8500 else 14500,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "mesh_phase"
    )

    val energyBoostState = animateFloatAsState(
        targetValue = if (isStreaming) {
            (0.24f + audioEnergy.coerceIn(0f, 1f) * 0.28f)
        } else {
            if (isDark) 0.22f else 0.21f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "mesh_energy"
    )

    val bgStops = if (isDark) {
        listOf(StudioDarkBg, StudioDarkBgMid, StudioDarkBgBottom)
    } else {
        listOf(StudioLightBg, StudioLightBgMid, StudioLightBgBottom)
    }
    val orbPrimary = cs.primary
    val orbSecondary = cs.secondary
    val orbTertiary = cs.tertiary

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = bgStops))
            .drawBehind {
                val w = size.width
                val h = size.height
                if (w <= 1f || h <= 1f) return@drawBehind
                val minDim = minOf(w, h)
                val angle = phaseState.value
                val alphaBoost = energyBoostState.value

                val c1 = Offset(
                    x = w * (0.82f + 0.13f * cos(angle)),
                    y = h * (0.14f + 0.08f * sin(angle))
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            orbPrimary.copy(alpha = alphaBoost * 1.25f),
                            orbPrimary.copy(alpha = alphaBoost * 0.36f),
                            Color.Transparent
                        ),
                        center = c1,
                        radius = minDim * 0.88f
                    ),
                    radius = minDim * 0.88f,
                    center = c1
                )

                val c2 = Offset(
                    x = w * (0.14f + 0.14f * sin(angle * 0.85f)),
                    y = h * (0.44f + 0.10f * cos(angle * 0.85f))
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            orbSecondary.copy(alpha = alphaBoost * 1.15f),
                            orbSecondary.copy(alpha = alphaBoost * 0.30f),
                            Color.Transparent
                        ),
                        center = c2,
                        radius = minDim * 0.82f
                    ),
                    radius = minDim * 0.82f,
                    center = c2
                )

                val c3 = Offset(
                    x = w * (0.72f + 0.15f * sin(angle + 2.1f)),
                    y = h * (0.84f + 0.07f * cos(angle + 2.1f))
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            orbTertiary.copy(alpha = alphaBoost * 1.08f),
                            orbTertiary.copy(alpha = alphaBoost * 0.26f),
                            Color.Transparent
                        ),
                        center = c3,
                        radius = minDim * 0.80f
                    ),
                    radius = minDim * 0.80f,
                    center = c3
                )
            }
    ) {
        content()
    }
}

/**
 * True Translucent Frosted Liquid Glass Card:
 * - Translucent layered crystal fill so the refractive aurora backdrop breathes through the glass
 * - Diagonal specular gloss reflection band + bright top glass edge highlight
 * - Optimized separately for Crystal Daylight (Light) and Obsidian Prism (Dark)
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    cornerRadius: Dp = 24.dp,
    contentPadding: Dp = 14.dp,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val isDark = cs.background.red < 0.25f
    val shape = RoundedCornerShape(cornerRadius)

    val fillBrush = remember(isDark, accentColor, highlighted) {
        if (isDark) {
            Brush.linearGradient(
                colors = listOf(
                    if (highlighted) accentColor.copy(alpha = 0.24f) else Color(0x8E1A2746),
                    if (highlighted) Color(0x99132140) else Color(0x730E172B),
                    accentColor.copy(alpha = if (highlighted) 0.16f else 0.09f)
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    if (highlighted) accentColor.copy(alpha = 0.15f) else Color(0xB8FFFFFF),
                    if (highlighted) Color(0xA6ECF3FF) else Color(0x8CF4F8FF),
                    accentColor.copy(alpha = if (highlighted) 0.11f else 0.06f)
                )
            )
        }
    }

    val rimBrush = remember(isDark, accentColor, highlighted) {
        if (isDark) {
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (highlighted) 0.44f else 0.30f),
                    accentColor.copy(alpha = if (highlighted) 0.58f else 0.34f),
                    Color.White.copy(alpha = 0.10f)
                )
            )
        } else {
            Brush.linearGradient(
                colors = listOf(
                    GlassSpecularWhite.copy(alpha = 0.98f),
                    accentColor.copy(alpha = if (highlighted) 0.46f else 0.24f),
                    GlassSpecularWhite.copy(alpha = 0.65f)
                )
            )
        }
    }

    Box(
        modifier = modifier
            .shadow(
                elevation = if (isDark) 12.dp else 9.dp,
                shape = shape,
                ambientColor = accentColor.copy(alpha = if (isDark) 0.24f else 0.15f),
                spotColor = accentColor.copy(alpha = if (isDark) 0.28f else 0.17f)
            )
            .clip(shape)
            .background(fillBrush)
            .drawBehind {
                // Soft diagonal curved-glass internal specular reflection sheen
                val sheenAlpha = if (isDark) 0.085f else 0.42f
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = sheenAlpha),
                            Color.White.copy(alpha = sheenAlpha * 0.25f),
                            Color.Transparent,
                            accentColor.copy(alpha = if (isDark) 0.07f else 0.04f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width * 0.85f, size.height * 0.72f)
                    )
                )
            }
            .border(
                width = if (highlighted) 1.4.dp else 1.15.dp,
                brush = rimBrush,
                shape = shape
            )
    ) {
        // Luminous top-edge specular prism line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.8.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = if (isDark) 0.48f else 0.98f),
                            accentColor.copy(alpha = if (isDark) 0.35f else 0.25f),
                            Color.Transparent
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content
        )
    }
}

/**
 * Luminous 60fps Liquid Glass Primary CTA Capsule Button.
 */
@Composable
fun LiquidGlassPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentStart: Color = MaterialTheme.colorScheme.primary,
    accentEnd: Color = MaterialTheme.colorScheme.secondary,
    height: Dp = 54.dp,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shimmerTransition = rememberInfiniteTransition(label = "liquid_cta_shimmer")
    val shimmerProgress = shimmerTransition.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_x"
    )
    val shape = RoundedCornerShape(height / 2)

    Box(
        modifier = modifier
            .height(height)
            .liquidPressBounce(interactionSource, pressedScale = 0.97f)
            .shadow(
                elevation = if (enabled) 12.dp else 4.dp,
                shape = shape,
                ambientColor = accentStart.copy(alpha = if (enabled) 0.34f else 0.10f),
                spotColor = accentStart.copy(alpha = if (enabled) 0.38f else 0.10f)
            )
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    colors = if (enabled) {
                        listOf(accentStart, accentEnd)
                    } else {
                        listOf(accentStart.copy(alpha = 0.45f), accentEnd.copy(alpha = 0.45f))
                    }
                )
            )
            .drawBehind {
                if (enabled && size.width > 1f && size.height > 1f) {
                    val sweepX = size.width * shimmerProgress.value
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.28f),
                                Color.Transparent
                            ),
                            start = Offset(sweepX - size.width * 0.28f, 0f),
                            end = Offset(sweepX + size.width * 0.28f, size.height)
                        )
                    )
                }
            }
            .border(
                width = 1.3.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.68f),
                        Color.White.copy(alpha = 0.14f)
                    )
                ),
                shape = shape
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 20.dp),
            content = content
        )
    }
}

/**
 * Compact circular frosted Liquid Glass action orb (48dp touch target).
 */
@Composable
fun LiquidGlassIconOrb(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    highlighted: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val isDark = cs.background.red < 0.25f
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(44.dp)
            .liquidPressBounce(interactionSource, pressedScale = 0.92f)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = when {
                        highlighted -> listOf(
                            accentColor.copy(alpha = if (isDark) 0.34f else 0.22f),
                            accentColor.copy(alpha = if (isDark) 0.18f else 0.10f)
                        )
                        isDark -> listOf(Color(0x9C1F2E4F), Color(0x78111B32))
                        else -> listOf(Color(0xCCFFFFFF), Color(0x96EDF4FF))
                    }
                )
            )
            .border(
                width = if (highlighted) 1.35.dp else 1.1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        if (highlighted) accentColor.copy(alpha = 0.78f) else Color.White.copy(alpha = if (isDark) 0.38f else 0.98f),
                        accentColor.copy(alpha = if (highlighted) 0.45f else 0.28f)
                    )
                ),
                shape = CircleShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/**
 * Frosted glass pill badge for status and metadata tags.
 */
@Composable
fun LiquidGlassPillBadge(
    text: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    leadingDot: Boolean = false
) {
    Surface(
        modifier = modifier,
        color = accentColor.copy(alpha = 0.14f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(
            1.dp,
            Brush.horizontalGradient(
                listOf(
                    accentColor.copy(alpha = 0.52f),
                    accentColor.copy(alpha = 0.22f)
                )
            )
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingDot) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = text,
                color = accentColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Compact Premium Glass Banner communicating 100% Automatic AI Zero-Shot Voice Cloning
 * + Zero-Word-Swallowing lossless streaming (no manual voice picker clutter!).
 */
@Composable
fun AiVoiceCloneBannerStrip(
    isLosslessActive: Boolean,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val cs = MaterialTheme.colorScheme
    val isDark = cs.background.red < 0.25f

    LiquidGlassCard(
        accentColor = cs.tertiary,
        cornerRadius = 22.dp,
        contentPadding = 12.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                cs.primary.copy(alpha = if (isDark) 0.30f else 0.16f),
                                cs.tertiary.copy(alpha = if (isDark) 0.28f else 0.14f)
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = cs.primary.copy(alpha = 0.38f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                VoiceWaveSparkIcon(
                    tint = cs.primary,
                    size = 20.dp
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = strings.aiVoiceCloneTitle,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = strings.aiVoiceCloneSubtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LiquidGlassPillBadge(
                        text = strings.aiVoiceCloneBadge,
                        accentColor = cs.primary,
                        leadingDot = true
                    )
                    if (isLosslessActive) {
                        LiquidGlassPillBadge(
                            text = strings.zeroWordDropBadge,
                            accentColor = EmeraldOk
                        )
                    }
                }
            }
        }
    }
}

/**
 * Interactive Flagship Obsidian Liquid Glass Floating OSD Live Preview Card.
 * Includes the Hide (`—`), Pause (`II`), and Stop (`■`) glass capsules matching the live overlay 1:1!
 */
@Composable
fun PremiumOsdPreviewCard(
    osdEnabled: Boolean,
    opacityPercent: Int,
    fontSizePreset: OsdFontSizePreset,
    showOriginalText: Boolean,
    compactMode: Boolean,
    languageBridgeLabel: String,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val cs = MaterialTheme.colorScheme
    val glassAlpha = (opacityPercent.coerceIn(50, 100) / 100f) * 0.88f
    val dubbedSp = when (fontSizePreset) {
        OsdFontSizePreset.SMALL -> 13.5.sp
        OsdFontSizePreset.MEDIUM -> 15.5.sp
        OsdFontSizePreset.LARGE -> 18.sp
    }
    val origSp = when (fontSizePreset) {
        OsdFontSizePreset.SMALL -> 11.sp
        OsdFontSizePreset.MEDIUM -> 12.sp
        OsdFontSizePreset.LARGE -> 13.5.sp
    }

    val stageShape = RoundedCornerShape(20.dp)
    val osdShape = RoundedCornerShape(if (compactMode) 16.dp else 20.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(stageShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF0A1124),
                        Color(0xFF162A52),
                        Color(0xFF201C42)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = cs.outlineVariant.copy(alpha = 0.42f),
                shape = stageShape
            )
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.osdPreviewBadge,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8)
                )
                Text(
                    text = "%$opacityPercent • ${strings.osdSizeLabel(fontSizePreset)}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF93C5FD)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = if (osdEnabled) 1f else 0.42f }
                    .shadow(
                        elevation = 10.dp,
                        shape = osdShape,
                        ambientColor = ElectricBlue.copy(alpha = 0.35f),
                        spotColor = ElectricBlue.copy(alpha = 0.35f)
                    )
                    .clip(osdShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF121C34).copy(alpha = glassAlpha),
                                Color(0xFF1A2B50).copy(alpha = glassAlpha * 0.90f)
                            )
                        )
                    )
                    .border(
                        width = 1.1.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.42f),
                                ElectricBlue.copy(alpha = 0.52f),
                                Color.White.copy(alpha = 0.12f)
                            )
                        ),
                        shape = osdShape
                    )
                    .padding(horizontal = 13.dp, vertical = if (compactMode) 9.dp else 11.dp),
                verticalArrangement = Arrangement.spacedBy(if (compactMode) 4.dp else 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (osdEnabled) EmeraldOk else Color(0xFF64748B))
                        )
                        Text(
                            text = languageBridgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE2E8F0),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(ElectricBlue.copy(alpha = 0.24f))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "142 ms",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF93C5FD)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        // Hide button preview (`—`)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.18f))
                                .border(0.8.dp, Color.White.copy(alpha = 0.40f), RoundedCornerShape(50))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text("—", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(SolarGold.copy(alpha = 0.22f))
                                .border(0.8.dp, SolarGold.copy(alpha = 0.55f), RoundedCornerShape(50))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text("II", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = SolarGold)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(CrimsonRed.copy(alpha = 0.28f))
                                .border(0.8.dp, CrimsonRed.copy(alpha = 0.65f), RoundedCornerShape(50))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text("■", fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFCA5A5))
                        }
                    }
                }

                if (showOriginalText && !compactMode) {
                    Text(
                        text = strings.osdPreviewOriginal,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = origSp,
                        color = Color(0xFFB3C7E6),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = strings.osdPreviewDubbed,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = dubbedSp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = if (compactMode) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = strings.osdHideHint,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                color = Color(0xFF9FB4D6),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Compact 60fps Real-Time Audio Capture & Dubbing Waveform Visualizer (100% Real PCM Data).
 */
@Composable
fun DualAudioWaveVisualizer(
    captureWaveform: List<Float>,
    playbackWaveform: List<Float>,
    isAudioDetected: Boolean,
    isActive: Boolean,
    captureLevel: Float = 0f,
    playbackLevel: Float = 0f,
    isPaused: Boolean = false,
    isSilencePaused: Boolean = false,
    captureDb: Float = -90f,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val smoothCapture by animateFloatAsState(
        targetValue = if (isActive) captureLevel.coerceIn(0f, 1f) else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "smooth_capture"
    )
    val smoothPlayback by animateFloatAsState(
        targetValue = if (isActive) playbackLevel.coerceIn(0f, 1f) else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "smooth_playback"
    )

    val cs = MaterialTheme.colorScheme
    val isDark = cs.background.red < 0.25f

    val badgeColor = when {
        isPaused -> SolarGold
        !isActive -> cs.onSurfaceVariant
        isAudioDetected -> EmeraldOk
        isSilencePaused -> ElectricBlue
        else -> SolarGold
    }
    val badgeLabel = when {
        isPaused -> strings.wavePaused
        !isActive -> strings.waveReady
        isAudioDetected -> "${strings.waveAudioDetected} (${captureDb.toInt()} dBFS)"
        isSilencePaused -> strings.waveSilencePause
        else -> strings.waveWaitingAudio
    }

    val containerShape = RoundedCornerShape(16.dp)
    val capPath = remember { Path() }
    val dubPath = remember { Path() }
    val baselineColor = cs.outlineVariant

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(containerShape)
            .background(if (isDark) Color(0x66091122) else Color(0x78EAF2FF))
            .border(
                width = 1.dp,
                color = cs.outlineVariant.copy(alpha = 0.65f),
                shape = containerShape
            )
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(badgeColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = badgeLabel,
                    color = badgeColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = if (isActive) "%${(smoothCapture * 100).toInt()}" else "%0",
                color = cs.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
        ) {
            val w = size.width
            val h = size.height
            if (w <= 1f || h <= 1f) return@Canvas
            val midY = h * 0.5f
            val barCount = 32
            val spacing = w / barCount.toFloat()
            val barWidth = (spacing * 0.54f).coerceAtLeast(3f)

            drawLine(
                color = baselineColor.copy(alpha = 0.55f),
                start = Offset(0f, midY),
                end = Offset(w, midY),
                strokeWidth = 1.dp.toPx()
            )

            capPath.reset()
            dubPath.reset()
            val idleTickColor = baselineColor.copy(alpha = 0.48f)
            val idleTickHeight = 2.dp.toPx()

            for (i in 0 until barCount) {
                val xCenter = i * spacing + spacing * 0.5f
                val capSample = if (isActive && i < captureWaveform.size) {
                    captureWaveform[i].coerceIn(0f, 1f)
                } else {
                    0f
                }
                val playSample = if (isActive && i < playbackWaveform.size) {
                    playbackWaveform[i].coerceIn(0f, 1f)
                } else {
                    0f
                }

                if (capSample > 0.005f) {
                    val capBarHeight = (capSample * (midY * 0.90f)).coerceAtLeast(idleTickHeight)
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(cs.secondary, cs.primary),
                            startY = midY - capBarHeight,
                            endY = midY
                        ),
                        topLeft = Offset(xCenter - barWidth / 2f, midY - capBarHeight),
                        size = Size(barWidth, capBarHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                } else {
                    drawRoundRect(
                        color = idleTickColor,
                        topLeft = Offset(xCenter - barWidth / 2f, midY - idleTickHeight),
                        size = Size(barWidth, idleTickHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }

                if (playSample > 0.005f) {
                    val playBarHeight = (playSample * (midY * 0.90f)).coerceAtLeast(idleTickHeight)
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(cs.tertiary, CrimsonRed),
                            startY = midY,
                            endY = midY + playBarHeight
                        ),
                        topLeft = Offset(xCenter - barWidth / 2f, midY),
                        size = Size(barWidth, playBarHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                } else {
                    drawRoundRect(
                        color = idleTickColor,
                        topLeft = Offset(xCenter - barWidth / 2f, midY),
                        size = Size(barWidth, idleTickHeight),
                        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
                    )
                }

                val capY = midY - capSample * (midY * 0.88f)
                val dubY = midY + playSample * (midY * 0.88f)
                if (i == 0) {
                    capPath.moveTo(xCenter, capY)
                    dubPath.moveTo(xCenter, dubY)
                } else {
                    capPath.lineTo(xCenter, capY)
                    dubPath.lineTo(xCenter, dubY)
                }
            }

            if (isActive && smoothCapture > 0.01f) {
                drawPath(
                    path = capPath,
                    color = cs.primary.copy(alpha = 0.65f),
                    style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
            if (isActive && smoothPlayback > 0.01f) {
                drawPath(
                    path = dubPath,
                    color = CrimsonRed.copy(alpha = 0.65f),
                    style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }
    }
}
