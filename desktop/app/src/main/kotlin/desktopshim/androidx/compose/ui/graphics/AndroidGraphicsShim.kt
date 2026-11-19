package androidx.compose.ui.graphics

import java.awt.geom.PathIterator

fun android.graphics.Path.asComposePath(): Path {
    val composePath = Path()
    val iter = this.awtPath.getPathIterator(null)
    val coords = FloatArray(6)
    while (!iter.isDone) {
        when (iter.currentSegment(coords)) {
            PathIterator.SEG_MOVETO -> composePath.moveTo(coords[0], coords[1])
            PathIterator.SEG_LINETO -> composePath.lineTo(coords[0], coords[1])
            PathIterator.SEG_QUADTO -> composePath.quadraticTo(coords[0], coords[1], coords[2], coords[3])
            PathIterator.SEG_CUBICTO -> composePath.cubicTo(coords[0], coords[1], coords[2], coords[3], coords[4], coords[5])
            PathIterator.SEG_CLOSE -> composePath.close()
        }
        iter.next()
    }
    return composePath
}

fun android.graphics.Bitmap.asImageBitmap(): ImageBitmap =
    ImageBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1))

