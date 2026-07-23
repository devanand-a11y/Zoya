package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.remote.ZoyaState
import com.example.ui.theme.DarkObsidian
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.GlowCyan
import com.example.ui.theme.GlowMagenta
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ZoyaOrbView(
    state: ZoyaState,
    micAmplitude: Float,
    outputAmplitude: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "OrbTransitions")

    // Rotation angle
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "Rotation"
    )

    // Breathing pulse for Idle
    val breathingScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Breathing"
    )

    // Smooth reactive amplitude interpolation
    val animatedMicAmp = remember { Animatable(0f) }
    val animatedOutputAmp = remember { Animatable(0f) }

    LaunchedEffect(micAmplitude) {
        animatedMicAmp.animateTo(micAmplitude, animationSpec = tween(80))
    }
    LaunchedEffect(outputAmplitude) {
        animatedOutputAmp.animateTo(outputAmplitude, animationSpec = tween(80))
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(280.dp)
            .testTag("zoya_orb_button")
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.width / 3.2f

            when (state) {
                ZoyaState.IDLE -> {
                    val currentRadius = baseRadius * breathingScale

                    // Soft ambient outer aura
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(GlowMagenta, GlowCyan, Color.Transparent),
                            center = center,
                            radius = currentRadius * 1.8f
                        ),
                        radius = currentRadius * 1.8f,
                        center = center
                    )

                    // Outer neon aura ring
                    rotate(rotationAngle, pivot = center) {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                colors = listOf(NeonCyan, NeonMagenta, ElectricViolet, NeonCyan),
                                center = center
                            ),
                            radius = currentRadius * 1.25f,
                            center = center,
                            style = Stroke(width = 4.dp.toPx())
                        )
                    }

                    // Inner Core
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(NeonMagenta, DarkObsidian),
                            center = center,
                            radius = currentRadius
                        ),
                        radius = currentRadius,
                        center = center
                    )
                }

                ZoyaState.LISTENING -> {
                    // Reactive listening waveform
                    val currentAmp = animatedMicAmp.value
                    val expandedRadius = baseRadius * (1f + currentAmp * 0.8f)

                    // Outer reactive aura
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(GlowCyan, GlowMagenta, Color.Transparent),
                            center = center,
                            radius = expandedRadius * 2.2f
                        ),
                        radius = expandedRadius * 2.2f,
                        center = center
                    )

                    // Waveform lines around perimeter
                    val wavePoints = 40
                    val wavePath = Path()

                    for (i in 0..wavePoints) {
                        val angle = (i.toFloat() / wavePoints) * 2 * PI.toFloat()
                        val waveOffset = sin(angle * 8 + rotationAngle * 0.1f) * (20f + currentAmp * 60f)
                        val r = expandedRadius + waveOffset
                        val x = center.x + r * cos(angle)
                        val y = center.y + r * sin(angle)

                        if (i == 0) wavePath.moveTo(x, y) else wavePath.lineTo(x, y)
                    }
                    wavePath.close()

                    drawPath(
                        path = wavePath,
                        brush = Brush.sweepGradient(
                            colors = listOf(NeonCyan, GlowCyan, NeonMagenta, NeonCyan),
                            center = center
                        ),
                        style = Stroke(width = 6.dp.toPx())
                    )

                    // Core Orb
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(NeonCyan, DarkObsidian),
                            center = center,
                            radius = baseRadius
                        ),
                        radius = baseRadius,
                        center = center
                    )
                }

                ZoyaState.THINKING -> {
                    // Pulsing neon thinking ring
                    val pulsingRadius = baseRadius * (1f + sin(rotationAngle * 0.1f) * 0.15f)

                    rotate(-rotationAngle * 2, pivot = center) {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                colors = listOf(NeonMagenta, ElectricViolet, Color.Transparent, NeonMagenta),
                                center = center
                            ),
                            radius = pulsingRadius * 1.4f,
                            center = center,
                            style = Stroke(width = 8.dp.toPx())
                        )
                    }

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(ElectricViolet, DarkObsidian),
                            center = center,
                            radius = baseRadius
                        ),
                        radius = baseRadius,
                        center = center
                    )
                }

                ZoyaState.SPEAKING -> {
                    // Dynamic audio wave matching Zoya's output audio stream
                    val currentAmp = animatedOutputAmp.value
                    val speakRadius = baseRadius * (1.1f + currentAmp * 0.6f)

                    // Radiant outer glow
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(GlowMagenta, GlowCyan, Color.Transparent),
                            center = center,
                            radius = speakRadius * 2.5f
                        ),
                        radius = speakRadius * 2.5f,
                        center = center
                    )

                    // Multiple dynamic energy rings
                    for (ring in 1..3) {
                        val ringRadius = speakRadius * (0.8f + ring * 0.25f * (1f + currentAmp * 0.5f))
                        drawCircle(
                            color = if (ring % 2 == 0) NeonMagenta.copy(alpha = 0.6f) else NeonCyan.copy(alpha = 0.6f),
                            radius = ringRadius,
                            center = center,
                            style = Stroke(width = (4 - ring).dp.toPx())
                        )
                    }

                    // Speaking Core
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(NeonMagenta, GlowMagenta, DarkObsidian),
                            center = center,
                            radius = baseRadius
                        ),
                        radius = baseRadius,
                        center = center
                    )
                }

                ZoyaState.ERROR -> {
                    drawCircle(
                        color = Color(0xFFEF4444).copy(alpha = 0.3f),
                        radius = baseRadius * 1.3f,
                        center = center
                    )
                    drawCircle(
                        color = Color(0xFFEF4444),
                        radius = baseRadius,
                        center = center,
                        style = Stroke(width = 4.dp.toPx())
                    )
                }
            }
        }

        // Center Icon based on state
        Icon(
            imageVector = when (state) {
                ZoyaState.IDLE -> Icons.Default.MicOff
                ZoyaState.LISTENING -> Icons.Default.Mic
                ZoyaState.THINKING -> Icons.Default.Psychology
                ZoyaState.SPEAKING -> Icons.Default.VolumeUp
                ZoyaState.ERROR -> Icons.Default.MicOff
            },
            contentDescription = "Zoya Orb Mic Control",
            tint = when (state) {
                ZoyaState.IDLE -> Color.White.copy(alpha = 0.6f)
                ZoyaState.LISTENING -> NeonCyan
                ZoyaState.THINKING -> ElectricViolet
                ZoyaState.SPEAKING -> NeonMagenta
                ZoyaState.ERROR -> Color(0xFFEF4444)
            },
            modifier = Modifier.size(56.dp)
        )
    }
}
