package androidx.graphics.shapes

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class RoundedPolygon(
    val lobes: Int = 8,
    val waveDepth: Float = 0.08f,
    val rotationRad: Float = 0f,
) {
    fun samplePoint(angleRad: Float): Pair<Float, Float> {
        val r = if (lobes <= 1) {
            0.5f
        } else {
            val baseRadius = 0.5f - waveDepth * 0.5f
            baseRadius + waveDepth * 0.5f * cos(lobes * (angleRad - rotationRad))
        }
        val x = 0.5f + r * cos(angleRad)
        val y = 0.5f + r * sin(angleRad)
        return x to y
    }
}

class Morph(
    val start: RoundedPolygon,
    val end: RoundedPolygon,
)

fun RoundedPolygon.toPath(path: android.graphics.Path = android.graphics.Path()): android.graphics.Path {
    path.reset()
    val steps = 96
    for (i in 0 until steps) {
        val angle = (2.0 * PI * i / steps).toFloat()
        val (x, y) = samplePoint(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

fun Morph.toPath(
    progress: Float,
    path: android.graphics.Path = android.graphics.Path(),
): android.graphics.Path {
    path.reset()
    val p = progress.coerceIn(0f, 1f)
    val steps = 96
    for (i in 0 until steps) {
        val angle = (2.0 * PI * i / steps).toFloat()
        val (x0, y0) = start.samplePoint(angle)
        val (x1, y1) = end.samplePoint(angle)
        val x = x0 + (x1 - x0) * p
        val y = y0 + (y1 - y0) * p
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

