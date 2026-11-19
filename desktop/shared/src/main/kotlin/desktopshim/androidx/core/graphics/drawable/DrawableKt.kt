package androidx.core.graphics.drawable

import android.graphics.Bitmap
import android.graphics.drawable.Drawable

fun Drawable.toBitmap(width: Int = 1, height: Int = 1, config: Any? = null): Bitmap =
    Bitmap(width.coerceAtLeast(1), height.coerceAtLeast(1))

