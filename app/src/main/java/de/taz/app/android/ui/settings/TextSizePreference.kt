package de.taz.app.android.ui.settings

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import de.taz.app.android.R
import de.taz.app.android.util.ToastHelper

class TextSizePreference @JvmOverloads constructor(
    context: Context, attributeSet: AttributeSet? = null, defStyleAttr: Int = 0, defStyleRes: Int = 0
) : Preference (context, attributeSet, defStyleAttr, defStyleRes) {

    init {
        widgetLayoutResource = R.layout.custom_preference_font_size
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder?) {
        super.onBindViewHolder(holder)
        holder?.let {
            it.itemView.isClickable = false
            it.findViewById(R.id.settings_text_decrease)?.setOnClickListener{
                ToastHelper.getInstance().makeToast("descrease!")
            }
            it.findViewById(R.id.settings_text_increase)?.setOnClickListener{
                ToastHelper.getInstance().makeToast("increase!")
            }
        }
    }
}