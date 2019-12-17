package de.taz.app.android.ui.settings

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import androidx.core.content.res.TypedArrayUtils
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceManager
import de.taz.app.android.R

class IssueCountPreference @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = TypedArrayUtils.getAttr(
        context, R.attr.editTextPreferenceStyle,
        android.R.attr.editTextPreferenceStyle
    ),
    defStyleRes: Int = 0
) : EditTextPreference(context, attributeSet, defStyleAttr, defStyleRes) {

    init {
        setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER
            editText.setSelection(editText.text.length)
        }

    }

    override fun onAttachedToHierarchy(preferenceManager: PreferenceManager?) {
        super.onAttachedToHierarchy(preferenceManager)
        setTitle()
    }

    private fun setTitle() {
        super.setTitle(context.getString(R.string.settings_general_keep_number_issues, getPersistedString(20.toString())))
    }

    override fun setTitle(titleResId: Int) {
        setTitle()
    }

    override fun setTitle(title: CharSequence?) {
        setTitle()
    }

    override fun persistString(value: String?): Boolean {
        val persisted = super.persistString(value)
        if (persisted) {
            setTitle()
        }
        return persisted
    }

}