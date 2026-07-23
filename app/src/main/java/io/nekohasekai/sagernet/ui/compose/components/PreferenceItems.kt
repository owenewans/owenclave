package io.nekohasekai.sagernet.ui.compose.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun PreferenceHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 28.dp, top = 24.dp, bottom = 10.dp, end = 24.dp)
    )
}

/**
 * A single expressive preference row rendered as its own [Surface] with a
 * per-position corner [shape] so a run of items forms one rounded container.
 * The leading icon sits inside a distinctive [MaterialShapes] cookie container.
 */
@Composable
fun PreferenceItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconContainerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    iconContentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(6.dp),
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailingContent: @Composable (RowScope.() -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else 0.38f

    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (enabled && onClick != null) Modifier.clickable { onClick() } else Modifier)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                ShapedIconStatic(
                    icon = icon,
                    containerColor = iconContainerColor.copy(alpha = alpha),
                    contentColor = iconContentColor.copy(alpha = alpha),
                    size = 44.dp,
                )
                Spacer(Modifier.width(16.dp))
            }

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (trailingContent != null) {
                Spacer(Modifier.width(16.dp))
                trailingContent()
            }
        }
    }
}

@Composable
fun SwitchPreferenceItem(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconContainerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    iconContentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(6.dp),
    enabled: Boolean = true,
) {
    PreferenceItem(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconContainerColor = iconContainerColor,
        iconContentColor = iconContentColor,
        shape = shape,
        enabled = enabled,
        modifier = modifier,
        onClick = { if (enabled) onCheckedChange(!checked) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        }
    )
}

@Deprecated("Groups now use gaps + per-position shapes; DividerItem is a no-op spacer")
@Composable
fun DividerItem(modifier: Modifier = Modifier) {
    Spacer(modifier.height(2.dp))
}

/**
 * Groups a run of expressive rows so they read as ONE rounded container:
 * pass items via [PreferenceGroupScope.item]; each gets the correct per-position
 * corner shape and a 2dp gap automatically.
 */
class PreferenceGroupScope internal constructor() {
    internal val entries = mutableListOf<@Composable (shape: androidx.compose.ui.graphics.Shape) -> Unit>()

    fun item(content: @Composable (shape: androidx.compose.ui.graphics.Shape) -> Unit) {
        entries.add(content)
    }
}

@Composable
fun PreferenceGroup(
    modifier: Modifier = Modifier,
    content: PreferenceGroupScope.() -> Unit,
) {
    val scope = PreferenceGroupScope().apply(content)
    // The whole group is clipped as ONE soft rounded container (like the
    // protocol picker). Items inside get a uniform small radius and a 2dp gap,
    // so the outer clip defines the group's rounded silhouette — no harsh
    // squared corners on the first/last rows.
    val itemShape = RoundedCornerShape(6.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(GroupContainerShape),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        scope.entries.forEach { entry ->
            entry(itemShape)
        }
    }
}

/** Shared outer rounding used to group runs of items into one soft container. */
val GroupContainerShape = RoundedCornerShape(24.dp)

/** Legacy card wrapper kept for source compatibility; now just a group container. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(GroupContainerShape),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}
