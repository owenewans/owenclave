package io.nekohasekai.sagernet.widget

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.res.TypedArrayUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.setPadding
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.R
import kotlin.math.roundToInt

class ColorPickerPreference
@JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = TypedArrayUtils.getAttr(
        context,
        androidx.preference.R.attr.editTextPreferenceStyle,
        android.R.attr.editTextPreferenceStyle
    )
) : Preference(
    context, attrs, defStyle
) {

    var inited = false

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val widgetFrame = holder.findViewById(android.R.id.widget_frame) as LinearLayout

        if (!inited) {
            inited = true

            val colors = context.resources.getIntArray(R.array.material_colors)
            var i = getPersistedInt(2)
            if (i > colors.size) {
                i = colors.size
            }

            widgetFrame.addView(
                getImageViewAtColor(
                    colors[i - 1],
                    36,
                    0
                )
            )
            widgetFrame.visibility = View.VISIBLE
        }
    }

    fun getImageViewAtColor(color: Int, sizeDp: Int, paddingDp: Int): ImageView {
        // dp to pixel
        val factor = context.resources.displayMetrics.density
        val size = (sizeDp * factor).roundToInt()
        val paddingSize = (paddingDp * factor).roundToInt()

        return ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(size, size)
            setPadding(paddingSize)
            setImageDrawable(getColor(resources, color))
        }
    }

    fun getColor(res: Resources, color: Int): Drawable {
        val drawable = ResourcesCompat.getDrawable(
            res,
            R.drawable.ic_baseline_fiber_manual_record_24,
            null
        )!!
        DrawableCompat.setTint(drawable.mutate(), color)
        return drawable
    }

    override fun onClick() {
        super.onClick()

        lateinit var dialog: AlertDialog

        val grid = GridLayout(context).apply {
            columnCount = 4

            val colors = context.resources.getIntArray(R.array.material_colors)
            var i = 0

            for (color in colors) {
                i++

                val themeId = i
                val view = getImageViewAtColor(color, 64, 0).apply {
                    setOnClickListener {
                        persistInt(themeId)
                        dialog.dismiss()
                        callChangeListener(themeId)
                    }
                }
                addView(view)
            }

        }

        dialog = MaterialAlertDialogBuilder(context).setTitle(title)
            .setView(LinearLayout(context).apply {
                gravity = Gravity.CENTER
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
                addView(grid)
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}