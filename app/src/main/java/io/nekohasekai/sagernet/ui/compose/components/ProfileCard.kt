package io.nekohasekai.sagernet.ui.compose.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.nekohasekai.sagernet.database.ProxyEntity

@Composable
fun ProfileCard(
    entity: ProxyEntity,
    selected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onShare: (() -> Unit)? = null,
    onDelete: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceContainerLow,
        label = "cardColor"
    )

    val onContainerColor by animateColorAsState(
        targetValue = if (selected)
            MaterialTheme.colorScheme.onPrimaryContainer
        else
            MaterialTheme.colorScheme.onSurface,
        label = "cardContentColor"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 2.dp else 0.5.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary
                        else Color.Transparent
                    )
            )
            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = entity.displayName(),
                    style = MaterialTheme.typography.titleMedium,
                    color = onContainerColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = entity.displayAddress(),
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainerColor.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = entity.displayType(),
                        style = MaterialTheme.typography.labelSmall,
                        color = onContainerColor.copy(alpha = 0.6f),
                    )
                    if (entity.tx > 0 || entity.rx > 0) {
                        Text(
                            text = formatTraffic(entity.tx, entity.rx),
                            style = MaterialTheme.typography.labelSmall,
                            color = onContainerColor.copy(alpha = 0.6f),
                        )
                    }
                    if (entity.ping > 0) {
                        Text(
                            text = "${entity.ping}ms",
                            style = MaterialTheme.typography.labelSmall,
                            color = when (entity.status) {
                                1 -> Color(0xFF4CAF50)
                                3 -> Color(0xFFF44336)
                                else -> onContainerColor.copy(alpha = 0.6f)
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Edit",
                    tint = onContainerColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp),
                )
            }
            if (onShare != null) {
                IconButton(onClick = onShare, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Share",
                        tint = onContainerColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = onContainerColor.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private fun formatTraffic(tx: Long, rx: Long): String {
    val total = tx + rx
    return when {
        total < 1024 -> "${total}B"
        total < 1024 * 1024 -> "%.1fKB".format(total / 1024.0)
        total < 1024 * 1024 * 1024 -> "%.1fMB".format(total / (1024.0 * 1024))
        else -> "%.2fGB".format(total / (1024.0 * 1024 * 1024))
    }
}

@Composable
fun ProfileStatusBadge(status: Int, ping: Int) {
    val (color, text) = when (status) {
        1 -> Color(0xFF4CAF50) to "OK"
        2 -> Color(0xFFFF9800) to "Testing"
        3 -> Color(0xFFF44336) to "Error"
        else -> MaterialTheme.colorScheme.onSurfaceVariant to "Unknown"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
        Text(
            text = if (ping > 0 && status == 1) "${ping}ms" else text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}
