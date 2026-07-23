package io.nekohasekai.sagernet.ui.compose.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A single layer of a logo: a vector path (in a 256x256 viewport) filled with a
 * muted blue-grey tone.
 */
internal data class LogoLayer(
    val pathData: String,
    val color: Color,
    val alpha: Float = 1f,
)

/**
 * A selectable app logo. All logos share the muted blue-grey ("Duster") palette
 * and carry a transparent, punched-out letter "O" in the centre.
 */
enum class AppLogoStyle(
    val id: Int,
    val label: String,
    internal val layers: List<LogoLayer>,
    internal val oOuter: Float = 46f,
    internal val oInner: Float = 26f,
    /** When set, the O is drawn as a solid ring instead of a transparent cut-out. */
    internal val ringO: Boolean = false,
) {
    SPLIT(
        0, "Split",
        listOf(
            LogoLayer(
                "M 118.00 -2.60 C 143.13 0.07 165.90 43.45 183.37 67.87 C 200.84 92.29 227.50 122.36 222.80 143.92 C 218.10 165.47 181.69 182.41 155.18 197.20 C 128.67 211.99 87.64 241.49 63.75 232.66 C 39.85 223.83 17.00 174.37 11.81 144.24 C 6.62 114.11 14.89 76.36 32.59 51.89 C 50.29 27.42 92.87 -5.26 118.00 -2.60 Z",
                Color(0xFF4A5866),
            ),
            LogoLayer(
                "M 140.00 49.04 C 169.05 49.44 211.58 37.24 225.94 52.06 C 240.29 66.89 226.29 109.53 226.12 138.00 C 225.94 166.47 239.25 203.70 224.89 222.89 C 210.54 242.09 167.52 253.95 140.00 253.17 C 112.48 252.39 75.15 237.40 59.79 218.21 C 44.44 199.01 49.20 166.09 47.85 138.00 C 46.49 109.91 36.30 64.48 51.65 49.65 C 67.01 34.83 110.95 48.64 140.00 49.04 Z",
                Color(0xFF5B6B7B),
                alpha = 0.92f,
            ),
        ),
    ),
    FLOWER(
        1, "Flower",
        listOf(LogoLayer(scalloped(128f, 128f, 116f, 8, 32f), Color(0xFF5B6B7B))),
    ),
    COOKIE(
        2, "Cookie",
        listOf(LogoLayer(scalloped(128f, 128f, 116f, 9, 20f), Color(0xFF647585))),
    ),
    SUNNY(
        3, "Sunny",
        listOf(LogoLayer(scalloped(128f, 128f, 116f, 12, 24f), Color(0xFF516170))),
        oOuter = 44f, oInner = 25f,
    ),
    BLOB(
        4, "Blob",
        listOf(LogoLayer(blob(21, 128f, 128f, 116f, 9, 0.28f), Color(0xFF5B6B7B))),
    ),
    BEAN(
        5, "Bean",
        listOf(LogoLayer(bean(128f, 128f, 96f), Color(0xFF5B6B7B))),
        oOuter = 42f, oInner = 24f,
    ),
    GEM(
        6, "Gem",
        listOf(
            LogoLayer(
                "M128 18 C150 18 168 30 188 42 C208 54 220 66 226 92 C232 118 226 138 226 164 C226 190 208 202 188 214 C168 226 150 238 128 238 C106 238 88 226 68 214 C48 202 30 190 30 164 C30 138 24 118 30 92 C36 66 48 54 68 42 C88 30 106 18 128 18 Z",
                Color(0xFF5B6B7B),
            ),
        ),
        oOuter = 50f, ringO = true,
    ),
    ;

    companion object {
        fun fromId(id: Int): AppLogoStyle = entries.firstOrNull { it.id == id } ?: SPLIT
    }
}

private val oRingColor = Color(0xFFAEBCC9)

/**
 * Draws the selected [style] logo, tinted with its own muted palette. Renders
 * the punched-out "O" using a transparent cut-out (respecting alpha) so it looks
 * correct on any background.
 */
@Composable
fun AppLogo(
    style: AppLogoStyle,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
) {
    val parsedLayers = remember(style) {
        style.layers.map { it to PathParser().parsePathString(it.pathData).toPath() }
    }
    Canvas(modifier = modifier.size(size)) {
        val s = this.size.minDimension / 256f
        scale(s, s, pivot = Offset.Zero) {
            drawLogo(style, parsedLayers)
        }
    }
}

