package io.nekohasekai.sagernet.ui.compose.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph

enum class ServiceState {
    IDLE,
    CONNECTING,
    CONNECTED,
    ERROR,
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
        targetValue = if (state == ServiceState.CONNECTED) 72f else 60f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "fabSize",
    )

    val pressScale = animateFloatAsState(
        targetValue = if (state == ServiceState.CONNECTING) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "pressScale",
    )

    // Signature M3E morph: the FAB container morphs from a circle to a cookie
    // while connecting, then settles. Longer, deliberate motion.
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val active = state == ServiceState.CONNECTING || state == ServiceState.CONNECTED
    val morphProgress = animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessVeryLow,
        ),
        label = "fabMorph",
    )
    val fabShape = MorphPolygonShape(morph, morphProgress.value)

    FloatingActionButton(
        onClick = onClick,
        modifier = modifier
            .size(size.value.dp)
            .graphicsLayer {
                scaleX = pressScale.value
                scaleY = pressScale.value
            },
        shape = fabShape,
        containerColor = color,
        contentColor = contentColor,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = if (state == ServiceState.IDLE) 3.dp else 8.dp,
        ),
    ) {
        when (state) {
            ServiceState.IDLE -> Icon(Icons.Default.PowerSettingsNew, contentDescription = "Start", modifier = Modifier.size(28.dp))
            ServiceState.CONNECTING -> ConnectingIndicator(contentColor)
            ServiceState.CONNECTED -> Icon(Icons.Default.Bolt, contentDescription = "Connected", modifier = Modifier.size(34.dp))
            ServiceState.ERROR -> Icon(Icons.Default.PowerSettingsNew, contentDescription = "Error - tap to retry", modifier = Modifier.size(28.dp))
        }
    }
}

/**
 * A spinning gear behind a STATIC lightning bolt — the gear rotates around its
 * own axis to signal "connecting", the bolt stays put.
 */
@Composable
private fun ConnectingIndicator(tint: Color) {
    val transition = rememberInfiniteTransition(label = "gearSpin")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "gearRotation",
    )
    Box(contentAlignment = Alignment.Center) {
        Icon(
            Icons.Default.Settings,
            contentDescription = "Connecting",
            tint = tint.copy(alpha = 0.55f),
            modifier = Modifier
                .size(34.dp)
                .graphicsLayer { rotationZ = rotation },
        )
        Icon(
            Icons.Default.Bolt,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
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
    connected: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 10.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                // Pulsing status dot for expressive feedback.
                StatusDot(connected = connected)
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }

            if (uplinkSpeed.isNotEmpty() || downlinkSpeed.isNotEmpty()) {
                Spacer(Modifier.width(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SpeedIndicator(label = "↑", value = uplinkSpeed, color = MaterialTheme.colorScheme.primary)
                    SpeedIndicator(label = "↓", value = downlinkSpeed, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}

@Composable
private fun StatusDot(connected: Boolean) {
    val transition = rememberInfiniteTransition(label = "dotPulse")
    val alpha by transition.animateFloat(
        initialValue = if (connected) 0.4f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dotAlpha",
    )
    val dotColor = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .size(10.dp)
            .graphicsLayer { this.alpha = if (connected) alpha else 1f }
            .clip(CircleShape)
            .background(dotColor),
    )
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
