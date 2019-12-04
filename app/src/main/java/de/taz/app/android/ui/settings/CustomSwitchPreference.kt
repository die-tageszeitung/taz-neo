package de.taz.app.android.ui.settings

import android.content.Context
import android.util.AttributeSet
import android.widget.Switch
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreference
import de.taz.app.android.R


class CustomSwitchPreference @JvmOverloads constructor(
    context: Context, attributeSet: AttributeSet? = null, defStyleAttr: Int = 0, defStyleRes: Int = 0
) : SwitchPreference(context, attributeSet, defStyleAttr, defStyleRes) {

    init {
        widgetLayoutResource = R.layout.custom_preference_switch_2
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder?) {
        super.onBindViewHolder(holder)
        holder?.let {

            val switchLayout = it.findViewById(R.id.settings_night_mode) as? Switch

            switchLayout?.isChecked = getPersistedBoolean(false)

            switchLayout?.setOnClickListener {
                persistBoolean(switchLayout.isChecked)
            }

            it.findViewById(R.id.settings_night_mode)
        }
    }

}
