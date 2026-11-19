package android.graphics

import java.awt.geom.AffineTransform
import java.awt.geom.Path2D

class Matrix {
    val awtTransform = AffineTransform()

    fun setScale(sx: Float, sy: Float) {
        awtTransform.setToScale(sx.toDouble(), sy.toDouble())
    }

    fun postTranslate(dx: Float, dy: Float) {
        val tr = AffineTransform.getTranslateInstance(dx.toDouble(), dy.toDouble())
        tr.concatenate(awtTransform)
        awtTransform.setTransform(tr)
    }
}

class Path {
    var awtPath = Path2D.Float()

    fun set(src: Path) {
        awtPath = src.awtPath.clone() as Path2D.Float
    }

    fun reset() {
        awtPath.reset()
    }

    fun moveTo(x: Float, y: Float) {
        awtPath.moveTo(x, y)
    }

    fun lineTo(x: Float, y: Float) {
        awtPath.lineTo(x, y)
    }

    fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) {
        awtPath.quadTo(x1, y1, x2, y2)
    }

    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        awtPath.curveTo(x1, y1, x2, y2, x3, y3)
    }

    fun close() {
        awtPath.closePath()
    }

    fun transform(matrix: Matrix) {
        awtPath.transform(matrix.awtTransform)
    }
}

class Bitmap(val width: Int = 1, val height: Int = 1)

