package androidx.compose.material3

import android.content.Context
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath

@RequiresOptIn(message = "This Material3 Expressive API is experimental.")
@Retention(AnnotationRetention.BINARY)
annotation class ExperimentalMaterial3ExpressiveApi

interface MotionScheme {
    fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T>
    fun <T> fastSpatialSpec(): FiniteAnimationSpec<T>
    fun <T> slowSpatialSpec(): FiniteAnimationSpec<T>
    fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T>
    fun <T> fastEffectsSpec(): FiniteAnimationSpec<T>
    fun <T> slowEffectsSpec(): FiniteAnimationSpec<T>

    companion object {
        private val StandardImpl = object : MotionScheme {
            override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 700f)
            override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 1400f)
            override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 300f)
            override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 1.0f, stiffness = 1600f)
            override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 1.0f, stiffness = 3800f)
            override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 1.0f, stiffness = 800f)
        }

        private val ExpressiveImpl = object : MotionScheme {
            override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 380f)
            override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.6f, stiffness = 800f)
            override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 200f)
            override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 1.0f, stiffness = 1600f)
            override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 1.0f, stiffness = 3800f)
            override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = spring(dampingRatio = 1.0f, stiffness = 800f)
        }

        fun standard(): MotionScheme = StandardImpl
        fun expressive(): MotionScheme = ExpressiveImpl

        @JvmName("standard\$material3")
        fun standardInternal(): MotionScheme = StandardImpl

        @JvmName("expressive\$material3")
        fun expressiveInternal(): MotionScheme = ExpressiveImpl
    }
}

@Composable
fun MaterialExpressiveTheme(
    colorScheme: ColorScheme? = null,
    shapes: Shapes? = null,
    typography: Typography? = null,
    motionScheme: MotionScheme? = null,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = colorScheme ?: MaterialTheme.colorScheme,
        shapes = shapes ?: MaterialTheme.shapes,
        typography = typography ?: MaterialTheme.typography,
        content = content,
    )
}

fun Shapes(
    extraSmall: CornerBasedShape = ShapeDefaults.ExtraSmall,
    small: CornerBasedShape = ShapeDefaults.Small,
    medium: CornerBasedShape = ShapeDefaults.Medium,
    large: CornerBasedShape = ShapeDefaults.Large,
    extraLarge: CornerBasedShape = ShapeDefaults.ExtraLarge,
    extraExtraLarge: CornerBasedShape = RoundedCornerShape(32.dp),
): Shapes = Shapes(
    extraSmall = extraSmall,
    small = small,
    medium = medium,
    large = large,
    extraLarge = extraLarge,
)

fun dynamicDarkColorScheme(context: Context): ColorScheme = darkColorScheme()

fun dynamicLightColorScheme(context: Context): ColorScheme = lightColorScheme()

object MaterialShapes {
    val Circle = RoundedPolygon(lobes = 1, waveDepth = 0f)
    val Square = RoundedPolygon(lobes = 4, waveDepth = 0.06f, rotationRad = 0.785f)
    val Slanted = RoundedPolygon(lobes = 4, waveDepth = 0.05f, rotationRad = 0.4f)
    val Arch = RoundedPolygon(lobes = 4, waveDepth = 0.07f)
    val Oval = RoundedPolygon(lobes = 2, waveDepth = 0.08f)
    val Pill = RoundedPolygon(lobes = 2, waveDepth = 0.06f)
    val Triangle = RoundedPolygon(lobes = 3, waveDepth = 0.08f)
    val Diamond = RoundedPolygon(lobes = 4, waveDepth = 0.09f)
    val ClamShell = RoundedPolygon(lobes = 5, waveDepth = 0.07f)
    val Pentagon = RoundedPolygon(lobes = 5, waveDepth = 0.05f)
    val Gem = RoundedPolygon(lobes = 6, waveDepth = 0.06f)
    val Sunny = RoundedPolygon(lobes = 8, waveDepth = 0.08f)
    val VerySunny = RoundedPolygon(lobes = 12, waveDepth = 0.09f)
    val Cookie4Sided = RoundedPolygon(lobes = 4, waveDepth = 0.07f)
    val Cookie6Sided = RoundedPolygon(lobes = 6, waveDepth = 0.07f)
    val Cookie7Sided = RoundedPolygon(lobes = 7, waveDepth = 0.07f)
    val Cookie9Sided = RoundedPolygon(lobes = 9, waveDepth = 0.07f)
    val Cookie12Sided = RoundedPolygon(lobes = 12, waveDepth = 0.06f)
    val Ghostish = RoundedPolygon(lobes = 5, waveDepth = 0.08f)
    val Clover4Leaf = RoundedPolygon(lobes = 4, waveDepth = 0.11f, rotationRad = 0.785f)
    val Clover8Leaf = RoundedPolygon(lobes = 8, waveDepth = 0.09f)
    val Burst = RoundedPolygon(lobes = 10, waveDepth = 0.10f)
    val SoftBurst = RoundedPolygon(lobes = 10, waveDepth = 0.06f)
    val Boom = RoundedPolygon(lobes = 8, waveDepth = 0.10f)
    val SoftBoom = RoundedPolygon(lobes = 8, waveDepth = 0.06f)
    val Flower = RoundedPolygon(lobes = 8, waveDepth = 0.09f)
    val Puffy = RoundedPolygon(lobes = 6, waveDepth = 0.08f)
    val PuffyDiamond = RoundedPolygon(lobes = 4, waveDepth = 0.08f)
    val Bun = RoundedPolygon(lobes = 3, waveDepth = 0.06f)
    val Heart = RoundedPolygon(lobes = 3, waveDepth = 0.09f)
}

