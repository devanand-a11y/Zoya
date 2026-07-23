package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.ZoyaState
import com.example.ui.components.PermissionsOnboarding
import com.example.ui.components.PersonalityBadge
import com.example.ui.components.ZoyaOrbView
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkObsidian
import com.example.ui.theme.DarkSurfaceGlass
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.GlowCyan
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun ZoyaHomeScreen(
    viewModel: ZoyaViewModel,
    onRequestPermissions: () -> Unit
) {
    val service by viewModel.serviceInstance.collectAsState()

    val hasMicPerm by viewModel.hasMicPermission.collectAsState()
    val hasContactsPerm by viewModel.hasContactsPermission.collectAsState()
    val hasPhonePerm by viewModel.hasPhonePermission.collectAsState()
    val hasNotifPerm by viewModel.hasNotificationPermission.collectAsState()

    val currentState = service?.sessionState?.collectAsState()?.value ?: ZoyaState.IDLE
    val statusText = service?.statusMessage?.collectAsState()?.value ?: "Initializing Zoya..."
    val lastTool = service?.lastToolExecuted?.collectAsState()?.value
    val micAmp = service?.recorderAmplitude?.collectAsState()?.value ?: 0f
    val playerAmp = service?.playerAmplitude?.collectAsState()?.value ?: 0f

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DarkBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            DarkObsidian,
                            DarkBackground,
                            Color(0xFF0F0721)
                        )
                    )
                )
        ) {
            if (!hasMicPerm) {
                // Permissions Onboarding Overlay
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    PermissionsOnboarding(
                        hasMicPermission = hasMicPerm,
                        hasContactsPermission = hasContactsPerm,
                        hasPhonePermission = hasPhonePerm,
                        hasNotificationPermission = hasNotifPerm,
                        onRequestPermissions = onRequestPermissions
                    )
                }
            } else {
                // Zero-Touch Immersive Futuristic Voice UI
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header Personality Badge
                    PersonalityBadge(
                        state = currentState,
                        statusText = statusText,
                        lastToolExecuted = lastTool
                    )

                    // Central Animated Orb / Mic Presence Button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ZoyaOrbView(
                            state = currentState,
                            micAmplitude = micAmp,
                            outputAmplitude = playerAmp,
                            onClick = {
                                viewModel.toggleOrbSession()
                            }
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Wake-Word / Interruption Indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(DarkSurfaceGlass)
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RecordVoiceOver,
                                contentDescription = null,
                                tint = NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Say 'Zoya' anytime to speak or interrupt",
                                color = TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Bottom Voice Commands Banner
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "TRY SAYING TO ZOYA:",
                            color = NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )

                        val quickPrompts = listOf(
                            QuickPrompt("Open YouTube", Icons.Default.Launch, NeonCyan),
                            QuickPrompt("Call Sarah", Icons.Default.Call, NeonMagenta),
                            QuickPrompt("WhatsApp Alex 'Meet at 8'", Icons.Default.Message, ElectricViolet),
                            QuickPrompt("Gmail email to boss", Icons.Default.Email, GlowCyan)
                        )

                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(quickPrompts) { prompt ->
                                PromptChip(prompt = prompt)
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class QuickPrompt(
    val title: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
private fun PromptChip(prompt: QuickPrompt) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurfaceGlass)
            .border(
                width = 1.dp,
                color = prompt.color.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Icon(
            imageVector = prompt.icon,
            contentDescription = null,
            tint = prompt.color,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = prompt.title,
            color = TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
