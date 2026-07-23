package io.nekohasekai.sagernet.ui.compose.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ServiceState {
    IDLE,
    CONNECTING,
    CONNECTED,
    ERROR,
}

@Composable
fun ServiceButton(
    state: ServiceState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = when (state) {
        ServiceState.IDLE -> MaterialTheme.colorScheme.primaryContainer
        ServiceState.CONNECTING -> MaterialTheme.colorScheme.primary
        ServiceState.CONNECTED -> MaterialTheme.colorScheme.primary
        ServiceState.ERROR -> MaterialTheme.colorScheme.error
    }

    val contentColor = when (state) {
        ServiceState.IDLE -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onPrimary
    }

    val size = animateFloatAsState(
        targetValue = if (state == ServiceState.CONNECTED) 64f else 56f,
        animationSpec = tween(300),
        label = "fabSize",
    )

    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(size.value.dp),
        shape = CircleShape,
        containerColor = color,
        contentColor = contentColor,
        elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(
            defaultElevation = if (state == ServiceState.IDLE) 3.dp else 6.dp,
        ),
    ) {
        when (state) {
            ServiceState.IDLE -> Icon(Icons.Default.PowerSettingsNew, contentDescription = "Start", modifier = Modifier.size(28.dp))
            ServiceState.CONNECTING -> CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 3.dp,
                color = contentColor,
            )
            ServiceState.CONNECTED -> Icon(Icons.Default.FlightTakeoff, contentDescription = "Connected", modifier = Modifier.size(32.dp))
            ServiceState.ERROR -> Icon(Icons.Default.PowerSettingsNew, contentDescription = "Error - tap to retry", modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
fun StatsBar(
    statusText: String,
    uplinkSpeed: String,
    downlinkSpeed: String,
    showDirectSpeed: Boolean = false,
    directUplink: String = "",
    directDownlink: String = "",
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )

            if (uplinkSpeed.isNotEmpty() || downlinkSpeed.isNotEmpty()) {
                Spacer(Modifier.width(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    SpeedIndicator(label = "↑", value = uplinkSpeed, color = MaterialTheme.colorScheme.primary)
                    SpeedIndicator(label = "↓", value = downlinkSpeed, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}

@Composable
private fun SpeedIndicator(
    label: String,
    value: String,
    color: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            textAlign = TextAlign.End,
        )
    }
}
