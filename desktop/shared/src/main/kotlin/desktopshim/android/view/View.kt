package android.view

import android.content.Context

open class View(open val context: Context? = null) {
    var layoutParams: ViewGroup.LayoutParams? = null

    companion object {
        const val VISIBLE = 0
        const val INVISIBLE = 4
        const val GONE = 8
        const val LAYOUT_DIRECTION_LTR = 0
        const val LAYOUT_DIRECTION_RTL = 1
    }
}

open class ViewGroup(context: Context? = null) : View(context) {
    open class LayoutParams(var width: Int = MATCH_PARENT, var height: Int = MATCH_PARENT) {
        companion object {
            const val MATCH_PARENT = -1
            const val WRAP_CONTENT = -2
        }
    }
}
