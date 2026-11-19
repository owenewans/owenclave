package androidx.camera.view

import android.content.Context
import android.view.View

class PreviewView(context: Context) : View(context) {
    enum class ScaleType {
        FILL_START,
        FILL_CENTER,
        FILL_END,
        FIT_START,
        FIT_CENTER,
        FIT_END,
    }

    var scaleType: ScaleType = ScaleType.FILL_CENTER
}

