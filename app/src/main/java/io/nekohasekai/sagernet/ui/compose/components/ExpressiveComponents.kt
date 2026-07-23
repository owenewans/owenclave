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
 * (Cookie, Clover, Sunny, Gem, Flower, ...). Morphs to a different shape while pressed.
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
    val morph = remember(shape, pressedShape) { Morph(shape, pressedShape) }
    val progress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow,
        ),
        label = "shapedIconMorph",
    )
    Box(
        modifier = modifier
            .size(size)
            .clip(MorphPolygonShape(morph, progress))
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
