package com.example.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun triggerPanicHaptic(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            val vibrator = vibratorManager?.defaultVibrator
            val timings = longArrayOf(0, 120, 80, 200)
            val amplitudes = intArrayOf(0, 255, 0, 255)
            vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            val timings = longArrayOf(0, 120, 80, 200)
            val amplitudes = intArrayOf(0, 255, 0, 255)
            vibrator?.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(300L)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
fun PulsingPanicButton(
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val infiniteTransition = rememberInfiniteTransition(label = "panic_button_pulse")

    // Pulsing aura radius scale animation
    val glowScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isActive) 1.35f else 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isActive) 600 else 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_scale"
    )

    // Pulsing aura alpha animation
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = if (isActive) 0.85f else 0.35f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isActive) 600 else 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    // Icon scale pulse when panic alarm is triggered
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isActive) 1.25f else 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isActive) 400 else 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_scale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
    ) {
        // Outer Pulsing Red Glow Aura Ring
        Box(
            modifier = Modifier
                .scale(glowScale)
                .alpha(glowAlpha)
                .matchParentSize()
                .background(
                    color = if (isActive) Color(0xFFEF4444) else Color(0xFFDC2626),
                    shape = RoundedCornerShape(16.dp)
                )
        )

        // Main Button Surface with High Visibility Glowing Accent
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(
                    color = if (isActive) Color(0xFFEF4444) else Color(0xFF881337)
                )
                .border(
                    width = if (isActive) 2.dp else 1.5.dp,
                    color = if (isActive) Color.White else Color(0xFFFCA5A5).copy(alpha = 0.9f),
                    shape = RoundedCornerShape(12.dp)
                )
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    triggerPanicHaptic(context)
                    onClick()
                }
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("panic_alert_toggle_btn"),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isActive) Icons.Default.NotificationsActive else Icons.Default.Warning,
                    contentDescription = "Botón de Pánico",
                    tint = Color.White,
                    modifier = Modifier
                        .size(18.dp)
                        .scale(iconScale)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isActive) "🚨 S.O.S. ACTIVO" else "Pánico",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 12.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
