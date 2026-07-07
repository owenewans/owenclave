/******************************************************************************
 *                                                                            *
 * Copyright (C) 2026  starifly                                               *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.widget

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatSpinner

class ReselectableSpinner @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatSpinner(context, attrs) {

    var onPopupClosed: (() -> Unit)? = null

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) onPopupClosed?.invoke()
    }

    override fun setSelection(position: Int) {
        val reselected = position == selectedItemPosition
        super.setSelection(position)
        if (reselected) notifyReselected(position)
    }

    override fun setSelection(position: Int, animate: Boolean) {
        val reselected = position == selectedItemPosition
        super.setSelection(position, animate)
        if (reselected) notifyReselected(position)
    }

    private fun notifyReselected(position: Int) {
        if (position < 0) return
        onItemSelectedListener?.onItemSelected(
            this, selectedView, position, adapter?.getItemId(position) ?: -1L
        )
    }
}