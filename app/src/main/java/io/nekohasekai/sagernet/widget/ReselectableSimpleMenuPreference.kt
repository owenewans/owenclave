/******************************************************************************
 *                                                                            *
 * Copyright (C) 2026  dyhkwong                                               *
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
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.core.content.ContextCompat
import androidx.core.view.setPadding
import androidx.preference.DropDownPreference
import androidx.preference.PreferenceViewHolder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.ktx.dp2px

open class ReselectableSimpleMenuPreference
@JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.dropdownPreferenceStyle,
    defStyleRes: Int = 0
) : DropDownPreference(context, attrs, defStyleAttr, defStyleRes) {

    init {
        layoutResource = R.layout.preference_dropdown_reselectable
    }

    private var dropdownOpened = false

    override fun onClick() {
        dropdownOpened = true
        super.onClick()
    }

    private lateinit var mAdapter: SimpleMenuAdapter

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        dropdownOpened = false
        super.onBindViewHolder(holder)
        val mSpinner = holder.itemView.findViewById<Spinner>(androidx.preference.R.id.spinner)
        mSpinner.layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT
        mSpinner.setPadding(dp2px(2))

        val listener = mSpinner.onItemSelectedListener
        mSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {

            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                mAdapter.setSelectedItemPosition(position)
                val newValue = entryValues[position].toString()
                if (dropdownOpened && newValue == value) {
                    callChangeListener(newValue)
                }
                dropdownOpened = false
                listener?.onItemSelected(parent, view, position, id)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                dropdownOpened = false
                listener?.onNothingSelected(parent)
            }

        }
    }

    override fun createAdapter(): ArrayAdapter<CharSequence?> {
        mAdapter = SimpleMenuAdapter(context, android.R.layout.simple_list_item_1)
        return mAdapter
    }

    private class SimpleMenuAdapter(context: Context, resource: Int) : ArrayAdapter<CharSequence?>(context, resource) {

        private var selectedItemPosition = -1

        fun setSelectedItemPosition(position: Int) {
            selectedItemPosition = position
            notifyDataSetChanged()
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getDropDownView(position, convertView, parent)
            view.setBackgroundColor(
                ContextCompat.getColor(context,
                    if (position == selectedItemPosition) {
                        R.color.dropdown_color_selected
                    } else {
                        R.color.dropdown_color_background
                    }
                )
            )
            return view
        }

    }

}