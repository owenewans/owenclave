package io.nekohasekai.sagernet.ui.compose.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.CircularProgressIndicator
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
        ServiceState.IDLE -> MaterialTheme.colorScheme.surfaceContainerHigh
        ServiceState.CONNECTING -> MaterialTheme.colorScheme.primary
        ServiceState.CONNECTED -> MaterialTheme.colorScheme.primary
        ServiceState.ERROR -> MaterialTheme.colorScheme.error
    }

    val contentColor = when (state) {
        ServiceState.IDLE -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onPrimary
    }

    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(56.dp),
        shape = CircleShape,
        containerColor = color,
        contentColor = contentColor,
    ) {
        when (state) {
            ServiceState.IDLE -> Icon(Icons.Default.PowerSettingsNew, contentDescription = "Start")
            ServiceState.CONNECTING -> CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 3.dp,
                color = contentColor,
            )
            ServiceState.CONNECTED -> Icon(Icons.Default.FlightTakeoff, contentDescription = "Connected")
            ServiceState.ERROR -> Icon(Icons.Default.PowerSettingsNew, contentDescription = "Error - tap to retry")
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
        shape = RoundedCornerShape(0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )

            if (uplinkSpeed.isNotEmpty() || downlinkSpeed.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SpeedIndicator(label = "↑", value = uplinkSpeed, color = MaterialTheme.colorScheme.primary)
                    SpeedIndicator(label = "↓", value = downlinkSpeed, color = MaterialTheme.colorScheme.tertiary)
                    if (showDirectSpeed) {
                        SpeedIndicator(label = "↑", value = directUplink, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SpeedIndicator(label = "↓", value = directDownlink, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            textAlign = TextAlign.End,
        )
    }
}