@Composable
fun RoundedPolygon.toShape(): Shape {
    return remember(this) {
        object : Shape {
            override fun createOutline(
                size: Size,
                layoutDirection: LayoutDirection,
                density: Density,
            ): Outline {
                val matrix = android.graphics.Matrix()
                matrix.setScale(size.width, size.height)
                val androidPath = this@toShape.toPath()
                androidPath.transform(matrix)
                return Outline.Generic(androidPath.asComposePath())
            }
        }
    }
}

@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    CircularProgressIndicator(
        modifier = modifier,
        color = color,
    )
}

@Composable
fun CircularWavyProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = color.copy(alpha = 0.2f),
) {
    CircularProgressIndicator(
        modifier = modifier,
        color = color,
        trackColor = trackColor,
    )
}

@Composable
fun CircularWavyProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = color.copy(alpha = 0.2f),
) {
    CircularProgressIndicator(
        progress = progress,
        modifier = modifier,
        color = color,
        trackColor = trackColor,
    )
}

@Composable
fun LinearWavyProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = color.copy(alpha = 0.2f),
) {
    LinearProgressIndicator(
        modifier = modifier,
        color = color,
        trackColor = trackColor,
    )
}

@Composable
fun LinearWavyProgressIndicator(
    progress: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = color.copy(alpha = 0.2f),
) {
    LinearProgressIndicator(
        progress = progress,
        modifier = modifier,
        color = color,
        trackColor = trackColor,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LargeFlexibleTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = colors,
        scrollBehavior = scrollBehavior,
    )
}

data class ToggleButtonShapes(
    val shape: Shape,
    val pressedShape: Shape = shape,
    val checkedShape: Shape = shape,
)

object ButtonGroupDefaults {
    val ConnectedSpaceBetween: Dp = 2.dp

    @Composable
    fun connectedLeadingButtonShapes(): ToggleButtonShapes = ToggleButtonShapes(
        shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp, topEnd = 6.dp, bottomEnd = 6.dp),
        checkedShape = RoundedCornerShape(20.dp),
    )

    @Composable
    fun connectedMiddleButtonShapes(): ToggleButtonShapes = ToggleButtonShapes(
        shape = RoundedCornerShape(6.dp),
        checkedShape = RoundedCornerShape(20.dp),
    )

    @Composable
    fun connectedTrailingButtonShapes(): ToggleButtonShapes = ToggleButtonShapes(
        shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 20.dp, bottomEnd = 20.dp),
        checkedShape = RoundedCornerShape(20.dp),
    )
}

object ToggleButtonDefaults {
    val IconSpacing: Dp = 8.dp
}

@Composable
fun ToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shapes: ToggleButtonShapes = ToggleButtonShapes(RoundedCornerShape(20.dp)),
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val activeShape = if (checked) shapes.checkedShape else shapes.shape
    val containerColor = if (checked) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = if (checked) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        onClick = { if (enabled) onCheckedChange(!checked) },
        modifier = modifier.defaultMinSize(minHeight = 40.dp),
        enabled = enabled,
        shape = activeShape,
        color = containerColor,
        contentColor = contentColor,
        border = if (!checked) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)) else null,
    ) {
        Row(
            modifier = Modifier.padding(contentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}