private fun DrawScope.drawLogo(
    style: AppLogoStyle,
    parsedLayers: List<Pair<LogoLayer, Path>>,
) {
    if (style.ringO) {
        // Solid-fill shape + a light ring for the O.
        parsedLayers.forEach { (layer, path) ->
            drawPath(path, color = layer.color, alpha = layer.alpha, style = Fill)
        }
        drawCircle(
            color = oRingColor,
            radius = style.oOuter,
            center = Offset(128f, 128f),
            style = Stroke(width = (style.oOuter - style.oInner)),
        )
    } else {
        // Draw all layers into an offscreen layer, then punch a transparent O.
        val paint = androidx.compose.ui.graphics.Paint()
        drawContext.canvas.saveLayer(
            androidx.compose.ui.geometry.Rect(0f, 0f, 256f, 256f),
            paint,
        )
        parsedLayers.forEach { (layer, path) ->
            drawPath(path, color = layer.color, alpha = layer.alpha, style = Fill)
        }
        // Clear an annulus: clear the outer disc, then restore the inner disc.
        drawCircle(
            color = Color.Transparent,
            radius = style.oOuter,
            center = Offset(128f, 128f),
            blendMode = BlendMode.Clear,
        )
        drawContext.canvas.restore()
    }
}

// ── Procedural shape generators (mirror the SVG preview generator) ──

private fun catmullRom(pts: List<Offset>): String {
    val p = ArrayList<Offset>(pts.size + 3)
    p.add(pts.last()); p.addAll(pts); p.add(pts[0]); p.add(pts[1])
    val sb = StringBuilder()
    sb.append("M ${p[1].x} ${p[1].y} ")
    for (i in 1 until p.size - 2) {
        val p0 = p[i - 1]; val p1 = p[i]; val p2 = p[i + 1]; val p3 = p[i + 2]
        val c1x = p1.x + (p2.x - p0.x) / 6; val c1y = p1.y + (p2.y - p0.y) / 6
        val c2x = p2.x - (p3.x - p1.x) / 6; val c2y = p2.y - (p3.y - p1.y) / 6
        sb.append("C $c1x $c1y $c2x $c2y ${p2.x} ${p2.y} ")
    }
    sb.append("Z")
    return sb.toString()
}

private fun scalloped(cx: Float, cy: Float, R: Float, petals: Int, depth: Float): String {
    val steps = petals * 40
    val pts = ArrayList<Offset>(steps)
    for (i in 0 until steps) {
        val a = 2 * Math.PI * i / steps
        val r = R - depth * (0.5 - 0.5 * Math.cos(petals * a))
        pts.add(
            Offset(
                (cx + r * Math.cos(a - Math.PI / 2)).toFloat(),
                (cy + r * Math.sin(a - Math.PI / 2)).toFloat(),
            ),
        )
    }
    // scalloped uses straight segments in the SVG; polyline is fine here too.
    val sb = StringBuilder("M ${pts[0].x} ${pts[0].y} ")
    for (i in 1 until pts.size) sb.append("L ${pts[i].x} ${pts[i].y} ")
    sb.append("Z")
    return sb.toString()
}

private fun blob(seed: Int, cx: Float, cy: Float, R: Float, n: Int, jitter: Float): String {
    var a = seed
    fun rnd(): Double {
        a = a + -0x61c88647
        var t = a xor (a ushr 15)
        t *= 1 or a
        t = t xor (t + (t xor (t ushr 7)) * (61 or t))
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toDouble() / 4294967296.0
    }
    val pts = ArrayList<Offset>(n)
    for (i in 0 until n) {
        val ang = 2 * Math.PI * i / n
        val r = R * (1 - jitter + jitter * 2 * rnd())
        pts.add(
            Offset(
                (cx + r * Math.cos(ang - Math.PI / 2)).toFloat(),
                (cy + r * Math.sin(ang - Math.PI / 2)).toFloat(),
            ),
        )
    }
    return catmullRom(pts)
}

private fun bean(cx: Float, cy: Float, R: Float): String {
    val n = 72
    val pts = ArrayList<Offset>(n)
    for (i in 0 until n) {
        val t = 2 * Math.PI * i / n
        val r = R * (1 + 0.28 * Math.cos(t)) * (1 - 0.18 * Math.cos(2 * t))
        pts.add(
            Offset(
                (cx + r * Math.cos(t)).toFloat(),
                (cy + r * Math.sin(t) * 0.9).toFloat(),
            ),
        )
    }
    return catmullRom(pts)
}
