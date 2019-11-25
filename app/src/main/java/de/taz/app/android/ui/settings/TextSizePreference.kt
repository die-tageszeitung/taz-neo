package de.taz.app.android.ui.settings

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceViewHolder
import de.taz.app.android.R

class TextSizePreference @JvmOverloads constructor(
    context: Context, attributeSet: AttributeSet? = null, defStyleAttr: Int = 0, defStyleRes: Int = 0
) : EditTextPreference (context, attributeSet, defStyleAttr, defStyleRes) {

    init {
        widgetLayoutResource = R.layout.custom_preference_font_size
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder?) {
        super.onBindViewHolder(holder)
        holder?.let {
            it.itemView.isClickable = false
            it.findViewById(R.id.settings_text_decrease)?.setOnClickListener{
                modifySize(-10)
            }

            val textView = it.findViewById(R.id.settings_text_size) as? TextView
            textView?.text = getPersistedString("100")

            it.findViewById(R.id.settings_text_increase)?.setOnClickListener {
                modifySize(10)
            }
        }
    }

    private fun modifySize(percentage: Int) {
        text = (getPersistedString("100").toInt() + percentage).toString()
    }

}