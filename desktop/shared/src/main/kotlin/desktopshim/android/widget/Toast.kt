package android.widget

import android.content.Context
import android.util.Log

class Toast private constructor(private val text: CharSequence, private val duration: Int) {
    companion object {
        const val LENGTH_SHORT = 0
        const val LENGTH_LONG = 1

        @Volatile
        var toastSink: ((String) -> Unit)? = null

        @JvmStatic
        fun makeText(context: Context?, text: CharSequence?, duration: Int): Toast =
            Toast(text ?: "", duration)

        @JvmStatic
        fun makeText(context: Context?, resId: Int, duration: Int): Toast =
            Toast(context?.getString(resId) ?: "", duration)
    }

    fun show() {
        val msg = text.toString()
        Log.i("Toast", msg)
        toastSink?.invoke(msg)
    }
}

