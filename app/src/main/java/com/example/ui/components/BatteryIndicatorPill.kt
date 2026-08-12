package com.example.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Power
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.GoldPrimary
import com.example.ui.theme.NavyDark
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningOrange

@Composable
fun BatteryIndicatorPill(
    showDetailedLabel: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var batteryPercentage by remember { mutableIntStateOf(85) }
    var isCharging by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(cntx: Context?, intent: Intent?) {
                if (intent != null) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (level != -1 && scale != -1) {
                        batteryPercentage = (level * 100 / scale.toFloat()).toInt()
                    }

                    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                    isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL
                }
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val stickyIntent = context.registerReceiver(receiver, filter)

        // Read initial sticky intent
        stickyIntent?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level != -1 && scale != -1) {
                batteryPercentage = (level * 100 / scale.toFloat()).toInt()
            }
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
        }

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val batteryColor = when {
        isCharging -> SuccessGreen
        batteryPercentage > 50 -> SuccessGreen
        batteryPercentage > 20 -> WarningOrange
        else -> Color(0xFFEF4444) // Danger Red
    }

    val icon = when {
        isCharging -> Icons.Default.BatteryChargingFull
        batteryPercentage <= 20 -> Icons.Default.BatteryAlert
        batteryPercentage <= 50 -> Icons.Default.BatterySaver
        else -> Icons.Default.BatteryFull
    }

    Column(horizontalAlignment = Alignment.End) {
        Surface(
            modifier = modifier.testTag("battery_indicator_pill"),
            shape = RoundedCornerShape(20.dp),
            color = NavyDark.copy(alpha = 0.85f),
            border = androidx.compose.foundation.BorderStroke(1.dp, batteryColor.copy(alpha = 0.8f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "Batería de Dispositivo",
                    tint = batteryColor,
                    modifier = Modifier.size(16.dp)
                )

                Text(
                    text = if (isCharging) "⚡ $batteryPercentage%" else "$batteryPercentage%",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                if (showDetailedLabel) {
                    Spacer(modifier = Modifier.width(2.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(batteryColor, CircleShape)
                    )
                }
            }
        }

        // Low battery alert tooltip for guard shift awareness
        AnimatedVisibility(visible = batteryPercentage <= 15 && !isCharging) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFEF4444),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = "⚠️ ¡BATERÍA BAJA GARITA!",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}
