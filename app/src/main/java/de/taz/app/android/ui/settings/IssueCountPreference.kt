package de.taz.app.android.ui.settings

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import androidx.core.content.res.TypedArrayUtils
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceManager
import de.taz.app.android.PREFERENCES_GENERAL
import de.taz.app.android.R
import de.taz.app.android.util.KEEP_ISSUES_DOWNLOADED_DEFAULT

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
        dialogTitle = context.getString(R.string.settings_general_keep_number_issue_title)

        setOnBindEditTextListener { editText ->
            editText.inputType = InputType.TYPE_CLASS_NUMBER
            editText.setSelection(editText.text.length)
        }

    }

    override fun onAttachedToHierarchy(preferenceManager: PreferenceManager?) {
        super.onAttachedToHierarchy(preferenceManager)
        // this is necessary - but hell knows why
        preferenceManager?.sharedPreferencesName = PREFERENCES_GENERAL
        text = getPersistedString(KEEP_ISSUES_DOWNLOADED_DEFAULT.toString())
        setTitle()
    }

    private fun setTitle() {
        super.setTitle(context.getString(R.string.settings_general_keep_number_issues, text))
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