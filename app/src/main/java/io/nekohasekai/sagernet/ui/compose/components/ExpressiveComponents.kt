@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package io.nekohasekai.sagernet.ui.compose.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath

/**
 * A Compose [Shape] driven by an [androidx.graphics.shapes.Morph] progress value.
 * This is the signature M3E "shape morphs when pressed" effect.
 */
class MorphPolygonShape(
    private val morph: Morph,
    private val percentage: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val matrix = android.graphics.Matrix()
        matrix.setScale(size.width, size.height)
        val androidPath = android.graphics.Path()
        androidPath.set(morph.toPath(percentage))
        androidPath.transform(matrix)
        return Outline.Generic(androidPath.asComposePath())
    }
}

/**
 * A colored icon sitting inside a distinctive M3E [MaterialShapes] container
 * (Cookie, Clover, Sunny, Gem, Flower, ...). Uses the polygon's static shape
 * (no per-frame morph) to avoid the GPU saturation / ANR that Morph caused.
 */
@Composable
fun ShapedIcon(
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shape: RoundedPolygon = MaterialShapes.Cookie7Sided,
    pressedShape: RoundedPolygon = MaterialShapes.Cookie12Sided,
    pressed: Boolean = false,
) {
    val activeShape = if (pressed) pressedShape else shape
    Box(
        modifier = modifier
            .size(size)
            .clip(activeShape.toShape())
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/**
 * Static shaped icon (no press morph) — cheaper for lists.
 * Uses the polygon's static [toShape] for the M3E look without per-frame cost.
 */
@Composable
fun ShapedIconStatic(
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shape: RoundedPolygon = MaterialShapes.Cookie7Sided,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape.toShape())
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/**
 * Per-position corner shape so a vertical stack of items reads as ONE rounded
 * container: big outer corners on the first/last item, tight inner corners in
 * between. Combine with a 2dp gap between items.
 */
fun groupedItemShape(index: Int, count: Int, outer: Dp = 22.dp, inner: Dp = 5.dp): RoundedCornerShape = when {
    count <= 1 -> RoundedCornerShape(outer)
    index == 0 -> RoundedCornerShape(topStart = outer, topEnd = outer, bottomStart = inner, bottomEnd = inner)
    index == count - 1 -> RoundedCornerShape(topStart = inner, topEnd = inner, bottomStart = outer, bottomEnd = outer)
    else -> RoundedCornerShape(inner)
}

/**
 * A transparent clipped column that groups expressive items together.
 */
@Composable
fun ExpressiveGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        content = content,
    )
}

/**
 * A deterministic playful [MaterialShapes] polygon derived from a string seed,
 * so each profile/group gets its own recognizable shape.
 */
val ExpressiveShapePalette: List<RoundedPolygon> = listOf(
    MaterialShapes.Cookie7Sided,
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Sunny,
    MaterialShapes.Gem,
    MaterialShapes.Flower,
    MaterialShapes.Cookie12Sided,
    MaterialShapes.PuffyDiamond,
    MaterialShapes.Pentagon,
    MaterialShapes.SoftBurst,
    MaterialShapes.Cookie6Sided,
    MaterialShapes.VerySunny,
)

fun shapeForSeed(seed: String): RoundedPolygon {
    val idx = (seed.hashCode() % ExpressiveShapePalette.size + ExpressiveShapePalette.size) % ExpressiveShapePalette.size
    return ExpressiveShapePalette[idx]
}

/**
 * A curated set of Material icons suitable for proxy/server profiles.
 * Indexed by [ProxyEntity.iconIndex]; -1 means "auto" (derived from name hash).
 */
val ProfileIconSet: List<ImageVector> = listOf(
    Icons.Filled.Bolt,
    Icons.Filled.Cloud,
    Icons.Filled.Public,
    Icons.Filled.Shield,
    Icons.Filled.Lock,
    Icons.Filled.VpnKey,
    Icons.Filled.Wifi,
    Icons.Filled.FlightTakeoff,
    Icons.Filled.Speed,
    Icons.Filled.Hub,
    Icons.Filled.Lan,
    Icons.Filled.Security,
)

/** Returns the icon for a profile, using its stored index or a name-derived fallback. */
fun profileIconFor(iconIndex: Int, name: String): ImageVector {
    return if (iconIndex in ProfileIconSet.indices) {
        ProfileIconSet[iconIndex]
    } else {
        ProfileIconSet[(name.hashCode() % ProfileIconSet.size + ProfileIconSet.size) % ProfileIconSet.size]
    }
}

/**
 * An M3E-flavoured text field: a soft, fully-rounded tonal field with no harsh
 * outline box. Replaces the default boxy [androidx.compose.material3.OutlinedTextField]
 * look used across the app's dialogs and forms.
 */
@Composable
fun ExpressiveTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    leadingIcon: ImageVector? = null,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    textStyle: androidx.compose.ui.text.TextStyle? = null,
) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = label?.let { { androidx.compose.material3.Text(it) } },
        placeholder = placeholder?.let { { androidx.compose.material3.Text(it) } },
        leadingIcon = leadingIcon?.let {
            {
                androidx.compose.material3.Icon(it, contentDescription = null)
            }
        },
        singleLine = singleLine,
        shape = RoundedCornerShape(20.dp),
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        textStyle = textStyle ?: androidx.compose.material3.MaterialTheme.typography.bodyLarge,
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            // A subtle always-on outline so the field stays visible even when
            // it sits on a same-tone dialog/popup surface.
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}
